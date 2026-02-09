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

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.apache.tika.config.EmbeddedLimits;
import org.apache.tika.config.loader.TikaJsonConfig;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.pipes.api.FetchEmitTuple;
import org.apache.tika.pipes.api.ParseMode;
import org.apache.tika.pipes.api.PipesResult;
import org.apache.tika.pipes.api.emitter.EmitKey;
import org.apache.tika.pipes.api.fetcher.FetchKey;
import org.apache.tika.sax.AbstractRecursiveParserWrapperHandler;

/**
 * Tests for EmbeddedLimits functionality in pipes-based parsing.
 */
public class EmbeddedLimitsTest {

    private static final String FETCHER_NAME = "fsf";
    // mock-embedded.xml has 2 embedded documents
    private static final String TEST_DOC_WITH_EMBEDDED = "mock-embedded.xml";

    private PipesClient init(Path tmp, String testFileName) throws Exception {
        Path tikaConfigPath = PluginsTestHelper.getFileSystemFetcherConfig(
                tmp, tmp.resolve("input"), tmp.resolve("output"));
        PluginsTestHelper.copyTestFilesToTmpInput(tmp, testFileName);

        TikaJsonConfig tikaJsonConfig = TikaJsonConfig.load(tikaConfigPath);
        PipesConfig pipesConfig = PipesConfig.load(tikaJsonConfig);
        return new PipesClient(pipesConfig, tikaConfigPath);
    }

    @Test
    public void testMaxCountLimit(@TempDir Path tmp) throws Exception {
        ParseContext parseContext = new ParseContext();
        parseContext.set(ParseMode.class, ParseMode.RMETA);

        // Limit to 1 embedded document (plus container = 2 total)
        EmbeddedLimits limits = new EmbeddedLimits();
        limits.setMaxCount(1);
        parseContext.set(EmbeddedLimits.class, limits);

        PipesClient pipesClient = init(tmp, TEST_DOC_WITH_EMBEDDED);

        PipesResult pipesResult = pipesClient.process(
                new FetchEmitTuple(TEST_DOC_WITH_EMBEDDED,
                        new FetchKey(FETCHER_NAME, TEST_DOC_WITH_EMBEDDED),
                        new EmitKey(), new Metadata(), parseContext,
                        FetchEmitTuple.ON_PARSE_EXCEPTION.SKIP));

        assertTrue(pipesResult.isSuccess(), "Parse should succeed");
        // With maxCount=1, we should get the container (1) + 1 embedded = 2 metadata objects
        // Note: The actual count depends on how EmbeddedLimits is applied
        int metadataCount = pipesResult.emitData().getMetadataList().size();
        assertTrue(metadataCount <= 2,
                "Should have at most 2 metadata objects (container + 1 embedded), got: " + metadataCount);
    }

    @Test
    public void testMaxDepthLimit(@TempDir Path tmp) throws Exception {
        ParseContext parseContext = new ParseContext();
        parseContext.set(ParseMode.class, ParseMode.RMETA);

        // Limit depth to 0 (only container, no embedded)
        EmbeddedLimits limits = new EmbeddedLimits();
        limits.setMaxDepth(0);
        parseContext.set(EmbeddedLimits.class, limits);

        PipesClient pipesClient = init(tmp, TEST_DOC_WITH_EMBEDDED);

        PipesResult pipesResult = pipesClient.process(
                new FetchEmitTuple(TEST_DOC_WITH_EMBEDDED,
                        new FetchKey(FETCHER_NAME, TEST_DOC_WITH_EMBEDDED),
                        new EmitKey(), new Metadata(), parseContext,
                        FetchEmitTuple.ON_PARSE_EXCEPTION.SKIP));

        assertTrue(pipesResult.isSuccess(), "Parse should succeed");
        // With maxDepth=0, we should only get the container (1 metadata object)
        int metadataCount = pipesResult.emitData().getMetadataList().size();
        assertEquals(1, metadataCount,
                "Should have only 1 metadata object (container only) with maxDepth=0");
    }

    @Test
    public void testNoLimits(@TempDir Path tmp) throws Exception {
        ParseContext parseContext = new ParseContext();
        parseContext.set(ParseMode.class, ParseMode.RMETA);
        // No limits set - should get all embedded documents

        PipesClient pipesClient = init(tmp, TEST_DOC_WITH_EMBEDDED);

        PipesResult pipesResult = pipesClient.process(
                new FetchEmitTuple(TEST_DOC_WITH_EMBEDDED,
                        new FetchKey(FETCHER_NAME, TEST_DOC_WITH_EMBEDDED),
                        new EmitKey(), new Metadata(), parseContext,
                        FetchEmitTuple.ON_PARSE_EXCEPTION.SKIP));

        assertTrue(pipesResult.isSuccess(), "Parse should succeed");
        // Without limits, should get container + all embedded documents
        int metadataCount = pipesResult.emitData().getMetadataList().size();
        assertTrue(metadataCount >= 2,
                "Should have at least 2 metadata objects (container + embedded), got: " + metadataCount);
    }

