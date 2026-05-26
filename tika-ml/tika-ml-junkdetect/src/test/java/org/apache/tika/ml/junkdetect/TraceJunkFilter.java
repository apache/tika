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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import org.apache.tika.detect.EncodingResult;
import org.apache.tika.ml.chardetect.HtmlByteStripper;
import org.apache.tika.ml.chardetect.MojibusterEncodingDetector;
import org.apache.tika.quality.TextQualityComparison;
import org.apache.tika.quality.TextQualityScore;

/**
 * Single-file diagnostic for {@link JunkFilterEncodingDetector}.  Replicates
 * the production pipeline (BOM strip → {@link HtmlByteStripper} → decode
 * per candidate → HTML entity expansion → JunkDetector compare) and dumps
 * scores, the full pairwise comparison matrix, and the tournament outcome.
 *
 * <p>Always shows Mojibuster's proposed candidate pool for the file so you
 * can see whether the candidates the chain actually used would have
 * matched what Mojibuster wanted.
 *
 * <p>Scoring is on production-shape text (raw decoded + entity-expanded,
 * no strip-COMMON).  This matches what JunkDetector sees in the live chain.
 *
 * <p>Usage:
 * <pre>
 *   --file &lt;path&gt;            (repeatable)
 *   --candidates cs1,cs2,...  (default windows-1252,IBM850,IBM852,x-MacRoman)
 *   --sample N                (sample N chars of decoded text per candidate)
 *   --features                (per-candidate z1..z8 feature breakdown)
 *   --script-dist             (per-candidate Unicode-script byte distribution)
 *   --content-cleaner         (decode each candidate, then run text through
 *                              HtmlContentCleaner — matches the chain;
 *                              raw byte-strip otherwise)
 *   --buckets                 (per-candidate per-script bucket breakdown
 *                              and COMMON-bucket category histogram)
 *   --head-bytes N            (truncate read to first N bytes; replicates
 *                              AdaptiveProbe deep-read behavior)
 *   --no-mojibuster           (skip the Mojibuster pool view)
 *   --entity-modes            (score each candidate three ways:
 *                              raw / entity-expanded / entity-removed)
 *   --auto-candidates         (use Mojibuster's per-file pool as the
 *                              candidate set, overriding --candidates)
 * </pre>
 */
public final class TraceJunkFilter {

    private static final int READ_LIMIT = 16384;

    private TraceJunkFilter() {
    }

