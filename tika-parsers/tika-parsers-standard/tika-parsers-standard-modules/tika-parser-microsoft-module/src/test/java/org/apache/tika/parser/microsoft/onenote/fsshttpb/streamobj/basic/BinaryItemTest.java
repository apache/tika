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
package org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.basic;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import org.apache.tika.exception.TikaException;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.util.ByteUtil;

public class BinaryItemTest {

    /**
     * Deserializing keeps only a view of the parsed buffer, so the accessors must still
     * return exactly the item's bytes, surrounded by unrelated data, and hand out arrays
     * that cannot reach back into that buffer.
     */
    @Test
    public void testDeserializedContentIsExactAndDetached() throws Exception {
        byte[] payload = {10, 20, 30, 40, 50};
        byte[] item = serialize(new BinaryItem(payload.clone()));
        byte[] buffer = new byte[item.length + 6];
        Arrays.fill(buffer, (byte) 99);
        System.arraycopy(item, 0, buffer, 3, item.length);

        AtomicInteger index = new AtomicInteger(3);
        BinaryItem parsed = BasicObject.parse(buffer, index, BinaryItem.class);
        assertEquals(3 + item.length, index.get());
        assertEquals(payload.length, parsed.getContentLength());

        byte[] copy = parsed.copyContent();
        assertArrayEquals(payload, copy);
        copy[0] = 0;
        assertArrayEquals(payload, parsed.copyContent());

        byte[] content = parsed.getContent();
        assertArrayEquals(payload, content);
        assertSame(content, parsed.getContent());
        assertNotSame(buffer, content);
        content[1] = 0;
        assertEquals(20, buffer[3 + item.length - payload.length + 1]);
    }

    /**
     * Replacing the content of a deserialized item must drop the view of the parsed buffer,
     * keep {@code length} in step, and serialize the new bytes; a deserialized item must also
     * reserialize to exactly the bytes it was parsed from.
     */
    @Test
    public void testSetContentAfterDeserializeAndRoundTrip() throws Exception {
        byte[] item = serialize(new BinaryItem(new byte[]{1, 2, 3, 4}));
        byte[] buffer = new byte[item.length + 2];
        System.arraycopy(item, 0, buffer, 1, item.length);
        BinaryItem parsed = BasicObject.parse(buffer, new AtomicInteger(1), BinaryItem.class);
        assertArrayEquals(item, serialize(parsed));

        byte[] replacement = {7, 8};
        parsed.setContent(replacement);
        assertSame(replacement, parsed.getContent());
        assertEquals(2, parsed.getContentLength());
        assertEquals(2, parsed.length.getDecodedValue());

        byte[] reserialized = serialize(parsed);
        BinaryItem reparsed =
                BasicObject.parse(reserialized, new AtomicInteger(0), BinaryItem.class);
        assertArrayEquals(replacement, reparsed.getContent());
    }

    @Test
    public void testLengthPastBufferEndFails() throws Exception {
        byte[] item = serialize(new BinaryItem(new byte[]{1, 2, 3, 4}));
        byte[] truncated = Arrays.copyOf(item, item.length - 1);
        assertThrows(TikaException.class,
                () -> BasicObject.parse(truncated, new AtomicInteger(0), BinaryItem.class));
    }

    /**
     * A 64-bit compact length of 2^63 or more decodes as a negative long; it must fail like any
     * other impossible length rather than produce an empty item.
     */
    @Test
    public void testNegativeLengthFails() {
        byte[] item = {(byte) 0x80, 0, 0, 0, 0, 0, 0, 0, (byte) 0x80};
        assertThrows(TikaException.class,
                () -> BasicObject.parse(item, new AtomicInteger(0), BinaryItem.class));
    }

    @Test
    public void testNullContent() throws Exception {
        BinaryItem item = new BinaryItem(new byte[]{1});
        item.setContent(null);
        assertNull(item.getContent());
        assertNull(item.copyContent());
        assertEquals(0, item.getContentLength());
        assertEquals(0, item.length.getDecodedValue());

        BinaryItem constructed = new BinaryItem((byte[]) null);
        assertNull(constructed.getContent());
        assertEquals(0, constructed.length.getDecodedValue());
        assertArrayEquals(serialize(new BinaryItem()), serialize(constructed));
    }

    private static byte[] serialize(BinaryItem item) throws Exception {
        return ByteUtil.toByteArray(item.serializeToByteList());
    }
}
