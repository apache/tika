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

import com.amazonaws.AmazonServiceException;
import com.amazonaws.SdkClientException;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.AmazonS3Exception;
import com.amazonaws.services.s3.model.CompressionType;
import com.amazonaws.services.s3.model.ExpressionType;
import com.amazonaws.services.s3.model.InputSerialization;
import com.amazonaws.services.s3.model.JSONInput;
import com.amazonaws.services.s3.model.JSONOutput;
import com.amazonaws.services.s3.model.JSONType;
import com.amazonaws.services.s3.model.OutputSerialization;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.PutObjectResult;
import com.amazonaws.services.s3.model.SelectObjectContentEvent;
import com.amazonaws.services.s3.model.SelectObjectContentEventVisitor;
import com.amazonaws.services.s3.model.SelectObjectContentRequest;
import com.amazonaws.services.s3.model.SelectObjectContentResult;
import com.amazonaws.services.transcribe.AmazonTranscribeAsync;
import com.amazonaws.services.transcribe.AmazonTranscribeAsyncClientBuilder;
import com.amazonaws.services.transcribe.model.Media;
import com.amazonaws.services.transcribe.model.StartTranscriptionJobRequest;
import com.amazonaws.services.transcribe.model.TranscriptionJob;
import com.amazonaws.services.transcribe.model.TranscriptionJobStatus;
import com.amazonaws.services.transcribe.model.GetTranscriptionJobRequest;
import com.amazonaws.services.transcribe.model.GetTranscriptionJobResult;
import com.amazonaws.services.transcribe.model.LanguageCode;
import org.apache.tika.exception.TikaException;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * <a href="https://aws.amazon.com/transcribe/">Amazon Transcribe</a> 
 * {@link Transcriber} implementation. See Javadoc for configiration options.
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
    public static final String REGION = "transcribe.REGION";
    private static final Logger LOG = LoggerFactory
            .getLogger(AmazonTranscribe.class);
    private AmazonTranscribeAsync amazonTranscribeAsync;
    private AmazonS3 amazonS3;
    private String bucketName;
    private String region;
    private boolean isAvailable; // Flag for whether or not transcription is
    // available.
    private String clientId;
    private String clientSecret; // Keys used for the API calls.
    private AWSStaticCredentialsProvider credsProvider;

    /**
     * Create a new AmazonTranscribe instance with the client keys specified in
     * <code>transcribe.amazon.properties</code> which needs to be available on
     * the Java Classpath.
     * Silently becomes unavailable when client keys are unavailable.
     * <code>transcribe.AWS_ACCESS_KEY</code>,
     * <code>transcribe.AWS_SECRET_KEY</code>,
     * <code>transcribe.BUCKET_NAME</code> and 
     * <code>transcribe.REGION</code> must be set in
     * <code>transcribe.amazon.properties</code>.
     * <b>N.B.</b> it is not necessary to create the bucket before hand. 
     * This implementation will automatically create the bucket if one
     * does not alrerady exist, per the name defined above.
     *
     * @since Tika 2.0
     */
    public AmazonTranscribe() {
        Properties config = new Properties();
        try {
            config.load(AmazonTranscribe.class
                    .getResourceAsStream(PROPERTIES_FILE));
            this.clientId = config.getProperty(ID_PROPERTY);
            this.clientSecret = config.getProperty(SECRET_PROPERTY);
            this.bucketName = config.getProperty(BUCKET_NAME);
            this.region = config.getProperty(REGION);
            BasicAWSCredentials creds = new BasicAWSCredentials(this.clientId,
                    this.clientSecret);
            this.credsProvider = new AWSStaticCredentialsProvider(creds);
            amazonS3 = AmazonS3ClientBuilder.standard()
                    .withCredentials(credsProvider).withRegion(this.region)
                    .build();
            this.isAvailable = checkAvailable();
            if (!this.amazonS3.doesBucketExistV2(this.bucketName)) {
                try {
                    amazonS3.createBucket(this.bucketName);
                } catch (AmazonS3Exception e) {
                    throw new RuntimeException(e.getErrorMessage());
                }
            }
            this.amazonTranscribeAsync = AmazonTranscribeAsyncClientBuilder
                    .standard().withCredentials(credsProvider)
                    .withRegion(this.region).build();
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
     * Constructs a new {@link PutObjectRequest} object to upload a file to the
     * specified bucket and jobName. After constructing the request, users may
     * optionally specify object metadata or a canned ACL as well.
     *
     * @param inputStream, null
     *            The file to upload to Amazon S3.
     * @param jobName
     *            The unique job name for each job(UUID).
     */
    private void uploadFileToBucket(InputStream inputStream, String jobName)
            throws TikaException {
        PutObjectRequest request = new PutObjectRequest(this.bucketName,
                jobName, inputStream, null);
        try {
            @SuppressWarnings("unused")
            PutObjectResult response = amazonS3.putObject(request);
        } catch (SdkClientException e) {
            throw (new TikaException("File Upload to AWS Failed"));
        }
    }

    /**
     * Starts AWS Transcribe Job without language specification.
     *
     * @param inputStream
     *            the source input stream.
     * @return The transcribed string result, NULL if the job failed.
     * @throws TikaException
     *             When there is an error transcribing.
     * @throws IOException
     *             If an I/O exception of some sort has occurred.
     */
    @Override
    public String transcribe(InputStream inputStream)
            throws TikaException, IOException {
        if (!isAvailable())
            return null;
        String jobName = getJobKey();
        uploadFileToBucket(inputStream, jobName);
        StartTranscriptionJobRequest startTranscriptionJobRequest = new StartTranscriptionJobRequest();
        Media media = new Media();
        media.setMediaFileUri(amazonS3.getUrl(bucketName, jobName).toString());
        startTranscriptionJobRequest.withIdentifyLanguage(true).withMedia(media)
        .withOutputBucketName(this.bucketName)
        .withTranscriptionJobName(jobName)
        .setRequestCredentialsProvider(credsProvider);
        amazonTranscribeAsync
        .startTranscriptionJob(startTranscriptionJobRequest);
        return getTranscriptText(jobName);
    }

    /**
     * Starts AWS Transcribe Job with language specification.
     *
     * @param inputStream
     *            the source input stream.
     * @param sourceLanguage
     *            <a href=
     *            "https://docs.aws.amazon.com/AWSJavaSDK/latest/javadoc/com/amazonaws/services/transcribe/model/LanguageCode.html">AWS
     *            Language Code</a> for the language used in the input media
     *            file.
     * @return The transcribed string result, NULL if the job failed.
     * @throws TikaException
     *             When there is an error transcribing.
     * @throws IOException
     *             If an I/O exception of some sort has occurred.
     * @see <a href=
     *      "https://docs.aws.amazon.com/AWSJavaSDK/latest/javadoc/com/amazonaws/services/transcribe/model/LanguageCode.html">AWS
     *      Language Code</a>
     */
    @Override
    public String transcribe(InputStream inputStream, String sourceLanguage)
            throws TikaException, IOException {
        if (!isAvailable())
            return null;
        String jobName = getJobKey();
        uploadFileToBucket(inputStream, jobName);
        StartTranscriptionJobRequest startTranscriptionJobRequest = new StartTranscriptionJobRequest();
        Media media = new Media();
        media.setMediaFileUri(amazonS3.getUrl(bucketName, jobName).toString());
        ((StartTranscriptionJobRequest) startTranscriptionJobRequest
                .withMedia(media).withOutputBucketName(this.bucketName)
                .withTranscriptionJobName(jobName)
                .withRequestCredentialsProvider(credsProvider))
        .withLanguageCode(
                LanguageCode.fromValue(sourceLanguage));
        amazonTranscribeAsync
        .startTranscriptionJob(startTranscriptionJobRequest);
        return getTranscriptText(jobName);
    }

    /**
     * @return true if this Transcriber is probably able to transcribe right
     *         now.
     * @since Tika 2.1
     */
    @Override
    public boolean isAvailable() {
        return this.isAvailable;
    }

    /**
     * Sets the client Id for the transcriber API.
     *
     * @param id
     *            The ID to set.
     */
    public void setId(String id) {
        this.clientId = id;
        this.isAvailable = checkAvailable();
    }

    /**
     * Sets the client secret for the transcriber API.
     *
     * @param secret
     *            The secret to set.
     */
    public void setSecret(String secret) {
        this.clientSecret = secret;
        this.isAvailable = checkAvailable();
    }

    /**
     * Sets the client secret for the transcriber API.
     *
     * @param bucket
     *            The bucket to set.
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
        return clientId != null && !clientId.equals(DEFAULT_ID)
                && clientSecret != null && !clientSecret.equals(DEFAULT_SECRET)
                && bucketName != null && !bucketName.equals(DEFAULT_BUCKET);
    }

    /**
     * Gets Transcription result from AWS S3 bucket given the jobName.
     *
     * @param fileNameS3
     *            The path of the file to upload to Amazon S3.
     * @return The transcribed string result, NULL if the job failed.
     * @throws IOException possible reasons include (i) an End Event is not received
     * from AWS S3 SelectObjectContentResult operation and (ii) a parse exception
     * whilst processing JSON from the AWS S3 SelectObjectContentResult operation.
     * @throws SdkClientException a AWS-specific exception related to SelectObjectContentResult
     * operation.
     * @throws AmazonServiceException possibly thrown if there is an issue selecting object content
     * from AWS S3 objects.
     */
    private String getTranscriptText(String fileNameS3) throws AmazonServiceException, SdkClientException, IOException {
        TranscriptionJob transcriptionJob = retrieveObjectWhenJobCompleted(
                fileNameS3);
        String text = null;
        if (transcriptionJob != null && !TranscriptionJobStatus.FAILED.name()
                .equals(transcriptionJob.getTranscriptionJobStatus())) {
            InputSerialization inputSerialization = new InputSerialization().withJson(new JSONInput().withType(JSONType.DOCUMENT))
                    .withCompressionType(CompressionType.NONE);
            OutputSerialization outputSerialization = new OutputSerialization().withJson(new JSONOutput());
            SelectObjectContentRequest request = new SelectObjectContentRequest()
                    .withBucketName(this.bucketName).withKey(fileNameS3 + ".json")
                    .withExpression("Select s.results.transcripts[0].transcript from S3Object s")//WHERE transcript IS NOT MISSING
                    .withExpressionType(ExpressionType.SQL).withRequestCredentialsProvider(credsProvider);
            request.setInputSerialization(inputSerialization);
            request.setOutputSerialization(outputSerialization);

            final AtomicBoolean isResultComplete = new AtomicBoolean(false);

            try (SelectObjectContentResult result = amazonS3
                    .selectObjectContent(request)) {
                InputStream resultInputStream = result.getPayload()
                        .getRecordsInputStream(
                                new SelectObjectContentEventVisitor() {
                                    @Override
                                    public void visit(
                                            SelectObjectContentEvent.StatsEvent event) {
                                        LOG.debug(
                                                "Received Stats, Bytes Scanned: "
                                                        + event.getDetails()
                                                        .getBytesScanned()
                                                        + " Bytes Processed: "
                                                        + event.getDetails()
                                                        .getBytesProcessed());
                                    }

                                    /*
                                     * An End Event informs that the request has
                                     * finished successfully.
                                     */
                                    @Override
                                    public void visit(
                                            SelectObjectContentEvent.EndEvent event) {
                                        isResultComplete.set(true);
                                        LOG.debug(
                                                "Received End Event. Result is complete.");
                                    }
                                });
                text = new BufferedReader(
                        new InputStreamReader(resultInputStream, StandardCharsets.UTF_8))
                        .lines()
                        .collect(Collectors.joining("\n"));
            }
            /*
             * The End Event indicates all matching records have been
             * transmitted. If the End Event is not received, the results
             * may be incomplete.
             */
            if (!isResultComplete.get()) {
                throw new IOException(
                        "S3 Select request was incomplete as End Event was not received.");
            }
        }
        JSONParser parser = new JSONParser();
        JSONObject obj = null;
        try {
            obj = (JSONObject) parser.parse(text);
        } catch (ParseException e) {
            throw new IOException(e.getMessage(), e);
        }
        return obj.get("transcript").toString();
    }

    /**
     * Private helper function to get object from s3.
     *
     * @param jobName
     *            The unique job name for each job(UUID).
     * @return TranscriptionJob object
     */
    private TranscriptionJob retrieveObjectWhenJobCompleted(String jobName) {
        GetTranscriptionJobRequest getTranscriptionJobRequest = new GetTranscriptionJobRequest();
        getTranscriptionJobRequest
        .withRequestCredentialsProvider(credsProvider);
        getTranscriptionJobRequest.setTranscriptionJobName(jobName);
        while (true) {
            GetTranscriptionJobResult innerResult = amazonTranscribeAsync
                    .getTranscriptionJob(getTranscriptionJobRequest);
            String status = innerResult.getTranscriptionJob()
                    .getTranscriptionJobStatus();
            if (TranscriptionJobStatus.COMPLETED.name().equals(status)
                    || TranscriptionJobStatus.FAILED.name().equals(status)) {
                return innerResult.getTranscriptionJob();
            }
        }
    }
}