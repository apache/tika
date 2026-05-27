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

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import org.apache.tika.pipes.core.testutil.AbstractConfigExamplesTest;
import org.apache.tika.pipes.emitter.gcs.GCSEmitterConfig;
import org.apache.tika.pipes.fetcher.gcs.config.GCSFetcherConfig;
import org.apache.tika.pipes.iterator.gcs.GCSPipesIteratorConfig;

/**
 * Validates GCS fetcher/emitter/iterator configuration examples used in documentation.
 * <p>
 * The JSON configuration examples are stored in {@code src/test/resources/config-examples/}
 * and are included directly in the AsciiDoc documentation via the {@code include::} directive.
 */
public class ConfigExamplesTest extends AbstractConfigExamplesTest {

    @Test
    public void testGCSFetcherConfig() throws Exception {
        loadAndValidate("gcs-fetcher.json");

        JsonNode inner = innerComponent(readExample("gcs-fetcher.json"),
                "fetchers", "gcsf", "gcs-fetcher");
        GCSFetcherConfig config = GCSFetcherConfig.load(inner.toString());
        assertEquals("my-gcp-project", config.getProjectId());
        assertEquals("my-tika-input", config.getBucket());
    }

    @Test
    public void testGCSEmitterConfig() throws Exception {
        loadAndValidate("gcs-emitter.json");

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
        loadAndValidate("gcs-pipes-iterator.json");

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
        loadAndValidate("gcs-pipeline.json");

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
