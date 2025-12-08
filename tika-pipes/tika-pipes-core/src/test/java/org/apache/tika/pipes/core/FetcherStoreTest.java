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
package org.apache.tika.pipes.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.pipes.api.fetcher.Fetcher;
import org.apache.tika.pipes.api.statestore.StateStore;
import org.apache.tika.pipes.core.statestore.StateStoreManager;
import org.apache.tika.plugins.ExtensionConfig;

/**
 * Test FetcherStore which extends ComponentStore<Fetcher>.
 * Tests both fetcher-specific methods and inherited ComponentStore functionality.
 */
class FetcherStoreTest {

    private StateStore stateStore;
    private FetcherStore fetcherStore;

    @BeforeEach
    void setUp() throws Exception {
        stateStore = StateStoreManager.createDefault();
        // Short expiration for testing: 60 seconds expire, check every 5 seconds
        fetcherStore = new FetcherStore(stateStore, 60000L, 5000L);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (fetcherStore != null) {
            fetcherStore.close();
        }
    }

    @Test
    void testCreateAndGetFetcher() {
        TestFetcher fetcher = new TestFetcher("test-fetcher-1");
        fetcherStore.createFetcher(fetcher, fetcher.getExtensionConfig());

        // Verify fetcher was created
        Map<String, Fetcher> fetchers = fetcherStore.getFetchers();
        assertEquals(1, fetchers.size());
        assertTrue(fetchers.containsKey("test-fetcher-1"));
        assertNotNull(fetchers.get("test-fetcher-1"));
    }

    @Test
    void testGetFetcherConfigs() {
        TestFetcher fetcher1 = new TestFetcher("fetcher-1");
        TestFetcher fetcher2 = new TestFetcher("fetcher-2");

        fetcherStore.createFetcher(fetcher1, fetcher1.getExtensionConfig());
        fetcherStore.createFetcher(fetcher2, fetcher2.getExtensionConfig());

        Map<String, ExtensionConfig> configs = fetcherStore.getFetcherConfigs();
        assertEquals(2, configs.size());
        assertTrue(configs.containsKey("fetcher-1"));
        assertTrue(configs.containsKey("fetcher-2"));
    }

