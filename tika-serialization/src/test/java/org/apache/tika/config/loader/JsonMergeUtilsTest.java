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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

/**
 * The defensive copy of the defaults must not run through the setters.
 * Runtime-config subclasses override setters to reject caller input -- often as
 * "any non-empty value is a modification" -- so re-applying the defaults' own
 * values through them throws before the caller's JSON is ever read (TIKA-4843).
 */
public class JsonMergeUtilsTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Stands in for a parser config: a normal field plus a locked one. */
    public static class Config {
        private String name = "default-name";
        private int size = 5;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getSize() {
            return size;
        }

        public void setSize(int size) {
            this.size = size;
        }
    }

    /** Stands in for a RuntimeConfig: {@code size} is locked, and a ceiling has no getter. */
    public static class LockedConfig extends Config {
        // no getter on purpose: init-time state a serialization round-trip would drop
        private int ceiling = 4096;

        public LockedConfig() {
        }

        public LockedConfig(int ceiling) {
            this.ceiling = ceiling;
        }

        public int ceiling() {
            return ceiling;
        }

        @Override
        public void setSize(int size) {
            throw new IllegalStateException("Cannot modify size at runtime");
        }

        @Override
        public void setName(String name) {
            // the "reject any non-empty value" shape, which breaks when the default is non-empty
            if (name != null && !name.isEmpty()) {
                throw new IllegalStateException("Cannot modify name at runtime");
            }
        }
    }

    @Test
    public void testEmptyJsonDoesNotTripLockedSetters() throws Exception {
        LockedConfig merged = JsonMergeUtils.mergeWithDefaults(
                MAPPER, "{}", LockedConfig.class, new LockedConfig());
        assertEquals("default-name", merged.getName());
        assertEquals(5, merged.getSize());
    }

    @Test
    public void testNonBlankDefaultDoesNotTripRejectNonEmptySetter() throws Exception {
        LockedConfig defaults = new LockedConfig();
        // the failing shape from TIKA-4843: a non-empty default re-applied through its own guard
        JsonMergeUtils.mergeWithDefaults(MAPPER, "{}", LockedConfig.class, defaults);
    }

    @Test
    public void testCallerSuppliedLockedFieldStillRejected() {
        IOException e = assertThrows(IOException.class, () -> JsonMergeUtils.mergeWithDefaults(
                MAPPER, "{\"size\": 9}", LockedConfig.class, new LockedConfig()));
        assertTrue(rootMessage(e).contains("Cannot modify size"), rootMessage(e));
    }

    @Test
    public void testInitTimeStateWithoutGetterSurvivesTheCopy() throws Exception {
        LockedConfig merged = JsonMergeUtils.mergeWithDefaults(
                MAPPER, "{}", LockedConfig.class, new LockedConfig(100));
        // a serialize/deserialize round-trip would have reset this to the class default
        assertEquals(100, merged.ceiling());
    }

    @Test
    public void testJsonNodeOverloadBehavesTheSame() throws Exception {
        LockedConfig merged = JsonMergeUtils.mergeWithDefaults(
                MAPPER, MAPPER.readTree("{}"), LockedConfig.class, new LockedConfig(100));
        assertEquals(100, merged.ceiling());
        assertEquals("default-name", merged.getName());
    }

    @Test
    public void testJsonNodeOverloadStillRejectsLockedField() {
        assertThrows(IOException.class, () -> JsonMergeUtils.mergeWithDefaults(
                MAPPER, MAPPER.readTree("{\"size\": 9}"), LockedConfig.class, new LockedConfig()));
    }

    @Test
    public void testUnlockedConfigStillMergesNormally() throws Exception {
        Config merged = JsonMergeUtils.mergeWithDefaults(
                MAPPER, "{\"name\": \"override\"}", Config.class, new Config());
        assertEquals("override", merged.getName());
        assertEquals(5, merged.getSize(), "unspecified fields keep their defaults");
    }

    @Test
    public void testDefaultsObjectIsNotMutated() throws Exception {
        Config defaults = new Config();
        JsonMergeUtils.mergeWithDefaults(MAPPER, "{\"name\": \"override\"}", Config.class, defaults);
        assertEquals("default-name", defaults.getName(), "the caller's defaults must not be touched");
    }

    private static String rootMessage(Throwable t) {
        while (t.getCause() != null) {
            t = t.getCause();
        }
        return String.valueOf(t.getMessage());
    }
}
