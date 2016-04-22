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

package org.apache.tika.example;

import java.io.IOException;

import org.apache.tika.langdetect.OptimaizeLangDetector;
import org.apache.tika.language.detect.LanguageDetector;
import org.apache.tika.language.detect.LanguageHandler;
import org.apache.tika.language.detect.LanguageResult;
import org.apache.tika.language.detect.LanguageWriter;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;

public class Language {
    public static void languageDetection() throws IOException {
        LanguageDetector detector = new OptimaizeLangDetector().loadModels();
        LanguageResult result = detector.detect("Alla människor är födda fria och lika i värde och rättigheter.");
        
        System.out.println(result.getLanguage());
    }

    public static void languageDetectionWithWriter() throws IOException {
    	// TODO support version of LanguageWriter that doesn't need a detector.
        LanguageDetector detector = new OptimaizeLangDetector().loadModels();
        LanguageWriter writer = new LanguageWriter(detector);
        writer.append("Minden emberi lény");
        writer.append(" szabadon születik és");
        writer.append(" egyenlő méltósága és");
        writer.append(" joga van.");

        LanguageResult result = writer.getLanguage();
        System.out.println(result.getLanguage());
        writer.close();
    }

    public static void languageDetectionWithHandler() throws Exception {
        LanguageHandler handler = new LanguageHandler();
        new AutoDetectParser().parse(System.in, handler, new Metadata(), new ParseContext());

        LanguageResult result = handler.getLanguage();
        System.out.println(result.getLanguage());
    }
}
