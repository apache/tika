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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.apache.tika.plugins.ExtensionConfig;

public class IgniteConfigStoreTest {

    private IgniteConfigStore store;

    @BeforeEach
    public void setUp() throws Exception {
        store = new IgniteConfigStore();
        store.setIgniteInstanceName("TestIgniteInstance-" + System.currentTimeMillis());
        store.init();
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
    public void testCustomCacheName() throws Exception {
        IgniteConfigStore customStore = new IgniteConfigStore("custom-cache");
        customStore.setIgniteInstanceName("CustomInstance-" + System.currentTimeMillis());
        
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
