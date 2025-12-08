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

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.apache.tika.pipes.api.statestore.StateStore;
import org.apache.tika.pipes.core.statestore.StateStoreManager;
import org.apache.tika.plugins.ExtensionConfig;

/**
 * Test the ComponentStore base class functionality using a concrete test implementation.
 */
public class ComponentStoreTest {

    private StateStore stateStore;
    private TestComponentStore componentStore;

    @BeforeEach
    public void setUp() throws Exception {
        stateStore = StateStoreManager.createDefault();
        // Expire after 1 second, check every 500ms for faster testing
        componentStore = new TestComponentStore(stateStore, 1000L, 500L);
    }

    @AfterEach
    public void tearDown() throws Exception {
        if (componentStore != null) {
            componentStore.close();
        }
    }

    @Test
    public void testComponentType() {
        assertEquals("test-component", componentStore.getComponentType());
    }

    @Test
    public void testKeyPrefixes() {
        assertEquals("test-component:config:", componentStore.getConfigPrefix());
        assertEquals("test-component:access:", componentStore.getAccessPrefix());
    }

    @Test
    public void testCreateComponent() throws Exception {
        TestComponent component = new TestComponent("comp-1");
        componentStore.createComponent(component, component.getExtensionConfig());

        // Verify component exists
        Map<String, TestComponent> components = componentStore.getComponents();
        assertEquals(1, components.size());
        assertTrue(components.containsKey("comp-1"));
    }

    @Test
    public void testGetComponentAndLogAccess() throws Exception {
        TestComponent component = new TestComponent("comp-access");
        componentStore.createComponent(component, component.getExtensionConfig());

        // Access the component
        TestComponent retrieved = componentStore.getComponentAndLogAccess("comp-access");
        assertNotNull(retrieved);
        assertEquals("comp-access", retrieved.getId());

        // Verify access time was recorded
        assertNotNull(stateStore.getAccessTime("test-component:access:comp-access"));
    }

    @Test
    public void testDeleteComponent() throws Exception {
        TestComponent component = new TestComponent("comp-delete");
        componentStore.createComponent(component, component.getExtensionConfig());

        assertTrue(componentStore.getComponents().containsKey("comp-delete"));

        boolean deleted = componentStore.deleteComponent("comp-delete");
        assertTrue(deleted);

        assertFalse(componentStore.getComponents().containsKey("comp-delete"));
    }

    @Test
    public void testDeleteNonExistent() {
        boolean deleted = componentStore.deleteComponent("non-existent");
        assertFalse(deleted);
    }

    @Test
    public void testGetComponentConfigs() throws Exception {
        TestComponent comp1 = new TestComponent("config-1");
        TestComponent comp2 = new TestComponent("config-2");

        componentStore.createComponent(comp1, comp1.getExtensionConfig());
        componentStore.createComponent(comp2, comp2.getExtensionConfig());

        Map<String, ExtensionConfig> configs = componentStore.getComponentConfigs();
        assertEquals(2, configs.size());
        assertTrue(configs.containsKey("config-1"));
        assertTrue(configs.containsKey("config-2"));
    }

    @Test
    public void testMultipleComponents() throws Exception {
        for (int i = 0; i < 5; i++) {
            TestComponent comp = new TestComponent("comp-" + i);
            componentStore.createComponent(comp, comp.getExtensionConfig());
        }

        assertEquals(5, componentStore.getComponents().size());

        // Delete some
        componentStore.deleteComponent("comp-2");
        componentStore.deleteComponent("comp-4");

        Map<String, TestComponent> remaining = componentStore.getComponents();
        assertEquals(3, remaining.size());
        assertTrue(remaining.containsKey("comp-0"));
        assertTrue(remaining.containsKey("comp-1"));
        assertFalse(remaining.containsKey("comp-2"));
        assertTrue(remaining.containsKey("comp-3"));
        assertFalse(remaining.containsKey("comp-4"));
    }

    @Test
    public void testExpiration() throws Exception {
        // Create a component
        TestComponent component = new TestComponent("expire-test");
        componentStore.createComponent(component, component.getExtensionConfig());

        assertNotNull(componentStore.getComponents().get("expire-test"));

        // Wait for expiration (component expires after 1 second, check runs every 500ms)
        // So it should expire within ~1.5 seconds
        Awaitility.await()
                .atMost(Duration.ofSeconds(5))
                .pollInterval(200, TimeUnit.MILLISECONDS)
                .until(() -> componentStore.getComponents().get("expire-test") == null);

        // Verify it's completely removed
        assertNull(componentStore.getComponents().get("expire-test"));
        assertNull(componentStore.getComponentConfigs().get("expire-test"));
    }

    @Test
    public void testAccessPreventsExpiration() throws Exception {
        // Create a component with 2 second expiration
        componentStore.close();
        // Need to create a new StateStore since closing ComponentStore closes the StateStore
        StateStore newStateStore = StateStoreManager.createDefault();
        componentStore = new TestComponentStore(newStateStore, 2000L, 500L);

        TestComponent component = new TestComponent("no-expire");
        componentStore.createComponent(component, component.getExtensionConfig());

        // Keep accessing it
        for (int i = 0; i < 5; i++) {
            Thread.sleep(500);
            componentStore.getComponentAndLogAccess("no-expire");
        }

        // After 2.5 seconds of periodic access, it should still exist
        assertNotNull(componentStore.getComponents().get("no-expire"));
    }

    @Test
    public void testNamespacePrefix() throws Exception {
        TestComponent component = new TestComponent("namespace-test");
        componentStore.createComponent(component, component.getExtensionConfig());

        // Verify keys use correct namespace
        assertTrue(stateStore.listKeys().contains("test-component:config:namespace-test"));

        componentStore.getComponentAndLogAccess("namespace-test");
        assertTrue(stateStore.listKeys().contains("test-component:access:namespace-test"));
    }

    // Test implementation of ComponentStore
    private static class TestComponentStore extends ComponentStore<TestComponent> {
        public TestComponentStore(StateStore stateStore, long expireAfterMillis,
                                 long checkForExpiredDelayMillis) {
            super("test-component", stateStore, expireAfterMillis, checkForExpiredDelayMillis);
        }

        @Override
        protected String getComponentId(TestComponent component) {
            return component.getId();
        }

        @Override
        protected ExtensionConfig getExtensionConfig(TestComponent component) {
            return component.getExtensionConfig();
        }
    }

    // Simple test component class
    private static class TestComponent {
        private final String id;
        private final ExtensionConfig config;

        public TestComponent(String id) {
            this.id = id;
            this.config = new ExtensionConfig(id, "test-factory", "{}");
        }

        public String getId() {
            return id;
        }

        public ExtensionConfig getExtensionConfig() {
            return config;
        }
    }
}
