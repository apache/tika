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
import java.util.Map;
import java.util.Set;

import org.apache.tika.config.TikaComponent;
import org.apache.tika.language.detect.LanguageConfidence;
import org.apache.tika.language.detect.LanguageDetector;
import org.apache.tika.language.detect.LanguageResult;

/**
 * Hash-based bigram language detector using INT8-quantized multinomial logistic regression.
 * <p>
 * This detector uses character bigrams with FNV-1a hashing, trained on the Leipzig Corpora
 * Collection for ~150 languages. It supports detection of RTL text that was extracted
 * left-to-right (e.g., from PDFs) via {@code xxx-x-ltr} classes.
 * </p>
 * <p>
 * Text is buffered via {@link #addText(char[], int, int)} up to
 * {@link CharSoupFeatureExtractor#MAX_TEXT_LENGTH} characters. At {@link #detectAll()} time,
 * the buffer is evaluated in independent {@value #CHUNK_SIZE}-character chunks.
 * Each chunk runs the full preprocessing pipeline (truncate → strip URLs/emails →
 * NFC normalize → extract bigram features → predict via softmax). If the first
 * chunk produces high entropy (indicating junk, code, or non-language content),
 * the next chunk is tried. The result from the chunk with the lowest entropy
 * is returned. This avoids polluting the language signal with leading junk while
 * keeping the implementation simple and predictable.
 * </p>
 */
@TikaComponent
public class CharSoupLanguageDetector extends LanguageDetector {

    private static final String MODEL_RESOURCE =
            "/org/apache/tika/langdetect/charsoup/langdetect.bin";

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
     * indistinguishable by character bigrams. Their softmax probabilities are
     * summed and assigned to the top scorer, so the model reports confidence
     * in the <em>group</em> rather than a noisy choice within it.
     */
    static final String[][] CONFUSABLE_GROUPS = {
            {"nob", "nno", "nor", "dan"},       // Scandinavian + Norwegian variants
            {"hrv", "srp", "bos", "hbs"},       // South Slavic + Serbo-Croatian
            {"msa", "zlm", "zsm", "ind"},       // Malay / Indonesian
            {"pes", "prs", "fas"},               // Persian / Dari
            {"zho", "cmn", "wuu", "yue"},        // Chinese varieties
            {"aze", "azj"},                      // Azerbaijani
            {"ekk", "est"},                      // Estonian
            {"lvs", "lav"},                      // Latvian
            {"plt", "mlg"},                      // Malagasy
            {"khk", "mon"},                      // Mongolian
            {"ydd", "yid"},                      // Yiddish
            {"sme", "smi"},                      // Sami
            {"sqi", "als"},                      // Albanian / Tosk Albanian
            {"tat", "bak"},                      // Tatar / Bashkir
            {"ita", "vec"},                      // Italian / Venetian
            {"spa", "arg", "ast"},               // Spanish / Aragonese / Asturian
    };

    /**
     * Maps each class index to the array of all class indices in its group.
     * Built lazily after the model is loaded. Classes not in any group map
     * to a singleton array containing only themselves (no-op during collapsing).
     */
    private static int[][] GROUP_INDICES;

    private static final CharSoupModel MODEL;
    private static final FeatureExtractor EXTRACTOR;
    private static final Set<String> SUPPORTED_LANGUAGES;

    static {
        try {
            MODEL = CharSoupModel.loadFromClasspath(MODEL_RESOURCE);
            EXTRACTOR = MODEL.createExtractor();
            Set<String> langs = new HashSet<>();
            Collections.addAll(langs, MODEL.getLabels());
            SUPPORTED_LANGUAGES = Collections.unmodifiableSet(langs);
            GROUP_INDICES = buildGroupIndices(MODEL);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load built-in language model: " + MODEL_RESOURCE,
                    e);
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

    /**
     * Collapse confusable group probabilities: sum each group's probabilities
     * and assign the total to the highest-scoring member. Other members get 0.
     * Returns a new array; the input is not modified.
     */
    static float[] collapseGroups(float[] probs, int[][] groupIndices) {
        float[] collapsed = Arrays.copyOf(probs, probs.length);
        boolean[] visited = new boolean[probs.length];

        for (int i = 0; i < probs.length; i++) {
            if (visited[i] || groupIndices[i].length <= 1) {
                continue;
            }
            int[] group = groupIndices[i];
            // Find the top scorer and sum
            float sum = 0f;
            int best = group[0];
            for (int idx : group) {
                sum += probs[idx];
                if (probs[idx] > probs[best]) {
                    best = idx;
                }
                visited[idx] = true;
            }
            // Assign sum to top scorer, zero the rest
            for (int idx : group) {
                collapsed[idx] = (idx == best) ? sum : 0f;
            }
        }
        return collapsed;
    }

    private final StringBuilder buffer = new StringBuilder();
    private int maxLength = CharSoupFeatureExtractor.MAX_TEXT_LENGTH;

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

    public CharSoupLanguageDetector() {
    }

    /**
     * Compute the confidence level from the top softmax score and distribution
     * entropy. High entropy (uncertain distribution) downgrades confidence even
     * if the top score looks reasonable — this catches junk text that happens to
     * activate one class slightly more than others.
     */
    private static LanguageConfidence toConfidence(float score, float entropy) {
        // High entropy means the model is guessing — likely junk
        if (entropy > 4.0f) {
            return LanguageConfidence.NONE;
        }
        if (score > 0.9f) {
            return LanguageConfidence.HIGH;
        } else if (score > 0.7f) {
            return entropy < 2.0f ? LanguageConfidence.MEDIUM : LanguageConfidence.LOW;
        } else if (score > 0.2f) {
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
        float[] bestProbs = null;
        float bestEntropy = Float.MAX_VALUE;

        for (int start = 0; start < len; start += CHUNK_SIZE) {
            int end = Math.min(start + CHUNK_SIZE, len);
            String chunk = text.substring(start, end);

            int[] features = EXTRACTOR.extract(chunk);
            float[] probs = MODEL.predict(features);
            float[] collapsed = collapseGroups(probs, GROUP_INDICES);
            float entropy = CharSoupModel.entropy(collapsed);

            if (entropy < bestEntropy) {
                bestEntropy = entropy;
                bestProbs = probs;
            }

            if (entropy < ENTROPY_THRESHOLD) {
                break; // confident enough
            }
        }

        return buildResults(bestProbs);
    }

    /**
     * Build sorted LanguageResult list from raw probabilities.
     */
    private List<LanguageResult> buildResults(float[] probs) {
        // Compute entropy on collapsed distribution
        float[] collapsed = collapseGroups(probs, GROUP_INDICES);
        lastEntropy = CharSoupModel.entropy(collapsed);

        // Build results from raw probabilities sorted by probability descending
        List<LanguageResult> results = new ArrayList<>(MODEL.getNumClasses());
        for (int c = 0; c < MODEL.getNumClasses(); c++) {
            results.add(new LanguageResult(
                    MODEL.getLabel(c), toConfidence(probs[c], lastEntropy), probs[c]));
        }
        results.sort((a, b) -> Float.compare(b.getRawScore(), a.getRawScore()));

        // If top score is below NONE threshold, return NULL
        if (results.get(0).getConfidence() == LanguageConfidence.NONE) {
            return Collections.singletonList(LanguageResult.NULL);
        }

        return results;
    }
}
