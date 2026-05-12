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
package org.apache.tika.pipes.kafka;

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
import org.apache.tika.pipes.emitter.kafka.KafkaEmitterConfig;
import org.apache.tika.pipes.iterator.kafka.KafkaPipesIteratorConfig;

/**
 * Validates Kafka emitter/iterator configuration examples used in documentation.
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
    public void testKafkaEmitterConfig() throws Exception {
        loadViaTikaLoader("kafka-emitter.json");

        JsonNode inner = innerComponent(readExample("kafka-emitter.json"),
                "emitters", "kafe", "kafka-emitter");
        KafkaEmitterConfig config = KafkaEmitterConfig.load(inner.toString());
        assertEquals("tika-parsed-docs", config.topic());
        assertTrue(config.bootstrapServers().contains("kafka1.example.com"));
        assertEquals("all", config.acks());
        assertEquals("lz4", config.compressionType());
        assertTrue(config.enableIdempotence());
        config.validate();
    }

    @Test
    public void testKafkaIteratorConfig() throws Exception {
        loadViaTikaLoader("kafka-pipes-iterator.json");

        JsonNode inner = innerComponent(readExample("kafka-pipes-iterator.json"),
                "pipes-iterator", null, "kafka-pipes-iterator");
        KafkaPipesIteratorConfig config = KafkaPipesIteratorConfig.load(inner.toString());
        assertEquals("tika-fetch-requests", config.getTopic());
        assertEquals("tika-pipes-iterator", config.getGroupId());
        assertEquals("earliest", config.getAutoOffsetReset());
        assertEquals(100, config.getPollDelayMs());
        assertEquals(-1, config.getEmitMax());
        assertEquals("fsf", config.getFetcherId());
        assertEquals("kafe", config.getEmitterId());
    }

    @Test
    public void testKafkaPipelineConfig() throws Exception {
        loadViaTikaLoader("kafka-pipeline.json");

        String json = readExample("kafka-pipeline.json");
        KafkaEmitterConfig emitter = KafkaEmitterConfig.load(
                innerComponent(json, "emitters", "kafe", "kafka-emitter").toString());
        emitter.validate();
        assertEquals("tika-parsed-docs", emitter.topic());
    }
}
