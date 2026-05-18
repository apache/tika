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

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;

import org.apache.tika.ml.junkdetect.JunkDetector;
import org.apache.tika.ml.junkdetect.TextQualityFeatures;
import org.apache.tika.ml.junkdetect.TextQualityFeatures.StripMode;
import org.apache.tika.quality.TextQualityScore;

/**
 * Eval harness: for each labeled charset in {@code ~/data/charsets/devtest/},
 * decode under its true charset (clean) and under a curated set of wrong
 * charsets (mojibake), score with {@link JunkDetector}, report margin
 * statistics per (labeled_charset × wrong_charset × source-byte-length).
 *
 * <p>Devtest file format: gzip → repeated {@code [u16 big-endian length,
 * length bytes]} records, where the bytes are real text encoded in the
 * labeled charset.  Same format the charset trainer consumes.
 *
 * <p>Output (TSVs):
 * <ul>
 *   <li><b>detail.tsv</b>: one row per (strip_mode × labeled_cs × script ×
 *       wrong_cs × length).  Columns: n, mean_clean_z, mean_mojibake_z,
 *       cohens_d, mean_margin, p5_margin, p50_margin, fpr, tpr.</li>
 *   <li><b>summary.tsv</b>: macro-averaged across wrong charsets, per
 *       (strip_mode, script, length).  The headline "is this script in
 *       trouble?" view.</li>
 *   <li><b>script_pivot.tsv</b>: per-(strip_mode, script) rollup across all
 *       lengths + wrong charsets.</li>
 *   <li><b>per_record.tsv</b> (when {@code --per-record} is set): one row
 *       per individual (record × strip_mode × wrong_cs) — wide feature
 *       columns z1..z4 from {@link JunkDetector#scoreWithFeatureComponents}
 *       plus z5..z9 from {@link TextQualityFeatures}, for both the clean
 *       and mojibake decode.  This is the substrate for the Phase-2
 *       feature study.</li>
 * </ul>
 *
 * <p>"Margin" is the per-record paired difference {@code clean_z -
 * mojibake_z}.  Mean margin and 5th-percentile margin are the
 * margin-maximization metrics the v6 retrain is optimizing for.  Cohen's d
 * is the independent-distribution analog (kept for compatibility with the
 * existing {@link EvalJunkDetector} schema).
 *
 * <p>Usage:
 * <pre>
 *   ./mvnw -pl tika-ml/tika-ml-junkdetect exec:java \
 *     -Dexec.mainClass=org.apache.tika.ml.junkdetect.tools.EvalJunkOnCharsetDevtest \
 *     -Dexec.args="--devtest-dir ~/data/charsets/devtest --output-dir /tmp/eval \
 *                  --strip-modes NONE,WHITESPACE,WHITESPACE_CONTROL,ALL_COMMON \
 *                  --per-record /tmp/eval/per_record.tsv --per-record-max 50"
 * </pre>
 */
public class EvalJunkOnCharsetDevtest {

    /**
     * Global wrong-charset fallback used for any labeled charset not present
     * in {@link #PER_SOURCE_WRONG_CHARSETS}.  Spans the common real-world
     * mojibake families: Western Latin (cp1252, ISO-8859-1, MacRoman), CJK
     * over-claim (GB18030, Big5-HKSCS, Shift_JIS), Cyrillic (KOI8-R,
     * cp1251), Arabic (cp1256), EBCDIC over-claim (IBM424), DOS Latin
     * (IBM850), and UTF-8 (catches non-UTF-8 bytes as replacement-char
     * garbage).
     */
    private static final List<String> DEFAULT_WRONG_CHARSETS = List.of(
            "windows-1252", "ISO-8859-1", "x-MacRoman",
            "GB18030", "Big5-HKSCS", "Shift_JIS",
            "KOI8-R", "windows-1251",
            "windows-1256", "IBM424",
            "IBM850", "UTF-8"
    );

    /**
     * Per-source-charset curated wrong-charset lists.  Targets the failure
     * cohorts surfaced by the 29 K CommonCrawl A-vs-B eval (Polish
     * windows-1250↔1252, Masada Cyrillic windows-1251↔1255, Portuguese
     * windows-1252↔ISO-8859-3, German windows-1252↔x-MacRoman, etc.).
     * Anything not in this map uses {@link #DEFAULT_WRONG_CHARSETS}.
     */
    private static final Map<String, List<String>> PER_SOURCE_WRONG_CHARSETS;

