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

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.tika.detect.EncodingResult;
import org.apache.tika.ml.chardetect.HtmlByteStripper;
import org.apache.tika.ml.chardetect.MojibusterEncodingDetector;

/**
 * Tests whether forcing higher w4 (script transitions) and w8 (script
 * coherence) on the LATIN classifier flips the wrong LATIN→CJK
 * over-overrides in cc-html-sample, and whether doing so breaks the
 * CJK→LATIN rescues.
 *
 * <p>Approach: load JunkDetector, call scoreWithFeatureComponents() to
 * get per-candidate z1..z8 + original LR weights, then RECOMPUTE the
 * logit with adjusted LATIN weights.  Pick winner by recomputed logit.
 *
 * <p>Limitation: only models the dominant-script chunk's contribution
 * (FeatureComponents reflects the dominant-script aggregate, not the
 * full multi-script weighted average that JunkDetector.score() does).
 * For the 66 wrong-CJK cases, both candidates are LATIN-dominant so the
 * approximation is reasonable.  For the 244 CJK-rescue cases, both are
 * CJK or both are LATIN-dominant; same logic applies.
 *
 * <p>Input format: TSV with columns "tag\tpath".  Tag is "WRONG" for
 * files we want to flip, "RESCUE" for files we must not break.
 */
public final class WeightTweakExperiment {

    private static final int READ_LIMIT = 16384;

    private WeightTweakExperiment() {
    }

