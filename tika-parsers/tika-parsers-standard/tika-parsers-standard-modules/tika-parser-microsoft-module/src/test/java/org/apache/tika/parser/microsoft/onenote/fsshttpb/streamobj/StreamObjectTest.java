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
package org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

public class StreamObjectTest {

    @Test
    public void testWrongTypeIsRejectedBeforeItIsParsed() {
        // 16-bit compound header for a DataElementPackage (0x15): bits are header type (2),
        // compound (1), type (6), length (7)
        int header = 1 << 2 | 0x15 << 3;
        byte[] bytes = new byte[64];
        bytes[0] = (byte) header;
        bytes[1] = (byte) (header >> 8);
        AtomicInteger index = new AtomicInteger(0);
        StreamObjectParseErrorException e = assertThrows(StreamObjectParseErrorException.class,
                () -> StreamObject.getCurrent(bytes, index, CellManifestCurrentRevision.class));
        assertTrue(e.getMessage().contains("DataElementPackage"), e.getMessage());
        assertEquals(0, index.get());
    }
}
