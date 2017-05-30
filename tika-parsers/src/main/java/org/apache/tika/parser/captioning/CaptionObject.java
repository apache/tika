/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.tika.parser.captioning;

import org.apache.tika.parser.recognition.RecognisedObject;

/**
 * A model for caption objects from graphics and texts typically includes
 * human readable sentence, language of the sentence and confidence score.
 *
 * @since Apache Tika 1.16
 */
public class CaptionObject extends RecognisedObject {

    public CaptionObject(String sentence, String sentenceLang, double confidence) {
        super(sentence, sentenceLang, null, confidence);
    }

    @Override
    public String toString() {
        return "Caption{" +
                "sentence='" + label + "\' (" + labelLang + ')' +
                ", confidence=" + confidence +
                '}';
    }

}
