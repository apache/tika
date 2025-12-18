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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Factory for creating ConfigStore instances based on configuration.
 */
public class ConfigStoreFactory {
    
    private static final Logger LOG = LoggerFactory.getLogger(ConfigStoreFactory.class);
    
    /**
     * Creates a ConfigStore instance based on the specified type.
     *
     * @param type the type of ConfigStore to create ("memory", "ignite", or a fully qualified class name)
     * @return a ConfigStore instance
     * @throws RuntimeException if the store type is invalid or cannot be instantiated
     */
    public static ConfigStore createConfigStore(String type) {
        if (type == null || type.isEmpty() || "memory".equalsIgnoreCase(type)) {
            LOG.info("Creating InMemoryConfigStore");
            return new InMemoryConfigStore();
        }
        
        if ("ignite".equalsIgnoreCase(type)) {
            try {
                LOG.info("Creating IgniteConfigStore");
                Class<?> igniteClass = Class.forName("org.apache.tika.pipes.ignite.IgniteConfigStore");
                return (ConfigStore) igniteClass.getDeclaredConstructor().newInstance();
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(
                    "IgniteConfigStore not found. Add tika-ignite-config-store dependency to use Ignite store.", e);
            } catch (Exception e) {
                throw new RuntimeException("Failed to instantiate IgniteConfigStore", e);
            }
        }
        
        // Try to load as a fully qualified class name
        try {
            LOG.info("Creating ConfigStore from class: {}", type);
            Class<?> storeClass = Class.forName(type);
            if (!ConfigStore.class.isAssignableFrom(storeClass)) {
                throw new RuntimeException(
                    "Class " + type + " does not implement ConfigStore interface");
            }
            return (ConfigStore) storeClass.getDeclaredConstructor().newInstance();
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(
                "Unknown ConfigStore type: " + type + ". Use 'memory', 'ignite', or a fully qualified class name.", e);
        } catch (Exception e) {
            throw new RuntimeException("Failed to instantiate ConfigStore: " + type, e);
        }
    }
}
