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
 * <p>Scoring combines several features:
 * <ol>
 *   <li><b>Codepoint-bigram log-probability (z1)</b> — every bigram is
 *       bucketed to its script ({@link #forEachScriptBigram}) and scored
 *       against that script's open-addressed bigram table; unseen pairs fall
 *       back to a unigram independence-assumption score
 *       {@code α * (log P(cp_a) + log P(cp_b))}.  This is the only per-script
 *       feature.</li>
 *   <li><b>Unicode block-transition log-probability (z2)</b> — a single
 *       global table over {@link Character.UnicodeBlock} values.</li>
 *   <li><b>Control-byte fraction (z3)</b> — fraction of bytes in control
 *       ranges [0x01–0x08, 0x0B, 0x0C, 0x0E–0x1F, 0x7F].</li>
 *   <li><b>Script-transition log-probability (z4)</b> — a single global
 *       table over raw {@link Character.UnicodeScript} values.</li>
 *   <li>Document-level quality features z5..z9 (letter-adjacent-to-mark,
 *       replacement ratio, script density, script coherence, script
 *       alternation).</li>
 * </ol>
 *
 * <p>Per-script z1 is calibrated (mu/sigma) on held-out dev text; z2..z9 are
 * global.  The z-scores are combined by a single global linear combiner:
 * {@code logit = w1*z1 + ... + w9*z9 + bias}, fit on clean vs. corrupted
 * windows.  Natural junk threshold is 0 (positive logit = clean); use
 * negative thresholds for conservative detection.</p>
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
    /** Sole supported file-format version.  Mismatch is a hard error —
     *  prior versions live in git history and are not loadable by this
     *  build.  We deliberately don't keep dual-version paths so it's
     *  impossible to confuse model versions. */
    // Bucket-by-script model.  z1 (codepoint-bigram LM) is the only per-script
    // feature, scored by bucketing every bigram to its script (digits skipped,
    // COMMON glue folded into the adjacent script).  z2 (block-transition) and
    // z3 (control-byte) are single GLOBAL document-level features; the combiner
    // is one global weight vector.  Older model files are structurally
    // incompatible and rejected at load.
    public static final int VERSION = 15;

    // Feature 1 — per-script open-addressed codepoint-bigram tables.
    // No global Bloom: empty-slot is the membership oracle.
    private final Map<String, BigramTables> f1TablesByScript;

    /** Per-script z1 calibration {mu, sigma} on the bucket mean log-prob.
     *  z1 is the ONLY per-script feature. */
    private final Map<String, float[]> calibrations; // script → float[2] {mu, sigma}

    // Feature 2 — single GLOBAL block-transition table.
    // int16-quantized; dequantize via {@code min + (s/65535) * (max - min)}.
    private final short[] blockTable;
    private final float[] blockTableQuant;   // {min, max}
    private final float[] blockCalibration;  // {mu, sigma}

    // Feature 3 — single GLOBAL control-byte fraction calibration {mu, sigma}.
    private final float[] controlCalibration;

    // Feature 4 — single global script-transition table (int16 quantized).
    private final short[] scriptTransitionTable;
    private final float[] scriptTransitionTableQuant; // {min, max}
    private final float[] scriptTransitionCalibration;
    private final Map<String, Integer> scriptBucketIndex;
    private final int numScriptBuckets;

    // Single GLOBAL linear combiner: float[numFeatures+1] = {w1, ..., wN, bias}.
    private final float[] combinerWeights;

    /** Document-level z5 calibration {mu, sigma}. */
    private final float[] z5Calibration;
    /** Document-level z6 calibration {mu, sigma}. */
    private final float[] z6Calibration;
    /** Document-level z9 calibration {mu, sigma}.  z9 = scriptAlternationRatio:
     *  transitions between dominant and foreign script, normalized by max
     *  possible transitions given the counts.  Length- and proportion-
     *  invariant.  Catches the mojibake-of-Latin-as-CJK pattern: scattered
     *  singleton Han chars in Latin text score near 1.0 (max alternation),
     *  while legitimate mixed-script (English with embedded Chinese phrase)
     *  scores low because the foreign script clumps together. */
    private final float[] z9Calibration;

    private JunkDetector(Map<String, float[]> calibrations,
                         short[] blockTable,
                         float[] blockTableQuant,
                         float[] blockCalibration,
                         float[] controlCalibration,
                         float[] combinerWeights,
                         short[] scriptTransitionTable,
                         float[] scriptTransitionTableQuant,
                         float[] scriptTransitionCalibration,
                         Map<String, Integer> scriptBucketIndex,
                         int numScriptBuckets,
                         Map<String, BigramTables> f1TablesByScript,
                         float[] z5Calibration,
                         float[] z6Calibration,
                         float[] z9Calibration) {
        this.calibrations = Collections.unmodifiableMap(calibrations);
        this.blockTable = blockTable;
        this.blockTableQuant = blockTableQuant;
        this.blockCalibration = blockCalibration;
        this.controlCalibration = controlCalibration;
        this.combinerWeights = combinerWeights;
        this.scriptTransitionTable = scriptTransitionTable;
        this.scriptTransitionTableQuant = scriptTransitionTableQuant;
        this.scriptTransitionCalibration = scriptTransitionCalibration;
        this.scriptBucketIndex = Collections.unmodifiableMap(scriptBucketIndex);
        this.numScriptBuckets = numScriptBuckets;
        this.f1TablesByScript = Collections.unmodifiableMap(f1TablesByScript);
        this.z5Calibration = z5Calibration;
        this.z6Calibration = z6Calibration;
        this.z9Calibration = z9Calibration;
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
     *   [1 byte]     version (= {@value #VERSION})
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
     *     // bigram tables for this script — see {@link BigramTables#writeTo}
     *     [4 bytes]      backoff_alpha (float32 BE)
     *     [4 bytes]      codepoint_count
     *     [codepoint_count × 4 bytes]  codepoint index (sorted, ascending)
     *     [4 bytes]      bigram_slots (power of 2)
     *     [4 bytes]      bigram_quant_min (float32 BE)
     *     [4 bytes]      bigram_quant_max (float32 BE)
     *     [bigram_slots × 4 bytes]  bigram open-addressing keys
     *                                ((idxA<<16)|idxB, or {@link BigramTables#EMPTY_KEY})
     *     [bigram_slots bytes]      bigram values (8-bit quantized log-probs)
     *     [4 bytes]      unigram_quant_min (float32 BE)
     *     [4 bytes]      unigram_quant_max (float32 BE)
     *     [4 bytes]      unigram_fallback_log_prob (float32 BE; used for
     *                                                codepoints not in index)
     *     [codepoint_count bytes]   unigram values (8-bit quantized log-probs)
     *     // F2/F3/classifier
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

            // z4 — global script-transition section (int16-quantized table).
            int numScriptBuckets = dis.readUnsignedByte();
            Map<String, Integer> scriptBucketIndex = new LinkedHashMap<>(numScriptBuckets * 2);
            for (int i = 0; i < numScriptBuckets; i++) {
                int nameLen = dis.readUnsignedShort();
                String bucketName = new String(dis.readNBytes(nameLen), StandardCharsets.UTF_8);
                scriptBucketIndex.put(bucketName, i);
            }
            float scriptTransMin = dis.readFloat();
            float scriptTransMax = dis.readFloat();
            short[] scriptTransitionTable = readShortTable(dis, numScriptBuckets * numScriptBuckets);
            float[] scriptTransitionTableQuant = new float[]{scriptTransMin, scriptTransMax};
            float[] scriptTransitionCalibration = new float[]{dis.readFloat(), dis.readFloat()};

            // z2 — single GLOBAL block-transition table (int16-quantized).
            float blockMin = dis.readFloat();
            float blockMax = dis.readFloat();
            short[] blockTable = readShortTable(dis, blockN * blockN);
            float[] blockTableQuant = new float[]{blockMin, blockMax};
            float[] blockCalibration = new float[]{dis.readFloat(), dis.readFloat()};

            // z3 — single GLOBAL control-byte calibration.
            float[] controlCalibration = new float[]{dis.readFloat(), dis.readFloat()};

            // Document-level calibrations: z5 (letter-adjacent-to-mark),
            // z6 (replacement-char), z9 (script-alternation).
            float[] z5Calibration = new float[]{dis.readFloat(), dis.readFloat()};
            float[] z6Calibration = new float[]{dis.readFloat(), dis.readFloat()};
            float[] z9Calibration = new float[]{dis.readFloat(), dis.readFloat()};

            // Single GLOBAL combiner weights {w1..wN, bias}.
            int numFeatures = dis.readUnsignedByte();
            float[] combinerWeights = new float[numFeatures + 1];
            for (int j = 0; j <= numFeatures; j++) {
                combinerWeights[j] = dis.readFloat();
            }

            // Per-script section: only z1 calibration + the bigram tables.
            Map<String, BigramTables> f1TablesByScript = new HashMap<>(numScripts * 2);
            Map<String, float[]>  calibrations     = new HashMap<>(numScripts * 2);
            for (int s = 0; s < numScripts; s++) {
                int nameLen = dis.readUnsignedShort();
                String script = new String(dis.readNBytes(nameLen), StandardCharsets.UTF_8);
                calibrations.put(script, new float[]{dis.readFloat(), dis.readFloat()});
                f1TablesByScript.put(script, BigramTables.readFrom(dis));
            }

            requireUsableSigma("scriptTransition", scriptTransitionCalibration);
            requireUsableSigma("block", blockCalibration);
            requireUsableSigma("control", controlCalibration);
            requireUsableSigma("z5", z5Calibration);
            requireUsableSigma("z6", z6Calibration);
            requireUsableSigma("z9", z9Calibration);
            for (Map.Entry<String, float[]> e : calibrations.entrySet()) {
                requireUsableSigma("z1[" + e.getKey() + "]", e.getValue());
            }

            return new JunkDetector(calibrations,
                    blockTable, blockTableQuant, blockCalibration,
                    controlCalibration, combinerWeights,
                    scriptTransitionTable, scriptTransitionTableQuant,
                    scriptTransitionCalibration,
                    scriptBucketIndex, numScriptBuckets, f1TablesByScript,
                    z5Calibration, z6Calibration, z9Calibration);
        }
    }

    /**
     * Validates a calibration {@code {mu, sigma}} from the model file: sigma is the
     * divisor in every z-score, so it must be finite and &gt; 0.  Single enforcement
     * point for that invariant -- inference divides without re-checking.
     */
    static void requireUsableSigma(String name, float[] calibration) throws IOException {
        boolean ok = calibration != null && calibration.length >= 2
                && Float.isFinite(calibration[1]) && calibration[1] > 0f;
        if (!ok) {
            String sigma = (calibration == null || calibration.length < 2)
                    ? "absent" : Float.toString(calibration[1]);
            throw new IOException("Invalid model: " + name
                    + " calibration sigma must be finite and > 0 but was " + sigma);
        }
    }

    /** Read {@code size} big-endian int16 values as a short[]. */
    private static short[] readShortTable(DataInputStream dis, int size) throws IOException {
        byte[] raw = dis.readNBytes(size * 2);
        short[] out = new short[size];
        ByteBuffer.wrap(raw).order(ByteOrder.BIG_ENDIAN).asShortBuffer().get(out);
        return out;
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

    /** Returns the file-format version of the loaded model
     *  (always {@link #VERSION}; mismatches are rejected at load time). */
    public int getModelVersion() {
        return VERSION;
    }

    // -----------------------------------------------------------------------
    // Internal scoring
    // -----------------------------------------------------------------------

    /** Aggregated document features under the bucket-by-script model. */
    private static final class Agg {
        float z1, z2, z3, z4, z5, z6, z7, z8, z9, logit;
        String dominantScript;     // null => no scoreable script (NONE fallback)
        int totalBigrams;
        float[] dominantCal1;
    }

    /**
     * Computes the document feature vector under the bucket-by-script model:
     * every bigram is bucketed to its script ({@link #forEachScriptBigram}),
     * each script's mean log-prob is calibrated to {@code z1_S=(mean-mu)/sigma},
     * and the per-script z1's are aggregated count-weighted into a single doc
     * {@code z1}.  z2..z9 are document-level globals.  A single global combiner
     * produces the logit.  No runs, no sentinels, no per-script z2/z3.
     */
    private Agg aggregate(String text) {
        // NFKC-normalize so inference matches the trainer's tally AND legacy
        // compatibility forms fold to canonical — critically half-width katakana
        // (U+FF66-FF9F) -> full-width, which the HAN bigram table is trained on.
        // Without this, half-width-katakana pages (Shift_JIS-era Japanese
        // e-commerce) floor every bigram as "unseen" (z1 ~-9, false-junk).
        text = java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFKC);
        int[] cps = text.codePoints().toArray();

        Map<String, double[]> buckets = new HashMap<>(); // script -> {sumLogP, count}
        // Left-index memo.  forEachScriptBigram emits (^,x),(x,y),(y,$)... so within
        // a run each pair's right codepoint b is the next pair's left codepoint a.
        // Reuse the previous pair's right-index as this pair's left-index when they
        // match (same codepoint AND same script => same table), so each codepoint is
        // binary-searched in the script's index once instead of twice.  Bit-identical
        // to scoring each pair independently; the guard falls back to a fresh search
        // whenever the overlap doesn't hold (run boundary, sentinel, script change).
        String[] lastScript = {null};
        int[] lastB = {Integer.MIN_VALUE};
        int[] lastBIdx = {-1};
        forEachScriptBigram(cps, (script, a, b) -> {
            if (!calibrations.containsKey(script)) {
                return;
            }
            BigramTables t = f1TablesByScript.get(script);
            if (t == null) {
                return;
            }
            int idxA = (a == lastB[0] && script.equals(lastScript[0]))
                    ? lastBIdx[0] : codepointToIndex(t, a);
            int idxB = codepointToIndex(t, b);
            lastScript[0] = script;
            lastB[0] = b;
            lastBIdx[0] = idxB;
            double lp = scorePairF1(a, idxA, b, idxB, t);
            if (Double.isNaN(lp)) {
                return;
            }
            double[] bk = buckets.computeIfAbsent(script, k -> new double[2]);
            bk[0] += lp;
            bk[1] += 1;
        });

        Agg agg = new Agg();
        double weightedZ1 = 0;
        long z1Count = 0;
        long maxCount = 0;
        for (Map.Entry<String, double[]> e : buckets.entrySet()) {
            long cnt = (long) e.getValue()[1];
            if (cnt == 0) {
                continue;
            }
            float[] cal = calibrations.get(e.getKey());
            double mean = e.getValue()[0] / cnt;
            double z1s = (mean - cal[0]) / cal[1];
            weightedZ1 += z1s * cnt;
            z1Count += cnt;
            if (cnt > maxCount) {
                maxCount = cnt;
                agg.dominantScript = e.getKey();
                agg.dominantCal1 = cal;
            }
        }

        // Document-level features (computed once).
        agg.z2 = computeZ2BlockTransitionQuantized(text, blockTable,
                blockTableQuant, blockCalibration);
        agg.z3 = computeZ3ControlByte(text.getBytes(StandardCharsets.UTF_8),
                controlCalibration);
        agg.z4 = computeScriptTransitionZ(text);
        agg.z5 = computeZ5LetterAdjacentToMarkRatio(text);
        agg.z6 = computeZ6ReplacementRatio(text);
        float z7 = (float) TextQualityFeatures.scriptDensity(text);
        agg.z7 = Float.isNaN(z7) ? 0f : z7;
        double rawFrag = TextQualityFeatures.scriptFragmentation(text);
        agg.z8 = Double.isNaN(rawFrag) ? 1f : 1f - (float) rawFrag;
        agg.z9 = computeZ9AlternationRatio(text);
        agg.totalBigrams = (int) z1Count;

        if (z1Count == 0 || agg.dominantScript == null) {
            // No scoreable LETTER at all (zero runs) — doc-level fallback:
            // density=0 -> very negative; density=1 coherence=1 -> positive
            // (unmodeled coherent script); density=1 coherence=0 -> very negative.
            // z6 is included so a letter-free but FFFD/anomaly-heavy doc (which can
            // no longer flood z1) is still penalized here — no path ignores the
            // replacement ratio.  For unmodeled-but-clean script z6~0, so it's inert.
            agg.dominantScript = null;
            agg.z1 = Float.NaN;
            agg.logit = -7f + 4f * agg.z7 + 6f * agg.z8 + 4f * agg.z6;
            return agg;
        }
        agg.z1 = (float) (weightedZ1 / z1Count);
        agg.logit = combineLogit(agg.z1, agg.z2, agg.z3, agg.z4,
                agg.z5, agg.z6, agg.z7, agg.z8, agg.z9);
        return agg;
    }

    private TextQualityScore scoreText(String text) {
        Agg a = aggregate(text);
        float pClean = (float) (1.0 / (1.0 + Math.exp(-a.logit)));
        if (a.dominantScript == null) {
            return new TextQualityScore(a.logit, pClean, a.logit, a.logit, "NONE");
        }
        float uncertainty = (a.dominantCal1 != null && a.totalBigrams > 0)
                ? (float) (1.96 * a.dominantCal1[1] / Math.sqrt(a.totalBigrams)) : 0f;
        return new TextQualityScore(a.logit, pClean,
                a.logit - uncertainty, a.logit + uncertainty, a.dominantScript);
    }

    /**
     * Diagnostic — exposes the per-feature z-scores, the global combiner
     * weights, and the dominant script.  Same math as {@link #score(String)}.
     */
    public FeatureComponents scoreWithFeatureComponents(String text) {
        if (text == null || text.isEmpty()) {
            return new FeatureComponents(Float.NaN, Float.NaN, Float.NaN,
                    Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN,
                    Float.NaN, Float.NaN, "UNKNOWN", null, 0);
        }
        Agg a = aggregate(text);
        if (a.dominantScript == null) {
            return new FeatureComponents(Float.NaN, Float.NaN, Float.NaN,
                    a.z4, a.z5, a.z6, a.z7, a.z8, a.z9, a.logit, "NONE", null, 0);
        }
        return new FeatureComponents(a.z1, a.z2, a.z3, a.z4, a.z5, a.z6, a.z7,
                a.z8, a.z9, a.logit, a.dominantScript, combinerWeights,
                a.totalBigrams);
    }

    /**
     * Per-feature z-score breakdown returned by
     * {@link #scoreWithFeatureComponents(String)}.  z1-z3 are byte-count-
     * weighted aggregates across script-homogeneous chunks; z4-z6 are
     * single document-level values.
     *
     * <p>z5 (letter-adjacent-to-mark ratio) and z6 (replacement-character
     * ratio) are document-level features included alongside z1–z4 in the
     * per-script LR.
     *
     * <p>{@code classifierWeights} is the per-script linear classifier
     * weight vector {@code {w1, ..., wN, bias}} for the dominant script
     * — useful for hybrid models that recompute the logit after
     * substituting one z-score with an externally-computed value.
     */
    public static final class FeatureComponents {
        public final float z1;
        public final float z2;
        public final float z3;
        public final float z4;
        public final float z5;
        public final float z6;
        public final float z7;
        public final float z8;
        public final float z9;
        public final float logit;
        public final String dominantScript;
        public final float[] classifierWeights;
        public final int totalBytes;

        FeatureComponents(float z1, float z2, float z3, float z4,
                          float z5, float z6, float z7, float z8, float z9,
                          float logit, String dominantScript,
                          float[] classifierWeights, int totalBytes) {
            this.z1 = z1;
            this.z2 = z2;
            this.z3 = z3;
            this.z4 = z4;
            this.z5 = z5;
            this.z6 = z6;
            this.z7 = z7;
            this.z8 = z8;
            this.z9 = z9;
            this.logit = logit;
            this.dominantScript = dominantScript;
            this.classifierWeights = classifierWeights;
            this.totalBytes = totalBytes;
        }
    }

    // -----------------------------------------------------------------------
    // Global features (computed once per document, like z4)
    // -----------------------------------------------------------------------

    /**
     * z5: calibrated letter-adjacent-to-mark ratio.  Delegates raw
     * computation to {@link TextQualityFeatures#letterAdjacentToMarkRatio}
     * and applies the document-level (mu, sigma) calibration loaded from
     * the model file.  Returns 0 (neutral) when the model has no z5
     * calibration or when the raw value is NaN.
     *
     * <p>Positive z5 = correct decoding of a precomposed-or-decomposed
     * script (Vietnamese, Indic, Thai, Arabic).  Negative z5 = mojibake
     * of such content as Latin-1.
     */
    public float computeZ5LetterAdjacentToMarkRatio(String text) {
        double raw = TextQualityFeatures.letterAdjacentToMarkRatio(text);
        if (Double.isNaN(raw) || z5Calibration == null) {
            return 0f;
        }
        return ((float) raw - z5Calibration[0]) / z5Calibration[1];
    }

    /**
     * z6: calibrated replacement-character ratio.  Direct decode-failure
     * signal — fraction of codepoints that are U+FFFD.  Higher raw value
     * = more decode failure = junkier; but the calibration centers on the
     * training distribution, so negative z6 = junkier than typical.
     *
     * <p>Returns 0 (neutral) when no calibration available.
     */
    public float computeZ6ReplacementRatio(String text) {
        double raw = TextQualityFeatures.replacementRatio(text);
        if (Double.isNaN(raw) || z6Calibration == null) {
            return 0f;
        }
        // Flip sign: higher replacement = lower quality, so feature is
        // (mu - raw) / sigma so a clean decode → positive z6.
        return (z6Calibration[0] - (float) raw) / z6Calibration[1];
    }

    /**
     * z9: calibrated script-alternation ratio.  Catches the mojibake-of-
     * Latin-as-CJK pattern where every accent becomes a singleton Han
     * char scattered through Latin text (high alternation = max value).
     * Length- and proportion-invariant by construction.  Sign flipped so
     * clean (low alternation) → positive z9 and mojibake (high
     * alternation) → negative.
     */
    public float computeZ9AlternationRatio(String text) {
        double raw = TextQualityFeatures.scriptAlternationRatio(text);
        if (Double.isNaN(raw) || z9Calibration == null) {
            return 0f;
        }
        // Higher alternation = junkier; (mu - raw) / sigma so clean text → positive z9.
        return (z9Calibration[0] - (float) raw) / z9Calibration[1];
    }


    /**
     * Inference-side z2 lookup against an int16-quantized block table.
     * Mirrors {@link #computeZ2BlockTransition}(float[]) but reads from
     * the quantized {@code short[]} table with per-table {min, max}
     * dequant params.  Per-bigram
     * dequantize is {@code min + (s/65535) * (max - min)} where s is
     * the unsigned 16-bit value.  65536 levels keep ~0.0002 nats/level
     * resolution — essentially lossless vs the float32 form for our
     * log-prob range.
     */
    private static float computeZ2BlockTransitionQuantized(String text,
                                                            short[] blockTable,
                                                            float[] quant,
                                                            float[] blockCal) {
        if (blockTable == null || quant == null || blockCal == null || text.length() < 2) {
            return 0f;
        }
        int blockN = UnicodeBlockRanges.bucketCount();
        float min = quant[0];
        float scale = (quant[1] - min) / 65535f;
        int prev = -1;
        double sum = 0;
        int count = 0;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            int blockId = UnicodeBlockRanges.bucketOf(cp);
            if (prev >= 0) {
                int s = blockTable[prev * blockN + blockId] & 0xFFFF;
                sum += min + s * scale;
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
            Character.UnicodeScript s = TextQualityFeatures.scriptOf(cp);
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
     * Combines per-feature z-scores via the global linear classifier.
     * Fallback (when no classifier weights stored): equal-weight average of
     * the four bigram-/transition-based features (z1-z4).
     *
     * <p>The classifier has 9 weights + bias (nFeat == 9) —
     * z1 (bigram), z2 (block transitions), z3 (control bytes),
     * z4 (script transitions), z5 (letter-adjacent-to-mark),
     * z6 (replacement ratio), z7 (script density), z8 (script coherence),
     * z9 (script-alternation ratio).
     */
    private float combineLogit(float z1, float z2, float z3, float z4,
                               float z5, float z6, float z7, float z8,
                               float z9) {
        float[] cw = combinerWeights;
        if (cw != null && cw.length >= 1) {
            int nFeat = cw.length - 1; // bias is last
            float logit = cw[nFeat];   // bias
            if (nFeat >= 1) logit += cw[0] * z1;
            if (nFeat >= 2) logit += cw[1] * z2;
            if (nFeat >= 3) logit += cw[2] * z3;
            if (nFeat >= 4) logit += cw[3] * z4;
            if (nFeat >= 5) logit += cw[4] * z5;
            if (nFeat >= 6) logit += cw[5] * z6;
            if (nFeat >= 7) logit += cw[6] * z7;
            if (nFeat >= 8) logit += cw[7] * z8;
            if (nFeat >= 9) logit += cw[8] * z9;
            return logit;
        }
        return (z1 + z2 + z3 + z4) / 4.0f; // fallback: equal weight
    }

    // -----------------------------------------------------------------------
    // Feature 1: per-script open-addressing codepoint-bigram lookup
    // -----------------------------------------------------------------------

    /**
     * Mean log-prob over the codepoint pairs in {@code text} using the given
     * script's bigram tables.
     *
     * <p>For each adjacent codepoint pair {@code (a, b)}:
     * <ol>
     *   <li>Binary-search both codepoints in the script's codepoint index.
     *       If either is absent, the pair was never seen in training; emit
     *       {@code α * (logP(a) + logP(b))} using each codepoint's unigram
     *       value (or {@link BigramTables#unigramFallbackLogProb} if the
     *       codepoint isn't even in the unigram index).</li>
     *   <li>Otherwise, look up the packed {@code (idxA<<16)|idxB} key in
     *       the open-addressing bigram table.  Empty slot → unseen pair →
     *       unigram backoff (same formula).  Match → dequantize the stored
     *       value.</li>
     * </ol>
     *
     * <p>This is the single authoritative implementation of the bigram
     * scoring math, shared by inference and training.  Keeping one
     * implementation eliminates the risk of train/infer drift in the F1
     * feature.
     *
     * @return mean log-prob, or {@link Double#NaN} if {@code text} has fewer
     *         than two codepoints or {@code tables} is null
     */
    public static double computeF1MeanLogP(String text, BigramTables tables) {
        if (text == null || text.length() < 2 || tables == null) {
            return Double.NaN;
        }
        return computeF1MeanLogP(text.codePoints().toArray(), tables);
    }

    /**
     * Codepoint-array form of {@link #computeF1MeanLogP(String, BigramTables)}.
     *
     * <p>Operates on a pre-decoded codepoint sequence rather than a
     * {@code String} so callers (e.g. {@link #forEachScriptBigram} bucketing)
     * can score arbitrary codepoint pairs without re-encoding.  Same math —
     * this is the single authoritative implementation; the {@code String}
     * overload just decodes and delegates.
     */
    public static double computeF1MeanLogP(int[] cps, BigramTables tables) {
        if (cps == null || cps.length < 2 || tables == null) {
            return Double.NaN;
        }
        // Every adjacent pair is scored.  The old whitespace+whitespace skip
        // (HTML-indentation guard) is gone: whitespace is now COMMON and lives
        // in its own COMMON run/table, so it no longer pollutes a per-script
        // mean.  Dropping the skip also makes the training tally trivially
        // match this scorer — both just count every adjacent pair.
        double sum = 0;
        int n = 0;
        int prevCp = -1;
        int prevIdx = -1;
        for (int cp : cps) {
            int curIdx = codepointToIndex(tables, cp);
            if (prevCp >= 0) {
                sum += scorePairF1(prevCp, prevIdx, cp, curIdx, tables);
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
    public static int codepointToIndex(BigramTables tables, int cp) {
        return java.util.Arrays.binarySearch(tables.codepointIndex, cp);
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
    /** Small per-bigram log-prob penalty subtracted from the case-folded
     *  (lowercase) value when scoring an uppercase pair.  All-caps is a genuinely
     *  weaker/rarer signal than lowercase, so it should score a hair BELOW its
     *  lowercase form, not equal to it — and the margin guards the edge case where
     *  an all-caps *mojibake* decode whose lowercase twin happens to be a seen
     *  bigram would otherwise score like real lowercase text.  Kept small (0.25):
     *  the lowercase/junk margin is ~0.8 logit, and δ=0.5 thinned it to ~0.1, so
     *  0.25 leaves all-caps clearly clean (~0.5 above junk) while honoring the
     *  "somewhat less languagey" principle. */
    private static final double CASE_FOLD_PENALTY = 0.25;

    private static double scorePairF1(int cpA, int idxA, int cpB, int idxB,
                                         BigramTables tables) {
        double direct = Double.NaN;
        if (idxA >= 0 && idxB >= 0) {
            int slot = lookupBigramSlot(tables, idxA, idxB);
            if (slot >= 0) {
                direct = dequantize(tables.bigramValues[slot],
                        tables.bigramQuantMin, tables.bigramQuantMax);
            }
        }
        // Case-folded backoff: an ALL-UPPERCASE pair that is the case variant of
        // a SEEN lowercase pair is real text wearing a different case (all-caps
        // headings / emphasis, e.g. Greek "ΚΑΤΑΛΟΓΟΣ", Russian "МУЗЕЙ"), NOT junk.
        // Score it as the BETTER of its own log-prob and its lowercase twin's —
        // i.e. max(direct, fold).  max (not fold-only-on-miss) is essential: real
        // all-caps bigrams ARE present in training (from headings) but rare, so the
        // direct lookup hits a low value (МУ −12.4 vs lowercase му −6.7) and would
        // otherwise bypass the fold and floor.  This is the discriminator raw
        // probability cannot be: all-caps real text and all-caps mojibake are both
        // improbable, but only real text has a SEEN lowercase twin.  Gated on BOTH
        // codepoints being uppercase (case-CONSISTENT) so alternating-case junk
        // ("tHiS") stays unfolded and floors; and only the lowercase twin's value
        // is borrowed when that pair is actually seen, so all-caps mojibake
        // (lowercase form also unseen) floors.
        // Gate = "at least one uppercase letter AND no LOWERCASE letter" — so it
        // folds both an interior all-caps pair (МУ) AND an edge pair where the other
        // side is a sentinel or glue (^М, Й$, "М."), but NOT a mixed-case pair (the
        // lowercase letter in "aB"/"tHiS" trips the gate, so case-inconsistent junk
        // still floors).  Each uppercase letter is folded; sentinels/digits/glue
        // pass through unchanged.  Folding the edges too is what fully rescues short
        // all-caps headings, whose ^X/X$ bigrams would otherwise floor on the rare
        // uppercase-letter unigram backoff.
        boolean upperA = Character.isValidCodePoint(cpA) && Character.isUpperCase(cpA);
        boolean upperB = Character.isValidCodePoint(cpB) && Character.isUpperCase(cpB);
        boolean lowerA = Character.isValidCodePoint(cpA) && Character.isLowerCase(cpA);
        boolean lowerB = Character.isValidCodePoint(cpB) && Character.isLowerCase(cpB);
        if ((upperA || upperB) && !(lowerA || lowerB)) {
            int lcA = upperA ? Character.toLowerCase(cpA) : cpA;
            int lcB = upperB ? Character.toLowerCase(cpB) : cpB;
            if (lcA != cpA || lcB != cpB) {
                int lcIdxA = codepointToIndex(tables, lcA);
                int lcIdxB = codepointToIndex(tables, lcB);
                if (lcIdxA >= 0 && lcIdxB >= 0) {
                    int slot = lookupBigramSlot(tables, lcIdxA, lcIdxB);
                    if (slot >= 0) {
                        double fold = dequantize(tables.bigramValues[slot],
                                tables.bigramQuantMin, tables.bigramQuantMax)
                                - CASE_FOLD_PENALTY;
                        return Double.isNaN(direct) ? fold : Math.max(direct, fold);
                    }
                }
            }
        }
        if (!Double.isNaN(direct)) {
            return direct;
        }
        // Unigram backoff for unseen pair or for codepoints absent from the
        // per-script index.  α=1.0 = plain independence.
        double ua = unigramLogProb(tables, idxA);
        double ub = unigramLogProb(tables, idxB);
        return tables.backoffAlpha * (ua + ub);
    }

    /**
     * Open-addressing lookup: returns the slot index that contains the key
     * for {@code (idxA, idxB)}, or {@code -1} if not present (probe hit an
     * empty slot first).
     *
     * <p>{@code bigramKeys} is sorted ascending (signed), so this is a binary search.
     */
    static int lookupBigramSlot(BigramTables tables, int idxA, int idxB) {
        int packedKey = packBigramKey(idxA, idxB);
        int slot = java.util.Arrays.binarySearch(tables.bigramKeys, packedKey);
        return slot >= 0 ? slot : -1;
    }

    private static double unigramLogProb(BigramTables tables, int idx) {
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
     * string against this model's loaded tables.  Uses the int16-quantized
     * lookup; the public static {@link #computeZ4ScriptTransition}
     * float[] variant remains for trainer use.
     */
    private float computeScriptTransitionZ(String text) {
        if (scriptTransitionTable == null || scriptTransitionCalibration == null
                || scriptBucketIndex == null || numScriptBuckets == 0) {
            return 0f;
        }
        int otherBucket = numScriptBuckets - 1;
        float min = scriptTransitionTableQuant[0];
        float scale = (scriptTransitionTableQuant[1] - min) / 65535f;
        int prev = -1;
        double sum = 0;
        int count = 0;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            Character.UnicodeScript s = TextQualityFeatures.scriptOf(cp);
            if (s == Character.UnicodeScript.COMMON
                    || s == Character.UnicodeScript.INHERITED
                    || s == Character.UnicodeScript.UNKNOWN) {
                continue;
            }
            int bucket = scriptBucketIndex.getOrDefault(s.name(), otherBucket);
            if (prev >= 0) {
                int val = scriptTransitionTable[prev * numScriptBuckets + bucket] & 0xFFFF;
                sum += min + val * scale;
                count++;
            }
            prev = bucket;
        }
        if (count == 0) {
            return 0f;
        }
        return ((float) (sum / count) - scriptTransitionCalibration[0])
                / scriptTransitionCalibration[1];
    }


    /** Model script key for the pooled COMMON (digits/punctuation/symbols) table. */
    public static final String COMMON_SCRIPT = "COMMON";

    /** Sentinel "codepoints" (above the Unicode maximum U+10FFFF, so they cannot
     *  collide with real text) that wrap each letter token: TOKEN_START (^) before
     *  the first letter of a run and TOKEN_END ($) after the last.  Emitted by
     *  {@link #forEachScriptBigram} so the bigram LM learns word-initial / word-
     *  final letter typicality, and so z1 never empties for text containing even a
     *  single letter. */
    public static final int TOKEN_START = 0x110000;
    public static final int TOKEN_END = 0x110001;

    /** COMMON-class predicate: COMMON, INHERITED, UNKNOWN all pool into COMMON. */
    static String classKey(int cp) {
        Character.UnicodeScript s = TextQualityFeatures.scriptOf(cp);
        if (s == Character.UnicodeScript.COMMON
                || s == Character.UnicodeScript.INHERITED
                || s == Character.UnicodeScript.UNKNOWN) {
            return COMMON_SCRIPT;
        }
        return SCRIPT_MODEL_FALLBACK.getOrDefault(s.name(), s.name());
    }

    /** Per-script F1 bigram tables (package-private, for diagnostics). */
    BigramTables f1TablesFor(String script) {
        return f1TablesByScript.get(script);
    }

    /** Per-script z1 calibration {mu, sigma} (package-private, for diagnostics). */
    float[] calibrationFor(String script) {
        return calibrations.get(script);
    }

    /** Per-script F1 bigram tables view (package-private, for diagnostics). */
    Map<String, BigramTables> f1TablesByScriptView() {
        return f1TablesByScript;
    }

    // -----------------------------------------------------------------------
    // Word-level bigram enumeration (the keystone).
    // Single source of truth for BOTH inference z1 scoring and training tally.
    // The codepoint stream is tokenized into maximal same-script runs:
    //   - LETTERs (Lu/Ll/Lt/Lm/Lo) are the scoreable content, bucketed by script;
    //   - combining MARKs (Mn/Me/Mc) attach to the current run (so NFD accents,
    //     Arabic harakat, Indic matras, Thai vowel signs stay inside their word);
    //   - GLUE — every other non-letter that is NOT whitespace/NUL and NOT a decode
    //     anomaly (punctuation, symbols, numbers) — ALSO attaches to the open run
    //     and IS scored, at codepoint resolution.  This is what lets z1 catch a
    //     wrong-charset symbol wedged mid-word: the LM learns letter->'.' is common
    //     but letter->U+2030 is ~0, so 'Hausj‰rven' (Latin-sibling misdecode) floors
    //     while 'Hausjärven' (a real accented letter) does not.  Resolution is the
    //     codepoint, never the Unicode category — '%', U+2030, U+2020 are all Po
    //     like '.', so a typed/binned boundary would hide them behind the period's
    //     frequency (measured: letter->'.' = 235k vs letter->U+2030 = 0 in LATIN);
    //   - BOUNDARIES that split a run and emit NOTHING: whitespace and NUL (word /
    //     structure separators) and the decode-anomaly set (U+FFFD / C1 / anomalous
    //     Cc / PUA), whose penalty is carried solely by z6 (anomaly ratio) and z3,
    //     NEVER z1 — keeping anomalies out of z1 is what stops z1 cannibalizing the
    //     FFFD signal z6 owns;
    //   - a letter-script change is also a boundary (cross-script structure is z4/z9).
    // Each run is wrapped TOKEN_START (^) ... TOKEN_END ($) so the LM learns
    // word-initial/final typicality and never empties for text with even one letter.
    // -----------------------------------------------------------------------

    /** Sink for {@link #forEachScriptBigram}: (modelScript, cpA, cpB). */
    @FunctionalInterface
    public interface BigramSink {
        void accept(String script, int a, int b);
    }

    /** True for letter codepoints (Lu/Ll/Lt/Lm/Lo) — the scoreable token content
     *  that forms per-script runs.  {@code type} is {@link Character#getType}. */
    static boolean isLetterCp(int type) {
        return type == Character.UPPERCASE_LETTER
                || type == Character.LOWERCASE_LETTER
                || type == Character.TITLECASE_LETTER
                || type == Character.MODIFIER_LETTER
                || type == Character.OTHER_LETTER;
    }

    /** True for combining marks (Mn/Me/Mc) — they attach to the current run
     *  rather than splitting it.  {@code type} is {@link Character#getType}. */
    static boolean isMarkCp(int type) {
        return type == Character.NON_SPACING_MARK
                || type == Character.ENCLOSING_MARK
                || type == Character.COMBINING_SPACING_MARK;
    }

    /**
     * Enumerates the script-bucketed bigrams of {@code cps} under the redesign
     * representation (see block comment above).  Used identically by inference
     * (score each emitted bigram) and training (tally each emitted bigram), so
     * the two can never drift.
     */
    /**
     * Per-script {@code {sumLogP, count}} buckets for a codepoint sequence,
     * scored with the given per-script tables.  This is exactly the z1 input
     * {@link #aggregate} computes, exposed so the trainer's calibration and the
     * inference scorer cannot drift.
     */
    public static Map<String, double[]> bucketSumsAndCounts(
            int[] cps, Map<String, BigramTables> tablesByScript) {
        Map<String, double[]> buckets = new HashMap<>();
        forEachScriptBigram(cps, (script, a, b) -> {
            BigramTables t = tablesByScript.get(script);
            if (t == null) {
                return;
            }
            double lp = computeF1MeanLogP(new int[]{a, b}, t);
            if (Double.isNaN(lp)) {
                return;
            }
            double[] bk = buckets.computeIfAbsent(script, k -> new double[2]);
            bk[0] += lp;
            bk[1] += 1;
        });
        return buckets;
    }

    public static void forEachScriptBigram(int[] cps, BigramSink sink) {
        if (cps == null || cps.length == 0) {
            return;
        }
        String curScript = null;   // script of the run in progress; null = no open run
        int prev = -1;             // previous codepoint in the open run (left side of next bigram)
        for (int cp : cps) {
            int type = Character.getType(cp);
            if (isLetterCp(type)) {
                String sc = classKey(cp);
                if (curScript != null && sc.equals(curScript)) {
                    sink.accept(curScript, prev, cp);          // within-run letter bigram
                } else {
                    if (curScript != null) {
                        sink.accept(curScript, prev, TOKEN_END);   // close the prior run
                    }
                    curScript = sc;
                    sink.accept(curScript, TOKEN_START, cp);        // open a new run
                }
                prev = cp;
            } else if (isBoundaryCp(cp)) {
                // WORD/STRUCTURE boundary (whitespace, NUL) or a decode anomaly
                // (U+FFFD / C1 / anomalous Cc / PUA — scored by z6/z3, never z1):
                // close the run, emit nothing.
                if (curScript != null) {
                    sink.accept(curScript, prev, TOKEN_END);
                    curScript = null;
                    prev = -1;
                }
            } else if (curScript != null) {
                // GLUE (punctuation / symbol / number) or a combining MARK inside an
                // open run: attach and SCORE it at codepoint resolution.  This is the
                // intrusion signal: the LM learns letter->'.' is common but
                // letter->U+2030 (per-mille) is ~0, so a wrong-charset symbol wedged
                // mid-word (the Latin-sibling misdecode, e.g. 'Hausj‰rven') floors
                // z1, while a clean accented letter (a real letter, scored as a letter
                // bigram) does not.  Resolution is the codepoint, never the Unicode
                // category: '%', U+2030, U+2020 are all Po like '.', so binning by
                // category would hide them behind the period's huge frequency.
                sink.accept(curScript, prev, cp);
                prev = cp;
            }
            // else: orphan glue/mark with no open run -> nothing to attach to, skip.
        }
        if (curScript != null) {
            sink.accept(curScript, prev, TOKEN_END);            // close the final run
        }
    }

    /** True for codepoints that BREAK a run without being scored in z1: whitespace
     *  and NUL (word/structure boundaries) plus the z6/z3 decode-anomaly set
     *  ({@link TextQualityFeatures#isAnomalyCodepoint} — U+FFFD, C1, anomalous Cc,
     *  private-use).  Every other non-letter (punctuation, symbol, number) is GLUE:
     *  it attaches to the open run and is scored, so the LM can floor a symbol
     *  wedged mid-word while keeping the anomaly penalty solely in z6 (so z1 never
     *  cannibalizes the FFFD signal). */
    static boolean isBoundaryCp(int cp) {
        return cp == 0x00
                || Character.isWhitespace(cp)
                || Character.isSpaceChar(cp)
                || TextQualityFeatures.isAnomalyCodepoint(cp);
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
            Character.UnicodeScript s = TextQualityFeatures.scriptOf(cp);
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
