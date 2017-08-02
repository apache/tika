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
package org.apache.tika.langdetect;

import java.io.CharArrayWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.tika.language.detect.LanguageConfidence;
import org.apache.tika.language.detect.LanguageDetector;
import org.apache.tika.language.detect.LanguageNames;
import org.apache.tika.language.detect.LanguageResult;

import com.optimaize.langdetect.DetectedLanguage;
import com.optimaize.langdetect.LanguageDetectorBuilder;
import com.optimaize.langdetect.i18n.LdLocale;
import com.optimaize.langdetect.ngram.NgramExtractors;
import com.optimaize.langdetect.profiles.BuiltInLanguages;
import com.optimaize.langdetect.profiles.LanguageProfile;
import com.optimaize.langdetect.profiles.LanguageProfileReader;

/**
 * Implementation of the LanguageDetector API that uses
 * https://github.com/optimaize/language-detector
 */
public class OptimaizeLangDetector extends LanguageDetector {

	private static final int MAX_CHARS_FOR_DETECTION = 20000;
	private static final int MAX_CHARS_FOR_SHORT_DETECTION = 200;
	
	private com.optimaize.langdetect.LanguageDetector detector;
	private CharArrayWriter writer;
	private Set<String> languages;
	private Map<String, Float> languageProbabilities;
	
	public OptimaizeLangDetector() {
		super();
		
		writer = new CharArrayWriter(MAX_CHARS_FOR_DETECTION);
	}
	
	@Override
	public LanguageDetector loadModels() throws IOException {
		List<LanguageProfile> languageProfiles = new LanguageProfileReader().readAllBuiltIn();
		
		// FUTURE when the "language-detector" project supports short profiles, check if
		// isShortText() returns true and switch to those.
		
		languages = new HashSet<>();
		for (LanguageProfile profile : languageProfiles) {
			languages.add(makeLanguageName(profile.getLocale()));
		}
		
		detector = createDetector(languageProfiles);
		
		return this;

	}

	private String makeLanguageName(LdLocale locale) {
		return LanguageNames.makeName(locale.getLanguage(), locale.getScript().orNull(), locale.getRegion().orNull());
	}

	@Override
	public LanguageDetector loadModels(Set<String> languages) throws IOException {
		
		// Normalize languages.
		this.languages = new HashSet<>(languages.size());
		for (String language : languages) {
			this.languages.add(LanguageNames.normalizeName(language));
		}
		
		// TODO what happens if you request a language that has no profile?
		Set<LdLocale> locales = new HashSet<>();
		for (LdLocale locale : BuiltInLanguages.getLanguages()) {
			String languageName = makeLanguageName(locale);
			if (this.languages.contains(languageName)) {
				locales.add(locale);
			}
		}
		
		detector = createDetector(new LanguageProfileReader().readBuiltIn(locales));
		
		return this;
	}

	private com.optimaize.langdetect.LanguageDetector createDetector(List<LanguageProfile> languageProfiles) {
		// FUTURE currently the short text algorithm doesn't normalize probabilities until the end, which
		// means you can often get 0 probabilities. So we pick a very short length for this limit.
		LanguageDetectorBuilder builder = LanguageDetectorBuilder.create(NgramExtractors.standard())
				.shortTextAlgorithm(30)
		        .withProfiles(languageProfiles);
		
		if (languageProbabilities != null) {
			Map<LdLocale, Double> languageWeights = new HashMap<>(languageProbabilities.size());
			for (String language : languageProbabilities.keySet()) {
				Double priority = (double)languageProbabilities.get(language);
				languageWeights.put(LdLocale.fromString(language), priority);
			}
			
			builder.languagePriorities(languageWeights);
		}
		
		return builder.build();
	}
	
	@Override
	public boolean hasModel(String language) {
		return languages.contains(language);
	}

	@Override
	public LanguageDetector setPriors(Map<String, Float> languageProbabilities) throws IOException {
		this.languageProbabilities = languageProbabilities;
		
		loadModels(languageProbabilities.keySet());

		return this;
	}
	
	@Override
	public void reset() {
		writer.reset();
	}

	@Override
	public void addText(char[] cbuf, int off, int len) {
		if (hasEnoughText()) {
			return; // do nothing if we've already got enough text.
		}
		
		writer.write(cbuf, off, len);
		
		// FUTURE - use support to get padding char from NGramExtractors.standard().
		// We'd like to get the textPadding character from the NGramExtractor, but
		// that's not exposed. NGramExtractors.standard() returns extractor with ' '
		// as padding, so that's what we'll use here.
		writer.write(' ');
	}

        /**
         * {@inheritDoc}
         *
         * @throws IllegalStateException if no models have been loaded with
         * {@link #loadModels() } or {@link #loadModels(java.util.Set) }
         * @return the detected list of languages
         */
	@Override
	public List<LanguageResult> detectAll() {
		if(detector == null) {
                    throw new IllegalStateException("models haven't been loaded yet (forgot to call loadModels?)");
                }
		
		List<LanguageResult> result = new ArrayList<>();
		
		List<DetectedLanguage> rawResults = detector.getProbabilities(writer.toString());
		for (DetectedLanguage rawResult : rawResults) {
			// TODO figure out right level for confidence brackets.
			LanguageConfidence confidence = rawResult.getProbability() > 0.9 ? LanguageConfidence.HIGH : LanguageConfidence.MEDIUM;
			result.add(new LanguageResult(makeLanguageName(rawResult.getLocale()), confidence, (float)rawResult.getProbability()));
		}

		if (result.isEmpty()) {
			result.add(LanguageResult.NULL);
		}
		
		return result;
	}

	@Override
	public boolean hasEnoughText() {
		return writer.size() >= getTextLimit();
	}

	private int getTextLimit() {
		int limit = (shortText ? MAX_CHARS_FOR_SHORT_DETECTION : MAX_CHARS_FOR_DETECTION);
		
		// We want more text if we're processing documents that have a mixture of languages.
		// FUTURE - figure out right amount to bump up the limit.
		if (mixedLanguages) {
			limit *= 2;
		}
		
		return limit;
	}
}
