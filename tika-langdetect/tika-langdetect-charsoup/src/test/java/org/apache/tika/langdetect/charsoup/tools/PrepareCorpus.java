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

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;

import org.apache.tika.langdetect.charsoup.CharSoupFeatureExtractor;

/**
 * Converts a raw MADLAD corpus directory into the three data splits used by
 * {@link TrainLanguageModel}:
 * <ul>
 *   <li>{@code pool/} — per-language preprocessed files for training</li>
 *   <li>{@code dev.txt} — fixed dev set (preprocessed), used for early stopping</li>
 *   <li>{@code test_raw.txt} — fixed test set (raw), used for post-training eval</li>
 * </ul>
 *
 * <p>This class owns all language-inclusion policy: the
 * {@link #EXCLUDED_LANGS} list, the {@link #LANG_MERGE_MAP} aliases, and the
 * script-aware sentence-count thresholds ({@link #MIN_SENTENCES_PER_LANG} for
 * unique-script languages, {@link #LATIN_MIN_SENTENCES_PER_LANG} for Latin-script
 * languages). {@link TrainLanguageModel} calls {@link #prepareData} directly so
 * there is a single implementation shared by both entry points.
 *
 * <p>Usage:
 * <pre>
 *   PrepareCorpus --corpus &lt;dir&gt; --output-dir &lt;dir&gt; [--max-train N]
 *                 [--max-dev N] [--max-test N]
 * </pre>
 */
public class PrepareCorpus {

    /**
     * Minimum sentences for unique-script (non-Latin) languages.
     * These languages occupy a distinct region of the character space and can
     * be identified reliably even with sparse data.
     */
    static final int MIN_SENTENCES_PER_LANG = 10_000;

    /**
     * Minimum sentences for Latin-script languages.
     * Previously 20k when training on MADLAD (noisy data required more volume).
     * Lowered to 10k for Wikipedia data which is cleaner.
     */
    static final int LATIN_MIN_SENTENCES_PER_LANG = 10_000;

    private static final int DEFAULT_MAX_TEST_PER_LANG  = 2_000;
    private static final int DEFAULT_MAX_DEV_PER_LANG   = 2_000;

