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
package org.apache.tika.ml.junkdetect.tools;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.zip.GZIPOutputStream;

/**
 * Builds per-script positive training data for the junk detector from MADLAD-400
 * and Wikipedia sentence files.
 *
 * <p>Script groups are derived entirely from the data: for each language directory
 * the dominant Unicode script is detected by histogramming {@link Character.UnicodeScript}
 * over a sample of sentences (COMMON, INHERITED, and UNKNOWN pseudo-scripts excluded).
 * Languages that share the same dominant script are pooled.  No script groups are
 * hardcoded.
 *
 * <p>The total byte budget is distributed across script groups proportionally to
 * each group's empirical byte-bigram entropy, measured from a small sample.
 * Scripts with high entropy (e.g. CJK, which has thousands of distinct 3-byte
 * codepoints) receive a proportionally larger allocation than low-entropy scripts
 * (e.g. Arabic, whose UTF-8 high bytes cluster in a narrow 0xD8-0xDB range).
 * This ensures every script's bigram table is estimated with comparable statistical
 * quality regardless of character-set size.
 *
 * <p>Within each script group the byte budget is distributed evenly across its
 * member languages, ensuring diversity (no single language dominates).
 *
 * <p>Input format ({@code sentences_madlad.txt} and {@code sentences_wikipedia.txt}):
 * {@code lineNum TAB text}, UTF-8.  MADLAD records contain literal {@code \n} escape
 * sequences as sub-sentence separators (full scraped documents); Wikipedia records
 * are individual sentences.  Both are split/cleaned to sentence-level strings.
 *
 * <p>Output:
 * <pre>
 *   output-dir/
 *     {script}.train.gz   — 80% split, one NFC-normalised sentence per line
 *     {script}.dev.gz     — 10% split, used for calibration (mu/sigma)
 *     {script}.test.gz    — 10% split, held out for final evaluation only
 *     manifest.tsv        — per-script stats: entropy, budget, bytes written, languages
 * </pre>
 *
 * <p>Usage:
 * <pre>
 *   java BuildJunkTrainingData \
 *     --data-dir   ~/datasets/madlad/data \
 *     --output-dir ~/datasets/madlad/junkdetect \
 *     [--total-budget-bytes 50000000]
 * </pre>
 */
public class BuildJunkTrainingData {

    // -----------------------------------------------------------------------
    // Defaults
    // -----------------------------------------------------------------------

    /** Lines read per language to determine dominant script. */
    private static final int DEFAULT_SCRIPT_SAMPLE_LINES = 2_000;

    /**
     * UTF-8 bytes loaded per script group for entropy estimation.
     * Budget is spread evenly across languages in the group.
     * 200KB is enough to observe the bigram distribution reliably.
     */
    private static final long ENTROPY_SAMPLE_BYTES = 200_000L;

    /**
     * Total UTF-8 byte budget across all script groups.  Divided proportionally
     * by bigram entropy after the sampling phase.  50MB gives ~1–3MB per script
     * on average across 34 groups; scale up for production runs.
     */
    private static final long DEFAULT_TOTAL_BUDGET_BYTES = 50_000_000L;

    /**
     * Maximum UTF-8 bytes any single language may contribute to its script
     * bucket.  Prevents one language (e.g. {@code zho} with 8 GB of MADLAD)
     * from dominating a multi-language script.  Languages with less than this
     * available take what they have; languages above the cap get truncated.
     * Default {@code 5 MB} balances diversity against per-language coverage.
     */
    private static final long DEFAULT_PER_LANGUAGE_CAP_BYTES = 5_000_000L;

    /** Minimum UTF-8 byte length for a sentence to pass the quality filter. */
    private static final int DEFAULT_MIN_BYTES = 50;

    /** Maximum fraction of codepoints that may be ASCII punctuation/digits. */
    private static final double DEFAULT_MAX_PUNC_FRAC = 0.30;

