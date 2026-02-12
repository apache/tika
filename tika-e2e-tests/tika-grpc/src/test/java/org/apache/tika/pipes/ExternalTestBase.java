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
package org.apache.tika.pipes;

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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.containers.DockerComposeContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Testcontainers;

import org.apache.tika.FetchAndParseReply;

/**
 * Base class for Tika gRPC end-to-end tests.
 * Can run with either local server (default in CI) or Docker Compose.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Testcontainers
@Slf4j
@Tag("E2ETest")
public abstract class ExternalTestBase {
    public static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    public static final int MAX_STARTUP_TIMEOUT = 120;
    public static final String GOV_DOCS_FOLDER = "/tika/govdocs1";
    public static final File TEST_FOLDER = new File("target", "govdocs1");
    public static final int GOV_DOCS_FROM_IDX = Integer.parseInt(System.getProperty("govdocs1.fromIndex", "1"));
    public static final int GOV_DOCS_TO_IDX = Integer.parseInt(System.getProperty("govdocs1.toIndex", "1"));
    public static final String DIGITAL_CORPORA_ZIP_FILES_URL = "https://corp.digitalcorpora.org/corpora/files/govdocs1/zipfiles";
    private static final boolean USE_LOCAL_SERVER = Boolean.parseBoolean(System.getProperty("tika.e2e.useLocalServer", "false"));
    private static final int GRPC_PORT = Integer.parseInt(System.getProperty("tika.e2e.grpcPort", "50052"));
    
    public static DockerComposeContainer<?> composeContainer;
    private static Process localGrpcProcess;

    @BeforeAll
    static void setup() throws Exception {
        loadGovdocs1();
        
        if (USE_LOCAL_SERVER) {
            startLocalGrpcServer();
        } else {
            startDockerGrpcServer();
        }
    }
    
    private static void startLocalGrpcServer() throws Exception {
        log.info("Starting local tika-grpc server using Maven exec");
        
        Path tikaGrpcDir = findTikaGrpcDirectory();
        Path configFile = Path.of("src/test/resources/tika-config.json").toAbsolutePath();
        
        if (!Files.exists(configFile)) {
            throw new IllegalStateException("Config file not found: " + configFile);
        }
        
        log.info("Using tika-grpc from: {}", tikaGrpcDir);
        log.info("Using config file: {}", configFile);
        
        String javaHome = System.getProperty("java.home");
        boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
        String javaCmd = javaHome + (isWindows ? "\\bin\\java.exe" : "/bin/java");
        String mvnCmd = isWindows ? "mvn.cmd" : "mvn";
        
        ProcessBuilder pb = new ProcessBuilder(
            mvnCmd,
            "exec:exec",
            "-Dexec.executable=" + javaCmd,
            "-Dexec.args=" +
                "--add-opens=java.base/java.lang=ALL-UNNAMED " +
                "--add-opens=java.base/java.nio=ALL-UNNAMED " +
                "--add-opens=java.base/java.util=ALL-UNNAMED " +
                "--add-opens=java.base/java.util.concurrent=ALL-UNNAMED " +
                "-classpath %classpath " +
                "org.apache.tika.pipes.grpc.TikaGrpcServer " +
                "-c " + configFile + " " +
                "-p " + GRPC_PORT
        );
        
        pb.directory(tikaGrpcDir.toFile());
        pb.redirectErrorStream(true);
        pb.redirectOutput(ProcessBuilder.Redirect.PIPE);
        
        localGrpcProcess = pb.start();
        
        // Start thread to consume output
        Thread logThread = new Thread(() -> {
            try (java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(localGrpcProcess.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    log.info("tika-grpc: {}", line);
                }
            } catch (IOException e) {
                log.error("Error reading server output", e);
            }
        });
        logThread.setDaemon(true);
        logThread.start();
        
        // Wait for server to be ready
        waitForServerReady();
        
        log.info("Local tika-grpc server started successfully on port {}", GRPC_PORT);
    }
    
    private static Path findTikaGrpcDirectory() {
        Path currentDir = Path.of("").toAbsolutePath();
        Path tikaRootDir = currentDir;
        
        while (tikaRootDir != null && 
               !(Files.exists(tikaRootDir.resolve("tika-grpc")) && 
                 Files.exists(tikaRootDir.resolve("tika-e2e-tests")))) {
            tikaRootDir = tikaRootDir.getParent();
        }
        
        if (tikaRootDir == null) {
            throw new IllegalStateException("Cannot find tika root directory. " +
                "Current dir: " + currentDir);
        }
        
        return tikaRootDir.resolve("tika-grpc");
    }
    
    private static void waitForServerReady() throws Exception {
        int maxAttempts = 60;
        for (int i = 0; i < maxAttempts; i++) {
            try {
                ManagedChannel testChannel = ManagedChannelBuilder
                    .forAddress("localhost", GRPC_PORT)
                    .usePlaintext()
                    .build();
                
                try {
                    // Try a simple connection
                    testChannel.getState(true);
                    TimeUnit.MILLISECONDS.sleep(100);
                    if (testChannel.getState(false).toString().contains("READY")) {
                        log.info("gRPC server is ready!");
                        return;
                    }
                } finally {
                    testChannel.shutdown();
                    testChannel.awaitTermination(1, TimeUnit.SECONDS);
                }
            } catch (Exception e) {
                // Server not ready yet
            }
            TimeUnit.SECONDS.sleep(1);
        }
        
        if (localGrpcProcess != null && localGrpcProcess.isAlive()) {
            localGrpcProcess.destroyForcibly();
        }
        throw new RuntimeException("Local gRPC server failed to start within timeout");
    }
    
    private static void startDockerGrpcServer() {
        log.info("Starting Docker Compose tika-grpc server");
        
        composeContainer = new DockerComposeContainer<>(
                new File("src/test/resources/docker-compose.yml"))
                .withEnv("HOST_GOVDOCS1_DIR", TEST_FOLDER.getAbsolutePath())
                .withStartupTimeout(Duration.of(MAX_STARTUP_TIMEOUT, ChronoUnit.SECONDS))
                .withExposedService("tika-grpc", 50052, 
                    Wait.forLogMessage(".*Server started.*\\n", 1))
                .withLogConsumer("tika-grpc", new Slf4jLogConsumer(log));
        
        composeContainer.start();
        
        log.info("Docker Compose containers started successfully");
    }

    private static void loadGovdocs1() throws IOException, InterruptedException {
        int retries = 3;
        int attempt = 0;
        while (true) {
            try {
                downloadAndUnzipGovdocs1(GOV_DOCS_FROM_IDX, GOV_DOCS_TO_IDX);
                break;
            } catch (IOException e) {
                attempt++;
                if (attempt >= retries) {
                    throw e;
                }
                log.warn("Download attempt {} failed, retrying in 10 seconds...", attempt, e);
                TimeUnit.SECONDS.sleep(10);
            }
        }
    }

    @AfterAll
    void close() {
        if (USE_LOCAL_SERVER && localGrpcProcess != null) {
            log.info("Stopping local gRPC server");
            localGrpcProcess.destroy();
            try {
                if (!localGrpcProcess.waitFor(10, TimeUnit.SECONDS)) {
                    localGrpcProcess.destroyForcibly();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                localGrpcProcess.destroyForcibly();
            }
        } else if (composeContainer != null) {
            composeContainer.close();
        }
    }

    public static void downloadAndUnzipGovdocs1(int fromIndex, int toIndex) throws IOException {
        Path targetDir = TEST_FOLDER.toPath();
        Files.createDirectories(targetDir);

        for (int i = fromIndex; i <= toIndex; i++) {
            String zipName = String.format(java.util.Locale.ROOT, "%03d.zip", i);
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

    public static void assertAllFilesFetched(Path baseDir, List<FetchAndParseReply> successes, 
                                            List<FetchAndParseReply> errors) {
        Set<String> allFetchKeys = new HashSet<>();
        for (FetchAndParseReply reply : successes) {
            allFetchKeys.add(reply.getFetchKey());
        }
        for (FetchAndParseReply reply : errors) {
            allFetchKeys.add(reply.getFetchKey());
        }
        
        Set<String> keysFromGovdocs1 = new HashSet<>();
        try (Stream<Path> paths = Files.walk(baseDir)) {
            paths.filter(Files::isRegularFile)
                    .forEach(file -> {
                        String relPath = baseDir.relativize(file).toString();
                        if (Pattern.compile("\\d{3}\\.zip").matcher(relPath).find()) {
                            return;
                        }
                        keysFromGovdocs1.add(relPath);
                    });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        
        Assertions.assertNotEquals(0, successes.size(), "Should have some successful fetches");
        // Note: errors.size() can be 0 if all files parse successfully
        log.info("Processed {} files: {} successes, {} errors", allFetchKeys.size(), successes.size(), errors.size());
        Assertions.assertEquals(keysFromGovdocs1, allFetchKeys, () -> {
            Set<String> missing = new HashSet<>(keysFromGovdocs1);
            missing.removeAll(allFetchKeys);
            return "Missing fetch keys: " + missing;
        });
    }

    public static ManagedChannel getManagedChannel() {
        if (USE_LOCAL_SERVER) {
            return ManagedChannelBuilder
                    .forAddress("localhost", GRPC_PORT)
                    .usePlaintext()
                    .executor(Executors.newCachedThreadPool())
                    .maxInboundMessageSize(160 * 1024 * 1024)
                    .build();
        } else {
            return ManagedChannelBuilder
                    .forAddress(composeContainer.getServiceHost("tika-grpc", 50052), 
                               composeContainer.getServicePort("tika-grpc", 50052))
                    .usePlaintext()
                    .executor(Executors.newCachedThreadPool())
                    .maxInboundMessageSize(160 * 1024 * 1024)
                    .build();
        }
    }
}
