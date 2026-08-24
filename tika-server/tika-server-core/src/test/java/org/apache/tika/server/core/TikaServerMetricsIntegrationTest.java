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
package org.apache.tika.server.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.ws.rs.core.Response;
import org.apache.cxf.jaxrs.client.WebClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import org.apache.tika.server.core.metrics.MetricsServer;
import org.apache.tika.utils.ProcessUtils;

/**
 * Runs the real server process with {@code --metricsPort} and checks the scrape output
 * and the port isolation both ways.
 */
public class TikaServerMetricsIntegrationTest extends IntegrationTestBase {

    private final int metricsPort = TestPortAllocator.findFreePort();
    private final String metricsEndPoint = "http://localhost:" + metricsPort;

    @Test
    @Timeout(120)
    public void testScrapeAfterParsesAndWorkerRestart() throws Exception {
        startProcess(new String[]{"-config", getConfig("tika-config-server-basic.json"),
                "--metricsPort", String.valueOf(metricsPort)});
        awaitServerStartup();

        assertEquals(200, rmeta(TEST_HELLO_WORLD).getStatus());
        assertEquals(503, rmeta(TEST_OOM).getStatus());
        // The OOM'd worker is restarted on its next use.
        assertEquals(200, rmeta(TEST_HELLO_WORLD).getStatus());
        assertEquals(404, WebClient.create(endPoint + "/no-such-path").get().getStatus());

        HttpResponse<String> scrape = get(metricsEndPoint + MetricsServer.PATH);
        assertEquals(200, scrape.statusCode());
        assertTrue(scrape.headers().firstValue("Content-Type").orElse("")
                .startsWith("text/plain; version=0.0.4"));
        String body = scrape.body();

        assertSample(body, "tika_server_requests_seconds_count",
                "endpoint=\"rmeta\",method=\"PUT\",status=\"2xx\"", 2.0);
        assertSample(body, "tika_server_requests_seconds_count",
                "endpoint=\"rmeta\",method=\"PUT\",status=\"5xx\"", 1.0);
        assertSample(body, "tika_server_requests_seconds_count",
                "endpoint=\"unmatched\",method=\"GET\",status=\"4xx\"", 1.0);
        assertSample(body, "tika_server_rejected_total", "reason=\"crash_503\"", 1.0);
        assertSample(body, "tika_pipes_worker_restarts_total", "reason=\"oom\"", 1.0);
        assertSample(body, "tika_pipes_workers", "state=\"idle\"", 2.0);
        assertSample(body, "tika_pipes_workers", "state=\"busy\"", 0.0);
        assertTrue(body.contains("tika_server_tasks_started_total"), body);
        assertTrue(body.contains("jvm_memory_used_bytes"), body);
        assertTrue(body.contains("tika_server_request_size_bytes_count{endpoint=\"rmeta\"}"), body);
        assertTrue(body.contains("jetty_threads_"), "CXF request pool metrics missing:\n" + body);

        // Isolation both ways.
        assertEquals(404, WebClient.create(endPoint + MetricsServer.PATH).get().getStatus());
        assertEquals(404, WebClient.create(metricsEndPoint + RMETA_PATH)
                .accept("application/json")
                .put(ClassLoader.getSystemResourceAsStream(TEST_HELLO_WORLD)).getStatus());
    }

    @Test
    @Timeout(120)
    public void testOffByDefault() throws Exception {
        startProcess(new String[]{"-config", getConfig("tika-config-server-basic.json")});
        awaitServerStartup();
        assertEquals(200, rmeta(TEST_HELLO_WORLD).getStatus());
        assertEquals(404, WebClient.create(endPoint + MetricsServer.PATH).get().getStatus());
    }

    private Response rmeta(String resource) {
        return WebClient.create(endPoint + RMETA_PATH)
                .accept("application/json")
                .put(ClassLoader.getSystemResourceAsStream(resource));
    }

    private static void assertSample(String body, String name, String labels, double expected) {
        Matcher m = Pattern.compile("^" + Pattern.quote(name) + "\\{" + Pattern.quote(labels)
                + ",?\\} (\\S+)$", Pattern.MULTILINE).matcher(body);
        assertTrue(m.find(), "missing " + name + "{" + labels + "} in:\n" + body);
        assertEquals(expected, Double.parseDouble(m.group(1)), 0.0, name + "{" + labels + "}");
    }

    private static HttpResponse<String> get(String url) throws Exception {
        return HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create(url)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private String getConfig(String configName) {
        try {
            return ProcessUtils.escapeCommandLine(Paths
                    .get(TikaServerMetricsIntegrationTest.class
                            .getResource("/configs/" + configName)
                            .toURI())
                    .toAbsolutePath()
                    .toString());
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }
}
