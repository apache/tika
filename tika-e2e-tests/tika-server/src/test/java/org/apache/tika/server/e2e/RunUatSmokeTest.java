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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * End-to-end smoke test that drives the portable REST UAT script
 * ({@code release-tools/uat/run-uat.sh}) against the real fat-jar unpacked from
 * the distribution zip, and asserts the start-time security gating.
 *
 * Three things are verified:
 * <ol>
 *   <li>the default-mode REST surface + per-request-config 403 gating, by running
 *       run-uat.sh against a forked server and asserting exit 0;</li>
 *   <li>selecting the {@code pipes} endpoint without {@code allowPipes} makes the
 *       server refuse to start (TIKA-4764 secure-by-default);</li>
 *   <li>a carried-over alpha-1 config that still sets the removed
 *       {@code enableUnsecureFeatures} key fails fast at startup (fail-closed
 *       upgrade behavior) — locks in the current behavior so a future change is
 *       a deliberate decision, not an accident.</li>
 * </ol>
 *
 * Run with: mvn test -pl tika-e2e-tests/tika-server -Pe2e
 *
 * Skipped automatically on Windows (no bash) and when the distribution jar or
 * the UAT script cannot be located.
 */
@Tag("E2ETest")
public class RunUatSmokeTest {

    private static final Logger log = LoggerFactory.getLogger(RunUatSmokeTest.class);
    private static final long SERVER_STARTUP_TIMEOUT_MILLIS = 90_000;
    private static final long STARTUP_FAILURE_TIMEOUT_S = 90;
    /** Health-check polls root (/), which always returns 200 without requiring endpoint config. */
    private static final String HEALTH_PATH = "/";

    private static final boolean IS_WINDOWS =
            System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win");

    @Test
    void testRestUatPasses() throws Exception {
        Assumptions.assumeFalse(IS_WINDOWS, "run-uat.sh requires bash; skipping on Windows");
        Path serverJar = locateServerJar();
        Path uatScript = locateUatScript();
        Assumptions.assumeTrue(serverJar != null, "tika-server-standard-*.jar not found; build the dist first");
        Assumptions.assumeTrue(uatScript != null, "release-tools/uat/run-uat.sh not found");

        int port = findFreePort();
        String baseUrl = "http://localhost:" + port;
        Process server = startServer(serverJar, "-p", String.valueOf(port), "-h", "localhost");
        try {
            awaitServerStartup(server, baseUrl);

            ProcessBuilder pb = new ProcessBuilder("bash", uatScript.toString(), baseUrl);
            pb.redirectErrorStream(true);
            Process uat = pb.start();
            String output = drain(uat);
            boolean finished = uat.waitFor(120, TimeUnit.SECONDS);
            if (!finished) {
                uat.destroyForcibly();
                fail("run-uat.sh did not finish within 120s");
            }
            log.info("run-uat.sh output:\n{}", output);
            assertEquals(0, uat.exitValue(),
                    "run-uat.sh reported failures against " + baseUrl + ":\n" + output);
        } finally {
            stop(server);
        }
    }

