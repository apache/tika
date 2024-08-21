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

import org.apache.tika.pipes.fetcher.config.FetcherConfig;

public class ExpiringFetcherStore implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(ExpiringFetcherStore.class);
    public static final long EXPIRE_JOB_INITIAL_DELAY = 1L;
    private final Map<String, FetcherConfig> fetcherConfigs = Collections.synchronizedMap(new HashMap<>());
    private final Map<String, Instant> fetcherLastAccessed = Collections.synchronizedMap(new HashMap<>());

    private final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();

    public ExpiringFetcherStore(int expireAfterSeconds, int checkForExpiredFetchersDelaySeconds) {
        executorService.scheduleAtFixedRate(() -> {
            Set<String> expired = new HashSet<>();
            for (String fetcherId : fetcherConfigs.keySet()) {
                Instant lastAccessed = fetcherLastAccessed.get(fetcherId);
                if (lastAccessed == null) {
                    LOG.error("Detected a fetcher with no last access time. fetcherId={}", fetcherId);
                    expired.add(fetcherId);
                } else if (Instant
                        .now()
                        .isAfter(lastAccessed.plusSeconds(expireAfterSeconds))) {
                    LOG.info("Detected stale fetcher {} hasn't been accessed in {} seconds. " + "Deleting.", fetcherId, Instant
                            .now()
                            .getEpochSecond() - lastAccessed.getEpochSecond());
                    expired.add(fetcherId);
                }
            }
            for (String expiredFetcherId : expired) {
                deleteFetcher(expiredFetcherId);
            }
        }, EXPIRE_JOB_INITIAL_DELAY, checkForExpiredFetchersDelaySeconds, TimeUnit.SECONDS);
    }

    public boolean deleteFetcher(String fetcherId) {
        boolean success = fetcherConfigs.remove(fetcherId) != null;
        fetcherLastAccessed.remove(fetcherId);
        return success;
    }

    public Map<String, FetcherConfig> getFetcherConfigs() {
        return fetcherConfigs;
    }

    /**
     * This method will get the fetcher, but will also log the access the fetcher as having
     * been accessed. This prevents the scheduled job from removing the stale fetcher.
     */
    public <C extends FetcherConfig> C getFetcherAndLogAccess(String fetcherId) {
        fetcherLastAccessed.put(fetcherId, Instant.now());
        return (C) fetcherConfigs.get(fetcherId);
    }

    public <C extends FetcherConfig> void createFetcher(String fetcherId, C config) {
        config.setFetcherId(fetcherId);
        fetcherConfigs.put(fetcherId, config);
        getFetcherAndLogAccess(fetcherId);
    }

    @Override
    public void close() {
        executorService.shutdownNow();
    }
}
