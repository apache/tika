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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;

import com.amazonaws.AmazonClientException;
import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.InstanceProfileCredentialsProvider;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.S3Object;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.config.Field;
import org.apache.tika.config.Initializable;
import org.apache.tika.config.InitializableProblemHandler;
import org.apache.tika.config.Param;
import org.apache.tika.exception.FileTooLongException;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.pipes.fetcher.AbstractFetcher;
import org.apache.tika.pipes.fetcher.RangeFetcher;
import org.apache.tika.utils.StringUtils;

/**
 * Fetches files from s3. Example file: s3://my_bucket/path/to/my_file.pdf
 * The bucket must be specified via the tika-config or before
 * initialization, and the fetch key is "path/to/my_file.pdf".
 */
public class S3Fetcher extends AbstractFetcher implements Initializable, RangeFetcher {

    private static final Logger LOGGER = LoggerFactory.getLogger(S3Fetcher.class);
    private static final String PREFIX = "s3";
    private final Object[] clientLock = new Object[0];
    private String region;
    private String bucket;
    private String profile;
    private String prefix;
    private String credentialsProvider;
    private boolean extractUserMetadata = true;
    private int maxConnections = ClientConfiguration.DEFAULT_MAX_CONNECTIONS;
    private AmazonS3 s3Client;
    private boolean spoolToTemp = true;
    private int retries = 0;
    private long maxLength = -1;

    @Override
    public InputStream fetch(String fetchKey, Metadata metadata) throws TikaException, IOException {
        return fetch(fetchKey, -1, -1, metadata);
    }

    @Override
    public InputStream fetch(String fetchKey, long startRange, long endRange, Metadata metadata)
            throws TikaException, IOException {
        String theFetchKey = StringUtils.isBlank(prefix) ? fetchKey : prefix + fetchKey;

        if (LOGGER.isDebugEnabled()) {
            if (startRange > -1) {
                LOGGER.debug("about to fetch fetchkey={} (start={} end={}) from bucket ({})",
                        theFetchKey, startRange, endRange, bucket);
            } else {
                LOGGER.debug("about to fetch fetchkey={} from bucket ({})",
                        theFetchKey, bucket);
            }
        }
        int tries = 0;
        IOException ex = null;
        while (tries++ <= retries) {
            if (tries > 1) {
                LOGGER.warn("sleeping for 30 seconds before retry");
                try {
                    Thread.sleep(30000);
                } catch (InterruptedException e) {
                    throw new RuntimeException("interrupted");
                }
                LOGGER.info("trying to re-initialize S3 client");
                initialize(new HashMap<>());
            }
            try {
                long start = System.currentTimeMillis();
                InputStream is = _fetch(theFetchKey, metadata, startRange, endRange);
                long elapsed = System.currentTimeMillis() - start;
                LOGGER.debug("total to fetch {}", elapsed);
                return is;
            } catch (AmazonClientException e ) {
                //TODO -- filter exceptions -- if the file doesn't exist, don't retry
                LOGGER.warn("client exception fetching on retry=" + tries, e);
                ex = new IOException(e);
            } catch (IOException e) {
                //TODO -- filter exceptions -- if the file doesn't exist, don't retry
                LOGGER.warn("client exception fetching on retry=" + tries, e);
                ex = e;
            }
        }
        throw ex;
    }

