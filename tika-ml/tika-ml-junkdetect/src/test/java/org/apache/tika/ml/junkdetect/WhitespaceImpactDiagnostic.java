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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

import org.apache.tika.detect.EncodingResult;
import org.apache.tika.ml.chardetect.HtmlByteStripper;
import org.apache.tika.ml.chardetect.MojibusterEncodingDetector;
import org.apache.tika.quality.TextQualityComparison;
import org.apache.tika.quality.TextQualityScore;

/**
 * Tests whether whitespace mass in post-strip text biases live
 * {@link JunkDetector} scoring vs. the natural-text calibration baseline.
 *
 * <p>For each file, runs the production pipeline (BOM strip,
 * {@link HtmlByteStripper}, decode, entity expand) and scores each
 * candidate two ways:
 * <ol>
 *   <li>raw — the decoded text as-is (current production)</li>
 *   <li>ws-collapsed — runs of ASCII whitespace replaced with a single
 *       space, then trimmed</li>
 * </ol>
 *
 * <p>Reports per-candidate delta (raw_z − collapsed_z) distribution and
 * per-file tournament-outcome differences.  If raw consistently scores
 * lower (more negative) than collapsed, whitespace runs are dragging the
 * live arbitration down vs. what the natural-text calibration assumes.
 * If raw and collapsed differ rarely, whitespace isn't the culprit.
 *
 * <p>Usage mirrors {@link BatchJunkFilterEval}:
 * <pre>
 *   --file &lt;p&gt; (rep) | --list &lt;f&gt; | --dir &lt;d&gt;
 *   --candidates cs1,cs2  (default: Mojibuster pool per file)
 *   --max N
 *   --detail &lt;tsv&gt;     per-file per-candidate row
 * </pre>
 */
public final class WhitespaceImpactDiagnostic {

    private static final int READ_LIMIT = 16384;

    private WhitespaceImpactDiagnostic() {
    }

