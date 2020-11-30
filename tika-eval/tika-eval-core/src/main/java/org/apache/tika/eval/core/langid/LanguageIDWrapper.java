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

import org.apache.tika.eval.core.textstats.StringStatsCalculator;
import org.apache.tika.langdetect.opennlp.OpenNLPDetector;
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
        OpenNLPDetector detector = new OpenNLPDetector();
        detector.setMaxLength(MAX_TEXT_LENGTH);
        detector.addText(txt);
        return detector.detectAll();
    }


    public String[] getSupportedLanguages() {
        return new OpenNLPDetector().getSupportedLanguages();
    }
}
