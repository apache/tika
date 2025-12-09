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
package org.apache.tika.pipes.core.statestore;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.apache.tika.pipes.api.statestore.StateStore;
import org.apache.tika.pipes.api.statestore.StateStoreException;

public class InMemoryStateStoreTest {

    private StateStore stateStore;

    @BeforeEach
    public void setUp() throws StateStoreException {
        stateStore = new InMemoryStateStore();
        stateStore.initialize(null);
    }

    @AfterEach
    public void tearDown() throws StateStoreException {
        if (stateStore != null) {
            stateStore.close();
        }
    }

    @Test
    public void testPutAndGet() throws StateStoreException {
        byte[] value = "test-value".getBytes(StandardCharsets.UTF_8);
        stateStore.put("test-key", value);

        byte[] retrieved = stateStore.get("test-key");
        assertNotNull(retrieved);
        assertArrayEquals(value, retrieved);
    }

    @Test
    public void testGetNonExistent() throws StateStoreException {
        byte[] retrieved = stateStore.get("non-existent");
        assertNull(retrieved);
    }

    @Test
    public void testDelete() throws StateStoreException {
        byte[] value = "test-value".getBytes(StandardCharsets.UTF_8);
        stateStore.put("test-key", value);

        assertTrue(stateStore.delete("test-key"));
        assertNull(stateStore.get("test-key"));
    }

    @Test
    public void testDeleteNonExistent() throws StateStoreException {
        assertFalse(stateStore.delete("non-existent"));
    }

    @Test
    public void testListKeys() throws StateStoreException {
        stateStore.put("key1", "value1".getBytes(StandardCharsets.UTF_8));
        stateStore.put("key2", "value2".getBytes(StandardCharsets.UTF_8));
        stateStore.put("key3", "value3".getBytes(StandardCharsets.UTF_8));

        Set<String> keys = stateStore.listKeys();
        assertEquals(3, keys.size());
        assertTrue(keys.contains("key1"));
        assertTrue(keys.contains("key2"));
        assertTrue(keys.contains("key3"));
    }

    @Test
    public void testAccessTime() throws StateStoreException {
        Instant now = Instant.now();
        stateStore.updateAccessTime("test-key", now);

        Instant retrieved = stateStore.getAccessTime("test-key");
        assertNotNull(retrieved);
        assertEquals(now, retrieved);
    }

    @Test
    public void testAccessTimeNonExistent() throws StateStoreException {
        Instant retrieved = stateStore.getAccessTime("non-existent");
        assertNull(retrieved);
    }

    @Test
    public void testAccessTimeDeletedWithKey() throws StateStoreException {
        byte[] value = "test-value".getBytes(StandardCharsets.UTF_8);
        Instant now = Instant.now();

        stateStore.put("test-key", value);
        stateStore.updateAccessTime("test-key", now);

        stateStore.delete("test-key");

        assertNull(stateStore.getAccessTime("test-key"));
    }

    @Test
    public void testContainsKey() throws StateStoreException {
        byte[] value = "test-value".getBytes(StandardCharsets.UTF_8);
        stateStore.put("test-key", value);

        assertTrue(stateStore.containsKey("test-key"));
        assertFalse(stateStore.containsKey("non-existent"));
    }

    @Test
    public void testNullKeyThrows() {
        assertThrows(StateStoreException.class, () -> stateStore.put(null, new byte[0]));
        assertThrows(StateStoreException.class, () -> stateStore.get(null));
        assertThrows(StateStoreException.class, () -> stateStore.delete(null));
        assertThrows(StateStoreException.class,
                () -> stateStore.updateAccessTime(null, Instant.now()));
        assertThrows(StateStoreException.class, () -> stateStore.getAccessTime(null));
    }

    @Test
    public void testNullValueThrows() {
        assertThrows(StateStoreException.class, () -> stateStore.put("key", null));
    }

    @Test
    public void testNullAccessTimeThrows() {
        assertThrows(StateStoreException.class, () -> stateStore.updateAccessTime("key", null));
    }

    @Test
    public void testOperationsAfterCloseThrow() throws StateStoreException {
        stateStore.close();

        assertThrows(StateStoreException.class,
                () -> stateStore.put("key", "value".getBytes(StandardCharsets.UTF_8)));
        assertThrows(StateStoreException.class, () -> stateStore.get("key"));
        assertThrows(StateStoreException.class, () -> stateStore.delete("key"));
        assertThrows(StateStoreException.class, () -> stateStore.listKeys());
        assertThrows(StateStoreException.class,
                () -> stateStore.updateAccessTime("key", Instant.now()));
        assertThrows(StateStoreException.class, () -> stateStore.getAccessTime("key"));
    }

    @Test
    public void testNamespacePrefixes() throws StateStoreException {
        // Test that keys with different namespace prefixes are handled correctly
        stateStore.put("fetcher:config:my-fetcher",
                "fetcher-data".getBytes(StandardCharsets.UTF_8));
        stateStore.put("emitter:config:my-emitter",
                "emitter-data".getBytes(StandardCharsets.UTF_8));
        stateStore.put("pipesiterator:my-iterator",
                "iterator-data".getBytes(StandardCharsets.UTF_8));

        Set<String> keys = stateStore.listKeys();
        assertEquals(3, keys.size());

        assertNotNull(stateStore.get("fetcher:config:my-fetcher"));
        assertNotNull(stateStore.get("emitter:config:my-emitter"));
        assertNotNull(stateStore.get("pipesiterator:my-iterator"));
    }
}
