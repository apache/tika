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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CodingErrorAction;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.GZIPOutputStream;

import org.apache.tika.ml.chardetect.CharsetConfusables;

/**
 * Generates charset-detection training, devtest, and test data from MADLAD-400
 * and Cantonese Wikipedia sentence files.  Produces gzipped files of
 * {@code [uint16-BE length][raw bytes]} records, one file per charset per split.
 *
 * <p>This is the single authoritative data-generation tool; it replaces the
 * Python {@code build_charset_training.py} script entirely.  Java is used
 * because it supports charsets unavailable in CPython's standard codec
 * library — IBM1047 (EBCDIC Open Systems Latin-1), x-EUC-TW (Traditional
 * Chinese Unix), IBM420 (EBCDIC Arabic), and IBM424 (EBCDIC Hebrew) — and
 * because eliminating the Python/ebcdic/fastText dependency chain simplifies
 * the build.
 *
 * <p>Charset design decisions match the former Python generator:
 * <ul>
 *   <li><b>Windows superset policy</b>: windows-12XX trained instead of the
 *       ISO-8859-X equivalent wherever a superset exists.  ISO-8859-3 is
 *       retained (Maltese — no Windows equivalent).</li>
 *   <li><b>Superset-only</b>: Big5-HKSCS (not plain Big5), GB18030 (not
 *       GBK/GB2312), Shift_JIS via Java's CP932 superset.</li>
 *   <li><b>Structural-only charsets</b> (US-ASCII, ISO-2022-*): devtest/test
 *       files are generated for evaluation, but train is skipped because these
 *       charsets produce zero high bytes and provide no ML features.</li>
 *   <li><b>Unicode charsets</b> (UTF-8/16/32): applied to every language so
 *       the model sees diverse scripts in wide encodings.</li>
 *   <li><b>IBM1047</b>: EBCDIC Open Systems Latin-1, used on z/OS Unix System
 *       Services.  Trained on the same Western European languages as IBM500.
 *       Distinguished from IBM500 primarily by the byte position of '!' (0x5A
 *       in IBM1047 vs 0x4F in IBM500) and the line-terminator byte.</li>
 * </ul>
 *
 * <p>Usage:
 * <pre>
 *   java BuildCharsetTrainingData \
 *     --madlad-dir  ~/datasets/madlad/data \
 *     --zh-yue-file ~/datasets/zh_yuewiki/sentences_zh_yue.txt \
 *     --output-dir  ~/datasets/madlad/charset-detect3
 * </pre>
 */
public class BuildCharsetTrainingData {

    // -----------------------------------------------------------------------
    // Charset label → Java Charset name
    // -----------------------------------------------------------------------

    /**
     * Maps our canonical charset label to the Java {@link Charset} name.
     * RTL variants (IBM424-rtl, IBM420-rtl) share the same Java charset
     * as their ltr counterpart; text is reversed before encoding.
     */
    private static final Map<String, String> CHARSET_JAVA = new LinkedHashMap<>();
    static {
        // Unicode — applied to every language via UNICODE_CHARSETS
        CHARSET_JAVA.put("UTF-8",          "UTF-8");
        CHARSET_JAVA.put("UTF-16-LE",      "UTF-16LE");
        CHARSET_JAVA.put("UTF-16-BE",      "UTF-16BE");
        CHARSET_JAVA.put("UTF-32-LE",      "UTF-32LE");
        CHARSET_JAVA.put("UTF-32-BE",      "UTF-32BE");
        // Structural-only (no train file; detected by structural gates)
        CHARSET_JAVA.put("US-ASCII",       "US-ASCII");
        CHARSET_JAVA.put("ISO-2022-JP",    "ISO-2022-JP");
        CHARSET_JAVA.put("ISO-2022-KR",    "ISO-2022-KR");
        // ISO-2022-CN (aggregate) is decode-only in the JDK. Encode via the
        // GB plane (x-ISO-2022-CN-GB) which is what Python's iso2022_cn does.
        CHARSET_JAVA.put("ISO-2022-CN",    "x-ISO-2022-CN-GB");
        // Traditional Chinese ISO-2022, CNS 11643 plane.  Rare in practice
        // (superseded by Big5/UTF-8) but structurally identifiable by its
        // escape sequences. Sourced from Cantonese Wikipedia.
        CHARSET_JAVA.put("x-ISO-2022-CN-CNS", "x-ISO-2022-CN-CNS");
        // CJK
        // Java's Shift_JIS resolves to CP932 (Windows-31J superset of JIS X 0208)
        CHARSET_JAVA.put("Shift_JIS",      "Shift_JIS");
        CHARSET_JAVA.put("EUC-JP",         "EUC-JP");
        CHARSET_JAVA.put("EUC-KR",         "EUC-KR");
        CHARSET_JAVA.put("GB18030",        "GB18030");
        CHARSET_JAVA.put("Big5-HKSCS",     "Big5-HKSCS");
        CHARSET_JAVA.put("x-EUC-TW",      "x-EUC-TW");
        // Latin / Southern European
        CHARSET_JAVA.put("ISO-8859-3",     "ISO-8859-3");
        CHARSET_JAVA.put("ISO-8859-16",    "ISO-8859-16");
        // Windows single-byte (replaces ISO-8859-X equivalents)
        CHARSET_JAVA.put("windows-1250",   "windows-1250");
        CHARSET_JAVA.put("windows-1251",   "windows-1251");
        CHARSET_JAVA.put("windows-1252",   "windows-1252");
        CHARSET_JAVA.put("windows-1253",   "windows-1253");
        CHARSET_JAVA.put("windows-1254",   "windows-1254");
        CHARSET_JAVA.put("windows-1255",   "windows-1255");
        CHARSET_JAVA.put("windows-1256",   "windows-1256");
        CHARSET_JAVA.put("windows-1257",   "windows-1257");
        // windows-1258 (Vietnamese) requires NFD normalization before encoding
        CHARSET_JAVA.put("windows-1258",   "windows-1258");
        // Thai — windows-874 is the superset of TIS-620
        CHARSET_JAVA.put("windows-874",    "x-windows-874");
        // Cyrillic variants
        CHARSET_JAVA.put("KOI8-R",         "KOI8-R");
        CHARSET_JAVA.put("KOI8-U",         "KOI8-U");
        CHARSET_JAVA.put("IBM855",         "IBM855");
        CHARSET_JAVA.put("IBM866",         "IBM866");
        CHARSET_JAVA.put("x-mac-cyrillic", "x-MacCyrillic");
        // DOS codepages — IBM437 is excluded: its distinguishing bytes are
        // box-drawing/graphical characters that never appear in prose text,
        // making it indistinguishable from IBM850 on Wikipedia sentences.
        // IBM850 is the superset for Western European content and safely
        // decodes all prose-level IBM437 content.
        CHARSET_JAVA.put("IBM850",         "IBM850");
        CHARSET_JAVA.put("IBM852",         "IBM852");
        // Mac Roman
        CHARSET_JAVA.put("x-MacRoman",     "x-MacRoman");
        // EBCDIC
        CHARSET_JAVA.put("IBM500",         "IBM500");
        CHARSET_JAVA.put("IBM1047",        "IBM1047");
        CHARSET_JAVA.put("IBM424-ltr",     "IBM424");
        CHARSET_JAVA.put("IBM424-rtl",     "IBM424");
        CHARSET_JAVA.put("IBM420-ltr",     "IBM420");
        CHARSET_JAVA.put("IBM420-rtl",     "IBM420");
    }

