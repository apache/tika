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
package org.apache.tika.pipes.emitter.solr;

import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.junit.Ignore;
import org.junit.Test;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.metadata.filter.FieldNameMappingFilter;

/**
 * This is meant only for one off development tests with a locally
 * running instance of Solr.  Please add unit tests to the
 * tika-integration-tests/tika-pipes-solr-integration-tests
 */
@Ignore
public class SolrEmitterDevTest {

    @Test
    public void oneOff() throws Exception {
        String core = "tika-example";
        String url = "http://localhost:8983/solr";
        String emitKey = "one-off-test-doc";
        SolrEmitter solrEmitter = new SolrEmitter();
        solrEmitter.setSolrUrls(Collections.singletonList(url));
        solrEmitter.setSolrCollection(core);
        solrEmitter.initialize(Collections.EMPTY_MAP);

        Metadata metadata = new Metadata();
        metadata.set(TikaCoreProperties.CREATED, new Date());
        metadata.set(TikaCoreProperties.TIKA_CONTENT, "the quick brown fox");

        Map<String, String> mappings = new HashMap();
        FieldNameMappingFilter filter = new FieldNameMappingFilter();
        mappings.put(TikaCoreProperties.CREATED.getName(), "created");
        mappings.put(TikaCoreProperties.TIKA_CONTENT.getName(), "content");
        filter.setMappings(mappings);
        filter.filter(metadata);

        solrEmitter.emit(emitKey, Collections.singletonList(metadata));
    }
}
