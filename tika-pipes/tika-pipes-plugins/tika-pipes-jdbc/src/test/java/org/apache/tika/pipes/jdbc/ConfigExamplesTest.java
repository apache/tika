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
package org.apache.tika.pipes.jdbc;

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
import org.apache.tika.pipes.emitter.jdbc.JDBCEmitterConfig;
import org.apache.tika.pipes.iterator.jdbc.JDBCPipesIteratorConfig;
import org.apache.tika.pipes.reporter.jdbc.JDBCPipesReporterConfig;

/**
 * Validates JDBC emitter/iterator/reporter configuration examples used in documentation.
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
    public void testJDBCEmitterConfig() throws Exception {
        loadViaTikaLoader("jdbc-emitter.json");

        JsonNode inner = innerComponent(readExample("jdbc-emitter.json"),
                "emitters", "jdbce", "jdbc-emitter");
        JDBCEmitterConfig config = JDBCEmitterConfig.load(inner.toString());
        assertTrue(config.connection().startsWith("jdbc:h2:"));
        assertTrue(config.insert().contains("insert into parsed_docs"));
        assertNotNull(config.keys());
        assertEquals(4, config.keys().size());
        assertEquals("string", config.keys().get("dc:title"));
        assertEquals("timestamp", config.keys().get("dcterms:modified"));
        config.validate();
        assertEquals(JDBCEmitterConfig.AttachmentStrategy.FIRST_ONLY,
                config.getAttachmentStrategyEnum());
        assertEquals(JDBCEmitterConfig.MultivaluedFieldStrategy.CONCATENATE,
                config.getMultivaluedFieldStrategyEnum());
    }

    @Test
    public void testJDBCIteratorConfig() throws Exception {
        loadViaTikaLoader("jdbc-pipes-iterator.json");

        JsonNode inner = innerComponent(readExample("jdbc-pipes-iterator.json"),
                "pipes-iterator", null, "jdbc-pipes-iterator");
        JDBCPipesIteratorConfig config = JDBCPipesIteratorConfig.load(inner.toString());
        assertTrue(config.getConnection().startsWith("jdbc:h2:"));
        assertTrue(config.getSelect().contains("docs_to_parse"));
        assertEquals("id", config.getIdColumn());
        assertEquals("source_path", config.getFetchKeyColumn());
        assertEquals("output_path", config.getEmitKeyColumn());
        assertEquals(1000, config.getFetchSize());
        assertEquals(60, config.getQueryTimeoutSeconds());
        assertEquals("fsf", config.getFetcherId());
        assertEquals("jdbce", config.getEmitterId());
    }

    @Test
    public void testJDBCReporterConfig() throws Exception {
        loadViaTikaLoader("jdbc-reporter.json");

        JsonNode inner = innerComponent(readExample("jdbc-reporter.json"),
                "pipes-reporters", null, "jdbc-reporter");
        JDBCPipesReporterConfig config = JDBCPipesReporterConfig.load(inner.toString());
        assertTrue(config.connectionString().startsWith("jdbc:h2:"));
        assertNotNull(config.includes());
        assertTrue(config.includes().contains("PARSE_SUCCESS"));
        // Fields previously unreachable from JSON — see JDBCPipesReporterConfig.fromJson
        assertEquals("tika_reporter_status", config.tableName());
        assertEquals(false, config.createTable());
        assertEquals(5000L, config.reportWithinMs());
        assertEquals(500, config.cacheSize());
    }

    @Test
    public void testJDBCPipelineConfig() throws Exception {
        loadViaTikaLoader("jdbc-pipeline.json");

        String json = readExample("jdbc-pipeline.json");
        JDBCEmitterConfig emitter = JDBCEmitterConfig.load(
                innerComponent(json, "emitters", "jdbce", "jdbc-emitter").toString());
        emitter.validate();
        JDBCPipesIteratorConfig iterator = JDBCPipesIteratorConfig.load(
                innerComponent(json, "pipes-iterator", null, "jdbc-pipes-iterator").toString());
        JDBCPipesReporterConfig reporter = JDBCPipesReporterConfig.load(
                innerComponent(json, "pipes-reporters", null, "jdbc-reporter").toString());

        assertEquals("jdbce", iterator.getEmitterId());
        assertNotNull(reporter.connectionString());
    }
}
