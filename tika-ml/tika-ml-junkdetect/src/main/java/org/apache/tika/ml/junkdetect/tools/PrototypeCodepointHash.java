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
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;

import org.apache.tika.detect.BOMDetector;
import org.apache.tika.detect.EncodingResult;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.ml.chardetect.HtmlByteStripper;
import org.apache.tika.ml.junkdetect.JunkDetector;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.html.HtmlEncodingDetector;
import org.apache.tika.parser.txt.UniversalEncodingDetector;
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
        boolean singleModel = false;
        List<String> candidates = List.of(
                "UTF-8", "GB18030", "windows-1252", "windows-1251", "windows-1257",
                "Shift_JIS", "EUC-JP", "ISO-2022-JP", "UTF-16LE", "UTF-16BE");
        List<String> forceCandidates = null; // when set, skip base detectors
        String expected = "UTF-8";
        int[] probeSizes = null; // when set, sweep these probe sizes per fixture

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
                case "--single-model":
                    // Skip prototype training; run N-way fixture eval on bundled JunkDetector only.
                    singleModel = true;
                    break;
                case "--candidates":
                    candidates = Arrays.asList(args[++i].split(","));
                    break;
                case "--force-candidates":
                    // Bypass base detectors; pairwise tournament directly on these.
                    forceCandidates = Arrays.asList(args[++i].split(","));
                    break;
                case "--expected":
                    expected = args[++i];
                    break;
                case "--probe-sizes":
                    // Comma-separated probe sizes (bytes).  Each fixture
                    // gets one row per size, so you can see how length
                    // affects UNKNOWN vs scored.
                    String[] sizes = args[++i].split(",");
                    probeSizes = new int[sizes.length];
                    for (int k = 0; k < sizes.length; k++) {
                        probeSizes[k] = Integer.parseInt(sizes[k].trim());
                    }
                    break;
                default:
                    System.err.println("Unknown arg: " + args[i]);
                    System.exit(1);
            }
        }
        Files.createDirectories(outputDir);

        // --single-model bypasses the v5/v6-prototype comparison apparatus.
        // For evaluating the currently-bundled JunkDetector against real fixtures.
        if (singleModel) {
            if (fixturesDirs.isEmpty()) {
                System.err.println("--single-model requires --fixtures-dir");
                System.exit(1);
            }
            evalFixturesSingleModel(fixturesDirs, candidates, forceCandidates, expected,
                    probeSizes, outputDir);
            return;
        }

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
    // Real-life fixture eval: runs the production base detectors (BOM +
    // HtmlEncodingDetector + UniversalEncodingDetector) and asks the
    // JunkDetector to pick among their candidates via pairwise compare.
    // Mirrors the production charset-detection arbitration.
    // -----------------------------------------------------------------------

    private static void evalFixturesSingleModel(List<Path> fixturesDirs,
                                                List<String> candidates, // ignored
                                                List<String> forceCandidates,
                                                String expected,
                                                int[] probeSizes,
                                                Path outputDir) throws IOException {
        boolean forceMode = forceCandidates != null && !forceCandidates.isEmpty();
        if (forceMode) {
            System.err.println("\n--- Forced-candidates fixture eval ---");
            System.err.println("  candidates: " + forceCandidates);
        } else {
            System.err.println("\n--- Real-life fixture eval (BOM + HTML + Universal) ---");
        }
        JunkDetector detector = JunkDetector.loadFromClasspath();
        System.err.println("  model version: " + detector.getModelVersion());
        System.err.println("  expected:      " + expected);

        // Pre-resolve forced charsets; skip unsupported ones up front.
        List<Charset> forced = new ArrayList<>();
        if (forceMode) {
            for (String n : forceCandidates) {
                try {
                    forced.add(Charset.forName(n));
                } catch (Exception e) {
                    System.err.println("  skip unsupported charset: " + n);
                }
            }
        }

        BOMDetector bom = new BOMDetector();
        HtmlEncodingDetector html = new HtmlEncodingDetector();
        UniversalEncodingDetector universal = new UniversalEncodingDetector();
        ParseContext pctx = new ParseContext();

        Path out = outputDir.resolve("fixtures-real-life.tsv");
        try (PrintWriter pw = new PrintWriter(
                Files.newBufferedWriter(out, StandardCharsets.UTF_8))) {
            pw.println("dir\tfile\tn_bytes\tprobe_size\texpected\tbom_cs\thtml_cs\tuniversal_cs"
                    + "\tcandidates\twinner\tmargin\tstatus\tnotes");
            int pass = 0, fail = 0, skip = 0, agree = 0;
            double passMarginSum = 0.0;
            List<String> failingLines = new ArrayList<>();

            for (Path dir : fixturesDirs) {
                if (!Files.isDirectory(dir)) {
                    System.err.println("  WARN: not a directory: " + dir);
                    continue;
                }
                try (Stream<Path> stream = Files.walk(dir)) {
                    List<Path> files = new ArrayList<>();
                    stream.filter(Files::isRegularFile).forEach(files::add);
                    Collections.sort(files);
                    int[] sizes = probeSizes != null ? probeSizes : new int[]{16_384};
                    for (Path f : files) {
                        for (int sz : sizes) {
                            FixtureResult r = forceMode
                                    ? evalOneForced(f, expected, detector, forced, sz)
                                    : evalOneRealLife(f, expected, detector, bom, html,
                                            universal, pctx, sz);
                            pw.println(r.toTsvLine());
                            switch (r.status) {
                                case "PASS":
                                    pass++;
                                    passMarginSum += r.margin;
                                    break;
                                case "FAIL":
                                    fail++;
                                    failingLines.add(r.dir + "/" + r.shortName
                                            + "@" + sz + " -> " + r.winner
                                            + " (expected " + r.expected + ")");
                                    break;
                                case "AGREE":
                                    agree++;
                                    break;
                                default:
                                    skip++;
                            }
                        }
                    }
                }
            }
            int n = pass + fail;
            System.err.println();
            System.err.println("=== Summary ===");
            System.err.printf("Pass:    %d / %d (%.1f%%) — JunkDetector picked the expected charset%n",
                    pass, n, n == 0 ? 0.0 : 100.0 * pass / n);
            System.err.printf("Fail:    %d%n", fail);
            System.err.printf("Agree:   %d  (all detectors agreed; no arbitration needed)%n", agree);
            System.err.printf("Skip:    %d%n", skip);
            if (pass > 0) {
                System.err.printf("Mean margin on pass: %.3f%n", passMarginSum / pass);
            }
            if (!failingLines.isEmpty()) {
                System.err.println("Failing:");
                Collections.sort(failingLines);
                for (String line : failingLines) {
                    System.err.println("  " + line);
                }
            }
        }
        System.err.println("Wrote " + out);
    }

    private static FixtureResult evalOneForced(Path file, String expected,
                                               JunkDetector detector,
                                               List<Charset> forced,
                                               int probeBytes) throws IOException {
        byte[] raw = Files.readAllBytes(file);
        FixtureResult r = new FixtureResult();
        r.dir = file.getParent().getFileName().toString();
        String fname = file.getFileName().toString();
        r.shortName = fname.length() > 24 ? fname.substring(0, 24) : fname;
        r.bytes = raw.length;
        r.probeSize = probeBytes;
        r.expected = expected;

        if (isBinaryMagic(raw)) {
            r.status = "SKIP_BIN";
            return r;
        }
        // Strip HTML on the WHOLE raw buffer first, then slice to probeBytes
        // from the stripped content.  Otherwise a small probe slice can land
        // entirely inside <!DOCTYPE>/<html>/<head> boilerplate and leave
        // nothing to score after strip.
        byte[] strippedFull = stripHtmlBytes(raw);
        byte[] forDecode = strippedFull.length > probeBytes
                ? Arrays.copyOf(strippedFull, probeBytes) : strippedFull;
        r.candidatesStr = forced.stream().map(Charset::name)
                .reduce((a, b) -> a + "," + b).orElse("-");

        // Always log every candidate in notes — even those JunkDetector
        // rejects as unknown — so the failure mode is visible.  An
        // "unknown" score itself is meaningful information when the other
        // candidate scored fine.
        String winner = null;
        String runner = null;
        float winnerZ = Float.NEGATIVE_INFINITY;
        float runnerZ = Float.NEGATIVE_INFINITY;
        StringBuilder notes = new StringBuilder();
        int decoded_scored = 0;
        for (Charset cs : forced) {
            String decoded = applyEntityVariant(new String(forDecode, cs), "expanded");
            int cps = toCodepoints(decoded).length;
            if (cps < 3) {
                notes.append(cs.name()).append("=TOO_SHORT(").append(cps).append(") ");
                continue;
            }
            TextQualityScore s = detector.score(decoded);
            if (s.isUnknown()) {
                // Diagnose: is this script-not-in-model (neutral case) or
                // all-runs-fragmented-too-short (a real mojibake signal)?
                String why = diagnoseUnknown(decoded, detector);
                notes.append(cs.name()).append("=UNK[").append(why).append("] ");
                continue;
            }
            float z = s.getZScore();
            notes.append(cs.name()).append("=").append(String.format("%.2f", z)).append(" ");
            decoded_scored++;
            if (z > winnerZ) {
                runner = winner;
                runnerZ = winnerZ;
                winner = cs.name();
                winnerZ = z;
            } else if (z > runnerZ) {
                runner = cs.name();
                runnerZ = z;
            }
        }
        if (winner == null) {
            r.status = "NO_DECODE";
            r.notes = notes.toString().trim();
            return r;
        }
        r.winner = winner;
        if (decoded_scored < 2) {
            // Only one candidate scored; no real arbitration happened.
            r.margin = Float.NaN;
            r.status = safeCanonical(winner).equals(safeCanonical(expected))
                    ? "ONLY_EXPECTED_SCORED" : "ONLY_WRONG_SCORED";
        } else {
            r.margin = winnerZ - runnerZ;
            r.status = safeCanonical(winner).equals(safeCanonical(expected)) ? "PASS" : "FAIL";
        }
        r.notes = notes.toString().trim();
        return r;
    }

    private static FixtureResult evalOneRealLife(Path file, String expected,
                                                 JunkDetector detector,
                                                 BOMDetector bom,
                                                 HtmlEncodingDetector html,
                                                 UniversalEncodingDetector universal,
                                                 ParseContext pctx,
                                                 int probeBytes) throws IOException {
        byte[] raw = Files.readAllBytes(file);
        int origLen = raw.length;
        FixtureResult r = new FixtureResult();
        r.dir = file.getParent().getFileName().toString();
        String fname = file.getFileName().toString();
        r.shortName = fname.length() > 24 ? fname.substring(0, 24) : fname;
        r.bytes = origLen;
        r.probeSize = probeBytes;
        r.expected = expected;

        if (isBinaryMagic(raw)) {
            r.status = "SKIP_BIN";
            return r;
        }

        // Probe bytes for the base detectors (16 KB matches production read limit).
        // For the base detectors we keep the raw bytes (the BOM detector and
        // HTML-header sniff both want the original prefix).
        byte[] probe = raw.length > probeBytes ? Arrays.copyOf(raw, probeBytes) : raw;

        r.bomCs    = firstCharset(bom,       probe, pctx);
        r.htmlCs   = firstCharset(html,      probe, pctx);
        r.universalCs = firstCharset(universal, probe, pctx);

        // Collect distinct candidates in order of priority: BOM > HTML > universal.
        List<Charset> candList = new ArrayList<>();
        addUnique(candList, r.bomCs);
        addUnique(candList, r.htmlCs);
        addUnique(candList, r.universalCs);
        r.candidatesStr = candList.stream().map(Charset::name)
                .reduce((a, b) -> a + "," + b).orElse("-");

        if (candList.isEmpty()) {
            r.status = "NO_CANDIDATES";
            return r;
        }
        if (candList.size() == 1) {
            // All detectors agreed (or only one fired): no arbitration to do.
            r.winner = candList.get(0).name();
            r.status = safeCanonical(r.winner).equals(safeCanonical(expected)) ? "AGREE" : "AGREE_WRONG";
            return r;
        }

        // Strip HTML from the FULL raw bytes, then slice to probeBytes from
        // the stripped content — so a small probe-size doesn't land inside
        // the DOCTYPE/head boilerplate with nothing left to score.
        byte[] strippedFull = stripHtmlBytes(raw);
        byte[] forDecode = strippedFull.length > probeBytes
                ? Arrays.copyOf(strippedFull, probeBytes) : strippedFull;
        // Pairwise tournament — pick the candidate that beats all others.
        Charset winnerCs = candList.get(0);
        float bestMargin = Float.POSITIVE_INFINITY;
        for (int i = 1; i < candList.size(); i++) {
            Charset challenger = candList.get(i);
            String aDecoded = applyEntityVariant(new String(forDecode, winnerCs), "expanded");
            String bDecoded = applyEntityVariant(new String(forDecode, challenger), "expanded");
            TextQualityScore aScore = detector.score(aDecoded);
            TextQualityScore bScore = detector.score(bDecoded);
            if (aScore.isUnknown() || bScore.isUnknown()) {
                continue;
            }
            float margin = aScore.getZScore() - bScore.getZScore();
            if (margin < 0) {
                winnerCs = challenger;
                margin = -margin;
            }
            bestMargin = Math.min(bestMargin, Math.abs(margin));
        }
        r.winner = winnerCs.name();
        r.margin = Float.isInfinite(bestMargin) ? Float.NaN : bestMargin;
        r.status = safeCanonical(r.winner).equals(safeCanonical(expected)) ? "PASS" : "FAIL";
        return r;
    }

    private static String firstCharset(org.apache.tika.detect.EncodingDetector d,
                                       byte[] bytes, ParseContext pctx) {
        try (TikaInputStream tis =
                     TikaInputStream.get(new java.io.ByteArrayInputStream(bytes))) {
            List<EncodingResult> results = d.detect(tis, new Metadata(), pctx);
            if (results == null || results.isEmpty()) {
                return null;
            }
            Charset cs = results.get(0).getCharset();
            return cs == null ? null : cs.name();
        } catch (Exception e) {
            return null;
        }
    }

    private static void addUnique(List<Charset> list, String name) {
        if (name == null) {
            return;
        }
        Charset cs;
        try {
            cs = Charset.forName(name);
        } catch (Exception e) {
            return;
        }
        for (Charset c : list) {
            if (c.equals(cs)) {
                return;
            }
        }
        list.add(cs);
    }

    /**
     * Diagnose why JunkDetector returned UNKNOWN for {@code text}.  Walks
     * the same script-run logic, then classifies the failure mode:
     * <ul>
     *   <li>{@code EMPTY} — input had no characters.</li>
     *   <li>{@code NO_MODELED_SCRIPT} — all runs are in scripts the model
     *       doesn't know (legit reason to be neutral).</li>
     *   <li>{@code ALL_RUNS_TOO_SHORT(N)} — runs exist in modeled scripts
     *       but every one is &lt;2 UTF-8 bytes.  Strong mojibake signal —
     *       text is a salad of single codepoints from many scripts.</li>
     *   <li>{@code MIXED} — some runs were modeled-but-too-short and
     *       some were unmodeled.</li>
     * </ul>
     */
    private static String diagnoseUnknown(String text, JunkDetector detector) {
        if (text == null || text.isEmpty()) {
            return "EMPTY";
        }
        Set<String> modeled = detector.knownScripts();
        // Walk codepoints, splitting on script boundaries — same as
        // JunkDetector.buildScriptRuns conceptually.  Track per-script:
        // longest UTF-8-byte run length, plus a separate "unmodeled" tally.
        java.util.Map<String, Integer> longestModeled = new java.util.HashMap<>();
        int unmodeledRuns = 0;
        int modeledTooShortRuns = 0;
        int currentBytes = 0;
        String currentScript = null;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            int charCount = Character.charCount(cp);
            String script = Character.UnicodeScript.of(cp).name();
            // COMMON / INHERITED / UNKNOWN attach to preceding run, but for
            // diagnosis we don't need to be that precise — treat them as a
            // continuation.
            if ("COMMON".equals(script) || "INHERITED".equals(script)
                    || "UNKNOWN".equals(script)) {
                if (currentScript != null) {
                    currentBytes += new String(new int[]{cp}, 0, 1)
                            .getBytes(StandardCharsets.UTF_8).length;
                }
            } else if (script.equals(currentScript)) {
                currentBytes += new String(new int[]{cp}, 0, 1)
                        .getBytes(StandardCharsets.UTF_8).length;
            } else {
                // close out previous run
                tallyRun(currentScript, currentBytes, modeled, longestModeled);
                if (currentScript != null) {
                    if (!modeled.contains(currentScript)) {
                        unmodeledRuns++;
                    } else if (currentBytes < 2) {
                        modeledTooShortRuns++;
                    }
                }
                currentScript = script;
                currentBytes = new String(new int[]{cp}, 0, 1)
                        .getBytes(StandardCharsets.UTF_8).length;
            }
            i += charCount;
        }
        // close final run
        if (currentScript != null) {
            if (!modeled.contains(currentScript)) {
                unmodeledRuns++;
            } else if (currentBytes < 2) {
                modeledTooShortRuns++;
            } else {
                longestModeled.merge(currentScript, currentBytes, Math::max);
            }
        }
        boolean anyModeledLong = !longestModeled.isEmpty();
        if (anyModeledLong) {
            // Some modeled run is ≥2 bytes — shouldn't have hit UNKNOWN.
            // (Possible discrepancy with the production logic; reported as MIXED.)
            return "MIXED(modeled_long=" + longestModeled.size() + ")";
        }
        if (modeledTooShortRuns > 0 && unmodeledRuns > 0) {
            return "MIXED(short=" + modeledTooShortRuns
                    + ",unmodeled=" + unmodeledRuns + ")";
        }
        if (modeledTooShortRuns > 0) {
            return "ALL_RUNS_TOO_SHORT(" + modeledTooShortRuns + ")";
        }
        if (unmodeledRuns > 0) {
            return "NO_MODELED_SCRIPT(" + unmodeledRuns + ")";
        }
        return "OTHER";
    }

    private static void tallyRun(String script, int bytes, Set<String> modeled,
                                 java.util.Map<String, Integer> longestModeled) {
        if (script == null) {
            return;
        }
        if (modeled.contains(script) && bytes >= 2) {
            longestModeled.merge(script, bytes, Math::max);
        }
    }

    /**
     * Run HtmlByteStripper over the entire input; return the stripped
     * content bytes (or the input verbatim if no tags found).
     */
    private static byte[] stripHtmlBytes(byte[] raw) {
        byte[] dst = new byte[raw.length];
        HtmlByteStripper.Result r =
                HtmlByteStripper.strip(raw, 0, raw.length, dst, 0);
        if (r.tagCount > 0 && r.length > 0) {
            return Arrays.copyOf(dst, r.length);
        }
        return raw;
    }

    private static boolean isBinaryMagic(byte[] b) {
        if (b.length < 4) {
            return false;
        }
        if (b[0] == 0x50 && b[1] == 0x4B
                && (b[2] == 0x03 || b[2] == 0x05 || b[2] == 0x07)) {
            return true; // ZIP / JAR / APK / docx
        }
        if ((b[0] & 0xFF) == 0x1F && (b[1] & 0xFF) == 0x8B) {
            return true; // gzip
        }
        if (b[0] == '%' && b[1] == 'P' && b[2] == 'D' && b[3] == 'F') {
            return true; // PDF
        }
        if ((b[0] & 0xFF) == 0xD0 && (b[1] & 0xFF) == 0xCF) {
            return true; // OLE2
        }
        return false;
    }

    private static String safeCanonical(String charset) {
        if (charset == null) {
            return "";
        }
        try {
            return Charset.forName(charset).name();
        } catch (Exception e) {
            return charset.toUpperCase();
        }
    }

    private static final class FixtureResult {
        String dir;
        String shortName;
        int bytes;
        int probeSize;
        String expected;
        String bomCs;
        String htmlCs;
        String universalCs;
        String candidatesStr = "-";
        String winner = "-";
        float margin = Float.NaN;
        String status = "";
        String notes = "";

        String toTsvLine() {
            return String.format("%s\t%s\t%d\t%d\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s",
                    dir, shortName, bytes, probeSize, expected,
                    str(bomCs), str(htmlCs), str(universalCs),
                    candidatesStr, str(winner),
                    Float.isNaN(margin) ? "-" : String.format("%.3f", margin),
                    status, notes.isEmpty() ? "-" : notes);
        }

        private static String str(String s) {
            return s == null ? "-" : s;
        }
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
