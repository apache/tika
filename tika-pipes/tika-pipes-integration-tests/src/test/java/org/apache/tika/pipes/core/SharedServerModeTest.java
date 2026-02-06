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
package org.apache.tika.pipes.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.apache.tika.config.loader.TikaJsonConfig;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.pipes.api.FetchEmitTuple;
import org.apache.tika.pipes.api.PipesResult;
import org.apache.tika.pipes.api.emitter.EmitKey;
import org.apache.tika.pipes.api.fetcher.FetchKey;

/**
 * Integration tests for shared server mode.
 * <p>
 * These tests verify that multiple PipesClients can share a single PipesServer
 * process, reducing memory overhead while maintaining correct functionality.
 */
public class SharedServerModeTest {

    private static final String FETCHER_NAME = "fsf";
    private static final String EMITTER_NAME = "fse";

    private static final String MOCK_OK = "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>" +
            "<mock>" +
            "<metadata action=\"add\" name=\"dc:creator\">Test Author</metadata>" +
            "<write element=\"p\">Test content</write>" +
            "</mock>";

    private static final String MOCK_SLOW = "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>" +
            "<mock>" +
            "<metadata action=\"add\" name=\"dc:creator\">Slow Author</metadata>" +
            "<write element=\"p\">Slow content</write>" +
            "<fakeload millis=\"500\" cpu=\"1\" mb=\"10\"/>" +
            "</mock>";

    private static final String MOCK_OOM = "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>" +
            "<mock>" +
            "<throw class=\"java.lang.OutOfMemoryError\">oom message</throw>" +
            "</mock>";

    private static final String MOCK_TIMEOUT = "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>" +
            "<mock>" +
            "<metadata action=\"add\" name=\"dc:creator\">Timeout Author</metadata>" +
            "<write element=\"p\">Timeout content</write>" +
            "<fakeload millis=\"60000\" cpu=\"1\" mb=\"10\"/>" +
            "</mock>";

    @Test
    public void testBasicSharedMode(@TempDir Path tmp) throws Exception {
        Path inputDir = setupInputDir(tmp);
        String testFile = "test.xml";
        Files.writeString(inputDir.resolve(testFile), MOCK_OK, StandardCharsets.UTF_8);

        Path tikaConfigPath = PluginsTestHelper.getFileSystemFetcherConfig(
                "tika-config-shared-server.json", tmp, inputDir, tmp.resolve("output"), false);
        TikaJsonConfig tikaJsonConfig = TikaJsonConfig.load(tikaConfigPath);
        PipesConfig pipesConfig = PipesConfig.load(tikaJsonConfig);

        // Verify shared mode is enabled
        assertTrue(pipesConfig.isUseSharedServer(), "Shared server mode should be enabled");

        try (PipesParser pipesParser = PipesParser.load(tikaJsonConfig, pipesConfig, tikaConfigPath)) {
            assertTrue(pipesParser.isSharedMode(), "PipesParser should be in shared mode");

            PipesResult result = pipesParser.parse(new FetchEmitTuple(
                    testFile,
                    new FetchKey(FETCHER_NAME, testFile),
                    new EmitKey(EMITTER_NAME, ""),
                    new Metadata(),
                    new ParseContext(),
                    FetchEmitTuple.ON_PARSE_EXCEPTION.SKIP));

            assertTrue(result.isSuccess(), "Parse should succeed");
            assertNotNull(result.emitData().getMetadataList());
            assertEquals(1, result.emitData().getMetadataList().size());
            assertEquals("Test Author", result.emitData().getMetadataList().get(0).get("dc:creator"));
        }
    }

