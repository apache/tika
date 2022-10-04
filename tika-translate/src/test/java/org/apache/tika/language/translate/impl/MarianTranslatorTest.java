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


import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class MarianTranslatorTest {

    private MarianTranslator translator;

    @BeforeEach
    public void setUp() {
        translator = new MarianTranslator();
    }

    @Test
    public void testTranslate_English_Romanian() throws Exception {
        assumeTrue(translator.isAvailable("en", "ro"));
        String source = "Apache Tika is a wonderful tool";
        String expected = "Apache Tika este un instrument minunat";
        String translated = translator.translate(source, "en", "ro");
        assertTrue(expected.equalsIgnoreCase(translated),
                "Translate " + source + " to " + expected + " (was " + translated + ")");
    }

    @Test
    public void testTranslate_Romanian_English() throws Exception {
        assumeTrue(translator.isAvailable("ro", "en"));
        String source = "Apache Tika este un instrument minunat";
        String expected = "Apache Tika is a wonderful tool";
        String translated = translator.translate(source, "ro", "en");
        assertTrue(expected.equalsIgnoreCase(translated),
                "Translate " + source + " to " + expected + " (was " + translated + ")");
    }

    @Test
    public void testNoConfig() throws Exception {
        String source = "Apache Tika is a wonderful tool";
        String expected =
                "Apache Tika is a wonderful tool"; // Pattern from other Translators is to return source
        String translated = translator.translate(source, "en", "zz");
        assertTrue(expected.equalsIgnoreCase(translated),
                "Translate " + source + " to " + expected + " (was " + translated + ")");
    }

}
