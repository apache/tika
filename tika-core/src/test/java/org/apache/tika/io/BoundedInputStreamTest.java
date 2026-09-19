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
package org.apache.tika.io;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

public class BoundedInputStreamTest {

    /** A skip that lies (FileInputStream seeks past EOF) must not move the bound's position. */
    @Test
    public void skipReadsAndStaysWithinTheBound() throws Exception {
        byte[] data = "0123456789".getBytes(StandardCharsets.US_ASCII);
        InputStream lying = new ByteArrayInputStream(data) {
            @Override
            public long skip(long n) {
                return n;   // claims to skip whatever is asked, like a FileInputStream past EOF
            }
        };
        BoundedInputStream bis = new BoundedInputStream(5, lying);
        assertEquals(3, bis.skip(3));
        assertEquals('3', bis.read());
        assertEquals(1, bis.skip(100), "bounded: only one byte remains under the limit");
        assertEquals(-1, bis.read());

        bis = new BoundedInputStream(8, new ByteArrayInputStream(data));
        bis.read();
        bis.mark(10);
        bis.read();
        bis.read();
        bis.reset();
        assertEquals('1', bis.read(), "reset returns to the marked position");
        assertEquals(6, bis.skip(100), "and the bound counts from the mark, not from zero");
    }

    @Test
    public void readNBytesHonorsTheBound() throws Exception {
        byte[] data = "0123456789".getBytes(StandardCharsets.US_ASCII);
        BoundedInputStream bis = new BoundedInputStream(5, new ByteArrayInputStream(data));
        assertEquals("01234", new String(bis.readNBytes(100), StandardCharsets.US_ASCII));
        assertEquals(-1, bis.read());

        bis = new BoundedInputStream(5, new ByteArrayInputStream(data));
        byte[] buf = new byte[10];
        assertEquals(5, bis.readNBytes(buf, 0, 10));
        assertEquals(0, bis.readNBytes(buf, 5, 5));
        assertEquals("01234", new String(buf, 0, 5, StandardCharsets.US_ASCII));
    }
}