    /**
     * Languages explicitly excluded from the model despite having enough corpus
     * data to meet the sentence-count threshold. Each exclusion falls into one
     * of two categories:
     *
     * <ul>
     *   <li><b>Accuracy interference</b> — adding the language causes a closely
     *       related majority language to drop significantly in accuracy.</li>
     *   <li><b>Unacceptable own accuracy</b> — the language's own detection
     *       accuracy is too low to be useful.</li>
     * </ul>
     *
     * See the build documentation for per-language justifications.
     */
    static final Set<String> EXCLUDED_LANGS;
    static {
        Set<String> ex = new HashSet<>();
        // Venetian (vec): 72.0% own accuracy; Italian (ita) dropped to 83.6%.
        ex.add("vec");
        // Waray (war): previously excluded for two reasons: (1) MADLAD audit flag,
        // (2) causes short-text confusion with English. Wikipedia source is cleaner;
        // retested. POST-TRAINING: verify English (eng) recall is not degraded.
        // Alsatian / Alemannic German (gsw): previously excluded for two reasons:
        // (1) MADLAD content-quality flags, (2) confusion with German at short lengths.
        // Wikipedia source retested. POST-TRAINING: verify German (deu) F1 not degraded.
        // Hawaiian (haw): previously excluded due to MADLAD content-quality
        // flags. Wikipedia source retested.
        // Inuktitut (iku): previously excluded because MADLAD data was heavily
        // English-contaminated. Wikipedia source uses Canadian Syllabics script;
        // SCRIPT_CONSISTENCY_LANGS entry added to filter Latin contamination.
        // Shan (shn): previously excluded due to Latin/English contamination in
        // MADLAD. SCRIPT_CONSISTENCY_LANGS entry added (MYANMAR script) to filter it.
        // Cornish (cor): Not present in Flores-200; Wikipedia corpus is
        // known to contain substantial English code-switching. Re-enabled
        // with Wikipedia data. POST-TRAINING: verify English (eng) recall
        // is not degraded.
        // Tosk Albanian (als): 69.7% own accuracy; Standard Albanian (sqi)
        // collapsed to 51.6%.
        ex.add("als");
        // Madurese (mad): 9.1% own accuracy — essentially random.
        ex.add("mad");
        // Anaang (anw): 32.5% own accuracy on only 3,036 test sentences.
        ex.add("anw");
        // Konkani (knn): 46.2% own accuracy. Devanagari script overlaps Marathi.
        ex.add("knn");
        // Gilaki (glk): 88.6% own accuracy. Script overlaps Persian/Mazanderani.
        ex.add("glk");
        // Kituba / Monokutuba (mkw): 80.1% own accuracy. Overlaps Kongo/Lingala.
        ex.add("mkw");
        // Dzongkha (dzo): F1=0.584. Irresolvable collision with Tibetan (bod).
        ex.add("dzo");
        // Tibetan (bod): F1=0.652. Mutually destructive with Dzongkha (dzo).
        ex.add("bod");
        // Sabah Malay (msi): F1=0.611. Indistinguishable from msa/ind.
        ex.add("msi");
        // Meru (meo): F1=0.629. Similar Malay-family confusion to msi.
        ex.add("meo");
        // Eastern Balochi (bgp): F1=0.351 at 20 chars. Heavy overlap with
        // Arabic/Urdu/Persian at short lengths.
        ex.add("bgp");
        // Fiji Hindi (hif): F1=0.808. Persistent confusion with hin.
        ex.add("hif");
        // Manx (glv): previously excluded due to MADLAD English contamination.
        // Wikipedia source retested.
        // Old English (ang): F1=0.860. Causes 109 English sentences to be
        // misclassified. Not a practical Tika detection target.
        ex.add("ang");
        // Crimean Tatar (crh): F1=0.727. Persistently confused with Turkish (tur).
        ex.add("crh");
        // Zazaki / Southern Zaza (zza): F1=0.712. Overlaps Turkish/Kurdish.
        ex.add("zza");
        // Bosnian (bos): near-random with hrv/srp at character level; F1 ~0.
        ex.add("bos");
        // Southern Sotho (sot): F1=0.659. Accuracy interference with tsn/nso.
        ex.add("sot");

        // --- Wikipedia-era drops (March 2026) ---
        // Serbo-Croatian (hbs): F1@20=0.113, F1@500=0.849 — never separable from
        // hrv/srp/bos at any useful length. 77.6% Pass-2 retention (worst in pool).
        ex.add("hbs");
        // Maithili (mai): F1@20=0.146, F1@50=0.490. Devanagari script identical to
        // hin/nep/mar at short lengths; 73.5% Pass-2 retention signals corpus
        // contamination. Collateral damage to Hindi makes inclusion net-negative.
        ex.add("mai");
        // Komi-Permyak (koi): F1@500=0.765 — permanently confused with Komi-Zyrian
        // (kpv) at 22% even at 500 chars. Weaker of the pair; kpv is retained.
        ex.add("koi");
        // Haitian Creole (hat): formerly dropped for F1@20=0.155 / English confusion.
        // Reinstated: length-gated confusables (hat→fra, threshold=400 ngrams) suppress
        // hat at short text and fold its probability into French, eliminating the
        // English bleed. hat is retained as a full class for longer text.
        // Scots (sco): F1@20=0.258. #1 FP source into English AND #2 destination
        // for English recall loss at @20 chars — hurts English in both directions.
        // Scots Wikipedia was largely written by a non-Scots speaker modifying
        // English articles; training signal is unreliable.
        ex.add("sco");
        // Quechua (que): F1@500=0.833. Never breaks 0.85 at any length; confusable
        // with Aymara (aym) — the two Andean languages bleed into each other.
        ex.add("que");
        // Aymara (aym): F1@500=0.837. Same structural problem as que; mutually
        // confusable Andean pair.
        ex.add("aym");
        // Picard (pcd): F1@500=0.805. French-adjacent Romance variety; never
        // separable from French at any length.
        ex.add("pcd");
        // Gorontalo (gor): F1@500=0.801, F1@50=0.503. Persistently poor at every
        // length — worst recovery rate of the post-Wikipedia drop candidates.
        ex.add("gor");
        // Cebuano (ceb): Wikipedia corpus was Lsjbot-generated municipality stubs
        // (F1@500=0.999 Wikipedia vs 13.9% FLORES-200). Re-enabled with MADLAD data
        // (sentences_madlad.txt), which contains genuine Cebuano prose.
        // POST-TRAINING: verify tgl/fil confusion is acceptable.
        // Zeelandic (zea): F1@500=0.827, below 0.85 threshold. Still bleeds 1.4%
        // into Dutch (nld) on FLORES at full sentence length even with the gate.
        // West Flemish (vls) and Low Saxon Dutch (nds-nl) are retained — both reach
        // F1@500=0.925 and the gate handles short-text confusion adequately.
        ex.add("zea");

        // --- v5 drops: bot-generated corpora identified by manual sampling ---
        // Method: random-sample audit of Wikipedia corpus pool files, cross-referenced
        // against Wikipedia/FLORES F1 gap analysis. Each language below had its training
        // data inspected and found to be dominated by templated bot stubs.

        // Egyptian Arabic (arz): corpus is overwhelmingly person/place stubs
        // ("من مواليد يوم [date] سنة [year] فى [place]"). FLORES F1=5.3% vs
        // Wikipedia F1@500=99.9% — same signature as ceb.
        ex.add("arz");

        // Buginese (bug): ~95% French municipality stubs
        // ("X iyanaritu séuwa komun ri déparetema Y ri Perancis"). Only 8k sentences.
        ex.add("bug");

        // Bishnupriya Manipuri (bpy): dominated by Brazilian municipality, US county,
        // and Indian/Bangladeshi location stubs. 18k sentences, near-zero genuine prose.
        ex.add("bpy");

        // Malagasy (mlg): Wikipedia corpus dominated by French commune stubs.
        // Re-enabled with MADLAD data (sentences_madlad.txt), which contains
        // genuine Malagasy prose. POST-TRAINING: verify F1 on FLORES.

        // Min Nan Chinese romanized (nan-x-rom): 6/8 random samples are geographic
        // stubs (Romanian communes, Bolivian municipalities, German/Iranian villages).
        // 455k sentences but overwhelmingly templated.
        ex.add("nan-x-rom");

        // Newari (new): Indian village stubs dominate
        // ("भारतया X राज्यया Y जनपदया Z तहसीलया छगु गां ख"). 20k sentences.
        ex.add("new");

        // Ladin (lld): all sampled sentences are stubs (mountain ranges, municipalities,
        // towns, incomplete game articles). 143k sentences.
        ex.add("lld");

        // Chechen (che): Wikipedia corpus was overwhelmingly Russian/Mexican village
        // stubs. Re-enabled with MADLAD data (sentences_madlad.txt), which contains
        // genuine Chechen prose. POST-TRAINING: verify Russian (rus) F1 not degraded.

        // Navajo (nav): ~95% species distribution stubs following the template
        // "éí [animal] dah yikahjí atah yisdzoh... Ndaʼałkaahí dóó ééʼdeetįįhii éí
        // [scientific name] deiłníigo dayózhí". 47k sentences.
        ex.add("nav");

        // --- gate-simplification drops (v5) ---
        // The length-gating mechanism has been removed from CharSoupLanguageDetector.
        // Languages that required gating to avoid severe parent-language bleed are
        // dropped entirely. Languages with only minor short-text parent bleed (vls,
        // ext, rue, bjn, nap) are retained and compete at all lengths.

        // Haitian Creole (hat): was #1 false-positive source into English @20 chars
        // without gating. Corpus is mixed (filmography stubs, place stubs, genuine
        // prose), weakening the hat signal. Dropping rather than restoring the bleed.
        ex.add("hat");

        // Low Saxon Dutch (nds-nl): had the most complex dual-confusable behavior —
        // permanently paired with nds (Low Saxon German) AND length-gated against nld
        // (Dutch). nds-nl text routes to nds via the confusable pair. Simplified out.
        ex.add("nds-nl");

        // Banyumasan (map-bms): 5.7% bleed into ind and 3.5% into jav at FULL length
        // (above any threshold). Gating only addressed short text; the full-length
        // bleed remained. Not recoverable without a better corpus.
        ex.add("map-bms");

        // --- v7 drops: Italian-dialect cluster and Spanish/Catalan bleed (March 2026) ---
        // FLORES analysis showed these languages score 0% on independent evaluation
        // while actively harming major neighbors as false-positive sources.
        // Italian dialects / close relatives (all harm Italian precision):
        // Neapolitan (nap): 966/~2000 Wikipedia FP sentences absorbed as Italian.
        // Previously retained; FLORES analysis confirmed net-negative inclusion.
        ex.add("nap");
        // Friulian (fur): 0% FLORES (411/997 predicted as Italian).
        ex.add("fur");
        // Ligurian (lij): 0% FLORES (308/997 predicted as Italian).
        ex.add("lij");
        // Lombard (lmo): 0% FLORES (predicted as cat/roh/cos/ita across all sentences).
        ex.add("lmo");
        // Sicilian (scn): 0% FLORES (988/997 absorbed by Corsican cos).
        ex.add("scn");
        // Sardinian (srd): 0% FLORES (absorbed by cos/tet/ron/por/spa).
        ex.add("srd");
        // Piedmontese (pms): not in FLORES; Wikipedia FP source for Italian.
        ex.add("pms");
        // Tarantino (roa-tara): not in FLORES; 386 Wikipedia FP sentences into Italian.
        ex.add("roa-tara");
        // Twi (twi): 987/997 FLORES sentences absorbed by Akan (aka). Twi is a dialect
        // of Akan; having both is structurally unsound. aka is retained (66.4% FLORES F1).
        ex.add("twi");
        // Asturian (ast): 0% FLORES (865/997 predicted as Spanish). Dumps 865 FP
        // sentences into Spanish, holding Spanish FLORES F1 at 65%.
        ex.add("ast");
        // Occitan (oci): 0% FLORES (819/997 predicted as Catalan). Dumps 819 FP
        // sentences into Catalan, holding Catalan FLORES F1 at 61.8%.
        ex.add("oci");

                EXCLUDED_LANGS = Collections.unmodifiableSet(ex);
    }

