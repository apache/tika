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
package org.apache.tika.pipes.iterator.gcs;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

import com.google.api.gax.paging.Page;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
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

public class GCSPipesIterator extends PipesIteratorBase {

    private static final Logger LOGGER = LoggerFactory.getLogger(GCSPipesIterator.class);

    private final GCSPipesIteratorConfig config;
    private final Storage storage;

    private GCSPipesIterator(GCSPipesIteratorConfig config, ExtensionConfig extensionConfig) throws TikaConfigException {
        super(extensionConfig);
        this.config = config;

        if (StringUtils.isBlank(config.getBucket())) {
            throw new TikaConfigException("bucket must not be empty");
        }
        if (StringUtils.isBlank(config.getProjectId())) {
            throw new TikaConfigException("projectId must not be empty");
        }

        // Initialize the GCS client
        this.storage = StorageOptions
                .newBuilder()
                .setProjectId(config.getProjectId())
                .build()
                .getService();
    }

    public static GCSPipesIterator build(ExtensionConfig extensionConfig) throws IOException, TikaConfigException {
        GCSPipesIteratorConfig config = GCSPipesIteratorConfig.load(extensionConfig.json());
        return new GCSPipesIterator(config, extensionConfig);
    }

    @Override
    protected void enqueue() throws InterruptedException, IOException, TimeoutException {
        PipesIteratorBaseConfig baseConfig = config.getBaseConfig();
        String fetcherPluginId = baseConfig.fetcherId();
        String emitterName = baseConfig.emitterId();
        long start = System.currentTimeMillis();
        int count = 0;
        HandlerConfig handlerConfig = baseConfig.handlerConfig();

        Page<Blob> blobs = null;
        String prefix = config.getPrefix();
        if (StringUtils.isBlank(prefix)) {
            blobs = storage.list(config.getBucket());
        } else {
            blobs = storage.list(config.getBucket(), Storage.BlobListOption.prefix(prefix));
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
            ParseContext parseContext = new ParseContext();
            parseContext.set(HandlerConfig.class, handlerConfig);
            tryToAdd(new FetchEmitTuple(blob.getName(), new FetchKey(fetcherPluginId, blob.getName()), new EmitKey(emitterName, blob.getName()), new Metadata(), parseContext,
                    baseConfig.onParseException()));
            count++;
        }
        long elapsed = System.currentTimeMillis() - start;
        LOGGER.info("finished enqueuing {} files in {} ms", count, elapsed);
    }
}
