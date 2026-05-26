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
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import org.apache.tika.pipes.core.testutil.AbstractConfigExamplesTest;
import org.apache.tika.pipes.emitter.kafka.KafkaEmitterConfig;
import org.apache.tika.pipes.iterator.kafka.KafkaPipesIteratorConfig;

/**
 * Validates Kafka emitter/iterator configuration examples used in documentation.
 * <p>
 * The JSON configuration examples are stored in {@code src/test/resources/config-examples/}
 * and are included directly in the AsciiDoc documentation via the {@code include::} directive.
 */
public class ConfigExamplesTest extends AbstractConfigExamplesTest {

    @Test
    public void testKafkaEmitterConfig() throws Exception {
        loadAndValidate("kafka-emitter.json");

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
        loadAndValidate("kafka-pipes-iterator.json");

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
        loadAndValidate("kafka-pipeline.json");

        String json = readExample("kafka-pipeline.json");
        KafkaEmitterConfig emitter = KafkaEmitterConfig.load(
                innerComponent(json, "emitters", "kafe", "kafka-emitter").toString());
        emitter.validate();
        assertEquals("tika-parsed-docs", emitter.topic());
    }
}
