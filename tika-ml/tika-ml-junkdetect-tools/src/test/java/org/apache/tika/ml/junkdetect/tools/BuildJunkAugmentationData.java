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
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.serialization.JsonMetadataList;

/**
 * Augments per-script {@code <script>.train.gz} files with quality-filtered text
 * extracted from tika-app RMETA JSON output.
 *
 * <p>Built to address a corpus-imbalance pathology in the bundled junk-detector
 * model: the primary training corpus (MADLAD + Wikipedia) is clean linguistic
 * text and carries almost no HTML symbols (©, ®, ™, €, £), so on web pages those
 * bytes look anomalously surprising relative to the rest of the bigram
 * distribution and tip charset-decoding decisions toward whichever encoding
 * happens to place a frequent training letter at the same byte position.
 *
 * <p>This tool is strictly additive: originals in {@code --baseline} are never
 * modified; output goes to a fresh directory. The {@code .dev.gz} / {@code
 * .test.gz} splits are copied verbatim (no augmentation), preserving evaluation
 * integrity.
 *
 * <h2>Pipeline</h2>
 * <ol>
 *   <li>For each {@code .json} extract under {@code --extracts} (tika-app output
 *       via {@link JsonMetadataList}), read {@code tk:content}.</li>
 *   <li>Per document: determine the dominant Unicode script (non-COMMON / non-
 *       INHERITED tally) and drop documents whose dominant-script fraction is
 *       below {@link #MIN_DOC_SCRIPT_DOMINANCE} — mixed-script pages are too
 *       ambiguous to attribute cleanly to one bucket.</li>
 *   <li>Split the content into ~{@value #TARGET_CHUNK_CHARS}-char chunks on
 *       newlines (then on whitespace if a line is longer than the cap).</li>
 *   <li>Run each chunk through {@link BuildJunkTrainingData#filterSentence(
 *       String, int, double, Character.UnicodeScript, double)} using the same
 *       MIN_BYTES / MAX_PUNC_FRAC / MIN_TARGET_SCRIPT_FRAC values from
 *       {@link JunkDetectorTrainingConfig}.</li>
 *   <li>Per-script gate: a script must reach at least {@code --min-docs} quality-
 *       filtered documents or its bucket is skipped (prevents asymmetric
 *       augmentation favouring data-rich scripts).</li>
 *   <li>Per-script cap: append at most {@code min(--hard-cap,
 *       --baseline-frac-cap × baselineLineCount)} lines so the original
 *       distribution is preserved.</li>
 *   <li>Output: baseline {@code .train.gz} is decompressed, augmentation lines
 *       are appended, and the file is recompressed at the output path. Other
 *       split files are byte-copied.</li>
 * </ol>
 *
 * <h2>Safety</h2>
 * Refuses to start when {@code --output} resolves to the same directory as
 * {@code --baseline}. Documents that do not pass the per-doc script-dominance
 * gate are dropped, not reassigned to a different bucket.
 */
public final class BuildJunkAugmentationData {

    /** Hard ceiling on appended lines per script when not constrained by baseline-frac cap. */
    public static final int DEFAULT_HARD_CAP_LINES = 3_000;

    /** Cap appended lines at this fraction of the baseline train-line count. */
    public static final double DEFAULT_BASELINE_FRAC_CAP = 0.10;

    /**
     * Per-script gate: a script must have at least this many quality-filtered
     * source documents before any augmentation is appended. Avoids creating an
     * asymmetric bias from a single noisy page.
     */
    public static final int DEFAULT_MIN_DOCS = 500;

    /**
     * Reject a document whose dominant Unicode script accounts for less than
     * this fraction of its non-COMMON / non-INHERITED codepoints. Mixed-script
     * pages are dropped rather than attributed.
     */
    public static final double MIN_DOC_SCRIPT_DOMINANCE = 0.80;

    /** Target character length when chunking long lines from extract content. */
    public static final int TARGET_CHUNK_CHARS = 300;

    /** Hard upper bound on chunk size; longer lines are sliced. */
    public static final int MAX_CHUNK_CHARS = 600;

    /** Minimum content length (chars) before a document is considered at all. */
    public static final int MIN_DOC_CHARS = 500;