    // -----------------------------------------------------------------------
    // Language → list of charset labels
    // -----------------------------------------------------------------------

    /**
     * ISO 639-3 language code → charset labels trained from that language's
     * MADLAD sentences.  Unicode charsets (UTF-8/16/32) are applied to every
     * language separately via {@link #UNICODE_CHARSETS} and are not listed here.
     * "yue" is a virtual key for Cantonese Wikipedia (Traditional Chinese).
     */
    private static final Map<String, List<String>> LANG_CHARSETS = new LinkedHashMap<>();
    static {
        // Western European
        // IBM500 and IBM1047 share the same Latin-1 character set;
        // IBM1047 is used on z/OS Unix System Services (Open Systems).
        put("eng", "US-ASCII", "windows-1252", "IBM850", "x-MacRoman",
                   "IBM500", "IBM1047");
        put("deu", "windows-1252", "IBM850", "x-MacRoman", "IBM500", "IBM1047");
        put("fra", "windows-1252", "IBM850", "x-MacRoman", "IBM500", "IBM1047");
        put("ita", "windows-1252", "IBM850", "x-MacRoman", "IBM500", "IBM1047");
        put("spa", "windows-1252", "IBM850", "x-MacRoman", "IBM500", "IBM1047");
        put("nld", "windows-1252", "IBM850", "x-MacRoman", "IBM500", "IBM1047");
        put("por", "windows-1252", "IBM850", "x-MacRoman");
        put("dan", "windows-1252", "IBM850", "x-MacRoman");
        put("swe", "windows-1252", "IBM850", "x-MacRoman");
        put("nob", "windows-1252", "IBM850", "x-MacRoman");
        put("fin", "windows-1252", "IBM850", "x-MacRoman");
        put("isl", "windows-1252", "IBM850", "x-MacRoman");
        put("cat", "windows-1252", "IBM850");
        put("glg", "windows-1252", "IBM850");
        put("eus", "windows-1252");
        put("afr", "windows-1252");
        put("swh", "windows-1252");
        put("ind", "windows-1252");
        put("msa", "windows-1252");
        // Baltic
        put("lav", "windows-1257");
        put("lit", "windows-1257");
        put("est", "windows-1257");
        // Southern European — ISO-8859-3 retained for Maltese (no Windows equivalent)
        put("mlt", "ISO-8859-3");
        put("tur", "windows-1254");
        // Central / Eastern European
        put("ces", "windows-1250", "IBM852");
        put("pol", "windows-1250", "IBM852");
        put("hrv", "windows-1250", "IBM852");
        put("slk", "windows-1250", "IBM852");
        put("slv", "windows-1250", "IBM852");
        put("hun", "windows-1250", "IBM852");
        // ISO-8859-16 (Latin-10) retained for Romanian and Albanian
        put("ron", "windows-1250", "IBM852", "ISO-8859-16");
        put("bos", "windows-1250", "IBM852");
        put("sqi", "windows-1250", "IBM852", "ISO-8859-16");
        // Cyrillic — keep all distinct encodings
        put("rus", "windows-1251", "KOI8-R", "IBM855", "IBM866", "x-mac-cyrillic");
        put("ukr", "windows-1251", "KOI8-U", "IBM855", "x-mac-cyrillic");
        put("bul", "windows-1251", "IBM855", "x-mac-cyrillic");
        put("bel", "windows-1251", "IBM855");
        put("mkd", "windows-1251");
        put("srp", "windows-1251");
        // Arabic
        put("ara", "windows-1256", "IBM420-ltr", "IBM420-rtl");
        put("urd", "windows-1256");
        put("fas", "windows-1256");
        put("pus", "windows-1256");
        // Hebrew
        put("heb", "windows-1255", "IBM424-ltr", "IBM424-rtl");
        // Greek
        put("ell", "windows-1253");
        // Vietnamese — windows-1258 requires NFD normalization before encoding
        put("vie", "windows-1258");
        // Japanese
        put("jpn", "Shift_JIS", "EUC-JP", "ISO-2022-JP");
        // Chinese (Simplified)
        put("zho", "GB18030", "ISO-2022-CN");
        // Korean
        put("kor", "EUC-KR", "ISO-2022-KR");
        // Thai
        put("tha", "windows-874");
        // Traditional Chinese — sourced from Cantonese Wikipedia (zh_yuewiki).
        // "yue" is a virtual language key; handled specially in sentence loading.
        // x-ISO-2022-CN-CNS (CNS 11643 plane) is structural-only; included
        // for eval coverage alongside Big5-HKSCS and x-EUC-TW.
        put("yue", "Big5-HKSCS", "x-EUC-TW", "x-ISO-2022-CN-CNS");
    }

