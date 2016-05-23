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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.tika.io.IOUtils;
import org.apache.tika.language.detect.LanguageConfidence;
import org.apache.tika.language.detect.LanguageDetector;
import org.apache.tika.language.detect.LanguageResult;
import org.apache.tika.language.detect.LanguageWriter;
import org.junit.Test;

public class OptimaizeLangDetectorTest extends LanguageDetectorTest {

	/*
	 * The complete list of supported languages (as of 0.5) is below.
	 * The ones we have tests for have '*' after the name.
	 * 
    af Afrikaans
    an Aragonese
    ar Arabic
    ast Asturian
    be Belarusian
    br Breton
    ca Catalan
    bg Bulgarian
    bn Bengali
    cs Czech
    cy Welsh
    da Danish *
    de German *
    el Greek *
    en English *
    es Spanish *
    et Estonian
    eu Basque
    fa Persian
    fi Finnish *
    fr French *
    ga Irish
    gl Galician
    gu Gujarati
    he Hebrew
    hi Hindi
    hr Croatian
    ht Haitian
    hu Hungarian
    id Indonesian
    is Icelandic
    it Italian *
    ja Japanese *
    km Khmer
    kn Kannada
    ko Korean
    lt Lithuanian *
    lv Latvian
    mk Macedonian
    ml Malayalam
    mr Marathi
    ms Malay
    mt Maltese
    ne Nepali
    nl Dutch *
    no Norwegian
    oc Occitan
    pa Punjabi
    pl Polish
    pt Portuguese *
    ro Romanian
    ru Russian
    sk Slovak
    sl Slovene
    so Somali
    sq Albanian
    sr Serbian
    sv Swedish *
    sw Swahili
    ta Tamil
    te Telugu
    th Thai *
    tl Tagalog
    tr Turkish
    uk Ukrainian
    ur Urdu
    vi Vietnamese
    yi Yiddish
    zh-CN Simplified Chinese * (just generic Chinese)
    zh-TW Traditional Chinese * (just generic Chinese)
	*/
	
	/**
	 * Test correct detection for the many (short) translations of the
	 * "Universal Declaration of Human Rights (Article 1)", at
	 * http://www.omniglot.com/udhr
	 * 
	 * Also make sure we get uncertain results for some set of unsupported
	 * languages.
	 * 
	 * @throws Exception
	 */
	@Test
	public void testUniversalDeclarationOfHumanRights() throws Exception {
    	LanguageDetector detector = new OptimaizeLangDetector();
    	detector.loadModels();

		LanguageWriter writer = new LanguageWriter(detector);

		Map<String, String> knownText = getTestLanguages("udhr-known.txt");
        for (String language : knownText.keySet()) {
			writer.reset();
			writer.append(knownText.get(language));
			
    		LanguageResult result = detector.detect();
    		assertNotNull(result);

            assertEquals(language, result.getLanguage());
            // System.out.println(String.format("'%s': %s (%f)", language, result.getConfidence(), result.getRawScore()));
        }
        
		Map<String, String> unknownText = getTestLanguages("udhr-unknown.txt");
        for (String language : unknownText.keySet()) {
			writer.reset();
			writer.append(unknownText.get(language));
			
    		LanguageResult result = detector.detect();
    		if (result != null) {
    			assertFalse(result.isReasonablyCertain());
                // System.out.println(String.format("Looking for '%s', got '%s': %s (%f)", language, result.getLanguage(), result.getConfidence(), result.getRawScore()));
    		}
        }
        
        writer.close();
	}
	
    @Test
    public void testAllLanguages() throws IOException {
    	LanguageDetector detector = new OptimaizeLangDetector();
    	detector.loadModels();

		LanguageWriter writer = new LanguageWriter(detector);

		for (String language : getTestLanguages()) {
			writer.reset();
			
    		writeTo(language, writer);

    		LanguageResult result = detector.detect();
    		assertNotNull(result);

    		assertTrue(result.isLanguage(language));
    		assertTrue(result.isReasonablyCertain());
    	}
    }
    
    @Test
    public void testMixedLanguages() throws IOException {
    	LanguageDetector detector = new OptimaizeLangDetector()
    		.setMixedLanguages(true);
    	
    	detector.loadModels();
		LanguageWriter writer = new LanguageWriter(detector);
    	
    	String[] languages = getTestLanguages();
        for (int i = 0; i < languages.length; i++) {
        	String language = languages[i];
        	for (int j = i + 1; j < languages.length; j++) {
        		String other = languages[j];
        		
        		writer.reset();
        		writeTo(language, writer);
        		writeTo(other, writer);
        		
        		List<LanguageResult> results = detector.detectAll();
        		if (results.size() > 0) {
        			LanguageResult result = results.get(0);

        			assertFalse("mix of " + language + " and " + other + " incorrectly detected as " + result, result.isReasonablyCertain());
        		}
        	}
        }
        
        writer.close();
    }
    
    @Test
    public void testShortText() throws IOException {
    	LanguageDetector detector = new OptimaizeLangDetector()
    		.setShortText(true)
    		.loadModels();

    	// First verify that we get no result with empty or very short text.
		LanguageWriter writer = new LanguageWriter(detector);
		writer.append("");
		assertEquals(LanguageConfidence.NONE, detector.detect().getConfidence());
		
		writer.reset();
		writer.append("  ");
		assertEquals(LanguageConfidence.NONE, detector.detect().getConfidence());

    	for (String language : getTestLanguages()) {
    		// Short pieces of Japanese are detected as Chinese
    		if (language.equals("ja")) {
    			continue;
    		}
    		
    		// We need at least 300 characters to detect Chinese reliably.
    		writer.reset();
    		writeTo(language, writer, 300);

    		LanguageResult result = detector.detect();
    		assertNotNull(String.format(Locale.US, "Language '%s' wasn't detected", language), result);

    		assertTrue(String.format(Locale.US, "Language '%s' was detected as '%s'", language, result.getLanguage()), result.isLanguage(language));
    		assertTrue(String.format(Locale.US, "Language '%s' isn't reasonably certain: %s", language, result.getConfidence()), result.isReasonablyCertain());
    	}
    	
    	writer.close();
    }
    
	private Map<String, String> getTestLanguages(String resourceName) throws IOException {
		Map<String, String> result = new HashMap<>();
		List<String> languages = IOUtils.readLines(OptimaizeLangDetectorTest.class.getResourceAsStream(resourceName));
        for (String line : languages) {
        	line = line.trim();
        	if (line.isEmpty() || line.startsWith("#")) {
        		continue;
        	}

        	String[] pieces = line.split("\t", 2);
        	if (pieces.length != 2) {
        		throw new IllegalArgumentException("Invalid language data line: " + line);
        	}
        	
        	result.put(pieces[0], pieces[1]);
        }
        
        return result;
	}
	

}
