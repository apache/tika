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

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;

import org.apache.tika.detect.EncodingResult;
import org.apache.tika.ml.chardetect.HtmlByteStripper;
import org.apache.tika.ml.chardetect.MojibusterEncodingDetector;
import org.apache.tika.quality.TextQualityComparison;
import org.apache.tika.quality.TextQualityScore;

/**
 * Batch diagnostic: replicates the {@link JunkFilterEncodingDetector}
 * pipeline over a list of files and reports tournament outcomes, agreement
 * with Mojibuster's top pick, and margin distribution.
 *
 * <p>Two candidate-pool modes:
 * <ul>
 *   <li><b>--from-mojibuster</b> (default) — pool is Mojibuster's emitted
 *       candidate set for the file (mirrors the production chain).</li>
 *   <li><b>--candidates cs1,cs2,...</b> — pool is a fixed list applied to
 *       every file (controlled experiment).</li>
 * </ul>
 *
 * <p>Input — any combination of:
 * <ul>
 *   <li>{@code --file <path>} (repeatable)</li>
 *   <li>{@code --list <file>} reads one path per line</li>
 *   <li>{@code --dir <dir>} walks the directory (regular files only)</li>
 * </ul>
 *
 * <p>Output — summary stats to stdout; optional per-file TSV via
 * {@code --detail <path>}.  Columns:
 * {@code path, raw_bytes, probe_bytes, strip_kept_pct, pool_size,
 *  moji_top, moji_top_conf, tournament_champion, agrees_with_moji,
 *  champion_vs_moji_delta, min_pairwise_delta}.
 */
public final class BatchJunkFilterEval {

    private static final int READ_LIMIT = 16384;

    /** Per-script (clean_mean, mojibake_mean) measured by
     *  {@link CalibrationGapDiagnostic} on the labeled charset devtest
     *  (200 records per source × multiple wrong targets).  Used to rescale
     *  per-candidate raw logits to a cross-script-comparable [junk=0,
     *  clean=1] scale before arbitration.  Falls back to LATIN constants
     *  for unmeasured scripts. */
    private static final Map<String, float[]> SCRIPT_CAL = Map.ofEntries(
            Map.entry("LATIN",      new float[]{ 0.773f, -3.240f}),
            Map.entry("HAN",        new float[]{ 0.719f, -4.122f}),
            Map.entry("HANGUL",     new float[]{ 1.697f, -9.700f}),
            Map.entry("CYRILLIC",   new float[]{ 1.524f, -5.041f}),
            Map.entry("ARABIC",     new float[]{ 1.491f, -13.904f}),
            Map.entry("HEBREW",     new float[]{ 1.144f, -13.898f}),
            Map.entry("ARMENIAN",   new float[]{ 1.114f, -15.221f}),
            Map.entry("TIBETAN",    new float[]{ 1.500f, -7.179f}),
            Map.entry("BENGALI",    new float[]{ 1.860f, -5.000f}),
            Map.entry("DEVANAGARI", new float[]{ 1.541f, -5.000f}),
            Map.entry("GREEK",      new float[]{ 1.500f, -13.226f})
    );
    private static final float[] FALLBACK_CAL = SCRIPT_CAL.get("LATIN");

    private BatchJunkFilterEval() {
    }

    /** Rescale a raw logit to a [junk≈0, clean≈1] common scale using the
     *  per-script (clean_mean, moji_mean) constants. */
    private static double calibrate(double rawZ, String script) {
        float[] cal = SCRIPT_CAL.getOrDefault(script, FALLBACK_CAL);
        float clean = cal[0];
        float moji = cal[1];
        double span = clean - moji;
        if (span <= 0) return rawZ;
        return (rawZ - moji) / span;
    }

