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
package org.apache.tika.langdetect.charsoup;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.apache.tika.config.TikaComponent;
import org.apache.tika.detect.EncodingDetectorContext;
import org.apache.tika.detect.MetaEncodingDetector;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;

/**
 * A {@link MetaEncodingDetector} that uses the CharSoup language detector
 * to arbitrate when base encoding detectors disagree.
 *
 * <p>When base detectors all agree, the unanimous charset is returned
 * without any language detection. When they disagree, raw bytes are
 * read from the stream, decoded with each candidate charset, and each
 * decoded text is scored by {@link CharSoupLanguageDetector}. The
 * charset that produces the highest-confidence language detection wins.</p>
 *
 * <p>To enable, add this detector to your encoding detector chain in
 * tika-config:</p>
 * <pre>{@code
 * "encoding-detectors": [
 *   { "default-encoding-detector": {} },
 *   { "charsoup-encoding-detector": {} }
 * ]
 * }</pre>
 *
 * @since Apache Tika 3.2
 */
@TikaComponent(name = "charsoup-encoding-detector")
public class CharSoupEncodingDetector implements MetaEncodingDetector {

    private static final long serialVersionUID = 1L;

    private static final int DEFAULT_READ_LIMIT = 16384;

    private int readLimit = DEFAULT_READ_LIMIT;

    @Override
    public Charset detect(TikaInputStream tis, Metadata metadata,
                          ParseContext parseContext) throws IOException {
        EncodingDetectorContext context =
                parseContext.get(EncodingDetectorContext.class);
        if (context == null || context.getResults().isEmpty()) {
            return null;
        }

        Set<Charset> uniqueCharsets = context.getUniqueCharsets();

        if (uniqueCharsets.size() <= 1) {
            // Unanimous or single detector — no arbitration needed
            EncodingDetectorContext.Result first = context.getResults().get(0);
            context.setArbitrationInfo("unanimous");
            return first.getCharset();
        }

        // Disagreement — arbitrate via language detection scoring
        return arbitrate(tis, context, uniqueCharsets);
    }

    private Charset arbitrate(TikaInputStream tis,
                              EncodingDetectorContext context,
                              Set<Charset> uniqueCharsets) throws IOException {
        EncodingDetectorContext.Result firstResult = context.getResults().get(0);

        if (tis == null) {
            context.setArbitrationInfo("no-stream");
            return firstResult.getCharset();
        }

        byte[] bytes = readBytes(tis);
        if (bytes == null || bytes.length == 0) {
            context.setArbitrationInfo("empty-stream");
            return firstResult.getCharset();
        }

        Map<Charset, String> candidates = new LinkedHashMap<>();
        Map<Charset, Float> junkRatios = new LinkedHashMap<>();
        for (Charset candidate : uniqueCharsets) {
            String decoded = stripTags(decode(bytes, candidate));
            candidates.put(candidate, decoded);
            junkRatios.put(candidate, CharSoupLanguageDetector.junkRatio(decoded));
        }

        CharSoupLanguageDetector langDetector = new CharSoupLanguageDetector();
        Charset bestCharset = langDetector.compareLanguageSignal(candidates);
        if (bestCharset != null) {
            context.setArbitrationInfo("scored");
            return bestCharset;
        }

        // Language scoring was inconclusive (e.g. short filename, no natural-text signal).
        // Fall back to the candidate with the lowest junk ratio: if one encoding decodes the
        // bytes cleanly (no U+FFFD / undefined codepoints) while another produces many
        // replacement chars, the clean one is structurally more consistent.
        float minJunk = Float.POSITIVE_INFINITY;
        Charset leastJunky = null;
        for (Map.Entry<Charset, Float> e : junkRatios.entrySet()) {
            if (e.getValue() < minJunk) {
                minJunk = e.getValue();
                leastJunky = e.getKey();
            }
        }
        float firstJunk = junkRatios.getOrDefault(firstResult.getCharset(), 1f);
        if (leastJunky != null && minJunk < firstJunk) {
            context.setArbitrationInfo("junk-fallback");
            return leastJunky;
        }

        context.setArbitrationInfo("inconclusive");
        return firstResult.getCharset();
    }

    private byte[] readBytes(TikaInputStream tis) throws IOException {
        try {
            tis.mark(readLimit);
            byte[] buf = new byte[readLimit];
            int totalRead = 0;
            int bytesRead;
            while (totalRead < readLimit &&
                    (bytesRead = tis.read(buf, totalRead,
                            readLimit - totalRead)) != -1) {
                totalRead += bytesRead;
            }
            if (totalRead == 0) {
                return null;
            }
            if (totalRead < readLimit) {
                byte[] trimmed = new byte[totalRead];
                System.arraycopy(buf, 0, trimmed, 0, totalRead);
                return trimmed;
            }
            return buf;
        } finally {
            tis.reset();
        }
    }

    /**
     * Decode bytes using the given charset, replacing malformed/unmappable
     * characters rather than throwing.
     */
    static String decode(byte[] bytes, Charset charset) {
        CharsetDecoder decoder = charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPLACE)
                .onUnmappableCharacter(CodingErrorAction.REPLACE);
        CharBuffer cb = CharBuffer.allocate(bytes.length * 2);
        decoder.decode(ByteBuffer.wrap(bytes), cb, true);
        decoder.flush(cb);
        cb.flip();
        return cb.toString();
    }

    /**
     * Simple tag stripping: removes &lt;...&gt; sequences so that
     * HTML/XML tag names and attributes don't pollute language scoring.
     */
    static String stripTags(String text) {
        StringBuilder sb = new StringBuilder(text.length());
        boolean inTag = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '<') {
                inTag = true;
            } else if (c == '>') {
                inTag = false;
            } else if (!inTag) {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    public int getReadLimit() {
        return readLimit;
    }

    public void setReadLimit(int readLimit) {
        this.readLimit = readLimit;
    }
}
