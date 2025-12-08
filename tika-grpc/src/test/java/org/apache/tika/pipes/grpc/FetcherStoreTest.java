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

import java.io.InputStream;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.pipes.api.fetcher.Fetcher;
import org.apache.tika.pipes.api.statestore.StateStore;
import org.apache.tika.pipes.core.FetcherStore;
import org.apache.tika.pipes.core.statestore.StateStoreManager;
import org.apache.tika.plugins.ExtensionConfig;

class FetcherStoreTest {
    @Test
    void createAndDeleteFetcher() throws Exception {
        StateStore stateStore = StateStoreManager.createDefault();
        try (FetcherStore fetcherStore = new FetcherStore(stateStore, 2000L, 1000L)) {
            Fetcher fetcher = new Fetcher() {
                @Override
                public InputStream fetch(String fetchKey, Metadata metadata,
                                         ParseContext parseContext) {
                    return null;
                }

                @Override
                public ExtensionConfig getExtensionConfig() {
                    return new ExtensionConfig("nick", "factory-plugin-id", "{}");
                }
            };
            fetcherStore.createFetcher(fetcher, fetcher.getExtensionConfig());
            Assertions.assertNotNull(fetcherStore.getFetchers()
                    .get(fetcher.getExtensionConfig().id()));
            Awaitility.await().atMost(Duration.ofSeconds(10))
                    .pollInterval(1000L, TimeUnit.MILLISECONDS)
                    .until(() -> fetcherStore.getFetchers().get(fetcher.getExtensionConfig().id()) == null);
        }
    }
}
