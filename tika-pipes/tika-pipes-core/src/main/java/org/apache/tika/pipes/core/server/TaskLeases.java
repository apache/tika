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
package org.apache.tika.pipes.core.server;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.apache.tika.exception.TikaException;
import org.apache.tika.pipes.api.emitter.Emitter;
import org.apache.tika.pipes.api.fetcher.Fetcher;
import org.apache.tika.pipes.core.AbstractComponentManager;
import org.apache.tika.pipes.core.Lease;

/**
 * The fetcher and emitter leases one task holds, one per id, released together when the task
 * ends: a runtime save or delete mid-task cannot close an instance the task is still using.
 */
class TaskLeases implements AutoCloseable {

    private final AbstractComponentManager<Fetcher, ?> fetcherManager;
    private final AbstractComponentManager<Emitter, ?> emitterManager;
    private final Map<String, Lease<Fetcher>> fetchers = new HashMap<>();
    private final Map<String, Lease<Emitter>> emitters = new HashMap<>();

    TaskLeases(AbstractComponentManager<Fetcher, ?> fetcherManager,
               AbstractComponentManager<Emitter, ?> emitterManager) {
        this.fetcherManager = fetcherManager;
        this.emitterManager = emitterManager;
    }

    synchronized Fetcher fetcher(String id) throws IOException, TikaException {
        Lease<Fetcher> lease = fetchers.get(id);
        if (lease == null) {
            lease = fetcherManager.acquire(id);
            fetchers.put(id, lease);
        }
        return lease.get();
    }

    synchronized Emitter emitter(String id) throws IOException, TikaException {
        Lease<Emitter> lease = emitters.get(id);
        if (lease == null) {
            lease = emitterManager.acquire(id);
            emitters.put(id, lease);
        }
        return lease.get();
    }

    @Override
    public synchronized void close() {
        fetchers.values().forEach(Lease::close);
        emitters.values().forEach(Lease::close);
        fetchers.clear();
        emitters.clear();
    }
}
