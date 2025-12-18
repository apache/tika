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
package org.apache.tika.pipes.core.config;

import java.util.Set;

import org.apache.tika.plugins.ExtensionConfig;
import org.apache.tika.plugins.TikaExtension;

/**
 * Interface for storing and retrieving component configurations.
 * Implementations can provide different storage backends (in-memory, database, distributed cache, etc.).
 * <p>
 * <b>Thread-safety:</b> Implementations of this interface may or may not be thread-safe.
 * If an implementation is thread-safe, it should be clearly documented as such.
 * Callers must not assume thread-safety unless it is explicitly documented by the implementation.
 * The default in-memory implementation ({@code InMemoryConfigStore}) is thread-safe.
 * <p>
 * <b>Performance considerations:</b> The {@link #keySet()} method should be an inexpensive operation
 * as it may be called in error message generation and other scenarios where performance matters.
 */
public interface ConfigStore extends TikaExtension {

    /**
     * Initializes the configuration store.
     * This method should be called once before using the store.
     * Implementations may use this to establish connections, initialize caches, etc.
     *
     * @throws Exception if initialization fails
     */
    default void init() throws Exception {
        // Default implementation does nothing
    }

    /**
     * Stores a configuration.
     *
     * @param id the configuration ID (must not be null)
     * @param config the configuration to store (must not be null)
     * @throws NullPointerException if id or config is null
     */
    void put(String id, ExtensionConfig config);

    /**
     * Retrieves a configuration by ID.
     *
     * @param id the configuration ID (must not be null)
     * @return the configuration, or null if not found
     * @throws NullPointerException if id is null
     */
    ExtensionConfig get(String id);

    /**
     * Checks if a configuration exists.
     *
     * @param id the configuration ID (must not be null)
     * @return true if the configuration exists
     * @throws NullPointerException if id is null
     */
    boolean containsKey(String id);

    /**
     * Returns all configuration IDs.
     * Implementations should return an immutable snapshot to avoid
     * ConcurrentModificationException during iteration.
     *
     * @return an immutable set of all configuration IDs
     */
    Set<String> keySet();

    /**
     * Returns the number of stored configurations.
     *
     * @return the number of configurations
     */
    int size();
}
