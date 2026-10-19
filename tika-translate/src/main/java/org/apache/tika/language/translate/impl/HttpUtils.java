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
package org.apache.tika.language.translate.impl;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Minimal REST helpers for the network translators.
 */
final class HttpUtils {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(60);

    private static final HttpClient CLIENT =
            HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
                    .connectTimeout(CONNECT_TIMEOUT).build();

    private HttpUtils() {
    }

    /**
     * @param nameValuePairs alternating parameter names and values; null values are skipped
     */
    static String get(String url, String... nameValuePairs) throws IOException {
        StringBuilder sb = new StringBuilder(url);
        char sep = '?';
        for (int i = 0; i < nameValuePairs.length; i += 2) {
            if (nameValuePairs[i + 1] == null) {
                continue;
            }
            sb.append(sep).append(encode(nameValuePairs[i])).append('=')
                    .append(encode(nameValuePairs[i + 1]));
            sep = '&';
        }
        return send(request(sb.toString()).GET().build()).body();
    }

    static String postJson(String url, String json) throws IOException {
        return send(request(url).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json, UTF_8)).build()).body();
    }

    static int head(String url) throws IOException {
        return send(request(url).method("HEAD", HttpRequest.BodyPublishers.noBody()).build())
                .statusCode();
    }

    private static HttpRequest.Builder request(String url) {
        return HttpRequest.newBuilder(URI.create(url)).timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json");
    }

    private static HttpResponse<String> send(HttpRequest request) throws IOException {
        try {
            return CLIENT.send(request, HttpResponse.BodyHandlers.ofString(UTF_8));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(e);
        }
    }

    private static String encode(String s) {
        return URLEncoder.encode(s, UTF_8);
    }
}
