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

import java.io.IOException;
import java.util.Locale;

import org.apache.tika.language.detect.LanguageDetector;
import org.apache.tika.language.detect.LanguageResult;
import org.apache.tika.language.translate.Translator;


public abstract class AbstractTranslator implements Translator {

    protected LanguageResult detectLanguage(String text) throws IOException {
        LanguageDetector detector = LanguageDetector.getDefaultLanguageDetector().loadModels();
        LanguageResult result = detector.detect(text);
        // Translation APIs use ISO 639-1 codes; normalize from ISO 639-3 if needed.
        String lang = result.getLanguage();
        if (lang != null && lang.length() == 3) {
            String iso1 = iso3ToIso1(lang);
            if (iso1 != null) {
                result = new LanguageResult(iso1, result.getConfidence(), result.getRawScore());
            }
        }
        return result;
    }

    private static String iso3ToIso1(String iso3) {
        for (String code : Locale.getISOLanguages()) {
            if (new Locale(code).getISO3Language().equals(iso3)) {
                return code;
            }
        }
        return null;
    }
}