    /**
     * ISO 639-3 aliases to merge into a canonical code before processing.
     * Sentences from the alias are relabeled to the canonical code and pooled
     * together before dedup and splitting.
     */
    static final Map<String, String> LANG_MERGE_MAP;
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

    /**
     * Minimum fraction of letter characters that must belong to the expected
     * script for a sentence to be retained. Sentences below this threshold
     * are considered contaminated and dropped from all splits (pool, dev, test).
     */
    static final double SCRIPT_CONSISTENCY_THRESHOLD = 0.80;

    /**
     * Per-language overrides for {@link #SCRIPT_CONSISTENCY_THRESHOLD}.
     * Use when a language naturally mixes its primary script with another
     * (e.g. Dhivehi mixes Thaana with Arabic in Islamic text).
     */
    static final Map<String, Double> SCRIPT_CONSISTENCY_THRESHOLD_OVERRIDES;
    static {
        Map<String, Double> m = new HashMap<>();
        // Dhivehi (div): Maldivian text naturally mixes Thaana with Arabic
        // script for Islamic phrases. 0.80 cuts too aggressively; 0.50 retains
        // genuine Thaana-dominant prose while allowing Arabic admixture.
        m.put("div", 0.50);
        SCRIPT_CONSISTENCY_THRESHOLD_OVERRIDES = Collections.unmodifiableMap(m);
    }

