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
package org.apache.tika.server.core.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import io.micrometer.core.instrument.Counter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class MetricsServerTest {

    private TikaServerMetrics metrics;
    private MetricsServer server;
    private String base;

    @BeforeEach
    public void setUp() throws Exception {
        metrics = new TikaServerMetrics();
        Counter.builder("tika_test_total").register(metrics.getRegistry()).increment(3);
        server = new MetricsServer("localhost", 0, metrics);
        server.start();
        base = "http://localhost:" + server.getPort();
    }

    @AfterEach
    public void tearDown() throws Exception {
        server.close();
        metrics.close();
    }

    @Test
    public void testScrape() throws Exception {
        HttpResponse<String> response = get(base + MetricsServer.PATH);
        assertEquals(200, response.statusCode());
        assertTrue(response.headers().firstValue("Content-Type").orElse("")
                .startsWith("text/plain; version=0.0.4"), response.headers().toString());
        String body = response.body();
        assertTrue(body.contains("# TYPE tika_test_total counter"), body);
        assertTrue(body.contains("tika_test_total 3.0"), body);
        assertEquals(200, get(base + MetricsServer.PATH + "/").statusCode());
        HttpResponse<String> head = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create(base + MetricsServer.PATH))
                        .method("HEAD", HttpRequest.BodyPublishers.noBody()).build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, head.statusCode());
        assertEquals("", head.body());
    }

    @Test
    public void testOnlyMetricsPathIsServed() throws Exception {
        assertEquals(404, get(base + "/tika").statusCode());
        assertEquals(404, get(base + "/").statusCode());
        HttpResponse<String> post = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create(base + MetricsServer.PATH))
                        .POST(HttpRequest.BodyPublishers.noBody()).build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(405, post.statusCode());
    }

    private static HttpResponse<String> get(String url) throws Exception {
        return HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create(url)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }
}