    private static void put(String lang, String... charsets) {
        LANG_CHARSETS.put(lang, Arrays.asList(charsets));
    }

    // -----------------------------------------------------------------------
    // Charset category sets
    // -----------------------------------------------------------------------

    /**
     * Unicode charsets applied to every language in {@link #LANG_CHARSETS}.
     * UTF-16/32 are trained in the ML model using stride-2 bigram features.
     */
    private static final Set<String> UNICODE_CHARSETS = new HashSet<>(Arrays.asList(
            "UTF-8", "UTF-16-LE", "UTF-16-BE", "UTF-32-LE", "UTF-32-BE"
    ));

    /**
     * Charsets whose encoded bytes are all {@literal <} 0x80, so the ML model
     * would see zero features.  Only devtest/test files are generated; train
     * is skipped.  These charsets are detected by structural gates in
     * {@code MojibusterEncodingDetector} before the model is ever called.
     */
    private static final Set<String> STRUCTURAL_ONLY = new HashSet<>(Arrays.asList(
            "US-ASCII", "ISO-2022-JP", "ISO-2022-KR", "ISO-2022-CN", "x-ISO-2022-CN-CNS"
    ));

    /**
     * Charsets exempt from the high-byte ratio gate.  UTF-16/32 have a
     * variable mix of zero and non-zero bytes depending on script; applying
     * a fixed ratio threshold would reject valid samples.
     */
    private static final Set<String> HIGH_BYTE_EXEMPT = new HashSet<>(Arrays.asList(
            "US-ASCII",
            "UTF-16-LE", "UTF-16-BE", "UTF-32-LE", "UTF-32-BE",
            "ISO-2022-JP", "ISO-2022-KR", "ISO-2022-CN", "x-ISO-2022-CN-CNS"
    ));

    /**
     * Multi-byte CJK charsets that must have {@literal ≥} 20% high bytes to
     * confirm the text actually uses the CJK character set and is not mostly
     * ASCII-range characters.
     */
    private static final Set<String> HIGH_BYTE_CJK = new HashSet<>(Arrays.asList(
            "Shift_JIS", "EUC-JP", "EUC-KR", "GB18030", "Big5-HKSCS", "x-EUC-TW"
    ));

    /** RTL charsets: text is reversed (character level) before encoding. */
    private static final Set<String> RTL_CHARSETS = new HashSet<>(Arrays.asList(
            "IBM424-rtl", "IBM420-rtl"
    ));

    /**
     * Charsets that require NFD normalization before encoding and NFC
     * recomposition after decoding for a fair drop-count comparison.
     * windows-1258 (Vietnamese) uses combining diacritical marks.
     */
    private static final Set<String> NFD_CHARSETS = new HashSet<>(Arrays.asList(
            "windows-1258"
    ));

    /**
     * IBM420 (Arabic EBCDIC) charsets: Arabic combining diacritics and
     * certain alef variants must be stripped/normalised before encoding
     * because mainframe Arabic text was written without harakat.
     */
    private static final Set<String> IBM420_CHARSETS = new HashSet<>(Arrays.asList(
            "IBM420-ltr", "IBM420-rtl"
    ));

    /**
     * Arabic combining-mark codepoint ranges stripped for IBM420.
     * Each element is {lo, hi} inclusive.
     */
    private static final int[][] ARABIC_DIACRITIC_RANGES = {
        {0x0610, 0x061A},   // Arabic extended combining (salat, etc.)
        {0x064B, 0x065F},   // Harakat (fatha, damma, kasra, shadda, etc.)
        {0x06D6, 0x06E4},   // Quranic annotation signs (combining)
        {0x06E7, 0x06E8},   // Combining above / below
        {0x06EA, 0x06ED},   // Combining letters
    };

    // -----------------------------------------------------------------------
    // High-byte ratio thresholds
    // -----------------------------------------------------------------------

    private static final double MIN_HIGH_UTF8 = 0.05;
    private static final double MIN_HIGH_CJK  = 0.20;
    private static final double MIN_HIGH_SBCS = 0.02;

