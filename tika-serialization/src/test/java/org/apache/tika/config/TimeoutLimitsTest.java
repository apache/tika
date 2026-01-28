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
        TimeoutLimits limits = loader.configs().load(TimeoutLimits.class);

        assertNotNull(limits);
        assertEquals(120000, limits.getTaskTimeoutMillis());
    }

    @Test
    public void testLoadIntoParseContext() throws Exception {
        TikaLoader loader = TikaLoader.load(getConfigPath(getClass(), "timeout-limits-test.json"));
        ParseContext context = loader.loadParseContext();

        TimeoutLimits limits = context.get(TimeoutLimits.class);
        assertNotNull(limits);
        assertEquals(120000, limits.getTaskTimeoutMillis());
    }

    @Test
    public void testDefaults() {
        TimeoutLimits limits = new TimeoutLimits();
        assertEquals(TimeoutLimits.DEFAULT_TASK_TIMEOUT_MILLIS, limits.getTaskTimeoutMillis());
        assertEquals(60000, limits.getTaskTimeoutMillis());
    }

    @Test
    public void testHelperMethod() {
        // Test with null context
        TimeoutLimits limits = TimeoutLimits.get(null);
        assertNotNull(limits);
        assertEquals(TimeoutLimits.DEFAULT_TASK_TIMEOUT_MILLIS, limits.getTaskTimeoutMillis());

        // Test with context that doesn't have TimeoutLimits
        ParseContext context = new ParseContext();
        limits = TimeoutLimits.get(context);
        assertNotNull(limits);
        assertEquals(TimeoutLimits.DEFAULT_TASK_TIMEOUT_MILLIS, limits.getTaskTimeoutMillis());

        // Test with context that has TimeoutLimits
        TimeoutLimits customLimits = new TimeoutLimits(300000);
        context.set(TimeoutLimits.class, customLimits);
        limits = TimeoutLimits.get(context);
        assertEquals(300000, limits.getTaskTimeoutMillis());
    }

    @Test
    public void testEqualsAndHashCode() {
        TimeoutLimits limits1 = new TimeoutLimits(120000);
        TimeoutLimits limits2 = new TimeoutLimits(120000);
        TimeoutLimits limits3 = new TimeoutLimits(300000);

        assertEquals(limits1, limits2);
        assertEquals(limits1.hashCode(), limits2.hashCode());
        assertFalse(limits1.equals(limits3));
    }

    @Test
    public void testToString() {
        TimeoutLimits limits = new TimeoutLimits(120000);
        String str = limits.toString();
        assertTrue(str.contains("taskTimeoutMillis=120000"));
    }
}
