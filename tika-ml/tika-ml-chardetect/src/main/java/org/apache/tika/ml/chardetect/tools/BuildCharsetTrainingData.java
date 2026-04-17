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
 *     --output-dir  ~/datasets/madlad/charset-detect4
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
        CHARSET_JAVA.put("x-windows-949",  "x-windows-949");
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
        // EBCDIC — all variants are generated into the training corpus so a future
        // EBCDIC specialist can be trained against them.  Today's main SBCS model
        // consumes only a subset of these (see TrainCharsetModel's hardcoded
        // exclusion list): IBM424 (Hebrew) and IBM420 (Arabic) live entirely in
        // the 0x41–0x6A range, below the 0x80 threshold our feature extractor
        // considers, so excluding them from today's model avoids training on a
        // signal the inference path cannot see; IBM1047 is byte-identical to
        // IBM500 on most prose bytes and is excluded to avoid near-duplicate
        // classes in the SBCS kitchen-sink model.
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
        // Korean — x-windows-949 (MS949) is a strict superset of EUC-KR.
        // Trained as a separate class so the model can discriminate MS949-
        // extension-byte content from pure-EUC-KR content.  Supersets at the
        // decoder level (CharsetSupersets) decode EUC-KR output as MS949 anyway.
        put("kor", "EUC-KR", "ISO-2022-KR", "x-windows-949");
        // Thai
        put("tha", "windows-874");
        // Traditional Chinese — sourced from Cantonese Wikipedia (yue)
        // and Chinese Wikipedia (zho-trad, which stores Traditional).
        // Both are loaded from sentences_wikipedia.txt in their MADLAD dirs.
        // x-ISO-2022-CN-CNS (CNS 11643 plane) is structural-only; included
        // for eval coverage alongside Big5-HKSCS and x-EUC-TW.
        put("yue", "Big5-HKSCS", "x-EUC-TW", "x-ISO-2022-CN-CNS");
        put("zho-trad", "Big5-HKSCS", "x-EUC-TW");
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
     * Charsets that must not appear in train, devtest, or test splits either
     * because they are confusable aliases for a trained label (IBM437 → IBM850)
     * or because they are structural-only charsets whose test data was generated
     * before the structural-only category was established (x-ISO-2022-CN-CNS).
     * The eval tool mirrors this set via {@code DEFAULT_EXCLUDE} so neither
     * charset produces misleading 0% strict rows.
     */
    private static final Set<String> CONFUSABLE_ALIAS = new HashSet<>(Arrays.asList(
            "IBM437",           // box-drawing bytes never appear in prose; IBM850 is the trained label
            "x-ISO-2022-CN-CNS" // structural-only; detected by ISO-2022 escape gates, not the model
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
            "Shift_JIS", "EUC-JP", "EUC-KR", "x-windows-949",
            "GB18030", "Big5-HKSCS", "x-EUC-TW"
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

    private static final Set<String> IBM424_CHARSETS = new HashSet<>(Arrays.asList(
            "IBM424-ltr", "IBM424-rtl"
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
     * decode) before a chunk is rejected.  Base allowance plus 1 per 200 source
     * characters so longer concatenated chunks aren't penalised unfairly.
     */
    private static final int MAX_DROPPED_BASE = 3;
    private static final int DROP_SCALE_CHARS = 200;

    // -----------------------------------------------------------------------
    // Configuration defaults
    // -----------------------------------------------------------------------

    /**
     * Safety-valve sample cap per charset per split.  The byte budget is the
     * real throttle; this just prevents runaway sample counts.
     */
    private static final int DEFAULT_SAMPLE_CAP = 500_000;

    /**
     * Byte budget per charset for the train split.  All charsets get the same
     * budget so the model sees comparable feature signal regardless of source
     * sentence length or encoding density.  100 MB is generous — a linear
     * model converges well before this, but more data means better-calibrated
     * weights for rare byte patterns (e.g. distinguishing Shift_JIS from
     * Big5-HKSCS on short probes).
     */
    private static final long DEFAULT_BYTE_BUDGET = 100_000_000L;

    private static final int DEFAULT_MIN_CHUNK       =     64;
    private static final int DEFAULT_MAX_CHUNK       =  1_024;
    private static final int DEFAULT_SEED            =     42;
    private static final int MAX_LOAD_CAP_PER_LANG = 4_000_000;
    private static final int LEGACY_SENTENCE_BUDGET = 8_000_000;
    private static final int UNICODE_SENTENCE_BUDGET = 5_000_000;
    private static final String UNICODE_LANGS_FILE = "unicode_langs.txt";

    // -----------------------------------------------------------------------
    // Main
    // -----------------------------------------------------------------------

    public static void main(String[] args) throws IOException {
        Path madladDir       = Paths.get(System.getProperty("user.home"),
                                         "datasets", "madlad", "data");
        Path outputDir       = Paths.get(System.getProperty("user.home"),
                                         "datasets", "madlad", "charset-detect4");
        Set<String> requested = new HashSet<>(CHARSET_JAVA.keySet());
        int sampleCap        = DEFAULT_SAMPLE_CAP;
        long byteBudget      = DEFAULT_BYTE_BUDGET;
        int minChunk         = DEFAULT_MIN_CHUNK;
        int maxChunk         = DEFAULT_MAX_CHUNK;
        int seed             = DEFAULT_SEED;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--madlad-dir":
                    madladDir = Paths.get(args[++i]);
                    break;
                case "--output-dir":
                    outputDir = Paths.get(args[++i]);
                    break;
                case "--charsets":
                    requested = new HashSet<>(Arrays.asList(args[++i].split(",")));
                    break;
                case "--sample-cap":
                    sampleCap = Integer.parseInt(args[++i]);
                    break;
                case "--byte-budget":
                    byteBudget = Long.parseLong(args[++i]);
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

        // Load unicode_langs.txt for Unicode charset training
        Path unicodeLangsFile = madladDir.resolve(UNICODE_LANGS_FILE);
        List<String> unicodeLangs = new ArrayList<>();
        if (Files.exists(unicodeLangsFile)) {
            for (String line : Files.readAllLines(unicodeLangsFile,
                    java.nio.charset.StandardCharsets.UTF_8)) {
                line = line.strip();
                if (!line.isEmpty() && !line.startsWith("#")) {
                    unicodeLangs.add(line);
                }
            }
        } else {
            System.err.println("WARNING: " + unicodeLangsFile + " not found; "
                    + "Unicode charsets will use LANG_CHARSETS languages only");
            for (String lang : LANG_CHARSETS.keySet()) {
                unicodeLangs.add(lang);
            }
        }

        System.out.println("=== BuildCharsetTrainingData ===");
        System.out.println("  madlad-dir:          " + madladDir);
        System.out.println("  output-dir:          " + outputDir);
        System.out.printf ("  sample cap:          %,d%n", sampleCap);
        System.out.printf ("  byte budget:         %,d%n", byteBudget);
        System.out.printf ("  chunk:               %d–%d bytes  seed=%d%n", minChunk, maxChunk, seed);
        System.out.printf ("  unicode langs:       %d (from %s)%n",
                unicodeLangs.size(), unicodeLangsFile.getFileName());
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
        // charset label → split name → total bytes
        Map<String, Map<String, Long>> byteTotals = new TreeMap<>();

        for (String label : targetCharsets) {
            String javaName = CHARSET_JAVA.get(label);
            Charset cs      = Charset.forName(javaName);
            boolean structOnly = STRUCTURAL_ONLY.contains(label);
            String[] splits       = {"train", "devtest", "test"};
            int[]    splitCaps    = {sampleCap, sampleCap, sampleCap};
            long[]   splitBudgets = {byteBudget, byteBudget / 5, byteBudget / 5};
            System.out.printf("%s  (%s)%s%n", label, javaName,
                    structOnly ? "  [structural-only: skipping train]" : "");

            // Determine contributing languages.
            // For Unicode charsets, read from unicode_langs.txt (generated
            // by select_unicode_langs.py) which covers all major scripts
            // with a random Latin sample for diversity.
            // For legacy charsets, use only the explicit LANG_CHARSETS mappings.
            boolean isUnicode = UNICODE_CHARSETS.contains(label);
            List<String> langs = new ArrayList<>();
            if (isUnicode) {
                langs.addAll(unicodeLangs);
            } else {
                for (Map.Entry<String, List<String>> e :
                        LANG_CHARSETS.entrySet()) {
                    if (e.getValue().contains(label)) {
                        langs.add(e.getKey());
                    }
                }
            }

            // Load sentences per language (capped for memory), then combine
            // and split 80/10/10.  The byte budget is the real throttle.
            int nLangs = Math.max(1, langs.size());
            int perLangCap = isUnicode
                    ? Math.max(5_000, UNICODE_SENTENCE_BUDGET / nLangs)
                    : Math.min(MAX_LOAD_CAP_PER_LANG, LEGACY_SENTENCE_BUDGET / nLangs);
            List<String> allSentences = new ArrayList<>();
            System.out.printf("  Contributing languages (%d), perLangCap=%,d:%n",
                    nLangs, perLangCap);
            for (String lang : langs) {
                Path langDir = madladDir.resolve(lang);
                List<String> sents = loadMadladSentences(langDir, perLangCap);
                if (sents.isEmpty()) {
                    System.out.printf("    %-6s: ** 0 sentences — MISSING DATA **%n", lang);
                } else {
                    long totalChars = 0;
                    for (String s : sents) totalChars += s.length();
                    System.out.printf("    %-6s: %,8d sentences  avg_len=%,.0f chars%n",
                            lang, sents.size(), (double) totalChars / sents.size());
                }
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
            Map<String, Long> splitBytes = new TreeMap<>();
            for (int si = 0; si < splits.length; si++) {
                String split = splits[si];
                int cap      = splitCaps[si];
                long budget  = splitBudgets[si];

                if (structOnly && "train".equals(split)) {
                    splitCounts.put(split, 0);
                    splitBytes.put(split, 0L);
                    continue;
                }

                List<String> sents = new ArrayList<>(splitSents.get(split));
                Collections.shuffle(sents,
                        new Random(seed + label.hashCode() + split.hashCode()));

                Path outFile = outputDir.resolve(split).resolve(label + ".bin.gz");
                Files.createDirectories(outFile.getParent());

                List<CharsetEncoder> rivals =
                        sbcsRivals.getOrDefault(label, Collections.emptyList());
                long[] result = writeSamples(sents, cs, label, outFile,
                                             cap, budget, minChunk, maxChunk,
                                             new Random(seed + label.hashCode()),
                                             rivals);
                int written          = (int) result[0];
                int ambiguousDropped = (int) result[1];
                long totalBytes      = result[2];
                splitCounts.put(split, written);
                splitBytes.put(split, totalBytes);
                double budgetPct = 100.0 * totalBytes / budget;
                if (ambiguousDropped > 0) {
                    System.out.printf("    %s: %,d samples  %,d bytes (%.1f%% of budget)  (%,d ambiguous-dropped)%n",
                            split, written, totalBytes, budgetPct, ambiguousDropped);
                } else {
                    System.out.printf("    %s: %,d samples  %,d bytes (%.1f%% of budget)%n",
                            split, written, totalBytes, budgetPct);
                }
            }
            manifest.put(label, splitCounts);
            byteTotals.put(label, splitBytes);
        }

        writeManifest(outputDir, manifest);

        // Summary table
        System.out.println("\n=== SUMMARY ===");
        System.out.printf("%-22s %8s %12s %8s %12s %8s %12s%n",
                "Charset", "Train", "Train MB", "DevTest", "DT MB", "Test", "Test MB");
        System.out.println("-".repeat(100));
        for (Map.Entry<String, Map<String, Integer>> e : manifest.entrySet()) {
            String cs = e.getKey();
            Map<String, Integer> sc = e.getValue();
            Map<String, Long> bt = byteTotals.getOrDefault(cs, Collections.emptyMap());
            System.out.printf("%-22s %,8d %10.1f MB %,8d %10.1f MB %,8d %10.1f MB%n",
                    cs,
                    sc.getOrDefault("train", 0),
                    bt.getOrDefault("train", 0L) / 1_000_000.0,
                    sc.getOrDefault("devtest", 0),
                    bt.getOrDefault("devtest", 0L) / 1_000_000.0,
                    sc.getOrDefault("test", 0),
                    bt.getOrDefault("test", 0L) / 1_000_000.0);
        }

        // Flag any charsets with suspiciously low train counts
        System.out.println();
        boolean anyWarnings = false;
        for (Map.Entry<String, Map<String, Integer>> e : manifest.entrySet()) {
            int train = e.getValue().getOrDefault("train", 0);
            if (train > 0 && train < 1000) {
                System.out.printf("WARNING: %s has only %,d train samples — check source data!%n",
                        e.getKey(), train);
                anyWarnings = true;
            }
        }
        if (!anyWarnings) {
            System.out.println("All charsets have >= 1,000 train samples.");
        }

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
     *
     * <p>Each MADLAD "line" is a full web-scraped document with literal
     * {@code \n} escape sequences as sub-sentence separators.  This method
     * splits on those separators so each returned string is an individual
     * sentence (typically 20–500 characters).  This produces more diverse
     * training data and equalises sentence length across source corpora
     * (MADLAD documents vs. Wikipedia single-line sentences).</p>
     */
    private static List<String> loadMadladSentences(Path langDir, int max) {
        List<String> result = new ArrayList<>();
        for (String filename : new String[]{"sentences_madlad.txt",
                                            "sentences_wikipedia.txt"}) {
            if (result.size() >= max) {
                break;
            }
            Path txt = langDir.resolve(filename);
            if (!Files.exists(txt)) {
                continue;
            }
            int before = result.size();
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(Files.newInputStream(txt),
                                          java.nio.charset.StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null && result.size() < max) {
                    int tab = line.indexOf('\t');
                    String text = (tab >= 0) ? line.substring(tab + 1) : line;
                    text = text.replace("\ufeff", "");
                    for (String part : text.split("\\\\n")) {
                        String cleaned = part.replace("\\r", "")
                                .replace("\\t", " ")
                                .strip().replaceAll("\\s+", " ");
                        if (!cleaned.isEmpty()) {
                            result.add(cleaned);
                            if (result.size() >= max) {
                                break;
                            }
                        }
                    }
                }
            } catch (IOException e) {
                System.err.println("WARNING: could not read " + txt
                        + ": " + e.getMessage());
            }
            int added = result.size() - before;
            if (added > 0) {
                System.out.printf("      (loaded %,d from %s)%n", added, filename);
            }
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
     * Encode sentences and write samples to a gzipped binary file in
     * {@code [uint16-BE length][raw bytes]} format.
     *
     * <p>Stops when either the sample count cap or byte budget is reached.
     * When an individual sentence encodes to fewer than {@code minChunk}
     * bytes, adjacent sentences are concatenated (joined with {@code \n})
     * until the combined text reaches the target chunk size.  This ensures
     * all charsets produce full-sized chunks regardless of source sentence
     * length.</p>
     *
     * @param rivals other SBCS {@link CharsetEncoder}s to check for byte-level
     *               identity (the ambiguity gate); empty for CJK/Unicode charsets
     * @return {@code long[]}{written, ambiguousDropped, totalBytes}
     */
    private static long[] writeSamples(List<String> sentences, Charset cs, String charset,
                                       Path outFile, int sampleCap, long byteBudget,
                                       int minChunk, int maxChunk,
                                       Random rng, List<CharsetEncoder> rivals)
            throws IOException {
        int written          = 0;
        int ambiguousDropped = 0;
        int encodeRejected   = 0;
        long totalBytes      = 0;
        int sentIdx          = 0;
        try (DataOutputStream out = new DataOutputStream(
                new GZIPOutputStream(Files.newOutputStream(outFile)))) {
            while (sentIdx < sentences.size()
                    && written < sampleCap && totalBytes < byteBudget) {
                int target = minChunk + rng.nextInt(maxChunk - minChunk + 1);

                // Build input text: concatenate sentences until we have
                // enough characters to plausibly fill the target byte size.
                // Rough heuristic: 2 bytes per char for CJK, 1 for SBCS.
                int charTarget = target;
                StringBuilder combined = new StringBuilder(
                        sentences.get(sentIdx++));
                while (combined.length() < charTarget
                        && sentIdx < sentences.size()) {
                    combined.append('\n').append(sentences.get(sentIdx++));
                }

                byte[] chunk = encodeChunk(combined.toString(), cs, charset,
                        target);
                if (chunk == null) {
                    encodeRejected++;
                    continue;
                }
                if (!rivals.isEmpty()
                        && isAmbiguous(combined.toString(), chunk, rivals)) {
                    ambiguousDropped++;
                    continue;
                }
                out.writeShort(chunk.length);
                out.write(chunk);
                written++;
                totalBytes += chunk.length;
            }
        }
        String stopReason;
        if (sentIdx >= sentences.size()) {
            stopReason = "exhausted sentences";
        } else if (written >= sampleCap) {
            stopReason = "hit sample cap";
        } else if (totalBytes >= byteBudget) {
            stopReason = "hit byte budget";
        } else {
            stopReason = "unknown";
        }
        if (encodeRejected > 0 || !"hit byte budget".equals(stopReason)) {
            System.out.printf("      [stop: %s | encode-rejected=%,d | "
                            + "sentences-consumed=%,d/%,d]%n",
                    stopReason, encodeRejected, sentIdx, sentences.size());
        }
        return new long[]{written, ambiguousDropped, totalBytes};
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
        if (!UNICODE_CHARSETS.contains(charset)) {
            text = normalizeTypography(text, charset);
        }
        if ("windows-1256".equals(charset)) {
            text = normalizeForWin1256(text);
        }
        if (RTL_CHARSETS.contains(charset)) {
            text = new StringBuilder(text).reverse().toString();
        }
        if (IBM420_CHARSETS.contains(charset)) {
            text = prepareForIbm420(text);
        }
        if (IBM424_CHARSETS.contains(charset)) {
            text = stripHebrewNikkud(text);
        }
        return text;
    }

    private static final char[][] TYPO_REPLACEMENTS = {
        {'\u2018', '\''},  // LEFT SINGLE QUOTATION MARK
        {'\u2019', '\''},  // RIGHT SINGLE QUOTATION MARK
        {'\u201A', '\''},  // SINGLE LOW-9 QUOTATION MARK
        {'\u201C', '"'},   // LEFT DOUBLE QUOTATION MARK
        {'\u201D', '"'},   // RIGHT DOUBLE QUOTATION MARK
        {'\u201E', '"'},   // DOUBLE LOW-9 QUOTATION MARK
        {'\u2013', '-'},   // EN DASH
        {'\u2014', '-'},   // EM DASH
        {'\u2011', '-'},   // NON-BREAKING HYPHEN
        {'\u2026', '.'},   // HORIZONTAL ELLIPSIS
        {'\u02BC', '\''},  // MODIFIER LETTER APOSTROPHE
    };

    private static final char[] TYPO_STRIP = {
        '\u200B',  // ZERO WIDTH SPACE
    };

    private static final Map<String, Set<Character>> TYPO_CACHE =
            new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Build the set of typographic chars that need replacing for a charset.
     * Chars that the charset CAN encode are left alone (they're discriminative).
     */
    private static Set<Character> unencodableTypoChars(String charset) {
        return TYPO_CACHE.computeIfAbsent(charset, cs -> {
            Charset javaCs = Charset.forName(CHARSET_JAVA.getOrDefault(cs, cs));
            CharsetEncoder enc = javaCs.newEncoder();
            Set<Character> result = new HashSet<>();
            for (char[] pair : TYPO_REPLACEMENTS) {
                if (!enc.canEncode(pair[0])) {
                    result.add(pair[0]);
                }
            }
            for (char c : TYPO_STRIP) {
                if (!enc.canEncode(c)) {
                    result.add(c);
                }
            }
            return result;
        });
    }

    /**
     * Normalize typographic punctuation to ASCII equivalents, but only for
     * characters that the target charset cannot encode.  Charsets like
     * windows-1252 that CAN encode curly quotes keep them as discriminative
     * features.  Also strips zero-width spaces.
     */
    private static String normalizeTypography(String text, String charset) {
        Set<Character> unencodable = unencodableTypoChars(charset);
        if (unencodable.isEmpty()) {
            return text;
        }
        StringBuilder sb = null;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (!unencodable.contains(c)) {
                if (sb != null) {
                    sb.append(c);
                }
                continue;
            }
            if (sb == null) {
                sb = new StringBuilder(text.length());
                sb.append(text, 0, i);
            }
            char replacement = 0;
            for (char[] pair : TYPO_REPLACEMENTS) {
                if (pair[0] == c) {
                    replacement = pair[1];
                    break;
                }
            }
            if (replacement != 0) {
                sb.append(replacement);
            }
            // else: it's in TYPO_STRIP — just drop it
        }
        return (sb != null) ? sb.toString() : text;
    }

    /**
     * Strip Hebrew vowel points (nikkud) for IBM424 EBCDIC Hebrew.
     * Mainframe Hebrew text was written without vowel points, just like
     * mainframe Arabic was written without harakat.
     */
    private static String stripHebrewNikkud(String text) {
        StringBuilder sb = null;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            boolean strip = (c >= '\u05B0' && c <= '\u05BD')
                    || c == '\u05BF'
                    || (c >= '\u05C1' && c <= '\u05C2')
                    || c == '\u05C4' || c == '\u05C5' || c == '\u05C7';
            if (strip) {
                if (sb == null) {
                    sb = new StringBuilder(text.length());
                    sb.append(text, 0, i);
                }
            } else if (sb != null) {
                sb.append(c);
            }
        }
        return (sb != null) ? sb.toString() : text;
    }

    /**
     * Normalize modern Unicode Arabic/Farsi/Urdu text to the windows-1256
     * repertoire.  Real windows-1256 documents used these mappings because
     * the charset had no Farsi Yeh, no Extended Arabic-Indic digits, etc.
     *
     * <ul>
     *   <li>Strip invisible bidi controls and zero-width chars.</li>
     *   <li>Farsi Yeh (U+06CC) → Arabic Yeh (U+064A).</li>
     *   <li>Extended Arabic-Indic digits (U+06F0–06F9) →
     *       Arabic-Indic digits (U+0660–0669).</li>
     *   <li>Arabic Full Stop (U+06D4) → full stop (U+002E).</li>
     * </ul>
     */
    private static String normalizeForWin1256(String text) {
        StringBuilder sb = null;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            char replacement = 0;
            boolean strip = false;
            switch (c) {
                // Invisible / bidi controls — strip entirely
                case '\u200B': case '\u200C': case '\u200D':
                case '\u200E': case '\u200F':
                case '\u202A': case '\u202B': case '\u202C':
                case '\u202D': case '\u202E':
                case '\u2060':
                case '\u2066': case '\u2067': case '\u2068':
                case '\u2069':
                    strip = true;
                    break;
                // Farsi Yeh → Arabic Yeh
                case '\u06CC':
                    replacement = '\u064A';
                    break;
                // Arabic Full Stop → period
                case '\u06D4':
                    replacement = '.';
                    break;
                default:
                    // Extended Arabic-Indic digits → Arabic-Indic digits
                    if (c >= '\u06F0' && c <= '\u06F9') {
                        replacement = (char) (c - '\u06F0' + '\u0660');
                    }
                    break;
            }
            if (strip) {
                if (sb == null) {
                    sb = new StringBuilder(text.length());
                    sb.append(text, 0, i);
                }
            } else if (replacement != 0) {
                if (sb == null) {
                    sb = new StringBuilder(text.length());
                    sb.append(text, 0, i);
                }
                sb.append(replacement);
            } else if (sb != null) {
                sb.append(c);
            }
        }
        return (sb != null) ? sb.toString() : text;
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
     *   <li>Reject if (dropped + corrupt) {@literal >} scaled max.</li>
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
        int maxDrop = MAX_DROPPED_BASE + sourceText.length() / DROP_SCALE_CHARS;
        if (dropped + corrupt > maxDrop) {
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
        System.err.println("  --output-dir          <path>  Output dir (default: ~/datasets/madlad/charset-detect4)");
        System.err.println("  --charsets            cs1,cs2 Comma-separated charset subset");
        System.err.println("  --sample-cap          N       Safety-valve sample cap per split (default: 200000)");
        System.err.println("  --byte-budget         N       Byte budget per charset for train (default: 100000000)");
        System.err.println("  --min-chunk           N       Min encoded bytes per sample (default: 64)");
        System.err.println("  --max-chunk           N       Max encoded bytes per sample (default: 1024)");
        System.err.println("  --seed                N       Random seed (default: 42)");
        System.err.println();
        System.err.println("Supported charsets (" + CHARSET_JAVA.size() + "):");
        for (String label : CHARSET_JAVA.keySet()) {
            System.err.println("  " + label);
        }
    }
}
