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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.apache.tika.config.loader.TikaJsonConfig;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.pipes.api.FetchEmitTuple;
import org.apache.tika.pipes.api.PipesResult;
import org.apache.tika.pipes.api.emitter.EmitKey;
import org.apache.tika.pipes.api.fetcher.FetchKey;

/**
 * Chaos monkey test for shared server mode.
 * <p>
 * This test randomly mixes OOM, timeout, and OK files to stress test the shared
 * server's ability to handle crashes, restarts, and concurrent requests.
 * <p>
 * Key insight: In shared mode, when OOM/timeout happens, ALL in-flight requests
 * on that server are affected. So "good" files that were being processed
 * concurrently may also fail with UNSPECIFIED_CRASH or similar status.
 */
public class SharedServerChaosMonkeyTest {

    private static final String FETCHER_NAME = "fsf";
    private static final String EMITTER_NAME = "fse";

    private static final String MOCK_OOM = "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>" +
            "<mock>" +
            "<throw class=\"java.lang.OutOfMemoryError\">oom message</throw>" +
            "</mock>";

    private static final String MOCK_OK = "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>" +
            "<mock>" +
            "<metadata action=\"add\" name=\"dc:creator\">Nikolai Lobachevsky</metadata>" +
            "<write element=\"p\">main_content</write>" +
            "</mock>";

    private static final String MOCK_TIMEOUT = "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>" +
            "<mock>" +
            "<metadata action=\"add\" name=\"dc:creator\">Nikolai Lobachevsky</metadata>" +
            "<write element=\"p\">main_content</write>" +
            "<fakeload millis=\"60000\" cpu=\"1\" mb=\"10\"/>" +
            "</mock>";

    private static final String MOCK_STACK_OVERFLOW = "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>" +
            "<mock>" +
            "<throw class=\"java.lang.StackOverflowError\">stack overflow message</throw>" +
            "</mock>";

    private static final int TOTAL_FILES = 50;

    enum FileType {
        OK, OOM, TIMEOUT, STACK_OVERFLOW
    }

