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
package org.apache.tika.parser.microsoft.onenote;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import org.apache.commons.io.output.UnsynchronizedByteArrayOutputStream;
import org.xml.sax.SAXException;

import org.apache.tika.exception.TikaException;
import org.apache.tika.sax.XHTMLContentHandler;

/**
 * OneNote versions before OneNote 2010 do not have a published OpenSpec document, and the older
 * formats are drastically
 * incompatible with the later OpenSpecs.
 * Therefore, we resort to scraping out useful ASCII and UTF16LE strings using a similar
 * algorithm used by the GNU "strings"
 * program.
 * <p>
 * This is only needed for OneNote versions prior to 2010.
 */
class OneNoteLegacyDumpStrings {

    private static final int MIN_STRING_LENGTH = 8;
    private static final float ACCEPTABLE_ALPHA_TO_OTHER_CHAR_RATIO = 0.6f;
    private static final int DEFAULT_BUFFER_SIZE = 1_000_000;
    // a longer run is written in pieces, so one crafted run cannot buffer the whole file
    private static final int MAX_RUN_LENGTH = 1_000_000;

    private final OneNoteDirectFileResource oneNoteDirectFileResource;
    private final XHTMLContentHandler xhtml;
    private final int bufferSize;

    public OneNoteLegacyDumpStrings(OneNoteDirectFileResource oneNoteDirectFileResource,
                                    XHTMLContentHandler xhtml) {
        this(oneNoteDirectFileResource, xhtml, DEFAULT_BUFFER_SIZE);
    }

    OneNoteLegacyDumpStrings(OneNoteDirectFileResource oneNoteDirectFileResource,
                             XHTMLContentHandler xhtml, int bufferSize) {
        this.oneNoteDirectFileResource = oneNoteDirectFileResource;
        this.xhtml = xhtml;
        this.bufferSize = bufferSize;
    }

    /**
     * Dump all "useful" Ascii and UTF16LE strings found in the file to the XHTMLContentHandler.
     *
     * @throws TikaException
     * @throws SAXException
     */
    public void dump() throws TikaException, SAXException {
        dumpAscii();
        dumpUtf16LE();
    }

    /**
     * Based on GNU "strings" implementation. Pulls out ascii text segments and writes them to
     * the XHTMLContentHandler.
     */
    private void dumpAscii() throws SAXException, TikaException {
        UnsynchronizedByteArrayOutputStream os = UnsynchronizedByteArrayOutputStream.builder().get();
        try {
            oneNoteDirectFileResource.position(0);
            ByteBuffer byteBuffer;
            while ((byteBuffer = nextBuffer()) != null) {
                while (byteBuffer.hasRemaining()) {
                    int b = byteBuffer.get();
                    if (b >= 0x20 && b < 0x7F) {
                        os.write(b);
                        if (os.size() >= MAX_RUN_LENGTH) {
                            endRun(os);
                        }
                    } else {
                        endRun(os);
                    }
                }
            }
            endRun(os);
        } catch (IOException e) {
            throw new TikaException("Could not extract text from legacy OneNote document", e);
        }
    }

    /**
     * Based on GNU "strings" implementation. Pulls out UTF16 LE text segments and writes them to
     * the XHTMLContentHandler.
     */
    private void dumpUtf16LE() throws SAXException, TikaException {
        UnsynchronizedByteArrayOutputStream os = UnsynchronizedByteArrayOutputStream.builder().get();
        try {
            oneNoteDirectFileResource.position(0);
            // low byte of the current candidate pair, carried across buffers; -1 if none
            int low = -1;
            ByteBuffer byteBuffer;
            while ((byteBuffer = nextBuffer()) != null) {
                while (byteBuffer.hasRemaining()) {
                    int b = byteBuffer.get();
                    if (low < 0) {
                        low = b & 0xff;
                    } else if (b == 0x00 && low >= 0x20) {
                        os.write(low);
                        low = -1;
                        if (os.size() >= MAX_RUN_LENGTH) {
                            endRun(os);
                        }
                    } else {
                        // not a character: resynchronize one byte later
                        endRun(os);
                        low = b & 0xff;
                    }
                }
            }
            endRun(os);
        } catch (IOException e) {
            throw new TikaException("Could not extract text from legacy OneNote document", e);
        }
    }

    private ByteBuffer nextBuffer() throws IOException {
        long remaining = oneNoteDirectFileResource.size() - oneNoteDirectFileResource.position();
        if (remaining <= 0) {
            return null;
        }
        ByteBuffer byteBuffer = ByteBuffer.allocate((int) Math.min(bufferSize, remaining));
        oneNoteDirectFileResource.read(byteBuffer);
        byteBuffer.flip();
        return byteBuffer;
    }

    private void endRun(UnsynchronizedByteArrayOutputStream os) throws SAXException {
        if (os.size() >= MIN_STRING_LENGTH) {
            writeIfUseful(os);
        }
        os.reset();
    }

    /**
     * Writes a buffer of output characters if it holds more than one word and
     * (alphabetic + whitespace chars) / (all chars) > ACCEPTABLE_ALPHA_TO_OTHER_CHAR_RATIO.
     *
     * @param os Byte array output stream containing the buffer.
     */
    private void writeIfUseful(UnsynchronizedByteArrayOutputStream os) throws SAXException {
        String str = os.toString(StandardCharsets.ISO_8859_1);
        if (!hasInnerSpace(str)) {
            return;
        }
        int numAlpha = 0;
        for (int i = 0; i < str.length(); ++i) {
            char c = str.charAt(i);
            if (Character.isAlphabetic(c) || Character.isWhitespace(c)) {
                ++numAlpha;
            }
        }
        float ratioAlphaToOtherChars = (float) numAlpha / (float) str.length();
        if (ratioAlphaToOtherChars > ACCEPTABLE_ALPHA_TO_OTHER_CHAR_RATIO) {
            xhtml.characters(str);
            xhtml.characters("\n");
        }
    }

    /** A space before the last non-space character: two or more words. */
    private static boolean hasInnerSpace(String str) {
        int end = str.length();
        while (end > 0 && str.charAt(end - 1) == ' ') {
            end--;
        }
        return end > 0 && str.lastIndexOf(' ', end - 1) >= 0;
    }
}
