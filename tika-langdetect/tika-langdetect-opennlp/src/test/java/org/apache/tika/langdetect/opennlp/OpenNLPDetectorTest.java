/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.tika.langdetect.opennlp;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import org.apache.tika.langdetect.LanguageDetectorTest;
import org.apache.tika.language.detect.LanguageResult;

public class OpenNLPDetectorTest {

    static Map<String, String> OPTIMAIZE_TO_OPENNLP = new HashMap<>();

    @BeforeAll
    public static void setUp() {
        OPTIMAIZE_TO_OPENNLP.put("da", "dan");
        OPTIMAIZE_TO_OPENNLP.put("de", "deu");
        OPTIMAIZE_TO_OPENNLP.put("el", "ell");
        OPTIMAIZE_TO_OPENNLP.put("en", "eng");
        OPTIMAIZE_TO_OPENNLP.put("es", "spa");
        OPTIMAIZE_TO_OPENNLP.put("et", "est");
        OPTIMAIZE_TO_OPENNLP.put("fi", "fin");
        OPTIMAIZE_TO_OPENNLP.put("fr", "fra");
        OPTIMAIZE_TO_OPENNLP.put("it", "ita");
        OPTIMAIZE_TO_OPENNLP.put("ja", "jpn");
        OPTIMAIZE_TO_OPENNLP.put("lt", "lit");
        OPTIMAIZE_TO_OPENNLP.put("nl", "nld");
        OPTIMAIZE_TO_OPENNLP.put("pt", "por");
        OPTIMAIZE_TO_OPENNLP.put("sv", "swe");
        OPTIMAIZE_TO_OPENNLP.put("th", "tha");
        OPTIMAIZE_TO_OPENNLP.put("zh", "zho-simp");
    }

    @Test
    public void languageTests() throws Exception {
        OpenNLPDetector detector = new OpenNLPDetector();
        for (String lang : OPTIMAIZE_TO_OPENNLP.keySet()) {
            String openNLPLang = OPTIMAIZE_TO_OPENNLP.get(lang);
            detector.addText(getLangText(lang));
            List<LanguageResult> results = detector.detectAll();
            assertEquals(openNLPLang, results.get(0).getLanguage());
            detector.reset();
        }
    }

    private CharSequence getLangText(String lang) throws IOException {
        try (Reader reader = new InputStreamReader(
                LanguageDetectorTest.class.getResourceAsStream("language-tests/" + lang + ".test"),
                StandardCharsets.UTF_8)) {
            return IOUtils.toString(reader);
        }
    }

}
