/**
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

package org.apache.tika.language.translate;

import org.apache.tika.exception.TikaException;
import org.apache.tika.language.LanguageIdentifier;
import org.apache.tika.language.LanguageProfile;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;
import java.util.Locale;

/**
 * Abstract class used to interact with command line/external Translators.
 *
 * @see org.apache.tika.language.translate.MosesTranslator for an example of extending this class.
 *
 * @since Tika 1.7
 */
public abstract class ExternalTranslator implements Translator {

    /**
     * Run the given command and return the output written to standard out.
     *
     * @param command The complete command to run.
     * @param env The environment to pass along to the Runtime.
     * @param workingDirectory The directory from which to run the command.
     * @return The output of the command written to standard out.
     * @throws IOException
     * @throws InterruptedException
     */
    public Reader runAndGetOutput(String command, String[] env, File workingDirectory) throws IOException, InterruptedException {
        Process process = Runtime.getRuntime().exec(command, env, workingDirectory);
        InputStreamReader reader = new InputStreamReader(process.getInputStream(), Charset.defaultCharset());
        BufferedReader bufferedReader = new BufferedReader(reader);
        process.waitFor();
        return bufferedReader;
    }

    /**
     * Checks to see if the command can be run. Typically used with
     *  something like "myapp --version" to check to see if "myapp"
     *  is installed and on the path.
     *
     * @param checkCommandString The command to run and check the return code of.
     * @param successCodes Return codes that signify success.
     */
    public boolean checkCommand(String checkCommandString, int... successCodes) {
        try {
            Process process = Runtime.getRuntime().exec(checkCommandString);
            process.waitFor();
            int result = process.waitFor();
            for (int code : successCodes) {
                if (code == result) return true;
            }
            return false;
        } catch(IOException e) {
            // Some problem, command is there or is broken
            System.err.println("Broken pipe");
            return false;
        } catch (InterruptedException ie) {
            // Some problem, command is there or is broken
            System.err.println("Interrupted");
            return false;
        }
    }

    /**
     * Default translate method which uses built Tika language identification.
     * @param text The text to translate.
     * @param targetLanguage The desired language to translate to (for example, "hi").
     * @return The translated text.
     * @throws Exception
     */
    @Override
    public String translate(String text, String targetLanguage) throws TikaException, IOException {
        LanguageIdentifier language = new LanguageIdentifier(
                new LanguageProfile(text));
        String sourceLanguage = language.getLanguage();
        return translate(text, sourceLanguage, targetLanguage);
    }
}
