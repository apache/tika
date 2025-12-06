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
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.apache.tika.config.TikaTaskTimeout;
import org.apache.tika.config.loader.TikaJsonConfig;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.metadata.filter.AttachmentCountingListFilter;
import org.apache.tika.metadata.filter.CompositeMetadataFilter;
import org.apache.tika.metadata.filter.MetadataFilter;
import org.apache.tika.metadata.filter.MockUpperCaseFilter;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.pipes.api.FetchEmitTuple;
import org.apache.tika.pipes.api.PipesResult;
import org.apache.tika.pipes.api.emitter.EmitKey;
import org.apache.tika.pipes.api.fetcher.FetchKey;

public class PipesClientTest {
    String fetcherName = "fsf";
    String testDoc = "testOverlappingText.pdf";


    private PipesClient init(Path tmp, String testFileName) throws Exception {
        Path tikaConfigPath = PluginsTestHelper.getFileSystemFetcherConfig(tmp, tmp.resolve("input"), tmp.resolve("output"));
        PluginsTestHelper.copyTestFilesToTmpInput(tmp, testFileName);

        TikaJsonConfig tikaJsonConfig = TikaJsonConfig.load(tikaConfigPath);
        PipesConfig pipesConfig = PipesConfig.load(tikaJsonConfig, tikaConfigPath);
        return new PipesClient(pipesConfig);
    }

    @Test
    public void testBasic(@TempDir Path tmp) throws Exception {
        PipesClient pipesClient = init(tmp, testDoc);

        PipesResult pipesResult = pipesClient.process(
                new FetchEmitTuple(testDoc, new FetchKey(fetcherName, testDoc),
                        new EmitKey(), new Metadata(), new ParseContext(), FetchEmitTuple.ON_PARSE_EXCEPTION.SKIP));
        Assertions.assertNotNull(pipesResult.emitData().getMetadataList());
        assertEquals(1, pipesResult.emitData().getMetadataList().size());
        Metadata metadata = pipesResult.emitData().getMetadataList().get(0);
        assertEquals("testOverlappingText.pdf", metadata.get(TikaCoreProperties.RESOURCE_NAME_KEY));
    }

    @Test
    public void testMetadataFilter(@TempDir Path tmp) throws Exception {
        ParseContext parseContext = new ParseContext();
        MetadataFilter metadataFilter = new CompositeMetadataFilter(List.of(new MockUpperCaseFilter()));
        parseContext.set(MetadataFilter.class, metadataFilter);
        PipesClient pipesClient = init(tmp, testDoc);
        PipesResult pipesResult = pipesClient.process(
                new FetchEmitTuple(testDoc, new FetchKey(fetcherName, testDoc),
                        new EmitKey(), new Metadata(), parseContext, FetchEmitTuple.ON_PARSE_EXCEPTION.SKIP));
        Assertions.assertNotNull(pipesResult.emitData().getMetadataList());
        assertEquals(1, pipesResult.emitData().getMetadataList().size());
        Metadata metadata = pipesResult.emitData().getMetadataList().get(0);
        assertEquals("TESTOVERLAPPINGTEXT.PDF", metadata.get(TikaCoreProperties.RESOURCE_NAME_KEY));
    }

    @Test
    public void testMetadataListFilter(@TempDir Path tmp) throws Exception {
        ParseContext parseContext = new ParseContext();
        MetadataFilter metadataFilter = new CompositeMetadataFilter(List.of(new AttachmentCountingListFilter()));
        parseContext.set(MetadataFilter.class, metadataFilter);

        String testFile = "mock-embedded.xml";

        PipesClient pipesClient = init(tmp, testFile);

        PipesResult pipesResult = pipesClient.process(
                new FetchEmitTuple(testFile, new FetchKey(fetcherName, testFile),
                        new EmitKey(), new Metadata(), parseContext, FetchEmitTuple.ON_PARSE_EXCEPTION.SKIP));
        Assertions.assertNotNull(pipesResult.emitData().getMetadataList());
        assertEquals(5, pipesResult.emitData().getMetadataList().size());
        Metadata metadata = pipesResult.emitData().getMetadataList().get(0);
        assertEquals(4, Integer.parseInt(metadata.get("X-TIKA:attachment_count")));
    }