    /** Max fraction of Latin letters allowed for languages in {@link #MAX_LATIN_RATIO_LANGS}. */
    static final double MAX_LATIN_RATIO = 0.50;

    /**
     * Languages that use non-Latin scripts but legitimately mix some Latin.
     * Sentences where Latin letters exceed {@link #MAX_LATIN_RATIO} are dropped.
     */
    private static final Set<String> MAX_LATIN_RATIO_LANGS =
            Collections.unmodifiableSet(new HashSet<>(Arrays.asList("jpn", "kor")));

    /**
     * Maps language codes to the set of Unicode scripts expected for that
     * language. Sentences from these languages are filtered by
     * {@link #filterByScriptConsistency} to remove Latin/foreign-script contamination
     * before training.
     *
     * <p>Note: {@code jpn} and {@code kor} are intentionally excluded from this map;
     * they use {@link #MAX_LATIN_RATIO_LANGS} instead to drop Latin-dominant sentences
     * while retaining naturally mixed ones.
     * Japanese commonly mixes Latin characters (brand names, loanwords,
     * abbreviations) and a strict CJK-consistency filter would destroy nearly half
     * the training data. Korean has a similar, though less extreme, mixing pattern.
     * These two languages are better handled by the volume of clean training data
     * than by a script consistency filter.
     */
    @SuppressWarnings("unchecked")
    private static final Map<String, Set<Character.UnicodeScript>> SCRIPT_CONSISTENCY_LANGS;
    static {
        Map<String, Set<Character.UnicodeScript>> m = new HashMap<>();

        // CJK — all Han-script Chinese variants
        for (String lang : new String[]{"zho", "yue", "wuu", "gan", "lzh"}) {
            m.put(lang, EnumSet.of(Character.UnicodeScript.HAN));
        }
        // nan-x-rom (Min Nan) uses romanized Pe̍h-ōe-jī in Wikipedia — no HAN filter
        // cdo-x-rom (Eastern Min) uses romanized Foochow Romanized — no HAN filter
        // hak-x-rom (Hakka) uses romanized Pha̍k-fa-sṳ — no HAN filter

        // Arabic script
        for (String lang : new String[]{"ara", "fas", "urd", "pus", "ckb", "uig", "snd"}) {
            m.put(lang, EnumSet.of(Character.UnicodeScript.ARABIC));
        }

        // Cyrillic script
        for (String lang : new String[]{
                "rus", "ukr", "bul", "bel", "mkd", "srp", "bak", "tat", "sah",
                "chv", "bua", "kir", "myv", "mdf", "krc", "ava", "che", "oss",
                "kom", "udm", "kjh", "kum", "mrj", "chm", "inh", "kbd", "mon"}) {
            m.put(lang, EnumSet.of(Character.UnicodeScript.CYRILLIC));
        }

        // Devanagari script
        for (String lang : new String[]{"hin", "mar", "nep", "san", "bho", "mai"}) {
            m.put(lang, EnumSet.of(Character.UnicodeScript.DEVANAGARI));
        }

        // Indic scripts
        m.put("pan", EnumSet.of(Character.UnicodeScript.GURMUKHI));
        m.put("ben", EnumSet.of(Character.UnicodeScript.BENGALI));
        m.put("asm", EnumSet.of(Character.UnicodeScript.BENGALI));
        m.put("tel", EnumSet.of(Character.UnicodeScript.TELUGU));
        m.put("kan", EnumSet.of(Character.UnicodeScript.KANNADA));
        m.put("mal", EnumSet.of(Character.UnicodeScript.MALAYALAM));
        m.put("sin", EnumSet.of(Character.UnicodeScript.SINHALA));
        m.put("tam", EnumSet.of(Character.UnicodeScript.TAMIL));
        m.put("guj", EnumSet.of(Character.UnicodeScript.GUJARATI));
        m.put("ori", EnumSet.of(Character.UnicodeScript.ORIYA));
        m.put("sat", EnumSet.of(Character.UnicodeScript.OL_CHIKI));
        m.put("mni", EnumSet.of(Character.UnicodeScript.MEETEI_MAYEK));

        // Other distinct scripts
        m.put("kat", EnumSet.of(Character.UnicodeScript.GEORGIAN));
        m.put("hye", EnumSet.of(Character.UnicodeScript.ARMENIAN));
        m.put("ell", EnumSet.of(Character.UnicodeScript.GREEK));
        m.put("heb", EnumSet.of(Character.UnicodeScript.HEBREW));
        m.put("ydd", EnumSet.of(Character.UnicodeScript.HEBREW));
        m.put("tha", EnumSet.of(Character.UnicodeScript.THAI));
        m.put("khm", EnumSet.of(Character.UnicodeScript.KHMER));
        m.put("mya", EnumSet.of(Character.UnicodeScript.MYANMAR));
        m.put("ksw", EnumSet.of(Character.UnicodeScript.MYANMAR));  // S'gaw Karen
        m.put("amh", EnumSet.of(Character.UnicodeScript.ETHIOPIC));
        m.put("tir", EnumSet.of(Character.UnicodeScript.ETHIOPIC));
        m.put("lao", EnumSet.of(Character.UnicodeScript.LAO));
        m.put("iku", EnumSet.of(Character.UnicodeScript.CANADIAN_ABORIGINAL));
        // Myanmar script: Burmese, S'gaw Karen, Shan, Mon, Pa'O Karen
        for (String lang : new String[]{"shn", "mnw", "blk"}) {
            m.put(lang, EnumSet.of(Character.UnicodeScript.MYANMAR));
        }
        m.put("div", EnumSet.of(Character.UnicodeScript.THAANA));
        m.put("chr", EnumSet.of(Character.UnicodeScript.CHEROKEE));
        m.put("nqo", EnumSet.of(Character.UnicodeScript.NKO));
        m.put("bod", EnumSet.of(Character.UnicodeScript.TIBETAN));
        m.put("dzo", EnumSet.of(Character.UnicodeScript.TIBETAN));
        // Mingrelian uses Georgian script
        m.put("xmf", EnumSet.of(Character.UnicodeScript.GEORGIAN));

        SCRIPT_CONSISTENCY_LANGS = Collections.unmodifiableMap(m);
    }

