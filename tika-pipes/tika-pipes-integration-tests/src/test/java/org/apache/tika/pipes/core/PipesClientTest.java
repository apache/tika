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

import org.apache.tika.config.ConfigContainer;
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
import org.apache.tika.serialization.ParseContextUtils;

public class PipesClientTest {
    String fetcherName = "fsf";
    String emitterName = "fse";
    String testDoc = "testOverlappingText.pdf";


    private PipesClient init(Path tmp, String testFileName) throws Exception {
        Path tikaConfigPath = PluginsTestHelper.getFileSystemFetcherConfig(tmp, tmp.resolve("input"), tmp.resolve("output"));
        PluginsTestHelper.copyTestFilesToTmpInput(tmp, testFileName);

        TikaJsonConfig tikaJsonConfig = TikaJsonConfig.load(tikaConfigPath);
        PipesConfig pipesConfig = PipesConfig.load(tikaJsonConfig);
        return new PipesClient(pipesConfig, tikaConfigPath);
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
    public void testMetadataFilterFromJsonConfig(@TempDir Path tmp) throws Exception {
        // Test that metadata filters specified as JSON array in ConfigContainer
        // are properly resolved and applied during pipe processing.
        // This tests the full serialization/deserialization flow.
        ParseContext parseContext = new ParseContext();
        ConfigContainer configContainer = new ConfigContainer();
        configContainer.set("metadata-filters", """
            [
              "mock-upper-case-filter"
            ]
        """);
        parseContext.set(ConfigContainer.class, configContainer);

        // Resolve the config to actual MetadataFilter instances
        ParseContextUtils.resolveAll(parseContext, PipesClientTest.class.getClassLoader());

        // Verify the filter was resolved
        MetadataFilter resolvedFilter = parseContext.get(MetadataFilter.class);
        Assertions.assertNotNull(resolvedFilter, "MetadataFilter should be resolved from ConfigContainer");
        assertEquals(CompositeMetadataFilter.class, resolvedFilter.getClass());

        PipesClient pipesClient = init(tmp, testDoc);
        PipesResult pipesResult = pipesClient.process(
                new FetchEmitTuple(testDoc, new FetchKey(fetcherName, testDoc),
                        new EmitKey(), new Metadata(), parseContext, FetchEmitTuple.ON_PARSE_EXCEPTION.SKIP));

        Assertions.assertNotNull(pipesResult.emitData().getMetadataList());
        assertEquals(1, pipesResult.emitData().getMetadataList().size());
        Metadata metadata = pipesResult.emitData().getMetadataList().get(0);
        // MockUpperCaseFilter uppercases all metadata values
        assertEquals("TESTOVERLAPPINGTEXT.PDF", metadata.get(TikaCoreProperties.RESOURCE_NAME_KEY));
    }

    @Test
    public void testMultipleMetadataFiltersFromJsonConfig(@TempDir Path tmp) throws Exception {
        // Test multiple filters specified as JSON array
        ParseContext parseContext = new ParseContext();
        ConfigContainer configContainer = new ConfigContainer();
        configContainer.set("metadata-filters", """
            [
              "attachment-counting-list-filter",
              "mock-upper-case-filter"
            ]
        """);
        parseContext.set(ConfigContainer.class, configContainer);

        // Resolve the config to actual MetadataFilter instances
        ParseContextUtils.resolveAll(parseContext, PipesClientTest.class.getClassLoader());

        String testFile = "mock-embedded.xml";
        PipesClient pipesClient = init(tmp, testFile);

        PipesResult pipesResult = pipesClient.process(
                new FetchEmitTuple(testFile, new FetchKey(fetcherName, testFile),
                        new EmitKey(), new Metadata(), parseContext, FetchEmitTuple.ON_PARSE_EXCEPTION.SKIP));

        Assertions.assertNotNull(pipesResult.emitData().getMetadataList());
        assertEquals(5, pipesResult.emitData().getMetadataList().size());
        Metadata metadata = pipesResult.emitData().getMetadataList().get(0);

        // AttachmentCountingListFilter should have added the count
        assertEquals(4, Integer.parseInt(metadata.get("X-TIKA:attachment_count")));

        // MockUpperCaseFilter should have uppercased the resource name
        assertEquals("MOCK-EMBEDDED.XML", metadata.get(TikaCoreProperties.RESOURCE_NAME_KEY));
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
        PipesConfig pipesConfig = PipesConfig.load(tikaJsonConfig);

        try (PipesClient pipesClient = new PipesClient(pipesConfig, tikaConfigPath)) {
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
        PipesConfig pipesConfig = PipesConfig.load(tikaJsonConfig);

        try (PipesClient pipesClient = new PipesClient(pipesConfig, tikaConfigPath)) {
            FetchEmitTuple tuple = new FetchEmitTuple(testDoc,
                    new FetchKey("bad-fetcher", testDoc),
                    new EmitKey(), new Metadata(), new ParseContext(),
                    FetchEmitTuple.ON_PARSE_EXCEPTION.SKIP);

            PipesResult pipesResult = pipesClient.process(tuple);
            assertEquals(PipesResult.RESULT_STATUS.FAILED_TO_INITIALIZE, pipesResult.status());
            assertTrue(pipesResult.isFatal(), "FAILED_TO_INITIALIZE should be a fatal error");
            Assertions.assertNotNull(pipesResult.message(), "Should have error message from server");
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
        PipesConfig pipesConfig = PipesConfig.load(tikaJsonConfig);

        try (PipesClient pipesClient = new PipesClient(pipesConfig, tikaConfigPath)) {
            FetchEmitTuple tuple = new FetchEmitTuple(testDoc,
                    new FetchKey("fsf", testDoc),
                    new EmitKey(), new Metadata(), new ParseContext(),
                    FetchEmitTuple.ON_PARSE_EXCEPTION.SKIP);

            PipesResult pipesResult = pipesClient.process(tuple);
            assertEquals(PipesResult.RESULT_STATUS.FAILED_TO_INITIALIZE, pipesResult.status());
            assertTrue(pipesResult.isFatal(), "FAILED_TO_INITIALIZE should be a fatal error");
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
        PipesConfig pipesConfig = PipesConfig.load(tikaJsonConfig);

        try (PipesClient pipesClient = new PipesClient(pipesConfig, tikaConfigPath)) {
            FetchEmitTuple tuple = new FetchEmitTuple(testDoc,
                    new FetchKey("fsf", testDoc),
                    new EmitKey(), new Metadata(), new ParseContext(),
                    FetchEmitTuple.ON_PARSE_EXCEPTION.SKIP);

            PipesResult pipesResult = pipesClient.process(tuple);
            assertEquals(PipesResult.RESULT_STATUS.FAILED_TO_INITIALIZE, pipesResult.status());
            assertTrue(pipesResult.isFatal(), "FAILED_TO_INITIALIZE should be a fatal error");
            Assertions.assertNotNull(pipesResult.message(), "Should have error message");
            assertTrue(pipesResult.message().contains("No such file") || pipesResult.message().contains("thisIsntJava"),
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
        PipesConfig pipesConfig = PipesConfig.load(tikaJsonConfig);

        try (PipesClient pipesClient = new PipesClient(pipesConfig, tikaConfigPath)) {
            FetchEmitTuple tuple = new FetchEmitTuple(testDoc,
                    new FetchKey("fsf", testDoc),
                    new EmitKey(), new Metadata(), new ParseContext(),
                    FetchEmitTuple.ON_PARSE_EXCEPTION.SKIP);

            PipesResult pipesResult = pipesClient.process(tuple);

            // Should be UNSPECIFIED_CRASH because RuntimeException during detection
            // is not caught by pre-parse IOException handler
            assertEquals(PipesResult.RESULT_STATUS.UNSPECIFIED_CRASH, pipesResult.status());
            assertTrue(pipesResult.isProcessCrash(),
                    "Should be categorized as a process crash");

            // Should have error message about the crash
            Assertions.assertNotNull(pipesResult.message(), "Should have error message");
            assertTrue(pipesResult.message().contains("problem reading response") |
                    pipesResult.message().contains("SocketException"),
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
        PipesConfig pipesConfig = PipesConfig.load(tikaJsonConfig);

        // Verify the misconfiguration that triggers socket timeout
        assertEquals(3000, pipesConfig.getSocketTimeoutMs(), "Socket timeout should be 3 seconds");
        assertEquals(10000, pipesConfig.getHeartbeatIntervalMs(), "Heartbeat interval should be 10 seconds");
        assertTrue(pipesConfig.getHeartbeatIntervalMs() > pipesConfig.getSocketTimeoutMs(),
                "Test requires heartbeat > socket timeout to trigger timeout");

        // The config file includes -Dtika.pipes.allowInvalidHeartbeat=true in forkedJvmArgs
        // to allow this invalid configuration for testing only
        try (PipesClient pipesClient = new PipesClient(pipesConfig, tikaConfigPath)) {
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

    @Test
    public void testParseSuccessWithException(@TempDir Path tmp) throws Exception {
        // Test PARSE_SUCCESS_WITH_EXCEPTION status
        // This occurs when parsing completes with some content but throws a non-fatal exception
        Path inputDir = tmp.resolve("input");
        Files.createDirectories(inputDir);

        // Mock file that writes content then throws IOException
        String mockContent = "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>" + "<mock>" +
                "<metadata action=\"add\" name=\"dc:creator\">Test Author</metadata>" +
                "<write element=\"p\">Some content before exception</write>" +
                "<throw class=\"java.io.IOException\">Non-fatal parse exception</throw>" +
                "</mock>";
        String testFile = "mock-parse-exception.xml";
        Files.write(inputDir.resolve(testFile), mockContent.getBytes(StandardCharsets.UTF_8));

        Path tikaConfigPath = PluginsTestHelper.getFileSystemFetcherConfig(tmp, inputDir, tmp.resolve("output"));
        TikaJsonConfig tikaJsonConfig = TikaJsonConfig.load(tikaConfigPath);
        PipesConfig pipesConfig = PipesConfig.load(tikaJsonConfig);

        try (PipesClient pipesClient = new PipesClient(pipesConfig, tikaConfigPath)) {
            FetchEmitTuple tuple = new FetchEmitTuple(testFile,
                    new FetchKey(fetcherName, testFile),
                    new EmitKey(emitterName, ""), new Metadata(), new ParseContext(),
                    FetchEmitTuple.ON_PARSE_EXCEPTION.EMIT);

            PipesResult pipesResult = pipesClient.process(tuple);

            // Should be PARSE_SUCCESS_WITH_EXCEPTION because content was produced despite exception
            assertEquals(PipesResult.RESULT_STATUS.PARSE_SUCCESS_WITH_EXCEPTION, pipesResult.status(),
                    "Should return PARSE_SUCCESS_WITH_EXCEPTION when parse throws but produces content");

            // Verify it's still categorized as SUCCESS
            assertTrue(pipesResult.isSuccess(), "PARSE_SUCCESS_WITH_EXCEPTION should be success category");

            // Verify we got the metadata before the exception
            Assertions.assertNotNull(pipesResult.emitData().getMetadataList());
            assertTrue(pipesResult.emitData().getMetadataList().size() > 0);
            Metadata metadata = pipesResult.emitData().getMetadataList().get(0);
            assertEquals("Test Author", metadata.get("dc:creator"));
        }
    }

    @Test
    public void testFetchException(@TempDir Path tmp) throws Exception {
        // Test FETCH_EXCEPTION status
        // Occurs when fetcher fails to retrieve the file
        Path inputDir = tmp.resolve("input");
        Files.createDirectories(inputDir);

        Path tikaConfigPath = PluginsTestHelper.getFileSystemFetcherConfig(tmp, inputDir, tmp.resolve("output"));
        TikaJsonConfig tikaJsonConfig = TikaJsonConfig.load(tikaConfigPath);
        PipesConfig pipesConfig = PipesConfig.load(tikaJsonConfig);

        try (PipesClient pipesClient = new PipesClient(pipesConfig, tikaConfigPath)) {
            // Request a file that doesn't exist
            String nonExistentFile = "does-not-exist.pdf";
            FetchEmitTuple tuple = new FetchEmitTuple(nonExistentFile,
                    new FetchKey(fetcherName, nonExistentFile),
                    new EmitKey(), new Metadata(), new ParseContext(),
                    FetchEmitTuple.ON_PARSE_EXCEPTION.SKIP);

            PipesResult pipesResult = pipesClient.process(tuple);

            // Should be FETCH_EXCEPTION because file doesn't exist
            assertEquals(PipesResult.RESULT_STATUS.FETCH_EXCEPTION, pipesResult.status(),
                    "Should return FETCH_EXCEPTION when file cannot be fetched");

            // Verify it's categorized as TASK_EXCEPTION
            assertTrue(pipesResult.isTaskException(),
                    "FETCH_EXCEPTION should be task exception category");

            // Verify error message contains useful information
            Assertions.assertNotNull(pipesResult.message());
            assertTrue(pipesResult.message().contains("does-not-exist") ||
                            pipesResult.message().contains("NoSuchFileException") ||
                            pipesResult.message().contains("not found"),
                    "Error message should indicate file not found");
        }
    }

    @Test
    public void testEmitException(@TempDir Path tmp) throws Exception {
        // Test EMIT_EXCEPTION status
        // Occurs when emitter fails to write results
        Path inputDir = tmp.resolve("input");
        Files.createDirectories(inputDir);

        // Create valid test file
        String testFile = "test.xml";
        String mockContent = "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>" + "<mock>" +
                "<metadata action=\"add\" name=\"dc:creator\">Test</metadata>" +
                "<write element=\"p\">content</write>" +
                "</mock>";
        Files.write(inputDir.resolve(testFile), mockContent.getBytes(StandardCharsets.UTF_8));

        // Create output directory and pre-create the output file to trigger onExists=EXCEPTION
        Path outputDir = tmp.resolve("output");
        Files.createDirectories(outputDir);
        // The emitter will try to create test.xml.json, so pre-create it
        Files.writeString(outputDir.resolve("test.xml.json"), "existing file");

        // Use config with directEmitThresholdBytes=0 to force server-side emission
        // Config has onExists=EXCEPTION which will trigger FileAlreadyExistsException
        Path tikaConfigPath = PluginsTestHelper.getFileSystemFetcherConfig("tika-config-emit-all.json", tmp, inputDir, outputDir, false);
        TikaJsonConfig tikaJsonConfig = TikaJsonConfig.load(tikaConfigPath);
        PipesConfig pipesConfig = PipesConfig.load(tikaJsonConfig);

        try (PipesClient pipesClient = new PipesClient(pipesConfig, tikaConfigPath)) {
            FetchEmitTuple tuple = new FetchEmitTuple(testFile,
                    new FetchKey(fetcherName, testFile),
                    new EmitKey(emitterName, ""), new Metadata(), new ParseContext(),
                    FetchEmitTuple.ON_PARSE_EXCEPTION.SKIP);

            PipesResult pipesResult = pipesClient.process(tuple);

            // Should be EMIT_EXCEPTION because output file exists and onExists=EXCEPTION
            assertEquals(PipesResult.RESULT_STATUS.EMIT_EXCEPTION, pipesResult.status(),
                    "Should return EMIT_EXCEPTION when emitter fails to write");

            // Verify it's categorized as TASK_EXCEPTION
            assertTrue(pipesResult.isTaskException(),
                    "EMIT_EXCEPTION should be task exception category");
        }
    }

    @Test
    public void testFetcherNotFound(@TempDir Path tmp) throws Exception {
        // Test FETCHER_NOT_FOUND status
        // Occurs when FetchKey references a fetcher that doesn't exist
        Path inputDir = tmp.resolve("input");
        Files.createDirectories(inputDir);

        Path tikaConfigPath = PluginsTestHelper.getFileSystemFetcherConfig(tmp, inputDir, tmp.resolve("output"));
        TikaJsonConfig tikaJsonConfig = TikaJsonConfig.load(tikaConfigPath);
        PipesConfig pipesConfig = PipesConfig.load(tikaJsonConfig);

        try (PipesClient pipesClient = new PipesClient(pipesConfig, tikaConfigPath)) {
            // Use invalid fetcher name
            FetchEmitTuple tuple = new FetchEmitTuple("test.pdf",
                    new FetchKey("non-existent-fetcher", "test.pdf"),
                    new EmitKey(), new Metadata(), new ParseContext(),
                    FetchEmitTuple.ON_PARSE_EXCEPTION.SKIP);

            PipesResult pipesResult = pipesClient.process(tuple);

            assertEquals(PipesResult.RESULT_STATUS.FETCHER_INITIALIZATION_EXCEPTION, pipesResult.status(),
                    "Should return FETCHER_INITIALIZATION_EXCEPTION when fetcher name is invalid");

            // Verify it's categorized as INITIALIZATION_FAILURE
            assertTrue(pipesResult.isInitializationFailure(),
                    "FETCHER_INITIALIZATION_EXCEPTION should be initialization failure category");

            // Verify error message mentions the fetcher name
            Assertions.assertNotNull(pipesResult.message());
            assertTrue(pipesResult.message().contains("non-existent-fetcher") ||
                            pipesResult.message().contains("not found") ||
                            pipesResult.message().contains("fetcher"),
                    "Error message should mention the missing fetcher");
        }
    }

    @Test
    public void testEmitterNotFound(@TempDir Path tmp) throws Exception {
        // Test EMITTER_NOT_FOUND status
        // Occurs when EmitKey references an emitter that doesn't exist
        Path inputDir = tmp.resolve("input");
        Files.createDirectories(inputDir);

        // Create valid test file
        String testFile = "test.xml";
        String mockContent = "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>" + "<mock>" +
                "<metadata action=\"add\" name=\"dc:creator\">Test</metadata>" +
                "<write element=\"p\">content</write>" +
                "</mock>";
        Files.write(inputDir.resolve(testFile), mockContent.getBytes(StandardCharsets.UTF_8));

        // Use config with directEmitThresholdBytes=0 to force server-side emission
        Path tikaConfigPath = PluginsTestHelper.getFileSystemFetcherConfig("tika-config-emit-all.json", tmp, inputDir, tmp.resolve("output"), false);
        TikaJsonConfig tikaJsonConfig = TikaJsonConfig.load(tikaConfigPath);
        PipesConfig pipesConfig = PipesConfig.load(tikaJsonConfig);

        try (PipesClient pipesClient = new PipesClient(pipesConfig, tikaConfigPath)) {
            // Use invalid emitter name
            FetchEmitTuple tuple = new FetchEmitTuple(testFile,
                    new FetchKey(fetcherName, testFile),
                    new EmitKey("non-existent-emitter", ""), new Metadata(), new ParseContext(),
                    FetchEmitTuple.ON_PARSE_EXCEPTION.SKIP);

            PipesResult pipesResult = pipesClient.process(tuple);

            // Should be EMITTER_NOT_FOUND
            assertEquals(PipesResult.RESULT_STATUS.EMITTER_NOT_FOUND, pipesResult.status(),
                    "Should return EMITTER_NOT_FOUND when emitter name is invalid");

            // Verify it's categorized as TASK_EXCEPTION
            assertTrue(pipesResult.isTaskException(),
                    "EMITTER_NOT_FOUND should be task exception category");

            // Verify error message mentions the emitter name
            Assertions.assertNotNull(pipesResult.message());
            assertTrue(pipesResult.message().contains("non-existent-emitter") ||
                            pipesResult.message().contains("not found") ||
                            pipesResult.message().contains("emitter"),
                    "Error message should mention the missing emitter");
        }
    }

    @Test
    public void testCustomContentHandlerFactory(@TempDir Path tmp) throws Exception {
        // Test that a custom ContentHandlerFactory configured in tika-config.json
        // is properly used during parsing. The UppercasingContentHandlerFactory
        // converts all extracted text to uppercase.
        Path inputDir = tmp.resolve("input");
        Files.createDirectories(inputDir);

        // Create a simple mock XML file with known content
        String mockContent = "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>" + "<mock>" +
                "<metadata action=\"add\" name=\"dc:creator\">Test Author</metadata>" +
                "<write element=\"p\">Hello World from Tika</write>" +
                "</mock>";
        String testFile = "test-uppercase.xml";
        Files.write(inputDir.resolve(testFile), mockContent.getBytes(StandardCharsets.UTF_8));

        // Use the uppercasing config
        Path tikaConfigPath = PluginsTestHelper.getFileSystemFetcherConfig(
                "tika-config-uppercasing.json", tmp, inputDir, tmp.resolve("output"), false);
        TikaJsonConfig tikaJsonConfig = TikaJsonConfig.load(tikaConfigPath);
        PipesConfig pipesConfig = PipesConfig.load(tikaJsonConfig);

        try (PipesClient pipesClient = new PipesClient(pipesConfig, tikaConfigPath)) {
            FetchEmitTuple tuple = new FetchEmitTuple(testFile,
                    new FetchKey(fetcherName, testFile),
                    new EmitKey(), new Metadata(), new ParseContext(),
                    FetchEmitTuple.ON_PARSE_EXCEPTION.SKIP);

            PipesResult pipesResult = pipesClient.process(tuple);

            // Should succeed
            assertTrue(pipesResult.isSuccess(),
                    "Processing should succeed. Got status: " + pipesResult.status() +
                            ", message: " + pipesResult.message());

            Assertions.assertNotNull(pipesResult.emitData().getMetadataList());
            assertEquals(1, pipesResult.emitData().getMetadataList().size());

            Metadata metadata = pipesResult.emitData().getMetadataList().get(0);

            // The content should be uppercased due to UppercasingContentHandlerFactory
            String content = metadata.get(TikaCoreProperties.TIKA_CONTENT);
            Assertions.assertNotNull(content, "Content should not be null");
            assertTrue(content.contains("HELLO WORLD FROM TIKA"),
                    "Content should be uppercased. Actual content: " + content);
        }
    }

    @Test
    public void testHeartbeatProtocol(@TempDir Path tmp) throws Exception {
        // Test that heartbeat protocol works correctly and doesn't cause protocol errors
        // This test exercises the WORKING status messages during long-running operations
        // to ensure the server properly awaits ACKs after sending heartbeats

        Path inputDir = tmp.resolve("input");
        Files.createDirectories(inputDir);

        // Create a mock file with 2 second delay to trigger multiple heartbeats
        String mockContent = "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>" +
                "<mock>" +
                "<metadata action=\"add\" name=\"dc:creator\">Heartbeat Test</metadata>" +
                "<write element=\"p\">Testing heartbeat protocol synchronization</write>" +
                "<fakeload millis=\"2000\" cpu=\"1\" mb=\"10\"/>" +
                "</mock>";
        String testFile = "mock-heartbeat-test.xml";
        Files.write(inputDir.resolve(testFile), mockContent.getBytes(StandardCharsets.UTF_8));

        // Create config with very short heartbeat interval (100ms) to ensure heartbeats are sent
        Path tikaConfigPath = PluginsTestHelper.getFileSystemFetcherConfig(tmp, inputDir, tmp.resolve("output"));
        String configContent = Files.readString(tikaConfigPath, StandardCharsets.UTF_8);

        // Modify config to add very short heartbeat interval
        configContent = configContent.replace(
                "\"pipes\": {",
                "\"pipes\": {\n    \"heartbeatIntervalMs\": 100,"
        );
        Files.writeString(tikaConfigPath, configContent, StandardCharsets.UTF_8);

        TikaJsonConfig tikaJsonConfig = TikaJsonConfig.load(tikaConfigPath);
        PipesConfig pipesConfig = PipesConfig.load(tikaJsonConfig);

        try (PipesClient pipesClient = new PipesClient(pipesConfig, tikaConfigPath)) {
            // Process file - should complete successfully despite multiple heartbeats
            PipesResult pipesResult = pipesClient.process(
                    new FetchEmitTuple(testFile, new FetchKey(fetcherName, testFile),
                            new EmitKey(), new Metadata(), new ParseContext(),
                            FetchEmitTuple.ON_PARSE_EXCEPTION.SKIP));

            // Verify successful completion
            assertTrue(pipesResult.isSuccess(),
                    "Processing should succeed even with heartbeat messages. Got status: " + pipesResult.status());
            Assertions.assertNotNull(pipesResult.emitData().getMetadataList());
            assertEquals(1, pipesResult.emitData().getMetadataList().size());
            Metadata metadata = pipesResult.emitData().getMetadataList().get(0);
            assertEquals("Heartbeat Test", metadata.get("dc:creator"));
        }
    }
}
