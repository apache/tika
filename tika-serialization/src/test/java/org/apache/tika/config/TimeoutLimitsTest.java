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

public class TimeoutLimitsTest extends TikaTest {

    @Test
    public void testLoadFromConfig() throws Exception {
        TikaLoader loader = TikaLoader.load(getConfigPath(getClass(), "timeout-limits-test.json"));
        ParseContext context = loader.loadParseContext();
        TimeoutLimits limits = context.get(TimeoutLimits.class);

        assertNotNull(limits);
        assertEquals(120000, limits.getProgressTimeoutMillis());
        assertEquals(7200000, limits.getTotalTaskTimeoutMillis());
    }

    @Test
    public void testLoadIntoParseContext() throws Exception {
        TikaLoader loader = TikaLoader.load(getConfigPath(getClass(), "timeout-limits-test.json"));
        ParseContext context = loader.loadParseContext();

        TimeoutLimits limits = context.get(TimeoutLimits.class);
        assertNotNull(limits);
        assertEquals(120000, limits.getProgressTimeoutMillis());
        assertEquals(7200000, limits.getTotalTaskTimeoutMillis());
    }

    @Test
    public void testDefaults() {
        TimeoutLimits limits = new TimeoutLimits();
        assertEquals(TimeoutLimits.DEFAULT_PROGRESS_TIMEOUT_MILLIS, limits.getProgressTimeoutMillis());
        assertEquals(60000, limits.getProgressTimeoutMillis());
        assertEquals(TimeoutLimits.DEFAULT_TOTAL_TASK_TIMEOUT_MILLIS, limits.getTotalTaskTimeoutMillis());
        assertEquals(3600000, limits.getTotalTaskTimeoutMillis());
    }

    @Test
    public void testHelperMethod() {
        // Test with null context
        TimeoutLimits limits = TimeoutLimits.get(null);
        assertNotNull(limits);
        assertEquals(TimeoutLimits.DEFAULT_PROGRESS_TIMEOUT_MILLIS, limits.getProgressTimeoutMillis());
        assertEquals(TimeoutLimits.DEFAULT_TOTAL_TASK_TIMEOUT_MILLIS, limits.getTotalTaskTimeoutMillis());

        // Test with context that doesn't have TimeoutLimits
        ParseContext context = new ParseContext();
        limits = TimeoutLimits.get(context);
        assertNotNull(limits);
        assertEquals(TimeoutLimits.DEFAULT_PROGRESS_TIMEOUT_MILLIS, limits.getProgressTimeoutMillis());

        // Test with context that has TimeoutLimits
        TimeoutLimits customLimits = new TimeoutLimits(7200000, 300000);
        context.set(TimeoutLimits.class, customLimits);
        limits = TimeoutLimits.get(context);
        assertEquals(300000, limits.getProgressTimeoutMillis());
        assertEquals(7200000, limits.getTotalTaskTimeoutMillis());
    }

    @Test
    public void testGetProcessTimeoutMillis() {
        // Test with null context
        assertEquals(5000, TimeoutLimits.getProcessTimeoutMillis(null, 5000));

        // Test with context that doesn't have TimeoutLimits
        ParseContext context = new ParseContext();
        assertEquals(5000, TimeoutLimits.getProcessTimeoutMillis(context, 5000));

        // Test with context that has TimeoutLimits
        TimeoutLimits limits = new TimeoutLimits(3600000, 60000);
        context.set(TimeoutLimits.class, limits);
        assertEquals(59900, TimeoutLimits.getProcessTimeoutMillis(context, 5000));

        // Test with very small progress timeout
        TimeoutLimits smallLimits = new TimeoutLimits(3600000, 50);
        context.set(TimeoutLimits.class, smallLimits);
        assertEquals(0, TimeoutLimits.getProcessTimeoutMillis(context, 5000));
    }

    @Test
    public void testEqualsAndHashCode() {
        TimeoutLimits limits1 = new TimeoutLimits(3600000, 120000);
        TimeoutLimits limits2 = new TimeoutLimits(3600000, 120000);
        TimeoutLimits limits3 = new TimeoutLimits(7200000, 120000);
        TimeoutLimits limits4 = new TimeoutLimits(3600000, 300000);

        assertEquals(limits1, limits2);
        assertEquals(limits1.hashCode(), limits2.hashCode());
        assertFalse(limits1.equals(limits3));
        assertFalse(limits1.equals(limits4));
    }

    @Test
    public void testToString() {
        TimeoutLimits limits = new TimeoutLimits(3600000, 120000);
        String str = limits.toString();
        assertTrue(str.contains("totalTaskTimeoutMillis=3600000"));
        assertTrue(str.contains("progressTimeoutMillis=120000"));
    }
}
