/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.tika.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.ByteArrayInputStream;

import org.junit.jupiter.api.Test;

public class EndianUtilsTest {
    @Test
    public void testReadUE7() throws Exception {
        byte[] data;

        data = new byte[]{0x08};
        assertEquals(8, EndianUtils.readUE7(new ByteArrayInputStream(data)));

        data = new byte[]{(byte) 0x84, 0x1e};
        assertEquals(542, EndianUtils.readUE7(new ByteArrayInputStream(data)));

        data = new byte[]{(byte) 0xac, (byte) 0xbe, 0x17};
        assertEquals(728855, EndianUtils.readUE7(new ByteArrayInputStream(data)));
    }

    @Test
    public void testReadUIntLE() throws Exception {
        byte[] data = new byte[]{(byte) 0x08, (byte) 0x00, (byte) 0x00, (byte) 0x00};
        assertEquals(8, EndianUtils.readUIntLE(new ByteArrayInputStream(data)));

        data = new byte[]{(byte) 0xF0, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF};
        assertEquals(4294967280L, EndianUtils.readUIntLE(new ByteArrayInputStream(data)));

        data = new byte[]{(byte) 0xFF, (byte) 0xFF, (byte) 0xFF};
        try {
            EndianUtils.readUIntLE(new ByteArrayInputStream(data));
            fail("Should have thrown exception");
        } catch (EndianUtils.BufferUnderrunException e) {
            //swallow
        }
    }

    @Test
    public void testReadUIntBE() throws Exception {
        byte[] data = new byte[]{(byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x08};
        assertEquals(8, EndianUtils.readUIntBE(new ByteArrayInputStream(data)));

        data = new byte[]{(byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xF0};
        assertEquals(4294967280L, EndianUtils.readUIntBE(new ByteArrayInputStream(data)));

        data = new byte[]{(byte) 0xFF, (byte) 0xFF, (byte) 0xFF};
        try {
            EndianUtils.readUIntLE(new ByteArrayInputStream(data));
            fail("Should have thrown exception");
        } catch (EndianUtils.BufferUnderrunException e) {
            //swallow
        }
    }

    @Test
    public void testReadIntME() throws Exception {
        // Example from https://yamm.finance/wiki/Endianness.html#mwAiw 
        byte[] data = new byte[]{(byte) 0x0b, (byte) 0x0a, (byte) 0x0d, (byte) 0x0c};
        assertEquals(0x0a0b0c0d, EndianUtils.readIntME(new ByteArrayInputStream(data)));

        data = new byte[]{(byte) 0xFE, (byte) 0xFF, (byte) 0xFC, (byte) 0xFD};
        assertEquals(0xfffefdfc, EndianUtils.readIntME(new ByteArrayInputStream(data)));

        data = new byte[]{(byte) 0xFF, (byte) 0xFF, (byte) 0xFF};
        try {
            EndianUtils.readIntME(new ByteArrayInputStream(data));
            fail("Should have thrown exception");
        } catch (EndianUtils.BufferUnderrunException e) {
            //swallow
        }
    }
}
