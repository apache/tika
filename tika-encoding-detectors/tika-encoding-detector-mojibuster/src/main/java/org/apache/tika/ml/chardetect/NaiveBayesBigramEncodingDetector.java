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
package org.apache.tika.ml.chardetect;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.commons.io.IOUtils;

import org.apache.tika.detect.EncodingDetector;
import org.apache.tika.detect.EncodingResult;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;

/**
 * Naive-Bayes byte-bigram charset classifier.  Drop-in for ICU4J /
 * juniversalchardet.  No structural gates, no language-aware
 * arbitration — just: count probe byte-bigrams, score each candidate
 * charset by log-likelihood of the bigram sequence under its trained
 * bigram distribution, return ranked candidates.
 *
 * <p>Model format and training described in
 * {@code TrainNaiveBayesBigram}.  Per-class data: top-K kept bigrams
 * with log-probabilities + a log-probability floor for
 * out-of-vocabulary bigrams (Laplace add-α smoothing).</p>
 *
 * <p><strong>In-memory layout — bigram-major:</strong>
 * {@code logP[bigram * numClasses + classIdx]}.  For the inner-loop
 * hot path, all class log-probs for one bigram sit in
 * {@code numClasses * 4 bytes} of consecutive memory — one or two
 * L1 cache lines vs the 256 KB stride of a class-major layout.</p>
 */
public class NaiveBayesBigramEncodingDetector implements EncodingDetector {

    private static final int MAGIC_V3 = 0x4E424233; // "NBB3"
    private static final int BIGRAM_SPACE = 65_536;

    /**
     * Cap probe scanning at 10 KB.  Bigram-based identification
     * saturates quickly — beyond the first 500-1000 bytes every
     * additional bigram nudges scores by &lt; 0.1 log-likelihood and
     * doesn't change the argmax.  Reducing the cap from 4 KB to 1 KB
     * quartes the inner-loop work on long probes at no measurable
     * accuracy cost.
     */
    private static final int MAX_PROBE_BYTES = 16 * 1024;

    /**
     * Default number of top candidates to return.  Most callers only
     * look at top-1 or top-3; returning all {@code numClasses}
     * candidates wastes allocations on never-read entries.
     */
    private static final int DEFAULT_TOP_K = 5;

    /**
     * Per-scored-bigram log-score margin (in nats) that defines
     * "model is reliably right" vs "model is genuinely uncertain
     * between candidates."  Derived from a calibration run over the
     * 158K-sample devtest split:
     *
     * <ul>
     *   <li>CORRECT picks have top-1-vs-top-2 margin median ≈
     *       1.5–2 nats/bg, with p10 ≥ 0.22 nats/bg in every
     *       length bucket.</li>
     *   <li>WRONG picks have margin p90 &lt; 0.10 nats/bg in every
     *       length bucket.</li>
     * </ul>
     *
     * <p>A threshold of 0.20 cleanly separates the two regimes.
     * Candidates within {@code MARGIN_THRESHOLD_NATS_PER_BIGRAM} of
     * top-1's score (i.e., the model can't reliably tell them apart
     * from top-1) are emitted into the candidate pool for downstream
     * arbitration; candidates further away are dropped.</p>
     *
     * <p>Softmax-based confidence is deliberately not used here:
     * softmax saturates to 1.0 on essentially every probe regardless
     * of how uncertain the model actually is, so it cannot serve as
     * a candidate-emission gate.</p>
     */
    public static final double MARGIN_THRESHOLD_NATS_PER_BIGRAM = 0.20;

