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
package org.apache.tika.pipes.pipesiterator.azblob;

import static org.apache.tika.config.TikaConfig.mustNotBeEmpty;

import java.io.IOException;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeoutException;

import com.azure.core.http.rest.PagedIterable;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.blob.models.BlobItem;
import com.azure.storage.blob.models.BlobListDetails;
import com.azure.storage.blob.models.ListBlobsOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.pipes.api.FetchEmitTuple;
import org.apache.tika.pipes.api.HandlerConfig;
import org.apache.tika.pipes.api.emitter.EmitKey;
import org.apache.tika.pipes.api.fetcher.FetchKey;
import org.apache.tika.pipes.api.pipesiterator.PipesIteratorBaseConfig;
import org.apache.tika.pipes.pipesiterator.PipesIteratorBase;
import org.apache.tika.plugins.ExtensionConfig;
import org.apache.tika.utils.StringUtils;

public class AZBlobPipesIterator extends PipesIteratorBase {

    private static final Logger LOGGER = LoggerFactory.getLogger(AZBlobPipesIterator.class);

    public static AZBlobPipesIterator build(ExtensionConfig extensionConfig) throws TikaConfigException, IOException {
        AZBlobPipesIterator iterator = new AZBlobPipesIterator(extensionConfig);
        iterator.configure();
        return iterator;
    }

    private AZBlobPipesIteratorConfig config;
    private BlobContainerClient blobContainerClient;

    private AZBlobPipesIterator(ExtensionConfig extensionConfig) {
        super(extensionConfig);
    }

    private void configure() throws IOException, TikaConfigException {
        config = AZBlobPipesIteratorConfig.load(pluginConfig.jsonConfig());
        checkConfig(config);

        //TODO -- allow authentication via other methods
        BlobServiceClient blobServiceClient = new BlobServiceClientBuilder()
                .endpoint(config.getEndpoint())
                .sasToken(config.getSasToken())
                .buildClient();
        blobContainerClient = blobServiceClient.getBlobContainerClient(config.getContainer());
    }

    private void checkConfig(AZBlobPipesIteratorConfig config) throws TikaConfigException {
        mustNotBeEmpty("sasToken", config.getSasToken());
        mustNotBeEmpty("endpoint", config.getEndpoint());
        mustNotBeEmpty("container", config.getContainer());
    }

    @Override
    protected void enqueue() throws InterruptedException, IOException, TimeoutException {
        PipesIteratorBaseConfig baseConfig = config.getBaseConfig();
        String fetcherId = baseConfig.fetcherId();
        String emitterId = baseConfig.emitterId();
        HandlerConfig handlerConfig = baseConfig.handlerConfig();

        long start = System.currentTimeMillis();
        int count = 0;

        String prefix = config.getPrefix();
        // Strip final "/" if it exists
        if (prefix != null && prefix.endsWith("/")) {
            prefix = prefix.substring(0, prefix.length() - 1);
        }

        PagedIterable<BlobItem> blobs;
        if (StringUtils.isBlank(prefix)) {
            ListBlobsOptions options = new ListBlobsOptions().setDetails(new BlobListDetails()
                    .setRetrieveDeletedBlobs(false)
                    .setRetrieveMetadata(false)
                    .setRetrieveSnapshots(false));
            blobs = blobContainerClient.listBlobs(options, Duration.of(config.getTimeoutMillis(), ChronoUnit.MILLIS));
        } else {
            ListBlobsOptions options = new ListBlobsOptions()
                    .setPrefix(prefix)
                    .setDetails(new BlobListDetails()
                            .setRetrieveDeletedBlobs(false)
                            .setRetrieveMetadata(false)
                            .setRetrieveSnapshots(false));
            blobs = blobContainerClient.listBlobs(options, Duration.of(config.getTimeoutMillis(), ChronoUnit.MILLIS));
        }

        for (BlobItem blob : blobs) {
            //tried blob.isPrefix() and got NPE ... user error?
            if (blob == null || blob.getProperties() == null || blob
                    .getProperties()
                    .getContentLength() == 0) {
                continue;
            }
            long elapsed = System.currentTimeMillis() - start;
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("adding ({}) {} in {} ms", count, blob.getName(), elapsed);
            }
            //TODO -- extract metadata about content length etc from properties
            ParseContext parseContext = new ParseContext();
            parseContext.set(HandlerConfig.class, handlerConfig);
            tryToAdd(new FetchEmitTuple(blob.getName(), new FetchKey(fetcherId, blob.getName()),
                    new EmitKey(emitterId, blob.getName()), new Metadata(), parseContext,
                    baseConfig.onParseException()));
            count++;
        }
        long elapsed = System.currentTimeMillis() - start;
        LOGGER.info("finished enqueuing {} files in {} ms", count, elapsed);
    }
}
