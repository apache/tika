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

package org.apache.tika.parser.transcribe.aws;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.SdkClientException;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.AmazonS3Exception;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.PutObjectResult;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectInputStream;
import com.amazonaws.services.securitytoken.AWSSecurityTokenService;
import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClientBuilder;
import com.amazonaws.services.securitytoken.model.GetCallerIdentityRequest;
import com.amazonaws.services.securitytoken.model.GetCallerIdentityResult;
import com.amazonaws.services.transcribe.AmazonTranscribeAsync;
import com.amazonaws.services.transcribe.AmazonTranscribeAsyncClientBuilder;
import com.amazonaws.services.transcribe.model.GetTranscriptionJobRequest;
import com.amazonaws.services.transcribe.model.GetTranscriptionJobResult;
import com.amazonaws.services.transcribe.model.LanguageCode;
import com.amazonaws.services.transcribe.model.Media;
import com.amazonaws.services.transcribe.model.StartTranscriptionJobRequest;
import com.amazonaws.services.transcribe.model.TranscriptionJob;
import com.amazonaws.services.transcribe.model.TranscriptionJobStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import org.apache.tika.config.Field;
import org.apache.tika.config.Initializable;
import org.apache.tika.config.InitializableProblemHandler;
import org.apache.tika.config.Param;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.XHTMLContentHandler;

/**
 * <a href="https://aws.amazon.com/transcribe/">Amazon Transcribe</a>
 * implementation. See Javadoc for configuration options.
 * <p>
 * Silently becomes unavailable when client keys are unavailable.
 *
 * <b>N.B.</b> it is not necessary to create the bucket before hand.
 * This implementation will automatically create the bucket if one
 * does not already exist, per the name defined above.
 *
 * @since Tika 2.0
 */

public class AmazonTranscribe implements Parser, Initializable {
    private static final Logger LOG = LoggerFactory.getLogger(AmazonTranscribe.class);
    private AmazonTranscribeAsync amazonTranscribeAsync;
    private AmazonS3 amazonS3;
    private String bucketName;
    private String region;
    private boolean isAvailable; // Flag for whether or not transcription is available.
    private String clientId; // Access key
    private String clientSecret; // Keys used for the API calls.
    private AWSStaticCredentialsProvider credsProvider;

