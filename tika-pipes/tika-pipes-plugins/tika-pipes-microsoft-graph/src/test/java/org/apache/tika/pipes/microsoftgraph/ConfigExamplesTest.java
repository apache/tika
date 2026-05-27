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
package org.apache.tika.pipes.microsoftgraph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import org.apache.tika.pipes.core.testutil.AbstractConfigExamplesTest;
import org.apache.tika.pipes.fetchers.microsoftgraph.config.MicrosoftGraphFetcherConfig;

/**
 * Validates Microsoft Graph fetcher configuration examples used in documentation.
 */
public class ConfigExamplesTest extends AbstractConfigExamplesTest {

    @Test
    public void testMicrosoftGraphFetcherConfig() throws Exception {
        loadAndValidate("microsoft-graph-fetcher.json");

        JsonNode inner = innerComponent(readExample("microsoft-graph-fetcher.json"),
                "fetchers", "msgf", "microsoft-graph-fetcher");
        MicrosoftGraphFetcherConfig config = MicrosoftGraphFetcherConfig.load(inner.toString());
        assertNotNull(config.getClientSecretCredentialsConfig());
        assertEquals("REDACTED-TENANT-UUID",
                config.getClientSecretCredentialsConfig().getTenantId());
        assertEquals("REDACTED-CLIENT-UUID",
                config.getClientSecretCredentialsConfig().getClientId());
        assertTrue(config.getScopes().contains("https://graph.microsoft.com/.default"));
        assertTrue(config.isSpoolToTemp());
    }
}
