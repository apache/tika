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

import org.apache.cxf.jaxrs.impl.UriInfoImpl;
import org.apache.cxf.message.MessageImpl;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.pipes.emitter.EmitData;
import org.apache.tika.pipes.emitter.EmitKey;
import org.apache.tika.pipes.fetchiterator.FetchEmitTuple;
import org.apache.tika.utils.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.core.MultivaluedHashMap;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

/**
 * Worker thread that takes {@link FetchEmitTuple} off the queue, parses
 * the file and puts the {@link EmitData} on the queue for the {@link AsyncEmitter}.
 *
 */
public class AsyncParser implements Callable<Integer> {

    private static final Logger LOG = LoggerFactory.getLogger(AsyncParser.class);

    private final ArrayBlockingQueue<FetchEmitTuple> queue;
    private final ArrayBlockingQueue<EmitData> emitDataQueue;

    public AsyncParser(ArrayBlockingQueue<FetchEmitTuple> queue,
                       ArrayBlockingQueue<EmitData> emitData) {
        this.queue = queue;
        this.emitDataQueue = emitData;
    }

    @Override
    public Integer call() throws Exception {
        int parsed = 0;
        while (true) {
            FetchEmitTuple request = queue.poll(1, TimeUnit.MINUTES);
            if (request != null) {
                EmitData emitData = processTuple(request);
                boolean offered = emitDataQueue.offer(emitData, 10, TimeUnit.MINUTES);
                parsed++;
                if (! offered) {
                    //TODO: deal with this
                }
            } else {
                LOG.trace("Nothing on the async queue");
            }
        }
    }

    private EmitData processTuple(FetchEmitTuple t) {
        Metadata userMetadata = t.getMetadata();
        Metadata metadata = new Metadata();
        String fetcherName = t.getFetchKey().getFetcherName();
        String fetchKey = t.getFetchKey().getKey();
        List<Metadata> metadataList = null;
        try (InputStream stream =
                     TikaResource.getConfig().getFetcherManager()
                             .getFetcher(fetcherName).fetch(fetchKey, metadata)) {
            metadataList = RecursiveMetadataResource.parseMetadata(
                    stream,
                    metadata,
                    new MultivaluedHashMap<>(),
                    new UriInfoImpl(new MessageImpl()), "text");
        } catch (SecurityException e) {
            throw e;
        } catch (Exception e) {
            LOG.warn(t.toString(), e);
        }
        injectUserMetadata(userMetadata, metadataList);
        EmitKey emitKey = t.getEmitKey();
        if (StringUtils.isBlank(emitKey.getKey())) {
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
