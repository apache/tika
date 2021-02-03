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
package org.apache.tika.pipes.fetchiterator.s3;

import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.iterable.S3Objects;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import org.apache.tika.config.Field;
import org.apache.tika.config.Initializable;
import org.apache.tika.config.InitializableProblemHandler;
import org.apache.tika.config.Param;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.pipes.emitter.EmitKey;
import org.apache.tika.pipes.fetcher.FetchKey;
import org.apache.tika.pipes.fetchiterator.FetchEmitTuple;
import org.apache.tika.pipes.fetchiterator.FetchIterator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import static org.apache.tika.config.TikaConfig.mustNotBeEmpty;

public class S3FetchIterator extends FetchIterator implements Initializable {


    private static final Logger LOGGER = LoggerFactory.getLogger(S3FetchIterator.class);
    private String s3PathPrefix = "";
    private String region;
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
    public void setS3PathPrefix(String s3PathPrefix) {
        this.s3PathPrefix = s3PathPrefix;
    }

    @Override
    public void initialize(Map<String, Param> params) throws TikaConfigException {
        //params have already been set
        //ignore them

        s3Client = AmazonS3ClientBuilder.standard()
                .withRegion(region)
                .withCredentials(new ProfileCredentialsProvider(profile))
                .build();
    }

    @Override
    public void checkInitialization(InitializableProblemHandler problemHandler)
            throws TikaConfigException {
        super.checkInitialization(problemHandler);
        mustNotBeEmpty("bucket", this.bucket);
        mustNotBeEmpty("profile", this.profile);
        mustNotBeEmpty("region", this.region);
    }

    @Override
    protected void enqueue() throws InterruptedException, IOException, TimeoutException {
        String fetcherName = getFetcherName();
        long start = System.currentTimeMillis();
        int count = 0;
        for (S3ObjectSummary summary : S3Objects.withPrefix(s3Client, bucket, s3PathPrefix)) {

            long elapsed = System.currentTimeMillis() - start;
            LOGGER.debug("adding ({}) {} in {} ms", count, summary.getKey(),
                    elapsed);
            tryToAdd(new FetchEmitTuple(
                    new FetchKey(fetcherName, summary.getKey()),
                    new EmitKey(fetcherName, summary.getKey()),
                    new Metadata(), getOnParseException()));
            count++;
        }
        long elapsed = System.currentTimeMillis() - start;
        LOGGER.info("finished enqueuing {} files in {} ms", count, elapsed);
    }
}