    /**
     * Per-bigram cross-class total-contribution cap (Type C clipping).
     * For each distinct bigram in the probe, the top-scoring class's
     * total contribution (count × logP × idf, after dequantization) is
     * capped at the runner-up class's contribution + this many nats.
     *
     * <p>Defends against corpus-skew pathologies where one class
     * accumulates extreme bigram mass that swings classification on
     * one or two byte-pairs alone (e.g., Czech "ČR" digraph in
     * ISO-8859-2 contributing +186 nats over win-1252 on Italian text).
     * Length-invariant by construction: the cap is on per-bigram
     * advantage, regardless of how many times the bigram appears.</p>
     *
     * <p>20 nats = e^20 ≈ 5×10^8 probability-ratio advantage per
     * bigram — preserves legitimate CJK-vs-Latin and other cross-script
     * signal while bounding the diffuse-corpus-skew tail.</p>
     */
    public static final double CAP_PER_BIGRAM_NATS = 20.0;

    /**
     * Minimum distinct bigrams required before the per-bigram cap
     * applies.  On short probes, each bigram carries proportionally
     * more signal — clipping would destroy more discrimination than
     * it saves.
     */
    public static final int MIN_DISTINCT_FOR_CAP = 30;

    /**
     * Minimum distinct-bigram fraction of total-scored-bigrams.  Below
     * this, the input is treated as degenerate (looped / repeated /
     * corrupt) and {@link #scoreClassesAndCount(byte[])} returns
     * {@code null} so callers can fall back.  Defends against pathological
     * inputs like {@code "thththth..."} where one bigram appears
     * hundreds of times.
     */
    public static final double MIN_DIVERSITY_RATIO = 0.02;

    /**
     * Minimum scored bigrams required before the diversity gate
     * applies.  Short probes legitimately have lower diversity ratios
     * (fewer total bigrams = fewer opportunities for distinct ones)
     * and shouldn't be gated as degenerate.  Above this floor, the
     * ratio measurement is meaningful.
     */
    public static final int MIN_BIGRAMS_FOR_DIVERSITY_GATE = 100;

    private final String[] labels;
    /** Charset objects cached at load — one {@code Charset.forName} per class, ever. */
    private final Charset[] charsets;
    /**
     * Bigram-major int8 logP layout.  Quantized at load time via
     * per-class scale {@code scale[c] = maxAbs(class c's logP column) / 127}.
     * In-memory footprint: {@code 65_536 × numClasses} bytes ≈ 2 MB for
     * 32 classes, 4× smaller than float32.  The hot-loop accumulates
     * raw int8 products and applies dequantization once at the end of
     * the probe, CharSoup-style.
     */
    private final byte[] logP8;
    /**
     * Global per-bigram IDF = log((C+1)/(df_i+1)) baked in at training,
     * quantized to int8 at load via {@code idfScale = maxAbs(idf)/127}.
     * IDF is non-negative so int8 values land in [0, 127].  Zero means
     * "bigram appears in every class, no signal" and is the hot-loop
     * skip condition.
     */
    private final byte[] idf8;
    /**
     * Per-class dequantization constant folded from
     * {@code scale[c] * idfScale / logVocabSize[c]}.  Applied once per
     * class at the end of the probe to convert int accumulator to the
     * final log-score.  Keeping {@code log V(c)} in the dequant
     * constant preserves the B-3 per-class score normalization from
     * the float-path at zero additional cost.
     */
    private final double[] perClassDequant;
    private final int numClasses;

    public NaiveBayesBigramEncodingDetector(Path modelPath) throws IOException {
        this(Files.newInputStream(modelPath));
    }

