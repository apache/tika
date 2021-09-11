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
import java.util.Map;
import java.util.regex.Pattern;

import com.amazonaws.AmazonClientException;
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
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.pipes.fetcher.AbstractFetcher;
import org.apache.tika.pipes.fetcher.RangeFetcher;

/**
 * Fetches files from s3. Example string: s3://my_bucket/path/to/my_file.pdf
 * This will parse the bucket out of that string and retrieve the path.
 */
public class S3Fetcher extends AbstractFetcher implements Initializable, RangeFetcher {

    private static final Logger LOGGER = LoggerFactory.getLogger(S3Fetcher.class);
    private static final String PREFIX = "s3";
    private static final Pattern RANGE_PATTERN = Pattern.compile("\\A(.*?):(\\d+):(\\d+)\\Z");
    private String region;
    private String bucket;
    private String profile;
    private String credentialsProvider;
    private boolean extractUserMetadata = true;
    private AmazonS3 s3Client;
    private boolean spoolToTemp = true;

    @Override
    public InputStream fetch(String fetchKey, Metadata metadata) throws TikaException, IOException {

        LOGGER.debug("about to fetch fetchkey={} from bucket ({})", fetchKey, bucket);

        try {
            S3Object s3Object = s3Client.getObject(new GetObjectRequest(bucket, fetchKey));
            if (extractUserMetadata) {
                for (Map.Entry<String, String> e : s3Object.getObjectMetadata().getUserMetadata()
                        .entrySet()) {
                    metadata.add(PREFIX + ":" + e.getKey(), e.getValue());
                }
            }
            if (!spoolToTemp) {
                return TikaInputStream.get(s3Object.getObjectContent());
            } else {
                long start = System.currentTimeMillis();
                TikaInputStream tis = TikaInputStream.get(s3Object.getObjectContent());
                tis.getPath();
                long elapsed = System.currentTimeMillis() - start;
                LOGGER.debug("took {} ms to copy to local tmp file", elapsed);
                return tis;
            }
        } catch (AmazonClientException e) {
            throw new IOException("s3 client exception", e);
        }
    }

    @Override
    public InputStream fetch(String fetchKey, long startRange, long endRange, Metadata metadata)
            throws TikaException, IOException {
        //TODO -- figure out how to integrate this
        LOGGER.debug("about to fetch fetchkey={} (start={} end={}) from bucket ({})", fetchKey,
                startRange, endRange, bucket);

        try {
            S3Object s3Object = s3Client.getObject(
                    new GetObjectRequest(bucket, fetchKey).withRange(startRange, endRange));

            if (extractUserMetadata) {
                for (Map.Entry<String, String> e : s3Object.getObjectMetadata().getUserMetadata()
                        .entrySet()) {
                    metadata.add(PREFIX + ":" + e.getKey(), e.getValue());
                }
            }
            if (!spoolToTemp) {
                return TikaInputStream.get(s3Object.getObjectContent());
            } else {
                long start = System.currentTimeMillis();
                TikaInputStream tis = TikaInputStream.get(s3Object.getObjectContent());
                tis.getPath();
                long elapsed = System.currentTimeMillis() - start;
                LOGGER.debug("took {} ms to copy to local tmp file", elapsed);
                return tis;
            }
        } catch (AmazonClientException e) {
            throw new IOException(e);
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
     * Whether or not to extract user metadata from the S3Object
     *
     * @param extractUserMetadata
     */
    @Field
    public void setExtractUserMetadata(boolean extractUserMetadata) {
        this.extractUserMetadata = extractUserMetadata;
    }

    @Field
    public void setCredentialsProvider(String credentialsProvider) {
        if (!credentialsProvider.equals("profile") && !credentialsProvider.equals("instance")) {
            throw new IllegalArgumentException(
                    "credentialsProvider must be either 'profile' or instance'");
        }
        this.credentialsProvider = credentialsProvider;
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

        try {
            s3Client = AmazonS3ClientBuilder.standard().withRegion(region).withCredentials(provider)
                    .build();
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
