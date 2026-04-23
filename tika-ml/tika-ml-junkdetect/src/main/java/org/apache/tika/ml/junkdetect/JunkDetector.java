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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;

import org.apache.tika.quality.TextQualityComparison;
import org.apache.tika.quality.TextQualityDetector;
import org.apache.tika.quality.TextQualityScore;

/**
 * Language-agnostic text quality scorer.  Discriminates clean UTF-8 text from
 * mojibake, reversed text, wrong-codec decodings, and other corruption forms.
 *
 * <p>Scoring combines up to three features, depending on the model version:
 * <ol>
 *   <li><b>Byte-bigram log-probability</b> — 256×256 table of log P(b|a) over
 *       consecutive byte pairs in the UTF-8 encoding.</li>
 *   <li><b>Unicode named-block transition log-probability</b> (version 2+) —
 *       N×N table of log P(block_b | block_a) where block IDs are the named
 *       {@link Character.UnicodeBlock} values (BASIC_LATIN, ARABIC,
 *       CJK_UNIFIED_IDEOGRAPHS, etc.).</li>
 *   <li><b>Control-byte fraction</b> (version 2+) — fraction of bytes in control
 *       ranges [0x01–0x08, 0x0B, 0x0C, 0x0E–0x1F, 0x7F].</li>
 * </ol>
 *
 * <p>All features are calibrated (mu/sigma) on held-out dev text so their z-scores
 * are on a common scale.
 *
 * <ul>
 *   <li><b>Version 1</b>: bigrams only; z-score = z1.</li>
 *   <li><b>Version 2</b>: equal-weight average: {@code (z1 + z2 + z3) / 3}.</li>
 *   <li><b>Version 3</b>: per-script learned linear combination:
 *       {@code w1*z1 + w2*z2 + w3*z3 + bias}, where weights are fit by logistic
 *       regression on clean vs. corrupted dev windows.  The natural junk threshold
 *       is 0 (positive logit = clean); use a negative threshold for conservative
 *       detection (e.g., {@code score < -1}).</li>
 * </ul>
 *
 * <p>Instances are immutable and thread-safe after construction.
 *
 * <p>Typical usage:
 * <pre>{@code
 * JunkDetector detector = JunkDetector.loadFromClasspath();
 * TextQualityScore score = detector.score("some text");
 * if (score.getZScore() < 0) { ... flag as junk ... }
 *
 * // Arbitrate between two charset decodings
 * TextQualityComparison result = detector.compare("cp1252", ascp1252, "cp1251", ascp1251);
 * String winner = result.winner();  // "A" or "B"
 * }</pre>
 */
public final class JunkDetector implements TextQualityDetector {

    /** Classpath resource path for the bundled production model. */
    public static final String DEFAULT_MODEL_RESOURCE =
            "org/apache/tika/ml/junkdetect/junkdetect.bin";

    static final String MAGIC = "JUNKDET1";

    private final int modelVersion;

    // Feature 1: byte bigrams (all versions)
    private final Map<String, float[]> tables;       // script → float[65536] log-prob
    private final Map<String, float[]> calibrations; // script → float[2] {mu, sigma}

    // Feature 2: named-block transitions (version 2+); null for v1 models
    private final Map<String, float[]> blockTables;         // script → float[blockN*blockN]
    private final Map<String, float[]> blockCalibrations;   // script → float[2] {mu, sigma}
    private final int blockN;                               // block table dimension (0 for v1)

    // Feature 3: control-byte fraction (version 2+); null for v1 models
    private final Map<String, float[]> controlCalibrations; // script → float[2] {mu, sigma}

    // Feature combination: per-script linear classifier (version 3+); null for v1/v2 models
    // float[numFeatures+1] = {w1, ..., wN, bias}; positive logit = clean
    private final Map<String, float[]> classifierWeights;

    // Shared block index for v2+ models: UnicodeBlock → index [0, blockN-1)
    // Index blockN-1 is the "unassigned" bucket (null UnicodeBlock).
    private final Map<Character.UnicodeBlock, Integer> blockIndex;