    //https://docs.aws.amazon.com/transcribe/latest/dg/input.html
    protected static final Set<MediaType> SUPPORTED_TYPES = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(MediaType.audio("x-flac"), MediaType.audio("mp3"),
                    MediaType.audio("mpeg"), MediaType.video("ogg"), MediaType.audio("vnd.wave"),
                    MediaType.audio("mp4"), MediaType.video("mp4"), MediaType.application("mp4"),
                    MediaType.video("quicktime"))));


    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        if (!isAvailable) {
            return Collections.EMPTY_SET;
        }
        return SUPPORTED_TYPES;
    }

    /**
     * Starts AWS Transcribe Job with language specification.
     *
     * @param stream   the source input stream.
     * @param handler  handler to use
     * @param metadata
     * @param context  -- set the {@link LanguageCode} in the ParseContext if known
     * @throws TikaException When there is an error transcribing.
     * @throws IOException   If an I/O exception of some sort has occurred.
     * @see <a href=
     * "https://docs.aws.amazon.com/AWSJavaSDK/latest/javadoc/com/amazonaws/services/transcribe/model/LanguageCode.html">AWS
     * Language Code</a>
     */
    @Override
    public void parse(InputStream stream, ContentHandler handler, Metadata metadata,
                      ParseContext context) throws IOException, SAXException, TikaException {

        if (!isAvailable) {
            return;
        }
        // isAvailable does not check this
        if (amazonS3 == null) {
            return;
        }
        String jobName = getJobKey();
        LanguageCode languageCode = context.get(LanguageCode.class);
        uploadFileToBucket(stream, jobName);
        StartTranscriptionJobRequest startTranscriptionJobRequest =
                new StartTranscriptionJobRequest();
        Media media = new Media();
        media.setMediaFileUri(amazonS3.getUrl(bucketName, jobName).toString());
        startTranscriptionJobRequest.withMedia(media).withOutputBucketName(this.bucketName)
                .withTranscriptionJobName(jobName).setRequestCredentialsProvider(credsProvider);

        if (languageCode != null) {
            startTranscriptionJobRequest.withLanguageCode(languageCode);
        } else {
            startTranscriptionJobRequest.withIdentifyLanguage(true);
        }
        amazonTranscribeAsync.startTranscriptionJob(startTranscriptionJobRequest);
        XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
        xhtml.startDocument();
        String text = getTranscriptText(jobName);
        xhtml.startElement("p");
        xhtml.characters(text);
        xhtml.endElement("p");
        xhtml.endDocument();

        deleteFilesFromBucket(jobName);
    }


    /**
     * @return true if this Transcriber is probably able to transcribe right
     * now.
     * @since Tika 2.1
     */
    public boolean isAvailable() {
        return this.isAvailable;
    }

    /**
     * Sets the client Id for the transcriber API.
     *
     * @param id The ID to set.
     */
    @Field
    public void setClientId(String id) {
        this.clientId = id;
        this.isAvailable = checkAvailable();
    }

    /**
     * Sets the client secret for the transcriber API.
     *
     * @param secret The secret to set.
     */
    @Field
    public void setClientSecret(String secret) {
        this.clientSecret = secret;
        this.isAvailable = checkAvailable();
    }

    /**
     * Sets the client secret for the transcriber API.
     *
     * @param bucket The bucket to set.
     */
    @Field
    public void setBucket(String bucket) {
        this.bucketName = bucket;
        this.isAvailable = checkAvailable();
    }

    @Field
    public void setRegion(String region) {
        this.region = region;
        this.isAvailable = checkAvailable();
    }

    /**
     * Private method check if the service is available.
     *
     * @return if the service is available
     */
    private boolean checkAvailable() {
        return clientId != null && clientSecret != null && bucketName != null;
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
     *                     The file to upload to Amazon S3.
     * @param jobName      The unique job name for each job(UUID).
     */
    private void uploadFileToBucket(InputStream inputStream, String jobName) throws TikaException {
        PutObjectRequest request =
                new PutObjectRequest(this.bucketName, jobName, inputStream, null);
        try {
            @SuppressWarnings("unused") PutObjectResult response = amazonS3.putObject(request);
        } catch (SdkClientException e) {
            throw new TikaException("File upload to AWS failed: " + e.getMessage(), e);
        }
    }

    private void deleteFilesFromBucket(String jobName) throws TikaException {
        try {
            amazonS3.deleteObject(bucketName, jobName);
            amazonS3.deleteObject(bucketName, jobName + ".json");
        } catch (SdkClientException e) {
            LOG.error("Failed to delete {} and/or {} from {}", jobName, jobName + ".json", bucketName, e);
        }
    }

    /**
     * Gets Transcription result from AWS S3 bucket given the jobName.
     *
     * @param fileNameS3 The path of the file to upload to Amazon S3.
     * @return The transcribed string result, NULL if the job failed.
     * @throws IOException            possible reasons include (i) an End Event is not received
     *                                from AWS S3 SelectObjectContentResult operation and (ii) a parse exception
     *                                whilst processing JSON from the AWS S3 SelectObjectContentResult operation.
     * @throws SdkClientException     a AWS-specific exception related to SelectObjectContentResult
     *                                operation.
     * @throws AmazonServiceException possibly thrown if there is an issue selecting object content
     *                                from AWS S3 objects.
     */
    private String getTranscriptText(String fileNameS3)
            throws AmazonServiceException, SdkClientException, IOException {
        TranscriptionJob transcriptionJob = retrieveObjectWhenJobCompleted(fileNameS3);
        String text = "";
        if (transcriptionJob != null && !TranscriptionJobStatus.FAILED.name()
                .equals(transcriptionJob.getTranscriptionJobStatus())) {
            S3Object s3Object = amazonS3.getObject(new GetObjectRequest(bucketName, fileNameS3 + ".json"));
            try (S3ObjectInputStream objectContent = s3Object.getObjectContent()) {
                ObjectMapper mapper = new ObjectMapper();
                JsonNode root = mapper.readTree(objectContent);
                text = root
                        .path("results")
                        .path("transcripts")
                        .get(0)
                        .path("transcript")
                        .asText();
                // could also be done with json.simple:
                // ((JSONObject)((JSONArray)((JSONObject) obj.get("results")).get("transcripts")).get(0)).get("transcript")
            }
        }
        return text;
    }

    /**
     * Private helper function to get object from s3.
     *
     * @param jobName The unique job name for each job(UUID).
     * @return TranscriptionJob object
     */
    private TranscriptionJob retrieveObjectWhenJobCompleted(String jobName) {
        GetTranscriptionJobRequest getTranscriptionJobRequest = new GetTranscriptionJobRequest();
        getTranscriptionJobRequest.withRequestCredentialsProvider(credsProvider);
        getTranscriptionJobRequest.setTranscriptionJobName(jobName);
        while (true) {
            GetTranscriptionJobResult innerResult =
                    amazonTranscribeAsync.getTranscriptionJob(getTranscriptionJobRequest);
            String status = innerResult.getTranscriptionJob().getTranscriptionJobStatus();
            if (TranscriptionJobStatus.COMPLETED.name().equals(status) ||
                    TranscriptionJobStatus.FAILED.name().equals(status)) {
                return innerResult.getTranscriptionJob();
            }
        }
    }

    @Override
    public void initialize(Map<String, Param> params) throws TikaConfigException {
        if (!checkAvailable()) {
            return;
        }

        try {
            BasicAWSCredentials creds = new BasicAWSCredentials(this.clientId, this.clientSecret);
            this.credsProvider = new AWSStaticCredentialsProvider(creds);
            if (region != null) {
                this.amazonS3 = AmazonS3ClientBuilder.standard().withCredentials(credsProvider)
                        .withRegion(this.region).build();
            } else {
                this.amazonS3 =
                        AmazonS3ClientBuilder.standard().withCredentials(credsProvider).build();
                this.region = amazonS3.getRegionName(); // not sure if this works at all
            }

            // for debugging
            AWSSecurityTokenService stsClient = AWSSecurityTokenServiceClientBuilder.standard()
                    .withCredentials(credsProvider).withRegion(region)
                    .build();
            GetCallerIdentityResult identity = stsClient.getCallerIdentity(new GetCallerIdentityRequest());
            LOG.debug("Authenticated as: {}", identity.getArn());

            if (!this.amazonS3.doesBucketExistV2(this.bucketName)) { // returns true if no access
                try {
                    amazonS3.createBucket(this.bucketName);
                } catch (AmazonS3Exception e) {
                    throw new TikaConfigException("couldn't create bucket", e);
                }
            }
            this.amazonTranscribeAsync =
                    AmazonTranscribeAsyncClientBuilder.standard().withCredentials(credsProvider)
                            .withRegion(this.region).build();
        } catch (Exception e) {
            LOG.warn("Exception reading config file", e);
            isAvailable = false;
        }

    }

    @Override
    public void checkInitialization(InitializableProblemHandler problemHandler)
            throws TikaConfigException {
        //TODO alert user if they've gotten 1 or 2 out of three?
        this.isAvailable = checkAvailable();
    }
}
