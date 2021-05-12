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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

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

    // TODO - parameterize this
    public static int MIN_STRING_LENGTH = 8;
    // TODO - parameterize this
    public static float ACCEPTABLE_ALPHA_TO_OTHER_CHAR_RATIO = 0.6f;
    // TODO - parameterize this
    public static long BUFFER_SIZE = 1000000L;
    OneNoteDirectFileResource oneNoteDirectFileResource;
    XHTMLContentHandler xhtml;

    public OneNoteLegacyDumpStrings(OneNoteDirectFileResource oneNoteDirectFileResource,
                                    XHTMLContentHandler xhtml) {
        this.oneNoteDirectFileResource = oneNoteDirectFileResource;
        this.xhtml = xhtml;
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
        try {
            oneNoteDirectFileResource.position(0);
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            long sz = oneNoteDirectFileResource.size();
            long pos;
            while ((pos = oneNoteDirectFileResource.position()) != sz) {
                long nextBufferSize = BUFFER_SIZE;
                if (sz - pos < BUFFER_SIZE) {
                    nextBufferSize = sz - pos;
                }
                ByteBuffer byteBuffer = ByteBuffer.allocate((int) nextBufferSize);
                oneNoteDirectFileResource.read(byteBuffer);
                for (long i = 0; i < nextBufferSize - 1; ++i) {
                    int b = byteBuffer.get((int) i);
                    if (b >= 0x20 && b < 0x7F) {
                        os.write(b);
                    } else {
                        if (os.size() >= MIN_STRING_LENGTH) {
                            writeIfUseful(os);
                        }
                        os.reset();
                    }
                }
                if (os.size() >= MIN_STRING_LENGTH) {
                    writeIfUseful(os);
                }
            }
        } catch (IOException e) {
            throw new TikaException("Could not extract text from legacy OneNote document", e);
        }
    }

    /**
     * Based on GNU "strings" implementation. Pulls out UTF16 LE text segments and writes them to
     * the XHTMLContentHandler.
     */
    private void dumpUtf16LE() throws SAXException, TikaException {
        try {
            oneNoteDirectFileResource.position(0);
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            long sz = oneNoteDirectFileResource.size();
            long bufSize = BUFFER_SIZE;
            // Make sure the buffer size is a multiple of 2.
            if (bufSize % 2 == 1) {
                bufSize += 1L;
            }

            long pos;
            while ((pos = oneNoteDirectFileResource.position()) != sz) {
                long nextBufferSize = bufSize;
                if (sz - pos < bufSize) {
                    nextBufferSize = sz - pos;
                }
                ByteBuffer byteBuffer = ByteBuffer.allocate((int) nextBufferSize);
                oneNoteDirectFileResource.read(byteBuffer);
                for (long i = 0; i < nextBufferSize - 1; i++) {
                    int c1 = byteBuffer.get((int) i) & 0xff;
                    int c2 = byteBuffer.get((int) i + 1);

                    if (c2 == 0x00 && c1 >= 0x20) { // add this back? && c1 < 0x7F) {
                        ++i;
                        os.write(c1);
                    } else {
                        if (os.size() >= MIN_STRING_LENGTH) {
                            writeIfUseful(os);
                        }
                        os.reset();
                    }
                }
                if (os.size() >= MIN_STRING_LENGTH) {
                    writeIfUseful(os);
                }
            }
        } catch (IOException e) {
            throw new TikaException("Could not extract text from legacy OneNote document", e);
        }
    }

    /**
     * Writes a buffer of output characters if the (num alpha chars in the buffer) / (number of
     * chars in the buffer) >
     * ACCEPTABLE_ALPHA_TO_OTHER_CHAR_RATIO.
     *
     * @param os Byte array output stream containing the buffer.
     */
    private void writeIfUseful(ByteArrayOutputStream os) throws SAXException {
        String str = new String(os.toByteArray(), StandardCharsets.ISO_8859_1);
        String[] spl = str.split(" ");
        if (spl.length > 1) {
            int numAlpha = 0;
            for (int i = 0; i < str.length(); ++i) {
                if (Character.isAlphabetic(str.charAt(i)) || Character.isWhitespace(i)) {
                    ++numAlpha;
                }
            }
            float ratioAlphaToOtherChars = (float) numAlpha / (float) str.length();
            if (ratioAlphaToOtherChars > ACCEPTABLE_ALPHA_TO_OTHER_CHAR_RATIO) {
                xhtml.characters(str);
                xhtml.characters("\n");
            }
        }
    }
}
