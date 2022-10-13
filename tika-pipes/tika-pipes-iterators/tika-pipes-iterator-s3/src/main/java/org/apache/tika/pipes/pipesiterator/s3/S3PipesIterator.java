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
package org.apache.tika.pipes.pipesiterator.s3;

import com.amazonaws.AmazonClientException;
import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.auth.InstanceProfileCredentialsProvider;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.iterable.S3Objects;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import org.apache.tika.config.Field;
import org.apache.tika.config.Initializable;
import org.apache.tika.config.InitializableProblemHandler;
import org.apache.tika.config.Param;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.io.FilenameUtils;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.pipes.FetchEmitTuple;
import org.apache.tika.pipes.HandlerConfig;
import org.apache.tika.pipes.emitter.EmitKey;
import org.apache.tika.pipes.fetcher.FetchKey;
import org.apache.tika.pipes.pipesiterator.PipesIterator;
import org.apache.tika.utils.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.apache.tika.config.TikaConfig.mustNotBeEmpty;

public class S3PipesIterator extends PipesIterator implements Initializable {

    private static final Logger LOGGER = LoggerFactory.getLogger(S3PipesIterator.class);
    private String prefix = "";
    private String region;
    private String accessKey;
    private String secretKey;
    private String endpointConfigurationService;
    private String endpointConfigurationSigningRegion;
    private String credentialsProvider;
    private String profile;
    private String bucket;
    private Pattern fileNamePattern = null;
    private int maxConnections = ClientConfiguration.DEFAULT_MAX_CONNECTIONS;
    private boolean pathStyleAccessEnabled = false;

    private AmazonS3 s3Client;

    @Field
    public void setEndpointConfigurationService(String endpointConfigurationService) {
        this.endpointConfigurationService = endpointConfigurationService;
    }

    @Field
    public void setEndpointConfigurationSigningRegion(String endpointConfigurationSigningRegion) {
        this.endpointConfigurationSigningRegion = endpointConfigurationSigningRegion;
    }

    @Field
    public void setBucket(String bucket) {
        this.bucket = bucket;
    }

    @Field
    public void setRegion(String region) {
        this.region = region;
    }

    @Field
    public void setProfile(String profile) {
        this.profile = profile;
    }

    @Field
    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    @Field
    public void setAccessKey(String accessKey) {
        this.accessKey = accessKey;
    }

    @Field
    public void setMaxConnections(int maxConnections) {
        this.maxConnections = maxConnections;
    }

    @Field
    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    @Field
    public void setCredentialsProvider(String credentialsProvider) {
        if (!credentialsProvider.equals("profile") && !credentialsProvider.equals("instance") && !credentialsProvider.equals("key_secret")) {
            throw new IllegalArgumentException(
                    "credentialsProvider must be either 'profile', 'instance' or 'key_secret'");
        }
        this.credentialsProvider = credentialsProvider;
    }

    @Field
    public void setFileNamePattern(String fileNamePattern) {
        this.fileNamePattern = Pattern.compile(fileNamePattern);
    }

    @Field
    public void setFileNamePattern(Pattern fileNamePattern) {
        this.fileNamePattern = fileNamePattern;
    }

    @Field
    public void setPathStyleAccessEnabled(boolean pathStyleAccessEnabled) {
        this.pathStyleAccessEnabled = pathStyleAccessEnabled;
    }

    /**
     * This initializes the s3 client. Note, we wrap S3's RuntimeExceptions,
     * e.g. AmazonClientException in a TikaConfigException.
     *
     * @param params params to use for initialization
     * @throws TikaConfigException
     */
    @Override
    public void initialize(Map<String, Param> params) throws TikaConfigException {
        //params have already been set...ignore them
        AWSCredentialsProvider provider;
        if (credentialsProvider.equals("instance")) {
            provider = InstanceProfileCredentialsProvider.getInstance();
        } else if (credentialsProvider.equals("profile")) {
            provider = new ProfileCredentialsProvider(profile);
        } else if (credentialsProvider.equals("key_secret")) {
            provider = new AWSStaticCredentialsProvider(new BasicAWSCredentials(accessKey, secretKey));
        } else {
            throw new TikaConfigException("credentialsProvider must be set and " +
                    "must be either 'instance', 'profile' or 'key_secret'");
        }

        ClientConfiguration clientConfig = new ClientConfiguration()
                .withMaxConnections(maxConnections);
        try {
            AmazonS3ClientBuilder amazonS3ClientBuilder = AmazonS3ClientBuilder.standard()
                    .withClientConfiguration(clientConfig)
                    .withCredentials(provider)
                    .withPathStyleAccessEnabled(pathStyleAccessEnabled);
            if (!StringUtils.isBlank(endpointConfigurationService) && !StringUtils.isBlank(endpointConfigurationSigningRegion)) {
                amazonS3ClientBuilder.setEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(endpointConfigurationService, endpointConfigurationSigningRegion));
            } else {
                amazonS3ClientBuilder.withRegion(region);
            }
            s3Client = amazonS3ClientBuilder.build();
        } catch (AmazonClientException e) {
            throw new TikaConfigException("can't initialize s3 pipesiterator", e);
        }
    }

    @Override
    public void checkInitialization(InitializableProblemHandler problemHandler)
            throws TikaConfigException {
        super.checkInitialization(problemHandler);
        mustNotBeEmpty("bucket", this.bucket);
    }

    @Override
    protected void enqueue() throws InterruptedException, IOException, TimeoutException {
        String fetcherName = getFetcherName();
        String emitterName = getEmitterName();
        long start = System.currentTimeMillis();
        int count = 0;
        HandlerConfig handlerConfig = getHandlerConfig();
        Matcher fileNameMatcher = null;
        if (fileNamePattern != null) {
            fileNameMatcher = fileNamePattern.matcher("");
        }
        for (S3ObjectSummary summary : S3Objects.withPrefix(s3Client, bucket, prefix)) {
            if (fileNameMatcher != null && !accept(fileNameMatcher, summary.getKey())) {
                continue;
            }
            long elapsed = System.currentTimeMillis() - start;
            LOGGER.debug("adding ({}) {} in {} ms", count, summary.getKey(), elapsed);
            //TODO -- allow user specified metadata as the "id"?
            tryToAdd(new FetchEmitTuple(summary.getKey(), new FetchKey(fetcherName,
                    summary.getKey()),
                    new EmitKey(emitterName, summary.getKey()), new Metadata(), handlerConfig,
                    getOnParseException()));
            count++;
        }
        long elapsed = System.currentTimeMillis() - start;
        LOGGER.info("finished enqueuing {} files in {} ms", count, elapsed);
    }

    private boolean accept(Matcher fileNameMatcher, String key) {
        String fName = FilenameUtils.getName(key);
        if (fileNameMatcher.reset(fName).find()) {
            return true;
        }
        return false;
    }
}
