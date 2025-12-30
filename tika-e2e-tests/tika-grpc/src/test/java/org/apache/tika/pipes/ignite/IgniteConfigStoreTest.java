package org.apache.tika.pipes.ignite;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.FetchAndParseReply;
import org.apache.tika.FetchAndParseRequest;
import org.apache.tika.SaveFetcherReply;
import org.apache.tika.SaveFetcherRequest;
import org.apache.tika.TikaGrpc;
import org.apache.tika.pipes.fetcher.fs.FileSystemFetcherConfig;
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
    
    private static DockerComposeContainer<?> igniteComposeContainer;
    
    @BeforeAll
    static void setupIgnite() throws Exception {
        // Load govdocs1 if not already loaded
        if (!TEST_FOLDER.exists() || TEST_FOLDER.listFiles().length == 0) {
            downloadAndUnzipGovdocs1(GOV_DOCS_FROM_IDX, GOV_DOCS_TO_IDX);
        }
        
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
        if (igniteComposeContainer != null) {
            igniteComposeContainer.close();
        }
    }
    
    @Test
    void testIgniteConfigStore() throws Exception {
        String fetcherId = "dynamicIgniteFetcher";
        ManagedChannel channel = getManagedChannelForIgnite();
        TikaGrpc.TikaBlockingStub blockingStub = TikaGrpc.newBlockingStub(channel);
        TikaGrpc.TikaStub tikaStub = TikaGrpc.newStub(channel);

        // Create and save the fetcher dynamically
        FileSystemFetcherConfig config = new FileSystemFetcherConfig();
        config.setBasePath("/tika/govdocs1");
        
        String configJson = OBJECT_MAPPER.writeValueAsString(config);
        log.info("Creating fetcher with Ignite ConfigStore: {}", configJson);
        
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
        return ManagedChannelBuilder
                .forAddress(igniteComposeContainer.getServiceHost("tika-grpc", 50052), 
                           igniteComposeContainer.getServicePort("tika-grpc", 50052))
                .usePlaintext()
                .executor(Executors.newCachedThreadPool())
                .maxInboundMessageSize(160 * 1024 * 1024)
                .build();
    }
}
