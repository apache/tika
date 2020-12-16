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
package org.apache.tika.fetcher.s3;

import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.S3Object;
import org.apache.tika.config.Field;
import org.apache.tika.exception.TikaException;
import org.apache.tika.fetcher.Fetcher;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Optional;

public class S3Fetcher implements Fetcher {

    private static final String PREFIX = "s3:";

    @Field
    private String bucket;

    @Field
    private String key;

    @Field
    private String region;

    @Override
    public boolean canFetch(String url) {
        return url.startsWith(PREFIX);
    }

    @Override
    public Optional<InputStream> fetch(String url, Metadata metadata) throws TikaException, IOException {
        //TODO cache this client so we're not starting a new one with every request
        S3Object fullObject = null;
        try {
            AmazonS3 s3Client = AmazonS3ClientBuilder.standard()
                    .withRegion(getRegion())
                    .withCredentials(new ProfileCredentialsProvider())
                    .build();
            fullObject = s3Client.getObject(new GetObjectRequest(bucket, key));
            updateMetadata(fullObject.getObjectMetadata(), metadata);
            return Optional.of(TikaInputStream.get(fullObject.getObjectContent()));
        } finally {
            if (fullObject != null) {
                fullObject.close();
            }
        }
    }

    private void updateMetadata(ObjectMetadata objectMetadata, Metadata metadata) {
        //TODO: what else do we want to grab?
        for (Map.Entry<String, String> e : objectMetadata.getUserMetadata().entrySet()) {
            metadata.add(PREFIX+e.getKey(), e.getValue());
        }
    }

    public Regions getRegion() {
        if (region == null) {
            return Regions.DEFAULT_REGION;
        } else {
            return Regions.fromName(region);
        }
    }

    public String getBucket() {
        return bucket;
    }

    public String getKey() {
        return key;
    }
}
