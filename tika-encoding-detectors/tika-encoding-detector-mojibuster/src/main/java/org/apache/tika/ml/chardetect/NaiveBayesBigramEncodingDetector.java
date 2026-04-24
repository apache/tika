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
     * Cap probe scanning at 1 KB.  Bigram-based identification
     * saturates quickly — beyond the first 500-1000 bytes every
     * additional bigram nudges scores by &lt; 0.1 log-likelihood and
     * doesn't change the argmax.  Reducing the cap from 4 KB to 1 KB
     * quartes the inner-loop work on long probes at no measurable
     * accuracy cost.
     */
    private static final int MAX_PROBE_BYTES = 1024;

    /**
     * Default number of top candidates to return.  Most callers only
     * look at top-1 or top-3; returning all {@code numClasses}
     * candidates wastes allocations on never-read entries.
     */
    private static final int DEFAULT_TOP_K = 5;

    /**
     * Minimum softmax confidence for a candidate to be emitted.  When
     * NB is very confident (e.g., top = 0.93 on a long clean EBCDIC
     * probe), the lower-ranked candidates' softmax values fall to ≤
     * 1e-3 and contribute nothing to downstream arbitration except
     * noise.  Low-confidence alternatives also give language-based
     * arbitrators (CharSoup) opportunities to pick cross-script
     * decodings that happen to look like valid letters of another
     * script — documented failure mode: English CP500 bytes decoded
     * as IBM424 produce all-Hebrew letters that CharSoup's language
     * model scores as "clean Hebrew" with high margin, beating
     * IBM500's "English with Latin siblings" fit.  Dropping noise
     * candidates removes the opportunity.
     */
    private static final double MIN_EMIT_CONFIDENCE = 0.01;

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

    public List<EncodingResult> detect(byte[] probe) {
        if (probe == null || probe.length < 2) {
            return Collections.emptyList();
        }
        int len = Math.min(probe.length, MAX_PROBE_BYTES);

        // Integer hot loop — CharSoup-style.  int8 logP × int8 IDF →
        // int16 product, accumulated into int32 per class.  Overflow
        // safety: at MAX_PROBE_BYTES=1024, max 1023 bigrams × 127 × 127
        // ≈ 16.5M per class, well inside int32's 2.1B headroom.
        int[] dots = new int[numClasses];
        for (int i = 0; i + 1 < len; i++) {
            int bigram = ((probe[i] & 0xFF) << 8) | (probe[i + 1] & 0xFF);
            int w = idf8[bigram];  // non-negative, 0..127
            if (w == 0) {
                continue; // bigram appears in every class; no signal
            }
            int base = bigram * numClasses;
            for (int c = 0; c < numClasses; c++) {
                dots[c] += logP8[base + c] * w;
            }
        }

        // Single per-class dequantization at end of probe.  The
        // perClassDequant constant folds scale[c] × idfScale ×
        // (1/logVocabSize[c]) into one float — the B-3 per-class
        // score normalization comes for free.
        double[] score = new double[numClasses];
        for (int c = 0; c < numClasses; c++) {
            score[c] = dots[c] * perClassDequant[c];
        }

        return topK(score, DEFAULT_TOP_K);
    }

    /**
     * Bounded top-K extraction via insertion sort on a size-K primitive
     * array.  Avoids {@code Integer[]} boxing + comparator callbacks of
     * {@code Arrays.sort} with a comparator.  O(N·K) comparisons total;
     * for K=5, N=35 that's &lt; 180 comparisons, comparable to an O(N
     * log N) sort but with zero allocation beyond the K-sized buffers.
     *
     * <p>Confidence is softmax over the top-K log-likelihoods only —
     * 5 exp() calls instead of numClasses.</p>
     */
    private List<EncodingResult> topK(double[] score, int k) {
        k = Math.min(k, numClasses);
        int[] idx = new int[k];
        double[] val = new double[k];
        Arrays.fill(idx, -1);
        Arrays.fill(val, Double.NEGATIVE_INFINITY);

        for (int c = 0; c < numClasses; c++) {
            double s = score[c];
            if (s > val[k - 1]) {
                // Shift-right insertion into sorted-desc buffer.
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

        // Softmax over top-K only.
        double maxScore = val[0];
        double sumExp = 0.0;
        double[] expBuf = new double[k];
        int filled = 0;
        for (int i = 0; i < k; i++) {
            if (idx[i] < 0) {
                break;
            }
            expBuf[i] = Math.exp(val[i] - maxScore);
            sumExp += expBuf[i];
            filled = i + 1;
        }

        List<EncodingResult> out = new ArrayList<>(filled);
        for (int i = 0; i < filled; i++) {
            Charset cs = charsets[idx[i]];
            if (cs == null) {
                continue; // training-only label with no Java charset
            }
            double conf = expBuf[i] / sumExp;
            // Always emit top-1 (even if tiny — at least one result
            // keeps the pipeline from going empty).  For the rest,
            // drop below MIN_EMIT_CONFIDENCE: those are noise and
            // cause CharSoup to pick cross-script decodings.
            if (i > 0 && conf < MIN_EMIT_CONFIDENCE) {
                break;
            }
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
