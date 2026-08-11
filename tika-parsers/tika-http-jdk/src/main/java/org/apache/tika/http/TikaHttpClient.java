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

import java.io.Closeable;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.tika.config.ParseTimeout;
import org.apache.tika.exception.TikaException;
import org.apache.tika.parser.ParseContext;

/**
 * Lightweight HTTP client for Tika parser modules that call external REST
 * endpoints (embedding APIs, VLM services, etc.).
 * <p>
 * Built on {@link java.net.http.HttpClient} with a daemon thread executor
 * so the JVM — including forked {@code PipesServer} processes — shuts down
 * cleanly without waiting for idle HTTP threads.
 * <p>
 * This class has no runtime dependencies beyond the JDK and {@code tika-core}.
 * Obtain an instance via {@link #build(int)} and close it when done to release
 * the underlying executor.
 *
 * @since Apache Tika 4.0
 */
public class TikaHttpClient implements Closeable {

    private static final String JSON_CONTENT_TYPE = "application/json; charset=utf-8";

    // How often a bounded HTTP wait checkpoints the task's ParseTimeout -- see
    // org.apache.tika.utils.ProcessUtils.HEARTBEAT_INTERVAL_MILLIS for the same rationale.
    private static final long HEARTBEAT_INTERVAL_MILLIS = 1000;

    private final HttpClient httpClient;
    private final ExecutorService executor;
    private final int defaultTimeoutSeconds;

    private TikaHttpClient(HttpClient httpClient, ExecutorService executor,
                           int defaultTimeoutSeconds) {
        this.httpClient = httpClient;
        this.executor = executor;
        this.defaultTimeoutSeconds = defaultTimeoutSeconds;
    }

