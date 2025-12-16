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

/**
 * Interface for storing and retrieving component configurations.
 * Implementations can provide different storage backends (in-memory, database, distributed cache, etc.).
 */
public interface ConfigStore {

    /**
     * Stores a configuration.
     *
     * @param id the configuration ID
     * @param config the configuration to store
     */
    void put(String id, ExtensionConfig config);

    /**
     * Retrieves a configuration by ID.
     *
     * @param id the configuration ID
     * @return the configuration, or null if not found
     */
    ExtensionConfig get(String id);

    /**
     * Checks if a configuration exists.
     *
     * @param id the configuration ID
     * @return true if the configuration exists
     */
    boolean containsKey(String id);

    /**
     * Returns all configuration IDs.
     *
     * @return set of all configuration IDs
     */
    Set<String> keySet();

    /**
     * Returns the number of stored configurations.
     *
     * @return the number of configurations
     */
    int size();
}