    public static void main(String[] args) throws IOException {
        Path corpusDir        = null;
        Path outputDir        = null;
        int  maxTrainPerLang  = 0;
        int  maxDevPerLang    = DEFAULT_MAX_DEV_PER_LANG;
        int  maxTestPerLang   = DEFAULT_MAX_TEST_PER_LANG;
        boolean noScriptFilter = false;
        int  unkPerLang       = 0;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--corpus":
                    corpusDir = Paths.get(args[++i]);
                    break;
                case "--output-dir":
                    outputDir = Paths.get(args[++i]);
                    break;
                case "--max-train":
                    maxTrainPerLang = Integer.parseInt(args[++i]);
                    break;
                case "--max-dev":
                    maxDevPerLang = Integer.parseInt(args[++i]);
                    break;
                case "--max-test":
                    maxTestPerLang = Integer.parseInt(args[++i]);
                    break;
                case "--no-script-filter":
                    noScriptFilter = true;
                    break;
                case "--unk-per-lang":
                    unkPerLang = Integer.parseInt(args[++i]);
                    break;
                default:
                    System.err.println("Unknown argument: " + args[i]);
                    printUsage();
                    System.exit(1);
            }
        }

        if (corpusDir == null || outputDir == null) {
            printUsage();
            System.exit(1);
        }

        Files.createDirectories(outputDir);
        System.out.println("=== PrepareCorpus ===");
        System.out.println("Corpus    : " + corpusDir);
        System.out.println("Output dir: " + outputDir);
        System.out.println("Script filter: " + (noScriptFilter ? "disabled" : "enabled"));
        System.out.println();

        long start = System.nanoTime();
        int[] counts = prepareData(corpusDir, outputDir,
                maxTrainPerLang, maxDevPerLang, maxTestPerLang,
                noScriptFilter, unkPerLang);
        double elapsed = (System.nanoTime() - start) / 1_000_000_000.0;

        System.out.printf(Locale.US,
                "%nDone: pool=%,d  dev=%,d  test=%,d  [%.1f s]%n",
                counts[0], counts[1], counts[2], elapsed);
        System.out.println("Pool dir : " + outputDir.resolve("pool"));
        System.out.println("Dev file : " + outputDir.resolve("dev.txt"));
        System.out.println("Test file: " + outputDir.resolve("test_raw.txt"));
    }

    private static void printUsage() {
        System.err.println("Usage: PrepareCorpus"
                + " --corpus <dir> --output-dir <dir>"
                + " [--max-train N] [--max-dev N] [--max-test N]"
                + " [--no-script-filter] [--unk-per-lang N]");
    }

    // ================================================================
    //  Core preparation logic — called by TrainLanguageModel as well
    // ================================================================

    /**
     * Prepare data splits from a raw MADLAD corpus directory.
     *
     * @param corpusDir      root directory; each subdirectory is a language code
     * @param prepDir        output directory; receives {@code pool/}, {@code dev.txt},
     *                       {@code test_raw.txt}
     * @param maxTrainPerLang cap on sentences per language (0 = unlimited)
     * @param maxDevPerLang  cap on dev sentences per language
     * @param maxTestPerLang cap on test sentences per language
     * @return int[3]: {poolCount, devCount, testCount}
     */
    static int[] prepareData(Path corpusDir, Path prepDir,
                             int maxTrainPerLang,
                             int maxDevPerLang, int maxTestPerLang)
            throws IOException {
        return prepareData(corpusDir, prepDir,
                maxTrainPerLang, maxDevPerLang, maxTestPerLang, false, 0);
    }

    static int[] prepareData(Path corpusDir, Path prepDir,
                             int maxTrainPerLang,
                             int maxDevPerLang, int maxTestPerLang,
                             boolean noScriptFilter)
            throws IOException {
        return prepareData(corpusDir, prepDir,
                maxTrainPerLang, maxDevPerLang, maxTestPerLang,
                noScriptFilter, 0);
    }

    static int[] prepareData(Path corpusDir, Path prepDir,
                             int maxTrainPerLang,
                             int maxDevPerLang, int maxTestPerLang,
                             boolean noScriptFilter, int unkPerLang)
            throws IOException {
        Path poolDir  = prepDir.resolve("pool");
        Path devFile  = prepDir.resolve("dev.txt");
        Path testFile = prepDir.resolve("test_raw.txt");

        int effectiveUniqueMin = maxTrainPerLang > 0
                ? Math.min(MIN_SENTENCES_PER_LANG, maxTrainPerLang / 2)
                : MIN_SENTENCES_PER_LANG;
        int effectiveLatinMin = maxTrainPerLang > 0
                ? Math.min(LATIN_MIN_SENTENCES_PER_LANG, maxTrainPerLang / 2)
                : LATIN_MIN_SENTENCES_PER_LANG;

        Files.createDirectories(poolDir);

        int totalPool = 0, totalDev = 0, totalTest = 0;
        int langCount = 0;
        int droppedCount = 0;
        long totalDupes = 0;
        Map<String, Integer> langCounts = new TreeMap<>();
        List<String> dropped = new ArrayList<>();
        // Accumulates sentences from non-trained languages for the unk class.
        // Languages that are aliases (LANG_MERGE_MAP keys) are excluded because
        // their text is indistinguishable from the canonical class they map to.
        Set<String> mergeAliases = LANG_MERGE_MAP.keySet();
        List<LabeledSentence> unkAccum = new ArrayList<>();

        List<Path> langDirs = new ArrayList<>();
        try (DirectoryStream<Path> dirs =
                     Files.newDirectoryStream(corpusDir,
                             Files::isDirectory)) {
            for (Path d : dirs) {
                langDirs.add(d);
            }
        }
        langDirs.sort((a, b) -> a.getFileName().toString()
                .compareTo(b.getFileName().toString()));

        Map<String, List<LabeledSentence>> mergeAccum = new HashMap<>();

        try (BufferedWriter devWriter = Files.newBufferedWriter(
                     devFile, StandardCharsets.UTF_8);
             BufferedWriter testWriter = Files.newBufferedWriter(
                     testFile, StandardCharsets.UTF_8)) {

            for (Path langDir : langDirs) {
                String dirName = langDir.getFileName().toString();
                if (dirName.startsWith("_")) {
                    continue;
                }

                List<LabeledSentence> sentences = new ArrayList<>();
                if (maxTrainPerLang > 0) {
                    CorpusReader.readLanguageDirSampled(
                            langDir, dirName, maxTrainPerLang, sentences);
                } else {
                    CorpusReader.readLanguageDir(
                            langDir, dirName, sentences);
                }

                int beforeDedup = sentences.size();
                sentences = dedup(sentences);
                int removed = beforeDedup - sentences.size();
                if (removed > 0) {
                    totalDupes += removed;
                    if (removed > beforeDedup / 5) {
                        System.out.printf(Locale.US,
                                "  %s: removed %,d/%,d dupes (%.1f%%)%n",
                                dirName, removed, beforeDedup,
                                100.0 * removed / beforeDedup);
                    }
                }

                String canonLang = LANG_MERGE_MAP.getOrDefault(
                        dirName, dirName);

                if (!canonLang.equals(dirName)) {
                    List<LabeledSentence> relabeled =
                            new ArrayList<>(sentences.size());
                    for (LabeledSentence s : sentences) {
                        relabeled.add(new LabeledSentence(
                                canonLang, s.getText()));
                    }
                    sentences = relabeled;
                    System.out.printf(Locale.US,
                            "  %s → %s (%,d sentences)%n",
                            dirName, canonLang, sentences.size());
                    mergeAccum.computeIfAbsent(canonLang,
                            k -> new ArrayList<>())
                            .addAll(sentences);
                    continue;
                }

                List<LabeledSentence> accumulated =
                        mergeAccum.remove(canonLang);
                if (accumulated != null) {
                    sentences.addAll(accumulated);
                    sentences = dedup(sentences);
                }

                if (EXCLUDED_LANGS.contains(canonLang)) {
                    dropped.add(canonLang + "(excluded)");
                    droppedCount++;
                    if (unkPerLang > 0 && !mergeAliases.contains(dirName)) {
                        sampleIntoUnk(sentences, unkPerLang, unkAccum);
                    }
                    continue;
                }

                if (!noScriptFilter) {
                    sentences = filterByScriptConsistency(sentences, canonLang);
                    sentences = filterByLatinRatio(sentences, canonLang);
                }

                int minRequired = isLatinScript(sentences)
                        ? effectiveLatinMin : effectiveUniqueMin;
                if (sentences.size() < minRequired) {
                    dropped.add(canonLang + "(" + sentences.size() + ")");
                    droppedCount++;
                    if (unkPerLang > 0 && !mergeAliases.contains(dirName)) {
                        sampleIntoUnk(sentences, unkPerLang, unkAccum);
                    }
                    continue;
                }

                int[] written = writeLanguageSplit(
                        sentences, canonLang, poolDir,
                        devWriter, testWriter,
                        maxDevPerLang, maxTestPerLang);
                totalPool  += written[0];
                totalDev   += written[1];
                totalTest  += written[2];
                langCounts.put(canonLang, sentences.size());
                langCount++;
                if (langCount % 50 == 0) {
                    System.out.printf(Locale.US,
                            "  Processed %d languages...%n", langCount);
                }
            }

            // Flush remaining merged languages (alias came after canonical)
            for (Map.Entry<String, List<LabeledSentence>> e
                    : mergeAccum.entrySet()) {
                String lang = e.getKey();
                List<LabeledSentence> sentences = dedup(e.getValue());
                if (maxTrainPerLang > 0
                        && sentences.size() > maxTrainPerLang) {
                    sentences = sentences.subList(0, maxTrainPerLang);
                }
                if (EXCLUDED_LANGS.contains(lang)) {
                    dropped.add(lang + "(excluded)");
                    droppedCount++;
                    if (unkPerLang > 0) {
                        sampleIntoUnk(sentences, unkPerLang, unkAccum);
                    }
                    continue;
                }
                if (!noScriptFilter) {
                    sentences = filterByScriptConsistency(sentences, lang);
                    sentences = filterByLatinRatio(sentences, lang);
                }
                int minRequired = isLatinScript(sentences)
                        ? effectiveLatinMin : effectiveUniqueMin;
                if (sentences.size() < minRequired) {
                    dropped.add(lang + "(" + sentences.size() + ")");
                    droppedCount++;
                    if (unkPerLang > 0) {
                        sampleIntoUnk(sentences, unkPerLang, unkAccum);
                    }
                    continue;
                }
                int[] written = writeLanguageSplit(
                        sentences, lang, poolDir,
                        devWriter, testWriter,
                        maxDevPerLang, maxTestPerLang);
                totalPool += written[0];
                totalDev  += written[1];
                totalTest += written[2];
                langCounts.put(lang, sentences.size());
                langCount++;
            }
        }

        // Write unk class pool/dev/test if requested
        if (unkPerLang > 0 && !unkAccum.isEmpty()) {
            java.util.Collections.shuffle(unkAccum,
                    new java.util.Random(42));
            try (BufferedWriter devWriter = Files.newBufferedWriter(
                         prepDir.resolve("dev.txt"),
                         StandardCharsets.UTF_8,
                         java.nio.file.StandardOpenOption.APPEND);
                 BufferedWriter testWriter = Files.newBufferedWriter(
                         prepDir.resolve("test_raw.txt"),
                         StandardCharsets.UTF_8,
                         java.nio.file.StandardOpenOption.APPEND)) {
                int[] written = writeLanguageSplit(
                        unkAccum, "unk", poolDir,
                        devWriter, testWriter,
                        maxDevPerLang, maxTestPerLang);
                totalPool += written[0];
                totalDev  += written[1];
                totalTest += written[2];
                System.out.printf(Locale.US,
                        "unk class: %,d pool + %,d dev + %,d test "
                        + "(from %,d source sentences across excluded langs)%n",
                        written[0], written[1], written[2], unkAccum.size());
            }
        }

        if (totalDupes > 0) {
            System.out.printf(Locale.US,
                    "Deduplicated: removed %,d duplicate sentences%n",
                    totalDupes);
        }
        if (!dropped.isEmpty()) {
            dropped.sort(String::compareTo);
            System.out.println("Dropped " + droppedCount
                    + " low-resource languages: "
                    + String.join(", ", dropped));
        }
        System.out.println("Languages included: " + langCounts.size());
        langCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue()
                        .reversed())
                .limit(20)
                .forEach(e -> System.out.printf(Locale.US,
                        "  %-12s %,d%n", e.getKey(), e.getValue()));
        if (langCounts.size() > 20) {
            System.out.println("  ... and " + (langCounts.size() - 20) + " more");
        }

        return new int[]{totalPool, totalDev, totalTest};
    }

    // ================================================================
    //  Helpers
    // ================================================================

    /**
     * Samples up to {@code maxPerLang} sentences from {@code source} into
     * {@code unkAccum}, relabeling them all as {@code "unk"}.
     */
    private static void sampleIntoUnk(List<LabeledSentence> source,
                                       int maxPerLang,
                                       List<LabeledSentence> unkAccum) {
        int take = Math.min(source.size(), maxPerLang);
        for (int i = 0; i < take; i++) {
            unkAccum.add(new LabeledSentence("unk", source.get(i).getText()));
        }
    }

    /**
     * Split one language's sentences into test (raw), dev (preprocessed),
     * and pool (preprocessed, per-language file).
     *
     * <ul>
     *   <li>Test : 10%, max {@code maxTestPerLang}, raw text</li>
     *   <li>Dev  : 10%, max {@code maxDevPerLang}, preprocessed</li>
     *   <li>Pool : remainder, preprocessed</li>
     * </ul>
     *
     * @return int[3]: {pool, dev, test} sentence counts
     */
    static int[] writeLanguageSplit(
            List<LabeledSentence> sentences, String lang,
            Path poolDir,
            BufferedWriter devWriter, BufferedWriter testWriter,
            int maxDevPerLang, int maxTestPerLang)
            throws IOException {

        Random rng = new Random(lang.hashCode() + 42L);
        Collections.shuffle(sentences, rng);

        int remaining = sentences.size();
        int testCount = Math.min(
                (int) (remaining * 0.1f), maxTestPerLang);
        int devCount  = Math.min(
                (int) ((remaining - testCount) * 0.1f / 0.9f),
                maxDevPerLang);
        int poolStart = testCount + devCount;

        for (int i = 0; i < testCount; i++) {
            testWriter.write(lang);
            testWriter.write('\t');
            testWriter.write(sentences.get(i).getText());
            testWriter.newLine();
        }

        for (int i = testCount; i < testCount + devCount; i++) {
            String cleaned = CharSoupFeatureExtractor.preprocess(
                    sentences.get(i).getText());
            devWriter.write(lang);
            devWriter.write('\t');
            devWriter.write(cleaned);
            devWriter.newLine();
        }

        Path poolFile = poolDir.resolve(lang);
        try (BufferedWriter pw = Files.newBufferedWriter(
                poolFile, StandardCharsets.UTF_8)) {
            for (int i = poolStart; i < sentences.size(); i++) {
                String cleaned = CharSoupFeatureExtractor.preprocess(
                        sentences.get(i).getText());
                pw.write(cleaned);
                pw.newLine();
            }
        }

        return new int[]{sentences.size() - poolStart, devCount, testCount};
    }

    /**
     * Returns {@code true} if the language's sample text is predominantly
     * Latin-script (Basic Latin + Latin Extended-A/B, U+0000–U+024F).
     * Samples up to 20 sentences; defaults to {@code true} (stricter threshold)
     * when no letter content is found.
     */
    static boolean isLatinScript(List<LabeledSentence> sentences) {
        int latinLetters = 0;
        int totalLetters = 0;
        int limit = Math.min(sentences.size(), 20);
        for (int i = 0; i < limit; i++) {
            for (char c : sentences.get(i).getText().toCharArray()) {
                if (Character.isLetter(c)) {
                    totalLetters++;
                    if (c <= '\u024F') {
                        latinLetters++;
                    }
                }
            }
        }
        return totalLetters == 0
                || (double) latinLetters / totalLetters > 0.5;
    }

    /**
     * Removes sentences whose target-script letter fraction falls below
     * {@link #SCRIPT_CONSISTENCY_THRESHOLD}. Languages not present in
     * {@link #SCRIPT_CONSISTENCY_LANGS} are returned unchanged.
     *
     * <p>The filter operates on raw (un-preprocessed) text so that script
     * detection is not confused by any lowercasing or normalization steps.
     * It is applied uniformly before the train/dev/test split so all three
     * splits see only clean sentences.
     */
    static List<LabeledSentence> filterByScriptConsistency(
            List<LabeledSentence> sentences, String lang) {
        Set<Character.UnicodeScript> expected = SCRIPT_CONSISTENCY_LANGS.get(lang);
        if (expected == null) {
            return sentences;
        }
        List<LabeledSentence> filtered = new ArrayList<>(sentences.size());
        int dropped = 0;
        for (LabeledSentence s : sentences) {
            String text = s.getText();
            int letters = 0;
            int matching = 0;
            for (int i = 0; i < text.length(); ) {
                int cp = text.codePointAt(i);
                i += Character.charCount(cp);
                if (Character.isLetter(cp)) {
                    letters++;
                    if (expected.contains(Character.UnicodeScript.of(cp))) {
                        matching++;
                    }
                }
            }
            double consistency = letters == 0 ? 1.0 : (double) matching / letters;
            double threshold = SCRIPT_CONSISTENCY_THRESHOLD_OVERRIDES.getOrDefault(
                    lang, SCRIPT_CONSISTENCY_THRESHOLD);
            if (consistency >= threshold) {
                filtered.add(s);
            } else {
                dropped++;
            }
        }
        if (dropped > 0) {
            System.out.printf(Locale.US,
                    "  %s: script-consistency filter removed %,d/%,d sentences (%.1f%%)%n",
                    lang, dropped, sentences.size(),
                    100.0 * dropped / sentences.size());
        }
        return filtered;
    }

    /**
     * Drops sentences where Latin letters exceed {@link #MAX_LATIN_RATIO} of all
     * letter codepoints. Only applied to languages in {@link #MAX_LATIN_RATIO_LANGS}
     * (currently {@code jpn} and {@code kor}), which legitimately mix some Latin but
     * should not have Latin-dominant sentences.
     */
    static List<LabeledSentence> filterByLatinRatio(
            List<LabeledSentence> sentences, String lang) {
        if (!MAX_LATIN_RATIO_LANGS.contains(lang)) {
            return sentences;
        }
        List<LabeledSentence> filtered = new ArrayList<>(sentences.size());
        int dropped = 0;
        for (LabeledSentence s : sentences) {
            String text = s.getText();
            int letters = 0, latin = 0;
            for (int i = 0; i < text.length(); ) {
                int cp = text.codePointAt(i);
                i += Character.charCount(cp);
                if (Character.isLetter(cp)) {
                    letters++;
                    if (Character.UnicodeScript.of(cp) == Character.UnicodeScript.LATIN) {
                        latin++;
                    }
                }
            }
            double ratio = letters == 0 ? 0.0 : (double) latin / letters;
            if (ratio <= MAX_LATIN_RATIO) {
                filtered.add(s);
            } else {
                dropped++;
            }
        }
        if (dropped > 0) {
            System.out.printf(Locale.US,
                    "  %s: latin-ratio filter removed %,d/%,d sentences (%.1f%%)%n",
                    lang, dropped, sentences.size(),
                    100.0 * dropped / sentences.size());
        }
        return filtered;
    }

    /**
     * Deduplicate by FNV-1a 64-bit hash of the sentence text.
     */
    static List<LabeledSentence> dedup(List<LabeledSentence> sentences) {
        Set<Long> seen = new HashSet<>();
        List<LabeledSentence> unique = new ArrayList<>();
        for (LabeledSentence s : sentences) {
            long hash = DuplicateChecker.fnv1a64(s.getText());
            if (seen.add(hash)) {
                unique.add(s);
            }
        }
        return unique;
    }
}
