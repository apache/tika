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
package org.apache.tika.pipes.ignite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.pipes.ignite.server.IgniteStoreServer;
import org.apache.tika.plugins.ExtensionConfig;

/**
 * Tests for IgniteConfigStore using Apache Ignite 3.x embedded mode via IgniteStoreServer.
 * Based on official Apache Ignite 3 test patterns.
 */
public class IgniteConfigStoreTest {

    private static final Logger LOG = LoggerFactory.getLogger(IgniteConfigStoreTest.class);

    @TempDir
    private static Path workDir;
    
    private static IgniteStoreServer server;
    private IgniteConfigStore store;

    @BeforeAll
    public static void setUpServer() throws Exception {
        // Set the work directory for Ignite to use the temp directory
        System.setProperty("ignite.work.dir", workDir.toString());
        
        LOG.info("Starting Ignite server with work dir: {}", workDir);
        
        // Start the Ignite server synchronously  
        server = new IgniteStoreServer();
        server.start();
        
        LOG.info("Ignite server started successfully");
    }
    
    @AfterAll
    public static void tearDownServer() {
        if (server != null) {
            server.close();
        }
    }

    @BeforeEach
    public void setUp() throws Exception {
        store = new IgniteConfigStore();
        store.init();
        
        // Clear any existing data from previous tests
        LOG.info("Clearing store before test");
        try {
            // Get all keys and remove them
            Set<String> keysToRemove = new HashSet<>(store.keySet());
            for (String key : keysToRemove) {
                store.remove(key);
            }
            LOG.info("Cleared {} entries from store", keysToRemove.size());
        } catch (Exception e) {
            LOG.warn("Failed to clear store: {}", e.getMessage());
        }
    }

    @AfterEach
    public void tearDown() {
        if (store != null) {
            store.close();
        }
    }

    @Test
    public void testPutAndGet() {
        ExtensionConfig config = new ExtensionConfig("id1", "type1", "{\"key\":\"value\"}");
        
        store.put("id1", config);
        
        ExtensionConfig retrieved = store.get("id1");
        assertNotNull(retrieved);
        assertEquals("id1", retrieved.id());
        assertEquals("type1", retrieved.name());
        assertEquals("{\"key\":\"value\"}", retrieved.json());
    }

    @Test
    public void testContainsKey() {
        ExtensionConfig config = new ExtensionConfig("id1", "type1", "{}");
        
        assertFalse(store.containsKey("id1"));
        
        store.put("id1", config);
        
        assertTrue(store.containsKey("id1"));
        assertFalse(store.containsKey("nonexistent"));
    }

    @Test
    public void testSize() {
        assertEquals(0, store.size());
        
        store.put("id1", new ExtensionConfig("id1", "type1", "{}"));
        assertEquals(1, store.size());
        
        store.put("id2", new ExtensionConfig("id2", "type2", "{}"));
        assertEquals(2, store.size());
        
        store.put("id1", new ExtensionConfig("id1", "type1", "{\"updated\":true}"));
        assertEquals(2, store.size());
    }

    @Test
    public void testKeySet() {
        assertTrue(store.keySet().isEmpty());
        
        store.put("id1", new ExtensionConfig("id1", "type1", "{}"));
        store.put("id2", new ExtensionConfig("id2", "type2", "{}"));
        
        assertEquals(2, store.keySet().size());
        assertTrue(store.keySet().contains("id1"));
        assertTrue(store.keySet().contains("id2"));
        assertFalse(store.keySet().contains("id3"));
    }

    @Test
    public void testGetNonExistent() {
        assertNull(store.get("nonexistent"));
    }

    @Test
    public void testUpdateExisting() {
        ExtensionConfig config1 = new ExtensionConfig("id1", "type1", "{\"version\":1}");
        ExtensionConfig config2 = new ExtensionConfig("id1", "type1", "{\"version\":2}");
        
        store.put("id1", config1);
        assertEquals("{\"version\":1}", store.get("id1").json());
        
        store.put("id1", config2);
        assertEquals("{\"version\":2}", store.get("id1").json());
        assertEquals(1, store.size());
    }

    @Test
    public void testMultipleConfigs() {
        for (int i = 0; i < 10; i++) {
            String id = "config" + i;
            ExtensionConfig config = new ExtensionConfig(id, "type" + i, "{\"index\":" + i + "}");
            store.put(id, config);
        }
        
        assertEquals(10, store.size());
        
        for (int i = 0; i < 10; i++) {
            String id = "config" + i;
            ExtensionConfig config = store.get(id);
            assertNotNull(config);
            assertEquals(id, config.id());
            assertEquals("type" + i, config.name());
        }
    }

    @Test
    public void testUninitializedStore() {
        IgniteConfigStore uninitializedStore = new IgniteConfigStore();
        
        assertThrows(IllegalStateException.class, () -> {
            uninitializedStore.put("id1", new ExtensionConfig("id1", "type1", "{}"));
        });
        
        assertThrows(IllegalStateException.class, () -> {
            uninitializedStore.get("id1");
        });
        
        assertThrows(IllegalStateException.class, () -> {
            uninitializedStore.containsKey("id1");
        });
        
        assertThrows(IllegalStateException.class, () -> {
            uninitializedStore.size();
        });
        
        assertThrows(IllegalStateException.class, () -> {
            uninitializedStore.keySet();
        });
    }

    @Test
    public void testThreadSafety() throws InterruptedException {
        int numThreads = 10;
        int numOperationsPerThread = 100;
        
        Thread[] threads = new Thread[numThreads];
        for (int i = 0; i < numThreads; i++) {
            final int threadId = i;
            threads[i] = new Thread(() -> {
                for (int j = 0; j < numOperationsPerThread; j++) {
                    String id = "thread" + threadId + "_config" + j;
                    ExtensionConfig config = new ExtensionConfig(id, "type", "{}");
                    store.put(id, config);
                    assertNotNull(store.get(id));
                }
            });
            threads[i].start();
        }
        
        for (Thread thread : threads) {
            thread.join();
        }
        
        assertEquals(numThreads * numOperationsPerThread, store.size());
    }

    @Test
    @org.junit.jupiter.api.Disabled("Custom table names require server-side table creation - not yet implemented")
    public void testCustomCacheName() throws Exception {
        IgniteConfigStore customStore = new IgniteConfigStore("custom_table");

        try {
            customStore.init();
            
            ExtensionConfig config = new ExtensionConfig("id1", "type1", "{}");
            customStore.put("id1", config);
            
            assertNotNull(customStore.get("id1"));
            assertEquals("id1", customStore.get("id1").id());
        } finally {
            customStore.close();
        }
    }
}
