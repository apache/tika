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
package org.apache.tika.langdetect.charsoup.tools;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.tika.langdetect.charsoup.GenerativeLanguageModel;

/**
 * Self-consistency evaluation for {@link GenerativeLanguageModel}.
 *
 * <p>For each sentence in the test file, computes {@code score(text, L)}
 * for every language in the model and checks whether the argmax equals
 * the true label.  Reports overall accuracy and per-language accuracy
 * sorted from worst to best.
 *
 * <p>Accepts either:
 * <ul>
 *   <li>Flores-200 TSV: {@code lang_Script TAB text} — script suffixes are
 *       stripped and FLORES-specific codes are remapped to model codes.</li>
 *   <li>Standard corpus format: {@code lang TAB text}</li>
 * </ul>
 *
 * <h3>Usage</h3>
 * <pre>
 *   java EvalGenerativeModel \
 *       --model  generative.bin \
 *       --test   /path/to/flores200_dev.tsv \
 *       [--max-per-lang 997]
 * </pre>
 */
public class EvalGenerativeModel {

    private static final int DEFAULT_MAX_PER_LANG = 0; // 0 = unlimited
    private static final int DEFAULT_MAX_CHARS    = 0; // 0 = full sentence

    // ---- Flores-200 normalisation (mirrors CompareDetectors) ----

    private static final Set<String> FLORES_KEEP_SCRIPT_SUFFIX = Set.of(
            "ace_Arab", "arb_Latn", "bjn_Arab",
            "kas_Deva", "knc_Latn", "min_Arab", "taq_Tfng"
    );

    private static final Map<String, String> FLORES_CODE_REMAP;
    static {
        Map<String, String> m = new HashMap<>();
        m.put("arb", "ara");
        m.put("pes", "fas");
        m.put("zsm", "msa");
        m.put("lvs", "lav");
        m.put("azj", "aze");
        m.put("ekk", "est");
        m.put("npi", "nep");
        m.put("als", "sqi");
        m.put("ory", "ori");
        m.put("nor", "nob");
        m.put("cmn", "zho");
        m.put("swa", "swh");
        m.put("yid", "ydd");
        m.put("gug", "grn");
        m.put("quz", "que");
        m.put("plt", "mlg");
        m.put("pbt", "pus");
        m.put("uzn", "uzb");
        m.put("kmr", "kur");
        m.put("khk", "mon");
        FLORES_CODE_REMAP = m;
    }

    static String normalizeLang(String raw) {
        if (FLORES_KEEP_SCRIPT_SUFFIX.contains(raw)) {
            return raw;
        }
        int underscore = raw.indexOf('_');
        String base = underscore >= 0 ? raw.substring(0, underscore) : raw;
        return FLORES_CODE_REMAP.getOrDefault(base, base);
    }

    // ---- Entry point ----