    /**
     * The UAT's inference pass: release-tools/uat/MockInferenceServer.java stands in for a
     * hosted OCR model and an embedding model (started flaky, so every other request is
     * refused first), the server runs on uat-inference-config.json with the engine key taken
     * from the environment, and run-uat.sh's T40-T47 checks exercise the forked worker's
     * engine wiring. Media needs ffmpeg on the PATH: asserted when it is there, skipped by
     * the script when it is not.
     */
    @Test
    void testRestUatInferencePasses() throws Exception {
        Assumptions.assumeFalse(IS_WINDOWS, "run-uat.sh requires bash; skipping on Windows");
        Path serverJar = locateServerJar();
        Path uatScript = locateUatScript();
        Assumptions.assumeTrue(serverJar != null, "tika-server-standard-*.jar not found; build the dist first");
        Assumptions.assumeTrue(uatScript != null, "release-tools/uat/run-uat.sh not found");
        Path uatDir = uatScript.getParent();
        Path mockSource = uatDir.resolve("MockInferenceServer.java");
        Path configTemplate = uatDir.resolve("uat-inference-config.json");
        Assumptions.assumeTrue(Files.exists(mockSource) && Files.exists(configTemplate),
                "mock engine or inference config missing next to run-uat.sh");

        int mockPort = findFreePort();
        // --flaky only once the shipped client retries a 429 (TIKA-4912); before that the
        // refusals would fail the run for a reason this test is not about
        boolean clientRetries = clientRetries(serverJar);
        ProcessBuilder mockPb = clientRetries
                ? new ProcessBuilder("java", mockSource.toString(), String.valueOf(mockPort), "--flaky")
                : new ProcessBuilder("java", mockSource.toString(), String.valueOf(mockPort));
        mockPb.redirectErrorStream(true);
        Process mock = mockPb.start();
        drainAsync(mock);
        Process server = null;
        try {
            awaitHttp("http://localhost:" + mockPort + "/v1/models", mock, "mock inference server");
            int port = findFreePort();
            String baseUrl = "http://localhost:" + port;
            String json = Files.readString(configTemplate, UTF_8)
                    .replace("MOCK_BASE_URL", "http://localhost:" + mockPort);
            Path config = writeConfig("uat-inference", json);
            server = startServer(serverJar, java.util.Map.of("TIKA_UAT_MOCK_KEY", "uat-secret"),
                    "-p", String.valueOf(port), "-h", "localhost", "-c", config.toString());
            awaitServerStartup(server, baseUrl);

            ProcessBuilder pb = new ProcessBuilder("bash", uatScript.toString(), baseUrl);
            pb.environment().put("TIKA_UAT_INFERENCE", "1");
            pb.environment().put("TIKA_UAT_MOCK_URL", "http://localhost:" + mockPort);
            if (onPath("ffmpeg") && onPath("ffprobe")) {
                pb.environment().put("TIKA_UAT_REQUIRE_MEDIA", "1");
            }
            pb.redirectErrorStream(true);
            Process uat = pb.start();
            String output = drain(uat);
            boolean finished = uat.waitFor(180, TimeUnit.SECONDS);
            if (!finished) {
                uat.destroyForcibly();
                fail("run-uat.sh (inference) did not finish within 180s");
            }
            log.info("run-uat.sh (inference) output:\n{}", output);
            assertEquals(0, uat.exitValue(),
                    "run-uat.sh (inference) reported failures against " + baseUrl + ":\n" + output);
            if (clientRetries) {
                assertTrue(output.contains("PASS  T47"),
                        "the flaky mock's refusals must have been retried:\n" + output);
            }
        } finally {
            if (server != null) {
                stop(server);
            }
            mock.destroy();
            mock.waitFor(10, TimeUnit.SECONDS);
        }
    }

    /** Whether the server's HTTP client retries a 429: TikaHttpClient.build(int, int) exists. */
    private static boolean clientRetries(Path serverJar) {
        try (DirectoryStream<Path> jars = Files.newDirectoryStream(
                serverJar.getParent().resolve("lib"), "tika-http-jdk-*.jar")) {
            for (Path jar : jars) {
                try (java.util.jar.JarFile jf = new java.util.jar.JarFile(jar.toFile())) {
                    java.util.jar.JarEntry e = jf.getJarEntry("org/apache/tika/http/TikaHttpClient.class");
                    if (e == null) {
                        return false;
                    }
                    String bytes = new String(jf.getInputStream(e).readAllBytes(), StandardCharsets.ISO_8859_1);
                    return bytes.contains("DEFAULT_MAX_RETRIES");
                }
            }
        } catch (Exception e) {
            // fall through
        }
        return false;
    }

