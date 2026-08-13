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
package org.apache.tika.metadata;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.Date;

import org.junit.jupiter.api.Test;

import org.apache.tika.utils.DateUtils;

/**
 * {@link Metadata#add(KeyPrefix, String, String)} and its {@code Instant} overload: the
 * single write route for document/tool-derived names. Contract under test: append-only,
 * never throws on hostile input -- blank, over-length, flooding names and unrepresentable
 * instants are skipped, not fatal.
 */
public class MetadataKeyPrefixRouteTest {

    private static KeyPrefix unique(String label) {
        return KeyPrefix.file("prefix-route-test-" + label + "-" + System.nanoTime() + ":", "d");
    }

    @Test
    public void testAppendAccumulates() {
        KeyPrefix kp = unique("append");
        Metadata m = new Metadata();
        m.add(kp, "name", "v1");
        m.add(kp, "name", "v2");
        assertArrayEquals(new String[]{"v1", "v2"}, m.getValues(kp.key("name")));
    }

    @Test
    public void testDistinctNamesLandUnderComposedKeys() {
        KeyPrefix kp = unique("distinct");
        Metadata m = new Metadata();
        m.add(kp, "a", "1");
        m.add(kp, "b", "2");
        assertEquals("1", m.get(kp.prefix() + "a"));
        assertEquals("2", m.get(kp.prefix() + "b"));
    }

    @Test
    public void testNullPrefixThrows() {
        Metadata m = new Metadata();
        assertThrows(NullPointerException.class, () -> m.add((KeyPrefix) null, "n", "v"));
    }

    @Test
    public void testHostileNamesAreSkippedNotFatal() {
        KeyPrefix kp = unique("hostile");
        Metadata m = new Metadata();
        m.add(kp, null, "v");
        m.add(kp, "", "v");
        m.add(kp, " \t", "v");
        m.add(kp, "x".repeat(Metadata.MAX_PREFIX_ROUTE_NAME_LENGTH + 1), "v");
        assertEquals(0, m.size());
    }

    @Test
    public void testMaxLengthNameAccepted() {
        KeyPrefix kp = unique("max-len");
        Metadata m = new Metadata();
        String name = "x".repeat(Metadata.MAX_PREFIX_ROUTE_NAME_LENGTH);
        m.add(kp, name, "v");
        assertEquals("v", m.get(kp.key(name)));
    }

    @Test
    public void testNullValueSkipped() {
        KeyPrefix kp = unique("null-value");
        Metadata m = new Metadata();
        m.add(kp, "name", (String) null);
        assertEquals(0, m.size());
    }

    @Test
    public void testNameFloodCapsNewNamesButNotExistingOnes() {
        KeyPrefix kp = unique("flood");
        Metadata m = new Metadata();
        m.add(kp, "first", "v");
        for (int i = 1; i < Metadata.MAX_PREFIX_ROUTE_NAMES; i++) {
            m.add(kp, "n" + i, "v");
        }
        assertEquals(Metadata.MAX_PREFIX_ROUTE_NAMES, m.size());
        m.add(kp, "one-too-many", "v");
        assertNull(m.get(kp.key("one-too-many")));
        assertEquals(Metadata.MAX_PREFIX_ROUTE_NAMES, m.size());
        // appends to names already present must survive the cap
        m.add(kp, "first", "v2");
        assertArrayEquals(new String[]{"v", "v2"}, m.getValues(kp.key("first")));
    }

    @Test
    public void testInstantStoredInCanonicalForm() {
        KeyPrefix kp = unique("instant");
        Metadata m = new Metadata();
        Instant instant = Instant.parse("2026-08-13T01:02:03Z");
        m.add(kp, "when", instant);
        assertEquals(DateUtils.formatDate(Date.from(instant)), m.get(kp.key("when")));
    }

    @Test
    public void testUnrepresentableInstantSkippedNotFatal() {
        KeyPrefix kp = unique("instant-extreme");
        Metadata m = new Metadata();
        m.add(kp, "when", Instant.MAX);
        m.add(kp, "when", (Instant) null);
        assertEquals(0, m.size());
    }
}
