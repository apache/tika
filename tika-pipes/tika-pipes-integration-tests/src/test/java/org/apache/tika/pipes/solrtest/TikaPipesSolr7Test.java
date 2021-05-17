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
package org.apache.tika.pipes.solrtest;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

public class TikaPipesSolr7Test extends TikaPipesSolrTestBase {

    @Rule
    public GenericContainer<?> solr7 =
            new GenericContainer<>(DockerImageName.parse("solr:7")).withExposedPorts(8983, 9983)
                    .withCommand("-DzkRun");

    @Before
    public void setupTest() throws Exception {
        setupSolr(solr7);
    }

    @Test
    public void testFetchIteratorWithSolrUrls() throws Exception {
        runTikaAsyncSolrPipeIteratorFileFetcherSolrEmitter(false);

    }

    @Test
    public void testFetchIteratorWithZkHost() throws Exception {
        runTikaAsyncSolrPipeIteratorFileFetcherSolrEmitter(true);
    }
}
