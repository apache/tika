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
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.tika.config.TikaComponent;
import org.apache.tika.detect.EncodingDetectorContext;
import org.apache.tika.detect.EncodingResult;
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

    /**
     * Symmetric confusable peer groups: within each group, encoding variants
     * (e.g. ISO-8859-6 vs windows-1256) produce different decoded text for the
     * same byte sequence (unlike ISO-8859-1 vs windows-1252 which are functional
     * supersets). When the language-quality winner and a CONFIDENCE_DEFINITIVE
     * declaration are in the same peer group, the language model cannot reliably
     * distinguish them — it merely reflects which variant happens to produce
     * Arabic (or Cyrillic, …) n-grams its training data favoured.
     * In that case we prefer the explicit declaration.
     */
    private static final Map<String, Set<String>> PEER_GROUPS;

    static {
        Map<String, Set<String>> m = new HashMap<>();
        for (String[] group : new String[][] {
                {"ISO-8859-1",  "ISO-8859-15", "windows-1252"},
                {"ISO-8859-2",  "windows-1250"},
                {"ISO-8859-5",  "windows-1251"},
                {"KOI8-R",      "KOI8-U"},
                {"ISO-8859-6",  "windows-1256"},
                {"ISO-8859-7",  "windows-1253"},
                {"ISO-8859-8",  "windows-1255"},
                {"ISO-8859-9",  "windows-1254"},
                {"ISO-8859-13", "windows-1257"},
                {"ISO-8859-4",  "windows-1257"},
        }) {
            Set<String> s = new HashSet<>(Arrays.asList(group));
            for (String name : group) {
                m.put(name, s);
            }
        }
        PEER_GROUPS = Collections.unmodifiableMap(m);
    }

    private static boolean arePeers(Charset a, Charset b) {
        Set<String> peers = PEER_GROUPS.get(a.name());
        return peers != null && peers.contains(b.name());
    }

    private int readLimit = DEFAULT_READ_LIMIT;

    @Override
    public List<EncodingResult> detect(TikaInputStream tis, Metadata metadata,
                                       ParseContext parseContext) throws IOException {
        EncodingDetectorContext context =
                parseContext.get(EncodingDetectorContext.class);
        if (context == null || context.getResults().isEmpty()) {
            return Collections.emptyList();
        }

        Set<Charset> uniqueCharsets = context.getUniqueCharsets();

        Charset winner;
        if (uniqueCharsets.size() <= 1) {
            context.setArbitrationInfo("unanimous");
            winner = context.getResults().get(0).getCharset();
        } else {
            winner = arbitrate(tis, context, uniqueCharsets);
        }

        if (winner == null) {
            return Collections.emptyList();
        }
        float confidence = context.getTopConfidenceFor(winner);
        return List.of(new EncodingResult(winner, confidence));
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
        for (Charset candidate : uniqueCharsets) {
            candidates.put(candidate, stripTags(decode(bytes, candidate)));
        }

        CharSoupLanguageDetector langDetector = new CharSoupLanguageDetector();
        Charset bestCharset = langDetector.compareLanguageSignal(candidates);
        if (bestCharset == null) {
            Charset fallback = firstResult.getCharset();
            String fallbackDecoded = candidates.get(fallback);
            float fallbackJunk = fallbackDecoded != null
                    ? CharSoupLanguageDetector.junkRatio(fallbackDecoded) : 1f;

            // If the fallback charset produces garbled output (replacement chars) but
            // a definitive declaration decodes the bytes cleanly, the probe was likely
            // too short or ASCII-only. Trust the explicit declaration in that case.
            Charset cleanerDeclared = null;
            if (fallbackJunk > 0f) {
                for (EncodingDetectorContext.Result r : context.getResults()) {
                    if (r.getConfidence() >= EncodingResult.CONFIDENCE_DEFINITIVE) {
                        String declaredDecoded = candidates.get(r.getCharset());
                        float declaredJunk = declaredDecoded != null
                                ? CharSoupLanguageDetector.junkRatio(declaredDecoded) : 1f;
                        if (declaredJunk < fallbackJunk / 2) {
                            cleanerDeclared = r.getCharset();
                            break;
                        }
                    }
                }
            }

            if (cleanerDeclared != null) {
                context.setArbitrationInfo("scored-inconclusive-use-cleaner-declared");
                return cleanerDeclared;
            }
            bestCharset = fallback;
        }

        // If a structurally-declared charset (CONFIDENCE_DEFINITIVE, e.g. HTML meta tag)
        // decodes the bytes to the same string as the language-quality winner, prefer
        // the declaration. This validates the HTML header against the actual bytes:
        // if they are functionally equivalent, trust the author's stated encoding.
        // If they produce different text (a real conflict), the bytes win.
        //
        // Additionally, when the winner and the declared charset are in the same
        // confusable peer group (e.g. ISO-8859-6 vs windows-1256) and the declared
        // charset decodes cleanly (low junk ratio), the language model cannot
        // reliably distinguish them — they both produce valid same-script text.
        // In that case, prefer the explicit declaration over the model's guess.
        String winnerDecoded = candidates.get(bestCharset);
        float winnerJunk = winnerDecoded != null ? CharSoupLanguageDetector.junkRatio(winnerDecoded) : 1f;
        if (winnerDecoded != null) {
            for (EncodingDetectorContext.Result r : context.getResults()) {
                if (r.getConfidence() >= EncodingResult.CONFIDENCE_DEFINITIVE
                        && !r.getCharset().equals(bestCharset)) {
                    Charset declared = r.getCharset();
                    String declaredDecoded = candidates.get(declared);
                    if (declaredDecoded == null) {
                        continue;
                    }
                    if (declaredDecoded.equals(winnerDecoded)) {
                        context.setArbitrationInfo("scored-prefer-declared");
                        return declared;
                    }
                    // When the winner and the declared charset are in the same confusable
                    // peer group (e.g. ISO-8859-6 vs windows-1256), and the declared
                    // charset decodes at least as cleanly as the winner (not junkier),
                    // prefer the explicit declaration — the language model cannot reliably
                    // distinguish same-script encoding variants.
                    float declaredJunk = CharSoupLanguageDetector.junkRatio(declaredDecoded);
                    if (arePeers(bestCharset, declared) && declaredJunk <= winnerJunk) {
                        context.setArbitrationInfo("scored-prefer-declared-peer");
                        return declared;
                    }
                }
            }
        }

        context.setArbitrationInfo("scored");
        return bestCharset;
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
