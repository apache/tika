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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
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
 * <p>Scoring combines four features:
 * <ol>
 *   <li><b>Codepoint-bigram log-probability (F1)</b> — global hashed table
 *       indexed by FNV-1a(cp_a, cp_b, seed) into {@code bigramBuckets} cells.
 *       A Bloom filter records seen pairs; unseen pairs fall back to a
 *       hashed-unigram independence-assumption score
 *       {@code α * (log P(cp_a) + log P(cp_b))}.</li>
 *   <li><b>Unicode named-block transition log-probability (F2)</b> —
 *       per-script N×N table over {@link Character.UnicodeBlock} values.</li>
 *   <li><b>Control-byte fraction (F3)</b> — fraction of bytes in control
 *       ranges [0x01–0x08, 0x0B, 0x0C, 0x0E–0x1F, 0x7F].</li>
 *   <li><b>Global script-transition log-probability (F4)</b> — single
 *       transition table over raw {@link Character.UnicodeScript} values,
 *       capturing document-level cross-script anomalies.</li>
 * </ol>
 *
 * <p>All features are calibrated per-script (mu/sigma) on held-out dev text
 * so their z-scores are on a common scale.  z-scores are combined by a
 * per-script linear classifier:
 * {@code logit = w1*z1 + w2*z2 + w3*z3 + w4*z4 + bias}, where weights are
 * fit on clean vs. corrupted dev windows.  Natural junk threshold is 0
 * (positive logit = clean); use negative thresholds for conservative
 * detection.</p>
 *
 * <p>Model file format: a single binary spec (see {@link #load(InputStream)}
 * javadoc).  No backwards-compat fallback to older formats — the loader
 * rejects mismatched version bytes with a clear error.  This is
 * intentional: keeping parallel scoring paths is a known source of silent
 * miscalibration bugs.
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
 * String winner = result.winner();  // returns "cp1252" or "cp1251"
 * }</pre>
 */
public final class JunkDetector implements TextQualityDetector {

    /** Classpath resource path for the bundled production model. */
    public static final String DEFAULT_MODEL_RESOURCE =
            "org/apache/tika/ml/junkdetect/junkdetect.bin";

    static final String MAGIC = "JUNKDET1";
    /** Sole supported file-format version.  Mismatch is a hard error. */
    static final int VERSION = 7;

    // Feature 1 — per-script open-addressed codepoint-bigram tables.
    // No global Bloom: empty-slot is the membership oracle.
    private final Map<String, V7Tables> f1TablesByScript;

    /** Per-script F1 calibration on the codepoint-hash mean log-prob. */
    private final Map<String, float[]> calibrations; // script → float[2] {mu, sigma}

    // Feature 2 — per-script block transition.  Block bucketing uses the
    // JVM-independent {@link UnicodeBlockRanges} static table; table size
    // per script is {@code bucketCount()²} floats.
    private final Map<String, float[]> blockTables;
    private final Map<String, float[]> blockCalibrations;

    // Feature 3 — per-script control-byte fraction calibration
    private final Map<String, float[]> controlCalibrations;

    // Feature 4 — single global script-transition table
    private final float[] scriptTransitionTable;
    private final float[] scriptTransitionCalibration;
    private final Map<String, Integer> scriptBucketIndex;
    private final int numScriptBuckets;

    // Per-script linear classifier: float[numFeatures+1] = {w1, ..., wN, bias}.
    private final Map<String, float[]> classifierWeights;

