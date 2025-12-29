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
package org.apache.tika.serialization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import org.apache.tika.config.ParseContextConfig;
import org.apache.tika.parser.ParseContext;

public class ConfigDeserializerTest {

    /**
     * Simple test config class to verify immutability
     */
    public static class TestConfig {
        private String name = "default";
        private int value = 100;
        private boolean enabled = false;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getValue() {
            return value;
        }

        public void setValue(int value) {
            this.value = value;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    @Test
    public void testDefaultConfigImmutability() throws Exception {
        // Create a default config
        TestConfig defaultConfig = new TestConfig();
        defaultConfig.setName("default");
        defaultConfig.setValue(100);
        defaultConfig.setEnabled(false);

        // Store original values
        String originalName = defaultConfig.getName();
        int originalValue = defaultConfig.getValue();
        boolean originalEnabled = defaultConfig.isEnabled();

        // Create ParseContext with user config that overrides some values
        ParseContext context = new ParseContext();
        context.setJsonConfig("test-config", "{\"name\":\"override\",\"value\":200}");

        // Get merged config
        TestConfig mergedConfig = ConfigDeserializer.getConfig(context, "test-config", TestConfig.class, defaultConfig);

        // Verify merged config has user overrides
        assertNotNull(mergedConfig);
        assertEquals("override", mergedConfig.getName());
        assertEquals(200, mergedConfig.getValue());
        assertEquals(false, mergedConfig.isEnabled()); // Not overridden, should use default

        // CRITICAL: Verify defaultConfig was NOT modified
        assertEquals(originalName, defaultConfig.getName(), "defaultConfig.name should not be modified");
        assertEquals(originalValue, defaultConfig.getValue(), "defaultConfig.value should not be modified");
        assertEquals(originalEnabled, defaultConfig.isEnabled(), "defaultConfig.enabled should not be modified");

        // Verify we got a different object
        assertNotSame(defaultConfig, mergedConfig, "Should return a new config object, not the default");
    }

    @Test
    public void testDefaultConfigImmutabilityMultipleCalls() throws Exception {
        // Create a shared default config (simulating what parsers do)
        TestConfig sharedDefault = new TestConfig();
        sharedDefault.setName("shared");
        sharedDefault.setValue(50);
        sharedDefault.setEnabled(true);

        // First request with one override
        ParseContext context1 = new ParseContext();
        context1.setJsonConfig("test-config", "{\"value\":100}");

        TestConfig config1 = ConfigDeserializer.getConfig(context1, "test-config", TestConfig.class, sharedDefault);

        // Second request with different override
        ParseContext context2 = new ParseContext();
        context2.setJsonConfig("test-config", "{\"name\":\"request2\",\"enabled\":false}");

        TestConfig config2 = ConfigDeserializer.getConfig(context2, "test-config", TestConfig.class, sharedDefault);

        // Verify each request got its own merged config
        assertEquals("shared", config1.getName());
        assertEquals(100, config1.getValue());
        assertEquals(true, config1.isEnabled());

        assertEquals("request2", config2.getName());
        assertEquals(50, config2.getValue()); // Used default
        assertEquals(false, config2.isEnabled());

        // CRITICAL: Verify shared default was never modified
        assertEquals("shared", sharedDefault.getName());
        assertEquals(50, sharedDefault.getValue());
        assertEquals(true, sharedDefault.isEnabled());

        // Verify all three are different objects
        assertNotSame(sharedDefault, config1);
        assertNotSame(sharedDefault, config2);
        assertNotSame(config1, config2);
    }

    @Test
    public void testNoDefaultConfig() throws Exception {
        ParseContext context = new ParseContext();
        context.setJsonConfig("test-config", "{\"name\":\"test\",\"value\":123}");

        TestConfig config = ConfigDeserializer.getConfig(context, "test-config", TestConfig.class, null);

        assertNotNull(config);
        assertEquals("test", config.getName());
        assertEquals(123, config.getValue());
        assertEquals(false, config.isEnabled()); // Default value from class
    }

    @Test
    public void testNoUserConfig() throws Exception {
        TestConfig defaultConfig = new TestConfig();
        defaultConfig.setName("default");
        defaultConfig.setValue(999);

        // No JSON config in ParseContext
        ParseContext context = new ParseContext();

        TestConfig config = ConfigDeserializer.getConfig(context, "test-config", TestConfig.class, defaultConfig);

        // Should return the default config as-is
        assertEquals(defaultConfig, config);
        assertEquals("default", config.getName());
        assertEquals(999, config.getValue());
    }

    @Test
    public void testNoJsonConfig() throws Exception {
        TestConfig defaultConfig = new TestConfig();
        defaultConfig.setName("default");

        ParseContext context = new ParseContext();
        // No JSON config set

        TestConfig config = ConfigDeserializer.getConfig(context, "test-config", TestConfig.class, defaultConfig);

        assertEquals(defaultConfig, config);
    }

    @Test
    public void testHasConfig() throws Exception {
        ParseContext context = new ParseContext();
        context.setJsonConfig("parser-a", "{\"key\":\"value\"}");
        context.setJsonConfig("parser-b", "{\"key\":\"value\"}");

        assertTrue(ConfigDeserializer.hasConfig(context, "parser-a"));
        assertTrue(ConfigDeserializer.hasConfig(context, "parser-b"));
        assertFalse(ConfigDeserializer.hasConfig(context, "parser-c"));
    }

    @Test
    public void testHasConfigNoJsonConfigs() throws Exception {
        ParseContext context = new ParseContext();

        assertFalse(ConfigDeserializer.hasConfig(context, "parser-a"));
    }

    @Test
    public void testHasConfigNullContext() throws Exception {
        assertFalse(ConfigDeserializer.hasConfig(null, "parser-a"));
    }

    @Test
    public void testGetConfigNullContext() throws Exception {
        TestConfig defaultConfig = new TestConfig();

        TestConfig config = ConfigDeserializer.getConfig(null, "test-config", TestConfig.class, defaultConfig);

        assertEquals(defaultConfig, config);
    }

    @Test
    public void testGetConfigWithoutDefault() throws Exception {
        ParseContext context = new ParseContext();
        context.setJsonConfig("test-config", "{\"name\":\"test\"}");

        TestConfig config = ConfigDeserializer.getConfig(context, "test-config", TestConfig.class);

        assertNotNull(config);
        assertEquals("test", config.getName());
    }

    @Test
    public void testGetConfigWithoutDefaultNoUserConfig() throws Exception {
        ParseContext context = new ParseContext();

        TestConfig config = ConfigDeserializer.getConfig(context, "test-config", TestConfig.class);

        assertNull(config);
    }

    @Test
    public void testParseContextConfigWrapperDelegation() throws Exception {
        // Test that ParseContextConfig correctly delegates to ConfigDeserializer
        // when tika-serialization is on the classpath

        TestConfig defaultConfig = new TestConfig();
        defaultConfig.setName("default");
        defaultConfig.setValue(100);

        ParseContext context = new ParseContext();
        context.setJsonConfig("test-parser", "{\"name\":\"override\",\"value\":200}");

        // Use the wrapper
        TestConfig config = ParseContextConfig.getConfig(context, "test-parser", TestConfig.class, defaultConfig);

        // Should get merged config
        assertNotNull(config);
        assertEquals("override", config.getName());
        assertEquals(200, config.getValue());

        // Verify immutability
        assertEquals("default", defaultConfig.getName());
        assertEquals(100, defaultConfig.getValue());
    }

    @Test
    public void testParseContextConfigWrapperNoConfig() throws Exception {
        // Test wrapper when no config is present
        TestConfig defaultConfig = new TestConfig();
        defaultConfig.setName("default");

        ParseContext context = new ParseContext();

        TestConfig config = ParseContextConfig.getConfig(context, "test-parser", TestConfig.class, defaultConfig);

        assertEquals(defaultConfig, config);
    }

    @Test
    public void testParseContextConfigWrapperIsAvailable() {
        // Verify ConfigDeserializer is detected as available in this test environment
        assertTrue(ParseContextConfig.isConfigDeserializerAvailable(),
                "ConfigDeserializer should be available when tika-serialization is on classpath");
    }

    @Test
    public void testParseContextConfigWrapperHasConfig() {
        ParseContext context = new ParseContext();
        context.setJsonConfig("parser-a", "{\"key\":\"value\"}");

        assertTrue(ParseContextConfig.hasConfig(context, "parser-a"));
        assertFalse(ParseContextConfig.hasConfig(context, "parser-b"));
    }
}
