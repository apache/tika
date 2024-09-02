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
package org.apache.tika.pipes.grpc;

import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.InputStream;
import java.time.Duration;

import org.awaitility.Awaitility;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.pipes.fetcher.AbstractFetcher;
import org.apache.tika.pipes.fetcher.config.FetcherConfig;

class ExpiringFetcherStoreTest {

    @Test
    void createFetcher() {
        try (ExpiringFetcherStore expiringFetcherStore = new ExpiringFetcherStore(1, 5)) {
            FetcherConfig config = getFetcherConfig();
            String fetcherId = "nicksFetcherId";
            expiringFetcherStore.createFetcher(fetcherId, config);

            Assertions.assertNotNull(expiringFetcherStore
                              .getFetcherConfigs()
                    .get(fetcherId));

            Awaitility
                    .await()
                    .atMost(Duration.ofSeconds(60))
                    .until(() -> expiringFetcherStore
                            .getFetcherConfigs()
                            .get(fetcherId) == null);

            assertNull(expiringFetcherStore
                    .getFetcherConfigs()
                    .get(fetcherId));
        }
    }

    @NotNull
    private static FetcherConfig getFetcherConfig() {
        AbstractFetcher fetcher = new AbstractFetcher() {
            @Override
            public InputStream fetch(String fetchKey, Metadata metadata, ParseContext parseContext) {
                return null;
            }
        };
        fetcher.setPluginId("nicksPlugin");
        return new FetcherConfig() {
            @Override
            public String getFetcherPluginId() {
                return fetcher.getPluginId();
            }
        };
    }
}
