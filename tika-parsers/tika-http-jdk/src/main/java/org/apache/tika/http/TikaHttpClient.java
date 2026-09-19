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
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
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
 * <p>
 * A hosted inference engine caps the requests in flight per key and answers the excess with
 * 429 at once, usually without a Retry-After; a saturated one answers 502, 503 or 504. Those
 * four statuses are retried up to {@code maxRetries} times with a short jittered backoff
 * (250 ms doubling to 4 s), or the Retry-After the server sends, never sleeping past the
 * budget the parse granted the call. Any other status fails at once. The retries cover
 * collisions between a few parse workers, not a sustained excess of workers over the key's
 * concurrency.
 * @since Apache Tika 4.0
 */
public class TikaHttpClient implements Closeable {

    private static final String JSON_CONTENT_TYPE = "application/json; charset=utf-8";

    /** Retries of a 429/502/503/504 answer unless {@link #build(int, int)} says otherwise. */
    public static final int DEFAULT_MAX_RETRIES = 4;
    private static final Set<Integer> RETRYABLE = Set.of(429, 502, 503, 504);
    private static final long INITIAL_BACKOFF_MILLIS = 250;
    private static final long MAX_BACKOFF_MILLIS = 4000;
    /** A Retry-After longer than this is treated as "not now": the call fails instead. */
    private static final long MAX_RETRY_AFTER_MILLIS = 30_000;

    // How often a bounded HTTP wait checkpoints the task's ParseTimeout -- see
    // org.apache.tika.utils.ProcessUtils.HEARTBEAT_INTERVAL_MILLIS for the same rationale.
    private static final long HEARTBEAT_INTERVAL_MILLIS = 1000;

    private final HttpClient httpClient;
    private final ExecutorService executor;
    private final int defaultTimeoutSeconds;
    private final int maxRetries;

    private TikaHttpClient(HttpClient httpClient, ExecutorService executor,
                           int defaultTimeoutSeconds, int maxRetries) {
        this.httpClient = httpClient;
        this.executor = executor;
        this.defaultTimeoutSeconds = defaultTimeoutSeconds;
        this.maxRetries = maxRetries;
    }

    /**
     * Create a new {@code TikaHttpClient} with a daemon-thread executor.
     *
     * @param connectTimeoutSeconds TCP connection timeout in seconds
     */
    public int getMaxRetries() {
        return maxRetries;
    }

    public static TikaHttpClient build(int connectTimeoutSeconds) {
        return build(connectTimeoutSeconds, DEFAULT_MAX_RETRIES);
    }

    /**
     * Create a new {@code TikaHttpClient} with a daemon-thread executor.
     *
     * @param connectTimeoutSeconds TCP connection timeout in seconds
     * @param maxRetries            how many times a 429, 502, 503 or 504 answer is retried;
     *                              0 fails on the first one
     */
    public static TikaHttpClient build(int connectTimeoutSeconds, int maxRetries) {
        if (maxRetries < 0) {
            throw new IllegalArgumentException("maxRetries must be >= 0, not " + maxRetries);
        }
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
        return new TikaHttpClient(client, executor, connectTimeoutSeconds, maxRetries);
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
     * Resolves the requested timeout (millis) against the task's remaining budget; can
     * legitimately return 0 -- see {@link #failFastIfExhausted}, always called right after.
     * A null context grants the request unclipped: {@code ParseTimeout.getOrCreate(null)}
     * would build a fresh detached instance from default TimeoutLimits, silently capping
     * anything above one hour and re-firing budgetFor's once-per-task warnings on every
     * call. Package-private for direct unit testing.
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

    /**
     * One budget for every attempt: each retry is sent with what is left of
     * {@code grantedMillis}, and a backoff that would run past it fails the call with the
     * last answer instead.
     */
    private String send(HttpRequest request, ParseContext context, long requestedMillis, long grantedMillis)
            throws IOException, TikaException {
        long startNanos = System.nanoTime();
        for (int attempt = 0; ; attempt++) {
            long remaining = grantedMillis - (System.nanoTime() - startNanos) / 1_000_000L;
            if (remaining <= 0) {
                throw new TikaTimeoutException("HTTP request to " + request.uri() + " timed out",
                        requestedMillis, grantedMillis);
            }
            HttpRequest attemptRequest = attempt == 0 ? request
                    : HttpRequest.newBuilder(request, (name, value) -> true)
                            .timeout(Duration.ofMillis(remaining)).build();
            HttpResponse<String> response = sendOnce(attemptRequest, context, requestedMillis, remaining);
            int status = response.statusCode();
            if (status >= 200 && status < 300) {
                return response.body();
            }
            TikaException failure = new TikaException("HTTP " + status
                    + " from " + request.uri() + ": " + response.body());
            if (!RETRYABLE.contains(status) || attempt >= maxRetries) {
                throw failure;
            }
            long delay = retryDelayMillis(response, attempt);
            long left = grantedMillis - (System.nanoTime() - startNanos) / 1_000_000L;
            if (delay < 0 || delay >= left) {
                throw failure;
            }
            sleepWithHeartbeat(delay, context);
        }
    }

    private HttpResponse<String> sendOnce(HttpRequest request, ParseContext context,
                                          long requestedMillis, long grantedMillis)
            throws IOException, TikaException {
        CompletableFuture<HttpResponse<String>> future = httpClient.sendAsync(
                request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        try {
            return waitWithHeartbeat(future, context, grantedMillis, request.uri(), requestedMillis);
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

    /** The server's Retry-After (delta seconds or an HTTP date) when usable, else backoff. */
    static long retryDelayMillis(HttpResponse<String> response, int attempt) {
        String retryAfter = response.headers().firstValue("Retry-After").orElse(null);
        if (retryAfter != null) {
            long millis = parseRetryAfterMillis(retryAfter.trim());
            if (millis >= 0) {
                return millis > MAX_RETRY_AFTER_MILLIS ? -1 : millis;
            }
        }
        return backoffMillis(attempt);
    }

    /** {@code 250 ms * 2^attempt}, capped at 4 s, with 25% jitter so workers do not retry in step. */
    static long backoffMillis(int attempt) {
        long base = Math.min(MAX_BACKOFF_MILLIS, INITIAL_BACKOFF_MILLIS << Math.min(attempt, 20));
        long jitter = base / 4;
        return base - jitter + ThreadLocalRandom.current().nextLong(2 * jitter + 1);
    }

    /** Millis to wait, or -1 when the header is neither a delta nor an HTTP date. */
    static long parseRetryAfterMillis(String value) {
        try {
            return Long.parseLong(value) * 1000L;
        } catch (NumberFormatException e) {
            // an HTTP date, then
        }
        try {
            ZonedDateTime at = ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME);
            return Math.max(0, at.toInstant().toEpochMilli() - System.currentTimeMillis());
        } catch (DateTimeParseException e) {
            return -1;
        }
    }

    /** Sleeps in heartbeat slices so the stall detector sees the wait as progress. */
    private static void sleepWithHeartbeat(long millis, ParseContext context) throws IOException {
        long deadline = System.nanoTime() + millis * 1_000_000L;
        try {
            while (true) {
                long left = (deadline - System.nanoTime()) / 1_000_000L;
                if (left <= 0) {
                    return;
                }
                Thread.sleep(Math.min(left, HEARTBEAT_INTERVAL_MILLIS));
                ParseTimeout.checkpoint(context);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("HTTP retry interrupted", e);
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
