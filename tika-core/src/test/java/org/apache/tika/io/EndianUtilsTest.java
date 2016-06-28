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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;

import org.junit.Test;

public class EndianUtilsTest {
    @Test
    public void testReadUE7() throws Exception {
        byte[] data;
        
        data = new byte[] { 0x08 };
        assertEquals((long)8, EndianUtils.readUE7(new ByteArrayInputStream(data)));
        
        data = new byte[] { (byte)0x84, 0x1e };
        assertEquals((long)542, EndianUtils.readUE7(new ByteArrayInputStream(data)));
        
        data = new byte[] { (byte)0xac, (byte)0xbe, 0x17 };
        assertEquals((long)728855, EndianUtils.readUE7(new ByteArrayInputStream(data)));
    }

    @Test
    public void testReadUIntLE() throws Exception {
        byte[] data = new byte[] {(byte)0x08, (byte)0x00, (byte)0x00, (byte)0x00 };
        assertEquals((long) 8, EndianUtils.readUIntLE(new ByteArrayInputStream(data)));

        data = new byte[] {(byte)0xF0, (byte)0xFF, (byte)0xFF, (byte)0xFF };
        assertEquals(4294967280L, EndianUtils.readUIntLE(new ByteArrayInputStream(data)));

        data = new byte[] {(byte)0xFF, (byte)0xFF, (byte)0xFF  };
        try {
            EndianUtils.readUIntLE(new ByteArrayInputStream(data));
            fail("Should have thrown exception");
        } catch (EndianUtils.BufferUnderrunException e) {

        }
    }

    @Test
    public void testReadUIntBE() throws Exception {
        byte[] data = new byte[] {(byte)0x00, (byte)0x00, (byte)0x00, (byte)0x08 };
        assertEquals((long) 8, EndianUtils.readUIntBE(new ByteArrayInputStream(data)));

        data = new byte[] {(byte)0xFF, (byte)0xFF, (byte)0xFF, (byte)0xF0 };
        assertEquals(4294967280L, EndianUtils.readUIntBE(new ByteArrayInputStream(data)));

        data = new byte[] {(byte)0xFF, (byte)0xFF, (byte)0xFF  };
        try {
            EndianUtils.readUIntLE(new ByteArrayInputStream(data));
            fail("Should have thrown exception");
        } catch (EndianUtils.BufferUnderrunException e) {

        }
    }
}
