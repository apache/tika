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
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;

/**
 * Calibration tool — for each devtest sample, run the bigram NB and
 * record where the true label appears in the ranked candidate list and
 * what softmax confidence it carries.  Bucket by scored-bigram count
 * (number of bigrams that actually contributed to the dot product,
 * which is the right unit of "evidence available to NB" regardless of
 * raw input length and HTML noise).
 *
 * <p>Outputs:</p>
 * <ul>
 *   <li>For each scored-count bucket: top-1 accuracy, top-3 / top-5 /
 *       top-10 cumulative coverage, MIN-confidence at which 95% / 99%
 *       coverage is achieved.</li>
 *   <li>Distribution of scored-bigram counts across devtest.</li>
 *   <li>Optionally: spot-check specific probe files to locate them
 *       on the (scored-count, top-1-margin, true-label-rank) plane.</li>
 * </ul>
 */
public final class CalibrateTopK {

    private CalibrateTopK() {
    }

    private static final int[] BUCKETS = {
            0, 50, 100, 200, 400, 800, 1600, 3200, 6400, 12000, 16000
    };

    public static void main(String[] args) throws IOException {
        Path devtestDir = null;
        Path modelPath = null;
        Path probeDir = null;
        String probesArg = null;
        int maxSamplesPerClass = 5_000;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--devtest":
                    devtestDir = Paths.get(args[++i].replaceFirst("^~",
                            System.getProperty("user.home")));
                    break;
                case "--model":
                    modelPath = Paths.get(args[++i]);
                    break;
                case "--probes":
                    probesArg = args[++i];
                    break;
                case "--probe-dir":
                    probeDir = Paths.get(args[++i].replaceFirst("^~",
                            System.getProperty("user.home")));
                    break;
                case "--max-samples-per-class":
                    maxSamplesPerClass = Integer.parseInt(args[++i]);
                    break;
                default:
                    System.err.println("Unknown arg: " + args[i]);
                    System.exit(1);
            }
        }
        if (modelPath == null) {
            System.err.println("Usage: CalibrateTopK --model <bin>"
                    + " [--devtest <dir>] [--probe-dir <dir> --probes c,...,c]"
                    + " [--max-samples-per-class N]");
            System.exit(1);
        }
        NaiveBayesBigramEncodingDetector det;
        try (InputStream is = Files.newInputStream(modelPath)) {
            det = new NaiveBayesBigramEncodingDetector(is);
        }
        String[] labels = det.getLabels();
        Map<String, Integer> labelIdx = new HashMap<>();
        for (int i = 0; i < labels.length; i++) {
            labelIdx.put(labels[i], i);
        }

        if (devtestDir != null) {
            runDevtest(det, labels, labelIdx, devtestDir, maxSamplesPerClass);
        }

        if (probesArg != null && probeDir != null) {
            String[] ids = probesArg.split(",");
            System.out.println();
            System.out.println("=== Per-probe spot check ===");
            for (String id : ids) {
                Path p = probeDir.resolve(id);
                if (!Files.exists(p)) {
                    System.err.println("Missing probe: " + p);
                    continue;
                }
                byte[] bytes = Files.readAllBytes(p);
                NaiveBayesBigramEncodingDetector.ScoreResult sr =
                        det.scoreClassesAndCount(bytes);
                if (sr == null) {
                    System.out.println(id + "  (no score)");
                    continue;
                }
                Rank r = rank(sr.scores, labels);
                int win1252 = labelIdx.getOrDefault("windows-1252", -1);
                int win1252Rank = win1252 >= 0 ? r.rankOf(win1252) : -1;
                double sc = Math.max(1, sr.scoredBigrams);
                double top1NatsPerBg = r.scores[r.idxRanked[0]] / sc;
                double top2NatsPerBg = r.scores[r.idxRanked[1]] / sc;
                double marginNatsPerBg = top1NatsPerBg - top2NatsPerBg;
                double top1Z = r.zOf(r.idxRanked[0]);
                double winZ = win1252 >= 0 ? r.zOf(win1252) : 0.0;
                double winNatsPerBg = win1252 >= 0 ? r.scores[win1252] / sc : 0.0;
                System.out.printf(Locale.ROOT,
                        "%-30s  scored=%5d  top-1=%-15s nats/bg=%+7.3f  z=%+5.2f  "
                                + "top2=%-15s margin=%+6.3f  win-1252@rank=%2d nats/bg=%+7.3f z=%+5.2f%n",
                        id.substring(id.indexOf('/') + 1, id.indexOf('/') + 13), sr.scoredBigrams,
                        labels[r.idxRanked[0]], top1NatsPerBg, top1Z,
                        labels[r.idxRanked[1]], marginNatsPerBg,
                        win1252Rank, winNatsPerBg, winZ);
            }
        }
    }

    private static void runDevtest(NaiveBayesBigramEncodingDetector det,
                                   String[] labels, Map<String, Integer> labelIdx,
                                   Path devtestDir, int maxSamplesPerClass) throws IOException {
        // Per bucket: counts of true rank, per-bigram log-margin distribution.
        int B = BUCKETS.length;
        long[][] rankCountByBucket = new long[B][20];
        long[][] beyondTopByBucket = new long[B][1];
        long[] sampleCountByBucket = new long[B];
        // CORRECT picks: per-bigram log-margin (top1 - top2) for correct predictions
        @SuppressWarnings("unchecked")
        List<Double>[] correctMarginByBucket = new List[B];
        for (int i = 0; i < B; i++) correctMarginByBucket[i] = new ArrayList<>();
        // WRONG picks: per-bigram log-margin for wrong predictions
        @SuppressWarnings("unchecked")
        List<Double>[] wrongMarginByBucket = new List[B];
        for (int i = 0; i < B; i++) wrongMarginByBucket[i] = new ArrayList<>();
        // ALL: per-bigram score gap between TRUE label and top-1 (for true label rank > 0,
        // negative; this tells us by how much the model misses on wrong picks)
        @SuppressWarnings("unchecked")
        List<Double>[] trueVsTop1MarginByBucket = new List[B];
        for (int i = 0; i < B; i++) trueVsTop1MarginByBucket[i] = new ArrayList<>();

        List<Path> files;
        try (Stream<Path> s = Files.list(devtestDir)) {
            files = s.filter(p -> p.getFileName().toString().endsWith(".bin.gz"))
                    .sorted()
                    .collect(Collectors.toList());
        }
        long total = 0;
        for (Path f : files) {
            String label = f.getFileName().toString().replaceAll("\\.bin\\.gz$", "");
            Integer trueIdx = labelIdx.get(label);
            if (trueIdx == null) continue;
            int sampled = 0;
            try (InputStream fis = new FileInputStream(f.toFile());
                 GZIPInputStream gis = new GZIPInputStream(fis);
                 DataInputStream dis = new DataInputStream(gis)) {
                while (sampled < maxSamplesPerClass) {
                    int len;
                    try {
                        len = dis.readUnsignedShort();
                    } catch (java.io.EOFException eof) {
                        break;
                    }
                    byte[] sample = new byte[len];
                    dis.readFully(sample);
                    NaiveBayesBigramEncodingDetector.ScoreResult sr =
                            det.scoreClassesAndCount(sample);
                    if (sr == null) continue;
                    int bucket = bucketFor(sr.scoredBigrams);
                    sampleCountByBucket[bucket]++;
                    total++;
                    Rank r = rank(sr.scores, labels);
                    int trueRank = r.rankOf(trueIdx);
                    if (trueRank < 20) rankCountByBucket[bucket][trueRank]++;
                    else beyondTopByBucket[bucket][0]++;
                    // Per-bigram log-margin top1 vs top2 (in nats / scored-bigram).
                    // Score is already in nats (log-probability units after dequant).
                    double margin = (sr.scores[r.idxRanked[0]] - sr.scores[r.idxRanked[1]])
                            / Math.max(1, sr.scoredBigrams);
                    if (trueRank == 0) {
                        correctMarginByBucket[bucket].add(margin);
                    } else {
                        wrongMarginByBucket[bucket].add(margin);
                    }
                    // How far the true label is from top-1, per-bigram.  Zero
                    // when correct, negative when the model missed.
                    double trueVsTop = (sr.scores[trueIdx] - sr.scores[r.idxRanked[0]])
                            / Math.max(1, sr.scoredBigrams);
                    trueVsTop1MarginByBucket[bucket].add(trueVsTop);
                    sampled++;
                }
            }
        }
        System.out.printf(Locale.ROOT, "Total devtest samples scored: %,d%n%n", total);

        System.out.println("=== Top-K cumulative coverage by scored-bigram-count bucket ===");
        System.out.printf(Locale.ROOT, "%-15s  %10s  %8s  %8s  %8s  %8s  %8s  %8s%n",
                "bucket", "samples", "top-1", "top-2", "top-3", "top-5", "top-10", ">=20");
        for (int b = 0; b < B; b++) {
            long n = sampleCountByBucket[b];
            if (n == 0) continue;
            long c1 = rankCountByBucket[b][0];
            long c2 = c1 + rankCountByBucket[b][1];
            long c3 = c2 + rankCountByBucket[b][2];
            long c5 = c3 + rankCountByBucket[b][3] + rankCountByBucket[b][4];
            long c10 = c5;
            for (int k = 5; k < 10; k++) c10 += rankCountByBucket[b][k];
            long beyond = beyondTopByBucket[b][0];
            String label = b == B - 1
                    ? String.format(Locale.ROOT, "%d+", BUCKETS[b])
                    : String.format(Locale.ROOT, "%d-%d", BUCKETS[b], BUCKETS[b + 1] - 1);
            System.out.printf(Locale.ROOT, "%-15s  %,10d  %7.2f%%  %7.2f%%  %7.2f%%  %7.2f%%  %7.2f%%  %7.2f%%%n",
                    label, n,
                    100.0 * c1 / n, 100.0 * c2 / n, 100.0 * c3 / n,
                    100.0 * c5 / n, 100.0 * c10 / n,
                    100.0 * beyond / n);
        }

        System.out.println();
        System.out.println("=== Per-bigram log-margin (nats/scored-bigram) top-1 vs top-2 ===");
        System.out.println("(How decisively the model favors top-1 over top-2, normalized by evidence.");
        System.out.println(" Compare CORRECT-pick distribution vs WRONG-pick distribution per bucket.)");
        System.out.printf(Locale.ROOT, "%-15s  %10s  %12s  %12s  %12s  %12s  %12s  %12s%n",
                "bucket", "n-correct", "corr-p10", "corr-p50", "corr-p90", "n-wrong", "wrong-p10", "wrong-p90");
        for (int b = 0; b < B; b++) {
            long n = sampleCountByBucket[b];
            if (n == 0) continue;
            List<Double> corr = correctMarginByBucket[b];
            List<Double> wrong = wrongMarginByBucket[b];
            Collections.sort(corr);
            Collections.sort(wrong);
            String label = b == B - 1
                    ? String.format(Locale.ROOT, "%d+", BUCKETS[b])
                    : String.format(Locale.ROOT, "%d-%d", BUCKETS[b], BUCKETS[b + 1] - 1);
            System.out.printf(Locale.ROOT,
                    "%-15s  %,10d  %12.5f  %12.5f  %12.5f  %,10d  %12.5f  %12.5f%n",
                    label, (long) corr.size(),
                    percentile(corr, 10), percentile(corr, 50), percentile(corr, 90),
                    (long) wrong.size(),
                    percentile(wrong, 10), percentile(wrong, 90));
        }

        System.out.println();
        System.out.println("=== True-label score gap vs top-1 (nats/scored-bigram) ===");
        System.out.println("(0 when correct.  Negative when wrong — magnitude shows how far model missed.");
        System.out.println(" Lower decile values are the hardest wrong picks per bucket.)");
        System.out.printf(Locale.ROOT, "%-15s  %10s  %12s  %12s  %12s  %12s%n",
                "bucket", "samples", "p1", "p5", "p10", "p50");
        for (int b = 0; b < B; b++) {
            long n = sampleCountByBucket[b];
            if (n == 0) continue;
            List<Double> diffs = trueVsTop1MarginByBucket[b];
            Collections.sort(diffs);
            String label = b == B - 1
                    ? String.format(Locale.ROOT, "%d+", BUCKETS[b])
                    : String.format(Locale.ROOT, "%d-%d", BUCKETS[b], BUCKETS[b + 1] - 1);
            System.out.printf(Locale.ROOT, "%-15s  %,10d  %12.5f  %12.5f  %12.5f  %12.5f%n",
                    label, n,
                    percentile(diffs, 1), percentile(diffs, 5),
                    percentile(diffs, 10), percentile(diffs, 50));
        }
    }

    private static int bucketFor(int scored) {
        for (int i = BUCKETS.length - 1; i >= 0; i--) {
            if (scored >= BUCKETS[i]) return i;
        }
        return 0;
    }

    private static double percentile(List<Double> sorted, double pctile) {
        if (sorted.isEmpty()) return Double.NaN;
        int idx = (int) Math.floor(pctile / 100.0 * sorted.size());
        if (idx < 0) idx = 0;
        if (idx >= sorted.size()) idx = sorted.size() - 1;
        return sorted.get(idx);
    }

    private static final class Rank {
        final int[] idxRanked;     // class index sorted by score desc
        final double[] scores;     // raw scores by class index
        final double mean;         // mean of scores across all classes
        final double std;          // stddev of scores across all classes
        Rank(int[] idxRanked, double[] scores, double mean, double std) {
            this.idxRanked = idxRanked;
            this.scores = scores;
            this.mean = mean;
            this.std = std;
        }
        int rankOf(int classIdx) {
            for (int k = 0; k < idxRanked.length; k++) {
                if (idxRanked[k] == classIdx) return k;
            }
            return -1;
        }
        /** Z-score of a class's raw score relative to the per-probe
         *  class-score distribution.  Top-1 typically gets a large
         *  positive z; classes the model thinks are impossible get
         *  large negative z. */
        double zOf(int classIdx) {
            if (std <= 0) return 0.0;
            return (scores[classIdx] - mean) / std;
        }
    }

    private static Rank rank(double[] scores, String[] labels) {
        int n = scores.length;
        Integer[] idx = new Integer[n];
        for (int i = 0; i < n; i++) idx[i] = i;
        Arrays.sort(idx, (a, b) -> Double.compare(scores[b], scores[a]));
        double sum = 0;
        for (double s : scores) sum += s;
        double mean = sum / n;
        double sq = 0;
        for (double s : scores) sq += (s - mean) * (s - mean);
        double std = Math.sqrt(sq / n);
        int[] idxRanked = new int[n];
        for (int k = 0; k < n; k++) idxRanked[k] = idx[k];
        return new Rank(idxRanked, scores, mean, std);
    }
}
