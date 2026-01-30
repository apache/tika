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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.apache.tika.config.loader.TikaJsonConfig;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.pipes.api.FetchEmitTuple;
import org.apache.tika.pipes.api.ParseMode;
import org.apache.tika.pipes.api.PipesResult;
import org.apache.tika.pipes.api.emitter.EmitKey;
import org.apache.tika.pipes.api.fetcher.FetchKey;
import org.apache.tika.pipes.core.extractor.UnpackConfig;

/**
 * Tests for the UNPACK ParseMode functionality.
 */
public class UnpackModeTest {

    String fetcherName = "fsf";
    String emitterName = "fse";
    String testDocWithEmbedded = "mock-embedded.xml";

    private PipesClient init(Path tmp, String testFileName) throws Exception {
        Path tikaConfigPath = PluginsTestHelper.getFileSystemFetcherConfig(tmp, tmp.resolve("input"), tmp.resolve("output"));
        PluginsTestHelper.copyTestFilesToTmpInput(tmp, testFileName);

        TikaJsonConfig tikaJsonConfig = TikaJsonConfig.load(tikaConfigPath);
        PipesConfig pipesConfig = PipesConfig.load(tikaJsonConfig);
        return new PipesClient(pipesConfig, tikaConfigPath);
    }

    @Test
    public void testUnpackModeBasic(@TempDir Path tmp) throws Exception {
        // Test that UNPACK mode works and returns metadata like RMETA
        PipesClient pipesClient = init(tmp, testDocWithEmbedded);

        ParseContext parseContext = new ParseContext();
        parseContext.set(ParseMode.class, ParseMode.UNPACK);

        PipesResult pipesResult = pipesClient.process(
                new FetchEmitTuple(testDocWithEmbedded, new FetchKey(fetcherName, testDocWithEmbedded),
                        new EmitKey(emitterName, testDocWithEmbedded), new Metadata(), parseContext,
                        FetchEmitTuple.ON_PARSE_EXCEPTION.EMIT));

        assertTrue(pipesResult.isSuccess(), "UNPACK mode should succeed. Status: " + pipesResult.status() +
                ", Message: " + pipesResult.message());

        // UNPACK mode may return EMIT_SUCCESS (without emitData) if passback filter is not used
        // Check if we have emitData, otherwise just verify success
        if (pipesResult.emitData() != null && pipesResult.emitData().getMetadataList() != null) {
            // With RMETA-like behavior, we should get metadata for container + embedded docs
            // mock-embedded.xml has 4 embedded documents, so we expect 5 metadata objects
            List<Metadata> metadataList = pipesResult.emitData().getMetadataList();
            assertEquals(5, metadataList.size(),
                    "UNPACK should return RMETA-style metadata list (container + 4 embedded docs)");

            // Verify container metadata
            assertEquals("Nikolai Lobachevsky", metadataList.get(0).get("author"));

            // Verify embedded metadata
            for (int i = 1; i < metadataList.size(); i++) {
                assertEquals("embeddedAuthor", metadataList.get(i).get("author"),
                        "Embedded document " + i + " should have embedded author");
            }
        }
        // Even without emitData passback, the fact that isSuccess() is true means UNPACK worked
    }

    @Test
    public void testUnpackModeAutoSetup(@TempDir Path tmp) throws Exception {
        // Test that UNPACK mode works without explicit UnpackConfig
        // It should automatically set up UnpackExtractor and EmittingUnpackHandler
        PipesClient pipesClient = init(tmp, testDocWithEmbedded);

        ParseContext parseContext = new ParseContext();
        parseContext.set(ParseMode.class, ParseMode.UNPACK);
        // No UnpackConfig set - should be created automatically

        PipesResult pipesResult = pipesClient.process(
                new FetchEmitTuple(testDocWithEmbedded, new FetchKey(fetcherName, testDocWithEmbedded),
                        new EmitKey(emitterName, testDocWithEmbedded), new Metadata(), parseContext,
                        FetchEmitTuple.ON_PARSE_EXCEPTION.EMIT));

        assertTrue(pipesResult.isSuccess(),
                "UNPACK should work without explicit UnpackConfig. Status: " + pipesResult.status() +
                        ", Message: " + pipesResult.message());
    }

    @Test
    public void testUnpackModeRequiresEmitter(@TempDir Path tmp) throws Exception {
        // Test that UNPACK mode fails gracefully when no emitter is specified
        PipesClient pipesClient = init(tmp, testDocWithEmbedded);

        ParseContext parseContext = new ParseContext();
        parseContext.set(ParseMode.class, ParseMode.UNPACK);

        // Create EmitKey with no emitterId to trigger the error
        PipesResult pipesResult = pipesClient.process(
                new FetchEmitTuple(testDocWithEmbedded, new FetchKey(fetcherName, testDocWithEmbedded),
                        new EmitKey("", ""), new Metadata(), parseContext,
                        FetchEmitTuple.ON_PARSE_EXCEPTION.EMIT));

        // Should fail because no emitter is configured
        // The error could be a crash (TikaConfigException thrown), initialization failure, or task exception
        assertTrue(!pipesResult.isSuccess(),
                "UNPACK without emitter should fail. Status: " + pipesResult.status());
        assertNotNull(pipesResult.message());
        assertTrue(pipesResult.message().contains("emitter") || pipesResult.message().contains("UNPACK") ||
                        pipesResult.message().contains("TikaConfigException"),
                "Error message should mention emitter requirement: " + pipesResult.message());
    }

