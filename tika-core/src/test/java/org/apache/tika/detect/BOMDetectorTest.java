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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.apache.commons.io.ByteOrderMark;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.BOMInputStream;
import org.apache.commons.io.input.UnsynchronizedByteArrayInputStream;
import org.apache.commons.io.output.UnsynchronizedByteArrayOutputStream;
import org.junit.jupiter.api.Test;

import org.apache.tika.TikaTest;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;

public class BOMDetectorTest extends TikaTest {
    @Test
    public void testBasic() throws Exception {
        EncodingDetector detector = new BOMDetector();
        for (ByteOrderMark bom : new ByteOrderMark[]{
                ByteOrderMark.UTF_8, ByteOrderMark.UTF_16BE,
                ByteOrderMark.UTF_16LE, ByteOrderMark.UTF_32BE, ByteOrderMark.UTF_32LE
        }) {
            UnsynchronizedByteArrayOutputStream bos = createStream(bom);
            try (BOMInputStream bomInputStream =
                    BOMInputStream.builder().
                            setInputStream(UnsynchronizedByteArrayInputStream.builder().setByteArray(bos.toByteArray()).get()).
                            setByteOrderMarks(ByteOrderMark.UTF_8, ByteOrderMark.UTF_32BE, ByteOrderMark.UTF_32LE,
                                 ByteOrderMark.UTF_16BE, ByteOrderMark.UTF_16LE).get()) {
                assertEquals(bom, bomInputStream.getBOM());
            }
            try (TikaInputStream tis = TikaInputStream.get(bos.toByteArray())) {
                List<EncodingResult> results = detector.detect(tis, new Metadata(), new ParseContext());
                assertEquals(Charset.forName(bom.getCharsetName()), results.get(0).getCharset());
                int cnt = 0;
                int c = tis.read();
                while (c > -1) {
                    cnt++;
                    c = tis.read();
                }
                assertEquals(100 * " ".getBytes(Charset.forName(bom.getCharsetName())).length
                        + bom.getBytes().length, cnt);
            }
        }
    }

    @Test
    public void testShort() throws Exception {
        EncodingDetector detector = new BOMDetector();
        for (ByteOrderMark bom : new ByteOrderMark[] {
                ByteOrderMark.UTF_8, ByteOrderMark.UTF_16BE, ByteOrderMark.UTF_16LE, ByteOrderMark.UTF_16BE,
                ByteOrderMark.UTF_32LE
        }) {
            byte[] bytes = new byte[3];
            System.arraycopy(bom.getBytes(), 0, bytes, 0, 1);
            bytes[1] = (byte)32;
            bytes[2] = (byte)32;
            try (TikaInputStream tis = TikaInputStream.get(bytes)) {
                assertTrue(detector.detect(tis, new Metadata(), new ParseContext()).isEmpty());
            }
        }
    }

    @Test
    public void testHintNotDeclaration() throws Exception {
        try (TikaInputStream tis = TikaInputStream.get(withBom(ByteOrderMark.UTF_8,
                "caf\u00e9".getBytes(StandardCharsets.UTF_8)))) {
            EncodingResult r = new BOMDetector().detect(tis, new Metadata(), new ParseContext()).get(0);
            assertEquals(EncodingResult.ResultType.STATISTICAL, r.getResultType());
        }
    }

    @Test
    public void testLyingBom() throws Exception {
        EncodingDetector detector = new BOMDetector();
        byte[] latin1 = "caf\u00e9 cr\u00e8me br\u00fbl\u00e9e".getBytes(StandardCharsets.ISO_8859_1);
        // UTF-8 BOM before a windows-1252 body: not valid UTF-8
        try (TikaInputStream tis = TikaInputStream.get(withBom(ByteOrderMark.UTF_8, latin1))) {
            assertTrue(detector.detect(tis, new Metadata(), new ParseContext()).isEmpty());
        }
        // UTF-16LE BOM before a lone low surrogate
        try (TikaInputStream tis = TikaInputStream.get(withBom(ByteOrderMark.UTF_16LE,
                new byte[]{0x41, 0x00, 0x00, (byte) 0xDC, 0x42, 0x00}))) {
            assertTrue(detector.detect(tis, new Metadata(), new ParseContext()).isEmpty());
        }
        // UTF-32LE BOM whose body isn't UTF-32 falls through to UTF-16LE (FF FE, then U+0000 ...)
        byte[] le16 = "\u0000abc".getBytes(StandardCharsets.UTF_16LE);
        try (TikaInputStream tis = TikaInputStream.get(withBom(ByteOrderMark.UTF_16LE, le16))) {
            assertEquals(StandardCharsets.UTF_16LE,
                    detector.detect(tis, new Metadata(), new ParseContext()).get(0).getCharset());
        }
    }

