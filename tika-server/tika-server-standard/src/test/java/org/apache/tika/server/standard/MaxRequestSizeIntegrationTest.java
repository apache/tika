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
package org.apache.tika.server.standard;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import jakarta.ws.rs.core.Response;
import org.apache.cxf.jaxrs.client.WebClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.apache.tika.server.core.IntegrationTestBase;
import org.apache.tika.server.core.TestPortAllocator;

/**
 * Transfer limits on a real server, across every body-accepting endpoint its welcome page
 * lists.
 */
public class MaxRequestSizeIntegrationTest extends IntegrationTestBase {

    // above what /language reads, so that endpoint cannot trip the limit by reading alone
    private static final int MAX_BYTES = 200_000;
    private static final int MAX_READ_MILLIS = 3_000;
    private static final int MAX_WRITE_MILLIS = 3_000;
    private static final int IDLE_TIMEOUT_MILLIS = 2_000;
    // room for two maximum-size bodies and part of a third
    private static final int MAX_IN_FLIGHT_BYTES = 3 * MAX_BYTES;
    // past the 16 MiB CXF discards after a response, so a reply proves reading stopped
    private static final int SENT_BYTES = 20 * 1024 * 1024;
    private static final String BOUNDARY = "tika-limit-boundary";
    // from tika-server-core's test preset catalog
    private static final String PRESET = "catalog-xml-content";
    // text in bigTextZip(): far more than the socket buffers hold
    private static final int TEXT_BYTES = 16 * 1024 * 1024;
    private static final String[] TIKA_TEXT = {"PUT", "/tika/text", "text/plain"};

    private static final Map<String, byte[]> PREAMBLES = new LinkedHashMap<>();

    static {
        PREAMBLES.put("text/plain", new byte[0]);
        // an open JSON string, so a JSON reader keeps consuming
        PREAMBLES.put("application/json", "{\"a\":\"".getBytes(UTF_8));
        PREAMBLES.put("multipart/form-data; boundary=" + BOUNDARY,
                ("--" + BOUNDARY + "\r\nContent-Disposition: form-data; name=\"file\"; "
                        + "filename=\"a.txt\"\r\nContent-Type: text/plain\r\n\r\n").getBytes(UTF_8));
    }

    @TempDir
    static Path classWorkingDir;

    private final int metricsPort = TestPortAllocator.findFreePort();

    @BeforeAll
    public void startServer() throws Exception {
        // every endpoint: the defaults, plus pipes/async via allowPipes and the config routes
        String config = """
                {
                  "server": {
                    "allowPipes": true,
                    "allowPerRequestConfig": true,
                    "allowHttp2": true,
                    "maxRequestSizeBytes": %d,
                    "maxRequestReadMillis": %r,
                    "maxResponseWriteMillis": %w,
                    "idleTimeoutMillis": %i,
                    "maxInFlightRequestBytes": %c,
                    "metricsPort": %m
                  },
                  "pipes": { "numClients": 1, "forkedJvmArgs": ["-Xmx256m"] },
                  "plugin-roots": "%s"
                }
                """
                .replace("%d", Integer.toString(MAX_BYTES))
                .replace("%r", Integer.toString(MAX_READ_MILLIS))
                .replace("%w", Integer.toString(MAX_WRITE_MILLIS))
                .replace("%i", Integer.toString(IDLE_TIMEOUT_MILLIS))
                .replace("%c", Integer.toString(MAX_IN_FLIGHT_BYTES))
                .replace("%m", Integer.toString(metricsPort))
                .replace("%s", Paths.get("target/plugins").toAbsolutePath().toString().replace("\\", "/"));
        Path configFile = Files.writeString(classWorkingDir.resolve("request-limit.json"), config);
        startClassProcess(new String[]{"-config", configFile.toString(), "--preset", PRESET},
                classWorkingDir);
    }

