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
package org.apache.tika.eval.core.tokens.tools;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.apache.tika.eval.core.tokens.TikaEvalTokenizer;

/**
 * Generates common-token frequency files from Leipzig corpora, using
 * {@link TikaEvalTokenizer} in {@link TikaEvalTokenizer.Mode#COMMON_TOKENS}
 * mode for consistent tokenization with tika-eval's common-token analysis.
 * <p>
 * Duplicate ISO 639-3 codes are merged to canonical codes (matching the
 * language detector's training pipeline) and sentences are deduplicated
 * within each language.
 * <p>
 * All tokenization rules (min-length filtering, HTML term exclusion,
 * CJK bigrams, NFKD normalization, case folding) are centralized in
 * {@link TikaEvalTokenizer}.
 * <p>
 * Output format matches the existing {@code common_tokens/} files in tika-eval-core:
 * <pre>
 * # (license header)
 * #DOC_COUNT&#9;N
 * #SUM_DOC_FREQS&#9;N
 * #SUM_TERM_FREQS&#9;N
 * #UNIQUE_TERMS&#9;N
 * #TOKEN&#9;DOCFREQ&#9;TERMFREQ
 * token1&#9;df1&#9;tf1
 * token2&#9;df2&#9;tf2
 * ...
 * </pre>
 * Usage: {@code CommonTokenGenerator <corpusDir> <outputDir> [topN] [minDocFreq]}
 */
public class CommonTokenGenerator {

    private static final int DEFAULT_TOP_N = 30_000;
    private static final int DEFAULT_MIN_DOC_FREQ = 10;

    private static final long FNV_OFFSET = 0xcbf29ce484222325L;
    private static final long FNV_PRIME = 0x100000001b3L;

    /**
     * Languages excluded from common-token generation. Must stay in sync with
     * {@code PrepareCorpus.EXCLUDED_LANGS}. These languages are excluded
     * because they are indistinguishable from closely related majority languages
     * at the character n-gram level, causing accuracy interference or
     * having unacceptably low own-detection accuracy. See the build
     * documentation for per-language justifications.
     */
    static final Set<String> EXCLUDED_LANGS;
    static {
        Set<String> ex = new HashSet<>();
        // MADLAD-era drops
        ex.add("vec"); // Venetian: Italian (ita) dropped to 83.6%
        ex.add("als"); // Tosk Albanian: Albanian (sqi) collapsed to 51.6%
        ex.add("mad"); // Madurese: 9.1% own accuracy; indistinguishable from Javanese/Indonesian
        ex.add("anw"); // Anaang: 32.5% own accuracy
        ex.add("knn"); // Konkani: 46.2% own accuracy; indistinguishable from Marathi
        ex.add("glk"); // Gilaki: 88.6% own accuracy; overlaps with Persian/Mazanderani
        ex.add("mkw"); // Kituba: 80.1% own accuracy; overlaps with Kongo/Lingala
        // Wikipedia-era drops (March 2026) — see language-drop-decisions.md
        ex.add("hbs"); // Serbo-Croatian: F1@500=0.849, same language as hrv/srp/bos
        ex.add("mai"); // Maithili: F1@50=0.490, Devanagari contamination with hin/nep
        ex.add("koi"); // Komi-Permyak: F1@500=0.765, permanently confused with kpv
        ex.add("hat"); // Haitian Creole: F1@20=0.155, destroys English recall at short text
        ex.add("sco"); // Scots: Scots Wikipedia is mostly English; mutual eng confusion
        ex.add("que"); // Quechua: F1@500=0.833, mutually confused with aym
        ex.add("aym"); // Aymara: F1@500=0.837, mutually confused with que
        ex.add("pcd"); // Picard: F1@500=0.805, never separable from French
        ex.add("gor"); // Gorontalo: F1@500=0.801, F1@50=0.503, persistently poor
        // v5 drops: bot-generated Wikipedia corpora (see language-drop-decisions.md)
        ex.add("arz"); // Egyptian Arabic: F1@500=0.999 Wikipedia, 5.3% FLORES — Lsjbot stubs
        ex.add("bug"); // Buginese: ~95% French municipality stubs
        ex.add("bpy"); // Bishnupriya Manipuri: Brazilian/US/South Asian location stubs
        ex.add("mlg"); // Malagasy: French commune stubs (~179k sentences of template)
        ex.add("nan-x-rom"); // Min Nan romanized: geographic stubs across 455k sentences
        ex.add("new"); // Newari: Indian village stubs
        ex.add("lld"); // Ladin: all samples are stubs
        ex.add("che"); // Chechen: 19/20 samples are Russian/Turkish/other village stubs
        ex.add("nav"); // Navajo: species distribution templates ~95% of 47k sentences
        // v5 drops: gate-simplification (length-gating mechanism removed entirely)
        ex.add("nds-nl"); // Low Saxon Dutch: dual confusable complexity (nds + nld)
        ex.add("map-bms"); // Banyumasan: 5.7% bleed into ind at full length
        // note: hat already excluded above; bjn retained without gate
        EXCLUDED_LANGS = Collections.unmodifiableSet(ex);
    }

