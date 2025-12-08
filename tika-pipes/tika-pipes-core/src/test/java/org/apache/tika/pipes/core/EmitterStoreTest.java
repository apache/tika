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

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.pipes.api.emitter.EmitData;
import org.apache.tika.pipes.api.emitter.Emitter;
import org.apache.tika.pipes.api.statestore.StateStore;
import org.apache.tika.pipes.core.statestore.StateStoreManager;
import org.apache.tika.plugins.ExtensionConfig;

public class EmitterStoreTest {

    private StateStore stateStore;
    private EmitterStore emitterStore;

    @BeforeEach
    public void setUp() throws Exception {
        stateStore = StateStoreManager.createDefault();
        // Short expiration for testing: 60 seconds expire, check every 5 seconds
        emitterStore = new EmitterStore(stateStore, 60000L, 5000L);
    }

    @AfterEach
    public void tearDown() throws Exception {
        if (emitterStore != null) {
            emitterStore.close();
        }
    }

    @Test
    public void testCreateAndGetEmitter() throws Exception {
        TestEmitter emitter = new TestEmitter("test-emitter-1");
        ExtensionConfig config = emitter.getExtensionConfig();

        emitterStore.createEmitter(emitter, config);

        // Verify emitter was created
        Map<String, Emitter> emitters = emitterStore.getEmitters();
        assertEquals(1, emitters.size());
        assertTrue(emitters.containsKey("test-emitter-1"));
        assertNotNull(emitters.get("test-emitter-1"));
    }

    @Test
    public void testGetEmitterConfigs() throws Exception {
        TestEmitter emitter1 = new TestEmitter("emitter-1");
        TestEmitter emitter2 = new TestEmitter("emitter-2");

        emitterStore.createEmitter(emitter1, emitter1.getExtensionConfig());
        emitterStore.createEmitter(emitter2, emitter2.getExtensionConfig());

        Map<String, ExtensionConfig> configs = emitterStore.getEmitterConfigs();
        assertEquals(2, configs.size());
        assertTrue(configs.containsKey("emitter-1"));
        assertTrue(configs.containsKey("emitter-2"));
    }

    @Test
    public void testGetEmitterAndLogAccess() throws Exception {
        TestEmitter emitter = new TestEmitter("access-test");
        emitterStore.createEmitter(emitter, emitter.getExtensionConfig());

        // Get and log access
        Emitter retrieved = emitterStore.getEmitterAndLogAccess("access-test");
        assertNotNull(retrieved);
        assertEquals("access-test", retrieved.getExtensionConfig().id());

        // Verify access time was logged in state store
        assertNotNull(stateStore.getAccessTime("emitter:access:access-test"));
    }

    @Test
    public void testDeleteEmitter() throws Exception {
        TestEmitter emitter = new TestEmitter("delete-test");
        emitterStore.createEmitter(emitter, emitter.getExtensionConfig());

        // Verify it exists
        assertTrue(emitterStore.getEmitters().containsKey("delete-test"));

        // Delete it
        boolean deleted = emitterStore.deleteEmitter("delete-test");
        assertTrue(deleted);

        // Verify it's gone
        assertFalse(emitterStore.getEmitters().containsKey("delete-test"));
        assertNull(emitterStore.getEmitterAndLogAccess("delete-test"));
    }

    @Test
    public void testDeleteNonExistentEmitter() throws Exception {
        boolean deleted = emitterStore.deleteEmitter("non-existent");
        assertFalse(deleted);
    }

    @Test
    public void testMultipleEmitters() throws Exception {
        TestEmitter emitter1 = new TestEmitter("multi-1");
        TestEmitter emitter2 = new TestEmitter("multi-2");
        TestEmitter emitter3 = new TestEmitter("multi-3");

        emitterStore.createEmitter(emitter1, emitter1.getExtensionConfig());
        emitterStore.createEmitter(emitter2, emitter2.getExtensionConfig());
        emitterStore.createEmitter(emitter3, emitter3.getExtensionConfig());

        Map<String, Emitter> emitters = emitterStore.getEmitters();
        assertEquals(3, emitters.size());

        // Delete one
        emitterStore.deleteEmitter("multi-2");
        emitters = emitterStore.getEmitters();
        assertEquals(2, emitters.size());
        assertTrue(emitters.containsKey("multi-1"));
        assertFalse(emitters.containsKey("multi-2"));
        assertTrue(emitters.containsKey("multi-3"));
    }

    @Test
    public void testNamespaceIsolation() throws Exception {
        // Create an emitter
        TestEmitter emitter = new TestEmitter("namespace-test");
        emitterStore.createEmitter(emitter, emitter.getExtensionConfig());

        // Verify the keys use the correct namespace prefix
        assertTrue(stateStore.listKeys().contains("emitter:config:namespace-test"));

        // After accessing, should have access time key
        emitterStore.getEmitterAndLogAccess("namespace-test");
        assertTrue(stateStore.listKeys().contains("emitter:access:namespace-test"));
    }

    // Test helper class
    private static class TestEmitter implements Emitter {
        private final ExtensionConfig config;

        public TestEmitter(String id) {
            this.config = new ExtensionConfig(id, "test-emitter-factory", "{}");
        }

        @Override
        public void emit(String emitKey, List<Metadata> metadataList, ParseContext parseContext)
                throws IOException {
            // No-op for testing
        }

        @Override
        public void emit(List<? extends EmitData> emitData) throws IOException {
            // No-op for testing
        }

        @Override
        public ExtensionConfig getExtensionConfig() {
            return config;
        }
    }
}
