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

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import org.apache.tika.pipes.core.testutil.AbstractConfigExamplesTest;
import org.apache.tika.pipes.emitter.jdbc.JDBCEmitterConfig;
import org.apache.tika.pipes.iterator.jdbc.JDBCPipesIteratorConfig;
import org.apache.tika.pipes.reporter.jdbc.JDBCPipesReporterConfig;

/**
 * Validates JDBC emitter/iterator/reporter configuration examples used in documentation.
 * <p>
 * The JSON configuration examples are stored in {@code src/test/resources/config-examples/}
 * and are included directly in the AsciiDoc documentation via the {@code include::} directive.
 */
public class ConfigExamplesTest extends AbstractConfigExamplesTest {

    @Test
    public void testJDBCEmitterConfig() throws Exception {
        loadAndValidate("jdbc-emitter.json");

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
        loadAndValidate("jdbc-pipes-iterator.json");

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
        loadAndValidate("jdbc-reporter.json");

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
        loadAndValidate("jdbc-pipeline.json");

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
