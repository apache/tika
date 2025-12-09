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

import java.util.Map;

import org.apache.tika.pipes.api.fetcher.Fetcher;
import org.apache.tika.pipes.api.statestore.StateStore;
import org.apache.tika.plugins.ExtensionConfig;

/**
 * FetcherStore manages Fetcher instances using StateStore for distributed state management.
 * Provides automatic expiration of stale fetchers based on last access time.
 * <p>
 * This extends ComponentStore to leverage common distributed state management patterns,
 * eliminating code duplication while maintaining backward-compatible API.
 */
public class FetcherStore extends ComponentStore<Fetcher> {

    /**
     * Create a FetcherStore with the given StateStore backend.
     *
     * @param stateStore the state store for distributed state
     * @param expireAfterMillis how long before fetchers expire (milliseconds)
     * @param checkForExpiredDelayMillis how often to check for expired fetchers (milliseconds)
     */
    public FetcherStore(StateStore stateStore, long expireAfterMillis,
                        long checkForExpiredDelayMillis) {
        super("fetcher", stateStore, expireAfterMillis, checkForExpiredDelayMillis);
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

    @Override
    protected String getComponentId(Fetcher component) {
        return component.getExtensionConfig().id();
    }

    @Override
    protected ExtensionConfig getExtensionConfig(Fetcher component) {
        return component.getExtensionConfig();
    }

    // Convenience methods with Fetcher-specific names for backward compatibility

    public boolean deleteFetcher(String fetcherId) {
        return deleteComponent(fetcherId);
    }

    public Map<String, Fetcher> getFetchers() {
        return getComponents();
    }

    public Map<String, ExtensionConfig> getFetcherConfigs() {
        return getComponentConfigs();
    }

    public <T extends Fetcher> T getFetcherAndLogAccess(String fetcherId) {
        return (T) getComponentAndLogAccess(fetcherId);
    }

    public <T extends Fetcher> void createFetcher(T fetcher, ExtensionConfig config) {
        createComponent(fetcher, config);
    }
}
