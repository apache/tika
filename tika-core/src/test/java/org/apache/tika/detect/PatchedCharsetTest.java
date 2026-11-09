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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Collections;
import java.util.HexFormat;
import java.util.Random;

import org.junit.jupiter.api.Test;

import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;

public class PatchedCharsetTest {

    private static final Charset HKSCS = Charset.forName("Big5-HKSCS");
    private static final Charset BIG5 = CharsetSupersets.decodeAs(Charset.forName("Big5"));
    private static final Charset EUCJP = CharsetSupersets.decodeAs(Charset.forName("EUC-JP"));

    private static String dec(Charset cs, int... bytes) {
        byte[] b = new byte[bytes.length];
        for (int i = 0; i < b.length; i++) {
            b[i] = (byte) bytes[i];
        }
        return new String(b, cs);
    }

    @Test
    public void tablesLoad() {
        assertFalse(CharsetSupersets.patched().isEmpty());
        assertTrue(BIG5 instanceof PatchedCharset);
        assertEquals("x-Big5-HKSCS-WHATWG", BIG5.name());
        assertEquals(144, ((PatchedCharset) BIG5).patchedUnits().length);
        assertEquals("x-eucJP-Open-WHATWG", EUCJP.name());
        assertEquals(9, ((PatchedCharset) EUCJP).patchedUnits().length);
    }

    /** The table's unit model is the base decoder's: what makes unit-boundary matching sound. */
    @Test
    public void unitLengthsMatchTheBase() {
        for (Charset c : CharsetSupersets.patched()) {
            PatchedCharset cs = (PatchedCharset) c;
            int[] len = WhatwgPatchGenerator.unitLengths(cs.base());
            assertNotNull(len, cs.name());
            for (int b = 0; b < 256; b++) {
                assertEquals(len[b], cs.unitLength(b), cs.name() + " " + Integer.toHexString(b));
            }
        }
    }

    /**
     * Every one- and two-byte input, followed by ASCII: the base decodes it unit by unit, and the
     * patched charset differs only on patched units.
     */
    @Test
    public void everyShortInputIsPatchOrBase() {
        for (Charset c : CharsetSupersets.patched()) {
            PatchedCharset cs = (PatchedCharset) c;
            for (int b1 = 0; b1 <= 0xFF; b1++) {
                byte[] one = {(byte) b1, 'A'};
                assertEquals(new String(one, cs.base()), unitwise(cs, one, false));
                assertEquals(unitwise(cs, one, true), new String(one, cs));
                for (int b2 = 0; b2 <= 0xFF; b2++) {
                    byte[] two = {(byte) b1, (byte) b2, 'A'};
                    String hex = HexFormat.of().formatHex(two);
                    assertEquals(new String(two, cs.base()), unitwise(cs, two, false), hex);
                    assertEquals(unitwise(cs, two, true), new String(two, cs), hex);
                    if (cs.unitLength(b1) == 3) {
                        for (int b3 = 0; b3 <= 0xFF; b3++) {
                            byte[] three = {(byte) b1, (byte) b2, (byte) b3, 'A'};
                            assertEquals(new String(three, cs.base()), unitwise(cs, three, true),
                                    HexFormat.of().formatHex(three));
                            assertEquals(unitwise(cs, three, true), new String(three, cs));
                        }
                    }
                }
            }
        }
    }

    @Test
    public void everyPatchChangesTheDecode() {
        for (Charset c : CharsetSupersets.patched()) {
            PatchedCharset cs = (PatchedCharset) c;
            for (int unit : cs.patchedUnits()) {
                byte[] b = unit > 0xFF ? new byte[]{(byte) (unit >>> 8), (byte) unit}
                        : new byte[]{(byte) unit};
                assertFalse(new String(b, cs).equals(new String(b, cs.base())),
                        cs.name() + " " + Integer.toHexString(unit));
            }
        }
    }

    /** Values from the WHATWG index (pointer = (lead - 0x81) * 157 + trail offset), by hand. */
    @Test
    public void big5SpotChecksAgainstWhatwgIndex() {
        assertEquals("€", dec(BIG5, 0xA3, 0xE1)); // pointer 5465
        assertEquals("‧", dec(BIG5, 0xA1, 0x45)); // 5029
        assertEquals("～", dec(BIG5, 0xA1, 0xE3)); // 5153
        assertEquals("／", dec(BIG5, 0xA1, 0xFE)); // 5180
        assertEquals("廴", dec(BIG5, 0xC6, 0xCF)); // 10942
        assertEquals("箸", dec(BIG5, 0x8E, 0x69)); // 2082
        assertEquals("Ê̄", dec(BIG5, 0x88, 0x62)); // 1133, two code points
        assertEquals("ê̌", dec(BIG5, 0x88, 0xA5)); // 1166
        assertEquals("中", dec(BIG5, 0xA4, 0xA4)); // unpatched
        assertNull(((PatchedCharset) BIG5).patch(0xA4, 0xA4));
    }

