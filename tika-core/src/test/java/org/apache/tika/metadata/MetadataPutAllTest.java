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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import org.apache.tika.metadata.writelimiter.StandardMetadataLimiterFactory;

/** {@link Metadata#putAll(Metadata)}: the provenance-preserving per-key-replace copy API. */
public class MetadataPutAllTest {

    @Test
    public void testMultiValuedKeyCopiesAllValuesInOrder() {
        Metadata src = new Metadata();
        src.add("multi", "a");
        src.add("multi", "b");
        src.add("multi", "c");

        Metadata dest = new Metadata();
        dest.putAll(src);

        // the manual `dest.set(n, src.get(n))` loop this replaces would collapse to just "a"
        assertArrayEquals(new String[] {"a", "b", "c"}, dest.getValues("multi"));
    }

    @Test
    public void testReservedKeyCopiesThroughTrustedRoute() {
        Metadata src = new Metadata();
        src.reconstruct(TikaCoreProperties.TIKA_CONTENT.getName(), "the content", false);

        Metadata dest = new Metadata();
        dest.putAll(src);

        assertEquals("the content", dest.get(TikaCoreProperties.TIKA_CONTENT));
    }

    @Test
    public void testReservedMultiValuedKeyReplacesWholesale() {
        String name = TikaCoreProperties.TIKA_PARSED_BY.getName();
        Property property = Property.get(name);
        assertNotNull(property, "precondition: tk:parsed-by must be a registered Property");
        assertTrue(property.isMultiValuePermitted(), "precondition: tk:parsed-by must be a bag");

        Metadata src = new Metadata();
        src.addTrusted(name, "v1");
        src.addTrusted(name, "v2");

        Metadata dest = new Metadata();
        dest.addTrusted(name, "pre-existing");

        dest.putAll(src);

        // per-key replace holds for the reserved Property route too, values in order
        assertArrayEquals(new String[] {"v1", "v2"}, dest.getValues(name));
    }

    @Test
    public void testLimiterCapsValuesWithoutThrowing() {
        Metadata src = new Metadata();
        src.add("multi", "a");
        src.add("multi", "b");
        src.add("multi", "c");
        src.add("multi", "d");

        StandardMetadataLimiterFactory factory = new StandardMetadataLimiterFactory();
        factory.setMaxValuesPerField(2);
        Metadata dest = new Metadata(factory.newInstance());

        dest.putAll(src);

        // the limiter silently caps at maxValuesPerField and flags truncation; no throw
        assertArrayEquals(new String[] {"a", "b"}, dest.getValues("multi"));
        assertEquals("true", dest.get(TikaCoreProperties.TRUNCATED_METADATA));
    }

    @Test
    public void testPerKeyReplaceSemantics() {
        Metadata src = new Metadata();
        src.add("k", "only");

        Metadata dest = new Metadata();
        dest.add("k", "v1");
        dest.add("k", "v2");
        dest.add("k", "v3");

        dest.putAll(src);

        assertArrayEquals(new String[] {"only"}, dest.getValues("k"));
    }

    @Test
    public void testKeysAbsentFromOtherAreUntouched() {
        Metadata src = new Metadata();
        src.add("inOther", "x");

        Metadata dest = new Metadata();
        dest.add("onlyInDest", "keepme");

        dest.putAll(src);

        assertEquals("keepme", dest.get("onlyInDest"));
        assertEquals("x", dest.get("inOther"));
    }

    @Test
    public void testSelfPutAllIsNoOp() {
        Metadata metadata = new Metadata();
        metadata.add("k", "v1");
        metadata.add("k", "v2");

        metadata.putAll(metadata);

        assertArrayEquals(new String[] {"v1", "v2"}, metadata.getValues("k"));
    }

    @Test
    public void testNullOtherThrowsNPE() {
        Metadata metadata = new Metadata();
        assertThrows(NullPointerException.class, () -> metadata.putAll(null));
    }
}
