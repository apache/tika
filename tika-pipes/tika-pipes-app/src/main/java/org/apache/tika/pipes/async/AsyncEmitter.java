package org.apache.tika.pipes.async;/*
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

import org.apache.tika.pipes.emitter.AbstractEmitter;
import org.apache.tika.pipes.emitter.EmitData;
import org.apache.tika.pipes.emitter.Emitter;
import org.apache.tika.pipes.emitter.EmitterManager;
import org.apache.tika.pipes.emitter.TikaEmitterException;
import org.apache.tika.utils.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

/**
 * Worker thread that takes EmitData off the queue, batches it
 * and tries to emit it as a batch
 */
public class AsyncEmitter implements Callable<Integer> {

    private static final Logger LOG = LoggerFactory.getLogger(AsyncEmitter.class);

    private final EmitterManager emitterManager;
    private final AsyncEmitHook asyncEmitHook;
    private final long emitWithinMs;
    private final long emitMaxBytes;
    private final EmitDataCache cache;
    ArrayBlockingQueue<AsyncData> dataQueue = new ArrayBlockingQueue<>(1000);

    Instant lastEmitted = Instant.now();

    public AsyncEmitter(EmitterManager emitterManager, AsyncEmitHook asyncEmitHook,
                        long emitWithinMs, long emitMaxBytes) {
        this.emitterManager = emitterManager;
        this.asyncEmitHook = asyncEmitHook;
        this.emitWithinMs = emitWithinMs;
        this.emitMaxBytes = emitMaxBytes;
        this.cache = new EmitDataCache();
    }

    public boolean emit(AsyncData asyncData, long pollMs)
            throws InterruptedException {
        if (asyncData == null) {
            return true;
        }
        return dataQueue.offer(asyncData, pollMs, TimeUnit.MILLISECONDS);
    }

    @Override
    public Integer call() throws Exception {
        while (true) {
            AsyncData asyncData = dataQueue.poll(100, TimeUnit.MILLISECONDS);
            if (asyncData != null) {
                cache.add(asyncData);
            }
            long elapsed = ChronoUnit.MILLIS.between(lastEmitted, Instant.now());
            if (elapsed > emitWithinMs) {
                LOG.debug("{} elapsed > {}, going to emitAll",
                        elapsed, emitWithinMs);
                //this can block for a bit
                emitAll();
            }
        }
    }

    public void emitAll() {
        cache.emitAll();
    }

    private class EmitDataCache {

        long estimatedSize = 0;
        int size = 0;
        Map<String, List<AsyncData>> map = new HashMap<>();


        void updateEstimatedSize(long newBytes) {
            estimatedSize += newBytes;
        }

        synchronized void add(AsyncData data) {
            size++;
            long sz = AbstractEmitter.estimateSizeInBytes(data.getEmitKey().getKey(), data.getMetadataList());
            if (estimatedSize + sz > emitMaxBytes) {
                LOG.debug("estimated size ({}) > maxBytes({}), going to emitAll",
                        (estimatedSize + sz), emitMaxBytes);
                emitAll();
            }
            List<AsyncData> cached = map.get(data.getEmitKey().getEmitterName());
            if (cached == null) {
                cached = new ArrayList<>();
                map.put(data.getEmitKey().getEmitterName(), cached);
            }
            updateEstimatedSize(sz);
            cached.add(data);
        }

        private synchronized void emitAll() {
            int emitted = 0;
            LOG.debug("about to emit all {}", size);
            for (Map.Entry<String, List<AsyncData>> e : map.entrySet()) {
                Emitter emitter = emitterManager.getEmitter(e.getKey());
                tryToEmit(emitter, e.getKey(), e.getValue());
                emitted += e.getValue().size();
            }
            LOG.debug("emitted: {}", emitted);
            estimatedSize = 0;
            size = 0;
            map.clear();
            lastEmitted = Instant.now();
        }

        private void tryToEmit(Emitter emitter, String emitterName, List<AsyncData> cachedEmitData) {
            if (emitter == null) {
                LOG.error("Can't find emitter '{}' in TikaConfig!", emitterName);
            }
            List<EmitData> emitData = new ArrayList<>();
            Set<AsyncTask> asyncTasks = new HashSet<>();
            for (AsyncData d : cachedEmitData) {
                emitData.add(new EmitData(d.getAsyncTask().getEmitKey(), d.getMetadataList()));
                asyncTasks.add(d.getAsyncTask());
            }
            try {
                emitter.emit(emitData);
                for (AsyncData d : cachedEmitData) {
                    asyncEmitHook.onSuccess(d.getAsyncTask());
                }
            } catch (IOException | TikaEmitterException e) {
                e.printStackTrace();
                for (AsyncData d : cachedEmitData) {
                    asyncEmitHook.onFail(d.getAsyncTask());
                }
                e.printStackTrace();
                LOG.warn("emitter class ({}): {}", emitter.getClass(),
                        ExceptionUtils.getStackTrace(e));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
