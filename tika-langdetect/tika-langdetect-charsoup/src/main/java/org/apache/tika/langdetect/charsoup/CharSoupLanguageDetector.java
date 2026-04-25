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
 * Inference uses raw logits throughout — no softmax distribution is ever computed.
     * Confidence is based on the <em>margin</em> between the top two logits after
     * confusable-group collapsing: {@code sigmoid(top_logit − second_logit)}.
     * This is invariant to the number of classes and provides a stable confidence
     * signal from short snippets up to full documents. Per-class {@code rawScore}
     * is {@code sigmoid(logit_c − best_competitor_logit)}: the winner gets a value
     * above 0.5, all others below.
 * </p>
 */
@TikaComponent(name = "charsoup-language-detector")
public class CharSoupLanguageDetector extends LanguageDetector implements SelfConfiguring {

    private static final Logger LOG =
            LoggerFactory.getLogger(CharSoupLanguageDetector.class);

    /**
     * Language model: 204 languages, 32 768 buckets, SaltedNgramFeatureExtractor.
     * Features: TRIGRAMS | 4GRAMS | SCRIPT_BLOCKS | L2_NORM | WORD_BIGRAMS | SALTED.
     */
    private static final String MODEL_RESOURCE =
            "/org/apache/tika/langdetect/charsoup/langdetect-20260320.bin";

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
        int modelFlags     = model.getFeatureFlags() & ~CharSoupModel.FLAG_L2_NORM;
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
     * Logit value used to mask out members of a confusable group that lost
     * to the group leader in {@link #collapseGroups}.
     */
    private static final float MASKED_LOGIT = -1e30f;

    /**
     * No-op: script gating is now handled entirely by the model's
     * script-salted n-gram features and explicit script-block counts.
     * Kept as a pass-through so call sites don't need to change.
     */
    private static float[] applyScriptGate(float[] logits, String inputText, int[] classScript) {
        return logits;
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
     * Sigmoid of the margin between the top two logits.
     * Invariant to the number of classes — only the gap between winner and
     * runner-up matters.  Returns 0.5 when they are tied, 1.0 when the
     * winner is infinitely far ahead.
     */
    private static float topClassScore(float[] logits) {
        float top = Float.NEGATIVE_INFINITY;
        float second = Float.NEGATIVE_INFINITY;
        for (float v : logits) {
            if (v > top) {
                second = top;
                top = v;
            } else if (v > second) {
                second = v;
            }
        }
        return sigmoid(top - second);
    }

    private static float sigmoid(float x) {
        return (float) (1.0 / (1.0 + Math.exp(-x)));
    }


    private final StringBuilder buffer = new StringBuilder();
    private int maxLength = CharSoupFeatureExtractor.MAX_TEXT_LENGTH;

    /**
     * Instance-level model fields.  When constructed via the default constructor
     * these point to the static classpath-loaded singletons.  When constructed
     * via {@link #CharSoupLanguageDetector(CharSoupModel)} they point to the
     * caller-supplied model, ensuring evaluations always use the intended
     * model.
     */
    private final CharSoupModel model;
    private final FeatureExtractor extractor;
    private final int[][] groupIndices;
    private final int[] classScript;

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

    /** Constructs a detector using the default classpath-loaded model. */
    public CharSoupLanguageDetector() {
        this.model = MODEL;
        this.extractor = EXTRACTOR;
        this.groupIndices = GROUP_INDICES;
        this.classScript = CLASS_SCRIPT;
    }

    /**
     * Constructs a detector that uses a caller-supplied model instead of the
     * classpath default.  This ensures evaluations and comparisons always run
     * against the intended model binary — not whatever happens to be on the
     * classpath.
     *
     * @param customModel the model to use for all predictions
     */
    public CharSoupLanguageDetector(CharSoupModel customModel) {
        if (customModel == null) {
            throw new IllegalArgumentException("customModel must not be null");
        }
        this.model = customModel;
        this.extractor = customModel.createExtractor();
        verifyFlagsMatch(customModel, this.extractor, "custom-model");
        this.groupIndices = buildGroupIndices(customModel);
        this.classScript = buildClassScript(customModel);
    }

    /**
     * Compute the confidence level from the sigmoid-of-margin score and
     * distribution entropy.  {@code score} is {@code sigmoid(top − second)},
     * so 0.5 = tied, 1.0 = infinitely separated.
     * <ul>
     *   <li>&gt; 0.90 — margin &gt; 2.2 (strong discrimination)</li>
     *   <li>&gt; 0.70 — margin &gt; 0.85 (moderate discrimination)</li>
     *   <li>&gt; floor — margin &gt; 0.2 / 0.08 (weak but present signal)</li>
     * </ul>
     * High entropy (&gt; 4.0 bits) forces {@link LanguageConfidence#NONE}
     * regardless of margin — the model has no real signal.
     */

    /**
     * Score threshold below which the result is {@link LanguageConfidence#NONE}.
     * Sigmoid(margin) &gt; 0.50 means the top class leads the runner-up by any
     * positive margin at all.
     */
    private static final float SCORE_FLOOR = 0.50f;

    private static LanguageConfidence toConfidence(float score, float entropy,
                                                   boolean unused) {
        if (score > 0.9f) {
            return LanguageConfidence.HIGH;
        } else if (score > 0.7f) {
            return entropy < 2.0f ? LanguageConfidence.MEDIUM : LanguageConfidence.LOW;
        } else if (score > SCORE_FLOOR) {
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
     * Minimum sigmoid(margin) for a candidate to be considered a genuine
     * language match in {@link #compareLanguageSignal}. If no candidate exceeds
     * this threshold, the comparison is inconclusive and {@code null} is returned.
     * <p>
     * 0.60 requires the top class to lead the runner-up by margin &gt; 0.4.
     * Typical values with sigmoid(margin):
     * <ul>
     *   <li>Arabic (windows-1256): &gt; 0.99</li>
     *   <li>Short CJK (2 chars, clear winner): ~0.62</li>
     *   <li>UTF-8 garbled: skipped by junk-ratio filter</li>
     *   <li>Genuinely ambiguous text: &lt; 0.55 — below threshold</li>
     * </ul>
     */
    private static final float MIN_CONFIDENCE_THRESHOLD = 0.60f;

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
     * candidates are scored using {@code sigmoid(top_logit − second_logit)}
     * — the margin between the top two classes, invariant to the number of
     * classes in the model.
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

            int[] features = extractor.extract(entry.getValue());
            float[] logits = model.predictLogits(features);
            logits = applyScriptGate(logits, entry.getValue(), classScript);
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
     * Return the top {@code n} language codes from the short-text
     * discriminative model, ranked by raw logit (descending).
     *
     * <p>Unlike {@link #detectAll()}, this method applies no entropy or
     * confidence thresholds — it always returns the model's ranking even
     * when the distribution is flat.  This is useful for downstream
     * generative-model confirmation on very short text (e.g. zip entry
     * filenames) where the discriminative model alone is inconclusive
     * but its top candidates still contain a useful language signal.</p>
     *
     * @param text the decoded text to classify
     * @param n    maximum number of language codes to return
     * @return top language codes, or empty list if the short-text model
     *         is not loaded or text is empty
     */
    public static List<String> topShortTextLanguages(String text, int n) {
        if (text == null || text.isEmpty()) {
            return Collections.emptyList();
        }
        int[] features = new int[EXTRACTOR.getNumBuckets()];
        EXTRACTOR.extractAndCount(text, features);
        float[] logits = MODEL.predictLogits(features);
        logits = applyScriptGate(logits, text, CLASS_SCRIPT);
        float[] collapsed = collapseGroups(logits, GROUP_INDICES);

        int numClasses = MODEL.getNumClasses();
        Integer[] indices = new Integer[numClasses];
        for (int i = 0; i < numClasses; i++) {
            indices[i] = i;
        }
        Arrays.sort(indices, (a, b) -> Float.compare(collapsed[b], collapsed[a]));

        List<String> result = new ArrayList<>(Math.min(n, numClasses));
        for (int i = 0; i < Math.min(n, numClasses); i++) {
            result.add(MODEL.getLabel(indices[i]));
        }
        return result;
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
        if (model != MODEL) {
            for (String label : model.getLabels()) {
                if (label.equals(language)) {
                    return true;
                }
            }
            return false;
        }
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
     * Returns the model this detector instance is using for predictions.
     * Useful for verification in evaluation tools.
     */
    public CharSoupModel getModel() {
        return model;
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
        String bestChunk = null;
        int[] features = new int[extractor.getNumBuckets()];

        for (int start = 0; start < len; start += CHUNK_SIZE) {
            int end = Math.min(start + CHUNK_SIZE, len);
            String chunk = text.substring(start, end);

            extractor.extractAndCount(chunk, features);
            float[] logits = model.predictLogits(features);
            logits = applyScriptGate(logits, chunk, classScript);
            float[] collapsed = collapseGroups(logits, groupIndices);

            float entropy = entropyFromLogits(collapsed);

            if (entropy < bestEntropy) {
                bestEntropy = entropy;
                bestLogits = collapsed;
                bestChunk = chunk;
            }

            if (entropy < ENTROPY_THRESHOLD) {
                break;
            }
        }

        return buildResults(bestLogits, bestEntropy);
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
     * Build sorted LanguageResult list from collapsed logits and pre-computed
     * entropy.  Scoring uses sigmoid(margin):
     * <ul>
     *   <li>Winner: {@code sigmoid(top_logit − second_logit)}</li>
     *   <li>Others: {@code sigmoid(logit_c − top_logit)} — always &lt; 0.5</li>
     * </ul>
     *
     * @param logits  collapsed (script-gated + group-collapsed) logits;
     *                length == MODEL.getNumClasses()
     * @param entropy pre-computed entropy of {@code logits}
     */
    private List<LanguageResult> buildResults(float[] logits, float entropy) {
        lastEntropy = entropy;
        float confScore = entropyToConfidenceScore(lastEntropy);

        int topIdx = 0;
        float topLogit = logits[0];
        float secondLogit = Float.NEGATIVE_INFINITY;
        for (int i = 1; i < logits.length; i++) {
            if (logits[i] > topLogit) {
                secondLogit = topLogit;
                topIdx = i;
                topLogit = logits[i];
            } else if (logits[i] > secondLogit) {
                secondLogit = logits[i];
            }
        }

        float topScore = sigmoid(topLogit - secondLogit);
        LanguageConfidence topConf = toConfidence(topScore, entropy, false);

        if (topConf == LanguageConfidence.NONE) {
            return Collections.singletonList(
                    new LanguageResult("", LanguageConfidence.NONE, 0.0f, confScore));
        }

        List<LanguageResult> results = new ArrayList<>(model.getNumClasses());
        for (int c = 0; c < model.getNumClasses(); c++) {
            float score;
            LanguageConfidence conf;
            if (c == topIdx) {
                score = topScore;
                conf = topConf;
            } else {
                score = sigmoid(logits[c] - topLogit);
                conf = toConfidence(score, entropy, false);
            }
            results.add(new LanguageResult(
                    model.getLabel(c), conf, score, confScore));
        }
        results.sort((a, b) -> Float.compare(b.getRawScore(), a.getRawScore()));

        return results;
    }

}
