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
package org.apache.tika.pipes.iterator.s3;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
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
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.S3Object;

import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.io.FilenameUtils;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.pipes.api.FetchEmitTuple;
import org.apache.tika.pipes.api.HandlerConfig;
import org.apache.tika.pipes.api.emitter.EmitKey;
import org.apache.tika.pipes.api.fetcher.FetchKey;
import org.apache.tika.pipes.api.pipesiterator.PipesIteratorBaseConfig;
import org.apache.tika.pipes.pipesiterator.PipesIteratorBase;
import org.apache.tika.plugins.ExtensionConfig;
import org.apache.tika.utils.StringUtils;

public class S3PipesIterator extends PipesIteratorBase {

    private static final Logger LOGGER = LoggerFactory.getLogger(S3PipesIterator.class);

    private final S3PipesIteratorConfig config;
    private final Pattern fileNamePattern;
    private final S3Client s3Client;

    private S3PipesIterator(S3PipesIteratorConfig config, ExtensionConfig extensionConfig) throws TikaConfigException {
        super(extensionConfig);
        this.config = config;

        String fileNamePatternStr = config.getFileNamePattern();
        this.fileNamePattern = StringUtils.isBlank(fileNamePatternStr) ? null : Pattern.compile(fileNamePatternStr);

        if (StringUtils.isBlank(config.getBucket())) {
            throw new TikaConfigException("bucket must not be empty");
        }
        if (StringUtils.isBlank(config.getRegion())) {
            throw new TikaConfigException("region must not be empty");
        }

        // Initialize S3 client
        String credentialsProvider = config.getCredentialsProvider();
        if (credentialsProvider == null) {
            credentialsProvider = "instance";
        }
        AwsCredentialsProvider provider;
        switch (credentialsProvider) {
            case "instance":
                provider = InstanceProfileCredentialsProvider.builder().build();
                break;
            case "profile":
                provider = ProfileCredentialsProvider.builder().profileName(config.getProfile()).build();
                break;
            case "key_secret":
                AwsBasicCredentials awsCreds = AwsBasicCredentials.create(config.getAccessKey(), config.getSecretKey());
                provider = StaticCredentialsProvider.create(awsCreds);
                break;
            default:
                throw new TikaConfigException("credentialsProvider must be set and " + "must be either 'instance', 'profile' or 'key_secret'");
        }

        SdkHttpClient httpClient = ApacheHttpClient.builder().maxConnections(config.getMaxConnections()).build();
        S3Configuration clientConfig = S3Configuration.builder().pathStyleAccessEnabled(config.isPathStyleAccessEnabled()).build();
        try {
            S3ClientBuilder s3ClientBuilder = S3Client.builder().httpClient(httpClient).
                    serviceConfiguration(clientConfig).credentialsProvider(provider);
            String endpointConfigurationService = config.getEndpointConfigurationService();
            if (!StringUtils.isBlank(endpointConfigurationService)) {
                try {
                    s3ClientBuilder.endpointOverride(new URI(endpointConfigurationService)).region(Region.of(config.getRegion()));
                } catch (URISyntaxException ex) {
                    throw new TikaConfigException("bad endpointConfigurationService: " + endpointConfigurationService, ex);
                }
            } else {
                s3ClientBuilder.region(Region.of(config.getRegion()));
            }
            s3Client = s3ClientBuilder.build();
        } catch (SdkClientException e) {
            throw new TikaConfigException("can't initialize s3 pipesiterator", e);
        }
    }

    public static S3PipesIterator build(ExtensionConfig extensionConfig) throws IOException, TikaConfigException {
        S3PipesIteratorConfig config = S3PipesIteratorConfig.load(extensionConfig.jsonConfig());
        return new S3PipesIterator(config, extensionConfig);
    }

    @Override
    protected void enqueue() throws InterruptedException, IOException, TimeoutException {
        PipesIteratorBaseConfig baseConfig = config.getBaseConfig();
        String fetcherPluginId = baseConfig.fetcherId();
        String emitterName = baseConfig.emitterId();
        long start = System.currentTimeMillis();
        int count = 0;
        HandlerConfig handlerConfig = baseConfig.handlerConfig();
        final Matcher fileNameMatcher;
        if (fileNamePattern != null) {
            fileNameMatcher = fileNamePattern.matcher("");
        } else {
            fileNameMatcher = null;
        }

        ListObjectsV2Request listObjectsV2Request = ListObjectsV2Request.builder().bucket(config.getBucket()).prefix(config.getPrefix()).build();
        List<S3Object> s3ObjectList = s3Client.listObjectsV2Paginator(listObjectsV2Request).stream().
                flatMap(resp -> resp.contents().stream()).toList();
        for (S3Object s3Object : s3ObjectList) {
            String key = s3Object.key();
            if (fileNameMatcher != null && !accept(fileNameMatcher, key)) {
                continue;
            }
            long elapsed = System.currentTimeMillis() - start;
            LOGGER.debug("adding ({}) {} in {} ms", count, key, elapsed);
            ParseContext parseContext = new ParseContext();
            parseContext.set(HandlerConfig.class, handlerConfig);
            tryToAdd(new FetchEmitTuple(key, new FetchKey(fetcherPluginId, key), new EmitKey(emitterName, key), new Metadata(), parseContext,
                    baseConfig.onParseException()));
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