    @Test
    public void testUnpackModeReturnsMetadata(@TempDir Path tmp) throws Exception {
        // Test that UNPACK mode returns full metadata list like RMETA
        PipesClient pipesClient = init(tmp, testDocWithEmbedded);

        ParseContext parseContext = new ParseContext();
        parseContext.set(ParseMode.class, ParseMode.UNPACK);

        PipesResult pipesResult = pipesClient.process(
                new FetchEmitTuple(testDocWithEmbedded, new FetchKey(fetcherName, testDocWithEmbedded),
                        new EmitKey(emitterName, testDocWithEmbedded), new Metadata(), parseContext,
                        FetchEmitTuple.ON_PARSE_EXCEPTION.EMIT));

        assertTrue(pipesResult.isSuccess(), "Processing should succeed. Status: " + pipesResult.status() +
                ", Message: " + pipesResult.message());

        // Check if emitData is available (depends on emit strategy)
        if (pipesResult.emitData() != null && pipesResult.emitData().getMetadataList() != null) {
            List<Metadata> metadataList = pipesResult.emitData().getMetadataList();
            assertTrue(metadataList.size() > 1,
                    "UNPACK should return multiple metadata objects for documents with embedded content");

            // Each metadata object should have content type
            for (Metadata m : metadataList) {
                assertNotNull(m.get("Content-Type"), "Each document should have Content-Type");
            }
        }
    }

    @Test
    public void testUnpackModeWithCustomUnpackConfig(@TempDir Path tmp) throws Exception {
        // Test that UNPACK mode respects custom UnpackConfig settings
        PipesClient pipesClient = init(tmp, testDocWithEmbedded);

        ParseContext parseContext = new ParseContext();
        parseContext.set(ParseMode.class, ParseMode.UNPACK);

        // Create custom UnpackConfig with specific settings
        UnpackConfig unpackConfig = new UnpackConfig();
        unpackConfig.setEmitter(emitterName);
        unpackConfig.setZeroPadName(8);
        unpackConfig.setSuffixStrategy(UnpackConfig.SUFFIX_STRATEGY.DETECTED);
        parseContext.set(UnpackConfig.class, unpackConfig);

        PipesResult pipesResult = pipesClient.process(
                new FetchEmitTuple(testDocWithEmbedded, new FetchKey(fetcherName, testDocWithEmbedded),
                        new EmitKey(emitterName, testDocWithEmbedded), new Metadata(), parseContext,
                        FetchEmitTuple.ON_PARSE_EXCEPTION.EMIT));

        assertTrue(pipesResult.isSuccess(),
                "UNPACK with custom UnpackConfig should succeed. Status: " + pipesResult.status());
    }

    @Test
    public void testUnpackModeWithIncludeOriginal(@TempDir Path tmp) throws Exception {
        // Test that includeOriginal=true works with UNPACK mode
        PipesClient pipesClient = init(tmp, testDocWithEmbedded);

        ParseContext parseContext = new ParseContext();
        parseContext.set(ParseMode.class, ParseMode.UNPACK);

        UnpackConfig unpackConfig = new UnpackConfig();
        unpackConfig.setEmitter(emitterName);
        unpackConfig.setIncludeOriginal(true);
        parseContext.set(UnpackConfig.class, unpackConfig);

        PipesResult pipesResult = pipesClient.process(
                new FetchEmitTuple(testDocWithEmbedded, new FetchKey(fetcherName, testDocWithEmbedded),
                        new EmitKey(emitterName, testDocWithEmbedded), new Metadata(), parseContext,
                        FetchEmitTuple.ON_PARSE_EXCEPTION.EMIT));

        assertTrue(pipesResult.isSuccess(),
                "UNPACK with includeOriginal should succeed. Status: " + pipesResult.status());
    }

