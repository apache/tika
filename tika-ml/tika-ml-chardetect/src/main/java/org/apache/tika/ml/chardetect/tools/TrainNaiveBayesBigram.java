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
package org.apache.tika.ml.chardetect.tools;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.GZIPInputStream;

/**
 * Naive-Bayes byte-bigram charset classifier trainer.
 *
 * <p>For each charset, counts all stride-1 byte bigrams across training
 * samples, keeps the top-K most frequent (default 2000), and applies
 * Laplace add-α smoothing for out-of-vocabulary bigrams.  Output is a
 * binary model file consumed by {@code NaiveBayesBigramEncodingDetector}.</p>
 *
 * <p>Standard training-data layout (per
 * {@link BuildCharsetTrainingData}): one {@code <charset>.bin.gz} per
 * class, each containing variable-length [uint16 len][bytes] samples.</p>
 *
 * <p><strong>Usage:</strong></p>
 * <pre>
 *   java TrainNaiveBayesBigram \
 *     --data /path/to/chardet-training \
 *     --output nb-bigram.bin \
 *     [--top-bigrams 2000] \
 *     [--alpha 1.0] \
 *     [--max-samples-per-class 50000] \
 *     [--classes cs1,cs2,...]    # optional class filter
 * </pre>
 *
 * <p><strong>Default class set (35 — v6 shipped model):</strong> listed
 * in {@link #V6_SHIPPED_CLASSES}.  Override with {@code --classes}.</p>
 */
public class TrainNaiveBayesBigram {

    /**
     * Binary magic for the saved model — "NBB3".
     *
     * <p>v3 = v2 with int8 quantization applied at save-time.  Per-class
     * {@code logP} values quantize via {@code scale[c] = maxAbs(class c
     * logP column) / 127}; the global IDF table quantizes via
     * {@code idfScale = maxAbs(idf) / 127}.  Saved file ~2-3× smaller
     * than v2 at same coverage; in-memory footprint 4× smaller because
     * the detector no longer needs to materialize a float array at load.
     */
    public static final int MAGIC = 0x4E424233;
    public static final int VERSION = 3;

    private static final double DEFAULT_ALPHA_BASE = 1.0;
    private static final int DEFAULT_MAX_SAMPLES = 50_000;
    /**
     * Default per-class vocabulary coverage.  0.999 means the top-K
     * most frequent bigrams covering 99.9% of the class's marginal
     * mass are kept.  Observed effective-vocab range on our corpus:
     * 3,741 (windows-1258) to 37,431 (GB18030).
     */
    private static final double DEFAULT_COVERAGE = 0.999;

    /** Full bigram space size (all (b1, b2) pairs). */
    private static final int BIGRAM_SPACE = 65_536;

