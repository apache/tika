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

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;

import org.apache.tika.langdetect.charsoup.CharSoupModel;
import org.apache.tika.langdetect.charsoup.FeatureExtractor;

/**
 * Grid ablation runner: trains all standard feature configs across multiple
 * bucket sizes in a single pass.
 * <p>
 * Configs (always run, in order):
 * <ol>
 *   <li>baseline  — bigrams + word unigrams</li>
 *   <li>+tri      — add character trigrams</li>
 *   <li>+tri+4g   — add 4-grams (with word-boundary sentinels)</li>
 *   <li>+tri+4g+5g — add 5-grams (including complete short-word grams)</li>
 * </ol>
 * <p>
 * Usage:
 * <pre>
 *   AblationRunner &lt;prepDir&gt; &lt;trainFile&gt; [options] [outputFile]
 *     --buckets 8192,16384,32768,65536   bucket sizes to test (default: all four)
 *     --max-train N                       reservoir-sample train to N sentences
 *     --allowed-langs &lt;file&gt;              one lang code per line (# = comment)
 *     --flores &lt;file&gt;                     FLORES-200 dev TSV (NOT devtest)
 *     --save-models &lt;dir&gt;                 save best model per bucket size
 * </pre>
 */
public class AblationRunner {

    /** Standard feature configs run for every bucket size. */
    private static final String[]   CFG_NAMES = {"baseline", "+tri", "+tri+4g", "+tri+4g+5g"};
    private static final boolean[]  CFG_TRI   = {false,      true,   true,      true};
    private static final boolean[]  CFG_4G    = {false,      false,  true,      true};
    private static final boolean[]  CFG_5G    = {false,      false,  false,     true};