    public static void main(String[] args) throws Exception {
        List<Path> files = new ArrayList<>();
        String[] candidates = {"windows-1252", "IBM850", "IBM852", "x-MacRoman"};
        int sampleLen = 200;
        boolean showFeatures = false;
        boolean showScriptDist = false;
        boolean showMojibuster = true;
        boolean entityModes = false;
        boolean autoCandidates = false;
        int headBytes = 0;
        boolean contentCleaner = false;
        boolean showBuckets = false;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--head-bytes":
                    headBytes = Integer.parseInt(args[++i]);
                    break;
                case "--content-cleaner":
                    contentCleaner = true;
                    break;
                case "--buckets":
                    showBuckets = true;
                    break;
                case "--file":
                    files.add(resolvePath(args[++i]));
                    break;
                case "--candidates":
                    candidates = args[++i].split(",");
                    break;
                case "--sample":
                    sampleLen = Integer.parseInt(args[++i]);
                    break;
                case "--features":
                    showFeatures = true;
                    break;
                case "--script-dist":
                    showScriptDist = true;
                    break;
                case "--no-mojibuster":
                    showMojibuster = false;
                    break;
                case "--entity-modes":
                    entityModes = true;
                    break;
                case "--auto-candidates":
                    autoCandidates = true;
                    break;
                default:
                    System.err.println("Unknown arg: " + args[i]);
                    System.exit(1);
            }
        }
        if (files.isEmpty()) {
            System.err.println(
                    "Usage: TraceJunkFilter --file <path> [--file ...] "
                            + "[--candidates cs1,cs2,...] [--sample N] "
                            + "[--features] [--script-dist] [--content-cleaner] "
                            + "[--buckets] [--head-bytes N] "
                            + "[--no-mojibuster] [--entity-modes] [--auto-candidates]");
            System.exit(1);
        }

        JunkDetector detector = JunkDetector.loadFromClasspath();
        MojibusterEncodingDetector moji = (showMojibuster || autoCandidates)
                ? safeNewMojibuster() : null;

        Charset[] fixedCharsets = null;
        if (!autoCandidates) {
            fixedCharsets = new Charset[candidates.length];
            for (int i = 0; i < candidates.length; i++) {
                fixedCharsets[i] = Charset.forName(candidates[i]);
            }
        }

        for (Path file : files) {
            traceOne(file, detector, moji, fixedCharsets, sampleLen,
                    showFeatures, showScriptDist,
                    entityModes, autoCandidates, showMojibuster,
                    headBytes, contentCleaner, showBuckets);
        }
    }

    private static MojibusterEncodingDetector safeNewMojibuster() {
        try {
            return new MojibusterEncodingDetector();
        } catch (Throwable t) {
            System.err.println("Mojibuster unavailable: " + t);
            return null;
        }
    }

    private static void traceOne(Path file, JunkDetector detector,
                                  MojibusterEncodingDetector moji,
                                  Charset[] fixedCharsets, int sampleLen,
                                  boolean showFeatures, boolean showScriptDist,
                                  boolean entityModes, boolean autoCandidates,
                                  boolean showMojibuster,
                                  int headBytes, boolean contentCleaner,
                                  boolean showBuckets)
            throws IOException {
        byte[] all = Files.readAllBytes(file);
        int limit = headBytes > 0 ? Math.min(headBytes, all.length) : READ_LIMIT;
        byte[] bytes = all.length > limit
                ? Arrays.copyOfRange(all, 0, limit) : all;

        String shortId = file.getFileName().toString();
        if (shortId.length() > 16) shortId = shortId.substring(0, 16);

        System.out.println();
        System.out.println("==== " + shortId + "  raw=" + all.length
                + " probe=" + bytes.length + " ====");

        bytes = stripBom(bytes);

        byte[] stripDst = new byte[bytes.length];
        HtmlByteStripper.Result strip =
                HtmlByteStripper.strip(bytes, 0, bytes.length, stripDst, 0);
        boolean stripUsed = strip.tagCount > 0 && strip.length > 0;
        System.out.printf(Locale.ROOT,
                "  HTML strip: tags=%d post=%d (%.1f%% kept) used=%s%n",
                strip.tagCount, strip.length,
                100.0 * strip.length / bytes.length, stripUsed);
        byte[] forDecode = stripUsed
                ? Arrays.copyOfRange(stripDst, 0, strip.length) : bytes;

        List<EncodingResult> mojiPool = moji != null
                ? moji.detect(bytes) : java.util.Collections.emptyList();
        if (showMojibuster && moji != null) {
            System.out.println("  Mojibuster proposed pool (" + mojiPool.size() + "):");
            for (EncodingResult er : mojiPool) {
                System.out.printf(Locale.ROOT,
                        "    %-14s conf=%.2f type=%-12s label=%s%n",
                        er.getCharset().name(), er.getConfidence(),
                        er.getResultType(), er.getLabel());
            }
        }

        Charset[] charsets;
        if (autoCandidates) {
            java.util.LinkedHashSet<Charset> pool = new java.util.LinkedHashSet<>();
            for (EncodingResult er : mojiPool) pool.add(er.getCharset());
            charsets = pool.toArray(new Charset[0]);
            if (charsets.length == 0) {
                System.out.println("  (Mojibuster returned empty pool; skipping)");
                return;
            }
        } else {
            charsets = fixedCharsets;
        }

        Map<String, String> decoded = new LinkedHashMap<>();
        Map<String, TextQualityScore> scores = new LinkedHashMap<>();
        for (Charset cs : charsets) {
            // --content-cleaner replicates the live chain exactly (decode the
            // BOM-stripped probe, then HtmlContentCleaner.clean); the default
            // path is the byte-strip diagnostic, which scores DIFFERENT text.
            String s = contentCleaner
                    ? HtmlContentCleaner.clean(new String(bytes, cs))
                    : JunkFilterEncodingDetector.expandHtmlEntities(
                            new String(forDecode, cs));
            decoded.put(cs.name(), s);
            scores.put(cs.name(), detector.score(s));
        }

        System.out.println("  per-candidate scores:");
        for (String cs : decoded.keySet()) {
            TextQualityScore sc = scores.get(cs);
            System.out.printf(Locale.ROOT,
                    "    %-14s z=%7.3f script=%-10s%n",
                    cs, sc.getZScore(), sc.getDominantScript());
        }

        if (entityModes) {
            System.out.println("  per-candidate entity-mode scores "
                    + "(raw / expanded / removed):");
            for (Charset cs : charsets) {
                String decodedRaw = new String(forDecode, cs);
                String decodedExp = JunkFilterEncodingDetector
                        .expandHtmlEntities(decodedRaw);
                String decodedRem = removeHtmlEntities(decodedRaw);
                TextQualityScore sRaw = detector.score(decodedRaw);
                TextQualityScore sExp = detector.score(decodedExp);
                TextQualityScore sRem = detector.score(decodedRem);
                System.out.printf(Locale.ROOT,
                        "    %-14s raw z=%+6.3f  expanded z=%+6.3f  removed z=%+6.3f%n",
                        cs.name(), sRaw.getZScore(), sExp.getZScore(),
                        sRem.getZScore());
            }
        }

        if (showFeatures) {
            System.out.println("  per-candidate feature components:");
            for (String cs : decoded.keySet()) {
                JunkDetector.FeatureComponents f =
                        detector.scoreWithFeatureComponents(decoded.get(cs));
                printFeatureComponents(cs, f);
            }
        }

        System.out.println("  pairwise comparisons (winner / delta):");
        String[] names = decoded.keySet().toArray(new String[0]);
        for (int i = 0; i < names.length; i++) {
            for (int j = i + 1; j < names.length; j++) {
                TextQualityComparison cmp = detector.compare(
                        names[i], decoded.get(names[i]),
                        names[j], decoded.get(names[j]));
                System.out.printf(Locale.ROOT,
                        "    %-14s vs %-14s -> %-14s  delta=%.3f%n",
                        names[i], names[j], cmp.winner(), cmp.delta());
            }
        }

        System.out.println("  tournament (insertion order):");
        String champion = names[0];
        for (int i = 1; i < names.length; i++) {
            TextQualityComparison cmp = detector.compare(
                    champion, decoded.get(champion),
                    names[i], decoded.get(names[i]));
            System.out.printf(Locale.ROOT,
                    "    %-14s vs %-14s -> %-14s  delta=%.3f%n",
                    champion, names[i], cmp.winner(), cmp.delta());
            if (names[i].equals(cmp.winner())) {
                champion = names[i];
            }
        }
        System.out.println("  tournament champion: " + champion);

        if (showScriptDist) {
            for (String cs : decoded.keySet()) {
                System.out.println("  script distribution (" + cs + "):");
                printScriptDist(decoded.get(cs));
            }
        }

        if (showBuckets) {
            printBucketReport(detector, decoded);
            printCommonBucketHistogram(detector, decoded);
        }

        if (sampleLen > 0) {
            System.out.println("  decoded samples (first " + sampleLen + " chars):");
            for (String cs : decoded.keySet()) {
                String s = decoded.get(cs);
                String sample = s.substring(0, Math.min(sampleLen, s.length()))
                        .replace('\n', ' ').replace('\r', ' ');
                System.out.println("    " + cs + ": " + sample);
            }
        }
    }

    private static void printFeatureComponents(String label,
                                                JunkDetector.FeatureComponents f) {
        if (Float.isNaN(f.logit) || f.dominantScript == null
                || "UNKNOWN".equals(f.dominantScript)) {
            System.out.println("    " + label + ": UNKNOWN/NaN  dom="
                    + f.dominantScript);
            return;
        }
        System.out.printf(Locale.ROOT,
                "    %-14s z1=%+6.3f z2=%+6.3f z3=%+6.3f z4=%+6.3f "
                        + "z5=%+6.3f z6=%+6.3f z7=%+6.3f z8=%+6.3f -> logit=%+6.3f "
                        + "dom=%s bytes=%d%n",
                label, f.z1, f.z2, f.z3, f.z4, f.z5, f.z6, f.z7, f.z8,
                f.logit, f.dominantScript, f.totalBytes);
        float[] w = f.classifierWeights;
        if (w != null && w.length >= 9) {
            System.out.printf(Locale.ROOT,
                    "                   contributions: "
                            + "w1*z1=%+.3f w2*z2=%+.3f w3*z3=%+.3f w4*z4=%+.3f "
                            + "w5*z5=%+.3f w6*z6=%+.3f w7*z7=%+.3f w8*z8=%+.3f "
                            + "bias=%+.3f%n",
                    w[0] * f.z1, w[1] * f.z2, w[2] * f.z3, w[3] * f.z4,
                    w[4] * f.z5, w[5] * f.z6, w[6] * f.z7, w[7] * f.z8, w[8]);
        }
    }

    private static void printScriptDist(String text) {
        Map<String, Integer> counts = new TreeMap<>();
        int i = 0;
        while (i < text.length()) {
            int cp = text.codePointAt(i);
            counts.merge(Character.UnicodeScript.of(cp).name(), 1, Integer::sum);
            i += Character.charCount(cp);
        }
        int total = counts.values().stream().mapToInt(Integer::intValue).sum();
        if (total == 0) return;
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            double pct = 100.0 * e.getValue() / total;
            if (pct < 0.1) continue;
            System.out.printf(Locale.ROOT, "    %-14s %7d  %5.1f%%%n",
                    e.getKey(), e.getValue(), pct);
        }
    }

    /** Per-bucket z1 breakdown — counts, raw means, calibration (mu,sigma),
     *  calibrated bucket-z, and the (count * z) contribution to the doc-z1.
     *  Probes the (B) "monoscript-collapse" hypothesis: which bucket(s) on
     *  which decode drag the count-weighted aggregate? */
    private static void printBucketReport(JunkDetector det,
                                          Map<String, String> decoded) {
        System.out.println("  per-bucket z1 breakdown:");
        for (Map.Entry<String, String> ent : decoded.entrySet()) {
            String cs = ent.getKey();
            String text = java.text.Normalizer.normalize(
                    ent.getValue(), java.text.Normalizer.Form.NFC);
            int[] cps = text.codePoints().toArray();
            Map<String, double[]> buckets =
                    JunkDetector.bucketSumsAndCounts(
                            cps, det.f1TablesByScriptView());
            // Aggregate doc z1 ourselves to verify against scoreWithFeatureComponents.
            double weightedSum = 0;
            long totalCount = 0;
            System.out.println("    " + cs + ":");
            System.out.printf(Locale.ROOT,
                    "      %-14s %7s %9s %9s %9s %9s %9s%n",
                    "bucket", "count", "rawMean", "mu", "sigma", "z", "ct*z");
            // Sort by count descending for readability
            List<Map.Entry<String, double[]>> rows =
                    new ArrayList<>(buckets.entrySet());
            rows.sort((a, b) -> Double.compare(b.getValue()[1], a.getValue()[1]));
            for (Map.Entry<String, double[]> e : rows) {
                String script = e.getKey();
                long cnt = (long) e.getValue()[1];
                if (cnt == 0) continue;
                double rawMean = e.getValue()[0] / cnt;
                float[] cal = det.calibrationFor(script);
                double mu = cal == null ? Double.NaN : cal[0];
                double sigma = cal == null ? Double.NaN : cal[1];
                double z = cal == null ? Double.NaN : (rawMean - mu) / sigma;
                double ctz = z * cnt;
                if (!Double.isNaN(z)) {
                    weightedSum += ctz;
                    totalCount += cnt;
                }
                System.out.printf(Locale.ROOT,
                        "      %-14s %7d %+9.3f %+9.3f %9.3f %+9.3f %+9.1f%n",
                        script, cnt, rawMean, mu, sigma, z, ctz);
            }
            if (totalCount > 0) {
                double docZ1 = weightedSum / totalCount;
                System.out.printf(Locale.ROOT,
                        "      %-14s %7d %9s %9s %9s %+9.3f  (sum-ctz/N = doc z1)%n",
                        "TOTAL", totalCount, "", "", "", docZ1);
            }
        }
    }

    /** Histogram of bigrams that land in the COMMON bucket, broken down by
     *  the Unicode general-category-letter pair (e.g. "Zs·Zs" = space·space).
     *  Probes whether COMMON is dominated by whitespace HTML residue. */
    private static void printCommonBucketHistogram(JunkDetector det,
                                                   Map<String, String> decoded) {
        System.out.println("  COMMON bucket bigram categories (top 12 by count):");
        for (Map.Entry<String, String> ent : decoded.entrySet()) {
            String cs = ent.getKey();
            String text = java.text.Normalizer.normalize(
                    ent.getValue(), java.text.Normalizer.Form.NFC);
            int[] cps = text.codePoints().toArray();
            BigramTables tCommon = det.f1TablesFor(JunkDetector.COMMON_SCRIPT);
            Map<String, long[]> hist = new java.util.HashMap<>(); // catPair -> {count, sumLogP}
            long commonBigrams = 0;
            double commonSum = 0;
            for (int i = 0; i + 1 < cps.length; i++) {
                int a = cps[i], b = cps[i + 1];
                if (Character.isDigit(a) || Character.isDigit(b)) continue;
                String ka = JunkDetector.classKey(a);
                String kb = JunkDetector.classKey(b);
                if (!JunkDetector.COMMON_SCRIPT.equals(ka)
                        || !JunkDetector.COMMON_SCRIPT.equals(kb)) {
                    continue;
                }
                String pair = catLabel(a) + "·" + catLabel(b);
                long[] row = hist.computeIfAbsent(pair, k -> new long[2]);
                row[0]++;
                if (tCommon != null) {
                    double lp = JunkDetector.computeF1MeanLogP(
                            new int[]{a, b}, tCommon);
                    if (!Double.isNaN(lp)) {
                        commonSum += lp;
                        commonBigrams++;
                        // Encode sum-lp ×1000 in row[1] for an int aggregate.
                        row[1] += (long) (lp * 1000);
                    }
                }
            }
            System.out.println("    " + cs + ":  total=" + commonBigrams
                    + "  rawMean=" + String.format(Locale.ROOT, "%.3f",
                            commonBigrams == 0 ? 0 : commonSum / commonBigrams));
            List<Map.Entry<String, long[]>> rows =
                    new ArrayList<>(hist.entrySet());
            rows.sort((x, y) -> Long.compare(y.getValue()[0], x.getValue()[0]));
            int shown = 0;
            for (Map.Entry<String, long[]> e : rows) {
                if (shown++ >= 12) break;
                long cnt = e.getValue()[0];
                double meanLp = cnt == 0 ? 0
                        : (e.getValue()[1] / 1000.0) / cnt;
                double pct = 100.0 * cnt / Math.max(1, commonBigrams);
                System.out.printf(Locale.ROOT,
                        "      %-12s %7d (%5.1f%%)   meanLp=%+8.3f%n",
                        e.getKey(), cnt, pct, meanLp);
            }
        }
    }

    /** Short category label for diagnostic output: Zs=space-sep, Zl=line-sep,
     *  Po=other-punct, Pd=dash-punct, Ps=open, Pe=close, Sm=math, Sc=currency,
     *  Sk=modifier-sym, So=other-sym, Cc=control, Cf=format, etc. */
    private static String catLabel(int cp) {
        int t = Character.getType(cp);
        switch (t) {
            case Character.SPACE_SEPARATOR:       return "Zs";
            case Character.LINE_SEPARATOR:        return "Zl";
            case Character.PARAGRAPH_SEPARATOR:   return "Zp";
            case Character.CONTROL:               return "Cc";
            case Character.FORMAT:                return "Cf";
            case Character.CONNECTOR_PUNCTUATION: return "Pc";
            case Character.DASH_PUNCTUATION:      return "Pd";
            case Character.START_PUNCTUATION:     return "Ps";
            case Character.END_PUNCTUATION:       return "Pe";
            case Character.INITIAL_QUOTE_PUNCTUATION: return "Pi";
            case Character.FINAL_QUOTE_PUNCTUATION:   return "Pf";
            case Character.OTHER_PUNCTUATION:     return "Po";
            case Character.MATH_SYMBOL:           return "Sm";
            case Character.CURRENCY_SYMBOL:       return "Sc";
            case Character.MODIFIER_SYMBOL:       return "Sk";
            case Character.OTHER_SYMBOL:          return "So";
            default:
                return String.format(Locale.ROOT, "?%d", t);
        }
    }

    private static Path resolvePath(String s) {
        if (s.startsWith("~")) {
            s = System.getProperty("user.home") + s.substring(1);
        }
        return Paths.get(s);
    }

    /** Replace every numeric/named entity ref with a single space.
     *  Alternative to {@link JunkFilterEncodingDetector#expandHtmlEntities}
     *  — useful when entities would inject codepoints that didn't come
     *  from the candidate charset's bytes. */
    private static String removeHtmlEntities(String s) {
        s = s.replaceAll("&#\\d{1,7};", " ");
        s = s.replaceAll("&#[xX][0-9a-fA-F]{1,6};", " ");
        s = s.replaceAll("&(amp|lt|gt|quot|apos|nbsp|copy|reg);", " ");
        return s;
    }

    private static byte[] stripBom(byte[] b) {
        if (b.length >= 3 && (b[0] & 0xFF) == 0xEF && (b[1] & 0xFF) == 0xBB
                && (b[2] & 0xFF) == 0xBF) {
            return Arrays.copyOfRange(b, 3, b.length);
        }
        return b;
    }
}
