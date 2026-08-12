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
import java.net.http.HttpTimeoutException;
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
import org.apache.tika.exception.TikaTimeoutException;
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
     * Equivalent to {@link #postJson(String, String, Map, long, ParseContext)} with a null
     * context: {@code requestedTimeoutMillis} is granted unclipped, no checkpointing.
     *
     * @param url                   target URL
     * @param jsonBody              request body (UTF-8 JSON)
     * @param headers               additional HTTP headers (e.g. {@code Authorization})
     * @param requestedTimeoutMillis read timeout in millis; {@code 0} uses the default timeout
     * @return response body string
     * @throws IOException    on network error
     * @throws TikaException  on non-2xx HTTP status
     */
    public String postJson(String url, String jsonBody, Map<String, String> headers,
                           long requestedTimeoutMillis) throws IOException, TikaException {
        return postJson(url, jsonBody, headers, requestedTimeoutMillis, null);
    }

    /**
     * Same as {@link #postJson(String, String, Map, long)}, but bounds the wait to
     * {@code min(requestedTimeoutMillis, ParseTimeout.remainingMillis())} (see
     * {@link ParseTimeout#budgetFor(long)}) so no single call can outlast the task's
     * total timeout regardless of its own configuration. While waiting, checkpoints the
     * {@link ParseTimeout} in {@code context} (if any) every
     * {@value #HEARTBEAT_INTERVAL_MILLIS} ms -- see
     * {@link org.apache.tika.utils.ProcessUtils#execute(ProcessBuilder, ParseContext, long, int, int)}
     * for the same rationale applied to subprocess calls. A null {@code context} means
     * the budget is granted unclipped.
     *
     * @param url                    target URL
     * @param jsonBody               request body (UTF-8 JSON)
     * @param headers                additional HTTP headers (e.g. {@code Authorization})
     * @param requestedTimeoutMillis the timeout the caller's own configuration asks
     *                               for, in millis; {@code 0} uses the default timeout
     * @param context                may be null
     * @return response body string
     * @throws IOException    on network error
     * @throws TikaException  on non-2xx HTTP status
     */
    public String postJson(String url, String jsonBody, Map<String, String> headers,
                           long requestedTimeoutMillis, ParseContext context) throws IOException, TikaException {
        long requestedMillis = requestedMillis(requestedTimeoutMillis);
        long grantedMillis = grantedMillis(requestedMillis, context);
        failFastIfExhausted(url, requestedMillis, grantedMillis);
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMillis(grantedMillis))
                .header("Content-Type", JSON_CONTENT_TYPE)
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8));

        headers.forEach(builder::header);

        return send(builder.build(), context, requestedMillis, grantedMillis);
    }

    /**
     * GET {@code url} and return the response body as a string.
     * Useful for health-check probes at init time.
     * <p>
     * Equivalent to {@link #get(String, Map, long, ParseContext)} with a null context.
     *
     * @param url                    target URL
     * @param headers                additional HTTP headers
     * @param requestedTimeoutMillis read timeout in millis; {@code 0} uses the default timeout
     * @return response body string
     * @throws IOException    on network error
     * @throws TikaException  on non-2xx HTTP status
     */
    public String get(String url, Map<String, String> headers,
                      long requestedTimeoutMillis) throws IOException, TikaException {
        return get(url, headers, requestedTimeoutMillis, null);
    }

    /**
     * Same as {@link #get(String, Map, long)}, but bounds the wait to
     * {@code min(requestedTimeoutMillis, ParseTimeout.remainingMillis())} and
     * checkpoints while waiting -- see
     * {@link #postJson(String, String, Map, long, ParseContext)}.
     *
     * @param url                    target URL
     * @param headers                additional HTTP headers
     * @param requestedTimeoutMillis the timeout the caller's own configuration asks
     *                               for, in millis; {@code 0} uses the default timeout
     * @param context                may be null
     * @return response body string
     * @throws IOException    on network error
     * @throws TikaException  on non-2xx HTTP status
     */
    public String get(String url, Map<String, String> headers,
                      long requestedTimeoutMillis, ParseContext context) throws IOException, TikaException {
        long requestedMillis = requestedMillis(requestedTimeoutMillis);
        long grantedMillis = grantedMillis(requestedMillis, context);
        failFastIfExhausted(url, requestedMillis, grantedMillis);
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMillis(grantedMillis))
                .GET();

        headers.forEach(builder::header);

        return send(builder.build(), context, requestedMillis, grantedMillis);
    }

    private long requestedMillis(long requestedTimeoutMillis) {
        return requestedTimeoutMillis > 0 ? requestedTimeoutMillis : defaultTimeoutSeconds * 1000L;
    }

    /**
     * Resolves the requested timeout (millis) against the task's remaining budget. Unlike
     * the old seconds-floored-at-1 version, this can legitimately return 0 -- see
     * {@link #failFastIfExhausted}, which is always called right after this.
     * <p>
     * A null context has no task to clip against -- {@code ParseTimeout.getOrCreate(null)}
     * would otherwise hand back a detached ParseTimeout built from *default* TimeoutLimits
     * (1 hour), silently capping any request above that and re-firing budgetFor's
     * once-per-task warnings on every call, since a fresh detached instance is created each
     * time (see {@code ProcessUtils.execute}'s null-context handling for the same
     * rationale). Package-private (rather than private) so it can be unit tested directly.
     */
    long grantedMillis(long requestedMillis, ParseContext context) {
        return context == null ? requestedMillis : ParseTimeout.getOrCreate(context).budgetFor(requestedMillis);
    }

    /**
     * An exhausted task budget (granted == 0) must fail immediately, not be floored up to
     * a 1-second HTTP call -- a document with many post-deadline calls (e.g. batched
     * embedding requests) would otherwise pay a full extra second per call instead of
     * failing fast, same as {@code ProcessUtils} does for external processes.
     */
    private void failFastIfExhausted(String url, long requestedMillis, long grantedMillis) throws TikaTimeoutException {
        if (grantedMillis <= 0) {
            throw new TikaTimeoutException("HTTP request to " + url + " not attempted",
                    requestedMillis, grantedMillis);
        }
    }

    private String send(HttpRequest request, ParseContext context, long requestedMillis, long grantedMillis)
            throws IOException, TikaException {
        CompletableFuture<HttpResponse<String>> future = httpClient.sendAsync(
                request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        try {
            HttpResponse<String> response = waitWithHeartbeat(future, context, grantedMillis,
                    request.uri(), requestedMillis);
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new TikaException("HTTP " + response.statusCode()
                        + " from " + request.uri() + ": " + response.body());
            }
            return response.body();
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof HttpTimeoutException) {
                // The JDK's own request timeout fired before our loop-level deadline did
                // (see waitWithHeartbeat) -- same budget, report it the same way.
                throw new TikaTimeoutException("HTTP request to " + request.uri() + " timed out",
                        requestedMillis, grantedMillis);
            }
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
     * Waits for the future to complete, polling in up to {@value #HEARTBEAT_INTERVAL_MILLIS}
     * ms increments and checkpointing {@code context}'s {@link ParseTimeout} on each
     * increment that doesn't complete -- mirrors
     * {@link org.apache.tika.utils.ProcessUtils#waitForWithHeartbeat}.
     * <p>
     * The {@code HttpRequest}'s own {@code timeout(Duration)} (set from this same
     * {@code grantedMillis} budget) is the primary deadline and should fire first in the
     * common case. But {@code HttpRequest.timeout} is not a guaranteed bound on the full
     * exchange in every JDK/transport scenario -- notably a slow-trickling response body
     * after headers have already arrived -- and this loop's own checkpoint call would
     * otherwise misreport an unbounded stall as "progress" every {@code
     * HEARTBEAT_INTERVAL_MILLIS}, defeating the task's stall detector. This method
     * therefore enforces the same deadline itself, independent of the JDK, and cancels the
     * future rather than waiting indefinitely if it's ever reached first.
     */
    private HttpResponse<String> waitWithHeartbeat(CompletableFuture<HttpResponse<String>> future,
                                                    ParseContext context, long grantedMillis, URI uri,
                                                    long requestedMillis)
            throws InterruptedException, ExecutionException, TikaTimeoutException {
        long startNanos = System.nanoTime();
        while (true) {
            long elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000L;
            long remaining = grantedMillis - elapsedMillis;
            long pollMillis = remaining <= 0 ? 0 : Math.min(remaining, HEARTBEAT_INTERVAL_MILLIS);
            try {
                return future.get(pollMillis, TimeUnit.MILLISECONDS);
            } catch (java.util.concurrent.TimeoutException e) {
                if (remaining <= 0) {
                    future.cancel(true);
                    throw new TikaTimeoutException("HTTP request to " + uri + " timed out",
                            requestedMillis, grantedMillis);
                }
                ParseTimeout.checkpoint(context);
            }
        }
    }

    @Override
    public void close() {
        executor.shutdown();
    }
}
