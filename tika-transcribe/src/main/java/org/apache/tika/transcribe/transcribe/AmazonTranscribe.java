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
import java.io.File;

import com.amazonaws.services.transcribe.model.*;
import org.apache.tika.exception.TikaException;
import org.apache.tika.transcribe.Transcriber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.transcribe.AmazonTranscribeAsync;

import java.io.IOException;
import java.util.Properties;


public class AmazonTranscribe implements Transcriber {

    private AmazonTranscribeAsync amazonTranscribe;

    private AmazonS3 amazonS3;

    private static final Logger LOG = LoggerFactory.getLogger(AmazonTranscribe.class);

    private String bucketName;

    private boolean isAvailable; // Flag for whether or not translation is available.

    private String clientId;

    private String clientSecret;  // Keys used for the API calls.

//    private HashSet<String> validSourceLanguages = new HashSet<>(Arrays.asList("en-US", "en-GB", "es-US", "fr-CA", "fr-FR", "en-AU",
//            "it-IT", "de-DE", "pt-BR", "ja-JP", "ko-KR"));  // Valid inputs to StartStreamTranscription for language of source file (audio)

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
     * @param fileName
     * @return Transcribed text
     * @throws TikaException
     * @throws IOException
     */
    @Override
    public void startTranscribeAudio(String fileName, String jobName) throws TikaException, IOException {
        if (!isAvailable())
            return;
        StartTranscriptionJobRequest startTranscriptionJobRequest = new StartTranscriptionJobRequest();
        Media media = new Media();
        media.setMediaFileUri(amazonS3.getUrl(bucketName, fileName).toString());
        startTranscriptionJobRequest.withMedia(media)
                .withOutputBucketName(this.bucketName)
                .setTranscriptionJobName(jobName);
        amazonTranscribe.startTranscriptionJob(startTranscriptionJobRequest);
    }

    /**
     * Audio to text function with language specification
     * @param fileName
     * @param sourceLanguage
     * @return Transcribed text
     * @throws TikaException
     * @throws IOException
     */
    @Override
    public void startTranscribeAudio(String fileName, LanguageCode sourceLanguage, String jobName) throws TikaException, IOException {
        if (!isAvailable())
			return;
        StartTranscriptionJobRequest startTranscriptionJobRequest = new StartTranscriptionJobRequest();
        Media media = new Media();
        media.setMediaFileUri(amazonS3.getUrl(bucketName, fileName).toString());
        startTranscriptionJobRequest.withMedia(media)
                .withLanguageCode(sourceLanguage)
                .withOutputBucketName(this.bucketName)
                .setTranscriptionJobName(jobName);
        amazonTranscribe.startTranscriptionJob(startTranscriptionJobRequest);
    }

    @Override
    public void startTranscribeVideo(String fileName, String jobName) throws TikaException, IOException {
        if (!isAvailable())
            return;
        //TODO

    }

    /**
     * Audio to text function with language specification
     * @param fileName
     * @param sourceLanguage
     * @return Transcribed text
     * @throws TikaException
     * @throws IOException
     */
    @Override
    public void startTranscribeVideo(String fileName, LanguageCode sourceLanguage, String jobName) throws TikaException, IOException {
        if (!isAvailable())
            return;
        //boolean validSourceLanguageFlag = validSourceLanguages.contains(sourceLanguage); // Checks if sourceLanguage in validSourceLanguages O(1) lookup time

        //if (!validSourceLanguageFlag) { // Throws TikaException if the input sourceLanguage is not present in validSourceLanguages
        //    throw new TikaException("Provided Source Language is Not Valid. Run without language parameter or please select one of: " +
        //           "en-US, en-GB, es-US, fr-CA, fr-FR, en-AU, it-IT, de-DE, pt-BR, ja-JP, ko-KR"); }
        //TODO

    }

    /**
     * @return Valid AWS Credentials
     */
	public boolean isAvailable() {
		return this.isAvailable;
	}

    /** Gets Transcriptioni result from AWS S3 bucket given bucketNamee and key
     * @param key
     * @return
     */
    @Override
    public String getTranscriptResult(String key) {
        TranscriptionJob transcriptionJob = retrieveObjectWhenJobCompleted(key);
        if (transcriptionJob != null && !TranscriptionJobStatus.FAILED.equals(transcriptionJob.getTranscriptionJobStatus())) {
            return amazonS3.getObjectAsString(this.bucketName, key + ".json");
        } else
            return null;
    }

    /**
     * Private helper function to get object from s3
     * @param key
     * @return
     */
    private TranscriptionJob retrieveObjectWhenJobCompleted(String key) {
        GetTranscriptionJobRequest getTranscriptionJobRequest = new GetTranscriptionJobRequest();
        getTranscriptionJobRequest.setTranscriptionJobName(key);

        while (true) {
            GetTranscriptionJobResult innerResult = amazonTranscribe.getTranscriptionJob(getTranscriptionJobRequest);
            String status = innerResult.getTranscriptionJob().getTranscriptionJobStatus();
            if (TranscriptionJobStatus.COMPLETED.name().equals(status) ||
                    TranscriptionJobStatus.FAILED.name().equals(status)) {
                return innerResult.getTranscriptionJob();
            }
        }
    }

    /**
     * Call this method in order to upload a file to the Amazon S3 bucket.
     * @param bucketName
     * @param fileName
     * @param fullFileName
     */
    @Override
    public void uploadFileToBucket(String bucketName, String fileName, String fullFileName) {
        PutObjectRequest request = new PutObjectRequest(bucketName, fileName, new File(fullFileName));
        amazonS3.putObject(request);
    }
}