    static {
        Map<String, List<String>> m = new LinkedHashMap<>();
        // Polish / Czech / Slovak / Hungarian / Croatian — the emblem case
        m.put("windows-1250", List.of(
                "windows-1252", "windows-1257", "ISO-8859-3",
                "ISO-8859-2", "x-MacRoman", "IBM852"));
        // Cyrillic — Masada cohort
        m.put("windows-1251", List.of(
                "windows-1252", "windows-1255", "windows-1258",
                "KOI8-R", "ISO-8859-3", "GB18030"));
        // Western Latin — Portuguese ISO-8859-3, German MacRoman.  Includes
        // UTF-16LE/BE because TIKA-4683 was specifically ASCII source
        // mis-decoded as UTF-16 → dense CJK (every byte-pair becomes a
        // Han codepoint, so "alphabetic" goes UP while the text is junk).
        m.put("windows-1252", List.of(
                "windows-1250", "ISO-8859-3", "x-MacRoman",
                "IBM850", "IBM852", "IBM866",
                "UTF-16LE", "UTF-16BE"));
        // Greek
        m.put("windows-1253", List.of(
                "windows-1252", "ISO-8859-3", "windows-1254"));
        // Turkish
        m.put("windows-1254", List.of(
                "windows-1252", "windows-1250", "ISO-8859-9"));
        // Hebrew
        m.put("windows-1255", List.of(
                "windows-1252", "windows-1251", "ISO-8859-8"));
        // Arabic
        m.put("windows-1256", List.of(
                "windows-1252", "UTF-8", "ISO-8859-6"));
        // Baltic
        m.put("windows-1257", List.of(
                "windows-1250", "windows-1252", "ISO-8859-13"));
        // Vietnamese
        m.put("windows-1258", List.of(
                "windows-1252", "windows-1250", "ISO-8859-3"));
        // ISO Latin-15 (ISO-8859-1's later cousin; same charset slot for
        // ISO-8859-1 is set below alongside the UTF-16 entries)
        m.put("ISO-8859-15", List.of(
                "windows-1252", "windows-1250", "x-MacRoman", "UTF-8"));
        // KOI8 vs Windows Cyrillic
        m.put("KOI8-R", List.of(
                "windows-1251", "windows-1252", "KOI8-U"));
        m.put("KOI8-U", List.of(
                "windows-1251", "windows-1252", "KOI8-R"));
        // CJK siblings
        m.put("GB18030", List.of(
                "EUC-JP", "Big5-HKSCS", "Shift_JIS",
                "x-windows-949", "windows-1252"));
        m.put("Big5-HKSCS", List.of(
                "GB18030", "EUC-JP", "Shift_JIS"));
        m.put("EUC-JP", List.of(
                "Shift_JIS", "GB18030", "Big5-HKSCS"));
        m.put("Shift_JIS", List.of(
                "EUC-JP", "GB18030", "Big5-HKSCS"));
        m.put("x-windows-949", List.of(
                "GB18030", "EUC-JP", "Shift_JIS"));
        // UTF-8 — the must-not-regress cohort (mis-declared meta tags).
        // UTF-16LE/BE here exercise the AIT5/TIKA-4683-shape failure
        // where multi-byte UTF-8 gets re-cast as 16-bit CJK ideographs.
        m.put("UTF-8", List.of(
                "windows-1252", "windows-1250", "ISO-8859-1",
                "UTF-16LE", "UTF-16BE"));
        // ISO-8859-1 — also exercises the UTF-16-as-CJK trap (Western Latin
        // bytes interpreted as UTF-16 produce dense CJK)
        m.put("ISO-8859-1", List.of(
                "windows-1252", "windows-1250", "x-MacRoman",
                "UTF-8", "UTF-16LE", "UTF-16BE"));
        // ISO-8859-2 (synthesized from windows-1250) — the Polish ¶ case.
        // Cross-decoding ISO-8859-2 bytes as windows-1250 reproduces the
        // ci¶nienia split-word mojibake that motivated this whole eval.
        m.put("ISO-8859-2", List.of(
                "windows-1250", "windows-1252", "ISO-8859-3"));
        PER_SOURCE_WRONG_CHARSETS = Collections.unmodifiableMap(m);
    }

    /**
     * Source-byte length buckets to slice records into.  Includes very short
     * buckets (5, 10, 15) because the Polish split-word case is exactly a
     * single ~10-byte word (e.g. {@code ciśnienia}), and the per-feature
     * discrimination at that length is the headline thing this eval is for.
     */
    private static final int[] DEFAULT_LENGTHS = {5, 10, 15, 20, 50, 100, 200, 500};

    /** Cap on records loaded per labeled-charset file. */
    private static final int DEFAULT_MAX_RECORDS = 2000;

    /** Threshold for FPR/TPR reporting; matches EvalJunkDetector default. */
    private static final float DEFAULT_THRESHOLD = -2.0f;

    /** Minimum number of paired (clean, mojibake) samples per cell to emit a row. */
    private static final int MIN_SAMPLES_PER_CELL = 30;

    /** Cap on records emitted to per_record.tsv per (labeled × length × wrong) cell. */
    private static final int DEFAULT_PER_RECORD_MAX = 50;

    /** Default strip modes to evaluate (matches Phase-1 plan). */
    private static final List<StripMode> DEFAULT_STRIP_MODES = List.of(
            StripMode.NONE,
            StripMode.WHITESPACE,
            StripMode.WHITESPACE_CONTROL,
            StripMode.ALL_COMMON);

    /**
     * Synthetic source-charset definitions.  For each entry, the eval reads
     * records from {@code <sourceFile>.bin.gz} in the devtest dir, decodes
     * them as {@code sourceCharset}, then re-encodes the resulting Unicode
     * under the synthetic charset (the map key).  The resulting bytes are
     * then treated as if they had been read from a labeled-with-the-key
     * file.
     *
     * <p>Needed because the training corpus only has Windows codepages
     * ({@code windows-1250.bin.gz}, etc.) but the real-world Polish
     * {@code ci¶nienia} failure mode requires ISO-8859-2 bytes (where
     * {@code ś = 0xB6}, not the windows-1250 {@code 0x9C}).  Re-encoding
     * windows-1250 Polish records as ISO-8859-2 gives us labeled
     * ISO-8859-2 data that, when cross-decoded back as windows-1250,
     * reproduces the {@code ¶}-splits-word pattern.
     */
    private static final Map<String, SyntheticSource> SYNTHETIC_SOURCES;

    static {
        Map<String, SyntheticSource> m = new LinkedHashMap<>();
        m.put("ISO-8859-2",
                new SyntheticSource("windows-1250", "windows-1250"));
        SYNTHETIC_SOURCES = Collections.unmodifiableMap(m);
    }

    private static final class SyntheticSource {
        final String sourceFileBasename; // without .bin.gz suffix
        final String sourceCharset;

        SyntheticSource(String sourceFileBasename, String sourceCharset) {
            this.sourceFileBasename = sourceFileBasename;
            this.sourceCharset = sourceCharset;
        }
    }

