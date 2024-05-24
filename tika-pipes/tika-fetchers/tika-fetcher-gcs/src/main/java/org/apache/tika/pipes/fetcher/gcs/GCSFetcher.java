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
package org.apache.tika.pipes.fetcher.gcs;

import static org.apache.tika.config.TikaConfig.mustNotBeEmpty;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Map;

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.config.Field;
import org.apache.tika.config.Initializable;
import org.apache.tika.config.InitializableProblemHandler;
import org.apache.tika.config.Param;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.pipes.FetchEmitTuple;
import org.apache.tika.pipes.fetcher.AbstractFetcher;

/**
 * Fetches files from google cloud storage. Must set projectId and bucket via the config.
 */
public class GCSFetcher extends AbstractFetcher implements Initializable {

    private static String PREFIX = "gcs";
    private static final Logger LOGGER = LoggerFactory.getLogger(GCSFetcher.class);
    private String projectId;
    private String bucket;
    private boolean extractUserMetadata = true;
    private Storage storage;
    private boolean spoolToTemp = true;

    @Override
    public InputStream fetch(FetchEmitTuple fetchEmitTuple) throws TikaException, IOException {
        if (fetchEmitTuple.getFetchKey().hasRange()) {
            throw new IllegalArgumentException("GCS fetcher does not support range queries");
        }
        String fetchKey = fetchEmitTuple.getFetchKey().getFetchKey();
        Metadata metadata = fetchEmitTuple.getMetadata();
        LOGGER.debug("about to fetch fetchkey={} from bucket ({})", fetchKey, bucket);

        try {
            Blob blob = storage.get(BlobId.of(bucket, fetchKey));

            if (extractUserMetadata) {
                if (blob.getMetadata() != null) {
                    for (Map.Entry<String, String> e : blob.getMetadata().entrySet()) {
                        metadata.add(PREFIX + ":" + e.getKey(), e.getValue());
                    }
                }
            }
            if (!spoolToTemp) {
                return TikaInputStream.get(blob.getContent());
            } else {
                long start = System.currentTimeMillis();
                TemporaryResources tmpResources = new TemporaryResources();
                Path tmp = tmpResources.createTempFile();
                blob.downloadTo(tmp);
                TikaInputStream tis = TikaInputStream.get(tmp, metadata, tmpResources);
                long elapsed = System.currentTimeMillis() - start;
                LOGGER.debug("took {} ms to copy to local tmp file", elapsed);
                return tis;
            }
        } catch (Exception e) {
            throw new IOException("gcs storage exception", e);
        }
    }

    @Field
    public void setSpoolToTemp(boolean spoolToTemp) {
        this.spoolToTemp = spoolToTemp;
    }

    @Field
    public void setProjectId(String projectId) {
        this.projectId = projectId;
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

    //TODO: parameterize extracting other blob metadata, eg. md5, crc, etc.

    /**
     * This initializes the gcs storage client.
     *
     * @param params params to use for initialization
     * @throws TikaConfigException
     */
    @Override
    public void initialize(Map<String, Param> params) throws TikaConfigException {
        //params have already been set...ignore them
        //TODO -- add other params to the builder as needed
        storage = StorageOptions.newBuilder().setProjectId(projectId).build().getService();
    }

    @Override
    public void checkInitialization(InitializableProblemHandler problemHandler)
            throws TikaConfigException {
        mustNotBeEmpty("bucket", this.bucket);
        mustNotBeEmpty("projectId", this.projectId);
    }
}
