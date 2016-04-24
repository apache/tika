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

import java.util.Locale;

/**
 * Support for language tags (as defined by https://tools.ietf.org/html/bcp47)
 * 
 * See https://en.wikipedia.org/wiki/List_of_ISO_639-3_codes for a list of
 * three character language codes.
 * 
 * TODO change to LanguageTag, and use these vs. strings everywhere in the
 * language detector API?
 *
 */
public class LanguageNames {

	public static String makeName(String language, String script, String region) {
		Locale locale = new Locale.Builder().setLanguage(language).setScript(script).setRegion(region).build();
		return locale.toLanguageTag();
	}

	public static String normalizeName(String languageTag) {
		Locale locale = Locale.forLanguageTag(languageTag);
		return locale.toLanguageTag();
	}
	
	public static boolean isMacroLanguage(String languageTag) {
		Locale locale = Locale.forLanguageTag(languageTag);
		// TODO make it so.
		return false;
	}
	
	public static boolean hasMacroLanguage(String languageTag) {
		Locale locale = Locale.forLanguageTag(languageTag);
		// TODO make it so
		return false;
	}
	
	/**
	 * If language is a specific variant of a macro language (e.g. 'nb' for Norwegian Bokmal),
	 * return the macro language (e.g. 'no' for Norwegian). If it doesn't have a macro language,
	 * return unchanged.
	 * 
	 * @param languageTag
	 * @return
	 */
	public static String getMacroLanguage(String languageTag) {
		// TODO make it so
		return languageTag;
	}
	
	public static boolean equals(String languageTagA, String languageTagB) {
		Locale localeA = Locale.forLanguageTag(languageTagA);
		Locale localeB = Locale.forLanguageTag(languageTagB);
		
		// TODO Fill in script if missing and something we could derive from lang+region
		// e.g. zh-CN => zh-Hans-CN, zh-TW => zh-Hant-TW.
		
		// TODO Treat missing script == present script, if present script is default (suppressed) for
		// the language. So "en-Latn" == "en"
		
		// TODO probably OK to ignore extensions
		
		// TODO Do we want/need a fuzzy match for region (and script)
		// E.g. are 'en' and 'en-GB' equal? Depends on the direction, e.g. if you want 'en', and
		// you get back something more specific (en-GB) then that's OK, but if you explicitly want
		// en-GB and you get back en then that might not be OK.
		return localeA.equals(localeB);
	}
}
