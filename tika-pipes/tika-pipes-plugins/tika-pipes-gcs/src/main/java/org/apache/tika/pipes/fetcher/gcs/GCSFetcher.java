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

import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.pipes.api.fetcher.Fetcher;
import org.apache.tika.pipes.fetcher.gcs.config.GCSFetcherConfig;
import org.apache.tika.plugins.AbstractTikaExtension;
import org.apache.tika.plugins.ExtensionConfig;

/**
 * Fetches files from google cloud storage. Must set projectId and bucket via the config.
 */
public class GCSFetcher extends AbstractTikaExtension implements Fetcher {

    private static final String PREFIX = "gcs";
    private static final Logger LOGGER = LoggerFactory.getLogger(GCSFetcher.class);

    private GCSFetcherConfig config;
    private Storage storage;

    private GCSFetcher(ExtensionConfig pluginConfig) {
        super(pluginConfig);
    }

    public static GCSFetcher build(ExtensionConfig extensionConfig) throws IOException, TikaConfigException {
        GCSFetcherConfig config = GCSFetcherConfig.load(extensionConfig.json());
        GCSFetcher fetcher = new GCSFetcher(extensionConfig);
        fetcher.config = config;
        fetcher.initialize();
        return fetcher;
    }

    private void initialize() throws TikaConfigException {
        mustNotBeEmpty("bucket", config.getBucket());
        mustNotBeEmpty("projectId", config.getProjectId());

        storage = StorageOptions.newBuilder()
                .setProjectId(config.getProjectId())
                .build()
                .getService();
    }

    @Override
    public InputStream fetch(String fetchKey, Metadata metadata, ParseContext parseContext)
            throws TikaException, IOException {

        LOGGER.debug("about to fetch fetchkey={} from bucket ({})", fetchKey, config.getBucket());

        try {
            Blob blob = storage.get(BlobId.of(config.getBucket(), fetchKey));

            if (config.isExtractUserMetadata()) {
                if (blob.getMetadata() != null) {
                    for (Map.Entry<String, String> e : blob.getMetadata().entrySet()) {
                        metadata.add(PREFIX + ":" + e.getKey(), e.getValue());
                    }
                }
            }
            if (!config.isSpoolToTemp()) {
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
}
