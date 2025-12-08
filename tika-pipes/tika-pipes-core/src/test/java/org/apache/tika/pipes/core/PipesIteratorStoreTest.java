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

import java.util.Iterator;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.apache.tika.pipes.api.FetchEmitTuple;
import org.apache.tika.pipes.api.pipesiterator.PipesIterator;
import org.apache.tika.pipes.api.statestore.StateStore;
import org.apache.tika.pipes.core.statestore.StateStoreManager;
import org.apache.tika.plugins.ExtensionConfig;

public class PipesIteratorStoreTest {

    private StateStore stateStore;
    private PipesIteratorStore iteratorStore;

    @BeforeEach
    public void setUp() throws Exception {
        stateStore = StateStoreManager.createDefault();
        // Short expiration for testing: 60 seconds expire, check every 5 seconds
        iteratorStore = new PipesIteratorStore(stateStore, 60000L, 5000L);
    }

    @AfterEach
    public void tearDown() throws Exception {
        if (iteratorStore != null) {
            iteratorStore.close();
        }
    }

    @Test
    public void testCreateAndGetIterator() throws Exception {
        TestPipesIterator iterator = new TestPipesIterator("test-iterator-1");
        ExtensionConfig config = iterator.getExtensionConfig();

        iteratorStore.createIterator(iterator, config);

        // Verify iterator was created
        Map<String, PipesIterator> iterators = iteratorStore.getIterators();
        assertEquals(1, iterators.size());
        assertTrue(iterators.containsKey("test-iterator-1"));
        assertNotNull(iterators.get("test-iterator-1"));
    }

    @Test
    public void testGetIteratorConfigs() throws Exception {
        TestPipesIterator iterator1 = new TestPipesIterator("iterator-1");
        TestPipesIterator iterator2 = new TestPipesIterator("iterator-2");

        iteratorStore.createIterator(iterator1, iterator1.getExtensionConfig());
        iteratorStore.createIterator(iterator2, iterator2.getExtensionConfig());

        Map<String, ExtensionConfig> configs = iteratorStore.getIteratorConfigs();
        assertEquals(2, configs.size());
        assertTrue(configs.containsKey("iterator-1"));
        assertTrue(configs.containsKey("iterator-2"));
    }

    @Test
    public void testGetIteratorAndLogAccess() throws Exception {
        TestPipesIterator iterator = new TestPipesIterator("access-test");
        iteratorStore.createIterator(iterator, iterator.getExtensionConfig());

        // Get and log access
        PipesIterator retrieved = iteratorStore.getIteratorAndLogAccess("access-test");
        assertNotNull(retrieved);
        assertEquals("access-test", retrieved.getExtensionConfig().id());

        // Verify access time was logged in state store
        assertNotNull(stateStore.getAccessTime("pipesiterator:access:access-test"));
    }

    @Test
    public void testDeleteIterator() throws Exception {
        TestPipesIterator iterator = new TestPipesIterator("delete-test");
        iteratorStore.createIterator(iterator, iterator.getExtensionConfig());

        // Verify it exists
        assertTrue(iteratorStore.getIterators().containsKey("delete-test"));

        // Delete it
        boolean deleted = iteratorStore.deleteIterator("delete-test");
        assertTrue(deleted);

        // Verify it's gone
        assertFalse(iteratorStore.getIterators().containsKey("delete-test"));
        assertNull(iteratorStore.getIteratorAndLogAccess("delete-test"));
    }

    @Test
    public void testDeleteNonExistentIterator() throws Exception {
        boolean deleted = iteratorStore.deleteIterator("non-existent");
        assertFalse(deleted);
    }

    @Test
    public void testMultipleIterators() throws Exception {
        TestPipesIterator iterator1 = new TestPipesIterator("multi-1");
        TestPipesIterator iterator2 = new TestPipesIterator("multi-2");
        TestPipesIterator iterator3 = new TestPipesIterator("multi-3");

        iteratorStore.createIterator(iterator1, iterator1.getExtensionConfig());
        iteratorStore.createIterator(iterator2, iterator2.getExtensionConfig());
        iteratorStore.createIterator(iterator3, iterator3.getExtensionConfig());

        Map<String, PipesIterator> iterators = iteratorStore.getIterators();
        assertEquals(3, iterators.size());

        // Delete one
        iteratorStore.deleteIterator("multi-2");
        iterators = iteratorStore.getIterators();
        assertEquals(2, iterators.size());
        assertTrue(iterators.containsKey("multi-1"));
        assertFalse(iterators.containsKey("multi-2"));
        assertTrue(iterators.containsKey("multi-3"));
    }

    @Test
    public void testNamespaceIsolation() throws Exception {
        // Create an iterator
        TestPipesIterator iterator = new TestPipesIterator("namespace-test");
        iteratorStore.createIterator(iterator, iterator.getExtensionConfig());

        // Verify the keys use the correct namespace prefix
        assertTrue(stateStore.listKeys().contains("pipesiterator:config:namespace-test"));

        // After accessing, should have access time key
        iteratorStore.getIteratorAndLogAccess("namespace-test");
        assertTrue(stateStore.listKeys().contains("pipesiterator:access:namespace-test"));
    }

    // Test helper class
    private static class TestPipesIterator implements PipesIterator {
        private final ExtensionConfig config;

        public TestPipesIterator(String id) {
            this.config = new ExtensionConfig(id, "test-iterator-factory", "{}");
        }

        @Override
        public Integer call() throws Exception {
            return 0; // No-op for testing
        }

        @Override
        public Iterator<FetchEmitTuple> iterator() {
            return new Iterator<>() {
                @Override
                public boolean hasNext() {
                    return false;
                }

                @Override
                public FetchEmitTuple next() {
                    return null;
                }
            };
        }

        @Override
        public ExtensionConfig getExtensionConfig() {
            return config;
        }
    }
}
