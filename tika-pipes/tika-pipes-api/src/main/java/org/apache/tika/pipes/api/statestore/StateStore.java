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
package org.apache.tika.pipes.api.statestore;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

import org.apache.tika.plugins.TikaExtension;

/**
 * StateStore provides a key-value store abstraction for distributed state management
 * in Tika Pipes. This enables sharing of Fetcher, Emitter, and PipesIterator configurations
 * across multiple nodes in a cluster.
 * <p>
 * Implementations can be in-memory (for single-node deployments) or backed by
 * distributed systems like Apache Ignite, Redis, or Hazelcast.
 * <p>
 * Keys use namespace prefixes to organize different types of data:
 * <ul>
 *   <li>fetcher:* - Fetcher configurations and instances</li>
 *   <li>emitter:* - Emitter configurations and instances</li>
 *   <li>pipesiterator:* - PipesIterator configurations</li>
 * </ul>
 */
public interface StateStore extends TikaExtension, AutoCloseable {

    /**
     * Store a value associated with the given key.
     *
     * @param key the key to store the value under
     * @param value the byte array value to store
     * @throws StateStoreException if the operation fails
     */
    void put(String key, byte[] value) throws StateStoreException;

    /**
     * Retrieve the value associated with the given key.
     *
     * @param key the key to retrieve
     * @return the byte array value, or null if the key doesn't exist
     * @throws StateStoreException if the operation fails
     */
    byte[] get(String key) throws StateStoreException;

    /**
     * Delete the value associated with the given key.
     *
     * @param key the key to delete
     * @return true if the key was deleted, false if it didn't exist
     * @throws StateStoreException if the operation fails
     */
    boolean delete(String key) throws StateStoreException;

    /**
     * List all keys currently in the store.
     *
     * @return a set of all keys
     * @throws StateStoreException if the operation fails
     */
    Set<String> listKeys() throws StateStoreException;

    /**
     * Update the last access time for the given key.
     * This is used for expiration tracking.
     *
     * @param key the key to update
     * @param accessTime the access time to record
     * @throws StateStoreException if the operation fails
     */
    void updateAccessTime(String key, Instant accessTime) throws StateStoreException;

    /**
     * Get the last access time for the given key.
     *
     * @param key the key to query
     * @return the last access time, or null if not tracked or key doesn't exist
     * @throws StateStoreException if the operation fails
     */
    Instant getAccessTime(String key) throws StateStoreException;

    /**
     * Initialize the state store with the given configuration.
     * This is called once before the store is used.
     *
     * @param config configuration parameters for the store
     * @throws StateStoreException if initialization fails
     */
    void initialize(Map<String, String> config) throws StateStoreException;

    /**
     * Check if a key exists in the store.
     *
     * @param key the key to check
     * @return true if the key exists, false otherwise
     * @throws StateStoreException if the operation fails
     */
    default boolean containsKey(String key) throws StateStoreException {
        return get(key) != null;
    }

    /**
     * Close the state store and release any resources.
     * After this is called, the store should not be used.
     *
     * @throws StateStoreException if closing fails
     */
    @Override
    void close() throws StateStoreException;
}