    @Test
    public void testTruncatedSequenceAtProbeEnd() throws Exception {
        byte[] body = new byte[8189]; // BOM + body = the 8192-byte probe
        java.util.Arrays.fill(body, (byte) 'a');
        body[body.length - 1] = (byte) 0xE2; // first byte of a 3-byte UTF-8 sequence, cut off
        try (TikaInputStream tis = TikaInputStream.get(withBom(ByteOrderMark.UTF_8, body))) {
            assertEquals(StandardCharsets.UTF_8,
                    new BOMDetector().detect(tis, new Metadata(), new ParseContext()).get(0).getCharset());
        }
    }

    @Test
    public void testTruncatedSequenceAtEndOfStream() throws Exception {
        byte[] body = {'a', 'b', 'c', (byte) 0xE2};
        try (TikaInputStream tis = TikaInputStream.get(withBom(ByteOrderMark.UTF_8, body))) {
            assertTrue(new BOMDetector().detect(tis, new Metadata(), new ParseContext()).isEmpty());
        }
    }

    @Test
    public void testReaderSkipsForeignBom() throws Exception {
        byte[] latin1 = "caf\u00e9".getBytes(StandardCharsets.ISO_8859_1);
        // ISO-8859-2 reads EF BB BF as "ď»ż"
        assertEquals("caf\u00e9", readAs(withBom(ByteOrderMark.UTF_8, latin1), "ISO-8859-2"));
        assertEquals("caf\u00e9", readAs(withBom(ByteOrderMark.UTF_16LE, latin1), "ISO-8859-1"));
        assertEquals("caf\u00e9", readAs(withBom(ByteOrderMark.UTF_8,
                "caf\u00e9".getBytes(StandardCharsets.UTF_8)), "UTF-8"));
        // the byte-order-neutral decoder needs its BOM
        assertEquals("caf\u00e9", readAs(withBom(ByteOrderMark.UTF_16LE,
                "caf\u00e9".getBytes(StandardCharsets.UTF_16LE)), "UTF-16"));
        // FF FE 00 00 under UTF-16LE is a BOM and U+0000, not a UTF-32LE BOM to skip
        assertEquals("\u0000caf\u00e9", readAs(withBom(ByteOrderMark.UTF_16LE,
                "\u0000caf\u00e9".getBytes(StandardCharsets.UTF_16LE)), "UTF-16LE"));
    }

    private static String readAs(byte[] bytes, String charset) throws Exception {
        EncodingDetector fixed = (tis, metadata, context) ->
                List.of(new EncodingResult(Charset.forName(charset), 1.0f));
        try (AutoDetectReader reader = new AutoDetectReader(TikaInputStream.get(bytes),
                new Metadata(), fixed)) {
            return IOUtils.toString(reader);
        }
    }

    private static byte[] withBom(ByteOrderMark bom, byte[] body) {
        byte[] out = java.util.Arrays.copyOf(bom.getBytes(), bom.length() + body.length);
        System.arraycopy(body, 0, out, bom.length(), body.length);
        return out;
    }

    /** BOM plus 100 spaces encoded in the BOM's own charset. */
    private UnsynchronizedByteArrayOutputStream createStream(ByteOrderMark bom) throws IOException {
        UnsynchronizedByteArrayOutputStream bos = UnsynchronizedByteArrayOutputStream.builder().get();
        IOUtils.write(bom.getBytes(), bos);
        byte[] space = " ".getBytes(Charset.forName(bom.getCharsetName()));
        for (int i = 0; i < 100; i++) {
            bos.write(space);
        }
        return bos;
    }
}
