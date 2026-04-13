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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.tika.ml.LinearModel;
import org.apache.tika.ml.chardetect.ByteNgramFeatureExtractor;

/**
 * Audits hash-bucket collisions for the shipped feature extractor.  For a
 * given probe, shows which n-grams fired which buckets, and for each bucket
 * lists every OTHER n-gram in the extractor's n-gram space that would hash
 * to the same bucket.  Optionally restricts the "colliding peers" enumeration
 * to specific byte-range classes (Arabic vs Central European letters, etc.).
 *
 * <p>Usage:
 * <pre>
 *   java BucketCollisionAudit --probe &lt;file&gt; [--model &lt;path&gt;]
 *                             [--max-probe-bytes N] [--top N]
 * </pre>
 *
 * <p>Uses the exact FNV constants from {@link ByteNgramFeatureExtractor}.
 * Enumerates four feature families:
 * <ul>
 *   <li>Unigrams — one byte in 0x80..0xFF (128 entries)</li>
 *   <li>Bigrams — high byte then any byte (128 * 256 = 32,768 entries)</li>
 *   <li>Anchored bigrams — one salt, (low-trail, any) byte pairs
 *       (128 * 256 = 32,768 entries, only those following a high byte)</li>
 *   <li>Stride-2 bigrams — (any, any) at even positions (256 * 256 = 65,536 entries)</li>
 * </ul>
 */
public final class BucketCollisionAudit {

    private static final int FNV_PRIME        = 0x01000193;
    private static final int FNV_OFFSET       = 0x811c9dc5;
    private static final int FNV_ANCHOR_SALT  = 0x27d4eb2f;
    private static final int FNV_STRIDE2_SALT = 0x9e3779b9;

    private BucketCollisionAudit() {
    }

