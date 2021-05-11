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
import java.util.Map;
import java.util.concurrent.TimeoutException;

import com.amazonaws.AmazonClientException;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.InstanceProfileCredentialsProvider;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.iterable.S3Objects;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.config.Field;
import org.apache.tika.config.Initializable;
import org.apache.tika.config.InitializableProblemHandler;
import org.apache.tika.config.Param;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.pipes.FetchEmitTuple;
import org.apache.tika.pipes.HandlerConfig;
import org.apache.tika.pipes.emitter.EmitKey;
import org.apache.tika.pipes.fetcher.FetchKey;
import org.apache.tika.pipes.pipesiterator.PipesIterator;

public class S3PipesIterator extends PipesIterator implements Initializable {

    private static final Logger LOGGER = LoggerFactory.getLogger(S3PipesIterator.class);
    private String prefix = "";
    private String region;
    private String credentialsProvider;
    private String profile;
    private String bucket;

    private AmazonS3 s3Client;

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
            throw new TikaConfigException("can't initialize s3 pipesiterator");
        }
    }

    @Override
    public void checkInitialization(InitializableProblemHandler problemHandler)
            throws TikaConfigException {
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
        for (S3ObjectSummary summary : S3Objects.withPrefix(s3Client, bucket, prefix)) {

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
}
