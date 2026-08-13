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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Constructor validation and key composition for {@link KeyPrefix}. The write route itself
 * ({@code Metadata#add(KeyPrefix, String, String)}) is covered in
 * {@code MetadataKeyPrefixRouteTest}. */
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
    public void testTrailingDelimiters() {
        // ':' and '-' are the complete live population (geotopic:alt-, ogg:streams-); '.' and
        // '_' were retired with the envi./NER_ renames and are rejected -- tightening later
        // would be breaking, loosening later is free.
        for (char delim : new char[]{':', '-'}) {
            KeyPrefix.file(uniquePrefix("delim-ok") + delim, "d"); // must not throw
        }
        for (char delim : new char[]{'.', '_'}) {
            String prefix = uniquePrefix("delim-bad") + delim;
            assertThrows(IllegalArgumentException.class, () -> KeyPrefix.file(prefix, "d"));
        }
    }

    @Test
    public void testIdenticalRedeclarationReturnsIncumbent() {
        // class re-init against a shared tika-core (webapp redeploy) must not brick the class
        String prefix = uniquePrefix("re-init");
        KeyPrefix first = KeyPrefix.file(prefix, "same");
        assertSame(first, KeyPrefix.file(prefix, "same"));
        assertSame(first, KeyPrefix.get(prefix));
    }

    @Test
    public void testConflictingRedeclarationThrows() {
        String prefix = uniquePrefix("conflict");
        KeyPrefix.file(prefix, "first");
        assertThrows(IllegalStateException.class, () -> KeyPrefix.tool(prefix, "first"));
        assertThrows(IllegalStateException.class, () -> KeyPrefix.file(prefix, "second"));
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
    public void testRegisteredIncludesDeclaredPrefix() {
        String prefix = uniquePrefix("registered");
        KeyPrefix kp = KeyPrefix.file(prefix, "d");
        assertTrue(KeyPrefix.registered().contains(kp));
    }
}