    public NaiveBayesBigramEncodingDetector(InputStream modelStream) throws IOException {
        try (DataInputStream dis = new DataInputStream(modelStream)) {
            int magic = dis.readInt();
            if (magic != MAGIC_V3) {
                throw new IOException(String.format(java.util.Locale.ROOT,
                        "Bad magic 0x%08X, expected 0x%08X (NBB3)", magic, MAGIC_V3));
            }
            int version = dis.readInt();
            if (version != 3) {
                throw new IOException("Unsupported NB bigram model version: " + version);
            }
            this.numClasses = dis.readInt();
            this.labels = new String[numClasses];
            this.charsets = new Charset[numClasses];

            // Read quantized IDF table + scale.
            float idfScale = dis.readFloat();
            this.idf8 = new byte[BIGRAM_SPACE];
            dis.readFully(idf8);

            // Per-class headers + trained pairs.  Build the dense
            // bigram-major logP8 array by filling with each class's
            // quantized unseen floor, then overwriting with trained
            // pairs.
            float[] scale = new float[numClasses];
            byte[] unseenQ = new byte[numClasses];
            int[] vocabSizes = new int[numClasses];
            this.logP8 = new byte[BIGRAM_SPACE * numClasses];

            for (int c = 0; c < numClasses; c++) {
                int labelLen = dis.readUnsignedShort();
                byte[] lbl = new byte[labelLen];
                dis.readFully(lbl);
                labels[c] = new String(lbl, StandardCharsets.UTF_8);
                // Cache Charset lookup; tolerate training-only labels
                // that aren't resolvable (bidi-suffixed EBCDIC, Mac
                // variants).  Null = emit-skip at detect time.
                Charset cs;
                try {
                    cs = Charset.forName(toJavaCharsetName(labels[c]));
                } catch (Exception ex) {
                    cs = null;
                }
                charsets[c] = cs;

                scale[c] = dis.readFloat();
                unseenQ[c] = dis.readByte();
                int vocabSize = dis.readInt();
                vocabSizes[c] = vocabSize;

                // Pre-fill this class's column with the unseen floor.
                byte u = unseenQ[c];
                for (int bg = 0; bg < BIGRAM_SPACE; bg++) {
                    logP8[bg * numClasses + c] = u;
                }
                // Overwrite with trained pairs.
                for (int i = 0; i < vocabSize; i++) {
                    int bigram = dis.readUnsignedShort();
                    byte q = dis.readByte();
                    logP8[bigram * numClasses + c] = q;
                }
            }

            // Per-class dequant constant = scale[c] × idfScale.
            // (B-3 per-class score normalization by log V(c) was
            // removed after empirically backfiring on probes where a
            // class with large V has no trained-bigram matches on the
            // probe: the larger logV divisor shrinks the unseen-heavy
            // penalty, making unmatched-but-large-V classes score
            // artificially well.  Keeping raw scale-dequant preserves
            // correct unseen-floor penalty magnitudes.)
            this.perClassDequant = new double[numClasses];
            for (int c = 0; c < numClasses; c++) {
                perClassDequant[c] = (double) scale[c] * idfScale;
            }
        }
    }

    @Override
    public List<EncodingResult> detect(TikaInputStream tis, Metadata metadata,
                                       ParseContext parseContext) throws IOException {
        return detect(readProbe(tis));
    }

    /** ASCII whitespace: TAB, LF, VT, FF, CR, SPACE. */
    private static boolean isWhitespace(int b) {
        return b == 0x09 || b == 0x0a || b == 0x0b || b == 0x0c
                || b == 0x0d || b == 0x20;
    }

    public List<EncodingResult> detect(byte[] probe) {
        ScoreResult sr = scoreClassesAndCount(probe);
        if (sr == null) {
            return Collections.emptyList();
        }
        return emitCandidates(sr.scores, sr.scoredBigrams);
    }

    /**
     * Score result returned by {@link #scoreClassesAndCount(byte[])}.
     * Exposes the raw per-class score vector together with the number
     * of bigrams that actually contributed to the dot product (i.e.,
     * bigrams with non-zero IDF and not skipped by the whitespace-pair
     * rule) and the total bigrams in the scored region of the probe.
     * {@code scoredBigrams} is the unit of "evidence available to NB"
     * — robust to HTML / whitespace noise in the input because those
     * bigrams have IDF == 0 and don't contribute.
     */
    public static final class ScoreResult {
        public final double[] scores;
        public final int scoredBigrams;
        public final int totalBigrams;
        public ScoreResult(double[] scores, int scoredBigrams, int totalBigrams) {
            this.scores = scores;
            this.scoredBigrams = scoredBigrams;
            this.totalBigrams = totalBigrams;
        }
    }