    private JunkDetector(int modelVersion,
                         Map<String, float[]> tables,
                         Map<String, float[]> calibrations,
                         Map<String, float[]> blockTables,
                         Map<String, float[]> blockCalibrations,
                         int blockN,
                         Map<String, float[]> controlCalibrations,
                         Map<String, float[]> classifierWeights,
                         Map<Character.UnicodeBlock, Integer> blockIndex) {
        this.modelVersion = modelVersion;
        this.tables = Collections.unmodifiableMap(tables);
        this.calibrations = Collections.unmodifiableMap(calibrations);
        this.blockTables = blockTables != null
                ? Collections.unmodifiableMap(blockTables) : null;
        this.blockCalibrations = blockCalibrations != null
                ? Collections.unmodifiableMap(blockCalibrations) : null;
        this.blockN = blockN;
        this.controlCalibrations = controlCalibrations != null
                ? Collections.unmodifiableMap(controlCalibrations) : null;
        this.classifierWeights = classifierWeights != null
                ? Collections.unmodifiableMap(classifierWeights) : null;
        this.blockIndex = blockIndex;
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
     * Supports model versions 1, 2, and 3.
     */
    public static JunkDetector load(InputStream rawIs) throws IOException {
        byte[] peek = rawIs.readNBytes(2);
        InputStream rest = new java.io.SequenceInputStream(
                new java.io.ByteArrayInputStream(peek), rawIs);
        InputStream in;
        if (peek.length >= 2 && (peek[0] & 0xFF) == 0x1f && (peek[1] & 0xFF) == 0x8b) {
            in = new GZIPInputStream(rest);
        } else {
            in = rest;
        }

        try (DataInputStream dis = new DataInputStream(in)) {
            byte[] magic = dis.readNBytes(8);
            if (!new String(magic, StandardCharsets.UTF_8).equals(MAGIC)) {
                throw new IOException("Not a JunkDetector model file (bad magic)");
            }
            int version = dis.readUnsignedByte();
            if (version < 1 || version > 3) {
                throw new IOException("Unsupported model version: " + version);
            }

            int numScripts = dis.readInt();

            // Version 2+: read global block table dimension
            int blockN = 0;
            Map<Character.UnicodeBlock, Integer> blockIndex = null;
            if (version >= 2) {
                blockN = dis.readUnsignedShort();
                blockIndex = buildBlockIndex();
                int expectedN = blockIndex.size() + 1;
                if (blockN != expectedN) {
                    throw new IOException(String.format(
                            "Block table dimension mismatch: model has %d but JVM gives %d. "
                            + "Model was trained with a different Java version.", blockN, expectedN));
                }
            }

            Map<String, float[]> tables       = new HashMap<>(numScripts * 2);
            Map<String, float[]> calibrations = new HashMap<>(numScripts * 2);

            Map<String, float[]> blockTables         = version >= 2 ? new HashMap<>(numScripts * 2) : null;
            Map<String, float[]> blockCalibrations   = version >= 2 ? new HashMap<>(numScripts * 2) : null;
            Map<String, float[]> controlCalibrations = version >= 2 ? new HashMap<>(numScripts * 2) : null;
            Map<String, float[]> classifierWeights   = version >= 3 ? new HashMap<>(numScripts * 2) : null;

            for (int s = 0; s < numScripts; s++) {
                int nameLen = dis.readUnsignedShort();
                String script = new String(dis.readNBytes(nameLen), StandardCharsets.UTF_8);

                // Feature 1: byte bigrams
                float mu1 = dis.readFloat();
                float sigma1 = dis.readFloat();
                calibrations.put(script, new float[]{mu1, sigma1});
                tables.put(script, readFloatTable(dis, 65536));

                if (version >= 2) {
                    // Feature 2: named-block transitions
                    float mu2 = dis.readFloat();
                    float sigma2 = dis.readFloat();
                    blockCalibrations.put(script, new float[]{mu2, sigma2});
                    blockTables.put(script, readFloatTable(dis, blockN * blockN));

                    // Feature 3: control-byte fraction
                    float mu3 = dis.readFloat();
                    float sigma3 = dis.readFloat();
                    controlCalibrations.put(script, new float[]{mu3, sigma3});

                    if (version >= 3) {
                        // Classifier weights: num_features (1 byte) + num_features floats + 1 bias
                        int numFeatures = dis.readUnsignedByte();
                        float[] weights = new float[numFeatures + 1]; // last = bias
                        for (int j = 0; j <= numFeatures; j++) {
                            weights[j] = dis.readFloat();
                        }
                        classifierWeights.put(script, weights);
                    }
                }
            }

            return new JunkDetector(version, tables, calibrations,
                    blockTables, blockCalibrations, blockN,
                    controlCalibrations, classifierWeights, blockIndex);
        }
    }

    private static float[] readFloatTable(DataInputStream dis, int size) throws IOException {
        byte[] tableBytes = dis.readNBytes(size * 4);
        float[] table = new float[size];
        ByteBuffer buf = ByteBuffer.wrap(tableBytes).order(ByteOrder.BIG_ENDIAN);
        buf.asFloatBuffer().get(table);
        return table;
    }

    /**
     * Builds the stable ordered mapping from {@link Character.UnicodeBlock} to index.
     * This must produce the same ordering as {@link TrainJunkModel#buildBlockIndex()}.
     */
    static Map<Character.UnicodeBlock, Integer> buildBlockIndex() {
        LinkedHashMap<Character.UnicodeBlock, Integer> index = new LinkedHashMap<>();
        for (int cp = 0; cp <= 0x10FFFF; cp++) {
            Character.UnicodeBlock b = Character.UnicodeBlock.of(cp);
            if (b != null) index.putIfAbsent(b, index.size());
        }
        return Collections.unmodifiableMap(index);
    }

    // -----------------------------------------------------------------------
    // TextQualityDetector implementation
    // -----------------------------------------------------------------------

    /**
     * {@inheritDoc}
     *
     * <p>The string is encoded to UTF-8 internally for bigram and control-byte scoring.
     * Codepoints are used directly for block-transition scoring.
     */
    @Override
    public TextQualityScore score(String text) {
        if (text == null || text.isEmpty()) {
            return unknownScore("UNKNOWN");
        }
        return scoreText(text.getBytes(StandardCharsets.UTF_8), text);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Each candidate is scored independently via {@link #score(String)}.
     * The candidate with the higher score wins.
     *
     * <p>An UNKNOWN score (script not in model) is treated as neutral (0) rather
     * than {@code -∞}.  This prevents a garbled-but-recognisable decoding from
     * beating a correct decoding whose script happens to be unknown to the model —
     * for example, a pure-katakana zip entry name decoded as Shift-JIS (UNKNOWN)
     * vs. the same bytes decoded as UTF-8 (garbled LATIN, negative z-score).
     */
    @Override
    public TextQualityComparison compare(String labelA, String candidateA,
                                         String labelB, String candidateB) {
        TextQualityScore scoreA = score(candidateA);
        TextQualityScore scoreB = score(candidateB);

        // UNKNOWN = "no evidence" = 0, not -∞.  A text whose script is not in the
        // model is assumed to be neutral, not junk.
        float zA = scoreA.isUnknown() ? 0f : scoreA.getZScore();
        float zB = scoreB.isUnknown() ? 0f : scoreB.getZScore();

        String winner = zA >= zB ? "A" : "B";
        float delta = Math.abs(zA - zB);

        return new TextQualityComparison(winner, delta, scoreA, scoreB, labelA, labelB);
    }

    /** Returns the set of script names this model knows about. */
    public Set<String> knownScripts() {
        return tables.keySet();
    }

    /** Returns the version of the loaded model (1, 2, or 3). */
    public int getModelVersion() {
        return modelVersion;
    }

    // -----------------------------------------------------------------------
    // Internal scoring
    // -----------------------------------------------------------------------

    private TextQualityScore scoreText(byte[] utf8, String text) {
        String script = detectDominantScript(text);

        float[] bigramTable = tables.get(script);
        if (bigramTable == null) {
            return unknownScore(script);
        }
        if (utf8.length < 2) {
            return unknownScore(script);
        }

        // Feature 1: byte-bigram mean log-prob
        double bigramSum = 0;
        int bigramCount = 0;
        for (int i = 0; i + 1 < utf8.length; i++) {
            bigramSum += bigramTable[((utf8[i] & 0xFF) << 8) | (utf8[i + 1] & 0xFF)];
            bigramCount++;
        }
        float meanBigramLogProb = (float) (bigramSum / bigramCount);
        float[] cal1 = calibrations.get(script);
        float z1 = (meanBigramLogProb - cal1[0]) / cal1[1];

        // Features 2 & 3 (version 2+)
        float z2 = 0f, z3 = 0f;
        if (modelVersion >= 2 && blockTables != null) {
            // Feature 2: named-block transition mean log-prob
            float[] blockTable = blockTables.get(script);
            if (blockTable != null) {
                int nullId = blockN - 1;
                int prev = -1;
                double blockSum = 0;
                int blockCount = 0;
                for (int i = 0; i < text.length(); ) {
                    int cp = text.codePointAt(i);
                    Character.UnicodeBlock b = Character.UnicodeBlock.of(cp);
                    int blockId = b != null ? blockIndex.getOrDefault(b, nullId) : nullId;
                    if (prev >= 0) {
                        blockSum += blockTable[prev * blockN + blockId];
                        blockCount++;
                    }
                    prev = blockId;
                    i += Character.charCount(cp);
                }
                if (blockCount > 0) {
                    float meanBlockLogProb = (float) (blockSum / blockCount);
                    float[] cal2 = blockCalibrations.get(script);
                    z2 = cal2 != null ? (meanBlockLogProb - cal2[0]) / cal2[1] : 0f;
                }
            }

            // Feature 3: control-byte fraction (stored as −fraction, so higher = cleaner)
            long controlCount = 0;
            for (byte b : utf8) {
                if (isControlByte(b & 0xFF)) controlCount++;
            }
            float controlScore = -(float) controlCount / utf8.length;
            float[] cal3 = controlCalibrations.get(script);
            z3 = cal3 != null ? (controlScore - cal3[0]) / cal3[1] : 0f;
        }

        // Combine features
        float zScore;
        if (modelVersion >= 3 && classifierWeights != null) {
            // Version 3: per-script linear combination (logistic regression weights)
            float[] cw = classifierWeights.get(script);
            if (cw != null && cw.length >= 4) {
                // cw = {w1, w2, w3, bias}; positive logit = clean
                zScore = cw[0] * z1 + cw[1] * z2 + cw[2] * z3 + cw[cw.length - 1];
            } else {
                zScore = (z1 + z2 + z3) / 3.0f; // fallback if weights missing
            }
        } else if (modelVersion >= 2 && blockTables != null) {
            // Version 2: equal-weight average
            zScore = (z1 + z2 + z3) / 3.0f;
        } else {
            // Version 1: bigrams only
            zScore = z1;
        }

        // CI is approximated from the bigram count and bigram sigma
        float uncertainty = (float) (1.96 * cal1[1] / Math.sqrt(bigramCount));
        float ciLow = zScore - uncertainty;
        float ciHigh = zScore + uncertainty;

        float pClean = (float) (1.0 / (1.0 + Math.exp(-zScore)));

        return new TextQualityScore(zScore, pClean, ciLow, ciHigh, script);
    }

    /**
     * Returns true if the byte value is a control character that should not appear
     * in natural-language UTF-8 text: {@code [0x01–0x08, 0x0B, 0x0C, 0x0E–0x1F, 0x7F]}.
     *
     * <p>Excluded: 0x00 (null), 0x09 (tab), 0x0A (newline), 0x0D (carriage return)
     * — all appear legitimately in text.
     */
    static boolean isControlByte(int b) {
        return (b >= 0x01 && b <= 0x08)
                || b == 0x0B || b == 0x0C
                || (b >= 0x0E && b <= 0x1F)
                || b == 0x7F;
    }

    private static TextQualityScore unknownScore(String script) {
        return new TextQualityScore(TextQualityScore.UNKNOWN, Float.NaN,
                Float.NaN, Float.NaN, script);
    }

    /**
     * Maps Unicode scripts that share a trained model with a related script.
     * Japanese kana (HIRAGANA, KATAKANA) map to HAN because the HAN model is
     * trained on mixed Japanese text containing all three writing systems, so
     * its byte-bigram and block-transition tables cover kana sequences.
     */
    private static final Map<String, String> SCRIPT_MODEL_FALLBACK = Map.of(
            "HIRAGANA", "HAN",
            "KATAKANA", "HAN"
    );

    /**
     * Detects the dominant Unicode script of the given text by histogramming
     * {@link Character.UnicodeScript} over all codepoints, excluding COMMON,
     * INHERITED, and UNKNOWN pseudo-scripts.  Returns "LATIN" for ASCII-only text.
     *
     * <p>Script names are mapped through {@link #SCRIPT_MODEL_FALLBACK} so that
     * scripts without dedicated models fall back to a related trained model
     * (e.g. KATAKANA and HIRAGANA both use the HAN model).
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
            return "LATIN";
        }
        String name = counts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(e -> e.getKey().name())
                .orElse("LATIN");
        return SCRIPT_MODEL_FALLBACK.getOrDefault(name, name);
    }
}
