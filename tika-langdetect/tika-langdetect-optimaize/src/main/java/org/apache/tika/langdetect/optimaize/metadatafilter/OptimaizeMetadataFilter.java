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
package org.apache.tika.langdetect.optimaize.metadatafilter;

import org.apache.tika.config.Field;
import org.apache.tika.exception.TikaException;
import org.apache.tika.langdetect.optimaize.OptimaizeLangDetector;
import org.apache.tika.language.detect.LanguageResult;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.metadata.filter.MetadataFilter;

public class OptimaizeMetadataFilter extends MetadataFilter {

    private int maxCharsForDetection = OptimaizeLangDetector.DEFAULT_MAX_CHARS_FOR_DETECTION;

    @Field
    public void setMaxCharsForDetection(int maxCharsForDetection) {
        this.maxCharsForDetection = maxCharsForDetection;
    }

    @Override
    public void filter(Metadata metadata) throws TikaException {
        OptimaizeLangDetector detector = new OptimaizeLangDetector(maxCharsForDetection);
        detector.loadModels();
        String content = metadata.get(TikaCoreProperties.TIKA_CONTENT);
        if (content == null) {
            return;
        }
        LanguageResult r = detector.detect(content);
        metadata.set(TikaCoreProperties.TIKA_DETECTED_LANGUAGE, r.getLanguage());
        metadata.set(TikaCoreProperties.TIKA_DETECTED_LANGUAGE_CONFIDENCE, r.getConfidence().name());
        metadata.set(TikaCoreProperties.TIKA_DETECTED_LANGUAGE_CONFIDENCE_RAW, r.getRawScore());
    }
}