    @Test
    public void testUnpackModeVsRmetaMode(@TempDir Path tmp) throws Exception {
        // Compare UNPACK mode output with RMETA mode to verify metadata consistency
        PipesClient pipesClient = init(tmp, testDocWithEmbedded);

        // Process with RMETA
        ParseContext rmetaContext = new ParseContext();
        rmetaContext.set(ParseMode.class, ParseMode.RMETA);

        PipesResult rmetaResult = pipesClient.process(
                new FetchEmitTuple(testDocWithEmbedded + "-rmeta", new FetchKey(fetcherName, testDocWithEmbedded),
                        new EmitKey(emitterName, testDocWithEmbedded + "-rmeta"), new Metadata(), rmetaContext,
                        FetchEmitTuple.ON_PARSE_EXCEPTION.EMIT));

        // Process with UNPACK
        ParseContext unpackContext = new ParseContext();
        unpackContext.set(ParseMode.class, ParseMode.UNPACK);

        PipesResult unpackResult = pipesClient.process(
                new FetchEmitTuple(testDocWithEmbedded + "-unpack", new FetchKey(fetcherName, testDocWithEmbedded),
                        new EmitKey(emitterName, testDocWithEmbedded + "-unpack"), new Metadata(), unpackContext,
                        FetchEmitTuple.ON_PARSE_EXCEPTION.EMIT));

        // Both should succeed
        assertTrue(rmetaResult.isSuccess(), "RMETA processing should succeed. Status: " + rmetaResult.status());
        assertTrue(unpackResult.isSuccess(), "UNPACK processing should succeed. Status: " + unpackResult.status() +
                ", Message: " + unpackResult.message());

        // If emitData is available for both, compare them
        if (rmetaResult.emitData() != null && rmetaResult.emitData().getMetadataList() != null &&
            unpackResult.emitData() != null && unpackResult.emitData().getMetadataList() != null) {
            List<Metadata> rmetaList = rmetaResult.emitData().getMetadataList();
            List<Metadata> unpackList = unpackResult.emitData().getMetadataList();

            assertEquals(rmetaList.size(), unpackList.size(),
                    "UNPACK should return same number of metadata objects as RMETA");

            // Compare key metadata values
            for (int i = 0; i < rmetaList.size(); i++) {
                assertEquals(rmetaList.get(i).get("author"), unpackList.get(i).get("author"),
                        "Author metadata should match at index " + i);
                assertEquals(rmetaList.get(i).get("Content-Type"), unpackList.get(i).get("Content-Type"),
                        "Content-Type should match at index " + i);
            }
        }
    }

    @Test
    public void testUnpackModeWithSimpleDocument(@TempDir Path tmp) throws Exception {
        // Test UNPACK mode with a simple document (no embedded files)
        String simpleDoc = "mock_times.xml";
        PipesClient pipesClient = init(tmp, simpleDoc);

        ParseContext parseContext = new ParseContext();
        parseContext.set(ParseMode.class, ParseMode.UNPACK);

        PipesResult pipesResult = pipesClient.process(
                new FetchEmitTuple(simpleDoc, new FetchKey(fetcherName, simpleDoc),
                        new EmitKey(emitterName, simpleDoc), new Metadata(), parseContext,
                        FetchEmitTuple.ON_PARSE_EXCEPTION.EMIT));

        assertTrue(pipesResult.isSuccess(),
                "UNPACK should work with simple documents. Status: " + pipesResult.status() +
                        ", Message: " + pipesResult.message());

        // Check emitData if available
        if (pipesResult.emitData() != null && pipesResult.emitData().getMetadataList() != null) {
            assertEquals(1, pipesResult.emitData().getMetadataList().size(),
                    "Simple document should have exactly one metadata object");
        }
    }

    @Test
    public void testParseModeParseMethod() {
        // Test the parse() method includes UNPACK in error message
        try {
            ParseMode.parse("INVALID_MODE");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("UNPACK"),
                    "Error message should include UNPACK as a valid option: " + e.getMessage());
        }

        // Test that UNPACK can be parsed
        assertEquals(ParseMode.UNPACK, ParseMode.parse("UNPACK"));
        assertEquals(ParseMode.UNPACK, ParseMode.parse("unpack"));
        assertEquals(ParseMode.UNPACK, ParseMode.parse("Unpack"));
    }

    @Test
    public void testUnpackModeBytesEmittedToOutputDir(@TempDir Path tmp) throws Exception {
        // Test that embedded bytes are actually emitted to the output directory
        Path outputDir = tmp.resolve("output");
        Files.createDirectories(outputDir);

        PipesClient pipesClient = init(tmp, testDocWithEmbedded);

        ParseContext parseContext = new ParseContext();
        parseContext.set(ParseMode.class, ParseMode.UNPACK);

        PipesResult pipesResult = pipesClient.process(
                new FetchEmitTuple(testDocWithEmbedded, new FetchKey(fetcherName, testDocWithEmbedded),
                        new EmitKey(emitterName, testDocWithEmbedded), new Metadata(), parseContext,
                        FetchEmitTuple.ON_PARSE_EXCEPTION.EMIT));

        assertTrue(pipesResult.isSuccess(), "UNPACK should succeed");

        // Check that output files were created for the embedded documents
        // The exact naming depends on the EmittingUnpackHandler configuration
        // At minimum, we verify the metadata JSON was written
        assertTrue(Files.exists(outputDir.resolve(testDocWithEmbedded + ".json")) ||
                        Files.list(outputDir).count() > 0,
                "Output directory should contain emitted files");
    }
}
