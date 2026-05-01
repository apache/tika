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
package org.apache.tika.pipes.filesystem;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import lombok.extern.slf4j.Slf4j;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import org.apache.tika.FetchAndParseReply;
import org.apache.tika.FetchAndParseRequest;
import org.apache.tika.SaveFetcherReply;
import org.apache.tika.SaveFetcherRequest;
import org.apache.tika.TikaGrpc;
import org.apache.tika.pipes.ExternalTestBase;
import org.apache.tika.pipes.fetcher.fs.FileSystemFetcherConfig;

/**
 * Tests per-request ParseContext configuration via FetchAndParseRequest.parse_context_json.
 *
 * Uses the Ignite ConfigStore so that fetchers registered via saveFetcher are visible
 * to both the gRPC server JVM and the forked PipesServer JVM.
 *
 * Verifies that clients can override any parse context component on a per-request basis
 * by providing a JSON object with component names as keys.
 * Example: {"basic-content-handler-factory": {"type": "HTML"}}
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Slf4j
@Tag("E2ETest")
@DisabledOnOs(value = OS.WINDOWS, disabledReason = "exec:exec classpath exceeds Windows CreateProcess command-line length limit")
class HandlerTypeTest {

    private static final File TEST_FOLDER = ExternalTestBase.TEST_FOLDER;
    private static final int GRPC_PORT = Integer.parseInt(System.getProperty("tika.e2e.grpcPort", "50052"));

    private static Process localGrpcProcess;

    @BeforeAll
    void setup() throws Exception {
        try {
            killProcessOnPort(GRPC_PORT);
            killProcessOnPort(3344);
            killProcessOnPort(10800);
        } catch (Exception e) {
            log.debug("No orphaned processes to clean up: {}", e.getMessage());
        }

        ExternalTestBase.copyTestFixtures();
        startLocalGrpcServer();
    }

    @AfterAll
    void teardown() {
        if (localGrpcProcess != null) {
            log.info("Stopping local gRPC server and child processes");
            localGrpcProcess.destroy();
            try {
                if (!localGrpcProcess.waitFor(10, TimeUnit.SECONDS)) {
                    localGrpcProcess.destroyForcibly();
                    localGrpcProcess.waitFor(5, TimeUnit.SECONDS);
                }
                Thread.sleep(2000);
                killProcessOnPort(GRPC_PORT);
                killProcessOnPort(3344);
                killProcessOnPort(10800);
            } catch (Exception e) {
                log.debug("Error during teardown: {}", e.getMessage());
            }
            log.info("Local gRPC server stopped");
        }
    }

    private static void startLocalGrpcServer() throws Exception {
        log.info("Starting local tika-grpc server with Ignite config for HandlerType test");

        Path currentDir = Path.of("").toAbsolutePath();
        Path tikaRootDir = currentDir;
        while (tikaRootDir != null &&
               !(Files.exists(tikaRootDir.resolve("tika-grpc")) &&
                 Files.exists(tikaRootDir.resolve("tika-e2e-tests")))) {
            tikaRootDir = tikaRootDir.getParent();
        }
        if (tikaRootDir == null) {
            throw new IllegalStateException("Cannot find tika root directory. Current dir: " + currentDir);
        }

        Path tikaGrpcDir = tikaRootDir.resolve("tika-grpc");
        Path configFile = Path.of("src/test/resources/tika-config-ignite-handlertype.json").toAbsolutePath();
        if (!Files.exists(configFile)) {
            throw new IllegalStateException("Config file not found: " + configFile);
        }

        log.info("tika-grpc dir: {}", tikaGrpcDir);
        log.info("Config file: {}", configFile);

        String javaHome = System.getProperty("java.home");
        boolean isWindows = System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win");
        String javaCmd = javaHome + (isWindows ? "\\bin\\java.exe" : "/bin/java");
        String mvnCmd = tikaRootDir.resolve(isWindows ? "mvnw.cmd" : "mvnw").toString();

        ProcessBuilder pb = new ProcessBuilder(
            mvnCmd,
            "exec:exec",
            "-Dexec.executable=" + javaCmd,
            "-Dexec.args=" +
                "--add-opens=java.base/java.lang=ALL-UNNAMED " +
                "--add-opens=java.base/java.lang.invoke=ALL-UNNAMED " +
                "--add-opens=java.base/java.lang.reflect=ALL-UNNAMED " +
                "--add-opens=java.base/java.io=ALL-UNNAMED " +
                "--add-opens=java.base/java.nio=ALL-UNNAMED " +
                "--add-opens=java.base/java.math=ALL-UNNAMED " +
                "--add-opens=java.base/java.util=ALL-UNNAMED " +
                "--add-opens=java.base/java.util.concurrent=ALL-UNNAMED " +
                "--add-opens=java.base/java.util.concurrent.atomic=ALL-UNNAMED " +
                "--add-opens=java.base/java.util.concurrent.locks=ALL-UNNAMED " +
                "--add-opens=java.base/java.time=ALL-UNNAMED " +
                "--add-opens=java.base/jdk.internal.misc=ALL-UNNAMED " +
                "--add-opens=java.base/jdk.internal.access=ALL-UNNAMED " +
                "--add-opens=java.base/sun.nio.ch=ALL-UNNAMED " +
                "--add-opens=java.management/com.sun.jmx.mbeanserver=ALL-UNNAMED " +
                "--add-opens=jdk.management/com.sun.management.internal=ALL-UNNAMED " +
                "-Dio.netty.tryReflectionSetAccessible=true " +
                "-Dignite.work.dir=\"" + tikaGrpcDir.resolve("target/ignite-work-handlertype") + "\" " +
                "-classpath %classpath " +
                "org.apache.tika.pipes.grpc.TikaGrpcServer " +
                "-c \"" + configFile + "\" " +
                "-p " + GRPC_PORT
        );

        pb.directory(tikaGrpcDir.toFile());
        pb.redirectErrorStream(true);
        pb.redirectOutput(ProcessBuilder.Redirect.PIPE);

        localGrpcProcess = pb.start();

        final boolean[] igniteStarted = {false};
        Thread logThread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(localGrpcProcess.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    log.info("tika-grpc: {}", line);
                    if (line.contains("Ignite server started") ||
                        (line.contains("Table") && line.contains("created successfully")) ||
                        line.contains("Server started, listening on")) {
                        synchronized (igniteStarted) {
                            igniteStarted[0] = true;
                            igniteStarted.notifyAll();
                        }
                    }
                }
            } catch (IOException e) {
                log.error("Error reading server output", e);
            }
        });
        logThread.setDaemon(true);
        logThread.start();

        try {
            Awaitility.await()
                .atMost(java.time.Duration.ofSeconds(180))
                .pollInterval(java.time.Duration.ofSeconds(2))
                .until(() -> {
                    synchronized (igniteStarted) {
                        if (!igniteStarted[0]) {
                            return false;
                        }
                    }
                    ManagedChannel testChannel = ManagedChannelBuilder
                        .forAddress("localhost", GRPC_PORT)
                        .usePlaintext()
                        .build();
                    try {
                        io.grpc.health.v1.HealthGrpc.HealthBlockingStub healthStub =
                            io.grpc.health.v1.HealthGrpc.newBlockingStub(testChannel)
                                .withDeadlineAfter(2, TimeUnit.SECONDS);
                        io.grpc.health.v1.HealthCheckResponse response = healthStub.check(
                            io.grpc.health.v1.HealthCheckRequest.getDefaultInstance());
                        return response.getStatus() ==
                            io.grpc.health.v1.HealthCheckResponse.ServingStatus.SERVING;
                    } catch (io.grpc.StatusRuntimeException e) {
                        if (e.getStatus().getCode() == io.grpc.Status.Code.UNIMPLEMENTED) {
                            return true;
                        }
                        return false;
                    } catch (Exception e) {
                        return false;
                    } finally {
                        testChannel.shutdown();
                        testChannel.awaitTermination(1, TimeUnit.SECONDS);
                    }
                });
        } catch (org.awaitility.core.ConditionTimeoutException e) {
            if (localGrpcProcess.isAlive()) {
                localGrpcProcess.destroyForcibly();
            }
            throw new RuntimeException("tika-grpc server with Ignite failed to start within timeout", e);
        }

        log.info("HandlerType test server ready on port {}", GRPC_PORT);
    }

    private ManagedChannel getManagedChannel() {
        return ManagedChannelBuilder
            .forAddress("localhost", GRPC_PORT)
            .usePlaintext()
            .maxInboundMessageSize(160 * 1024 * 1024)
            .build();
    }

    private static void killProcessOnPort(int port) throws IOException, InterruptedException {
        ProcessBuilder findPb = new ProcessBuilder("lsof", "-ti", ":" + port);
        findPb.redirectErrorStream(true);
        Process findProcess = findPb.start();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(findProcess.getInputStream(), StandardCharsets.UTF_8))) {
            String pidStr = reader.readLine();
            if (pidStr != null && !pidStr.trim().isEmpty()) {
                long pid = Long.parseLong(pidStr.trim());
                long myPid = ProcessHandle.current().pid();
                if (pid == myPid || isParentProcess(pid)) {
                    return;
                }
                String cmdLine = ProcessHandle.of(pid)
                    .flatMap(h -> h.info().commandLine())
                    .orElse("");
                if (!cmdLine.contains("tika") && !cmdLine.contains("TikaGrpc") && !cmdLine.contains("ignite")) {
                    log.debug("Skipping kill of PID {} on port {} — not a tika/ignite process", pid, port);
                    return;
                }
                log.info("Killing tika/ignite process {} on port {}", pid, port);
                new ProcessBuilder("kill", String.valueOf(pid)).start().waitFor(2, TimeUnit.SECONDS);
                Thread.sleep(1000);
                new ProcessBuilder("kill", "-9", String.valueOf(pid)).start().waitFor(2, TimeUnit.SECONDS);
            }
        }
        findProcess.waitFor(2, TimeUnit.SECONDS);
    }

    private static boolean isParentProcess(long pid) {
        try {
            ProcessHandle current = ProcessHandle.current();
            while (current.parent().isPresent()) {
                current = current.parent().get();
                if (current.pid() == pid) {
                    return true;
                }
            }
        } catch (Exception e) {
            log.debug("Error checking parent process", e);
        }
        return false;
    }

    @Test
    void testParseContextJson() throws Exception {
        String fetcherId = "handlerTypeFetcher";
        ManagedChannel channel = getManagedChannel();
        try {
            TikaGrpc.TikaBlockingStub blockingStub = TikaGrpc.newBlockingStub(channel);

            FileSystemFetcherConfig config = new FileSystemFetcherConfig();
            config.setBasePath(TEST_FOLDER.getAbsolutePath());

            SaveFetcherReply saveReply = blockingStub.saveFetcher(SaveFetcherRequest.newBuilder()
                    .setFetcherId(fetcherId)
                    .setFetcherClass("org.apache.tika.pipes.fetcher.fs.FileSystemFetcher")
                    .setFetcherConfigJson(ExternalTestBase.OBJECT_MAPPER.writeValueAsString(config))
                    .build());
            log.info("Fetcher created: {}", saveReply.getFetcherId());

            // Parse sample.html requesting HTML output
            FetchAndParseReply htmlReply = blockingStub.fetchAndParse(FetchAndParseRequest.newBuilder()
                    .setFetcherId(fetcherId)
                    .setFetchKey("sample.html")
                    .setParseContextJson("{\"basic-content-handler-factory\": {\"type\": \"HTML\"}}")
                    .build());

            log.info("HTML parse status: {}", htmlReply.getStatus());
            Assertions.assertEquals("PARSE_SUCCESS", htmlReply.getStatus(),
                    "Parse should succeed with HTML handler type");

            String htmlContent = htmlReply.getFieldsMap().get("X-TIKA:content");
            Assertions.assertNotNull(htmlContent, "Content should be present in HTML response");
            log.info("HTML content (first 200 chars): {}", htmlContent.substring(0, Math.min(200, htmlContent.length())));
            Assertions.assertTrue(
                    htmlContent.contains("<html") || htmlContent.contains("<body") || htmlContent.contains("<p"),
                    "HTML handler should produce HTML markup, got: " + htmlContent);

            // Parse the same file requesting plain text — expect no HTML tags
            FetchAndParseReply textReply = blockingStub.fetchAndParse(FetchAndParseRequest.newBuilder()
                    .setFetcherId(fetcherId)
                    .setFetchKey("sample.html")
                    .setParseContextJson("{\"basic-content-handler-factory\": {\"type\": \"TEXT\"}}")
                    .build());

            log.info("Text parse status: {}", textReply.getStatus());
            Assertions.assertEquals("PARSE_SUCCESS", textReply.getStatus(),
                    "Parse should succeed with TEXT handler type");

            String textContent = textReply.getFieldsMap().get("X-TIKA:content");
            Assertions.assertNotNull(textContent, "Content should be present in text response");
            log.info("Text content (first 200 chars): {}", textContent.substring(0, Math.min(200, textContent.length())));
            Assertions.assertFalse(
                    textContent.contains("<html") || textContent.contains("<body"),
                    "TEXT handler should not produce HTML tags, got: " + textContent);

            Assertions.assertNotEquals(htmlContent, textContent,
                    "HTML and TEXT outputs should differ for the same document");

        } finally {
            channel.shutdown();
            try {
                if (!channel.awaitTermination(5, TimeUnit.SECONDS)) {
                    channel.shutdownNow();
                }
            } catch (InterruptedException e) {
                channel.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
}