    @Test
    public void testTimeout(@TempDir Path tmp) throws Exception {
        //TODO -- figure out how to test pipes server timeout alone
        //I did both manually during development, but unit tests are better. :D
        ParseContext parseContext = new ParseContext();
        parseContext.set(TikaTaskTimeout.class, new TikaTaskTimeout(1000));
        MetadataFilter metadataFilter = new CompositeMetadataFilter(List.of(new AttachmentCountingListFilter()));
        parseContext.set(MetadataFilter.class, metadataFilter);

        String testFile = "mock-timeout-10s.xml";
        PipesClient pipesClient = init(tmp, testFile);
        PipesResult pipesResult = pipesClient.process(
                new FetchEmitTuple(testFile, new FetchKey(fetcherName, testFile),
                        new EmitKey(), new Metadata(), parseContext, FetchEmitTuple.ON_PARSE_EXCEPTION.SKIP));
        assertEquals(PipesResults.TIMEOUT.status(), pipesResult.status());
    }

    @Test
    public void testRuntimeTimeoutChange(@TempDir Path tmp) throws Exception {
        // Test that TikaTaskTimeout can be changed at runtime via ParseContext
        // Use a mock file with 3 second delay
        Path inputDir = tmp.resolve("input");
        Files.createDirectories(inputDir);
        String mockContent = "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>" + "<mock>" +
                "<metadata action=\"add\" name=\"dc:creator\">Test</metadata>" +
                "<write element=\"p\">main_content</write>" +
                "<fakeload millis=\"3000\" cpu=\"1\" mb=\"10\"/>" +
                "</mock>";
        String testFile = "mock-3s-delay.xml";
        Files.write(inputDir.resolve(testFile), mockContent.getBytes(StandardCharsets.UTF_8));

        Path tikaConfigPath = PluginsTestHelper.getFileSystemFetcherConfig(tmp, inputDir, tmp.resolve("output"));
        TikaJsonConfig tikaJsonConfig = TikaJsonConfig.load(tikaConfigPath);
        PipesConfig pipesConfig = PipesConfig.load(tikaJsonConfig, tikaConfigPath);

        try (PipesClient pipesClient = new PipesClient(pipesConfig)) {
            // First test: Short timeout (1 second) - should timeout
            ParseContext shortTimeoutContext = new ParseContext();
            shortTimeoutContext.set(TikaTaskTimeout.class, new TikaTaskTimeout(1000));

            PipesResult timeoutResult = pipesClient.process(
                    new FetchEmitTuple(testFile, new FetchKey(fetcherName, testFile),
                            new EmitKey(), new Metadata(), shortTimeoutContext,
                            FetchEmitTuple.ON_PARSE_EXCEPTION.SKIP));

            assertEquals(PipesResult.RESULT_STATUS.TIMEOUT, timeoutResult.status(),
                    "Should timeout with 1 second timeout on 3 second file");

            // Second test: Long timeout (10 seconds) - should succeed
            ParseContext longTimeoutContext = new ParseContext();
            longTimeoutContext.set(TikaTaskTimeout.class, new TikaTaskTimeout(10000));

            PipesResult successResult = pipesClient.process(
                    new FetchEmitTuple(testFile, new FetchKey(fetcherName, testFile),
                            new EmitKey(), new Metadata(), longTimeoutContext,
                            FetchEmitTuple.ON_PARSE_EXCEPTION.SKIP));

            assertEquals(PipesResult.RESULT_STATUS.PARSE_SUCCESS, successResult.status(),
                    "Should succeed with 10 second timeout on 3 second file");
            Assertions.assertNotNull(successResult.emitData().getMetadataList());
            assertTrue(successResult.emitData().getMetadataList().size() > 0);
        }
    }

    @Test
    public void testStartupFailure(@TempDir Path tmp) throws Exception {
        // Create a config that references a non-existent fetcher plugin
        // This should cause PipesServer to fail during initialization
        Path tikaConfigPath = PluginsTestHelper.getFileSystemFetcherConfig(
                "tika-config-bad-class.json", tmp);

        TikaJsonConfig tikaJsonConfig = TikaJsonConfig.load(tikaConfigPath);
        PipesConfig pipesConfig = PipesConfig.load(tikaJsonConfig, tikaConfigPath);

        try (PipesClient pipesClient = new PipesClient(pipesConfig)) {
            FetchEmitTuple tuple = new FetchEmitTuple(testDoc,
                    new FetchKey("bad-fetcher", testDoc),
                    new EmitKey(), new Metadata(), new ParseContext(),
                    FetchEmitTuple.ON_PARSE_EXCEPTION.SKIP);

            PipesResult pipesResult = pipesClient.process(tuple);
            assertEquals(PipesResult.RESULT_STATUS.FAILED_TO_INITIALIZE, pipesResult.status());
            assertTrue(pipesResult.isApplicationError(), "FAILED_TO_INITIALIZE should be an application error");
            Assertions.assertNotNull(pipesResult.message(), "Should have error message from server");
            System.out.println(pipesResult.message());
            assertTrue(pipesResult.message().contains("non-existent-fetcher-plugin") ||
                      pipesResult.message().contains("TikaConfigException") ||
                      pipesResult.message().contains("error") ||
                      pipesResult.message().contains("Exception"),
                      "Error message should contain details about the failure");
        }
    }

