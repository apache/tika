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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
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
import org.apache.tika.server.core.metrics.TikaServerMetrics;
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
        assertSample(body, "tika_pipes_worker_restarts_total",
                "pool=\"sync\",reason=\"oom\"", 1.0);
        assertSample(body, "tika_pipes_workers", "pool=\"sync\",state=\"idle\"", 2.0);
        assertSample(body, "tika_pipes_workers", "pool=\"sync\",state=\"busy\"", 0.0);
        assertSample(body, "tika_server_tasks_active", "", 0.0);
        assertTrue(body.contains("jvm_memory_used_bytes"), body);
        assertTrue(body.contains("tika_server_request_size_bytes_count{endpoint=\"rmeta\"}"), body);

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
        // The gate is the port: nothing may be listening on the one the metrics config names.
        assertThrows(IOException.class, () -> get(metricsEndPoint + MetricsServer.PATH),
                "a scrape listener came up with no metrics port configured");
    }

    /**
     * /async forks its own workers, separate from the sync pool's. Without a pool label and
     * a second binding, a crash in an async worker is counted nowhere.
     */
    @Test
    @Timeout(240)
    public void testBothWorkerPoolsAreCounted() throws Exception {
        startProcess(new String[]{"-config", getConfig("tika-config-server-async-metrics.json"),
                "--metricsPort", String.valueOf(metricsPort)});
        awaitServerStartup();
        assertEquals(200, rmeta(TEST_HELLO_WORLD).getStatus());

        String body = get(metricsEndPoint + MetricsServer.PATH).body();
        assertSample(body, "tika_pipes_worker_restarts_total", "pool=\"sync\",reason=\"oom\"", 0.0);
        assertSample(body, "tika_pipes_worker_restarts_total", "pool=\"async\",reason=\"oom\"", 0.0);
        assertSample(body, "tika_pipes_queue_depth", "pool=\"async\"", 0.0);
    }

    /** Routine restarts (max files, idle exit 24) must not be counted as crashes. */
    @Test
    @Timeout(240)
    public void testRoutineRestartReasons() throws Exception {
        startProcess(new String[]{"-config", getConfig("tika-config-server-metrics-restarts.json"),
                "--metricsPort", String.valueOf(metricsPort)});
        awaitServerStartup();
        for (int i = 0; i < 3; i++) {
            assertEquals(200, rmeta(TEST_HELLO_WORLD).getStatus());
        }
        // Past the idle socket timeout the fork exits 24 and is restarted by the next request.
        String body = awaitSample("tika_pipes_worker_restarts_total", "pool=\"sync\",reason=\"idle\"", 1.0);
        assertSample(body, "tika_pipes_worker_restarts_total", "pool=\"sync\",reason=\"max_files\"", 1.0);
        assertSample(body, "tika_pipes_worker_restarts_total", "pool=\"sync\",reason=\"crash\"", 0.0);
    }

    /** Timeout and crash are attributed per client; a 503 for either is a crash_503 rejection. */
    @Test
    @Timeout(240)
    public void testTimeoutAndCrashReasons() throws Exception {
        startProcess(new String[]{"-config", getConfig("tika-config-server-metrics-timeout.json"),
                "--metricsPort", String.valueOf(metricsPort)});
        awaitServerStartup();
        assertEquals(503, rmeta(TEST_HEAVY_HANG).getStatus());
        assertEquals(200, rmeta(TEST_HELLO_WORLD).getStatus());
        assertEquals(503, rmeta(TEST_SYSTEM_EXIT).getStatus());
        assertEquals(200, rmeta(TEST_HELLO_WORLD).getStatus());

        String body = get(metricsEndPoint + MetricsServer.PATH).body();
        assertSample(body, "tika_pipes_worker_restarts_total", "pool=\"sync\",reason=\"timeout\"", 1.0);
        assertSample(body, "tika_pipes_worker_restarts_total", "pool=\"sync\",reason=\"crash\"", 1.0);
        assertSample(body, "tika_pipes_worker_restarts_total", "pool=\"sync\",reason=\"oom\"", 0.0);
        assertSample(body, "tika_server_rejected_total", "reason=\"crash_503\"", 2.0);
    }

    /** Shared server: the client that saw the OOM marks it; the restarter must not overwrite it with crash. */
    @Test
    @Timeout(240)
    public void testSharedServerOomReason() throws Exception {
        startProcess(new String[]{"-config", getConfig("tika-config-server-metrics-shared.json"),
                "--metricsPort", String.valueOf(metricsPort)});
        awaitServerStartup();
        assertEquals(503, rmeta(TEST_OOM).getStatus());
        assertEquals(200, rmeta(TEST_HELLO_WORLD).getStatus());

        String body = get(metricsEndPoint + MetricsServer.PATH).body();
        assertSample(body, "tika_pipes_worker_restarts_total", "pool=\"sync\",reason=\"oom\"", 1.0);
        assertSample(body, "tika_pipes_worker_restarts_total", "pool=\"sync\",reason=\"crash\"", 0.0);
    }

    /** Polls (parse + scrape) until the sample reaches {@code expected}; returns the last body. */
    private String awaitSample(String name, String labels, double expected) throws Exception {
        long deadline = System.currentTimeMillis() + 30_000;
        String body;
        do {
            // Each parse resets the fork's idle clock, so sleep past the config's socketTimeoutMillis.
            Thread.sleep(3000);
            assertEquals(200, rmeta(TEST_HELLO_WORLD).getStatus());
            body = get(metricsEndPoint + MetricsServer.PATH).body();
            if (sample(body, name, labels) == expected) {
                return body;
            }
        } while (System.currentTimeMillis() < deadline);
        throw new AssertionError("timed out waiting for " + name + "{" + labels + "}=" + expected
                + " in:\n" + body);
    }

    /**
     * The explicit SLO boundaries, not micrometer's ~70-bucket percentile histogram.
     * Guards the cardinality decision: a stray publishPercentileHistogram() fails here.
     */
    @Test
    @Timeout(240)
    public void testDurationBucketsAreBounded() throws Exception {
        startProcess(new String[]{"-config", getConfig("tika-config-server-basic.json"),
                "--metricsPort", String.valueOf(metricsPort)});
        awaitServerStartup();
        assertEquals(200, rmeta(TEST_HELLO_WORLD).getStatus());

        String body = get(metricsEndPoint + MetricsServer.PATH).body();
        long buckets = body
                .lines()
                .filter(l -> l.startsWith("tika_server_requests_seconds_bucket{")
                        && l.contains("endpoint=\"rmeta\""))
                .count();
        int expected = TikaServerMetrics.DURATION_SLOS.length + 1;
        assertEquals(expected, buckets, "expected SLO buckets + Inf, got " + buckets + ":\n" + body);
    }

    private Response rmeta(String resource) {
        return WebClient.create(endPoint + RMETA_PATH)
                .accept("application/json")
                .put(ClassLoader.getSystemResourceAsStream(resource));
    }

    private static void assertSample(String body, String name, String labels, double expected) {
        assertEquals(expected, sample(body, name, labels), 0.0, name + "{" + labels + "}");
    }

    private static double sample(String body, String name, String labels) {
        String labelled = labels.isEmpty() ? "" : "\\{" + Pattern.quote(labels) + ",?\\}";
        Matcher m = Pattern.compile("^" + Pattern.quote(name) + labelled + " (\\S+)$",
                Pattern.MULTILINE).matcher(body);
        assertTrue(m.find(), "missing " + name + "{" + labels + "} in:\n" + body);
        return Double.parseDouble(m.group(1));
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
