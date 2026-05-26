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

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import org.apache.tika.pipes.core.testutil.AbstractConfigExamplesTest;
import org.apache.tika.pipes.emitter.s3.S3EmitterConfig;
import org.apache.tika.pipes.fetcher.s3.config.S3FetcherConfig;
import org.apache.tika.pipes.iterator.s3.S3PipesIteratorConfig;

/**
 * Validates S3 fetcher/emitter/iterator configuration examples used in documentation.
 * <p>
 * The JSON configuration examples are stored in {@code src/test/resources/config-examples/}
 * and are included directly in the AsciiDoc documentation via the {@code include::} directive.
 */
public class ConfigExamplesTest extends AbstractConfigExamplesTest {

    @Test
    public void testS3FetcherConfig() throws Exception {
        loadAndValidate("s3-fetcher.json");

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
        loadAndValidate("s3-emitter.json");

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
        loadAndValidate("s3-pipes-iterator.json");

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
        loadAndValidate("s3-pipeline.json");

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
