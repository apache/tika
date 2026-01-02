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
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.testcontainers.containers.DockerComposeContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Testcontainers;

import org.apache.tika.FetchAndParseReply;
import org.apache.tika.FetchAndParseRequest;
import org.apache.tika.SaveFetcherReply;
import org.apache.tika.SaveFetcherRequest;
import org.apache.tika.TikaGrpc;
import org.apache.tika.pipes.fetcher.fs.FileSystemFetcherConfig;

/**
 * End-to-end test for Ignite ConfigStore.
 * Tests that fetchers saved via gRPC are persisted in Ignite
 * and available in the forked PipesServer process.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Testcontainers
@Slf4j
@Tag("E2ETest")
class IgniteConfigStoreTest {
    
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final int MAX_STARTUP_TIMEOUT = 120;
    private static final File TEST_FOLDER = new File("target", "govdocs1");
    private static final int GOV_DOCS_FROM_IDX = Integer.parseInt(System.getProperty("govdocs1.fromIndex", "1"));
    private static final int GOV_DOCS_TO_IDX = Integer.parseInt(System.getProperty("govdocs1.toIndex", "1"));
    private static final String DIGITAL_CORPORA_ZIP_FILES_URL = "https://corp.digitalcorpora.org/corpora/files/govdocs1/zipfiles";
    private static final boolean USE_LOCAL_SERVER = Boolean.parseBoolean(System.getProperty("tika.e2e.useLocalServer", "false"));
    private static final int GRPC_PORT = Integer.parseInt(System.getProperty("tika.e2e.grpcPort", "50052"));
    
    private static DockerComposeContainer<?> igniteComposeContainer;
    private static Process localGrpcProcess;
    
    @BeforeAll
    static void setupIgnite() throws Exception {
        // Clean up any orphaned processes from previous runs
        if (USE_LOCAL_SERVER) {
            log.info("Cleaning up any orphaned processes from previous runs");
            try {
                killProcessOnPort(GRPC_PORT);
                killProcessOnPort(3344);
                killProcessOnPort(10800);
            } catch (Exception e) {
                log.debug("No orphaned processes to clean up");
            }
        }
        
        // Load govdocs1 if not already loaded
        if (!TEST_FOLDER.exists() || TEST_FOLDER.listFiles().length == 0) {
            downloadAndUnzipGovdocs1(GOV_DOCS_FROM_IDX, GOV_DOCS_TO_IDX);
        }
        
        if (USE_LOCAL_SERVER) {
            startLocalGrpcServer();
        } else {
            startDockerGrpcServer();
        }
    }
    
    private static void startLocalGrpcServer() throws Exception {
        log.info("Starting local tika-grpc server using Maven");
        
        // Find the tika root directory - it should contain both tika-grpc and tika-e2e-tests
        Path currentDir = Path.of("").toAbsolutePath();
        Path tikaRootDir = currentDir;
        
        // Navigate up to find the directory that contains both tika-grpc and tika-e2e-tests
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
        
        // Use different config for local vs Docker
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
        String javaCmd = javaHome + "/bin/java";
        
        ProcessBuilder pb = new ProcessBuilder(
            "mvn",
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
                "-Dignite.work.dir=" + tikaGrpcDir.resolve("target/ignite-work") + " " +
                "-classpath %classpath " +
                "org.apache.tika.pipes.grpc.TikaGrpcServer " +
                "-c " + configFile + " " +
                "-p " + GRPC_PORT
        );
        
        pb.directory(tikaGrpcDir.toFile());
        pb.redirectErrorStream(true);
        pb.redirectOutput(ProcessBuilder.Redirect.PIPE);
        
        localGrpcProcess = pb.start();
        
        // Track whether Ignite has started
        final boolean[] igniteStarted = {false};
        
        // Start a thread to consume and log output, watching for Ignite startup
        Thread logThread = new Thread(() -> {
            try (java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(localGrpcProcess.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    log.info("tika-grpc: {}", line);
                    
                    // Look for signs that Ignite has fully started
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
        
        // Wait for Ignite to start - check both log messages and gRPC connectivity
        log.info("Waiting for local gRPC server and Ignite to start (timeout: 180 seconds)...");
        
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
                    
                    // Try to actually test gRPC readiness with a real (lightweight) call
                    try {
                        ManagedChannel testChannel = ManagedChannelBuilder
                            .forAddress("localhost", GRPC_PORT)
                            .usePlaintext()
                            .build();
                        
                        try {
                            // Try to use the health check service
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
        log.info("Starting Docker Compose tika-grpc server");
        
        igniteComposeContainer = new DockerComposeContainer<>(
                new File("src/test/resources/docker-compose-ignite.yml"))
                .withEnv("HOST_GOVDOCS1_DIR", TEST_FOLDER.getAbsolutePath())
                .withStartupTimeout(Duration.of(MAX_STARTUP_TIMEOUT, ChronoUnit.SECONDS))
                .withExposedService("tika-grpc", 50052, 
                    Wait.forLogMessage(".*Server started.*\\n", 1))
                .withLogConsumer("tika-grpc", new Slf4jLogConsumer(log));
        
        igniteComposeContainer.start();
        
        log.info("Ignite Docker Compose containers started successfully");
    }
    
    @AfterAll
    static void teardownIgnite() {
        if (USE_LOCAL_SERVER && localGrpcProcess != null) {
            log.info("Stopping local gRPC server and all child processes");
            
            try {
                // Get the PID of the Maven process
                long mvnPid = localGrpcProcess.pid();
                log.info("Maven process PID: {}", mvnPid);
                
                // Try graceful shutdown first
                localGrpcProcess.destroy();
                
                if (!localGrpcProcess.waitFor(10, TimeUnit.SECONDS)) {
                    log.warn("Process didn't stop gracefully, forcing shutdown");
                    localGrpcProcess.destroyForcibly();
                    localGrpcProcess.waitFor(5, TimeUnit.SECONDS);
                }
                
                // Give it a moment for cleanup
                Thread.sleep(2000);
                
                // Kill any remaining child processes by finding processes listening on Ignite/gRPC ports
                // Only do this if the process is still running
                try {
                    killProcessOnPort(GRPC_PORT);  // Kill gRPC server
                    killProcessOnPort(3344);        // Kill Ignite's internal port
                    killProcessOnPort(10800);       // Kill Ignite client connector
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
        // Find process listening on the port using lsof
        ProcessBuilder findPb = new ProcessBuilder("lsof", "-ti", ":" + port);
        findPb.redirectErrorStream(true);
        Process findProcess = findPb.start();
        
        try (java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(findProcess.getInputStream()))) {
            String pidStr = reader.readLine();
            if (pidStr != null && !pidStr.trim().isEmpty()) {
                long pid = Long.parseLong(pidStr.trim());
                long myPid = ProcessHandle.current().pid();
                
                // Don't kill ourselves or our parent
                if (pid == myPid || isParentProcess(pid)) {
                    log.debug("Skipping kill of PID {} on port {} (test process or parent)", pid, port);
                    return;
                }
                
                log.info("Found process {} listening on port {}, killing it", pid, port);
                
                ProcessBuilder killPb = new ProcessBuilder("kill", String.valueOf(pid));
                Process killProcess = killPb.start();
                killProcess.waitFor(2, TimeUnit.SECONDS);
                
                // If still alive, force kill
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

            // Create and save the fetcher dynamically
            FileSystemFetcherConfig config = new FileSystemFetcherConfig();
            // Use local path when running in local mode, Docker path otherwise
            String basePath = USE_LOCAL_SERVER ? TEST_FOLDER.getAbsolutePath() : "/tika/govdocs1";
            config.setBasePath(basePath);
            
            String configJson = OBJECT_MAPPER.writeValueAsString(config);
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

            // Submit files for parsing - limit to configured number
            int maxDocs = Integer.parseInt(System.getProperty("corpa.numdocs", "-1"));
            log.info("Document limit: {}", maxDocs == -1 ? "unlimited" : maxDocs);
            
            try (Stream<Path> paths = Files.walk(TEST_FOLDER.toPath())) {
                Stream<Path> fileStream = paths.filter(Files::isRegularFile);
                
                if (maxDocs > 0) {
                    fileStream = fileStream.limit(maxDocs);
                }
                
                fileStream.forEach(file -> {
                    try {
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

            // Wait for all parsing to complete
            try {
                if (!countDownLatch.await(3, TimeUnit.MINUTES)) {
                    log.error("Timed out waiting for parse to complete");
                    Assertions.fail("Timed out waiting for parsing to complete");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                Assertions.fail("Interrupted while waiting for parsing to complete");
            }
            
            // Verify documents were processed
            if (maxDocs == -1) {
                assertAllFilesFetched(TEST_FOLDER.toPath(), successes, errors);
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
            // Properly shutdown gRPC channel to avoid resource leak
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
    
    // Helper method for downloading test data
    private static void downloadAndUnzipGovdocs1(int fromIndex, int toIndex) throws IOException {
        Path targetDir = TEST_FOLDER.toPath();
        Files.createDirectories(targetDir);

        for (int i = fromIndex; i <= toIndex; i++) {
            String zipName = String.format("%03d.zip", i);
            String url = DIGITAL_CORPORA_ZIP_FILES_URL + "/" + zipName;
            Path zipPath = targetDir.resolve(zipName);
            
            if (Files.exists(zipPath)) {
                log.info("{} already exists, skipping download", zipName);
                continue;
            }

            log.info("Downloading {} from {}...", zipName, url);
            try (InputStream in = new URL(url).openStream()) {
                Files.copy(in, zipPath, StandardCopyOption.REPLACE_EXISTING);
            }

            log.info("Unzipping {}...", zipName);
            try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipPath.toFile()))) {
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    Path outPath = targetDir.resolve(entry.getName());
                    if (entry.isDirectory()) {
                        Files.createDirectories(outPath);
                    } else {
                        Files.createDirectories(outPath.getParent());
                        try (OutputStream out = Files.newOutputStream(outPath)) {
                            zis.transferTo(out);
                        }
                    }
                    zis.closeEntry();
                }
            }
        }
        
        log.info("Finished downloading and extracting govdocs1 files");
    }
    
    // Helper method to validate all files were fetched
    private static void assertAllFilesFetched(Path baseDir, List<FetchAndParseReply> successes, 
                                            List<FetchAndParseReply> errors) {
        java.util.Set<String> allFetchKeys = new java.util.HashSet<>();
        for (FetchAndParseReply reply : successes) {
            allFetchKeys.add(reply.getFetchKey());
        }
        for (FetchAndParseReply reply : errors) {
            allFetchKeys.add(reply.getFetchKey());
        }
        
        java.util.Set<String> keysFromGovdocs1 = new java.util.HashSet<>();
        try (Stream<Path> paths = Files.walk(baseDir)) {
            paths.filter(Files::isRegularFile)
                    .forEach(file -> {
                        String relPath = baseDir.relativize(file).toString();
                        if (java.util.regex.Pattern.compile("\\d{3}\\.zip").matcher(relPath).find()) {
                            return;
                        }
                        keysFromGovdocs1.add(relPath);
                    });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        
        Assertions.assertNotEquals(0, successes.size(), "Should have some successful fetches");
        log.info("Processed {} files: {} successes, {} errors", allFetchKeys.size(), successes.size(), errors.size());
        Assertions.assertEquals(keysFromGovdocs1, allFetchKeys, () -> {
            java.util.Set<String> missing = new java.util.HashSet<>(keysFromGovdocs1);
            missing.removeAll(allFetchKeys);
            return "Missing fetch keys: " + missing;
        });
    }
    
    // Helper method to create gRPC channel
    private static ManagedChannel getManagedChannelForIgnite() {
        if (USE_LOCAL_SERVER) {
            return ManagedChannelBuilder
                    .forAddress("localhost", GRPC_PORT)
                    .usePlaintext()
                    .executor(Executors.newCachedThreadPool())
                    .maxInboundMessageSize(160 * 1024 * 1024)
                    .build();
        } else {
            return ManagedChannelBuilder
                    .forAddress(igniteComposeContainer.getServiceHost("tika-grpc", 50052), 
                               igniteComposeContainer.getServicePort("tika-grpc", 50052))
                    .usePlaintext()
                    .executor(Executors.newCachedThreadPool())
                    .maxInboundMessageSize(160 * 1024 * 1024)
                    .build();
        }
    }
}
