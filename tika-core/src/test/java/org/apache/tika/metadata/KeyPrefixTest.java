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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import org.apache.tika.metadata.Property.PropertyType;
import org.apache.tika.metadata.Property.ValueType;

/**
 * Stage 3: the {@code PassthroughPrefix} -> {@code KeyPrefix} rename, constructor validation,
 * and the typed unregistered-Property minting factories.
 */
public class KeyPrefixTest {

    // Every registered prefix is a unique, unrepeatable static registration -- give each test
    // its own prefix (nanoTime-suffixed, following PropertyReservedNameTest's convention) so
    // tests never collide with each other or with real parser-declared prefixes.
    private static String uniquePrefix(String label) {
        return "keyprefix-test-" + label + "-" + System.nanoTime() + ":";
    }

    @Test
    public void testRejectsNullPrefix() {
        assertThrows(IllegalArgumentException.class, () -> KeyPrefix.file(null, "d"));
    }

    @Test
    public void testRejectsEmptyPrefix() {
        assertThrows(IllegalArgumentException.class, () -> KeyPrefix.file("", "d"));
    }

    @Test
    public void testRejectsReservedTkPrefix() {
        assertThrows(IllegalArgumentException.class, () -> KeyPrefix.file("tk:", "d"));
    }

    @Test
    public void testRejectsReservedLegacyXTikaPrefix() {
        assertThrows(IllegalArgumentException.class, () -> KeyPrefix.file("X-TIKA:", "d"));
    }

    @Test
    public void testRejectsMissingTrailingDelimiter() {
        String prefix = "keyprefix-test-no-delim-" + System.nanoTime();
        assertThrows(IllegalArgumentException.class, () -> KeyPrefix.file(prefix, "d"));
    }

    @Test
    public void testAcceptsAllCurrentlyUsedTrailingDelimiters() {
        // ':' '.' '-' '_' -- the population enumerated pre-implementation (netcdf: envi.
        // grobid:header_ MboxParser- etc). A later stage normalizes these to ':'; stage 3 must
        // not break the still-live population.
        for (char delim : new char[]{':', '.', '-', '_'}) {
            String prefix = uniquePrefix("delim-" + delim) + delim;
            KeyPrefix.file(prefix, "d"); // must not throw
        }
    }

    @Test
    public void testDuplicateRegistrationThrows() {
        String prefix = uniquePrefix("dup");
        KeyPrefix.file(prefix, "first");
        assertThrows(IllegalStateException.class, () -> KeyPrefix.tool(prefix, "second"));
    }

    @Test
    public void testKeyUnchanged() {
        KeyPrefix kp = KeyPrefix.file(uniquePrefix("key"), "d");
        assertEquals(kp.prefix() + "suffix", kp.key("suffix"));
    }

    @Test
    public void testFileProvenancePreserved() {
        KeyPrefix kp = KeyPrefix.file(uniquePrefix("prov-file"), "d");
        assertEquals(KeyPrefix.Provenance.FILE, kp.provenance());
    }

    @Test
    public void testToolProvenancePreserved() {
        KeyPrefix kp = KeyPrefix.tool(uniquePrefix("prov-tool"), "d");
        assertEquals(KeyPrefix.Provenance.TOOL, kp.provenance());
    }

    @Test
    public void testTextFactoryShape() {
        KeyPrefix kp = KeyPrefix.file(uniquePrefix("text"), "d");
        Property p = kp.text("name");
        assertEquals(kp.prefix() + "name", p.getName());
        assertEquals(PropertyType.SIMPLE, p.getPropertyType());
        assertEquals(ValueType.TEXT, p.getValueType());
        assertNull(Property.get(p.getName()), "minted Property must not register");
    }

    @Test
    public void testTextBagFactoryShape() {
        KeyPrefix kp = KeyPrefix.file(uniquePrefix("textbag"), "d");
        Property p = kp.textBag("name");
        assertEquals(kp.prefix() + "name", p.getName());
        assertEquals(PropertyType.BAG, p.getPropertyType());
        assertEquals(ValueType.TEXT, p.getValueType());
        assertNull(Property.get(p.getName()));
    }

    @Test
    public void testDateFactoryShape() {
        KeyPrefix kp = KeyPrefix.file(uniquePrefix("date"), "d");
        Property p = kp.date("name");
        assertEquals(kp.prefix() + "name", p.getName());
        assertEquals(PropertyType.SIMPLE, p.getPropertyType());
        assertEquals(ValueType.DATE, p.getValueType());
        assertNull(Property.get(p.getName()));
    }

    @Test
    public void testIntegerFactoryShape() {
        KeyPrefix kp = KeyPrefix.file(uniquePrefix("integer"), "d");
        Property p = kp.integer("name");
        assertEquals(kp.prefix() + "name", p.getName());
        assertEquals(PropertyType.SIMPLE, p.getPropertyType());
        assertEquals(ValueType.INTEGER, p.getValueType());
        assertNull(Property.get(p.getName()));
    }

    @Test
    public void testRealFactoryShape() {
        KeyPrefix kp = KeyPrefix.file(uniquePrefix("real"), "d");
        Property p = kp.real("name");
        assertEquals(kp.prefix() + "name", p.getName());
        assertEquals(PropertyType.SIMPLE, p.getPropertyType());
        assertEquals(ValueType.REAL, p.getValueType());
        assertNull(Property.get(p.getName()));
    }

    @Test
    public void testBoolFactoryShape() {
        KeyPrefix kp = KeyPrefix.file(uniquePrefix("bool"), "d");
        Property p = kp.bool("name");
        assertEquals(kp.prefix() + "name", p.getName());
        assertEquals(PropertyType.SIMPLE, p.getPropertyType());
        assertEquals(ValueType.BOOLEAN, p.getValueType());
        assertNull(Property.get(p.getName()));
    }

    @Test
    public void testFactoriesRejectEmptyName() {
        KeyPrefix kp = KeyPrefix.file(uniquePrefix("empty-name"), "d");
        assertThrows(IllegalArgumentException.class, () -> kp.text(""));
        assertThrows(IllegalArgumentException.class, () -> kp.text(null));
        assertThrows(IllegalArgumentException.class, () -> kp.textBag(""));
        assertThrows(IllegalArgumentException.class, () -> kp.date(""));
        assertThrows(IllegalArgumentException.class, () -> kp.integer(""));
        assertThrows(IllegalArgumentException.class, () -> kp.real(""));
        assertThrows(IllegalArgumentException.class, () -> kp.bool(""));
    }

    @Test
    public void testRegisteredIncludesDeclaredPrefix() {
        String prefix = uniquePrefix("registered");
        KeyPrefix kp = KeyPrefix.file(prefix, "d");
        assertTrue(KeyPrefix.registered().contains(kp));
    }
}