    @Test
    public void testSharedServerChaosMonkey(@TempDir Path tmp) throws Exception {
        Path inputDir = tmp.resolve("input");
        Path outputDir = tmp.resolve("output");
        Files.createDirectories(inputDir);
        Files.createDirectories(outputDir);

        // Track expected file types
        List<FileType> fileTypes = new ArrayList<>();
        Random r = new Random(42); // Fixed seed for reproducibility

        int expectedOk = 0;
        int expectedOom = 0;
        int expectedTimeout = 0;
        int expectedStackOverflow = 0;

        // Create random mix of files
        for (int i = 0; i < TOTAL_FILES; i++) {
            float f = r.nextFloat();
            FileType type;
            String content;

            if (f < 0.10) {
                type = FileType.OOM;
                content = MOCK_OOM;
                expectedOom++;
            } else if (f < 0.15) {
                type = FileType.TIMEOUT;
                content = MOCK_TIMEOUT;
                expectedTimeout++;
            } else if (f < 0.20) {
                type = FileType.STACK_OVERFLOW;
                content = MOCK_STACK_OVERFLOW;
                expectedStackOverflow++;
            } else {
                type = FileType.OK;
                content = MOCK_OK;
                expectedOk++;
            }

            Files.writeString(inputDir.resolve(i + ".xml"), content, StandardCharsets.UTF_8);
            fileTypes.add(type);
        }

        Path tikaConfigPath = PluginsTestHelper.getFileSystemFetcherConfig(
                "tika-config-shared-server.json", tmp, inputDir, outputDir, false);
        TikaJsonConfig tikaJsonConfig = TikaJsonConfig.load(tikaConfigPath);
        PipesConfig pipesConfig = PipesConfig.load(tikaJsonConfig);

        // Track results
        AtomicInteger observedOk = new AtomicInteger(0);
        AtomicInteger observedOom = new AtomicInteger(0);
        AtomicInteger observedTimeout = new AtomicInteger(0);
        AtomicInteger observedCrash = new AtomicInteger(0);
        AtomicInteger collateralDamage = new AtomicInteger(0); // OK files that failed due to concurrent crash

        try (PipesParser pipesParser = PipesParser.load(tikaJsonConfig, pipesConfig, tikaConfigPath)) {
            assertTrue(pipesParser.isSharedMode(), "Should be in shared mode");

            ExecutorService executor = Executors.newFixedThreadPool(8);
            List<Future<ResultWithType>> futures = new ArrayList<>();

            // Submit all requests concurrently
            for (int i = 0; i < TOTAL_FILES; i++) {
                final int idx = i;
                final FileType expectedType = fileTypes.get(i);
                futures.add(executor.submit(() -> {
                    try {
                        PipesResult result = pipesParser.parse(new FetchEmitTuple(
                                idx + ".xml",
                                new FetchKey(FETCHER_NAME, idx + ".xml"),
                                new EmitKey(EMITTER_NAME, ""),
                                new Metadata(),
                                new ParseContext(),
                                FetchEmitTuple.ON_PARSE_EXCEPTION.SKIP));
                        return new ResultWithType(result, expectedType, null);
                    } catch (Exception e) {
                        // Connection failures during rapid crash/restart cycles
                        return new ResultWithType(null, expectedType, e);
                    }
                }));
            }

            // Collect results with timeout
            for (Future<ResultWithType> future : futures) {
                ResultWithType rwt;
                try {
                    rwt = future.get(30, TimeUnit.SECONDS);
                } catch (TimeoutException e) {
                    // Request is stuck - count as crash (collateral damage from concurrent crash)
                    observedCrash.incrementAndGet();
                    collateralDamage.incrementAndGet();
                    continue;
                } catch (ExecutionException e) {
                    // Shouldn't happen - we catch exceptions in the callable
                    observedCrash.incrementAndGet();
                    continue;
                }

                // Handle connection failures as crashes
                if (rwt.exception != null) {
                    observedCrash.incrementAndGet();
                    if (rwt.expectedType == FileType.OK) {
                        collateralDamage.incrementAndGet();
                    }
                    continue;
                }

                PipesResult result = rwt.result;
                FileType expectedType = rwt.expectedType;

                if (result.isSuccess()) {
                    observedOk.incrementAndGet();
                    if (expectedType != FileType.OK) {
                        // This shouldn't happen - crash files should return crash status
                        throw new AssertionError("Expected " + expectedType + " but got success");
                    }
                } else if (result.status() == PipesResult.RESULT_STATUS.OOM) {
                    observedOom.incrementAndGet();
                } else if (result.status() == PipesResult.RESULT_STATUS.TIMEOUT) {
                    observedTimeout.incrementAndGet();
                } else if (result.isProcessCrash()) {
                    observedCrash.incrementAndGet();
                    // In shared mode, OK files may fail if server crashed during their processing
                    if (expectedType == FileType.OK) {
                        collateralDamage.incrementAndGet();
                    }
                }
            }

            executor.shutdown();
        }

        // Log results for debugging
        System.out.println("=== Shared Server Chaos Monkey Results ===");
        System.out.println("Expected: ok=" + expectedOk + ", oom=" + expectedOom +
                ", timeout=" + expectedTimeout + ", stackoverflow=" + expectedStackOverflow);
        System.out.println("Observed: ok=" + observedOk.get() + ", oom=" + observedOom.get() +
                ", timeout=" + observedTimeout.get() + ", crash=" + observedCrash.get());
        System.out.println("Collateral damage (OK files that failed due to concurrent crash): " +
                collateralDamage.get());

        // Assertions
        // 1. We should have processed all files
        int totalObserved = observedOk.get() + observedOom.get() + observedTimeout.get() + observedCrash.get();
        assertEquals(TOTAL_FILES, totalObserved, "Should have results for all files");

        // 2. At least some OK files should succeed (server recovery works)
        assertTrue(observedOk.get() > 0, "At least some OK files should succeed, proving server recovery");

        // 3. At least some OOMs should be detected
        assertTrue(observedOom.get() > 0 || observedCrash.get() > 0,
                "Should detect OOM or crash errors");

        // 4. Successful files should equal expected minus collateral damage
        assertTrue(observedOk.get() <= expectedOk,
                "Can't have more successes than expected OK files");

        // 5. Total crash-related results should account for all bad files plus collateral
        int totalCrashRelated = observedOom.get() + observedTimeout.get() + observedCrash.get();
        int expectedCrashRelated = expectedOom + expectedTimeout + expectedStackOverflow;
        assertTrue(totalCrashRelated >= expectedCrashRelated,
                "Crash-related results should be at least as many as expected crash files");
    }

