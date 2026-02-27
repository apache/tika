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
package org.apache.tika.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

public class ParseContextTest {

    @Test
    public void testCopyFromInvalidatesStaleResolvedConfigs() {
        // Simulate a "default" context that has already resolved a config
        ParseContext defaults = new ParseContext();
        defaults.setJsonConfig("my-component", "{\"value\":\"default\"}");
        Object defaultResolved = new Object();
        defaults.setResolvedConfig("my-component", defaultResolved);

        // Simulate a request context that overrides only the jsonConfig (no resolvedConfig)
        ParseContext request = new ParseContext();
        request.setJsonConfig("my-component", "{\"value\":\"override\"}");

        // Merge: defaults + request overlay
        defaults.copyFrom(request);

        // The stale resolvedConfig must be cleared so resolveAll() will re-resolve
        assertNull(defaults.getResolvedConfig("my-component"),
                "copyFrom must clear stale resolvedConfig when jsonConfig is overridden");

        // The jsonConfig should be the override
        assertNotNull(defaults.getJsonConfigs().get("my-component"));
        assertEquals("{\"value\":\"override\"}",
                defaults.getJsonConfigs().get("my-component").json());
    }

    @Test
    public void testCopyFromPreservesResolvedConfigsForUnrelatedKeys() {
        ParseContext defaults = new ParseContext();
        defaults.setJsonConfig("component-a", "{\"a\":true}");
        Object resolvedA = new Object();
        defaults.setResolvedConfig("component-a", resolvedA);

        // Request overrides a DIFFERENT key
        ParseContext request = new ParseContext();
        request.setJsonConfig("component-b", "{\"b\":true}");

        defaults.copyFrom(request);

        // component-a's resolvedConfig should be untouched
        assertEquals(resolvedA, defaults.getResolvedConfig("component-a"),
                "copyFrom must not clear resolvedConfigs for keys not overridden by source");
    }

    @Test
    public void testCopyFromWithSourceResolvedConfigOverrides() {
        ParseContext defaults = new ParseContext();
        defaults.setJsonConfig("my-component", "{\"value\":\"default\"}");
        Object defaultResolved = new Object();
        defaults.setResolvedConfig("my-component", defaultResolved);

        // Source has both jsonConfig AND resolvedConfig (e.g., already resolved upstream)
        ParseContext source = new ParseContext();
        source.setJsonConfig("my-component", "{\"value\":\"override\"}");
        Object sourceResolved = new Object();
        source.setResolvedConfig("my-component", sourceResolved);

        defaults.copyFrom(source);

        // Source's resolvedConfig should win
        assertEquals(sourceResolved, defaults.getResolvedConfig("my-component"),
                "copyFrom should use source's resolvedConfig when source has one");
    }

    @Test
    public void testCopyFromEmptySourcePreservesDefaults() {
        ParseContext defaults = new ParseContext();
        defaults.setJsonConfig("my-component", "{\"value\":\"default\"}");
        Object defaultResolved = new Object();
        defaults.setResolvedConfig("my-component", defaultResolved);

        ParseContext emptySource = new ParseContext();
        defaults.copyFrom(emptySource);

        // Empty source should not disturb existing state
        assertEquals(defaultResolved, defaults.getResolvedConfig("my-component"),
                "copyFrom with empty source must preserve existing resolvedConfigs");
    }
}
