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
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;

import org.apache.commons.io.output.UnsynchronizedByteArrayOutputStream;
import org.xml.sax.SAXException;

import org.apache.tika.exception.TikaException;
import org.apache.tika.quality.TextQualityDetector;
import org.apache.tika.quality.TextQualityScore;
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
    // a text-quality logit; scraped binary scores below about -1, scraped text above
    private static final float MIN_TEXT_QUALITY = -1f;
    private static final int DEFAULT_BUFFER_SIZE = 1_000_000;
    // a longer run is written in pieces, so one crafted run cannot buffer the whole file
    private static final int MAX_RUN_LENGTH = 1_000_000;

    private final OneNoteDirectFileResource oneNoteDirectFileResource;
    private final XHTMLContentHandler xhtml;
    private final int bufferSize;
    private final TextQualityDetector qualityDetector;

    public OneNoteLegacyDumpStrings(OneNoteDirectFileResource oneNoteDirectFileResource,
                                    XHTMLContentHandler xhtml) {
        this(oneNoteDirectFileResource, xhtml, DEFAULT_BUFFER_SIZE,
                QualityDetectorHolder.DETECTOR);
    }

    /**
     * @param qualityDetector scores candidate strings; null uses a letter-ratio heuristic
     */
    OneNoteLegacyDumpStrings(OneNoteDirectFileResource oneNoteDirectFileResource,
                             XHTMLContentHandler xhtml, int bufferSize,
                             TextQualityDetector qualityDetector) {
        this.oneNoteDirectFileResource = oneNoteDirectFileResource;
        this.xhtml = xhtml;
        this.bufferSize = bufferSize;
        this.qualityDetector = qualityDetector;
    }

    private static final class QualityDetectorHolder {
        private static final TextQualityDetector DETECTOR = load();

        private static TextQualityDetector load() {
            try {
                return ServiceLoader.load(TextQualityDetector.class,
                        OneNoteLegacyDumpStrings.class.getClassLoader()).findFirst().orElse(null);
            } catch (ServiceConfigurationError | RuntimeException e) {
                return null;
            }
        }
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
     * Writes a buffer of output characters if it holds more than one token and reads as text.
     *
     * @param os Byte array output stream containing the buffer.
     */
    private void writeIfUseful(UnsynchronizedByteArrayOutputStream os) throws SAXException {
        String str = os.toString(StandardCharsets.ISO_8859_1);
        if (!hasInnerSpace(str)) {
            return;
        }
        if (qualityDetector != null ? readsAsText(str) : looksLikeText(str)) {
            xhtml.characters(str);
            xhtml.characters("\n");
        }
    }

    private boolean readsAsText(String str) {
        TextQualityScore score = qualityDetector.score(str);
        return !score.isUnknown() && score.getZScore() > MIN_TEXT_QUALITY;
    }

    /**
     * Without a quality detector: a word (a letter followed by at least two lower-case
     * letters), and letters make up more than ACCEPTABLE_ALPHA_TO_OTHER_CHAR_RATIO of the
     * non-whitespace chars. Binary data rarely has a lower-case run; all-caps text is lost.
     */
    private static boolean looksLikeText(String str) {
        if (!hasWord(str)) {
            return false;
        }
        int numAlpha = 0;
        int numNonSpace = 0;
        for (int i = 0; i < str.length(); ++i) {
            char c = str.charAt(i);
            if (!Character.isWhitespace(c)) {
                ++numNonSpace;
                if (Character.isAlphabetic(c)) {
                    ++numAlpha;
                }
            }
        }
        return (float) numAlpha / (float) numNonSpace > ACCEPTABLE_ALPHA_TO_OTHER_CHAR_RATIO;
    }

    private static boolean hasWord(String str) {
        int lowerRun = 0;
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            if (c >= 'a' && c <= 'z') {
                if (++lowerRun >= 2 && i >= 2 && isAsciiLetter(str.charAt(i - 2))) {
                    return true;
                }
            } else {
                lowerRun = 0;
            }
        }
        return false;
    }

    private static boolean isAsciiLetter(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
    }

    /** A space before the last non-space character: two or more tokens. */
    private static boolean hasInnerSpace(String str) {
        int end = str.length();
        while (end > 0 && str.charAt(end - 1) == ' ') {
            end--;
        }
        return end > 0 && str.lastIndexOf(' ', end - 1) >= 0;
    }
}
