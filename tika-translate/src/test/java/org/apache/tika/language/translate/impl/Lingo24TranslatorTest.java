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
package org.apache.tika.language.translate.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Test harness for the {@link Lingo24Translator}.
 */
public class Lingo24TranslatorTest {

    private Lingo24Translator translator;

    @BeforeEach
    public void setUp() {
        translator = new Lingo24Translator();
    }

    @Test
    public void testSimpleTranslate() {
        String source = "Hola, hoy es un día genial para traducir";
        String expected = "Hello, today is a great day to translate";

        String result = null;
        if (translator.isAvailable()) {
            try {
                result = translator.translate(source, "es", "en");
                assertNotNull(result);
                assertEquals(expected, result,
                        "Result: [" + result + "]: not equal to expected: [" + expected + "]");
            } catch (Exception e) {
                e.printStackTrace();
                fail(e.getMessage());
            }
        }
    }

    @Test
    public void testTranslateGuessLanguage() {
        String source = "C'est une merveilleuse journée pour traduction";
        String expected = "It is a wonderful day for translation";

        String result = null;
        if (translator.isAvailable()) {
            try {
                result = translator.translate(source, "en");
                assertNotNull(result);
                assertEquals(expected, result,
                        "Result: [" + result + "]: not equal to expected: [" + expected + "]");
            } catch (Exception e) {
                e.printStackTrace();
                fail(e.getMessage());
            }
        }

    }

}

