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
package org.apache.tika.detect.encoding.tools;

import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;

import org.apache.tika.detect.EncodingDetector;
import org.apache.tika.detect.encoding.ByteNgramFeatureExtractor;
import org.apache.tika.detect.encoding.CharsetConfusables;
import org.apache.tika.detect.encoding.Icu4jEncodingDetector;
import org.apache.tika.detect.encoding.MlEncodingDetector;
import org.apache.tika.detect.encoding.UniversalEncodingDetector;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;

/**
 * Compares our {@link MlEncodingDetector} against the Tika-wrapped ICU4J
 * ({@link Icu4jEncodingDetector}) and juniversalchardet
 * ({@link UniversalEncodingDetector}) on a held-out test set.
 *
 * <p>All three detectors are exercised through the same Tika
 * {@link EncodingDetector#detect(TikaInputStream, Metadata, ParseContext)}
 * interface so the comparison reflects real production behaviour.
 * Both raw (strict) and soft (confusable-aware) accuracy are shown for every
 * detector simultaneously.</p>
 *
 * <p>When {@code --raw-data} points to an <em>unfiltered</em> test set (generated
 * with {@code --no-high-byte-filter}), the tool applies the same min-high-byte
 * filter used during training and reports N(orig), N(eval), and coverage% so
 * the reader can see how much of the real-world population for each encoding
 * is pure ASCII and therefore undetectable by any byte-frequency model.</p>
 *
 * <p>Usage:
 * <pre>
 *   java EvalCharsetDetectors \
 *     --model    /path/to/chardetect.bin \
 *     --data     /path/to/test-dir-filtered    (or unfiltered if --raw-data used)
 *     --raw-data /path/to/test-dir-unfiltered  (optional; enables OOV columns)
 * </pre>
 */
public class EvalCharsetDetectors {

    private static final String NULL_LABEL = "(null)";

    // Charsets whose CJK structure requires >= 20% high bytes for meaningful signal.
    // All others (SBCS) require only >= 2%.  Exempt sets need no high bytes at all.
    private static final double OOV_THRESHOLD_CJK  = 0.80; // high-byte ratio < 0.20
    private static final double OOV_THRESHOLD_SBCS = 0.98; // high-byte ratio < 0.02
    private static final Set<String> CJK_CHARSETS = Set.of(
            "Big5", "Big5-HKSCS", "EUC-JP", "EUC-KR", "EUC-TW",
            "GB18030", "GB2312", "GBK", "Shift_JIS"
    );
    private static final Set<String> OOV_EXEMPT = Set.of(
            "US-ASCII", "UTF-16-LE", "UTF-16-BE", "UTF-32-LE", "UTF-32-BE",
            "ISO-2022-JP", "ISO-2022-KR", "ISO-2022-CN", "HZ"
    );

