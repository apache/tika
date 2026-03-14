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
package org.apache.tika.langdetect.charsoup;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.config.SelfConfiguring;
import org.apache.tika.config.TikaComponent;
import org.apache.tika.language.detect.LanguageConfidence;
import org.apache.tika.language.detect.LanguageDetector;
import org.apache.tika.language.detect.LanguageResult;
import org.apache.tika.parser.ParseContext;

/**
 * CharSoup language detector using INT8-quantized multinomial logistic regression
 * trained on Wikipedia (primary corpus) with MADLAD supplements for thin languages.
 * <p>
 * Text is buffered via {@link #addText(char[], int, int)} up to
 * {@link CharSoupFeatureExtractor#MAX_TEXT_LENGTH} characters. At {@link #detectAll()} time,
 * the buffer is evaluated in independent {@value #CHUNK_SIZE}-character chunks.
 * Each chunk runs the full preprocessing pipeline (truncate → strip URLs/emails →
 * NFC normalize → extract bigram features → score via raw logits). If the first
 * chunk produces high entropy (indicating junk, code, or non-language content),
 * the next chunk is tried. The result from the chunk with the lowest entropy
 * is returned. This avoids polluting the language signal with leading junk while
 * keeping the implementation simple and predictable.
 * </p>
 * <p>
 * Inference uses raw logits throughout — the softmax array is never materialized.
 * The reported {@code rawScore} for each class is
 * {@code sigmoid(logit_c − logsumexp(other logits)) = exp(logit_c − logsumexp(all))},
 * computed by a single logsumexp pass followed by one {@code exp()} per class.
 * This is numerically equivalent to the softmax probability of class c but avoids
 * allocating or mutating a full probability distribution array.
 * </p>
 */
@TikaComponent(name = "charsoup-language-detector")
public class CharSoupLanguageDetector extends LanguageDetector implements SelfConfiguring {

    /**
     * Model selection strategy.
     * <ul>
     *   <li>{@link #AUTOMATIC} — use length and feature-density gates to choose
     *       between the short-text model and the general model (default)</li>
     *   <li>{@link #SHORT_TEXT} — always use the short-text model regardless of
     *       input length or feature count (no-op if the short-text model is absent)</li>
     *   <li>{@link #STANDARD} — always use the general model regardless of input
     *       length or feature count</li>
     * </ul>
     */
    public enum Strategy {
        AUTOMATIC,
        SHORT_TEXT,
        STANDARD
    }

    private static final Logger LOG =
            LoggerFactory.getLogger(CharSoupLanguageDetector.class);

    /**
     * General language model: v7, trained 2026-03-06.
     * 203 languages, 16 384 buckets, ScriptAwareFeatureExtractor (flags 0x075).
     */
    private static final String MODEL_RESOURCE =
            "/org/apache/tika/langdetect/charsoup/langdetect-v7-20260306.bin";

    /**
     * Short-text model: v1, trained 2026-03-10.
     * 123 languages, 32 768 buckets, ShortTextFeatureExtractor (flags 0x0a1).
     * Routed to for inputs shorter than {@link #SHORT_TEXT_LENGTH_THRESHOLD}
     * characters or with fewer than {@link #SHORT_TEXT_FEATURE_THRESHOLD} n-gram
     * emissions.
     */
    private static final String SHORT_TEXT_MODEL_RESOURCE =
            "/org/apache/tika/langdetect/charsoup/langdetect-short-v1-20260310.bin";

    /**
     * Inputs shorter than this many characters are routed to the short-text model
     * (when loaded). Calibrated from the FLORES per-length crossover point where
     * the short-text model outperforms the general model.
     * TODO: tune after ablation results are available.
     */
    static final int SHORT_TEXT_LENGTH_THRESHOLD = 200;

    /**
     * Inputs whose n-gram emission count is below this threshold are routed to
     * the short-text model (when loaded), regardless of character length. This
     * catches degenerate inputs such as a kilobyte of whitespace followed by a
     * single word, where raw character length would incorrectly trigger the
     * general-model path.
     * <p>
     * Calibration reference (from {@link FeatureExtractor} Javadoc):
     * ~200 chars of typical Latin prose ≈ 400 emissions, so ~2 emissions/char.
     * A threshold of 200 means "effective content below ~100 chars of prose
     * → short-text model", regardless of how much padding surrounds it.
     * <ul>
     *   <li>1 KB whitespace + "the" → ~5 emissions → short-text ✓</li>
     *   <li>1 KB whitespace + 20-char sentence → ~40 emissions → short-text ✓</li>
     *   <li>200-char dense Latin text → ~400 emissions → general model ✓</li>
     * </ul>
     * TODO: tune on real document metadata samples.
     */
    static final int SHORT_TEXT_FEATURE_THRESHOLD = 200;

