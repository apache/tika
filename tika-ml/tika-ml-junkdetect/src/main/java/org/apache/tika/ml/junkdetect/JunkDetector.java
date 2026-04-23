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
package org.apache.tika.ml.junkdetect;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * Language-agnostic text quality scorer.  Discriminates clean UTF-8 text from
 * mojibake, reversed text, wrong-codec decodings, and other corruption forms.
 *
 * <p>Scoring is based on a per-script byte-bigram log-probability model: a 256×256
 * table of {@code log P(b|a)} values trained on clean Wikipedia and MADLAD-400 text.
 * The per-sentence mean bigram log-prob is z-scored against the calibration statistics
 * (mean and stddev measured on held-out clean text) to produce a dimensionless quality
 * score.  Negative z-score = worse than average clean text for that script;
 * more negative = worse.
 *
 * <p>Instances are immutable and thread-safe after construction.
 *
 * <p>Typical usage:
 * <pre>{@code
 * JunkDetector detector = JunkDetector.loadFromClasspath();
 * JunkScore score = detector.score("some text");
 * if (score.getZScore() < -2.0) { ... re-OCR or flag ... }
 *
 * // Compare two charset interpretations of the same bytes
 * JunkDetector.CompareResult result = detector.compare(rawBytes, "cp1252", "cp1257");
 * String winner = result.winner();  // "A" or "B"
 * }</pre>
 */
public final class JunkDetector {

    /** Classpath resource path for the bundled production model. */
    public static final String DEFAULT_MODEL_RESOURCE =
            "org/apache/tika/ml/junkdetect/junkdetect.bin";

    static final String MAGIC = "JUNKDET1";

    // Per-script model data
    private final Map<String, float[]> tables;       // script → float[65536] log-prob table
    private final Map<String, float[]> calibrations; // script → float[2] {mu, sigma}

    private JunkDetector(Map<String, float[]> tables, Map<String, float[]> calibrations) {
        this.tables = Collections.unmodifiableMap(tables);
        this.calibrations = Collections.unmodifiableMap(calibrations);
    }

    // -----------------------------------------------------------------------
    // Factory methods
    // -----------------------------------------------------------------------

    /**
     * Loads the bundled model from the classpath.
     *
     * @throws IOException if the model resource is missing or malformed
     */
    public static JunkDetector loadFromClasspath() throws IOException {
        InputStream is = JunkDetector.class.getClassLoader()
                .getResourceAsStream(DEFAULT_MODEL_RESOURCE);
        if (is == null) {
            throw new IOException("Model resource not found on classpath: "
                    + DEFAULT_MODEL_RESOURCE);
        }
        try (InputStream wrapped = is) {
            return load(wrapped);
        }
    }

    /**
     * Loads a model from the given file path.  The file may be gzipped or raw.
     */
    public static JunkDetector loadFromPath(Path path) throws IOException {
        try (InputStream is = Files.newInputStream(path)) {
            return load(is);
        }
    }