    @Test
    public void testEmbeddedLimitsViaJsonConfig(@TempDir Path tmp) throws Exception {
        ParseContext parseContext = new ParseContext();
        parseContext.set(ParseMode.class, ParseMode.RMETA);

        // Set embedded limits via JSON config
        parseContext.setJsonConfig("embedded-limits", """
            {
              "maxCount": 1,
              "throwOnMaxCount": false
            }
        """);

        PipesClient pipesClient = init(tmp, TEST_DOC_WITH_EMBEDDED);

        PipesResult pipesResult = pipesClient.process(
                new FetchEmitTuple(TEST_DOC_WITH_EMBEDDED,
                        new FetchKey(FETCHER_NAME, TEST_DOC_WITH_EMBEDDED),
                        new EmitKey(), new Metadata(), parseContext,
                        FetchEmitTuple.ON_PARSE_EXCEPTION.SKIP));

        assertTrue(pipesResult.isSuccess(), "Parse should succeed");
        // With maxCount=1, should have limited embedded documents
        int metadataCount = pipesResult.emitData().getMetadataList().size();
        assertTrue(metadataCount <= 2,
                "Should have at most 2 metadata objects with maxCount=1, got: " + metadataCount);
    }

    @Test
    public void testMaxDepthWithException(@TempDir Path tmp) throws Exception {
        ParseContext parseContext = new ParseContext();
        parseContext.set(ParseMode.class, ParseMode.RMETA);

        // Limit depth to 0 and throw on limit
        EmbeddedLimits limits = new EmbeddedLimits();
        limits.setMaxDepth(0);
        limits.setThrowOnMaxDepth(true);
        parseContext.set(EmbeddedLimits.class, limits);

        PipesClient pipesClient = init(tmp, TEST_DOC_WITH_EMBEDDED);

        PipesResult pipesResult = pipesClient.process(
                new FetchEmitTuple(TEST_DOC_WITH_EMBEDDED,
                        new FetchKey(FETCHER_NAME, TEST_DOC_WITH_EMBEDDED),
                        new EmitKey(), new Metadata(), parseContext,
                        FetchEmitTuple.ON_PARSE_EXCEPTION.EMIT));

        // When throwOnMaxDepth=true and limit is exceeded, an exception is thrown
        // but caught and recorded. Result is still "success" but with exception.
        // The key behavior: parsing stops early, container metadata is returned
        assertTrue(pipesResult.isSuccess(), "Parse should complete (with exception recorded)");
        assertEquals(1, pipesResult.emitData().getMetadataList().size(),
                "Should have only container when maxDepth=0 with exception");
        // The status should indicate an exception was encountered
        assertEquals(PipesResult.RESULT_STATUS.PARSE_SUCCESS_WITH_EXCEPTION, pipesResult.status(),
                "Should have parse exception status when throwOnMaxDepth=true and limit exceeded");
    }

    @Test
    public void testMaxCountWithException(@TempDir Path tmp) throws Exception {
        ParseContext parseContext = new ParseContext();
        parseContext.set(ParseMode.class, ParseMode.RMETA);

        // Limit count to 1 and throw on limit
        EmbeddedLimits limits = new EmbeddedLimits();
        limits.setMaxCount(1);
        limits.setThrowOnMaxCount(true);
        parseContext.set(EmbeddedLimits.class, limits);

        PipesClient pipesClient = init(tmp, TEST_DOC_WITH_EMBEDDED);

        PipesResult pipesResult = pipesClient.process(
                new FetchEmitTuple(TEST_DOC_WITH_EMBEDDED,
                        new FetchKey(FETCHER_NAME, TEST_DOC_WITH_EMBEDDED),
                        new EmitKey(), new Metadata(), parseContext,
                        FetchEmitTuple.ON_PARSE_EXCEPTION.EMIT));

        // When throwOnMaxCount=true and limit is exceeded, an exception is thrown
        // but caught and recorded. Result is still "success" but with exception.
        // The key behavior: parsing stops early, limited metadata is returned
        assertTrue(pipesResult.isSuccess(), "Parse should complete (with exception recorded)");
        int metadataCount = pipesResult.emitData().getMetadataList().size();
        assertTrue(metadataCount <= 2,
                "Should have at most 2 metadata objects with maxCount=1, got: " + metadataCount);
        // The status should indicate an exception was encountered
        assertEquals(PipesResult.RESULT_STATUS.PARSE_SUCCESS_WITH_EXCEPTION, pipesResult.status(),
                "Should have parse exception status when throwOnMaxCount=true and limit exceeded");
    }

