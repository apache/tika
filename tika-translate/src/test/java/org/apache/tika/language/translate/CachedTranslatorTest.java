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

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Test harness for the {@link CachedTranslator}. Take care to choose your target language carefully
 * if you're testing the size of the cache!
 */
public class CachedTranslatorTest {
    private CachedTranslator cachedTranslator;

    @Before
    public void setUp(){
        cachedTranslator = new CachedTranslator(new GoogleTranslator());
    }

    @Test
    public void testCachingSingleString() throws Exception {
        for (int i = 0; i < 20; i++) {
            cachedTranslator.translate("This is a test string to translate!", "en", "sv");
        }
        assertEquals("Cache doesn't have a single translation pair!", cachedTranslator.getNumTranslationPairs(), 1);
        assertEquals("Cache has more than one element!", cachedTranslator.getNumTranslationsFor("en", "sv"), 1);
    }

    @Test
    public void testCachingTwoStrings() throws Exception {
        for (int i = 0; i < 20; i++) {
            cachedTranslator.translate("This is a test string to translate!", "en", "no");
            cachedTranslator.translate("This is a different string...", "en", "fr");
        }
        assertEquals("Cache doesn't have two translation pairs!", cachedTranslator.getNumTranslationPairs(), 2);
        assertEquals("Cache has more than en to no translation!", cachedTranslator.getNumTranslationsFor("en", "no"), 1);
        assertEquals("Cache has more than en to fr translation!", cachedTranslator.getNumTranslationsFor("en", "fr"), 1);
    }

    @Test
    public void testSimpleTranslate() throws Exception {
        String source = "hola senor";
        String expected = "hello sir";

        if (cachedTranslator.isAvailable()) {
            String result = cachedTranslator.translate(source, "es", "en");
            assertNotNull(result);
            assertEquals("Result: [" + result
                            + "]: not equal to expected: [" + expected + "]",
                    expected, result);
        }
    }

    @Test
    public void testCacheContains() throws Exception {
        String text = "Text that should be long enough to detect a language from.";
        assertFalse("Cache should not contain a translation!",
                cachedTranslator.contains(text, "en", "it"));
        cachedTranslator.translate(text, "en", "it");
        assertTrue("Cache should contain a translation!",
                cachedTranslator.contains(text, "en", "it"));
        assertTrue("Cache should detect source language when checking if contains.",
                cachedTranslator.contains(text, "it"));
    }
}
