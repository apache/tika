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

import java.io.InputStream;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import javax.ws.rs.core.MultivaluedHashMap;

import org.apache.cxf.jaxrs.impl.UriInfoImpl;
import org.apache.cxf.message.MessageImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.pipes.emitter.EmitData;
import org.apache.tika.pipes.emitter.EmitKey;
import org.apache.tika.pipes.fetchiterator.FetchEmitTuple;
import org.apache.tika.utils.StringUtils;

/**
 * Worker thread that takes {@link FetchEmitTuple} off the queue, parses
 * the file and puts the {@link EmitData} on the emitDataQueue for the {@link AsyncEmitter}.
 */
public class AsyncParser implements Callable<Integer> {

    private static final Logger LOG = LoggerFactory.getLogger(AsyncParser.class);

    private final ArrayBlockingQueue<FetchEmitTuple> fetchEmitTupleQueue;
    private final ArrayBlockingQueue<EmitData> emitDataQueue;

    public AsyncParser(ArrayBlockingQueue<FetchEmitTuple> queue,
                       ArrayBlockingQueue<EmitData> emitData) {
        this.fetchEmitTupleQueue = queue;
        this.emitDataQueue = emitData;
    }

    @Override
    public Integer call() throws Exception {
        while (true) {
            FetchEmitTuple request = fetchEmitTupleQueue.poll(1, TimeUnit.MINUTES);
            if (request != null) {
                EmitData emitData = processTuple(request);
                boolean shouldEmit = checkForParseException(request, emitData);
                if (shouldEmit) {
                    boolean offered = emitDataQueue.offer(emitData, 10, TimeUnit.MINUTES);
                    if (!offered) {
                        //TODO: deal with this
                        LOG.warn("Failed to add ({}) " + "to emit queue after 10 minutes.",
                                request.getFetchKey().getFetchKey());
                    }
                }
            } else {
                LOG.trace("Nothing on the async queue");
            }
        }
    }

    private boolean checkForParseException(FetchEmitTuple request, EmitData emitData) {
        if (emitData == null || emitData.getMetadataList() == null ||
                emitData.getMetadataList().size() == 0) {
            LOG.warn("empty or null emit data ({})", request.getFetchKey().getFetchKey());
            return false;
        }
        boolean shouldEmit = true;
        Metadata container = emitData.getMetadataList().get(0);
        String stack = container.get(TikaCoreProperties.CONTAINER_EXCEPTION);
        if (stack != null) {
            LOG.warn("fetchKey ({}) container parse exception ({})",
                    request.getFetchKey().getFetchKey(),
                    stack);
            if (request.getOnParseException() == FetchEmitTuple.ON_PARSE_EXCEPTION.SKIP) {
                shouldEmit = false;
            }
        }

        for (int i = 1; i < emitData.getMetadataList().size(); i++) {
            Metadata m = emitData.getMetadataList().get(i);
            String embeddedStack = m.get(TikaCoreProperties.EMBEDDED_EXCEPTION);
            if (embeddedStack != null) {
                LOG.warn("fetchKey ({}) embedded parse exception ({})",
                        request.getFetchKey().getFetchKey(), embeddedStack);
            }
        }
        return shouldEmit;
    }

    private EmitData processTuple(FetchEmitTuple t) {
        Metadata userMetadata = t.getMetadata();
        Metadata metadata = new Metadata();
        String fetcherName = t.getFetchKey().getFetcherName();
        String fetchKey = t.getFetchKey().getFetchKey();
        List<Metadata> metadataList = null;
        try (InputStream stream = TikaResource.getConfig().getFetcherManager()
                .getFetcher(fetcherName).fetch(fetchKey, metadata)) {
            metadataList = RecursiveMetadataResource
                    .parseMetadata(stream, metadata, new MultivaluedHashMap<>(),
                            new UriInfoImpl(new MessageImpl()), "text");
        } catch (SecurityException e) {
            throw e;
        } catch (Exception e) {
            LOG.warn(t.toString(), e);
        }

        injectUserMetadata(userMetadata, metadataList);
        EmitKey emitKey = t.getEmitKey();
        if (StringUtils.isBlank(emitKey.getEmitKey())) {
            emitKey = new EmitKey(emitKey.getEmitterName(), fetchKey);
        }
        return new EmitData(emitKey, metadataList);
    }

    private void injectUserMetadata(Metadata userMetadata, List<Metadata> metadataList) {
        for (String n : userMetadata.names()) {
            //overwrite whatever was there
            metadataList.get(0).set(n, null);
            for (String val : userMetadata.getValues(n)) {
                metadataList.get(0).add(n, val);
            }
        }
    }
}
