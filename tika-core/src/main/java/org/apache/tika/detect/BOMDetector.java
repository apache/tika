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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.UnsupportedCharsetException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.commons.io.ByteOrderMark;
import org.apache.commons.io.IOUtils;

import org.apache.tika.annotation.TikaComponent;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;

/**
 * Encoding detector that reports the charset named by a byte-order mark (BOM) at the
 * start of the stream.
 *
 * <p>A BOM is a hint, not a declaration: editors prepend them to bytes in other
 * encodings, and concatenation leaves them in front of the wrong body.  The BOM is
 * reported only when the bytes after it decode cleanly in the named charset, and then
 * as a {@link EncodingResult.ResultType#STATISTICAL} candidate, so it never overrides the
 * other detectors; the meta arbiter weighs it like any other candidate.  A UTF-16 body is
 * checked only for unpaired surrogates and noncharacters, since nearly any even-length
 * byte run decodes as UTF-16; whether it is plausible text is left to the arbiter.</p>
 *
 * <p>{@code MojibusterEncodingDetector} and {@code JunkFilterEncodingDetector} strip the BOM
 * (see {@link #bomLength(byte[])}) before their own analysis.</p>
 *
 * @since Apache Tika 0.x (moved to org.apache.tika.detect in 4.0)
 */
@TikaComponent(spi = false)
public class BOMDetector implements EncodingDetector {

    private static final ByteOrderMark[] BOMS =
            //order matters -- have to try the 32 before the 16
            new ByteOrderMark[] {
                    ByteOrderMark.UTF_8,
                    ByteOrderMark.UTF_32BE,
                    ByteOrderMark.UTF_32LE,
                    ByteOrderMark.UTF_16BE,
                    ByteOrderMark.UTF_16LE
            };
    private static final Charset[] CHARSETS = new Charset[BOMS.length];

    private static final int MIN_BYTES = 2;
    private static final int PROBE_BYTES = 8192;
    static final float CONFIDENCE = 0.9f;

    static {
        for (int i = 0; i < BOMS.length; i++) {
            try {
                CHARSETS[i] = Charset.forName(BOMS[i].getCharsetName());
            } catch (UnsupportedCharsetException e) {
                //log it
            }
        }
    }

    @Override
    public List<EncodingResult> detect(TikaInputStream tis, Metadata metadata,
                                       ParseContext parseContext) throws IOException {
        byte[] head = new byte[4];
        tis.mark(head.length);
        int headRead;
        try {
            headRead = IOUtils.read(tis, head);
        } finally {
            tis.reset();
        }
        if (headRead < MIN_BYTES || bomLength(Arrays.copyOf(head, headRead)) == 0) {
            return Collections.emptyList();
        }
        tis.mark(PROBE_BYTES);
        byte[] bytes;
        boolean endOfInput;
        try {
            byte[] buf = new byte[PROBE_BYTES];
            int numRead = IOUtils.read(tis, buf);
            endOfInput = numRead < PROBE_BYTES;
            bytes = numRead == buf.length ? buf : Arrays.copyOf(buf, numRead);
        } finally {
            tis.reset();
        }
        for (int i = 0; i < BOMS.length; i++) {
            ByteOrderMark bom = BOMS[i];
            // FF FE 00 00 is also a UTF-16LE BOM followed by U+0000: fall through to UTF-16 if
            // the body isn't valid UTF-32
            if (startsWith(bom, bytes) && CHARSETS[i] != null
                    && bodyDecodes(bytes, bom.length(), CHARSETS[i], endOfInput)) {
                return List.of(new EncodingResult(CHARSETS[i], CONFIDENCE,
                        CHARSETS[i].name(), EncodingResult.ResultType.STATISTICAL));
            }
        }
        return Collections.emptyList();
    }

    /**
     * Strict decode of the bytes after the BOM. A sequence cut off by the probe limit is not
     * an error; one cut off by the end of the stream is. U+FFFE and U+FFFF never occur in text.
     */
    static boolean bodyDecodes(byte[] bytes, int offset, Charset cs, boolean endOfInput) {
        CharsetDecoder dec = cs.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        ByteBuffer in = ByteBuffer.wrap(bytes, offset, bytes.length - offset);
        CharBuffer out = CharBuffer.allocate(bytes.length);
        CoderResult r = dec.decode(in, out, endOfInput);
        if (r.isError()) {
            return false;
        }
        out.flip();
        while (out.hasRemaining()) {
            char c = out.get();
            if (c == '\uFFFE' || c == '\uFFFF') {
                return false;
            }
        }
        return true;
    }

    /** Length of the BOM at the start of {@code bytes}, or 0. UTF-32 is tried before UTF-16. */
    public static int bomLength(byte[] bytes) {
        for (ByteOrderMark bom : BOMS) {
            if (startsWith(bom, bytes)) {
                return bom.length();
            }
        }
        return 0;
    }

    /**
     * Skips a BOM at the current position of {@code tis} unless it names {@code charset}.
     * Readers drop only U+FEFF, so a BOM the detectors rejected would reach the text as junk
     * (EF BB BF read as windows-1252), and jsoup lets a BOM override the charset it is given.
     */
    public static void skipForeignBom(TikaInputStream tis, Charset charset) throws IOException {
        byte[] head = new byte[4];
        tis.mark(head.length);
        int n;
        try {
            n = IOUtils.read(tis, head);
        } finally {
            tis.reset();
        }
        head = Arrays.copyOf(head, n);
        int skip = 0;
        // FF FE 00 00 is both the UTF-32LE BOM and the UTF-16LE BOM: keep it if either is ours
        for (int i = 0; i < BOMS.length; i++) {
            if (startsWith(BOMS[i], head)) {
                if (isOwnBom(CHARSETS[i], charset)) {
                    return;
                }
                skip = Math.max(skip, BOMS[i].length());
            }
        }
        IOUtils.skipFully(tis, skip);
    }

    /** {@code bomCharset} is {@code charset}, or its byte-order-neutral form (which reads the BOM). */
    private static boolean isOwnBom(Charset bomCharset, Charset charset) {
        if (bomCharset == null) {
            return false;
        }
        String bom = bomCharset.name();
        String cs = charset.name();
        return bom.equals(cs) || bom.startsWith(cs) && (cs.equals("UTF-16") || cs.equals("UTF-32"));
    }

    private static boolean startsWith(ByteOrderMark bom, byte[] bytes) {
        byte[] bomBytes = bom.getBytes();
        if (bytes.length < bomBytes.length) {
            return false;
        }
        for (int i = 0; i < bomBytes.length; i++) {
            if (bomBytes[i] != bytes[i]) {
                return false;
            }
        }
        return true;
    }
}