    // -----------------------------------------------------------------------
    // Quality gate
    // -----------------------------------------------------------------------

    /**
     * Max characters that may be dropped (unencodable) or corrupt (U+FFFD on
     * decode) before a sentence is rejected.  Allows one or two typographic
     * characters (curly quotes, em-dash) without discarding the whole sentence.
     */
    private static final int MAX_DROPPED_CHARS = 3;

    // -----------------------------------------------------------------------
    // Configuration defaults
    // -----------------------------------------------------------------------

    /**
     * CJK and Unicode charsets saturate quickly — their byte patterns are so
     * distinctive that a linear model converges with far fewer samples than SBCS.
     */
    private static final int DEFAULT_TRAIN_CAP       = 20_000;
    private static final int DEFAULT_DEVTEST_CAP     =  2_000;
    private static final int DEFAULT_TEST_CAP        =  5_000;

    /**
     * SBCS/EBCDIC charsets share linguistic content and differ only in how they
     * map the 0x80–0xFF range.  The harder confusable pairs (IBM500 vs IBM1047,
     * windows-1252 vs IBM850 vs x-MacRoman) need more samples for the linear
     * model to lock onto subtle byte-frequency differences.
     */
    private static final int DEFAULT_SBCS_TRAIN_CAP  = 50_000;
    private static final int DEFAULT_SBCS_TEST_CAP   = 10_000;

    private static final int DEFAULT_MIN_CHUNK       =     64;
    private static final int DEFAULT_MAX_CHUNK       =  1_024;
    private static final int DEFAULT_SEED            =     42;
    private static final int DEFAULT_MAX_SOURCE_LANG = 200_000;

    // -----------------------------------------------------------------------
    // Main
    // -----------------------------------------------------------------------

