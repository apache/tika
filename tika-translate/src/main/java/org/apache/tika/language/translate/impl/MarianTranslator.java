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

package org.apache.tika.language.translate.impl;


import org.apache.tika.exception.TikaException;
import org.apache.tika.utils.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Translator that uses the Marian NMT decoder for translation.
 *
 * Users must install Marian NMT and configure model locations before using this Translator.
 * @link https://marian-nmt.github.io/.
 */
public class MarianTranslator extends AbstractTranslator {

    private static final Logger LOG = LoggerFactory.getLogger(MarianTranslator.class);
    private static final String DEFAULT_PATH = "dummy-path";
    private static final String INPUT_TMP_NAME = "tika.marian.input";
    private static final String OUTPUT_TMP_NAME = "tika.marian.translation";

    private final String marianPath;
    private final Properties config;

    /**
     * Default constructor.
     */
    public MarianTranslator() {
        config = new Properties();
        try {
            config.load(MarianTranslator.class.getResourceAsStream("translator.marian.properties"));
            marianPath = config.getProperty("translator.marian.path", DEFAULT_PATH);
        } catch (IOException e) {
            throw new AssertionError("Failed to read translator.marian.properties.");
        }
    }

    /**
     * Default translate method which uses built Tika language identification.
     *
     * @param text The text to translate.
     * @param targetLanguage The desired language to translate to (for example, "hi").
     * @return The translated text.
     * @throws TikaException on any error performing translation.
     * @throws IOException on any I/O error performing translation.
     */
    public String translate(String text, String targetLanguage) throws TikaException, IOException {
        String sourceLanguage = detectLanguage(text).getLanguage();
        return translate(text, sourceLanguage, targetLanguage);
    }

    /**
     * Translate method with specific source and target languages.
     *
     * @param text The text to translate.
     * @param sourceLanguage The language to translate from (for example, "en").
     * @param targetLanguage The desired language to translate to (for example, "hi").
     * @return The translated text.
     * @throws TikaException on any error performing translation.
     * @throws IOException on any I/O error performing translation.
     */
    public String translate(String text, String sourceLanguage, String targetLanguage) throws TikaException, IOException {
        String configPath = config.getProperty(
                String.format("translator.marian.%s_%s.config", sourceLanguage, targetLanguage));

        if (!isAvailable(sourceLanguage, targetLanguage)) return text;

        StringBuilder translation = new StringBuilder();
        File tmpFile = File.createTempFile(INPUT_TMP_NAME, ".tmp");
        tmpFile.deleteOnExit();
        try (OutputStreamWriter out = new OutputStreamWriter(new FileOutputStream(tmpFile), Charset.defaultCharset())) {
            out.append(text).append('\n').close();
        }
        File tmpTranslatedFile = File.createTempFile(OUTPUT_TMP_NAME, ".tmp");
        tmpTranslatedFile.deleteOnExit();

        try {
            String preProcessScript = config.getProperty("translator.marian.preprocess");
            executeScript(preProcessScript, tmpFile);

            ProcessBuilder builder = new ProcessBuilder();
            String device = config.getProperty("translator.marian.device", "cpu");
            builder.command(buildMarianCommand(configPath, tmpFile, tmpTranslatedFile, device));
            builder.directory(new File(configPath).getParentFile());
            builder.redirectErrorStream(true);
            Process process = builder.start();
            process.waitFor();

            BufferedReader stdOutReader =
                    new BufferedReader(new InputStreamReader(process.getInputStream(), Charset.defaultCharset()));
            stdOutReader.lines().forEach(LOG::debug);
            stdOutReader.close();

            String postProcessScript = config.getProperty("translator.marian.postprocess");
            executeScript(postProcessScript, tmpTranslatedFile);

            BufferedReader fileReader
                    = new BufferedReader(new InputStreamReader(
                            new FileInputStream(tmpTranslatedFile), Charset.defaultCharset()));
            fileReader.lines().forEach(translation::append);
            fileReader.close();

        } catch (InterruptedException e) {
            throw new TikaException("Failed perform translation");
        }

        if (!tmpFile.delete() || !tmpTranslatedFile.delete()){
            throw new IOException("Failed to delete temporary files.");
        }

        return translation.toString();
    }

    /**
     * Executes a script taking the passed file as it's first argument.
     *
     * @param script the path to the script to execute
     * @param file the file to process
     * @throws IOException on any IO errors
     * @throws InterruptedException if the process fails.
     */
    private void executeScript(String script, File file) throws IOException, InterruptedException {
        if (!StringUtils.isEmpty(script) && !script.equals("no-script")) {
            Path scriptPath = Paths.get(script);
            if (!Files.exists(scriptPath) || !Files.isExecutable(scriptPath)) {
                throw new IOException(String.format("Cannot execute configured script at %s",scriptPath));
            }
            ProcessBuilder postProcess = new ProcessBuilder();
            postProcess.command(script, file.getAbsolutePath());
            postProcess.directory(new File(script).getParentFile());
            postProcess.redirectErrorStream(true);
            Process processProc = postProcess.start();
            processProc.waitFor();
        }
    }

    /**
     * Builds the Marian NMT Command for the configuration.
     *
     * @param configPath the path to the configuration file
     * @param input the input file location
     * @param output the output file location
     * @param device the device for inference (i.e. cpu or gpu)
     * @return the command to be executed.
     */
    private List<String> buildMarianCommand(String configPath, File input, File output, String device) {
        List<String> command = new ArrayList<>();
        command.add(Paths.get(marianPath).toString());
        command.add("-c");
        command.add(configPath);
        command.add("-i");
        command.add(input.getAbsolutePath());
        command.add("-o");
        command.add(output.getAbsolutePath());
        if (device.equalsIgnoreCase("cpu")) {
            command.add("--cpu-threads");
            command.add("1");
        }
        return command;
    }

    @Override
    public boolean isAvailable() {
        return !marianPath.equals(DEFAULT_PATH);
    }

    /**
     * Checks if the approproate Marian engine is available.
     * @param sourceLanguage The language to translate from (for example, "en").
     * @param targetLanguage The desired language to translate to (for example, "hi").
     * @return
     */
    public boolean isAvailable(String sourceLanguage, String targetLanguage) {
        String configPath = config.getProperty(
                String.format("translator.marian.%s_%s.config", sourceLanguage, targetLanguage));
        return !marianPath.equals(DEFAULT_PATH) && !StringUtils.isEmpty(configPath);
    }

}
