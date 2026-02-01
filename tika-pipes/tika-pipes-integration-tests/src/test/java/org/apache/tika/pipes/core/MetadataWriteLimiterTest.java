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
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.file.Path;

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
 * Tests that MetadataWriteLimiterFactory is loaded from config and applied during parsing,
 * and can be overridden via ParseContext.
 */
public class MetadataWriteLimiterTest {

    private static final String FETCHER_NAME = "fsf";
    private static final String TEST_DOC = "testOverlappingText.pdf";

    private PipesClient initWithWriteLimiter(Path tmp, String testFileName) throws Exception {
        Path tikaConfigPath = PluginsTestHelper.getFileSystemFetcherConfig(
                "tika-config-write-limiter.json", tmp, tmp.resolve("input"), tmp.resolve("output"), false);
        PluginsTestHelper.copyTestFilesToTmpInput(tmp, testFileName);

        TikaJsonConfig tikaJsonConfig = TikaJsonConfig.load(tikaConfigPath);
        PipesConfig pipesConfig = PipesConfig.load(tikaJsonConfig);
        return new PipesClient(pipesConfig, tikaConfigPath);
    }

    /**
     * Test that MetadataWriteLimiterFactory is loaded from config and limits are applied.
     * The config specifies includeFields: ["dc:creator", "Content-Type", "X-TIKA:content"]
     * so other fields like "pdf:PDFVersion" should be filtered out.
     */
    @Test
    public void testWriteLimiterFromConfig(@TempDir Path tmp) throws Exception {
        PipesClient pipesClient = initWithWriteLimiter(tmp, TEST_DOC);

        PipesResult pipesResult = pipesClient.process(
                new FetchEmitTuple(TEST_DOC, new FetchKey(FETCHER_NAME, TEST_DOC),
                        new EmitKey(), new Metadata(), new ParseContext(), FetchEmitTuple.ON_PARSE_EXCEPTION.SKIP));

        assertNotNull(pipesResult.emitData().getMetadataList());
        assertEquals(1, pipesResult.emitData().getMetadataList().size());

        Metadata metadata = pipesResult.emitData().getMetadataList().get(0);

        // These fields should be present (in includeFields or "must add" fields)
        assertNotNull(metadata.get("Content-Type"), "Content-Type should be present");

        // Fields not in includeFields should be filtered out
        // (unless they're "must add" fields like X-TIKA:Parsed-By)
        assertNull(metadata.get("pdf:PDFVersion"), "pdf:PDFVersion should be filtered out");
        assertNull(metadata.get("dc:format"), "dc:format should be filtered out");
    }

    /**
     * Test that MetadataWriteLimiterFactory can be overridden via ParseContext.
     * The default config excludes X-TIKA:parse_time_millis, but the override allows it.
     *
     * Note: We use X-TIKA:parse_time_millis instead of pdf:PDFVersion because the PDF parser
     * module is not a dependency of this test module, so PDF-specific metadata isn't extracted.
     */
    @Test
    public void testWriteLimiterOverrideViaParseContext(@TempDir Path tmp) throws Exception {
        PipesClient pipesClient = initWithWriteLimiter(tmp, TEST_DOC);

        // Create a ParseContext with an override that allows X-TIKA:parse_time_millis
        // The default config's includeFields (dc:creator, Content-Type, X-TIKA:content)
        // does NOT include X-TIKA:parse_time_millis, but this override does.
        ParseContext parseContext = new ParseContext();
        String overrideJson = """
                {
                    "includeFields": ["Content-Type", "X-TIKA:parse_time_millis"],
                    "maxKeySize": 100,
                    "maxFieldSize": 1000,
                    "maxTotalBytes": 10000,
                    "maxValuesPerField": 5
                }
                """;
        parseContext.setJsonConfig("standard-metadata-limiter-factory", () -> overrideJson);

        PipesResult pipesResult = pipesClient.process(
                new FetchEmitTuple(TEST_DOC, new FetchKey(FETCHER_NAME, TEST_DOC),
                        new EmitKey(), new Metadata(), parseContext, FetchEmitTuple.ON_PARSE_EXCEPTION.SKIP));

        assertNotNull(pipesResult.emitData().getMetadataList());
        assertEquals(1, pipesResult.emitData().getMetadataList().size());

        Metadata metadata = pipesResult.emitData().getMetadataList().get(0);

        // These fields should be present (in the override includeFields or ALWAYS_SET/ADD_FIELDS)
        assertNotNull(metadata.get("Content-Type"), "Content-Type should be present");
        assertNotNull(metadata.get("X-TIKA:parse_time_millis"), "X-TIKA:parse_time_millis should be present (allowed by override)");

        // dc:creator was in the default config's includeFields but NOT in the override
        assertNull(metadata.get("dc:creator"), "dc:creator should be filtered out (not in override)");
    }
}
