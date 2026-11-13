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
package org.apache.tika.parser.microsoft.onenote.fsshttpb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import org.apache.tika.parser.microsoft.onenote.fsshttpb.property.PrtArrayOfPropertyValues;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.property.PrtFourBytesOfLengthFollowedByData;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.PropertySet;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.StorageManifestSchemaGUID;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.basic.PropertyType;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.space.ObjectSpaceObjectStreamOfContextIDs;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.space.ObjectSpaceObjectStreamOfOIDs;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.space.ObjectSpaceObjectStreamOfOSIDs;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.util.BitConverter;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.util.GuidUtil;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.util.LittleEndianBitConverter;

/**
 * File-derived counts and nesting in the fsshttpb property deserializers must be bounded -
 * a malformed count would otherwise allocate up to gigabytes and a deeply nested property
 * set would overflow the stack, both Errors that escape the parser's Exception fallback.
 */
public class PropertyDeserializationBoundsTest {

    @Test
    public void testFourBytesOfLengthRejectsLengthBeyondRemainingData() {
        byte[] bytes = concat(int32(1000), new byte[2]);
        IOException e = assertThrows(IOException.class,
                () -> new PrtFourBytesOfLengthFollowedByData()
                        .doDeserializeFromByteArray(bytes, 0));
        assertTrue(e.getMessage().contains("exceeds remaining data"), e.getMessage());
    }

    @Test
    public void testFourBytesOfLengthRejectsNegativeLength() {
        byte[] bytes = concat(int32(0xF0000000), new byte[8]);
        assertThrows(IOException.class,
                () -> new PrtFourBytesOfLengthFollowedByData()
                        .doDeserializeFromByteArray(bytes, 0));
    }

    @Test
    public void testPropertySetCountIsUnsignedAndBounded() {
        // 0xFFFF properties declared, none present: a signed read made this a negative size
        byte[] bytes = new byte[]{(byte) 0xFF, (byte) 0xFF, 0, 0};
        IOException e = assertThrows(IOException.class,
                () -> new PropertySet().doDeserializeFromByteArray(bytes, 0));
        assertTrue(e.getMessage().contains("exceeds"), e.getMessage());
    }

    @Test
    public void testUndefinedPropertyTypeFailsCleanly() {
        byte[] bytes = concat(new byte[]{1, 0}, propertyId(0x1E));
        IOException e = assertThrows(IOException.class,
                () -> new PropertySet().doDeserializeFromByteArray(bytes, 0));
        assertTrue(e.getMessage().contains("Undefined property type"), e.getMessage());
    }

    @Test
    public void testBitConverterRejectsReadsPastTheEnd() throws IOException {
        byte[] bytes = new byte[]{1, 2, 3, 4, 5, 6};
        assertEquals(0x06050403, BitConverter.toInt32(bytes, 2));
        assertThrows(IOException.class, () -> BitConverter.toInt32(bytes, 4));
        assertThrows(IOException.class, () -> BitConverter.toInt64(bytes, 0));
        assertThrows(IOException.class, () -> BitConverter.toInt16(bytes, -1));
    }

    @Test
    public void testUnsignedReads() throws IOException {
        byte[] bytes = {(byte) 0xFE, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF};
        assertEquals(0xFFFFFFFEL, BitConverter.toUInt32(bytes, 0));
        // a two-byte read at the last two bytes
        assertEquals((short) 0xFFFF, LittleEndianBitConverter.toInt16(bytes, 2));
    }

