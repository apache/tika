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
package org.apache.tika.pipes.solr;

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
import org.apache.tika.pipes.emitter.solr.SolrEmitterConfig;
import org.apache.tika.pipes.iterator.solr.SolrPipesIteratorConfig;

/**
 * Validates Solr emitter/iterator configuration examples used in documentation.
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
    public void testSolrEmitterUrlsConfig() throws Exception {
        loadViaTikaLoader("solr-emitter.json");

        JsonNode inner = innerComponent(readExample("solr-emitter.json"),
                "emitters", "solre", "solr-emitter");
        SolrEmitterConfig config = SolrEmitterConfig.load(inner.toString());
        assertEquals("tika-docs", config.solrCollection());
        assertNotNull(config.solrUrls());
        assertEquals(2, config.solrUrls().size());
        assertTrue(config.solrZkHosts() == null || config.solrZkHosts().isEmpty());
        config.validate();
        assertEquals(SolrEmitterConfig.AttachmentStrategy.PARENT_CHILD,
                config.getAttachmentStrategyEnum());
        assertEquals(SolrEmitterConfig.UpdateStrategy.ADD, config.getUpdateStrategyEnum());
    }

    @Test
    public void testSolrEmitterZkConfig() throws Exception {
        loadViaTikaLoader("solr-emitter-zk.json");

        JsonNode inner = innerComponent(readExample("solr-emitter-zk.json"),
                "emitters", "solre", "solr-emitter");
        SolrEmitterConfig config = SolrEmitterConfig.load(inner.toString());
        assertEquals("tika-docs", config.solrCollection());
        assertNotNull(config.solrZkHosts());
        assertEquals(3, config.solrZkHosts().size());
        assertEquals("/solr", config.solrZkChroot());
        assertTrue(config.solrUrls() == null || config.solrUrls().isEmpty());
        config.validate();
    }

    @Test
    public void testSolrIteratorConfig() throws Exception {
        loadViaTikaLoader("solr-pipes-iterator.json");

        JsonNode inner = innerComponent(readExample("solr-pipes-iterator.json"),
                "pipes-iterator", null, "solr-pipes-iterator");
        SolrPipesIteratorConfig config = SolrPipesIteratorConfig.load(inner.toString());
        assertEquals("tika-docs", config.getSolrCollection());
        assertEquals(5000, config.getRows());
        assertTrue(config.getFilters().contains("status:NEEDS_REPARSE"));
        assertEquals("fsf", config.getFetcherId());
        assertEquals("solre", config.getEmitterId());
    }

    @Test
    public void testSolrPipelineConfig() throws Exception {
        loadViaTikaLoader("solr-pipeline.json");

        String json = readExample("solr-pipeline.json");
        SolrEmitterConfig emitter = SolrEmitterConfig.load(
                innerComponent(json, "emitters", "solre", "solr-emitter").toString());
        emitter.validate();
        assertEquals("tika-docs", emitter.solrCollection());
    }
}