    /**
     * Loads a model from an {@link InputStream}.  Gzip-detection is automatic.
     */
    public static JunkDetector load(InputStream rawIs) throws IOException {
        // Peek to detect gzip magic
        InputStream is = rawIs.markSupported() ? rawIs : rawIs; // already have stream
        // Wrap in buffered so we can read the first bytes; rely on GZIPInputStream magic
        InputStream in;
        byte[] peek = rawIs.readNBytes(2);
        InputStream rest = new java.io.SequenceInputStream(
                new java.io.ByteArrayInputStream(peek), rawIs);
        if (peek.length >= 2 && (peek[0] & 0xFF) == 0x1f && (peek[1] & 0xFF) == 0x8b) {
            in = new GZIPInputStream(rest);
        } else {
            in = rest;
        }

        try (DataInputStream dis = new DataInputStream(in)) {
            // Verify magic
            byte[] magic = dis.readNBytes(8);
            if (!new String(magic, StandardCharsets.UTF_8).equals(MAGIC)) {
                throw new IOException("Not a JunkDetector model file (bad magic)");
            }
            int version = dis.readUnsignedByte();
            if (version != 1) {
                throw new IOException("Unsupported model version: " + version);
            }

            int numScripts = dis.readInt();
            Map<String, float[]> tables = new HashMap<>(numScripts * 2);
            Map<String, float[]> calibrations = new HashMap<>(numScripts * 2);

            for (int s = 0; s < numScripts; s++) {
                int nameLen = dis.readUnsignedShort();
                String script = new String(dis.readNBytes(nameLen), StandardCharsets.UTF_8);

                float mu = dis.readFloat();
                float sigma = dis.readFloat();
                calibrations.put(script, new float[]{mu, sigma});

                byte[] tableBytes = dis.readNBytes(65536 * 4);
                float[] table = new float[65536];
                ByteBuffer buf = ByteBuffer.wrap(tableBytes).order(ByteOrder.BIG_ENDIAN);
                buf.asFloatBuffer().get(table);
                tables.put(script, table);
            }

            return new JunkDetector(tables, calibrations);
        }
    }

    // -----------------------------------------------------------------------
    // Scoring API
    // -----------------------------------------------------------------------

    /**
     * Scores a UTF-8 string for text quality.
     *
     * @param text the string to score (will be encoded to UTF-8 internally)
     * @return a {@link JunkScore}; use {@link JunkScore#isUnknown()} to check
     *         whether scoring was possible
     */
    public JunkScore score(String text) {
        if (text == null || text.isEmpty()) {
            return unknownScore("UNKNOWN");
        }
        return scoreBytes(text.getBytes(StandardCharsets.UTF_8), text);
    }

    /**
     * Scores a byte array assumed to be UTF-8 text.
     *
     * @param utf8 raw UTF-8 bytes
     * @return a {@link JunkScore}
     */
    public JunkScore score(byte[] utf8) {
        if (utf8 == null || utf8.length == 0) {
            return unknownScore("UNKNOWN");
        }
        String text = new String(utf8, StandardCharsets.UTF_8);
        return scoreBytes(utf8, text);
    }

    /**
     * Compares two charset interpretations of the same raw bytes and returns
     * which decoding scores higher (is more likely to be clean natural language).
     *
     * @param rawBytes the raw bytes to decode
     * @param charsetA first charset name (e.g. {@code "cp1252"})
     * @param charsetB second charset name (e.g. {@code "cp1257"})
     * @return a {@link CompareResult} indicating the winner and confidence
     */
    public CompareResult compare(byte[] rawBytes, String charsetA, String charsetB) {
        JunkScore scoreA = decodeAndScore(rawBytes, charsetA);
        JunkScore scoreB = decodeAndScore(rawBytes, charsetB);

        float zA = scoreA.isUnknown() ? Float.NEGATIVE_INFINITY : scoreA.getZScore();
        float zB = scoreB.isUnknown() ? Float.NEGATIVE_INFINITY : scoreB.getZScore();

        String winner = zA >= zB ? "A" : "B";
        float delta = Math.abs(zA - zB);

        return new CompareResult(winner, delta, scoreA, scoreB, charsetA, charsetB);
    }

    /** Returns the set of script names this model knows about. */
    public java.util.Set<String> knownScripts() {
        return tables.keySet();
    }

    // -----------------------------------------------------------------------
    // Internal scoring
    // -----------------------------------------------------------------------