    @Test
    public void testConcurrentRequests(@TempDir Path tmp) throws Exception {
        Path inputDir = setupInputDir(tmp);

        // Create multiple test files
        int numFiles = 20;
        for (int i = 0; i < numFiles; i++) {
            Files.writeString(inputDir.resolve("test" + i + ".xml"), MOCK_OK, StandardCharsets.UTF_8);
        }

        Path tikaConfigPath = PluginsTestHelper.getFileSystemFetcherConfig(
                "tika-config-shared-server.json", tmp, inputDir, tmp.resolve("output"), false);
        TikaJsonConfig tikaJsonConfig = TikaJsonConfig.load(tikaConfigPath);
        PipesConfig pipesConfig = PipesConfig.load(tikaJsonConfig);

        try (PipesParser pipesParser = PipesParser.load(tikaJsonConfig, pipesConfig, tikaConfigPath)) {
            ExecutorService executor = Executors.newFixedThreadPool(8);
            List<Future<PipesResult>> futures = new ArrayList<>();

            // Submit concurrent parse requests
            for (int i = 0; i < numFiles; i++) {
                final int fileIndex = i;
                futures.add(executor.submit(() -> pipesParser.parse(new FetchEmitTuple(
                        "test" + fileIndex + ".xml",
                        new FetchKey(FETCHER_NAME, "test" + fileIndex + ".xml"),
                        new EmitKey(EMITTER_NAME, ""),
                        new Metadata(),
                        new ParseContext(),
                        FetchEmitTuple.ON_PARSE_EXCEPTION.SKIP))));
            }

            // Verify all succeeded
            int successCount = 0;
            for (Future<PipesResult> future : futures) {
                PipesResult result = future.get();
                if (result.isSuccess()) {
                    successCount++;
                    assertNotNull(result.emitData().getMetadataList());
                    assertEquals("Test Author", result.emitData().getMetadataList().get(0).get("dc:creator"));
                }
            }

            executor.shutdown();
            assertEquals(numFiles, successCount, "All concurrent requests should succeed");
        }
    }

    @Test
    public void testMultipleSequentialRequests(@TempDir Path tmp) throws Exception {
        Path inputDir = setupInputDir(tmp);

        // Create test files
        Files.writeString(inputDir.resolve("file1.xml"), MOCK_OK, StandardCharsets.UTF_8);
        Files.writeString(inputDir.resolve("file2.xml"), MOCK_OK, StandardCharsets.UTF_8);
        Files.writeString(inputDir.resolve("file3.xml"), MOCK_OK, StandardCharsets.UTF_8);

        Path tikaConfigPath = PluginsTestHelper.getFileSystemFetcherConfig(
                "tika-config-shared-server.json", tmp, inputDir, tmp.resolve("output"), false);
        TikaJsonConfig tikaJsonConfig = TikaJsonConfig.load(tikaConfigPath);
        PipesConfig pipesConfig = PipesConfig.load(tikaJsonConfig);

        try (PipesParser pipesParser = PipesParser.load(tikaJsonConfig, pipesConfig, tikaConfigPath)) {
            // Process files sequentially with the same PipesParser
            for (int i = 1; i <= 3; i++) {
                String fileName = "file" + i + ".xml";
                PipesResult result = pipesParser.parse(new FetchEmitTuple(
                        fileName,
                        new FetchKey(FETCHER_NAME, fileName),
                        new EmitKey(EMITTER_NAME, ""),
                        new Metadata(),
                        new ParseContext(),
                        FetchEmitTuple.ON_PARSE_EXCEPTION.SKIP));

                assertTrue(result.isSuccess(), "Parse of " + fileName + " should succeed");
                assertNotNull(result.emitData().getMetadataList());
            }
        }
    }

