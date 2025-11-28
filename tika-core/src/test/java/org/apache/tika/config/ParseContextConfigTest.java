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
package org.apache.tika.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import org.apache.tika.parser.ParseContext;

/**
 * Tests for ParseContextConfig wrapper.
 * <p>
 * Note: These tests assume tika-serialization is NOT on the classpath (typical for tika-core tests).
 * Additional integration tests in tika-serialization verify behavior when ConfigDeserializer IS available.
 */
public class ParseContextConfigTest {

    public static class TestConfig {
        private String name = "default";

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    @Test
    public void testNoConfigContainer() throws Exception {
        ParseContext context = new ParseContext();
        TestConfig defaultConfig = new TestConfig();
        defaultConfig.setName("my-default");

        TestConfig result = ParseContextConfig.getConfig(context, "test-parser", TestConfig.class, defaultConfig);

        assertEquals(defaultConfig, result);
        assertEquals("my-default", result.getName());
    }

    @Test
    public void testNullContext() throws Exception {
        TestConfig defaultConfig = new TestConfig();
        defaultConfig.setName("my-default");

        TestConfig result = ParseContextConfig.getConfig(null, "test-parser", TestConfig.class, defaultConfig);

        assertEquals(defaultConfig, result);
    }

    @Test
    public void testConfigContainerWithoutMatchingKey() throws Exception {
        ParseContext context = new ParseContext();
        ConfigContainer configContainer = new ConfigContainer();
        configContainer.set("other-parser", "{\"name\":\"other\"}");
        context.set(ConfigContainer.class, configContainer);

        TestConfig defaultConfig = new TestConfig();
        defaultConfig.setName("my-default");

        // No config for "test-parser", should return default
        TestConfig result = ParseContextConfig.getConfig(context, "test-parser", TestConfig.class, defaultConfig);

        assertEquals(defaultConfig, result);
        assertEquals("my-default", result.getName());
    }

    @Test
    public void testHasConfigTrue() {
        ParseContext context = new ParseContext();
        ConfigContainer configContainer = new ConfigContainer();
        configContainer.set("test-parser", "{\"name\":\"test\"}");
        context.set(ConfigContainer.class, configContainer);

        assertTrue(ParseContextConfig.hasConfig(context, "test-parser"));
    }

    @Test
    public void testHasConfigFalse() {
        ParseContext context = new ParseContext();
        ConfigContainer configContainer = new ConfigContainer();
        configContainer.set("other-parser", "{\"name\":\"test\"}");
        context.set(ConfigContainer.class, configContainer);

        assertFalse(ParseContextConfig.hasConfig(context, "test-parser"));
    }

    @Test
    public void testHasConfigNoContainer() {
        ParseContext context = new ParseContext();

        assertFalse(ParseContextConfig.hasConfig(context, "test-parser"));
    }

    @Test
    public void testHasConfigNullContext() {
        assertFalse(ParseContextConfig.hasConfig(null, "test-parser"));
    }

    @Test
    public void testIsConfigDeserializerAvailable() {
        // This will be false in tika-core tests, true in tika-serialization tests
        // Just verify the method works
        boolean available = ParseContextConfig.isConfigDeserializerAvailable();
        assertNotNull(available); // Just checking it doesn't throw
    }

}
