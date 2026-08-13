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
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

import org.apache.tika.config.loader.TikaJsonConfig;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.pipes.api.FetchEmitTuple;
import org.apache.tika.pipes.api.fetcher.FetchKey;
import org.apache.tika.pipes.core.async.AsyncProcessor;
import org.apache.tika.pipes.core.async.OfferLargerThanQueueSize;
import org.apache.tika.pipes.core.emitter.EmitDataImpl;
import org.apache.tika.pipes.core.emitter.EmitterManager;
import org.apache.tika.pipes.core.extractor.UnpackConfig;
import org.apache.tika.pipes.core.fetcher.FetcherManager;
import org.apache.tika.pipes.core.serialization.JsonFetchEmitTupleList;
import org.apache.tika.plugins.TikaPluginManager;
import org.apache.tika.serialization.ParseContextUtils;

@Path("/async")
public class AsyncResource {

    private static final Logger LOG = LoggerFactory.getLogger(AsyncResource.class);
    private final AsyncProcessor asyncProcessor;
    private final EmitterManager emitterManager;
    private final FetcherManager fetcherManager;
    long maxQueuePauseMillis = 60000;
    private ArrayBlockingQueue<FetchEmitTuple> queue;

    public AsyncResource(java.nio.file.Path tikaConfigPath) throws TikaException, IOException, SAXException {
        this.asyncProcessor = AsyncProcessor.load(tikaConfigPath);
        // One bad tuple must not halt the shared workers and brick /async until restart.
        this.asyncProcessor.setStopOnlyOnFatal(true);
        TikaJsonConfig tikaJsonConfig = TikaJsonConfig.load(tikaConfigPath);
        TikaPluginManager pluginManager = TikaPluginManager.load(tikaJsonConfig);
        this.emitterManager = EmitterManager.load(pluginManager, tikaJsonConfig);
        this.fetcherManager = FetcherManager.load(pluginManager, tikaJsonConfig);
    }

    /** How long a full /async queue blocks a POST before returning 429; see TikaServerConfig. */
    public void setMaxQueuePauseMillis(long maxQueuePauseMillis) {
        this.maxQueuePauseMillis = maxQueuePauseMillis;
    }

    public ArrayBlockingQueue<FetchEmitTuple> getFetchEmitQueue(int queueSize) {
        this.queue = new ArrayBlockingQueue<>(queueSize);
        return queue;
    }

    public ArrayBlockingQueue<EmitDataImpl> getEmitDataQueue(int size) {
        return new ArrayBlockingQueue<>(size);
    }

    /**
     * The client posts a JSON object {@code {"tuples":[...]}}. Each tuple names
     * a fetcher and fetch key for input and an emitter for output; it may also
     * carry a metadata object (passed through to the emitted metadata) and a
     * parse context.
     * <p>
     * The extracted text content is stored with the key
     * {@link TikaCoreProperties#TIKA_CONTENT}
     *
     * @param info uri info
     * @return JSON body reporting acceptance ({@code {"status":"ok","added":n}});
     *         the parses themselves run asynchronously
     * @throws Exception
     */
    @POST
    @Produces("application/json")
    public Response post(InputStream is, @Context HttpHeaders httpHeaders, @Context UriInfo info) throws Exception {

        AsyncRequest request = deserializeASyncRequest(is);

        //make sure that there are no problems with
        //the requested fetchers and emitters
        //throw early
        for (FetchEmitTuple t : request.getTuples()) {
            PipesParsingHelper.rejectReservedComponentIds(t);
            if (!fetcherManager
                    .getSupported()
                    .contains(t
                            .getFetchKey()
                            .getFetcherId())) {
                return badFetcher(t.getFetchKey());
            }
            if (!emitterManager
                    .getSupported()
                    .contains(t
                            .getEmitKey()
                            .getEmitterId())) {
                return badEmitter(t
                        .getEmitKey()
                        .getEmitterId());
            }
            ParseContext parseContext = t.getParseContext();
            // Configs are lazy; resolve before reading UnpackConfig for the bytes-emitter check.
            try {
                ParseContextUtils.resolveAll(parseContext, getClass().getClassLoader());
            } catch (TikaConfigException e) {
                throw new BadRequestException(
                        "Could not resolve the parseContext in the /async request body: " + e.getMessage(), e);
            }
            UnpackConfig unpackConfig = parseContext.get(UnpackConfig.class);
            if (unpackConfig != null && !StringUtils.isAllBlank(unpackConfig.getEmitter())) {
                String bytesEmitter = unpackConfig.getEmitter();
                if (!emitterManager
                        .getSupported()
                        .contains(bytesEmitter)) {
                    return badEmitter(bytesEmitter);
                }
            }
        }
        //Instant start = Instant.now();
        try {
            boolean offered = asyncProcessor.offer(request.getTuples(), maxQueuePauseMillis);
            if (offered) {
                LOG.debug("accepted {} tuples, capacity={}", request
                        .getTuples()
                        .size(), asyncProcessor.getCapacity());
                return ok(request
                        .getTuples()
                        .size());
            } else {
                LOG.debug("throttling {} tuples, capacity={}", request
                        .getTuples()
                        .size(), asyncProcessor.getCapacity());
                return throttle(request
                        .getTuples()
                        .size());
            }
        } catch (OfferLargerThanQueueSize e) {
            // Bigger than the queue can EVER hold -- retrying is futile, so 400, not 429.
            throw new BadRequestException("Batch of " + e.getSizeOffered() +
                    " tuples exceeds the queue capacity (" + e.getQueueSize() +
                    "); split the batch", e);
        }
    }

    private Response ok(int size) {
        Map<String, Object> map = new HashMap<>();
        map.put("status", "ok");
        map.put("added", size);
        return Response.ok(map).build();
    }

    /**
     * 429, not 200. The queue was full for the whole {@code maxQueuePauseMillis} wait, so nothing
     * was accepted -- a 200 made a rejected batch indistinguishable from an accepted one to
     * any client that checks status rather than parsing the body, and there are such clients.
     */
    private Response throttle(int requestSize) {
        Map<String, Object> map = new HashMap<>();
        map.put("status", "throttled");
        map.put("message", "not able to receive request of size " + requestSize + " at this time");
        map.put("capacity", asyncProcessor.getCapacity());
        return Response
                .status(Response.Status.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER,
                        Math.max(1, TimeUnit.MILLISECONDS.toSeconds(maxQueuePauseMillis)))
                .entity(map)
                .build();
    }

    private Response badEmitter(String emitterName) {
        throw new BadRequestException("can't find emitter for " + emitterName);
    }

    private Response badFetcher(FetchKey fetchKey) {
        throw new BadRequestException("can't find fetcher for " + fetchKey.getFetcherId());
    }

    private AsyncRequest deserializeASyncRequest(InputStream is) throws IOException {
        try (Reader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
            return new AsyncRequest(JsonFetchEmitTupleList.fromJson(reader));
        } catch (IOException e) {
            // A malformed body is the caller's error, not a server fault -- 400 with the reason.
            throw new BadRequestException("Could not parse the /async request body: " + e.getMessage(), e);
        }
    }

    public void shutdownNow() throws Exception {
        asyncProcessor.close();
    }

}