    /**
     * Minimum fraction of a sentence's non-COMMON/INHERITED codepoints that
     * must belong to the script bucket's target script for the sentence to be
     * accepted.  Lines whose target-script fraction falls below this floor are
     * dropped — typically these are off-target Wikipedia stubs (e.g. an article
     * about Gothic written almost entirely in English).  Set very low by
     * default so that legitimate mixed-script content (Japanese with kanji +
     * kana, Korean with hanja annotations, Chinese with English citations) is
     * preserved.
     */
    private static final double DEFAULT_MIN_TARGET_SCRIPT_FRAC = 0.05;

    /** Fraction of sentences written to each split (train / dev / test = 80/10/10). */
    private static final double TRAIN_FRAC = 0.80;
    private static final double DEV_FRAC   = 0.10;
    // remaining (1 - TRAIN_FRAC - DEV_FRAC) goes to the test split

    /**
     * Minimum number of sentences that must land in the dev split for a script to be
     * included in the model.  Scripts below this floor have too few samples to reliably
     * estimate calibration statistics (mu/sigma), which produces noisy z-scores and
     * inflated false positive rates.  With DEV_FRAC=0.10 the effective minimum total
     * sentence count is minDevSentences / DEV_FRAC (default: 5,000 total sentences).
     */
    private static final int DEFAULT_MIN_DEV_SENTENCES = 500;

    // -----------------------------------------------------------------------
    // Entry point
    // -----------------------------------------------------------------------

