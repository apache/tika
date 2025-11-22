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
package org.apache.tika.plugins;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.pf4j.PluginManager;

import org.apache.tika.exception.TikaConfigException;

public class PluginComponentLoaderTest {

    private ObjectMapper objectMapper;
    private PluginManager pluginManager;
    private MockExtensionFactory factoryA;
    private MockExtensionFactory factoryB;

    // Concrete implementation of TikaExtension for testing
    static class MockTikaExtension implements TikaExtension {
        private final ExtensionConfig config;

        MockTikaExtension(ExtensionConfig config) {
            this.config = config;
        }

        @Override
        public ExtensionConfig getExtensionConfig() {
            return config;
        }
    }

    // Concrete factory class so we can use it with getExtensions(Class)
    static class MockExtensionFactory implements TikaExtensionFactory<MockTikaExtension> {
        private final String name;
        private MockTikaExtension instanceToReturn;

        MockExtensionFactory(String name) {
            this.name = name;
        }

        void setInstanceToReturn(MockTikaExtension instance) {
            this.instanceToReturn = instance;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public MockTikaExtension buildExtension(ExtensionConfig extensionConfig) {
            return instanceToReturn != null ? instanceToReturn : new MockTikaExtension(extensionConfig);
        }
    }

    @BeforeEach
    @SuppressWarnings("unchecked")
    public void setUp() {
        objectMapper = new ObjectMapper();

        factoryA = new MockExtensionFactory("type-a");
        factoryB = new MockExtensionFactory("type-b");

        pluginManager = mock(PluginManager.class);
        // Return non-empty list so loader doesn't try to load/start plugins
        when(pluginManager.getStartedPlugins()).thenReturn(Arrays.asList(mock(org.pf4j.PluginWrapper.class)));
        when(pluginManager.getExtensions(MockExtensionFactory.class))
                .thenReturn(Arrays.asList(factoryA, factoryB));
    }

    @Test
    public void testLoadSingleInstance() throws Exception {
        String json = """
                {
                    "type-a": {
                        "instance1": {
                            "someConfig": "value"
                        }
                    }
                }
                """;

        MockTikaExtension mockInstance = new MockTikaExtension(null);
        factoryA.setInstanceToReturn(mockInstance);

        JsonNode configNode = objectMapper.readTree(json);
        Map<String, MockTikaExtension> instances =
                PluginComponentLoader.loadInstances(pluginManager, MockExtensionFactory.class, configNode);

        assertEquals(1, instances.size());
        assertSame(mockInstance, instances.get("instance1"));
    }

    @Test
    public void testLoadMultipleInstances() throws Exception {
        String json = """
                {
                    "type-a": {
                        "first": {}
                    },
                    "type-b": {
                        "second": {}
                    }
                }
                """;

        MockTikaExtension instanceA = new MockTikaExtension(null);
        MockTikaExtension instanceB = new MockTikaExtension(null);
        factoryA.setInstanceToReturn(instanceA);
        factoryB.setInstanceToReturn(instanceB);

        JsonNode configNode = objectMapper.readTree(json);
        Map<String, MockTikaExtension> instances =
                PluginComponentLoader.loadInstances(pluginManager, MockExtensionFactory.class, configNode);

        assertEquals(2, instances.size());
        assertSame(instanceA, instances.get("first"));
        assertSame(instanceB, instances.get("second"));
    }

    @Test
    public void testMultipleInstancesSameType() throws Exception {
        String json = """
                {
                    "type-a": {
                        "first": { "id": 1 },
                        "second": { "id": 2 }
                    }
                }
                """;

        // Don't set a specific return - let factory create instances with config
        JsonNode configNode = objectMapper.readTree(json);
        Map<String, MockTikaExtension> instances =
                PluginComponentLoader.loadInstances(pluginManager, MockExtensionFactory.class, configNode);

        assertEquals(2, instances.size());
        // Verify configs were passed correctly
        assertEquals("first", instances.get("first").getExtensionConfig().id());
        assertEquals("type-a", instances.get("first").getExtensionConfig().name());
        assertEquals("second", instances.get("second").getExtensionConfig().id());
    }

    @Test
    public void testUnknownTypeThrows() throws Exception {
        String json = """
                {
                    "unknown-type": {
                        "instance1": {}
                    }
                }
                """;

        JsonNode configNode = objectMapper.readTree(json);

        TikaConfigException ex = assertThrows(TikaConfigException.class,
                () -> PluginComponentLoader.loadInstances(pluginManager, MockExtensionFactory.class, configNode));

        assertTrue(ex.getMessage().contains("unknown-type"));
    }

    @Test
    public void testEmptyTypeReturnsNoInstances() throws Exception {
        // A type with no instances is valid - just returns nothing for that type
        String json = """
                {
                    "type-a": {}
                }
                """;

        JsonNode configNode = objectMapper.readTree(json);
        Map<String, MockTikaExtension> instances =
                PluginComponentLoader.loadInstances(pluginManager, MockExtensionFactory.class, configNode);

        assertTrue(instances.isEmpty());
    }

