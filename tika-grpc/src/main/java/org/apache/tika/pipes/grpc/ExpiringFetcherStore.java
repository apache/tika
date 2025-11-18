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
package org.apache.tika.pipes.grpc;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.pipes.api.fetcher.Fetcher;
import org.apache.tika.plugins.ExtensionConfig;

public class ExpiringFetcherStore implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(ExpiringFetcherStore.class);
    public static final long EXPIRE_JOB_INITIAL_DELAY = 1L;
    private final Map<String, Fetcher> fetchers = Collections.synchronizedMap(new HashMap<>());
    private final Map<String, ExtensionConfig> fetcherConfigs = Collections.synchronizedMap(new HashMap<>());
    private final Map<String, Instant> fetcherLastAccessed = Collections.synchronizedMap(new HashMap<>());

    private final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();

    public ExpiringFetcherStore(int expireAfterSeconds, int checkForExpiredFetchersDelaySeconds) {
        executorService.scheduleAtFixedRate(() -> {
            Set<String> expired = new HashSet<>();
            for (String fetcherPluginId : fetchers.keySet()) {
                Instant lastAccessed = fetcherLastAccessed.get(fetcherPluginId);
                if (lastAccessed == null) {
                    LOG.error("Detected a fetcher with no last access time. FetcherName={}", fetcherPluginId);
                    expired.add(fetcherPluginId);
                } else if (Instant
                        .now()
                        .isAfter(lastAccessed.plusSeconds(expireAfterSeconds))) {
                    LOG.info("Detected stale fetcher {} hasn't been accessed in {} seconds. " + "Deleting.", fetcherPluginId, Instant
                            .now()
                            .getEpochSecond() - lastAccessed.getEpochSecond());
                    expired.add(fetcherPluginId);
                }
            }
            for (String expiredFetcherId : expired) {
                deleteFetcher(expiredFetcherId);
            }
        }, EXPIRE_JOB_INITIAL_DELAY, checkForExpiredFetchersDelaySeconds, TimeUnit.SECONDS);
    }

    public boolean deleteFetcher(String fetcherPluginId) {
        boolean success = fetchers.remove(fetcherPluginId) != null;
        fetcherConfigs.remove(fetcherPluginId);
        fetcherLastAccessed.remove(fetcherPluginId);
        return success;
    }

    public Map<String, Fetcher> getFetchers() {
        return fetchers;
    }

    public Map<String, ExtensionConfig> getFetcherConfigs() {
        return fetcherConfigs;
    }

    /**
     * This method will get the fetcher, but will also log the access the fetcher as having
     * been accessed. This prevents the scheduled job from removing the stale fetcher.
     */
    public <T extends Fetcher> T getFetcherAndLogAccess(String fetcherPluginId) {
        fetcherLastAccessed.put(fetcherPluginId, Instant.now());
        return (T) fetchers.get(fetcherPluginId);
    }

    public <T extends Fetcher> void createFetcher(T fetcher, ExtensionConfig config) {
        String id = fetcher.getExtensionConfig().id();

        fetchers.put(id, fetcher);
        fetcherConfigs.put(id, config);
        getFetcherAndLogAccess(id);
    }

    @Override
    public void close() {
        executorService.shutdownNow();
    }
}
