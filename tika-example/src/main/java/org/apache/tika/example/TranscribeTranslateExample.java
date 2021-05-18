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

import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.tika.Tika;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.language.translate.GoogleTranslator;
import org.apache.tika.language.translate.Translator;

/**
 * This example demonstrates primitive logic for
 * chaining Tika API calls. In this case translation
 * could be considered as a downstream process to
 * transcription.
 * We simply pass the output of
 * a call to {@link Tika#parseToString(Path)}
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
     * Use {@link org.apache.tika.parser.transcribe.aws.AmazonTranscribe} to execute transcription
     * on input data.
     * This implementation needs to be configured as explained in the Javadoc.
     * @param file the name of the file (which needs to be on the Java Classpath) to transcribe.
     * @return transcribed text.
     */
    public static String amazonTranscribe(Path tikaConfig, Path file) throws Exception {
        return new Tika(new TikaConfig(tikaConfig)).parseToString(file);
    }

    /**
     * Main method to run this example. This program can be invoked as follows
     * <ol>
     * <li><code>transcribe-translate ${tika-config.xml} ${file}</code>; which executes both
     * transcription then translation on the given resource, or
     * <li><code>transcribe ${tika-config.xml} ${file}</code>; which executes only translation</li>
     * @param args either of the commands described above and the input file
     * (which needs to be on the Java Classpath).
     *
     *
     *
     * ${tika-config.xml} must include credentials for aws and a temporary storage bucket:
     * <pre>
     * {@code
     *  <properties>
     *   <parsers>
     *     <parser class="org.apache.tika.parser.DefaultParser"/>
     *     <parser class="org.apache.tika.parser.transcribe.aws.AmazonTranscribe">
     *       <params>
     *         <param name="bucket" type="string">bucket</param>
     *         <param name="clientId" type="string">clientId</param>
     *         <param name="clientSecret" type="string">clientSecret</param>
     *       </params>
     *     </parser>
     *   </parsers>
     * </properties>
     * }
     * </pre>
     */
    public static void main (String[] args) throws Exception {
        String text = null;
        if (args.length > 1) {
            if ("transcribe-translate".equals(args[1])) {
                text = googleTranslateToEnglish(amazonTranscribe(Paths.get(args[0]),
                        Paths.get(args[1])));
                System.out.print("Transcription and translation successful!\nEXTRACTED TEXT: " + text);
            } else if ("transcribe".equals(args[1])) {
                text = amazonTranscribe(Paths.get(args[0]), Paths.get(args[1]));
                System.out.print("Transcription successful!\nEXTRACTED TEXT: " + text);
            } else {
                System.out.print("Incorrect invocation, see Javadoc.");
            }
        } else {
            System.out.print("Incorrect invocation, see Javadoc.");
        }
    }
}
