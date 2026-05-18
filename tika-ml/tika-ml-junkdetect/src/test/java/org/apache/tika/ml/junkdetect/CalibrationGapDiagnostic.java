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
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.GZIPInputStream;

import org.apache.tika.quality.TextQualityScore;

/**
 * Cross-script calibration gap diagnostic.  Measures whether
 * {@link JunkDetector}'s per-script classifiers produce comparable scores
 * across scripts — the design intent of the per-script z-score calibration.
 *
 * <p>For each labeled source charset, walks its devtest records and:
 * <ol>
 *   <li>Scores the CORRECT decoding (record bytes decoded under the source
 *       charset) — this is "clean text scored against the script the
 *       classifier was calibrated on."</li>
 *   <li>For each target charset in the wrong-decoding pool, scores the
 *       MOJIBAKE decoding (same bytes decoded under the wrong charset) —
 *       this is "mojibake text scored against whichever script's classifier
 *       the wrong decode happens to land in."</li>
 * </ol>
 *
 * <p>Aggregates two tables:
 * <ul>
 *   <li><b>per-script clean baseline</b>: mean &amp; sd of logit for
 *       correct decodes, grouped by dominant script.  If these means
 *       differ wildly across scripts, classifiers are not cross-script
 *       comparable on clean text alone.</li>
 *   <li><b>per-script mojibake baseline</b>: mean &amp; sd of logit for
 *       wrong-decoded text, grouped by the dominant script of the
 *       resulting text.  Compared to the clean baseline for the same
 *       script, the gap is the classifier's discriminating power.  A
 *       small gap = "permissive" classifier (accepts mojibake nearly as
 *       readily as real text).</li>
 * </ul>
 *
 * <p>The smoking gun for cross-script bias is when one script's
 * mojibake-mean is close to or above another script's clean-mean —
 * meaning wrong decodes can outscore correct decodes across the
 * classifier boundary.
 *
 * <p>Usage:
 * <pre>
 *   --devtest-dir &lt;dir&gt;         default ~/data/charsets/devtest
 *   --source-charsets cs1,cs2     (default: windows-1252,GB18030,
 *                                  x-windows-949,EUC-JP,Shift_JIS,
 *                                  Big5-HKSCS,UTF-8)
 *   --target-charsets cs1,cs2     (default: same as source)
 *   --records-per N               default 200
 *   --detail &lt;tsv&gt;              optional per-record TSV output
 *   --collapse-whitespace         collapse ASCII whitespace runs before
 *                                 scoring (mirror what we'd do at live
 *                                 scoring time)
 * </pre>
 */
public final class CalibrationGapDiagnostic {

    private CalibrationGapDiagnostic() {
    }