    public static void main(String[] args) throws Exception {
        Path listFile = null;
        float w4New = 0.5f;
        float w8New = 0.3f;
        boolean verbose = false;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--list":
                    listFile = Paths.get(args[++i]);
                    break;
                case "--w4":
                    w4New = Float.parseFloat(args[++i]);
                    break;
                case "--w8":
                    w8New = Float.parseFloat(args[++i]);
                    break;
                case "--verbose":
                    verbose = true;
                    break;
                default:
                    System.err.println("Unknown arg: " + args[i]);
                    System.exit(1);
            }
        }
        if (listFile == null) {
            System.err.println("Usage: --list <tsv> [--w4 X] [--w8 Y] [--verbose]");
            System.exit(1);
        }

        JunkDetector detector = JunkDetector.loadFromClasspath();
        MojibusterEncodingDetector moji = new MojibusterEncodingDetector();

        List<String[]> rows = new ArrayList<>();
        for (String line : Files.readAllLines(listFile, StandardCharsets.UTF_8)) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            rows.add(line.split("\t"));
        }

        System.out.printf("Experiment: w4 %s→%.2f, w8 %s→%.2f for LATIN classifier%n%n",
                "(original)", w4New, "(original)", w8New);

        int[] wrongFlipped = {0}, wrongKept = {0};
        int[] rescueFlipped = {0}, rescueKept = {0};

        for (String[] row : rows) {
            String tag = row[0];
            Path file = Paths.get(row[1]);
            Result r = evaluate(file, detector, moji, w4New, w8New);
            if (r == null) continue;
            boolean origChosesCJK = isCJK(r.origWinner);
            boolean newChosesCJK = isCJK(r.newWinner);
            boolean flipped = !r.origWinner.equals(r.newWinner);
            if (tag.equals("WRONG")) {
                if (flipped && !newChosesCJK) wrongFlipped[0]++;
                else wrongKept[0]++;
            } else if (tag.equals("RESCUE")) {
                if (flipped && newChosesCJK) rescueFlipped[0]++;  // BAD: rescue broken
                else rescueKept[0]++;
            }
            if (verbose && flipped) {
                System.out.printf("%-6s %s -> %s  (file %s)%n",
                        tag, r.origWinner, r.newWinner, file.getFileName());
            }
        }

        System.out.println();
        System.out.println("=== Results ===");
        System.out.printf("WRONG cases (Latin→CJK over-overrides, want to flip back to LATIN):%n");
        System.out.printf("  flipped to LATIN (FIXED):  %d / %d%n",
                wrongFlipped[0], wrongFlipped[0] + wrongKept[0]);
        System.out.printf("  still picks CJK (no change): %d%n", wrongKept[0]);
        System.out.println();
        System.out.printf("RESCUE cases (CJK→Latin chain wins, must NOT break):%n");
        System.out.printf("  still picks LATIN (preserved): %d / %d%n",
                rescueKept[0], rescueFlipped[0] + rescueKept[0]);
        System.out.printf("  flipped to CJK (REGRESSION): %d%n", rescueFlipped[0]);
    }

    private static boolean isCJK(String cs) {
        return cs.equals("GB18030") || cs.equals("EUC-JP") || cs.equals("Shift_JIS")
                || cs.equals("Big5-HKSCS") || cs.equals("x-windows-949")
                || cs.equals("x-EUC-TW");
    }

    private static Result evaluate(Path file, JunkDetector detector,
                                    MojibusterEncodingDetector moji,
                                    float w4New, float w8New) throws IOException {
        byte[] all;
        try {
            all = Files.readAllBytes(file);
        } catch (IOException e) {
            return null;
        }
        if (all.length == 0) return null;
        byte[] bytes = all.length > READ_LIMIT ? Arrays.copyOfRange(all, 0, READ_LIMIT) : all;
        bytes = stripBom(bytes);

        List<EncodingResult> pool = moji.detect(bytes);
        if (pool.size() < 2) return null;

        byte[] stripDst = new byte[bytes.length];
        HtmlByteStripper.Result strip =
                HtmlByteStripper.strip(bytes, 0, bytes.length, stripDst, 0);
        boolean stripUsed = strip.tagCount > 0 && strip.length > 0;
        byte[] forDecode = stripUsed
                ? Arrays.copyOfRange(stripDst, 0, strip.length) : bytes;

        Map<String, Float> origLogit = new LinkedHashMap<>();
        Map<String, Float> newLogit = new LinkedHashMap<>();
        for (EncodingResult er : pool) {
            Charset cs = er.getCharset();
            String name = cs.name();
            String s = JunkFilterEncodingDetector.expandHtmlEntities(
                    new String(forDecode, cs));
            if (s.isEmpty()) continue;
            JunkDetector.FeatureComponents fc = detector.scoreWithFeatureComponents(s);
            if (fc == null || fc.classifierWeights == null
                    || fc.classifierWeights.length < 9) {
                origLogit.put(name, fc != null ? fc.logit : 0f);
                newLogit.put(name, fc != null ? fc.logit : 0f);
                continue;
            }
            origLogit.put(name, fc.logit);
            // Recompute with adjusted weights — only for LATIN dominant script
            if ("LATIN".equals(fc.dominantScript)) {
                float[] w = fc.classifierWeights;
                float bias = w[8];
                float newL = bias
                        + w[0] * fc.z1
                        + w[1] * fc.z2
                        + w[2] * fc.z3
                        + w4New  * fc.z4   // tweaked
                        + w[4] * fc.z5
                        + w[5] * fc.z6
                        + w[6] * fc.z7
                        + w8New  * fc.z8;  // tweaked
                newLogit.put(name, newL);
            } else {
                // Non-LATIN: keep original
                newLogit.put(name, fc.logit);
            }
        }
        if (origLogit.size() < 2) return null;

        Result r = new Result();
        r.origWinner = argmax(origLogit);
        r.newWinner = argmax(newLogit);
        return r;
    }

    private static String argmax(Map<String, Float> m) {
        String best = null;
        float bestV = Float.NEGATIVE_INFINITY;
        for (Map.Entry<String, Float> e : m.entrySet()) {
            if (e.getValue() > bestV) {
                bestV = e.getValue();
                best = e.getKey();
            }
        }
        return best;
    }

    private static byte[] stripBom(byte[] b) {
        if (b.length >= 3 && (b[0] & 0xFF) == 0xEF && (b[1] & 0xFF) == 0xBB
                && (b[2] & 0xFF) == 0xBF) {
            return Arrays.copyOfRange(b, 3, b.length);
        }
        return b;
    }

    private static final class Result {
        String origWinner;
        String newWinner;
    }
}
