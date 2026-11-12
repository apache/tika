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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

import org.apache.tika.exception.TikaException;
import org.apache.tika.quality.TextQualityDetector;
import org.apache.tika.quality.TextQualityScore;
import org.apache.tika.sax.XHTMLContentHandler;

/**
 * Pre-2010 OneNote formats have no published spec and are incompatible with the later ones, so
 * ASCII and UTF-16LE strings are scraped as GNU "strings" does.
 */
class OneNoteLegacyDumpStrings {

    private static final Logger LOG = LoggerFactory.getLogger(OneNoteLegacyDumpStrings.class);

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
    // reused by both passes
    private ByteBuffer byteBuffer;
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
            ClassLoader own = OneNoteLegacyDumpStrings.class.getClassLoader();
            TextQualityDetector detector = load(own);
            ClassLoader context = Thread.currentThread().getContextClassLoader();
            if (detector == null && context != null && context != own) {
                detector = load(context);
            }
            if (detector == null) {
                LOG.debug("No TextQualityDetector found; the OneNote dump uses a letter ratio");
            }
            return detector;
        }

        private static TextQualityDetector load(ClassLoader loader) {
            try {
                return ServiceLoader.load(TextQualityDetector.class, loader).findFirst()
                        .orElse(null);
            } catch (ServiceConfigurationError | RuntimeException e) {
                LOG.warn("Could not load a TextQualityDetector", e);
                return null;
            }
        }
    }

    public void dump() throws TikaException, SAXException {
        dumpAscii();
        dumpUtf16LE();
    }

    private void dumpAscii() throws SAXException, TikaException {
        UnsynchronizedByteArrayOutputStream os = UnsynchronizedByteArrayOutputStream.builder().get();
        try {
            oneNoteDirectFileResource.position(0);
            ByteBuffer buf;
            while ((buf = nextBuffer()) != null) {
                while (buf.hasRemaining()) {
                    int b = buf.get();
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

    private void dumpUtf16LE() throws SAXException, TikaException {
        UnsynchronizedByteArrayOutputStream os = UnsynchronizedByteArrayOutputStream.builder().get();
        try {
            oneNoteDirectFileResource.position(0);
            // low byte of the current candidate pair, carried across buffers; -1 if none
            int low = -1;
            ByteBuffer buf;
            while ((buf = nextBuffer()) != null) {
                while (buf.hasRemaining()) {
                    int b = buf.get();
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
        if (byteBuffer == null) {
            byteBuffer = ByteBuffer.allocate(
                    (int) Math.min(bufferSize, oneNoteDirectFileResource.size()));
        }
        byteBuffer.clear();
        byteBuffer.limit((int) Math.min(byteBuffer.capacity(), remaining));
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

    /** Writes the buffer if it holds more than one token and reads as text. */
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