    @Test
    public void testGracefulShutdown(@TempDir Path tmp) throws Exception {
        Path inputDir = setupInputDir(tmp);
        Files.writeString(inputDir.resolve("test.xml"), MOCK_SLOW, StandardCharsets.UTF_8);

        Path tikaConfigPath = PluginsTestHelper.getFileSystemFetcherConfig(
                "tika-config-shared-server.json", tmp, inputDir, tmp.resolve("output"), false);
        TikaJsonConfig tikaJsonConfig = TikaJsonConfig.load(tikaConfigPath);
        PipesConfig pipesConfig = PipesConfig.load(tikaJsonConfig);

        // Create and close parser multiple times to verify graceful shutdown/restart
        for (int iteration = 0; iteration < 3; iteration++) {
            try (PipesParser pipesParser = PipesParser.load(tikaJsonConfig, pipesConfig, tikaConfigPath)) {
                PipesResult result = pipesParser.parse(new FetchEmitTuple(
                        "test.xml",
                        new FetchKey(FETCHER_NAME, "test.xml"),
                        new EmitKey(EMITTER_NAME, ""),
                        new Metadata(),
                        new ParseContext(),
                        FetchEmitTuple.ON_PARSE_EXCEPTION.SKIP));

                assertTrue(result.isSuccess(),
                        "Parse should succeed on iteration " + iteration);
            }
            // PipesParser closed - server should shut down cleanly
        }
    }

    @Test
    public void testPerClientModeStillWorks(@TempDir Path tmp) throws Exception {
        // Verify default per-client mode still works
        Path inputDir = setupInputDir(tmp);
        String testFile = "test.xml";
        Files.writeString(inputDir.resolve(testFile), MOCK_OK, StandardCharsets.UTF_8);

        // Use standard config (not shared server)
        Path tikaConfigPath = PluginsTestHelper.getFileSystemFetcherConfig(tmp, inputDir, tmp.resolve("output"));
        TikaJsonConfig tikaJsonConfig = TikaJsonConfig.load(tikaConfigPath);
        PipesConfig pipesConfig = PipesConfig.load(tikaJsonConfig);

        // Verify shared mode is NOT enabled (default)
        assertTrue(!pipesConfig.isUseSharedServer(), "Shared server mode should be disabled by default");

        try (PipesParser pipesParser = PipesParser.load(tikaJsonConfig, pipesConfig, tikaConfigPath)) {
            assertTrue(!pipesParser.isSharedMode(), "PipesParser should NOT be in shared mode");

            PipesResult result = pipesParser.parse(new FetchEmitTuple(
                    testFile,
                    new FetchKey(FETCHER_NAME, testFile),
                    new EmitKey(EMITTER_NAME, ""),
                    new Metadata(),
                    new ParseContext(),
                    FetchEmitTuple.ON_PARSE_EXCEPTION.SKIP));

            assertTrue(result.isSuccess(), "Parse should succeed in per-client mode");
            assertEquals("Test Author", result.emitData().getMetadataList().get(0).get("dc:creator"));
        }
    }

    @Test
    public void testCompareSharedVsPerClientMode(@TempDir Path tmp) throws Exception {
        // This test verifies that both modes produce identical results
        Path inputDir = setupInputDir(tmp);
        String testFile = "compare.xml";
        Files.writeString(inputDir.resolve(testFile), MOCK_OK, StandardCharsets.UTF_8);

        Path outputDir = tmp.resolve("output");
        Files.createDirectories(outputDir);

        // Test per-client mode
        Path perClientConfigPath = PluginsTestHelper.getFileSystemFetcherConfig(tmp, inputDir, outputDir);
        TikaJsonConfig perClientConfig = TikaJsonConfig.load(perClientConfigPath);
        PipesConfig perClientPipesConfig = PipesConfig.load(perClientConfig);

        Metadata perClientMetadata;
        try (PipesParser parser = PipesParser.load(perClientConfig, perClientPipesConfig, perClientConfigPath)) {
            PipesResult result = parser.parse(new FetchEmitTuple(
                    testFile,
                    new FetchKey(FETCHER_NAME, testFile),
                    new EmitKey(EMITTER_NAME, ""),
                    new Metadata(),
                    new ParseContext(),
                    FetchEmitTuple.ON_PARSE_EXCEPTION.SKIP));
            assertTrue(result.isSuccess());
            perClientMetadata = result.emitData().getMetadataList().get(0);
        }

        // Test shared mode
        Path sharedConfigPath = PluginsTestHelper.getFileSystemFetcherConfig(
                "tika-config-shared-server.json", tmp, inputDir, outputDir, false);
        TikaJsonConfig sharedConfig = TikaJsonConfig.load(sharedConfigPath);
        PipesConfig sharedPipesConfig = PipesConfig.load(sharedConfig);

        Metadata sharedMetadata;
        try (PipesParser parser = PipesParser.load(sharedConfig, sharedPipesConfig, sharedConfigPath)) {
            PipesResult result = parser.parse(new FetchEmitTuple(
                    testFile,
                    new FetchKey(FETCHER_NAME, testFile),
                    new EmitKey(EMITTER_NAME, ""),
                    new Metadata(),
                    new ParseContext(),
                    FetchEmitTuple.ON_PARSE_EXCEPTION.SKIP));
            assertTrue(result.isSuccess());
            sharedMetadata = result.emitData().getMetadataList().get(0);
        }

        // Compare key metadata values
        assertEquals(
                perClientMetadata.get(TikaCoreProperties.RESOURCE_NAME_KEY),
                sharedMetadata.get(TikaCoreProperties.RESOURCE_NAME_KEY),
                "Resource name should match between modes");
        assertEquals(
                perClientMetadata.get("dc:creator"),
                sharedMetadata.get("dc:creator"),
                "Creator should match between modes");
        assertEquals(
                perClientMetadata.get(TikaCoreProperties.TIKA_CONTENT),
                sharedMetadata.get(TikaCoreProperties.TIKA_CONTENT),
                "Content should match between modes");
    }

