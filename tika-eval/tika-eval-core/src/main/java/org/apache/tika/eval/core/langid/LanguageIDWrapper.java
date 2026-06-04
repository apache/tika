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
package org.apache.tika.eval.core.langid;

import java.util.List;
import java.util.Set;

import org.apache.tika.eval.core.textstats.StringStatsCalculator;
import org.apache.tika.langdetect.charsoup.CharSoupLanguageDetector;
import org.apache.tika.language.detect.LanguageResult;

public class LanguageIDWrapper implements StringStatsCalculator<List<LanguageResult>> {

    static int MAX_TEXT_LENGTH = 50000;

    public LanguageIDWrapper() {
    }

    public static void setMaxTextLength(int maxContentLengthForLangId) {
        MAX_TEXT_LENGTH = maxContentLengthForLangId;
    }

    @Override
    public List<LanguageResult> calculate(String txt) {
        CharSoupLanguageDetector detector = new CharSoupLanguageDetector();
        detector.setMaxLength(MAX_TEXT_LENGTH);
        detector.addText(normalizeWhitespace(txt));
        return detector.detectAll();
    }

    /**
     * Collapse whitespace runs and trim before langid: the truncation window
     * counts whitespace, so extracts differing only in whitespace can flip the
     * detected language and pick different common-token dictionaries in an A/B
     * eval. CharSoup features are whitespace-invariant, so this only stabilizes
     * the window, not the scoring.
     */
    static String normalizeWhitespace(String txt) {
        if (txt == null) {
            return "";
        }
        return txt.replaceAll("\\s+", " ").trim();
    }


    public static Set<String> getSupportedLanguages() {
        return CharSoupLanguageDetector.getSupportedLanguages();
    }
}