    public static void main(String[] args) throws IOException {
        Path dataDir = Paths.get(System.getProperty("user.home"), "datasets", "madlad", "data");
        Path outputDir = Paths.get(System.getProperty("user.home"), "datasets", "madlad", "junkdetect");
        int scriptSampleLines = DEFAULT_SCRIPT_SAMPLE_LINES;
        long totalBudgetBytes = DEFAULT_TOTAL_BUDGET_BYTES;
        long perLanguageCapBytes = DEFAULT_PER_LANGUAGE_CAP_BYTES;
        int minBytes = DEFAULT_MIN_BYTES;
        double maxPuncFrac = DEFAULT_MAX_PUNC_FRAC;
        double minTargetScriptFrac = DEFAULT_MIN_TARGET_SCRIPT_FRAC;
        int seed = 42;
        boolean dryRun = false;
        int minDevSentences = DEFAULT_MIN_DEV_SENTENCES;
        java.util.Set<String> dropScripts = new java.util.HashSet<>();

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--data-dir":
                    dataDir = Paths.get(args[++i]);
                    break;
                case "--output-dir":
                    outputDir = Paths.get(args[++i]);
                    break;
                case "--script-sample-lines":
                    scriptSampleLines = Integer.parseInt(args[++i]);
                    break;
                case "--total-budget-bytes":
                    totalBudgetBytes = Long.parseLong(args[++i]);
                    break;
                case "--per-language-cap-bytes":
                    perLanguageCapBytes = Long.parseLong(args[++i]);
                    break;
                case "--min-bytes":
                    minBytes = Integer.parseInt(args[++i]);
                    break;
                case "--max-punc-frac":
                    maxPuncFrac = Double.parseDouble(args[++i]);
                    break;
                case "--min-target-script-frac":
                    minTargetScriptFrac = Double.parseDouble(args[++i]);
                    break;
                case "--seed":
                    seed = Integer.parseInt(args[++i]);
                    break;
                case "--min-dev-sentences":
                    minDevSentences = Integer.parseInt(args[++i]);
                    break;
                case "--drop-scripts":
                    for (String s : args[++i].split(",")) {
                        String t = s.trim().toUpperCase();
                        if (!t.isEmpty()) dropScripts.add(t);
                    }
                    break;
                case "--dry-run":
                    dryRun = true;
                    break;
                default:
                    System.err.println("Unknown argument: " + args[i]);
                    printUsage();
                    System.exit(1);
            }
        }

        System.out.println("=== BuildJunkTrainingData ===");
        System.out.println("  data-dir:           " + dataDir);
        System.out.println("  output-dir:         " + outputDir);
        System.out.printf( "  total-budget-bytes:    %,d (%.1f MB)%n",
                totalBudgetBytes, totalBudgetBytes / 1_000_000.0);
        System.out.printf( "  per-language-cap:      %,d (%.1f MB)%n",
                perLanguageCapBytes, perLanguageCapBytes / 1_000_000.0);
        System.out.printf( "  min-bytes:             %d%n", minBytes);
        System.out.printf( "  max-punc-frac:         %.2f%n", maxPuncFrac);
        System.out.printf( "  min-target-script-frac: %.2f%n", minTargetScriptFrac);
        System.out.printf( "  min-dev-sentences:     %d  (min total ≈ %d)%n",
                minDevSentences, (int)(minDevSentences / DEV_FRAC));
        if (!dropScripts.isEmpty()) {
            System.out.println("  drop-scripts:          " + dropScripts);
        }
        System.out.println("  dry-run:               " + dryRun);

        if (!Files.isDirectory(dataDir)) {
            System.err.println("ERROR: data-dir not found: " + dataDir);
            System.exit(1);
        }

        // -----------------------------------------------------------------------
        // Phase 1: Detect dominant script per language, group languages
        // -----------------------------------------------------------------------

        System.out.println("\n--- Phase 1: Detecting dominant script per language ---");

        Map<String, List<Path>> scriptGroups = new TreeMap<>();
        Map<String, String> langToScript = new LinkedHashMap<>();

        try (var dirStream = Files.list(dataDir)) {
            List<Path> langDirs = dirStream.filter(Files::isDirectory).sorted().toList();
            for (Path langDir : langDirs) {
                String lang = langDir.getFileName().toString();
                String script = detectDominantScript(langDir, scriptSampleLines);
                langToScript.put(lang, script);
                scriptGroups.computeIfAbsent(script, k -> new ArrayList<>()).add(langDir);
                System.out.printf("  %-12s → %s%n", lang, script);
            }
        }

        if (!dropScripts.isEmpty()) {
            for (String s : dropScripts) {
                if (scriptGroups.remove(s) != null) {
                    System.out.printf("  DROP script: %s%n", s);
                }
            }
        }

        System.out.printf("%n  → %d languages, %d script groups%n",
                langToScript.size(), scriptGroups.size());

        // -----------------------------------------------------------------------
        // Phase 2: Load small sample per script, compute byte-bigram entropy
        // -----------------------------------------------------------------------

        System.out.println("\n--- Phase 2: Estimating byte-bigram entropy per script ---");

        Map<String, Double> scriptEntropy = new TreeMap<>();
        for (Map.Entry<String, List<Path>> entry : scriptGroups.entrySet()) {
            String script = entry.getKey();
            List<Path> langDirs = entry.getValue();

            long perLangSampleBytes = Math.max(ENTROPY_SAMPLE_BYTES / langDirs.size(), 2_000L);
            List<String> sample = new ArrayList<>();
            for (Path langDir : langDirs) {
                loadSentences(langDir, perLangSampleBytes, minBytes, maxPuncFrac, sample);
            }

            double entropy = computeBigramEntropy(sample);
            scriptEntropy.put(script, entropy);
            System.out.printf("  %-20s H=%.3f bits  (%d sentences)%n",
                    script, entropy, sample.size());
        }

        // -----------------------------------------------------------------------
        // Phase 3: Allocate byte budget proportional to entropy
        // -----------------------------------------------------------------------

        System.out.println("\n--- Phase 3: Allocating byte budget ---");

        double totalEntropy = scriptEntropy.values().stream()
                .mapToDouble(Double::doubleValue).sum();

        Map<String, Long> scriptBudget = new TreeMap<>();
        for (Map.Entry<String, Double> e : scriptEntropy.entrySet()) {
            long budget = (long) (totalBudgetBytes * e.getValue() / totalEntropy);
            scriptBudget.put(e.getKey(), budget);
            System.out.printf("  %-20s H=%.3f → %,d bytes (%.1f MB)%n",
                    e.getKey(), e.getValue(), budget, budget / 1_000_000.0);
        }

        if (dryRun) {
            System.out.println("\nDry-run: stopping before writing files.");
            return;
        }

        // -----------------------------------------------------------------------
        // Phase 4: Collect data, write train/dev splits
        // -----------------------------------------------------------------------

        Files.createDirectories(outputDir);
        System.out.println("\n--- Phase 4a: Round 1 — collecting with initial budgets ---");

        Random rng = new Random(seed);

        // Collect sentences and actual byte counts for every script
        Map<String, List<String>> allSentences = new LinkedHashMap<>();
        Map<String, Long> actualBytes = new LinkedHashMap<>();

        for (Map.Entry<String, Long> budgetEntry : scriptBudget.entrySet()) {
            String script = budgetEntry.getKey();
            long budget = budgetEntry.getValue();
            List<Path> langDirs = scriptGroups.get(script);
            Character.UnicodeScript targetScript = parseUnicodeScript(script);

            long perLangBytes = Math.max(budget / langDirs.size(), 1L);
            // Apply per-language cap on top of the even split, but only for
            // multi-language buckets.  For single-language scripts (e.g. KHMER,
            // HANGUL), the cap would needlessly limit a bucket that has only
            // one source; let it consume its full budget instead.
            long capPerLang = langDirs.size() > 1
                    ? Math.min(perLangBytes, perLanguageCapBytes)
                    : perLangBytes;
            List<String> sentences = new ArrayList<>();
            long totalBytesLoaded = 0;

            for (Path langDir : langDirs) {
                long remaining = budget - totalBytesLoaded;
                if (remaining <= 0) break;
                long langBytes = loadSentences(langDir,
                        Math.min(capPerLang, remaining),
                        minBytes, maxPuncFrac,
                        targetScript, minTargetScriptFrac,
                        sentences);
                totalBytesLoaded += langBytes;
                if (langBytes > 0) {
                    System.out.printf("  %-12s %-20s +%,d bytes%n",
                            script, langDir.getFileName(), langBytes);
                }
            }
            allSentences.put(script, sentences);
            actualBytes.put(script, totalBytesLoaded);
        }

        // Compute surplus bytes from data-starved scripts (< 90% of budget used)
        long surplus = 0;
        for (Map.Entry<String, Long> e : scriptBudget.entrySet()) {
            long budget = e.getValue();
            long actual = actualBytes.getOrDefault(e.getKey(), 0L);
            if (actual < budget * 0.9) {
                surplus += (budget - actual);
            }
        }

        // Round 2: redistribute surplus to saturated scripts proportional to entropy
        if (surplus > 0) {
            System.out.printf(
                    "\n--- Phase 4b: Redistributing %,d surplus bytes (%.1f MB) ---\n",
                    surplus, surplus / 1_000_000.0);

            double saturatedEntropy = scriptBudget.entrySet().stream()
                    .filter(e -> actualBytes.getOrDefault(e.getKey(), 0L) >= e.getValue() * 0.9)
                    .mapToDouble(e -> scriptEntropy.getOrDefault(e.getKey(), 0.0))
                    .sum();

            for (Map.Entry<String, Long> budgetEntry : scriptBudget.entrySet()) {
                String script = budgetEntry.getKey();
                long budget = budgetEntry.getValue();
                long actual = actualBytes.getOrDefault(script, 0L);
                if (actual < budget * 0.9) continue; // data-starved — skip

                long extra = (long) (surplus
                        * scriptEntropy.getOrDefault(script, 0.0) / saturatedEntropy);
                if (extra <= 0) continue;

                long newBudget = budget + extra;
                List<Path> langDirs = scriptGroups.get(script);
                Character.UnicodeScript targetScript = parseUnicodeScript(script);
                long perLangBytes = Math.max(newBudget / langDirs.size(), 1L);
                long capPerLang = langDirs.size() > 1
                        ? Math.min(perLangBytes, perLanguageCapBytes)
                        : perLangBytes;

                List<String> sentences = new ArrayList<>();
                long totalBytesLoaded = 0;
                for (Path langDir : langDirs) {
                    long remaining = newBudget - totalBytesLoaded;
                    if (remaining <= 0) break;
                    long langBytes = loadSentences(langDir,
                            Math.min(capPerLang, remaining),
                            minBytes, maxPuncFrac,
                            targetScript, minTargetScriptFrac,
                            sentences);
                    totalBytesLoaded += langBytes;
                }
                if (!sentences.isEmpty()) {
                    allSentences.put(script, sentences);
                    actualBytes.put(script, totalBytesLoaded);
                    System.out.printf("  %-20s +%,d extra → %,d total bytes, %,d sentences%n",
                            script, extra, totalBytesLoaded, sentences.size());
                }
            }
        }

        // Write split files
        System.out.println("\n--- Phase 4c: Writing train/dev/test splits ---");

        // manifest columns: script, entropy, budget_bytes, written_bytes, sentences, train_bytes, languages
        Map<String, long[]> manifestStats = new TreeMap<>();

        for (Map.Entry<String, List<String>> e : allSentences.entrySet()) {
            String script = e.getKey();
            List<String> sentences = e.getValue();

            int expectedDevSize = (int) (sentences.size() * DEV_FRAC);
            if (sentences.isEmpty() || expectedDevSize < minDevSentences) {
                System.out.printf(
                        "  SKIP %-20s — %,d sentences → dev=%d < min-dev-sentences=%d%n",
                        script, sentences.size(), expectedDevSize, minDevSentences);
                manifestStats.put(script, new long[]{0, 0, 0, 0, 0});
                continue;
            }

            Collections.shuffle(sentences, rng);

            int nTrain = (int) (sentences.size() * TRAIN_FRAC);
            int nDev   = (int) (sentences.size() * DEV_FRAC);
            List<String> train = sentences.subList(0, nTrain);
            List<String> dev   = sentences.subList(nTrain, nTrain + nDev);
            List<String> test  = sentences.subList(nTrain + nDev, sentences.size());

            String baseName = script.toLowerCase();
            writeGzipped(outputDir.resolve(baseName + ".train.gz"), train);
            writeGzipped(outputDir.resolve(baseName + ".dev.gz"),   dev);
            writeGzipped(outputDir.resolve(baseName + ".test.gz"),  test);

            long totalBytesLoaded = actualBytes.getOrDefault(script, 0L);
            manifestStats.put(script,
                    new long[]{totalBytesLoaded, sentences.size(), nTrain, nDev, test.size()});
            System.out.printf(
                    "  WROTE %-12s — %,d bytes, %,d sentences (train=%,d dev=%,d test=%,d)%n",
                    script, totalBytesLoaded, sentences.size(),
                    nTrain, nDev, test.size());
        }

        // -----------------------------------------------------------------------
        // Phase 5: Write manifest
        // -----------------------------------------------------------------------

        Path manifest = outputDir.resolve("manifest.tsv");
        try (BufferedWriter w = Files.newBufferedWriter(manifest, StandardCharsets.UTF_8)) {
            w.write("script\tentropy_bits\tbudget_bytes\twritten_bytes\tsentences"
                    + "\ttrain_sentences\tdev_sentences\ttest_sentences\tlanguages\n");
            for (Map.Entry<String, long[]> e : manifestStats.entrySet()) {
                String script = e.getKey();
                long[] stats = e.getValue();
                double entropy = scriptEntropy.getOrDefault(script, 0.0);
                long budget = scriptBudget.getOrDefault(script, 0L);
                String langs = scriptGroups.get(script).stream()
                        .map(p -> p.getFileName().toString())
                        .reduce((a, b) -> a + "," + b).orElse("");
                w.write(String.format("%s\t%.3f\t%d\t%d\t%d\t%d\t%d\t%d\t%s%n",
                        script, entropy, budget,
                        stats[0], stats[1], stats[2], stats[3], stats[4], langs));
            }
        }

        System.out.println("\nWrote manifest: " + manifest);
        System.out.println("Done.");
    }

    /**
     * Parses a script-bucket name (e.g. {@code "HAN"}) into a
     * {@link Character.UnicodeScript}, or returns {@code null} if the name
     * does not correspond to a real script (e.g. {@code "COMMON"} or any
     * future synthetic bucket).  Used by the corpus builder to look up the
     * target script for the {@code min-target-script-frac} filter.
     */
    static Character.UnicodeScript parseUnicodeScript(String name) {
        try {
            return Character.UnicodeScript.valueOf(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // -----------------------------------------------------------------------
    // Script detection
    // -----------------------------------------------------------------------

    /**
     * Detects the dominant Unicode script for a language by histogramming
     * {@link Character.UnicodeScript} over a sample of its sentences.
     * COMMON, INHERITED, and UNKNOWN pseudo-scripts are excluded from voting.
     * Returns "COMMON" if no script reaches at least 1% of codepoints.
     */
    static String detectDominantScript(Path langDir, int sampleLines) {
        Map<Character.UnicodeScript, Long> counts = new HashMap<>();
        long total = 0;

        outer:
        for (String filename : new String[]{"sentences_wikipedia.txt", "sentences_madlad.txt"}) {
            Path file = langDir.resolve(filename);
            if (!Files.exists(file)) {
                continue;
            }
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(Files.newInputStream(file), StandardCharsets.UTF_8))) {
                String line;
                int linesRead = 0;
                while ((line = r.readLine()) != null && linesRead < sampleLines) {
                    String text = extractText(line);
                    for (int i = 0; i < text.length(); ) {
                        int cp = text.codePointAt(i);
                        Character.UnicodeScript s = Character.UnicodeScript.of(cp);
                        if (s != Character.UnicodeScript.COMMON
                                && s != Character.UnicodeScript.INHERITED
                                && s != Character.UnicodeScript.UNKNOWN) {
                            counts.merge(s, 1L, Long::sum);
                            total++;
                        }
                        i += Character.charCount(cp);
                    }
                    linesRead++;
                }
            } catch (IOException e) {
                // Skip unreadable file; report COMMON if nothing else succeeds
            }
            if (total >= sampleLines * 10L) {
                break outer; // sufficient signal
            }
        }

        if (total == 0) {
            return "COMMON";
        }

        // Plurality with a 1% floor to suppress spurious Latin wins on mixed text
        Character.UnicodeScript best = Character.UnicodeScript.COMMON;
        long bestCount = total / 100;
        for (Map.Entry<Character.UnicodeScript, Long> e : counts.entrySet()) {
            if (e.getValue() > bestCount) {
                bestCount = e.getValue();
                best = e.getKey();
            }
        }
        return best.name();
    }

    // -----------------------------------------------------------------------
    // Entropy estimation
    // -----------------------------------------------------------------------

    /**
     * Computes the empirical byte-bigram Shannon entropy (bits) of a list of
     * UTF-8 sentences.
     *
     * <p>All 256×256 = 65,536 consecutive byte pairs are counted; entropy is
     * {@code -sum p(a,b) * log2(p(a,b))} over pairs with non-zero count.
     * Maximum theoretical value is 16 bits (all pairs equally likely).
     * Typical ranges: Latin ~8–11 bits, Arabic ~9–12, CJK ~13–15.
     */
    static double computeBigramEntropy(List<String> sentences) {
        long[] counts = new long[65536];
        long total = 0;
        for (String s : sentences) {
            byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
            for (int i = 0; i + 1 < bytes.length; i++) {
                counts[((bytes[i] & 0xFF) << 8) | (bytes[i + 1] & 0xFF)]++;
                total++;
            }
        }
        if (total == 0) {
            return 0.0;
        }
        double entropy = 0.0;
        for (long c : counts) {
            if (c > 0) {
                double p = (double) c / total;
                entropy -= p * (Math.log(p) / Math.log(2.0));
            }
        }
        return entropy;
    }

    // -----------------------------------------------------------------------
    // Sentence loading and filtering
    // -----------------------------------------------------------------------

    /**
     * Loads filtered, NFC-normalised sentences from {@code langDir} until
     * {@code maxBytes} UTF-8 bytes have been accumulated, and appends them
     * to {@code result}.
     *
     * <p>Reads {@code sentences_wikipedia.txt} before {@code sentences_madlad.txt}.
     * MADLAD records contain literal {@code \n} escape sequences as sub-sentence
     * separators (full scraped documents) and are split accordingly.
     *
     * @return total UTF-8 bytes of accepted sentences appended
     */
    static long loadSentences(Path langDir, long maxBytes, int minBytes,
                               double maxPuncFrac, List<String> result) {
        // Backwards-compatible overload: no target-script filter.
        return loadSentences(langDir, maxBytes, minBytes, maxPuncFrac,
                null, 0.0, result);
    }

    /**
     * Same as the 5-arg overload, but additionally drops sentences whose
     * fraction of {@code targetScript} codepoints (relative to all non-
     * COMMON/INHERITED codepoints) is below {@code minTargetScriptFrac}.
     * Passing {@code targetScript == null} disables the target-script filter.
     */
    static long loadSentences(Path langDir, long maxBytes, int minBytes,
                               double maxPuncFrac,
                               Character.UnicodeScript targetScript,
                               double minTargetScriptFrac,
                               List<String> result) {
        long bytesLoaded = 0;
        for (String filename : new String[]{"sentences_wikipedia.txt", "sentences_madlad.txt"}) {
            if (bytesLoaded >= maxBytes) {
                break;
            }
            Path file = langDir.resolve(filename);
            if (!Files.exists(file)) {
                continue;
            }
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(Files.newInputStream(file), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null && bytesLoaded < maxBytes) {
                    String raw = extractText(line);
                    for (String part : raw.split("\\\\n")) {
                        String text = part.replace("\\r", "")
                                .replace("\\t", " ")
                                .strip()
                                .replaceAll("\\s+", " ");
                        if (text.isEmpty()) {
                            continue;
                        }
                        String filtered = filterSentence(text, minBytes, maxPuncFrac,
                                targetScript, minTargetScriptFrac);
                        if (filtered != null) {
                            int sentBytes = filtered.getBytes(StandardCharsets.UTF_8).length;
                            result.add(filtered);
                            bytesLoaded += sentBytes;
                            if (bytesLoaded >= maxBytes) {
                                break;
                            }
                        }
                    }
                }
            } catch (IOException e) {
                System.err.println("WARNING: could not read " + file + ": " + e.getMessage());
            }
        }
        return bytesLoaded;
    }

    /**
     * Applies quality filters to a single sentence and NFC-normalises it.
     *
     * @return the normalised sentence, or {@code null} if it should be discarded
     */
    static String filterSentence(String text, int minBytes, double maxPuncFrac) {
        return filterSentence(text, minBytes, maxPuncFrac, null, 0.0);
    }

    /**
     * Same as the 3-arg overload, but additionally rejects sentences whose
     * fraction of {@code targetScript} codepoints (over non-COMMON/INHERITED
     * codepoints) is below {@code minTargetScriptFrac}.  If {@code
     * targetScript == null} the target-script filter is skipped.
     */
    static String filterSentence(String text, int minBytes, double maxPuncFrac,
                                  Character.UnicodeScript targetScript,
                                  double minTargetScriptFrac) {
        if (text.indexOf('\uFFFD') >= 0) {
            return null;
        }
        text = Normalizer.normalize(text, Normalizer.Form.NFC);
        if (text.getBytes(StandardCharsets.UTF_8).length < minBytes) {
            return null;
        }
        int cpCount = 0;
        int puncCount = 0;
        int scriptCpTotal = 0;
        int scriptCpMatching = 0;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            cpCount++;
            if (cp >= 0x21 && cp <= 0x7E && !Character.isLetter(cp)) {
                puncCount++;
            }
            if (targetScript != null) {
                Character.UnicodeScript s = Character.UnicodeScript.of(cp);
                if (s != Character.UnicodeScript.COMMON
                        && s != Character.UnicodeScript.INHERITED
                        && s != Character.UnicodeScript.UNKNOWN) {
                    scriptCpTotal++;
                    if (s == targetScript) {
                        scriptCpMatching++;
                    }
                }
            }
            i += Character.charCount(cp);
        }
        if (cpCount > 0 && (double) puncCount / cpCount > maxPuncFrac) {
            return null;
        }
        if (targetScript != null && scriptCpTotal > 0
                && (double) scriptCpMatching / scriptCpTotal < minTargetScriptFrac) {
            return null;
        }
        return text;
    }

    // -----------------------------------------------------------------------
    // I/O helpers
    // -----------------------------------------------------------------------

    private static String extractText(String line) {
        int tab = line.indexOf('\t');
        String text = (tab >= 0) ? line.substring(tab + 1) : line;
        return text.replace("\uFEFF", "");
    }

    private static void writeGzipped(Path path, List<String> lines) throws IOException {
        try (BufferedWriter w = new BufferedWriter(
                new OutputStreamWriter(
                        new GZIPOutputStream(Files.newOutputStream(path)),
                        StandardCharsets.UTF_8))) {
            for (String line : lines) {
                w.write(line);
                w.newLine();
            }
        }
    }

    private static void printUsage() {
        System.err.println("Usage: BuildJunkTrainingData [options]");
        System.err.println("  --data-dir               <path>  MADLAD data root"
                + " (default: ~/datasets/madlad/data)");
        System.err.println("  --output-dir             <path>  Output directory"
                + " (default: ~/datasets/madlad/junkdetect)");
        System.err.println("  --script-sample-lines    N       Lines per language for script"
                + " detection (default: 2000)");
        System.err.println("  --total-budget-bytes     N       Total UTF-8 bytes across all"
                + " scripts (default: 50000000)");
        System.err.println("  --per-language-cap-bytes N       Max UTF-8 bytes contributed by any"
                + " single language to its script bucket (default: 5000000).  Prevents one large"
                + " language source from dominating a multi-language bucket.");
        System.err.println("  --min-bytes              N       Min UTF-8 bytes per sentence"
                + " (default: 50)");
        System.err.println("  --max-punc-frac          F       Max ASCII punct fraction"
                + " (default: 0.30)");
        System.err.println("  --min-target-script-frac F       Min fraction of non-COMMON cps that"
                + " must be in the bucket's target script for a sentence to be kept"
                + " (default: 0.05).  Filters off-target Wikipedia stubs (e.g. English-about-Gothic"
                + " articles in the GOTHIC bucket).");
        System.err.println("  --min-dev-sentences      N       Min sentences in dev split for a"
                + " script to be included (default: 500). Scripts below this floor"
                + " have unreliable calibration and inflated FPR.");
        System.err.println("  --drop-scripts           S,S,..  Comma-separated script bucket names"
                + " to exclude (e.g. GOTHIC,THAANA).  Use when source data is too thin or off-"
                + " target for reliable distribution estimates.");
        System.err.println("  --seed                   N       Random seed (default: 42)");
        System.err.println("  --dry-run                        Detect scripts + show budget,"
                + " skip file writing");
    }
}