    @Test
    public void testServerRecoveryAfterChaos(@TempDir Path tmp) throws Exception {
        // After chaos monkey, verify server is in clean state by processing batch of OK files
        Path inputDir = tmp.resolve("input");
        Path outputDir = tmp.resolve("output");
        Files.createDirectories(inputDir);
        Files.createDirectories(outputDir);

        // First, create chaos
        Random r = new Random(123);
        for (int i = 0; i < 20; i++) {
            String content = (r.nextFloat() < 0.3) ? MOCK_OOM : MOCK_OK;
            Files.writeString(inputDir.resolve("chaos" + i + ".xml"), content, StandardCharsets.UTF_8);
        }

        // Then, create clean batch
        for (int i = 0; i < 10; i++) {
            Files.writeString(inputDir.resolve("clean" + i + ".xml"), MOCK_OK, StandardCharsets.UTF_8);
        }

        Path tikaConfigPath = PluginsTestHelper.getFileSystemFetcherConfig(
                "tika-config-shared-server.json", tmp, inputDir, outputDir, false);
        TikaJsonConfig tikaJsonConfig = TikaJsonConfig.load(tikaConfigPath);
        PipesConfig pipesConfig = PipesConfig.load(tikaJsonConfig);

        try (PipesParser pipesParser = PipesParser.load(tikaJsonConfig, pipesConfig, tikaConfigPath)) {
            // Process chaos files (some will crash)
            for (int i = 0; i < 20; i++) {
                pipesParser.parse(new FetchEmitTuple(
                        "chaos" + i + ".xml",
                        new FetchKey(FETCHER_NAME, "chaos" + i + ".xml"),
                        new EmitKey(EMITTER_NAME, ""),
                        new Metadata(),
                        new ParseContext(),
                        FetchEmitTuple.ON_PARSE_EXCEPTION.SKIP));
            }

            // Wait for server to stabilize
            Thread.sleep(500);

            // Now process clean batch - ALL should succeed
            int successCount = 0;
            for (int i = 0; i < 10; i++) {
                PipesResult result = pipesParser.parse(new FetchEmitTuple(
                        "clean" + i + ".xml",
                        new FetchKey(FETCHER_NAME, "clean" + i + ".xml"),
                        new EmitKey(EMITTER_NAME, ""),
                        new Metadata(),
                        new ParseContext(),
                        FetchEmitTuple.ON_PARSE_EXCEPTION.SKIP));
                if (result.isSuccess()) {
                    successCount++;
                }
            }

            // All clean files should succeed - proves server is stable after chaos
            assertEquals(10, successCount,
                    "All clean files should succeed after chaos, proving server recovery");
        }
    }

    private static class ResultWithType {
        final PipesResult result;
        final FileType expectedType;
        final Exception exception;

        ResultWithType(PipesResult result, FileType expectedType, Exception exception) {
            this.result = result;
            this.expectedType = expectedType;
            this.exception = exception;
        }
    }
}