    /**
     * v6 shipped model class set (33 classes).
     *
     * <p>UTF coverage:
     * <ul>
     *   <li>UTF-32 — handled by {@code WideUnicodeDetector} (structural
     *       codepoint validity).  Not in NB training.</li>
     *   <li>UTF-16-LE / UTF-16-BE — handled by the stride-2
     *       {@code Utf16SpecialistEncodingDetector}.  Not in NB
     *       training: stride-1 byte bigrams cannot distinguish
     *       UTF-16-encoded CJK from legacy byte encodings because
     *       CJK characters in UTF-16 encode to byte pairs that alias
     *       common ASCII bigrams (e.g. U+6572 in UTF-16-LE is
     *       {@code 72 65} which also encodes "re").  See
     *       {@code ~/Desktop/claude-todo/charset/why-stride1-bigrams-dont-work-for-utf16.md}.</li>
     *   <li>UTF-8 — trained NB class.  UTF-8's lead + continuation
     *       byte-pair structure has distinctive frequency signatures
     *       that don't alias with legacy encodings at the bigram-
     *       frequency level.  The structural {@code NOT_UTF8} check
     *       acts as a post-NB disqualifier: if byte grammar proves
     *       the probe cannot be valid UTF-8, drop UTF-8 from NB's
     *       candidate pool.</li>
     * </ul>
     */
    static final Set<String> V6_SHIPPED_CLASSES = Set.of(
            // CJK (6 — multi-byte supersets)
            "Big5-HKSCS", "EUC-JP", "x-windows-949", "GB18030",
            "Shift_JIS", "x-EUC-TW",
            // UTF-8 only; UTF-16 handled by specialist, UTF-32 by WideUnicodeDetector.
            "UTF-8",
            // EBCDIC (6)
            "IBM420-ltr", "IBM420-rtl", "IBM424-ltr", "IBM424-rtl",
            "IBM500", "IBM1047",
            // DOS / OEM Latin (4)
            "IBM850", "IBM852", "IBM855", "IBM866",
            // Cyrillic (2)
            "KOI8-R", "KOI8-U",
            // Windows (10)
            "windows-1250", "windows-1251", "windows-1252", "windows-1253",
            "windows-1254", "windows-1255", "windows-1256", "windows-1257",
            "windows-1258", "windows-874",
            // ISO-8859 (2)
            "ISO-8859-3", "ISO-8859-16",
            // Mac (2)
            "x-MacRoman", "x-mac-cyrillic");

    /**
     * Training-data filename → training-class-label aliases.  Empty
     * by default; reserved for cases where multiple training files
     * should merge into one class label.  (UTF-16 was an experiment
     * that didn't pan out — see why-stride1-bigrams-dont-work-for-utf16.md.)
     */
    static final Map<String, String> TRAINING_LABEL_ALIASES = Map.of();

