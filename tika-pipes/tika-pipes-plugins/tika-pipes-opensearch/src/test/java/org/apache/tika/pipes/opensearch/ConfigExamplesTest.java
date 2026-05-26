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

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import org.apache.tika.pipes.core.testutil.AbstractConfigExamplesTest;
import org.apache.tika.pipes.emitter.opensearch.OpenSearchEmitterConfig;
import org.apache.tika.pipes.reporter.opensearch.OpenSearchReporterConfig;

/**
 * Validates OpenSearch emitter/reporter configuration examples used in documentation.
 * <p>
 * The JSON configuration examples are stored in {@code src/test/resources/config-examples/}
 * and are included directly in the AsciiDoc documentation via the {@code include::} directive.
 */
public class ConfigExamplesTest extends AbstractConfigExamplesTest {

    @Test
    public void testOpenSearchEmitterConfig() throws Exception {
        loadAndValidate("opensearch-emitter.json");

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
        loadAndValidate("opensearch-reporter.json");

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
        loadAndValidate("opensearch-pipeline.json");

        String json = readExample("opensearch-pipeline.json");
        OpenSearchEmitterConfig emitter = OpenSearchEmitterConfig.load(
                innerComponent(json, "emitters", "ose", "opensearch-emitter").toString());
        OpenSearchReporterConfig reporter = OpenSearchReporterConfig.load(
                innerComponent(json, "pipes-reporters", null, "opensearch-pipes-reporter").toString());

        assertEquals("doc_id", emitter.idField());
        assertNotNull(reporter.httpClientConfig());
    }
}