    public static void main(String[] args) throws Exception {
        Path probePath = null;
        Path modelPath = null;
        int maxProbeBytes = 32 * 1024;
        int topBuckets = 20;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--probe":
                    probePath = Paths.get(args[++i]);
                    break;
                case "--model":
                    modelPath = Paths.get(args[++i]);
                    break;
                case "--max-probe-bytes":
                    maxProbeBytes = Integer.parseInt(args[++i]);
                    break;
                case "--top":
                    topBuckets = Integer.parseInt(args[++i]);
                    break;
                default:
                    System.err.println("Unknown arg: " + args[i]);
                    System.exit(1);
            }
        }
        if (probePath == null) {
            System.err.println("Usage: BucketCollisionAudit --probe <file> [--model <path>] "
                    + "[--max-probe-bytes N] [--top N]");
            System.exit(1);
        }

        LinearModel model = loadModel(modelPath);
        int numBuckets = model.getNumBuckets();
        ByteNgramFeatureExtractor extractor = new ByteNgramFeatureExtractor(numBuckets);

        // Pre-build inverse map: bucket -> list of n-grams that hash to it.
        System.out.printf(Locale.ROOT,
                "Building inverse bucket map over %,d buckets (can take a few seconds)...%n",
                numBuckets);
        List<Ngram>[] inverse = buildInverseMap(numBuckets);

        // Collision-rate summary.
        int maxSize = 0;
        long totalNgrams = 0;
        int populated = 0;
        for (List<Ngram> l : inverse) {
            if (l == null || l.isEmpty()) {
                continue;
            }
            populated++;
            totalNgrams += l.size();
            if (l.size() > maxSize) {
                maxSize = l.size();
            }
        }
        double avg = populated > 0 ? (double) totalNgrams / populated : 0;
        System.out.printf(Locale.ROOT,
                "n-grams enumerated: %,d   populated buckets: %,d / %,d (%.1f%%)   "
                        + "avg n-grams/bucket: %.2f   max: %d%n%n",
                totalNgrams, populated, numBuckets,
                100.0 * populated / numBuckets, avg, maxSize);

        // Load probe, extract features.
        byte[] all = Files.readAllBytes(probePath);
        byte[] probe = all.length <= maxProbeBytes ? all : Arrays.copyOf(all, maxProbeBytes);
        int[] features = extractor.extract(probe);

        int nnz = 0;
        for (int v : features) {
            if (v != 0) {
                nnz++;
            }
        }
        System.out.printf(Locale.ROOT,
                "Probe %s: %,d bytes (probe: %,d), %,d active buckets%n%n",
                probePath, all.length, probe.length, nnz);

        // For the top-N hottest buckets (by count), show which of this probe's
        // n-grams fired them, and list every OTHER n-gram that hashes to the
        // same bucket.
        Integer[] order = new Integer[numBuckets];
        for (int i = 0; i < numBuckets; i++) {
            order[i] = i;
        }
        Arrays.sort(order, Comparator.comparingInt((Integer i) -> -features[i]));

        // Compute which n-grams from THIS probe fired each bucket (with occurrences).
        Map<Integer, List<Ngram>> probeFirings = new LinkedHashMap<>();
        enumerateProbeFirings(probe, numBuckets, probeFirings);

        byte[][] weights = model.getWeights();
        float[] scales = model.getScales();
        String[] labels = model.getLabels();

        int ibm852 = indexOf(labels, "IBM852");
        int win1256 = indexOf(labels, "windows-1256");
        int win1250 = indexOf(labels, "windows-1250");

        System.out.printf(Locale.ROOT, "Top-%d hottest buckets on this probe:%n", topBuckets);
        System.out.println("====================================================================");
        int shown = 0;
        for (int rank = 0; rank < numBuckets && shown < topBuckets; rank++) {
            int b = order[rank];
            if (features[b] == 0) {
                break;
            }
            shown++;
            String ibm852Col = col(weights, scales, b, ibm852, features[b]);
            String win1256Col = col(weights, scales, b, win1256, features[b]);
            String win1250Col = col(weights, scales, b, win1250, features[b]);
            System.out.printf(Locale.ROOT,
                    "Bucket %5d   count %3d   IBM852:%s   win-1256:%s   win-1250:%s%n",
                    b, features[b], ibm852Col, win1256Col, win1250Col);
            List<Ngram> fired = probeFirings.getOrDefault(b, new ArrayList<>());
            List<Ngram> allHere = inverse[b];
            System.out.printf(Locale.ROOT,
                    "  fired by probe (%d distinct ngram kinds):%n", fired.size());
            for (Ngram ng : fired) {
                System.out.println("    " + ng.describe());
            }
            System.out.printf(Locale.ROOT,
                    "  other n-grams colliding into this bucket (%d total):%n",
                    allHere == null ? 0 : allHere.size() - fired.size());
            if (allHere != null) {
                int samples = 0;
                for (Ngram ng : allHere) {
                    if (containsSame(fired, ng)) {
                        continue;
                    }
                    if (samples++ >= 8) {
                        break;
                    }
                    System.out.println("    " + ng.describe());
                }
            }
            System.out.println();
        }
    }

    private static String col(byte[][] weights, float[] scales, int bucket,
                              int cls, int count) {
        if (cls < 0) {
            return "(n/a)";
        }
        int w = weights[cls][bucket];
        float raw = scales[cls] * w * count;
        return String.format(Locale.ROOT, "w=%+4d raw=%+7.1f", w, raw);
    }

    private static int indexOf(String[] labels, String target) {
        for (int i = 0; i < labels.length; i++) {
            if (labels[i].equalsIgnoreCase(target)) {
                return i;
            }
        }
        return -1;
    }

    private static boolean containsSame(List<Ngram> list, Ngram ng) {
        for (Ngram o : list) {
            if (o.equalsNgram(ng)) {
                return true;
            }
        }
        return false;
    }

    private static LinearModel loadModel(Path modelPath) throws Exception {
        if (modelPath != null) {
            return LinearModel.loadFromPath(modelPath);
        }
        String res = "/org/apache/tika/ml/chardetect/chardetect-v6-no-utf32.bin";
        try (InputStream is = BucketCollisionAudit.class.getResourceAsStream(res)) {
            if (is == null) {
                throw new IllegalStateException("default model resource not found: " + res);
            }
            return LinearModel.load(is);
        }
    }

    // ----------------------------------------------------------------------
    // N-gram enumeration and hashing
    // ----------------------------------------------------------------------

    private static int bucket(int hash, int numBuckets) {
        return (hash & 0x7fffffff) % numBuckets;
    }

    private static int hashUnigram(int bi) {
        return (FNV_OFFSET ^ bi) * FNV_PRIME;
    }

    private static int hashBigram(int bi, int bi1) {
        int h = (FNV_OFFSET ^ bi) * FNV_PRIME;
        return (h ^ bi1) * FNV_PRIME;
    }

    private static int hashAnchored(int lowTrail, int next) {
        int h = (FNV_ANCHOR_SALT ^ lowTrail) * FNV_PRIME;
        return (h ^ next) * FNV_PRIME;
    }

    private static int hashAnchoredNoTrail(int lowTrail) {
        // When the low-trail is the last byte in the probe, anchored bigram
        // has no 'next' — the extractor emits just the hash seeded with lowTrail.
        return (FNV_ANCHOR_SALT ^ lowTrail) * FNV_PRIME;
    }

    private static int hashStride2(int b0, int b1) {
        int h = (FNV_STRIDE2_SALT ^ b0) * FNV_PRIME;
        return (h ^ b1) * FNV_PRIME;
    }

    @SuppressWarnings("unchecked")
    private static List<Ngram>[] buildInverseMap(int numBuckets) {
        List<Ngram>[] inverse = new List[numBuckets];

        // Unigrams: high bytes only.
        for (int bi = 0x80; bi < 0x100; bi++) {
            add(inverse, bucket(hashUnigram(bi), numBuckets), Ngram.unigram(bi));
        }
        // Bigrams: (high, any).
        for (int bi = 0x80; bi < 0x100; bi++) {
            for (int bi1 = 0; bi1 < 0x100; bi1++) {
                add(inverse, bucket(hashBigram(bi, bi1), numBuckets),
                        Ngram.bigram(bi, bi1));
            }
        }
        // Anchored: (low-trail, any) — only fires when preceded by a high byte.
        // Hash doesn't include the precursor; two variants depending on whether
        // a 'next' byte exists.
        for (int bi1 = 0; bi1 < 0x80; bi1++) {
            add(inverse, bucket(hashAnchoredNoTrail(bi1), numBuckets),
                    Ngram.anchoredNoNext(bi1));
            for (int bi2 = 0; bi2 < 0x100; bi2++) {
                add(inverse, bucket(hashAnchored(bi1, bi2), numBuckets),
                        Ngram.anchored(bi1, bi2));
            }
        }
        // Stride-2: (any, any).
        for (int b0 = 0; b0 < 0x100; b0++) {
            for (int b1 = 0; b1 < 0x100; b1++) {
                add(inverse, bucket(hashStride2(b0, b1), numBuckets),
                        Ngram.stride2(b0, b1));
            }
        }
        return inverse;
    }

    private static void add(List<Ngram>[] inv, int b, Ngram ng) {
        if (inv[b] == null) {
            inv[b] = new ArrayList<>();
        }
        inv[b].add(ng);
    }

    /**
     * For a given probe, walk the exact same emission logic as
     * {@link ByteNgramFeatureExtractor#extractSparseInto} and record, per
     * bucket, which n-gram(s) fired it.  This is needed because the
     * inverse map gives us the universe of potentially-colliding n-grams,
     * and we want to separate "this probe fired it via X" from
     * "X' is a colliding peer that didn't fire here."
     */
    private static void enumerateProbeFirings(byte[] input, int numBuckets,
                                              Map<Integer, List<Ngram>> firings) {
        // Stride-1
        for (int i = 0; i < input.length; i++) {
            int bi = input[i] & 0xFF;
            if (bi < 0x80) {
                continue;
            }
            addFiring(firings, bucket(hashUnigram(bi), numBuckets), Ngram.unigram(bi));
            if (i + 1 < input.length) {
                int bi1 = input[i + 1] & 0xFF;
                addFiring(firings, bucket(hashBigram(bi, bi1), numBuckets),
                        Ngram.bigram(bi, bi1));
                if (bi1 < 0x80) {
                    if (i + 2 < input.length) {
                        int bi2 = input[i + 2] & 0xFF;
                        addFiring(firings, bucket(hashAnchored(bi1, bi2), numBuckets),
                                Ngram.anchored(bi1, bi2));
                    } else {
                        addFiring(firings, bucket(hashAnchoredNoTrail(bi1), numBuckets),
                                Ngram.anchoredNoNext(bi1));
                    }
                }
            }
        }
        // Stride-2
        for (int i = 0; i + 1 < input.length; i += 2) {
            int b0 = input[i] & 0xFF;
            int b1 = input[i + 1] & 0xFF;
            addFiring(firings, bucket(hashStride2(b0, b1), numBuckets),
                    Ngram.stride2(b0, b1));
        }
    }

    private static void addFiring(Map<Integer, List<Ngram>> firings, int b, Ngram ng) {
        List<Ngram> list = firings.computeIfAbsent(b, k -> new ArrayList<>());
        for (Ngram o : list) {
            if (o.equalsNgram(ng)) {
                return;
            }
        }
        list.add(ng);
    }

    private static final class Ngram {
        final char kind;  // 'U' 'B' 'A' 'a' (anchored-no-next) 'S'
        final int a;
        final int b;

        Ngram(char kind, int a, int b) {
            this.kind = kind;
            this.a = a;
            this.b = b;
        }

        static Ngram unigram(int bi) {
            return new Ngram('U', bi, -1);
        }

        static Ngram bigram(int bi, int bi1) {
            return new Ngram('B', bi, bi1);
        }

        static Ngram anchored(int low, int next) {
            return new Ngram('A', low, next);
        }

        static Ngram anchoredNoNext(int low) {
            return new Ngram('a', low, -1);
        }

        static Ngram stride2(int b0, int b1) {
            return new Ngram('S', b0, b1);
        }

        boolean equalsNgram(Ngram o) {
            return kind == o.kind && a == o.a && b == o.b;
        }

        String describe() {
            switch (kind) {
                case 'U':
                    return String.format(Locale.ROOT, "UNIGRAM  0x%02X       (%s)",
                            a, letterHint(a));
                case 'B':
                    return String.format(Locale.ROOT, "BIGRAM   0x%02X 0x%02X (%s, %s)",
                            a, b, letterHint(a), letterHint(b));
                case 'A':
                    return String.format(Locale.ROOT, "ANCHORED 0x%02X 0x%02X (%s after high byte)",
                            a, b, asciiHint(a));
                case 'a':
                    return String.format(Locale.ROOT, "ANCHOR-L 0x%02X       (%s at end after high byte)",
                            a, asciiHint(a));
                case 'S':
                    return String.format(Locale.ROOT, "STRIDE2  0x%02X 0x%02X",
                            a, b);
                default:
                    return "?";
            }
        }

        private static String letterHint(int v) {
            if (v < 0x80) {
                return asciiHint(v);
            }
            if (v == 0xC7) return "alef[1256]/Ă[852]";
            if (v == 0xE1) return "lam[1256]/ß[852]";
            if (v == 0xE3) return "meem[1256]/Ń[852]";
            if (v == 0xCA) return "teh[1256]/╩[852]";
            if (v == 0xD1) return "reh[1256]/Đ[852]";
            if (v == 0xED) return "yeh[1256]/ý[852]";
            if (v == 0xE7) return "ain[1256]/š[852]";
            if (v == 0xCF) return "ithal[1256]/¤[852]";
            if (v == 0xE4) return "nun[1256]/ń[852]";
            if (v == 0xE6) return "waw[1256]/Š[852]";
            if (v == 0xE9) return "yeh[1256]/Ú[852]";
            if (v == 0xF4) return "fathaton[1256]/─[852]";
            return String.format(Locale.ROOT, "hi-%02X", v);
        }

        private static String asciiHint(int v) {
            if (v == 0x20) return "SP";
            if (v == 0x0A) return "LF";
            if (v == 0x0D) return "CR";
            if (v >= 0x21 && v <= 0x7E) return "'" + ((char) v) + "'";
            return String.format(Locale.ROOT, "\\x%02X", v);
        }
    }
}
