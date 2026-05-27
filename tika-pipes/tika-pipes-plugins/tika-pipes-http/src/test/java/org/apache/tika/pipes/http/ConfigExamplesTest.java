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
package org.apache.tika.pipes.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import org.apache.tika.pipes.core.testutil.AbstractConfigExamplesTest;
import org.apache.tika.pipes.fetcher.http.config.HttpFetcherConfig;

/**
 * Validates HTTP fetcher configuration examples used in documentation.
 */
public class ConfigExamplesTest extends AbstractConfigExamplesTest {

    @Test
    public void testHttpFetcherConfig() throws Exception {
        loadAndValidate("http-fetcher.json");

        JsonNode inner = innerComponent(readExample("http-fetcher.json"),
                "fetchers", "httpf", "http-fetcher");
        HttpFetcherConfig config = HttpFetcherConfig.load(inner.toString());
        assertEquals("tika", config.getUserName());
        assertEquals("basic", config.getAuthScheme());
        assertEquals(Integer.valueOf(30000), config.getConnectTimeoutMillis());
        assertEquals(Integer.valueOf(5), config.getMaxRedirects());
        assertTrue(config.getHttpHeaders().contains("Accept: application/octet-stream"));
    }
}
