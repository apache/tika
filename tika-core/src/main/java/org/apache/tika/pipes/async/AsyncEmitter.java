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
package org.apache.tika.pipes.async;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.pipes.emitter.AbstractEmitter;
import org.apache.tika.pipes.emitter.EmitData;
import org.apache.tika.pipes.emitter.Emitter;
import org.apache.tika.pipes.emitter.EmitterManager;
import org.apache.tika.pipes.emitter.TikaEmitterException;
import org.apache.tika.utils.ExceptionUtils;

/**
 * Worker thread that takes EmitData off the queue, batches it
 * and tries to emit it as a batch
 */
public class AsyncEmitter implements Callable<Integer> {

    static final EmitData EMIT_DATA_STOP_SEMAPHORE = new EmitData(null, null);
    static final int EMITTER_FUTURE_CODE = 2;

    private static final Logger LOG = LoggerFactory.getLogger(AsyncEmitter.class);

    //TODO -- need to configure these
    private final long emitWithinMs = 1000;

    private long maxEstimatedBytes = 10_000_000;

    private final EmitterManager emitterManager;
    private final ArrayBlockingQueue<EmitData> emitDataQueue;

    Instant lastEmitted = Instant.now();

    public AsyncEmitter(ArrayBlockingQueue<EmitData> emitData, EmitterManager emitterManager) {
        this.emitDataQueue = emitData;
        this.emitterManager = emitterManager;
    }

    @Override
    public Integer call() throws Exception {
        EmitDataCache cache = new EmitDataCache(maxEstimatedBytes);

        while (true) {
            EmitData emitData = emitDataQueue.poll(100, TimeUnit.MILLISECONDS);
            if (emitData == EMIT_DATA_STOP_SEMAPHORE) {
                cache.emitAll();
                return EMITTER_FUTURE_CODE;
            }
            if (emitData != null) {
                //this can block on emitAll
                cache.add(emitData);
            } else {
                LOG.trace("Nothing on the async queue");
            }
            LOG.debug("cache size: ({}) bytes and count: {}", cache.estimatedSize, cache.size);
            long elapsed = ChronoUnit.MILLIS.between(lastEmitted, Instant.now());
            if (elapsed > emitWithinMs) {
                LOG.debug("{} elapsed > {}, going to emitAll", elapsed, emitWithinMs);
                //this can block
                cache.emitAll();
            }
        }
    }

    private class EmitDataCache {
        private final long maxBytes;

        long estimatedSize = 0;
        int size = 0;
        Map<String, List<EmitData>> map = new HashMap<>();

        public EmitDataCache(long maxBytes) {
            this.maxBytes = maxBytes;
        }

        void updateEstimatedSize(long newBytes) {
            estimatedSize += newBytes;
        }

        void add(EmitData data) {
            size++;
            long sz = AbstractEmitter
                    .estimateSizeInBytes(data.getEmitKey().getEmitKey(), data.getMetadataList());
            if (estimatedSize + sz > maxBytes) {
                LOG.debug("estimated size ({}) > maxBytes({}), going to emitAll",
                        (estimatedSize + sz), maxBytes);
                emitAll();
            }
            List<EmitData> cached = map.get(data.getEmitKey().getEmitterName());
            if (cached == null) {
                cached = new ArrayList<>();
                map.put(data.getEmitKey().getEmitterName(), cached);
            }
            updateEstimatedSize(sz);
            cached.add(data);
        }

        private void emitAll() {
            int emitted = 0;
            LOG.debug("about to emit {}", size);
            for (Map.Entry<String, List<EmitData>> e : map.entrySet()) {
                Emitter emitter = emitterManager.getEmitter(e.getKey());
                tryToEmit(emitter, e.getValue());
                emitted += e.getValue().size();
            }

            LOG.debug("emitted: {}", emitted);
            estimatedSize = 0;
            size = 0;
            map.clear();
            lastEmitted = Instant.now();
        }

        private void tryToEmit(Emitter emitter, List<EmitData> cachedEmitData) {

            try {
                emitter.emit(cachedEmitData);
            } catch (IOException | TikaEmitterException e) {
                LOG.warn("emitter class ({}): {}", emitter.getClass(),
                        ExceptionUtils.getStackTrace(e));
            }
        }
    }
}
