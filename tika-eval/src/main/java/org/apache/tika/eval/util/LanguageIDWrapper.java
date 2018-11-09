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
package org.apache.tika.eval.util;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import com.google.common.base.Optional;
import com.optimaize.langdetect.DetectedLanguage;
import com.optimaize.langdetect.LanguageDetector;
import com.optimaize.langdetect.LanguageDetectorBuilder;
import com.optimaize.langdetect.i18n.LdLocale;
import com.optimaize.langdetect.ngram.NgramExtractors;
import com.optimaize.langdetect.profiles.LanguageProfile;
import com.optimaize.langdetect.profiles.LanguageProfileReader;
import com.optimaize.langdetect.text.MultiTextFilter;
import com.optimaize.langdetect.text.RemoveMinorityScriptsTextFilter;
import com.optimaize.langdetect.text.TextFilter;
import com.optimaize.langdetect.text.TextObject;
import com.optimaize.langdetect.text.TextObjectFactory;


public class LanguageIDWrapper {
    static List<LanguageProfile> languageProfiles;
    static LanguageDetector detector;
    static TextObjectFactory textObjectFactory;

    static int MAX_TEXT_LENGTH = 50000;

    public static void loadBuiltInModels() throws IOException {

        languageProfiles = new LanguageProfileReader().readAllBuiltIn();
        detector = LanguageDetectorBuilder.create(NgramExtractors.standard())
                .withProfiles(languageProfiles)
                .build();
        textObjectFactory = buildTextObjectFactory();
    }

    public static void loadModels(Path path) throws IOException {

        languageProfiles = new LanguageProfileReader().readAll(path.toFile());
        detector = LanguageDetectorBuilder.create(NgramExtractors.standard())
                .withProfiles(languageProfiles)
                .build();
        textObjectFactory = buildTextObjectFactory();
    }

    private static TextObjectFactory buildTextObjectFactory() {
        List<TextFilter> textFilters = new ArrayList<>();
        textFilters.add(TikasUrlTextFilter.getInstance());
        textFilters.add(RemoveMinorityScriptsTextFilter.forThreshold(0.3));
        return new TextObjectFactory(new MultiTextFilter(textFilters), MAX_TEXT_LENGTH);
    }


    public static Optional<LdLocale> detect(String s) {
        return detector.detect(textObjectFactory.forText(s));
    }

    public static List<DetectedLanguage> getProbabilities(String s) {
        TextObject textObject = textObjectFactory.forText(s);
        return detector.getProbabilities(textObject);
    }

    public static void setMaxTextLength(int maxTextLength) {
        MAX_TEXT_LENGTH = maxTextLength;
    }

    private static class TikasUrlTextFilter implements TextFilter {
        //use this custom copy/paste of optimaize to avoid long, long hang with mail_regex
        //TIKA-2777
        private static final Pattern URL_REGEX = Pattern.compile("https?://[-_.?&~;+=/#0-9A-Za-z]{10,10000}");
        private static final Pattern MAIL_REGEX = Pattern.compile("[-_.0-9A-Za-z]{1,100}@[-_0-9A-Za-z]{1,100}[-_.0-9A-Za-z]{1,100}");
        private static final TikasUrlTextFilter INSTANCE = new TikasUrlTextFilter();

        public static TikasUrlTextFilter getInstance() {
            return INSTANCE;
        }

        private TikasUrlTextFilter() {
        }

        public String filter(CharSequence text) {
            String modified = URL_REGEX.matcher(text).replaceAll(" ");
            return MAIL_REGEX.matcher(modified).replaceAll(" ");
        }
    }
}