    /**
     * Merge map for duplicate ISO 639-3 codes. Must match the mappings used
     * in {@code TrainLanguageModel} and {@code download_corpus.py}.
     */
    private static final Map<String, String> LANG_MERGE_MAP;
    static {
        Map<String, String> m = new HashMap<>();
        m.put("azj", "aze");
        m.put("ekk", "est");
        m.put("pes", "fas");
        m.put("zsm", "msa");
        m.put("nor", "nob");
        m.put("plt", "mlg");
        m.put("cmn", "zho");
        m.put("lvs", "lav");
        m.put("gug", "grn");
        m.put("quz", "que");
        m.put("swa", "swh");
        m.put("yid", "ydd");
        LANG_MERGE_MAP = Collections.unmodifiableMap(m);
    }

    private static final String LICENSE =
            "# Licensed to the Apache Software Foundation (ASF) under one or more\n"
                    + "# contributor license agreements.  See the NOTICE file distributed with\n"
                    + "# this work for additional information regarding copyright ownership.\n"
                    + "# The ASF licenses this file to You under the Apache License, Version 2.0\n"
                    + "# (the \"License\"); you may not use this file except in compliance with\n"
                    + "# the License.  You may obtain a copy of the License at\n"
                    + "#\n"
                    + "#     http://www.apache.org/licenses/LICENSE-2.0\n"
                    + "#\n"
                    + "# Unless required by applicable law or agreed to in writing, software\n"
                    + "# distributed under the License is distributed on an \"AS IS\" BASIS,\n"
                    + "# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.\n"
                    + "# See the License for the specific language governing permissions and\n"
                    + "# limitations under the License.\n"
                    + "#\n";

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println(
                    "Usage: CommonTokenGenerator <corpusDir> <outputDir> [topN] [minDocFreq]"
                    + " [--model <modelFile>]");
            System.err.println("  corpusDir   — directory with lang/sentences.txt files");
            System.err.println("  outputDir   — where to write per-language token files");
            System.err.println("  topN        — max tokens per language (default "
                    + DEFAULT_TOP_N + ")");
            System.err.println("  minDocFreq  — minimum document frequency (default "
                    + DEFAULT_MIN_DOC_FREQ + ")");
            System.err.println("  --model <f> — only generate tokens for languages in this"
                    + " CharSoup model (LDM1 binary)");
            System.exit(1);
        }

        Path corpusDir = Paths.get(args[0]);
        Path outputDir = Paths.get(args[1]);
        int topN = args.length > 2 ? Integer.parseInt(args[2]) : DEFAULT_TOP_N;
        int minDocFreq = args.length > 3 ? Integer.parseInt(args[3]) : DEFAULT_MIN_DOC_FREQ;

        Set<String> modelLangs = null;
        for (int i = 4; i < args.length - 1; i++) {
            if ("--model".equals(args[i])) {
                modelLangs = loadModelLabels(Paths.get(args[++i]));
                System.out.printf(Locale.US, "Model filter: %d languages%n", modelLangs.size());
            }
        }
        final Set<String> allowedLangs = modelLangs;

        Files.createDirectories(outputDir);

        // Scan corpus directories and group by canonical language code
        Map<String, List<Path>> langToFiles = new TreeMap<>();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(corpusDir)) {
            for (Path p : ds) {
                Path sentencesFile;
                String entryName = p.getFileName().toString();
                if (Files.isDirectory(p)) {
                    // Leipzig / MADLAD subdirectory layout: lang/sentences[_madlad].txt
                    sentencesFile = p.resolve("sentences_madlad.txt");
                    if (!Files.isRegularFile(sentencesFile)) {
                        sentencesFile = p.resolve("sentences.txt");
                    }
                    if (!Files.isRegularFile(sentencesFile)) {
                        continue;
                    }
                } else if (Files.isRegularFile(p)) {
                    // Flat pool layout: one file per language, filename = lang code
                    sentencesFile = p;
                } else {
                    continue;
                }
                String canonical = LANG_MERGE_MAP.getOrDefault(entryName, entryName);
                if (!canonical.equals(entryName)) {
                    System.out.printf(Locale.US, "Merging %s -> %s%n", entryName, canonical);
                }
                if (EXCLUDED_LANGS.contains(canonical)) {
                    System.out.printf(Locale.US, "Skipping excluded language: %s%n", canonical);
                    continue;
                }
                if (allowedLangs != null && !allowedLangs.contains(canonical)) {
                    System.out.printf(Locale.US, "Skipping (not in model): %s%n", canonical);
                    continue;
                }
                langToFiles.computeIfAbsent(canonical, k -> new ArrayList<>())
                        .add(sentencesFile);
            }
        }

        System.out.printf(Locale.US, "Found %d corpus directories -> %d canonical languages%n%n",
                langToFiles.values().stream().mapToInt(List::size).sum(),
                langToFiles.size());

        int processed = 0;
        int skipped = 0;
        for (Map.Entry<String, List<Path>> entry : langToFiles.entrySet()) {
            String lang = entry.getKey();
            List<Path> sentenceFiles = entry.getValue();

            Path outFile = outputDir.resolve(lang);
            if (Files.isRegularFile(outFile)) {
                System.out.printf(Locale.US, "Skipping %s (output already exists)%n", lang);
                skipped++;
                continue;
            }

            System.out.printf(Locale.US, "Processing %s (%d file(s)) ...%n",
                    lang, sentenceFiles.size());
            long start = System.nanoTime();
            processLanguage(sentenceFiles, outFile, topN, minDocFreq);
            double elapsed = (System.nanoTime() - start) / 1_000_000_000.0;
            System.out.printf(Locale.US, "  %s done [%.1f s]%n", lang, elapsed);
            processed++;
        }

        System.out.printf(Locale.US, "%nDone: %d languages processed, %d skipped.%n",
                processed, skipped);
    }

    /**
     * Process one or more sentences.txt files for a single language,
     * deduplicating sentences and writing the common tokens file.
     */
    static void processLanguage(List<Path> sentenceFiles, Path outFile,
                                int topN, int minDocFreq) throws IOException {
        Map<String, long[]> tokenStats = new HashMap<>();
        Set<Long> seenSentences = new HashSet<>();
        long docCount = 0;
        long dupes = 0;
        long sumTermFreqs = 0;

        for (Path sentencesFile : sentenceFiles) {
            try (BufferedReader reader =
                         Files.newBufferedReader(sentencesFile, StandardCharsets.UTF_8)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    int tab = line.indexOf('\t');
                    String sentence = (tab >= 0) ? line.substring(tab + 1) : line;
                    if (sentence.isEmpty()) {
                        continue;
                    }

                    long hash = fnv1a64(sentence);
                    if (!seenSentences.add(hash)) {
                        dupes++;
                        continue;
                    }

                    docCount++;
                    Set<String> seenInDoc = new HashSet<>();
                    List<String> tokens = TikaEvalTokenizer.tokenize(sentence);

                    for (String token : tokens) {
                        sumTermFreqs++;
                        long[] stats = tokenStats.computeIfAbsent(token, k -> new long[2]);
                        stats[1]++;

                        if (seenInDoc.add(token)) {
                            stats[0]++;
                        }
                    }
                }
            }
        }

        if (dupes > 0) {
            System.out.printf(Locale.US, "  Deduplicated: removed %,d duplicate sentences%n",
                    dupes);
        }

        long uniqueTerms = tokenStats.size();
        long sumDocFreqs = 0;
        for (long[] stats : tokenStats.values()) {
            sumDocFreqs += stats[0];
        }

        List<Map.Entry<String, long[]>> entries = new ArrayList<>(tokenStats.entrySet());
        entries.sort((a, b) -> {
            int cmp = Long.compare(b.getValue()[0], a.getValue()[0]);
            if (cmp != 0) {
                return cmp;
            }
            return a.getKey().compareTo(b.getKey());
        });

        Files.createDirectories(outFile.getParent());
        try (BufferedWriter writer = Files.newBufferedWriter(outFile, StandardCharsets.UTF_8)) {
            writer.write(LICENSE);
            writer.write(String.format(Locale.US, "#DOC_COUNT\t%d%n", docCount));
            writer.write(String.format(Locale.US, "#SUM_DOC_FREQS\t%d%n", sumDocFreqs));
            writer.write(String.format(Locale.US, "#SUM_TERM_FREQS\t%d%n", sumTermFreqs));
            writer.write(String.format(Locale.US, "#UNIQUE_TERMS\t%d%n", uniqueTerms));
            writer.write("#TOKEN\tDOCFREQ\tTERMFREQ\n");

            int written = 0;
            for (Map.Entry<String, long[]> entry : entries) {
                if (written >= topN) {
                    break;
                }
                long df = entry.getValue()[0];
                if (df < minDocFreq) {
                    continue;
                }
                long tf = entry.getValue()[1];
                writer.write(String.format(Locale.US, "%s\t%d\t%d%n",
                        entry.getKey(), df, tf));
                written++;
            }
        }

        System.out.printf(Locale.US, "  %,d docs, %,d unique terms, wrote top %d (minDF=%d)%n",
                docCount, uniqueTerms, topN, minDocFreq);
    }

    private static Set<String> loadModelLabels(Path modelFile) throws IOException {
        try (java.io.InputStream is = Files.newInputStream(modelFile)) {
            org.apache.tika.langdetect.charsoup.CharSoupModel model =
                    org.apache.tika.langdetect.charsoup.CharSoupModel.load(is);
            Set<String> labels = new HashSet<>(model.getNumClasses());
            Collections.addAll(labels, model.getLabels());
            return Collections.unmodifiableSet(labels);
        }
    }

    static long fnv1a64(String s) {
        long hash = FNV_OFFSET;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < 0x80) {
                hash ^= c;
                hash *= FNV_PRIME;
            } else if (c < 0x800) {
                hash ^= (0xC0 | (c >> 6));
                hash *= FNV_PRIME;
                hash ^= (0x80 | (c & 0x3F));
                hash *= FNV_PRIME;
            } else if (Character.isHighSurrogate(c) && i + 1 < s.length()) {
                int cp = Character.toCodePoint(c, s.charAt(++i));
                hash ^= (0xF0 | (cp >> 18));
                hash *= FNV_PRIME;
                hash ^= (0x80 | ((cp >> 12) & 0x3F));
                hash *= FNV_PRIME;
                hash ^= (0x80 | ((cp >> 6) & 0x3F));
                hash *= FNV_PRIME;
                hash ^= (0x80 | (cp & 0x3F));
                hash *= FNV_PRIME;
            } else {
                hash ^= (0xE0 | (c >> 12));
                hash *= FNV_PRIME;
                hash ^= (0x80 | ((c >> 6) & 0x3F));
                hash *= FNV_PRIME;
                hash ^= (0x80 | (c & 0x3F));
                hash *= FNV_PRIME;
            }
        }
        return hash;
    }
}
