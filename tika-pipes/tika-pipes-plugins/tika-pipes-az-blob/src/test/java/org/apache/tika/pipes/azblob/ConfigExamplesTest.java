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
package org.apache.tika.pipes.azblob;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

import org.apache.tika.pipes.core.testutil.AbstractConfigExamplesTest;
import org.apache.tika.pipes.emitter.azblob.AZBlobEmitterConfig;
import org.apache.tika.pipes.fetcher.azblob.config.AZBlobFetcherConfig;
import org.apache.tika.pipes.iterator.azblob.AZBlobPipesIteratorConfig;

/**
 * Validates Azure Blob fetcher/emitter/iterator configuration examples used in documentation.
 * <p>
 * The JSON configuration examples are stored in {@code src/test/resources/config-examples/}
 * and are included directly in the AsciiDoc documentation via the {@code include::} directive.
 */
public class ConfigExamplesTest extends AbstractConfigExamplesTest {

    @Test
    public void testAZBlobFetcherConfig() throws Exception {
        loadAndValidate("az-blob-fetcher.json");

        AZBlobFetcherConfig config = AZBlobFetcherConfig.load(
                innerComponent(readExample("az-blob-fetcher.json"),
                        "fetchers", "azf", "az-blob-fetcher").toString());
        assertEquals("tika-input", config.getContainer());
        assertEquals("https://myaccount.blob.core.windows.net", config.getEndpoint());
        assertNotNull(config.getSasToken());
    }

    @Test
    public void testAZBlobEmitterConfig() throws Exception {
        loadAndValidate("az-blob-emitter.json");

        AZBlobEmitterConfig config = AZBlobEmitterConfig.load(
                innerComponent(readExample("az-blob-emitter.json"),
                        "emitters", "aze", "az-blob-emitter").toString());
        assertEquals("tika-output", config.container());
        assertEquals("json", config.fileExtension());
        config.validate();
        assertEquals("results", config.getNormalizedPrefix());
    }

    @Test
    public void testAZBlobIteratorConfig() throws Exception {
        loadAndValidate("az-blob-pipes-iterator.json");

        AZBlobPipesIteratorConfig config = AZBlobPipesIteratorConfig.load(
                innerComponent(readExample("az-blob-pipes-iterator.json"),
                        "pipes-iterator", null, "az-blob-pipes-iterator").toString());
        assertEquals("tika-input", config.getContainer());
        assertEquals("incoming/", config.getPrefix());
        assertEquals(360000L, config.getTimeoutMillis());
        assertEquals("azf", config.getFetcherId());
        assertEquals("aze", config.getEmitterId());
    }

    @Test
    public void testAZBlobPipelineConfig() throws Exception {
        loadAndValidate("az-blob-pipeline.json");

        String json = readExample("az-blob-pipeline.json");
        AZBlobFetcherConfig fetcher = AZBlobFetcherConfig.load(
                innerComponent(json, "fetchers", "azf", "az-blob-fetcher").toString());
        AZBlobEmitterConfig emitter = AZBlobEmitterConfig.load(
                innerComponent(json, "emitters", "aze", "az-blob-emitter").toString());
        AZBlobPipesIteratorConfig iterator = AZBlobPipesIteratorConfig.load(
                innerComponent(json, "pipes-iterator", null, "az-blob-pipes-iterator").toString());

        emitter.validate();
        assertEquals(fetcher.getContainer(), iterator.getContainer());
        assertEquals("azf", iterator.getFetcherId());
        assertEquals("aze", iterator.getEmitterId());
    }
}
