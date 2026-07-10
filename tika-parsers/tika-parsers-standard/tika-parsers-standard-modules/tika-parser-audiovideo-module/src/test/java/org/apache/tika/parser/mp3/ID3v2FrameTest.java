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
package org.apache.tika.parser.mp3;

import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static java.nio.charset.StandardCharsets.UTF_16BE;
import static java.nio.charset.StandardCharsets.UTF_16LE;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;

import org.junit.jupiter.api.Test;

import org.apache.tika.parser.mp3.ID3Tags.ID3Comment;

/**
 * Tests the shared ID3v2 text decoding used by every ID3v2.2/2.3/2.4 text frame.
 */
public class ID3v2FrameTest {

    private static final byte ISO_8859_1_FLAG = 0;
    private static final byte UTF_16_BOM_FLAG = 1;
    private static final byte UTF_16BE_FLAG = 2;
    private static final byte UTF_8_FLAG = 3;

    private static final byte[] BOM_LE = {(byte) 0xff, (byte) 0xfe};
    private static final byte[] BOM_BE = {(byte) 0xfe, (byte) 0xff};

    /**
     * Latin text, which encodes to a NUL byte per character in UTF-16 and so carries a
     * byte-order signal, and CJK text, which does not.
     */
    private static final String LATIN = "Test Copyright";
    private static final String CJK = "日本語";

    private static byte[] frame(byte encodingFlag, byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(encodingFlag);
        for (byte[] part : parts) {
            out.write(part, 0, part.length);
        }
        return out.toByteArray();
    }

    private static byte[] bytes(String text, Charset charset) {
        return text.getBytes(charset);
    }

    private static String tagString(byte[] data) {
        return ID3v2Frame.getTagString(data, 0, data.length);
    }

    @Test
    public void testSingleByteEncodings() {
        assertEquals(LATIN, tagString(frame(ISO_8859_1_FLAG, bytes(LATIN, ISO_8859_1))));
        assertEquals(CJK, tagString(frame(UTF_8_FLAG, bytes(CJK, UTF_8))));
    }

    @Test
    public void testUTF16WithBOM() {
        assertEquals(LATIN, tagString(frame(UTF_16_BOM_FLAG, BOM_LE, bytes(LATIN, UTF_16LE))));
        assertEquals(LATIN, tagString(frame(UTF_16_BOM_FLAG, BOM_BE, bytes(LATIN, UTF_16BE))));
        assertEquals(CJK, tagString(frame(UTF_16_BOM_FLAG, BOM_LE, bytes(CJK, UTF_16LE))));
        assertEquals(CJK, tagString(frame(UTF_16_BOM_FLAG, BOM_BE, bytes(CJK, UTF_16BE))));
    }

    @Test
    public void testUTF16BEWithoutBOMFlag() {
        assertEquals(LATIN, tagString(frame(UTF_16BE_FLAG, bytes(LATIN, UTF_16BE))));
        assertEquals(CJK, tagString(frame(UTF_16BE_FLAG, bytes(CJK, UTF_16BE))));
    }

    /**
     * Encoding $01 promises a BOM. Taggers that omit it used to be decoded as big-endian,
     * turning little-endian text into CJK mojibake ('T' 0x54 0x00 becomes U+5400).
     */
    @Test
    public void testUTF16WithoutBOMRecoversByteOrder() {
        assertEquals(LATIN, tagString(frame(UTF_16_BOM_FLAG, bytes(LATIN, UTF_16LE))));
        assertEquals(LATIN, tagString(frame(UTF_16_BOM_FLAG, bytes(LATIN, UTF_16BE))));
    }

    /**
     * A BOM-less frame whose characters are all above U+00FF has no NUL bytes and so gives
     * no byte-order signal at all. Nothing can rescue that, so the big-endian default of
     * Java's UTF-16 charset must be preserved rather than guessed away.
     */
    @Test
    public void testUTF16WithoutBOMKeepsBigEndianDefaultWhenNoSignal() {
        assertEquals(CJK, tagString(frame(UTF_16_BOM_FLAG, bytes(CJK, UTF_16BE))));
    }

