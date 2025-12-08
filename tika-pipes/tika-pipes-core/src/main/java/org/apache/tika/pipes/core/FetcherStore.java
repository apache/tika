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
package org.apache.tika.pipes.core;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.pipes.api.fetcher.Fetcher;
import org.apache.tika.pipes.api.statestore.StateStore;
import org.apache.tika.pipes.api.statestore.StateStoreException;
import org.apache.tika.pipes.core.statestore.ComponentSerializer;
import org.apache.tika.plugins.ExtensionConfig;

/**
 * FetcherStore manages Fetcher instances using StateStore for distributed state management.
 * Provides automatic expiration of stale fetchers based on last access time.
 * <p>
 * Key namespaces used:
 * <ul>
 *   <li>fetcher:config:{id} - ExtensionConfig for the fetcher</li>
 *   <li>fetcher:access:{id} - Last access time tracking</li>
 * </ul>
 * <p>
 * Fetcher instances are kept in local memory cache for performance,
 * while configurations are stored in StateStore for cluster-wide sharing.
 */
public class FetcherStore implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(FetcherStore.class);
    public static final long EXPIRE_JOB_INITIAL_DELAY = 1L;

    private static final String FETCHER_CONFIG_PREFIX = "fetcher:config:";
    private static final String FETCHER_ACCESS_PREFIX = "fetcher:access:";

    private final StateStore stateStore;
    private final ComponentSerializer serializer;
    private final long expireAfterMillis;
    private final long checkForExpiredDelayMillis;

    // Local cache of fetcher instances for performance
    private final Map<String, Fetcher> fetcherCache = new ConcurrentHashMap<>();

    private final ScheduledExecutorService executorService =
            Executors.newSingleThreadScheduledExecutor();

    /**
     * Create a FetcherStore with the given StateStore backend.
     *
     * @param stateStore the state store for distributed state
     * @param expireAfterMillis how long before fetchers expire (milliseconds)
     * @param checkForExpiredDelayMillis how often to check for expired fetchers (milliseconds)
     */
    public FetcherStore(StateStore stateStore, long expireAfterMillis,
                        long checkForExpiredDelayMillis) {
        this.stateStore = stateStore;
        this.serializer = new ComponentSerializer();
        this.expireAfterMillis = expireAfterMillis;
        this.checkForExpiredDelayMillis = checkForExpiredDelayMillis;

        // Start expiration check job
        executorService.scheduleAtFixedRate(this::checkAndRemoveExpired,
                EXPIRE_JOB_INITIAL_DELAY, checkForExpiredDelayMillis, TimeUnit.MILLISECONDS);
    }

    /**
     * Legacy constructor for backward compatibility (takes seconds).
     *
     * @param stateStore the state store
     * @param expireAfterSeconds how long before fetchers expire (seconds)
     * @param checkForExpiredDelaySeconds how often to check for expired fetchers (seconds)
     */
    public FetcherStore(StateStore stateStore, int expireAfterSeconds,
                        int checkForExpiredDelaySeconds) {
        this(stateStore, expireAfterSeconds * 1000L, checkForExpiredDelaySeconds * 1000L);
    }

    private void checkAndRemoveExpired() {
        Set<String> expired = new HashSet<>();
        try {
            Set<String> keys = stateStore.listKeys();
            for (String key : keys) {
                LOG.info("Key in state store: {}", key);
                if (key.startsWith(FETCHER_ACCESS_PREFIX)) {
                    String fetcherId = key.substring(FETCHER_ACCESS_PREFIX.length());
                    Instant lastAccessed = stateStore.getAccessTime(key);
                    if (lastAccessed == null) {
                        LOG.error("Detected a fetcher with no last access time. " +
                                "FetcherName={}", fetcherId);
                        expired.add(fetcherId);
                    } else if (Instant.now()
                            .isAfter(lastAccessed.plusMillis(expireAfterMillis))) {
                        long elapsedMillis = Instant.now().toEpochMilli() -
                                lastAccessed.toEpochMilli();
                        LOG.info("Detected stale fetcher {} hasn't been accessed in {} ms. " +
                                "Deleting.", fetcherId, elapsedMillis);
                        expired.add(fetcherId);
                    }
                }
            }
            for (String expiredFetcherId : expired) {
                deleteFetcher(expiredFetcherId);
            }
        } catch (StateStoreException e) {
            LOG.error("Error checking for expired fetchers", e);
        }
    }

    /**
     * Delete a fetcher from the store.
     *
     * @param fetcherPluginId the fetcher ID to delete
     * @return true if the fetcher was deleted, false if it didn't exist
     */
    public boolean deleteFetcher(String fetcherPluginId) {
        try {
            fetcherCache.remove(fetcherPluginId);
            boolean configDeleted =
                    stateStore.delete(FETCHER_CONFIG_PREFIX + fetcherPluginId);
            stateStore.delete(FETCHER_ACCESS_PREFIX + fetcherPluginId);
            return configDeleted;
        } catch (StateStoreException e) {
            LOG.error("Error deleting fetcher: {}", fetcherPluginId, e);
            return false;
        }
    }

    /**
     * Get all fetchers currently in the store.
     *
     * @return map of fetcher ID to Fetcher instance
     */
    public Map<String, Fetcher> getFetchers() {
        Map<String, Fetcher> result = new HashMap<>();
        try {
            Set<String> keys = stateStore.listKeys();
            for (String key : keys) {
                if (key.startsWith(FETCHER_CONFIG_PREFIX)) {
                    String fetcherId = key.substring(FETCHER_CONFIG_PREFIX.length());
                    Fetcher fetcher = fetcherCache.get(fetcherId);
                    if (fetcher != null) {
                        result.put(fetcherId, fetcher);
                    }
                }
            }
        } catch (StateStoreException e) {
            LOG.error("Error getting fetchers", e);
        }
        return result;
    }

    /**
     * Get all fetcher configurations currently in the store.
     *
     * @return map of fetcher ID to ExtensionConfig
     */
    public Map<String, ExtensionConfig> getFetcherConfigs() {
        Map<String, ExtensionConfig> result = new HashMap<>();
        try {
            Set<String> keys = stateStore.listKeys();
            for (String key : keys) {
                if (key.startsWith(FETCHER_CONFIG_PREFIX)) {
                    String fetcherId = key.substring(FETCHER_CONFIG_PREFIX.length());
                    byte[] configData = stateStore.get(key);
                    if (configData != null) {
                        ExtensionConfig config = serializer.deserializeConfig(configData);
                        result.put(fetcherId, config);
                    }
                }
            }
        } catch (StateStoreException e) {
            LOG.error("Error getting fetcher configs", e);
        }
        return result;
    }

    /**
     * Get a fetcher and log its access time.
     * This prevents the scheduled job from removing the stale fetcher.
     *
     * @param fetcherPluginId the fetcher ID to retrieve
     * @param <T> the fetcher type
     * @return the fetcher instance, or null if not found
     */
    public <T extends Fetcher> T getFetcherAndLogAccess(String fetcherPluginId) {
        try {
            // Update access time in state store
            String accessKey = FETCHER_ACCESS_PREFIX + fetcherPluginId;
            stateStore.updateAccessTime(accessKey, Instant.now());

            // Return from cache
            return (T) fetcherCache.get(fetcherPluginId);
        } catch (StateStoreException e) {
            LOG.error("Error logging access for fetcher: {}", fetcherPluginId, e);
            return (T) fetcherCache.get(fetcherPluginId);
        }
    }

    /**
     * Create and store a new fetcher.
     *
     * @param fetcher the fetcher instance
     * @param config the fetcher configuration
     * @param <T> the fetcher type
     */
    public <T extends Fetcher> void createFetcher(T fetcher, ExtensionConfig config) {
        String id = fetcher.getExtensionConfig().id();

        try {
            // Store config in state store for cluster-wide sharing
            byte[] configData = serializer.serializeConfig(config);
            stateStore.put(FETCHER_CONFIG_PREFIX + id, configData);

            // Cache the fetcher instance locally
            fetcherCache.put(id, fetcher);

            // Log initial access
            getFetcherAndLogAccess(id);

            LOG.info("Created fetcher: {}", id);
        } catch (StateStoreException e) {
            LOG.error("Error creating fetcher: {}", id, e);
            throw new RuntimeException("Failed to create fetcher: " + id, e);
        }
    }

    @Override
    public void close() {
        executorService.shutdownNow();
        try {
            stateStore.close();
        } catch (StateStoreException e) {
            LOG.error("Error closing state store", e);
        }
    }
}