    private static final int[] DEFAULT_BUCKETS = {8192, 16384, 32768, 65536};

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println(
                    "Usage: AblationRunner <prepDir> <trainFile> [options] [outputFile]");
            System.err.println("  --buckets 8192,16384,32768,65536");
            System.err.println("  --max-train N      subsample train to N sentences");
            System.err.println("  --allowed-langs <file>");
            System.err.println("  --flores <file>    FLORES-200 dev TSV only (not devtest)");
            System.err.println("  --compare-v5 <file>  v5 per-lang TSV for comparison");
            System.err.println("  --save-models <dir>");
            System.exit(1);
        }

        Path prepDir   = Paths.get(args[0]);
        Path trainFile = Paths.get(args[1]);

        int[] buckets      = DEFAULT_BUCKETS;
        int   maxTrain     = Integer.MAX_VALUE;
        Path  allowedFile  = null;
        Path  floresFile   = null;
        Path  compareV5File = null;
        Path  saveModelsDir = null;
        Path  outFile      = null;

        for (int i = 2; i < args.length; i++) {
            switch (args[i]) {
                case "--buckets":
                    String[] parts = args[++i].split(",");
                    buckets = new int[parts.length];
                    for (int j = 0; j < parts.length; j++) {
                        buckets[j] = Integer.parseInt(parts[j].trim());
                    }
                    break;
                case "--max-train":
                    maxTrain = Integer.parseInt(args[++i]);
                    break;
                case "--allowed-langs":
                    allowedFile = Paths.get(args[++i]);
                    break;
                case "--flores":
                    floresFile = Paths.get(args[++i]);
                    break;
                case "--compare-v5":
                    compareV5File = Paths.get(args[++i]);
                    break;
                case "--save-models":
                    saveModelsDir = Paths.get(args[++i]);
                    break;
                default:
                    outFile = Paths.get(args[i]);
            }
        }

        Set<String> allowedLangs = loadAllowedLangs(allowedFile);
        List<LabeledSentence> floresData = loadFlores(floresFile);
        Map<String, double[]> v5PerLang = loadV5PerLang(compareV5File);
        int threads = Runtime.getRuntime().availableProcessors();

        System.out.println("=== CharSoup Ablation Grid ===");
        System.out.println("Train file  : " + trainFile);
        System.out.println("Max train   : " + (maxTrain == Integer.MAX_VALUE ? "all" : maxTrain));
        System.out.println("Allowed langs: " + (allowedLangs != null ? allowedLangs.size() : "all"));
        System.out.println("FLORES      : " + (floresData != null ? floresData.size() + " sentences" : "none"));
        System.out.println("Compare v5  : " + (v5PerLang != null ? v5PerLang.size() + " langs" : "none"));
        System.out.printf(Locale.US, "Threads     : %d%n", threads);
        System.out.println();

        // Subsample train if requested
        Path effectiveTrainFile = trainFile;
        Path tempTrain = null;
        if (maxTrain < Integer.MAX_VALUE) {
            System.out.printf(Locale.US,
                    "Subsampling train to %,d sentences...%n", maxTrain);
            tempTrain = Files.createTempFile("ablation-train-", ".txt");
            tempTrain.toFile().deleteOnExit();
            subsampleTrain(trainFile, tempTrain, maxTrain, allowedLangs);
            effectiveTrainFile = tempTrain;
            System.out.printf(Locale.US, "Temp train: %s%n%n", tempTrain);
        }

        // Load dev + test
        long t0 = System.nanoTime();
        System.out.println("Loading dev + test...");
        List<LabeledSentence> dev = readReservoir(
                prepDir.resolve("dev.txt"), 100_000, allowedLangs);
        Path testPath = prepDir.resolve("test.txt");
        if (!Files.exists(testPath)) testPath = prepDir.resolve("test_raw.txt");
        List<LabeledSentence> test = readReservoir(testPath, 200_000, allowedLangs);
        System.out.printf(Locale.US,
                "Loaded: dev=%,d (%d langs)  test=%,d (%d langs)  [%.1f s]%n%n",
                dev.size(), countLangs(dev),
                test.size(), countLangs(test), elapsed(t0));

        StringBuilder report = new StringBuilder();
        report.append("=== CharSoup Ablation Grid ===\n\n");

        // Results store: [bucketIdx][configIdx][lengthIdx] = F1
        int nLengths = CompareDetectors.EVAL_LENGTHS.length;
        double[][][] testF1Grid   = new double[buckets.length][CFG_NAMES.length][nLengths];
        double[][][] floresF1Grid = new double[buckets.length][CFG_NAMES.length][nLengths];
        double[][]   trainSecs    = new double[buckets.length][CFG_NAMES.length];
        // Per-lang @20 and @50 for the last two configs (tri+4g and tri+4g+5g) per bucket
        @SuppressWarnings("unchecked")
        Map<String, Double>[][] floresPerLang20 =
                new Map[buckets.length][CFG_NAMES.length];
        @SuppressWarnings("unchecked")
        Map<String, Double>[][] floresPerLang50 =
                new Map[buckets.length][CFG_NAMES.length];

        // Main grid loop
        for (int bi = 0; bi < buckets.length; bi++) {
            int nb = buckets[bi];
            String bucketLabel = nb / 1024 + "k";
            System.out.printf(Locale.US,
                    "════════════════════════════════════════%n" +
                    "  Bucket size: %s (%,d)%n" +
                    "════════════════════════════════════════%n%n", bucketLabel, nb);
            report.append(String.format(Locale.US,
                    "\n=== Bucket size: %s ===\n\n", bucketLabel));

            Phase2Trainer lastTrainer = null;

            for (int ci = 0; ci < CFG_NAMES.length; ci++) {
                String cfgName = bucketLabel + " " + CFG_NAMES[ci];
                System.out.printf(Locale.US, "--- %s ---%n", cfgName);
                report.append(String.format(Locale.US, "--- %s ---\n", cfgName));

                t0 = System.nanoTime();
                Phase2Trainer trainer = buildTrainer(nb, threads,
                        CFG_TRI[ci], CFG_4G[ci], CFG_5G[ci], allowedLangs);
                trainer.train(effectiveTrainFile, dev);
                trainSecs[bi][ci] = elapsed(t0);

                // Length-stratified test F1
                for (int li = 0; li < nLengths; li++) {
                    int maxChars = CompareDetectors.EVAL_LENGTHS[li];
                    List<LabeledSentence> truncated =
                            CompareDetectors.truncate(test, maxChars);
                    testF1Grid[bi][ci][li] =
                            trainer.evaluateMacroF1(truncated).f1;
                }

                // FLORES length-stratified F1
                if (floresData != null) {
                    Set<String> known = new HashSet<>(trainer.getLabelIndex().keySet());
                    List<LabeledSentence> ff = new ArrayList<>();
                    for (LabeledSentence s : floresData) {
                        if (known.contains(s.getLanguage())) ff.add(s);
                    }
                    boolean collectPerLang = (ci >= CFG_NAMES.length - 2);
                    for (int li = 0; li < nLengths; li++) {
                        int maxChars = CompareDetectors.EVAL_LENGTHS[li];
                        List<LabeledSentence> truncated =
                                CompareDetectors.truncate(ff, maxChars);
                        if (collectPerLang && (maxChars == 20 || maxChars == 50)) {
                            Map<String, Double> perLang = new TreeMap<>();
                            floresF1Grid[bi][ci][li] =
                                    trainer.evaluateMacroF1(truncated, perLang).f1;
                            if (maxChars == 20) floresPerLang20[bi][ci] = perLang;
                            else               floresPerLang50[bi][ci] = perLang;
                        } else {
                            floresF1Grid[bi][ci][li] =
                                    trainer.evaluateMacroF1(truncated).f1;
                        }
                    }
                }

                // Confusion dump at @20
                List<LabeledSentence> test20 = CompareDetectors.truncate(test, 20);
                String confBlock = dumpTopConfusions(trainer, test20, 20, 20);
                System.out.print(confBlock);
                report.append(confBlock);

                System.out.printf(Locale.US, "  Train: %.1f s%n%n", trainSecs[bi][ci]);
                report.append(String.format(Locale.US, "  Train: %.1f s\n\n", trainSecs[bi][ci]));

                lastTrainer = trainer;
            }

            // Save best config model for this bucket size (last config = tri+4g+5g)
            if (saveModelsDir != null && lastTrainer != null) {
                Files.createDirectories(saveModelsDir);
                Path modelPath = saveModelsDir.resolve(
                        "model-" + bucketLabel + "-tri+4g+5g.bin");
                CharSoupModel model = ModelQuantizer.quantize(lastTrainer);
                try (OutputStream os = new BufferedOutputStream(
                        Files.newOutputStream(modelPath))) {
                    model.save(os);
                }
                System.out.printf(Locale.US, "Model saved: %s%n%n", modelPath);
                report.append(String.format(Locale.US, "Model saved: %s\n\n", modelPath));
            }
        }

        // Summary grid tables
        report.append(summaryGrid("Test F1 by length",
                buckets, testF1Grid, trainSecs));
        System.out.print(summaryGrid("Test F1 by length",
                buckets, testF1Grid, trainSecs));

        if (floresData != null) {
            report.append(summaryGrid("FLORES-200 dev F1 by length",
                    buckets, floresF1Grid, trainSecs));
            System.out.print(summaryGrid("FLORES-200 dev F1 by length",
                    buckets, floresF1Grid, trainSecs));

            // Per-language detail for the last two configs per bucket
            for (int bi = 0; bi < buckets.length; bi++) {
                for (int ci = Math.max(0, CFG_NAMES.length - 2); ci < CFG_NAMES.length; ci++) {
                    if (floresPerLang20[bi][ci] == null) continue;
                    String header = String.format(Locale.US,
                            "Per-language FLORES @20/@50  (%dk %s)",
                            buckets[bi] / 1024, CFG_NAMES[ci]);
                    String block = perLangReport(header, floresPerLang20[bi][ci],
                            floresPerLang50[bi][ci], v5PerLang);
                    System.out.print(block);
                    report.append(block);
                }
            }
        }

        report.append("\nDone.\n");
        System.out.println("Done.");

        if (outFile != null) {
            try (BufferedWriter w = Files.newBufferedWriter(
                    outFile, StandardCharsets.UTF_8)) {
                w.write(report.toString());
            }
            System.out.println("Results written to: " + outFile);
        }
    }

    /** Print a compact grid: rows = configs, columns = @20/@50/@100/... per bucket. */
    private static String summaryGrid(String title, int[] buckets,
                                      double[][][] f1Grid, double[][] trainSecs) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n=== ").append(title).append(" ===\n\n");

        // Build column headers: one group per bucket size
        int nLengths = CompareDetectors.EVAL_LENGTHS.length;
        sb.append(String.format(Locale.US, "%-16s", "config"));
        for (int bi = 0; bi < buckets.length; bi++) {
            String bk = buckets[bi] / 1024 + "k";
            for (int li = 0; li < nLengths; li++) {
                String len = CompareDetectors.EVAL_LENGTHS[li] == Integer.MAX_VALUE
                        ? "full" : "@" + CompareDetectors.EVAL_LENGTHS[li];
                sb.append(String.format(Locale.US, "  %s/%-4s", bk, len));
            }
            sb.append("  trn_s");
        }
        sb.append("\n");
        sb.append("-".repeat(16 + buckets.length * (nLengths * 9 + 7))).append("\n");

        for (int ci = 0; ci < CFG_NAMES.length; ci++) {
            sb.append(String.format(Locale.US, "%-16s", CFG_NAMES[ci]));
            for (int bi = 0; bi < buckets.length; bi++) {
                for (int li = 0; li < nLengths; li++) {
                    sb.append(String.format(Locale.US,
                            "  %8.2f%%", 100 * f1Grid[bi][ci][li]));
                }
                sb.append(String.format(Locale.US, "  %5.0f", trainSecs[bi][ci]));
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    private static Phase2Trainer buildTrainer(
            int numBuckets, int threads,
            boolean useTrigrams, boolean use4grams, boolean use5grams,
            Set<String> allowedLangs) {
        return new Phase2Trainer(numBuckets)
                .setAdamLr(0.001f)
                .setSgdLr(0.01f, 0.001f)
                .setAdamEpochs(2)
                .setMaxEpochs(6)
                .setCheckpointInterval(500_000)
                .setPatience(2)
                .setDevSubsampleSize(10_000)
                .setNumThreads(threads)
                .setVerbose(false)
                .setPreprocessed(true)
                .setUseTrigrams(useTrigrams)
                .setUse4grams(use4grams)
                .setUse5grams(use5grams)
                .setUseWordUnigrams(true)
                .setAllowedLangs(allowedLangs);
    }

    private static String dumpTopConfusions(
            Phase2Trainer trainer, List<LabeledSentence> data,
            int maxChars, int topN) {
        FeatureExtractor ext = trainer.getExtractor();
        int[] featureBuf = new int[trainer.getNumBuckets()];
        float[] logitBuf = new float[trainer.getNumClasses()];
        Map<String, Integer> pairCounts = new HashMap<>();
        for (LabeledSentence s : data) {
            String pred = trainer.predictBuffered(
                    s.getText(), ext, featureBuf, logitBuf);
            if (!pred.equals(s.getLanguage())) {
                pairCounts.merge(s.getLanguage() + " -> " + pred, 1, Integer::sum);
            }
        }
        List<Map.Entry<String, Integer>> entries =
                new ArrayList<>(pairCounts.entrySet());
        entries.sort(Map.Entry.<String, Integer>comparingByValue().reversed());
        int show = Math.min(topN, entries.size());
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(Locale.US,
                "  Top %d confusions @%d chars:%n", show, maxChars));
        for (int i = 0; i < show; i++) {
            Map.Entry<String, Integer> e = entries.get(i);
            sb.append(String.format(Locale.US,
                    "    %-30s  %5d%n", e.getKey(), e.getValue()));
        }
        return sb.toString();
    }

    /**
     * Reservoir-sample up to {@code maxLines} from the train file,
     * respecting allowedLangs, and write to {@code dest}.
     */
    private static void subsampleTrain(Path src, Path dest,
                                       int maxLines,
                                       Set<String> allowedLangs) throws Exception {
        String[] reservoir = new String[maxLines];
        Random rng = new Random(42);
        int seen = 0;
        try (BufferedReader br = Files.newBufferedReader(src, StandardCharsets.UTF_8)) {
            String line;
            while ((line = br.readLine()) != null) {
                int tab = line.indexOf('\t');
                if (tab < 0) continue;
                if (allowedLangs != null
                        && !allowedLangs.contains(line.substring(0, tab))) continue;
                if (seen < maxLines) {
                    reservoir[seen] = line;
                } else {
                    int j = rng.nextInt(seen + 1);
                    if (j < maxLines) reservoir[j] = line;
                }
                seen++;
            }
        }
        int fill = Math.min(seen, maxLines);
        try (BufferedWriter bw = Files.newBufferedWriter(dest, StandardCharsets.UTF_8)) {
            for (int i = 0; i < fill; i++) {
                bw.write(reservoir[i]);
                bw.newLine();
            }
        }
        System.out.printf(Locale.US,
                "  Sampled %,d / %,d lines to temp file%n", fill, seen);
    }

    private static List<LabeledSentence> readReservoir(
            Path file, int maxLines, Set<String> allowedLangs) throws Exception {
        LabeledSentence[] reservoir = new LabeledSentence[maxLines];
        Random rng = new Random(42);
        int seen = 0;
        try (BufferedReader br = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            while ((line = br.readLine()) != null) {
                int tab = line.indexOf('\t');
                if (tab < 0) continue;
                String lang = line.substring(0, tab);
                if (allowedLangs != null && !allowedLangs.contains(lang)) continue;
                LabeledSentence s = new LabeledSentence(lang, line.substring(tab + 1));
                if (seen < maxLines) {
                    reservoir[seen] = s;
                } else {
                    int j = rng.nextInt(seen + 1);
                    if (j < maxLines) reservoir[j] = s;
                }
                seen++;
            }
        }
        int fill = Math.min(seen, maxLines);
        List<LabeledSentence> result = new ArrayList<>(fill);
        for (int i = 0; i < fill; i++) result.add(reservoir[i]);
        return result;
    }

    private static List<LabeledSentence> loadFlores(Path file) throws Exception {
        if (file == null) return null;
        List<LabeledSentence> result = new ArrayList<>();
        try (BufferedReader br = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            while ((line = br.readLine()) != null) {
                int tab = line.indexOf('\t');
                if (tab < 0) continue;
                String rawLang = line.substring(0, tab);
                String lang = CompareDetectors.FLORES_KEEP_SCRIPT_SUFFIX.contains(rawLang)
                        ? rawLang : CompareDetectors.normalizeLang(rawLang);
                result.add(new LabeledSentence(lang, line.substring(tab + 1)));
            }
        }
        return result;
    }

    private static Set<String> loadAllowedLangs(Path file) throws Exception {
        if (file == null) return null;
        Set<String> langs = new HashSet<>();
        try (BufferedReader br = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.strip();
                if (!line.isEmpty() && !line.startsWith("#")) langs.add(line);
            }
        }
        return langs;
    }

    private static int countLangs(List<LabeledSentence> data) {
        Set<String> langs = new HashSet<>();
        for (LabeledSentence s : data) langs.add(s.getLanguage());
        return langs.size();
    }

    private static double elapsed(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000_000.0;
    }

    /**
     * Loads a v5 per-lang TSV with columns: lang, f1@20, f1@50, [f1@100, ...].
     * Returns null if file is null.
     */
    private static Map<String, double[]> loadV5PerLang(Path file) throws Exception {
        if (file == null) return null;
        Map<String, double[]> result = new HashMap<>();
        try (BufferedReader br = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line = br.readLine(); // skip header
            while ((line = br.readLine()) != null) {
                String[] cols = line.split("\t");
                if (cols.length < 3) continue;
                String lang = cols[0].trim();
                double f1at20 = Double.parseDouble(cols[1]);
                double f1at50 = Double.parseDouble(cols[2]);
                result.put(lang, new double[]{f1at20, f1at50});
            }
        }
        return result;
    }

    /**
     * Builds a per-language report table sorted by ours@20 ascending (worst first).
     * Shows our @20 and @50, v5's @20 and @50 (if available), and deltas.
     */
    private static String perLangReport(
            String title,
            Map<String, Double> ours20,
            Map<String, Double> ours50,
            Map<String, double[]> v5PerLang) {

        StringBuilder sb = new StringBuilder();
        sb.append("\n=== ").append(title).append(" ===\n");

        boolean hasV5 = (v5PerLang != null && !v5PerLang.isEmpty());
        if (hasV5) {
            sb.append(String.format(Locale.US,
                    "%-8s  %8s  %7s  %7s  |  %8s  %7s  %7s%n",
                    "lang", "ours@20", "v5@20", "Δ@20", "ours@50", "v5@50", "Δ@50"));
            sb.append("-".repeat(70)).append("\n");
        } else {
            sb.append(String.format(Locale.US,
                    "%-8s  %8s  %8s%n", "lang", "ours@20", "ours@50"));
            sb.append("-".repeat(28)).append("\n");
        }

        List<String> langs = new ArrayList<>(ours20.keySet());
        langs.sort(Comparator.comparingDouble(ours20::get));

        for (String lang : langs) {
            double o20 = ours20.getOrDefault(lang, Double.NaN);
            double o50 = (ours50 != null) ? ours50.getOrDefault(lang, Double.NaN) : Double.NaN;
            if (hasV5 && v5PerLang.containsKey(lang)) {
                double[] v = v5PerLang.get(lang);
                sb.append(String.format(Locale.US,
                        "%-8s  %7.2f%%  %6.2f%%  %+6.2f%%  |  %7.2f%%  %6.2f%%  %+6.2f%%%n",
                        lang, o20 * 100, v[0] * 100, (o20 - v[0]) * 100,
                        o50 * 100, v[1] * 100, (o50 - v[1]) * 100));
            } else if (hasV5) {
                sb.append(String.format(Locale.US,
                        "%-8s  %7.2f%%  %6s   %6s   |  %7.2f%%  %6s   %6s%n",
                        lang, o20 * 100, "n/a", "n/a", o50 * 100, "n/a", "n/a"));
            } else {
                sb.append(String.format(Locale.US,
                        "%-8s  %7.2f%%  %7.2f%%%n", lang, o20 * 100, o50 * 100));
            }
        }

        // Summary line
        double avgOurs20 = ours20.values().stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double avgOurs50 = (ours50 != null)
                ? ours50.values().stream().mapToDouble(Double::doubleValue).average().orElse(0)
                : Double.NaN;
        if (hasV5) {
            List<double[]> matched = new ArrayList<>();
            for (String lang : ours20.keySet()) {
                if (v5PerLang.containsKey(lang)) matched.add(v5PerLang.get(lang));
            }
            double avgV20 = matched.stream().mapToDouble(a -> a[0]).average().orElse(0);
            double avgV50 = matched.stream().mapToDouble(a -> a[1]).average().orElse(0);
            sb.append("-".repeat(70)).append("\n");
            sb.append(String.format(Locale.US,
                    "%-8s  %7.2f%%  %6.2f%%  %+6.2f%%  |  %7.2f%%  %6.2f%%  %+6.2f%%%n",
                    "MACRO", avgOurs20 * 100, avgV20 * 100, (avgOurs20 - avgV20) * 100,
                    avgOurs50 * 100, avgV50 * 100, (avgOurs50 - avgV50) * 100));
        } else {
            sb.append("-".repeat(28)).append("\n");
            sb.append(String.format(Locale.US,
                    "%-8s  %7.2f%%  %7.2f%%%n", "MACRO", avgOurs20 * 100, avgOurs50 * 100));
        }
        sb.append("\n");
        return sb.toString();
    }
}
