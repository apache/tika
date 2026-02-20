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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;

import org.apache.tika.detect.encoding.CharsetConfusables;
import org.apache.tika.detect.encoding.MlEncodingDetector;
import org.apache.tika.detect.encoding.Prediction;

/**
 * For each charset in the test set, breaks down per-sample outcomes into:
 * <ul>
 *   <li><b>Correct</b> (strict hit)</li>
 *   <li><b>Soft</b> (lenient hit — predicted a confusable)</li>
 *   <li><b>Wrong</b> (model was confident but predicted an unrelated charset)</li>
 *   <li><b>Low-conf</b> (model's top-1 prediction was right or soft but fell
 *       below the minConfidence threshold — suppressed to null)</li>
 *   <li><b>Null-wrong</b> (model was uncertain AND predicting the wrong thing)</li>
 * </ul>
 * This lets us separate the confidence-threshold problem from genuine model errors.
 *
 * <p>Usage:
 * <pre>
 *   java DiagnoseCharsetDetector --model /path/to/chardetect.bin --data /path/to/test-dir
 * </pre>
 */
public class DiagnoseCharsetDetector {

    public static void main(String[] args) throws Exception {
        Path modelPath = null;
        Path dataDir   = null;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--model":
                    modelPath = Paths.get(args[++i]);
                    break;
                case "--data":
                    dataDir = Paths.get(args[++i]);
                    break;
                default:
                    System.err.println("Unknown arg: " + args[i]);
                    System.exit(1);
            }
        }
        if (modelPath == null || dataDir == null) {
            System.err.println("Usage: DiagnoseCharsetDetector --model <path> --data <dir>");
            System.exit(1);
        }

        MlEncodingDetector detector = new MlEncodingDetector(modelPath);
        float threshold = detector.getMinConfidence();

        List<Path> testFiles = Files.list(dataDir)
                .filter(p -> p.getFileName().toString().endsWith(".bin.gz"))
                .sorted()
                .collect(Collectors.toList());

        System.out.printf(Locale.ROOT,
                "%-22s  %5s  |  %6s  %6s  %6s  %6s  %6s  | top predictions when wrong%n",
                "Charset", "N", "Correct", "Soft", "Wrong", "LowConf", "NullWrg");
        System.out.println("-".repeat(90));

        long[] totals = new long[5];
        long totalN = 0;

        for (Path f : testFiles) {
            String charset = f.getFileName().toString().replaceAll("\\.bin\\.gz$", "");
            List<byte[]> samples = loadSamples(f);
            if (samples.isEmpty()) {
                continue;
            }

            int correct = 0;
            int soft    = 0;
            int wrong   = 0;
            int lowConf = 0;
            int nullWrong = 0;

            Map<String, Integer> wrongTop1   = new LinkedHashMap<>();
            Map<String, Integer> lowConfTop1 = new LinkedHashMap<>();

            for (byte[] sample : samples) {
                List<Prediction> preds = detector.detectAll(sample, 1);
                if (preds.isEmpty()) {
                    nullWrong++;
                    continue;
                }
                Prediction top = preds.get(0);
                boolean aboveThreshold = top.getConfidence() >= threshold;
                boolean isCorrect = normalize(charset).equals(normalize(top.getLabel()));
                boolean isSoft    = !isCorrect && (
                        CharsetConfusables.isLenientMatch(charset, top.getLabel())
                        || CharsetConfusables.isLenientMatch(top.getLabel(), charset));

                if (aboveThreshold) {
                    if (isCorrect) {
                        correct++;
                    } else if (isSoft) {
                        soft++;
                    } else {
                        wrong++;
                        wrongTop1.merge(top.getLabel(), 1, Integer::sum);
                    }
                } else {
                    if (isCorrect || isSoft) {
                        lowConf++;
                        lowConfTop1.merge(top.getLabel(), 1, Integer::sum);
                    } else {
                        nullWrong++;
                        lowConfTop1.merge(top.getLabel(), 1, Integer::sum);
                    }
                }
            }

            int n = samples.size();
            String wrongSummary = wrongTop1.entrySet().stream()
                    .sorted((a, b) -> b.getValue() - a.getValue())
                    .limit(4)
                    .map(e -> e.getKey() + "×" + e.getValue())
                    .collect(Collectors.joining(", "));

            System.out.printf(Locale.ROOT,
                    "%-22s  %5d  |  %6.1f  %6.1f  %6.1f  %6.1f  %6.1f  | %s%n",
                    charset, n,
                    pct(correct, n), pct(soft, n), pct(wrong, n),
                    pct(lowConf, n), pct(nullWrong, n),
                    wrongSummary);

            totals[0] += correct;
            totals[1] += soft;
            totals[2] += wrong;
            totals[3] += lowConf;
            totals[4] += nullWrong;
            totalN += n;
        }

        System.out.println("-".repeat(90));
        System.out.printf(Locale.ROOT,
                "%-22s  %5d  |  %6.1f  %6.1f  %6.1f  %6.1f  %6.1f%n",
                "OVERALL", totalN,
                pct(totals[0], totalN), pct(totals[1], totalN), pct(totals[2], totalN),
                pct(totals[3], totalN), pct(totals[4], totalN));
        System.out.printf(Locale.ROOT,
                "%nThreshold: %.2f   Correct+Soft if threshold=0: %.1f%%%n",
                threshold, pct(totals[0] + totals[1] + totals[3], totalN));
    }

    private static String normalize(String name) {
        return name.toLowerCase(Locale.ROOT).replace("-", "").replace("_", "");
    }

    private static double pct(long n, long total) {
        return total == 0 ? 0.0 : 100.0 * n / total;
    }

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