    @Test
    public void testNullTerminationIsTrimmed() {
        byte[] doubleNul = {0, 0};
        byte[] singleNul = {0};
        assertEquals(LATIN, tagString(frame(UTF_16_BOM_FLAG, bytes(LATIN, UTF_16LE), doubleNul)));
        assertEquals(LATIN,
                tagString(frame(UTF_16_BOM_FLAG, BOM_LE, bytes(LATIN, UTF_16LE), doubleNul)));
        assertEquals(LATIN, tagString(frame(ISO_8859_1_FLAG, bytes(LATIN, ISO_8859_1), singleNul)));
    }

    /**
     * TIKA-1024: a frame holding nothing but a BOM decodes to the empty string.
     */
    @Test
    public void testNakedBOM() {
        assertEquals("", tagString(frame(UTF_16_BOM_FLAG, BOM_LE)));
        assertEquals("", tagString(frame(UTF_16_BOM_FLAG, BOM_BE)));
    }

    /**
     * COMM frames decode a description and a text part separately, so both have to recover
     * the byte order independently.
     */
    @Test
    public void testCommentWithoutBOMRecoversByteOrder() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(UTF_16_BOM_FLAG);
        out.write(bytes("eng", ISO_8859_1));
        out.write(bytes("Desc", UTF_16LE));
        out.write(new byte[]{0, 0});
        out.write(bytes(LATIN, UTF_16LE));
        byte[] data = out.toByteArray();

        ID3Comment comment = ID3v2Frame.getComment(data, 0, data.length);
        assertEquals("eng", comment.getLanguage());
        assertEquals("Desc", comment.getDescription());
        assertEquals(LATIN, comment.getText());
    }

    @Test
    public void testCommentWithBOM() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(UTF_16_BOM_FLAG);
        out.write(bytes("eng", ISO_8859_1));
        out.write(BOM_LE);
        out.write(bytes("Desc", UTF_16LE));
        out.write(new byte[]{0, 0});
        out.write(BOM_LE);
        out.write(bytes(LATIN, UTF_16LE));
        byte[] data = out.toByteArray();

        ID3Comment comment = ID3v2Frame.getComment(data, 0, data.length);
        assertEquals("eng", comment.getLanguage());
        assertEquals("Desc", comment.getDescription());
        assertEquals(LATIN, comment.getText());
    }

    /**
     * A comment frame too short to hold an encoding flag and a 3 byte language, or one
     * carrying an unknown encoding flag, decodes to null rather than reading off the end
     * of the frame.
     */
    @Test
    public void testMalformedCommentsReturnNull() {
        assertNull(ID3v2Frame.getComment(new byte[0], 0, 0));
        assertNull(ID3v2Frame.getComment(new byte[]{UTF_16_BOM_FLAG}, 0, 1));
        assertNull(ID3v2Frame.getComment(new byte[]{ISO_8859_1_FLAG, 'e', 'n'}, 0, 3));
        // 0x05 is not a defined ID3v2 text encoding
        byte[] badFlag = {5, 'e', 'n', 'g', 'D', 'e', 's', 'c', 0, 'T'};
        assertNull(ID3v2Frame.getComment(badFlag, 0, badFlag.length));
    }

    /**
     * A double byte comment whose frame ends on a lone NUL has no room for a terminator,
     * and must not read past the end of the frame looking for one.
     */
    @Test
    public void testCommentTruncatedOnTerminatorDoesNotOverrun() {
        byte[] data = {UTF_16_BOM_FLAG, 'e', 'n', 'g', 0};
        ID3Comment comment = ID3v2Frame.getComment(data, 0, data.length);
        assertEquals("eng", comment.getLanguage());
    }
}
