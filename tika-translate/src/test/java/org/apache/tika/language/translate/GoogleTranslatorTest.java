/**
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

import org.junit.Before;

import junit.framework.TestCase;

/**
 * Test harness for the {@link GoogleTranslator}.
 * 
 */
public class GoogleTranslatorTest extends TestCase {

	private GoogleTranslator translator;

	public GoogleTranslatorTest() {

	}

	@Before
	public void setUp() {
		translator = new GoogleTranslator();
	}

	public void testSimpleTranslate() {
		String source = "hola senor";
		String expected = "hello sir";

		String result = null;
		if (translator.isAvailable()) {
			try {
				result = translator.translate(source, "es", "en");
				assertNotNull(result);
				assertEquals("Result: [" + result
						+ "]: not equal to expected: [" + expected + "]",
						expected, result);
			} catch (Exception e) {
				e.printStackTrace();
				fail(e.getMessage());
			}
		}
	}

	public void testTranslateGuessLanguage() {
		String source = "bonjour comment vas-tu";
		String expected = "hello how are you";

		String result = null;
		if (translator.isAvailable()) {
			try {
				result = translator.translate(source, "en");
				assertNotNull(result);
				assertEquals("Result: [" + result
						+ "]: not equal to expected: [" + expected + "]",
						expected, result);
			} catch (Exception e) {
				e.printStackTrace();
				fail(e.getMessage());
			}
		}

	}

}
