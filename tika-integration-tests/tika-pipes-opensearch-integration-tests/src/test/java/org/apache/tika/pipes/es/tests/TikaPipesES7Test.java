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
package org.apache.tika.pipes.es.tests;

import org.junit.Ignore;

import org.apache.tika.pipes.opensearch.tests.TikaPipesOpenSearchTest;

/**
 * This is used only for devtesting to figure out when the OpenSearch
 * emitter no longer works with elasticsearch.  We should not use
 * &gt; 7.10.x in our unit tests because those versions are not ASL 2.0
 */
@Ignore
public class TikaPipesES7Test extends TikaPipesOpenSearchTest {

    @Override
    public String getOpenSearchImageName() {
        // versions > 7.10.x are no longer ASL 2.0. We should not
        // test with non-ASL 2.0 dependencies
        return "docker.elastic.co/elasticsearch/elasticsearch:7.10.2";
    }

    @Override
    public String getProtocol() {
        return "http://";
    }
}