    @Test
    public void testOomCausesServerRestart(@TempDir Path tmp) throws Exception {
        // Test that an OOM causes server crash, and subsequent requests succeed after restart
        Path inputDir = setupInputDir(tmp);
        Files.writeString(inputDir.resolve("oom.xml"), MOCK_OOM, StandardCharsets.UTF_8);
        Files.writeString(inputDir.resolve("ok.xml"), MOCK_OK, StandardCharsets.UTF_8);

        Path tikaConfigPath = PluginsTestHelper.getFileSystemFetcherConfig(
                "tika-config-shared-server.json", tmp, inputDir, tmp.resolve("output"), false);
        TikaJsonConfig tikaJsonConfig = TikaJsonConfig.load(tikaConfigPath);
        PipesConfig pipesConfig = PipesConfig.load(tikaJsonConfig);

        try (PipesParser pipesParser = PipesParser.load(tikaJsonConfig, pipesConfig, tikaConfigPath)) {
            // First, trigger OOM
            PipesResult oomResult = pipesParser.parse(new FetchEmitTuple(
                    "oom.xml",
                    new FetchKey(FETCHER_NAME, "oom.xml"),
                    new EmitKey(EMITTER_NAME, ""),
                    new Metadata(),
                    new ParseContext(),
                    FetchEmitTuple.ON_PARSE_EXCEPTION.SKIP));

            // OOM should be reported
            assertEquals(PipesResult.RESULT_STATUS.OOM, oomResult.status(),
                    "OOM file should return OOM status");

            // Now verify the server restarts and subsequent requests succeed
            // This tests the critical restart-after-crash behavior
            PipesResult okResult = pipesParser.parse(new FetchEmitTuple(
                    "ok.xml",
                    new FetchKey(FETCHER_NAME, "ok.xml"),
                    new EmitKey(EMITTER_NAME, ""),
                    new Metadata(),
                    new ParseContext(),
                    FetchEmitTuple.ON_PARSE_EXCEPTION.SKIP));

            assertTrue(okResult.isSuccess(),
                    "After OOM, server should restart and subsequent request should succeed. Got: " + okResult.status());
            assertEquals("Test Author", okResult.emitData().getMetadataList().get(0).get("dc:creator"));
        }
    }