    /**
     * Compute the raw per-class score vector for a probe, without
     * top-K extraction or softmax.  Returns {@code null} for null /
     * tiny probes that can't be scored.
     */
    public double[] scoreClasses(byte[] probe) {
        ScoreResult sr = scoreClassesAndCount(probe);
        return sr == null ? null : sr.scores;
    }

    /**
     * Per-bigram contribution to the per-class score, used for
     * diagnostic tools that want to understand why a probe scores
     * one class over another.  Returned by
     * {@link #analyzeBigrams(byte[], int, int)}.
     */
    public static final class BigramContrib {
        public final int bigram;       // (b0 << 8) | b1
        public final double contribA;  // logP_A * idf in nats
        public final double contribB;
        public BigramContrib(int bigram, double a, double b) {
            this.bigram = bigram;
            this.contribA = a;
            this.contribB = b;
        }
        public double diff() {
            return contribA - contribB;
        }
    }

    /**
     * For each scored bigram in the probe (same skip rules as
     * {@link #scoreClasses(byte[])}), compute and return its
     * dequantized contribution to two specified classes' scores.
     * The list is in probe order, with duplicates allowed (a bigram
     * that appears N times in the probe yields N entries).
     */
    public List<BigramContrib> analyzeBigrams(byte[] probe, int classA, int classB) {
        List<BigramContrib> out = new java.util.ArrayList<>();
        if (probe == null || probe.length < 2) {
            return out;
        }
        int len = Math.min(probe.length, MAX_PROBE_BYTES);
        // perClassDequant[c] folds scale[c] × idfScale already, so
        // contribution(bigram, c) = logP8[..c] * idf8[bigram] * perClassDequant[c]
        double dqA = perClassDequant[classA];
        double dqB = perClassDequant[classB];
        for (int i = 0; i + 1 < len; i++) {
            int b0 = probe[i] & 0xFF;
            int b1 = probe[i + 1] & 0xFF;
            if (isWhitespace(b0) && isWhitespace(b1)) {
                continue;
            }
            int bigram = (b0 << 8) | b1;
            int w = idf8[bigram];
            if (w == 0) {
                continue;
            }
            int base = bigram * numClasses;
            double contribA = logP8[base + classA] * w * dqA;
            double contribB = logP8[base + classB] * w * dqB;
            out.add(new BigramContrib(bigram, contribA, contribB));
        }
        return out;
    }

