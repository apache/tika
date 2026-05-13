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
package org.apache.tika.ml.junkdetect.tools;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

import org.apache.tika.ml.chardetect.HtmlByteStripper;
import org.apache.tika.ml.junkdetect.JunkDetector;
import org.apache.tika.quality.TextQualityScore;

/**
 * Throwaway prototype: validates the v6 codepoint-bigram-hash architecture
 * (Bloom-gated lookup with unigram backoff) by training on locally-available
 * text and measuring margins on the AIT5-class failure case (UTF-8 multi-
 * language records cross-decoded as GB18030).
 *
 * <p>Goal: prove the codepoint-bigram-hash approach opens the
 * UTF-8→GB18030 mojibake margin meaningfully above v5's ~1 z-unit
 * baseline BEFORE committing to a multi-day production retrain.
 *
 * <p>Training corpus: decode {@code ~/data/charsets/devtest/GB18030.bin.gz}
 * (Chinese) + first 80% of {@code UTF-8.bin.gz} (multi-language Wikipedia)
 * under their labeled charsets, iterate codepoints, count bigrams and unigrams,
 * hash into N buckets, build Bloom filter of seen pairs.  Held-out: last 20%
 * of UTF-8 records.
 *
 * <p>Eval: for each held-out UTF-8 record, slice to length buckets
 * {20, 50, 100, 200, 500, 1000} source bytes.  Decode each slice under
 * UTF-8 (clean) and GB18030 (mojibake-as-HAN).  Score both with the
 * prototype model.  Margin = clean_score - mojibake_score.  Report
 * mean and 5th-percentile margin per length.
 *
 * <p>Sweep: {bigramBuckets, alpha} grid.  Pick the configuration that
 * maximises margin.  Compare to v5 baseline (mean margin ~1 z-unit
 * across all lengths in the same cohort).
 *
 * <p>Outputs:
 * <ul>
 *   <li><b>prototype-sweep.tsv</b>: one row per
 *       (bigram_buckets, alpha, length).  Columns: n, mean_clean,
 *       mean_moji, mean_margin, std_margin, p5_margin, p50_margin,
 *       margin_in_clean_stds (effective z-units).</li>
 * </ul>
 *
 * <p>Usage:
 * <pre>
 *   ./mvnw -pl tika-ml/tika-ml-junkdetect exec:java \
 *     -Dexec.mainClass=org.apache.tika.ml.junkdetect.tools.PrototypeCodepointHash \
 *     -Dexec.args="--devtest-dir ~/data/charsets/devtest --output-dir /tmp/v6-prototype"
 * </pre>
 */
public class PrototypeCodepointHash {

    // --- Hyperparameter sweep grid ---
    private static final int[] BIGRAM_BUCKETS = {4096, 8192, 16384, 32768};
    private static final double[] ALPHAS = {1.0, 0.4};
    private static final int UNIGRAM_BUCKETS = 8192;
    private static final int BLOOM_BITS = 4 * 1024 * 1024; // 512 KB
    private static final int BLOOM_K = 7;

    // --- Smoothing ---
    private static final double ADD_ALPHA = 0.01;

    // --- Eval ---
    private static final int[] LENGTHS = {20, 50, 100, 200, 500, 1000};
    private static final int MAX_RECORDS_PER_FILE = 5000;
    private static final double HOLDOUT_FRACTION = 0.20;
    private static final int MIN_SCORE_CODEPOINTS = 3;