    public static void main(String[] args) throws IOException {
        Path dataDir = null;
        Path outputPath = Paths.get("nb-bigram.bin");
        double coverage = DEFAULT_COVERAGE;
        double alphaBase = DEFAULT_ALPHA_BASE;
        int maxSamples = DEFAULT_MAX_SAMPLES;
        Set<String> classFilter = new HashSet<>(V6_SHIPPED_CLASSES);

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--data":
                    dataDir = Paths.get(args[++i]);
                    break;
                case "--output":
                    outputPath = Paths.get(args[++i]);
                    break;
                case "--coverage":
                    coverage = Double.parseDouble(args[++i]);
                    break;
                case "--alpha-base":
                    alphaBase = Double.parseDouble(args[++i]);
                    break;
                case "--max-samples-per-class":
                    maxSamples = Integer.parseInt(args[++i]);
                    break;
                case "--classes":
                    classFilter = new HashSet<>(Arrays.asList(args[++i].split(",")));
                    break;
                default:
                    System.err.println("Unknown argument: " + args[i]);
                    System.exit(1);
            }
        }

        if (dataDir == null) {
            System.err.println("Usage: TrainNaiveBayesBigram --data <dir> [--output nb-bigram.bin]"
                    + " [--coverage 0.999] [--alpha-base 1.0]"
                    + " [--max-samples-per-class 50000] [--classes cs1,cs2,...]");
            System.err.println("Default classes (" + V6_SHIPPED_CLASSES.size() + "): "
                    + new java.util.TreeSet<>(V6_SHIPPED_CLASSES));
            System.exit(1);
        }

        System.out.printf(Locale.ROOT,
                "coverage=%.3f  alpha-base=%.3f  max-samples/class=%,d%n",
                coverage, alphaBase, maxSamples);
        System.out.println("Classes (" + classFilter.size() + "): "
                + new java.util.TreeSet<>(classFilter));

        // Enumerate files, keep only included classes after applying
        // training-label aliases.  A file {@code UTF-16-LE.bin.gz}
        // maps to class label {@code "UTF-16"} via TRAINING_LABEL_ALIASES,
        // so multiple files can feed into one trained class.
        final Set<String> finalFilter = classFilter;
        List<Path> files = Files.list(dataDir)
                .filter(p -> p.getFileName().toString().endsWith(".bin.gz"))
                .filter(p -> {
                    String raw = p.getFileName().toString()
                            .replaceAll("\\.bin\\.gz$", "");
                    String aliased = TRAINING_LABEL_ALIASES.getOrDefault(raw, raw);
                    return finalFilter.contains(aliased);
                })
                .sorted()
                .collect(java.util.stream.Collectors.toList());

        if (files.isEmpty()) {
            System.err.println("No matching .bin.gz files in: " + dataDir);
            System.exit(1);
        }

        // Group files by aliased class label so UTF-16-LE + UTF-16-BE
        // accumulate into one {@code "UTF-16"} count array.
        java.util.LinkedHashMap<String, List<Path>> filesByClass =
                new java.util.LinkedHashMap<>();
        for (Path f : files) {
            String raw = f.getFileName().toString().replaceAll("\\.bin\\.gz$", "");
            String label = TRAINING_LABEL_ALIASES.getOrDefault(raw, raw);
            filesByClass.computeIfAbsent(label, k -> new ArrayList<>()).add(f);
        }

        int numClasses = filesByClass.size();
        String[] labels = new String[numClasses];
        long[][] countsPerClass = new long[numClasses][];
        long[] totalsPerClass = new long[numClasses];

        int ci = 0;
        for (Map.Entry<String, List<Path>> entry : filesByClass.entrySet()) {
            labels[ci] = entry.getKey();
            long[] counts = new long[BIGRAM_SPACE];
            long totalBigrams = 0;
            int numSamples = 0;

            // Per aliased class, the budget is maxSamples — split evenly
            // across contributing files so a 2-file class (UTF-16 LE+BE)
            // doesn't overrun a 1-file class's sample count.
            int perFileBudget = Math.max(1, maxSamples / entry.getValue().size());
            for (Path f : entry.getValue()) {
                int fileSamples = 0;
                try (InputStream fis = new FileInputStream(f.toFile());
                     GZIPInputStream gis = new GZIPInputStream(fis);
                     DataInputStream dis = new DataInputStream(gis)) {
                    while (fileSamples < perFileBudget) {
                        int len;
                        try {
                            len = dis.readUnsignedShort();
                        } catch (java.io.EOFException eof) {
                            break;
                        }
                        byte[] sample = new byte[len];
                        dis.readFully(sample);
                        for (int i = 0; i + 1 < sample.length; i++) {
                            int bigram = ((sample[i] & 0xFF) << 8) | (sample[i + 1] & 0xFF);
                            counts[bigram]++;
                            totalBigrams++;
                        }
                        fileSamples++;
                    }
                }
                numSamples += fileSamples;
            }
            countsPerClass[ci] = counts;
            totalsPerClass[ci] = totalBigrams;
            System.out.printf(Locale.ROOT,
                    "  counted %-20s  %,7d samples  %,10d total bigrams  (%d file%s)%n",
                    labels[ci], numSamples, totalBigrams,
                    entry.getValue().size(),
                    entry.getValue().size() == 1 ? "" : "s");
            ci++;
        }

        // Phase 2: class-level IDF.  df_i = number of classes with
        // non-zero count for bigram i.  idf_i = log((C+1)/(df_i+1)).
        // Bigrams that appear in every class (common ASCII) get idf
        // near zero; bigrams that appear in few classes (CJK-signal
        // bytes, EBCDIC-specific patterns) get large positive weight.
        float[] idf = new float[BIGRAM_SPACE];
        for (int bg = 0; bg < BIGRAM_SPACE; bg++) {
            int df = 0;
            for (int c = 0; c < numClasses; c++) {
                if (countsPerClass[c][bg] > 0) {
                    df++;
                }
            }
            idf[bg] = (float) Math.log((numClasses + 1.0) / (df + 1.0));
        }

        // Compute a shared REFERENCE denominator for Laplace
        // smoothing.  Without this, classes with smaller training
        // corpora (Mac variants with 10-14M total bigrams vs 27-32M
        // for well-represented classes) get a milder unseen-logP
        // because their per-class denominator is smaller.  Smaller
        // penalty on unseen bigrams → systematic free-lift for
        // under-represented classes on any probe that hits many
        // unseen bigrams (sparse-Latin probes, non-UTF HTML, etc).
        //
        // Fix: use the max per-class total as the shared denominator
        // across all classes for both kept and unseen log-probs.
        // Kept-bigram frequencies are computed as if each class had
        // been trained on N_ref bigrams, preserving relative within-
        // class frequencies while eliminating the cross-class unseen
        // disparity.
        long refTotal = 0;
        for (long t : totalsPerClass) {
            if (t > refTotal) {
                refTotal = t;
            }
        }
        System.out.printf(Locale.ROOT,
                "Laplace reference total: %,d bigrams (shared denom across classes)%n",
                refTotal);

        // Phase 3: per-class vocabulary selection by coverage, and
        // per-class α = alphaBase / V(c).  Tight priors for peaky
        // Latin classes, flatter for diffuse CJK.  Smoothing uses
        // the shared reference total above.
        TreeMap<Integer, Float>[] logProbsPerClass = new TreeMap[numClasses];
        float[] logPUnseen = new float[numClasses];
        float[] alphaPerClass = new float[numClasses];
        int[] vocabSizePerClass = new int[numClasses];

        for (int classIdx = 0; classIdx < numClasses; classIdx++) {
            long[] counts = countsPerClass[classIdx];
            long totalBigrams = totalsPerClass[classIdx];

            // Sort bigrams by frequency descending.
            Integer[] order = new Integer[BIGRAM_SPACE];
            for (int i = 0; i < BIGRAM_SPACE; i++) order[i] = i;
            Arrays.sort(order, (a, b) -> Long.compare(counts[b], counts[a]));

            // Select V(c) = smallest K such that cumulative probability
            // mass >= coverage, but never fewer than 32 (the floor
            // guarantees tiny classes still have usable vocab) and
            // never include zero-count bigrams.
            long cumulative = 0;
            int v = 0;
            long coverageTarget = (long) (coverage * totalBigrams);
            for (int k = 0; k < BIGRAM_SPACE; k++) {
                long c = counts[order[k]];
                if (c == 0) break;
                cumulative += c;
                v = k + 1;
                if (cumulative >= coverageTarget && v >= 32) break;
            }
            vocabSizePerClass[classIdx] = v;

            // Per-class α = alphaBase / V(c).  For a class with small
            // V (peaky Latin), α is large relative to vocab → tight
            // smoothing.  For diffuse CJK (large V), α is tiny → flat.
            double alphaC = alphaBase / v;
            alphaPerClass[classIdx] = (float) alphaC;

            // Scale per-class counts to the reference total.  Equivalent
            // to uniformly up-sampling smaller training sets; preserves
            // relative bigram frequencies within the class.
            double scaleToRef = (double) refTotal / totalBigrams;
            double denom = refTotal + alphaC * BIGRAM_SPACE;
            float logPUnseenC = (float) Math.log(alphaC / denom);

            TreeMap<Integer, Float> kept = new TreeMap<>();
            for (int k = 0; k < v; k++) {
                int bigram = order[k];
                double prob = (counts[bigram] * scaleToRef + alphaC) / denom;
                kept.put(bigram, (float) Math.log(prob));
            }

            logProbsPerClass[classIdx] = kept;
            logPUnseen[classIdx] = logPUnseenC;

            System.out.printf(Locale.ROOT,
                    "  trained %-20s  V=%,6d  alpha=%.6f  unseen-logP=%8.3f%n",
                    labels[classIdx], v, alphaC, logPUnseenC);
        }

        save(outputPath, labels, logProbsPerClass, logPUnseen, alphaPerClass, idf);
        System.out.println("Model saved to: " + outputPath);
    }

    /** Binary format (v3 "NBB3"):
     *   int32 magic (0x4E424233 "NBB3")
     *   int32 version (3)
     *   int32 numClasses
     *   float32 idfScale                     (global)
     *   byte[65536] idf8                     (int8 quantized, non-neg)
     *   for each class:
     *     uint16 labelLen, utf8 bytes
     *     float32 scale                      (per-class dequant)
     *     byte    unseenQ                    (int8 quantized unseen floor)
     *     int32 vocabSize                    (number of trained pairs)
     *     for each kept bigram:
     *       uint16 bigramKey
     *       byte   logP8                     (int8 quantized)
     *
     * <p>Sparse representation: only trained bigram pairs are stored;
     * the loader fills the dense {@code logP8[65536 × numClasses]}
     * array with per-class {@code unseenQ} floors and overwrites with
     * the trained {@code logP8} values.
     */
    private static void save(Path path, String[] labels,
                             Map<Integer, Float>[] logProbsPerClass,
                             float[] logPUnseen,
                             float[] alphaPerClass,
                             float[] idf) throws IOException {
        int numClasses = labels.length;

        // Quantize IDF against a single global scale.  IDF is non-negative.
        float idfMax = 1e-6f;
        for (int bg = 0; bg < BIGRAM_SPACE; bg++) {
            if (idf[bg] > idfMax) {
                idfMax = idf[bg];
            }
        }
        float idfScale = idfMax / 127f;
        byte[] idf8 = new byte[BIGRAM_SPACE];
        for (int bg = 0; bg < BIGRAM_SPACE; bg++) {
            int q = Math.round(idf[bg] / idfScale);
            if (q > 127) q = 127;
            if (q < 0) q = 0;
            idf8[bg] = (byte) q;
        }

        // Per-class scale: match maxAbs of the class column (including
        // the unseen floor, since unseen is typically the most negative).
        float[] perClassScale = new float[numClasses];
        byte[] unseenQ = new byte[numClasses];
        for (int c = 0; c < numClasses; c++) {
            float maxAbs = Math.abs(logPUnseen[c]);
            for (float lp : logProbsPerClass[c].values()) {
                float v = Math.abs(lp);
                if (v > maxAbs) {
                    maxAbs = v;
                }
            }
            if (maxAbs < 1e-6f) {
                maxAbs = 1e-6f;
            }
            float scale = maxAbs / 127f;
            perClassScale[c] = scale;
            int qu = Math.round(logPUnseen[c] / scale);
            if (qu > 127) qu = 127;
            if (qu < -127) qu = -127;
            unseenQ[c] = (byte) qu;
        }

        try (OutputStream os = new FileOutputStream(path.toFile());
             DataOutputStream dos = new DataOutputStream(os)) {
            dos.writeInt(MAGIC);
            dos.writeInt(VERSION);
            dos.writeInt(numClasses);
            dos.writeFloat(idfScale);
            dos.write(idf8);
            for (int c = 0; c < numClasses; c++) {
                byte[] lbl = labels[c].getBytes(StandardCharsets.UTF_8);
                dos.writeShort(lbl.length);
                dos.write(lbl);
                dos.writeFloat(perClassScale[c]);
                dos.writeByte(unseenQ[c]);
                float scale = perClassScale[c];
                dos.writeInt(logProbsPerClass[c].size());
                for (Map.Entry<Integer, Float> e : logProbsPerClass[c].entrySet()) {
                    int q = Math.round(e.getValue() / scale);
                    if (q > 127) q = 127;
                    if (q < -127) q = -127;
                    dos.writeShort(e.getKey());
                    dos.writeByte(q);
                }
            }
        }
    }
}
