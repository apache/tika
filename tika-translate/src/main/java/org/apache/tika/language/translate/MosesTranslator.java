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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.util.Properties;

/**
 * Translator that uses the Moses decoder for translation.
 * Users must install the Moses system before using this Translator. @link http://www.statmt.org/moses/.
 */
public class MosesTranslator extends ExternalTranslator {

    private static final String DEFAULT_PATH = "dummy-path";
    private static final String TMP_FILE_NAME = "tika.moses.translation.tmp";

    private String smtPath = DEFAULT_PATH;
    private String scriptPath = DEFAULT_PATH;

    /**
     * Default constructor that attempts to read the smt jar and script paths from the
     * translator.moses.properties file.
     *
     * @throws java.lang.AssertionError When the properties file is unreadable.
     */
    public MosesTranslator() {
        Properties config = new Properties();
        try {
            config.load(MosesTranslator.class
                    .getClassLoader()
                    .getResourceAsStream("org/apache/tika/language/translate/translator.moses.properties"));
            new MosesTranslator(
                    config.getProperty("translator.smt_path"),
                    config.getProperty("translator.script_path"));
        } catch (IOException e) {
            throw new AssertionError("Failed to read translator.moses.properties.");
        }
    }

    /**
     * Create a Moses Translator with the specified smt jar and script paths.
     *
     * @param smtPath Full path to the jar to run.
     * @param scriptPath Full path to the script to pass to the smt jar.
     */
    public MosesTranslator(String smtPath, String scriptPath) {
        this.smtPath = smtPath;
        this.scriptPath = scriptPath;
        System.out.println(buildCommand(smtPath, scriptPath));
    }

    @Override
    public String translate(String text, String sourceLanguage, String targetLanguage) throws TikaException, IOException {
        if (!isAvailable() || !checkCommand(buildCheckCommand(smtPath), 1)) return text;
        File tmpFile = new File(TMP_FILE_NAME);
        OutputStreamWriter out = new OutputStreamWriter(new FileOutputStream(tmpFile), Charset.defaultCharset());
        out.append(text).append('\n').close();

        Runtime.getRuntime().exec(buildCommand(smtPath, scriptPath), new String[]{}, buildWorkingDirectory(scriptPath));

        File tmpTranslatedFile = new File(TMP_FILE_NAME + ".translated");

        StringBuilder stringBuilder = new StringBuilder();
        BufferedReader reader = new BufferedReader(new InputStreamReader(
                new FileInputStream(tmpTranslatedFile),
                Charset.defaultCharset()
        ));
        String line;
        while ((line = reader.readLine()) != null) stringBuilder.append(line);

        if (!tmpFile.delete() || !tmpTranslatedFile.delete()){
            throw new IOException("Failed to delete temporary files.");
        }
        return stringBuilder.toString();
    }

    @Override
    public boolean isAvailable() {
        return !smtPath.equals(DEFAULT_PATH) && !scriptPath.equals(DEFAULT_PATH);
    }

    /**
     * Build the command String to be executed.
     * @param smtPath Full path to the jar to run.
     * @param scriptPath Full path to the script to pass to the smt jar.
     * @return String to run on the command line.
     */
    private String buildCommand(String smtPath, String scriptPath) {
        return "java -jar " + smtPath +
                " -c NONE " +
                scriptPath + " " +
                System.getProperty("user.dir") + "/" + TMP_FILE_NAME;
    }

    /**
     * Build the command String to check if we can execute the smt jar.
     * @param smtPath Full path to the jar to run.
     * @return String to run on the command line.
     */
    private String buildCheckCommand(String smtPath) {
        return "java -jar " + smtPath;
    }

    /**
     * Build the File that represents the desired working directory. In this case,
     * the directory the script is in.
     * @param scriptPath Full path to the script passed to the smt jar.
     * @return File of the directory with the script in it.
     */
    private File buildWorkingDirectory(String scriptPath) {
        return new File(scriptPath.substring(0, scriptPath.lastIndexOf("/") + 1));
    }

}
