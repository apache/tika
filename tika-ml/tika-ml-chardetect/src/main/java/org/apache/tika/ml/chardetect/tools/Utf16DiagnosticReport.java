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

import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.GZIPInputStream;

import org.apache.tika.detect.EncodingResult;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.ml.chardetect.MojibusterEncodingDetector;
import org.apache.tika.parser.ParseContext;

/**
 * Diagnostic: for UTF-16 test samples misclassified at short probe lengths,
 * reports the Unicode script distribution of the misclassified content.
 */
public class Utf16DiagnosticReport {

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: Utf16DiagnosticReport <test-data-dir> [probe-length]");
            System.exit(1);
        }
        Path dataDir = Paths.get(args[0]);
        int probeLen = args.length > 1 ? Integer.parseInt(args[1]) : 8;

        MojibusterEncodingDetector detector = new MojibusterEncodingDetector();

        for (String csName : new String[]{"UTF-16-BE", "UTF-16-LE"}) {
            Path file = dataDir.resolve(csName + ".bin.gz");
            if (!file.toFile().exists()) {
                System.err.println("Not found: " + file);
                continue;
            }

            Charset cs = csName.contains("LE") ? StandardCharsets.UTF_16LE : StandardCharsets.UTF_16BE;
            List<byte[]> samples = loadSamples(file);

            System.out.printf("%n=== %s at %dB probe (%d samples) ===%n", csName, probeLen, samples.size());

            int correct = 0;
            int total = 0;
            // predLabel -> script -> count
            Map<String, Map<String, Integer>> confusionByScript = new LinkedHashMap<>();
            // script -> count (for correct predictions too)
            Map<String, Integer> scriptTotals = new TreeMap<>();
            Map<String, Integer> scriptCorrect = new TreeMap<>();
            // Track null-byte stats for misclassified samples
            int misclassifiedWithNulls = 0;
            int misclassifiedWithoutNulls = 0;

            for (byte[] full : samples) {
                byte[] probe = full.length <= probeLen ? full : Arrays.copyOf(full, probeLen);
                total++;

                String decoded = new String(probe, cs);
                String script = dominantScript(decoded);
                scriptTotals.merge(script, 1, Integer::sum);

                String pred;
                try (TikaInputStream tis = TikaInputStream.get(probe)) {
                    List<EncodingResult> results = detector.detect(tis, new Metadata(), new ParseContext());
                    pred = results.isEmpty() ? "(null)" : results.get(0).getLabel();
                }

                String normPred = pred.toLowerCase(Locale.ROOT).replace("-", "");
                String normTrue = csName.toLowerCase(Locale.ROOT).replace("-", "");
                if (normPred.equals(normTrue)) {
                    correct++;
                    scriptCorrect.merge(script, 1, Integer::sum);
                } else {
                    confusionByScript.computeIfAbsent(pred, k -> new TreeMap<>())
                            .merge(script, 1, Integer::sum);
                    boolean hasNull = false;
                    for (byte b : probe) {
                        if (b == 0) {
                            hasNull = true;
                            break;
                        }
                    }
                    if (hasNull) misclassifiedWithNulls++;
                    else misclassifiedWithoutNulls++;
                }
            }

            System.out.printf("  Correct: %d/%d (%.1f%%)%n", correct, total, 100.0 * correct / total);
            System.out.printf("  Misclassified with null bytes: %d, without: %d%n",
                    misclassifiedWithNulls, misclassifiedWithoutNulls);

            // Per-script accuracy
            System.out.printf("%n  %-25s  %7s  %7s  %7s%n", "Script", "Total", "Correct", "Acc%");
            System.out.println("  " + "-".repeat(55));
            for (Map.Entry<String, Integer> e : scriptTotals.entrySet()) {
                String s = e.getKey();
                int t = e.getValue();
                int c = scriptCorrect.getOrDefault(s, 0);
                System.out.printf("  %-25s  %7d  %7d  %6.1f%%%n", s, t, c, 100.0 * c / t);
            }

            // Top confusions by predicted label, broken down by script
            System.out.printf("%n  Top confusions:%n");
            confusionByScript.entrySet().stream()
                    .sorted((a, b) -> b.getValue().values().stream().mapToInt(Integer::intValue).sum()
                            - a.getValue().values().stream().mapToInt(Integer::intValue).sum())
                    .limit(5)
                    .forEach(e -> {
                        String predLabel = e.getKey();
                        Map<String, Integer> scripts = e.getValue();
                        int predTotal = scripts.values().stream().mapToInt(Integer::intValue).sum();
                        System.out.printf("    → %-20s (%d total)%n", predLabel, predTotal);
                        scripts.entrySet().stream()
                                .sorted((a, b) -> b.getValue() - a.getValue())
                                .limit(10)
                                .forEach(se -> System.out.printf("        %-20s %5d  (%.1f%% of this confusion)%n",
                                        se.getKey(), se.getValue(), 100.0 * se.getValue() / predTotal));
                    });
        }
    }

    private static String dominantScript(String text) {
        Map<Character.UnicodeScript, Integer> counts = new LinkedHashMap<>();
        text.codePoints().forEach(cp -> {
            Character.UnicodeScript s = Character.UnicodeScript.of(cp);
            if (s != Character.UnicodeScript.COMMON && s != Character.UnicodeScript.INHERITED) {
                counts.merge(s, 1, Integer::sum);
            }
        });
        if (counts.isEmpty()) {
            return "COMMON/UNKNOWN";
        }
        return counts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .get().getKey().name();
    }

    private static List<byte[]> loadSamples(Path file) throws Exception {
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
