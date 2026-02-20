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
package org.apache.tika.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

public class ByteEncodingHintTest {

    @Test
    public void testNullReturnsLatin1() {
        ByteEncodingHint hint = ByteEncodingHint.detect(null);
        assertEquals(StandardCharsets.ISO_8859_1, hint.charset());
        assertFalse(hint.isUtf16());
    }

    @Test
    public void testUtf8FallsThrough() {
        // UTF-8 has no null bytes for ASCII content — falls through to Latin-1.
        ByteEncodingHint hint = ByteEncodingHint.detect("<p>hello</p>".getBytes(StandardCharsets.UTF_8));
        assertEquals(StandardCharsets.ISO_8859_1, hint.charset());
        assertFalse(hint.isUtf16());
    }

    @Test
    public void testLatin1FallsThrough() {
        byte[] bytes = "<p>caf\u00e9</p>".getBytes(StandardCharsets.ISO_8859_1);
        assertFalse(ByteEncodingHint.detect(bytes).isUtf16());
    }

    @Test
    public void testEbcdicFallsThrough() {
        // EBCDIC '<' = 0x4C — no null bytes — falls through.
        byte[] ebcdic = {0x4C, (byte)0x88, (byte)0x94, (byte)0x93, (byte)0x93, 0x6E};
        ByteEncodingHint hint = ByteEncodingHint.detect(ebcdic);
        assertEquals(StandardCharsets.ISO_8859_1, hint.charset());
        assertFalse(hint.isUtf16());
    }

    @Test
    public void testUtf16LeDetected() {
        ByteEncodingHint hint = ByteEncodingHint.detect("<p>hello</p>".getBytes(StandardCharsets.UTF_16LE));
        assertEquals(StandardCharsets.UTF_16LE, hint.charset());
        assertTrue(hint.isUtf16());
    }

    @Test
    public void testUtf16BeDetected() {
        ByteEncodingHint hint = ByteEncodingHint.detect("<p>hello</p>".getBytes(StandardCharsets.UTF_16BE));
        assertEquals(StandardCharsets.UTF_16BE, hint.charset());
        assertTrue(hint.isUtf16());
    }

    @Test
    public void testUtf16WithBomStillDetected() {
        // BOM is not relied upon but doesn't confuse the heuristic.
        byte[] body = "<p>hello</p>".getBytes(StandardCharsets.UTF_16LE);
        byte[] input = new byte[body.length + 2];
        input[0] = (byte) 0xFF;
        input[1] = (byte) 0xFE;
        System.arraycopy(body, 0, input, 2, body.length);
        assertTrue(ByteEncodingHint.detect(input).isUtf16());
    }

    @Test
    public void testUtf32LeNotMisidentified() {
        // Both columns high → not UTF-16.
        byte[] bytes = toUtf32Le("<p>hello</p>");
        ByteEncodingHint hint = ByteEncodingHint.detect(bytes);
        assertFalse(hint.isUtf16());
        assertEquals(StandardCharsets.ISO_8859_1, hint.charset());
    }

    @Test
    public void testUtf32BeNotMisidentified() {
        byte[] bytes = toUtf32Be("<p>hello</p>");
        ByteEncodingHint hint = ByteEncodingHint.detect(bytes);
        assertFalse(hint.isUtf16());
        assertEquals(StandardCharsets.ISO_8859_1, hint.charset());
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static byte[] toUtf32Le(String s) {
        byte[] out = new byte[s.length() * 4];
        for (int i = 0; i < s.length(); i++) {
            int cp = s.charAt(i);
            out[i * 4]     = (byte) (cp & 0xFF);
            out[i * 4 + 1] = (byte) ((cp >> 8) & 0xFF);
            out[i * 4 + 2] = 0;
            out[i * 4 + 3] = 0;
        }
        return out;
    }

    private static byte[] toUtf32Be(String s) {
        byte[] out = new byte[s.length() * 4];
        for (int i = 0; i < s.length(); i++) {
            int cp = s.charAt(i);
            out[i * 4]     = 0;
            out[i * 4 + 1] = 0;
            out[i * 4 + 2] = (byte) ((cp >> 8) & 0xFF);
            out[i * 4 + 3] = (byte) (cp & 0xFF);
        }
        return out;
    }
}
