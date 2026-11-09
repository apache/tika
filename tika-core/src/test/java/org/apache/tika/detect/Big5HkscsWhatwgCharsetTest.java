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
package org.apache.tika.detect;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.ByteArrayInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.Random;

import org.junit.jupiter.api.Test;

import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;

public class Big5HkscsWhatwgCharsetTest {

    private static final Charset HKSCS = Charset.forName("Big5-HKSCS");
    private static final Charset PATCHED = CharsetSupersets.decodeAs(Charset.forName("Big5"));

    private static String dec(Charset cs, int... bytes) {
        byte[] b = new byte[bytes.length];
        for (int i = 0; i < b.length; i++) {
            b[i] = (byte) bytes[i];
        }
        return new String(b, cs);
    }

    /** Values from the WHATWG index (pointer = (lead - 0x81) * 157 + trail offset), by hand. */
    @Test
    public void spotChecksAgainstWhatwgIndex() {
        assertEquals("\u20AC", dec(PATCHED, 0xA3, 0xE1)); // pointer 5465
        assertEquals("\u2027", dec(PATCHED, 0xA1, 0x45)); // 5029
        assertEquals("\uFF5E", dec(PATCHED, 0xA1, 0xE3)); // 5153
        assertEquals("\uFF0F", dec(PATCHED, 0xA1, 0xFE)); // 5180
        assertEquals("\u5EF4", dec(PATCHED, 0xC6, 0xCF)); // 10942
        assertEquals("\u7BB8", dec(PATCHED, 0x8E, 0x69)); // 2082
        assertEquals("\u00CA\u0304", dec(PATCHED, 0x88, 0x62)); // 1133, two code points
        assertEquals("\u00EA\u030C", dec(PATCHED, 0x88, 0xA5)); // 1166
        assertEquals("\u4E2D", dec(PATCHED, 0xA4, 0xA4)); // unpatched
    }

    /**
     * Every two-byte input from a high byte, followed by ASCII: patched pairs give the table
     * value, everything else (including invalid trails and malformed leads) is Big5-HKSCS's.
     */
    @Test
    public void everyPairIsPatchOrBigFiveHkscs() {
        int patched = 0;
        for (int b1 = 0x80; b1 <= 0xFF; b1++) {
            for (int b2 = 0; b2 <= 0xFF; b2++) {
                String patch = Big5HkscsWhatwgCharset.patch(b1, b2);
                String expected = patch != null ? patch + "A" : dec(HKSCS, b1, b2, 'A');
                assertEquals(expected, dec(PATCHED, b1, b2, 'A'),
                        String.format(java.util.Locale.ROOT, "%02X%02X", b1, b2));
                if (patch != null) {
                    patched++;
                    boolean validTrail = (b2 >= 0x40 && b2 <= 0x7E) || (b2 >= 0xA1 && b2 <= 0xFE);
                    assertEquals(true, b1 >= 0x81 && b1 <= 0xFE && validTrail);
                }
            }
        }
        assertEquals(144, patched);
        assertEquals(144, Big5HkscsWhatwgCharset.patchedPairs().length);
    }

    @Test
    public void everyPatchChangesTheDecode() {
        for (int pair : Big5HkscsWhatwgCharset.patchedPairs()) {
            int lead = pair >>> 8;
            int trail = pair & 0xFF;
            String patch = Big5HkscsWhatwgCharset.patch(lead, trail);
            assertNotNull(patch);
            assertEquals(false, patch.equals(dec(HKSCS, lead, trail)), Integer.toHexString(pair));
        }
        assertNull(Big5HkscsWhatwgCharset.patch(0xA4, 0xA4));
    }

    @Test
    public void loneLeadAtEndIsBigFiveHkscsMalformed() {
        assertEquals("a\uFFFD", dec(HKSCS, 'a', 0xA3));
        assertEquals("a\uFFFD", dec(PATCHED, 'a', 0xA3));
        assertEquals("\u20AC\uFFFD", dec(PATCHED, 0xA3, 0xE1, 0xA3));
    }

