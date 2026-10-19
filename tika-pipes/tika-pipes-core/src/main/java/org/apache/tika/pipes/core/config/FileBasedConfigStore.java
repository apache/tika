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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.plugins.ExtensionConfig;

/**
 * File-based implementation of {@link ConfigStore} that persists configurations to a JSON file.
 * This allows multiple JVM processes to share configuration through the filesystem.
 * Thread-safe and suitable for multi-process deployments where PipesClient forks PipesServer.
 */
public class FileBasedConfigStore implements ConfigStore {
    
    private static final Logger LOG = LoggerFactory.getLogger(FileBasedConfigStore.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    
    private final Path configFile;
    private final ConcurrentHashMap<String, ExtensionConfig> cache = new ConcurrentHashMap<>();
    private ExtensionConfig extensionConfig;
    
    public FileBasedConfigStore(Path configFile) {
        this.configFile = configFile;
    }

    @Override
    public ExtensionConfig getExtensionConfig() {
        return extensionConfig;
    }

    public void setExtensionConfig(ExtensionConfig extensionConfig) {
        this.extensionConfig = extensionConfig;
    }

    @Override
    public void init() throws Exception {
        // Create parent directories if they don't exist
        if (configFile.getParent() != null) {
            Files.createDirectories(configFile.getParent());
        }
        
        // Load existing configs if file exists
        if (Files.exists(configFile)) {
            loadFromFile();
            LOG.info("Loaded {} configurations from {}", cache.size(), configFile);
        } else {
            LOG.info("Config file does not exist yet, will be created on first save: {}", configFile);
        }
    }

    @Override
    public synchronized void put(String id, ExtensionConfig config) {
        cache.put(id, config);
        saveToFile();
    }

    @Override
    public ExtensionConfig get(String id) {
        // Reload from file to get latest changes from other processes
        try {
            loadFromFile();
        } catch (IOException e) {
            LOG.warn("Failed to reload config from file, using cache", e);
        }
        return cache.get(id);
    }

    @Override
    public boolean containsKey(String id) {
        try {
            loadFromFile();
        } catch (IOException e) {
            LOG.warn("Failed to reload config from file, using cache", e);
        }
        return cache.containsKey(id);
    }

    @Override
    public Set<String> keySet() {
        try {
            loadFromFile();
        } catch (IOException e) {
            LOG.warn("Failed to reload config from file, using cache", e);
        }
        return Set.copyOf(cache.keySet());
    }

    @Override
    public int size() {
        try {
            loadFromFile();
        } catch (IOException e) {
            LOG.warn("Failed to reload config from file, using cache", e);
        }
        return cache.size();
    }

    @Override
    public synchronized ExtensionConfig remove(String id) {
        ExtensionConfig removed = cache.remove(id);
        if (removed != null) {
            saveToFile();
        }
        return removed;
    }

    private synchronized void loadFromFile() throws IOException {
        if (!Files.exists(configFile)) {
            return;
        }
        
        try {
            Map<String, ExtensionConfig> loaded = OBJECT_MAPPER.readValue(
                configFile.toFile(),
                new TypeReference<Map<String, ExtensionConfig>>() {}
            );
            cache.clear();
            cache.putAll(loaded);
        } catch (IOException e) {
            LOG.error("Failed to load configurations from {}", configFile, e);
            throw e;
        }
    }

    private synchronized void saveToFile() {
        try {
            // Write to temp file first, then atomic rename
            Path tempFile = Paths.get(configFile.toString() + ".tmp");
            OBJECT_MAPPER.writerWithDefaultPrettyPrinter()
                .writeValue(tempFile.toFile(), cache);
            Files.move(tempFile, configFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            LOG.debug("Saved {} configurations to {}", cache.size(), configFile);
        } catch (IOException e) {
            LOG.error("Failed to save configurations to {}", configFile, e);
            throw new RuntimeException("Failed to save config store", e);
        }
    }
}
