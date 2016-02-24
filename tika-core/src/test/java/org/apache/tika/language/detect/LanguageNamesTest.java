package org.apache.tika.language.detect;

import static org.junit.Assert.*;

import org.junit.Test;

public class LanguageNamesTest {

	@Test
	public void test() {
		
		// macro language + language == language
		String languageA = LanguageNames.normalizeName("zh-yue");
		String languageB = LanguageNames.normalizeName("yue");
		assertTrue(LanguageNames.equals(languageA, languageB));
		
		// TODO verify that "en-Latn" == "en"
		
		// TODO verify that "en-GB" == "en"???
	}

}