    public static void main(String[] args) throws IOException {
        Path devtestDir = Paths.get(System.getProperty("user.home"),
                "data", "charsets", "devtest");
        Path outputDir = Paths.get("/tmp/junkdetect-eval");
        Path modelPath = null;
        int maxRecords = DEFAULT_MAX_RECORDS;
        int[] lengths = DEFAULT_LENGTHS;
        float threshold = DEFAULT_THRESHOLD;
        List<String> wrongCharsetsOverride = null;
        List<String> labeledFilter = null;
        boolean usePerSourceMap = true;
        List<StripMode> stripModes = DEFAULT_STRIP_MODES;
        Path perRecordPath = null;
        int perRecordMax = DEFAULT_PER_RECORD_MAX;
        boolean wordMode = false;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--devtest-dir":
                    devtestDir = Paths.get(args[++i]);
                    break;
                case "--output-dir":
                    outputDir = Paths.get(args[++i]);
                    break;
                case "--model":
                    modelPath = Paths.get(args[++i]);
                    break;
                case "--max-records":
                    maxRecords = Integer.parseInt(args[++i]);
                    break;
                case "--threshold":
                    threshold = Float.parseFloat(args[++i]);
                    break;
                case "--lengths":
                    lengths = Arrays.stream(args[++i].split(","))
                            .mapToInt(Integer::parseInt).toArray();
                    break;
                case "--wrong-charsets":
                    wrongCharsetsOverride = Arrays.asList(args[++i].split(","));
                    usePerSourceMap = false;
                    break;
                case "--no-per-source-map":
                    usePerSourceMap = false;
                    break;
                case "--only":
                    labeledFilter = Arrays.asList(args[++i].split(","));
                    break;
                case "--strip-modes":
                    stripModes = parseStripModes(args[++i]);
                    break;
                case "--per-record":
                    perRecordPath = Paths.get(args[++i]);
                    break;
                case "--per-record-max":
                    perRecordMax = Integer.parseInt(args[++i]);
                    break;
                case "--word-mode":
                    wordMode = true;
                    break;
                default:
                    System.err.println("Unknown arg: " + args[i]);
                    printUsage();
                    System.exit(1);
            }
        }

        if (!Files.isDirectory(devtestDir)) {
            System.err.println("ERROR: devtest-dir not found: " + devtestDir);
            System.exit(1);
        }
        Files.createDirectories(outputDir);

        JunkDetector detector = modelPath != null
                ? JunkDetector.loadFromPath(modelPath)
                : JunkDetector.loadFromClasspath();

        System.err.println("=== EvalJunkOnCharsetDevtest ===");
        System.err.println("  devtest-dir:  " + devtestDir);
        System.err.println("  output-dir:   " + outputDir);
        System.err.println("  model:        " + (modelPath != null ? modelPath : "classpath default"));
        System.err.println("  model version: " + detector.getModelVersion());
        System.err.println("  max-records:  " + maxRecords);
        System.err.println("  lengths:      " + Arrays.toString(lengths));
        System.err.println("  threshold:    " + threshold);
        System.err.println("  strip-modes:  " + stripModes);
        System.err.println("  per-source map: " + (usePerSourceMap ? "yes" : "no"));
        System.err.println("  word-mode:    " + wordMode);
        if (perRecordPath != null) {
            System.err.println("  per-record:   " + perRecordPath
                    + "  (max " + perRecordMax + " per cell)");
        }

        List<Path> files;
        try (Stream<Path> stream = Files.list(devtestDir)) {
            files = stream
                    .filter(p -> p.getFileName().toString().endsWith(".bin.gz"))
                    .sorted()
                    .toList();
        }
        if (files.isEmpty()) {
            System.err.println("ERROR: no *.bin.gz files in " + devtestDir);
            System.exit(1);
        }

        Path detailPath = outputDir.resolve("detail.tsv");
        Path summaryPath = outputDir.resolve("summary.tsv");
        Path pivotPath = outputDir.resolve("script_pivot.tsv");

        List<Row> allRows = new ArrayList<>();

        try (PrintWriter detail = new PrintWriter(
                Files.newBufferedWriter(detailPath, StandardCharsets.UTF_8));
             PrintWriter perRecord = perRecordPath != null
                     ? new PrintWriter(Files.newBufferedWriter(perRecordPath,
                                     StandardCharsets.UTF_8))
                     : null) {

            detail.println("strip_mode\tlabeled_cs\tscript\twrong_cs\tlength\tn"
                    + "\tmean_clean_z\tmean_mojibake_z\tcohens_d"
                    + "\tmean_margin\tp5_margin\tp50_margin"
                    + "\tfpr\ttpr");

            if (perRecord != null) {
                perRecord.println(perRecordHeader());
            }

            for (Path file : files) {
                String labeledName = filenameToCharsetName(file);
                if (labeledFilter != null && !labeledFilter.contains(labeledName)) {
                    continue;
                }
                Charset labeled = tryGetCharset(labeledName);
                if (labeled == null) {
                    System.err.println("  SKIP: labeled charset unavailable: " + labeledName);
                    continue;
                }
                List<byte[]> records = readRecords(file, maxRecords);
                processLabeled(detector, detail, perRecord, allRows,
                        labeledName, labeled, records,
                        stripModes, lengths, threshold,
                        wrongCharsetsOverride, usePerSourceMap, perRecordMax,
                        false, wordMode);
            }

            // Synthetic sources: re-encode a known charset's records under
            // a charset that has no devtest file of its own.  Used to
            // reproduce the Polish ¶ failure (synthesize ISO-8859-2 bytes
            // from windows-1250 records).
            for (Map.Entry<String, SyntheticSource> e : SYNTHETIC_SOURCES.entrySet()) {
                String synthName = e.getKey();
                if (labeledFilter != null && !labeledFilter.contains(synthName)) {
                    continue;
                }
                Charset synth = tryGetCharset(synthName);
                if (synth == null) {
                    System.err.println("  SKIP synthetic: charset unavailable: " + synthName);
                    continue;
                }
                SyntheticSource src = e.getValue();
                Path sourceFile = devtestDir.resolve(src.sourceFileBasename + ".bin.gz");
                if (!Files.isReadable(sourceFile)) {
                    System.err.println("  SKIP synthetic " + synthName
                            + ": source file missing: " + sourceFile);
                    continue;
                }
                Charset sourceCs = tryGetCharset(src.sourceCharset);
                if (sourceCs == null) {
                    System.err.println("  SKIP synthetic " + synthName
                            + ": source charset unavailable: " + src.sourceCharset);
                    continue;
                }
                List<byte[]> sourceRecords = readRecords(sourceFile, maxRecords);
                List<byte[]> synthRecords =
                        synthesizeRecords(sourceRecords, sourceCs, synth);
                System.err.printf("%n=== synthetic %s ← %s round-trip: kept %d / %d records ===%n",
                        synthName, src.sourceCharset, synthRecords.size(),
                        sourceRecords.size());
                processLabeled(detector, detail, perRecord, allRows,
                        synthName, synth, synthRecords,
                        stripModes, lengths, threshold,
                        wrongCharsetsOverride, usePerSourceMap, perRecordMax,
                        true, wordMode);
            }
        }

        writeSummary(summaryPath, allRows, lengths);
        writeScriptPivot(pivotPath, allRows);

        System.err.println("\nWrote " + detailPath);
        System.err.println("Wrote " + summaryPath);
        System.err.println("Wrote " + pivotPath);
        if (perRecordPath != null) {
            System.err.println("Wrote " + perRecordPath);
        }
        System.err.println("Done.");
    }

    // -----------------------------------------------------------------------
    // Per-labeled-charset processing (shared by real-file and synthetic loops)
    // -----------------------------------------------------------------------

    private static void processLabeled(JunkDetector detector,
                                       PrintWriter detail, PrintWriter perRecord,
                                       List<Row> allRows,
                                       String labeledName, Charset labeled,
                                       List<byte[]> records,
                                       List<StripMode> stripModes,
                                       int[] lengths,
                                       float threshold,
                                       List<String> wrongCharsetsOverride,
                                       boolean usePerSourceMap,
                                       int perRecordMax,
                                       boolean synthetic,
                                       boolean wordMode) {
        if (records.size() < MIN_SAMPLES_PER_CELL) {
            System.err.printf("  SKIP %s: only %d records%n",
                    labeledName, records.size());
            return;
        }
        List<String> wrongCharsetNames = resolveWrongCharsets(
                labeledName, wrongCharsetsOverride, usePerSourceMap);
        Map<String, Charset> resolvedWrong = resolveCharsets(wrongCharsetNames);

        System.err.printf("%n--- %s%s (%d records, wrong=%s) ---%n",
                labeledName, synthetic ? " (synthetic)" : "",
                records.size(), wrongCharsetNames);

        // Word-mode replaces the length-bucket loop with a single bucket of
        // whitespace-delimited tokens (length-in-bytes = WORD_MODE_LEN_SENTINEL
        // for reporting; actual sizes vary per token).
        int[] effectiveLengths = wordMode
                ? new int[]{WORD_MODE_LEN_SENTINEL} : lengths;

        for (StripMode strip : stripModes) {
            for (int len : effectiveLengths) {
                List<byte[]> slices = wordMode
                        ? extractTokens(records, labeled)
                        : sliceToLength(records, len);
                if (slices.size() < MIN_SAMPLES_PER_CELL) {
                    continue;
                }

                List<String> cleanTextsRaw = decodeAll(slices, labeled);
                List<String> cleanTexts = applyStrip(cleanTextsRaw, strip);
                List<Float> cleanZs = scoreAll(detector, cleanTexts);
                if (cleanZs.size() < MIN_SAMPLES_PER_CELL) {
                    continue;
                }

                String script = detectDominantScript(
                        cleanTextsRaw.get(cleanTextsRaw.size() / 2));

                List<FeatureSnapshot> cleanFeats = perRecord != null
                        ? snapshotAll(detector, cleanTexts)
                        : null;

                for (Map.Entry<String, Charset> entry : resolvedWrong.entrySet()) {
                    String wrongName = entry.getKey();
                    Charset wrongCs = entry.getValue();
                    if (equalCharset(labeled, wrongCs)) {
                        continue; // can't be its own mojibake
                    }

                    List<String> mojiTextsRaw = decodeAll(slices, wrongCs);
                    List<String> mojiTexts = applyStrip(mojiTextsRaw, strip);

                    Row row = scorePairs(detector, strip, script, labeledName,
                            wrongName, len, cleanTexts, mojiTexts,
                            cleanZs, threshold);
                    if (row == null) {
                        continue;
                    }
                    allRows.add(row);
                    detail.println(row.toTsv());

                    if (perRecord != null) {
                        List<FeatureSnapshot> mojiFeats =
                                snapshotAll(detector, mojiTexts);
                        writePerRecord(perRecord, labeledName + (synthetic ? " (syn)" : ""),
                                strip, labeledName, script, wrongName, len,
                                cleanTexts, mojiTexts,
                                cleanFeats, mojiFeats, perRecordMax);
                    }
                }
                detail.flush();
                if (perRecord != null) {
                    perRecord.flush();
                }
                System.err.printf("    strip=%-18s len=%4d  n_clean=%d%n",
                        strip, len, cleanZs.size());
            }
        }
    }

    // -----------------------------------------------------------------------
    // Synthetic source generation: source bytes → Unicode → synthetic bytes.
    // -----------------------------------------------------------------------

    private static List<byte[]> synthesizeRecords(List<byte[]> sourceRecords,
                                                  Charset sourceCs,
                                                  Charset synthCs) {
        CharsetDecoder dec = sourceCs.newDecoder()
                .onMalformedInput(CodingErrorAction.REPLACE)
                .onUnmappableCharacter(CodingErrorAction.REPLACE);
        java.nio.charset.CharsetEncoder enc = synthCs.newEncoder()
                .onMalformedInput(CodingErrorAction.REPLACE)
                .onUnmappableCharacter(CodingErrorAction.REPLACE)
                .replaceWith(new byte[]{(byte) 0x3F});  // '?' for unmappable
        List<byte[]> out = new ArrayList<>();
        for (byte[] src : sourceRecords) {
            String text;
            try {
                text = dec.decode(ByteBuffer.wrap(src)).toString();
            } catch (CharacterCodingException e) {
                continue;
            }
            if (text.isEmpty()) {
                continue;
            }
            byte[] synth;
            try {
                java.nio.ByteBuffer bb = enc.encode(java.nio.CharBuffer.wrap(text));
                synth = new byte[bb.remaining()];
                bb.get(synth);
            } catch (CharacterCodingException e) {
                continue;
            }
            // No fidelity filter — lossy round-trips just mean the synthetic
            // bytes contain more '?' chars (which become a replacement-ratio
            // signal in the eval, not a discard reason).
            if (synth.length > 0) {
                out.add(synth);
            }
        }
        return out;
    }

    // -----------------------------------------------------------------------
    // Per-cell scoring (one strip × labeled × wrong × length cell)
    // -----------------------------------------------------------------------

    private static Row scorePairs(JunkDetector detector,
                                  StripMode strip,
                                  String script,
                                  String labeledName, String wrongName,
                                  int length,
                                  List<String> cleanTexts,
                                  List<String> mojiTexts,
                                  List<Float> cleanZsPre,
                                  float threshold) {
        int n = Math.min(cleanTexts.size(), mojiTexts.size());
        List<Float> cleanZs = new ArrayList<>(n);
        List<Float> mojiZs = new ArrayList<>(n);
        List<Float> margins = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            float cz = cleanZsPre.get(i);
            TextQualityScore ms = detector.score(mojiTexts.get(i));
            if (ms.isUnknown()) {
                continue;
            }
            float mz = ms.getZScore();
            if (Float.isNaN(cz)) {
                continue;
            }
            cleanZs.add(cz);
            mojiZs.add(mz);
            margins.add(cz - mz);
        }
        if (margins.size() < MIN_SAMPLES_PER_CELL) {
            return null;
        }
        return new Row(strip, labeledName, script, wrongName, length,
                cleanZs, mojiZs, margins, threshold);
    }

    // -----------------------------------------------------------------------
    // Per-record output (Phase 2 feature study)
    // -----------------------------------------------------------------------

    private static String perRecordHeader() {
        return "strip_mode\tlabeled_cs\tscript\twrong_cs\tlength\trecord_idx"
                + "\tclean_logit\twrong_logit\tmargin"
                + "\tclean_z1\tclean_z2\tclean_z3\tclean_z4"
                + "\twrong_z1\twrong_z2\twrong_z3\twrong_z4"
                + "\tclean_alphabetic_ratio\twrong_alphabetic_ratio"
                + "\tclean_letter_pair_density\twrong_letter_pair_density"
                + "\tclean_high_byte_entropy\twrong_high_byte_entropy"
                + "\tclean_replacement_ratio\twrong_replacement_ratio"
                + "\tclean_replacement_count\twrong_replacement_count"
                + "\tclean_per_word_script_purity\twrong_per_word_script_purity"
                + "\tclean_combining_mark_ratio\twrong_combining_mark_ratio"
                + "\tclean_letter_adj_mark_ratio\twrong_letter_adj_mark_ratio"
                + "\tclean_dominant_script\twrong_dominant_script"
                + "\tn_cp_clean\tn_differing_cp"
                + "\tclean_text\twrong_text";
    }

    private static void writePerRecord(PrintWriter out, String fileName,
                                       StripMode strip,
                                       String labeledName, String script,
                                       String wrongName, int len,
                                       List<String> cleanTexts,
                                       List<String> mojiTexts,
                                       List<FeatureSnapshot> cleanFeats,
                                       List<FeatureSnapshot> mojiFeats,
                                       int maxRecords) {
        int n = Math.min(cleanFeats.size(), mojiFeats.size());
        int emitted = 0;
        for (int i = 0; i < n && emitted < maxRecords; i++) {
            FeatureSnapshot c = cleanFeats.get(i);
            FeatureSnapshot w = mojiFeats.get(i);
            if (c.logit != c.logit || w.logit != w.logit) { // NaN check
                continue;
            }
            String cText = cleanTexts.get(i);
            String wText = mojiTexts.get(i);
            int nCpClean = cText.codePointCount(0, cText.length());
            int nDiffCp = countDifferingCodepoints(cText, wText);
            float margin = c.logit - w.logit;
            out.printf("%s\t%s\t%s\t%s\t%d\t%d"
                            + "\t%.4f\t%.4f\t%.4f"
                            + "\t%.4f\t%.4f\t%.4f\t%.4f"
                            + "\t%.4f\t%.4f\t%.4f\t%.4f"
                            + "\t%.4f\t%.4f"
                            + "\t%.4f\t%.4f"
                            + "\t%.4f\t%.4f"
                            + "\t%.4f\t%.4f"
                            + "\t%d\t%d"
                            + "\t%.4f\t%.4f"
                            + "\t%.4f\t%.4f"
                            + "\t%.4f\t%.4f"
                            + "\t%s\t%s"
                            + "\t%d\t%d"
                            + "\t%s\t%s%n",
                    strip, labeledName, script, wrongName, len, i,
                    c.logit, w.logit, margin,
                    c.z1, c.z2, c.z3, c.z4,
                    w.z1, w.z2, w.z3, w.z4,
                    c.alphabeticRatio, w.alphabeticRatio,
                    c.letterPairDensity, w.letterPairDensity,
                    c.highByteEntropy, w.highByteEntropy,
                    c.replacementRatio, w.replacementRatio,
                    c.replacementCount, w.replacementCount,
                    c.perWordScriptPurity, w.perWordScriptPurity,
                    c.combiningMarkRatio, w.combiningMarkRatio,
                    c.letterAdjacentToMarkRatio, w.letterAdjacentToMarkRatio,
                    c.dominantScript, w.dominantScript,
                    nCpClean, nDiffCp,
                    escapeForTsv(cText, 80),
                    escapeForTsv(wText, 80));
            emitted++;
        }
    }

    /**
     * Render {@code s} for inclusion in a TSV cell: replace every control,
     * format, and tab/newline codepoint with a {@code <U+XXXX>} escape so
     * the row remains parseable.  Truncate to {@code maxCp} codepoints
     * with a trailing ellipsis to keep TSV rows manageable.
     */
    private static String escapeForTsv(String s, int maxCp) {
        if (s == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        int kept = 0;
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            i += Character.charCount(cp);
            if (kept >= maxCp) {
                sb.append("…");
                break;
            }
            kept++;
            if (cp == '\t' || cp == '\n' || cp == '\r' || cp == '\\'
                    || Character.getType(cp) == Character.CONTROL
                    || Character.getType(cp) == Character.FORMAT) {
                sb.append(String.format("<U+%04X>", cp));
            } else {
                sb.appendCodePoint(cp);
            }
        }
        return sb.toString();
    }

    private static int countDifferingCodepoints(String a, String b) {
        int[] ac = a.codePoints().toArray();
        int[] bc = b.codePoints().toArray();
        int n = Math.min(ac.length, bc.length);
        int diff = 0;
        for (int i = 0; i < n; i++) {
            if (ac[i] != bc[i]) {
                diff++;
            }
        }
        diff += Math.abs(ac.length - bc.length);
        return diff;
    }

    private static List<FeatureSnapshot> snapshotAll(JunkDetector detector,
                                                     List<String> texts) {
        List<FeatureSnapshot> out = new ArrayList<>(texts.size());
        for (String t : texts) {
            out.add(FeatureSnapshot.of(detector, t));
        }
        return out;
    }

    private static final class FeatureSnapshot {
        final float z1, z2, z3, z4, logit;
        final String dominantScript;
        final double alphabeticRatio;
        final double letterPairDensity;
        final double highByteEntropy;
        final double replacementRatio;
        final int replacementCount;
        final double perWordScriptPurity;
        final double combiningMarkRatio;
        final double letterAdjacentToMarkRatio;

        FeatureSnapshot(float z1, float z2, float z3, float z4, float logit,
                        String dominantScript,
                        double alphabeticRatio, double letterPairDensity,
                        double highByteEntropy,
                        double replacementRatio, int replacementCount,
                        double perWordScriptPurity,
                        double combiningMarkRatio,
                        double letterAdjacentToMarkRatio) {
            this.z1 = z1;
            this.z2 = z2;
            this.z3 = z3;
            this.z4 = z4;
            this.logit = logit;
            this.dominantScript = dominantScript;
            this.alphabeticRatio = alphabeticRatio;
            this.letterPairDensity = letterPairDensity;
            this.highByteEntropy = highByteEntropy;
            this.replacementRatio = replacementRatio;
            this.replacementCount = replacementCount;
            this.perWordScriptPurity = perWordScriptPurity;
            this.combiningMarkRatio = combiningMarkRatio;
            this.letterAdjacentToMarkRatio = letterAdjacentToMarkRatio;
        }

        static FeatureSnapshot of(JunkDetector detector, String text) {
            JunkDetector.FeatureComponents fc =
                    detector.scoreWithFeatureComponents(text);
            return new FeatureSnapshot(
                    fc.z1, fc.z2, fc.z3, fc.z4, fc.logit,
                    fc.dominantScript == null ? "-" : fc.dominantScript,
                    TextQualityFeatures.alphabeticRatio(text),
                    TextQualityFeatures.letterPairDensity(text),
                    TextQualityFeatures.highByteEntropy(text),
                    TextQualityFeatures.replacementRatio(text),
                    TextQualityFeatures.replacementCount(text),
                    TextQualityFeatures.perWordScriptPurity(text),
                    TextQualityFeatures.combiningMarkRatio(text),
                    TextQualityFeatures.letterAdjacentToMarkRatio(text));
        }
    }

    // -----------------------------------------------------------------------
    // Wrong-charset list resolution
    // -----------------------------------------------------------------------

    private static List<String> resolveWrongCharsets(String labeledName,
                                                     List<String> override,
                                                     boolean usePerSourceMap) {
        if (override != null) {
            return override;
        }
        if (usePerSourceMap) {
            List<String> perSource = PER_SOURCE_WRONG_CHARSETS.get(labeledName);
            if (perSource != null) {
                return perSource;
            }
        }
        return DEFAULT_WRONG_CHARSETS;
    }

    private static Map<String, Charset> resolveCharsets(List<String> names) {
        Map<String, Charset> out = new LinkedHashMap<>();
        for (String n : names) {
            Charset cs = tryGetCharset(n);
            if (cs != null) {
                out.put(n, cs);
            } else {
                System.err.println("  WARN: wrong-charset unavailable: " + n);
            }
        }
        return out;
    }

    // -----------------------------------------------------------------------
    // I/O: read the gzipped length-prefixed record format
    // -----------------------------------------------------------------------

    private static List<byte[]> readRecords(Path file, int maxRecords) throws IOException {
        List<byte[]> records = new ArrayList<>();
        try (FileInputStream fis = new FileInputStream(file.toFile());
             GZIPInputStream gis = new GZIPInputStream(fis);
             DataInputStream dis = new DataInputStream(gis)) {
            while (records.size() < maxRecords) {
                int len;
                try {
                    len = dis.readUnsignedShort();
                } catch (EOFException eof) {
                    break;
                }
                byte[] rec = new byte[len];
                dis.readFully(rec);
                records.add(rec);
            }
        }
        return records;
    }

    /**
     * Tile each record into contiguous {@code len}-byte slices.  A 500-byte
     * record at {@code len=10} yields 50 slices, not 1.  This dramatically
     * increases the short-length sample count without needing more records,
     * which matters because the short-string buckets (5, 10, 15) are the
     * focus of this eval.  Caps total slices per length to avoid runaway
     * memory on extreme combinations.
     */
    private static List<byte[]> sliceToLength(List<byte[]> records, int len) {
        List<byte[]> slices = new ArrayList<>();
        int cap = MAX_SLICES_PER_LENGTH;
        outer:
        for (byte[] r : records) {
            int n = r.length / len;
            for (int i = 0; i < n; i++) {
                slices.add(Arrays.copyOfRange(r, i * len, (i + 1) * len));
                if (slices.size() >= cap) {
                    break outer;
                }
            }
        }
        return slices;
    }

    /** Cap on slices emitted per length bucket — prevents runaway growth at
     *  small lengths (e.g. 1000-byte records × len=5 = 200 slices each). */
    private static final int MAX_SLICES_PER_LENGTH = 20000;

    /**
     * Extract whitespace-delimited tokens from records decoded under
     * {@code sourceCs}, then re-encode each token under {@code sourceCs} to
     * get token-sized byte sequences.  Used by {@code --word-mode}: directly
     * tests the "single Polish word" failure case the eval is for.  Token
     * length filter (in codepoints) is set by {@link #WORD_MODE_MIN_CP} and
     * {@link #WORD_MODE_MAX_CP}.
     *
     * <p>Does NOT work for CJK cohorts — CJK text has no inter-character
     * whitespace, so each record collapses to one giant token that exceeds
     * {@link #WORD_MODE_MAX_CP} and gets dropped.  For CJK use the
     * fixed-length slicing path (default).
     */
    private static List<byte[]> extractTokens(List<byte[]> records,
                                              Charset sourceCs) {
        List<byte[]> out = new ArrayList<>();
        for (byte[] r : records) {
            String text = decode(r, sourceCs);
            int len = text.length();
            int i = 0;
            while (i < len) {
                int cp = text.codePointAt(i);
                if (Character.isWhitespace(cp)) {
                    i += Character.charCount(cp);
                    continue;
                }
                int tokenStart = i;
                int cps = 0;
                while (i < len) {
                    int c = text.codePointAt(i);
                    if (Character.isWhitespace(c)) {
                        break;
                    }
                    cps++;
                    i += Character.charCount(c);
                }
                if (cps >= WORD_MODE_MIN_CP && cps <= WORD_MODE_MAX_CP) {
                    String token = text.substring(tokenStart, i);
                    byte[] tokenBytes = token.getBytes(sourceCs);
                    if (tokenBytes.length > 0) {
                        out.add(tokenBytes);
                    }
                }
                if (out.size() >= MAX_SLICES_PER_LENGTH) {
                    return out;
                }
            }
        }
        return out;
    }

    private static final int WORD_MODE_MIN_CP = 3;
    private static final int WORD_MODE_MAX_CP = 30;

    /**
     * Length column written for word-mode rows.  Word-mode produces samples
     * of varying byte length, so we report a sentinel ({@code -1}) rather
     * than tagging each row with the per-record token length (which would
     * shatter the per-cell aggregation).  Use {@code grep '\t-1\t'} or
     * filter {@code length == -1} in the TSV to isolate word-mode rows.
     */
    private static final int WORD_MODE_LEN_SENTINEL = -1;

    private static List<String> decodeAll(List<byte[]> slices, Charset cs) {
        List<String> texts = new ArrayList<>(slices.size());
        for (byte[] s : slices) {
            texts.add(decode(s, cs));
        }
        return texts;
    }

    private static String decode(byte[] bytes, Charset cs) {
        CharsetDecoder dec = cs.newDecoder()
                .onMalformedInput(CodingErrorAction.REPLACE)
                .onUnmappableCharacter(CodingErrorAction.REPLACE);
        try {
            return dec.decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) {
            return new String(bytes, cs); // fallback; shouldn't happen with REPLACE
        }
    }

    private static List<String> applyStrip(List<String> texts, StripMode mode) {
        if (mode == StripMode.NONE) {
            return texts;
        }
        List<String> out = new ArrayList<>(texts.size());
        for (String t : texts) {
            out.add(TextQualityFeatures.strip(t, mode));
        }
        return out;
    }

    private static List<Float> scoreAll(JunkDetector detector, List<String> texts) {
        List<Float> zs = new ArrayList<>(texts.size());
        for (String t : texts) {
            TextQualityScore s = detector.score(t);
            if (!s.isUnknown()) {
                zs.add(s.getZScore());
            } else {
                zs.add(Float.NaN);
            }
        }
        return zs;
    }

    // -----------------------------------------------------------------------
    // Aggregation: summary.tsv (macro across wrong charsets, per script×length)
    // -----------------------------------------------------------------------

    private static void writeSummary(Path summaryPath, List<Row> rows,
                                     int[] lengths) throws IOException {
        try (PrintWriter out = new PrintWriter(
                Files.newBufferedWriter(summaryPath, StandardCharsets.UTF_8))) {
            out.println("strip_mode\tscript\tlength\tn_cells"
                    + "\tmacro_cohens_d\tmacro_mean_margin\tmacro_p5_margin"
                    + "\tmacro_fpr\tmacro_tpr");

            // Group by (strip_mode, script, length)
            Map<StripMode, Map<String, Map<Integer, List<Row>>>> bucketed = new LinkedHashMap<>();
            for (Row r : rows) {
                bucketed
                        .computeIfAbsent(r.stripMode, k -> new LinkedHashMap<>())
                        .computeIfAbsent(r.script, k -> new HashMap<>())
                        .computeIfAbsent(r.length, k -> new ArrayList<>())
                        .add(r);
            }

            for (Map.Entry<StripMode, Map<String, Map<Integer, List<Row>>>> e
                    : bucketed.entrySet()) {
                StripMode strip = e.getKey();
                List<String> scripts = new ArrayList<>(e.getValue().keySet());
                Collections.sort(scripts);
                for (String script : scripts) {
                    for (int len : lengths) {
                        List<Row> cell = e.getValue().get(script).get(len);
                        if (cell == null || cell.isEmpty()) {
                            continue;
                        }
                        double macroD = cell.stream()
                                .filter(r -> !Double.isNaN(r.cohensD))
                                .mapToDouble(r -> r.cohensD)
                                .average().orElse(Double.NaN);
                        double macroMargin = cell.stream()
                                .mapToDouble(r -> r.meanMargin)
                                .average().orElse(Double.NaN);
                        double macroP5 = cell.stream()
                                .mapToDouble(r -> r.p5Margin)
                                .average().orElse(Double.NaN);
                        double macroFpr = cell.stream()
                                .mapToDouble(r -> r.fpr)
                                .average().orElse(Double.NaN);
                        double macroTpr = cell.stream()
                                .mapToDouble(r -> r.tpr)
                                .average().orElse(Double.NaN);
                        out.printf("%s\t%s\t%d\t%d\t%.3f\t%.3f\t%.3f\t%.3f\t%.3f%n",
                                strip, script, len, cell.size(),
                                macroD, macroMargin, macroP5, macroFpr, macroTpr);
                    }
                }
            }
        }
    }

    // -----------------------------------------------------------------------
    // Aggregation: script_pivot.tsv (one line per strip × script — quick triage)
    // -----------------------------------------------------------------------

    private static void writeScriptPivot(Path path, List<Row> rows) throws IOException {
        try (PrintWriter out = new PrintWriter(
                Files.newBufferedWriter(path, StandardCharsets.UTF_8))) {
            out.println("strip_mode\tscript\tn_cells"
                    + "\tmean_d\tmean_margin\tmean_p5_margin"
                    + "\tmin_d_cell\tmin_margin_cell");

            Map<StripMode, Map<String, List<Row>>> byStripScript = new LinkedHashMap<>();
            for (Row r : rows) {
                byStripScript
                        .computeIfAbsent(r.stripMode, k -> new LinkedHashMap<>())
                        .computeIfAbsent(r.script, k -> new ArrayList<>())
                        .add(r);
            }
            for (Map.Entry<StripMode, Map<String, List<Row>>> e : byStripScript.entrySet()) {
                StripMode strip = e.getKey();
                List<String> scripts = new ArrayList<>(e.getValue().keySet());
                Collections.sort(scripts);
                for (String script : scripts) {
                    List<Row> cells = e.getValue().get(script);
                    double meanD = cells.stream()
                            .filter(r -> !Double.isNaN(r.cohensD))
                            .mapToDouble(r -> r.cohensD)
                            .average().orElse(Double.NaN);
                    double meanMargin = cells.stream()
                            .mapToDouble(r -> r.meanMargin)
                            .average().orElse(Double.NaN);
                    double meanP5 = cells.stream()
                            .mapToDouble(r -> r.p5Margin)
                            .average().orElse(Double.NaN);
                    Row minDCell = cells.stream()
                            .filter(r -> !Double.isNaN(r.cohensD))
                            .min((a, b) -> Double.compare(a.cohensD, b.cohensD))
                            .orElse(null);
                    Row minMarginCell = cells.stream()
                            .min((a, b) -> Double.compare(a.meanMargin, b.meanMargin))
                            .orElse(null);
                    out.printf("%s\t%s\t%d\t%.3f\t%.3f\t%.3f\t%s\t%s%n",
                            strip, script, cells.size(),
                            meanD, meanMargin, meanP5,
                            minDCell != null ? cellLabel(minDCell) : "-",
                            minMarginCell != null ? cellLabel(minMarginCell) : "-");
                }
            }
        }
    }

    private static String cellLabel(Row r) {
        return String.format("[%s→%s@%d]", r.labeledCs, r.wrongCs, r.length);
    }

    // -----------------------------------------------------------------------
    // Charset utilities
    // -----------------------------------------------------------------------

    private static String filenameToCharsetName(Path file) {
        String name = file.getFileName().toString();
        if (name.endsWith(".bin.gz")) {
            name = name.substring(0, name.length() - ".bin.gz".length());
        }
        return name;
    }

    private static Charset tryGetCharset(String name) {
        try {
            return Charset.forName(name);
        } catch (UnsupportedCharsetException | IllegalCharsetNameException e) {
            return null;
        }
    }

    private static boolean equalCharset(Charset a, Charset b) {
        return a.name().equalsIgnoreCase(b.name())
                || a.aliases().contains(b.name())
                || b.aliases().contains(a.name());
    }

    private static List<StripMode> parseStripModes(String s) {
        List<StripMode> out = new ArrayList<>();
        for (String tok : s.split(",")) {
            out.add(StripMode.valueOf(tok.trim().toUpperCase()));
        }
        return out;
    }

    // -----------------------------------------------------------------------
    // Script detection (parallels JunkDetector.detectDominantScript)
    // -----------------------------------------------------------------------

    private static final Map<String, String> SCRIPT_FALLBACK = Map.of(
            "HIRAGANA", "HAN",
            "KATAKANA", "HAN"
    );

    private static String detectDominantScript(String text) {
        if (text == null || text.isEmpty()) {
            return "LATIN";
        }
        Map<Character.UnicodeScript, Integer> counts = new HashMap<>();
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            Character.UnicodeScript s = Character.UnicodeScript.of(cp);
            if (s != Character.UnicodeScript.COMMON
                    && s != Character.UnicodeScript.INHERITED
                    && s != Character.UnicodeScript.UNKNOWN) {
                counts.merge(s, 1, Integer::sum);
            }
            i += Character.charCount(cp);
        }
        if (counts.isEmpty()) {
            return "LATIN";
        }
        String name = counts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(e -> e.getKey().name())
                .orElse("LATIN");
        return SCRIPT_FALLBACK.getOrDefault(name, name);
    }

    // -----------------------------------------------------------------------
    // Row
    // -----------------------------------------------------------------------

    private static final class Row {
        final StripMode stripMode;
        final String labeledCs;
        final String script;
        final String wrongCs;
        final int length;
        final int n;
        final double meanCleanZ;
        final double meanMojiZ;
        final double cohensD;
        final double meanMargin;
        final double p5Margin;
        final double p50Margin;
        final double fpr;
        final double tpr;

        Row(StripMode stripMode, String labeledCs, String script, String wrongCs, int length,
            List<Float> cleanZs, List<Float> mojiZs, List<Float> margins,
            float threshold) {
            this.stripMode = stripMode;
            this.labeledCs = labeledCs;
            this.script = script;
            this.wrongCs = wrongCs;
            this.length = length;
            this.n = margins.size();
            this.meanCleanZ = mean(cleanZs);
            this.meanMojiZ = mean(mojiZs);
            this.cohensD = computeCohensD(cleanZs, mojiZs);
            this.meanMargin = mean(margins);
            this.p5Margin = percentile(margins, 0.05);
            this.p50Margin = percentile(margins, 0.50);
            this.fpr = fractionBelow(cleanZs, threshold);
            this.tpr = fractionBelow(mojiZs, threshold);
        }

        String toTsv() {
            return String.format(
                    "%s\t%s\t%s\t%s\t%d\t%d\t%.3f\t%.3f\t%.3f\t%.3f\t%.3f\t%.3f\t%.3f\t%.3f",
                    stripMode, labeledCs, script, wrongCs, length, n,
                    meanCleanZ, meanMojiZ, cohensD,
                    meanMargin, p5Margin, p50Margin,
                    fpr, tpr);
        }
    }

    // -----------------------------------------------------------------------
    // Statistics
    // -----------------------------------------------------------------------

    private static double computeCohensD(List<Float> a, List<Float> b) {
        if (a.size() < 2 || b.size() < 2) {
            return Double.NaN;
        }
        double ma = mean(a);
        double mb = mean(b);
        double va = variance(a, ma);
        double vb = variance(b, mb);
        double pooled = Math.sqrt((va + vb) / 2.0);
        if (pooled < 1e-9) {
            return Double.NaN;
        }
        return (ma - mb) / pooled;
    }

    private static double mean(List<Float> xs) {
        double s = 0;
        int n = 0;
        for (float f : xs) {
            if (!Float.isNaN(f)) {
                s += f;
                n++;
            }
        }
        return n == 0 ? Double.NaN : s / n;
    }

    private static double variance(List<Float> xs, double m) {
        if (xs.size() < 2) {
            return 0;
        }
        double s = 0;
        int n = 0;
        for (float f : xs) {
            if (!Float.isNaN(f)) {
                double d = f - m;
                s += d * d;
                n++;
            }
        }
        return n < 2 ? 0 : s / (n - 1);
    }

    private static double percentile(List<Float> xs, double p) {
        List<Float> sorted = new ArrayList<>(xs);
        sorted.removeIf(f -> Float.isNaN(f));
        if (sorted.isEmpty()) {
            return Double.NaN;
        }
        Collections.sort(sorted);
        int idx = (int) Math.floor(p * (sorted.size() - 1));
        return sorted.get(idx);
    }

    private static double fractionBelow(List<Float> xs, float threshold) {
        int below = 0;
        int n = 0;
        for (float f : xs) {
            if (!Float.isNaN(f)) {
                if (f < threshold) {
                    below++;
                }
                n++;
            }
        }
        return n == 0 ? Double.NaN : (double) below / n;
    }

    // -----------------------------------------------------------------------

    private static void printUsage() {
        System.err.println("Usage:");
        System.err.println("  EvalJunkOnCharsetDevtest");
        System.err.println("    [--devtest-dir <path>]   (default ~/data/charsets/devtest)");
        System.err.println("    [--output-dir <path>]    (default /tmp/junkdetect-eval)");
        System.err.println("    [--model <path>]         (default classpath junkdetect.bin)");
        System.err.println("    [--max-records N]        (default 2000)");
        System.err.println("    [--threshold F]          (default -2.0)");
        System.err.println("    [--lengths 20,50,...]");
        System.err.println("    [--strip-modes NONE,WHITESPACE,WHITESPACE_CONTROL,ALL_COMMON]");
        System.err.println("    [--wrong-charsets a,b,...]  (override per-source map)");
        System.err.println("    [--no-per-source-map]    (use the global default list)");
        System.err.println("    [--only labeledCs,...]   (filter for spot runs)");
        System.err.println("    [--per-record <path>]    (write wide per-record TSV)");
        System.err.println("    [--per-record-max N]     (cap per cell, default 50)");
        System.err.println("    [--word-mode]            (use whitespace-delimited tokens, not byte slices)");
    }
}
