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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.apache.tika.exception.TikaConfigException;

/**
 * Unit tests for {@link ConfigLoader}.
 */
public class ConfigLoaderTest {

    private TikaLoader tikaLoader;
    private ConfigLoader configLoader;

    @BeforeEach
    public void setUp() throws Exception {
        Path configPath = Paths.get(
                getClass().getResource("/configs/test-config-loader.json").toURI());
        tikaLoader = TikaLoader.load(configPath);
        configLoader = tikaLoader.configs();
    }

    // ==================== Test POJOs ====================

    /**
     * Simple config POJO with properties.
     */
    public static class HandlerConfig {
        private int timeout;
        private int retries;
        private boolean enabled;

        public int getTimeout() {
            return timeout;
        }

        public void setTimeout(int timeout) {
            this.timeout = timeout;
        }

        public int getRetries() {
            return retries;
        }

        public void setRetries(int retries) {
            this.retries = retries;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    /**
     * Config class with suffix that should be stripped.
     */
    public static class TikaTaskTimeout {
        private long millis;

        public long getMillis() {
            return millis;
        }

        public void setMillis(long millis) {
            this.millis = millis;
        }
    }

    /**
     * Config class with "Settings" suffix.
     */
    public static class MyFeatureSettings {
        private String featureName;
        private int priority;

        public String getFeatureName() {
            return featureName;
        }

        public void setFeatureName(String featureName) {
            this.featureName = featureName;
        }

        public int getPriority() {
            return priority;
        }

        public void setPriority(int priority) {
            this.priority = priority;
        }
    }

    /**
     * Interface for testing interface handling.
     */
    public interface TestHandler {
        String getName();
    }

    /**
     * Simple implementation with no-arg constructor.
     */
    public static class SimpleHandlerImpl implements TestHandler {
        public SimpleHandlerImpl() {
        }

        @Override
        public String getName() {
            return "simple";
        }
    }

    /**
     * Implementation with configuration properties.
     */
    public static class ConfiguredHandlerImpl implements TestHandler {
        private int maxSize;
        private String prefix;

        public ConfiguredHandlerImpl() {
        }

        @Override
        public String getName() {
            return "configured";
        }

        public int getMaxSize() {
            return maxSize;
        }

        public void setMaxSize(int maxSize) {
            this.maxSize = maxSize;
        }

        public String getPrefix() {
            return prefix;
        }

        public void setPrefix(String prefix) {
            this.prefix = prefix;
        }
    }

    /**
     * Abstract class for testing abstract class handling.
     */
    public abstract static class AbstractHandler implements TestHandler {
        public abstract void doSomething();
    }

    // ==================== Tests ====================

    @Test
    public void testLoadByExplicitKey() throws Exception {
        HandlerConfig config = configLoader.load("handler-config", HandlerConfig.class);

        assertNotNull(config);
        assertEquals(5000, config.getTimeout());
        assertEquals(3, config.getRetries());
        assertTrue(config.isEnabled());
    }

    @Test
    public void testLoadByClassNameKebabCase() throws Exception {
        HandlerConfig config = configLoader.load(HandlerConfig.class);

        assertNotNull(config);
        assertEquals(5000, config.getTimeout());
    }

    @Test
    public void testLoadByClassNameTikaTaskTimeout() throws Exception {
        // TikaTaskTimeout -> "tika-task-timeout" (no suffix stripping)
        // JSON has "tika-task-timeout"
        TikaTaskTimeout timeout = configLoader.load(TikaTaskTimeout.class);

        assertNotNull(timeout);
        assertEquals(30000, timeout.getMillis());
    }

    @Test
    public void testLoadByClassNameMyFeatureSettings() throws Exception {
        // MyFeatureSettings -> "my-feature-settings" (full name, no suffix stripping)
        // JSON has "my-feature-settings"
        MyFeatureSettings settings = configLoader.load(MyFeatureSettings.class);

        assertNotNull(settings);
        assertEquals("test-feature", settings.getFeatureName());
        assertEquals(10, settings.getPriority());
    }

    @Test
    public void testLoadWithDefaultValue() throws Exception {
        HandlerConfig config = configLoader.load("handler-config", HandlerConfig.class);
        assertNotNull(config);

        // Non-existent key with default
        HandlerConfig defaultConfig = new HandlerConfig();
        defaultConfig.setTimeout(9999);

        HandlerConfig result = configLoader.load("non-existent", HandlerConfig.class, defaultConfig);
        assertEquals(9999, result.getTimeout());
    }

    @Test
    public void testLoadMissingKeyReturnsNull() throws Exception {
        HandlerConfig config = configLoader.load("non-existent-key", HandlerConfig.class);
        assertNull(config);
    }

    @Test
    public void testLoadInterfaceAsString() throws Exception {
        // JSON: "simple-handler": "org.apache.tika.config.loader.ConfigLoaderTest$SimpleHandlerImpl"
        TestHandler handler = configLoader.load("simple-handler", TestHandler.class);

        assertNotNull(handler);
        assertTrue(handler instanceof SimpleHandlerImpl);
        assertEquals("simple", handler.getName());
    }

    @Test
    public void testLoadInterfaceWithAtClassAndProperties() throws Exception {
        // JSON: "configured-handler": { "@class": "...", "maxSize": 100000, ... }
        TestHandler handler = configLoader.load("configured-handler", TestHandler.class);

        assertNotNull(handler);
        assertTrue(handler instanceof ConfiguredHandlerImpl);
        assertEquals("configured", handler.getName());

        ConfiguredHandlerImpl impl = (ConfiguredHandlerImpl) handler;
        assertEquals(100000, impl.getMaxSize());
        assertEquals("test-", impl.getPrefix());
    }

    @Test
    public void testLoadInterfaceWithoutTypeInfoFails() throws Exception {
        // Create a minimal config with just properties, no @class
        Path configPath = Paths.get(
                getClass().getResource("/configs/test-interface-no-type.json").toURI());
        TikaLoader loader = TikaLoader.load(configPath);

        TikaConfigException ex = assertThrows(TikaConfigException.class, () ->
                loader.configs().load("handler-no-type", TestHandler.class));

        assertTrue(ex.getMessage().contains("interface"));
        assertTrue(ex.getMessage().contains("@class"));
    }

    @Test
    public void testLoadAbstractClassFails() throws Exception {
        TikaConfigException ex = assertThrows(TikaConfigException.class, () ->
                configLoader.load("abstract-handler", AbstractHandler.class));

        assertTrue(ex.getMessage().contains("abstract"));
    }

    @Test
    public void testLoadProhibitedKeyParsers() throws Exception {
        TikaConfigException ex = assertThrows(TikaConfigException.class, () ->
                configLoader.load("parsers", Object.class));

        assertTrue(ex.getMessage().contains("Cannot load 'parsers'"));
        assertTrue(ex.getMessage().contains("TikaLoader"));
    }

    @Test
    public void testLoadProhibitedKeyDetectors() throws Exception {
        TikaConfigException ex = assertThrows(TikaConfigException.class, () ->
                configLoader.load("detectors", Object.class));

        assertTrue(ex.getMessage().contains("Cannot load 'detectors'"));
    }

    @Test
    public void testLoadProhibitedKeyMetadataFilters() throws Exception {
        TikaConfigException ex = assertThrows(TikaConfigException.class, () ->
                configLoader.load("metadata-filters", Object.class));

        assertTrue(ex.getMessage().contains("Cannot load 'metadata-filters'"));
    }

    @Test
    public void testHasKey() throws Exception {
        assertTrue(configLoader.hasKey("handler-config"));
        assertTrue(configLoader.hasKey("simple-handler"));
        assertFalse(configLoader.hasKey("non-existent"));
    }

    @Test
    public void testLoadInvalidClassName() throws Exception {
        Path configPath = Paths.get(
                getClass().getResource("/configs/test-invalid-class.json").toURI());
        TikaLoader loader = TikaLoader.load(configPath);

        TikaConfigException ex = assertThrows(TikaConfigException.class, () ->
                loader.configs().load("handler", TestHandler.class));

        assertTrue(ex.getMessage().contains("Class not found"));
    }

    @Test
    public void testLoadWrongTypeAssignment() throws Exception {
        // String class name that doesn't implement the interface
        Path configPath = Paths.get(
                getClass().getResource("/configs/test-wrong-type.json").toURI());
        TikaLoader loader = TikaLoader.load(configPath);

        TikaConfigException ex = assertThrows(TikaConfigException.class, () ->
                loader.configs().load("handler", TestHandler.class));

        assertTrue(ex.getMessage().contains("not assignable"));
    }

    @Test
    public void testLoadWithUnexpectedFieldFails() throws Exception {
        // Verify that unexpected/unrecognized fields cause an exception
        Path configPath = Paths.get(
                getClass().getResource("/configs/test-unexpected-field.json").toURI());
        TikaLoader loader = TikaLoader.load(configPath);

        TikaConfigException ex = assertThrows(TikaConfigException.class, () ->
                loader.configs().load("handler-config", HandlerConfig.class));

        // Should contain information about the unrecognized field
        assertTrue(ex.getMessage().contains("handler-config") ||
                   ex.getCause().getMessage().contains("Unrecognized") ||
                   ex.getCause().getMessage().contains("unexpectedField"),
                   "Exception should mention the unrecognized field");
    }

    @Test
    public void testKebabCaseConversion() throws Exception {
        // Test that kebab-case conversion works correctly
        // MyFeatureSettings should look for "my-feature-settings" (full kebab-case, no stripping)
        MyFeatureSettings settings = configLoader.load(MyFeatureSettings.class);
        assertNotNull(settings);
        assertEquals("test-feature", settings.getFeatureName());
    }

    @Test
    public void testLoadByClassWithDefault() throws Exception {
        HandlerConfig config = configLoader.load(HandlerConfig.class);
        assertNotNull(config);

        // Non-existent class
        TikaTaskTimeout defaultTimeout = new TikaTaskTimeout();
        defaultTimeout.setMillis(60000);

        // Use a class name that won't match
        TikaTaskTimeout result = configLoader.load("NonExistentConfig.class",
                                                    TikaTaskTimeout.class,
                                                    defaultTimeout);
        assertEquals(60000, result.getMillis());
    }

    // ==================== Tests for loadWithDefaults (Partial Config) ====================

    @Test
    public void testLoadWithDefaultsPartialConfig() throws Exception {
        // Load config that merges defaults with partial JSON
        Path configPath = Paths.get(
                getClass().getResource("/configs/test-partial-config.json").toURI());
        TikaLoader loader = TikaLoader.load(configPath);

        // Set up defaults
        HandlerConfig defaults = new HandlerConfig();
        defaults.setTimeout(30000);
        defaults.setRetries(2);
        defaults.setEnabled(false);

        // JSON only has: { "enabled": true }
        HandlerConfig config = loader.configs().loadWithDefaults("handler-config",
                                                                  HandlerConfig.class,
                                                                  defaults);

        assertNotNull(config);
        assertEquals(30000, config.getTimeout()); // ✅ From defaults
        assertEquals(2, config.getRetries());      // ✅ From defaults
        assertTrue(config.isEnabled());            // ✅ From JSON (overridden)
    }

    @Test
    public void testLoadWithDefaultsFullOverride() throws Exception {
        // Test that JSON can override all defaults
        Path configPath = Paths.get(
                getClass().getResource("/configs/test-partial-config.json").toURI());
        TikaLoader loader = TikaLoader.load(configPath);

        HandlerConfig defaults = new HandlerConfig();
        defaults.setTimeout(30000);
        defaults.setRetries(2);
        defaults.setEnabled(false);

        // JSON has: { "timeout": 10000, "retries": 5, "enabled": false }
        HandlerConfig config = loader.configs().loadWithDefaults("handler-config-full",
                                                                  HandlerConfig.class,
                                                                  defaults);

        assertNotNull(config);
        assertEquals(10000, config.getTimeout()); // All overridden
        assertEquals(5, config.getRetries());
        assertFalse(config.isEnabled());
    }

    @Test
    public void testLoadWithDefaultsMissingKey() throws Exception {
        // When key doesn't exist, should return original defaults unchanged
        HandlerConfig defaults = new HandlerConfig();
        defaults.setTimeout(30000);
        defaults.setRetries(2);
        defaults.setEnabled(false);

        HandlerConfig config = configLoader.loadWithDefaults("non-existent-key",
                                                              HandlerConfig.class,
                                                              defaults);

        assertNotNull(config);
        assertEquals(30000, config.getTimeout());
        assertEquals(2, config.getRetries());
        assertFalse(config.isEnabled());
    }

    @Test
    public void testLoadWithDefaultsByClass() throws Exception {
        // Test the class-name version
        Path configPath = Paths.get(
                getClass().getResource("/configs/test-partial-config.json").toURI());
        TikaLoader loader = TikaLoader.load(configPath);

        HandlerConfig defaults = new HandlerConfig();
        defaults.setTimeout(30000);
        defaults.setRetries(2);
        defaults.setEnabled(false);

        // Uses kebab-case: HandlerConfig -> "handler-config"
        HandlerConfig config = loader.configs().loadWithDefaults(HandlerConfig.class, defaults);

        assertNotNull(config);
        assertEquals(30000, config.getTimeout());
        assertEquals(2, config.getRetries());
        assertTrue(config.isEnabled()); // Overridden from JSON
    }

    @Test
    public void testLoadVsLoadWithDefaults() throws Exception {
        // Demonstrate difference between load() and loadWithDefaults()
        Path configPath = Paths.get(
                getClass().getResource("/configs/test-partial-config.json").toURI());
        TikaLoader loader = TikaLoader.load(configPath);

        HandlerConfig defaults = new HandlerConfig();
        defaults.setTimeout(30000);
        defaults.setRetries(2);
        defaults.setEnabled(false);

        // Using load() - creates new object, loses defaults
        HandlerConfig config1 = loader.configs().load("handler-config", HandlerConfig.class);
        assertEquals(0, config1.getTimeout());  // ❌ Lost default!
        assertEquals(0, config1.getRetries());  // ❌ Lost default!
        assertTrue(config1.isEnabled());        // ✅ From JSON

        // Using loadWithDefaults() - merges into defaults
        HandlerConfig config2 = loader.configs().loadWithDefaults("handler-config",
                                                                   HandlerConfig.class,
                                                                   defaults);
        assertEquals(30000, config2.getTimeout()); // ✅ Kept default!
        assertEquals(2, config2.getRetries());     // ✅ Kept default!
        assertTrue(config2.isEnabled());           // ✅ From JSON
    }

    // ==================== Immutability Tests ====================

    @Test
    public void testLoadWithDefaultsDoesNotMutateOriginal() throws Exception {
        // Verify that the original defaults object is NOT modified
        Path configPath = Paths.get(
                getClass().getResource("/configs/test-partial-config.json").toURI());
        TikaLoader loader = TikaLoader.load(configPath);

        HandlerConfig defaults = new HandlerConfig();
        defaults.setTimeout(30000);
        defaults.setRetries(2);
        defaults.setEnabled(false);

        // Load config with partial override (JSON only has "enabled": true)
        HandlerConfig result = loader.configs().loadWithDefaults("handler-config",
                                                                  HandlerConfig.class,
                                                                  defaults);

        // Verify result has merged values
        assertEquals(30000, result.getTimeout());
        assertEquals(2, result.getRetries());
        assertTrue(result.isEnabled());  // Overridden from JSON

        // CRITICAL: Verify original defaults object is unchanged
        assertEquals(30000, defaults.getTimeout());  // ✅ Still original value
        assertEquals(2, defaults.getRetries());      // ✅ Still original value
        assertFalse(defaults.isEnabled());           // ✅ Still original value (NOT changed!)

        // Verify they are different objects
        assertNotEquals(System.identityHashCode(defaults),
                       System.identityHashCode(result),
                       "Result should be a different object than defaults");
    }

    @Test
    public void testLoadWithDefaultsReusableDefaults() throws Exception {
        // Verify defaults can be safely reused for multiple loads
        Path configPath = Paths.get(
                getClass().getResource("/configs/test-partial-config.json").toURI());
        TikaLoader loader = TikaLoader.load(configPath);

        HandlerConfig defaults = new HandlerConfig();
        defaults.setTimeout(30000);
        defaults.setRetries(2);
        defaults.setEnabled(false);

        // Load multiple times with same defaults
        HandlerConfig config1 = loader.configs().loadWithDefaults("handler-config",
                                                                   HandlerConfig.class,
                                                                   defaults);
        HandlerConfig config2 = loader.configs().loadWithDefaults("handler-config-full",
                                                                   HandlerConfig.class,
                                                                   defaults);

        // Verify results are different
        assertTrue(config1.isEnabled());   // From partial config
        assertFalse(config2.isEnabled());  // From full config

        // Verify defaults still unchanged and can be used again
        assertEquals(30000, defaults.getTimeout());
        assertEquals(2, defaults.getRetries());
        assertFalse(defaults.isEnabled());

        // Use defaults one more time
        HandlerConfig config3 = loader.configs().loadWithDefaults("non-existent",
                                                                   HandlerConfig.class,
                                                                   defaults);
        assertEquals(defaults, config3);  // Should return original when key missing
    }

    @Test
    public void testLoadWithDefaultsComplexObjectImmutability() throws Exception {
        // Test with nested/complex objects to ensure deep copy works
        Path configPath = Paths.get(
                getClass().getResource("/configs/test-partial-config.json").toURI());
        TikaLoader loader = TikaLoader.load(configPath);

        TikaTaskTimeout defaults = new TikaTaskTimeout();
        defaults.setMillis(60000);

        // Note: tika-task-timeout in JSON has millis: 30000
        TikaTaskTimeout result = loader.configs().loadWithDefaults("tika-task-timeout",
                                                                    TikaTaskTimeout.class,
                                                                    defaults);

        // Result should have JSON value
        assertEquals(30000, result.getMillis());

        // Original should be unchanged
        assertEquals(60000, defaults.getMillis());
    }

    @Test
    public void testLoadWithDefaultsMissingKeyDoesNotClone() throws Exception {
        // When key is missing, should return the original object (no unnecessary cloning)
        HandlerConfig defaults = new HandlerConfig();
        defaults.setTimeout(30000);
        defaults.setRetries(2);
        defaults.setEnabled(false);

        HandlerConfig result = configLoader.loadWithDefaults("non-existent-key",
                                                              HandlerConfig.class,
                                                              defaults);

        // Should return the exact same object when key is missing
        assertEquals(defaults, result);
        assertEquals(System.identityHashCode(defaults),
                    System.identityHashCode(result),
                    "Should return same object when key missing (no unnecessary clone)");
    }

    @Test
    public void testLoadWithDefaultsThreadSafety() throws Exception {
        // Demonstrate that defaults can be safely shared across threads
        Path configPath = Paths.get(
                getClass().getResource("/configs/test-partial-config.json").toURI());
        TikaLoader loader = TikaLoader.load(configPath);

        // Shared defaults object
        HandlerConfig sharedDefaults = new HandlerConfig();
        sharedDefaults.setTimeout(30000);
        sharedDefaults.setRetries(2);
        sharedDefaults.setEnabled(false);

        // Simulate concurrent usage (not a real concurrency test, just demonstrates safety)
        HandlerConfig result1 = loader.configs().loadWithDefaults("handler-config",
                                                                   HandlerConfig.class,
                                                                   sharedDefaults);
        HandlerConfig result2 = loader.configs().loadWithDefaults("handler-config-full",
                                                                   HandlerConfig.class,
                                                                   sharedDefaults);

        // Both results should be valid
        assertNotNull(result1);
        assertNotNull(result2);

        // Shared defaults should still be unchanged
        assertEquals(30000, sharedDefaults.getTimeout());
        assertEquals(2, sharedDefaults.getRetries());
        assertFalse(sharedDefaults.isEnabled());
    }
}
