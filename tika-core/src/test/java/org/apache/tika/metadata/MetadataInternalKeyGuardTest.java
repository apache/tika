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
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Reserved Tika-native ({@code tk:}) keys can't be written by String writes -- only via Property; the String route throws. */
public class MetadataInternalKeyGuardTest {

    @Test
    public void testLegacyXTikaPrefixStaysReserved() {
        Metadata metadata = new Metadata();
        // pre-4.0.0 prefix stays reserved so a crafted file can't forge it during the 4.x window
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> metadata.add(TikaCoreProperties.LEGACY_TIKA_META_PREFIX + "Parsed-By", "org.evil.FakeParser"),
                "legacy X-TIKA: String write must still throw");
        assertTrue(ex.getMessage().contains(TikaCoreProperties.LEGACY_TIKA_META_PREFIX + "Parsed-By"));
        assertNull(metadata.get(TikaCoreProperties.LEGACY_TIKA_META_PREFIX + "Parsed-By"));
    }

    @Test
    public void testStringWriteToInternalKeyThrows() {
        Metadata metadata = new Metadata();
        // hostile scrape
        assertThrows(IllegalArgumentException.class,
                () -> metadata.set(TikaCoreProperties.TIKA_CONTENT.getName(), "injected"),
                "String write to an internal key must throw");
        assertNull(metadata.get(TikaCoreProperties.TIKA_CONTENT));
        assertNull(metadata.get(TikaCoreProperties.TIKA_CONTENT.getName()));
    }

    @Test
    public void testStringAddToInternalMultiValueKeyThrows() {
        Metadata metadata = new Metadata();
        assertThrows(IllegalArgumentException.class,
                () -> metadata.add(TikaCoreProperties.TIKA_PARSED_BY.getName(), "org.evil.FakeParser"),
                "String add to an internal key must throw");
        assertArrayEquals(new String[0], metadata.getValues(TikaCoreProperties.TIKA_PARSED_BY));
    }

    @Test
    public void testStringWriteCannotOverwriteTrustedInternalValue() {
        Metadata metadata = new Metadata();
        metadata.set(TikaCoreProperties.TIKA_CONTENT, "trusted");
        // String-path attempt must throw, not clobber
        assertThrows(IllegalArgumentException.class,
                () -> metadata.set(TikaCoreProperties.TIKA_CONTENT.getName(), "injected"));
        assertEquals("trusted", metadata.get(TikaCoreProperties.TIKA_CONTENT));
    }

    @Test
    public void testReservedKeyThrowMessageNamesTheRemedies() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> new Metadata().set("tk:content", "x"));
        // the message is user-facing migration guidance; pin the three remedies it names
        assertTrue(ex.getMessage().contains("putAll"));
        assertTrue(ex.getMessage().contains("KeyPrefix"));
        assertTrue(ex.getMessage().contains("setTrusted"));
    }

    @Test
    public void testPropertyWriteToInternalKeyStillWorks() {
        Metadata metadata = new Metadata();
        metadata.set(TikaCoreProperties.TIKA_CONTENT, "legit");
        assertEquals("legit", metadata.get(TikaCoreProperties.TIKA_CONTENT));

        metadata.add(TikaCoreProperties.TIKA_PARSED_BY, "p1");
        metadata.add(TikaCoreProperties.TIKA_PARSED_BY, "p2");
        assertArrayEquals(new String[] {"p1", "p2"},
                metadata.getValues(TikaCoreProperties.TIKA_PARSED_BY));
    }

    @Test
    public void testNonReservedStringKeysAreUnaffected() {
        Metadata metadata = new Metadata();
        // unregistered key passes through
        metadata.set("my:customKey", "value");
        assertEquals("value", metadata.get("my:customKey"));

        // a registered non-reserved property (dc:description) written by name still works
        metadata.set(TikaCoreProperties.DESCRIPTION.getName(), "hello");
        assertEquals("hello", metadata.get(TikaCoreProperties.DESCRIPTION));
    }

    @Test
    public void testNullNameDoesNotThrow() {
        Metadata metadata = new Metadata();
        // no NPE on a null name
        metadata.set((String) null, "x");
    }

    @Test
    public void testSetNullValueOnReservedKeyRemovesRatherThanThrows() {
        Metadata metadata = new Metadata();
        metadata.setTrusted(TikaCoreProperties.TIKA_CONTENT.getName(), "trusted");
        // set(name, null) is the documented removal path -- it must not be blocked by the
        // reserved-key guard, matching remove(name).
        metadata.set(TikaCoreProperties.TIKA_CONTENT.getName(), (String) null);
        assertNull(metadata.get(TikaCoreProperties.TIKA_CONTENT));
    }

    @Test
    public void testReconstructPreservesRegisteredReservedKey() {
        Metadata metadata = new Metadata();
        metadata.reconstruct(TikaCoreProperties.TIKA_CONTENT.getName(), "the content", false);
        assertEquals("the content", metadata.get(TikaCoreProperties.TIKA_CONTENT));

        metadata.reconstruct(TikaCoreProperties.TIKA_PARSED_BY.getName(), "p1", true);
        metadata.reconstruct(TikaCoreProperties.TIKA_PARSED_BY.getName(), "p2", true);
        assertArrayEquals(new String[] {"p1", "p2"},
                metadata.getValues(TikaCoreProperties.TIKA_PARSED_BY));
    }

    @Test
    public void testReconstructPreservesUnregisteredReservedKey() {
        Metadata metadata = new Metadata();
        String unregistered = TikaCoreProperties.TIKA_META_PREFIX + "noSuchRegisteredProperty";
        assertNull(Property.get(unregistered), "precondition: key must be unregistered");

        assertThrows(IllegalArgumentException.class, () -> metadata.set(unregistered, "thrown"));
        assertNull(metadata.get(unregistered));

        metadata.reconstruct(unregistered, "kept", false);
        assertEquals("kept", metadata.get(unregistered));

        metadata.reconstruct(unregistered, "kept2", true);
        assertArrayEquals(new String[] {"kept", "kept2"}, metadata.getValues(unregistered));
    }

    @Test
    public void testTrustedWriteBypassesGuard() {
        Metadata metadata = new Metadata();
        metadata.setTrusted(TikaCoreProperties.TIKA_CONTENT.getName(), "trusted");
        assertEquals("trusted", metadata.get(TikaCoreProperties.TIKA_CONTENT));

        // untrusted String-path attempt must throw, not clobber
        assertThrows(IllegalArgumentException.class,
                () -> metadata.set(TikaCoreProperties.TIKA_CONTENT.getName(), "blocked"));
        assertEquals("trusted", metadata.get(TikaCoreProperties.TIKA_CONTENT));
    }

    @Test
    public void testTrustedAddBypassesGuard() {
        Metadata metadata = new Metadata();
        metadata.addTrusted(TikaCoreProperties.TIKA_PARSED_BY.getName(), "p1");
        metadata.addTrusted(TikaCoreProperties.TIKA_PARSED_BY.getName(), "p2");
        assertArrayEquals(new String[] {"p1", "p2"},
                metadata.getValues(TikaCoreProperties.TIKA_PARSED_BY));
    }

    @Test
    public void testReconstructNonReservedRoutesThroughStringPath() {
        Metadata metadata = new Metadata();
        metadata.reconstruct("my:customKey", "v1", false);
        assertEquals("v1", metadata.get("my:customKey"));

        metadata.reconstruct("my:customKey", "v2", true);
        assertArrayEquals(new String[] {"v1", "v2"}, metadata.getValues("my:customKey"));
    }

    /**
     * {@code reconstruct} is a deliberately trusted route, not subject to the String-route
     * guard -- for both a registered curated Property and an unregistered reserved name,
     * contrasted directly against the throw on the same names via the String route.
     */
    @Test
    public void testReconstructIsNotSubjectToReservedKeyGuard() {
        Metadata metadata = new Metadata();

        // registered curated Property
        assertThrows(IllegalArgumentException.class,
                () -> metadata.set(TikaCoreProperties.TIKA_CONTENT.getName(), "thrown-by-guard"));
        assertNull(metadata.get(TikaCoreProperties.TIKA_CONTENT));
        metadata.reconstruct(TikaCoreProperties.TIKA_CONTENT.getName(), "lands-via-reconstruct", false);
        assertEquals("lands-via-reconstruct", metadata.get(TikaCoreProperties.TIKA_CONTENT));

        // reserved but unregistered
        String unregistered = TikaCoreProperties.TIKA_META_PREFIX + "noSuchRegisteredProperty2";
        assertThrows(IllegalArgumentException.class, () -> metadata.set(unregistered, "thrown-by-guard"));
        assertNull(metadata.get(unregistered));
        metadata.reconstruct(unregistered, "lands-via-reconstruct", false);
        assertEquals("lands-via-reconstruct", metadata.get(unregistered));
    }
}
