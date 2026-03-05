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
package org.apache.tika.langdetect.charsoup;

import org.apache.tika.config.TikaComponent;
import org.apache.tika.language.detect.LanguageResult;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.metadata.filter.MetadataFilterBase;

/**
 * A {@link org.apache.tika.metadata.filter.MetadataFilter} that runs
 * CharSoup language detection on the extracted text content and writes
 * the detected language and confidence into the metadata.
 *
 * <p>Configure in tika-config.xml:
 * <pre>{@code
 * <metadataFilter class="org.apache.tika.langdetect.charsoup.CharSoupMetadataFilter">
 *   <params>
 *     <param name="maxLength" type="int">10000</param>
 *   </params>
 * </metadataFilter>
 * }</pre>
 */
@TikaComponent(name = "charsoup-metadata-filter")
public class CharSoupMetadataFilter extends MetadataFilterBase {

    private int maxLength = CharSoupFeatureExtractor.MAX_TEXT_LENGTH;

    public void setMaxLength(int maxLength) {
        this.maxLength = maxLength;
    }

    @Override
    public void filter(Metadata metadata) {
        String content = metadata.get(TikaCoreProperties.TIKA_CONTENT);
        if (content == null || content.isEmpty()) {
            return;
        }
        CharSoupLanguageDetector detector = new CharSoupLanguageDetector();
        detector.setMaxLength(maxLength);
        detector.addText(content);
        LanguageResult r = detector.detect();
        metadata.set(TikaCoreProperties.TIKA_DETECTED_LANGUAGE, r.getLanguage());
        metadata.set(TikaCoreProperties.TIKA_DETECTED_LANGUAGE_CONFIDENCE, r.getConfidence().name());
        metadata.set(TikaCoreProperties.TIKA_DETECTED_LANGUAGE_CONFIDENCE_RAW, r.getRawScore());
    }
}