    @Test
    public void testMaxDepthAllowsFirstLevel(@TempDir Path tmp) throws Exception {
        ParseContext parseContext = new ParseContext();
        parseContext.set(ParseMode.class, ParseMode.RMETA);

        // Limit depth to 2 (should allow first-level embedded documents)
        EmbeddedLimits limits = new EmbeddedLimits();
        limits.setMaxDepth(2);
        parseContext.set(EmbeddedLimits.class, limits);

        PipesClient pipesClient = init(tmp, TEST_DOC_WITH_EMBEDDED);

        PipesResult pipesResult = pipesClient.process(
                new FetchEmitTuple(TEST_DOC_WITH_EMBEDDED,
                        new FetchKey(FETCHER_NAME, TEST_DOC_WITH_EMBEDDED),
                        new EmitKey(), new Metadata(), parseContext,
                        FetchEmitTuple.ON_PARSE_EXCEPTION.SKIP));

        assertTrue(pipesResult.isSuccess(), "Parse should succeed");
        // With maxDepth=2, first-level embedded should be parsed
        // mock-embedded.xml has 4 embedded documents
        int metadataCount = pipesResult.emitData().getMetadataList().size();
        assertTrue(metadataCount >= 2,
                "Should have at least 2 metadata objects with maxDepth=2, got: " + metadataCount);
    }

    @Test
    public void testMaxCountExact(@TempDir Path tmp) throws Exception {
        ParseContext parseContext = new ParseContext();
        parseContext.set(ParseMode.class, ParseMode.RMETA);

        // Limit count to 2 (container + 2 embedded = 3 total, but only first 2 embedded parsed)
        EmbeddedLimits limits = new EmbeddedLimits();
        limits.setMaxCount(2);
        parseContext.set(EmbeddedLimits.class, limits);

        PipesClient pipesClient = init(tmp, TEST_DOC_WITH_EMBEDDED);

        PipesResult pipesResult = pipesClient.process(
                new FetchEmitTuple(TEST_DOC_WITH_EMBEDDED,
                        new FetchKey(FETCHER_NAME, TEST_DOC_WITH_EMBEDDED),
                        new EmitKey(), new Metadata(), parseContext,
                        FetchEmitTuple.ON_PARSE_EXCEPTION.SKIP));

        assertTrue(pipesResult.isSuccess(), "Parse should succeed");
        // With maxCount=2, we should get container + 2 embedded = 3 metadata objects
        int metadataCount = pipesResult.emitData().getMetadataList().size();
        assertTrue(metadataCount <= 3,
                "Should have at most 3 metadata objects with maxCount=2, got: " + metadataCount);

        // Check that the limit reached flag is set
        Metadata containerMetadata = pipesResult.emitData().getMetadataList().get(0);
        String limitReached = containerMetadata.get(AbstractRecursiveParserWrapperHandler.EMBEDDED_RESOURCE_LIMIT_REACHED);
        assertEquals("true", limitReached,
                "Container metadata should have limit reached flag set");
    }

    @Test
    public void testMaxDepthNoExceptionSetsFlag(@TempDir Path tmp) throws Exception {
        ParseContext parseContext = new ParseContext();
        parseContext.set(ParseMode.class, ParseMode.RMETA);

        // Limit depth to 0, no exception
        EmbeddedLimits limits = new EmbeddedLimits();
        limits.setMaxDepth(0);
        limits.setThrowOnMaxDepth(false);
        parseContext.set(EmbeddedLimits.class, limits);

        PipesClient pipesClient = init(tmp, TEST_DOC_WITH_EMBEDDED);

        PipesResult pipesResult = pipesClient.process(
                new FetchEmitTuple(TEST_DOC_WITH_EMBEDDED,
                        new FetchKey(FETCHER_NAME, TEST_DOC_WITH_EMBEDDED),
                        new EmitKey(), new Metadata(), parseContext,
                        FetchEmitTuple.ON_PARSE_EXCEPTION.SKIP));

        assertTrue(pipesResult.isSuccess(), "Parse should succeed without exception");
        assertEquals(1, pipesResult.emitData().getMetadataList().size(),
                "Should have only container when maxDepth=0");

        // Check that the depth limit reached flag is set
        Metadata containerMetadata = pipesResult.emitData().getMetadataList().get(0);
        String limitReached = containerMetadata.get(AbstractRecursiveParserWrapperHandler.EMBEDDED_DEPTH_LIMIT_REACHED);
        assertEquals("true", limitReached,
                "Container metadata should have depth limit reached flag set");
    }
}
