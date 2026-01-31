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

public class OutputLimitsTest extends TikaTest {

    @Test
    public void testLoadFromConfig() throws Exception {
        TikaLoader loader = TikaLoader.load(getConfigPath(getClass(), "output-limits-test.json"));
        ParseContext context = loader.loadParseContext();
        OutputLimits limits = context.get(OutputLimits.class);

        assertNotNull(limits);
        assertEquals(50000, limits.getWriteLimit());
        assertTrue(limits.isThrowOnWriteLimit());
        assertEquals(50, limits.getMaxXmlDepth());
        assertEquals(5, limits.getMaxPackageEntryDepth());
        assertEquals(500000, limits.getZipBombThreshold());
        assertEquals(50, limits.getZipBombRatio());
    }

    @Test
    public void testLoadIntoParseContext() throws Exception {
        TikaLoader loader = TikaLoader.load(getConfigPath(getClass(), "output-limits-test.json"));
        ParseContext context = loader.loadParseContext();

        OutputLimits limits = context.get(OutputLimits.class);
        assertNotNull(limits);
        assertEquals(50000, limits.getWriteLimit());
        assertTrue(limits.isThrowOnWriteLimit());
        assertEquals(50, limits.getMaxXmlDepth());
        assertEquals(5, limits.getMaxPackageEntryDepth());
        assertEquals(500000, limits.getZipBombThreshold());
        assertEquals(50, limits.getZipBombRatio());
    }

    @Test
    public void testDefaults() {
        OutputLimits limits = new OutputLimits();
        assertEquals(OutputLimits.UNLIMITED, limits.getWriteLimit());
        assertFalse(limits.isThrowOnWriteLimit());
        assertEquals(100, limits.getMaxXmlDepth());
        assertEquals(10, limits.getMaxPackageEntryDepth());
        assertEquals(1_000_000, limits.getZipBombThreshold());
        assertEquals(100, limits.getZipBombRatio());
    }

    @Test
    public void testHelperMethod() {
        // Test with null context
        OutputLimits limits = OutputLimits.get(null);
        assertNotNull(limits);
        assertEquals(OutputLimits.UNLIMITED, limits.getWriteLimit());

        // Test with context that doesn't have OutputLimits
        ParseContext context = new ParseContext();
        limits = OutputLimits.get(context);
        assertNotNull(limits);
        assertEquals(OutputLimits.UNLIMITED, limits.getWriteLimit());

        // Test with context that has OutputLimits
        OutputLimits customLimits = new OutputLimits(10000, true, 50, 5, 500000, 50);
        context.set(OutputLimits.class, customLimits);
        limits = OutputLimits.get(context);
        assertEquals(10000, limits.getWriteLimit());
        assertTrue(limits.isThrowOnWriteLimit());
        assertEquals(50, limits.getMaxXmlDepth());
        assertEquals(5, limits.getMaxPackageEntryDepth());
        assertEquals(500000, limits.getZipBombThreshold());
        assertEquals(50, limits.getZipBombRatio());
    }

    @Test
    public void testEqualsAndHashCode() {
        OutputLimits limits1 = new OutputLimits(50000, true, 50, 5, 500000, 50);
        OutputLimits limits2 = new OutputLimits(50000, true, 50, 5, 500000, 50);
        OutputLimits limits3 = new OutputLimits(100000, true, 50, 5, 500000, 50);

        assertEquals(limits1, limits2);
        assertEquals(limits1.hashCode(), limits2.hashCode());
        assertFalse(limits1.equals(limits3));
    }

    @Test
    public void testToString() {
        OutputLimits limits = new OutputLimits(50000, true, 50, 5, 500000, 50);
        String str = limits.toString();
        assertTrue(str.contains("writeLimit=50000"));
        assertTrue(str.contains("throwOnWriteLimit=true"));
        assertTrue(str.contains("maxXmlDepth=50"));
        assertTrue(str.contains("maxPackageEntryDepth=5"));
        assertTrue(str.contains("zipBombThreshold=500000"));
        assertTrue(str.contains("zipBombRatio=50"));
    }
}
