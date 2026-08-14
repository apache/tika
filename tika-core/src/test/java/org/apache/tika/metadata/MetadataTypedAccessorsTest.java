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

import org.junit.jupiter.api.Test;

/** {@code set(Property, long)}'s INTEGER/REAL acceptance and the {@code getLong}/{@code
 * getBoolean}/{@code getDouble} typed accessors, null-on-mismatch like {@code getInt}/{@code
 * getDate}. */
public class MetadataTypedAccessorsTest {

    @Test
    public void testSetLongAcceptsIntegerProperty() {
        Metadata metadata = new Metadata();
        metadata.set(TIFF.IMAGE_WIDTH, 42L);
        assertEquals("42", metadata.get(TIFF.IMAGE_WIDTH));
        assertEquals(42L, metadata.getLong(TIFF.IMAGE_WIDTH).longValue());
    }

    @Test
    public void testSetLongAcceptsRealProperty() {
        Metadata metadata = new Metadata();
        metadata.set(XMPDM.TEMPO, 120L);
        assertEquals(120L, metadata.getLong(XMPDM.TEMPO).longValue());
    }

    @Test
    public void testSetLongRejectsNonNumericProperty() {
        Metadata metadata = new Metadata();
        assertThrows(PropertyTypeException.class, () -> metadata.set(XMPDM.ALBUM, 1L));
    }

    @Test
    public void testGetLongReturnsNullOnTypeMismatch() {
        Metadata metadata = new Metadata();
        metadata.set(XMPDM.ALBUM, "not a number");
        assertNull(metadata.getLong(XMPDM.ALBUM));
    }

    @Test
    public void testGetLongReturnsNullWhenUnset() {
        Metadata metadata = new Metadata();
        assertNull(metadata.getLong(TIFF.IMAGE_WIDTH));
    }

    @Test
    public void testGetLongReturnsNullOnUnparseableValue() {
        Metadata metadata = new Metadata();
        metadata.setTrusted(TIFF.IMAGE_WIDTH.getName(), "not-a-long");
        assertNull(metadata.getLong(TIFF.IMAGE_WIDTH));
    }

    @Test
    public void testGetBooleanRoundTrips() {
        Metadata metadata = new Metadata();
        metadata.set(TIFF.FLASH_FIRED, true);
        assertEquals(Boolean.TRUE, metadata.getBoolean(TIFF.FLASH_FIRED));
    }

    @Test
    public void testGetBooleanReturnsNullOnTypeMismatch() {
        Metadata metadata = new Metadata();
        metadata.set(XMPDM.ALBUM, "yes");
        assertNull(metadata.getBoolean(XMPDM.ALBUM));
    }

    @Test
    public void testGetBooleanReturnsNullWhenUnset() {
        Metadata metadata = new Metadata();
        assertNull(metadata.getBoolean(TIFF.FLASH_FIRED));
    }

    @Test
    public void testGetDoubleRoundTripsRealProperty() {
        Metadata metadata = new Metadata();
        metadata.set(XMPDM.DURATION, 3.5);
        assertEquals(3.5, metadata.getDouble(XMPDM.DURATION));
    }

    @Test
    public void testGetDoubleRoundTripsRationalProperty() {
        Metadata metadata = new Metadata();
        metadata.setTrusted(TIFF.EXPOSURE_TIME.getName(), "1.5");
        assertEquals(1.5, metadata.getDouble(TIFF.EXPOSURE_TIME));
    }

    @Test
    public void testGetDoubleReturnsNullOnTypeMismatch() {
        Metadata metadata = new Metadata();
        metadata.set(XMPDM.ALBUM, "not a double");
        assertNull(metadata.getDouble(XMPDM.ALBUM));
    }

    @Test
    public void testGetDoubleReturnsNullWhenUnset() {
        Metadata metadata = new Metadata();
        assertNull(metadata.getDouble(XMPDM.DURATION));
    }
}