    private static boolean onPath(String tool) {
        try {
            Process p = new ProcessBuilder(tool, "-version").redirectErrorStream(true).start();
            p.getInputStream().readAllBytes();
            return p.waitFor(10, TimeUnit.SECONDS) && p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static void awaitHttp(String url, Process p, String what) throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        Instant deadline = Instant.now().plus(Duration.ofSeconds(30));
        while (Instant.now().isBefore(deadline)) {
            if (!p.isAlive()) {
                fail(what + " exited with " + p.exitValue());
            }
            try {
                HttpResponse<String> r = client.send(HttpRequest.newBuilder(URI.create(url))
                        .timeout(Duration.ofSeconds(2)).GET().build(),
                        HttpResponse.BodyHandlers.ofString());
                if (r.statusCode() == 200) {
                    return;
                }
            } catch (Exception e) {
                // not up yet
            }
            Thread.sleep(250);
        }
        fail(what + " did not answer at " + url + " within 30s");
    }

    @Test
    void testPipesEndpointWithoutAllowPipesRefusesToStart() throws Exception {
        Path serverJar = locateServerJar();
        Assumptions.assumeTrue(serverJar != null, "tika-server-standard-*.jar not found; build the dist first");
        int port = findFreePort();
        Path config = writeConfig("uat-pipes-no-flag",
                "{\"server\":{\"port\":" + port + ",\"host\":\"localhost\",\"endpoints\":[\"tika\",\"pipes\"]}}");

        StartupResult r = runUntilExit(serverJar, "-c", config.toString());
        assertNotEquals(0, r.exitCode,
                "server should refuse to start with pipes endpoint and no allowPipes; output:\n" + r.output);
        assertTrue(r.output.contains("allowPipes"),
                "startup failure should name 'allowPipes'; output:\n" + r.output);
    }

    @Test
    void testStaleEnableUnsecureFeaturesRefusesToStart() throws Exception {
        Path serverJar = locateServerJar();
        Assumptions.assumeTrue(serverJar != null, "tika-server-standard-*.jar not found; build the dist first");
        int port = findFreePort();
        // 'enableUnsecureFeatures' was the alpha-1 flag; removed in TIKA-4764. A carried-over
        // config must fail fast (fail-closed) rather than silently dropping the security-relevant key.
        Path config = writeConfig("uat-stale-unsecure",
                "{\"server\":{\"port\":" + port + ",\"host\":\"localhost\",\"enableUnsecureFeatures\":true}}");

        StartupResult r = runUntilExit(serverJar, "-c", config.toString());
        assertNotEquals(0, r.exitCode,
                "server should refuse to start with the removed enableUnsecureFeatures key; output:\n" + r.output);
        assertTrue(r.output.contains("enableUnsecureFeatures"),
                "startup failure should name the offending 'enableUnsecureFeatures' key; output:\n" + r.output);
    }

    // ----- helpers -----

    private Process forkServer(Path serverJar, String... args) throws Exception {
        return forkServer(serverJar, java.util.Map.of(), args);
    }

    private Process forkServer(Path serverJar, java.util.Map<String, String> env, String... args)
            throws Exception {
        String[] cmd = new String[args.length + 3];
        cmd[0] = "java";
        cmd[1] = "-jar";
        cmd[2] = serverJar.getFileName().toString();
        System.arraycopy(args, 0, cmd, 3, args.length);
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(serverJar.getParent().toFile());
        pb.environment().putAll(env);
        pb.redirectErrorStream(true);
        return pb.start();
    }

    private Process startServer(Path serverJar, String... args) throws Exception {
        return startServer(serverJar, java.util.Map.of(), args);
    }

    private Process startServer(Path serverJar, java.util.Map<String, String> env, String... args)
            throws Exception {
        Process p = forkServer(serverJar, env, args);
        drainAsync(p);
        return p;
    }

    /**
     * Forks the server and waits for it to exit on its own (used for the fail-fast config tests).
     * A single reader thread captures the merged stdout/stderr; do NOT also {@code drainAsync} the
     * same process, or the two readers race for the stream and the captured output is incomplete
     * (which made the start-time assertions flaky across platforms).
     */
    private StartupResult runUntilExit(Path serverJar, String... args) throws Exception {
        Process p = forkServer(serverJar, args);
        StringBuilder sb = new StringBuilder();
        Thread reader = new Thread(() -> {
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(p.getInputStream(), UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    synchronized (sb) {
                        sb.append(line).append('\n');
                    }
                }
            } catch (Exception e) {
                log.debug("stream closed", e);
            }
        });
        reader.setDaemon(true);
        reader.start();
        boolean exited = p.waitFor(STARTUP_FAILURE_TIMEOUT_S, TimeUnit.SECONDS);
        if (!exited) {
            // The config was supposed to be rejected at startup; if the server is still up, that's a failure.
            p.destroyForcibly();
            p.waitFor(15, TimeUnit.SECONDS);
            reader.join(5_000);
            synchronized (sb) {
                return new StartupResult(0, "server did NOT exit (started despite invalid config):\n" + sb);
            }
        }
        reader.join(5_000);
        synchronized (sb) {
            return new StartupResult(p.exitValue(), sb.toString());
        }
    }