    @Test
    public void testOomCausesServerPortChange(@TempDir Path tmp) throws Exception {
        // CRITICAL TEST: Verify that OOM actually kills the server and restarts on a NEW port.
        // This test would have caught the bug where ConnectionHandler didn't call System.exit()
        // and clients were reconnecting to the same (corrupted) server.
        Path inputDir = setupInputDir(tmp);
        Files.writeString(inputDir.resolve("warmup.xml"), MOCK_OK, StandardCharsets.UTF_8);
        Files.writeString(inputDir.resolve("oom.xml"), MOCK_OOM, StandardCharsets.UTF_8);
        Files.writeString(inputDir.resolve("ok.xml"), MOCK_OK, StandardCharsets.UTF_8);

        Path tikaConfigPath = PluginsTestHelper.getFileSystemFetcherConfig(
                "tika-config-shared-server.json", tmp, inputDir, tmp.resolve("output"), false);
        TikaJsonConfig tikaJsonConfig = TikaJsonConfig.load(tikaConfigPath);
        PipesConfig pipesConfig = PipesConfig.load(tikaJsonConfig);

        try (PipesParser pipesParser = PipesParser.load(tikaJsonConfig, pipesConfig, tikaConfigPath)) {
            // First, make a request to ensure the server is started (lazy initialization)
            PipesResult warmupResult = pipesParser.parse(new FetchEmitTuple(
                    "warmup.xml",
                    new FetchKey(FETCHER_NAME, "warmup.xml"),
                    new EmitKey(EMITTER_NAME, ""),
                    new Metadata(),
                    new ParseContext(),
                    FetchEmitTuple.ON_PARSE_EXCEPTION.SKIP));
            assertTrue(warmupResult.isSuccess(), "Warmup request should succeed");

            // Now get the initial server port
            int initialPort = pipesParser.getCurrentServerPort();
            assertTrue(initialPort > 0, "Should have valid initial port after warmup");

            // Trigger OOM
            PipesResult oomResult = pipesParser.parse(new FetchEmitTuple(
                    "oom.xml",
                    new FetchKey(FETCHER_NAME, "oom.xml"),
                    new EmitKey(EMITTER_NAME, ""),
                    new Metadata(),
                    new ParseContext(),
                    FetchEmitTuple.ON_PARSE_EXCEPTION.SKIP));

            assertEquals(PipesResult.RESULT_STATUS.OOM, oomResult.status());

            // Process another request to trigger server restart
            PipesResult okResult = pipesParser.parse(new FetchEmitTuple(
                    "ok.xml",
                    new FetchKey(FETCHER_NAME, "ok.xml"),
                    new EmitKey(EMITTER_NAME, ""),
                    new Metadata(),
                    new ParseContext(),
                    FetchEmitTuple.ON_PARSE_EXCEPTION.SKIP));
            assertTrue(okResult.isSuccess(), "Post-OOM request should succeed. Got: " + okResult.status());

            // CRITICAL: Verify the server port changed - proves server was actually killed and restarted
            int newPort = pipesParser.getCurrentServerPort();
            assertTrue(newPort > 0, "Should have valid new port");
            assertTrue(newPort != initialPort,
                    "Server port MUST change after OOM. Initial port: " + initialPort +
                    ", new port: " + newPort + ". If ports are the same, the server wasn't " +
                    "properly killed and restarted - this is a critical bug!");
        }
    }