    public static void main(String[] args) throws Exception {
        Path devtestDir = Paths.get(System.getProperty("user.home")
                + "/data/charsets/devtest");
        String[] sourceCharsets = {
                "windows-1252", "GB18030", "x-windows-949",
                "EUC-JP", "Shift_JIS", "Big5-HKSCS", "UTF-8",
                "windows-1250", "ISO-8859-2", "windows-1251"
        };
        String[] targetCharsets = null; // default: same as source
        int recordsPer = 200;
        Path detailOut = null;
        boolean collapseWhitespace = false;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--devtest-dir":
                    devtestDir = Paths.get(args[++i].replaceFirst("^~",
                            System.getProperty("user.home")));
                    break;
                case "--source-charsets":
                    sourceCharsets = args[++i].split(",");
                    break;
                case "--target-charsets":
                    targetCharsets = args[++i].split(",");
                    break;
                case "--records-per":
                    recordsPer = Integer.parseInt(args[++i]);
                    break;
                case "--detail":
                    detailOut = Paths.get(args[++i].replaceFirst("^~",
                            System.getProperty("user.home")));
                    break;
                case "--collapse-whitespace":
                    collapseWhitespace = true;
                    break;
                default:
                    System.err.println("Unknown arg: " + args[i]);
                    System.exit(1);
            }
        }
        if (targetCharsets == null) targetCharsets = sourceCharsets;

        JunkDetector detector = JunkDetector.loadFromClasspath();

        BufferedWriter detail = detailOut != null
                ? Files.newBufferedWriter(detailOut, StandardCharsets.UTF_8) : null;
        if (detail != null) {
            detail.write("source_cs\ttarget_cs\trecord_idx\tbyte_len\t"
                    + "decoded_script\tlogit\tp_clean\tis_mojibake\n");
        }

        Map<String, Agg> cleanByScript = new TreeMap<>();
        Map<String, Map<String, Agg>> mojibakeBySrcTarget = new LinkedHashMap<>();
        Map<String, Agg> mojibakeByLandingScript = new TreeMap<>();

        try {
            for (String srcName : sourceCharsets) {
                Path file = devtestDir.resolve(srcName + ".bin.gz");
                if (!Files.exists(file)) {
                    System.err.println("Missing devtest file: " + file);
                    continue;
                }
                Charset srcCs = Charset.forName(srcName);

                List<byte[]> records = readRecords(file, recordsPer);
                System.err.printf("loaded %d records from %s%n",
                        records.size(), file.getFileName());

                Map<String, Agg> perTarget = mojibakeBySrcTarget.computeIfAbsent(
                        srcName, k -> new LinkedHashMap<>());

                for (int rIdx = 0; rIdx < records.size(); rIdx++) {
                    byte[] bytes = records.get(rIdx);

                    String cleanText = new String(bytes, srcCs);
                    if (collapseWhitespace) {
                        cleanText = WhitespaceImpactDiagnostic.collapseWhitespace(cleanText);
                    }
                    TextQualityScore cleanScore = detector.score(cleanText);
                    if (!cleanScore.isUnknown()) {
                        cleanByScript.computeIfAbsent(
                                cleanScore.getDominantScript(), k -> new Agg())
                                .add(cleanScore.getZScore());
                    }
                    if (detail != null) {
                        detail.write(String.format(Locale.ROOT,
                                "%s\t%s\t%d\t%d\t%s\t%.4f\t%.4f\t%s%n",
                                srcName, srcName, rIdx, bytes.length,
                                cleanScore.getDominantScript(),
                                cleanScore.getZScore(), cleanScore.getPClean(),
                                "no"));
                    }

                    for (String tgtName : targetCharsets) {
                        if (tgtName.equals(srcName)) continue;
                        Charset tgtCs;
                        try {
                            tgtCs = Charset.forName(tgtName);
                        } catch (Exception e) {
                            continue;
                        }
                        String mojiText = new String(bytes, tgtCs);
                        if (collapseWhitespace) {
                            mojiText = WhitespaceImpactDiagnostic.collapseWhitespace(mojiText);
                        }
                        TextQualityScore mojiScore = detector.score(mojiText);
                        if (mojiScore.isUnknown()) continue;
                        perTarget.computeIfAbsent(tgtName, k -> new Agg())
                                .add(mojiScore.getZScore());
                        mojibakeByLandingScript.computeIfAbsent(
                                mojiScore.getDominantScript(), k -> new Agg())
                                .add(mojiScore.getZScore());
                        if (detail != null) {
                            detail.write(String.format(Locale.ROOT,
                                    "%s\t%s\t%d\t%d\t%s\t%.4f\t%.4f\t%s%n",
                                    srcName, tgtName, rIdx, bytes.length,
                                    mojiScore.getDominantScript(),
                                    mojiScore.getZScore(),
                                    mojiScore.getPClean(), "yes"));
                        }
                    }
                }
            }
        } finally {
            if (detail != null) detail.close();
        }

        System.out.println();
        System.out.println("=== Per-script CLEAN baseline (correct decodes, "
                + "grouped by dominant script of decoded text) ===");
        printAgg(cleanByScript, "script", "n", "mean", "sd");

        System.out.println();
        System.out.println("=== Per-script MOJIBAKE baseline (wrong decodes, "
                + "grouped by dominant script of decoded text) ===");
        printAgg(mojibakeByLandingScript, "script", "n", "mean", "sd");

        System.out.println();
        System.out.println("=== Clean-vs-mojibake GAP per script (the "
                + "discriminating power of each per-script classifier) ===");
        System.out.printf("  %-12s  %10s  %10s  %10s%n",
                "script", "clean_mean", "moji_mean", "gap");
        for (String sc : cleanByScript.keySet()) {
            Agg cleanAgg = cleanByScript.get(sc);
            Agg mojiAgg = mojibakeByLandingScript.get(sc);
            if (mojiAgg == null) continue;
            double cleanMean = cleanAgg.mean();
            double mojiMean = mojiAgg.mean();
            System.out.printf("  %-12s  %+10.3f  %+10.3f  %+10.3f%n",
                    sc, cleanMean, mojiMean, cleanMean - mojiMean);
        }

        System.out.println();
        System.out.println("=== Per-source-charset → target-charset mojibake "
                + "logit (mean over records) ===");
        System.out.printf("  %-14s  %-14s  %6s  %8s  %8s%n",
                "source_cs", "target_cs", "n", "mean", "sd");
        for (Map.Entry<String, Map<String, Agg>> srcE : mojibakeBySrcTarget.entrySet()) {
            for (Map.Entry<String, Agg> tgtE : srcE.getValue().entrySet()) {
                Agg a = tgtE.getValue();
                System.out.printf("  %-14s  %-14s  %6d  %+8.3f  %+8.3f%n",
                        srcE.getKey(), tgtE.getKey(), a.n, a.mean(), a.sd());
            }
        }
    }

    private static void printAgg(Map<String, Agg> map,
                                 String c1, String c2, String c3, String c4) {
        System.out.printf("  %-12s  %6s  %8s  %8s%n", c1, c2, c3, c4);
        for (Map.Entry<String, Agg> e : map.entrySet()) {
            Agg a = e.getValue();
            System.out.printf("  %-12s  %6d  %+8.3f  %+8.3f%n",
                    e.getKey(), a.n, a.mean(), a.sd());
        }
    }

    /** Read up to {@code max} records from a gzipped length-prefixed file. */
    private static List<byte[]> readRecords(Path file, int max) throws IOException {
        List<byte[]> out = new ArrayList<>();
        try (InputStream raw = Files.newInputStream(file);
             GZIPInputStream gz = new GZIPInputStream(raw);
             DataInputStream dis = new DataInputStream(gz)) {
            while (out.size() < max) {
                int len;
                try {
                    len = dis.readUnsignedShort();
                } catch (EOFException eof) {
                    break;
                }
                byte[] buf = new byte[len];
                dis.readFully(buf);
                out.add(buf);
            }
        }
        return out;
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
            double var = (sumSq / n) - m * m;
            return Math.sqrt(Math.max(0, var));
        }
    }
}
