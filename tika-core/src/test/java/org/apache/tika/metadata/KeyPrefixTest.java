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

import java.util.List;
import java.util.function.BiFunction;

import org.junit.jupiter.api.Test;

import org.apache.tika.metadata.Property.PropertyType;
import org.apache.tika.metadata.Property.ValueType;

/** Constructor validation and the typed unregistered-Property minting factories for
 * {@link KeyPrefix}. */
public class KeyPrefixTest {

    // Every registered prefix is a unique, unrepeatable static registration -- give each test
    // its own prefix (nanoTime-suffixed, following PropertyReservedNameTest's convention) so
    // tests never collide with each other or with real parser-declared prefixes.
    private static String uniquePrefix(String label) {
        return "keyprefix-test-" + label + "-" + System.nanoTime() + ":";
    }

    private record Shape(String label, BiFunction<KeyPrefix, String, Property> factory,
                         PropertyType propertyType, ValueType valueType) {
    }

    // One entry per typed minting factory -- table-driven so the shape and empty-name checks
    // below cover all of them without a near-identical test method each.
    private static final List<Shape> SHAPES = List.of(
            new Shape("text", KeyPrefix::text, PropertyType.SIMPLE, ValueType.TEXT),
            new Shape("textBag", KeyPrefix::textBag, PropertyType.BAG, ValueType.TEXT),
            new Shape("date", KeyPrefix::date, PropertyType.SIMPLE, ValueType.DATE),
            new Shape("integer", KeyPrefix::integer, PropertyType.SIMPLE, ValueType.INTEGER),
            new Shape("real", KeyPrefix::real, PropertyType.SIMPLE, ValueType.REAL),
            new Shape("bool", KeyPrefix::bool, PropertyType.SIMPLE, ValueType.BOOLEAN));

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
        // ':' '.' '-' '_' -- kept loose even though stage 5a normalized the underscore/dot
        // outliers it found (NER_ -> ner:, grobid:header_ -> grobid:header:, envi. -> envi:):
        // MboxParser- (dash) is still live, and the constructor doesn't police convention, only
        // structure (see the class javadoc).
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
    public void testKeyRejectsNullOrEmptySuffix() {
        KeyPrefix kp = KeyPrefix.file(uniquePrefix("key-empty"), "d");
        assertThrows(IllegalArgumentException.class, () -> kp.key(null));
        assertThrows(IllegalArgumentException.class, () -> kp.key(""));
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
    public void testFactoryShapes() {
        for (Shape shape : SHAPES) {
            KeyPrefix kp = KeyPrefix.file(uniquePrefix(shape.label()), "d");
            Property p = shape.factory().apply(kp, "name");
            assertEquals(kp.prefix() + "name", p.getName(), shape.label());
            assertEquals(shape.propertyType(), p.getPropertyType(), shape.label());
            assertEquals(shape.valueType(), p.getValueType(), shape.label());
            assertNull(Property.get(p.getName()), shape.label() + ": minted Property must not register");
        }
    }

    @Test
    public void testFactoriesRejectEmptyOrNullName() {
        KeyPrefix kp = KeyPrefix.file(uniquePrefix("empty-name"), "d");
        for (Shape shape : SHAPES) {
            assertThrows(IllegalArgumentException.class, () -> shape.factory().apply(kp, ""),
                    shape.label());
            assertThrows(IllegalArgumentException.class, () -> shape.factory().apply(kp, null),
                    shape.label());
        }
    }

    @Test
    public void testRegisteredIncludesDeclaredPrefix() {
        String prefix = uniquePrefix("registered");
        KeyPrefix kp = KeyPrefix.file(prefix, "d");
        assertTrue(KeyPrefix.registered().contains(kp));
    }
}
