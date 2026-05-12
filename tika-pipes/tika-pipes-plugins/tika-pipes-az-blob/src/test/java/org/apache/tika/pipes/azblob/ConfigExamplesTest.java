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

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.apache.tika.config.loader.TikaLoader;
import org.apache.tika.pipes.emitter.azblob.AZBlobEmitterConfig;
import org.apache.tika.pipes.fetcher.azblob.config.AZBlobFetcherConfig;
import org.apache.tika.pipes.iterator.azblob.AZBlobPipesIteratorConfig;

/**
 * Validates Azure Blob fetcher/emitter/iterator configuration examples used in documentation.
 * <p>
 * The JSON configuration examples are stored in {@code src/test/resources/config-examples/}
 * and are included directly in the AsciiDoc documentation via the {@code include::} directive.
 */
public class ConfigExamplesTest {

    private static final String EXAMPLES_DIR = "/config-examples/";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @TempDir
    Path tempDir;

    private String readExample(String resourceName) throws Exception {
        try (InputStream is = getClass().getResourceAsStream(EXAMPLES_DIR + resourceName)) {
            assertNotNull(is, "Resource not found: " + resourceName);
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private void loadViaTikaLoader(String resourceName) throws Exception {
        String json = readExample(resourceName);
        Path configFile = tempDir.resolve("tika-config.json");
        Files.writeString(configFile, json, StandardCharsets.UTF_8);
        TikaLoader loader = TikaLoader.load(configFile);
        assertNotNull(loader, "TikaLoader should not be null for: " + resourceName);
    }

    private JsonNode innerComponent(String json, String section, String id, String typeName)
            throws Exception {
        JsonNode root = OBJECT_MAPPER.readTree(json);
        JsonNode sectionNode = root.get(section);
        assertNotNull(sectionNode, "Missing section: " + section);
        JsonNode idNode = id == null ? sectionNode : sectionNode.get(id);
        assertNotNull(idNode, "Missing id: " + id);
        JsonNode typed = idNode.get(typeName);
        assertNotNull(typed, "Missing type: " + typeName);
        return typed;
    }

    @Test
    public void testAZBlobFetcherConfig() throws Exception {
        loadViaTikaLoader("az-blob-fetcher.json");

        JsonNode inner = innerComponent(readExample("az-blob-fetcher.json"),
                "fetchers", "azf", "az-blob-fetcher");
        AZBlobFetcherConfig config = AZBlobFetcherConfig.load(inner.toString());
        assertEquals("tika-input", config.getContainer());
        assertEquals("https://myaccount.blob.core.windows.net", config.getEndpoint());
        assertNotNull(config.getSasToken());
    }

    @Test
    public void testAZBlobEmitterConfig() throws Exception {
        loadViaTikaLoader("az-blob-emitter.json");

        JsonNode inner = innerComponent(readExample("az-blob-emitter.json"),
                "emitters", "aze", "az-blob-emitter");
        AZBlobEmitterConfig config = AZBlobEmitterConfig.load(inner.toString());
        assertEquals("tika-output", config.container());
        assertEquals("json", config.fileExtension());
        config.validate();
        assertEquals("results", config.getNormalizedPrefix());
    }

    @Test
    public void testAZBlobIteratorConfig() throws Exception {
        loadViaTikaLoader("az-blob-pipes-iterator.json");

        JsonNode inner = innerComponent(readExample("az-blob-pipes-iterator.json"),
                "pipes-iterator", null, "az-blob-pipes-iterator");
        AZBlobPipesIteratorConfig config = AZBlobPipesIteratorConfig.load(inner.toString());
        assertEquals("tika-input", config.getContainer());
        assertEquals("incoming/", config.getPrefix());
        assertEquals(360000L, config.getTimeoutMillis());
        assertEquals("azf", config.getFetcherId());
        assertEquals("aze", config.getEmitterId());
    }

    @Test
    public void testAZBlobPipelineConfig() throws Exception {
        loadViaTikaLoader("az-blob-pipeline.json");

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
