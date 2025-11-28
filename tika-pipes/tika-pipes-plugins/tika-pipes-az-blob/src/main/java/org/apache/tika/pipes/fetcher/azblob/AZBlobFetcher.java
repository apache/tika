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
package org.apache.tika.pipes.fetcher.azblob;

import static org.apache.tika.config.TikaConfig.mustNotBeEmpty;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Map;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobClientBuilder;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.blob.models.BlobProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.config.ComponentConfigs;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.pipes.api.fetcher.Fetcher;
import org.apache.tika.pipes.fetcher.azblob.config.AZBlobFetcherConfig;
import org.apache.tika.plugins.AbstractTikaExtension;
import org.apache.tika.plugins.ExtensionConfig;
import org.apache.tika.utils.StringUtils;

/**
 * Fetches files from Azure blob storage.
 * <p>
 * There are two modes:
 * 1) If you are only using one endpoint and one sas token and one container,
 * configure those in the config file.  In this case, your fetchKey will
 * be the path in the container to the blob.
 * 2) If you have different endpoints or sas tokens or containers across
 * your requests, your fetchKey will be the complete SAS url pointing to the blob.
 */
public class AZBlobFetcher extends AbstractTikaExtension implements Fetcher {

    private static final Logger LOGGER = LoggerFactory.getLogger(AZBlobFetcher.class);
    private static final String PREFIX = "az-blob";

    private AZBlobFetcherConfig config;
    private BlobClientFactory blobClientFactory;

    private AZBlobFetcher(ExtensionConfig pluginConfig) {
        super(pluginConfig);
    }

    public static AZBlobFetcher build(ExtensionConfig extensionConfig) throws IOException, TikaConfigException {
        AZBlobFetcherConfig config = AZBlobFetcherConfig.load(extensionConfig.jsonConfig());
        AZBlobFetcher fetcher = new AZBlobFetcher(extensionConfig);
        fetcher.config = config;
        fetcher.initialize();
        return fetcher;
    }

    private void initialize() throws TikaConfigException {
        // Validation - if the user has set one of these, they need to have set all of them
        if (!StringUtils.isBlank(config.getSasToken())
                || !StringUtils.isBlank(config.getEndpoint())
                || !StringUtils.isBlank(config.getContainer())) {
            mustNotBeEmpty("sasToken", config.getSasToken());
            mustNotBeEmpty("endpoint", config.getEndpoint());
            mustNotBeEmpty("container", config.getContainer());
        }

        if (!StringUtils.isBlank(config.getSasToken())) {
            LOGGER.debug("Setting up immutable endpoint, token and container");
            blobClientFactory = new SingleBlobContainerFactory(
                    config.getEndpoint(), config.getSasToken(), config.getContainer());
        } else {
            LOGGER.debug("Setting up blobclientfactory to receive the full sas url for the blob");
            blobClientFactory = new SASUrlFactory();
        }
    }

    @Override
    public InputStream fetch(String fetchKey, Metadata metadata, ComponentConfigs componentConfigs)
            throws TikaException, IOException {

        LOGGER.debug("about to fetch fetchkey={} from endpoint ({})", fetchKey, config.getEndpoint());

        try {
            BlobClient blobClient = blobClientFactory.getClient(fetchKey);

            if (config.isExtractUserMetadata()) {
                BlobProperties properties = blobClient.getProperties();
                if (properties.getMetadata() != null) {
                    for (Map.Entry<String, String> e : properties.getMetadata().entrySet()) {
                        metadata.add(PREFIX + ":" + e.getKey(), e.getValue());
                    }
                }
            }
            if (!config.isSpoolToTemp()) {
                return TikaInputStream.get(blobClient.openInputStream());
            } else {
                long start = System.currentTimeMillis();
                TemporaryResources tmpResources = new TemporaryResources();
                Path tmp = tmpResources.createTempFile();
                blobClient.downloadToFile(tmp.toRealPath().toString(), true);
                TikaInputStream tis = TikaInputStream.get(tmp, metadata, tmpResources);
                long elapsed = System.currentTimeMillis() - start;
                LOGGER.debug("took {} ms to copy to local tmp file", elapsed);
                return tis;
            }
        } catch (Exception e) {
            throw new IOException("az-blob storage exception", e);
        }
    }

    private interface BlobClientFactory {
        BlobClient getClient(String fetchKey);
    }

    private static class SingleBlobContainerFactory implements BlobClientFactory {
        private final BlobContainerClient blobContainerClient;

        private SingleBlobContainerFactory(String endpoint, String sasToken, String container) {
            BlobServiceClient blobServiceClient = new BlobServiceClientBuilder()
                    .endpoint(endpoint)
                    .sasToken(sasToken)
                    .buildClient();
            blobContainerClient = blobServiceClient.getBlobContainerClient(container);
        }

        @Override
        public BlobClient getClient(String fetchKey) {
            return blobContainerClient.getBlobClient(fetchKey);
        }
    }

    private static class SASUrlFactory implements BlobClientFactory {

        @Override
        public BlobClient getClient(String fetchKey) {
            return new BlobClientBuilder()
                    .connectionString(fetchKey)
                    .buildClient();
        }
    }
}