    /**
     * Like {@link #scoreClasses(byte[])} but also reports the number
     * of bigrams that contributed to the dot product vs the total
     * scored region.  Used by offline calibration to bucket samples
     * by "evidence available" rather than raw byte length.
     */
    public ScoreResult scoreClassesAndCount(byte[] probe) {
        if (probe == null || probe.length < 2) {
            return null;
        }
        int len = Math.min(probe.length, MAX_PROBE_BYTES);

        // Pass 1: count distinct bigrams.  Whitespace and zero-IDF
        // bigrams are skipped as in the original hot loop.  short[] is
        // enough since count fits in 16383 (max possible).  Track the
        // ids of distinct bigrams in a parallel array so pass 2 doesn't
        // need to scan the full 65k space.
        short[] count = new short[BIGRAM_SPACE];
        int[] distinctBigrams = new int[len];
        int distinctIdx = 0;
        int scored = 0;
        int total = 0;
        for (int i = 0; i + 1 < len; i++) {
            int b0 = probe[i] & 0xFF;
            int b1 = probe[i + 1] & 0xFF;
            total++;
            if (isWhitespace(b0) && isWhitespace(b1)) {
                continue;
            }
            int bigram = (b0 << 8) | b1;
            int w = idf8[bigram];
            if (w == 0) {
                continue;
            }
            scored++;
            if (count[bigram] == 0) {
                distinctBigrams[distinctIdx++] = bigram;
            }
            count[bigram]++;
        }

        // Type A — diversity gate.  If the input has too few distinct
        // bigrams relative to total scored bigrams, it's a degenerate
        // / looped input ("thththth..." or worse).  Abstain — caller
        // falls back.  Only applied above a minimum scored-bigrams
        // floor, since short probes legitimately have lower diversity
        // ratios.
        if (scored >= MIN_BIGRAMS_FOR_DIVERSITY_GATE
                && (double) distinctIdx / scored < MIN_DIVERSITY_RATIO) {
            return null;
        }

        // Type C — per-bigram total-contribution cap.  Only applies
        // when we have enough distinct bigrams that capping any single
        // one won't destroy a large fraction of the discriminative
        // signal.  Below the floor, short-probe semantics rule: every
        // bigram counts fully.
        boolean applyCap = distinctIdx >= MIN_DISTINCT_FOR_CAP;

        // Pass 2: per distinct bigram, compute per-class total
        // contribution and (when above floor) apply Type C cap.
        double[] score = new double[numClasses];
        double[] contributions = new double[numClasses];
        for (int k = 0; k < distinctIdx; k++) {
            int bigram = distinctBigrams[k];
            int n = count[bigram];
            int w = idf8[bigram];
            double countTimesIdf = (double) n * w;
            int base = bigram * numClasses;

            if (!applyCap) {
                // Fast path: no cap, just accumulate.
                for (int c = 0; c < numClasses; c++) {
                    score[c] += logP8[base + c] * countTimesIdf * perClassDequant[c];
                }
                continue;
            }

            // logPs are negative; "best" class for the bigram = highest
            // (least negative) contribution after dequant.
            double max = Double.NEGATIVE_INFINITY;
            double secondMax = Double.NEGATIVE_INFINITY;
            for (int c = 0; c < numClasses; c++) {
                double contrib = logP8[base + c] * countTimesIdf * perClassDequant[c];
                contributions[c] = contrib;
                if (contrib > max) {
                    secondMax = max;
                    max = contrib;
                } else if (contrib > secondMax) {
                    secondMax = contrib;
                }
            }
            // Cap any class whose contribution exceeds runner-up + cap.
            double cap = secondMax + CAP_PER_BIGRAM_NATS;
            if (max > cap) {
                for (int c = 0; c < numClasses; c++) {
                    if (contributions[c] > cap) {
                        contributions[c] = cap;
                    }
                }
            }
            for (int c = 0; c < numClasses; c++) {
                score[c] += contributions[c];
            }
        }
        return new ScoreResult(score, scored, total);
    }

    public String[] getLabels() {
        return labels.clone();
    }

    public Charset[] getCharsets() {
        return charsets.clone();
    }