    private InputStream _fetch(String fetchKey, Metadata metadata,
                               Long startRange, Long endRange) throws IOException {
        TemporaryResources tmp = null;
        try {
            long start = System.currentTimeMillis();
            GetObjectRequest objectRequest = new GetObjectRequest(bucket, fetchKey);
            if (startRange != null && endRange != null
                    && startRange > -1 && endRange > -1) {
                objectRequest.withRange(startRange, endRange);
            }
            S3Object s3Object = null;
            synchronized (clientLock) {
                s3Object = s3Client.getObject(objectRequest);
            }
            long length = s3Object.getObjectMetadata().getContentLength();
            metadata.set(Metadata.CONTENT_LENGTH, Long.toString(length));
            if (maxLength > -1) {
                if (length > maxLength) {
                    throw new FileTooLongException(length, maxLength);
                }
            }
            LOGGER.debug("took {} ms to fetch file's metadata", System.currentTimeMillis() - start);

            if (extractUserMetadata) {
                for (Map.Entry<String, String> e : s3Object.getObjectMetadata().getUserMetadata()
                        .entrySet()) {
                    metadata.add(PREFIX + ":" + e.getKey(), e.getValue());
                }
            }
            if (!spoolToTemp) {
                return TikaInputStream.get(s3Object.getObjectContent());
            } else {
                start = System.currentTimeMillis();
                tmp = new TemporaryResources();
                Path tmpPath = tmp.createTempFile();
                Files.copy(s3Object.getObjectContent(), tmpPath, StandardCopyOption.REPLACE_EXISTING);
                TikaInputStream tis = TikaInputStream.get(tmpPath, metadata, tmp);
                LOGGER.debug("took {} ms to fetch metadata and copy to local tmp file",
                        System.currentTimeMillis() - start);
                return tis;
            }
        } catch (Throwable e) {
            if (tmp != null) {
                tmp.close();
            }
            throw e;
        }
    }

    @Field
    public void setSpoolToTemp(boolean spoolToTemp) {
        this.spoolToTemp = spoolToTemp;
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
    public void setBucket(String bucket) {
        this.bucket = bucket;
    }

    /**
     * prefix to prepend to the fetch key before fetching.
     * This will automatically add a '/' at the end.
     * @param prefix
     */
    @Field
    public void setPrefix(String prefix) {
        //guarantee that the prefix ends with /
        if (! prefix.endsWith("/")) {
            prefix += "/";
        }
        this.prefix = prefix;
    }
    /**
     * Whether or not to extract user metadata from the S3Object
     *
     * @param extractUserMetadata
     */
    @Field
    public void setExtractUserMetadata(boolean extractUserMetadata) {
        this.extractUserMetadata = extractUserMetadata;
    }

    @Field
    public void setMaxConnections(int maxConnections) {
        this.maxConnections = maxConnections;
    }

    @Field
    public void setRetries(int retries) {
        this.retries = retries;
    }

    @Field
    public void setCredentialsProvider(String credentialsProvider) {
        if (!credentialsProvider.equals("profile") && !credentialsProvider.equals("instance")) {
            throw new IllegalArgumentException(
                    "credentialsProvider must be either 'profile' or 'instance'");
        }
        this.credentialsProvider = credentialsProvider;
    }

    @Field
    public void setMaxLength(long maxLength) {
        this.maxLength = maxLength;
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
        AWSCredentialsProvider provider = null;
        if ("instance".equals(credentialsProvider)) {
            provider = InstanceProfileCredentialsProvider.getInstance();
        } else if ("profile".equals(credentialsProvider)) {
            provider = new ProfileCredentialsProvider(profile);
        } else {
            throw new TikaConfigException("credentialsProvider must be set and " +
                    "must be either 'instance' or 'profile'");
        }
        ClientConfiguration clientConfiguration = new ClientConfiguration()
                        .withMaxConnections(maxConnections);
        try {
            synchronized (clientLock) {
                s3Client = AmazonS3ClientBuilder.standard()
                        .withClientConfiguration(clientConfiguration)
                        .withRegion(region)
                        .withCredentials(provider).build();
            }
        } catch (AmazonClientException e) {
            throw new TikaConfigException("can't initialize s3 fetcher", e);
        }
    }

    @Override
    public void checkInitialization(InitializableProblemHandler problemHandler)
            throws TikaConfigException {
        mustNotBeEmpty("bucket", this.bucket);
        mustNotBeEmpty("region", this.region);
    }
}
