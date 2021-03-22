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

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.UriInfo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.metadata.serialization.JsonFetchEmitTupleList;
import org.apache.tika.pipes.emitter.EmitData;
import org.apache.tika.pipes.emitter.EmitKey;
import org.apache.tika.pipes.emitter.EmitterManager;
import org.apache.tika.pipes.fetcher.FetchKey;
import org.apache.tika.pipes.fetcher.FetcherManager;
import org.apache.tika.pipes.fetchiterator.FetchEmitTuple;

@Path("/async")
public class AsyncResource {

    private static final Logger LOG = LoggerFactory.getLogger(AsyncResource.class);

    private static final int DEFAULT_FETCH_EMIT_QUEUE_SIZE = 10000;
    long maxQueuePauseMs = 60000;
    private ArrayBlockingQueue<FetchEmitTuple> queue;

    public ArrayBlockingQueue<FetchEmitTuple> getFetchEmitQueue(int queueSize) {
        this.queue = new ArrayBlockingQueue<>(queueSize);
        return queue;
    }

    public ArrayBlockingQueue<EmitData> getEmitDataQueue(int size) {
        return new ArrayBlockingQueue<>(size);
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
    public Map<String, Object> post(InputStream is, @Context HttpHeaders httpHeaders,
                                    @Context UriInfo info) throws Exception {

        AsyncRequest request = deserializeASyncRequest(is);

        //make sure that there are no problems with
        //the requested fetchers and emitters
        //throw early
        FetcherManager fetcherManager = TikaResource.getConfig().getFetcherManager();
        EmitterManager emitterManager = TikaResource.getConfig().getEmitterManager();
        for (FetchEmitTuple t : request.getTuples()) {
            if (!fetcherManager.getSupported().contains(t.getFetchKey().getFetcherName())) {
                return badFetcher(t.getFetchKey());
            }
            if (!emitterManager.getSupported().contains(t.getEmitKey().getEmitterName())) {
                return badEmitter(t.getEmitKey());
            }
        }
        Instant start = Instant.now();
        long elapsed = ChronoUnit.MILLIS.between(start, Instant.now());
        List<FetchEmitTuple> notAdded = new ArrayList<>();
        int addedCount = 0;
        for (FetchEmitTuple t : request.getTuples()) {
            boolean offered = false;
            while (!offered && elapsed < maxQueuePauseMs) {
                offered = queue.offer(t, 10, TimeUnit.MILLISECONDS);
                elapsed = ChronoUnit.MILLIS.between(start, Instant.now());
            }
            if (!offered) {
                notAdded.add(t);
            } else {
                addedCount++;
            }
        }

        if (notAdded.size() > 0) {
            return throttle(notAdded, addedCount);
        }
        return ok(request.getTuples().size());
    }

    private Map<String, Object> ok(int size) {
        Map<String, Object> map = new HashMap<>();
        map.put("status", "ok");
        map.put("added", size);
        return map;
    }

    private Map<String, Object> throttle(List<FetchEmitTuple> notAdded, int added) {
        List<String> fetchKeys = new ArrayList<>();
        for (FetchEmitTuple t : notAdded) {
            fetchKeys.add(t.getFetchKey().getKey());
        }
        Map<String, Object> map = new HashMap<>();
        map.put("status", "throttled");
        map.put("added", added);
        map.put("skipped", notAdded.size());
        map.put("skippedFetchKeys", fetchKeys);
        return map;
    }

    private Map<String, Object> badEmitter(EmitKey emitKey) {
        throw new BadRequestException("can't find emitter for " + emitKey.getEmitterName());
    }

    private Map<String, Object> badFetcher(FetchKey fetchKey) {
        throw new BadRequestException("can't find fetcher for " + fetchKey.getFetcherName());
    }

    private AsyncRequest deserializeASyncRequest(InputStream is) throws IOException {
        try (Reader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
            return new AsyncRequest(JsonFetchEmitTupleList.fromJson(reader));
        }
    }

}
