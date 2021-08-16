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
package org.apache.tika.pipes.pipesiterator.gcs;

import static org.apache.tika.config.TikaConfig.mustNotBeEmpty;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import com.google.api.gax.paging.Page;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
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
import org.apache.tika.utils.StringUtils;

public class GCSPipesIterator extends PipesIterator implements Initializable {

    private static final Logger LOGGER = LoggerFactory.getLogger(GCSPipesIterator.class);
    private String prefix = "";
    private String projectId = "";
    private String bucket;

    private Storage storage;

    @Field
    public void setBucket(String bucket) {
        this.bucket = bucket;
    }

    @Field
    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    @Field
    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    /**
     * This initializes the gcs client.
     *
     * @param params params to use for initialization
     * @throws TikaConfigException
     */
    @Override
    public void initialize(Map<String, Param> params) throws TikaConfigException {
        //TODO -- add other params to the builder as needed
        storage = StorageOptions.newBuilder().setProjectId(projectId).build().getService();
    }

    @Override
    public void checkInitialization(InitializableProblemHandler problemHandler)
            throws TikaConfigException {
        super.checkInitialization(problemHandler);
        mustNotBeEmpty("bucket", this.bucket);
        mustNotBeEmpty("projectId", this.projectId);
    }

    @Override
    protected void enqueue() throws InterruptedException, IOException, TimeoutException {
        String fetcherName = getFetcherName();
        String emitterName = getEmitterName();
        long start = System.currentTimeMillis();
        int count = 0;
        HandlerConfig handlerConfig = getHandlerConfig();

        Page<Blob> blobs = null;
        if (StringUtils.isBlank(prefix)) {
            blobs = storage.list(bucket);
        } else {
            blobs = storage.list(bucket,
                    Storage.BlobListOption.prefix(prefix));
        }

        for (Blob blob : blobs.iterateAll()) {
            //I couldn't find a better way to skip directories
            //calling blob.isDirectory() does not appear to work.  #usererror I'm sure.
            if (blob.getSize() == 0) {
                continue;
            }
            long elapsed = System.currentTimeMillis() - start;
            LOGGER.debug("adding ({}) {} in {} ms", count, blob.getName(), elapsed);
            //TODO -- allow user specified metadata as the "id"?
            tryToAdd(new FetchEmitTuple(blob.getName(), new FetchKey(fetcherName,
                    blob.getName()),
                    new EmitKey(emitterName, blob.getName()), new Metadata(), handlerConfig,
                    getOnParseException()));
            count++;
        }
        long elapsed = System.currentTimeMillis() - start;
        LOGGER.info("finished enqueuing {} files in {} ms", count, elapsed);
    }
}
