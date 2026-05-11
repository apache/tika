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
package org.apache.tika.pipes.s3;

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
import org.apache.tika.pipes.emitter.s3.S3EmitterConfig;
import org.apache.tika.pipes.fetcher.s3.config.S3FetcherConfig;
import org.apache.tika.pipes.iterator.s3.S3PipesIteratorConfig;

/**
 * Validates S3 fetcher/emitter/iterator configuration examples used in documentation.
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
    public void testS3FetcherConfig() throws Exception {
        loadViaTikaLoader("s3-fetcher.json");

        JsonNode inner = innerComponent(readExample("s3-fetcher.json"),
                "fetchers", "s3f", "s3-fetcher");
        S3FetcherConfig config = S3FetcherConfig.load(inner.toString());
        assertEquals("my-tika-input", config.getBucket());
        assertEquals("us-east-1", config.getRegion());
        assertEquals("profile", config.getCredentialsProvider());
        assertEquals("default", config.getProfile());
    }

    @Test
    public void testS3EmitterConfig() throws Exception {
        loadViaTikaLoader("s3-emitter.json");

        JsonNode inner = innerComponent(readExample("s3-emitter.json"),
                "emitters", "s3e", "s3-emitter");
        S3EmitterConfig config = S3EmitterConfig.load(inner.toString());
        assertEquals("my-tika-output", config.bucket());
        assertEquals("us-east-1", config.region());
        assertEquals("profile", config.credentialsProvider());
        assertEquals("json", config.fileExtension());
        // exercises required-field + credentialsProvider whitelist validation
        config.validate();
    }

    @Test
    public void testS3IteratorConfig() throws Exception {
        loadViaTikaLoader("s3-pipes-iterator.json");

        JsonNode inner = innerComponent(readExample("s3-pipes-iterator.json"),
                "pipes-iterator", null, "s3-pipes-iterator");
        S3PipesIteratorConfig config = S3PipesIteratorConfig.load(inner.toString());
        assertEquals("my-tika-input", config.getBucket());
        assertEquals("us-east-1", config.getRegion());
        assertEquals("s3f", config.getFetcherId());
        assertEquals("s3e", config.getEmitterId());
    }

    @Test
    public void testS3PipelineConfig() throws Exception {
        loadViaTikaLoader("s3-pipeline.json");

        String json = readExample("s3-pipeline.json");
        S3FetcherConfig fetcher = S3FetcherConfig.load(
                innerComponent(json, "fetchers", "s3f", "s3-fetcher").toString());
        S3EmitterConfig emitter = S3EmitterConfig.load(
                innerComponent(json, "emitters", "s3e", "s3-emitter").toString());
        S3PipesIteratorConfig iterator = S3PipesIteratorConfig.load(
                innerComponent(json, "pipes-iterator", null, "s3-pipes-iterator").toString());

        emitter.validate();
        assertEquals(fetcher.getBucket(), iterator.getBucket());
        assertEquals("s3f", iterator.getFetcherId());
        assertEquals("s3e", iterator.getEmitterId());
    }
}