    /** Whatever a test did to the server, every request's share of the body budget comes back. */
    @AfterEach
    public void assertBudgetDrained() throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        long inFlight;
        do {
            inFlight = bodyBytesInFlight();
            if (inFlight != 0) {
                Thread.sleep(50);
            }
        } while (inFlight != 0 && System.nanoTime() < deadline);
        assertEquals(0, inFlight, "request body budget not released");
    }

    @Test
    public void testEveryEndpointAnswersOverLimitChunkedBeforeBodyEnds() throws Exception {
        List<String[]> endpoints = bodyEndpoints();
        assertTrue(endpoints.size() > 40, "welcome page listed too few endpoints: " + endpoints.size());
        List<String> failures = new ArrayList<>();
        for (String[] endpoint : endpoints) {
            boolean anyTooLarge = false;
            List<String> statuses = new ArrayList<>();
            for (Map.Entry<String, byte[]> e : PREAMBLES.entrySet()) {
                String status = sendChunked(endpoint, e.getKey(), e.getValue());
                statuses.add(e.getKey() + "=" + status);
                // fail fast: each further hang would cost another socket timeout
                assertNotNull(status, String.join(" ", endpoint) + " " + e.getKey()
                        + ": no response before the body ended");
                if (status.startsWith("2")) {
                    failures.add(String.join(" ", endpoint) + " " + e.getKey() + " -> " + status);
                }
                anyTooLarge |= "413".equals(status);
            }
            if (!anyTooLarge) {
                failures.add(String.join(" ", endpoint) + " never reached the limit: " + statuses);
            }
        }
        assertTrue(failures.isEmpty(), String.join("\n", failures));
    }

    @Test
    public void testEveryEndpointRejectsOverLimitContentLength() throws Exception {
        List<String> failures = new ArrayList<>();
        for (String[] endpoint : bodyEndpoints()) {
            List<String> statuses = new ArrayList<>();
            for (Map.Entry<String, byte[]> e : PREAMBLES.entrySet()) {
                statuses.add(sendDeclared(endpoint, e.getKey(), e.getValue()));
            }
            // other types may be refused (415) before the size is looked at
            if (!statuses.contains("413") || statuses.stream().anyMatch(s -> s == null || s.startsWith("2"))) {
                failures.add(String.join(" ", endpoint) + " -> " + statuses);
            }
        }
        assertTrue(failures.isEmpty(), String.join("\n", failures));
    }

    /** A body dripped in under the size limit is cut off at the read deadline. */
    @Test
    public void testSlowBodyTimesOut() throws Exception {
        assertWithinReadDeadline("408", sendThenDrip(TIKA_TEXT, 0));
    }

    /** CXF discards up to 16 MiB left over after a 413; a drip must not stretch that out. */
    @Test
    public void testSlowRemainderAfterOverLimitTimesOut() throws Exception {
        assertWithinReadDeadline("413", sendThenDrip(TIKA_TEXT, 2 * MAX_BYTES));
    }

    /** Refused before any resource runs, the body is still discarded under the read deadline. */
    @Test
    public void testSlowBodyToUnmatchedPathTimesOut() throws Exception {
        assertWithinReadDeadline("404", sendThenDrip(new String[]{"PUT", "/nonexistent", "*/*"}, 0));
    }

    /**
     * CXF answers OPTIONS itself, before any resource or filter runs; the body must still be
     * discarded, not spooled to disk without limit.
     */
    @Test
    public void testOptionsBodyNotSpooled() throws Exception {
        for (String path : new String[]{"/tika", "/rmeta"}) {
            String[] reply = sendThenDrip(new String[]{"OPTIONS", path, "*/*"}, SENT_BYTES);
            assertEquals("200", reply[0]);
            // spooling reads on until the read deadline; discarding stops at 16 MiB and answers
            assertTrue(Long.parseLong(reply[1]) < MAX_READ_MILLIS / 2, "answered after " + reply[1] + " ms");
        }
    }

    private static void assertWithinReadDeadline(String expected, String[] reply) {
        assertEquals(expected, reply[0], "after " + reply[1] + " ms");
        // the idle timeout alone would answer only once the drip ends
        assertTrue(Long.parseLong(reply[1]) < 2 * MAX_READ_MILLIS, "answered after " + reply[1] + " ms");
    }

    /** Bodies held in flight fill the budget; the request that would go over gets 429. */
    @Test
    public void testOverBudgetRefused() throws Exception {
        int held = MAX_BYTES - 10_000;
        List<Socket> uploads = new ArrayList<>();
        try {
            for (int i = 0; i < 3; i++) {
                uploads.add(startUpload(held));
            }
            awaitBodyBytesInFlight(3L * held);
            try (Socket socket = new Socket("localhost", integrationTestPort)) {
                socket.setSoTimeout(10_000);
                OutputStream out = socket.getOutputStream();
                out.write(requestHead(TIKA_TEXT, "text/plain")
                        .append("Transfer-Encoding: chunked\r\n\r\n").toString().getBytes(UTF_8));
                writeChunk(out, new byte[MAX_IN_FLIGHT_BYTES - 3 * held + 1]);
                out.write("0\r\n\r\n".getBytes(UTF_8));
                out.flush();
                assertEquals("429", readStatus(socket));
            }
        } finally {
            for (Socket upload : uploads) {
                upload.close();
            }
        }
    }

    /** Clients that send slowly hold only what they have sent, so they cannot lock others out. */
    @Test
    public void testDripsDoNotExhaustBudget() throws Exception {
        List<Socket> uploads = new ArrayList<>();
        try {
            for (int i = 0; i < 16; i++) {
                uploads.add(startUpload(0));
            }
            awaitBodyBytesInFlight(16);
            assertEquals("200", sendSmall());
        } finally {
            for (Socket upload : uploads) {
                upload.close();
            }
        }
    }

    private void awaitBodyBytesInFlight(long atLeast) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (bodyBytesInFlight() < atLeast) {
            assertTrue(System.nanoTime() < deadline, "uploads not in flight: " + bodyBytesInFlight());
            Thread.sleep(20);
        }
    }

    private long bodyBytesInFlight() {
        String scrape = WebClient.create("http://localhost:" + metricsPort + "/metrics").get()
                .readEntity(String.class);
        for (String line : scrape.split("\n")) {
            if (line.startsWith("tika_server_request_body_in_flight_bytes ")) {
                return (long) Double.parseDouble(line.substring(line.indexOf(' ') + 1).trim());
            }
        }
        throw new AssertionError("no body budget gauge in:\n" + scrape);
    }

    private String sendSmall() throws IOException {
        byte[] body = "hello world".getBytes(UTF_8);
        try (Socket socket = new Socket("localhost", integrationTestPort)) {
            socket.setSoTimeout(10_000);
            OutputStream out = socket.getOutputStream();
            out.write(requestHead(new String[]{"PUT", "/tika/text", "text/plain"}, "text/plain")
                    .append("Content-Length: ").append(body.length).append("\r\n\r\n")
                    .toString().getBytes(UTF_8));
            out.write(body);
            out.flush();
            return readStatus(socket);
        }
    }

    /** An upload of {@code headBytes}, then kept in flight a byte at a time until its socket is closed. */
    private Socket startUpload(int headBytes) throws IOException {
        Socket socket = new Socket("localhost", integrationTestPort);
        OutputStream out = socket.getOutputStream();
        Thread writer = new Thread(() -> {
            try {
                out.write(requestHead(TIKA_TEXT, "text/plain")
                        .append("Transfer-Encoding: chunked\r\n\r\n").toString().getBytes(UTF_8));
                writeChunk(out, new byte[headBytes]);
                while (!socket.isClosed()) {
                    writeChunk(out, new byte[]{'a'});
                    out.flush();
                    Thread.sleep(100);
                }
            } catch (IOException | InterruptedException e) {
                // closed
            }
        });
        writer.setDaemon(true);
        writer.start();
        return socket;
    }

    /** A client that stops sending is dropped at the idle timeout, with the same 408. */
    @Test
    public void testStalledBodyTimesOut() throws Exception {
        try (Socket socket = new Socket("localhost", integrationTestPort)) {
            socket.setSoTimeout(5 * IDLE_TIMEOUT_MILLIS);
            OutputStream out = socket.getOutputStream();
            out.write(requestHead(new String[]{"PUT", "/tika/text", "text/plain"}, "text/plain")
                    .append("Content-Length: 100\r\n\r\nabc").toString().getBytes(UTF_8));
            out.flush();
            assertEquals("408", readStatus(socket));
        }
    }

    /**
     * Sends {@code headBytes}, then a byte at a time, well inside the idle timeout, for over
     * three times the read deadline. Returns {status, millis from start to status}.
     */
    private String[] sendThenDrip(String[] endpoint, int headBytes) throws IOException {
        byte[] filler = new byte[64 * 1024];
        Arrays.fill(filler, (byte) 'a');
        try (Socket socket = new Socket("localhost", integrationTestPort)) {
            socket.setSoTimeout(5 * MAX_READ_MILLIS);
            OutputStream out = socket.getOutputStream();
            long start = System.nanoTime();
            Thread writer = new Thread(() -> {
                try {
                    out.write(requestHead(endpoint, "text/plain")
                            .append("Transfer-Encoding: chunked\r\n\r\n").toString().getBytes(UTF_8));
                    for (int sent = 0; sent < headBytes; sent += filler.length) {
                        writeChunk(out, Arrays.copyOf(filler, Math.min(filler.length, headBytes - sent)));
                    }
                    out.flush();
                    for (int i = 0; i < 100; i++) {
                        writeChunk(out, new byte[]{'a'});
                        out.flush();
                        Thread.sleep(100);
                    }
                } catch (IOException | InterruptedException e) {
                    // the server stopped reading and closed
                }
            });
            writer.setDaemon(true);
            writer.start();
            String status = readStatus(socket);
            return new String[]{status,
                    Long.toString(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start))};
        }
    }

    private static byte[] bigTextZip() throws IOException {
        ByteArrayOutputStream zipped = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(zipped)) {
            zip.putNextEntry(new ZipEntry("big.txt"));
            byte[] line = "the quick brown fox jumps over the lazy dog\n".getBytes(UTF_8);
            for (int written = 0; written < TEXT_BYTES; written += line.length) {
                zip.write(line);
            }
        }
        assertTrue(zipped.size() < MAX_BYTES);
        return zipped.toByteArray();
    }

    /**
     * A client reading too slowly to finish within the write deadline is cut off. It keeps
     * reading, so the server's writes progress to the deadline check; the response, from a
     * small zip, is far larger than the socket buffers.
     */
    @Test
    public void testSlowReaderCutOff() throws Exception {
        byte[] body = bigTextZip();
        try (Socket socket = new Socket("localhost", integrationTestPort)) {
            socket.setSoTimeout(30_000);
            OutputStream out = socket.getOutputStream();
            // keep-alive, so the body is chunked: a close-delimited one cannot show a truncation
            out.write(requestHead(new String[]{"PUT", "/tika/text", "text/plain"}, "application/zip")
                    .append("Content-Length: ").append(body.length).append("\r\n\r\n")
                    .toString().replace("Connection: close\r\n", "").getBytes(UTF_8));
            out.write(body);
            out.flush();
            InputStream in = socket.getInputStream();
            byte[] buffer = new byte[64 * 1024];
            long read = 0;
            String tail = "";
            // about 1.3 MB/s: the whole body would take over 4 times the deadline
            while (true) {
                int n;
                try {
                    n = in.read(buffer);
                } catch (IOException e) {
                    break;
                }
                if (n == -1) {
                    break;
                }
                read += n;
                tail = (tail + new String(buffer, 0, n, UTF_8));
                tail = tail.substring(Math.max(0, tail.length() - 16));
                Thread.sleep(50);
            }
            assertTrue(read < TEXT_BYTES, "the whole response arrived: " + read);
            // a cleanly ended chunked body would pass a truncated document off as complete
            assertFalse(tail.endsWith("\r\n0\r\n\r\n"), "the cut-off response ended cleanly");
        }
    }

    /**
     * With allowHttp2, the body limits act per stream: an over-limit body is refused and the
     * connection carries on.
     */
    @Test
    public void testH2cLimitsPerStream() throws Exception {
        HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_2).build();
        // a request with a body cannot upgrade to h2c, so upgrade first
        HttpResponse<String> upgrade = client.send(HttpRequest.newBuilder(URI.create(endPoint + "/version"))
                .GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(HttpClient.Version.HTTP_2, upgrade.version());
        HttpResponse<String> tooLarge = client.send(HttpRequest.newBuilder(URI.create(endPoint + "/tika/text"))
                .header("Content-Type", "text/plain")
                .PUT(HttpRequest.BodyPublishers.ofByteArray(new byte[2 * MAX_BYTES])).build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(413, tooLarge.statusCode());
        assertEquals(HttpClient.Version.HTTP_2, tooLarge.version());
        HttpResponse<String> small = client.send(HttpRequest.newBuilder(URI.create(endPoint + "/tika/text"))
                .header("Content-Type", "text/plain")
                .PUT(HttpRequest.BodyPublishers.ofString("hello world")).build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, small.statusCode());
        assertEquals(HttpClient.Version.HTTP_2, small.version());
    }

    /** The limit counts decompressed bytes, so a small gzip body cannot smuggle in a large one. */
    @Test
    public void testGzipBodyCountedDecompressed() throws Exception {
        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        try (GZIPOutputStream gz = new GZIPOutputStream(compressed)) {
            gz.write(new byte[4 * MAX_BYTES]);
        }
        byte[] body = compressed.toByteArray();
        assertTrue(body.length < MAX_BYTES);
        try (Socket socket = new Socket("localhost", integrationTestPort)) {
            socket.setSoTimeout(10_000);
            OutputStream out = socket.getOutputStream();
            out.write(requestHead(new String[]{"PUT", "/tika/text", "text/plain"}, "text/plain")
                    .append("Content-Encoding: gzip\r\nContent-Length: ").append(body.length)
                    .append("\r\n\r\n").toString().getBytes(UTF_8));
            out.write(body);
            out.flush();
            assertEquals("413", readStatus(socket));
        }
    }

    /**
     * {method, path, accept} per PUT/POST endpoint and type it produces, from the server's
     * own listing; Accept is what selects among methods sharing a path (XMP on /meta).
     */
    private List<String[]> bodyEndpoints() {
        Response response = WebClient.create(endPoint + "/").accept("text/plain").get();
        String listing = response.readEntity(String.class);
        List<String[]> endpoints = new ArrayList<>();
        String[] current = null;
        boolean producesListed = false;
        for (String line : listing.split("\n")) {
            line = line.trim();
            if (line.startsWith("=> ")) {
                if (current != null) {
                    endpoints.add(new String[]{current[0], current[1], line.substring(3)});
                    producesListed = true;
                }
                continue;
            }
            if (current != null && !producesListed) {
                endpoints.add(new String[]{current[0], current[1], "*/*"});
            }
            current = null;
            producesListed = false;
            String[] parts = line.split(" ", 2);
            if (parts.length == 2 && ("PUT".equals(parts[0]) || "POST".equals(parts[0]))) {
                current = new String[]{parts[0], fillTemplate(parts[1])};
            }
        }
        if (current != null && !producesListed) {
            endpoints.add(new String[]{current[0], current[1], "*/*"});
        }
        assertFalse(endpoints.isEmpty(), "no PUT/POST endpoints in:\n" + listing);
        return endpoints;
    }

    private static String fillTemplate(String path) {
        // {name} or {name : regex}; regexes may themselves hold braces, e.g. \w{1,3}
        StringBuilder sb = new StringBuilder();
        StringBuilder param = new StringBuilder();
        int depth = 0;
        for (char c : path.toCharArray()) {
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                if (--depth == 0) {
                    String name = param.toString().split(":")[0].trim();
                    sb.append("presetName".equals(name) ? PRESET : "text");
                    param.setLength(0);
                }
            } else if (depth > 0) {
                param.append(c);
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /** Status code, "closed" if the connection closed first, or null if nothing came in time. */
    private String sendChunked(String[] endpoint, String contentType, byte[] preamble)
            throws IOException {
        byte[] filler = new byte[64 * 1024];
        Arrays.fill(filler, (byte) 'a');
        try (Socket socket = new Socket("localhost", integrationTestPort)) {
            socket.setSoTimeout(10_000);
            OutputStream out = socket.getOutputStream();
            Thread writer = new Thread(() -> {
                try {
                    out.write(requestHead(endpoint, contentType)
                            .append("Transfer-Encoding: chunked\r\n\r\n").toString().getBytes(UTF_8));
                    writeChunk(out, preamble);
                    for (int sent = 0; sent < SENT_BYTES; sent += filler.length) {
                        writeChunk(out, filler);
                    }
                    out.flush();
                    // the terminating chunk is never sent
                } catch (IOException e) {
                    // the server stopped reading and closed
                }
            });
            writer.setDaemon(true);
            writer.start();
            return readStatus(socket);
        }
    }

    private String sendDeclared(String[] endpoint, String contentType, byte[] preamble)
            throws IOException {
        byte[] body = new byte[2 * MAX_BYTES];
        Arrays.fill(body, (byte) 'a');
        System.arraycopy(preamble, 0, body, 0, preamble.length);
        try (Socket socket = new Socket("localhost", integrationTestPort)) {
            socket.setSoTimeout(10_000);
            OutputStream out = socket.getOutputStream();
            out.write(requestHead(endpoint, contentType)
                    .append("Content-Length: ").append(body.length).append("\r\n\r\n")
                    .toString().getBytes(UTF_8));
            // sent in full: CXF reads past a rejected body before answering
            out.write(body);
            out.flush();
            return readStatus(socket);
        }
    }

    private StringBuilder requestHead(String[] endpoint, String contentType) {
        return new StringBuilder(endpoint[0]).append(' ').append(endpoint[1]).append(" HTTP/1.1\r\n")
                .append("Host: localhost\r\nConnection: close\r\n")
                .append("Accept: ").append(endpoint[2]).append("\r\n")
                .append("Content-Type: ").append(contentType).append("\r\n");
    }

    private static void writeChunk(OutputStream out, byte[] bytes) throws IOException {
        if (bytes.length == 0) {
            return;
        }
        out.write((Integer.toHexString(bytes.length) + "\r\n").getBytes(UTF_8));
        out.write(bytes);
        out.write("\r\n".getBytes(UTF_8));
    }

    private static String readStatus(Socket socket) throws IOException {
        try {
            String line = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), UTF_8)).readLine();
            // "HTTP/1.1 413 Payload Too Large"
            return line == null ? "closed" : line.split(" ")[1];
        } catch (SocketTimeoutException e) {
            return null;
        }
    }
}
