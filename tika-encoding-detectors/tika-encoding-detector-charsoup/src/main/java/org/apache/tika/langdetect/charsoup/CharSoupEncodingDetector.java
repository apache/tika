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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.config.TikaComponent;
import org.apache.tika.detect.EncodingDetectorContext;
import org.apache.tika.detect.EncodingResult;
import org.apache.tika.detect.MetaEncodingDetector;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.language.detect.LanguageResult;
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

    private static final Logger LOG = LoggerFactory.getLogger(CharSoupEncodingDetector.class);

    private static final int DEFAULT_READ_LIMIT = 16384;

    private static final String GLM_RESOURCE = GenerativeLanguageModel.DEFAULT_MODEL_RESOURCE;

    /**
     * Minimum z-score for the generative-model tiebreaker to consider a
     * candidate "language-like enough" to win. Candidates below this are
     * treated as mojibake.
     */
    private static final float MIN_GENERATIVE_ZSCORE = -4.0f;

    private static final GenerativeLanguageModel GLM;

    static {
        try {
            GLM = GenerativeLanguageModel.loadFromClasspath(GLM_RESOURCE);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load generative language model: "
                    + GLM_RESOURCE, e);
        }
    }

    /**
     * Symmetric confusable peer groups: within each group, encoding variants
     * (e.g. ISO-8859-6 vs windows-1256) produce different decoded text for the
     * same byte sequence (unlike ISO-8859-1 vs windows-1252 which are functional
     * supersets). When the language-quality winner and a DECLARATIVE result
     * are in the same peer group, the language model cannot reliably
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

    private static boolean hasPositiveLangSignal(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        CharSoupLanguageDetector detector = new CharSoupLanguageDetector();
        char[] chars = text.toCharArray();
        detector.addText(chars, 0, chars.length);
        List<LanguageResult> results = detector.detectAll();
        return !results.isEmpty() && results.get(0).getRawScore() > 0f;
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
        bytes = stripBomBytes(bytes);

        Map<Charset, String> candidates = new LinkedHashMap<>();
        for (Charset candidate : uniqueCharsets) {
            candidates.put(candidate, stripTags(decode(bytes, candidate)));
        }

        CharSoupLanguageDetector langDetector = new CharSoupLanguageDetector();
        Charset bestCharset = langDetector.compareLanguageSignal(candidates);
        if (bestCharset == null) {
            // Discriminative model inconclusive. Try generative model as tiebreaker.
            Charset generativeWinner = generativeTiebreak(candidates);
            if (generativeWinner != null) {
                context.setArbitrationInfo("scored-inconclusive-generative-tiebreak");
                return generativeWinner;
            }

            // Generative model also inconclusive. When a DECLARATIVE result
            // (HTML meta charset, BOM, HTTP Content-Type) exists and decodes
            // the bytes at least as cleanly as the statistical fallback,
            // trust the declaration. This covers:
            //  • Pure-ASCII probe (both decodings identical) — prefer declared.
            //  • Probe with high bytes valid in BOTH charsets (e.g. Cyrillic
            //    in a page starting with ASCII JavaScript).
            Charset fallback = firstResult.getCharset();
            String fallbackDecoded = candidates.get(fallback);
            float fallbackJunk = fallbackDecoded != null
                    ? CharSoupLanguageDetector.junkRatio(fallbackDecoded) : 1f;

            Charset cleanerDeclared = null;
            for (EncodingDetectorContext.Result r : context.getResults()) {
                if (r.getResultType() == EncodingResult.ResultType.DECLARATIVE) {
                    String declaredDecoded = candidates.get(r.getCharset());
                    float declaredJunk = declaredDecoded != null
                            ? CharSoupLanguageDetector.junkRatio(declaredDecoded) : 1f;
                    if (declaredJunk <= fallbackJunk) {
                        cleanerDeclared = r.getCharset();
                        break;
                    }
                }
            }

            if (cleanerDeclared != null) {
                context.setArbitrationInfo("scored-inconclusive-use-cleaner-declared");
                return cleanerDeclared;
            }
            bestCharset = fallback;
        }

        // If a DECLARATIVE result (e.g. HTML meta charset) decodes the bytes to the same
        // string as the language-quality winner, prefer the declaration. This validates the
        // declared encoding against the actual bytes: if they are functionally equivalent,
        // trust the author's stated encoding. If they produce different text (a real conflict
        // — e.g. a lying BOM or a wrong meta tag), the bytes win and the language scorer's
        // choice stands.
        //
        // Additionally, when the winner and a DECLARATIVE charset are in the same confusable
        // peer group (e.g. ISO-8859-6 vs windows-1256) and the declared charset decodes
        // cleanly (low junk ratio), the language model cannot reliably distinguish them —
        // they both produce valid same-script text. Prefer the explicit declaration.
        String winnerDecoded = candidates.get(bestCharset);
        float winnerJunk = winnerDecoded != null ? CharSoupLanguageDetector.junkRatio(winnerDecoded) : 1f;
        if (winnerDecoded != null) {
            for (EncodingDetectorContext.Result r : context.getResults()) {
                if (r.getResultType() == EncodingResult.ResultType.DECLARATIVE
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
                    float declaredJunk = CharSoupLanguageDetector.junkRatio(declaredDecoded);
                    // Same-script peer group: language model cannot distinguish variants
                    // (e.g. ISO-8859-6 vs windows-1256 both produce valid Arabic text).
                    // Prefer the declaration when it decodes at least as cleanly as the winner.
                    if (arePeers(bestCharset, declared) && declaredJunk <= winnerJunk) {
                        context.setArbitrationInfo("scored-prefer-declared-peer");
                        return declared;
                    }
                    // DECLARATIVE result decodes cleanly and has a positive language signal:
                    // trust the declaration over the language-model winner. The language scorer
                    // can be fooled on short probes (e.g. 4 CJK code points from a wrong-endian
                    // UTF-16 decode score higher than "test" in English), but a DECLARATIVE
                    // charset that itself produces meaningful text is almost certainly correct.
                    // A lying BOM or wrong meta-tag would produce high junk (replacement chars),
                    // so the declaredJunk guard prevents false positives.
                    boolean hasDeclaredLangSignal = hasPositiveLangSignal(declaredDecoded);
                    if (declaredJunk <= winnerJunk && hasDeclaredLangSignal) {
                        context.setArbitrationInfo("scored-prefer-declared-positive-lang");
                        return declared;
                    }
                }
            }
        }

        context.setArbitrationInfo("scored");
        return bestCharset;
    }

    /**
     * Generative-model tiebreaker: for each candidate charset's decoded text,
     * detect the most likely language then compute its z-score. The charset
     * producing the highest z-score (closest to "real language") wins, provided
     * it exceeds {@link #MIN_GENERATIVE_ZSCORE}.
     *
     * @return the winning charset, or {@code null} if the generative model is
     *         unavailable or no candidate passes the threshold
     */
    private static <K> K generativeTiebreak(Map<K, String> candidates) {
        if (candidates.isEmpty()) {
            return null;
        }

        float bestZ = Float.NEGATIVE_INFINITY;
        K bestKey = null;

        for (Map.Entry<K, String> entry : candidates.entrySet()) {
            String text = entry.getValue();
            if (text == null || text.isEmpty()) {
                continue;
            }
            if (CharSoupLanguageDetector.junkRatio(text) > 0.10f) {
                continue;
            }
            Map.Entry<String, Float> match = GLM.bestMatch(text);
            if (match == null) {
                continue;
            }
            float z = GLM.zScoreLengthAdjusted(text, match.getKey());
            LOG.debug("generativeTiebreak: {} -> lang={} z={}",
                    entry.getKey(), match.getKey(), z);
            if (!Float.isNaN(z) && z > bestZ) {
                bestZ = z;
                bestKey = entry.getKey();
            }
        }

        if (bestZ < MIN_GENERATIVE_ZSCORE) {
            LOG.debug("generativeTiebreak: inconclusive (bestZ={} < {})",
                    bestZ, MIN_GENERATIVE_ZSCORE);
            return null;
        }
        return bestKey;
    }

    /**
     * Strip any leading byte-order mark from {@code bytes}, returning the
     * suffix after the BOM, or the original array if no BOM is found.
     * UTF-32 signatures are checked before UTF-16 because the UTF-32 LE BOM
     * ({@code FF FE 00 00}) starts with the UTF-16 LE BOM ({@code FF FE}).
     */
    private static byte[] stripBomBytes(byte[] bytes) {
        return bomCharsetName(bytes) != null ? Arrays.copyOfRange(bytes, bomLength(bytes), bytes.length) : bytes;
    }

    /**
     * Return the Java charset name for a leading BOM, or {@code null} if none.
     */
    static String bomCharsetName(byte[] bytes) {
        if (bytes.length >= 4
                && (bytes[0] & 0xFF) == 0x00 && (bytes[1] & 0xFF) == 0x00
                && (bytes[2] & 0xFF) == 0xFE && (bytes[3] & 0xFF) == 0xFF) {
            return "UTF-32BE";
        }
        if (bytes.length >= 4
                && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xFE
                && (bytes[2] & 0xFF) == 0x00 && (bytes[3] & 0xFF) == 0x00) {
            return "UTF-32LE";
        }
        if (bytes.length >= 3
                && (bytes[0] & 0xFF) == 0xEF && (bytes[1] & 0xFF) == 0xBB
                && (bytes[2] & 0xFF) == 0xBF) {
            return "UTF-8";
        }
        if (bytes.length >= 2
                && (bytes[0] & 0xFF) == 0xFE && (bytes[1] & 0xFF) == 0xFF) {
            return "UTF-16BE";
        }
        if (bytes.length >= 2
                && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xFE) {
            return "UTF-16LE";
        }
        return null;
    }

    private static int bomLength(byte[] bytes) {
        if (bytes.length >= 4
                && ((bytes[0] & 0xFF) == 0x00 || (bytes[0] & 0xFF) == 0xFF)
                && (bytes[2] & 0xFF) == 0x00) {
            return 4; // UTF-32
        }
        if (bytes.length >= 3 && (bytes[0] & 0xFF) == 0xEF) {
            return 3; // UTF-8
        }
        return 2; // UTF-16
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
