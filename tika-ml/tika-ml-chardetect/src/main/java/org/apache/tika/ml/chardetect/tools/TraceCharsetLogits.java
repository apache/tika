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

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.apache.tika.ml.FeatureExtractor;
import org.apache.tika.ml.LinearModel;
import org.apache.tika.ml.chardetect.ByteNgramFeatureExtractor;

/**
 * Forensic trace for a single probe: top-15 raw logits, per-class bucket
 * contribution breakdown, and probe statistics.  Helps diagnose cases where
 * the model is confidently wrong (e.g. the Arabic-vs-IBM852 rank-15 case).
 *
 * <p>Usage:
 * <pre>
 *   java TraceCharsetLogits --probe &lt;file&gt; [--model &lt;path&gt;]
 *                           [--focus label1,label2,...] [--top-buckets N]
 *                           [--max-probe-bytes N]
 * </pre>
 */
public final class TraceCharsetLogits {

    private TraceCharsetLogits() {
    }

    public static void main(String[] args) throws Exception {
        Path probePath = null;
        Path modelPath = null;
        List<String> focus = new ArrayList<>();
        int topBuckets = 20;
        int maxProbeBytes = 32 * 1024;
        boolean noStride2 = false;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--probe":
                    probePath = Paths.get(args[++i]);
                    break;
                case "--model":
                    modelPath = Paths.get(args[++i]);
                    break;
                case "--focus":
                    for (String s : args[++i].split(",")) {
                        focus.add(s.trim());
                    }
                    break;
                case "--top-buckets":
                    topBuckets = Integer.parseInt(args[++i]);
                    break;
                case "--max-probe-bytes":
                    maxProbeBytes = Integer.parseInt(args[++i]);
                    break;
                case "--no-stride2":
                    noStride2 = true;
                    break;
                default:
                    System.err.println("Unknown arg: " + args[i]);
                    System.exit(1);
            }
        }
        if (probePath == null) {
            System.err.println("Usage: TraceCharsetLogits --probe <file> [--model <path>] "
                    + "[--focus <label1,label2,...>] [--top-buckets N] [--max-probe-bytes N]");
            System.exit(1);
        }

        LinearModel model = loadModel(modelPath);
        FeatureExtractor<byte[]> extractor = noStride2
                // Production flags minus stride-2, matching FeatureExtractorParityTest
                // for the stride-1 features (uni + bi, no trigrams, no anchored).
                ? new ConfigurableByteNgramFeatureExtractor(model.getNumBuckets(),
                        true, true, false, false, false)
                : new ByteNgramFeatureExtractor(model.getNumBuckets());
        if (noStride2) {
            System.out.println("Stride-2 features suppressed for this run.");
        }

        byte[] allBytes = Files.readAllBytes(probePath);
        byte[] probe = allBytes.length <= maxProbeBytes
                ? allBytes
                : Arrays.copyOf(allBytes, maxProbeBytes);

        printProbeStats(probePath, allBytes.length, probe);

        int[] features = extractor.extract(probe);
        float[] logits = model.predictLogits(features);

        String[] labels = model.getLabels();
        int numClasses = labels.length;

        // Top-15 by raw logit
        Integer[] order = new Integer[numClasses];
        for (int i = 0; i < numClasses; i++) {
            order[i] = i;
        }
        Arrays.sort(order, Comparator.comparingDouble((Integer i) -> -logits[i]));

        System.out.println();
        System.out.println("Top-15 raw logits:");
        System.out.println("  rank  label                     logit       gap-from-top");
        float topLogit = logits[order[0]];
        for (int r = 0; r < Math.min(15, numClasses); r++) {
            int c = order[r];
            System.out.printf(Locale.ROOT,
                    "  %3d   %-24s  %10.1f  %+10.1f%n",
                    r + 1, labels[c], logits[c], logits[c] - topLogit);
        }

        // Per-class bucket contribution breakdown for top-1 and any --focus classes
        List<String> forensic = new ArrayList<>();
        forensic.add(labels[order[0]]);
        for (String f : focus) {
            if (!forensic.contains(f)) {
                forensic.add(f);
            }
        }

        byte[][] weights = model.getWeights();
        float[] scales = model.getScales();
        float[] biases = model.getBiases();
        int numBuckets = model.getNumBuckets();

        for (String label : forensic) {
            int c = indexOf(labels, label);
            if (c < 0) {
                System.out.println();
                System.out.println("(label '" + label + "' not in model)");
                continue;
            }
            System.out.println();
            System.out.printf(Locale.ROOT, "Per-bucket contributions for %s (class %d, bias=%.2f, scale=%.4g):%n",
                    label, c, biases[c], scales[c]);

            float clip = 1.5f * (float) Math.sqrt(nnz(features));

            BucketContrib[] contribs = new BucketContrib[numBuckets];
            int nContribs = 0;
            for (int b = 0; b < numBuckets; b++) {
                if (features[b] == 0) {
                    continue;
                }
                float raw = scales[c] * weights[c][b] * features[b];
                float clipped = Math.max(-clip, Math.min(clip, raw));
                contribs[nContribs++] = new BucketContrib(b, features[b], weights[c][b],
                        raw, clipped);
            }
            BucketContrib[] trim = Arrays.copyOf(contribs, nContribs);
            Arrays.sort(trim, (a, bb) -> Float.compare(Math.abs(bb.clipped), Math.abs(a.clipped)));

            double sumClipped = 0, sumRaw = 0;
            for (BucketContrib bc : trim) {
                sumClipped += bc.clipped;
                sumRaw += bc.raw;
            }
            System.out.printf(Locale.ROOT,
                    "  active buckets: %d   sum(clipped)=%.1f   sum(raw)=%.1f   bias=%.2f   "
                            + "logit=%.1f   clip=%.2f%n",
                    nContribs, sumClipped, sumRaw, biases[c],
                    sumClipped + biases[c], clip);

            System.out.printf(Locale.ROOT,
                    "  top-%d buckets by |clipped contribution|:%n", topBuckets);
            System.out.println("    bucket    count   weight(INT8)   raw          clipped");
            for (int k = 0; k < Math.min(topBuckets, trim.length); k++) {
                BucketContrib bc = trim[k];
                System.out.printf(Locale.ROOT,
                        "    %7d   %5d   %+5d         %+10.2f  %+10.2f%n",
                        bc.bucket, bc.count, bc.weight, bc.raw, bc.clipped);
            }
        }

        // For any pair of focus classes (or top-1 + first focus), show shared buckets.
        if (forensic.size() >= 2) {
            String a = forensic.get(0);
            String b = forensic.get(1);
            int ca = indexOf(labels, a);
            int cb = indexOf(labels, b);
            if (ca >= 0 && cb >= 0) {
                System.out.println();
                System.out.printf(Locale.ROOT,
                        "Head-to-head bucket comparison: %s vs %s%n", a, b);
                System.out.println("    bucket    count   wA      wB     raw-diff   "
                        + "(wA-wB)*scale*count ~ net logit delta for A over B");
                float scA = scales[ca];
                float scB = scales[cb];
                List<BucketDiff> diffs = new ArrayList<>();
                for (int bk = 0; bk < numBuckets; bk++) {
                    if (features[bk] == 0) {
                        continue;
                    }
                    float rawA = scA * weights[ca][bk] * features[bk];
                    float rawB = scB * weights[cb][bk] * features[bk];
                    float diff = rawA - rawB;
                    diffs.add(new BucketDiff(bk, features[bk],
                            weights[ca][bk], weights[cb][bk], rawA, rawB, diff));
                }
                diffs.sort((x, y) -> Float.compare(Math.abs(y.diff), Math.abs(x.diff)));
                for (int k = 0; k < Math.min(topBuckets, diffs.size()); k++) {
                    BucketDiff d = diffs.get(k);
                    System.out.printf(Locale.ROOT,
                            "    %7d   %5d   %+4d    %+4d    %+10.2f   %+10.2f%n",
                            d.bucket, d.count, d.wA, d.wB, d.rawA - d.rawB, d.diff);
                }
            }
        }
    }

    private static int nnz(int[] features) {
        int n = 0;
        for (int v : features) {
            if (v != 0) {
                n++;
            }
        }
        return n;
    }

    private static int indexOf(String[] labels, String target) {
        for (int i = 0; i < labels.length; i++) {
            if (labels[i].equalsIgnoreCase(target)) {
                return i;
            }
        }
        return -1;
    }

    private static LinearModel loadModel(Path modelPath) throws Exception {
        if (modelPath != null) {
            return LinearModel.loadFromPath(modelPath);
        }
        // Default: the model shipped with mojibuster.
        String res = "/org/apache/tika/ml/chardetect/chardetect-v6-no-utf32.bin";
        try (InputStream is = TraceCharsetLogits.class.getResourceAsStream(res)) {
            if (is == null) {
                throw new IllegalStateException("default model resource not found: " + res);
            }
            return LinearModel.load(is);
        }
    }

    private static void printProbeStats(Path p, long fileSize, byte[] probe) {
        int[] hist = new int[256];
        int high = 0, c1 = 0, nul = 0, ascii = 0, asciiText = 0;
        for (byte b : probe) {
            int v = b & 0xFF;
            hist[v]++;
            if (v >= 0x80) {
                high++;
            }
            if (v >= 0x80 && v < 0xA0) {
                c1++;
            }
            if (v == 0) {
                nul++;
            }
            if (v < 0x80) {
                ascii++;
            }
            if ((v >= 0x20 && v <= 0x7E) || v == 0x09 || v == 0x0A || v == 0x0D) {
                asciiText++;
            }
        }
        System.out.println("Probe trace");
        System.out.printf(Locale.ROOT, "  file         : %s%n", p);
        System.out.printf(Locale.ROOT, "  file size    : %,d bytes (probe: %,d)%n", fileSize, probe.length);
        System.out.printf(Locale.ROOT,
                "  high bytes   : %,d (%.2f%%)    ASCII: %,d (%.2f%%)    ASCII-text: %,d (%.2f%%)%n",
                high, 100.0 * high / probe.length,
                ascii, 100.0 * ascii / probe.length,
                asciiText, 100.0 * asciiText / probe.length);
        System.out.printf(Locale.ROOT,
                "  C1 (0x80-9F) : %,d (%.2f%%)    NUL: %,d%n",
                c1, 100.0 * c1 / probe.length, nul);

        // High-byte range distribution
        int[] ranges = new int[4];  // 0x80-BF, 0xC0-DF, 0xE0-EF, 0xF0-FF
        for (int v = 0x80; v < 0x100; v++) {
            int bucket;
            if (v < 0xC0) {
                bucket = 0;
            } else if (v < 0xE0) {
                bucket = 1;
            } else if (v < 0xF0) {
                bucket = 2;
            } else {
                bucket = 3;
            }
            ranges[bucket] += hist[v];
        }
        int highTotal = ranges[0] + ranges[1] + ranges[2] + ranges[3];
        if (highTotal > 0) {
            System.out.printf(Locale.ROOT,
                    "  high ranges  : 0x80-BF=%.1f%%   0xC0-DF=%.1f%%   0xE0-EF=%.1f%%   0xF0-FF=%.1f%%%n",
                    100.0 * ranges[0] / highTotal,
                    100.0 * ranges[1] / highTotal,
                    100.0 * ranges[2] / highTotal,
                    100.0 * ranges[3] / highTotal);
        }

        // Top 10 most frequent high-byte values
        Integer[] idx = new Integer[256];
        for (int i = 0; i < 256; i++) {
            idx[i] = i;
        }
        Arrays.sort(idx, (a, b) -> Integer.compare(hist[b], hist[a]));
        StringBuilder sb = new StringBuilder("  top high bytes: ");
        int shown = 0;
        for (int i : idx) {
            if (shown >= 10 || hist[i] == 0) {
                break;
            }
            if (i < 0x80) {
                continue;
            }
            sb.append(String.format(Locale.ROOT, "0x%02X(%d) ", i, hist[i]));
            shown++;
        }
        System.out.println(sb);
    }

    private static final class BucketContrib {
        final int bucket;
        final int count;
        final byte weight;
        final float raw;
        final float clipped;

        BucketContrib(int bucket, int count, byte weight, float raw, float clipped) {
            this.bucket = bucket;
            this.count = count;
            this.weight = weight;
            this.raw = raw;
            this.clipped = clipped;
        }
    }

    private static final class BucketDiff {
        final int bucket;
        final int count;
        final byte wA;
        final byte wB;
        final float rawA;
        final float rawB;
        final float diff;

        BucketDiff(int bucket, int count, byte wA, byte wB, float rawA, float rawB, float diff) {
            this.bucket = bucket;
            this.count = count;
            this.wA = wA;
            this.wB = wB;
            this.rawA = rawA;
            this.rawB = rawB;
            this.diff = diff;
        }
    }
}