    /**
     * Structural test for UTF-8 source decoded as windows-1252. UTF-8 multi-
     * byte lead bytes (0xC2–0xDF) followed by continuation bytes (0x80–0xBF)
     * decode as Latin-Supplement letters ("Â"–"ß") immediately followed by C1
     * controls / typographic codepoints ("€"–"¿"). Legitimate German/French/
     * Italian text essentially never produces this exact bigram shape because
     * those codepoints aren't normally adjacent to Latin letters.
     *
     * <p>This is NOT a content-quality heuristic (that's JunkDetector's job —
     * and it can't catch this, because mojibake'd Latin is still bigram-wise
     * "Latin-like"). It's a check for a specific, well-known encoding
     * pathology: UTF-8 misread as windows-1252. A chunk with this many of
     * those bigrams is essentially guaranteed mojibake and gets dropped to
     * keep contaminated samples out of the LATIN bigram table.
     */
    public static final int MAX_UTF8_AS_WIN1252_BIGRAMS = 1;

    /**
     * Maximum OOV (tika-eval out-of-vocabulary rate) for a doc to be accepted.
     * Applied only when a profile CSV is provided. Negative OOV (e.g. tika-eval
     * has no word list for the detected language, like Tibetan) bypasses this
     * gate so we don't unfairly drop content from unsupported languages.
     */
    public static final double DEFAULT_MAX_OOV = 0.5;

    /**
     * Minimum LANGUAGENESS for a doc to be accepted. Applied only when a profile
     * CSV is provided. LANGUAGENESS sums to {@code (langProb1 - OOV)} normalized
     * to a per-doc score; ≥0 means "more in-vocabulary than out-of-vocabulary".
     */
    public static final double DEFAULT_MIN_LANGNESS = 0.0;

    /**
     * win-1252 symbols that collide with ISO-8859-2 letters (bytes 0xA0-0xBF)
     * or are otherwise web-common but starved in clean linguistic corpora.
     * These are the bytes whose ISO-8859-2 re-decode produces a confusable
     * Central-European letter (© → Š, ® → Ž, £ → Ł, ¥ → Ľ, ¦ → Ś, µ → ľ,
     * ¶ → ś, ¼ → ź, ¾ → ž), plus ™/€ which are web-ubiquitous. Symbol-aware
     * selection biases the LATIN augmentation toward chunks containing these
     * so they reach confident bigram-table density (z1), widening the thin
     * win-1252-vs-ISO-8859-2 margin.
     */
    public static final String SYMBOL_TARGETS = "©®™€£¥¦µ¶¼½¾";

    /**
     * Fraction of the per-script cap reserved for symbol-bearing chunks when
     * {@code --symbol-boost} is set. 0 disables (random selection, original
     * behaviour). Only applied to the LATIN bucket — the win-1252/ISO-8859-2
     * symbol→letter collision is Latin-specific.
     */
    public static final double DEFAULT_SYMBOL_BOOST_FRAC = 0.0;

    private BuildJunkAugmentationData() {
    }