    @Test
    public void testJvmStartupFailure(@TempDir Path tmp) throws Exception {
        // Create a config with bad JVM arguments
        // This should cause the JVM process to fail before it can connect
        Path tikaConfigPath = PluginsTestHelper.getFileSystemFetcherConfig(
                "tika-config-bad-jvm-args.json", tmp);
        PluginsTestHelper.copyTestFilesToTmpInput(tmp, testDoc);

        TikaJsonConfig tikaJsonConfig = TikaJsonConfig.load(tikaConfigPath);
        PipesConfig pipesConfig = PipesConfig.load(tikaJsonConfig, tikaConfigPath);

        try (PipesClient pipesClient = new PipesClient(pipesConfig)) {
            FetchEmitTuple tuple = new FetchEmitTuple(testDoc,
                    new FetchKey("fsf", testDoc),
                    new EmitKey(), new Metadata(), new ParseContext(),
                    FetchEmitTuple.ON_PARSE_EXCEPTION.SKIP);

            PipesResult pipesResult = pipesClient.process(tuple);
            assertEquals(PipesResult.RESULT_STATUS.FAILED_TO_INITIALIZE, pipesResult.status());
            assertTrue(pipesResult.isApplicationError(), "FAILED_TO_INITIALIZE should be an application error");
            Assertions.assertNotNull(pipesResult.message(), "Should have error message");
            assertTrue(pipesResult.message().contains("exit code") ||
                            pipesResult.message().contains("JVM") ||
                            pipesResult.message().contains("Process failed"),
                    "Error message should indicate process failure: " + pipesResult.message());
        }
    }

    @Test
    public void testFailureBeforeJvm(@TempDir Path tmp) throws Exception {
        // Create a config with bad application path
        // This will cause failure before the process begins
        Path tikaConfigPath = PluginsTestHelper.getFileSystemFetcherConfig(
                "tika-config-bad-java-path.json", tmp);
        PluginsTestHelper.copyTestFilesToTmpInput(tmp, testDoc);

        TikaJsonConfig tikaJsonConfig = TikaJsonConfig.load(tikaConfigPath);
        PipesConfig pipesConfig = PipesConfig.load(tikaJsonConfig, tikaConfigPath);

        try (PipesClient pipesClient = new PipesClient(pipesConfig)) {
            FetchEmitTuple tuple = new FetchEmitTuple(testDoc,
                    new FetchKey("fsf", testDoc),
                    new EmitKey(), new Metadata(), new ParseContext(),
                    FetchEmitTuple.ON_PARSE_EXCEPTION.SKIP);

            PipesResult pipesResult = pipesClient.process(tuple);
            assertEquals(PipesResult.RESULT_STATUS.FAILED_TO_INITIALIZE, pipesResult.status());
            assertTrue(pipesResult.isApplicationError(), "FAILED_TO_INITIALIZE should be an application error");
            Assertions.assertNotNull(pipesResult.message(), "Should have error message");
            assertTrue(pipesResult.message().contains("No such file"),
                    "Error message should indicate process failure: " + pipesResult.message());
        }
    }

