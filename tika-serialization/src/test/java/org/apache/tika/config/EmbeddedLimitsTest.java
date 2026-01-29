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

import org.apache.tika.TikaTest;
import org.apache.tika.config.loader.TikaLoader;
import org.apache.tika.parser.ParseContext;

public class EmbeddedLimitsTest extends TikaTest {

    @Test
    public void testLoadFromConfig() throws Exception {
        TikaLoader loader = TikaLoader.load(getConfigPath(getClass(), "embedded-limits-test.json"));
        EmbeddedLimits limits = loader.configs().load(EmbeddedLimits.class);

        assertNotNull(limits);
        assertEquals(5, limits.getMaxDepth());
        assertTrue(limits.isThrowOnMaxDepth());
        assertEquals(100, limits.getMaxCount());
        assertFalse(limits.isThrowOnMaxCount());
    }

    @Test
    public void testLoadIntoParseContext() throws Exception {
        TikaLoader loader = TikaLoader.load(getConfigPath(getClass(), "embedded-limits-test.json"));
        ParseContext context = loader.loadParseContext();

        EmbeddedLimits limits = context.get(EmbeddedLimits.class);
        assertNotNull(limits);
        assertEquals(5, limits.getMaxDepth());
        assertTrue(limits.isThrowOnMaxDepth());
        assertEquals(100, limits.getMaxCount());
        assertFalse(limits.isThrowOnMaxCount());
    }

    @Test
    public void testDefaults() {
        EmbeddedLimits limits = new EmbeddedLimits();
        assertEquals(EmbeddedLimits.UNLIMITED, limits.getMaxDepth());
        assertFalse(limits.isThrowOnMaxDepth());
        assertEquals(EmbeddedLimits.UNLIMITED, limits.getMaxCount());
        assertFalse(limits.isThrowOnMaxCount());
    }

    @Test
    public void testHelperMethod() {
        // Test with null context
        EmbeddedLimits limits = EmbeddedLimits.get(null);
        assertNotNull(limits);
        assertEquals(EmbeddedLimits.UNLIMITED, limits.getMaxDepth());

        // Test with context that doesn't have EmbeddedLimits
        ParseContext context = new ParseContext();
        limits = EmbeddedLimits.get(context);
        assertNotNull(limits);
        assertEquals(EmbeddedLimits.UNLIMITED, limits.getMaxDepth());

        // Test with context that has EmbeddedLimits
        EmbeddedLimits customLimits = new EmbeddedLimits(10, true, 500, false);
        context.set(EmbeddedLimits.class, customLimits);
        limits = EmbeddedLimits.get(context);
        assertEquals(10, limits.getMaxDepth());
        assertTrue(limits.isThrowOnMaxDepth());
        assertEquals(500, limits.getMaxCount());
        assertFalse(limits.isThrowOnMaxCount());
    }

    @Test
    public void testEqualsAndHashCode() {
        EmbeddedLimits limits1 = new EmbeddedLimits(5, true, 100, false);
        EmbeddedLimits limits2 = new EmbeddedLimits(5, true, 100, false);
        EmbeddedLimits limits3 = new EmbeddedLimits(10, true, 100, false);

        assertEquals(limits1, limits2);
        assertEquals(limits1.hashCode(), limits2.hashCode());
        assertFalse(limits1.equals(limits3));
    }

    @Test
    public void testToString() {
        EmbeddedLimits limits = new EmbeddedLimits(5, true, 100, false);
        String str = limits.toString();
        assertTrue(str.contains("maxDepth=5"));
        assertTrue(str.contains("throwOnMaxDepth=true"));
        assertTrue(str.contains("maxCount=100"));
        assertTrue(str.contains("throwOnMaxCount=false"));
    }
}