    @Test
    public void testMultipleOomsWithRecovery(@TempDir Path tmp) throws Exception {
        // Test multiple OOMs with recovery between each
        Path inputDir = setupInputDir(tmp);
        for (int i = 0; i < 3; i++) {
            Files.writeString(inputDir.resolve("oom" + i + ".xml"), MOCK_OOM, StandardCharsets.UTF_8);
            Files.writeString(inputDir.resolve("ok" + i + ".xml"), MOCK_OK, StandardCharsets.UTF_8);
        }

        Path tikaConfigPath = PluginsTestHelper.getFileSystemFetcherConfig(
                "tika-config-shared-server.json", tmp, inputDir, tmp.resolve("output"), false);
        TikaJsonConfig tikaJsonConfig = TikaJsonConfig.load(tikaConfigPath);
        PipesConfig pipesConfig = PipesConfig.load(tikaJsonConfig);

        try (PipesParser pipesParser = PipesParser.load(tikaJsonConfig, pipesConfig, tikaConfigPath)) {
            for (int i = 0; i < 3; i++) {
                // Trigger OOM
                PipesResult oomResult = pipesParser.parse(new FetchEmitTuple(
                        "oom" + i + ".xml",
                        new FetchKey(FETCHER_NAME, "oom" + i + ".xml"),
                        new EmitKey(EMITTER_NAME, ""),
                        new Metadata(),
                        new ParseContext(),
                        FetchEmitTuple.ON_PARSE_EXCEPTION.SKIP));
                assertEquals(PipesResult.RESULT_STATUS.OOM, oomResult.status(),
                        "OOM " + i + " should return OOM status");

                // Verify recovery
                PipesResult okResult = pipesParser.parse(new FetchEmitTuple(
                        "ok" + i + ".xml",
                        new FetchKey(FETCHER_NAME, "ok" + i + ".xml"),
                        new EmitKey(EMITTER_NAME, ""),
                        new Metadata(),
                        new ParseContext(),
                        FetchEmitTuple.ON_PARSE_EXCEPTION.SKIP));
                assertTrue(okResult.isSuccess(),
                        "After OOM " + i + ", recovery should succeed. Got: " + okResult.status());
            }
        }
    }

    @Test
    public void testConcurrentRequestsDuringOom(@TempDir Path tmp) throws Exception {
        // Test that when OOM occurs, in-flight requests on other connections
        // get reasonable results (either success if they completed before crash, or failure)
        // and that subsequent requests succeed after restart
        Path inputDir = setupInputDir(tmp);

        // Create a mix of slow files and one OOM file
        for (int i = 0; i < 10; i++) {
            Files.writeString(inputDir.resolve("slow" + i + ".xml"), MOCK_SLOW, StandardCharsets.UTF_8);
        }
        Files.writeString(inputDir.resolve("oom.xml"), MOCK_OOM, StandardCharsets.UTF_8);
        Files.writeString(inputDir.resolve("verify.xml"), MOCK_OK, StandardCharsets.UTF_8);

        Path tikaConfigPath = PluginsTestHelper.getFileSystemFetcherConfig(
                "tika-config-shared-server.json", tmp, inputDir, tmp.resolve("output"), false);
        TikaJsonConfig tikaJsonConfig = TikaJsonConfig.load(tikaConfigPath);
        PipesConfig pipesConfig = PipesConfig.load(tikaJsonConfig);

        try (PipesParser pipesParser = PipesParser.load(tikaJsonConfig, pipesConfig, tikaConfigPath)) {
            ExecutorService executor = Executors.newFixedThreadPool(6);
            List<Future<PipesResult>> futures = new ArrayList<>();

            // Submit slow requests
            for (int i = 0; i < 4; i++) {
                final int idx = i;
                futures.add(executor.submit(() -> pipesParser.parse(new FetchEmitTuple(
                        "slow" + idx + ".xml",
                        new FetchKey(FETCHER_NAME, "slow" + idx + ".xml"),
                        new EmitKey(EMITTER_NAME, ""),
                        new Metadata(),
                        new ParseContext(),
                        FetchEmitTuple.ON_PARSE_EXCEPTION.SKIP))));
            }

            // Wait a bit for requests to start processing
            Thread.sleep(100);

            // Submit OOM request
            Future<PipesResult> oomFuture = executor.submit(() -> pipesParser.parse(new FetchEmitTuple(
                    "oom.xml",
                    new FetchKey(FETCHER_NAME, "oom.xml"),
                    new EmitKey(EMITTER_NAME, ""),
                    new Metadata(),
                    new ParseContext(),
                    FetchEmitTuple.ON_PARSE_EXCEPTION.SKIP)));

            // Wait for all concurrent requests to complete
            int oomCount = 0;
            int successCount = 0;
            int crashCount = 0;

            for (Future<PipesResult> future : futures) {
                PipesResult result = future.get();
                if (result.status() == PipesResult.RESULT_STATUS.OOM) {
                    oomCount++;
                } else if (result.isSuccess()) {
                    successCount++;
                } else if (result.isProcessCrash()) {
                    crashCount++;
                }
            }

            PipesResult oomResult = oomFuture.get();
            if (oomResult.status() == PipesResult.RESULT_STATUS.OOM) {
                oomCount++;
            }

            // The OOM should have been detected
            assertTrue(oomCount >= 1, "At least one OOM should be detected");

            executor.shutdown();

            // Now verify the server recovered and can process more requests
            PipesResult verifyResult = pipesParser.parse(new FetchEmitTuple(
                    "verify.xml",
                    new FetchKey(FETCHER_NAME, "verify.xml"),
                    new EmitKey(EMITTER_NAME, ""),
                    new Metadata(),
                    new ParseContext(),
                    FetchEmitTuple.ON_PARSE_EXCEPTION.SKIP));

            assertTrue(verifyResult.isSuccess(),
                    "After concurrent OOM, server should restart and process new request. Got: " + verifyResult.status());
        }
    }

