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

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.pf4j.PluginManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.plugins.ExtensionConfig;
import org.apache.tika.plugins.TikaExtensionFactory;

/**
 * Factory interface for creating ConfigStore instances.
 * Implementations should be annotated with @Extension to be discovered by PF4J.
 */
public interface ConfigStoreFactory extends TikaExtensionFactory<ConfigStore> {
    
    Logger LOG = LoggerFactory.getLogger(ConfigStoreFactory.class);

    /**
     * Creates a ConfigStore instance based on configuration.
     *
     * @param pluginManager the plugin manager
     * @param configStoreType the type of ConfigStore to create
     * @param extensionConfig optional configuration for the store
     * @return a ConfigStore instance
     * @throws TikaConfigException if the store cannot be created
     */
    static ConfigStore createConfigStore(PluginManager pluginManager, String configStoreType, 
                                         ExtensionConfig extensionConfig) 
            throws TikaConfigException {
        if (configStoreType == null || configStoreType.isEmpty() || "memory".equalsIgnoreCase(configStoreType)) {
            LOG.info("Creating InMemoryConfigStore");
            InMemoryConfigStore store = new InMemoryConfigStore();
            if (extensionConfig != null) {
                store.setExtensionConfig(extensionConfig);
            }
            return store;
        }
        
        Map<String, ConfigStoreFactory> factoryMap = loadAllConfigStoreFactoryExtensions(pluginManager);

        ConfigStoreFactory factory = factoryMap.get(configStoreType);
        if (factory != null) {
            return configStoreByConfigByFactoryName(configStoreType, extensionConfig, factory);
        }
        return configStoreByFullyQualifiedClassName(configStoreType, extensionConfig, factoryMap);
    }

    private static ConfigStore configStoreByConfigByFactoryName(String configStoreType, ExtensionConfig extensionConfig, ConfigStoreFactory factory) throws TikaConfigException {
        LOG.info("Creating ConfigStore using factory: {}", factory.getName());
        try {
            ExtensionConfig config = extensionConfig != null ? extensionConfig :
                new ExtensionConfig(configStoreType, configStoreType, "{}");
            return factory.buildExtension(config);
        } catch (IOException e) {
            throw new TikaConfigException("Failed to create ConfigStore: " + configStoreType, e);
        }
    }

    private static ConfigStore configStoreByFullyQualifiedClassName(String configStoreType,
            ExtensionConfig extensionConfig,
            Map<String, ConfigStoreFactory> factoryMap) throws TikaConfigException {
        try {
            LOG.info("Creating ConfigStore from class: {}", configStoreType);
            Class<?> storeClass = Class.forName(configStoreType);
            if (!ConfigStore.class.isAssignableFrom(storeClass)) {
                throw new TikaConfigException(
                    "Class " + configStoreType + " does not implement ConfigStore interface");
            }
            ConfigStore store = (ConfigStore) storeClass.getDeclaredConstructor().newInstance();
            if (extensionConfig != null) {
                ((InMemoryConfigStore) store).setExtensionConfig(extensionConfig);
            }
            return store;
        } catch (ClassNotFoundException e) {
            throw new TikaConfigException(
                "Unknown ConfigStore type: " + configStoreType +
                ". Available types: memory, " + String.join(", ", factoryMap.keySet()), e);
        } catch (Exception e) {
            throw new TikaConfigException("Failed to instantiate ConfigStore: " + configStoreType, e);
        }
    }

    private static Map<String, ConfigStoreFactory> loadAllConfigStoreFactoryExtensions(PluginManager pluginManager) {
        List<ConfigStoreFactory> factories = pluginManager.getExtensions(ConfigStoreFactory.class);
        Map<String, ConfigStoreFactory> factoryMap = new HashMap<>();
        for (ConfigStoreFactory factory : factories) {
            factoryMap.put(factory.getName(), factory);
        }
        return factoryMap;
    }
}
