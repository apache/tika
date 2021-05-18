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

import java.io.FileInputStream;

import org.apache.tika.language.translate.GoogleTranslator;
import org.apache.tika.language.translate.Translator;
import org.apache.tika.transcribe.AmazonTranscribe;
import org.apache.tika.transcribe.Transcriber;

/**
 * This example demonstrates primitive logic for
 * chaining Tika API calls. In this case translation
 * could be considered as a downstream process to 
 * transcription.
 * We simply pass the output of
 * a call to {@link Transcriber#transcribe(java.io.InputStream)}
 * into {@link Translator#translate(String, String)}. 
 * The {@link GoogleTranslator} is configured with a target 
 * language of "en-US".
 * @author lewismc
 *
 */
public class TranscribeTranslateExample {

    /**
     * Use {@link GoogleTranslator} to execute translation on
     * input data. This implementation needs configured as explained in the Javadoc.
     * In this implementation, Google will try to guess the input language. The target 
     * language is "en-US".
     * @param text input text to translate.
     * @return translated text String.
     */
    public static String googleTranslateToEnglish(String text) {
        Translator translator = new GoogleTranslator();
        String result = null;
        if (translator.isAvailable()) {
            try {
                result = translator.translate(text, "en-US");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return result;
    }

    /**
     * Use {@link AmazonTranscribe} to execute transcription on input data.
     * This implementation needs configured as explained in the Javadoc.
     * @param file the name of the file (which needs to be on the Java Classpath) to transcribe.
     * @return transcribed text.
     */
    public static String amazonTranscribe(String file) {
        String filePath = TranscribeTranslateExample.class.getClassLoader().getResource(file).getPath();
        String result = null;
        Transcriber transcriber = new AmazonTranscribe();
        if (transcriber.isAvailable()) {
            try {
                result = transcriber.transcribe(new FileInputStream(filePath));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return result;
    }

    /**
     * Main method to run this example. This program can be invoked as follows
     * <ol>
     * <li><code>transcribe-translate ${file}</code>; which executes both 
     * transcription then translation on the given resource, or 
     * <li><code>transcribe ${file}</code>; which executes only translation</li>
     * @param args either of the commands described above and the input file 
     * (which needs to be on the Java Classpath). 
     */
    public static void main (String[] args) {
        String text = null;
        if (args.length != 0) {
            if ("transcribe-translate".equals(args[0])) {
                text = googleTranslateToEnglish(amazonTranscribe(args[1]));
                System.out.print("Transcription and translation successful!\nEXTRAXCTED TEXT: " + text);
            } else if ("transcribe".equals(args[0])) {
                text = amazonTranscribe(args[1]);
                System.out.print("Transcription successful!\nEXTRAXCTED TEXT: " + text);
            } else {
                System.out.print("Incorrect invocation, see Javadoc.");
            }
        } else {
            System.out.print("Incorrect invocation, see Javadoc.");
        }
    }
}