    public static void main(String[] args) throws Exception {
        Path  modelPath  = null;
        Path  testPath   = null;
        int     maxPerLang      = DEFAULT_MAX_PER_LANG;
        int[]   maxCharsSet     = null; // null = full sentence only
        boolean showConfusions  = false;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--model":
                    modelPath = Paths.get(args[++i]);
                    break;
                case "--test":
                    testPath = Paths.get(args[++i]);
                    break;
                case "--max-per-lang":
                    maxPerLang = Integer.parseInt(args[++i]);
                    break;
                case "--show-confusions":
                    showConfusions = true;
                    break;
                case "--lengths": {
                    String[] parts = args[++i].split(",");
                    maxCharsSet = new int[parts.length];
                    for (int j = 0; j < parts.length; j++) {
                        maxCharsSet[j] = Integer.parseInt(parts[j].trim());
                    }
                    break;
                }
                default:
                    System.err.println("Unknown option: " + args[i]);
                    printUsage();
                    System.exit(1);
            }
        }

        if (modelPath == null || testPath == null) {
            printUsage();
            System.exit(1);
        }

        System.out.println("Loading model: " + modelPath);
        GenerativeLanguageModel model;
        try (InputStream is = new FileInputStream(modelPath.toFile())) {
            model = GenerativeLanguageModel.load(is);
        }
        System.out.printf(Locale.US, "  %d languages (%d CJK, %d non-CJK)%n",
                model.getLanguages().size(),
                model.getLanguages().stream().filter(model::isCjk).count(),
                model.getLanguages().stream().filter(l -> !model.isCjk(l)).count());

        System.out.println("Loading test data: " + testPath);
        List<LabeledSentence> data = loadTestFile(testPath);
        boolean floresMode = data.stream().anyMatch(s -> s.getLanguage().contains("_"));
        if (floresMode) {
            System.out.println("  Flores-200 mode: normalizing lang codes");
            List<LabeledSentence> normalized = new ArrayList<>(data.size());
            for (LabeledSentence s : data) {
                normalized.add(new LabeledSentence(
                        normalizeLang(s.getLanguage()), s.getText()));
            }
            data = normalized;
        }

        // Cap per language if requested
        if (maxPerLang > 0) {
            data = samplePerLang(data, maxPerLang);
        }

        Set<String> modelLangs = new java.util.HashSet<>(model.getLanguages());

        // Split into scorable (true lang is in model) and unscorable
        List<LabeledSentence> scorable   = new ArrayList<>();
        Map<String, Integer>  skipped    = new HashMap<>();
        for (LabeledSentence s : data) {
            if (modelLangs.contains(s.getLanguage())) {
                scorable.add(s);
            } else {
                skipped.merge(s.getLanguage(), 1, Integer::sum);
            }
        }
        System.out.printf(Locale.US, "  %,d sentences; %,d scorable, %,d skipped (%d langs not in model)%n",
                data.size(), scorable.size(),
                data.size() - scorable.size(), skipped.size());
        if (!skipped.isEmpty()) {
            List<String> sk = new ArrayList<>(skipped.keySet());
            java.util.Collections.sort(sk);
            System.out.println("  Skipped langs: " + sk);
        }

        // Build the set of lengths to evaluate
        int[] lengths = maxCharsSet != null ? maxCharsSet : new int[]{0};

        for (int maxChars : lengths) {
            String label = maxChars > 0 ? "@" + maxChars + " chars" : "full";
            List<LabeledSentence> run = maxChars > 0
                    ? truncate(scorable, maxChars) : scorable;

            System.out.printf(Locale.US, "%nScoring [%s]…%n", label);
            long wallStart = System.nanoTime();
            // confusions: trueLang -> (predictedLang -> count)
            Map<String, Map<String, Integer>> confusions =
                    showConfusions ? new java.util.TreeMap<>() : null;
            Map<String, int[]> perLang = evalAll(model, run, confusions);
            long elapsedMs = (System.nanoTime() - wallStart) / 1_000_000;

            int totalCorrect = 0;
            int totalCount   = 0;
            for (int[] v : perLang.values()) {
                totalCorrect += v[0];
                totalCount   += v[1];
            }

            System.out.printf(Locale.US,
                    "Overall [%s]: %.2f%%  (%,d / %,d)  in %,dms (%.0f sent/s)%n",
                    label, 100.0 * totalCorrect / totalCount,
                    totalCorrect, totalCount,
                    elapsedMs, totalCount * 1000.0 / elapsedMs);

            List<Map.Entry<String, int[]>> rows = new ArrayList<>(perLang.entrySet());
            rows.sort(Comparator.comparingDouble(
                    e -> (double) e.getValue()[0] / e.getValue()[1]));

            System.out.printf(Locale.US, "%n%-16s  %8s  %8s  %8s%n",
                    "Language", "Correct", "Total", "Acc%");
            System.out.println("-".repeat(46));
            for (Map.Entry<String, int[]> e : rows) {
                int[] v = e.getValue();
                System.out.printf(Locale.US, "%-16s  %8d  %8d  %7.2f%%%n",
                        e.getKey(), v[0], v[1], 100.0 * v[0] / v[1]);
            }

            System.out.println();
            int[] thresholds = {100, 95, 90, 80, 50};
            for (int t : thresholds) {
                long above = rows.stream()
                        .filter(e -> 100.0 * e.getValue()[0] / e.getValue()[1] >= t)
                        .count();
                System.out.printf(Locale.US, "  >= %3d%% accuracy: %3d / %d languages%n",
                        t, above, rows.size());
            }

            if (confusions != null) {
                System.out.println("\n=== Confusion distributions (wrong predictions only) ===");
                for (Map.Entry<String, Map<String, Integer>> langEntry
                        : confusions.entrySet()) {
                    String trueLang = langEntry.getKey();
                    Map<String, Integer> preds = langEntry.getValue();
                    int total = perLang.get(trueLang)[1];
                    int correct = perLang.get(trueLang)[0];
                    int wrong = total - correct;
                    if (wrong == 0) continue;
                    System.out.printf(Locale.US,
                            "%n  %s (%d wrong / %d total):  ",
                            trueLang, wrong, total);
                    preds.entrySet().stream()
                            .sorted(Map.Entry.<String, Integer>comparingByValue()
                                    .reversed())
                            .limit(10)
                            .forEach(e -> System.out.printf(Locale.US,
                                    "%s=%d ", e.getKey(), e.getValue()));
                    System.out.println();
                }
            }
        }
    }

    // ---- Scoring ----

    private static Map<String, int[]> evalAll(
            GenerativeLanguageModel model,
            List<LabeledSentence> data,
            Map<String, Map<String, Integer>> confusions) {
        Map<String, int[]> perLang = new HashMap<>();
        List<String> allLangs = model.getLanguages();

        for (LabeledSentence s : data) {
            String trueLang  = s.getLanguage();
            String predicted = argmax(model, allLangs, s.getText());
            int[]  counts    = perLang.computeIfAbsent(trueLang, k -> new int[2]);
            counts[1]++;
            if (trueLang.equals(predicted)) {
                counts[0]++;
            } else if (confusions != null && predicted != null) {
                confusions.computeIfAbsent(trueLang, k -> new HashMap<>())
                        .merge(predicted, 1, Integer::sum);
            }
        }
        return perLang;
    }

    private static String argmax(GenerativeLanguageModel model,
                                  List<String> langs, String text) {
        String best  = null;
        float  bestS = Float.NEGATIVE_INFINITY;
        for (String lang : langs) {
            float s = model.score(text, lang);
            if (!Float.isNaN(s) && s > bestS) {
                bestS = s;
                best  = lang;
            }
        }
        return best;
    }

    // ---- I/O helpers ----

    static List<LabeledSentence> loadTestFile(Path path) throws Exception {
        List<LabeledSentence> sentences = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                int tab = line.indexOf('\t');
                if (tab < 0) {
                    continue;
                }
                String lang = line.substring(0, tab).trim();
                String text = line.substring(tab + 1).trim();
                if (!lang.isEmpty() && !text.isEmpty()) {
                    sentences.add(new LabeledSentence(lang, text));
                }
            }
        }
        return sentences;
    }

    private static List<LabeledSentence> truncate(
            List<LabeledSentence> data, int maxChars) {
        List<LabeledSentence> result = new ArrayList<>(data.size());
        for (LabeledSentence s : data) {
            String t = s.getText();
            result.add(new LabeledSentence(s.getLanguage(),
                    t.length() > maxChars ? t.substring(0, maxChars) : t));
        }
        return result;
    }

    private static List<LabeledSentence> samplePerLang(
            List<LabeledSentence> data, int max) {
        Map<String, Integer> counts = new HashMap<>();
        List<LabeledSentence> result = new ArrayList<>();
        for (LabeledSentence s : data) {
            int n = counts.merge(s.getLanguage(), 1, Integer::sum);
            if (n <= max) {
                result.add(s);
            }
        }
        return result;
    }

    private static void printUsage() {
        System.err.println("Usage: EvalGenerativeModel");
        System.err.println("         --model <generative.bin>");
        System.err.println("         --test  <testFile.tsv>");
        System.err.println("         [--max-per-lang <N>]");
        System.err.println("         [--lengths 50,100,200]  (truncate sentences to N chars)");
    }
}