    /** Random mixes of patched pairs, mapped pairs, ASCII and junk, read in random chunks. */
    @Test
    public void streamedDecodeMatchesTokenwiseReference() throws IOException {
        int[] pairs = Big5HkscsWhatwgCharset.patchedPairs();
        long seed = new Random().nextLong();
        Random r = new Random(seed);
        for (int iter = 0; iter < 300; iter++) {
            byte[] b = new byte[1 + r.nextInt(20000)];
            for (int i = 0; i < b.length; i++) {
                int k = r.nextInt(10);
                if (k < 3 && i + 1 < b.length) {
                    int p = pairs[r.nextInt(pairs.length)];
                    b[i++] = (byte) (p >>> 8);
                    b[i] = (byte) p;
                } else if (k < 6) {
                    b[i] = (byte) r.nextInt(0x80);
                } else {
                    b[i] = (byte) r.nextInt(0x100);
                }
            }
            String expected = tokenwise(b);
            assertEquals(expected, new String(b, PATCHED), "seed " + seed);
            assertEquals(expected, read(new ChunkedStream(b, r), 1 + r.nextInt(64)), "seed " + seed);
        }
    }

    /** Lead byte of a patched pair as the last byte of every read and every reader buffer. */
    @Test
    public void patchedPairSplitAcrossBuffers() throws IOException {
        for (int prefix = 0; prefix < 40; prefix++) {
            byte[] b = new byte[prefix + 4];
            java.util.Arrays.fill(b, 0, prefix, (byte) 'x');
            b[prefix] = (byte) 0xA3;
            b[prefix + 1] = (byte) 0xE1;
            b[prefix + 2] = (byte) 0xA1;
            b[prefix + 3] = (byte) 0x45;
            String expected = "x".repeat(prefix) + "\u20AC\u2027";
            for (int chunk = 1; chunk <= 8; chunk++) {
                assertEquals(expected, read(new FixedChunkStream(b, chunk), chunk));
            }
        }
    }

    @Test
    public void encodesAsBigFiveHkscs() {
        String s = "\u4E2D\u6587 abc";
        assertArrayEquals(s.getBytes(HKSCS), s.getBytes(PATCHED));
    }

    @Test
    public void autoDetectReaderDecodesPatchedReportsJdkCharset() throws Exception {
        byte[] b = {'e', (byte) 0xA3, (byte) 0xE1};
        EncodingDetector big5 = (tis, md, ctx) ->
                Collections.singletonList(new EncodingResult(Charset.forName("Big5"), 1.0f));
        Metadata metadata = new Metadata();
        try (AutoDetectReader reader =
                     new AutoDetectReader(TikaInputStream.get(b), metadata, big5)) {
            assertEquals("Big5-HKSCS", reader.getCharset().name());
            assertEquals("e\u20AC", readAll(reader));
        }
        assertEquals("x-Big5-HKSCS-WHATWG", metadata.get(TikaCoreProperties.DECODED_CHARSET));
    }

    private static String tokenwise(byte[] b) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < b.length; ) {
            int lead = b[i] & 0xFF;
            if (lead < 0x80 || i + 1 == b.length) {
                sb.append(new String(b, i, 1, HKSCS));
                i++;
                continue;
            }
            String patch = Big5HkscsWhatwgCharset.patch(lead, b[i + 1] & 0xFF);
            sb.append(patch != null ? patch : new String(b, i, 2, HKSCS));
            i += 2;
        }
        return sb.toString();
    }

    private static String read(InputStream in, int bufSize) throws IOException {
        try (Reader reader = new InputStreamReader(in, PATCHED)) {
            StringBuilder sb = new StringBuilder();
            char[] buf = new char[bufSize];
            int n;
            while ((n = reader.read(buf)) != -1) {
                sb.append(buf, 0, n);
            }
            return sb.toString();
        }
    }

    private static String readAll(Reader reader) throws IOException {
        StringBuilder sb = new StringBuilder();
        int c;
        while ((c = reader.read()) != -1) {
            sb.append((char) c);
        }
        return sb.toString();
    }

    private static final class ChunkedStream extends FilterInputStream {
        private final Random r;

        ChunkedStream(byte[] b, Random r) {
            super(new ByteArrayInputStream(b));
            this.r = r;
        }

        @Override
        public int read(byte[] buf, int off, int len) throws IOException {
            return super.read(buf, off, Math.min(len, 1 + r.nextInt(7)));
        }
    }

    private static final class FixedChunkStream extends FilterInputStream {
        private final int chunk;

        FixedChunkStream(byte[] b, int chunk) {
            super(new ByteArrayInputStream(b));
            this.chunk = chunk;
        }

        @Override
        public int read(byte[] buf, int off, int len) throws IOException {
            return super.read(buf, off, Math.min(len, chunk));
        }
    }
}
