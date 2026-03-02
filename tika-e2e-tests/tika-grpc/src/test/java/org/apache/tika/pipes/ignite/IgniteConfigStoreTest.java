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
package org.apache.tika.pipes.ignite;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.testcontainers.containers.DockerComposeContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Testcontainers;

import org.apache.tika.FetchAndParseReply;
import org.apache.tika.FetchAndParseRequest;
import org.apache.tika.SaveFetcherReply;
import org.apache.tika.SaveFetcherRequest;
import org.apache.tika.TikaGrpc;
import org.apache.tika.pipes.ExternalTestBase;
import org.apache.tika.pipes.fetcher.fs.FileSystemFetcherConfig;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Testcontainers
@Slf4j
@Tag("E2ETest")
@DisabledOnOs(value = OS.WINDOWS, disabledReason = "Windows classpath length limit (CreateProcess error=206) exceeded by exec:exec with full Tika classpath")
class IgniteConfigStoreTest {
    
    private static final int MAX_STARTUP_TIMEOUT = ExternalTestBase.MAX_STARTUP_TIMEOUT;
    private static final File TEST_FOLDER = ExternalTestBase.TEST_FOLDER;
    private static final boolean USE_LOCAL_SERVER = Boolean.parseBoolean(System.getProperty("tika.e2e.useLocalServer", "true"));
    private static final int GRPC_PORT = Integer.parseInt(System.getProperty("tika.e2e.grpcPort", "50052"));
    
    private static DockerComposeContainer<?> igniteComposeContainer;
    private static Process localGrpcProcess;
    
    @BeforeAll
    static void setupIgnite() throws Exception {
        if (USE_LOCAL_SERVER) {
            try {
                killProcessOnPort(GRPC_PORT);
                killProcessOnPort(3344);
                killProcessOnPort(10800);
            } catch (Exception e) {
                log.debug("No orphaned processes to clean up");
            }
        }
        
        if (!TEST_FOLDER.exists() || TEST_FOLDER.listFiles().length == 0) {
            if (Boolean.parseBoolean(System.getProperty("tika.e2e.useGovdocs", "false"))) {
                ExternalTestBase.downloadAndUnzipGovdocs1(ExternalTestBase.GOV_DOCS_FROM_IDX, ExternalTestBase.GOV_DOCS_TO_IDX);
            } else {
                ExternalTestBase.copyTestFixtures();
            }
        }
        
        if (USE_LOCAL_SERVER) {
            startLocalGrpcServer();
        } else {
            startDockerGrpcServer();
        }
    }
    
