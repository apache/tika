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

@Ignore("until we figure out if we're supporting es")
public class TikaPipesES7Test extends TikaPipesOpenSearchTest {

    @Override
    public String getOpenSearchImageName() {
        return "docker.elastic.co/elasticsearch/elasticsearch:7.13.4";
    }

    @Override
    public String getProtocol() {
        return "http://";
    }
}