    @Test
    public void testTimeoutRecovery(@TempDir Path tmp) throws Exception {
        // Test that timeout causes server crash and subsequent requests succeed after restart
        Path inputDir = setupInputDir(tmp);
        Files.writeString(inputDir.resolve("timeout.xml"), MOCK_TIMEOUT, StandardCharsets.UTF_8);
        Files.writeString(inputDir.resolve("ok.xml"), MOCK_OK, StandardCharsets.UTF_8);

        Path tikaConfigPath = PluginsTestHelper.getFileSystemFetcherConfig(
                "tika-config-shared-server.json", tmp, inputDir, tmp.resolve("output"), false);
        TikaJsonConfig tikaJsonConfig = TikaJsonConfig.load(tikaConfigPath);
        PipesConfig pipesConfig = PipesConfig.load(tikaJsonConfig);

        try (PipesParser pipesParser = PipesParser.load(tikaJsonConfig, pipesConfig, tikaConfigPath)) {
            // Trigger timeout
            PipesResult timeoutResult = pipesParser.parse(new FetchEmitTuple(
                    "timeout.xml",
                    new FetchKey(FETCHER_NAME, "timeout.xml"),
                    new EmitKey(EMITTER_NAME, ""),
                    new Metadata(),
                    new ParseContext(),
                    FetchEmitTuple.ON_PARSE_EXCEPTION.SKIP));

            // Timeout should be reported
            assertEquals(PipesResult.RESULT_STATUS.TIMEOUT, timeoutResult.status(),
                    "Timeout file should return TIMEOUT status");

            // Verify recovery
            PipesResult okResult = pipesParser.parse(new FetchEmitTuple(
                    "ok.xml",
                    new FetchKey(FETCHER_NAME, "ok.xml"),
                    new EmitKey(EMITTER_NAME, ""),
                    new Metadata(),
                    new ParseContext(),
                    FetchEmitTuple.ON_PARSE_EXCEPTION.SKIP));

            assertTrue(okResult.isSuccess(),
                    "After timeout, server should restart and subsequent request should succeed. Got: " + okResult.status());
        }
    }