    @Test
    public void testDuplicateInstanceIdAcrossTypesThrows() throws Exception {
        // Same instance ID under different types should throw
        String json = """
                {
                    "type-a": {
                        "same-id": {}
                    },
                    "type-b": {
                        "same-id": {}
                    }
                }
                """;

        JsonNode configNode = objectMapper.readTree(json);

        TikaConfigException ex = assertThrows(TikaConfigException.class,
                () -> PluginComponentLoader.loadInstances(pluginManager, MockExtensionFactory.class, configNode));

        assertTrue(ex.getMessage().contains("same-id"));
        assertTrue(ex.getMessage().contains("Duplicate"));
    }

    @Test
    public void testNullConfigReturnsEmpty() throws Exception {
        Map<String, MockTikaExtension> instances =
                PluginComponentLoader.loadInstances(pluginManager, MockExtensionFactory.class, null);
        assertTrue(instances.isEmpty());
    }

    @Test
    public void testEmptyConfigReturnsEmpty() throws Exception {
        JsonNode configNode = objectMapper.readTree("{}");
        Map<String, MockTikaExtension> instances =
                PluginComponentLoader.loadInstances(pluginManager, MockExtensionFactory.class, configNode);
        assertTrue(instances.isEmpty());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testDuplicateFactoryNamesSkipsDuplicate() throws Exception {
        // Duplicates are silently skipped (first one wins, or plugin version preferred over classpath)
        MockExtensionFactory duplicateFactory = new MockExtensionFactory("type-a"); // same name as factoryA

        PluginManager pmWithDupes = mock(PluginManager.class);
        when(pmWithDupes.getStartedPlugins()).thenReturn(Arrays.asList(mock(org.pf4j.PluginWrapper.class)));
        when(pmWithDupes.getExtensions(MockExtensionFactory.class))
                .thenReturn(Arrays.asList(factoryA, duplicateFactory));

        String json = """
                {
                    "type-a": {
                        "instance1": {}
                    }
                }
                """;
        JsonNode configNode = objectMapper.readTree(json);

        // Should not throw - duplicates are skipped
        Map<String, MockTikaExtension> instances =
                PluginComponentLoader.loadInstances(pmWithDupes, MockExtensionFactory.class, configNode);

        assertEquals(1, instances.size());
    }

    @Test
    public void testNoFactoriesButConfigExistsThrows() throws Exception {
        String json = """
                {
                    "some-type": {
                        "myInstance": {
                            "basePath": "/input"
                        }
                    }
                }
                """;

        // Plugin manager returns no factories
        PluginManager emptyPm = mock(PluginManager.class);
        when(emptyPm.getStartedPlugins()).thenReturn(Arrays.asList(mock(org.pf4j.PluginWrapper.class)));
        when(emptyPm.getExtensions(MockExtensionFactory.class))
                .thenReturn(java.util.Collections.emptyList());

        JsonNode configNode = objectMapper.readTree(json);

        TikaConfigException ex = assertThrows(TikaConfigException.class,
                () -> PluginComponentLoader.loadInstances(emptyPm, MockExtensionFactory.class, configNode));

        assertTrue(ex.getMessage().contains("some-type"));
        assertTrue(ex.getMessage().contains("Unknown type"));
    }

    // ---- Singleton tests ----

    @Test
    public void testLoadSingleton() throws Exception {
        String json = """
                {
                    "type-a": {
                        "someConfig": "value"
                    }
                }
                """;

        MockTikaExtension mockInstance = new MockTikaExtension(null);
        factoryA.setInstanceToReturn(mockInstance);

        JsonNode configNode = objectMapper.readTree(json);
        Optional<MockTikaExtension> result =
                PluginComponentLoader.loadSingleton(pluginManager, MockExtensionFactory.class, configNode);

        assertTrue(result.isPresent());
        assertSame(mockInstance, result.get());
    }

    @Test
    public void testLoadSingletonPassesConfig() throws Exception {
        String json = """
                {
                    "type-a": {
                        "basePath": "/input"
                    }
                }
                """;

        JsonNode configNode = objectMapper.readTree(json);
        Optional<MockTikaExtension> result =
                PluginComponentLoader.loadSingleton(pluginManager, MockExtensionFactory.class, configNode);

        assertTrue(result.isPresent());
        // For singletons, id and name are both the typeName
        assertEquals("type-a", result.get().getExtensionConfig().id());
        assertEquals("type-a", result.get().getExtensionConfig().name());
        JsonNode parsedConfig = objectMapper.readTree(result.get().getExtensionConfig().jsonConfig());
        assertEquals("/input", parsedConfig.get("basePath").asText());
    }

    @Test
    public void testLoadSingletonNullConfigReturnsEmpty() throws Exception {
        Optional<MockTikaExtension> result =
                PluginComponentLoader.loadSingleton(pluginManager, MockExtensionFactory.class, null);

        assertTrue(result.isEmpty());
    }

    @Test
    public void testLoadSingletonEmptyConfigReturnsEmpty() throws Exception {
        JsonNode configNode = objectMapper.readTree("{}");
        Optional<MockTikaExtension> result =
                PluginComponentLoader.loadSingleton(pluginManager, MockExtensionFactory.class, configNode);

        assertTrue(result.isEmpty());
    }

    @Test
    public void testLoadSingletonUnknownTypeThrows() throws Exception {
        String json = """
                {
                    "unknown-type": {
                        "foo": "bar"
                    }
                }
                """;

        JsonNode configNode = objectMapper.readTree(json);

        TikaConfigException ex = assertThrows(TikaConfigException.class,
                () -> PluginComponentLoader.loadSingleton(pluginManager, MockExtensionFactory.class, configNode));

        assertTrue(ex.getMessage().contains("unknown-type"));
        assertTrue(ex.getMessage().contains("Unknown type"));
    }

    @Test
    public void testLoadSingletonMultipleTypesThrows() throws Exception {
        String json = """
                {
                    "type-a": {},
                    "type-b": {}
                }
                """;

        JsonNode configNode = objectMapper.readTree(json);

        TikaConfigException ex = assertThrows(TikaConfigException.class,
                () -> PluginComponentLoader.loadSingleton(pluginManager, MockExtensionFactory.class, configNode));

        assertTrue(ex.getMessage().contains("multiple"));
    }

    // ---- Unnamed instances tests (for composite components like reporters) ----

    @Test
    public void testLoadUnnamedInstances() throws Exception {
        String json = """
                {
                    "type-a": {
                        "setting": "value1"
                    },
                    "type-b": {
                        "setting": "value2"
                    }
                }
                """;

        JsonNode configNode = objectMapper.readTree(json);
        List<MockTikaExtension> instances =
                PluginComponentLoader.loadUnnamedInstances(pluginManager, MockExtensionFactory.class, configNode);

        assertEquals(2, instances.size());
        // Verify order is preserved
        assertEquals("type-a", instances.get(0).getExtensionConfig().name());
        assertEquals("type-b", instances.get(1).getExtensionConfig().name());
        // For unnamed instances, id equals typeName
        assertEquals("type-a", instances.get(0).getExtensionConfig().id());
        assertEquals("type-b", instances.get(1).getExtensionConfig().id());
    }

    @Test
    public void testLoadUnnamedInstancesSingleItem() throws Exception {
        String json = """
                {
                    "type-a": {
                        "config": "test"
                    }
                }
                """;

        JsonNode configNode = objectMapper.readTree(json);
        List<MockTikaExtension> instances =
                PluginComponentLoader.loadUnnamedInstances(pluginManager, MockExtensionFactory.class, configNode);

        assertEquals(1, instances.size());
        assertEquals("type-a", instances.get(0).getExtensionConfig().name());
    }

    @Test
    public void testLoadUnnamedInstancesNullConfigReturnsEmpty() throws Exception {
        List<MockTikaExtension> instances =
                PluginComponentLoader.loadUnnamedInstances(pluginManager, MockExtensionFactory.class, null);

        assertTrue(instances.isEmpty());
    }

    @Test
    public void testLoadUnnamedInstancesEmptyConfigReturnsEmpty() throws Exception {
        JsonNode configNode = objectMapper.readTree("{}");
        List<MockTikaExtension> instances =
                PluginComponentLoader.loadUnnamedInstances(pluginManager, MockExtensionFactory.class, configNode);

        assertTrue(instances.isEmpty());
    }

    @Test
    public void testLoadUnnamedInstancesUnknownTypeThrows() throws Exception {
        String json = """
                {
                    "type-a": {},
                    "unknown-type": {}
                }
                """;

        JsonNode configNode = objectMapper.readTree(json);

        TikaConfigException ex = assertThrows(TikaConfigException.class,
                () -> PluginComponentLoader.loadUnnamedInstances(pluginManager, MockExtensionFactory.class, configNode));

        assertTrue(ex.getMessage().contains("unknown-type"));
        assertTrue(ex.getMessage().contains("Unknown type"));
    }

    @Test
    public void testLoadUnnamedInstancesPassesConfig() throws Exception {
        String json = """
                {
                    "type-a": {
                        "basePath": "/reports",
                        "enabled": true
                    }
                }
                """;

        JsonNode configNode = objectMapper.readTree(json);
        List<MockTikaExtension> instances =
                PluginComponentLoader.loadUnnamedInstances(pluginManager, MockExtensionFactory.class, configNode);

        assertEquals(1, instances.size());
        JsonNode config = objectMapper.readTree(instances.get(0).getExtensionConfig().jsonConfig());
        assertEquals("/reports", config.get("basePath").asText());
        assertTrue(config.get("enabled").asBoolean());
    }
}