    private static String readAll(Process p) {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                sb.append(line).append('\n');
            }
        } catch (Exception e) {
            log.debug("stream closed", e);
        }
        return sb.toString();
    }

    private static String drain(Process p) {
        return readAll(p);
    }

    private void drainAsync(Process p) {
        Thread t = new Thread(() -> {
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    log.info("tika-server: {}", line);
                }
            } catch (Exception e) {
                log.debug("Server output stream closed", e);
            }
        });
        t.setDaemon(true);
        t.start();
    }

    private void awaitServerStartup(Process server, String baseUrl) throws Exception {
        HttpClient pollClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        Instant deadline = Instant.now().plusMillis(SERVER_STARTUP_TIMEOUT_MILLIS);
        while (Instant.now().isBefore(deadline)) {
            if (!server.isAlive()) {
                throw new IllegalStateException(
                        "tika-server exited unexpectedly with code " + server.exitValue());
            }
            try {
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl + HEALTH_PATH))
                        .timeout(Duration.ofSeconds(5))
                        .GET()
                        .build();
                HttpResponse<Void> resp = pollClient.send(req, HttpResponse.BodyHandlers.discarding());
                if (resp.statusCode() == 200) {
                    log.info("tika-server ready at {}", baseUrl);
                    return;
                }
            } catch (Exception e) {
                log.debug("waiting for server: {}", e.getMessage());
            }
            Thread.sleep(1000);
        }
        throw new IllegalStateException("tika-server did not start within " + SERVER_STARTUP_TIMEOUT_MILLIS + " ms");
    }

    private void stop(Process server) throws Exception {
        if (server != null && server.isAlive()) {
            server.destroy();
            if (!server.waitFor(5, TimeUnit.SECONDS)) {
                server.destroyForcibly();
                server.waitFor(30, TimeUnit.SECONDS);
            }
        }
    }

    private Path writeConfig(String prefix, String json) throws Exception {
        Path f = Files.createTempFile(prefix, ".json");
        Files.write(f, json.getBytes(StandardCharsets.UTF_8));
        f.toFile().deleteOnExit();
        return f;
    }

    private static int findFreePort() throws Exception {
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        }
    }

    /** Locates the unpacked dist jar via {@code tika.server.home} (set by the -Pe2e profile). */
    private static Path locateServerJar() throws Exception {
        String serverHome = System.getProperty("tika.server.home");
        Path home;
        if (serverHome != null) {
            home = Paths.get(serverHome);
        } else {
            Path repoRoot = repoRoot();
            if (repoRoot == null) {
                return null;
            }
            home = repoRoot.resolve("tika-e2e-tests/tika-server/target/tika-server-dist");
        }
        if (!Files.isDirectory(home)) {
            return null;
        }
        try (DirectoryStream<Path> jars =
                     Files.newDirectoryStream(home, "tika-server-standard-*.jar")) {
            for (Path jar : jars) {
                return jar;
            }
        }
        return null;
    }

    private static Path locateUatScript() {
        Path repoRoot = repoRoot();
        if (repoRoot == null) {
            return null;
        }
        Path script = repoRoot.resolve("release-tools/uat/run-uat.sh");
        return Files.exists(script) ? script : null;
    }

    private static Path repoRoot() {
        Path p = Paths.get("").toAbsolutePath();
        while (p != null && !p.resolve("release-tools").toFile().isDirectory()) {
            p = p.getParent();
        }
        return p;
    }

    private static final class StartupResult {
        final int exitCode;
        final String output;

        StartupResult(int exitCode, String output) {
            this.exitCode = exitCode;
            this.output = output;
        }
    }
}