    private static void startLocalGrpcServer() throws Exception {
        log.info("Starting local tika-grpc server using Maven");
        
        Path currentDir = Path.of("").toAbsolutePath();
        Path tikaRootDir = currentDir;
        
        while (tikaRootDir != null && 
               !(Files.exists(tikaRootDir.resolve("tika-grpc")) && 
                 Files.exists(tikaRootDir.resolve("tika-e2e-tests")))) {
            tikaRootDir = tikaRootDir.getParent();
        }
        
        if (tikaRootDir == null) {
            throw new IllegalStateException("Cannot find tika root directory. " +
                "Current dir: " + currentDir + ". " +
                "Please run from within the tika project.");
        }
        
        Path tikaGrpcDir = tikaRootDir.resolve("tika-grpc");
        if (!Files.exists(tikaGrpcDir)) {
            throw new IllegalStateException("Cannot find tika-grpc directory at: " + tikaGrpcDir);
        }
        
        String configFileName = "tika-config-ignite-local.json";
        Path configFile = Path.of("src/test/resources/" + configFileName).toAbsolutePath();
        
        if (!Files.exists(configFile)) {
            throw new IllegalStateException("Config file not found: " + configFile);
        }
        
        log.info("Tika root: {}", tikaRootDir);
        log.info("Using tika-grpc from: {}", tikaGrpcDir);
        log.info("Using config file: {}", configFile);
        
        // Use mvn exec:exec to run as external process (not exec:java which breaks ServiceLoader)
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
                "-Dignite.work.dir=\"" + tikaGrpcDir.resolve("target/ignite-work") + "\" " +
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
            try (java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(localGrpcProcess.getInputStream(), java.nio.charset.StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    log.info("tika-grpc: {}", line);
                    
                    if (line.contains("Ignite server started") ||
                        line.contains("Table") && line.contains("created successfully") ||
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
            org.awaitility.Awaitility.await()
                .atMost(java.time.Duration.ofSeconds(180))
                .pollInterval(java.time.Duration.ofSeconds(2))
                .until(() -> {
                    boolean igniteReady;
                    synchronized (igniteStarted) {
                        igniteReady = igniteStarted[0];
                    }
                    
                    if (!igniteReady) {
                        log.debug("Waiting for Ignite to start...");
                        return false;
                    }
                    
                    try {
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
                            
                            boolean serving = response.getStatus() == 
                                io.grpc.health.v1.HealthCheckResponse.ServingStatus.SERVING;
                            
                            if (serving) {
                                log.info("gRPC server is healthy and serving!");
                                return true;
                            } else {
                                log.debug("gRPC server responding but not serving yet: {}", response.getStatus());
                                return false;
                            }
                        } finally {
                            testChannel.shutdown();
                            testChannel.awaitTermination(1, TimeUnit.SECONDS);
                        }
                    } catch (io.grpc.StatusRuntimeException e) {
                        if (e.getStatus().getCode() == io.grpc.Status.Code.UNIMPLEMENTED) {
                            // Health check not implemented, just verify channel works
                            log.info("Health check not available, assuming server is ready");
                            return true;
                        }
                        log.debug("gRPC server not ready yet: {}", e.getMessage());
                        return false;
                    } catch (Exception e) {
                        log.debug("gRPC server not ready yet: {}", e.getMessage());
                        return false;
                    }
                });
            
            log.info("Both gRPC server and Ignite are ready!");
        } catch (org.awaitility.core.ConditionTimeoutException e) {
            if (localGrpcProcess.isAlive()) {
                localGrpcProcess.destroyForcibly();
            }
            throw new RuntimeException("Local gRPC server or Ignite failed to start within timeout", e);
        }
        
        log.info("Local tika-grpc server started successfully on port {}", GRPC_PORT);
    }
    
    
    private static void startDockerGrpcServer() {
        String composeFilePath = System.getProperty("tika.docker.compose.ignite.file");
        if (composeFilePath == null || composeFilePath.isBlank()) {
            throw new IllegalStateException(
                    "Docker Compose mode requires system property 'tika.docker.compose.ignite.file' " +
                    "pointing to a valid docker-compose-ignite.yml file.");
        }
        File composeFile = new File(composeFilePath);
        if (!composeFile.isFile()) {
            throw new IllegalStateException("Docker Compose file not found: " + composeFile.getAbsolutePath());
        }
        igniteComposeContainer = new DockerComposeContainer<>(composeFile)
                .withEnv("HOST_GOVDOCS1_DIR", TEST_FOLDER.getAbsolutePath())
                .withStartupTimeout(Duration.of(MAX_STARTUP_TIMEOUT, ChronoUnit.SECONDS))
                .withExposedService("tika-grpc", 50052,
                    Wait.forLogMessage(".*Server started.*\\n", 1))
                .withLogConsumer("tika-grpc", new Slf4jLogConsumer(log));
        
        igniteComposeContainer.start();
    }
    
    @AfterAll
    static void teardownIgnite() {
        if (USE_LOCAL_SERVER && localGrpcProcess != null) {
            log.info("Stopping local gRPC server and all child processes");
            
            try {
                long mvnPid = localGrpcProcess.pid();
                log.info("Maven process PID: {}", mvnPid);
                localGrpcProcess.destroy();
                
                if (!localGrpcProcess.waitFor(10, TimeUnit.SECONDS)) {
                    log.warn("Process didn't stop gracefully, forcing shutdown");
                    localGrpcProcess.destroyForcibly();
                    localGrpcProcess.waitFor(5, TimeUnit.SECONDS);
                }
                
                Thread.sleep(2000);
                
                try {
                    killProcessOnPort(GRPC_PORT);
                    killProcessOnPort(3344);
                    killProcessOnPort(10800);
                } catch (Exception e) {
                    log.debug("Error killing processes on ports (may already be stopped): {}", e.getMessage());
                }
                
                log.info("Local gRPC server stopped");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                localGrpcProcess.destroyForcibly();
            }
        } else if (igniteComposeContainer != null) {
            igniteComposeContainer.close();
        }
    }
    
    private static void killProcessOnPort(int port) throws IOException, InterruptedException {
        ProcessBuilder findPb = new ProcessBuilder("lsof", "-ti", ":" + port);
        findPb.redirectErrorStream(true);
        Process findProcess = findPb.start();
        
        try (java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(findProcess.getInputStream(), java.nio.charset.StandardCharsets.UTF_8))) {
            String pidStr = reader.readLine();
            if (pidStr != null && !pidStr.trim().isEmpty()) {
                long pid = Long.parseLong(pidStr.trim());
                long myPid = ProcessHandle.current().pid();
                
                if (pid == myPid || isParentProcess(pid)) {
                    log.debug("Skipping kill of PID {} on port {} (test process or parent)", pid, port);
                    return;
                }
                
                // Only kill processes we can identify as tika-grpc or Ignite instances to avoid
                // accidentally killing unrelated processes that happen to be on the same port.
                String cmdLine = ProcessHandle.of(pid)
                        .flatMap(h -> h.info().commandLine())
                        .orElse("");
                if (!cmdLine.contains("tika") && !cmdLine.contains("TikaGrpc") && !cmdLine.contains("ignite")) {
                    log.debug("Skipping kill of PID {} on port {} — not a tika/ignite process: {}", pid, port, cmdLine);
                    return;
                }
                
                log.info("Found tika/ignite process {} on port {}, killing it", pid, port);
                
                ProcessBuilder killPb = new ProcessBuilder("kill", String.valueOf(pid));
                Process killProcess = killPb.start();
                killProcess.waitFor(2, TimeUnit.SECONDS);
                
                Thread.sleep(1000);
                ProcessBuilder forceKillPb = new ProcessBuilder("kill", "-9", String.valueOf(pid));
                Process forceKillProcess = forceKillPb.start();
                forceKillProcess.waitFor(2, TimeUnit.SECONDS);
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
    void testIgniteConfigStore() throws Exception {
        String fetcherId = "dynamicIgniteFetcher";
        ManagedChannel channel = getManagedChannelForIgnite();
        
        try {
            TikaGrpc.TikaBlockingStub blockingStub = TikaGrpc.newBlockingStub(channel);
            TikaGrpc.TikaStub tikaStub = TikaGrpc.newStub(channel);

            FileSystemFetcherConfig config = new FileSystemFetcherConfig();
            String basePath = USE_LOCAL_SERVER ? TEST_FOLDER.getAbsolutePath() : "/tika/govdocs1";
            config.setBasePath(basePath);
            
            String configJson = ExternalTestBase.OBJECT_MAPPER.writeValueAsString(config);
            log.info("Creating fetcher with Ignite ConfigStore (basePath={}): {}", basePath, configJson);
            
            SaveFetcherReply saveReply = blockingStub.saveFetcher(SaveFetcherRequest
                    .newBuilder()
                    .setFetcherId(fetcherId)
                    .setFetcherClass("org.apache.tika.pipes.fetcher.fs.FileSystemFetcher")
                    .setFetcherConfigJson(configJson)
                    .build());
            
            log.info("Fetcher saved to Ignite: {}", saveReply.getFetcherId());

            List<FetchAndParseReply> successes = Collections.synchronizedList(new ArrayList<>());
            List<FetchAndParseReply> errors = Collections.synchronizedList(new ArrayList<>());

            CountDownLatch countDownLatch = new CountDownLatch(1);
            StreamObserver<FetchAndParseRequest>
                    requestStreamObserver = tikaStub.fetchAndParseBiDirectionalStreaming(new StreamObserver<>() {
                @Override
                public void onNext(FetchAndParseReply fetchAndParseReply) {
                    log.debug("Reply from fetch-and-parse - key={}, status={}", 
                        fetchAndParseReply.getFetchKey(), fetchAndParseReply.getStatus());
                    if ("FETCH_AND_PARSE_EXCEPTION".equals(fetchAndParseReply.getStatus())) {
                        errors.add(fetchAndParseReply);
                    } else {
                        successes.add(fetchAndParseReply);
                    }
                }

                @Override
                public void onError(Throwable throwable) {
                    log.error("Received an error", throwable);
                    Assertions.fail(throwable);
                    countDownLatch.countDown();
                }

                @Override
                public void onCompleted() {
                    log.info("Finished streaming fetch and parse replies");
                    countDownLatch.countDown();
                }
            });

            int maxDocs = Integer.parseInt(System.getProperty("corpus.numDocs", "-1"));
            log.info("Document limit: {}", maxDocs == -1 ? "unlimited" : maxDocs);
            
            try (Stream<Path> paths = Files.walk(TEST_FOLDER.toPath())) {
                Stream<Path> fileStream = paths
                        .filter(Files::isRegularFile)
                        .filter(p -> !p.getFileName().toString()
                                .toLowerCase(Locale.ROOT)
                                .endsWith(".zip"));
                
                if (maxDocs > 0) {
                    fileStream = fileStream.limit(maxDocs);
                        String relPath = TEST_FOLDER.toPath().relativize(file).toString();
                        requestStreamObserver.onNext(FetchAndParseRequest
                                .newBuilder()
                                .setFetcherId(fetcherId)
                                .setFetchKey(relPath)
                                .build());
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
            }
            log.info("Done submitting files to Ignite-backed fetcher {}", fetcherId);

            requestStreamObserver.onCompleted();

            try {
                if (!countDownLatch.await(3, TimeUnit.MINUTES)) {
                    log.error("Timed out waiting for parse to complete");
                    Assertions.fail("Timed out waiting for parsing to complete");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                Assertions.fail("Interrupted while waiting for parsing to complete");
            }
            
            if (maxDocs == -1) {
                ExternalTestBase.assertAllFilesFetched(TEST_FOLDER.toPath(), successes, errors);
            } else {
                int totalProcessed = successes.size() + errors.size();
                log.info("Processed {} documents with Ignite ConfigStore (limit was {})", 
                    totalProcessed, maxDocs);
                Assertions.assertTrue(totalProcessed <= maxDocs, 
                    "Should not process more than " + maxDocs + " documents");
                Assertions.assertTrue(totalProcessed > 0, 
                    "Should have processed at least one document");
            }
            
            log.info("Ignite ConfigStore test completed successfully - {} successes, {} errors", 
                successes.size(), errors.size());
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
    
    private static ManagedChannel getManagedChannelForIgnite() {
        if (USE_LOCAL_SERVER) {
            return ManagedChannelBuilder
                    .forAddress("localhost", GRPC_PORT)
                    .usePlaintext()
                    .maxInboundMessageSize(160 * 1024 * 1024)
                    .build();
        } else {
            return ManagedChannelBuilder
                    .forAddress(igniteComposeContainer.getServiceHost("tika-grpc", 50052),
                               igniteComposeContainer.getServicePort("tika-grpc", 50052))
                    .usePlaintext()
                    .maxInboundMessageSize(160 * 1024 * 1024)
                    .build();
        }
    }
}