    private JunkDetector(Map<String, float[]> calibrations,
                         Map<String, float[]> blockTables,
                         Map<String, float[]> blockCalibrations,
                         Map<String, float[]> controlCalibrations,
                         Map<String, float[]> classifierWeights,
                         float[] scriptTransitionTable,
                         float[] scriptTransitionCalibration,
                         Map<String, Integer> scriptBucketIndex,
                         int numScriptBuckets,
                         Map<String, V7Tables> f1TablesByScript) {
        this.calibrations = Collections.unmodifiableMap(calibrations);
        this.blockTables = Collections.unmodifiableMap(blockTables);
        this.blockCalibrations = Collections.unmodifiableMap(blockCalibrations);
        this.controlCalibrations = Collections.unmodifiableMap(controlCalibrations);
        this.classifierWeights = Collections.unmodifiableMap(classifierWeights);
        this.scriptTransitionTable = scriptTransitionTable;
        this.scriptTransitionCalibration = scriptTransitionCalibration;
        this.scriptBucketIndex = Collections.unmodifiableMap(scriptBucketIndex);
        this.numScriptBuckets = numScriptBuckets;
        this.f1TablesByScript = Collections.unmodifiableMap(f1TablesByScript);
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
     * {@link java.util.ServiceLoader} provider hook (Java 9+).  Allows
     * {@code JunkDetector} to be registered as a
     * {@link org.apache.tika.quality.TextQualityDetector} SPI implementation
     * even though its construction goes through
     * {@link #loadFromClasspath()} rather than a public no-arg constructor.
     *
     * @throws UncheckedIOException if the bundled model cannot be loaded
     */
    public static JunkDetector provider() {
        try {
            return loadFromClasspath();
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(
                    "Failed to load bundled JunkDetector model", e);
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
     * Strictly requires the current file-format version ({@value #VERSION}) —
     * older formats are rejected with a clear error rather than supported
     * via a fallback path.
     *
     * <p>File-format layout (gzipped):
     * <pre>
     *   [8 bytes]    magic "JUNKDET1" (ASCII)
     *   [1 byte]     version (= 7)
     *   [4 bytes]    num_scripts (int BE)
     *   [1 byte]     block_scheme_version  (must equal
     *                {@link UnicodeBlockRanges#SCHEME_VERSION})
     *   [1 byte]     num_script_buckets
     *   for each bucket:
     *     [2 bytes]      name length (ushort BE)
     *     [name bytes]   bucket name (UTF-8)
     *   [num_script_buckets² × 4 bytes]  script-transition log-prob table (F4)
     *   [4 bytes]    mu4 (float32 BE)
     *   [4 bytes]    sigma4 (float32 BE)
     *   for each script (sorted by name):
     *     [2 bytes]      name length
     *     [name bytes]   script name (UTF-8)
     *     [4 bytes]      mu1 (F1 calibration, codepoint-bigram mean log-prob)
     *     [4 bytes]      sigma1
     *     // V7 F1 tables for this script — see {@link V7Tables#writeTo}
     *     [4 bytes]      backoff_alpha (float32 BE)
     *     [4 bytes]      codepoint_count
     *     [codepoint_count × 4 bytes]  codepoint index (sorted, ascending)
     *     [4 bytes]      bigram_slots (power of 2)
     *     [4 bytes]      bigram_quant_min (float32 BE)
     *     [4 bytes]      bigram_quant_max (float32 BE)
     *     [bigram_slots × 4 bytes]  bigram open-addressing keys
     *                                ((idxA<<16)|idxB, or {@link V7Tables#EMPTY_KEY})
     *     [bigram_slots bytes]      bigram values (8-bit quantized log-probs)
     *     [4 bytes]      unigram_quant_min (float32 BE)
     *     [4 bytes]      unigram_quant_max (float32 BE)
     *     [4 bytes]      unigram_fallback_log_prob (float32 BE; used for
     *                                                codepoints not in index)
     *     [codepoint_count bytes]   unigram values (8-bit quantized log-probs)
     *     // F2/F3/classifier (unchanged from v6 layout)
     *     [4 bytes]      mu2 (F2 calibration)
     *     [4 bytes]      sigma2
     *     [block_N² × 4 bytes]  block-transition log-prob table (F2)
     *     [4 bytes]      mu3 (F3 calibration)
     *     [4 bytes]      sigma3
     *     [1 byte]       num_features
     *     [(num_features+1) × 4 bytes]  classifier weights w1..wN and bias
     * </pre>
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
            if (version != VERSION) {
                throw new IOException("Unsupported model format version: " + version
                        + ". This build expects version " + VERSION
                        + ".  Retrain the model with the current TrainJunkModel.");
            }

            int numScripts = dis.readInt();

            int blockSchemeVersion = dis.readUnsignedByte();
            if (blockSchemeVersion != UnicodeBlockRanges.SCHEME_VERSION) {
                throw new IOException("Unsupported block-scheme version: "
                        + blockSchemeVersion + ". This build expects "
                        + UnicodeBlockRanges.SCHEME_VERSION
                        + ".  Retrain with the current TrainJunkModel.");
            }
            int blockN = UnicodeBlockRanges.bucketCount();

            // Global script-transition section
            int numScriptBuckets = dis.readUnsignedByte();
            Map<String, Integer> scriptBucketIndex = new LinkedHashMap<>(numScriptBuckets * 2);
            for (int i = 0; i < numScriptBuckets; i++) {
                int nameLen = dis.readUnsignedShort();
                String bucketName = new String(dis.readNBytes(nameLen), StandardCharsets.UTF_8);
                scriptBucketIndex.put(bucketName, i);
            }
            float[] scriptTransitionTable = readFloatTable(dis, numScriptBuckets * numScriptBuckets);
            float[] scriptTransitionCalibration = new float[]{dis.readFloat(), dis.readFloat()};

            Map<String, V7Tables>  f1TablesByScript   = new HashMap<>(numScripts * 2);
            Map<String, float[]>   calibrations       = new HashMap<>(numScripts * 2);
            Map<String, float[]>   blockTables        = new HashMap<>(numScripts * 2);
            Map<String, float[]>   blockCalibrations  = new HashMap<>(numScripts * 2);
            Map<String, float[]>   controlCalibrations = new HashMap<>(numScripts * 2);
            Map<String, float[]>   classifierWeights  = new HashMap<>(numScripts * 2);

            for (int s = 0; s < numScripts; s++) {
                int nameLen = dis.readUnsignedShort();
                String script = new String(dis.readNBytes(nameLen), StandardCharsets.UTF_8);

                calibrations.put(script, new float[]{dis.readFloat(), dis.readFloat()});

                // Per-script V7 F1 tables.
                f1TablesByScript.put(script, V7Tables.readFrom(dis));

                blockCalibrations.put(script, new float[]{dis.readFloat(), dis.readFloat()});
                blockTables.put(script, readFloatTable(dis, blockN * blockN));
                controlCalibrations.put(script, new float[]{dis.readFloat(), dis.readFloat()});

                int numFeatures = dis.readUnsignedByte();
                float[] weights = new float[numFeatures + 1];
                for (int j = 0; j <= numFeatures; j++) {
                    weights[j] = dis.readFloat();
                }
                classifierWeights.put(script, weights);
            }

            return new JunkDetector(calibrations,
                    blockTables, blockCalibrations,
                    controlCalibrations, classifierWeights,
                    scriptTransitionTable, scriptTransitionCalibration,
                    scriptBucketIndex, numScriptBuckets, f1TablesByScript);
        }
    }

    private static float[] readFloatTable(DataInputStream dis, int size) throws IOException {
        byte[] tableBytes = dis.readNBytes(size * 4);
        float[] table = new float[size];
        ByteBuffer buf = ByteBuffer.wrap(tableBytes).order(ByteOrder.BIG_ENDIAN);
        buf.asFloatBuffer().get(table);
        return table;
    }


    // -----------------------------------------------------------------------
    // TextQualityDetector implementation
    // -----------------------------------------------------------------------

    /**
     * {@inheritDoc}
     *
     * <p>The text is split into contiguous runs of the same Unicode script.
     * Each run is scored against its own script model.  Logits are combined
     * as a byte-count-weighted average, so mixed-script text (e.g. half
     * LATIN, half HAN) is scored fairly without arbitrarily picking one script.
     * COMMON, INHERITED, and UNKNOWN codepoints (spaces, punctuation, digits)
     * are attached to the preceding script run.
     */
    @Override
    public TextQualityScore score(String text) {
        if (text == null || text.isEmpty()) {
            return unknownScore("UNKNOWN");
        }
        return scoreText(text);
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

        String winner = zA >= zB ? labelA : labelB;
        float delta = Math.abs(zA - zB);

        return new TextQualityComparison(winner, delta, scoreA, scoreB, labelA, labelB);
    }

    /** Returns the set of script names this model knows about. */
    public Set<String> knownScripts() {
        return calibrations.keySet();
    }

    /** Returns the file-format version of the loaded model. */
    public int getModelVersion() {
        return VERSION;
    }

    // -----------------------------------------------------------------------
    // Internal scoring
    // -----------------------------------------------------------------------

    private TextQualityScore scoreText(String text) {
        List<ScriptRun> runs = buildScriptRuns(text);

        // Global z4: script-transition feature over the whole input string.
        // Computed before chunking because it captures document-level script mixing.
        float z4 = computeScriptTransitionZ(text);

        // Score each run against its own model; aggregate weighted by byte count.
        float totalBytes = 0;
        float weightedLogit = 0;
        String dominantScript = null;
        int maxBytes = 0;
        int totalBigramCount = 0;
        float[] dominantCal1 = null;

        for (ScriptRun run : runs) {
            if (!calibrations.containsKey(run.script)) {
                continue; // skip scripts not in model; treat as neutral, not junk
            }
            byte[] runUtf8 = run.text.getBytes(StandardCharsets.UTF_8);
            // Skip if too short to form a bigram by either metric.  A single
            // CJK char is 3 UTF-8 bytes (passes the byte filter) but 1 UTF-16
            // unit, and computeF1MeanLogP filters by text.length() < 2 and
            // returns NaN — which would poison the weighted sum here.
            if (runUtf8.length < 2 || run.text.length() < 2) {
                continue;
            }
            float logit = scoreChunk(runUtf8, run.text, run.script, z4);
            int n = runUtf8.length;
            weightedLogit += logit * n;
            totalBytes += n;
            totalBigramCount += n - 1;
            if (n > maxBytes) {
                maxBytes = n;
                dominantScript = run.script;
                dominantCal1 = calibrations.get(run.script);
            }
        }

        if (totalBytes == 0 || dominantScript == null) {
            String label = runs.isEmpty() ? "LATIN" : runs.get(0).script;
            return unknownScore(label);
        }

        float zScore = weightedLogit / totalBytes;

        float uncertainty = (dominantCal1 != null && totalBigramCount > 0)
                ? (float) (1.96 * dominantCal1[1] / Math.sqrt(totalBigramCount)) : 0f;
        float ciLow = zScore - uncertainty;
        float ciHigh = zScore + uncertainty;
        float pClean = (float) (1.0 / (1.0 + Math.exp(-zScore)));

        return new TextQualityScore(zScore, pClean, ciLow, ciHigh, dominantScript);
    }

    /**
     * Diagnostic — exposes per-feature z-scores and classifier weights.  Same
     * chunking and aggregation as {@link #score(String)}, but returns the
     * intermediate signals individually for analysis or for hybrid models
     * that want to substitute one feature with an externally-computed value.
     *
     * <p>Aggregation: per-chunk z1/z2/z3 and per-chunk logit are byte-count-
     * weighted across script-homogeneous chunks.  z4 is a global signal
     * (already document-level).  {@code dominantScript} and
     * {@code classifierWeights} refer to the script run with the most bytes.
     */
    public FeatureComponents scoreWithFeatureComponents(String text) {
        if (text == null || text.isEmpty()) {
            return new FeatureComponents(Float.NaN, Float.NaN, Float.NaN,
                    Float.NaN, Float.NaN, "UNKNOWN", null, 0);
        }
        List<ScriptRun> runs = buildScriptRuns(text);
        float z4 = computeScriptTransitionZ(text);

        float totalBytes = 0;
        float weightedZ1 = 0;
        float weightedZ2 = 0;
        float weightedZ3 = 0;
        float weightedLogit = 0;
        String dominantScript = null;
        int maxBytes = 0;

        for (ScriptRun run : runs) {
            if (!calibrations.containsKey(run.script)) {
                continue;
            }
            byte[] runUtf8 = run.text.getBytes(StandardCharsets.UTF_8);
            if (runUtf8.length < 2 || run.text.length() < 2) {
                continue; // see scoreText: paired filter avoids NaN poisoning
            }
            float[] zs = computeChunkZs(runUtf8, run.text, run.script);
            float chunkLogit = combineLogit(zs[0], zs[1], zs[2], z4, run.script);
            int n = runUtf8.length;
            weightedZ1 += zs[0] * n;
            weightedZ2 += zs[1] * n;
            weightedZ3 += zs[2] * n;
            weightedLogit += chunkLogit * n;
            totalBytes += n;
            if (n > maxBytes) {
                maxBytes = n;
                dominantScript = run.script;
            }
        }

        if (totalBytes == 0 || dominantScript == null) {
            return new FeatureComponents(Float.NaN, Float.NaN, Float.NaN, z4,
                    Float.NaN, runs.isEmpty() ? "UNKNOWN" : runs.get(0).script,
                    null, 0);
        }

        float[] cw = classifierWeights.get(dominantScript);
        return new FeatureComponents(
                weightedZ1 / totalBytes,
                weightedZ2 / totalBytes,
                weightedZ3 / totalBytes,
                z4,
                weightedLogit / totalBytes,
                dominantScript,
                cw,
                (int) totalBytes);
    }

    /**
     * Per-feature z-score breakdown returned by
     * {@link #scoreWithFeatureComponents(String)}.  All z-scores are
     * byte-count-weighted aggregates across script-homogeneous chunks
     * except {@code z4}, which is a single document-level value.
     *
     * <p>{@code classifierWeights} is the per-script linear classifier
     * weight vector {@code {w1, w2, w3, w4, bias}} for the dominant
     * script — useful for hybrid models that recompute the logit after
     * substituting one z-score with an externally-computed value.
     */
    public static final class FeatureComponents {
        public final float z1;
        public final float z2;
        public final float z3;
        public final float z4;
        public final float logit;
        public final String dominantScript;
        public final float[] classifierWeights;
        public final int totalBytes;

        FeatureComponents(float z1, float z2, float z3, float z4,
                          float logit, String dominantScript,
                          float[] classifierWeights, int totalBytes) {
            this.z1 = z1;
            this.z2 = z2;
            this.z3 = z3;
            this.z4 = z4;
            this.logit = logit;
            this.dominantScript = dominantScript;
            this.classifierWeights = classifierWeights;
            this.totalBytes = totalBytes;
        }
    }

    /**
     * Scores a single script-homogeneous chunk and returns its logit.
     * Positive = clean, negative = junk.  Returns 0 (neutral) if the chunk
     * has no model or is too short.
     */
    private float scoreChunk(byte[] utf8, String text, String script, float z4) {
        if (utf8.length < 2 || !calibrations.containsKey(script)) {
            return 0f;
        }
        float[] zs = computeChunkZs(utf8, text, script);
        return combineLogit(zs[0], zs[1], zs[2], z4, script);
    }

    /**
     * Computes per-feature z-scores {z1, z2, z3} for a single script-
     * homogeneous chunk.  Shared between {@link #scoreChunk} and
     * {@link #scoreWithFeatureComponents}, and used at training time
     * via the public {@code computeZ2/3/4...} static helpers so
     * training and inference share the same math.
     */
    private float[] computeChunkZs(byte[] utf8, String text, String script) {
        // Feature 1: per-script codepoint-bigram, calibrated per-script
        V7Tables tables = f1TablesByScript.get(script);
        float meanF1LogProb = computeCodepointF1MeanLogP(text, tables);
        float[] cal1 = calibrations.get(script);
        float z1 = (meanF1LogProb - cal1[0]) / cal1[1];

        float z2 = computeZ2BlockTransition(text,
                blockTables.get(script), blockCalibrations.get(script));
        float z3 = computeZ3ControlByte(utf8, controlCalibrations.get(script));
        return new float[]{z1, z2, z3};
    }

    private static float computeCodepointF1MeanLogP(String text, V7Tables tables) {
        if (tables == null) return Float.NaN;
        double v = computeF1MeanLogP(text, tables);
        return Double.isNaN(v) ? Float.NaN : (float) v;
    }

    /**
     * Feature 2 — calibrated z-score for block-transition mean log-prob on
     * one text window.  Returns 0 if the window has fewer than two
     * codepoints or if {@code blockTable} / {@code blockCal} are null.
     *
     * <p>Block bucketing is via the JVM-independent
     * {@link UnicodeBlockRanges}.  Public so the trainer's classifier
     * feature extractor calls into the exact same math used at inference
     * time — single source of truth, no train/infer drift.
     *
     * @param blockTable {@code (blockN)² × float} log-prob table where
     *                   {@code blockN = UnicodeBlockRanges.bucketCount()}
     */
    public static float computeZ2BlockTransition(String text,
                                                  float[] blockTable, float[] blockCal) {
        if (blockTable == null || blockCal == null || text.length() < 2) {
            return 0f;
        }
        int blockN = UnicodeBlockRanges.bucketCount();
        int prev = -1;
        double sum = 0;
        int count = 0;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            int blockId = UnicodeBlockRanges.bucketOf(cp);
            if (prev >= 0) {
                sum += blockTable[prev * blockN + blockId];
                count++;
            }
            prev = blockId;
            i += Character.charCount(cp);
        }
        if (count == 0) {
            return 0f;
        }
        return ((float) (sum / count) - blockCal[0]) / blockCal[1];
    }

    /**
     * Feature 3 — calibrated z-score for control-byte fraction on the UTF-8
     * byte sequence of one text window.  Stored score is {@code -fraction}
     * so higher = cleaner (matching the direction convention of the other
     * z-features).
     *
     * <p>Public for train/infer math-sharing.
     */
    public static float computeZ3ControlByte(byte[] utf8, float[] controlCal) {
        if (utf8.length == 0 || controlCal == null) {
            return 0f;
        }
        long controlCount = 0;
        for (byte b : utf8) {
            if (isControlByte(b & 0xFF)) {
                controlCount++;
            }
        }
        float score = -(float) controlCount / utf8.length;
        return (score - controlCal[0]) / controlCal[1];
    }

    /**
     * Feature 4 — calibrated z-score for global script-transition mean
     * log-prob on one text window.  Uses raw {@link Character.UnicodeScript}
     * values (no model fallback) so HIRAGANA / KATAKANA / HAN remain
     * distinct.  Returns 0 if the window has fewer than two non-neutral
     * codepoints or if the script-transition data isn't supplied.
     *
     * <p>Public for train/infer math-sharing.  Note: inference computes
     * z4 once per document via {@link #computeScriptTransitionZ} (which
     * uses the instance's loaded tables); this helper takes them as
     * arguments so training can compute z4 before the model is finalised.
     */
    public static float computeZ4ScriptTransition(String text,
                                                   float[] scriptTransTable,
                                                   float[] scriptTransCal,
                                                   Map<String, Integer> scriptBucketIndex,
                                                   int numScriptBuckets) {
        if (scriptTransTable == null || scriptTransCal == null
                || scriptBucketIndex == null || numScriptBuckets == 0) {
            return 0f;
        }
        int otherBucket = numScriptBuckets - 1;
        int prev = -1;
        double sum = 0;
        int count = 0;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            Character.UnicodeScript s = Character.UnicodeScript.of(cp);
            if (s == Character.UnicodeScript.COMMON
                    || s == Character.UnicodeScript.INHERITED
                    || s == Character.UnicodeScript.UNKNOWN) {
                continue;
            }
            int bucket = scriptBucketIndex.getOrDefault(s.name(), otherBucket);
            if (prev >= 0) {
                sum += scriptTransTable[prev * numScriptBuckets + bucket];
                count++;
            }
            prev = bucket;
        }
        if (count == 0) {
            return 0f;
        }
        return ((float) (sum / count) - scriptTransCal[0]) / scriptTransCal[1];
    }

    /**
     * Combines per-feature z-scores via the per-script linear classifier.
     * Fallback (when no classifier weights stored): equal-weight average.
     */
    private float combineLogit(float z1, float z2, float z3, float z4, String script) {
        float[] cw = classifierWeights.get(script);
        if (cw != null) {
            int nFeat = cw.length - 1; // bias is last
            float logit = cw[nFeat];   // bias
            if (nFeat >= 1) logit += cw[0] * z1;
            if (nFeat >= 2) logit += cw[1] * z2;
            if (nFeat >= 3) logit += cw[2] * z3;
            if (nFeat >= 4) logit += cw[3] * z4;
            return logit;
        }
        return (z1 + z2 + z3 + z4) / 4.0f; // fallback: equal weight
    }

    // -----------------------------------------------------------------------
    // Feature 1: per-script open-addressing codepoint-bigram lookup
    // -----------------------------------------------------------------------

    /**
     * Mean log-prob over the codepoint pairs in {@code text} using the given
     * script's V7 F1 tables.
     *
     * <p>For each adjacent codepoint pair {@code (a, b)}:
     * <ol>
     *   <li>Binary-search both codepoints in the script's codepoint index.
     *       If either is absent, the pair was never seen in training; emit
     *       {@code α * (logP(a) + logP(b))} using each codepoint's unigram
     *       value (or {@link V7Tables#unigramFallbackLogProb} if the
     *       codepoint isn't even in the unigram index).</li>
     *   <li>Otherwise, look up the packed {@code (idxA<<16)|idxB} key in
     *       the open-addressing bigram table.  Empty slot → unseen pair →
     *       unigram backoff (same formula).  Match → dequantize the stored
     *       value.</li>
     * </ol>
     *
     * <p>This is the single authoritative implementation of the V7 F1
     * scoring math, shared by inference and training.  Keeping one
     * implementation eliminates the risk of train/infer drift in the F1
     * feature.
     *
     * @return mean log-prob, or {@link Double#NaN} if {@code text} has fewer
     *         than two codepoints or {@code tables} is null
     */
    public static double computeF1MeanLogP(String text, V7Tables tables) {
        if (text == null || text.length() < 2 || tables == null) {
            return Double.NaN;
        }
        double sum = 0;
        int n = 0;
        int prevCp = -1;
        int prevIdx = -1;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            int curIdx = codepointToIndex(tables, cp);
            if (prevCp >= 0) {
                sum += scorePairF1V7(prevCp, prevIdx, cp, curIdx, tables);
                n++;
            }
            prevCp = cp;
            prevIdx = curIdx;
        }
        return n == 0 ? Double.NaN : sum / n;
    }

    /**
     * Binary-search a codepoint in the script's index.
     *
     * @return the dense index (≥ 0) if found, or -1 if the codepoint
     *         doesn't appear in any kept bigram for this script
     */
    public static int codepointToIndex(V7Tables tables, int cp) {
        return java.util.Arrays.binarySearch(tables.codepointIndex, cp);
    }

    /**
     * Mixing function used to scatter packed (idxA, idxB) keys across
     * the open-addressing table.  A simple integer finalizer (splitmix32
     * style) gives good distribution for sequential index values.
     *
     * <p>Public so the trainer's open-addressing insertion routine uses
     * the same probe order as inference — drift here would silently
     * corrupt every lookup.
     */
    public static int mixIndexKey(int packedKey) {
        int x = packedKey;
        x = (x ^ (x >>> 16)) * 0x7feb352d;
        x = (x ^ (x >>> 15)) * 0x846ca68b;
        x = x ^ (x >>> 16);
        return x;
    }

    /**
     * Packed bigram key for indices {@code (a, b)} where each index fits in
     * {@link JunkDetectorTrainingConfig#KEY_INDEX_BITS} bits.  Asserts that
     * indices are non-negative; that's the caller's contract.
     */
    public static int packBigramKey(int idxA, int idxB) {
        return (idxA << 16) | (idxB & 0xFFFF);
    }

    /**
     * Looks up a (cpA, cpB) bigram in the script's V7 tables and returns
     * its dequantized log-prob.  Falls back to unigram backoff on miss.
     *
     * <p>{@code idxA}/{@code idxB} are the pre-computed codepoint indices
     * (from {@link #codepointToIndex}); {@code -1} means the codepoint is
     * not in this script's index.  The caller is expected to compute them
     * once when scanning the text (avoiding a redundant binary search per
     * codepoint).
     */
    private static double scorePairF1V7(int cpA, int idxA, int cpB, int idxB,
                                         V7Tables tables) {
        if (idxA >= 0 && idxB >= 0) {
            int slot = lookupBigramSlot(tables, idxA, idxB);
            if (slot >= 0) {
                return dequantize(tables.bigramValues[slot],
                        tables.bigramQuantMin, tables.bigramQuantMax);
            }
        }
        // Unigram backoff for unseen pair or for codepoints absent from the
        // per-script index.  α=1.0 = plain independence; prototype-validated.
        double ua = unigramLogProb(tables, idxA);
        double ub = unigramLogProb(tables, idxB);
        return tables.backoffAlpha * (ua + ub);
    }

    /**
     * Open-addressing lookup: returns the slot index that contains the key
     * for {@code (idxA, idxB)}, or {@code -1} if not present (probe hit an
     * empty slot first).
     *
     * <p>Linear probing with the same mix-hash used at training time —
     * required for the table to be readable, not just writable.
     */
    static int lookupBigramSlot(V7Tables tables, int idxA, int idxB) {
        int packedKey = packBigramKey(idxA, idxB);
        int[] keys = tables.bigramKeys;
        int mask = keys.length - 1;
        int h = mixIndexKey(packedKey) & mask;
        while (true) {
            int k = keys[h];
            if (k == V7Tables.EMPTY_KEY) return -1;
            if (k == packedKey) return h;
            h = (h + 1) & mask;
        }
    }

    private static double unigramLogProb(V7Tables tables, int idx) {
        if (idx < 0) {
            return tables.unigramFallbackLogProb;
        }
        return dequantize(tables.unigramTable[idx],
                tables.unigramQuantMin, tables.unigramQuantMax);
    }

    private static float dequantize(byte b, float min, float max) {
        int u = b & 0xFF;
        return min + (u / 255.0f) * (max - min);
    }

    /**
     * Computes the global script-transition z-score for the whole input
     * string against this model's loaded tables.  Thin wrapper around the
     * public static {@link #computeZ4ScriptTransition} helper — same math,
     * just preloaded with this instance's parameters.
     */
    private float computeScriptTransitionZ(String text) {
        return computeZ4ScriptTransition(text,
                scriptTransitionTable, scriptTransitionCalibration,
                scriptBucketIndex, numScriptBuckets);
    }

    /**
     * Splits text into maximal runs of the same Unicode script.
     * COMMON, INHERITED, and UNKNOWN codepoints (spaces, punctuation, digits)
     * are attached to the preceding script run so that inter-word bigrams are
     * preserved within each run.  Any leading COMMON characters are prepended
     * to the first non-COMMON run.
     */
    private List<ScriptRun> buildScriptRuns(String text) {
        List<ScriptRun> runs = new ArrayList<>();
        String currentScript = null;
        StringBuilder currentText = new StringBuilder();
        StringBuilder leadingCommon = new StringBuilder();

        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);

            Character.UnicodeScript s = Character.UnicodeScript.of(cp);
            if (s == Character.UnicodeScript.COMMON
                    || s == Character.UnicodeScript.INHERITED
                    || s == Character.UnicodeScript.UNKNOWN) {
                if (currentScript != null) {
                    currentText.appendCodePoint(cp);
                } else {
                    leadingCommon.appendCodePoint(cp);
                }
                continue;
            }

            String scriptName = SCRIPT_MODEL_FALLBACK.getOrDefault(s.name(), s.name());

            if (!scriptName.equals(currentScript)) {
                if (currentScript != null && currentText.length() > 0) {
                    runs.add(new ScriptRun(currentScript, currentText.toString()));
                }
                currentScript = scriptName;
                currentText = new StringBuilder();
                if (leadingCommon.length() > 0) {
                    currentText.append(leadingCommon);
                    leadingCommon.setLength(0);
                }
            }
            currentText.appendCodePoint(cp);
        }

        if (currentScript != null && currentText.length() > 0) {
            runs.add(new ScriptRun(currentScript, currentText.toString()));
        }
        return runs;
    }

    private static final class ScriptRun {
        final String script;
        final String text;
        ScriptRun(String script, String text) {
            this.script = script;
            this.text = text;
        }
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
