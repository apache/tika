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
package org.apache.tika.ml.junkdetect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;

import org.apache.tika.detect.AutoDetectReader;
import org.apache.tika.io.TikaInputStream;

/** The reader drops a BOM whether or not the detectors honored it. */
public class RejectedBomReaderTest {

    private static final String TEXT = "Café au lait, crème brûlée et "
            + "gâteau à la française. ";

    private static String read(byte[] body, Charset expected) throws Exception {
        byte[] bytes = new byte[body.length + 3];
        bytes[0] = (byte) 0xEF;
        bytes[1] = (byte) 0xBB;
        bytes[2] = (byte) 0xBF;
        System.arraycopy(body, 0, bytes, 3, body.length);
        try (AutoDetectReader reader = new AutoDetectReader(TikaInputStream.get(bytes))) {
            if (expected != null) {
                assertEquals(expected, reader.getCharset());
            } else {
                assertNotEquals(StandardCharsets.UTF_8, reader.getCharset());
            }
            return IOUtils.toString(reader);
        }
    }

    @Test
    public void rejectedUtf8BomIsDropped() throws Exception {
        String text = read(TEXT.repeat(5).getBytes(StandardCharsets.ISO_8859_1), null);
        assertTrue(text.startsWith("Caf"), text);
    }

    @Test
    public void honoredUtf8BomIsDropped() throws Exception {
        String text = read(TEXT.repeat(5).getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
        assertEquals(TEXT.repeat(5), text);
    }
}
