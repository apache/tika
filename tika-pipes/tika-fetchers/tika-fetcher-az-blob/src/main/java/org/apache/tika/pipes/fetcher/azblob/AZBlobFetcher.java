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
import java.io.OutputStream;
import java.nio.file.Files;
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

import org.apache.tika.config.Field;
import org.apache.tika.config.Initializable;
import org.apache.tika.config.InitializableProblemHandler;
import org.apache.tika.config.Param;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.pipes.fetcher.AbstractFetcher;
import org.apache.tika.utils.StringUtils;

/**
 * Fetches files from Azure blob storage.
 *
 * There are two modes:
 * 1) If you are only using one endpoint and one sas token and one container,
 *    configure those in the config file.  In this case, your fetchKey will
 *    be the path in the container to the blob.
 * 2) If you have different endpoints or sas tokens or containers across
 *    your requests, your fetchKey will be the complete SAS url pointing to the blob.
 */
public class AZBlobFetcher extends AbstractFetcher implements Initializable {

    private static String PREFIX = "az-blob";
    private static final Logger LOGGER = LoggerFactory.getLogger(AZBlobFetcher.class);
    private String sasToken;
    private String container;
    private String endpoint;
    private BlobClientFactory blobClientFactory;
    private boolean extractUserMetadata = true;
    private BlobServiceClient blobServiceClient;
    private BlobContainerClient blobContainerClient;
    private boolean spoolToTemp = true;

    @Override
    public InputStream fetch(String fetchKey, Metadata fetchRequestMetadata, Metadata fetchResponseMetadata) throws TikaException, IOException {

        LOGGER.debug("about to fetch fetchkey={} from endpoint ({})", fetchKey, endpoint);

        try {
            BlobClient blobClient = blobClientFactory.getClient(fetchKey);

            if (extractUserMetadata) {
                BlobProperties properties = blobClient.getProperties();
                if (properties.getMetadata() != null) {
                    for (Map.Entry<String, String> e : properties.getMetadata().entrySet()) {
                        fetchRequestMetadata.add(PREFIX + ":" + e.getKey(), e.getValue());
                    }
                }
            }
            if (!spoolToTemp) {
                return TikaInputStream.get(blobClient.openInputStream());
            } else {
                long start = System.currentTimeMillis();
                TemporaryResources tmpResources = new TemporaryResources();
                Path tmp = tmpResources.createTempFile();
                try (OutputStream os = Files.newOutputStream(tmp)) {
                    blobClient.download(os);
                }
                TikaInputStream tis = TikaInputStream.get(tmp, fetchRequestMetadata, tmpResources);
                long elapsed = System.currentTimeMillis() - start;
                LOGGER.debug("took {} ms to copy to local tmp file", elapsed);
                return tis;
            }
        } catch (Exception e) {
            throw new IOException("az-blob storage exception", e);
        }
    }

    @Field
    public void setSpoolToTemp(boolean spoolToTemp) {
        this.spoolToTemp = spoolToTemp;
    }

    @Field
    public void setSasToken(String sasToken) {
        this.sasToken = sasToken;
    }

    @Field
    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    @Field
    public void setContainer(String container) {
        this.container = container;
    }
    /**
     * Whether or not to extract user metadata from the blob object
     *
     * @param extractUserMetadata
     */
    @Field
    public void setExtractUserMetadata(boolean extractUserMetadata) {
        this.extractUserMetadata = extractUserMetadata;
    }


    /**
     * This initializes the az blob container client
     *
     * @param params params to use for initialization
     * @throws TikaConfigException
     */
    @Override
    public void initialize(Map<String, Param> params) throws TikaConfigException {
        if (!StringUtils.isBlank(sasToken)) {
            LOGGER.debug("Setting up immutable endpoint, token and container");
            blobClientFactory = new SingleBlobContainerFactory(endpoint, sasToken, container);
        } else {
            LOGGER.debug("Setting up blobclientfactory to recieve the full sas url for the blob");
            blobClientFactory = new SASUrlFactory();
        }
    }

    @Override
    public void checkInitialization(InitializableProblemHandler problemHandler)
            throws TikaConfigException {
        //if the user has set one of these, they need to have set all of them
        if (!StringUtils.isBlank(this.sasToken) ||
                !StringUtils.isBlank(this.endpoint) || !StringUtils.isBlank(this.container)) {
            mustNotBeEmpty("sasToken", this.sasToken);
            mustNotBeEmpty("endpoint", this.endpoint);
            mustNotBeEmpty("container", this.container);
        }
    }

    private interface BlobClientFactory {
        BlobClient getClient(String fetchKey);
    }

    private static class SingleBlobContainerFactory implements BlobClientFactory {
        private final BlobContainerClient blobContainerClient;

        private SingleBlobContainerFactory(String endpoint, String sasToken, String container) {
            //TODO -- allow authentication via other methods
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
            return new BlobClientBuilder().connectionString(fetchKey).buildClient();
        }
    }
}
