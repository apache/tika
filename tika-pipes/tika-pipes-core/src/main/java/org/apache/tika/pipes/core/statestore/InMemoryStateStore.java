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
package org.apache.tika.pipes.core.statestore;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.tika.pipes.api.statestore.StateStore;
import org.apache.tika.pipes.api.statestore.StateStoreException;
import org.apache.tika.plugins.ExtensionConfig;

/**
 * In-memory implementation of StateStore using ConcurrentHashMap.
 * This is the default implementation used for single-node deployments.
 * <p>
 * This implementation provides thread-safe operations but does not
 * persist state across restarts or share state across JVM instances.
 */
public class InMemoryStateStore implements StateStore {

    private final ConcurrentHashMap<String, byte[]> data = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Instant> accessTimes = new ConcurrentHashMap<>();
    private volatile boolean closed = false;
    private ExtensionConfig extensionConfig;

    @Override
    public void put(String key, byte[] value) throws StateStoreException {
        checkNotClosed();
        if (key == null) {
            throw new StateStoreException("Key cannot be null");
        }
        if (value == null) {
            throw new StateStoreException("Value cannot be null");
        }
        data.put(key, value);
    }

    @Override
    public byte[] get(String key) throws StateStoreException {
        checkNotClosed();
        if (key == null) {
            throw new StateStoreException("Key cannot be null");
        }
        return data.get(key);
    }

    @Override
    public boolean delete(String key) throws StateStoreException {
        checkNotClosed();
        if (key == null) {
            throw new StateStoreException("Key cannot be null");
        }
        accessTimes.remove(key);
        return data.remove(key) != null;
    }

    @Override
    public Set<String> listKeys() throws StateStoreException {
        checkNotClosed();
        // Return union of both data keys and accessTimes keys
        // This allows expiration logic to find both config and access time entries
        Set<String> allKeys = new java.util.HashSet<>(data.keySet());
        allKeys.addAll(accessTimes.keySet());
        return allKeys;
    }

    @Override
    public void updateAccessTime(String key, Instant accessTime) throws StateStoreException {
        checkNotClosed();
        if (key == null) {
            throw new StateStoreException("Key cannot be null");
        }
        if (accessTime == null) {
            throw new StateStoreException("Access time cannot be null");
        }
        accessTimes.put(key, accessTime);
    }

    @Override
    public Instant getAccessTime(String key) throws StateStoreException {
        checkNotClosed();
        if (key == null) {
            throw new StateStoreException("Key cannot be null");
        }
        return accessTimes.get(key);
    }

    @Override
    public void initialize(Map<String, String> config) throws StateStoreException {
        // No initialization needed for in-memory store
    }

    @Override
    public void close() throws StateStoreException {
        closed = true;
        data.clear();
        accessTimes.clear();
    }

    @Override
    public ExtensionConfig getExtensionConfig() {
        return extensionConfig;
    }

    public void setExtensionConfig(ExtensionConfig extensionConfig) {
        this.extensionConfig = extensionConfig;
    }

    private void checkNotClosed() throws StateStoreException {
        if (closed) {
            throw new StateStoreException("StateStore has been closed");
        }
    }
}