    public static void main(String[] args) throws IOException {
        Path madladDir       = Paths.get(System.getProperty("user.home"),
                                         "datasets", "madlad", "data");
        Path zhYueFile       = Paths.get(System.getProperty("user.home"),
                                         "datasets", "zh_yuewiki", "sentences_zh_yue.txt");
        Path outputDir       = Paths.get(System.getProperty("user.home"),
                                         "datasets", "madlad", "charset-detect3");
        Set<String> requested = new HashSet<>(CHARSET_JAVA.keySet());
        int trainCap         = DEFAULT_TRAIN_CAP;
        int sbcsTrainCap     = DEFAULT_SBCS_TRAIN_CAP;
        int devtestCap       = DEFAULT_DEVTEST_CAP;
        int testCap          = DEFAULT_TEST_CAP;
        int sbcsTestCap      = DEFAULT_SBCS_TEST_CAP;
        int minChunk         = DEFAULT_MIN_CHUNK;
        int maxChunk         = DEFAULT_MAX_CHUNK;
        int seed             = DEFAULT_SEED;
        int maxSourcePerLang = DEFAULT_MAX_SOURCE_LANG;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--madlad-dir":
                    madladDir = Paths.get(args[++i]);
                    break;
                case "--zh-yue-file":
                    zhYueFile = Paths.get(args[++i]);
                    break;
                case "--output-dir":
                    outputDir = Paths.get(args[++i]);
                    break;
                case "--charsets":
                    requested = new HashSet<>(Arrays.asList(args[++i].split(",")));
                    break;
                case "--train-cap":
                    trainCap = Integer.parseInt(args[++i]);
                    break;
                case "--sbcs-train-cap":
                    sbcsTrainCap = Integer.parseInt(args[++i]);
                    break;
                case "--devtest-cap":
                    devtestCap = Integer.parseInt(args[++i]);
                    break;
                case "--test-cap":
                    testCap = Integer.parseInt(args[++i]);
                    break;
                case "--sbcs-test-cap":
                    sbcsTestCap = Integer.parseInt(args[++i]);
                    break;
                case "--min-chunk":
                    minChunk = Integer.parseInt(args[++i]);
                    break;
                case "--max-chunk":
                    maxChunk = Integer.parseInt(args[++i]);
                    break;
                case "--seed":
                    seed = Integer.parseInt(args[++i]);
                    break;
                case "--max-source-per-lang":
                    maxSourcePerLang = Integer.parseInt(args[++i]);
                    break;
                default:
                    System.err.println("Unknown argument: " + args[i]);
                    printUsage();
                    System.exit(1);
            }
        }

        // Validate and probe charset availability
        List<String> targetCharsets = new ArrayList<>();
        for (String label : CHARSET_JAVA.keySet()) {
            if (!requested.contains(label)) {
                continue;
            }
            String javaName = CHARSET_JAVA.get(label);
            try {
                Charset cs = Charset.forName(javaName);
                // Verify the charset has an encoder — some JDK charsets
                // (e.g. ISO-2022-CN) are decode-only and throw
                // UnsupportedOperationException from newEncoder().
                cs.newEncoder();
                targetCharsets.add(label);
            } catch (UnsupportedOperationException e) {
                System.err.println("WARNING: " + javaName
                        + " is decode-only in this JVM — skipping " + label);
            } catch (Exception e) {
                System.err.println("WARNING: Java charset not available: "
                        + javaName + " — skipping " + label);
            }
        }
        // Report any requested charsets that are unknown
        for (String label : requested) {
            if (!CHARSET_JAVA.containsKey(label)) {
                System.err.println("WARNING: unknown charset label: " + label);
            }
        }

        System.out.println("=== BuildCharsetTrainingData ===");
        System.out.println("  madlad-dir:          " + madladDir);
        System.out.println("  zh-yue-file:         " + zhYueFile);
        System.out.println("  output-dir:          " + outputDir);
        System.out.printf ("  caps (CJK/Unicode):  train=%,d  devtest=%,d  test=%,d%n",
                           trainCap, devtestCap, testCap);
        System.out.printf ("  caps (SBCS/EBCDIC):  train=%,d  devtest=%,d  test=%,d%n",
                           sbcsTrainCap, devtestCap, sbcsTestCap);
        System.out.printf ("  chunk:               %d–%d bytes  seed=%d%n", minChunk, maxChunk, seed);
        System.out.printf ("  max-source-per-lang: %,d%n", maxSourcePerLang);
        System.out.printf ("  charsets:            %d%n%n", targetCharsets.size());

        // Ambiguity gate: for each SBCS charset, precompute encoders for all
        // other SBCS charsets. A chunk is dropped if any rival produces
        // byte-for-byte identical output — such chunks carry no discriminative
        // signal and actively confuse the model.
        Map<String, List<CharsetEncoder>> sbcsRivals = buildSbcsRivals(targetCharsets);
        System.out.printf("  ambiguity-gate:      %d SBCS charsets compared pairwise%n%n",
                sbcsRivals.size());

        // charset label → split name → sample count (for manifest)
        Map<String, Map<String, Integer>> manifest = new TreeMap<>();

        for (String label : targetCharsets) {
            String javaName = CHARSET_JAVA.get(label);
            Charset cs      = Charset.forName(javaName);
            boolean structOnly = STRUCTURAL_ONLY.contains(label);
            boolean isSbcs = !UNICODE_CHARSETS.contains(label)
                    && !HIGH_BYTE_CJK.contains(label)
                    && !structOnly;
            int effectiveTrainCap = isSbcs ? sbcsTrainCap : trainCap;
            int effectiveTestCap  = isSbcs ? sbcsTestCap  : testCap;
            String[] splits    = {"train", "devtest", "test"};
            int[]    splitCaps = {effectiveTrainCap, devtestCap, effectiveTestCap};
            System.out.printf("%s  (%s)%s%n", label, javaName,
                    structOnly ? "  [structural-only: skipping train]" : "");

            // Determine contributing languages
            boolean isUnicode = UNICODE_CHARSETS.contains(label);
            List<String> langs = new ArrayList<>();
            for (Map.Entry<String, List<String>> e : LANG_CHARSETS.entrySet()) {
                if (isUnicode || e.getValue().contains(label)) {
                    langs.add(e.getKey());
                }
            }

            // Per-language load cap: scale down when many languages contribute
            int nLangs  = Math.max(1, langs.size());
            int loadCap = Math.min(maxSourcePerLang,
                                   Math.max(5_000, (effectiveTrainCap * 10) / nLangs));

            // Load sentences per language, then combine and split 80/10/10
            List<String> allSentences = new ArrayList<>();
            for (String lang : langs) {
                List<String> sents;
                if ("yue".equals(lang)) {
                    sents = loadPlaintextSentences(zhYueFile, loadCap);
                } else {
                    Path langDir = madladDir.resolve(lang);
                    sents = loadMadladSentences(langDir, loadCap);
                }
                System.out.printf("    %s: %,d sentences%n", lang, sents.size());
                allSentences.addAll(sents);
            }

            if (allSentences.isEmpty()) {
                System.out.println("    WARNING: no sentences found — skipping");
                continue;
            }

            // Shuffle once with fixed seed, then split 80 / 10 / 10
            Collections.shuffle(allSentences, new Random(seed));
            int n        = allSentences.size();
            int nTrain   = (int) (n * 0.80);
            int nDevtest = (n - nTrain) / 2;
            Map<String, List<String>> splitSents = new HashMap<>();
            splitSents.put("train",   allSentences.subList(0, nTrain));
            splitSents.put("devtest", allSentences.subList(nTrain, nTrain + nDevtest));
            splitSents.put("test",    allSentences.subList(nTrain + nDevtest, n));

            Map<String, Integer> splitCounts = new TreeMap<>();
            for (int si = 0; si < splits.length; si++) {
                String split = splits[si];
                int cap      = splitCaps[si];

                if (structOnly && "train".equals(split)) {
                    splitCounts.put(split, 0);
                    continue;
                }

                List<String> sents = new ArrayList<>(splitSents.get(split));
                Collections.shuffle(sents,
                        new Random(seed + label.hashCode() + split.hashCode()));

                Path outFile = outputDir.resolve(split).resolve(label + ".bin.gz");
                Files.createDirectories(outFile.getParent());

                List<CharsetEncoder> rivals =
                        sbcsRivals.getOrDefault(label, Collections.emptyList());
                int[] result = writeSamples(sents, cs, label, outFile,
                                            cap, minChunk, maxChunk,
                                            new Random(seed + label.hashCode()), rivals);
                int written          = result[0];
                int ambiguousDropped = result[1];
                splitCounts.put(split, written);
                if (ambiguousDropped > 0) {
                    System.out.printf("    %s: %,d samples  (%,d ambiguous-dropped)%n",
                            split, written, ambiguousDropped);
                } else {
                    System.out.printf("    %s: %,d samples%n", split, written);
                }
            }
            manifest.put(label, splitCounts);
        }

        writeManifest(outputDir, manifest);
        System.out.println("\nDone.");
    }

    // -----------------------------------------------------------------------
    // Ambiguity gate helpers
    // -----------------------------------------------------------------------

    /**
     * Build the SBCS ambiguity-gate rivals map.
     *
     * <p>For every SBCS charset label (non-CJK, non-Unicode, non-structural),
     * produces a list of {@link CharsetEncoder}s for all <em>other</em> SBCS
     * charsets in the target set.  CJK and Unicode charsets are excluded: their
     * byte ranges are so different that cross-encoding would never match, making
     * the check useless overhead.
     */
    private static Map<String, List<CharsetEncoder>> buildSbcsRivals(
            List<String> targetCharsets) {
        // Collect SBCS labels in encounter order
        Map<String, Charset> sbcsMap = new LinkedHashMap<>();
        for (String label : targetCharsets) {
            if (!CHARSET_JAVA.containsKey(label))       continue;
            if (UNICODE_CHARSETS.contains(label))        continue;
            if (HIGH_BYTE_CJK.contains(label))           continue;
            if (STRUCTURAL_ONLY.contains(label))         continue;
            sbcsMap.put(label, Charset.forName(CHARSET_JAVA.get(label)));
        }
        Map<String, List<CharsetEncoder>> result = new LinkedHashMap<>();
        for (String label : sbcsMap.keySet()) {
            // Exclude self and confusable peers: comparing against an already-
            // declared confusable partner would drop valid training samples
            // (e.g., KOI8-R vs KOI8-U for pure-Russian text, IBM500 vs IBM1047
            // for most prose, IBM424-ltr vs IBM424-rtl which are byte-identical).
            // The ambiguity gate is only useful between pairs that are NOT yet
            // acknowledged confusables — e.g., windows-1250 vs windows-1252.
            Set<String> excluded = new HashSet<>(
                    CharsetConfusables.symmetricPeersOf(label));
            excluded.add(label);
            List<CharsetEncoder> rivals = new ArrayList<>();
            for (Map.Entry<String, Charset> e : sbcsMap.entrySet()) {
                if (!excluded.contains(e.getKey())) {
                    rivals.add(e.getValue().newEncoder()
                            .onMalformedInput(CodingErrorAction.IGNORE)
                            .onUnmappableCharacter(CodingErrorAction.IGNORE));
                }
            }
            result.put(label, rivals);
        }
        return result;
    }

    // -----------------------------------------------------------------------
    // Sentence loading
    // -----------------------------------------------------------------------

    /**
     * Load sentences from a MADLAD {@code sentences_madlad.txt} file.
     * Format: {@code lineNum TAB text} (tab-separated, or just text).
     */
    private static List<String> loadMadladSentences(Path langDir, int max) {
        Path txt = langDir.resolve("sentences_madlad.txt");
        if (!Files.exists(txt)) {
            return Collections.emptyList();
        }
        List<String> result = new ArrayList<>();
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(Files.newInputStream(txt),
                                      java.nio.charset.StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null && result.size() < max) {
                int tab = line.indexOf('\t');
                String text = (tab >= 0) ? line.substring(tab + 1) : line;
                text = cleanMadladText(text);
                if (!text.isEmpty()) {
                    result.add(text);
                }
            }
        } catch (IOException e) {
            System.err.println("WARNING: could not read " + txt + ": " + e.getMessage());
        }
        return result;
    }

    /**
     * Load sentences from a plain-text file (one sentence per line).
     * Used for Cantonese Wikipedia (zh_yuewiki).
     */
    private static List<String> loadPlaintextSentences(Path file, int max) {
        if (!Files.exists(file)) {
            System.err.println("WARNING: not found: " + file);
            return Collections.emptyList();
        }
        List<String> result = new ArrayList<>();
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(Files.newInputStream(file),
                                      java.nio.charset.StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null && result.size() < max) {
                line = line.strip();
                if (!line.isEmpty()) {
                    result.add(line);
                }
            }
        } catch (IOException e) {
            System.err.println("WARNING: could not read " + file + ": " + e.getMessage());
        }
        return result;
    }

    /**
     * Strip BOM, replace embedded literal {@code \n}/{\code \r}/{\code \t}
     * escape sequences (two-character sequences in MADLAD source), and
     * collapse whitespace.
     */
    private static String cleanMadladText(String text) {
        text = text.replace("\ufeff", "");
        text = text.replace("\\n", " ").replace("\\r", "").replace("\\t", " ");
        return text.strip().replaceAll("\\s+", " ");
    }

    // -----------------------------------------------------------------------
    // Encoding and output
    // -----------------------------------------------------------------------

    /**
     * Encode sentences and write up to {@code cap} samples to a gzipped binary
     * file in {@code [uint16-BE length][raw bytes]} format.
     *
     * @param rivals other SBCS {@link CharsetEncoder}s to check for byte-level
     *               identity (the ambiguity gate); empty for CJK/Unicode charsets
     * @return {@code int[]}{written, ambiguousDropped}
     */
    private static int[] writeSamples(List<String> sentences, Charset cs, String charset,
                                      Path outFile, int cap, int minChunk, int maxChunk,
                                      Random rng, List<CharsetEncoder> rivals)
            throws IOException {
        int written          = 0;
        int ambiguousDropped = 0;
        try (DataOutputStream out = new DataOutputStream(
                new GZIPOutputStream(Files.newOutputStream(outFile)))) {
            for (String sent : sentences) {
                if (written >= cap) {
                    break;
                }
                int target = minChunk + rng.nextInt(maxChunk - minChunk + 1);
                byte[] chunk = encodeChunk(sent, cs, charset, target);
                if (chunk == null) {
                    continue;
                }
                if (!rivals.isEmpty() && isAmbiguous(sent, chunk, rivals)) {
                    ambiguousDropped++;
                    continue;
                }
                out.writeShort(chunk.length);
                out.write(chunk);
                written++;
            }
        }
        return new int[]{written, ambiguousDropped};
    }

    /**
     * Returns {@code true} if encoding {@code sent} through any rival charset
     * produces a byte array that is byte-for-byte identical to {@code chunk}.
     * Such a sample carries no discriminative signal — the model cannot
     * distinguish the two charsets from this input.
     *
     * <p>Each encoder in {@code rivals} is {@link CharsetEncoder#reset() reset}
     * before use; this method is safe to call repeatedly on the same encoder
     * list as long as calls are sequential (single-threaded).
     */
    private static boolean isAmbiguous(String sent, byte[] chunk,
                                       List<CharsetEncoder> rivals) {
        for (CharsetEncoder rival : rivals) {
            rival.reset();
            try {
                ByteBuffer bb = rival.encode(CharBuffer.wrap(sent));
                int len = bb.limit();
                // Trim rival to the same length as chunk (matching encodeChunk's trim)
                int cmpLen = Math.min(len, chunk.length);
                if (cmpLen != chunk.length) {
                    // Different byte length after encoding → not identical
                    continue;
                }
                byte[] rivalBytes = new byte[cmpLen];
                bb.get(rivalBytes);
                if (Arrays.equals(chunk, rivalBytes)) {
                    return true;
                }
            } catch (Exception e) {
                // Rival encoder failed — not a match
            }
        }
        return false;
    }

    /**
     * Prepare text for encoding by applying charset-specific transformations:
     * <ol>
     *   <li>Strip U+FEFF (BOM must never reach the model).</li>
     *   <li>RTL: reverse character order (IBM424-rtl, IBM420-rtl).</li>
     *   <li>IBM420: strip Arabic diacritics and normalise alef variants.</li>
     * </ol>
     * NFD normalization (windows-1258) is handled separately in
     * {@link #encodeChunk} because it must be applied after the drop-count
     * comparison baseline is established.
     */
    private static String prepareText(String text, String charset) {
        text = text.replace("\ufeff", "");
        if (RTL_CHARSETS.contains(charset)) {
            text = new StringBuilder(text).reverse().toString();
        }
        if (IBM420_CHARSETS.contains(charset)) {
            text = prepareForIbm420(text);
        }
        return text;
    }

    /**
     * Strip Arabic combining diacritics (harakat, tashkeel, quranic marks)
     * and normalise alef variants (U+0625 إ, U+0671 ٱ) to plain alef (U+0627 ا).
     *
     * <p>IBM420 only encodes the 28 basic Arabic letters; mainframe Arabic
     * text was written without combining diacritics, so stripping them
     * produces valid training samples that match real-world IBM420 files.
     */
    private static String prepareForIbm420(String text) {
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c  = text.charAt(i);
            int  cp = c;
            boolean diacritic = false;
            for (int[] range : ARABIC_DIACRITIC_RANGES) {
                if (cp >= range[0] && cp <= range[1]) {
                    diacritic = true;
                    break;
                }
            }
            if (diacritic) {
                continue;
            }
            if (c == '\u0625' || c == '\u0671') {
                sb.append('\u0627');
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * Encode {@code text} using {@code cs} and apply quality gates.
     *
     * <p>Quality gates (matching the Python generator):
     * <ol>
     *   <li>Prepare text (BOM strip, RTL reversal, IBM420 diacritic strip).</li>
     *   <li>NFD normalize for windows-1258 (Vietnamese).</li>
     *   <li>Encode with IGNORE — unencodable characters are silently dropped.</li>
     *   <li>Decode back with REPLACE — corrupt sequences become U+FFFD.</li>
     *   <li>Reject if (dropped + corrupt) {@literal >} {@link #MAX_DROPPED_CHARS}.</li>
     *   <li>Trim to {@code targetBytes}.</li>
     *   <li>Reject if high-byte ratio is below threshold for the encoding family.</li>
     * </ol>
     *
     * @return encoded bytes, or {@code null} if any gate rejects the sample
     */
    private static byte[] encodeChunk(String text, Charset cs, String charset,
                                      int targetBytes) {
        text = prepareText(text, charset);
        if (text.isEmpty()) {
            return null;
        }

        // Baseline for drop-count (NFC; after RTL + IBM420 prep but before NFD)
        String sourceText = text;

        // windows-1258 (Vietnamese) uses combining diacritical marks (NFD)
        String encText = NFD_CHARSETS.contains(charset)
                ? Normalizer.normalize(text, Normalizer.Form.NFD)
                : text;

        // Encode with IGNORE: unencodable chars are dropped, not replaced.
        // This matches Python's errors='ignore' so the drop-count is accurate.
        CharsetEncoder encoder = cs.newEncoder()
                .onMalformedInput(CodingErrorAction.IGNORE)
                .onUnmappableCharacter(CodingErrorAction.IGNORE);
        byte[] encoded;
        try {
            ByteBuffer bb = encoder.encode(CharBuffer.wrap(encText));
            encoded = new byte[bb.limit()];
            bb.get(encoded);
        } catch (Exception e) {
            return null;
        }
        if (encoded.length == 0) {
            return null;
        }

        // Decode back with REPLACE: corrupt sequences become U+FFFD
        String decoded;
        try {
            CharsetDecoder decoder = cs.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPLACE)
                    .onUnmappableCharacter(CodingErrorAction.REPLACE);
            decoded = decoder.decode(ByteBuffer.wrap(encoded)).toString();
        } catch (Exception e) {
            return null;
        }
        // NFC recompose for fair length comparison with NFD charsets
        if (NFD_CHARSETS.contains(charset)) {
            decoded = Normalizer.normalize(decoded, Normalizer.Form.NFC);
        }

        int dropped = sourceText.length() - decoded.length();
        int corrupt = countChar(decoded, '\ufffd');
        if (dropped + corrupt > MAX_DROPPED_CHARS) {
            return null;
        }

        // Trim to target length
        byte[] chunk = (encoded.length > targetBytes)
                ? Arrays.copyOf(encoded, targetBytes)
                : encoded;

        // High-byte ratio gate
        if (!HIGH_BYTE_EXEMPT.contains(charset)) {
            int highBytes = 0;
            for (byte b : chunk) {
                if ((b & 0xFF) >= 0x80) {
                    highBytes++;
                }
            }
            double ratio = (double) highBytes / chunk.length;
            double minRatio;
            if ("UTF-8".equals(charset)) {
                minRatio = MIN_HIGH_UTF8;
            } else if (HIGH_BYTE_CJK.contains(charset)) {
                minRatio = MIN_HIGH_CJK;
            } else {
                minRatio = MIN_HIGH_SBCS;
            }
            if (ratio < minRatio) {
                return null;
            }
        }

        return chunk;
    }

    private static int countChar(String s, char c) {
        int count = 0;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == c) {
                count++;
            }
        }
        return count;
    }

    // -----------------------------------------------------------------------
    // Manifest
    // -----------------------------------------------------------------------

    private static void writeManifest(Path outputDir,
                                      Map<String, Map<String, Integer>> manifest)
            throws IOException {
        StringBuilder sb = new StringBuilder("{\n");
        boolean firstCs = true;
        for (Map.Entry<String, Map<String, Integer>> e : manifest.entrySet()) {
            if (!firstCs) {
                sb.append(",\n");
            }
            firstCs = false;
            String cs = e.getKey();
            Map<String, Integer> samples = e.getValue();
            sb.append("  \"").append(cs).append("\": {\n");
            sb.append("    \"java_charset\": \"")
              .append(CHARSET_JAVA.getOrDefault(cs, cs)).append("\",\n");
            sb.append("    \"structural_only\": ")
              .append(STRUCTURAL_ONLY.contains(cs)).append(",\n");
            sb.append("    \"samples\": {\n");
            sb.append("      \"train\": ").append(samples.getOrDefault("train", 0)).append(",\n");
            sb.append("      \"devtest\": ")
              .append(samples.getOrDefault("devtest", 0)).append(",\n");
            sb.append("      \"test\": ").append(samples.getOrDefault("test", 0)).append("\n");
            sb.append("    }\n");
            sb.append("  }");
        }
        sb.append("\n}\n");

        Files.createDirectories(outputDir);
        Path path = outputDir.resolve("manifest.json");
        try (BufferedWriter w = Files.newBufferedWriter(path)) {
            w.write(sb.toString());
        }
        System.out.println("Manifest: " + path);
    }

    // -----------------------------------------------------------------------
    // Usage
    // -----------------------------------------------------------------------

    private static void printUsage() {
        System.err.println("Usage: BuildCharsetTrainingData [options]");
        System.err.println("  --madlad-dir          <path>  MADLAD data dir (default: ~/datasets/madlad/data)");
        System.err.println("  --zh-yue-file         <path>  Cantonese Wikipedia sentences");
        System.err.println("  --output-dir          <path>  Output dir (default: ~/datasets/madlad/charset-detect3)");
        System.err.println("  --charsets            cs1,cs2 Comma-separated charset subset");
        System.err.println("  --train-cap           N       Max train samples for CJK/Unicode charsets (default: 20000)");
        System.err.println("  --sbcs-train-cap      N       Max train samples for SBCS/EBCDIC charsets (default: 50000)");
        System.err.println("  --devtest-cap         N       Max devtest samples, all charsets (default: 2000)");
        System.err.println("  --test-cap            N       Max test samples for CJK/Unicode charsets (default: 5000)");
        System.err.println("  --sbcs-test-cap       N       Max test samples for SBCS/EBCDIC charsets (default: 10000)");
        System.err.println("  --min-chunk           N       Min encoded bytes per sample (default: 64)");
        System.err.println("  --max-chunk           N       Max encoded bytes per sample (default: 1024)");
        System.err.println("  --seed                N       Random seed (default: 42)");
        System.err.println("  --max-source-per-lang N       Max source sentences per language (default: 200000)");
        System.err.println();
        System.err.println("Supported charsets (" + CHARSET_JAVA.size() + "):");
        for (String label : CHARSET_JAVA.keySet()) {
            System.err.println("  " + label);
        }
    }
}
