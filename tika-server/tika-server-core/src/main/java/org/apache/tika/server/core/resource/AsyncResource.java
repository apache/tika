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

package org.apache.tika.server.core.resource;

import org.apache.tika.config.TikaConfig;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.pipes.emitter.EmitKey;
import org.apache.tika.pipes.emitter.EmitterManager;
import org.apache.tika.pipes.fetcher.FetchKey;
import org.apache.tika.pipes.fetcher.FetcherManager;
import org.apache.tika.pipes.fetchiterator.FetchEmitTuple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;

import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.UriInfo;
import java.io.InputStream;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

@Path("/async")
public class AsyncResource {

    private static final Logger LOG = LoggerFactory.getLogger(AsyncResource.class);

    private static final int DEFAULT_QUEUE_SIZE = 100;
    private int queueSize = DEFAULT_QUEUE_SIZE;
    private ArrayBlockingQueue<AsyncRequest> queue;

    public ArrayBlockingQueue<AsyncRequest> getQueue(int numThreads) {
        this.queue = new ArrayBlockingQueue<>(queueSize+numThreads);
        return queue;
    }
    /**
     * The client posts a json request.  At a minimum, this must be a
     * json object that contains an emitter and a fetcherString key with
     * the key to fetch the inputStream. Optionally, it may contain a metadata
     * object that will be used to populate the metadata key for pass
     * through of metadata from the client.
     * <p>
     * The extracted text content is stored with the key
     * {@link TikaCoreProperties#TIKA_CONTENT}
     * <p>
     * Must specify a fetcherString and an emitter in the posted json.
     *
     * @param info uri info
     * @return InputStream that can be deserialized as a list of {@link Metadata} objects
     * @throws Exception
     */
    @POST
    @Produces("application/json")
    public Map<String, String> post(InputStream is,
                                         @Context HttpHeaders httpHeaders,
                                         @Context UriInfo info
    ) throws Exception {

        AsyncRequest request = deserializeASyncRequest(is);
        FetcherManager fetcherManager = TikaConfig.getDefaultConfig().getFetcherManager();
        EmitterManager emitterManager = TikaConfig.getDefaultConfig().getEmitterManager();
        for (FetchEmitTuple t : request.getTuples()) {
            if (! fetcherManager.getSupported().contains(t.getFetchKey().getFetcherName())) {
                return badFetcher(t.getFetchKey());
            }
            if (! emitterManager.getSupported().contains(t.getEmitKey().getEmitterName())) {
                return badEmitter(t.getEmitKey());
            }
        }

        //parameterize
        boolean offered = queue.offer(request, 60, TimeUnit.SECONDS);
        if (! offered) {
            return throttleResponse();
        }
        return ok(request.getId(), request.getTuples().size());
    }

    private Map<String, String> ok(String id, int size) {
        return null;
    }

    private Map<String, String> throttleResponse() {
        Map<String, String> map = new HashMap<>();
        return map;
    }

    private Map<String, String> badEmitter(EmitKey emitKey) {
        return null;
    }

    private Map<String, String> badFetcher(FetchKey fetchKey) {
        return null;
    }

    private AsyncRequest deserializeASyncRequest(InputStream is) {
        return new AsyncRequest("", Collections.EMPTY_LIST);
    }

}
