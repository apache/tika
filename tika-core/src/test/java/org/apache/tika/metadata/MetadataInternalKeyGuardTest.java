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

import org.junit.jupiter.api.Test;

/** Reserved {@code X-TIKA:} keys can't be overwritten by String writes, only via Property. */
public class MetadataInternalKeyGuardTest {

    @Test
    public void testStringWriteToInternalKeyIsDropped() {
        Metadata metadata = new Metadata();
        // hostile scrape
        metadata.set(TikaCoreProperties.TIKA_CONTENT.getName(), "injected");
        assertNull(metadata.get(TikaCoreProperties.TIKA_CONTENT),
                "String write to an internal key must be dropped");
        assertNull(metadata.get(TikaCoreProperties.TIKA_CONTENT.getName()));
    }

    @Test
    public void testStringAddToInternalMultiValueKeyIsDropped() {
        Metadata metadata = new Metadata();
        metadata.add(TikaCoreProperties.TIKA_PARSED_BY.getName(), "org.evil.FakeParser");
        assertArrayEquals(new String[0], metadata.getValues(TikaCoreProperties.TIKA_PARSED_BY),
                "String add to an internal key must be dropped");
    }

    @Test
    public void testStringWriteCannotOverwriteTrustedInternalValue() {
        Metadata metadata = new Metadata();
        metadata.set(TikaCoreProperties.TIKA_CONTENT, "trusted");
        // String-path attempt must not clobber
        metadata.set(TikaCoreProperties.TIKA_CONTENT.getName(), "injected");
        assertEquals("trusted", metadata.get(TikaCoreProperties.TIKA_CONTENT));
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

        metadata.set(unregistered, "dropped");
        assertNull(metadata.get(unregistered));

        metadata.reconstruct(unregistered, "kept", false);
        assertEquals("kept", metadata.get(unregistered));

        metadata.reconstruct(unregistered, "kept2", true);
        assertArrayEquals(new String[] {"kept", "kept2"}, metadata.getValues(unregistered));
    }

    @Test
    public void testTrustedModeAllowsReservedStringWrites() {
        Metadata metadata = new Metadata();
        metadata.setTrusted(true);
        metadata.set(TikaCoreProperties.TIKA_CONTENT.getName(), "trusted");
        assertEquals("trusted", metadata.get(TikaCoreProperties.TIKA_CONTENT));

        metadata.setTrusted(false);
        metadata.set(TikaCoreProperties.TIKA_CONTENT.getName(), "blocked");
        assertEquals("trusted", metadata.get(TikaCoreProperties.TIKA_CONTENT));
    }
}
