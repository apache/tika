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
package org.apache.tika.language.detect;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.tika.config.ServiceLoader;
import org.apache.tika.utils.CompareUtils;

// We should use the IANA registry for primary language names...see
// http://www.iana.org/assignments/language-subtag-registry/language-subtag-registry
// There must be a package that uses this dataset to support knowledge of
// the default script, etc. And how to map from <lang>-<country> (e.g. 'zh-CN')
// to <sublang> ('cmn'), or <lang>-<sublang> to <sublan> ('zh-cmn' => 'cmn')
// We'd also want to know the default sublang for a macro language ('zh' => 'zh-cmn')
// There's also mapping 'zh-CN' to 'cmn-Hans' (simplified chinese script)

// TODO decide how deep to go into supporting extended language tags, see
// http://www.w3.org/International/articles/language-tags/. For example,
// what should you expect from calling hasModel("en-GB") if there's only
// a model for "en"?

// This is mostly an issue for interpreting language tags in (X)HTML docs,
// and maybe XML if we really care. In those cases you could get something
// like "ast" (three letter language code), or even zh-cmn-Hant-SG
// (Chinese, Mandarin, Traditional script, in Singapore) plus additional:
// language-extlang-script-region-variant-extension-privateuse

// The full spec is at http://www.rfc-editor.org/rfc/bcp/bcp47.txt

public abstract class LanguageDetector {

    private static final ServiceLoader DEFAULT_SERVICE_LOADER = new ServiceLoader();

    //if a user calls detect on a huge string, break it into this size
    //and add sequentially until hasEnoughText() is true
    private static final int BUFFER_LENGTH = 4096;

    // True if text is expected to be a mix of languages, and thus higher-resolution
    // detection must be done to avoid under-sampling the text.
    protected boolean mixedLanguages = false;

    // True if the text is expected to be 'short' (typically less than 100 chars), and
    // thus a different algorithm and/or set of profiles should be used.
    protected boolean shortText = false;

    public static LanguageDetector getDefaultLanguageDetector() {
        List<LanguageDetector> detectors = getLanguageDetectors();
        if (detectors.isEmpty()) {
            throw new IllegalStateException("No language detectors available");
        } else {
            return detectors.get(0);
        }
    }

    public static List<LanguageDetector> getLanguageDetectors() {
        return getLanguageDetectors(DEFAULT_SERVICE_LOADER);
    }

    public static List<LanguageDetector> getLanguageDetectors(ServiceLoader loader) {
        List<LanguageDetector> detectors =
                loader.loadStaticServiceProviders(LanguageDetector.class);
        detectors.sort(CompareUtils::compareClassName);
        return detectors;
    }

    public boolean isMixedLanguages() {
        return mixedLanguages;
    }

    public LanguageDetector setMixedLanguages(boolean mixedLanguages) {
        this.mixedLanguages = mixedLanguages;
        return this;
    }

    public boolean isShortText() {
        return shortText;
    }

    public LanguageDetector setShortText(boolean shortText) {
        this.shortText = shortText;
        return this;
    }

    /**
     * Load (or re-load) all available language models. This must
     * be called after any settings that would impact the models
     * being loaded (e.g. mixed language/short text), but
     * before any of the document processing routines (below)
     * are called. Note that it only needs to be called once.
     *
     * @return this
     */
    public abstract LanguageDetector loadModels() throws IOException;

    /**
     * Load (or re-load) the models specified in <languages>. These use the
     * ISO 639-1 names, with an optional "-<country code>" for more
     * specific specification (e.g. "zh-CN" for Chinese in China).
     *
     * @param languages list of target languages.
     * @return this
     */
    public abstract LanguageDetector loadModels(Set<String> languages) throws IOException;

    /**
     * Provide information about whether a model exists for a specific
     * language.
     *
     * @param language ISO 639-1 name for language
     * @return true if a model for this language exists.
     */
    public abstract boolean hasModel(String language);

    /**
     * Set the a-priori probabilities for these languages. The provided map uses the language
     * as the key, and the probability (0.0 > probability < 1.0) of text being in that language.
     * Note that if the probabilities don't sum to 1.0, these values will be normalized.
     * <p>
     * If hasModel() returns false for any of the languages, an IllegalArgumentException is thrown.
     * <p>
     * Use of these probabilities is detector-specific, and thus might not impact the results  at
     * all. As such, these should be viewed as a hint.
     *
     * @param languageProbabilities Map from language to probability
     * @return this
     */
    public abstract LanguageDetector setPriors(Map<String, Float> languageProbabilities)
            throws IOException;

    // ============================================================
    // The routines below are called when processing a document
    // ============================================================

    /**
     * Reset statistics about the current document being processed
     */
    public abstract void reset();

    /**
     * Add statistics about this text for the current document. Note
     * that we assume an implicit word break exists before/after
     * each of these runs of text.
     *
     * @param cbuf Character buffer
     * @param off  Offset into cbuf to first character in the run of text
     * @param len  Number of characters in the run of text.
     */
    public abstract void addText(char[] cbuf, int off, int len);

    /**
     * Add <text> to the statistics being accumulated for the current
     * document. Note that this is a default implementation for adding
     * a string (not optimized)
     *
     * @param text Characters to add to current statistics.
     */
    public void addText(CharSequence text) {
        int len = text.length();
        if (len < BUFFER_LENGTH) {
            char[] chars = text.toString().toCharArray();
            addText(chars, 0, chars.length);
            return;
        }
        int start = 0;
        while (! hasEnoughText() && start < len) {
            int end = Math.min(start + BUFFER_LENGTH, len);
            char[] chars = text.subSequence(start, end).toString().toCharArray();
            addText(chars, 0, chars.length);
            start += BUFFER_LENGTH;
        }

    }


    /**
     * Tell the caller whether more text is required for the current document
     * before the language can be reliably detected.
     * <p>
     * Implementations can override this to do early termination of stats
     * collection, which can improve performance with longer documents.
     * <p>
     * Note that detect() can be called even when this returns false
     *
     * @return true if we have enough text for reliable detection.
     */
    public boolean hasEnoughText() {
        return false;
    }

    /**
     * Detect languages based on previously submitted text (via addText calls).
     *
     * @return list of all possible languages with at least medium confidence,
     * sorted by confidence from highest to lowest. There will always
     * be at least one result, which might have a confidence of NONE.
     */
    public abstract List<LanguageResult> detectAll();

    public LanguageResult detect() {
        List<LanguageResult> results = detectAll();
        return results.get(0);
    }

    /**
     * Utility wrapper that detects the language of a given chunk of text.
     *
     * @param text String to add to current statistics.
     * @return list of all possible languages with at least medium confidence,
     * sorted by confidence from highest to lowest.
     */
    public List<LanguageResult> detectAll(String text) {
        reset();
        addText(text);
        return detectAll();
    }

    public LanguageResult detect(CharSequence text) {
        reset();
        addText(text);
        return detect();
    }

}
