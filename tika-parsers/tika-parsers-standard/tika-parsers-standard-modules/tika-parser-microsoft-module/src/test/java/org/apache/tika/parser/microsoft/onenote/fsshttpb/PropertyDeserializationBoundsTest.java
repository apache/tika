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
import java.util.List;

import org.junit.jupiter.api.Test;

import org.apache.tika.parser.microsoft.onenote.fsshttpb.property.PrtArrayOfPropertyValues;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.property.PrtFourBytesOfLengthFollowedByData;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.PropertySet;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.basic.PropertyID;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.basic.PropertyType;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.space.ObjectSpaceObjectStreamHeader;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.space.ObjectSpaceObjectStreamOfContextIDs;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.space.ObjectSpaceObjectStreamOfOIDs;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.space.ObjectSpaceObjectStreamOfOSIDs;

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

    private static byte[] propertyId(int type) throws IOException {
        PropertyID propertyID = new PropertyID();
        propertyID.id = 1;
        propertyID.type = type;
        return toArray(propertyID.serializeToByteList());
    }

    private static byte[] streamHeader(int count) throws IOException {
        ObjectSpaceObjectStreamHeader header = new ObjectSpaceObjectStreamHeader();
        header.count = count;
        return toArray(header.serializeToByteList());
    }

    private static byte[] int32(int value) {
        return new byte[]{(byte) value, (byte) (value >> 8), (byte) (value >> 16),
                (byte) (value >> 24)};
    }

    private static byte[] toArray(List<Byte> bytes) {
        byte[] result = new byte[bytes.size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = bytes.get(i);
        }
        return result;
    }

    private static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        for (byte[] part : parts) {
            bos.writeBytes(part);
        }
        return bos.toByteArray();
    }
}
