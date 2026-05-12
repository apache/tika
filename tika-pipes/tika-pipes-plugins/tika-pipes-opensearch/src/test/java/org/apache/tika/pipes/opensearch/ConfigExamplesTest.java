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
package org.apache.tika.pipes.opensearch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.apache.tika.config.loader.TikaLoader;
import org.apache.tika.pipes.emitter.opensearch.OpenSearchEmitterConfig;
import org.apache.tika.pipes.reporter.opensearch.OpenSearchReporterConfig;

/**
 * Validates OpenSearch emitter/reporter configuration examples used in documentation.
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
    public void testOpenSearchEmitterConfig() throws Exception {
        loadViaTikaLoader("opensearch-emitter.json");

        JsonNode inner = innerComponent(readExample("opensearch-emitter.json"),
                "emitters", "ose", "opensearch-emitter");
        OpenSearchEmitterConfig config = OpenSearchEmitterConfig.load(inner.toString());
        assertEquals("doc_id", config.idField());
        assertEquals(OpenSearchEmitterConfig.AttachmentStrategy.PARENT_CHILD,
                config.attachmentStrategy());
        assertEquals(OpenSearchEmitterConfig.UpdateStrategy.OVERWRITE,
                config.updateStrategy());
        assertEquals(1000, config.commitWithin());
        assertNotNull(config.httpClientConfig());
        assertEquals("admin", config.httpClientConfig().userName());
    }

    @Test
    public void testOpenSearchReporterConfig() throws Exception {
        loadViaTikaLoader("opensearch-reporter.json");

        JsonNode inner = innerComponent(readExample("opensearch-reporter.json"),
                "pipes-reporters", null, "opensearch-pipes-reporter");
        OpenSearchReporterConfig config = OpenSearchReporterConfig.load(inner.toString());
        assertTrue(config.openSearchUrl().contains("tika-status"));
        assertEquals("tika_", config.keyPrefix());
        assertTrue(config.includeRouting());
        assertNotNull(config.includes());
        assertTrue(config.includes().contains("PARSE_SUCCESS"));
        assertNotNull(config.httpClientConfig());
    }

    @Test
    public void testOpenSearchPipelineConfig() throws Exception {
        loadViaTikaLoader("opensearch-pipeline.json");

        String json = readExample("opensearch-pipeline.json");
        OpenSearchEmitterConfig emitter = OpenSearchEmitterConfig.load(
                innerComponent(json, "emitters", "ose", "opensearch-emitter").toString());
        OpenSearchReporterConfig reporter = OpenSearchReporterConfig.load(
                innerComponent(json, "pipes-reporters", null, "opensearch-pipes-reporter").toString());

        assertEquals("doc_id", emitter.idField());
        assertNotNull(reporter.httpClientConfig());
    }
}