    public static void main(String[] args) throws Exception {
        List<Path> files = new ArrayList<>();
        String[] fixedCharsets = null;
        Path detailOut = null;
        int max = Integer.MAX_VALUE;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--file":
                    files.add(resolve(args[++i]));
                    break;
                case "--list":
                    for (String line : Files.readAllLines(resolve(args[++i]),
                            StandardCharsets.UTF_8)) {
                        line = line.trim();
                        if (!line.isEmpty() && !line.startsWith("#")) {
                            files.add(resolve(line));
                        }
                    }
                    break;
                case "--dir":
                    try (Stream<Path> s = Files.walk(resolve(args[++i]))) {
                        s.filter(Files::isRegularFile).forEach(files::add);
                    }
                    break;
                case "--candidates":
                    fixedCharsets = args[++i].split(",");
                    break;
                case "--max":
                    max = Integer.parseInt(args[++i]);
                    break;
                case "--detail":
                    detailOut = resolve(args[++i]);
                    break;
                default:
                    System.err.println("Unknown arg: " + args[i]);
                    System.exit(1);
            }
        }
        if (files.isEmpty()) {
            System.err.println(
                    "Usage: WhitespaceImpactDiagnostic [--file p|--list f|--dir d]... "
                            + "[--candidates cs1,...] [--max N] [--detail tsv]");
            System.exit(1);
        }
        if (files.size() > max) files = files.subList(0, max);

        JunkDetector detector = JunkDetector.loadFromClasspath();
        MojibusterEncodingDetector moji = new MojibusterEncodingDetector();

        java.io.BufferedWriter detail = detailOut != null
                ? Files.newBufferedWriter(detailOut, StandardCharsets.UTF_8) : null;
        if (detail != null) {
            detail.write("path\tcandidate\traw_z\tcollapsed_z\tdelta\t"
                    + "raw_script\tcollapsed_script\ttext_len\tcollapsed_len\t"
                    + "ws_fraction\n");
        }

        Map<String, Agg> deltaByScript = new TreeMap<>();
        List<Double> allDeltas = new ArrayList<>();
        long n = 0, tournamentDiffers = 0, evaluated = 0;

        try {
            for (Path file : files) {
                byte[] all;
                try {
                    all = Files.readAllBytes(file);
                } catch (IOException e) {
                    continue;
                }
                if (all.length == 0) continue;
                byte[] bytes = all.length > READ_LIMIT
                        ? Arrays.copyOfRange(all, 0, READ_LIMIT) : all;
                bytes = stripBom(bytes);

                byte[] stripDst = new byte[bytes.length];
                HtmlByteStripper.Result strip =
                        HtmlByteStripper.strip(bytes, 0, bytes.length, stripDst, 0);
                boolean stripUsed = strip.tagCount > 0 && strip.length > 0;
                byte[] forDecode = stripUsed
                        ? Arrays.copyOfRange(stripDst, 0, strip.length) : bytes;

                List<Charset> pool = new ArrayList<>();
                if (fixedCharsets != null) {
                    for (String c : fixedCharsets) {
                        try {
                            pool.add(Charset.forName(c));
                        } catch (Exception ignored) {
                            // unknown charset name; skip
                        }
                    }
                } else {
                    for (EncodingResult er : moji.detect(bytes)) {
                        if (!pool.contains(er.getCharset())) pool.add(er.getCharset());
                    }
                }
                if (pool.size() < 2) continue;

                Map<String, String> rawByCs = new LinkedHashMap<>();
                Map<String, String> colByCs = new LinkedHashMap<>();
                Map<String, Float> rawZ = new LinkedHashMap<>();
                Map<String, Float> colZ = new LinkedHashMap<>();
                for (Charset cs : pool) {
                    String raw = JunkFilterEncodingDetector.expandHtmlEntities(
                            new String(forDecode, cs));
                    if (raw.isEmpty()) continue;
                    String collapsed = collapseWhitespace(raw);
                    if (collapsed.isEmpty()) continue;
                    rawByCs.put(cs.name(), raw);
                    colByCs.put(cs.name(), collapsed);
                    TextQualityScore rs = detector.score(raw);
                    TextQualityScore cs2 = detector.score(collapsed);
                    float rZ = rs.isUnknown() ? 0f : rs.getZScore();
                    float cZ = cs2.isUnknown() ? 0f : cs2.getZScore();
                    rawZ.put(cs.name(), rZ);
                    colZ.put(cs.name(), cZ);
                    double delta = rZ - cZ;
                    allDeltas.add(delta);
                    String script = rs.isUnknown() ? "UNKNOWN" : rs.getDominantScript();
                    deltaByScript.computeIfAbsent(script, k -> new Agg()).add(delta);

                    if (detail != null) {
                        double wsFrac = raw.length() == 0 ? 0
                                : 1.0 - (double) collapsed.length() / raw.length();
                        detail.write(String.format(Locale.ROOT,
                                "%s\t%s\t%.4f\t%.4f\t%.4f\t%s\t%s\t%d\t%d\t%.3f%n",
                                file, cs.name(), rZ, cZ, delta,
                                script,
                                cs2.isUnknown() ? "UNKNOWN" : cs2.getDominantScript(),
                                raw.length(), collapsed.length(), wsFrac));
                    }
                }
                if (rawByCs.size() < 2) continue;
                evaluated++;

                String rawChamp = tournament(detector, rawByCs);
                String colChamp = tournament(detector, colByCs);
                if (!rawChamp.equals(colChamp)) tournamentDiffers++;
                n++;
            }
        } finally {
            if (detail != null) detail.close();
        }

        System.out.println();
        System.out.println("=== Whitespace impact summary ===");
        System.out.printf("files evaluated: %d%n", evaluated);
        System.out.printf("tournament champion differs (raw vs collapsed): %d (%.2f%%)%n",
                tournamentDiffers, 100.0 * tournamentDiffers / Math.max(1, n));

        System.out.println();
        System.out.println("Per-candidate raw_z − collapsed_z, grouped by raw dominant script:");
        System.out.printf("  %-12s  %6s  %9s  %9s%n", "script", "n", "mean", "sd");
        for (Map.Entry<String, Agg> e : deltaByScript.entrySet()) {
            Agg a = e.getValue();
            System.out.printf("  %-12s  %6d  %+9.4f  %+9.4f%n",
                    e.getKey(), a.n, a.mean(), a.sd());
        }

        System.out.println();
        System.out.println("Overall delta percentiles:");
        printPercentiles(allDeltas);
    }

    private static String tournament(JunkDetector det, Map<String, String> decoded) {
        String[] names = decoded.keySet().toArray(new String[0]);
        String champion = names[0];
        for (int i = 1; i < names.length; i++) {
            TextQualityComparison cmp = det.compare(
                    champion, decoded.get(champion),
                    names[i], decoded.get(names[i]));
            if (names[i].equals(cmp.winner())) champion = names[i];
        }
        return champion;
    }

    /** Collapse runs of ASCII whitespace ([\\t\\n\\r\\f\\v ]) to a single
     *  space, trim ends.  Mirrors what a tight body-text extractor would
     *  do — does NOT touch Unicode whitespace separators (no-break space,
     *  ideographic space) so CJK and Latin behave consistently. */
    static String collapseWhitespace(String s) {
        StringBuilder out = new StringBuilder(s.length());
        boolean lastSpace = true; // suppress leading whitespace
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            boolean isWs = c == ' ' || c == '\t' || c == '\n' || c == '\r'
                    || c == 0x0B || c == 0x0C;
            if (isWs) {
                if (!lastSpace) {
                    out.append(' ');
                    lastSpace = true;
                }
            } else {
                out.append(c);
                lastSpace = false;
            }
        }
        int len = out.length();
        if (len > 0 && out.charAt(len - 1) == ' ') out.setLength(len - 1);
        return out.toString();
    }

    private static void printPercentiles(List<Double> values) {
        if (values.isEmpty()) return;
        values.sort(Comparator.naturalOrder());
        double[] pcts = {0.05, 0.25, 0.5, 0.75, 0.95};
        System.out.printf("  n=%d  min=%+.4f  max=%+.4f  mean=%+.4f%n",
                values.size(), values.get(0), values.get(values.size() - 1),
                values.stream().mapToDouble(Double::doubleValue).average().orElse(0));
        for (double p : pcts) {
            int idx = (int) Math.floor(p * (values.size() - 1));
            System.out.printf("    p%-3d = %+.4f%n", (int) (p * 100), values.get(idx));
        }
    }

    private static Path resolve(String s) {
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

    private static final class Agg {
        long n;
        double sum;
        double sumSq;
        void add(double v) {
            n++;
            sum += v;
            sumSq += v * v;
        }

        double mean() {
            return n == 0 ? Double.NaN : sum / n;
        }

        double sd() {
            if (n < 2) return 0;
            double m = mean();
            return Math.sqrt(Math.max(0, sumSq / n - m * m));
        }
    }
}