    public static void main(String[] args) throws Exception {
        Path modelPath = null;
        Path dataDir   = null;
        Path rawDir    = null;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--model":
                    modelPath = Paths.get(args[++i]);
                    break;
                case "--data":
                    dataDir = Paths.get(args[++i]);
                    break;
                case "--raw-data":
                    rawDir = Paths.get(args[++i]);
                    break;
                default:
                    System.err.println("Unknown argument: " + args[i]);
                    System.exit(1);
            }
        }
        if (dataDir == null && rawDir == null) {
            System.err.println(
                    "Usage: EvalCharsetDetectors [--model <path>] --data <dir> [--raw-data <dir>]");
            System.err.println("  --model omitted → uses the bundled classpath model");
            System.err.println("  --raw-data enables coverage columns; --data can be omitted when --raw-data is set");
            System.exit(1);
        }

        EncodingDetector ours = modelPath != null
                ? new MlEncodingDetector(modelPath)
                : new MlEncodingDetector();
        EncodingDetector icu  = new Icu4jEncodingDetector();
        EncodingDetector ju   = new UniversalEncodingDetector();

        // When --raw-data is supplied we apply the OOV filter here; otherwise
        // we assume the data dir is already filtered and show no coverage columns.
        boolean showCoverage = rawDir != null;
        Path evalDir = showCoverage ? rawDir : dataDir;

        List<Path> testFiles = Files.list(evalDir)
                .filter(p -> p.getFileName().toString().endsWith(".bin.gz"))
                .sorted()
                .collect(Collectors.toList());

        if (testFiles.isEmpty()) {
            System.err.println("No .bin.gz files found in: " + evalDir);
            System.exit(1);
        }

        // Header
        if (showCoverage) {
            System.out.printf(Locale.ROOT,
                    "%-22s  %16s  |  %11s  |  %11s  |  %11s%n",
                    "", "--- Coverage ---", "--- Ours ---", "-- ICU4J ---", "juniversal-");
            System.out.printf(Locale.ROOT,
                    "%-22s  %5s %5s %4s  |  %5s %5s  |  %5s %5s  |  %5s %5s%n",
                    "Charset", "Orig", "Eval", "Cov%", "Raw", "Soft",
                    "Raw", "Soft", "Raw", "Soft");
            System.out.println("-".repeat(88));
        } else {
            System.out.printf(Locale.ROOT,
                    "%-22s  %5s  |  %11s  |  %11s  |  %11s%n",
                    "", "", "--- Ours ---", "-- ICU4J ---", "juniversal-");
            System.out.printf(Locale.ROOT,
                    "%-22s  %5s  |  %5s %5s  |  %5s %5s  |  %5s %5s%n",
                    "Charset", "N", "Raw", "Soft", "Raw", "Soft", "Raw", "Soft");
            System.out.println("-".repeat(72));
        }

        long totalOrig  = 0;
        long totalEval  = 0;
        long[] totals   = new long[6]; // oursR, oursS, icuR, icuS, juR, juS

        for (Path f : testFiles) {
            String charset = f.getFileName().toString().replaceAll("\\.bin\\.gz$", "");
            List<byte[]> allSamples = loadSamples(f);
            int nOrig = allSamples.size();
            if (nOrig == 0) {
                continue;
            }

            // Apply the high-byte OOV filter when using the raw (unfiltered) set.
            List<byte[]> samples = showCoverage ? filterByOov(allSamples, charset) : allSamples;
            int nEval = samples.size();
            if (nEval == 0 && showCoverage) {
                System.out.printf(Locale.ROOT,
                        "%-22s  %5d %5d %3.0f%%  |  (no evaluable samples)%n",
                        charset, nOrig, 0, 0.0);
                totalOrig += nOrig;
                continue;
            }

            int[] counts = new int[6];
            for (byte[] sample : samples) {
                String predOurs = predict(ours, sample);
                String predIcu  = predict(icu,  sample);
                String predJu   = predict(ju,   sample);

                if (isStrict(charset, predOurs)) counts[0]++;
                if (isSoft(charset, predOurs))   counts[1]++;
                if (isStrict(charset, predIcu))  counts[2]++;
                if (isSoft(charset, predIcu))    counts[3]++;
                if (isStrict(charset, predJu))   counts[4]++;
                if (isSoft(charset, predJu))     counts[5]++;
            }

            if (showCoverage) {
                System.out.printf(Locale.ROOT,
                        "%-22s  %5d %5d %3.0f%%  |  %5.1f %5.1f  |  %5.1f %5.1f  |  %5.1f %5.1f%n",
                        charset, nOrig, nEval, pct(nEval, nOrig),
                        pct(counts[0], nEval), pct(counts[1], nEval),
                        pct(counts[2], nEval), pct(counts[3], nEval),
                        pct(counts[4], nEval), pct(counts[5], nEval));
            } else {
                System.out.printf(Locale.ROOT,
                        "%-22s  %5d  |  %5.1f %5.1f  |  %5.1f %5.1f  |  %5.1f %5.1f%n",
                        charset, nEval,
                        pct(counts[0], nEval), pct(counts[1], nEval),
                        pct(counts[2], nEval), pct(counts[3], nEval),
                        pct(counts[4], nEval), pct(counts[5], nEval));
            }

            totalOrig += nOrig;
            totalEval += nEval;
            for (int j = 0; j < 6; j++) {
                totals[j] += counts[j];
            }
        }

        if (showCoverage) {
            System.out.println("-".repeat(88));
            System.out.printf(Locale.ROOT,
                    "%-22s  %5d %5d %3.0f%%  |  %5.1f %5.1f  |  %5.1f %5.1f  |  %5.1f %5.1f%n",
                    "OVERALL", totalOrig, totalEval, pct(totalEval, totalOrig),
                    pct(totals[0], totalEval), pct(totals[1], totalEval),
                    pct(totals[2], totalEval), pct(totals[3], totalEval),
                    pct(totals[4], totalEval), pct(totals[5], totalEval));
            System.out.println("  Cov% = fraction of real-world samples with enough high-byte signal to classify");
        } else {
            System.out.println("-".repeat(72));
            System.out.printf(Locale.ROOT,
                    "%-22s  %5d  |  %5.1f %5.1f  |  %5.1f %5.1f  |  %5.1f %5.1f%n",
                    "OVERALL", totalEval,
                    pct(totals[0], totalEval), pct(totals[1], totalEval),
                    pct(totals[2], totalEval), pct(totals[3], totalEval),
                    pct(totals[4], totalEval), pct(totals[5], totalEval));
        }
    }

    // -----------------------------------------------------------------------
    //  OOV filtering
    // -----------------------------------------------------------------------

    /** Mirror of the thresholds in build_charset_training.py. */
    private static List<byte[]> filterByOov(List<byte[]> samples, String charset) {
        if (OOV_EXEMPT.contains(charset)) {
            return samples; // these encodings have no high bytes by design
        }
        double maxOov = CJK_CHARSETS.contains(charset)
                ? OOV_THRESHOLD_CJK
                : OOV_THRESHOLD_SBCS;
        List<byte[]> out = new ArrayList<>();
        for (byte[] s : samples) {
            if (ByteNgramFeatureExtractor.oovRate(s) <= maxOov) {
                out.add(s);
            }
        }
        return out;
    }

    // -----------------------------------------------------------------------
    //  Detection
    // -----------------------------------------------------------------------

    private static String predict(EncodingDetector detector, byte[] sample) {
        try (TikaInputStream tis = TikaInputStream.get(sample)) {
            Charset cs = detector.detect(tis, new Metadata(), new ParseContext());
            return cs == null ? NULL_LABEL : cs.name();
        } catch (Exception e) {
            return NULL_LABEL;
        }
    }

    // -----------------------------------------------------------------------
    //  Scoring
    // -----------------------------------------------------------------------

    private static boolean isStrict(String actual, String predicted) {
        if (NULL_LABEL.equals(predicted)) {
            return false;
        }
        return normalize(actual).equals(normalize(predicted));
    }

    private static boolean isSoft(String actual, String predicted) {
        if (NULL_LABEL.equals(predicted)) {
            return false;
        }
        if (normalize(actual).equals(normalize(predicted))) {
            return true;
        }
        return CharsetConfusables.isLenientMatch(actual, predicted)
                || CharsetConfusables.isLenientMatch(predicted, actual);
    }

    /** Lower-case and strip hyphens/underscores for name-variant tolerance. */
    private static String normalize(String name) {
        return name.toLowerCase(Locale.ROOT)
                .replace("-", "")
                .replace("_", "");
    }

    private static double pct(long correct, long total) {
        return total == 0 ? 0.0 : 100.0 * correct / total;
    }

    // -----------------------------------------------------------------------
    //  Data loading
    // -----------------------------------------------------------------------

    private static List<byte[]> loadSamples(Path file) throws IOException {
        List<byte[]> out = new ArrayList<>();
        try (InputStream fis = new FileInputStream(file.toFile());
             GZIPInputStream gis = new GZIPInputStream(fis);
             DataInputStream dis = new DataInputStream(gis)) {
            while (true) {
                int len;
                try {
                    len = dis.readUnsignedShort();
                } catch (java.io.EOFException e) {
                    break;
                }
                byte[] chunk = new byte[len];
                dis.readFully(chunk);
                out.add(chunk);
            }
        }
        return out;
    }
}
