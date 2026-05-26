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
package org.apache.tika.pipes.es;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import org.apache.tika.pipes.core.testutil.AbstractConfigExamplesTest;
import org.apache.tika.pipes.emitter.es.ESEmitterConfig;
import org.apache.tika.pipes.reporter.es.ESReporterConfig;

/**
 * Validates Elasticsearch emitter/reporter configuration examples used in documentation.
 * <p>
 * The JSON configuration examples are stored in {@code src/test/resources/config-examples/}
 * and are included directly in the AsciiDoc documentation via the {@code include::} directive.
 */
public class ConfigExamplesTest extends AbstractConfigExamplesTest {

    @Test
    public void testESEmitterConfig() throws Exception {
        loadAndValidate("es-emitter.json");

        ESEmitterConfig config = ESEmitterConfig.load(
                innerComponent(readExample("es-emitter.json"),
                        "emitters", "ese", "es-emitter").toString());
        assertEquals("doc_id", config.idField());
        assertEquals(ESEmitterConfig.AttachmentStrategy.PARENT_CHILD,
                config.attachmentStrategy());
        assertEquals(ESEmitterConfig.UpdateStrategy.OVERWRITE, config.updateStrategy());
        assertEquals(1000, config.commitWithin());
        assertNotNull(config.apiKey());
        assertNotNull(config.httpClientConfig());
        // The toString override redacts the apiKey value
        assertFalse(config.toString().contains(config.apiKey()),
                "apiKey must not appear in toString() output");
    }

    @Test
    public void testESReporterConfig() throws Exception {
        loadAndValidate("es-reporter.json");

        ESReporterConfig config = ESReporterConfig.load(
                innerComponent(readExample("es-reporter.json"),
                        "pipes-reporters", null, "es-pipes-reporter").toString());
        assertTrue(config.esUrl().contains("tika-status"));
        assertEquals("tika_", config.keyPrefix());
        assertTrue(config.includeRouting());
        assertNotNull(config.includes());
        assertTrue(config.includes().contains("PARSE_SUCCESS"));
        assertNotNull(config.httpClientConfig());
    }

    @Test
    public void testESPipelineConfig() throws Exception {
        loadAndValidate("es-pipeline.json");

        String json = readExample("es-pipeline.json");
        ESEmitterConfig emitter = ESEmitterConfig.load(
                innerComponent(json, "emitters", "ese", "es-emitter").toString());
        ESReporterConfig reporter = ESReporterConfig.load(
                innerComponent(json, "pipes-reporters", null, "es-pipes-reporter").toString());

        assertEquals("doc_id", emitter.idField());
        assertNotNull(reporter.httpClientConfig());
    }
}
