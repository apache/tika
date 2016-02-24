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

package org.apache.tika.language.translate;

import java.io.IOException;
import java.util.HashMap;

import org.apache.tika.exception.TikaException;
import org.apache.tika.language.detect.LanguageResult;

import com.fasterxml.jackson.databind.util.LRUMap;

/**
 * CachedTranslator. Saves a map of previous translations in order to prevent repetitive translation requests.
 */
public class CachedTranslator extends AbstractTranslator {
    private static final int INITIAL_ENTRIES = 100;
    private static final int MAX_ENTRIES = 1000;
    private Translator translator;
    // The cache is a map from sourceLang:targetLang to an LRUMap of previously translated pairs.
    // Old entries are removed from the cache when it reaches its limit.
    // For example, {en:fr -> {hello -> salut}}.
    private HashMap<String, LRUMap<String, String>> cache;
    
    /**
     * Create a new CachedTranslator (must set the {@link Translator} with {@link #setTranslator(Translator)} before use!)
     */
    public CachedTranslator(){
    	this(null);
    }

    /**
     * Create a new CachedTranslator.
     *
     * @param translator The translator that should be used for the underlying translation service. The properties
     *                   for that service must be set properly!
     */
    public CachedTranslator(Translator translator) {
        this.translator = translator;
        this.cache = new HashMap<>();
    }

    /**
	 * @return the translator
	 */
	public Translator getTranslator() {
		return translator;
	}

	/**
	 * @param translator the translator to set
	 */
	public void setTranslator(Translator translator) {
		this.translator = translator;
	}

	@Override
    public String translate(String text, String sourceLanguage, String targetLanguage) throws TikaException, IOException {
        if (translator == null) {
            return text;
        }
        LRUMap<String, String> translationCache = getTranslationCache(sourceLanguage, targetLanguage);
        String translatedText = translationCache.get(text);
        if (translatedText == null) {
            translatedText = translator.translate(text, sourceLanguage, targetLanguage);
            translationCache.put(text, translatedText);
        }
        return translatedText;
    }

    @Override
    public String translate(String text, String targetLanguage) throws TikaException, IOException {
        LanguageResult language = detectLanguage(text);
        String sourceLanguage = language.getLanguage();
        return translate(text, sourceLanguage, targetLanguage);
    }

    @Override
    public boolean isAvailable() {
        return translator != null && translator.isAvailable();
    }

    /**
     * Get the number of different source/target translation pairs this CachedTranslator
     * currently has in its cache.
     *
     * @return Number of translation source/target pairs in this CachedTranslator's cache.
     * @since Tika 1.6
     */
    public int getNumTranslationPairs() {
        return cache.size();
    }

    /**
     * Get the number of different translations from the source language to the target language
     * this CachedTranslator has in its cache.
     *
     * @param sourceLanguage The source language of translation.
     * @param targetLanguage The target language of translation.
     * @return The number of translations between source and target.
     * @since Tika 1.6
     */
    public int getNumTranslationsFor(String sourceLanguage, String targetLanguage) {
        LRUMap<String, String> translationCache = cache.get(buildCacheKeyString(sourceLanguage, targetLanguage));
        if (translationCache == null) {
            return 0;
        } else {
            return translationCache.size();
        }
    }

    /**
     * Check whether this CachedTranslator's cache contains a translation of the text from the
     * source language to the target language.
     *
     * @param text What string to check for.
     * @param sourceLanguage The source language of translation.
     * @param targetLanguage The target language of translation.
     * @return true if the cache contains a translation of the text, false otherwise.
     */
    public boolean contains(String text, String sourceLanguage, String targetLanguage) {
        LRUMap<String, String> translationCache = getTranslationCache(sourceLanguage, targetLanguage);
        return translationCache.get(text) != null;
    }

    /**
     * Check whether this CachedTranslator's cache contains a translation of the text to the target language,
     * attempting to auto-detect the source language.
     *
     * @param text What string to check for.
     * @param targetLanguage The target language of translation.
     * @return true if the cache contains a translation of the text, false otherwise.
     */
    public boolean contains(String text, String targetLanguage) {
		try {
			LanguageResult language = detectLanguage(text);
	        String sourceLanguage = language.getLanguage();
	        return contains(text, sourceLanguage, targetLanguage);
		} catch (IOException e) {
			// TODO what to do if we get an error?
			return false;
		}
    }

    /**
     * Build the String to be used as the key into this CachedTranslator's cache.
     *
     * @param sourceLanguage The source language of translation.
     * @param targetLanguage The target language of translation.
     * @return The string to be used as the key into this CachedTranslator's cache.
     */
    private String buildCacheKeyString(String sourceLanguage, String targetLanguage) {
        return sourceLanguage + ":" + targetLanguage;
    }

    /**
     * Get the cache of translations from the given source language to target language.
     *
     * @param sourceLanguage The source language of translation.
     * @param targetLanguage The target language of translation.
     * @return The LRUMap representing the translation cache.
     */
    private LRUMap<String, String> getTranslationCache(String sourceLanguage, String targetLanguage) {
        LRUMap<String, String> translationCache = cache.get(buildCacheKeyString(sourceLanguage, targetLanguage));
        if (translationCache == null) {
            translationCache = new LRUMap<>(INITIAL_ENTRIES, MAX_ENTRIES);
            cache.put(buildCacheKeyString(sourceLanguage, targetLanguage), translationCache);
        }
        return translationCache;
    }
}