    /**
     * Create a new {@code TikaHttpClient} with a daemon-thread executor.
     *
     * @param connectTimeoutSeconds TCP connection timeout in seconds
     */
    public static TikaHttpClient build(int connectTimeoutSeconds) {
        ExecutorService executor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "tika-http-jdk");
            t.setDaemon(true);
            return t;
        });
        HttpClient client = HttpClient.newBuilder()
                .executor(executor)
                .connectTimeout(Duration.ofSeconds(connectTimeoutSeconds))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .version(HttpClient.Version.HTTP_1_1)
                .build();
        return new TikaHttpClient(client, executor, connectTimeoutSeconds);
    }

    /**
     * POST a JSON body to {@code url} and return the response body as a string.
     * <p>
     * Equivalent to {@link #postJson(String, String, Map, int, ParseContext)} with a null
     * context: {@code requestedTimeoutSeconds} is granted unclipped, no checkpointing.
     *
     * @param url                    target URL
     * @param jsonBody               request body (UTF-8 JSON)
     * @param headers                additional HTTP headers (e.g. {@code Authorization})
     * @param requestedTimeoutSeconds read timeout; {@code 0} uses the default timeout
     * @return response body string
     * @throws IOException    on network error
     * @throws TikaException  on non-2xx HTTP status
     */
    public String postJson(String url, String jsonBody, Map<String, String> headers,
                           int requestedTimeoutSeconds) throws IOException, TikaException {
        return postJson(url, jsonBody, headers, requestedTimeoutSeconds, null);
    }

    /**
     * Same as {@link #postJson(String, String, Map, int)}, but bounds the wait to
     * {@code min(requestedTimeoutSeconds, ParseTimeout.remainingMillis())} (see
     * {@link ParseTimeout#budgetFor(long)}) so no single call can outlast the task's
     * total timeout regardless of its own configuration. While waiting, checkpoints the
     * {@link ParseTimeout} in {@code context} (if any) every
     * {@value #HEARTBEAT_INTERVAL_MILLIS} ms -- see
     * {@link org.apache.tika.utils.ProcessUtils#execute(ProcessBuilder, ParseContext, long, int, int)}
     * for the same rationale applied to subprocess calls. A null {@code context} means
     * the budget is granted unclipped.
     *
     * @param url                     target URL
     * @param jsonBody                request body (UTF-8 JSON)
     * @param headers                 additional HTTP headers (e.g. {@code Authorization})
     * @param requestedTimeoutSeconds the timeout the caller's own configuration asks
     *                                for; {@code 0} uses the default timeout
     * @param context                 may be null
     * @return response body string
     * @throws IOException    on network error
     * @throws TikaException  on non-2xx HTTP status
     */
    public String postJson(String url, String jsonBody, Map<String, String> headers,
                           int requestedTimeoutSeconds, ParseContext context) throws IOException, TikaException {
        int grantedTimeoutSeconds = grantedTimeoutSeconds(requestedTimeoutSeconds, context);
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(grantedTimeoutSeconds))
                .header("Content-Type", JSON_CONTENT_TYPE)
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8));

        headers.forEach(builder::header);

        return send(builder.build(), context);
    }

    /**
     * GET {@code url} and return the response body as a string.
     * Useful for health-check probes at init time.
     * <p>
     * Equivalent to {@link #get(String, Map, int, ParseContext)} with a null context.
     *
     * @param url                     target URL
     * @param headers                 additional HTTP headers
     * @param requestedTimeoutSeconds read timeout; {@code 0} uses the default timeout
     * @return response body string
     * @throws IOException    on network error
     * @throws TikaException  on non-2xx HTTP status
     */
    public String get(String url, Map<String, String> headers,
                      int requestedTimeoutSeconds) throws IOException, TikaException {
        return get(url, headers, requestedTimeoutSeconds, null);
    }

    /**
     * Same as {@link #get(String, Map, int)}, but bounds the wait to
     * {@code min(requestedTimeoutSeconds, ParseTimeout.remainingMillis())} and
     * checkpoints while waiting -- see
     * {@link #postJson(String, String, Map, int, ParseContext)}.
     *
     * @param url                     target URL
     * @param headers                 additional HTTP headers
     * @param requestedTimeoutSeconds the timeout the caller's own configuration asks
     *                                for; {@code 0} uses the default timeout
     * @param context                 may be null
     * @return response body string
     * @throws IOException    on network error
     * @throws TikaException  on non-2xx HTTP status
     */
    public String get(String url, Map<String, String> headers,
                      int requestedTimeoutSeconds, ParseContext context) throws IOException, TikaException {
        int grantedTimeoutSeconds = grantedTimeoutSeconds(requestedTimeoutSeconds, context);
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(grantedTimeoutSeconds))
                .GET();

        headers.forEach(builder::header);

        return send(builder.build(), context);
    }

    /**
     * Resolves the requested timeout (0 = client default) against the task's remaining
     * budget, in whole seconds, floored at 1 -- {@code Duration.ofSeconds(0)} means "no
     * timeout" to the JDK HTTP client, never the intent of an exhausted budget, which
     * should fail fast instead.
     */
    private int grantedTimeoutSeconds(int requestedTimeoutSeconds, ParseContext context) {
        long requestedSeconds = requestedTimeoutSeconds > 0 ? requestedTimeoutSeconds : defaultTimeoutSeconds;
        long grantedMillis = ParseTimeout.getOrCreate(context).budgetFor(requestedSeconds * 1000L);
        return (int) Math.max(1L, grantedMillis / 1000L);
    }

    private String send(HttpRequest request, ParseContext context) throws IOException, TikaException {
        CompletableFuture<HttpResponse<String>> future = httpClient.sendAsync(
                request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        try {
            HttpResponse<String> response = waitWithHeartbeat(future, context);
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new TikaException("HTTP " + response.statusCode()
                        + " from " + request.uri() + ": " + response.body());
            }
            return response.body();
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IOException ioException) {
                throw ioException;
            }
            throw new IOException("HTTP request failed: " + request.uri(), cause);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            throw new IOException("HTTP request interrupted: " + request.uri(), e);
        }
    }

    /**
     * Waits for the future to complete, polling in {@value #HEARTBEAT_INTERVAL_MILLIS} ms
     * increments and checkpointing {@code context}'s {@link ParseTimeout} on each
     * increment that doesn't complete. The actual deadline is enforced by the JDK via the
     * {@code HttpRequest}'s own {@code timeout(Duration)} (see
     * {@link #send(HttpRequest, ParseContext)}), surfacing as
     * {@link java.net.http.HttpTimeoutException} wrapped in the {@link ExecutionException}
     * this method throws -- this loop only sets checkpoint cadence.
     */
    private HttpResponse<String> waitWithHeartbeat(CompletableFuture<HttpResponse<String>> future,
                                                    ParseContext context)
            throws InterruptedException, ExecutionException {
        while (true) {
            try {
                return future.get(HEARTBEAT_INTERVAL_MILLIS, TimeUnit.MILLISECONDS);
            } catch (java.util.concurrent.TimeoutException e) {
                ParseTimeout.checkpoint(context);
            }
        }
    }

    @Override
    public void close() {
        executor.shutdown();
    }
}
