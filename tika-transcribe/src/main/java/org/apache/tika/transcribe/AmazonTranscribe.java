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

package org.apache.tika.transcribe;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.transcribe.AmazonTranscribeAsync;
import com.amazonaws.services.transcribe.model.*;
import org.apache.tika.exception.TikaException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Properties;


public class AmazonTranscribe implements Transcriber {

    public static final String PROPERTIES_FILE = "transcribe.amazon.properties";
    public static final String ID_PROPERTY = "transcribe.AWS_ACCESS_KEY";
    public static final String SECRET_PROPERTY = "transcribe.AWS_SECRET_KEY";
    public static final String DEFAULT_ID = "dummy-id";
    public static final String DEFAULT_SECRET = "dummy-secret";
    public static final String DEFAULT_BUCKET = "dummy-bucket";
    public static final String BUCKET_NAME = "transcribe.BUCKET_NAME";

    private static final Logger LOG = LoggerFactory.getLogger(AmazonTranscribe.class);
    private AmazonTranscribeAsync amazonTranscribe;
    private AmazonS3 amazonS3;
    private String bucketName;
    private boolean isAvailable; // Flag for whether or not translation is available.
    private String jobName;
    private String clientId;
    private String clientSecret;  // Keys used for the API calls.
//    private HashSet<String> validSourceLanguages = new HashSet<>(Arrays.asList("en-US", "en-GB", "es-US", "fr-CA", "fr-FR", "en-AU",
//            "it-IT", "de-DE", "pt-BR", "ja-JP", "ko-KR"));  // Valid inputs to StartStreamTranscription for language of source file (audio)

    public AmazonTranscribe(String jobName) {
        this.jobName = jobName;
        Properties config = new Properties();
        try {
            config.load(AmazonTranscribe.class
                    .getResourceAsStream(
                            PROPERTIES_FILE));
            this.clientId = config.getProperty(ID_PROPERTY);
            this.clientSecret = config.getProperty(SECRET_PROPERTY);
            this.bucketName = config.getProperty(BUCKET_NAME);
            this.isAvailable = checkAvailable();

        } catch (Exception e) {
            LOG.warn("Exception reading config file", e);
            isAvailable = false;
        }
    }

    /**
     * Call this method in order to upload a file to the Amazon S3 bucket.
     *
     * @param bucketName
     * @param filePath
     * @param fullfilePath
     */
    public void uploadFileToBucket(String bucketName, String filePath, String fullfilePath) {
        PutObjectRequest request = new PutObjectRequest(bucketName, filePath, new File(fullfilePath));
        amazonS3.putObject(request);
    }

    /**
     * Gets Transcription result from AWS S3 bucket given bucketName and key
     *
     * @param key
     * @return
     */
    public String getTranscriptResult(String key) {
        TranscriptionJob transcriptionJob = retrieveObjectWhenJobCompleted(key);
        if (transcriptionJob != null && !TranscriptionJobStatus.FAILED.equals(transcriptionJob.getTranscriptionJobStatus())) {
            return amazonS3.getObjectAsString(this.bucketName, key + ".json");
        } else
            return null;
    }

    /**
     * Private helper function to get object from s3
     *
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
     * @param filePath
     * @throws TikaException
     * @throws IOException
     */
    @Override
    public String startTranscribeAudio(String filePath) throws TikaException, IOException {
        if (!isAvailable) return "";
        StartTranscriptionJobRequest startTranscriptionJobRequest = new StartTranscriptionJobRequest();
        Media media = new Media();
        media.setMediaFileUri(amazonS3.getUrl(bucketName, filePath).toString());
        startTranscriptionJobRequest.withMedia(media)
//                .withLanguageCode(LanguageCode.EnUS)
                .withOutputBucketName(this.bucketName)
                .setTranscriptionJobName(jobName);
        amazonTranscribe.startTranscriptionJob(startTranscriptionJobRequest);
        return getTranscriptResult(jobName + ".json");
    }

    @Override
    public String startTranscribeAudio(String filePath, String sourceLanguage) throws TikaException, IOException {
        if (!isAvailable) return "";
        StartTranscriptionJobRequest startTranscriptionJobRequest = new StartTranscriptionJobRequest();
        Media media = new Media();
        media.setMediaFileUri(amazonS3.getUrl(bucketName, filePath).toString());
        startTranscriptionJobRequest.withMedia(media)
                .withLanguageCode(LanguageCode.valueOf(sourceLanguage))
                .withOutputBucketName(this.bucketName)
                .setTranscriptionJobName(jobName);
        amazonTranscribe.startTranscriptionJob(startTranscriptionJobRequest);
        return getTranscriptResult(jobName + ".json");
    }

    @Override
    public String startTranscribeVideo(String filePath) throws TikaException, IOException {
        if (!isAvailable) return "";
        //TODO
        return "";
    }

    @Override
    public String startTranscribeVideo(String filePath, String sourceLanguage) throws TikaException, IOException {
        if (!isAvailable) return "";
        //TODO
        return "";
    }

    @Override
    public boolean isAvailable() {
        return isAvailable;
    }

    /**
     * Sets the client Id for the translator API.
     *
     * @param id The ID to set.
     */
    public void setId(String id) {
        this.clientId = id;
        this.isAvailable = checkAvailable();
    }

    /**
     * Sets the client secret for the translator API.
     *
     * @param secret The secret to set.
     */
    public void setSecret(String secret) {
        this.clientSecret = secret;
        this.isAvailable = checkAvailable();
    }

    /**
     * Sets the client secret for the translator API.
     *
     * @param bucket The bucket to set.
     */
    public void setBucket(String bucket) {
        this.bucketName = bucket;
        this.isAvailable = checkAvailable();
    }

    private boolean checkAvailable() {
        return clientId != null &&
                !clientId.equals(DEFAULT_ID) &&
                clientSecret != null &&
                !clientSecret.equals(DEFAULT_SECRET) &&
                bucketName != null &&
                !bucketName.equals(DEFAULT_BUCKET);
    }
}