    @Test
    void testGetFetcherAndLogAccess() {
        TestFetcher fetcher = new TestFetcher("access-test");
        fetcherStore.createFetcher(fetcher, fetcher.getExtensionConfig());

        // Get and log access
        Fetcher retrieved = fetcherStore.getFetcherAndLogAccess("access-test");
        assertNotNull(retrieved);
        assertEquals("access-test", retrieved.getExtensionConfig().id());

        // Verify access time was logged in state store
        try {
            assertNotNull(stateStore.getAccessTime("fetcher:access:access-test"));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void testDeleteFetcher() {
        TestFetcher fetcher = new TestFetcher("delete-test");
        fetcherStore.createFetcher(fetcher, fetcher.getExtensionConfig());

        // Verify it exists
        assertTrue(fetcherStore.getFetchers().containsKey("delete-test"));

        // Delete it
        boolean deleted = fetcherStore.deleteFetcher("delete-test");
        assertTrue(deleted);

        // Verify it's gone
        assertFalse(fetcherStore.getFetchers().containsKey("delete-test"));
        assertNull(fetcherStore.getFetcherAndLogAccess("delete-test"));
    }

    @Test
    void testDeleteNonExistentFetcher() {
        boolean deleted = fetcherStore.deleteFetcher("non-existent");
        assertFalse(deleted);
    }

    @Test
    void testMultipleFetchers() {
        TestFetcher fetcher1 = new TestFetcher("multi-1");
        TestFetcher fetcher2 = new TestFetcher("multi-2");
        TestFetcher fetcher3 = new TestFetcher("multi-3");

        fetcherStore.createFetcher(fetcher1, fetcher1.getExtensionConfig());
        fetcherStore.createFetcher(fetcher2, fetcher2.getExtensionConfig());
        fetcherStore.createFetcher(fetcher3, fetcher3.getExtensionConfig());

        Map<String, Fetcher> fetchers = fetcherStore.getFetchers();
        assertEquals(3, fetchers.size());

        // Delete one
        fetcherStore.deleteFetcher("multi-2");
        fetchers = fetcherStore.getFetchers();
        assertEquals(2, fetchers.size());
        assertTrue(fetchers.containsKey("multi-1"));
        assertFalse(fetchers.containsKey("multi-2"));
        assertTrue(fetchers.containsKey("multi-3"));
    }

    @Test
    void testExpiration() throws Exception {
        // Use short expiration: 1 second expire, check every 500ms
        fetcherStore.close();
        StateStore newStateStore = StateStoreManager.createDefault();
        fetcherStore = new FetcherStore(newStateStore, 1000L, 500L);

        TestFetcher fetcher = new TestFetcher("expire-test");
        fetcherStore.createFetcher(fetcher, fetcher.getExtensionConfig());

        assertNotNull(fetcherStore.getFetchers().get("expire-test"));

        // Wait for expiration
        Awaitility.await()
                .atMost(Duration.ofSeconds(5))
                .pollInterval(200, TimeUnit.MILLISECONDS)
                .until(() -> fetcherStore.getFetchers().get("expire-test") == null);

        // Verify it's completely removed
        assertNull(fetcherStore.getFetchers().get("expire-test"));
        assertNull(fetcherStore.getFetcherConfigs().get("expire-test"));
    }

    @Test
    void testAccessPreventsExpiration() throws Exception {
        // Use 2 second expiration, check every 500ms
        fetcherStore.close();
        StateStore newStateStore = StateStoreManager.createDefault();
        fetcherStore = new FetcherStore(newStateStore, 2000L, 500L);

        TestFetcher fetcher = new TestFetcher("no-expire");
        fetcherStore.createFetcher(fetcher, fetcher.getExtensionConfig());

        // Keep accessing it
        for (int i = 0; i < 5; i++) {
            Thread.sleep(500);
            fetcherStore.getFetcherAndLogAccess("no-expire");
        }

        // After 2.5 seconds of periodic access, it should still exist
        assertNotNull(fetcherStore.getFetchers().get("no-expire"));
    }

    @Test
    void testNamespaceIsolation() throws Exception {
        // Create a fetcher
        TestFetcher fetcher = new TestFetcher("namespace-test");
        fetcherStore.createFetcher(fetcher, fetcher.getExtensionConfig());

        // Verify the keys use the correct namespace prefix
        assertTrue(stateStore.listKeys().contains("fetcher:config:namespace-test"));

        // After accessing, should have access time key
        fetcherStore.getFetcherAndLogAccess("namespace-test");
        assertTrue(stateStore.listKeys().contains("fetcher:access:namespace-test"));
    }

    @Test
    void testLegacySecondsConstructor() throws TikaConfigException {
        // Test the legacy constructor that takes seconds instead of milliseconds
        fetcherStore.close();
        StateStore newStateStore = StateStoreManager.createDefault();
        fetcherStore = new FetcherStore(newStateStore, 60, 5);

        TestFetcher fetcher = new TestFetcher("legacy-test");
        fetcherStore.createFetcher(fetcher, fetcher.getExtensionConfig());

        assertNotNull(fetcherStore.getFetchers().get("legacy-test"));
    }

    // Test helper class
    private static class TestFetcher implements Fetcher {
        private final ExtensionConfig config;

        public TestFetcher(String id) {
            this.config = new ExtensionConfig(id, "test-fetcher-factory", "{}");
        }

        @Override
        public InputStream fetch(String fetchKey, Metadata metadata,
                                 ParseContext parseContext) {
            return null;
        }

        @Override
        public ExtensionConfig getExtensionConfig() {
            return config;
        }
    }
}
