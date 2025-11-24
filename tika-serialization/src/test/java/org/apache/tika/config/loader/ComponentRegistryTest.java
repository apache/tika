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
package org.apache.tika.config.loader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

import org.apache.tika.exception.TikaConfigException;

/**
 * Unit tests for ComponentRegistry.
 */
public class ComponentRegistryTest {

    @Test
    public void testLoadParsersIndex() throws Exception {
        ComponentRegistry registry = new ComponentRegistry("parsers",
                getClass().getClassLoader());

        assertNotNull(registry, "Registry should not be null");

        // Verify test parsers are registered
        assertTrue(registry.hasComponent("configurable-test-parser"),
                "Should have configurable-test-parser");
        assertTrue(registry.hasComponent("fallback-test-parser"),
                "Should have fallback-test-parser");
        assertTrue(registry.hasComponent("minimal-test-parser"),
                "Should have minimal-test-parser");
    }

    @Test
    public void testGetComponentClass() throws Exception {
        ComponentRegistry registry = new ComponentRegistry("parsers",
                getClass().getClassLoader());

        Class<?> clazz = registry.getComponentClass("configurable-test-parser");
        assertNotNull(clazz, "Component class should not be null");
        assertEquals("org.apache.tika.config.loader.ConfigurableTestParser",
                clazz.getName());
    }

    @Test
    public void testGetAllComponents() throws Exception {
        ComponentRegistry registry = new ComponentRegistry("parsers",
                getClass().getClassLoader());

        Map<String, Class<?>> all = registry.getAllComponents();
        assertNotNull(all, "All components map should not be null");
        assertTrue(all.size() >= 3, "Should have at least 3 test parsers");
    }

    @Test
    public void testUnknownComponent() throws Exception {
        ComponentRegistry registry = new ComponentRegistry("parsers",
                getClass().getClassLoader());

        // Should throw exception
        assertThrows(TikaConfigException.class, () -> {
            registry.getComponentClass("non-existent-parser");
        });
    }

    @Test
    public void testNonExistentIndexFile() throws Exception {
        // Should throw exception when index file doesn't exist
        assertThrows(TikaConfigException.class, () -> {
            new ComponentRegistry("non-existent-type", getClass().getClassLoader());
        });
    }
}
