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
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.AfterEach;
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
    private static final long SERVER_STARTUP_TIMEOUT_MS = 60_000;
    private static final String STATUS_PATH = "/status";

    private Process serverProcess;
    private int port;
    private String endPoint;

    @BeforeEach
    void startServer() throws Exception {
        port = findFreePort();
        endPoint = "http://localhost:" + port;

        String jarPath = System.getProperty("tika.server.jar");
        if (jarPath == null) {
            // fall back to conventional location relative to this module
            Path moduleDir = Paths.get("").toAbsolutePath();
            Path repoRoot = moduleDir;
            while (repoRoot != null && !repoRoot.resolve("tika-server").toFile().isDirectory()) {
                repoRoot = repoRoot.getParent();
            }
            if (repoRoot == null) {
                throw new IllegalStateException("Cannot locate tika root. Pass -Dtika.server.jar=/path/to/tika-server-standard.jar");
            }
            jarPath = repoRoot.resolve("tika-server/tika-server-standard/target")
                    .toAbsolutePath()
                    .toString() + "/tika-server-standard-" +
                    System.getProperty("tika.version", "4.0.0-SNAPSHOT") + ".jar";
        }

        log.info("Starting tika-server-standard from: {}", jarPath);
        ProcessBuilder pb = new ProcessBuilder(
                "java", "-jar", jarPath,
                "-p", String.valueOf(port),
                "-h", "localhost"
        );
        pb.redirectErrorStream(true);
        serverProcess = pb.start();

        // Drain output in background so the process doesn't block
        Thread drainThread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(serverProcess.getInputStream(), UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    log.debug("tika-server: {}", line);
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
            serverProcess.waitFor();
        }
    }

    @Test
    void testH2cStatusEndpoint() throws Exception {
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .build();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endPoint + STATUS_PATH))
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(UTF_8));

        assertEquals(200, response.statusCode(), "Expected 200 from /status");
        assertEquals(HttpClient.Version.HTTP_2, response.version(),
                "Expected HTTP/2 protocol; server may be missing http2-server on classpath");
        log.info("HTTP/2 h2c verified: {} {}", response.statusCode(), response.version());
    }

    @Test
    void testH2cParseEndpoint() throws Exception {
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .build();

        // Send a small plain-text document for parsing
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
        // Use HTTP/1.1 for the health-check poll so we don't depend on HTTP/2 during startup
        HttpClient pollClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(2))
                .build();
        HttpRequest pollRequest = HttpRequest.newBuilder()
                .uri(URI.create(endPoint + "/"))
                .GET()
                .build();

        Instant deadline = Instant.now().plusMillis(SERVER_STARTUP_TIMEOUT_MS);
        while (Instant.now().isBefore(deadline)) {
            try {
                HttpResponse<Void> resp = pollClient.send(pollRequest, HttpResponse.BodyHandlers.discarding());
                if (resp.statusCode() == 200) {
                    log.info("tika-server ready on port {}", port);
                    return;
                }
            } catch (Exception e) {
                log.debug("Waiting for server on port {} ...", port);
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