    @Test
    public void testStorageManifestSchemaGuidIsReadAtItsIndex() throws Exception {
        byte[] bytes = new byte[20];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) i;
        }
        StorageManifestSchemaGUID schema = new StorageManifestSchemaGUID();
        Method deserialize = StorageManifestSchemaGUID.class.getDeclaredMethod(
                "deserializeItemsFromByteArray", byte[].class, AtomicInteger.class, int.class);
        deserialize.setAccessible(true);
        deserialize.invoke(schema, bytes, new AtomicInteger(4), 16);
        assertEquals(GuidUtil.fromMicrosoftBytes(bytes, 4), schema.guid);
    }

    @Test
    public void testFourBytesOfLengthAcceptsExactFit() throws IOException {
        byte[] bytes = concat(int32(3), new byte[]{1, 2, 3});
        PrtFourBytesOfLengthFollowedByData property = new PrtFourBytesOfLengthFollowedByData();
        assertEquals(7, property.doDeserializeFromByteArray(bytes, 0));
        assertEquals(3, property.data.length);
    }

    @Test
    public void testArrayOfPropertyValuesRejectsCountBeyondRemainingData() throws IOException {
        byte[] bytes = concat(int32(Integer.MAX_VALUE),
                propertyId(PropertyType.PropertySet.getIntVal()));
        IOException e = assertThrows(IOException.class,
                () -> new PrtArrayOfPropertyValues().doDeserializeFromByteArray(bytes, 0));
        assertTrue(e.getMessage().contains("exceeds remaining data"), e.getMessage());
    }

    @Test
    public void testArrayOfPropertyValuesAcceptsSmallCount() throws IOException {
        // one element: an empty PropertySet (int16 count of 0)
        byte[] bytes = concat(int32(1), propertyId(PropertyType.PropertySet.getIntVal()),
                new byte[]{0, 0});
        PrtArrayOfPropertyValues array = new PrtArrayOfPropertyValues();
        array.doDeserializeFromByteArray(bytes, 0);
        assertEquals(1, array.data.length);
    }

    @Test
    public void testStreamOfIdsRejectCountBeyondRemainingData() throws IOException {
        byte[] bytes = streamHeader(0xFFFFFF);
        assertThrows(IOException.class,
                () -> new ObjectSpaceObjectStreamOfOIDs().doDeserializeFromByteArray(bytes, 0));
        assertThrows(IOException.class,
                () -> new ObjectSpaceObjectStreamOfOSIDs().doDeserializeFromByteArray(bytes, 0));
        assertThrows(IOException.class,
                () -> new ObjectSpaceObjectStreamOfContextIDs()
                        .doDeserializeFromByteArray(bytes, 0));
    }

    @Test
    public void testStreamOfOidsAcceptsSmallCount() throws IOException {
        // one CompactID, 4 bytes
        byte[] bytes = concat(streamHeader(1), new byte[4]);
        ObjectSpaceObjectStreamOfOIDs stream = new ObjectSpaceObjectStreamOfOIDs();
        stream.doDeserializeFromByteArray(bytes, 0);
        assertEquals(1, stream.body.length);
    }

    @Test
    public void testPropertySetNestingIsDepthCapped() throws IOException {
        byte[] bytes = nestedPropertySet(PropertySet.MAX_PROPERTY_NESTING + 50);
        IOException e = assertThrows(IOException.class,
                () -> new PropertySet().doDeserializeFromByteArray(bytes, 0));
        assertTrue(e.getMessage().contains("nesting exceeds"), e.getMessage());
    }

    @Test
    public void testPropertySetShallowNestingParses() throws IOException {
        PropertySet propertySet = new PropertySet();
        propertySet.doDeserializeFromByteArray(nestedPropertySet(3), 0);
        assertEquals(1, propertySet.rgData.size());
    }

    @Test
    public void testNestingThroughArrayOfPropertyValuesIsDepthCapped() throws IOException {
        // alternate PropertySet -> ArrayOfPropertyValues -> PropertySet -> ...
        byte[] bytes = new byte[]{0, 0};
        for (int i = 0; i < PropertySet.MAX_PROPERTY_NESTING + 50; i++) {
            bytes = concat(new byte[]{1, 0},
                    propertyId(PropertyType.ArrayOfPropertyValues.getIntVal()), int32(1),
                    propertyId(PropertyType.PropertySet.getIntVal()), bytes);
        }
        byte[] finalBytes = bytes;
        IOException e = assertThrows(IOException.class,
                () -> new PropertySet().doDeserializeFromByteArray(finalBytes, 0));
        assertTrue(e.getMessage().contains("nesting exceeds"), e.getMessage());
    }

    private static byte[] nestedPropertySet(int depth) throws IOException {
        // innermost: empty PropertySet; each wrapper declares one PropertySet-typed property
        byte[] bytes = new byte[]{0, 0};
        for (int i = 0; i < depth; i++) {
            bytes = concat(new byte[]{1, 0},
                    propertyId(PropertyType.PropertySet.getIntVal()), bytes);
        }
        return bytes;
    }

    // PropertyID: id (26 bits), type (5 bits), boolValue (1 bit), little-endian
    private static byte[] propertyId(int type) {
        return int32(1 | type << 26);
    }

    // ObjectSpaceObjectStreamHeader: count is the low 24 bits; the flag bits stay zero
    private static byte[] streamHeader(int count) {
        return int32(count);
    }

    private static byte[] int32(int value) {
        return new byte[]{(byte) value, (byte) (value >> 8), (byte) (value >> 16),
                (byte) (value >> 24)};
    }

    private static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        for (byte[] part : parts) {
            bos.writeBytes(part);
        }
        return bos.toByteArray();
    }
}
