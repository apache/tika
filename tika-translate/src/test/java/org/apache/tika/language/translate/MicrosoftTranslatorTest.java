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

import org.apache.tika.Tika;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

/**
 * Test cases for the {@link MicrosoftTranslator} class.
 */
public class MicrosoftTranslatorTest {
    Tika tika;
    @Before
    public void setUp() {
        tika = new Tika();
    }

    @Test
    public void testSimpleTranslate() throws Exception {
        String source = "hello";
        String expected = "salut";
        String translated = tika.translate(source, "en", "fr");
        System.err.println(tika.getTranslator().isAvailable());
        if (tika.getTranslator().isAvailable()) assertTrue("Translate " + source + " to " + expected + " (was " + translated + ")",
                expected.equalsIgnoreCase(translated));
    }

    @Test
    public void testSimpleDetectTranslate() throws Exception {
        String source = "hello";
        String expected = "salut";
        String translated = tika.translate(source, "fr");
        System.err.println(tika.getTranslator().isAvailable());
        if (tika.getTranslator().isAvailable()) assertTrue("Translate " + source + " to " + expected + " (was " + translated + ")",
                expected.equalsIgnoreCase(translated));
    }

}
