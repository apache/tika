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

import static org.apache.tika.config.TikaConfig.mustNotBeEmpty;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.InstanceProfileCredentialsProvider;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.http.SdkHttpConfigurationOption;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.S3Object;

import org.apache.tika.config.Field;
import org.apache.tika.config.Initializable;
import org.apache.tika.config.InitializableProblemHandler;
import org.apache.tika.config.Param;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.io.FilenameUtils;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.pipes.core.FetchEmitTuple;
import org.apache.tika.pipes.core.HandlerConfig;
import org.apache.tika.pipes.core.emitter.EmitKey;
import org.apache.tika.pipes.core.fetcher.FetchKey;
import org.apache.tika.pipes.core.pipesiterator.PipesIterator;
import org.apache.tika.utils.StringUtils;

public class S3PipesIterator extends PipesIterator implements Initializable {

    private static final Logger LOGGER = LoggerFactory.getLogger(S3PipesIterator.class);
    private String prefix = "";
    private String region;
    private String accessKey;
    private String secretKey;
    private String endpointConfigurationService;
    private String credentialsProvider;
    private String profile;
    private String bucket;
    private Pattern fileNamePattern = null;
    private int maxConnections = SdkHttpConfigurationOption.GLOBAL_HTTP_DEFAULTS.get(SdkHttpConfigurationOption.MAX_CONNECTIONS);
    private boolean pathStyleAccessEnabled = false;

    private S3Client s3Client;

    @Field
    public void setEndpointConfigurationService(String endpointConfigurationService) {
        this.endpointConfigurationService = endpointConfigurationService;
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
            throw new IllegalArgumentException("credentialsProvider must be either 'profile', 'instance' or 'key_secret'");
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
     * e.g. SdkClientException in a TikaConfigException.
     *
     * @param params params to use for initialization
     * @throws TikaConfigException
     */
    @Override
    public void initialize(Map<String, Param> params) throws TikaConfigException {
        //params have already been set...ignore them
        AwsCredentialsProvider provider;
        switch (credentialsProvider) {
            case "instance":
                provider = InstanceProfileCredentialsProvider.builder().build();
                break;
            case "profile":
                provider = ProfileCredentialsProvider.builder().profileName(profile).build();
                break;
            case "key_secret":
                AwsBasicCredentials awsCreds = AwsBasicCredentials.create(accessKey, secretKey);
                provider = StaticCredentialsProvider.create(awsCreds);
                break;
            default:
                throw new TikaConfigException("credentialsProvider must be set and " + "must be either 'instance', 'profile' or 'key_secret'");
        }

        SdkHttpClient httpClient = ApacheHttpClient.builder().maxConnections(maxConnections).build();
        S3Configuration clientConfig = S3Configuration.builder().pathStyleAccessEnabled(pathStyleAccessEnabled).build();
        try {
            S3ClientBuilder s3ClientBuilder = S3Client.builder().httpClient(httpClient).
                    serviceConfiguration(clientConfig).credentialsProvider(provider);
            if (!StringUtils.isBlank(endpointConfigurationService)) {
                try {
                    s3ClientBuilder.endpointOverride(new URI(endpointConfigurationService)).region(Region.of(region));
                }
                catch (URISyntaxException ex) {
                    throw new TikaConfigException("bad endpointConfigurationService: " + endpointConfigurationService, ex);
                }
            } else {
                s3ClientBuilder.region(Region.of(region));
            }
            s3Client = s3ClientBuilder.build();
        } catch (SdkClientException e) {
            throw new TikaConfigException("can't initialize s3 pipesiterator", e);
        }
    }

    @Override
    public void checkInitialization(InitializableProblemHandler problemHandler) throws TikaConfigException {
        super.checkInitialization(problemHandler);
        mustNotBeEmpty("bucket", this.bucket);
        mustNotBeEmpty("region", this.region);
    }

    @Override
    protected void enqueue() throws InterruptedException, IOException, TimeoutException {
        String fetcherName = getFetcherName();
        String emitterName = getEmitterName();
        long start = System.currentTimeMillis();
        int count = 0;
        HandlerConfig handlerConfig = getHandlerConfig();
        final Matcher fileNameMatcher;
        if (fileNamePattern != null) {
            fileNameMatcher = fileNamePattern.matcher("");
        } else {
            fileNameMatcher = null;
        }
        
        ListObjectsV2Request listObjectsV2Request = ListObjectsV2Request.builder().bucket(bucket).prefix(prefix).build();
        List<S3Object> s3ObjectList = s3Client.listObjectsV2Paginator(listObjectsV2Request).stream().
                flatMap(resp -> resp.contents().stream()).toList();
        for (S3Object s3Object : s3ObjectList) {
            String key = s3Object.key();
            if (fileNameMatcher != null && !accept(fileNameMatcher, key)) {
                continue;
            }
            long elapsed = System.currentTimeMillis() - start;
            LOGGER.debug("adding ({}) {} in {} ms", count, key, elapsed);
            //TODO -- allow user specified metadata as the "id"?
            ParseContext parseContext = new ParseContext();
            parseContext.set(HandlerConfig.class, handlerConfig);
            tryToAdd(new FetchEmitTuple(key, new FetchKey(fetcherName, key), new EmitKey(emitterName, key), new Metadata(), parseContext,
                    getOnParseException()));
            count++;
        }
        long elapsed = System.currentTimeMillis() - start;
        LOGGER.info("finished enqueuing {} files in {} ms", count, elapsed);
    }

    private boolean accept(Matcher fileNameMatcher, String key) {
        String fName = FilenameUtils.getName(key);
        return fileNameMatcher
                .reset(fName)
                .find();
    }
}