    private JunkScore scoreBytes(byte[] utf8, String text) {
        String script = detectDominantScript(text);

        float[] table = tables.get(script);
        if (table == null) {
            // Script not in model — return unknown with script name for diagnostics
            return unknownScore(script);
        }

        if (utf8.length < 2) {
            return unknownScore(script);
        }

        // Mean byte-bigram log-prob
        double sum = 0;
        int count = 0;
        for (int i = 0; i + 1 < utf8.length; i++) {
            sum += table[((utf8[i] & 0xFF) << 8) | (utf8[i + 1] & 0xFF)];
            count++;
        }
        float meanLogProb = (float) (sum / count);

        // Z-score against calibration
        float[] cal = calibrations.get(script);
        float mu = cal[0];
        float sigma = cal[1];
        float zScore = (meanLogProb - mu) / sigma;

        // Confidence interval: uncertainty ~ 1.96 * sigma / sqrt(count)
        float uncertainty = (float) (1.96 * sigma / Math.sqrt(count));
        float ciLow = zScore - uncertainty;
        float ciHigh = zScore + uncertainty;

        // P(clean): sigmoid of z-score (simple calibration-free estimate)
        float pClean = (float) (1.0 / (1.0 + Math.exp(-zScore)));

        return new JunkScore(zScore, pClean, ciLow, ciHigh, script);
    }

    private JunkScore decodeAndScore(byte[] raw, String charsetName) {
        try {
            Charset cs = Charset.forName(charsetName);
            byte[] utf8 = new String(raw, cs).getBytes(StandardCharsets.UTF_8);
            return score(utf8);
        } catch (Exception e) {
            return unknownScore(charsetName);
        }
    }

    private static JunkScore unknownScore(String script) {
        return new JunkScore(JunkScore.UNKNOWN, Float.NaN, Float.NaN, Float.NaN, script);
    }

    /**
     * Detects the dominant Unicode script of the given text by histogramming
     * {@link Character.UnicodeScript} over all codepoints, excluding COMMON,
     * INHERITED, and UNKNOWN pseudo-scripts.  Returns "LATIN" for ASCII-only
     * text (no non-ASCII codepoints).
     */
    static String detectDominantScript(String text) {
        Map<Character.UnicodeScript, Integer> counts = new HashMap<>();
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            Character.UnicodeScript s = Character.UnicodeScript.of(cp);
            if (s != Character.UnicodeScript.COMMON
                    && s != Character.UnicodeScript.INHERITED
                    && s != Character.UnicodeScript.UNKNOWN) {
                counts.merge(s, 1, Integer::sum);
            }
            i += Character.charCount(cp);
        }
        if (counts.isEmpty()) {
            return "LATIN"; // ASCII-only → use Latin model
        }
        return counts.entrySet().stream()
                .max(java.util.Map.Entry.comparingByValue())
                .map(e -> e.getKey().name())
                .orElse("LATIN");
    }

    // -----------------------------------------------------------------------
    // Result type for compare()
    // -----------------------------------------------------------------------

    /**
     * Result of comparing two charset decodings of the same raw bytes.
     */
    public static final class CompareResult {
        private final String winner;
        private final float delta;
        private final JunkScore scoreA;
        private final JunkScore scoreB;
        private final String charsetA;
        private final String charsetB;

        CompareResult(String winner, float delta,
                      JunkScore scoreA, JunkScore scoreB,
                      String charsetA, String charsetB) {
            this.winner = winner;
            this.delta = delta;
            this.scoreA = scoreA;
            this.scoreB = scoreB;
            this.charsetA = charsetA;
            this.charsetB = charsetB;
        }

        /** "A" if charsetA decodes to cleaner text, "B" otherwise. */
        public String winner() {
            return winner;
        }

        /**
         * Absolute difference in z-scores.  Small delta = uncertain; large delta = confident.
         * As a rough guide: delta > 1.0 is useful signal, delta > 3.0 is confident.
         */
        public float delta() {
            return delta;
        }

        public JunkScore scoreA() {
            return scoreA;
        }

        public JunkScore scoreB() {
            return scoreB;
        }

        public String charsetA() {
            return charsetA;
        }

        public String charsetB() {
            return charsetB;
        }

        @Override
        public String toString() {
            return String.format("CompareResult[winner=%s(%s) delta=%.3f A=%s B=%s]",
                    winner, winner.equals("A") ? charsetA : charsetB,
                    delta, scoreA, scoreB);
        }
    }
}
