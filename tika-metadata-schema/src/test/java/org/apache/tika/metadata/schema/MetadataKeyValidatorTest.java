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
package org.apache.tika.metadata.schema;

import static org.apache.tika.metadata.schema.MetadataKeyValidator.Classification.CLOSED;
import static org.apache.tika.metadata.schema.MetadataKeyValidator.Classification.OPEN;
import static org.apache.tika.metadata.schema.MetadataKeyValidator.Classification.TEMPLATE;
import static org.apache.tika.metadata.schema.MetadataKeyValidator.Classification.UNKNOWN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

public class MetadataKeyValidatorTest {

    // Controlled fixture: two closed keys and two open prefixes, so classification is unambiguous.
    private final MetadataKeyValidator v = new MetadataKeyValidator(
            java.util.Set.of("dc:title", "X-TIKA:Parsed-By"),
            List.of("html:", "pdf:docinfo:custom:"));

    @Test
    public void closedKeyIsClosed() {
        assertEquals(CLOSED, v.classify("dc:title"));
        assertEquals(CLOSED, v.classify("X-TIKA:Parsed-By"));
    }

    @Test
    public void passthroughPrefixIsOpen() {
        assertEquals(OPEN, v.classify("html:og:image"));
        assertEquals(OPEN, v.classify("pdf:docinfo:custom:MyField"));
    }

    @Test
    public void langAltVariantIsTemplate() {
        assertEquals(TEMPLATE, v.classify("dc:title:fr"));
        assertEquals(TEMPLATE, v.classify("dc:title:x-default"));
        assertEquals(TEMPLATE, v.classify("dc:title:en-US"));
    }

    @Test
    public void unregisteredKeyIsUnknown() {
        assertEquals(UNKNOWN, v.classify("bogus:notakey"));
        assertEquals(UNKNOWN, v.classify("Content-Type"));      // not in this fixture's closed set
        assertEquals(UNKNOWN, v.classify("dc:title:notalangtag_but_long"));
    }

    @Test
    public void edgeCases() {
        assertEquals(UNKNOWN, v.classify(null));
        assertEquals(UNKNOWN, v.classify(""));
        assertEquals(UNKNOWN, v.classify("html:"));             // bare prefix, no suffix
    }

    @Test
    public void findUnknownFiltersLegit() {
        List<String> unknown = v.findUnknown(
                List.of("dc:title", "html:x", "dc:title:fr", "Content-Type", "bogus:k"));
        assertEquals(List.of("Content-Type", "bogus:k"), unknown);
    }

    @Test
    public void loadsRealRegistriesAndAgreesWithThem() {
        MetadataKeyValidator real = MetadataKeyValidator.fromClasspath();
        // Every real closed key classifies CLOSED; a real passthrough suffix classifies OPEN.
        assertEquals(CLOSED, real.classify("dc:title"));
        assertEquals(CLOSED, real.classify("tk:digest:MD5"));   // synthesized digest key
        assertEquals(CLOSED, real.classify("Content-Type"));        // now a Property (HTTP name verbatim)
        assertEquals(CLOSED, real.classify("message:from"));
        assertEquals(OPEN, real.classify("html:twitter:card"));
        assertTrue(real.isLegitimate("dc:title:fr"));
        assertFalse(real.isLegitimate("totally:made:up:key"));
    }
}
