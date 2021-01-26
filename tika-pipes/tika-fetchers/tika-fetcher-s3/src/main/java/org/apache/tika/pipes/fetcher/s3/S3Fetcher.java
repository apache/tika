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

import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.S3Object;
import org.apache.commons.io.IOUtils;
import org.apache.tika.config.Field;
import org.apache.tika.config.Initializable;
import org.apache.tika.config.InitializableProblemHandler;
import org.apache.tika.config.Param;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.pipes.fetcher.AbstractFetcher;
import org.apache.tika.pipes.fetcher.FetchId;
import org.apache.tika.pipes.fetcher.Fetcher;
import org.apache.tika.pipes.fetcher.FetcherStringException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.CopyOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * Fetches files from s3. Example string: s3://my_bucket/path/to/my_file.pdf
 * This will parse the bucket out of that string and retrieve the path.
 */
public class S3Fetcher extends AbstractFetcher implements Initializable {

    private static final Logger LOGGER = LoggerFactory.getLogger(S3Fetcher.class);
    private static final String PREFIX = "s3";
    private String region;
    private String profile;
    private boolean extractUserMetadata = true;
    private AmazonS3 s3Client;
    private boolean spoolToTemp = true;

    @Override
    public InputStream fetch(String fetchKey, Metadata metadata)
            throws TikaException, IOException {

        LOGGER.debug("about to fetch fetchkey={}", fetchKey);
        final String origFetchKey = fetchKey;
        if (fetchKey.startsWith("//")) {
            fetchKey = fetchKey.substring(2);
        } else if (fetchKey.startsWith("/")) {
            fetchKey = fetchKey.substring(1);
        }
        int i = fetchKey.indexOf("/");
        if (i < 0) {
            throw new FetcherStringException("Couldn't find bucket:" +
                    origFetchKey);
        }
        String bucket = fetchKey.substring(0, i);
        String key = fetchKey.substring(i + 1);
        LOGGER.debug("about to fetch bucket: ({}); key: ({})", bucket, key);
        S3Object fullObject = s3Client.getObject(new GetObjectRequest(bucket, key));
        if (extractUserMetadata) {
            for (Map.Entry<String, String> e :
                    fullObject.getObjectMetadata().getUserMetadata().entrySet()) {
                metadata.add(PREFIX + ":" + e.getKey(), e.getValue());
            }
        }
        if (! spoolToTemp) {
            return TikaInputStream.get(
                    fullObject.getObjectContent());
        } else {
            long start = System.currentTimeMillis();
            TikaInputStream tis = TikaInputStream.get(
                    fullObject.getObjectContent());
            tis.getPath();
            long elapsed = System.currentTimeMillis()-start;
            LOGGER.debug("took {} ms to copy to local tmp file", elapsed);
            return tis;
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

    /**
     * Whether or not to extract user metadata from the S3Object
     *
     * @param extractUserMetadata
     */
    @Field
    public void setExtractUserMetadata(boolean extractUserMetadata) {
        this.extractUserMetadata = extractUserMetadata;
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
    public void checkInitialization(InitializableProblemHandler problemHandler) throws TikaConfigException {

    }
}