    /**
     * Size (in chars) of each independent chunk evaluated during detection.
     * If the first chunk yields high entropy (junk/code), the next chunk
     * is tried, and so on, until a confident result is found or the buffer
     * is exhausted. Each chunk is preprocessed and evaluated independently
     * so that junk in one chunk does not pollute the signal in the next.
     */
    private static final int CHUNK_SIZE = 5_000;

    /**
     * Buffer length at which {@link #hasEnoughText()} returns true.
     * One chunk is more than sufficient for reliable language detection;
     * this is set to two chunks so the detector has a fallback if the
     * first chunk is junk.
     */
    private static final int ENOUGH_TEXT_LENGTH = CHUNK_SIZE * 2;

    /**
     * Maximum entropy (in bits) for a chunk to be considered "confident
     * enough" to return. If a chunk's collapsed-distribution entropy
     * exceeds this threshold, the detector moves on to the next chunk.
     * <p>
     * Typical values:
     * <ul>
     *   <li>&lt; 1.0 — clean, single-language text</li>
     *   <li>1.0–3.0 — confusable language or short text</li>
     *   <li>&gt; 3.5 — likely junk (code, OCR garbage, binary, etc.)</li>
     * </ul>
     */
    private static final float ENTROPY_THRESHOLD = 3.5f;

    /**
     * Confusable language groups — languages within the same group are nearly
     * indistinguishable by character bigrams. Their logits are combined via
     * logsumexp and assigned to the top scorer, so the model reports confidence
     * in the <em>group</em> rather than a noisy choice within it.
     */
    static final String[][] CONFUSABLE_GROUPS = ConfusableGroups.load();

    /**
     * Maps each class index to the array of all class indices in its group.
     * Built lazily after the model is loaded. Classes not in any group map
     * to a singleton array containing only themselves (no-op during collapsing).
     */
    private static int[][] GROUP_INDICES;

    /**
     * Per-class expected ScriptCategory. Built once after MODEL loads.
     * Latin-script languages map to {@link ScriptCategory#LATIN}.
     * A value of -1 means "no gate applied" (mixed/unknown script).
     */
    private static int[] CLASS_SCRIPT;

    static final CharSoupModel MODEL;
    private static final FeatureExtractor EXTRACTOR;
    private static final Set<String> SUPPORTED_LANGUAGES;

    /** Short-text model — {@code null} if the resource is not on the classpath. */
    static final CharSoupModel SHORT_TEXT_MODEL;
    static final FeatureExtractor SHORT_TEXT_EXTRACTOR;
    private static int[][] SHORT_TEXT_GROUP_INDICES;
    private static int[] SHORT_TEXT_CLASS_SCRIPT;

    static {
        try {
            MODEL = CharSoupModel.loadFromClasspath(MODEL_RESOURCE);
            EXTRACTOR = MODEL.createExtractor();
            verifyFlagsMatch(MODEL, EXTRACTOR, MODEL_RESOURCE);
            Set<String> langs = new HashSet<>();
            Collections.addAll(langs, MODEL.getLabels());
            SUPPORTED_LANGUAGES = Collections.unmodifiableSet(langs);
            GROUP_INDICES = buildGroupIndices(MODEL);
            CLASS_SCRIPT = buildClassScript(MODEL);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load built-in language model: " + MODEL_RESOURCE,
                    e);
        }

