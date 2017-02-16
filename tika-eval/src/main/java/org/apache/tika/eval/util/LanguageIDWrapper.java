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
import java.util.List;

import com.google.common.base.Optional;
import com.optimaize.langdetect.DetectedLanguage;
import com.optimaize.langdetect.LanguageDetector;
import com.optimaize.langdetect.LanguageDetectorBuilder;
import com.optimaize.langdetect.i18n.LdLocale;
import com.optimaize.langdetect.ngram.NgramExtractors;
import com.optimaize.langdetect.profiles.LanguageProfile;
import com.optimaize.langdetect.profiles.LanguageProfileReader;
import com.optimaize.langdetect.text.CommonTextObjectFactories;
import com.optimaize.langdetect.text.TextObjectFactory;


public class LanguageIDWrapper {
    static List<LanguageProfile> languageProfiles;
    static LanguageDetector detector;
    static TextObjectFactory textObjectFactory;

    public static void loadBuiltInModels() throws IOException {

        languageProfiles = new LanguageProfileReader().readAllBuiltIn();
        detector = LanguageDetectorBuilder.create(NgramExtractors.standard())
                .withProfiles(languageProfiles)
                .build();
        textObjectFactory = CommonTextObjectFactories.forDetectingOnLargeText();
    }

    public static void loadModels(Path path) throws IOException {

        languageProfiles = new LanguageProfileReader().readAll(path.toFile());
        detector = LanguageDetectorBuilder.create(NgramExtractors.standard())
                .withProfiles(languageProfiles)
                .build();
        textObjectFactory = CommonTextObjectFactories.forDetectingOnLargeText();
    }



    public static Optional<LdLocale> detect(String s) {
        return detector.detect(textObjectFactory.forText(s));
    }

    public static List<DetectedLanguage> getProbabilities(String s) {

        return detector.getProbabilities(textObjectFactory.forText(s));
    }

}
