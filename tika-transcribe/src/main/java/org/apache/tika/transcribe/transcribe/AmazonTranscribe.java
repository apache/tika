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

package org.apache.tika.transcribe.transcribe;
import java.util.HashSet;
import java.util.Arrays;
import org.apache.tika.exception.TikaException;
import org.apache.tika.transcribe.Transcriber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;


public class AmazonTranscribe implements Transcriber {


    private static final Logger LOG = LoggerFactory.getLogger(AmazonTranscribe.class);

    private String bucketName;

    private boolean isAvailable; // Flag for whether or not translation is available.

    private String clientId;

    private String clientSecret;  // Keys used for the API calls.

    private HashSet<String> validSourceLanguages = new HashSet<>(Arrays.asList("en-US", "en-GB", "es-US", "fr-CA", "fr-FR", "en-AU",
            "it-IT", "de-DE", "pt-BR", "ja-JP", "ko-KR"));  // Valid inputs to StartStreamTranscription for language of source file (audio)

    public AmazonTranscribe() {
        this.isAvailable = true;
        Properties config = new Properties();
        try {
            config.load(AmazonTranscribe.class
                    .getResourceAsStream(
                            "transcribe.amazon.properties"));
            this.clientId = config.getProperty("transcribe.AWS_ACCESS_KEY");
            this.clientSecret = config.getProperty("transcribe.AWS_SECRET_KEY");
            this.bucketName = config.getProperty("transcribe.BUCKET_NAME");

        } catch (Exception e) {
            LOG.warn("Exception reading config file", e);
            isAvailable = false;
        }
    }

    
    /**
     * Audio to text function without language specification
     * @param filepath
     * @return Transcribed text
     * @throws TikaException
     * @throws IOException
     */
    @Override
    public String transcribeAudio(String filepath) throws TikaException, IOException {
        if (!isAvailable())
			return "";
        //TODO
        return null;
    }

    /**
     * Audio to text function with language specification
     * @param filepath
     * @param sourceLanguage
     * @return Transcribed text
     * @throws TikaException
     * @throws IOException
     */
    @Override
    public String transcribeAudio(String filepath, String sourceLanguage) throws TikaException, IOException {
        if (!isAvailable())
			return "";
(
        boolean validSourceLanguageFlag = validSourceLanguages.contains(sourceLanguage); // Checks if sourceLanguage in validSourceLanguages O(1) lookup time

        if (!validSourceLanguageFlag) { // Throws TikaException if the input sourceLanguage is not present in validSourceLanguages
            throw new TikaException("Provided Source Language is Not Valid. Run without language parameter or please select one of: " +
                    "en-US, en-GB, es-US, fr-CA, fr-FR, en-AU, it-IT, de-DE, pt-BR, ja-JP, ko-KR"); }
        //TODO
        return null;
    }

    @Override
    public String transcribeVideo(String filepath) throws TikaException, IOException {
        if (!isAvailable())
            return "";
        //TODO
        return null;
    }

    /**
     * Audio to text function with language specification
     * @param filepath
     * @param sourceLanguage
     * @return Transcribed text
     * @throws TikaException
     * @throws IOException
     */
    @Override
    public String transcribeVideo(String filepath, String sourceLanguage) throws TikaException, IOException {
        if (!isAvailable())
            return "";
        (
        boolean validSourceLanguageFlag = validSourceLanguages.contains(sourceLanguage); // Checks if sourceLanguage in validSourceLanguages O(1) lookup time

        if (!validSourceLanguageFlag) { // Throws TikaException if the input sourceLanguage is not present in validSourceLanguages
            throw new TikaException("Provided Source Language is Not Valid. Run without language parameter or please select one of: " +
                    "en-US, en-GB, es-US, fr-CA, fr-FR, en-AU, it-IT, de-DE, pt-BR, ja-JP, ko-KR"); }
        //TODO
        return null;
    }

    /**
     * @return Valid AWS Credentials
     */
	public boolean isAvailable() {
		return this.isAvailable;
	}
}