    @Test
    public void testOomWithConcurrentSlowRequestsThenRecovery(@TempDir Path tmp) throws Exception {
        // Test: 5 slow requests + 1 OOM concurrently, then pause, then 5 OK requests concurrently
        // This verifies the server properly restarts and can handle new concurrent requests
        Path inputDir = setupInputDir(tmp);

        // Create slow files
        for (int i = 0; i < 5; i++) {
            Files.writeString(inputDir.resolve("slow" + i + ".xml"), MOCK_SLOW, StandardCharsets.UTF_8);
        }
        // Create OOM file
        Files.writeString(inputDir.resolve("oom.xml"), MOCK_OOM, StandardCharsets.UTF_8);
        // Create OK files for recovery test
        for (int i = 0; i < 5; i++) {
            Files.writeString(inputDir.resolve("ok" + i + ".xml"), MOCK_OK, StandardCharsets.UTF_8);
        }

        Path tikaConfigPath = PluginsTestHelper.getFileSystemFetcherConfig(
                "tika-config-shared-server.json", tmp, inputDir, tmp.resolve("output"), false);
        TikaJsonConfig tikaJsonConfig = TikaJsonConfig.load(tikaConfigPath);
        PipesConfig pipesConfig = PipesConfig.load(tikaJsonConfig);

        try (PipesParser pipesParser = PipesParser.load(tikaJsonConfig, pipesConfig, tikaConfigPath)) {
            ExecutorService executor = Executors.newFixedThreadPool(6);

            // Phase 1: Submit 5 slow requests + 1 OOM concurrently
            List<Future<PipesResult>> phase1Futures = new ArrayList<>();

            // Submit slow requests
            for (int i = 0; i < 5; i++) {
                final int idx = i;
                phase1Futures.add(executor.submit(() -> pipesParser.parse(new FetchEmitTuple(
                        "slow" + idx + ".xml",
                        new FetchKey(FETCHER_NAME, "slow" + idx + ".xml"),
                        new EmitKey(EMITTER_NAME, ""),
                        new Metadata(),
                        new ParseContext(),
                        FetchEmitTuple.ON_PARSE_EXCEPTION.SKIP))));
            }

            // Submit OOM request
            phase1Futures.add(executor.submit(() -> pipesParser.parse(new FetchEmitTuple(
                    "oom.xml",
                    new FetchKey(FETCHER_NAME, "oom.xml"),
                    new EmitKey(EMITTER_NAME, ""),
                    new Metadata(),
                    new ParseContext(),
                    FetchEmitTuple.ON_PARSE_EXCEPTION.SKIP))));

            // Wait for all phase 1 requests to complete
            int phase1OomCount = 0;
            int phase1SuccessCount = 0;
            int phase1CrashCount = 0;
            for (Future<PipesResult> future : phase1Futures) {
                PipesResult result = future.get();
                if (result.status() == PipesResult.RESULT_STATUS.OOM) {
                    phase1OomCount++;
                } else if (result.isSuccess()) {
                    phase1SuccessCount++;
                } else if (result.isProcessCrash()) {
                    phase1CrashCount++;
                }
            }

            // At least one OOM should have been detected
            assertTrue(phase1OomCount >= 1, "At least one OOM should be detected in phase 1");

            // Brief pause to ensure server has fully restarted
            Thread.sleep(500);

            // Phase 2: Submit 5 OK requests concurrently - all should succeed
            List<Future<PipesResult>> phase2Futures = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                final int idx = i;
                phase2Futures.add(executor.submit(() -> pipesParser.parse(new FetchEmitTuple(
                        "ok" + idx + ".xml",
                        new FetchKey(FETCHER_NAME, "ok" + idx + ".xml"),
                        new EmitKey(EMITTER_NAME, ""),
                        new Metadata(),
                        new ParseContext(),
                        FetchEmitTuple.ON_PARSE_EXCEPTION.SKIP))));
            }

            // All phase 2 requests must succeed to prove recovery
            int phase2SuccessCount = 0;
            for (Future<PipesResult> future : phase2Futures) {
                PipesResult result = future.get();
                if (result.isSuccess()) {
                    phase2SuccessCount++;
                    assertEquals("Test Author", result.emitData().getMetadataList().get(0).get("dc:creator"));
                }
            }

            executor.shutdown();

            // All 5 recovery requests must succeed
            assertEquals(5, phase2SuccessCount,
                    "All 5 phase 2 requests should succeed after server recovery. " +
                    "Phase 1 had: " + phase1OomCount + " OOMs, " + phase1SuccessCount + " successes, " +
                    phase1CrashCount + " crashes");
        }
    }

    private Path setupInputDir(Path tmp) throws Exception {
        Path inputDir = tmp.resolve("input");
        Files.createDirectories(inputDir);
        return inputDir;
    }
}
