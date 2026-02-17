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
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.tika.langdetect.charsoup.WordTokenizer;

/**
 * Generates common-token frequency files from Leipzig corpora, using
 * {@link WordTokenizer#tokenize(String)} for consistent tokenization with the
 * bigram language detector and tika-eval.
 * <p>
 * For CJK languages, the tokenizer automatically produces character bigrams
 * (equivalent to Lucene's CJKBigramFilter).
 * <p>
 * Languages with an {@code -x-ltr} suffix are skipped — those exist only for
 * bidi detection and are not meaningful for common-token analysis.
 * <p>
 * Output format matches the existing {@code common_tokens/} files in tika-eval-core:
 * <pre>
 * # (license header)
 * #DOC_COUNT&#9;N
 * #SUM_DOC_FREQS&#9;N
 * #SUM_TERM_FREQS&#9;N
 * #UNIQUE_TERMS&#9;N
 * #TOKEN&#9;DOCFREQ&#9;TERMFREQ
 * ___email___
 * ___url___
 * token1&#9;df1&#9;tf1
 * token2&#9;df2&#9;tf2
 * ...
 * </pre>
 * Usage: {@code CommonTokenGenerator <corpusDir> <outputDir> [topN] [minDocFreq]}
 */
public class CommonTokenGenerator {

    private static final int DEFAULT_TOP_N = 30_000;
    private static final int DEFAULT_MIN_DOC_FREQ = 10;

    /**
     * Minimum token length for alphabetic (non-CJK) tokens.
     * Matches the old Lucene-based CommonTokensAnalyzer which intentionally
     * dropped tokens shorter than 4 characters. CJK bigrams (2 chars) are
     * exempt since they are the natural unit for ideographic scripts.
     */
    private static final int MIN_ALPHA_TOKEN_LENGTH = 4;

    /** Common HTML markup terms to exclude (carried over from the old Lucene-based tool). */
    private static final Set<String> SKIP_LIST = new HashSet<>(Arrays.asList(
            "span", "table", "href", "head", "title", "body", "html",
            "tagname", "lang", "style", "script", "strong", "blockquote",
            "form", "iframe", "section", "colspan", "rowspan"));

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
                    "Usage: CommonTokenGenerator <corpusDir> <outputDir> [topN] [minDocFreq]");
            System.err.println("  corpusDir  — directory with lang/sentences.txt files");
            System.err.println("  outputDir  — where to write per-language token files");
            System.err.println("  topN       — max tokens per language (default "
                    + DEFAULT_TOP_N + ")");
            System.err.println("  minDocFreq — minimum document frequency (default "
                    + DEFAULT_MIN_DOC_FREQ + ")");
            System.exit(1);
        }

        Path corpusDir = Paths.get(args[0]);
        Path outputDir = Paths.get(args[1]);
        int topN = args.length > 2 ? Integer.parseInt(args[2]) : DEFAULT_TOP_N;
        int minDocFreq = args.length > 3 ? Integer.parseInt(args[3]) : DEFAULT_MIN_DOC_FREQ;

        Files.createDirectories(outputDir);

        List<Path> langDirs = new ArrayList<>();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(corpusDir)) {
            for (Path p : ds) {
                if (Files.isDirectory(p)) {
                    langDirs.add(p);
                }
            }
        }
        langDirs.sort(null);

        int processed = 0;
        int skipped = 0;
        for (Path langDir : langDirs) {
            String lang = langDir.getFileName().toString();

            // Skip -x-ltr variants — they exist only for bidi detection
            if (lang.contains("-x-ltr")) {
                System.out.printf(Locale.US, "Skipping %s (LTR variant)%n", lang);
                skipped++;
                continue;
            }

            Path sentencesFile = langDir.resolve("sentences.txt");
            if (!Files.isRegularFile(sentencesFile)) {
                System.out.printf(Locale.US, "Skipping %s (no sentences.txt)%n", lang);
                skipped++;
                continue;
            }

            Path outFile = outputDir.resolve(lang);
            if (Files.isRegularFile(outFile)) {
                System.out.printf(Locale.US, "Skipping %s (output already exists)%n", lang);
                skipped++;
                continue;
            }

            System.out.printf(Locale.US, "Processing %s ...%n", lang);
            long start = System.nanoTime();
            processLanguage(sentencesFile, outFile, topN, minDocFreq);
            double elapsed = (System.nanoTime() - start) / 1_000_000_000.0;
            System.out.printf(Locale.US, "  %s done [%.1f s]%n", lang, elapsed);
            processed++;
        }

        System.out.printf(Locale.US, "%nDone: %d languages processed, %d skipped.%n",
                processed, skipped);
    }

    /**
     * Process a single language's sentences.txt file and write the common tokens file.
     */
    static void processLanguage(Path sentencesFile, Path outFile,
                                int topN, int minDocFreq) throws IOException {
        // token -> [docFreq, termFreq]
        Map<String, long[]> tokenStats = new HashMap<>();
        long docCount = 0;
        long sumTermFreqs = 0;

        try (BufferedReader reader = Files.newBufferedReader(sentencesFile, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                // Leipzig format: lineNum<TAB>sentence
                int tab = line.indexOf('\t');
                String sentence = (tab >= 0) ? line.substring(tab + 1) : line;
                if (sentence.isEmpty()) {
                    continue;
                }

                docCount++;
                Set<String> seenInDoc = new HashSet<>();
                List<String> tokens = WordTokenizer.tokenize(sentence);

                for (String token : tokens) {
                    if (SKIP_LIST.contains(token)) {
                        continue;
                    }
                    // Enforce min length 4 for alphabetic tokens, but allow
                    // CJK bigrams (2 chars) through — they're ideographic pairs
                    if (token.length() < MIN_ALPHA_TOKEN_LENGTH
                            && !isIdeographicToken(token)) {
                        continue;
                    }
                    sumTermFreqs++;
                    long[] stats = tokenStats.computeIfAbsent(token, k -> new long[2]);
                    stats[1]++; // termFreq

                    if (seenInDoc.add(token)) {
                        stats[0]++; // docFreq (count each token once per document)
                    }
                }
            }
        }

        long uniqueTerms = tokenStats.size();
        long sumDocFreqs = 0;
        for (long[] stats : tokenStats.values()) {
            sumDocFreqs += stats[0];
        }

        // Sort by docFreq descending, then token ascending for tie-breaking
        List<Map.Entry<String, long[]>> entries = new ArrayList<>(tokenStats.entrySet());
        entries.sort((a, b) -> {
            int cmp = Long.compare(b.getValue()[0], a.getValue()[0]);
            if (cmp != 0) {
                return cmp;
            }
            return a.getKey().compareTo(b.getKey());
        });

        // Write output
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
    }

    /**
     * Returns true if the token consists entirely of ideographic characters
     * (CJK bigrams). These are allowed through even when shorter than
     * {@link #MIN_ALPHA_TOKEN_LENGTH}.
     */
    private static boolean isIdeographicToken(String token) {
        int i = 0;
        while (i < token.length()) {
            int cp = token.codePointAt(i);
            if (!Character.isIdeographic(cp)) {
                return false;
            }
            i += Character.charCount(cp);
        }
        return !token.isEmpty();
    }
}
