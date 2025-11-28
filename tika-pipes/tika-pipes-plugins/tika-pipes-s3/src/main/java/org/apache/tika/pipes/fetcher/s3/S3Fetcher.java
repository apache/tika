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
package org.apache.tika.pipes.fetcher.s3;

import static org.apache.tika.config.TikaConfig.mustNotBeEmpty;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.InstanceProfileCredentialsProvider;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.http.SdkHttpConfigurationOption;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import org.apache.tika.exception.FileTooLongException;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.config.ComponentConfigs;
import org.apache.tika.io.FilenameUtils;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.pipes.api.fetcher.Fetcher;
import org.apache.tika.pipes.api.fetcher.RangeFetcher;
import org.apache.tika.pipes.fetcher.s3.config.S3FetcherConfig;
import org.apache.tika.plugins.AbstractTikaExtension;
import org.apache.tika.plugins.ExtensionConfig;
import org.apache.tika.utils.StringUtils;

/**
 * Fetches files from s3. Example file: s3://my_bucket/path/to/my_file.pdf
 * The bucket must be specified via the tika-config or before
 * initialization, and the fetch key is "path/to/my_file.pdf".
 */
public class S3Fetcher extends AbstractTikaExtension implements Fetcher, RangeFetcher {

    private static final Logger LOGGER = LoggerFactory.getLogger(S3Fetcher.class);
    private static final String PREFIX = "s3";

    //Do not retry if there's an AmazonS3Exception with this error code
    private static final Set<String> NO_RETRY_ERROR_CODES = new HashSet<>();

    static {
        NO_RETRY_ERROR_CODES.add("AccessDenied");
        NO_RETRY_ERROR_CODES.add("NoSuchKey");
        NO_RETRY_ERROR_CODES.add("ExpiredToken");
        NO_RETRY_ERROR_CODES.add("InvalidAccessKeyId");
        NO_RETRY_ERROR_CODES.add("InvalidRange");
        NO_RETRY_ERROR_CODES.add("InvalidRequest");
    }

    private final Object[] clientLock = new Object[0];
    private S3FetcherConfig config;
    private S3Client s3Client;

    private S3Fetcher(ExtensionConfig pluginConfig) {
        super(pluginConfig);
    }

    public static S3Fetcher build(ExtensionConfig extensionConfig) throws IOException, TikaConfigException {
        S3FetcherConfig config = S3FetcherConfig.load(extensionConfig.jsonConfig());
        S3Fetcher fetcher = new S3Fetcher(extensionConfig);
        fetcher.config = config;
        fetcher.initialize();
        return fetcher;
    }

    private void initialize() throws TikaConfigException {
        mustNotBeEmpty("bucket", config.getBucket());
        mustNotBeEmpty("region", config.getRegion());

        AwsCredentialsProvider provider;
        String credentialsProvider = config.getCredentialsProvider();
        if (credentialsProvider == null) {
            credentialsProvider = "instance";
        }
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
                throw new TikaConfigException("credentialsProvider must be set and must be either 'instance', 'profile' or 'key_secret'");
        }

        int maxConnections = config.getMaxConnections();
        if (maxConnections <= 0) {
            maxConnections = SdkHttpConfigurationOption.GLOBAL_HTTP_DEFAULTS.get(SdkHttpConfigurationOption.MAX_CONNECTIONS);
        }

