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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import org.apache.tika.pipes.fetcher.atlassianjwt.AtlassianJwtFetcher;
import org.apache.tika.pipes.fetcher.atlassianjwt.config.AtlassianJwtFetcherConfig;

public class AtlassianJwtFetcherTest {

    /**
     * The one-line factory hand-off is what makes verifySsl real: deleting it silently
     * pins the factory to its verify-on default, with no way to reach an internal CA.
     */
    @Test
    public void testVerifySslReachesClientFactory() throws Exception {
        AtlassianJwtFetcherConfig config = new AtlassianJwtFetcherConfig();
        assertTrue(config.isVerifySsl(), "bare config default must be verify-on");

        AtlassianJwtFetcher fetcher = new AtlassianJwtFetcher(null, config);
        fetcher.initialize();
        assertTrue(fetcher.getHttpClientFactory().isVerifySsl(),
                "the config default must reach the factory");

        fetcher = new AtlassianJwtFetcher(null, config.setVerifySsl(false));
        fetcher.initialize();
        assertFalse(fetcher.getHttpClientFactory().isVerifySsl(),
                "the explicit opt-out must reach the factory");
    }

    @Test
    public void testVerifySslDeserializes() throws Exception {
        assertFalse(AtlassianJwtFetcherConfig.load("{\"verifySsl\":false}").isVerifySsl());
        assertTrue(AtlassianJwtFetcherConfig.load("{}").isVerifySsl());
    }
}