    public static void main(String[] args) throws IOException {
        Path devtestDir = Paths.get(System.getProperty("user.home"),
                "data", "charsets", "devtest");
        Path outputDir = Paths.get("/tmp/v6-prototype");
        int maxRecords = MAX_RECORDS_PER_FILE;
        List<Path> fixturesDirs = new ArrayList<>();
        String wrongCharsetName = "GB18030";

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--devtest-dir":
                    devtestDir = Paths.get(args[++i]);
                    break;
                case "--output-dir":
                    outputDir = Paths.get(args[++i]);
                    break;
                case "--max-records":
                    maxRecords = Integer.parseInt(args[++i]);
                    break;
                case "--fixtures-dir":
                    fixturesDirs.add(Paths.get(args[++i]));
                    break;
                case "--wrong-charset":
                    wrongCharsetName = args[++i];
                    break;
                default:
                    System.err.println("Unknown arg: " + args[i]);
                    System.exit(1);
            }
        }
        Files.createDirectories(outputDir);

        System.err.println("=== PrototypeCodepointHash ===");
        System.err.println("  devtest-dir:  " + devtestDir);
        System.err.println("  output-dir:   " + outputDir);
        System.err.println("  max-records:  " + maxRecords);
        System.err.println("  bigram_buckets sweep: " + Arrays.toString(BIGRAM_BUCKETS));
        System.err.println("  alpha sweep:          " + Arrays.toString(ALPHAS));
        System.err.println("  unigram_buckets:      " + UNIGRAM_BUCKETS);
        System.err.println("  bloom_bits:           " + BLOOM_BITS
                + " (" + (BLOOM_BITS / 8 / 1024) + " KB, k=" + BLOOM_K + ")");

        // -------- Load corpus --------

        Charset utf8 = StandardCharsets.UTF_8;
        Charset gb18030 = Charset.forName("GB18030");

        System.err.println("\n--- Loading corpus ---");
        List<byte[]> utf8Records = readRecords(
                devtestDir.resolve("UTF-8.bin.gz"), maxRecords);
        List<byte[]> gbRecords = readRecords(
                devtestDir.resolve("GB18030.bin.gz"), maxRecords);
        System.err.printf("  UTF-8.bin.gz:    %d records%n", utf8Records.size());
        System.err.printf("  GB18030.bin.gz:  %d records%n", gbRecords.size());

        // Train/eval split on UTF-8 records.  GB18030 records all go to training.
        int holdoutCount = (int) (utf8Records.size() * HOLDOUT_FRACTION);
        int utf8TrainSize = utf8Records.size() - holdoutCount;
        List<byte[]> utf8TrainBytes = utf8Records.subList(0, utf8TrainSize);
        List<byte[]> utf8EvalBytes = utf8Records.subList(utf8TrainSize, utf8Records.size());
        System.err.printf("  UTF-8 train: %d  eval: %d%n",
                utf8TrainBytes.size(), utf8EvalBytes.size());

        // Decode training corpus to codepoint streams
        System.err.println("\n--- Decoding training corpus ---");
        List<int[]> trainStreams = new ArrayList<>();
        long totalTrainCp = 0;
        for (byte[] r : utf8TrainBytes) {
            int[] cps = toCodepoints(decode(r, utf8));
            if (cps.length >= 2) trainStreams.add(cps);
            totalTrainCp += cps.length;
        }
        for (byte[] r : gbRecords) {
            int[] cps = toCodepoints(decode(r, gb18030));
            if (cps.length >= 2) trainStreams.add(cps);
            totalTrainCp += cps.length;
        }
        System.err.printf("  total training codepoints: %,d across %d records%n",
                totalTrainCp, trainStreams.size());

        // Count unique pairs (for Bloom sizing sanity)
        Set<Long> uniquePairs = new HashSet<>();
        for (int[] cps : trainStreams) {
            for (int i = 0; i + 1 < cps.length; i++) {
                uniquePairs.add(packPair(cps[i], cps[i + 1]));
                if (uniquePairs.size() >= 2_000_000) break;
            }
            if (uniquePairs.size() >= 2_000_000) break;
        }
        System.err.printf("  unique codepoint-pairs in training: ~%,d%n",
                uniquePairs.size());

        // -------- Hyperparameter sweep --------

        Path sweepPath = outputDir.resolve("prototype-sweep.tsv");
        try (PrintWriter out = new PrintWriter(
                Files.newBufferedWriter(sweepPath, StandardCharsets.UTF_8))) {
            out.println("bigram_buckets\talpha\tlength\tn"
                    + "\tmean_clean\tstd_clean\tmean_moji"
                    + "\tmean_margin\tstd_margin\tp5_margin\tp50_margin"
                    + "\tmargin_in_clean_stds\tbloom_seen_frac_clean\tbloom_seen_frac_moji");

            for (int buckets : BIGRAM_BUCKETS) {
                for (double alpha : ALPHAS) {
                    System.err.printf("%n--- Config: bigram_buckets=%d  alpha=%.1f ---%n",
                            buckets, alpha);

                    Model m = train(trainStreams, buckets, UNIGRAM_BUCKETS,
                            BLOOM_BITS, BLOOM_K, ADD_ALPHA, alpha);

                    // Calibrate on a sample of training streams (for the
                    // "margin_in_clean_stds" effective-z normalization)
                    double[] muSigma = calibrate(m, trainStreams);
                    System.err.printf("    train mu=%.3f  sigma=%.3f%n", muSigma[0], muSigma[1]);

                    // Eval on held-out UTF-8 records
                    for (int len : LENGTHS) {
                        EvalCell cell = evalAtLength(m, utf8EvalBytes, len, utf8, gb18030);
                        if (cell == null) continue;
                        double effZ = cell.meanMargin / Math.max(muSigma[1], 1e-6);
                        out.printf("%d\t%.2f\t%d\t%d\t%.4f\t%.4f\t%.4f\t%.4f\t%.4f\t%.4f\t%.4f\t%.3f\t%.3f\t%.3f%n",
                                buckets, alpha, len, cell.n,
                                cell.meanClean, cell.stdClean, cell.meanMoji,
                                cell.meanMargin, cell.stdMargin,
                                cell.p5Margin, cell.p50Margin,
                                effZ, cell.bloomSeenFracClean, cell.bloomSeenFracMoji);
                        System.err.printf("    len=%4d  n=%-5d  mean_margin=%6.3f  p5=%6.3f"
                                + "  eff_z=%5.2f  bloom_clean=%.2f  bloom_moji=%.2f%n",
                                len, cell.n, cell.meanMargin, cell.p5Margin, effZ,
                                cell.bloomSeenFracClean, cell.bloomSeenFracMoji);
                        out.flush();
                    }
                }
            }
        }
        System.err.println("\nWrote " + sweepPath);

        // -------- Fixture eval (AIT5-class HTML files) --------

        if (!fixturesDirs.isEmpty()) {
            evalFixtures(trainStreams, fixturesDirs, wrongCharsetName, outputDir);
        }

        System.err.println("Done.");
    }

    // -----------------------------------------------------------------------
    // Fixture eval: score real-world AIT5-class HTML files under v5 and v6
    // prototype, with byte-level HTML stripping and entity-variant comparison.
    // -----------------------------------------------------------------------

    private static void evalFixtures(List<int[]> trainStreams,
                                     List<Path> fixturesDirs,
                                     String wrongCharsetName,
                                     Path outputDir) throws IOException {
        System.err.println("\n--- Fixture eval (best config: 4096 buckets, alpha=1.0) ---");
        Model v6 = train(trainStreams, 4096, UNIGRAM_BUCKETS,
                BLOOM_BITS, BLOOM_K, ADD_ALPHA, 1.0);
        double[] muSigma = calibrate(v6, trainStreams);
        float mu = (float) muSigma[0];
        float sigma = (float) Math.max(muSigma[1], 1e-6);
        System.err.printf("  v6 train mu=%.3f  sigma=%.3f%n", mu, sigma);

        JunkDetector v5 = JunkDetector.loadFromClasspath();
        Charset cleanCs = StandardCharsets.UTF_8;
        Charset wrongCs = Charset.forName(wrongCharsetName);
        System.err.println("  v5 model version: " + v5.getModelVersion());
        System.err.println("  clean charset:    " + cleanCs.name());
        System.err.println("  mojibake charset: " + wrongCs.name());

        Path fixturesPath = outputDir.resolve("fixtures.tsv");
        try (PrintWriter out = new PrintWriter(
                Files.newBufferedWriter(fixturesPath, StandardCharsets.UTF_8))) {
            out.println("cluster\tfile\tentity_variant\tn_clean_cp\tn_moji_cp"
                    + "\tv5_clean_z\tv5_moji_z\tv5_margin"
                    + "\tv6_F1_clean\tv6_F1_moji\tv6_F1_margin"
                    + "\tv6_combo_clean\tv6_combo_moji\tv6_combo_margin"
                    + "\tdominant_script"
                    + "\tv5_winner\tv6_F1_winner\tv6_combo_winner");

            for (Path dir : fixturesDirs) {
                if (!Files.isDirectory(dir)) {
                    System.err.println("  WARN: not a directory: " + dir);
                    continue;
                }
                try (java.util.stream.Stream<Path> files = Files.walk(dir)) {
                    List<Path> sorted = new ArrayList<>();
                    files.filter(Files::isRegularFile).forEach(sorted::add);
                    Collections.sort(sorted);
                    for (Path f : sorted) {
                        evalOneFixture(f, v6, mu, sigma, v5, cleanCs, wrongCs, out);
                    }
                }
            }
        }
        System.err.println("Wrote " + fixturesPath);
    }

    private static void evalOneFixture(Path file, Model v6, float v6Mu, float v6Sigma,
                                       JunkDetector v5,
                                       Charset cleanCs, Charset wrongCs,
                                       PrintWriter out) throws IOException {
        byte[] rawBytes = Files.readAllBytes(file);
        if (rawBytes.length > 16384) {
            rawBytes = Arrays.copyOf(rawBytes, 16384);
        }
        // Byte-level HTML strip (matches JunkFilterEncodingDetector production pipeline)
        byte[] stripDst = new byte[rawBytes.length];
        HtmlByteStripper.Result strip =
                HtmlByteStripper.strip(rawBytes, 0, rawBytes.length, stripDst, 0);
        byte[] forDecode = rawBytes;
        if (strip.tagCount > 0 && strip.length > 0) {
            forDecode = new byte[strip.length];
            System.arraycopy(stripDst, 0, forDecode, 0, strip.length);
        }

        String cluster = file.getParent().getFileName().toString();
        String fname = file.getFileName().toString();
        // shorten long content-hash names for readability in output
        String shortName = fname.length() > 12 ? fname.substring(0, 12) : fname;

        String cleanRaw = decode(forDecode, cleanCs);
        String mojiRaw = decode(forDecode, wrongCs);

        for (String variant : List.of("raw", "expanded", "removed")) {
            String clean = applyEntityVariant(cleanRaw, variant);
            String moji = applyEntityVariant(mojiRaw, variant);
            int[] cleanCps = toCodepoints(clean);
            int[] mojiCps = toCodepoints(moji);
            if (cleanCps.length < 3 || mojiCps.length < 3) continue;

            // --- v5 full pipeline (existing) ---
            TextQualityScore v5cs = v5.score(clean);
            TextQualityScore v5ms = v5.score(moji);
            float v5cleanZ = v5cs.isUnknown() ? Float.NaN : v5cs.getZScore();
            float v5mojiZ = v5ms.isUnknown() ? Float.NaN : v5ms.getZScore();
            float v5Margin = v5cleanZ - v5mojiZ;

            // --- v6 Feature 1 alone (codepoint-bigram-hash + Bloom + unigram backoff) ---
            ScoreResult v6c = score(v6, cleanCps);
            ScoreResult v6m = score(v6, mojiCps);
            double v6Margin = v6c.meanLogP - v6m.meanLogP;

            // --- v6 combined: substitute v6's F1 z-score into v5's classifier ---
            JunkDetector.FeatureComponents cleanFc = v5.scoreWithFeatureComponents(clean);
            JunkDetector.FeatureComponents mojiFc = v5.scoreWithFeatureComponents(moji);
            float v6F1zClean = (float) (v6c.meanLogP - v6Mu) / v6Sigma;
            float v6F1zMoji  = (float) (v6m.meanLogP - v6Mu) / v6Sigma;
            float comboClean = recombineLogit(v6F1zClean, cleanFc);
            float comboMoji  = recombineLogit(v6F1zMoji, mojiFc);
            float comboMargin = comboClean - comboMoji;
            String dominantScript = cleanFc != null ? cleanFc.dominantScript : "?";

            String v5Winner   = Float.isNaN(v5Margin) ? "?" : (v5Margin > 0 ? "CLEAN" : "MOJI");
            String v6F1Winner = Double.isNaN(v6Margin) ? "?" : (v6Margin > 0 ? "CLEAN" : "MOJI");
            String v6cWinner  = Float.isNaN(comboMargin) ? "?" : (comboMargin > 0 ? "CLEAN" : "MOJI");

            out.printf("%s\t%s\t%s\t%d\t%d"
                            + "\t%.3f\t%.3f\t%.3f"
                            + "\t%.4f\t%.4f\t%.4f"
                            + "\t%.3f\t%.3f\t%.3f"
                            + "\t%s\t%s\t%s\t%s%n",
                    cluster, shortName, variant,
                    cleanCps.length, mojiCps.length,
                    v5cleanZ, v5mojiZ, v5Margin,
                    v6c.meanLogP, v6m.meanLogP, v6Margin,
                    comboClean, comboMoji, comboMargin,
                    dominantScript,
                    v5Winner, v6F1Winner, v6cWinner);
            out.flush();
            System.err.printf("    [%s/%s %-8s] v5: Δ%+6.2f %s   v6F1: Δ%+6.3f %s   v6combo: Δ%+6.2f %s   script=%s%n",
                    cluster, shortName, variant,
                    v5Margin, v5Winner,
                    v6Margin, v6F1Winner,
                    comboMargin, v6cWinner,
                    dominantScript);
        }
    }

    /**
     * Recomputes v5's per-script classifier logit with v6's F1 z-score
     * substituted for v5's z1.  Approximation: keeps v5's classifier weights
     * (w1..w4, bias) which were trained on the OLD F1 distribution.  A true
     * v6 retrain would re-fit w1 on the new F1 distribution; this version
     * gives a directional estimate of "what if we just swap F1?"
     */
    private static float recombineLogit(float v6F1z, JunkDetector.FeatureComponents fc) {
        if (fc == null || fc.classifierWeights == null) {
            return Float.NaN;
        }
        float[] cw = fc.classifierWeights;
        int nFeat = cw.length - 1;
        float logit = cw[nFeat]; // bias
        if (nFeat >= 1) logit += cw[0] * v6F1z;
        if (nFeat >= 2) logit += cw[1] * fc.z2;
        if (nFeat >= 3) logit += cw[2] * fc.z3;
        if (nFeat >= 4) logit += cw[3] * fc.z4;
        return logit;
    }

    // -----------------------------------------------------------------------
    // HTML entity expansion / removal (regex-based, sufficient for fixtures)
    // -----------------------------------------------------------------------

    private static final Pattern NUM_DEC = Pattern.compile("&#(\\d{1,7});");
    private static final Pattern NUM_HEX = Pattern.compile("&#[xX]([0-9a-fA-F]{1,6});");
    private static final Pattern NAMED =
            Pattern.compile("&(amp|lt|gt|quot|apos|nbsp|copy|reg);");

    private static String applyEntityVariant(String s, String variant) {
        switch (variant) {
            case "raw": return s;
            case "expanded": return expandEntities(s);
            case "removed": return removeEntities(s);
            default: throw new IllegalArgumentException(variant);
        }
    }

    private static String expandEntities(String in) {
        String s = in;
        s = NUM_DEC.matcher(s).replaceAll(mr -> {
            try {
                int cp = Integer.parseInt(mr.group(1));
                if (cp >= 0 && cp <= 0x10FFFF) {
                    return Matcher.quoteReplacement(new String(Character.toChars(cp)));
                }
            } catch (NumberFormatException ignored) {
                // fall through, leave unchanged
            }
            return Matcher.quoteReplacement(mr.group());
        });
        s = NUM_HEX.matcher(s).replaceAll(mr -> {
            try {
                int cp = Integer.parseInt(mr.group(1), 16);
                if (cp >= 0 && cp <= 0x10FFFF) {
                    return Matcher.quoteReplacement(new String(Character.toChars(cp)));
                }
            } catch (NumberFormatException ignored) {
                // fall through, leave unchanged
            }
            return Matcher.quoteReplacement(mr.group());
        });
        s = NAMED.matcher(s).replaceAll(mr -> {
            switch (mr.group(1)) {
                case "amp":  return "&";
                case "lt":   return "<";
                case "gt":   return ">";
                case "quot": return "\"";
                case "apos": return "'";
                case "nbsp": return " ";
                case "copy": return "©";
                case "reg":  return "®";
                default:     return Matcher.quoteReplacement(mr.group());
            }
        });
        return s;
    }

    private static String removeEntities(String s) {
        s = NUM_DEC.matcher(s).replaceAll("");
        s = NUM_HEX.matcher(s).replaceAll("");
        s = NAMED.matcher(s).replaceAll("");
        return s;
    }

    // -----------------------------------------------------------------------
    // Training
    // -----------------------------------------------------------------------

    private static Model train(List<int[]> streams,
                               int bigramBuckets, int unigramBuckets,
                               int bloomBits, int bloomK,
                               double addAlpha, double backoffAlpha) {
        if (Integer.bitCount(bigramBuckets) != 1 || Integer.bitCount(unigramBuckets) != 1) {
            throw new IllegalArgumentException("Bucket counts must be powers of 2");
        }
        long[] bigramCounts = new long[bigramBuckets];
        long[] unigramCounts = new long[unigramBuckets];
        long bigramTotal = 0;
        long unigramTotal = 0;
        long[] bloomBitArr = new long[(bloomBits + 63) / 64];

        for (int[] cps : streams) {
            for (int i = 0; i < cps.length; i++) {
                int cp = cps[i];
                int uBucket = (int) (fnv1aUnigram(cp) & (unigramBuckets - 1));
                unigramCounts[uBucket]++;
                unigramTotal++;
                if (i + 1 < cps.length) {
                    int cpNext = cps[i + 1];
                    int bBucket = (int) (fnv1aBigram(cp, cpNext) & (bigramBuckets - 1));
                    bigramCounts[bBucket]++;
                    bigramTotal++;
                    bloomAdd(bloomBitArr, bloomBits, bloomK, cp, cpNext);
                }
            }
        }

        // Convert to log-probabilities with add-alpha smoothing
        float[] bigramLogP = new float[bigramBuckets];
        double bigramDenom = bigramTotal + addAlpha * bigramBuckets;
        for (int i = 0; i < bigramBuckets; i++) {
            double p = (bigramCounts[i] + addAlpha) / bigramDenom;
            bigramLogP[i] = (float) Math.log(p);
        }
        float[] unigramLogP = new float[unigramBuckets];
        double unigramDenom = unigramTotal + addAlpha * unigramBuckets;
        for (int i = 0; i < unigramBuckets; i++) {
            double p = (unigramCounts[i] + addAlpha) / unigramDenom;
            unigramLogP[i] = (float) Math.log(p);
        }

        return new Model(bigramBuckets, unigramBuckets, bigramLogP, unigramLogP,
                bloomBitArr, bloomBits, bloomK, backoffAlpha);
    }

    private static double[] calibrate(Model m, List<int[]> streams) {
        double s = 0;
        double s2 = 0;
        int n = 0;
        // Use a stride to avoid scoring every single train record
        int stride = Math.max(1, streams.size() / 1000);
        for (int i = 0; i < streams.size(); i += stride) {
            int[] cps = streams.get(i);
            if (cps.length < MIN_SCORE_CODEPOINTS) continue;
            ScoreResult r = score(m, cps);
            s += r.meanLogP;
            s2 += r.meanLogP * r.meanLogP;
            n++;
        }
        if (n == 0) return new double[]{0, 1};
        double mu = s / n;
        double var = Math.max(0, s2 / n - mu * mu);
        double sigma = Math.sqrt(var);
        return new double[]{mu, sigma};
    }

    // -----------------------------------------------------------------------
    // Scoring
    // -----------------------------------------------------------------------

    private static ScoreResult score(Model m, int[] cps) {
        if (cps.length < 2) return new ScoreResult(Double.NaN, 0, 0);
        double sum = 0;
        int n = 0;
        int seen = 0;
        for (int i = 0; i + 1 < cps.length; i++) {
            int cp1 = cps[i];
            int cp2 = cps[i + 1];
            double logP;
            if (bloomContains(m.bloomBits, m.bloomBitCount, m.bloomK, cp1, cp2)) {
                int b = (int) (fnv1aBigram(cp1, cp2) & (m.bigramBuckets - 1));
                logP = m.bigramLogP[b];
                seen++;
            } else {
                int u1 = (int) (fnv1aUnigram(cp1) & (m.unigramBuckets - 1));
                int u2 = (int) (fnv1aUnigram(cp2) & (m.unigramBuckets - 1));
                logP = m.backoffAlpha * (m.unigramLogP[u1] + m.unigramLogP[u2]);
            }
            sum += logP;
            n++;
        }
        return new ScoreResult(sum / n, n, seen);
    }

    private static final class ScoreResult {
        final double meanLogP;
        final int nPairs;
        final int seenPairs;
        ScoreResult(double m, int n, int s) {
            this.meanLogP = m;
            this.nPairs = n;
            this.seenPairs = s;
        }
    }

    // -----------------------------------------------------------------------
    // Eval at one length bucket
    // -----------------------------------------------------------------------

    private static EvalCell evalAtLength(Model m, List<byte[]> evalBytes, int length,
                                         Charset cleanCs, Charset wrongCs) {
        List<Double> cleans = new ArrayList<>();
        List<Double> mojis = new ArrayList<>();
        List<Double> margins = new ArrayList<>();
        double seenSumClean = 0, seenSumMoji = 0;
        int nSeenObs = 0;
        for (byte[] rec : evalBytes) {
            if (rec.length < length) continue;
            byte[] slice = Arrays.copyOf(rec, length);
            int[] cleanCps = toCodepoints(decode(slice, cleanCs));
            int[] mojiCps = toCodepoints(decode(slice, wrongCs));
            if (cleanCps.length < MIN_SCORE_CODEPOINTS
                    || mojiCps.length < MIN_SCORE_CODEPOINTS) continue;
            ScoreResult sc = score(m, cleanCps);
            ScoreResult sm = score(m, mojiCps);
            if (Double.isNaN(sc.meanLogP) || Double.isNaN(sm.meanLogP)) continue;
            cleans.add(sc.meanLogP);
            mojis.add(sm.meanLogP);
            margins.add(sc.meanLogP - sm.meanLogP);
            if (sc.nPairs > 0) seenSumClean += (double) sc.seenPairs / sc.nPairs;
            if (sm.nPairs > 0) seenSumMoji += (double) sm.seenPairs / sm.nPairs;
            nSeenObs++;
        }
        if (margins.size() < 30) return null;
        EvalCell cell = new EvalCell();
        cell.n = margins.size();
        cell.meanClean = mean(cleans);
        cell.stdClean = std(cleans, cell.meanClean);
        cell.meanMoji = mean(mojis);
        cell.meanMargin = mean(margins);
        cell.stdMargin = std(margins, cell.meanMargin);
        cell.p5Margin = percentile(margins, 0.05);
        cell.p50Margin = percentile(margins, 0.50);
        cell.bloomSeenFracClean = nSeenObs > 0 ? seenSumClean / nSeenObs : Double.NaN;
        cell.bloomSeenFracMoji = nSeenObs > 0 ? seenSumMoji / nSeenObs : Double.NaN;
        return cell;
    }

    private static final class EvalCell {
        int n;
        double meanClean, stdClean;
        double meanMoji;
        double meanMargin, stdMargin;
        double p5Margin, p50Margin;
        double bloomSeenFracClean, bloomSeenFracMoji;
    }

    // -----------------------------------------------------------------------
    // FNV-1a hashing for codepoint bigram / unigram + Bloom filter
    // -----------------------------------------------------------------------

    private static final long FNV_OFFSET = 0xcbf29ce484222325L;
    private static final long FNV_PRIME = 0x100000001b3L;

    private static long fnv1aBigram(int cp1, int cp2) {
        long h = FNV_OFFSET;
        h = (h ^ ((cp1 >>> 24) & 0xFF)) * FNV_PRIME;
        h = (h ^ ((cp1 >>> 16) & 0xFF)) * FNV_PRIME;
        h = (h ^ ((cp1 >>> 8) & 0xFF))  * FNV_PRIME;
        h = (h ^ (cp1 & 0xFF))          * FNV_PRIME;
        h = (h ^ 0xFF)                  * FNV_PRIME; // separator
        h = (h ^ ((cp2 >>> 24) & 0xFF)) * FNV_PRIME;
        h = (h ^ ((cp2 >>> 16) & 0xFF)) * FNV_PRIME;
        h = (h ^ ((cp2 >>> 8) & 0xFF))  * FNV_PRIME;
        h = (h ^ (cp2 & 0xFF))          * FNV_PRIME;
        return h;
    }

    private static long fnv1aUnigram(int cp) {
        long h = FNV_OFFSET;
        h = (h ^ ((cp >>> 24) & 0xFF)) * FNV_PRIME;
        h = (h ^ ((cp >>> 16) & 0xFF)) * FNV_PRIME;
        h = (h ^ ((cp >>> 8) & 0xFF))  * FNV_PRIME;
        h = (h ^ (cp & 0xFF))          * FNV_PRIME;
        return h;
    }

    private static long secondaryHash(int cp1, int cp2) {
        // Independent secondary hash for Bloom double-hashing.  Just shuffle
        // the inputs differently.
        long h = 0xff51afd7ed558ccdL;
        h = (h ^ Integer.reverse(cp1)) * 0xc4ceb9fe1a85ec53L;
        h = (h ^ Integer.reverse(cp2)) * 0xc4ceb9fe1a85ec53L;
        h ^= h >>> 33;
        return h;
    }

    private static void bloomAdd(long[] bits, int bitCount, int k, int cp1, int cp2) {
        long h1 = fnv1aBigram(cp1, cp2);
        long h2 = secondaryHash(cp1, cp2);
        for (int i = 0; i < k; i++) {
            long pos = ((h1 + (long) i * h2) & 0x7FFFFFFFFFFFFFFFL) % bitCount;
            bits[(int) (pos >>> 6)] |= 1L << (pos & 63);
        }
    }

    private static boolean bloomContains(long[] bits, int bitCount, int k,
                                         int cp1, int cp2) {
        long h1 = fnv1aBigram(cp1, cp2);
        long h2 = secondaryHash(cp1, cp2);
        for (int i = 0; i < k; i++) {
            long pos = ((h1 + (long) i * h2) & 0x7FFFFFFFFFFFFFFFL) % bitCount;
            if ((bits[(int) (pos >>> 6)] & (1L << (pos & 63))) == 0) return false;
        }
        return true;
    }

    private static long packPair(int cp1, int cp2) {
        return ((long) cp1 << 32) | (cp2 & 0xFFFFFFFFL);
    }

    // -----------------------------------------------------------------------
    // I/O and decode utilities (copied from EvalJunkOnCharsetDevtest)
    // -----------------------------------------------------------------------

    private static List<byte[]> readRecords(Path file, int maxRecords) throws IOException {
        List<byte[]> records = new ArrayList<>();
        try (FileInputStream fis = new FileInputStream(file.toFile());
             GZIPInputStream gis = new GZIPInputStream(fis);
             DataInputStream dis = new DataInputStream(gis)) {
            while (records.size() < maxRecords) {
                int len;
                try {
                    len = dis.readUnsignedShort();
                } catch (EOFException eof) {
                    break;
                }
                byte[] rec = new byte[len];
                dis.readFully(rec);
                records.add(rec);
            }
        }
        return records;
    }

    private static String decode(byte[] bytes, Charset cs) {
        CharsetDecoder dec = cs.newDecoder()
                .onMalformedInput(CodingErrorAction.REPLACE)
                .onUnmappableCharacter(CodingErrorAction.REPLACE);
        try {
            return dec.decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) {
            return new String(bytes, cs);
        }
    }

    private static int[] toCodepoints(String s) {
        int[] cps = new int[s.length()];
        int n = 0;
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            cps[n++] = cp;
            i += Character.charCount(cp);
        }
        return Arrays.copyOf(cps, n);
    }

    // -----------------------------------------------------------------------
    // Stats
    // -----------------------------------------------------------------------

    private static double mean(List<Double> xs) {
        double s = 0;
        int n = 0;
        for (double v : xs) {
            if (!Double.isNaN(v)) {
                s += v;
                n++;
            }
        }
        return n == 0 ? Double.NaN : s / n;
    }

    private static double std(List<Double> xs, double mu) {
        if (xs.size() < 2) return 0;
        double s = 0;
        int n = 0;
        for (double v : xs) {
            if (!Double.isNaN(v)) {
                s += (v - mu) * (v - mu);
                n++;
            }
        }
        return n < 2 ? 0 : Math.sqrt(s / (n - 1));
    }

    private static double percentile(List<Double> xs, double p) {
        List<Double> sorted = new ArrayList<>(xs);
        sorted.removeIf(v -> Double.isNaN(v));
        if (sorted.isEmpty()) return Double.NaN;
        Collections.sort(sorted);
        int idx = (int) Math.floor(p * (sorted.size() - 1));
        return sorted.get(idx);
    }

    // -----------------------------------------------------------------------
    // Model
    // -----------------------------------------------------------------------

    private static final class Model {
        final int bigramBuckets;
        final int unigramBuckets;
        final float[] bigramLogP;
        final float[] unigramLogP;
        final long[] bloomBits;
        final int bloomBitCount;
        final int bloomK;
        final double backoffAlpha;
        Model(int bb, int ub, float[] blp, float[] ulp,
              long[] bloom, int bbc, int bk, double a) {
            this.bigramBuckets = bb;
            this.unigramBuckets = ub;
            this.bigramLogP = blp;
            this.unigramLogP = ulp;
            this.bloomBits = bloom;
            this.bloomBitCount = bbc;
            this.bloomK = bk;
            this.backoffAlpha = a;
        }
    }
}
