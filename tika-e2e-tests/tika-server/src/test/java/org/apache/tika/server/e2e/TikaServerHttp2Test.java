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
package org.apache.tika.server.e2e;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * End-to-end test verifying that tika-server-standard supports HTTP/2 (h2c cleartext).
 *
 * Starts the real fat-jar, sends a request using Java's HttpClient configured for HTTP/2,
 * and asserts the response was served over HTTP/2. This validates the runtime classpath
 * has the Jetty http2-server jar and CXF negotiates h2c correctly.
 *
 * Run with: mvn test -pl tika-e2e-tests/tika-server -Pe2e
 *
 * Inspired by Lawrence Moorehead's original contribution (elemdisc/tika PR#1, TIKA-4679).
 */
@Tag("E2ETest")
public class TikaServerHttp2Test {

    private static final Logger log = LoggerFactory.getLogger(TikaServerHttp2Test.class);
    private static final long SERVER_STARTUP_TIMEOUT_MS = 90_000;
    /** Health-check polls root (/), which always returns 200 without requiring endpoint config. */
    private static final String HEALTH_PATH = "/";

    private Process serverProcess;
    private int port;
    private String endPoint;

    @BeforeEach
    void startServer() throws Exception {
        port = findFreePort();
        endPoint = "http://localhost:" + port;

        String serverHome = System.getProperty("tika.server.home");
        if (serverHome == null) {
            // fall back to conventional location relative to this module
            Path repoRoot = Paths.get("").toAbsolutePath();
            while (repoRoot != null && !repoRoot.resolve("tika-server").toFile().isDirectory()) {
                repoRoot = repoRoot.getParent();
            }
            if (repoRoot == null) {
                throw new IllegalStateException("Cannot locate tika root. Pass -Dtika.server.home=/path/to/extracted-assembly");
            }
            serverHome = repoRoot.resolve("tika-e2e-tests/tika-server/target/tika-server-dist").toAbsolutePath().toString();
        }

        Path serverJar = Paths.get(serverHome, "tika-server.jar");
        Assumptions.assumeTrue(Files.exists(serverJar),
                "tika-server.jar not found at " + serverJar + "; skipping HTTP/2 e2e test. " +
                "Build with: mvn package -pl tika-server/tika-server-standard && " +
                "mvn test -pl tika-e2e-tests/tika-server -Pe2e");

        log.info("Starting tika-server from: {}", serverJar);
        ProcessBuilder pb = new ProcessBuilder(
                "java", "-jar", "tika-server.jar",
                "-p", String.valueOf(port),
                "-h", "localhost"
        );
        pb.directory(Paths.get(serverHome).toFile());
        pb.redirectErrorStream(true);
        serverProcess = pb.start();

        // Drain output in background so the process doesn't block
        Thread drainThread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(serverProcess.getInputStream(), UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    log.info("tika-server: {}", line);
                }
            } catch (Exception e) {
                log.debug("Server output stream closed", e);
            }
        });
        drainThread.setDaemon(true);
        drainThread.start();

        awaitServerStartup();
    }

    @AfterEach
    void stopServer() throws Exception {
        if (serverProcess != null && serverProcess.isAlive()) {
            serverProcess.destroy();
            if (!serverProcess.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)) {
                serverProcess.destroyForcibly();
                serverProcess.waitFor(30, java.util.concurrent.TimeUnit.SECONDS);
            }
        }
    }

    @Test
    void testH2cTikaEndpoint() throws Exception {
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .build();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endPoint + "/tika"))
                .header("Accept", "text/plain")
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(UTF_8));

        assertEquals(200, response.statusCode(), "Expected 200 from /tika");
        assertEquals(HttpClient.Version.HTTP_2, response.version(),
                "Expected HTTP/2 protocol; server may be missing http2-server on classpath");
        log.info("HTTP/2 h2c verified: {} {}", response.statusCode(), response.version());
    }

    @Test
    void testH2cParseEndpoint() throws Exception {
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .build();

        // First: GET / to negotiate h2c upgrade, establishing an HTTP/2 connection
        HttpRequest warmup = HttpRequest.newBuilder()
                .uri(URI.create(endPoint + "/"))
                .GET()
                .build();
        httpClient.send(warmup, HttpResponse.BodyHandlers.discarding());

        // Now PUT /tika — the existing HTTP/2 connection is reused
        byte[] body = "Hello, HTTP/2 world!".getBytes(UTF_8);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endPoint + "/tika"))
                .header("Content-Type", "text/plain")
                .PUT(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(UTF_8));

        assertEquals(200, response.statusCode(), "Expected 200 from /tika");
        assertEquals(HttpClient.Version.HTTP_2, response.version(),
                "Expected HTTP/2 protocol on /tika endpoint");
        log.info("HTTP/2 parse endpoint verified: {} bytes returned over {}", response.body().length(), response.version());
    }

    private void awaitServerStartup() throws Exception {
        // Use HTTP/1.1 for the health-check poll so we don't depend on HTTP/2 during startup.
        // Both connectTimeout and request timeout are set to avoid hanging when Jetty has bound
        // the port but CXF has not yet finished initializing (accepts TCP but doesn't respond).
        HttpClient pollClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        Instant deadline = Instant.now().plusMillis(SERVER_STARTUP_TIMEOUT_MS);
        while (Instant.now().isBefore(deadline)) {
            if (!serverProcess.isAlive()) {
                throw new IllegalStateException(
                        "tika-server process exited unexpectedly with code " + serverProcess.exitValue());
            }
            try {
                HttpRequest pollRequest = HttpRequest.newBuilder()
                        .uri(URI.create(endPoint + HEALTH_PATH))
                        .timeout(Duration.ofSeconds(5))
                        .GET()
                        .build();
                HttpResponse<Void> resp = pollClient.send(pollRequest, HttpResponse.BodyHandlers.discarding());
                if (resp.statusCode() == 200) {
                    log.info("tika-server ready on port {}", port);
                    return;
                }
                log.debug("Server returned {} on {}; still waiting...", resp.statusCode(), HEALTH_PATH);
            } catch (Exception e) {
                log.debug("Waiting for server on port {}: {}", port, e.getMessage());
            }
            Thread.sleep(1000);
        }
        throw new IllegalStateException("tika-server did not start within " + SERVER_STARTUP_TIMEOUT_MS + " ms");
    }

    private static int findFreePort() throws Exception {
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        }
    }
}
