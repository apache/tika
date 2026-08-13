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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

/**
 * Mint-time validation: the public {@code Property} factories reject reserved
 * ({@code tk:}/{@code X-TIKA:}) names, curated reserved constants mint through the
 * package-private path instead, and the digest template factory produces usable Properties.
 */
public class PropertyReservedNameTest {

    // Every public static factory that mints a (non-composite) Property, keyed by method
    // name for readable failure messages. Table-driven so one test method covers all 22
    // shapes instead of 22 near-identical methods (no junit-jupiter-params in this module).
    private static final Map<String, Function<String, Property>> PUBLIC_FACTORIES = publicFactories();

    private static Map<String, Function<String, Property>> publicFactories() {
        Map<String, Function<String, Property>> m = new LinkedHashMap<>();
        m.put("internalBoolean", Property::internalBoolean);
        m.put("internalClosedChoice", n -> Property.internalClosedChoice(n, "a", "b"));
        m.put("internalDate", Property::internalDate);
        m.put("internalDateBag", Property::internalDateBag);
        m.put("internalInteger", Property::internalInteger);
        m.put("internalIntegerSequence", Property::internalIntegerSequence);
        m.put("internalRational", Property::internalRational);
        m.put("internalOpenChoice", n -> Property.internalOpenChoice(n, "a", "b"));
        m.put("internalReal", Property::internalReal);
        m.put("internalText", Property::internalText);
        m.put("internalTextBag", Property::internalTextBag);
        m.put("internalURI", Property::internalURI);
        m.put("externalClosedChoice", n -> Property.externalClosedChoice(n, "a", "b"));
        m.put("externalOpenChoice", n -> Property.externalOpenChoice(n, "a", "b"));
        m.put("externalDate", Property::externalDate);
        m.put("externalReal", Property::externalReal);
        m.put("externalRealSeq", Property::externalRealSeq);
        m.put("externalInteger", Property::externalInteger);
        m.put("externalBoolean", Property::externalBoolean);
        m.put("externalBooleanSeq", Property::externalBooleanSeq);
        m.put("externalText", Property::externalText);
        m.put("externalTextBag", Property::externalTextBag);
        return m;
    }

    @Test
    public void testAllPublicFactoriesRejectTkPrefix() {
        for (Map.Entry<String, Function<String, Property>> e : PUBLIC_FACTORIES.entrySet()) {
            String label = e.getKey();
            Function<String, Property> factory = e.getValue();
            String name = "tk:prop-test-reserved-" + label + "-" + System.nanoTime();
            assertThrows(IllegalArgumentException.class, () -> factory.apply(name),
                    label + " must reject a tk: name");
        }
    }

    @Test
    public void testAllPublicFactoriesRejectLegacyXTikaPrefix() {
        for (Map.Entry<String, Function<String, Property>> e : PUBLIC_FACTORIES.entrySet()) {
            String label = e.getKey();
            Function<String, Property> factory = e.getValue();
            String name = "X-TIKA:prop-test-reserved-" + label + "-" + System.nanoTime();
            assertThrows(IllegalArgumentException.class, () -> factory.apply(name),
                    label + " must reject an X-TIKA: name");
        }
    }

    @Test
    public void testAllPublicFactoriesStillWorkForNonReservedNames() {
        for (Map.Entry<String, Function<String, Property>> e : PUBLIC_FACTORIES.entrySet()) {
            String label = e.getKey();
            String name = "prop-test:reserved-ok-" + label + "-" + System.nanoTime();
            Property p = e.getValue().apply(name);
            assertEquals(name, p.getName(), label + " should still mint a non-reserved name");
            assertSame(p, Property.get(name), label + " should still register");
        }
    }

    @Test
    public void testReservedFactoryRegistersAndResolvesViaPropertyGet() {
        String name = "tk:prop-test-curated-" + System.nanoTime();
        Property p = Property.reservedInternalText(name);
        assertSame(p, Property.get(name), "curated tk: constants must stay in the global registry");
    }

    @Test
    public void testReservedFactoryRejectsNonReservedName() {
        // symmetry check: the reserved path must not silently accept a non-reserved name
        String name = "prop-test:not-reserved-" + System.nanoTime();
        assertThrows(IllegalArgumentException.class, () -> Property.reservedInternalText(name));
    }

    @Test
    public void testDigestPropertyProducesUsableRegisteredProperty() {
        String suffix = "SHA256-" + System.nanoTime();
        Property p = TikaCoreProperties.digestProperty(suffix);

        assertEquals("tk:digest:" + suffix, p.getName());
        assertSame(p, Property.get(p.getName()), "digest Properties must register");

        Metadata metadata = new Metadata();
        metadata.set(p, "abc123");
        assertEquals("abc123", metadata.get(p.getName()));
    }

    @Test
    public void testDigestPropertyRejectsBlankOrNullSuffix() {
        assertThrows(IllegalArgumentException.class, () -> TikaCoreProperties.digestProperty(""));
        assertThrows(IllegalArgumentException.class, () -> TikaCoreProperties.digestProperty(null));
        assertThrows(IllegalArgumentException.class, () -> TikaCoreProperties.digestProperty("   "));
    }

    @Test
    public void testDigestPropertyRejectsWhitespaceInSuffix() {
        assertThrows(IllegalArgumentException.class,
                () -> TikaCoreProperties.digestProperty("SHA 256"));
    }

    @Test
    public void testDigestPropertyAllowsColonSeparatedEncodingSuffix() {
        // real shape produced by DigestDef.metadataKey(), e.g. "SHA256:BASE32"
        String suffix = "SHA256:BASE32-" + System.nanoTime();
        Property p = TikaCoreProperties.digestProperty(suffix);
        assertEquals("tk:digest:" + suffix, p.getName());
    }

    @Test
    public void testMintUnregisteredPathUnaffectedByReservedNameChecks() {
        // mintUnregistered carries no reserved-name validation either way -- it is the
        // non-registering path used by curated in-package code today and by KeyPrefix from
        // stage 3; this documents that stage 2 left it untouched.
        String name = "tk:prop-test-unregistered-" + System.nanoTime();
        Property p = Property.mintUnregistered(name, true, Property.PropertyType.SIMPLE,
                Property.ValueType.TEXT, null);
        assertEquals(name, p.getName());
        assertNull(Property.get(name), "mintUnregistered must never intern");
    }
}