        SdkHttpClient httpClient = ApacheHttpClient.builder().maxConnections(maxConnections).build();
        S3Configuration clientConfig = S3Configuration.builder().pathStyleAccessEnabled(config.isPathStyleAccessEnabled()).build();
        try {
            synchronized (clientLock) {
                S3ClientBuilder s3ClientBuilder = S3Client.builder().httpClient(httpClient)
                        .serviceConfiguration(clientConfig).credentialsProvider(provider);
                if (!StringUtils.isBlank(config.getEndpointConfigurationService())) {
                    try {
                        s3ClientBuilder.endpointOverride(new URI(config.getEndpointConfigurationService())).region(Region.of(config.getRegion()));
                    } catch (URISyntaxException ex) {
                        throw new TikaConfigException("bad endpointConfigurationService: " + config.getEndpointConfigurationService(), ex);
                    }
                } else {
                    s3ClientBuilder.region(Region.of(config.getRegion()));
                }
                s3Client = s3ClientBuilder.build();
            }
        } catch (SdkClientException e) {
            throw new TikaConfigException("can't initialize s3 fetcher", e);
        }
    }

    @Override
    public InputStream fetch(String fetchKey, Metadata metadata, ComponentConfigs componentConfigs) throws TikaException, IOException {
        return fetch(fetchKey, -1, -1, metadata, componentConfigs);
    }

    @Override
    public InputStream fetch(String fetchKey, long startRange, long endRange, Metadata metadata, ComponentConfigs componentConfigs)
            throws TikaException, IOException {
        String prefix = config.getPrefix();
        String theFetchKey = StringUtils.isBlank(prefix) ? fetchKey : prefix + fetchKey;

        if (LOGGER.isDebugEnabled()) {
            if (startRange > -1) {
                LOGGER.debug("about to fetch fetchkey={} (start={} end={}) from bucket ({})",
                        theFetchKey, startRange, endRange, config.getBucket());
            } else {
                LOGGER.debug("about to fetch fetchkey={} from bucket ({})",
                        theFetchKey, config.getBucket());
            }
        }

        long[] throttleSeconds = config.getThrottleSeconds();
        int tries = 0;
        IOException ex = null;
        do {
            try {
                long start = System.currentTimeMillis();
                InputStream is = _fetch(theFetchKey, metadata, startRange, endRange);
                long elapsed = System.currentTimeMillis() - start;
                LOGGER.debug("total to fetch {}", elapsed);
                return is;
            } catch (AwsServiceException e) {
                if (e.awsErrorDetails() != null) {
                    String errorCode = e.awsErrorDetails().errorCode();
                    if (errorCode != null && NO_RETRY_ERROR_CODES.contains(e.awsErrorDetails().errorCode())) {
                        LOGGER.warn("Hit a no retry error code. Not retrying." + tries, e);
                        throw new IOException(e);
                    }
                }
                LOGGER.warn("client exception fetching on retry=" + tries, e);
                ex = new IOException(e);
            } catch (SdkClientException e) {
                LOGGER.warn("client exception fetching on retry=" + tries, e);
                ex = new IOException(e);
            } catch (IOException e) {
                LOGGER.warn("client exception fetching on retry=" + tries, e);
                ex = e;
            }
            if (throttleSeconds != null && tries < throttleSeconds.length) {
                LOGGER.warn("sleeping for {} seconds before retry", throttleSeconds[tries]);
                try {
                    Thread.sleep(throttleSeconds[tries] * 1000);
                } catch (InterruptedException e) {
                    throw new RuntimeException("interrupted");
                }
            }
        } while (throttleSeconds != null && ++tries < throttleSeconds.length);

        throw ex;
    }

    private InputStream _fetch(String fetchKey, Metadata metadata,
                               Long startRange, Long endRange) throws IOException {
        TemporaryResources tmp = null;
        ResponseInputStream<GetObjectResponse> s3Object = null;
        try {
            long start = System.currentTimeMillis();
            GetObjectRequest.Builder builder = GetObjectRequest.builder().bucket(config.getBucket()).key(fetchKey);
            if (startRange != null && endRange != null
                    && startRange > -1 && endRange > -1) {
                String range = String.format(Locale.US, "bytes=%d-%d", startRange, endRange);
                builder.range(range);
            }
            GetObjectRequest objectRequest = builder.build();
            synchronized (clientLock) {
                s3Object = s3Client.getObject(objectRequest);
            }
            long length = s3Object.response().contentLength();
            metadata.set(Metadata.CONTENT_LENGTH, Long.toString(length));
            long maxLength = config.getMaxLength();
            if (maxLength > -1) {
                if (length > maxLength) {
                    throw new FileTooLongException(length, maxLength);
                }
            }
            LOGGER.debug("took {} ms to fetch file's metadata", System.currentTimeMillis() - start);

            if (config.isExtractUserMetadata()) {
                for (Map.Entry<String, String> e : s3Object.response().metadata().entrySet()) {
                    metadata.add(PREFIX + ":" + e.getKey(), e.getValue());
                }
            }
            if (!config.isSpoolToTemp()) {
                return TikaInputStream.get(s3Object);
            } else {
                start = System.currentTimeMillis();
                tmp = new TemporaryResources();
                Path tmpPath = tmp.createTempFile(FilenameUtils.getSuffixFromPath(fetchKey));
                Files.copy(s3Object, tmpPath, StandardCopyOption.REPLACE_EXISTING);
                TikaInputStream tis = TikaInputStream.get(tmpPath, metadata, tmp);
                LOGGER.debug("took {} ms to fetch metadata and copy to local tmp file",
                        System.currentTimeMillis() - start);
                return tis;
            }
        } catch (Throwable e) {
            if (tmp != null) {
                tmp.close();
            }
            if (s3Object != null) {
                s3Object.close();
            }
            throw e;
        }
    }
}
