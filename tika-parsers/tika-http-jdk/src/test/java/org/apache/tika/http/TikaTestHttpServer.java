/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.tika.http;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Minimal mock HTTP/1.1 server for unit tests, backed by a plain
 * {@link ServerSocket}. Has no dependencies outside the JDK.
 * <p>
 * Drop-in replacement for OkHttp's {@code MockWebServer} in Tika unit tests.
 * <p>
 * Usage:
 * <pre>{@code
 * try (TikaTestHttpServer server = new TikaTestHttpServer()) {
 *     server.enqueue(new MockResponse(200, "{\"data\":[]}"));
 *     // configure code under test to use server.url()
 *     RecordedRequest req = server.takeRequest();
 *     assertEquals("POST", req.method());
 * }
 * }</pre>
 *
 * @since Apache Tika 4.0
 */
public class TikaTestHttpServer implements Closeable {

    /** A pre-programmed response to return for the next incoming request. */
    public record MockResponse(int status, String body) {}

    /** A captured incoming HTTP request. */
    public record RecordedRequest(String method, String path,
                                  Map<String, String> headers, String body) {
        /** Returns the header value for {@code name} (case-insensitive), or {@code null}. */
        public String header(String name) {
            return headers.get(name.toLowerCase(java.util.Locale.ROOT));
        }
    }

    private final ServerSocket serverSocket;
    private final ExecutorService executor;
    private final BlockingQueue<MockResponse> responses = new LinkedBlockingQueue<>();
    private final BlockingQueue<RecordedRequest> requests = new LinkedBlockingQueue<>();
    private final AtomicInteger requestCount = new AtomicInteger(0);
    private volatile boolean running = true;

    public TikaTestHttpServer() throws IOException {
        serverSocket = new ServerSocket(0);
        executor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "tika-test-http");
            t.setDaemon(true);
            return t;
        });
        executor.submit(this::acceptLoop);
    }

    private void acceptLoop() {
        while (running) {
            try {
                Socket socket = serverSocket.accept();
                executor.submit(() -> handleConnection(socket));
            } catch (IOException e) {
                if (running) {
                    // unexpected error while accepting
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    private void handleConnection(Socket socket) {
        try (socket) {
            // Use a single BufferedReader for the entire connection — reading
            // body via the same reader avoids the buffered-read-ahead pitfall
            // where a raw InputStream read would miss bytes already buffered.
            // Body content is JSON (UTF-8/ASCII), so char-level reading is safe.
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(),
                            StandardCharsets.ISO_8859_1));
            OutputStream out = socket.getOutputStream();

            // Parse request line: METHOD path HTTP/1.x
            String requestLine = reader.readLine();
            if (requestLine == null || requestLine.isBlank()) {
                return;
            }
            String[] parts = requestLine.split(" ", 3);
            String method = parts[0];
            String path = parts.length > 1 ? parts[1] : "/";

            // Parse headers
            Map<String, String> headers = new HashMap<>();
            int contentLength = 0;
            String line;
            while ((line = reader.readLine()) != null && !line.isEmpty()) {
                int colon = line.indexOf(':');
                if (colon > 0) {
                    String name = line.substring(0, colon).trim()
                            .toLowerCase(java.util.Locale.ROOT);
                    String value = line.substring(colon + 1).trim();
                    headers.put(name, value);
                    if ("content-length".equals(name)) {
                        try {
                            contentLength = Integer.parseInt(value);
                        } catch (NumberFormatException ignored) {
                            // ignore
                        }
                    }
                }
            }

            // Read body through the same BufferedReader to avoid consuming bytes
            // from the underlying stream that are already buffered in the reader.
            String body = "";
            String transferEncoding = headers.getOrDefault("transfer-encoding", "");
            if (transferEncoding.toLowerCase(java.util.Locale.ROOT).contains("chunked")) {
                body = readChunkedFromReader(reader);
            } else if (contentLength > 0) {
                char[] bodyChars = new char[contentLength];
                int read = 0;
                while (read < contentLength) {
                    int n = reader.read(bodyChars, read, contentLength - read);
                    if (n < 0) {
                        break;
                    }
                    read += n;
                }
                body = new String(bodyChars, 0, read);
            }

            requests.add(new RecordedRequest(method, path, headers, body));
            requestCount.incrementAndGet();

            // Send response
            MockResponse resp = responses.poll();
            if (resp == null) {
                resp = new MockResponse(500, "{\"error\":\"no response queued\"}");
            }

            byte[] responseBytes = resp.body().getBytes(StandardCharsets.UTF_8);
            String statusText = resp.status() == 200 ? "OK"
                    : resp.status() == 500 ? "Internal Server Error"
                    : String.valueOf(resp.status());
            String responseHeaders =
                    "HTTP/1.1 " + resp.status() + " " + statusText + "\r\n"
                    + "Content-Type: application/json\r\n"
                    + "Content-Length: " + responseBytes.length + "\r\n"
                    + "Connection: close\r\n"
                    + "\r\n";
            out.write(responseHeaders.getBytes(StandardCharsets.US_ASCII));
            out.write(responseBytes);
            out.flush();
        } catch (IOException e) {
            // connection closed or error; ignore in test context
        }
    }

    private static String readChunkedFromReader(BufferedReader reader) throws IOException {
        StringBuilder sb = new StringBuilder();
        String sizeLine;
        while ((sizeLine = reader.readLine()) != null) {
            // strip any chunk extensions (e.g. "4;ext=val")
            int semicolon = sizeLine.indexOf(';');
            String hexSize = semicolon >= 0 ? sizeLine.substring(0, semicolon) : sizeLine;
            int chunkSize = Integer.parseInt(hexSize.trim(), 16);
            if (chunkSize == 0) {
                reader.readLine(); // consume trailing empty line
                break;
            }
            char[] chunk = new char[chunkSize];
            int read = 0;
            while (read < chunkSize) {
                int n = reader.read(chunk, read, chunkSize - read);
                if (n < 0) {
                    break;
                }
                read += n;
            }
            sb.append(chunk, 0, read);
            reader.readLine(); // consume CRLF after chunk data
        }
        return sb.toString();
    }

    /** Queue a response to return for the next request. */
    public void enqueue(MockResponse response) {
        responses.add(response);
    }

    /**
     * Retrieves and removes the earliest recorded request, waiting up to
     * 5 seconds if necessary.
     *
     * @return the recorded request, or {@code null} if no request arrived
     *         within the timeout
     */
    public RecordedRequest takeRequest() throws InterruptedException {
        return requests.poll(5, TimeUnit.SECONDS);
    }

    /**
     * Returns the total number of requests received so far
     * (including those already consumed by {@link #takeRequest()}).
     */
    public int getRequestCount() {
        return requestCount.get();
    }

    /**
     * Clears all recorded requests and resets the request counter to zero.
     * <p>
     * Call this in test {@code setUp()} after invoking {@code initialize()} on a
     * parser under test so that health-check probes made during initialization do
     * not pollute per-test assertions about request count or request content.
     */
    public void clearRequests() {
        requests.clear();
        requestCount.set(0);
    }

    /** Returns the base URL (e.g. {@code http://localhost:54321}) with no trailing slash. */
    public String url() {
        return "http://localhost:" + serverSocket.getLocalPort();
    }

    public void shutdown() {
        running = false;
        try {
            serverSocket.close();
        } catch (IOException ignored) {
            // ignore
        }
        executor.shutdownNow();
    }

    @Override
    public void close() {
        shutdown();
    }
}
