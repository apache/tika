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
import java.util.Map;
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

import org.apache.tika.config.Field;
import org.apache.tika.config.Initializable;
import org.apache.tika.config.InitializableProblemHandler;
import org.apache.tika.config.Param;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.pipes.FetchEmitTuple;
import org.apache.tika.pipes.HandlerConfig;
import org.apache.tika.pipes.emitter.EmitKey;
import org.apache.tika.pipes.fetcher.FetchKey;
import org.apache.tika.pipes.pipesiterator.PipesIterator;
import org.apache.tika.utils.StringUtils;

public class AZBlobPipesIterator extends PipesIterator implements Initializable {

    private static final Logger LOGGER = LoggerFactory.getLogger(AZBlobPipesIterator.class);

    private BlobServiceClient blobServiceClient;
    private BlobContainerClient blobContainerClient;
    private String prefix = "";
    private String container = "";
    private String sasToken;
    private String endpoint;
    private long timeoutMillis = 360000;

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

    @Field
    public void setPrefix(String prefix) {
        //strip final "/" if it exists
        if (prefix.endsWith("/")) {
            this.prefix = prefix.substring(0, prefix.length() - 1);
        } else {
            this.prefix = prefix;
        }
    }

    @Override
    protected void enqueue() throws InterruptedException, IOException, TimeoutException {
        String fetcherName = getFetcherName();
        String emitterName = getEmitterName();
        long start = System.currentTimeMillis();
        int count = 0;
        HandlerConfig handlerConfig = getHandlerConfig();

        PagedIterable<BlobItem> blobs = null;
        if (StringUtils.isBlank(prefix)) {
            ListBlobsOptions options = new ListBlobsOptions().setDetails(new BlobListDetails()
                    .setRetrieveDeletedBlobs(false)
                    .setRetrieveMetadata(false)
                    .setRetrieveSnapshots(false));
            blobs = blobContainerClient.listBlobs(options, Duration.of(timeoutMillis, ChronoUnit.MILLIS));
        } else {
            ListBlobsOptions options = new ListBlobsOptions()
                    .setPrefix(prefix)
                    .setDetails(new BlobListDetails()
                            .setRetrieveDeletedBlobs(false)
                            .setRetrieveMetadata(false)
                            .setRetrieveSnapshots(false));
            blobs = blobContainerClient.listBlobs(options, Duration.of(timeoutMillis, ChronoUnit.MILLIS));
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
            tryToAdd(new FetchEmitTuple(blob.getName(), new FetchKey(fetcherName, blob.getName()), new EmitKey(emitterName, blob.getName()), new Metadata(), parseContext,
                    getOnParseException()));
            count++;
        }
        long elapsed = System.currentTimeMillis() - start;
        LOGGER.info("finished enqueuing {} files in {} ms", count, elapsed);
    }

    @Override
    public void initialize(Map<String, Param> params) throws TikaConfigException {
        //TODO -- allow authentication via other methods
        blobServiceClient = new BlobServiceClientBuilder()
                .endpoint(endpoint)
                .sasToken(sasToken)
                .buildClient();
        blobContainerClient = blobServiceClient.getBlobContainerClient(container);
    }

    @Override
    public void checkInitialization(InitializableProblemHandler problemHandler) throws TikaConfigException {
        mustNotBeEmpty("sasToken", this.sasToken);
        mustNotBeEmpty("endpoint", this.endpoint);
        mustNotBeEmpty("container", this.container);
    }
}
