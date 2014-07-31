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

import junit.framework.TestCase;
import org.junit.Before;

/**
 * Test harness for the {@link org.apache.tika.language.translate.Lingo24Translator}.
 *
 */
public class Lingo24TranslatorTest extends TestCase {

    private Lingo24Translator translator;

    @Before
    public void setUp() {
        translator = new Lingo24Translator();
    }

    public void testSimpleTranslate() {
        String source = "Hola, hoy es un día genial para traducir";
        String expected = "Hello, today is a great day to translate";

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
        String source = "C'est une merveilleuse journée pour traduction";
        String expected = "It is a wonderful day for translation";

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

