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

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.apache.tika.detect.EncodingResult;

/**
 * Trace each layer of Mojibuster on a set of files: raw NB, post-strip NB,
 * and full Mojibuster.detect().  Helps locate where a specific charset
 * pick comes from in the pipeline.
 */
public final class TraceMojibuster {

    private TraceMojibuster() {
    }

    public static void main(String[] args) throws Exception {
        Path probeDir = null;
        String[] probes = null;
        Path inlineFile = null;
        byte[] inlineBytes = null;
        boolean fullRanking = false;
        int rankingTopN = 25;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--probe-dir":
                    probeDir = Paths.get(args[++i].replaceFirst("^~",
                            System.getProperty("user.home")));
                    break;
                case "--probes":
                    probes = args[++i].split(",");
                    break;
                case "--file":
                    inlineFile = Paths.get(args[++i].replaceFirst("^~",
                            System.getProperty("user.home")));
                    break;
                case "--bytes-hex":
                    inlineBytes = decodeHex(args[++i]);
                    break;
                case "--full-ranking":
                    fullRanking = true;
                    break;
                case "--ranking-top-n":
                    rankingTopN = Integer.parseInt(args[++i]);
                    break;
                default:
                    System.err.println("Unknown arg: " + args[i]);
                    System.exit(1);
            }
        }
        boolean hasInline = inlineFile != null || inlineBytes != null;
        boolean hasProbeList = probeDir != null && probes != null;
        if (!hasInline && !hasProbeList) {
            System.err.println("Usage: TraceMojibuster"
                    + " (--probe-dir <dir> --probes p1,p2,... | --file <path> | --bytes-hex <hex>)"
                    + " [--full-ranking] [--ranking-top-n N]");
            System.exit(1);
        }
        // Load the bundled model from the classpath (same path Mojibuster uses).
        NaiveBayesBigramEncodingDetector rawNb;
        try (InputStream is = MojibusterEncodingDetector.class
                .getResourceAsStream(
                        "/org/apache/tika/ml/chardetect/nb-bigram.bin")) {
            if (is == null) throw new IOException("bundled model not on classpath");
            rawNb = new NaiveBayesBigramEncodingDetector(is);
        }
        MojibusterEncodingDetector det = new MojibusterEncodingDetector();

        // Build the (label, bytes) work list.  Inline probes (--file /
        // --bytes-hex) are processed first; then any --probes from
        // --probe-dir.
        List<ProbeInput> work = new ArrayList<>();
        if (inlineFile != null) {
            if (!Files.exists(inlineFile)) {
                System.err.println("Missing: " + inlineFile);
                System.exit(1);
            }
            work.add(new ProbeInput(inlineFile.getFileName().toString(),
                    Files.readAllBytes(inlineFile)));
        }
        if (inlineBytes != null) {
            work.add(new ProbeInput("inline-hex(" + inlineBytes.length + "B)",
                    inlineBytes));
        }
        if (probes != null) {
            for (String pid : probes) {
                Path p = probeDir.resolve(pid);
                if (!Files.exists(p)) {
                    System.err.println("Missing: " + p);
                    continue;
                }
                String shortId = pid.contains("/")
                        ? pid.substring(pid.indexOf('/') + 1, pid.indexOf('/') + 13) : pid;
                work.add(new ProbeInput(shortId, Files.readAllBytes(p)));
            }
        }

        for (ProbeInput w : work) {
            byte[] bytes = w.bytes;
            System.out.println();
            System.out.println("==== " + w.label + "  raw=" + bytes.length + " bytes ====");

            // Layer 1: raw NB on raw bytes (no strip).
            List<EncodingResult> rawResults = rawNb.detect(bytes);
            System.out.println("  raw NB (no strip):       " + fmt(rawResults));
            if (fullRanking) {
                dumpFullRanking(rawNb, bytes, "raw", rankingTopN);
            }

            // Layer 2: NB on HTML-stripped bytes.
            byte[] dst = new byte[bytes.length];
            HtmlByteStripper.Result sr = HtmlByteStripper.strip(bytes, 0, bytes.length, dst, 0);
            byte[] strippedView = null;
            if (sr.tagCount >= 1) {
                strippedView = new byte[sr.length];
                System.arraycopy(dst, 0, strippedView, 0, sr.length);
                System.out.printf(Locale.ROOT,
                        "  HTML strip: tags=%d, post-strip=%d bytes (%.1f%% kept)%n",
                        sr.tagCount, sr.length, 100.0 * sr.length / bytes.length);
                List<EncodingResult> stripResults = rawNb.detect(strippedView);
                System.out.println("  NB on stripped bytes:    " + fmt(stripResults));
                if (fullRanking) {
                    dumpFullRanking(rawNb, strippedView, "strip", rankingTopN);
                }
            } else {
                System.out.println("  HTML strip: tagCount=0 (backoff, used original)");
            }

            // Layer 3: full Mojibuster (which internally strips conditionally).
            List<EncodingResult> mojiResults = det.detect(bytes);
            System.out.println("  Full Mojibuster.detect:  " + fmt(mojiResults));
        }
    }

    /**
     * Print every class sorted by raw NB log-score for this probe.
     * Shows where the true charset actually ranks before margin gating,
     * gap-from-top-1 in nats, and gap-per-scored-bigram (the unit the
     * margin gate uses).  An "emit?" column flags which candidates would
     * pass the {@link NaiveBayesBigramEncodingDetector#MARGIN_THRESHOLD_NATS_PER_BIGRAM}
     * gate.
     */
    private static void dumpFullRanking(NaiveBayesBigramEncodingDetector nb,
                                        byte[] probe, String layer, int topN) {
        NaiveBayesBigramEncodingDetector.ScoreResult sr = nb.scoreClassesAndCount(probe);
        if (sr == null) {
            System.out.println("    [full-ranking " + layer + "]  <probe too short to score>");
            return;
        }
        String[] labels = nb.getLabels();
        int n = labels.length;
        Integer[] idx = new Integer[n];
        for (int i = 0; i < n; i++) idx[i] = i;
        final double[] scores = sr.scores;
        java.util.Arrays.sort(idx, (a, b) -> Double.compare(scores[b], scores[a]));
        double top1 = scores[idx[0]];
        double marginNats = NaiveBayesBigramEncodingDetector.MARGIN_THRESHOLD_NATS_PER_BIGRAM
                * Math.max(1, sr.scoredBigrams);
        System.out.printf(Locale.ROOT,
                "    [full-ranking %s]  scoredBigrams=%d totalBigrams=%d  marginGate=%.3f nats (%.3f×bg)%n",
                layer, sr.scoredBigrams, sr.totalBigrams, marginNats,
                NaiveBayesBigramEncodingDetector.MARGIN_THRESHOLD_NATS_PER_BIGRAM);
        int limit = Math.min(topN, n);
        for (int rank = 0; rank < limit; rank++) {
            int c = idx[rank];
            double score = scores[c];
            double gap = top1 - score;
            double gapPerBg = (sr.scoredBigrams > 0) ? gap / sr.scoredBigrams : Double.NaN;
            String emit = (rank == 0) ? "top1"
                    : (gap < marginNats ? "EMIT" : "----");
            System.out.printf(Locale.ROOT,
                    "      #%2d  %-18s  score=%+11.3f  gap=%+8.3f  gap/bg=%+.4f  %s%n",
                    rank + 1, labels[c], score, gap, gapPerBg, emit);
        }
    }

    private static byte[] decodeHex(String s) {
        String cleaned = s.replaceAll("[\\s,:]", "");
        if (cleaned.length() % 2 != 0) {
            throw new IllegalArgumentException("hex string must have even length: " + s);
        }
        byte[] out = new byte[cleaned.length() / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(cleaned.substring(2 * i, 2 * i + 2), 16);
        }
        return out;
    }

    private static final class ProbeInput {
        final String label;
        final byte[] bytes;
        ProbeInput(String label, byte[] bytes) {
            this.label = label;
            this.bytes = bytes;
        }
    }

    private static String fmt(List<EncodingResult> rs) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < rs.size(); i++) {
            if (i > 0) sb.append(", ");
            EncodingResult r = rs.get(i);
            sb.append(r.getCharset().name())
                    .append("@").append(String.format(Locale.ROOT, "%.3f", r.getConfidence()))
                    .append("/").append(r.getResultType());
        }
        if (sb.length() == 0) sb.append("<empty>");
        return sb.toString();
    }
}
