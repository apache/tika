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
package org.apache.tika.pipes.core.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import org.apache.tika.plugins.ExtensionConfig;

public class ConfigStoreTest {

    @Test
    public void testInMemoryConfigStore() {
        ConfigStore store = new InMemoryConfigStore();
        
        ExtensionConfig config1 = new ExtensionConfig("id1", "type1", "{\"key\":\"value\"}");
        ExtensionConfig config2 = new ExtensionConfig("id2", "type2", "{\"key2\":\"value2\"}");
        
        // Test put and get
        store.put("id1", config1);
        store.put("id2", config2);
        
        assertNotNull(store.get("id1"));
        assertEquals("id1", store.get("id1").id());
        assertEquals("type1", store.get("id1").name());
        
        assertNotNull(store.get("id2"));
        assertEquals("id2", store.get("id2").id());
        
        // Test containsKey
        assertTrue(store.containsKey("id1"));
        assertTrue(store.containsKey("id2"));
        assertFalse(store.containsKey("id3"));
        
        // Test size
        assertEquals(2, store.size());
        
        // Test keySet
        assertEquals(2, store.keySet().size());
        assertTrue(store.keySet().contains("id1"));
        assertTrue(store.keySet().contains("id2"));
        
        // Test get non-existent
        assertNull(store.get("nonexistent"));
    }
    
    @Test
    public void testConfigStoreThreadSafety() throws InterruptedException {
        ConfigStore store = new InMemoryConfigStore();
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
}
