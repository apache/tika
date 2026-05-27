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
package org.apache.tika.pipes.atlassianjwt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import org.apache.tika.pipes.core.testutil.AbstractConfigExamplesTest;
import org.apache.tika.pipes.fetcher.atlassianjwt.config.AtlassianJwtFetcherConfig;

/**
 * Validates Atlassian JWT fetcher configuration examples used in documentation.
 */
public class ConfigExamplesTest extends AbstractConfigExamplesTest {

    @Test
    public void testAtlassianJwtFetcherConfig() throws Exception {
        loadAndValidate("atlassian-jwt-fetcher.json");

        JsonNode inner = innerComponent(readExample("atlassian-jwt-fetcher.json"),
                "fetchers", "ajwt", "atlassian-jwt-fetcher");
        AtlassianJwtFetcherConfig config = AtlassianJwtFetcherConfig.load(inner.toString());
        assertEquals("tika-pipes-app-key", config.getIssuer());
        assertNotNull(config.getSharedSecret());
        assertEquals("service-account@example.com", config.getSubject());
        assertEquals(Integer.valueOf(3600), config.getJwtExpiresInSeconds());
        assertEquals(Integer.valueOf(30000), config.getConnectTimeoutMillis());
    }
}
