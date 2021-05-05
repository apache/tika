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

import com.amazonaws.SdkClientException;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.PutObjectResult;
import com.amazonaws.services.transcribe.AmazonTranscribeAsync;
import com.amazonaws.services.transcribe.model.Media;
import com.amazonaws.services.transcribe.model.StartTranscriptionJobRequest;
import com.amazonaws.services.transcribe.model.TranscriptionJob;
import com.amazonaws.services.transcribe.model.TranscriptionJobStatus;
import com.amazonaws.services.transcribe.model.GetTranscriptionJobRequest;
import com.amazonaws.services.transcribe.model.GetTranscriptionJobResult;
import com.amazonaws.services.transcribe.model.LanguageCode;
import org.apache.tika.exception.TikaException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.FileOutputStream;
import java.util.Properties;
import java.util.UUID;

/**
 * Wrapper class to access the AWS transcription service.
 *
 * @since Tika 2.1
 */
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
    private boolean isAvailable; // Flag for whether or not transcription is available.
    private String clientId;
    private String clientSecret;  // Keys used for the API calls.

    /**
     * Create a new AmazonTranscriber with the client keys specified in
     * resources/org/apache/tika/transcribe/transcribe.amazon.properties.
     * Silently becomes unavailable when client keys are unavailable.
     * transcribe.AWS_ACCESS_KEY, transcribe.AWS_SECRET_KEY, and transcribe.BUCKET_NAME must be set in transcribe.amazon.properties for transcription to work.
     *
     * @since Tika 2.1
     */
    public AmazonTranscribe() {
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
     * private method to get a unique job key.
     *
     * @return unique job key.
     */
    private String getJobKey() {
        return UUID.randomUUID().toString();
    }

    /**
     * Constructs a new
     * {@link PutObjectRequest} object to upload a file to the
     * specified bucket and jobName. After constructing the request,
     * users may optionally specify object metadata or a canned ACL as well.
     *
     * @param file    The file to upload to Amazon S3.
     * @param jobName The unique job name for each job(UUID).
     */
    private void uploadFileToBucket(File file, String jobName) throws TikaException {
        PutObjectRequest request = new PutObjectRequest(this.bucketName, jobName, file);
        try {
            //  Block of code to try
            PutObjectResult response = amazonS3.putObject(request);
        } catch (SdkClientException e) {
            throw (new TikaException("File Upload to AWS Failed"));
        }
    }

    /**
     * Starts AWS Transcribe Job without language specification.
     *
     * @param inputStream the source input stream.
     * @return The transcribed string result, NULL if the job failed.
     * @throws TikaException When there is an error transcribing.
     * @throws IOException   If an I/O exception of some sort has occurred.
     */
    @Override
    public String transcribe(InputStream inputStream) throws TikaException, IOException {
        if (!isAvailable()) return null;
        String jobName = getJobKey();
        byte[] buffer = new byte[inputStream.available()];
        inputStream.read(buffer);
        File targetFile = new File("src/main/resources/targetFile.tmp");
        OutputStream outStream = new FileOutputStream(targetFile);
        outStream.write(buffer);
        targetFile.deleteOnExit();
        uploadFileToBucket(targetFile, jobName);
        StartTranscriptionJobRequest startTranscriptionJobRequest = new StartTranscriptionJobRequest();
        Media media = new Media();
        media.setMediaFileUri(amazonS3.getUrl(bucketName, jobName).toString());
        startTranscriptionJobRequest.withMedia(media)
                .withOutputBucketName(this.bucketName)
                .setTranscriptionJobName(jobName);
        amazonTranscribe.startTranscriptionJob(startTranscriptionJobRequest);
        return getTranscriptResult(jobName);
    }

    /**
     * Starts AWS Transcribe Job with language specification.
     *
     * @param inputStream    the source input stream.
     * @param sourceLanguage <a href="https://docs.aws.amazon.com/AWSJavaSDK/latest/javadoc/com/amazonaws/services/transcribe/model/LanguageCode.html">AWS Language Code</a> for the language used in the input media file.
     * @return The transcribed string result, NULL if the job failed.
     * @throws TikaException When there is an error transcribing.
     * @throws IOException   If an I/O exception of some sort has occurred.
     * @see <a href="https://docs.aws.amazon.com/AWSJavaSDK/latest/javadoc/com/amazonaws/services/transcribe/model/LanguageCode.html">AWS Language Code</a>
     */
    @Override
    public String transcribe(InputStream inputStream, String sourceLanguage) throws TikaException, IOException {
        if (!isAvailable()) return null;
        String jobName = getJobKey();
        byte[] buffer = new byte[inputStream.available()];
        inputStream.read(buffer);
        File targetFile = new File("src/main/resources/targetFile.tmp");
        OutputStream outStream = new FileOutputStream(targetFile);
        outStream.write(buffer);
        targetFile.deleteOnExit();
        uploadFileToBucket(targetFile, jobName);
        StartTranscriptionJobRequest startTranscriptionJobRequest = new StartTranscriptionJobRequest();
        Media media = new Media();
        media.setMediaFileUri(amazonS3.getUrl(bucketName, jobName).toString());
        startTranscriptionJobRequest.withMedia(media)
                .withLanguageCode(LanguageCode.fromValue(sourceLanguage))
                .withOutputBucketName(this.bucketName)
                .setTranscriptionJobName(jobName);
        amazonTranscribe.startTranscriptionJob(startTranscriptionJobRequest);
        return getTranscriptResult(jobName);
    }

    /**
     * @return true if this Transcriber is probably able to transcribe right now.
     * @since Tika 2.1
     */
    @Override
    public boolean isAvailable() {
        return this.isAvailable;
    }

    /**
     * Sets the client Id for the transcriber API.
     *
     * @param id The ID to set.
     */
    public void setId(String id) {
        this.clientId = id;
        this.isAvailable = checkAvailable();
    }

    /**
     * Sets the client secret for the transcriber API.
     *
     * @param secret The secret to set.
     */
    public void setSecret(String secret) {
        this.clientSecret = secret;
        this.isAvailable = checkAvailable();
    }

    /**
     * Sets the client secret for the transcriber API.
     *
     * @param bucket The bucket to set.
     */
    public void setBucket(String bucket) {
        this.bucketName = bucket;
        this.isAvailable = checkAvailable();
    }

    /**
     * Private method check if the service is available.
     *
     * @return if the service is available
     */
    private boolean checkAvailable() {
        return clientId != null &&
                !clientId.equals(DEFAULT_ID) &&
                clientSecret != null &&
                !clientSecret.equals(DEFAULT_SECRET) &&
                bucketName != null &&
                !bucketName.equals(DEFAULT_BUCKET);
    }

    /**
     * Gets Transcription result from AWS S3 bucket given the jobName.
     *
     * @param fileNameS3 The path of the file to upload to Amazon S3.
     * @return The transcribed string result, NULL if the job failed.
     */
    private String getTranscriptResult(String fileNameS3) {
        TranscriptionJob transcriptionJob = retrieveObjectWhenJobCompleted(fileNameS3);
        if (transcriptionJob != null && !TranscriptionJobStatus.FAILED.name().equals(transcriptionJob.getTranscriptionJobStatus())) {
            return amazonS3.getObjectAsString(this.bucketName, fileNameS3);
        } else
            return null;
    }

    /**
     * Private helper function to get object from s3.
     *
     * @param jobName The unique job name for each job(UUID).
     * @return TranscriptionJob object
     */
    private TranscriptionJob retrieveObjectWhenJobCompleted(String jobName) {
        GetTranscriptionJobRequest getTranscriptionJobRequest = new GetTranscriptionJobRequest();
        getTranscriptionJobRequest.setTranscriptionJobName(jobName);
        while (true) {
            GetTranscriptionJobResult innerResult = amazonTranscribe.getTranscriptionJob(getTranscriptionJobRequest);
            String status = innerResult.getTranscriptionJob().getTranscriptionJobStatus();
            if (TranscriptionJobStatus.COMPLETED.name().equals(status) ||
                    TranscriptionJobStatus.FAILED.name().equals(status)) {
                return innerResult.getTranscriptionJob();
            }
        }
    }
}