        CharSoupModel shortModel = null;
        FeatureExtractor shortExtractor = null;
        try {
            shortModel = CharSoupModel.loadFromClasspath(SHORT_TEXT_MODEL_RESOURCE);
            shortExtractor = shortModel.createExtractor();
            verifyFlagsMatch(shortModel, shortExtractor, SHORT_TEXT_MODEL_RESOURCE);
            SHORT_TEXT_GROUP_INDICES = buildGroupIndices(shortModel);
            SHORT_TEXT_CLASS_SCRIPT = buildClassScript(shortModel);
            LOG.info("Short-text language model loaded ({} languages, {} buckets)",
                    shortModel.getNumClasses(), shortModel.getNumBuckets());
        } catch (IOException e) {
            LOG.debug("Short-text language model not found ({}); using general model only",
                    SHORT_TEXT_MODEL_RESOURCE);
        }
        SHORT_TEXT_MODEL = shortModel;
        SHORT_TEXT_EXTRACTOR = shortExtractor;
    }

    /**
     * Asserts that the feature flags embedded in {@code model} exactly match the
     * flags reported by {@code extractor}.  A mismatch means the model was trained
     * with a different feature set than the one being used for inference, which
     * produces silently wrong scores.
     *
     * @throws IllegalStateException if the flags do not match
     */
    private static void verifyFlagsMatch(CharSoupModel model,
                                         FeatureExtractor extractor,
                                         String resourcePath) {
        int modelFlags     = model.getFeatureFlags();
        int extractorFlags = extractor.getFeatureFlags();
        if (modelFlags != extractorFlags) {
            throw new IllegalStateException(String.format(
                    Locale.ROOT,
                    "Feature flag mismatch for model '%s': "
                    + "model has 0x%03x but extractor reports 0x%03x. "
                    + "The model was trained with a different feature set "
                    + "than the extractor used for inference.",
                    resourcePath, modelFlags, extractorFlags));
        }
    }

    /**
     * Build a mapping from each class index to the set of class indices in its
     * confusable group. Only groups where at least 2 members are present in the
     * model are created; singletons are left as no-ops.
     */
    private static int[][] buildGroupIndices(CharSoupModel model) {
        // Build label → index map
        Map<String, Integer> labelIdx = new HashMap<>();
        for (int i = 0; i < model.getNumClasses(); i++) {
            labelIdx.put(model.getLabel(i), i);
        }

        // For each class, determine its group members (by index)
        int[][] result = new int[model.getNumClasses()][];
        boolean[] assigned = new boolean[model.getNumClasses()];

        for (String[] group : CONFUSABLE_GROUPS) {
            // Collect indices of group members present in the model
            List<Integer> members = new ArrayList<>();
            for (String lang : group) {
                Integer idx = labelIdx.get(lang);
                if (idx != null) {
                    members.add(idx);
                }
            }
            if (members.size() >= 2) {
                int[] memberArr = members.stream().mapToInt(Integer::intValue).toArray();
                for (int idx : memberArr) {
                    result[idx] = memberArr;
                    assigned[idx] = true;
                }
            }
        }

        // Singletons: classes not in any group
        for (int i = 0; i < result.length; i++) {
            if (!assigned[i]) {
                result[i] = new int[]{i};
            }
        }
        return result;
    }

    private static int[] buildClassScript(CharSoupModel model) {
        Map<String, Integer> langToScript = new HashMap<>();

        for (String l : new String[]{"rus", "ukr", "bul", "bel", "mkd", "srp", "bak", "tat",
                "sah", "chv", "bua", "kir", "myv", "mdf", "krc", "ava", "che", "oss", "kom",
                "udm", "kjh", "kum", "mrj", "chm", "inh", "kbd", "mon", "abk",
                // Turkic/Iranian languages written in Cyrillic in their Wikipedia corpora:
                "kaz", "tgk"}) {
            langToScript.put(l, ScriptCategory.CYRILLIC);
        }
        for (String l : new String[]{"ara", "fas", "urd", "pus", "ckb", "uig", "snd", "kur",
                "bal", "hau_Arab", "arz", "arb", "ary", "aeb", "acm", "acq", "ajp", "apc",
                "ars",
                // South Azerbaijani: Perso-Arabic script (distinct from North Azerbaijani azj/aze
                // which use Latin script and are merged into aze in our model)
                "azb",
                // Panjabi (Shahmukhi): Perso-Arabic script
                "pnb"}) {
            langToScript.put(l, ScriptCategory.ARABIC);
        }
        for (String l : new String[]{"zho", "yue", "wuu", "nan", "cmn", "lzh"}) {
            langToScript.put(l, ScriptCategory.HAN);
        }
        langToScript.put("jpn", ScriptCategory.HAN);
        langToScript.put("kor", ScriptCategory.HANGUL);
        for (String l : new String[]{"hin", "mar", "nep", "san", "awa", "bho", "mai", "hne",
                "mag", "new", "gom", "kok", "doi"}) {
            langToScript.put(l, ScriptCategory.DEVANAGARI);
        }
        langToScript.put("tha", ScriptCategory.THAI);
        langToScript.put("ell", ScriptCategory.GREEK);
        for (String l : new String[]{"heb", "ydd"}) {
            langToScript.put(l, ScriptCategory.HEBREW);
        }
        for (String l : new String[]{"ben", "asm", "mni"}) {
            langToScript.put(l, ScriptCategory.BENGALI);
        }
        langToScript.put("kat", ScriptCategory.GEORGIAN);
        langToScript.put("hye", ScriptCategory.ARMENIAN);
        for (String l : new String[]{"amh", "tir", "tig", "orm_Ethi", "gez"}) {
            langToScript.put(l, ScriptCategory.ETHIOPIC);
        }
        langToScript.put("iku", ScriptCategory.CANADIAN_ABORIGINAL);
        for (String l : new String[]{"mya", "ksw", "shn", "kht"}) {
            langToScript.put(l, ScriptCategory.MYANMAR);
        }
        langToScript.put("bod", ScriptCategory.TIBETAN);
        langToScript.put("khm", ScriptCategory.KHMER);
        // Distinct Indic scripts — skip gate for now
        for (String l : new String[]{"tel", "kan", "mal", "sin", "tam", "ory"}) {
            langToScript.put(l, -1);
        }

        int[] result = new int[model.getNumClasses()];
        Arrays.fill(result, ScriptCategory.LATIN);
        for (int i = 0; i < model.getNumClasses(); i++) {
            String label = model.getLabel(i);
            Integer scriptId = langToScript.get(label);
            if (scriptId != null) {
                result[i] = scriptId;
            }
        }
        return result;
    }

    /**
     * Returns the dominant ScriptCategory of the input text by letter count,
     * or -1 if fewer than 5 letters or no script reaches the 85% threshold.
     */
    private static int dominantScript(String text) {
        int[] counts = new int[ScriptCategory.COUNT];
        int totalLetters = 0;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            if (Character.isLetter(cp)) {
                counts[ScriptCategory.of((char) cp)]++;
                totalLetters++;
            }
        }
        if (totalLetters < 5) {
            return -1;
        }
        int best = 0;
        for (int s = 1; s < counts.length; s++) {
            if (counts[s] > counts[best]) {
                best = s;
            }
        }
        return ((double) counts[best] / totalLetters >= 0.85) ? best : -1;
    }

    /**
     * Logit value used to mask out classes that are incompatible with the
     * detected script. Effectively -infinity: exp(-1e30) ≈ 0.
     */
    private static final float MASKED_LOGIT = -1e30f;

    /**
     * Masks logits whose expected script does not match the dominant script of
     * {@code inputText} by setting them to {@link #MASKED_LOGIT}. No renormalization
     * is needed — logit-space operations (argmax, logsumexp) handle masked values
     * naturally. Returns {@code logits} unchanged if no dominant script is detected
     * or it is {@link ScriptCategory#OTHER}.
     */
    private static float[] applyScriptGate(float[] logits, String inputText, int[] classScript) {
        int domScript = dominantScript(inputText);
        if (domScript < 0 || domScript == ScriptCategory.OTHER) {
            return logits;
        }
        float[] gated = Arrays.copyOf(logits, logits.length);
        for (int i = 0; i < gated.length; i++) {
            boolean compatible = (classScript[i] == domScript)
                    || (domScript == ScriptCategory.HAN
                        && (classScript[i] == ScriptCategory.HIRAGANA
                            || classScript[i] == ScriptCategory.KATAKANA));
            if (!compatible) {
                gated[i] = MASKED_LOGIT;
            }
        }
        return gated;
    }

    /**
     * Collapse confusable groups in logit space using logsumexp.
     * Each group's combined logit is {@code logsumexp(group logits)}, assigned
     * to the highest-scoring member; other members are masked out.
     * Returns a new array; the input is not modified.
     */
    static float[] collapseGroups(float[] logits, int[][] groupIndices) {
        float[] collapsed = Arrays.copyOf(logits, logits.length);
        boolean[] visited = new boolean[logits.length];

        for (int i = 0; i < logits.length; i++) {
            if (visited[i] || groupIndices[i].length <= 1) {
                continue;
            }
            int[] group = groupIndices[i];
            int best = group[0];
            float maxLogit = logits[group[0]];
            for (int idx : group) {
                if (logits[idx] > maxLogit) {
                    maxLogit = logits[idx];
                    best = idx;
                }
                visited[idx] = true;
            }
            // logsumexp: max + log(sum(exp(logit - max))) for numerical stability
            float sumExp = 0f;
            for (int idx : group) {
                sumExp += Math.exp(logits[idx] - maxLogit);
            }
            float groupLogit = maxLogit + (float) Math.log(sumExp);
            for (int idx : group) {
                collapsed[idx] = (idx == best) ? groupLogit : MASKED_LOGIT;
            }
        }
        return collapsed;
    }

    /**
     * Numerically stable logsumexp over an array of logits.
     */
    private static float logSumExp(float[] logits) {
        float max = Float.NEGATIVE_INFINITY;
        for (float v : logits) {
            if (v > max) max = v;
        }
        float sumExp = 0f;
        for (float v : logits) {
            sumExp += Math.exp(v - max);
        }
        return max + (float) Math.log(sumExp);
    }

    /**
     * Shannon entropy (in bits) computed directly from logits without exposing
     * softmax probabilities. Equivalent to {@code CharSoupModel.entropy(softmax(logits))}
     * but computed as {@code (logsumexp(L) - E[L under softmax]) / ln(2)}.
     */
    private static float entropyFromLogits(float[] logits) {
        float lse = logSumExp(logits);
        double weightedSumLogits = 0.0;
        for (float l : logits) {
            weightedSumLogits += Math.exp(l - lse) * l;
        }
        return (float) ((lse - weightedSumLogits) / Math.log(2.0));
    }

    /**
     * Score of the top class via sigmoid(top_logit − logsumexp(other logits)),
     * which equals exp(top_logit − logsumexp(all logits)) — the softmax probability
     * of the top class, computed stably without materializing a probability distribution.
     */
    private static float topClassScore(float[] logits) {
        float lse = logSumExp(logits);
        float topLogit = Float.NEGATIVE_INFINITY;
        for (float v : logits) {
            if (v > topLogit) topLogit = v;
        }
        return (float) Math.exp(topLogit - lse);
    }


    private final StringBuilder buffer = new StringBuilder();
    private int maxLength = CharSoupFeatureExtractor.MAX_TEXT_LENGTH;

    /** Constructed (default) config — never null. */
    private final CharSoupDetectorConfig config;

    /**
     * Per-document effective config, set in {@link #reset(ParseContext)}.
     * May differ from {@link #config} when a caller injects a
     * {@link CharSoupDetectorConfig} via {@link ParseContext}.
     * Starts equal to {@link #config} and is reset to it on every {@link #reset()}.
     */
    private CharSoupDetectorConfig activeConfig;

    /**
     * Entropy (in bits) of the probability distribution from the most recent
     * {@link #detectAll()} call. Low entropy = confident, high entropy = uncertain/junk.
     * <p>
     * Typical values:
     * <ul>
     *   <li>&lt; 1.0 — clean, single-language text</li>
     *   <li>1.0–3.0 — confusable language or short text</li>
     *   <li>&gt; 4.0 — likely junk (OCR garbage, mojibake, binary, etc.)</li>
     * </ul>
     */
    private float lastEntropy = Float.NaN;

    /**
     * Returns the set of ISO 639-3 language codes supported by the given strategy.
     * <ul>
     *   <li>{@link Strategy#SHORT_TEXT} — labels of the short-text model (or empty if not loaded)</li>
     *   <li>{@link Strategy#STANDARD} — labels of the standard model</li>
     *   <li>{@link Strategy#AUTOMATIC} — union of both models (standard model labels as superset)</li>
     * </ul>
     */
    public static Set<String> getSupportedLanguages(Strategy strategy) {
        if (strategy == Strategy.SHORT_TEXT) {
            if (SHORT_TEXT_MODEL == null) {
                return java.util.Collections.emptySet();
            }
            return new java.util.HashSet<>(java.util.Arrays.asList(SHORT_TEXT_MODEL.getLabels()));
        }
        // STANDARD and AUTOMATIC both expose the full standard model set
        return new java.util.HashSet<>(java.util.Arrays.asList(MODEL.getLabels()));
    }

    /** Constructs a detector with default configuration ({@link Strategy#AUTOMATIC}). */
    public CharSoupLanguageDetector() {
        this(CharSoupDetectorConfig.DEFAULT);
    }

    /**
     * Constructs a detector with the supplied configuration.
     * Use {@link CharSoupDetectorConfig#fromMap(java.util.Map)} to build a config
     * from JSON-decoded values read out of a ParseContext.
     *
     * @param config immutable configuration; must not be null
     */
    public CharSoupLanguageDetector(CharSoupDetectorConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("config must not be null");
        }
        this.config = config;
        this.activeConfig = config;
    }

    /**
     * Compute the confidence level from the sigmoid-of-margin score and distribution
     * entropy. High entropy (uncertain distribution) downgrades confidence even
     * if the top score looks reasonable — this catches junk text that happens to
     * activate one class slightly more than others.
     * <p>
     * {@code score} is {@code sigmoid(logit_margin)}, so the thresholds map to:
     * <ul>
     *   <li>&gt;0.90 — logit margin &gt; 2.2 (strong discrimination)</li>
     *   <li>&gt;0.70 — logit margin &gt; 0.85 (moderate discrimination)</li>
     *   <li>&gt;0.20 — logit margin &gt; -1.4 (weak but present signal)</li>
     * </ul>
     */
    /**
     * Score threshold below which the result is {@link LanguageConfidence#NONE}
     * for the general model. Sigmoid(margin) &gt; 0.20 ≈ logit margin &gt; −1.4.
     */
    private static final float GENERAL_SCORE_FLOOR = 0.20f;

    /**
     * Score threshold below which the result is {@link LanguageConfidence#NONE}
     * for the short-text model.  At 20 chars there is inherently less signal,
     * so we accept a weaker margin before refusing to answer.
     * Sigmoid(margin) &gt; 0.10 ≈ logit margin &gt; −2.2.
     */
    private static final float SHORT_TEXT_SCORE_FLOOR = 0.10f;

    private static LanguageConfidence toConfidence(float score, float entropy,
                                                   boolean shortText) {
        if (entropy > 4.0f) {
            return LanguageConfidence.NONE;
        }
        float floor = shortText ? SHORT_TEXT_SCORE_FLOOR : GENERAL_SCORE_FLOOR;
        if (score > 0.9f) {
            return LanguageConfidence.HIGH;
        } else if (score > 0.7f) {
            return entropy < 2.0f ? LanguageConfidence.MEDIUM : LanguageConfidence.LOW;
        } else if (score > floor) {
            return LanguageConfidence.LOW;
        }
        return LanguageConfidence.NONE;
    }

    /**
     * Returns the Shannon entropy (in bits) of the probability distribution from
     * the most recent {@link #detectAll()} call, or {@link Float#NaN} if
     * {@code detectAll()} has not been called since the last {@link #reset()}.
     * <p>
     * This can be used as a junk/garbage detector: high entropy (&gt; 4.0 bits)
     * indicates the model has no confident prediction, which typically means the
     * input is not natural language text.
     *
     * @return entropy in bits, or {@link Float#NaN}
     */
    public float getDistributionEntropy() {
        return lastEntropy;
    }

    /**
     * Minimum sigmoid-of-margin score for a candidate to be considered a genuine
     * language match in {@link #compareLanguageSignal}. If no candidate exceeds
     * this threshold, the comparison is inconclusive and {@code null} is returned.
     * <p>
     * 0.30 is intentionally permissive: the underlying linear classifier is
     * discriminative, not generative, so confidence scores compress toward the
     * middle and a well-separated winner still sits well above 0.30.  A future
     * generative model should allow this threshold to be raised.
     * Typical values:
     * <ul>
     *   <li>Arabic (windows-1256): &gt; 0.99</li>
     *   <li>Short CJK (2 chars, clear winner): ~0.31</li>
     *   <li>UTF-8 garbled: skipped by junk-ratio filter</li>
     *   <li>Genuinely ambiguous text: &lt; 0.21 — below threshold</li>
     * </ul>
     */
    private static final float MIN_CONFIDENCE_THRESHOLD = 0.30f;

    /**
     * Maximum ratio of junk characters (U+FFFD replacement chars + C0/C1
     * control chars) allowed in a candidate text. Candidates exceeding
     * this ratio are discarded before language scoring — they are almost
     * certainly decoded with the wrong charset.
     * <p>
     * Typical values:
     * <ul>
     *   <li>Correct decoding: 0.00</li>
     *   <li>UTF-8 decoding of windows-1256 bytes: 0.80</li>
     *   <li>IBM500 decoding of ASCII bytes: 0.23</li>
     * </ul>
     */
    private static final float MAX_JUNK_RATIO = 0.10f;

    /**
     * Compare multiple candidate texts and return the key of the one with
     * the strongest language signal. Candidates with a high ratio of
     * replacement or control characters are discarded first. Remaining
     * candidates are scored using {@code sigmoid(top_logit − logsumexp(other logits))}
     * — the log-normalized probability of the top class, computed without materializing
     * a full softmax distribution.
     * <p>
     * Returns {@code null} if no candidate exceeds the minimum confidence
     * threshold, indicating the comparison is inconclusive.
     *
     * @param candidates map of arbitrary keys to candidate text strings
     * @param <K>        key type (e.g., {@link java.nio.charset.Charset})
     * @return the key whose text has the strongest language signal,
     *         or {@code null} if the map is empty or no candidate is
     *         confident enough
     */
    public <K> K compareLanguageSignal(Map<K, String> candidates) {
        if (candidates.isEmpty()) {
            return null;
        }

        float bestConfidence = Float.NEGATIVE_INFINITY;
        K bestKey = null;

        for (Map.Entry<K, String> entry : candidates.entrySet()) {
            float junkRatio = junkRatio(entry.getValue());
            if (junkRatio > MAX_JUNK_RATIO) {
                LOG.debug("compareLanguageSignal: {} -> skipped (junkRatio={})",
                        entry.getKey(), junkRatio);
                continue;
            }

            int[] features = EXTRACTOR.extract(entry.getValue());
            float[] logits = MODEL.predictLogits(features);
            logits = applyScriptGate(logits, entry.getValue(), CLASS_SCRIPT);
            float confidence = topClassScore(logits);

            LOG.debug("compareLanguageSignal: {} -> confidence={}",
                    entry.getKey(), confidence);

            if (confidence > bestConfidence) {
                bestConfidence = confidence;
                bestKey = entry.getKey();
            }
        }

        if (bestConfidence < MIN_CONFIDENCE_THRESHOLD) {
            LOG.debug("compareLanguageSignal: inconclusive (bestConfidence={} < {})",
                    bestConfidence, MIN_CONFIDENCE_THRESHOLD);
            return null;
        }

        return bestKey;
    }

    /**
     * Ratio of junk characters (U+FFFD replacement + ISO control + C1
     * control range U+0080-U+009F) to total characters. High values
     * indicate a wrong-charset decoding.
     */
    static float junkRatio(String text) {
        if (text == null || text.isEmpty()) {
            return 0f;
        }
        int junk = 0;
        int total = 0;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            total++;
            if (cp == 0xFFFD || (Character.isISOControl(cp) && !Character.isWhitespace(cp))) {
                junk++;
            }
        }
        return total == 0 ? 0f : (float) junk / total;
    }

    @Override
    public LanguageDetector loadModels() throws IOException {
        // Models are loaded statically; nothing to do.
        return this;
    }

    @Override
    public LanguageDetector loadModels(Set<String> languages) throws IOException {
        throw new UnsupportedOperationException(
                "This language detector does not support subsetting models");
    }

    @Override
    public boolean hasModel(String language) {
        return SUPPORTED_LANGUAGES.contains(language);
    }

    /**
     * Returns all language codes supported by the loaded model.
     *
     * @return unmodifiable set of ISO 639-3 language codes
     */
    public static Set<String> getSupportedLanguages() {
        return SUPPORTED_LANGUAGES;
    }

    /**
     * Sets the maximum text length (in characters) that will be buffered
     * for detection. Text beyond this limit is silently discarded.
     * <p>
     * The default limit is {@link CharSoupFeatureExtractor#MAX_TEXT_LENGTH}
     * (100,000 characters).
     *
     * @param maxLength maximum number of characters to buffer
     */
    public void setMaxLength(int maxLength) {
        this.maxLength = maxLength;
    }


    @Override
    public LanguageDetector setPriors(Map<String, Float> languageProbabilities) throws IOException {
        throw new UnsupportedOperationException("Priors are not supported");
    }

    @Override
    public void reset() {
        buffer.setLength(0);
        lastEntropy = Float.NaN;
        activeConfig = config;
    }

    /**
     * Reset for a new document, applying any {@link CharSoupDetectorConfig} found
     * in {@code context}. A context config overrides the instance config for the
     * duration of this document only; the next {@link #reset()} or
     * {@link #reset(ParseContext)} call restores the baseline.
     * <p>
     * Also bridges the legacy {@link #shortText} boolean: if no context config is
     * present but {@code shortText == true}, {@link Strategy#SHORT_TEXT} is applied.
     *
     * @param context parse context for the current document; may be {@code null}
     */
    @Override
    public void reset(ParseContext context) {
        reset();
        if (context != null) {
            CharSoupDetectorConfig ctxConfig = context.get(CharSoupDetectorConfig.class);
            if (ctxConfig != null) {
                activeConfig = ctxConfig;
                return;
            }
        }
        // Bridge legacy shortText hint when no explicit context config is present
        if (shortText && activeConfig.getStrategy() == Strategy.AUTOMATIC) {
            activeConfig = CharSoupDetectorConfig.fromMap(
                    Map.of("strategy", Strategy.SHORT_TEXT.name(),
                           "lengthThreshold", activeConfig.getLengthThreshold(),
                           "featureThreshold", activeConfig.getFeatureThreshold()));
        }
    }

    @Override
    public void addText(char[] cbuf, int off, int len) {
        int remaining = maxLength - buffer.length();
        if (remaining <= 0) {
            return;
        }
        int toAppend = Math.min(len, remaining);
        buffer.append(cbuf, off, toAppend);
    }

    @Override
    public boolean hasEnoughText() {
        return buffer.length() >= ENOUGH_TEXT_LENGTH;
    }

    @Override
    public List<LanguageResult> detectAll() {
        String text = buffer.toString();
        if (text.isEmpty()) {
            lastEntropy = Float.NaN;
            return Collections.singletonList(LanguageResult.NULL);
        }

        int len = text.length();
        float[] bestLogits = null;
        float bestEntropy = Float.MAX_VALUE;
        CharSoupModel bestModel = MODEL;
        int[] features = new int[EXTRACTOR.getNumBuckets()];
        int[] shortFeatures = SHORT_TEXT_MODEL != null
                ? new int[SHORT_TEXT_EXTRACTOR.getNumBuckets()] : null;

        for (int start = 0; start < len; start += CHUNK_SIZE) {
            int end = Math.min(start + CHUNK_SIZE, len);
            String chunk = text.substring(start, end);

            // Determine model routing via strategy + gates.
            // Gate 1 (length) and Gate 2 (feature density) only apply in AUTOMATIC mode.
            // Gate 2 catches degenerate inputs (e.g. 1 KB of spaces + "the") where
            // character length alone would incorrectly select the general-model path.
            int emissionCount = EXTRACTOR.extractAndCount(chunk, features);
            boolean useShortText;
            switch (activeConfig.getStrategy()) {
                case SHORT_TEXT:
                    useShortText = SHORT_TEXT_MODEL != null;
                    break;
                case STANDARD:
                    useShortText = false;
                    break;
                default: // AUTOMATIC
                    useShortText = SHORT_TEXT_MODEL != null
                            && (chunk.length() < activeConfig.getLengthThreshold()
                                || emissionCount < activeConfig.getFeatureThreshold());
                    break;
            }

            float[] logits;
            float[] collapsed;
            CharSoupModel chunkModel;
            if (useShortText) {
                SHORT_TEXT_EXTRACTOR.extractAndCount(chunk, shortFeatures);
                logits = SHORT_TEXT_MODEL.predictLogits(shortFeatures);
                logits = applyScriptGate(logits, chunk, SHORT_TEXT_CLASS_SCRIPT);
                collapsed = collapseGroups(logits, SHORT_TEXT_GROUP_INDICES);
                chunkModel = SHORT_TEXT_MODEL;
            } else {
                logits = MODEL.predictLogits(features);
                logits = applyScriptGate(logits, chunk, CLASS_SCRIPT);
                collapsed = collapseGroups(logits, GROUP_INDICES);
                chunkModel = MODEL;
            }

            float entropy = entropyFromLogits(collapsed);

            if (entropy < bestEntropy) {
                bestEntropy = entropy;
                bestLogits = collapsed;
                bestModel = chunkModel;
            }

            if (entropy < ENTROPY_THRESHOLD) {
                break; // confident enough
            }
        }

        return buildResults(bestLogits, bestEntropy, bestModel);
    }

    /**
     * Maximum meaningful entropy (bits) for normalizing confidenceScore.
     * log2(numClasses) for ~165 classes is ~7.4. We cap at 7.0 so that
     * even moderately uncertain text gets a near-zero confidenceScore.
     */
    private static final float MAX_ENTROPY = 7.0f;

    /**
     * Convert entropy to a 0-1 confidence score. Lower entropy = higher confidence.
     * Uses 1/(1+entropy) to preserve discrimination even at very low entropies,
     * unlike a linear mapping which saturates at 1.0 too quickly.
     */
    private static float entropyToConfidenceScore(float entropy) {
        return 1.0f / (1.0f + entropy);
    }

    /**
     * Build sorted LanguageResult list from collapsed logits and pre-computed entropy.
     * Each class's {@code rawScore} is {@code exp(logit_c − logsumexp(all logits))} —
     * the softmax probability of class c, computed stably without materializing the
     * full distribution. logsumexp is computed once; each per-class score is one exp().
     *
     * @param logits  collapsed (script-gated + group-collapsed) logits from the model
     *                that produced the winning chunk; length == usedModel.getNumClasses()
     * @param entropy pre-computed entropy of {@code logits}
     * @param usedModel the model (general or short-text) that produced {@code logits}
     */
    private List<LanguageResult> buildResults(float[] logits, float entropy,
                                              CharSoupModel usedModel) {
        lastEntropy = entropy;
        float confScore = entropyToConfidenceScore(lastEntropy);
        float lse = logSumExp(logits);

        List<LanguageResult> results = new ArrayList<>(usedModel.getNumClasses());
        for (int c = 0; c < usedModel.getNumClasses(); c++) {
            float score = (float) Math.exp(logits[c] - lse);
            results.add(new LanguageResult(
                    usedModel.getLabel(c),
                    toConfidence(score, lastEntropy, usedModel == SHORT_TEXT_MODEL),
                    score, confScore));
        }
        results.sort((a, b) -> Float.compare(b.getRawScore(), a.getRawScore()));

        if (results.get(0).getConfidence() == LanguageConfidence.NONE) {
            return Collections.singletonList(
                    new LanguageResult("", LanguageConfidence.NONE, 0.0f, confScore));
        }

        return results;
    }
}
