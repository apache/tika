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
package org.apache.tika.langdetect.tika;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.tika.language.detect.LanguageConfidence;
import org.apache.tika.language.detect.LanguageDetector;
import org.apache.tika.language.detect.LanguageResult;

/**
 * This is Tika's original legacy, homegrown language detector.
 * As it is currently implemented, it computes vector distance
 * of trigrams between input string and language models.
 * <p>
 * Because it works only on trigrams, it is not suitable for short
 * texts.
 * <p>
 * There are better performing language detectors.  This module is still
 * here in the hopes that we'll get around to improving it, because
 * it is elegant and could be fairly trivially improved.
 */
public class TikaLanguageDetector extends LanguageDetector {

    StringBuilder sb = new StringBuilder();

    @Override
    public LanguageDetector loadModels() throws IOException {
        return this;
    }

    @Override
    public LanguageDetector loadModels(Set<String> languages) throws IOException {
        return this;
    }

    @Override
    public boolean hasModel(String language) {
        return LanguageIdentifier.getSupportedLanguages().contains(language);
    }

    /**
     * not supported
     *
     * @param languageProbabilities Map from language to probability
     * @return
     * @throws IOException
     */
    @Override
    public LanguageDetector setPriors(Map<String, Float> languageProbabilities) throws IOException {
        return null;
    }

    @Override
    public void reset() {
        sb.setLength(0);
    }

    @Override
    public void addText(char[] cbuf, int off, int len) {
        sb.append(cbuf, off, len);
    }

    @Override
    public List<LanguageResult> detectAll() {
        LanguageIdentifier langIder = new LanguageIdentifier(sb.toString());
        String lang = langIder.getLanguage();
        if (langIder.isReasonablyCertain()) {
            return Collections.singletonList(
                    new LanguageResult(lang, LanguageConfidence.MEDIUM, langIder.getRawScore()));
        }
        return Collections.EMPTY_LIST;
    }
}
