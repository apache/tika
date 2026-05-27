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
package org.apache.tika.pipes.solr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import org.apache.tika.pipes.core.testutil.AbstractConfigExamplesTest;
import org.apache.tika.pipes.emitter.solr.SolrEmitterConfig;
import org.apache.tika.pipes.iterator.solr.SolrPipesIteratorConfig;

/**
 * Validates Solr emitter/iterator configuration examples used in documentation.
 * <p>
 * The JSON configuration examples are stored in {@code src/test/resources/config-examples/}
 * and are included directly in the AsciiDoc documentation via the {@code include::} directive.
 */
public class ConfigExamplesTest extends AbstractConfigExamplesTest {

    @Test
    public void testSolrEmitterUrlsConfig() throws Exception {
        loadAndValidate("solr-emitter.json");

        JsonNode inner = innerComponent(readExample("solr-emitter.json"),
                "emitters", "solre", "solr-emitter");
        SolrEmitterConfig config = SolrEmitterConfig.load(inner.toString());
        assertEquals("tika-docs", config.solrCollection());
        assertNotNull(config.solrUrls());
        assertEquals(2, config.solrUrls().size());
        assertTrue(config.solrZkHosts() == null || config.solrZkHosts().isEmpty());
        config.validate();
        assertEquals(SolrEmitterConfig.AttachmentStrategy.PARENT_CHILD,
                config.getAttachmentStrategyEnum());
        assertEquals(SolrEmitterConfig.UpdateStrategy.ADD, config.getUpdateStrategyEnum());
    }

    @Test
    public void testSolrEmitterZkConfig() throws Exception {
        loadAndValidate("solr-emitter-zk.json");

        JsonNode inner = innerComponent(readExample("solr-emitter-zk.json"),
                "emitters", "solre", "solr-emitter");
        SolrEmitterConfig config = SolrEmitterConfig.load(inner.toString());
        assertEquals("tika-docs", config.solrCollection());
        assertNotNull(config.solrZkHosts());
        assertEquals(3, config.solrZkHosts().size());
        assertEquals("/solr", config.solrZkChroot());
        assertTrue(config.solrUrls() == null || config.solrUrls().isEmpty());
        config.validate();
    }

    @Test
    public void testSolrIteratorConfig() throws Exception {
        loadAndValidate("solr-pipes-iterator.json");

        JsonNode inner = innerComponent(readExample("solr-pipes-iterator.json"),
                "pipes-iterator", null, "solr-pipes-iterator");
        SolrPipesIteratorConfig config = SolrPipesIteratorConfig.load(inner.toString());
        assertEquals("tika-docs", config.getSolrCollection());
        assertEquals(5000, config.getRows());
        assertTrue(config.getFilters().contains("status:NEEDS_REPARSE"));
        assertEquals("fsf", config.getFetcherId());
        assertEquals("solre", config.getEmitterId());
    }

    @Test
    public void testSolrPipelineConfig() throws Exception {
        loadAndValidate("solr-pipeline.json");

        String json = readExample("solr-pipeline.json");
        SolrEmitterConfig emitter = SolrEmitterConfig.load(
                innerComponent(json, "emitters", "solre", "solr-emitter").toString());
        emitter.validate();
        assertEquals("tika-docs", emitter.solrCollection());
    }
}