    /** EUC-JP gets the forms windows-31j gives the same JIS X 0208 characters in Shift_JIS. */
    @Test
    public void eucJpMatchesWindows31j() {
        Charset sjis = Charset.forName("windows-31j");
        assertEquals("～", dec(EUCJP, 0xA1, 0xC1));
        assertEquals(dec(sjis, 0x81, 0x60), dec(EUCJP, 0xA1, 0xC1)); // wave dash
        assertEquals(dec(sjis, 0x81, 0x7C), dec(EUCJP, 0xA1, 0xDD)); // minus
        assertEquals(dec(sjis, 0x81, 0x91), dec(EUCJP, 0xA1, 0xF1)); // cent
        assertEquals(dec(sjis, 0x81, 0xCA), dec(EUCJP, 0xA2, 0xCC)); // not sign
        assertEquals("日本", dec(EUCJP, 0xC6, 0xFC, 0xCB, 0xDC));
        // JIS X 0212 (three bytes) is the base's, patched pair or not after it
        assertEquals(dec(Charset.forName("x-eucJP-Open"), 0x8F, 0xB0, 0xA1) + "～",
                dec(EUCJP, 0x8F, 0xB0, 0xA1, 0xA1, 0xC1));
        assertEquals("\u2170", dec(EUCJP, 0x8F, 0xF3, 0xF3));
        assertEquals("\uFFFD", dec(EUCJP, 0x8F, 0xA1, 0xC1));
    }

    @Test
    public void big5LoneLeadAtEndIsBaseMalformed() {
        assertEquals("a�", dec(HKSCS, 'a', 0xA3));
        assertEquals("a�", dec(BIG5, 'a', 0xA3));
        assertEquals("€�", dec(BIG5, 0xA3, 0xE1, 0xA3));
    }

    /** Random mixes of patched units, ASCII and arbitrary bytes, read in random chunks. */
    @Test
    public void streamedDecodeMatchesUnitwiseReference() throws IOException {
        long seed = new Random().nextLong();
        Random r = new Random(seed);
        for (Charset c : CharsetSupersets.patched()) {
            PatchedCharset cs = (PatchedCharset) c;
            int[] units = cs.patchedUnits();
            for (int iter = 0; iter < 300; iter++) {
                byte[] b = new byte[1 + r.nextInt(20000)];
                for (int i = 0; i < b.length; i++) {
                    int k = r.nextInt(10);
                    int u = units[r.nextInt(units.length)];
                    if (k < 3 && u > 0xFF && i + 1 < b.length) {
                        b[i++] = (byte) (u >>> 8);
                        b[i] = (byte) u;
                    } else if (k < 3) {
                        b[i] = (byte) u;
                    } else if (k < 6) {
                        b[i] = (byte) r.nextInt(0x80);
                    } else {
                        b[i] = (byte) r.nextInt(0x100);
                    }
                }
                String expected = unitwise(cs, b, true);
                assertEquals(expected, new String(b, cs), cs.name() + " seed " + seed);
                assertEquals(expected, read(new ChunkedStream(b, r), cs, 1 + r.nextInt(64)),
                        cs.name() + " seed " + seed);
            }
        }
    }

    /** Lead byte of a patched pair as the last byte of every read and every reader buffer. */
    @Test
    public void big5PatchedPairSplitAcrossBuffers() throws IOException {
        for (int prefix = 0; prefix < 40; prefix++) {
            byte[] b = new byte[prefix + 4];
            Arrays.fill(b, 0, prefix, (byte) 'x');
            b[prefix] = (byte) 0xA3;
            b[prefix + 1] = (byte) 0xE1;
            b[prefix + 2] = (byte) 0xA1;
            b[prefix + 3] = (byte) 0x45;
            String expected = "x".repeat(prefix) + "€‧";
            for (int chunk = 1; chunk <= 8; chunk++) {
                assertEquals(expected, read(new FixedChunkStream(b, chunk), BIG5, chunk));
            }
        }
    }

    @Test
    public void encodesAsTheBase() {
        String s = "中文 abc";
        assertArrayEquals(s.getBytes(HKSCS), s.getBytes(BIG5));
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
            assertEquals("e€", readAll(reader));
        }
        assertEquals("x-Big5-HKSCS-WHATWG", metadata.get(TikaCoreProperties.DECODED_CHARSET));
    }

    /** Decodes unit by unit with the base, applying the patch table if {@code patched}. */
    private static String unitwise(PatchedCharset cs, byte[] b, boolean patched) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < b.length; ) {
            int n = Math.min(cs.unitLength(b[i]), b.length - i);
            String patch = !patched || n != cs.unitLength(b[i]) ? null
                    : n == 1 ? cs.patch(b[i]) : n == 2 ? cs.patch(b[i], b[i + 1]) : null;
            sb.append(patch != null ? patch : new String(b, i, n, cs.base()));
            i += n;
        }
        return sb.toString();
    }

    private static String read(InputStream in, Charset cs, int bufSize) throws IOException {
        try (Reader reader = new InputStreamReader(in, cs)) {
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