    /**
     * Margin-gated candidate emission.  Always emits top-1.  Additional
     * candidates are emitted only when their score is within
     * {@link #MARGIN_THRESHOLD_NATS_PER_BIGRAM} × {@code scoredBigrams}
     * of top-1 — i.e., when the model is genuinely close between
     * top-1 and the alternative.  Cap at {@link #DEFAULT_TOP_K}
     * candidates total.
     *
     * <p>The emitted {@code confidence} value is NOT softmax (which
     * saturates to 1.0 on essentially every probe regardless of true
     * uncertainty — see {@code feedback_no_softmax_for_ood.md}).
     * It's a linear margin distance:</p>
     * <ul>
     *   <li>top-1: 1.0</li>
     *   <li>rank {@code i &gt; 0}:
     *       {@code 1.0 - (top1_score − this_score) / margin_threshold},
     *       clamped to [0.0, 1.0]</li>
     * </ul>
     * <p>A candidate at exactly the margin threshold gets confidence
     * 0.0 and isn't emitted; one at half the threshold gets 0.5;
     * top-1 always gets 1.0.</p>
     */
    private List<EncodingResult> emitCandidates(double[] score, int scoredBigrams) {
        if (scoredBigrams <= 0) {
            // No evidence at all — emit nothing.  Higher-level callers
            // (MojibusterEncodingDetector) have their own pure-ASCII /
            // empty-probe fallbacks.
            return Collections.emptyList();
        }
        double marginThreshold = MARGIN_THRESHOLD_NATS_PER_BIGRAM * scoredBigrams;

        int k = Math.min(DEFAULT_TOP_K, numClasses);
        int[] idx = new int[k];
        double[] val = new double[k];
        Arrays.fill(idx, -1);
        Arrays.fill(val, Double.NEGATIVE_INFINITY);
        for (int c = 0; c < numClasses; c++) {
            double s = score[c];
            if (s > val[k - 1]) {
                int pos = k - 1;
                while (pos > 0 && val[pos - 1] < s) {
                    val[pos] = val[pos - 1];
                    idx[pos] = idx[pos - 1];
                    pos--;
                }
                val[pos] = s;
                idx[pos] = c;
            }
        }
        if (idx[0] < 0) {
            return Collections.emptyList();
        }
        double top1Score = val[0];

        List<EncodingResult> out = new ArrayList<>(k);
        for (int i = 0; i < k; i++) {
            if (idx[i] < 0) {
                break;
            }
            Charset cs = charsets[idx[i]];
            if (cs == null) {
                continue;
            }
            double gap = top1Score - val[i];
            if (i > 0 && gap >= marginThreshold) {
                break;
            }
            double conf = (i == 0) ? 1.0
                    : Math.max(0.0, 1.0 - gap / marginThreshold);
            out.add(new EncodingResult(cs, (float) conf,
                    labels[idx[i]], EncodingResult.ResultType.STATISTICAL));
        }
        return out;
    }

    /**
     * Map training labels to Java canonical charset names.  Training
     * uses several synthetic label conventions Java doesn't recognize:
     * <ul>
     *   <li>Internal hyphens: {@code "UTF-16-LE" → "UTF-16LE"}</li>
     *   <li>Bidi-suffix variants: {@code "IBM420-ltr" / "IBM420-rtl" →
     *       "IBM420"} — same byte charset, only the training-time
     *       reshaping strategy differs.</li>
     * </ul>
     */
    private static String toJavaCharsetName(String label) {
        switch (label) {
            case "UTF-16-LE": return "UTF-16LE";
            case "UTF-16-BE": return "UTF-16BE";
            case "UTF-32-LE": return "UTF-32LE";
            case "UTF-32-BE": return "UTF-32BE";
            case "IBM420-ltr":
            case "IBM420-rtl": return "IBM420";
            case "IBM424-ltr":
            case "IBM424-rtl": return "IBM424";
            // Java uses camelcase for the Mac Cyrillic code page; the
            // training-filename convention is lowercase.  Without this
            // mapping every x-mac-cyrillic candidate was silently dropped
            // at detect time because Charset.forName threw on
            // "x-mac-cyrillic".
            case "x-mac-cyrillic": return "x-MacCyrillic";
            // Java's canonical Thai code page prefixes "x-".
            case "windows-874":   return "x-windows-874";
            default: return label;
        }
    }

    private static byte[] readProbe(TikaInputStream tis) throws IOException {
        tis.mark(MAX_PROBE_BYTES);
        byte[] buf = new byte[MAX_PROBE_BYTES];
        try {
            int n = IOUtils.read(tis, buf);
            if (n < buf.length) {
                byte[] trimmed = new byte[n];
                System.arraycopy(buf, 0, trimmed, 0, n);
                return trimmed;
            }
            return buf;
        } finally {
            tis.reset();
        }
    }

    public int getNumClasses() {
        return numClasses;
    }

    public String getLabel(int idx) {
        return labels[idx];
    }
}
