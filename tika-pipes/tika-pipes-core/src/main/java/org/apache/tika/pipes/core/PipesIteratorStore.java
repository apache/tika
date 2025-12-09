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

import org.apache.tika.pipes.api.pipesiterator.PipesIterator;
import org.apache.tika.pipes.api.statestore.StateStore;
import org.apache.tika.plugins.ExtensionConfig;

/**
 * PipesIteratorStore manages PipesIterator instances using StateStore for distributed state management.
 * Provides automatic expiration of stale pipe iterators based on last access time.
 * <p>
 * This extends ComponentStore to leverage common distributed state management patterns,
 * enabling dynamic pipes iterator CRUD operations across cluster nodes.
 */
public class PipesIteratorStore extends ComponentStore<PipesIterator> {

    /**
     * Create a PipesIteratorStore with the given StateStore backend.
     *
     * @param stateStore the state store for distributed state
     * @param expireAfterMillis how long before iterators expire (milliseconds)
     * @param checkForExpiredDelayMillis how often to check for expired iterators (milliseconds)
     */
    public PipesIteratorStore(StateStore stateStore, long expireAfterMillis,
                              long checkForExpiredDelayMillis) {
        super("pipesiterator", stateStore, expireAfterMillis, checkForExpiredDelayMillis);
    }

    /**
     * Legacy constructor for backward compatibility (takes seconds).
     *
     * @param stateStore the state store
     * @param expireAfterSeconds how long before iterators expire (seconds)
     * @param checkForExpiredDelaySeconds how often to check for expired iterators (seconds)
     */
    public PipesIteratorStore(StateStore stateStore, int expireAfterSeconds,
                              int checkForExpiredDelaySeconds) {
        this(stateStore, expireAfterSeconds * 1000L, checkForExpiredDelaySeconds * 1000L);
    }

    @Override
    protected String getComponentId(PipesIterator component) {
        return component.getExtensionConfig().id();
    }

    @Override
    protected ExtensionConfig getExtensionConfig(PipesIterator component) {
        return component.getExtensionConfig();
    }

    // Convenience methods with PipesIterator-specific names

    public boolean deleteIterator(String iteratorId) {
        return deleteComponent(iteratorId);
    }

    public Map<String, PipesIterator> getIterators() {
        return getComponents();
    }

    public Map<String, ExtensionConfig> getIteratorConfigs() {
        return getComponentConfigs();
    }

    public <T extends PipesIterator> T getIteratorAndLogAccess(String iteratorId) {
        return (T) getComponentAndLogAccess(iteratorId);
    }

    public <T extends PipesIterator> void createIterator(T iterator, ExtensionConfig config) {
        createComponent(iterator, config);
    }
}
