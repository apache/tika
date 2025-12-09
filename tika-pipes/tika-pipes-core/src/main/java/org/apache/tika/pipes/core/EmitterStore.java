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

import org.apache.tika.pipes.api.emitter.Emitter;
import org.apache.tika.pipes.api.statestore.StateStore;
import org.apache.tika.plugins.ExtensionConfig;

/**
 * EmitterStore manages Emitter instances using StateStore for distributed state management.
 * Provides automatic expiration of stale emitters based on last access time.
 * <p>
 * This extends ComponentStore to leverage common distributed state management patterns,
 * enabling dynamic emitter CRUD operations across cluster nodes.
 */
public class EmitterStore extends ComponentStore<Emitter> {

    /**
     * Create an EmitterStore with the given StateStore backend.
     *
     * @param stateStore the state store for distributed state
     * @param expireAfterMillis how long before emitters expire (milliseconds)
     * @param checkForExpiredDelayMillis how often to check for expired emitters (milliseconds)
     */
    public EmitterStore(StateStore stateStore, long expireAfterMillis,
                        long checkForExpiredDelayMillis) {
        super("emitter", stateStore, expireAfterMillis, checkForExpiredDelayMillis);
    }

    /**
     * Legacy constructor for backward compatibility (takes seconds).
     *
     * @param stateStore the state store
     * @param expireAfterSeconds how long before emitters expire (seconds)
     * @param checkForExpiredDelaySeconds how often to check for expired emitters (seconds)
     */
    public EmitterStore(StateStore stateStore, int expireAfterSeconds,
                        int checkForExpiredDelaySeconds) {
        this(stateStore, expireAfterSeconds * 1000L, checkForExpiredDelaySeconds * 1000L);
    }

    @Override
    protected String getComponentId(Emitter component) {
        return component.getExtensionConfig().id();
    }

    @Override
    protected ExtensionConfig getExtensionConfig(Emitter component) {
        return component.getExtensionConfig();
    }

    // Convenience methods with Emitter-specific names

    public boolean deleteEmitter(String emitterId) {
        return deleteComponent(emitterId);
    }

    public Map<String, Emitter> getEmitters() {
        return getComponents();
    }

    public Map<String, ExtensionConfig> getEmitterConfigs() {
        return getComponentConfigs();
    }

    public <T extends Emitter> T getEmitterAndLogAccess(String emitterId) {
        return (T) getComponentAndLogAccess(emitterId);
    }

    public <T extends Emitter> void createEmitter(T emitter, ExtensionConfig config) {
        createComponent(emitter, config);
    }
}
