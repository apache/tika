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

package org.apache.tika.language.translate.impl;

import org.junit.Before;
import org.junit.Test;

import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import org.apache.tika.language.translate.impl.RTGTranslator;


/**
 * Test harness for the {@link RTGTranslator}.
 * 
 */
public class RTGTranslatorTest {

	private RTGTranslator translator;


	@Before
	public void setUp() {
		translator = new RTGTranslator();
	}

	@Test
	public void testSimpleTranslate() {
		String source = "hola señor";
		String expected = "hello, sir.";

		String result = null;
		if (translator.isAvailable()) {
			try {
				result = translator.translate(source);
				assertNotNull(result);
				assertEquals("Result: [" + result
						+ "]: not equal to expected: [" + expected + "]",
					     expected, result.toLowerCase(Locale.getDefault()));
			} catch (Exception e) {
				e.printStackTrace();
				fail(e.getMessage());
			}
		}
	}

}