    @Test
    public void testCrashDuringDetection(@TempDir Path tmp) throws Exception {
        // Test that crashes during pre-parse detection phase are handled correctly
        // The detector will throw RuntimeException which should NOT be caught in pre-parse
        Path tikaConfigPath = PluginsTestHelper.getFileSystemFetcherConfig(
                "tika-config-crashing-detector.json", tmp);
        PluginsTestHelper.copyTestFilesToTmpInput(tmp, testDoc);

        TikaJsonConfig tikaJsonConfig = TikaJsonConfig.load(tikaConfigPath);
        PipesConfig pipesConfig = PipesConfig.load(tikaJsonConfig, tikaConfigPath);

        try (PipesClient pipesClient = new PipesClient(pipesConfig)) {
            FetchEmitTuple tuple = new FetchEmitTuple(testDoc,
                    new FetchKey("fsf", testDoc),
                    new EmitKey(), new Metadata(), new ParseContext(),
                    FetchEmitTuple.ON_PARSE_EXCEPTION.SKIP);

            PipesResult pipesResult = pipesClient.process(tuple);

            // Should be UNSPECIFIED_CRASH because RuntimeException during detection
            // is not caught by pre-parse IOException handler
            assertEquals(PipesResult.RESULT_STATUS.UNSPECIFIED_CRASH, pipesResult.status());
            assertTrue(pipesResult.isProcessCrash() || pipesResult.isApplicationError(),
                    "Should be categorized as a crash or application error");

            // Should have error message about the crash
            Assertions.assertNotNull(pipesResult.message(), "Should have error message");
            assertTrue(pipesResult.message().contains("problem reading response"),
                    "Error message should mention the detection crash: " + pipesResult.message());

            // Note: Because crash happens during pre-parse (before intermediate result is sent),
            // the emitData will have minimal metadata - just what was captured before the crash
        }
    }

    @Test
    public void testSocketTimeout(@TempDir Path tmp) throws Exception {
        // Test socket timeout when heartbeats are sent too slowly
        // Config has heartbeatIntervalMs=10000 (10 seconds) but socketTimeoutMs=3000 (3 seconds)
        // This simulates a server that appears unresponsive (different from parse timeout)
        // NOTE: This is an invalid configuration that would never be used in production,
        // but we allow it for testing via system property

        // Create input directory and mock XML file with 10 second fakeload
        Path inputDir = tmp.resolve("input");
        Files.createDirectories(inputDir);
        String mockContent = "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>" + "<mock>" +
                "<metadata action=\"add\" name=\"dc:creator\">Test</metadata>" +
                "<write element=\"p\">main_content</write>" +
                "<fakeload millis=\"10000\" cpu=\"1\" mb=\"10\"/>" +
                "</mock>";
        String testFile = "mock-slow.xml";
        Files.write(inputDir.resolve(testFile), mockContent.getBytes(StandardCharsets.UTF_8));

        Path tikaConfigPath = PluginsTestHelper.getFileSystemFetcherConfig(
                "tika-config-timeout-lt-heartbeat.json", tmp);

        TikaJsonConfig tikaJsonConfig = TikaJsonConfig.load(tikaConfigPath);
        PipesConfig pipesConfig = PipesConfig.load(tikaJsonConfig, tikaConfigPath);

        // Verify the misconfiguration that triggers socket timeout
        assertEquals(3000, pipesConfig.getSocketTimeoutMs(), "Socket timeout should be 3 seconds");
        assertEquals(10000, pipesConfig.getHeartbeatIntervalMs(), "Heartbeat interval should be 10 seconds");
        assertTrue(pipesConfig.getHeartbeatIntervalMs() > pipesConfig.getSocketTimeoutMs(),
                "Test requires heartbeat > socket timeout to trigger timeout");

        // The config file includes -Dtika.pipes.allowInvalidHeartbeat=true in forkedJvmArgs
        // to allow this invalid configuration for testing only
        try (PipesClient pipesClient = new PipesClient(pipesConfig)) {
            FetchEmitTuple tuple = new FetchEmitTuple(testFile,
                    new FetchKey("fsf", testFile),
                    new EmitKey(), new Metadata(), new ParseContext(),
                    FetchEmitTuple.ON_PARSE_EXCEPTION.SKIP);

            long startTime = System.currentTimeMillis();
            PipesResult pipesResult = pipesClient.process(tuple);
            long elapsed = System.currentTimeMillis() - startTime;

            // Should timeout due to socket timeout (no heartbeats received within socketTimeoutMs)
            assertEquals(PipesResult.RESULT_STATUS.TIMEOUT, pipesResult.status(),
                    "Should timeout when socket times out");

            // Should timeout relatively quickly (within ~5 seconds including overhead)
            // Socket timeout is 3 seconds, but allow some buffer for processing
            assertTrue(elapsed < 10000,
                    "Socket timeout should occur quickly (elapsed: " + elapsed + "ms)");

            // Verify it's a process crash category (socket timeout means process isn't responding)
            assertTrue(pipesResult.isProcessCrash(),
                    "Socket timeout should be categorized as process crash");
        }
    }
}
