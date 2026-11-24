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
package org.apache.tika.pipes.qdrant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.pipes.core.testutil.AbstractConfigExamplesTest;
import org.apache.tika.pipes.emitter.qdrant.QdrantEmitterConfig;

/**
 * Validates the Qdrant emitter configuration examples that the documentation includes.
 */
public class ConfigExamplesTest extends AbstractConfigExamplesTest {

    @Test
    public void testQdrantEmitterConfig() throws Exception {
        loadAndValidate("qdrant-emitter.json");
        QdrantEmitterConfig config = QdrantEmitterConfig.load(
                innerComponent(readExample("qdrant-emitter.json"),
                        "emitters", "qdrant", "qdrant-emitter").toString());
        assertEquals("tika-chunks", config.collection());
        assertEquals(64, config.batchSize());
        assertTrue(config.replaceExisting());
        assertEquals(List.of("dc:title", "Content-Type", "tk:embedded-resource-path"),
                config.metadataFields());
        assertEquals(60000, config.httpClientConfig().socketTimeoutMillis());
        assertFalse(config.toString().contains(config.apiKey()),
                "apiKey must not appear in toString() output");
    }

    @Test
    public void testQdrantPipelineConfig() throws Exception {
        loadAndValidate("qdrant-pipeline.json");
        QdrantEmitterConfig config = QdrantEmitterConfig.load(
                innerComponent(readExample("qdrant-pipeline.json"),
                        "emitters", "qdrant", "qdrant-emitter").toString());
        assertEquals(QdrantEmitterConfig.DEFAULT_BATCH_SIZE, config.batchSize());
        assertTrue(config.replaceExisting());
        assertEquals(List.of(), config.metadataFields());
    }

    @Test
    public void testUpperCaseSchemeIsValid() throws Exception {
        QdrantEmitterConfig.load("{\"qdrantUrl\":\"HTTPS://q.example.com\",\"collection\":\"c\"}");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "{\"collection\":\"c\"}|qdrantUrl must be set",
        "{\"qdrantUrl\":\"localhost:6333\",\"collection\":\"c\"}|must start with http",
        "{\"qdrantUrl\":\"http://bad host\",\"collection\":\"c\"}|not a valid URL",
        "{\"qdrantUrl\":\"http:///collections\",\"collection\":\"c\"}|must name a host",
        "{\"qdrantUrl\":\"http://q:6333?x=1\",\"collection\":\"c\"}|query or a fragment",
        "{\"qdrantUrl\":\"http://q:6333#f\",\"collection\":\"c\"}|query or a fragment",
        "{\"qdrantUrl\":\"http://q:6333\"}|collection must be set",
        "{\"qdrantUrl\":\"http://q:6333\",\"collection\":\"a/b\"}|must not contain '/'",
        "{\"qdrantUrl\":\"http://q:6333\",\"collection\":\"c\",\"metadataFields\":[\"a\",\" \"]}|empty names",
        "{\"qdrantUrl\":\"http://q:6333\",\"collection\":\"c\",\"metadataFields\":[\"a\",null]}|empty names",
        "{\"qdrantUrl\":\"http://q:6333\",\"collection\":\"c\",\"batchSize\":0}|batchSize must be 1 or more",
        "{\"qdrantUrl\":\"http://q:6333\",\"collection\":\"c\",\"batchSize\":-5}|batchSize must be 1 or more",
        "{\"qdrantUrl\":\"http://q:6333\",\"collection\":\"c\",\"apiKey\":\"k\",\"httpClientConfig\":{\"userName\":\"u\"}}|not both"
    })
    public void testInvalidConfig(String json, String message) {
        TikaConfigException e = assertThrows(TikaConfigException.class,
                () -> QdrantEmitterConfig.load(json));
        assertTrue(e.getMessage().contains(message), e.getMessage());
    }
}