    public static void main(String[] args) throws IOException {
        Path extractsDir = null;
        Path baselineDir = null;
        Path outputDir = null;
        Path profileCsv = null;
        boolean dryRun = false;
        int hardCap = DEFAULT_HARD_CAP_LINES;
        double fracCap = DEFAULT_BASELINE_FRAC_CAP;
        int minDocs = DEFAULT_MIN_DOCS;
        double maxOov = DEFAULT_MAX_OOV;
        double minLangness = DEFAULT_MIN_LANGNESS;
        double symbolBoost = DEFAULT_SYMBOL_BOOST_FRAC;
        long seed = JunkDetectorTrainingConfig.SEED;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--extracts":
                    extractsDir = Paths.get(args[++i]);
                    break;
                case "--baseline":
                    baselineDir = Paths.get(args[++i]);
                    break;
                case "--output":
                    outputDir = Paths.get(args[++i]);
                    break;
                case "--profile-csv":
                    profileCsv = Paths.get(args[++i]);
                    break;
                case "--max-oov":
                    maxOov = Double.parseDouble(args[++i]);
                    break;
                case "--min-langness":
                    minLangness = Double.parseDouble(args[++i]);
                    break;
                case "--symbol-boost":
                    symbolBoost = Double.parseDouble(args[++i]);
                    break;
                case "--dry-run":
                    dryRun = true;
                    break;
                case "--hard-cap":
                    hardCap = Integer.parseInt(args[++i]);
                    break;
                case "--baseline-frac-cap":
                    fracCap = Double.parseDouble(args[++i]);
                    break;
                case "--min-docs":
                    minDocs = Integer.parseInt(args[++i]);
                    break;
                case "--seed":
                    seed = Long.parseLong(args[++i]);
                    break;
                default:
                    System.err.println("Unknown argument: " + args[i]);
                    printUsage();
                    System.exit(1);
            }
        }
        if (extractsDir == null || baselineDir == null || outputDir == null) {
            printUsage();
            System.exit(1);
        }

        if (!Files.isDirectory(extractsDir)) {
            System.err.println("ERROR: --extracts directory not found: " + extractsDir);
            System.exit(1);
        }
        if (!Files.isDirectory(baselineDir)) {
            System.err.println("ERROR: --baseline directory not found: " + baselineDir);
            System.exit(1);
        }
        if (Files.exists(outputDir) && Files.isSameFile(outputDir, baselineDir)) {
            System.err.println("ERROR: --output must differ from --baseline.");
            System.exit(1);
        }

        System.out.println("=== BuildJunkAugmentationData ===");
        System.out.println("  extracts:           " + extractsDir);
        System.out.println("  baseline:           " + baselineDir);
        System.out.println("  output:             " + outputDir);
        System.out.println("  profile-csv:        " + (profileCsv == null ? "(none)" : profileCsv));
        System.out.println("  max-oov:            " + maxOov);
        System.out.println("  min-langness:       " + minLangness);
        System.out.println("  symbol-boost:       " + symbolBoost
                + (symbolBoost > 0 ? " (LATIN only, targets: " + SYMBOL_TARGETS + ")" : " (off)"));
        System.out.println("  hard-cap:           " + hardCap);
        System.out.println("  baseline-frac-cap:  " + fracCap);
        System.out.println("  min-docs:           " + minDocs);
        System.out.println("  seed:               " + seed);
        System.out.println("  dry-run:            " + dryRun);

        // Load tika-eval profile CSV if provided.
        Map<String, ProfileRow> profiles = profileCsv == null
                ? null
                : loadProfileCsv(profileCsv);
        if (profiles != null) {
            System.out.printf("  loaded %,d profile rows%n", profiles.size());
        }

        // --- Phase 1: discover baseline scripts + line counts -------------------
        System.out.println("\n--- Phase 1: scanning baseline train files ---");
        Map<String, Long> baselineLineCounts = scanBaselineLineCounts(baselineDir);
        for (Map.Entry<String, Long> e : baselineLineCounts.entrySet()) {
            System.out.printf("  %-20s baseline=%,d lines%n", e.getKey(), e.getValue());
        }

        // --- Phase 2: walk extracts ---------------------------------------------
        System.out.println("\n--- Phase 2: scanning extracts ---");
        // Per-script: list of accepted chunks (one chunk per line). Doc-level
        // gating is by counting how many docs contributed ≥ 1 accepted chunk.
        Map<String, List<String>> scriptChunks = new TreeMap<>();
        Map<String, Integer> scriptDocCount = new TreeMap<>();
        // Diagnostics: how many docs we saw total / per stage.
        long totalSeen = 0;
        long droppedNoContent = 0;
        long droppedShort = 0;
        long droppedMixedScript = 0;
        long droppedNoBaseline = 0;
        long droppedOov = 0;
        long droppedLangness = 0;
        long droppedNoProfile = 0;
        long accepted = 0;

        try (Stream<Path> walk = Files.walk(extractsDir)) {
            for (Path file : (Iterable<Path>) walk
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".json"))::iterator) {
                totalSeen++;
                String content = readContent(file);
                if (content == null || content.isEmpty()) {
                    droppedNoContent++;
                    continue;
                }
                if (content.length() < MIN_DOC_CHARS) {
                    droppedShort++;
                    continue;
                }
                // Profile gate (OOV / LANGUAGENESS), if a profile CSV was loaded.
                if (profiles != null) {
                    String key = profileKey(extractsDir, file);
                    ProfileRow pr = profiles.get(key);
                    if (pr == null) {
                        droppedNoProfile++;
                        continue;
                    }
                    // Negative OOV means tika-eval has no word list for the
                    // detected language; we don't penalise those docs.
                    if (pr.oov >= 0 && pr.oov > maxOov) {
                        droppedOov++;
                        continue;
                    }
                    if (pr.langness < minLangness) {
                        droppedLangness++;
                        continue;
                    }
                }
                DocScript ds = dominantScript(content);
                if (ds.script == null || ds.dominance < MIN_DOC_SCRIPT_DOMINANCE) {
                    droppedMixedScript++;
                    continue;
                }
                String scriptName = ds.script.name();
                if (!baselineLineCounts.containsKey(scriptName.toLowerCase())) {
                    // No baseline bucket for this script — nothing to augment.
                    droppedNoBaseline++;
                    continue;
                }
                List<String> chunks = chunk(content);
                List<String> filtered = new ArrayList<>(chunks.size());
                for (String c : chunks) {
                    String ok = BuildJunkTrainingData.filterSentence(
                            c,
                            JunkDetectorTrainingConfig.MIN_BYTES_PER_SENTENCE,
                            JunkDetectorTrainingConfig.MAX_PUNC_FRAC,
                            ds.script,
                            JunkDetectorTrainingConfig.MIN_TARGET_SCRIPT_FRAC);
                    if (ok == null) {
                        continue;
                    }
                    if (countUtf8AsWin1252Bigrams(ok) > MAX_UTF8_AS_WIN1252_BIGRAMS) {
                        continue;
                    }
                    filtered.add(ok);
                }
                if (filtered.isEmpty()) {
                    continue;
                }
                accepted++;
                scriptChunks.computeIfAbsent(scriptName, k -> new ArrayList<>())
                        .addAll(filtered);
                scriptDocCount.merge(scriptName, 1, Integer::sum);
            }
        }

        System.out.printf("  total extracts seen:    %,d%n", totalSeen);
        System.out.printf("  dropped no-content:     %,d%n", droppedNoContent);
        System.out.printf("  dropped short:          %,d%n", droppedShort);
        if (profiles != null) {
            System.out.printf("  dropped no-profile:     %,d%n", droppedNoProfile);
            System.out.printf("  dropped OOV>%.2f:       %,d%n", maxOov, droppedOov);
            System.out.printf("  dropped langness<%.2f:  %,d%n", minLangness, droppedLangness);
        }
        System.out.printf("  dropped mixed-script:   %,d%n", droppedMixedScript);
        System.out.printf("  dropped no-baseline:    %,d%n", droppedNoBaseline);
        System.out.printf("  contributed ≥1 chunk:   %,d%n", accepted);

        // --- Phase 3: apply per-script gates and caps ---------------------------
        System.out.println("\n--- Phase 3: per-script gating and capping ---");
        Random rng = new Random(seed);
        Map<String, List<String>> finalLines = new LinkedHashMap<>();
        // Per-script report rows for the manifest.
        Map<String, long[]> manifest = new TreeMap<>();
        // columns: docs, chunks_pre_cap, lines_appended, baseline_lines, cap

        for (Map.Entry<String, List<String>> entry : scriptChunks.entrySet()) {
            String script = entry.getKey();
            List<String> chunks = entry.getValue();
            int docs = scriptDocCount.getOrDefault(script, 0);
            long baselineLines = baselineLineCounts.getOrDefault(script.toLowerCase(), 0L);
            long fracCapVal = (long) Math.floor(baselineLines * fracCap);
            long cap = Math.min(hardCap, fracCapVal);

            if (docs < minDocs) {
                System.out.printf(
                        "  SKIP %-20s docs=%-6d (<%d gate)  chunks=%,d  cap=%,d%n",
                        script, docs, minDocs, chunks.size(), cap);
                manifest.put(script, new long[]{docs, chunks.size(), 0, baselineLines, cap});
                continue;
            }

            Collections.shuffle(chunks, rng);
            List<String> kept;
            // Symbol-aware selection — LATIN only (the win-1252/ISO-8859-2
            // symbol→letter collision is Latin-specific). Reserve a quota of
            // the cap for symbol-bearing chunks so ©/®/£/etc. reach confident
            // bigram-table density without inflating the total.
            if (symbolBoost > 0 && "LATIN".equals(script) && chunks.size() > cap) {
                List<String> withSym = new ArrayList<>();
                List<String> noSym = new ArrayList<>();
                for (String c : chunks) {
                    (containsTargetSymbol(c) ? withSym : noSym).add(c);
                }
                int quota = (int) Math.min((long) Math.floor(cap * symbolBoost), withSym.size());
                kept = new ArrayList<>((int) cap);
                kept.addAll(withSym.subList(0, quota));
                int remaining = (int) cap - quota;
                if (remaining > 0) {
                    kept.addAll(noSym.subList(0, Math.min(remaining, noSym.size())));
                }
                // If noSym ran short, top up from leftover symbol-bearing chunks.
                for (int k = quota; kept.size() < cap && k < withSym.size(); k++) {
                    kept.add(withSym.get(k));
                }
                System.out.printf(
                        "  KEEP %-20s docs=%-6d chunks=%,8d -> append=%,6d  "
                        + "(baseline=%,d, cap=%,d, symbol-quota=%d, symbol-bearing-pool=%d)%n",
                        script, docs, chunks.size(), kept.size(), baselineLines, cap,
                        quota, withSym.size());
            } else {
                kept = chunks.size() > cap
                        ? new ArrayList<>(chunks.subList(0, (int) cap))
                        : chunks;
                System.out.printf(
                        "  KEEP %-20s docs=%-6d chunks=%,8d -> append=%,6d  (baseline=%,d, cap=%,d)%n",
                        script, docs, chunks.size(), kept.size(), baselineLines, cap);
            }
            finalLines.put(script, kept);
            manifest.put(script,
                    new long[]{docs, chunks.size(), kept.size(), baselineLines, cap});
        }

        if (dryRun) {
            System.out.println("\nDry-run: skipping output writes.");
            return;
        }

        // --- Phase 4: write output ----------------------------------------------
        System.out.println("\n--- Phase 4: writing output ---");
        Files.createDirectories(outputDir);
        // Copy or rewrite every baseline file. Any *.train.gz with a kept
        // augmentation set is rewritten with appended lines; everything else
        // is byte-copied so the output directory is a complete drop-in for
        // TrainJunkModel.
        try (Stream<Path> stream = Files.list(baselineDir)) {
            for (Path src : (Iterable<Path>) stream.filter(Files::isRegularFile)::iterator) {
                String name = src.getFileName().toString();
                Path dst = outputDir.resolve(name);
                if (name.endsWith(".train.gz")) {
                    String script = name.substring(0, name.length() - ".train.gz".length())
                            .toUpperCase();
                    List<String> add = finalLines.get(script);
                    if (add != null && !add.isEmpty()) {
                        rewriteTrainWithAppend(src, dst, add);
                        System.out.printf("  WROTE  %-30s +%,d lines appended%n", name, add.size());
                    } else {
                        Files.copy(src, dst);
                        System.out.printf("  COPY   %-30s (no augmentation)%n", name);
                    }
                } else {
                    Files.copy(src, dst);
                }
            }
        }

        // Manifest
        Path manifestPath = outputDir.resolve("augmentation_manifest.tsv");
        try (BufferedWriter w = Files.newBufferedWriter(manifestPath, StandardCharsets.UTF_8)) {
            w.write("script\tdocs\tchunks_pre_cap\tlines_appended\tbaseline_lines\tcap\n");
            for (Map.Entry<String, long[]> e : manifest.entrySet()) {
                long[] r = e.getValue();
                w.write(String.format("%s\t%d\t%d\t%d\t%d\t%d%n",
                        e.getKey(), r[0], r[1], r[2], r[3], r[4]));
            }
        }
        System.out.println("\nWrote manifest: " + manifestPath);
    }

    // -------------------------------------------------------------------------
    // Phase helpers
    // -------------------------------------------------------------------------

    /** Returns lowercase script-name → line-count for every {@code *.train.gz}. */
    static Map<String, Long> scanBaselineLineCounts(Path baselineDir) throws IOException {
        Map<String, Long> out = new TreeMap<>();
        try (Stream<Path> stream = Files.list(baselineDir)) {
            for (Path p : (Iterable<Path>) stream
                    .filter(Files::isRegularFile)
                    .filter(x -> x.getFileName().toString().endsWith(".train.gz"))::iterator) {
                String name = p.getFileName().toString();
                String script = name.substring(0, name.length() - ".train.gz".length());
                out.put(script, countGzLines(p));
            }
        }
        return out;
    }

    static long countGzLines(Path path) throws IOException {
        long count = 0;
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(
                        new GZIPInputStream(Files.newInputStream(path)),
                        StandardCharsets.UTF_8))) {
            while (r.readLine() != null) {
                count++;
            }
        }
        return count;
    }

    /** Reads {@code tk:content} from the first metadata record of a tika-app RMETA JSON. */
    static String readContent(Path jsonFile) {
        try (Reader r = new InputStreamReader(
                Files.newInputStream(jsonFile), StandardCharsets.UTF_8)) {
            List<Metadata> list = JsonMetadataList.fromJson(r);
            if (list == null || list.isEmpty()) {
                return null;
            }
            return list.get(0).get(TikaCoreProperties.TIKA_CONTENT);
        } catch (IOException e) {
            return null;
        }
    }

    /** Result of dominant-script analysis for a document. */
    static final class DocScript {
        final Character.UnicodeScript script;
        final double dominance;

        DocScript(Character.UnicodeScript script, double dominance) {
            this.script = script;
            this.dominance = dominance;
        }
    }

    static DocScript dominantScript(String text) {
        Map<Character.UnicodeScript, Long> counts = new LinkedHashMap<>();
        long total = 0;
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
        if (total == 0) {
            return new DocScript(null, 0.0);
        }
        Character.UnicodeScript best = null;
        long bestCount = 0;
        for (Map.Entry<Character.UnicodeScript, Long> e : counts.entrySet()) {
            if (e.getValue() > bestCount) {
                bestCount = e.getValue();
                best = e.getKey();
            }
        }
        return new DocScript(best, (double) bestCount / total);
    }

    /**
     * Structural test for UTF-8 source decoded as windows-1252.
     *
     * <p>UTF-8 multi-byte sequences start with a lead byte in the 0xC2–0xDF
     * range followed by one or more continuation bytes in 0x80–0xBF. When
     * those same bytes are mis-decoded as windows-1252 they render as a
     * Latin-Supplement letter (U+00C0–U+00DF) immediately followed by a C1
     * control or typographic codepoint (U+0080–U+00BF). Legitimate German /
     * French / Italian / Spanish text essentially never produces this bigram
     * shape — those high-Latin letters appear adjacent to other Latin letters,
     * not to currency symbols / smart quotes / non-breaking spaces.
     *
     * <p>This is a structural cross-encoding test, not a content-quality
     * heuristic. {@link org.apache.tika.ml.junkdetect.JunkDetector} cannot
     * discriminate this case (empirically the zScore delta between mojibake'd
     * Latin and clean Latin is &lt;0.03, within noise), so the right tool is
     * a structural check that targets this specific known encoding pathology.
     */
    static int countUtf8AsWin1252Bigrams(String text) {
        int n = 0;
        for (int i = 0; i + 1 < text.length(); i++) {
            char a = text.charAt(i);
            char b = text.charAt(i + 1);
            if (a >= 0x00C0 && a <= 0x00DF && b >= 0x0080 && b <= 0x00BF) {
                n++;
            }
        }
        return n;
    }

    /**
     * Splits content into sentence-shaped chunks of roughly
     * {@link #TARGET_CHUNK_CHARS} characters.
     *
     * <p>Tika's text extraction inserts a newline between each HTML element, so
     * a single paragraph that uses {@code <span>} / {@code <a>} / {@code <br>}
     * arrives here as many short lines (headers, captions, list items,
     * navigation labels). Treating each of those as a separate training
     * sample produces a bigram table with too many one-off bigrams and too
     * little within-chunk statistics — multi-byte scripts (HAN, HANGUL, etc.)
     * suffer worst because their bytes-per-char ratio means the same
     * per-byte minimum filter passes very small char counts through.
     *
     * <p>This chunker therefore greedily concatenates short newline-separated
     * lines (with a single-space joiner) until they reach the target size,
     * yielding chunks comparable in shape to MADLAD/Wikipedia sentences. Any
     * source line that's already longer than {@link #MAX_CHUNK_CHARS} flushes
     * the current buffer first and is then sliced at whitespace.
     */
    static List<String> chunk(String content) {
        List<String> out = new ArrayList<>();
        StringBuilder buf = new StringBuilder();
        for (String raw : content.split("\n")) {
            String line = raw.replace('\t', ' ').strip();
            if (line.isEmpty()) {
                continue;
            }
            // Collapse repeated whitespace to keep chunk shape comparable to
            // sentence-level training samples.
            line = line.replaceAll("\\s+", " ");
            if (line.length() > MAX_CHUNK_CHARS) {
                // Flush whatever's been accumulating, then slice the long line.
                if (buf.length() > 0) {
                    out.add(buf.toString());
                    buf.setLength(0);
                }
                int start = 0;
                while (start < line.length()) {
                    int end = Math.min(start + TARGET_CHUNK_CHARS, line.length());
                    if (end < line.length()) {
                        int hardCap = Math.min(start + MAX_CHUNK_CHARS, line.length());
                        int ws = line.indexOf(' ', end);
                        if (ws >= 0 && ws < hardCap) {
                            end = ws;
                        } else {
                            end = hardCap;
                        }
                    }
                    String piece = line.substring(start, end).strip();
                    if (!piece.isEmpty()) {
                        out.add(piece);
                    }
                    start = end + 1; // skip whitespace boundary
                }
                continue;
            }
            // Short line: accumulate into the buffer. Emit when the joiner
            // plus the new line would exceed the target.
            int joinerLen = buf.length() > 0 ? 1 : 0;
            if (buf.length() + joinerLen + line.length() > TARGET_CHUNK_CHARS
                    && buf.length() > 0) {
                out.add(buf.toString());
                buf.setLength(0);
            }
            if (buf.length() > 0) {
                buf.append(' ');
            }
            buf.append(line);
        }
        if (buf.length() > 0) {
            out.add(buf.toString());
        }
        return out;
    }

    /**
     * Decompresses {@code src}, writes every original line to {@code dst}, then
     * appends the {@code extra} lines, and recompresses. Single gzip member —
     * round-trips identically through {@link GZIPInputStream}.
     */
    static void rewriteTrainWithAppend(Path src, Path dst, List<String> extra)
            throws IOException {
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(
                        new GZIPInputStream(Files.newInputStream(src)),
                        StandardCharsets.UTF_8));
                BufferedWriter w = new BufferedWriter(
                        new OutputStreamWriter(
                                new GZIPOutputStream(Files.newOutputStream(dst)),
                                StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                w.write(line);
                w.newLine();
            }
            for (String s : extra) {
                w.write(s);
                w.newLine();
            }
        }
    }

    /** Per-doc quality fields from a tika-eval Profile run. */
    static final class ProfileRow {
        final double oov;
        final double langness;
        final String lang;

        ProfileRow(double oov, double langness, String lang) {
            this.oov = oov;
            this.langness = langness;
            this.lang = lang;
        }
    }

    /**
     * Loads a tika-eval Profile CSV export (H2 default CSV format: comma-
     * separated, double-quoted fields). Expected columns: {@code FILE_PATH,
     * OOV, LANGNESS, LANG}. Produces {@code FILE_PATH → ProfileRow} for join
     * against extracts on disk.
     */
    static Map<String, ProfileRow> loadProfileCsv(Path csv) throws IOException {
        Map<String, ProfileRow> out = new HashMap<>();
        try (BufferedReader r = Files.newBufferedReader(csv, StandardCharsets.UTF_8)) {
            String header = r.readLine();
            if (header == null) {
                return out;
            }
            String[] cols = parseCsvLine(header);
            int idxPath = -1;
            int idxOov = -1;
            int idxLang = -1;
            int idxLangId = -1;
            for (int i = 0; i < cols.length; i++) {
                switch (cols[i].toUpperCase()) {
                    case "FILE_PATH":
                        idxPath = i;
                        break;
                    case "OOV":
                        idxOov = i;
                        break;
                    case "LANGNESS":
                    case "LANGUAGENESS":
                        idxLang = i;
                        break;
                    case "LANG":
                    case "LANG_ID_1":
                        idxLangId = i;
                        break;
                    default:
                        break;
                }
            }
            if (idxPath < 0 || idxOov < 0 || idxLang < 0) {
                throw new IOException("profile CSV must have FILE_PATH, OOV, LANGNESS columns; "
                        + "saw: " + String.join(",", cols));
            }
            String line;
            while ((line = r.readLine()) != null) {
                if (line.isEmpty()) continue;
                String[] f = parseCsvLine(line);
                if (f.length <= Math.max(idxPath, Math.max(idxOov, idxLang))) continue;
                double oov;
                double langness;
                try {
                    oov = Double.parseDouble(f[idxOov]);
                    langness = Double.parseDouble(f[idxLang]);
                } catch (NumberFormatException e) {
                    continue;
                }
                String lang = idxLangId >= 0 && idxLangId < f.length ? f[idxLangId] : null;
                out.put(f[idxPath], new ProfileRow(oov, langness, lang));
            }
        }
        return out;
    }

    /** True if the chunk contains at least one {@link #SYMBOL_TARGETS} character. */
    static boolean containsTargetSymbol(String chunk) {
        for (int i = 0; i < chunk.length(); i++) {
            if (SYMBOL_TARGETS.indexOf(chunk.charAt(i)) >= 0) {
                return true;
            }
        }
        return false;
    }

    /** Minimal H2-flavour CSV row parser: double-quoted fields, doubled "" escapes. */
    static String[] parseCsvLine(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    cur.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                out.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        out.add(cur.toString());
        return out.toArray(new String[0]);
    }

    /**
     * Builds the join key used to look up a tika-eval profile row for a given
     * extract file: extracts-root-relative path with the {@code .json} suffix
     * stripped and any backslashes normalised to forward slashes. Matches the
     * value tika-eval writes as {@code FILE_PATH} in the CONTAINERS table.
     */
    static String profileKey(Path extractsDir, Path extractFile) {
        String rel = extractsDir.relativize(extractFile).toString()
                .replace('\\', '/');
        if (rel.endsWith(".json")) {
            rel = rel.substring(0, rel.length() - ".json".length());
        }
        return rel;
    }

    private static void printUsage() {
        System.err.println("Usage: BuildJunkAugmentationData [options]");
        System.err.println("  --extracts <dir>          tika-app RMETA JSON output (required)");
        System.err.println("  --baseline <dir>          read-only original training dir"
                + " containing <script>.train.gz / .dev.gz / .test.gz (required)");
        System.err.println("  --output <dir>            output dir; gets copies +"
                + " augmented .train.gz files (required, must differ from --baseline)");
        System.err.println("  --profile-csv <file>      optional tika-eval Profile CSV"
                + " (FILE_PATH, OOV, LANGNESS, LANG); enables OOV/langness gating");
        System.err.println("  --max-oov <f>             max OOV when profile-csv set"
                + " (default " + DEFAULT_MAX_OOV + "); negative OOV (no word list) bypasses");
        System.err.println("  --min-langness <f>        min LANGUAGENESS when profile-csv set"
                + " (default " + DEFAULT_MIN_LANGNESS + ")");
        System.err.println("  --symbol-boost <f>        LATIN-only: reserve fraction f of the cap"
                + " for chunks containing win-1252 symbols " + SYMBOL_TARGETS
                + " (default " + DEFAULT_SYMBOL_BOOST_FRAC + " = off)");
        System.err.println("  --hard-cap <int>          max appended lines per script"
                + " (default " + DEFAULT_HARD_CAP_LINES + ")");
        System.err.println("  --baseline-frac-cap <f>   fraction-of-baseline cap"
                + " (default " + DEFAULT_BASELINE_FRAC_CAP + ")");
        System.err.println("  --min-docs <int>          min quality-filtered docs to augment"
                + " a script (default " + DEFAULT_MIN_DOCS + ")");
        System.err.println("  --seed <long>             RNG seed for sampling"
                + " (default " + JunkDetectorTrainingConfig.SEED + ")");
        System.err.println("  --dry-run                 plan only; no file writes");
    }
}