    public static void main(String[] args) throws Exception {
        List<Path> files = new ArrayList<>();
        String[] fixedCandidates = null;
        Path detailOut = null;
        int max = Integer.MAX_VALUE;
        int sampleChars = 0;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--file":
                    files.add(resolvePath(args[++i]));
                    break;
                case "--list":
                    files.addAll(readList(resolvePath(args[++i])));
                    break;
                case "--dir":
                    files.addAll(walkDir(resolvePath(args[++i])));
                    break;
                case "--candidates":
                    fixedCandidates = args[++i].split(",");
                    break;
                case "--from-mojibuster":
                    fixedCandidates = null;
                    break;
                case "--detail":
                    detailOut = resolvePath(args[++i]);
                    break;
                case "--max":
                    max = Integer.parseInt(args[++i]);
                    break;
                case "--samples":
                    sampleChars = Integer.parseInt(args[++i]);
                    break;
                default:
                    System.err.println("Unknown arg: " + args[i]);
                    System.exit(1);
            }
        }
        if (files.isEmpty()) {
            System.err.println(
                    "Usage: BatchJunkFilterEval [--file p|--list f|--dir d]... "
                            + "[--from-mojibuster | --candidates cs1,cs2,...] "
                            + "[--detail tsv] [--max N] [--samples N]");
            System.exit(1);
        }
        if (files.size() > max) {
            files = files.subList(0, max);
        }

        JunkDetector detector = JunkDetector.loadFromClasspath();
        MojibusterEncodingDetector moji = new MojibusterEncodingDetector();

        Charset[] fixedSet = null;
        if (fixedCandidates != null) {
            fixedSet = new Charset[fixedCandidates.length];
            for (int i = 0; i < fixedCandidates.length; i++) {
                fixedSet[i] = Charset.forName(fixedCandidates[i]);
            }
        }

        BufferedWriter detail = detailOut != null
                ? Files.newBufferedWriter(detailOut, StandardCharsets.UTF_8) : null;
        if (detail != null) {
            detail.write("path\traw_bytes\tprobe_bytes\tstrip_kept_pct\tpool_size\t"
                    + "moji_top\tmoji_top_conf\ttournament_champion\t"
                    + "agrees_with_moji\tchampion_vs_moji_delta\tmin_pairwise_delta\t"
                    + "bayes_champion\tbayes_agrees_with_moji\t"
                    + "bayes_agrees_with_tournament\t"
                    + "calibrated_champion\tcalibrated_bayes_champion");
            if (sampleChars > 0) detail.write("\tmoji_top_sample\tchampion_sample");
            detail.write("\n");
        }
        final int sampleCharsF = sampleChars;

        long n = 0, agree = 0, disagree = 0, skipped = 0;
        long bayesAgreeWithMoji = 0, bayesAgreeWithTournament = 0;
        long bayesFlipsTournamentToMoji = 0, bayesFlipsTournamentAway = 0;
        long calAgreeWithMoji = 0, calAgreeWithTournament = 0;
        long calBayesAgreeWithMoji = 0, calBayesAgreeWithTournament = 0;
        Map<String, Integer> championCounts = new TreeMap<>();
        Map<String, Long> mojiTopCounts = new TreeMap<>();
        Map<String, Long> mismatchPairs = new TreeMap<>();
        Map<String, Long> bayesMismatchPairs = new TreeMap<>();
        Map<String, Long> calMismatchPairs = new TreeMap<>();
        List<Double> championVsMojiDeltas = new ArrayList<>();
        List<Double> minPairwiseDeltas = new ArrayList<>();

        try {
            for (Path file : files) {
                Row row = evaluateOne(file, detector, moji, fixedSet);
                if (row == null) {
                    skipped++;
                    continue;
                }
                n++;
                championCounts.merge(row.champion, 1, Integer::sum);
                mojiTopCounts.merge(row.mojiTop, 1L, Long::sum);
                if (row.champion.equals(row.mojiTop)) {
                    agree++;
                } else {
                    disagree++;
                    mismatchPairs.merge(
                            row.mojiTop + " -> " + row.champion, 1L, Long::sum);
                    championVsMojiDeltas.add((double) row.championVsMojiDelta);
                }
                if (!Float.isNaN(row.minPairwiseDelta)) {
                    minPairwiseDeltas.add((double) row.minPairwiseDelta);
                }
                if (row.bayesChampion.equals(row.mojiTop)) {
                    bayesAgreeWithMoji++;
                }
                if (row.bayesChampion.equals(row.champion)) {
                    bayesAgreeWithTournament++;
                } else {
                    bayesMismatchPairs.merge(
                            row.champion + " -> " + row.bayesChampion, 1L, Long::sum);
                    if (row.bayesChampion.equals(row.mojiTop)) {
                        bayesFlipsTournamentToMoji++;
                    } else {
                        bayesFlipsTournamentAway++;
                    }
                }
                if (row.calibratedChampion.equals(row.mojiTop)) calAgreeWithMoji++;
                if (row.calibratedChampion.equals(row.champion)) {
                    calAgreeWithTournament++;
                } else {
                    calMismatchPairs.merge(
                            row.champion + " -> " + row.calibratedChampion,
                            1L, Long::sum);
                }
                if (row.calibratedBayesChampion.equals(row.mojiTop)) calBayesAgreeWithMoji++;
                if (row.calibratedBayesChampion.equals(row.champion)) calBayesAgreeWithTournament++;
                if (detail != null) {
                    detail.write(String.format(Locale.ROOT,
                            "%s\t%d\t%d\t%.1f\t%d\t%s\t%.3f\t%s\t%s\t%.4f\t%.4f\t%s\t%s\t%s\t%s\t%s",
                            row.path, row.rawBytes, row.probeBytes,
                            row.stripKeptPct, row.poolSize,
                            row.mojiTop, row.mojiTopConf,
                            row.champion, row.champion.equals(row.mojiTop),
                            row.championVsMojiDelta, row.minPairwiseDelta,
                            row.bayesChampion,
                            row.bayesChampion.equals(row.mojiTop),
                            row.bayesChampion.equals(row.champion),
                            row.calibratedChampion, row.calibratedBayesChampion));
                    if (sampleCharsF > 0) {
                        detail.write("\t" + sanitize(row.mojiTopSample, sampleCharsF)
                                + "\t" + sanitize(row.championSample, sampleCharsF));
                    }
                    detail.write("\n");
                }
                if (n % 1000 == 0) {
                    System.err.printf("processed %d (agree=%d disagree=%d skipped=%d)%n",
                            n, agree, disagree, skipped);
                }
            }
        } finally {
            if (detail != null) detail.close();
        }

        System.out.println();
        System.out.println("=== BatchJunkFilterEval summary ===");
        System.out.printf("files evaluated: %d (skipped %d)%n", n, skipped);
        if (n == 0) return;
        System.out.printf("tournament agrees with Mojibuster top: %d (%.1f%%)%n",
                agree, 100.0 * agree / n);
        System.out.printf("tournament disagrees with Mojibuster top: %d (%.1f%%)%n",
                disagree, 100.0 * disagree / n);

        final long total = n;
        System.out.println();
        System.out.println("tournament champion frequency:");
        championCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(15)
                .forEach(e -> System.out.printf("  %-20s %6d  (%.1f%%)%n",
                        e.getKey(), e.getValue(), 100.0 * e.getValue() / total));

        System.out.println();
        System.out.println("Mojibuster top-pick frequency:");
        mojiTopCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(15)
                .forEach(e -> System.out.printf("  %-20s %6d  (%.1f%%)%n",
                        e.getKey(), e.getValue(), 100.0 * e.getValue() / total));

        if (!mismatchPairs.isEmpty()) {
            System.out.println();
            System.out.println("top mismatch pairs (moji_top -> champion):");
            mismatchPairs.entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                    .limit(20)
                    .forEach(e -> System.out.printf("  %-44s %6d%n",
                            e.getKey(), e.getValue()));
        }

        if (!championVsMojiDeltas.isEmpty()) {
            System.out.println();
            System.out.println("champion-vs-moji-top delta distribution (disagreements only):");
            printPercentiles(championVsMojiDeltas);
        }
        if (!minPairwiseDeltas.isEmpty()) {
            System.out.println();
            System.out.println("min pairwise delta in tournament (every file):");
            printPercentiles(minPairwiseDeltas);
        }

        System.out.println();
        System.out.println("=== Bayesian combination (prior × exp(logit)) A/B ===");
        System.out.printf("bayes_agrees_with_moji_top:        %d (%.1f%%)%n",
                bayesAgreeWithMoji, 100.0 * bayesAgreeWithMoji / total);
        System.out.printf("bayes_agrees_with_tournament:      %d (%.1f%%)%n",
                bayesAgreeWithTournament, 100.0 * bayesAgreeWithTournament / total);
        System.out.printf("bayes_flips_tournament -> moji_top:  %d  (tournament had overridden moji; bayes restores it)%n",
                bayesFlipsTournamentToMoji);
        System.out.printf("bayes_flips_tournament -> elsewhere: %d  (bayes picks a third option)%n",
                bayesFlipsTournamentAway);
        if (!bayesMismatchPairs.isEmpty()) {
            System.out.println();
            System.out.println("top bayes-vs-tournament mismatches (tournament -> bayes):");
            bayesMismatchPairs.entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                    .limit(20)
                    .forEach(e -> System.out.printf("  %-44s %6d%n",
                            e.getKey(), e.getValue()));
        }

        System.out.println();
        System.out.println("=== Calibrated-rescale arbitration (z' = (z-moji_mean)/(clean_mean-moji_mean)) ===");
        System.out.printf("calibrated_agrees_with_moji_top:        %d (%.1f%%)%n",
                calAgreeWithMoji, 100.0 * calAgreeWithMoji / total);
        System.out.printf("calibrated_agrees_with_tournament:      %d (%.1f%%)%n",
                calAgreeWithTournament, 100.0 * calAgreeWithTournament / total);
        System.out.printf("calibrated_bayes_agrees_with_moji_top:  %d (%.1f%%)%n",
                calBayesAgreeWithMoji, 100.0 * calBayesAgreeWithMoji / total);
        System.out.printf("calibrated_bayes_agrees_with_tournament:%d (%.1f%%)%n",
                calBayesAgreeWithTournament, 100.0 * calBayesAgreeWithTournament / total);
        if (!calMismatchPairs.isEmpty()) {
            System.out.println();
            System.out.println("top calibrated-vs-tournament mismatches (tournament -> calibrated):");
            calMismatchPairs.entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                    .limit(20)
                    .forEach(e -> System.out.printf("  %-44s %6d%n",
                            e.getKey(), e.getValue()));
        }
    }

    private static Row evaluateOne(Path file, JunkDetector detector,
                                    MojibusterEncodingDetector moji,
                                    Charset[] fixedSet) throws IOException {
        byte[] all;
        try {
            all = Files.readAllBytes(file);
        } catch (IOException e) {
            System.err.println("read failed " + file + ": " + e);
            return null;
        }
        if (all.length == 0) return null;
        byte[] bytes = all.length > READ_LIMIT
                ? Arrays.copyOfRange(all, 0, READ_LIMIT) : all;
        bytes = stripBom(bytes);

        List<EncodingResult> mojiPool = moji.detect(bytes);
        if (mojiPool.isEmpty()) return null;

        byte[] stripDst = new byte[bytes.length];
        HtmlByteStripper.Result strip =
                HtmlByteStripper.strip(bytes, 0, bytes.length, stripDst, 0);
        boolean stripUsed = strip.tagCount > 0 && strip.length > 0;
        byte[] forDecode = stripUsed
                ? Arrays.copyOfRange(stripDst, 0, strip.length) : bytes;

        Set<Charset> pool;
        if (fixedSet != null) {
            pool = new LinkedHashSet<>(Arrays.asList(fixedSet));
        } else {
            pool = new LinkedHashSet<>();
            for (EncodingResult er : mojiPool) {
                pool.add(er.getCharset());
            }
        }
        if (pool.size() < 2) return null;

        Map<String, String> decoded = new LinkedHashMap<>();
        Map<String, Float> logitByCs = new LinkedHashMap<>();
        Map<String, Double> calibratedByCs = new LinkedHashMap<>();
        for (Charset cs : pool) {
            String s = JunkFilterEncodingDetector.expandHtmlEntities(
                    new String(forDecode, cs));
            if (!s.isEmpty()) {
                decoded.put(cs.name(), s);
                TextQualityScore sc = detector.score(s);
                float z = sc.isUnknown() ? 0f : sc.getZScore();
                logitByCs.put(cs.name(), z);
                String script = sc.isUnknown() ? "LATIN" : sc.getDominantScript();
                calibratedByCs.put(cs.name(), calibrate(z, script));
            }
        }
        if (decoded.size() < 2) return null;

        String[] names = decoded.keySet().toArray(new String[0]);
        String champion = names[0];
        float minPairwise = Float.NaN;
        for (int i = 1; i < names.length; i++) {
            TextQualityComparison cmp = detector.compare(
                    champion, decoded.get(champion),
                    names[i], decoded.get(names[i]));
            float d = cmp.delta();
            if (Float.isNaN(minPairwise) || d < minPairwise) minPairwise = d;
            if (names[i].equals(cmp.winner())) champion = names[i];
        }

        Map<String, Float> priorByCs = new LinkedHashMap<>();
        for (EncodingResult er : mojiPool) {
            priorByCs.merge(er.getCharset().name(),
                    er.getConfidence(), Math::max);
        }
        String bayesChampion = null;
        double bayesBestScore = Double.NEGATIVE_INFINITY;
        String calibratedChampion = null;
        double calibratedBest = Double.NEGATIVE_INFINITY;
        String calibratedBayesChampion = null;
        double calibratedBayesBest = Double.NEGATIVE_INFINITY;
        for (String cs : decoded.keySet()) {
            float prior = priorByCs.getOrDefault(cs, 0.01f);
            if (prior < 0.01f) prior = 0.01f;
            double rawZ = logitByCs.getOrDefault(cs, 0f);
            double calZ = calibratedByCs.getOrDefault(cs, 0.0);

            double bayes = Math.log(prior) + rawZ;
            if (bayes > bayesBestScore) {
                bayesBestScore = bayes;
                bayesChampion = cs;
            }
            if (calZ > calibratedBest) {
                calibratedBest = calZ;
                calibratedChampion = cs;
            }
            double calBayes = Math.log(prior) + calZ;
            if (calBayes > calibratedBayesBest) {
                calibratedBayesBest = calBayes;
                calibratedBayesChampion = cs;
            }
        }

        EncodingResult mojiTop = mojiPool.get(0);
        float championVsMojiDelta = 0f;
        if (!champion.equals(mojiTop.getCharset().name())
                && decoded.containsKey(mojiTop.getCharset().name())) {
            TextQualityComparison cmp = detector.compare(
                    mojiTop.getCharset().name(),
                    decoded.get(mojiTop.getCharset().name()),
                    champion, decoded.get(champion));
            championVsMojiDelta = cmp.delta();
        }

        Row r = new Row();
        r.path = file.toString();
        r.rawBytes = all.length;
        r.probeBytes = bytes.length;
        r.stripKeptPct = stripUsed ? 100.0 * strip.length / bytes.length : 100.0;
        r.poolSize = decoded.size();
        r.mojiTop = mojiTop.getCharset().name();
        r.mojiTopConf = mojiTop.getConfidence();
        r.champion = champion;
        r.championVsMojiDelta = championVsMojiDelta;
        r.minPairwiseDelta = minPairwise;
        r.mojiTopSample = decoded.get(r.mojiTop);
        r.championSample = decoded.get(r.champion);
        r.bayesChampion = bayesChampion;
        r.calibratedChampion = calibratedChampion;
        r.calibratedBayesChampion = calibratedBayesChampion;
        return r;
    }

    /** Trim to {@code n} chars and collapse whitespace so the value fits on
     *  one TSV line. */
    private static String sanitize(String s, int n) {
        if (s == null) return "";
        String trimmed = s.length() <= n ? s : s.substring(0, n);
        StringBuilder out = new StringBuilder(trimmed.length());
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (c == '\t' || c == '\n' || c == '\r') out.append(' ');
            else out.append(c);
        }
        return out.toString();
    }

    private static void printPercentiles(List<Double> values) {
        if (values.isEmpty()) return;
        values.sort(Comparator.naturalOrder());
        double[] pcts = {0.05, 0.25, 0.5, 0.75, 0.95};
        System.out.printf("  n=%d  min=%.4f  max=%.4f  mean=%.4f%n",
                values.size(), values.get(0), values.get(values.size() - 1),
                values.stream().mapToDouble(Double::doubleValue).average().orElse(0));
        for (double p : pcts) {
            int idx = (int) Math.floor(p * (values.size() - 1));
            System.out.printf("    p%-3d = %.4f%n", (int) (p * 100), values.get(idx));
        }
    }

    private static List<Path> readList(Path p) throws IOException {
        List<Path> out = new ArrayList<>();
        for (String line : Files.readAllLines(p, StandardCharsets.UTF_8)) {
            line = line.trim();
            if (!line.isEmpty() && !line.startsWith("#")) {
                out.add(resolvePath(line));
            }
        }
        return out;
    }

    private static List<Path> walkDir(Path dir) throws IOException {
        List<Path> out = new ArrayList<>();
        try (Stream<Path> s = Files.walk(dir)) {
            s.filter(Files::isRegularFile).forEach(out::add);
        }
        return out;
    }

    private static Path resolvePath(String s) {
        if (s.startsWith("~")) {
            s = System.getProperty("user.home") + s.substring(1);
        }
        return Paths.get(s);
    }

    private static byte[] stripBom(byte[] b) {
        if (b.length >= 3 && (b[0] & 0xFF) == 0xEF && (b[1] & 0xFF) == 0xBB
                && (b[2] & 0xFF) == 0xBF) {
            return Arrays.copyOfRange(b, 3, b.length);
        }
        return b;
    }

    private static final class Row {
        String path;
        long rawBytes;
        int probeBytes;
        double stripKeptPct;
        int poolSize;
        String mojiTop;
        float mojiTopConf;
        String champion;
        float championVsMojiDelta;
        float minPairwiseDelta;
        String mojiTopSample;
        String championSample;
        String bayesChampion;
        String calibratedChampion;
        String calibratedBayesChampion;
    }
}
