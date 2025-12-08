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

import org.apache.tika.pipes.api.statestore.StateStore;
import org.apache.tika.pipes.api.statestore.StateStoreException;
import org.apache.tika.pipes.core.statestore.ComponentSerializer;
import org.apache.tika.plugins.ExtensionConfig;

/**
 * Base class for managing Tika Pipes components (Fetchers, Emitters, PipesIterators)
 * with automatic expiration using StateStore backend for distributed state management.
 * <p>
 * Key namespaces used:
 * <ul>
 *   <li>{componentType}:config:{id} - ExtensionConfig for the component</li>
 *   <li>{componentType}:access:{id} - Last access time tracking</li>
 * </ul>
 * <p>
 * Component instances are kept in local memory cache for performance,
 * while configurations are stored in StateStore for cluster-wide sharing.
 *
 * @param <T> the component type (Fetcher, Emitter, PipesIterator)
 */
public abstract class ComponentStore<T> implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(ComponentStore.class);
    public static final long EXPIRE_JOB_INITIAL_DELAY = 1L;

    private final String componentType;
    private final StateStore stateStore;
    private final ComponentSerializer serializer;
    private final long expireAfterMillis;
    private final long checkForExpiredDelayMillis;

    // Local cache of component instances for performance
    private final Map<String, T> componentCache = new ConcurrentHashMap<>();

    private final ScheduledExecutorService executorService =
            Executors.newSingleThreadScheduledExecutor();

    /**
     * Create a ComponentStore with the given StateStore backend.
     *
     * @param componentType the type name (e.g., "fetcher", "emitter", "pipesiterator")
     * @param stateStore the state store for distributed state
     * @param expireAfterMillis how long before components expire (milliseconds)
     * @param checkForExpiredDelayMillis how often to check for expired components (milliseconds)
     */
    protected ComponentStore(String componentType, StateStore stateStore,
                           long expireAfterMillis, long checkForExpiredDelayMillis) {
        this.componentType = componentType;
        this.stateStore = stateStore;
        this.serializer = new ComponentSerializer();
        this.expireAfterMillis = expireAfterMillis;
        this.checkForExpiredDelayMillis = checkForExpiredDelayMillis;

        // Start expiration check job
        executorService.scheduleAtFixedRate(this::checkAndRemoveExpired,
                EXPIRE_JOB_INITIAL_DELAY, checkForExpiredDelayMillis, TimeUnit.MILLISECONDS);
    }

    /**
     * Get the component type name.
     */
    protected String getComponentType() {
        return componentType;
    }

    /**
     * Get the config key prefix for this component type.
     */
    protected String getConfigPrefix() {
        return componentType + ":config:";
    }

    /**
     * Get the access time key prefix for this component type.
     */
    protected String getAccessPrefix() {
        return componentType + ":access:";
    }

    /**
     * Extract the component ID from the given component instance.
     * Must be implemented by subclasses.
     */
    protected abstract String getComponentId(T component);

    /**
     * Get the ExtensionConfig from the given component instance.
     * Must be implemented by subclasses.
     */
    protected abstract ExtensionConfig getExtensionConfig(T component);

    private void checkAndRemoveExpired() {
        Set<String> expired = new HashSet<>();
        try {
            Set<String> keys = stateStore.listKeys();
            String accessPrefix = getAccessPrefix();

            for (String key : keys) {
                if (key.startsWith(accessPrefix)) {
                    String componentId = key.substring(accessPrefix.length());
                    Instant lastAccessed = stateStore.getAccessTime(key);
                    if (lastAccessed == null) {
                        LOG.error("Detected a {} with no last access time. ComponentId={}",
                                componentType, componentId);
                        expired.add(componentId);
                    } else if (Instant.now()
                            .isAfter(lastAccessed.plusMillis(expireAfterMillis))) {
                        long elapsedMillis = Instant.now().toEpochMilli() -
                                lastAccessed.toEpochMilli();
                        LOG.info("Detected stale {} '{}' hasn't been accessed in {} ms. Deleting.",
                                componentType, componentId, elapsedMillis);
                        expired.add(componentId);
                    }
                }
            }
            for (String expiredId : expired) {
                deleteComponent(expiredId);
            }
        } catch (StateStoreException e) {
            LOG.error("Error checking for expired {}s", componentType, e);
        }
    }

    /**
     * Delete a component from the store.
     *
     * @param componentId the component ID to delete
     * @return true if the component was deleted, false if it didn't exist
     */
    public boolean deleteComponent(String componentId) {
        try {
            componentCache.remove(componentId);
            boolean configDeleted = stateStore.delete(getConfigPrefix() + componentId);
            stateStore.delete(getAccessPrefix() + componentId);
            return configDeleted;
        } catch (StateStoreException e) {
            LOG.error("Error deleting {}: {}", componentType, componentId, e);
            return false;
        }
    }

    /**
     * Get all components currently in the store.
     *
     * @return map of component ID to component instance
     */
    public Map<String, T> getComponents() {
        Map<String, T> result = new HashMap<>();
        try {
            Set<String> keys = stateStore.listKeys();
            String configPrefix = getConfigPrefix();

            for (String key : keys) {
                if (key.startsWith(configPrefix)) {
                    String componentId = key.substring(configPrefix.length());
                    T component = componentCache.get(componentId);
                    if (component != null) {
                        result.put(componentId, component);
                    }
                }
            }
        } catch (StateStoreException e) {
            LOG.error("Error getting {}s", componentType, e);
        }
        return result;
    }

    /**
     * Get all component configurations currently in the store.
     *
     * @return map of component ID to ExtensionConfig
     */
    public Map<String, ExtensionConfig> getComponentConfigs() {
        Map<String, ExtensionConfig> result = new HashMap<>();
        try {
            Set<String> keys = stateStore.listKeys();
            String configPrefix = getConfigPrefix();

            for (String key : keys) {
                if (key.startsWith(configPrefix)) {
                    String componentId = key.substring(configPrefix.length());
                    byte[] configData = stateStore.get(key);
                    if (configData != null) {
                        ExtensionConfig config = serializer.deserializeConfig(configData);
                        result.put(componentId, config);
                    }
                }
            }
        } catch (StateStoreException e) {
            LOG.error("Error getting {} configs", componentType, e);
        }
        return result;
    }

    /**
     * Get a component and log its access time.
     * This prevents the scheduled job from removing the stale component.
     *
     * @param componentId the component ID to retrieve
     * @return the component instance, or null if not found
     */
    public T getComponentAndLogAccess(String componentId) {
        try {
            // Update access time in state store
            String accessKey = getAccessPrefix() + componentId;
            stateStore.updateAccessTime(accessKey, Instant.now());

            // Return from cache
            return componentCache.get(componentId);
        } catch (StateStoreException e) {
            LOG.error("Error logging access for {}: {}", componentType, componentId, e);
            return componentCache.get(componentId);
        }
    }

    /**
     * Create and store a new component.
     *
     * @param component the component instance
     * @param config the component configuration
     */
    public void createComponent(T component, ExtensionConfig config) {
        String id = getComponentId(component);

        try {
            // Store config in state store for cluster-wide sharing
            byte[] configData = serializer.serializeConfig(config);
            stateStore.put(getConfigPrefix() + id, configData);

            // Cache the component instance locally
            componentCache.put(id, component);

            // Log initial access
            getComponentAndLogAccess(id);

            LOG.info("Created {}: {}", componentType, id);
        } catch (StateStoreException e) {
            LOG.error("Error creating {}: {}", componentType, id, e);
            throw new RuntimeException("Failed to create " + componentType + ": " + id, e);
        }
    }

    @Override
    public void close() {
        executorService.shutdownNow();
        try {
            stateStore.close();
        } catch (StateStoreException e) {
            LOG.error("Error closing state store for {}", componentType, e);
        }
    }
}
