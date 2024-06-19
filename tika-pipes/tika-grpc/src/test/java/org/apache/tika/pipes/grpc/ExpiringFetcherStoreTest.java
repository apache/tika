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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.pipes.fetcher.AbstractFetcher;
import org.apache.tika.pipes.fetcher.config.AbstractConfig;

class ExpiringFetcherStoreTest {

    @Test
    void createFetcher() {
        try (ExpiringFetcherStore expiringFetcherStore = new ExpiringFetcherStore(1, 60)) {
            AbstractFetcher fetcher = new AbstractFetcher() {
                @Override
                public InputStream fetch(String fetchKey, Metadata metadata, ParseContext parseContext) {
                    return null;
                }
            };
            fetcher.setName("nick");
            AbstractConfig config = new AbstractConfig() {

            };
            expiringFetcherStore.createFetcher(fetcher, config);

            Assertions.assertNotNull(expiringFetcherStore.getFetchers().get(fetcher.getName()));

            try {
                Thread.sleep(2000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            Assertions.assertNull(expiringFetcherStore.getFetchers().get(fetcher.getName()));
            Assertions.assertNull(expiringFetcherStore.getFetcherConfigs().get(fetcher.getName()));
        }
    }
}
