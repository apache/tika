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
package org.apache.tika.pipes.gcs;

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
import org.apache.tika.pipes.emitter.gcs.GCSEmitterConfig;
import org.apache.tika.pipes.fetcher.gcs.config.GCSFetcherConfig;
import org.apache.tika.pipes.iterator.gcs.GCSPipesIteratorConfig;

/**
 * Validates GCS fetcher/emitter/iterator configuration examples used in documentation.
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
    public void testGCSFetcherConfig() throws Exception {
        loadViaTikaLoader("gcs-fetcher.json");

        JsonNode inner = innerComponent(readExample("gcs-fetcher.json"),
                "fetchers", "gcsf", "gcs-fetcher");
        GCSFetcherConfig config = GCSFetcherConfig.load(inner.toString());
        assertEquals("my-gcp-project", config.getProjectId());
        assertEquals("my-tika-input", config.getBucket());
    }

    @Test
    public void testGCSEmitterConfig() throws Exception {
        loadViaTikaLoader("gcs-emitter.json");

        JsonNode inner = innerComponent(readExample("gcs-emitter.json"),
                "emitters", "gcse", "gcs-emitter");
        GCSEmitterConfig config = GCSEmitterConfig.load(inner.toString());
        assertEquals("my-gcp-project", config.projectId());
        assertEquals("my-tika-output", config.bucket());
        assertEquals("json", config.fileExtension());
        config.validate();
        assertEquals("results", config.getNormalizedPrefix());
    }

    @Test
    public void testGCSIteratorConfig() throws Exception {
        loadViaTikaLoader("gcs-pipes-iterator.json");

        JsonNode inner = innerComponent(readExample("gcs-pipes-iterator.json"),
                "pipes-iterator", null, "gcs-pipes-iterator");
        GCSPipesIteratorConfig config = GCSPipesIteratorConfig.load(inner.toString());
        assertEquals("my-gcp-project", config.getProjectId());
        assertEquals("my-tika-input", config.getBucket());
        assertEquals("gcsf", config.getFetcherId());
        assertEquals("gcse", config.getEmitterId());
    }

    @Test
    public void testGCSPipelineConfig() throws Exception {
        loadViaTikaLoader("gcs-pipeline.json");

        String json = readExample("gcs-pipeline.json");
        GCSFetcherConfig fetcher = GCSFetcherConfig.load(
                innerComponent(json, "fetchers", "gcsf", "gcs-fetcher").toString());
        GCSEmitterConfig emitter = GCSEmitterConfig.load(
                innerComponent(json, "emitters", "gcse", "gcs-emitter").toString());
        GCSPipesIteratorConfig iterator = GCSPipesIteratorConfig.load(
                innerComponent(json, "pipes-iterator", null, "gcs-pipes-iterator").toString());

        emitter.validate();
        assertEquals(fetcher.getBucket(), iterator.getBucket());
        assertEquals("gcsf", iterator.getFetcherId());
        assertEquals("gcse", iterator.getEmitterId());
    }
}
