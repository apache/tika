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

import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Per-bigram contribution diagnostic.  For each probe file, picks a
 * specific (classA vs classB) comparison and aggregates bigram
 * contributions by bigram identity.  Output: which bigrams in this
 * probe most push the decision toward classA vs classB, with byte
 * values, hit count, total contribution, and decoded chars under
 * each charset.
 *
 * <p>Used to investigate why Mojibuster gives GB18030 a slight edge
 * over windows-1252 on clearly Western European HTML probes — is it
 * a few systematic bigrams or a diffuse accumulation of small
 * contributions?</p>
 */
public final class InspectBigramContributions {

    private InspectBigramContributions() {
    }

    public static void main(String[] args) throws Exception {
        Path modelPath = null;
        Path probeDir = null;
        String classA = null;
        String classB = null;
        String[] probes = null;
        int topK = 15;
        boolean stripHtml = false;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--model":
                    modelPath = Paths.get(args[++i]);
                    break;
                case "--probe-dir":
                    probeDir = Paths.get(args[++i].replaceFirst("^~",
                            System.getProperty("user.home")));
                    break;
                case "--class-a":
                    classA = args[++i];
                    break;
                case "--class-b":
                    classB = args[++i];
                    break;
                case "--probes":
                    probes = args[++i].split(",");
                    break;
                case "--top-k":
                    topK = Integer.parseInt(args[++i]);
                    break;
                case "--strip-html":
                    stripHtml = true;
                    break;
                default:
                    System.err.println("Unknown arg: " + args[i]);
                    System.exit(1);
            }
        }
        if (modelPath == null || probeDir == null || classA == null
                || classB == null || probes == null) {
            System.err.println("Usage: InspectBigramContributions --model <bin> "
                    + "--probe-dir <dir> --class-a <c> --class-b <c> "
                    + "--probes p1,p2,... [--top-k N]");
            System.exit(1);
        }
        NaiveBayesBigramEncodingDetector det;
        try (InputStream is = Files.newInputStream(modelPath)) {
            det = new NaiveBayesBigramEncodingDetector(is);
        }
        String[] labels = det.getLabels();
        Map<String, Integer> labelIdx = new HashMap<>();
        for (int i = 0; i < labels.length; i++) labelIdx.put(labels[i], i);
        Integer a = labelIdx.get(classA);
        Integer b = labelIdx.get(classB);
        if (a == null || b == null) {
            System.err.println("Unknown class.  Available: " + labelIdx.keySet());
            System.exit(1);
        }
        Charset csA = safe(classA);
        Charset csB = safe(classB);

        for (String pid : probes) {
            Path p = probeDir.resolve(pid);
            if (!Files.exists(p)) {
                System.err.println("Missing: " + p);
                continue;
            }
            byte[] bytes = Files.readAllBytes(p);
            int rawLen = bytes.length;
            int stripTagCount = 0;
            if (stripHtml) {
                byte[] dst = new byte[bytes.length];
                HtmlByteStripper.Result r = HtmlByteStripper.strip(bytes, 0, bytes.length, dst, 0);
                stripTagCount = r.tagCount;
                if (r.tagCount >= 1) {
                    byte[] trimmed = new byte[r.length];
                    System.arraycopy(dst, 0, trimmed, 0, r.length);
                    bytes = trimmed;
                }
            }
            // Aggregate per-bigram across the probe.
            List<NaiveBayesBigramEncodingDetector.BigramContrib> contribs =
                    det.analyzeBigrams(bytes, a, b);
            // (bigram → [count, sumA, sumB])
            TreeMap<Integer, double[]> agg = new TreeMap<>();
            for (NaiveBayesBigramEncodingDetector.BigramContrib c : contribs) {
                double[] e = agg.computeIfAbsent(c.bigram, k -> new double[3]);
                e[0] += 1;
                e[1] += c.contribA;
                e[2] += c.contribB;
            }
            int totalScored = contribs.size();
            int distinct = agg.size();
            double sumA = 0;
            double sumB = 0;
            for (double[] e : agg.values()) {
                sumA += e[1];
                sumB += e[2];
            }
            double margin = (sumA - sumB) / Math.max(1, totalScored);
            String short_ = pid.contains("/")
                    ? pid.substring(pid.indexOf('/') + 1, pid.indexOf('/') + 13) : pid;
            System.out.printf(Locale.ROOT,
                    "=== %s  raw=%d strip-tags=%d post-strip=%d  scored=%d distinct=%d  total[%s]=%+9.3f  total[%s]=%+9.3f  margin/bg=%+.4f ===%n",
                    short_, rawLen, stripTagCount, bytes.length, totalScored, distinct,
                    classA, sumA, classB, sumB, margin);

            // Rank bigrams by signed accumulated diff (positive = pulls toward A).
            java.util.List<Map.Entry<Integer, double[]>> entries =
                    new java.util.ArrayList<>(agg.entrySet());
            entries.sort((x, y) -> Double.compare(
                    (y.getValue()[1] - y.getValue()[2]),
                    (x.getValue()[1] - x.getValue()[2])));

            System.out.printf(Locale.ROOT,
                    "  TOP-%d bigrams pulling toward %s (positive):%n", topK, classA);
            printBlock(entries, 0, topK, csA, csB, classA, classB, true);
            System.out.printf(Locale.ROOT,
                    "  TOP-%d bigrams pulling toward %s (negative):%n", topK, classB);
            printBlock(entries, entries.size() - topK, topK, csA, csB, classA, classB, false);
            System.out.println();
        }
    }

    private static void printBlock(List<Map.Entry<Integer, double[]>> entries,
                                   int start, int n, Charset csA, Charset csB,
                                   String classA, String classB, boolean fromHead) {
        if (start < 0) start = 0;
        int end = Math.min(start + n, entries.size());
        if (fromHead) {
            for (int i = start; i < end; i++) printRow(entries.get(i), csA, csB, classA, classB);
        } else {
            // print from end backwards (most-negative first)
            for (int i = entries.size() - 1; i >= start; i--) {
                printRow(entries.get(i), csA, csB, classA, classB);
            }
        }
    }

    private static void printRow(Map.Entry<Integer, double[]> e,
                                 Charset csA, Charset csB,
                                 String classA, String classB) {
        int bg = e.getKey();
        int b0 = (bg >>> 8) & 0xFF;
        int b1 = bg & 0xFF;
        double count = e.getValue()[0];
        double a = e.getValue()[1];
        double b = e.getValue()[2];
        byte[] bytes = new byte[]{(byte) b0, (byte) b1};
        String hi = ((b0 >= 0x80) ? "H" : "-") + ((b1 >= 0x80) ? "H" : "-");
        System.out.printf(Locale.ROOT,
                "    %02X %02X  %s  n=%6.0f  %s=%+9.3f  %s=%+9.3f  diff=%+8.3f  decode(%s)='%s' decode(%s)='%s'%n",
                b0, b1, hi, count, classA, a, classB, b, (a - b),
                classA, decode(csA, bytes), classB, decode(csB, bytes));
    }

    private static Charset safe(String name) {
        try { return Charset.forName(name); }
        catch (Exception e) { return null; }
    }

    private static String decode(Charset cs, byte[] bytes) {
        if (cs == null) return "?";
        String s = new String(bytes, cs);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            if (cp < 0x20 || cp == 0x7F) out.append(String.format(Locale.ROOT, "\\x%02X", cp));
            else if (cp == 0xFFFD) out.append("<?>");
            else out.appendCodePoint(cp);
            i += Character.charCount(cp);
        }
        return out.toString();
    }
}
