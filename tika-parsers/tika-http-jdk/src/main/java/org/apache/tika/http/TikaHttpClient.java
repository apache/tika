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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.tika.exception.TikaException;

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
     *
     * @param url            target URL
     * @param jsonBody       request body (UTF-8 JSON)
     * @param headers        additional HTTP headers (e.g. {@code Authorization})
     * @param timeoutSeconds read timeout; {@code 0} uses the default timeout
     * @return response body string
     * @throws IOException    on network error
     * @throws TikaException  on non-2xx HTTP status
     */
    public String postJson(String url, String jsonBody, Map<String, String> headers,
                           int timeoutSeconds) throws IOException, TikaException {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(timeoutSeconds > 0
                        ? timeoutSeconds : defaultTimeoutSeconds))
                .header("Content-Type", JSON_CONTENT_TYPE)
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8));

        headers.forEach(builder::header);

        return send(builder.build());
    }

    /**
     * GET {@code url} and return the response body as a string.
     * Useful for health-check probes at init time.
     *
     * @param url            target URL
     * @param headers        additional HTTP headers
     * @param timeoutSeconds read timeout; {@code 0} uses the default timeout
     * @return response body string
     * @throws IOException    on network error
     * @throws TikaException  on non-2xx HTTP status
     */
    public String get(String url, Map<String, String> headers,
                      int timeoutSeconds) throws IOException, TikaException {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(timeoutSeconds > 0
                        ? timeoutSeconds : defaultTimeoutSeconds))
                .GET();

        headers.forEach(builder::header);

        return send(builder.build());
    }

    private String send(HttpRequest request) throws IOException, TikaException {
        try {
            HttpResponse<String> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new TikaException("HTTP " + response.statusCode()
                        + " from " + request.uri() + ": " + response.body());
            }
            return response.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("HTTP request interrupted: " + request.uri(), e);
        }
    }

    @Override
    public void close() {
        executor.shutdown();
    }
}
