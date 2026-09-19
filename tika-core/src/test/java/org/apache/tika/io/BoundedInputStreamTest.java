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
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

public class BoundedInputStreamTest {

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
