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
package org.apache.tika.plugins;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import org.pf4j.DefaultExtensionFinder;
import org.pf4j.DefaultPluginManager;
import org.pf4j.ExtensionFinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.config.loader.TikaJsonConfig;
import org.apache.tika.exception.TikaConfigException;

/**
 * PF4J-based plugin manager for Tika pipes components.
 * <p>
 * This manager loads plugins from configured plugin root directories and
 * makes their extensions available for discovery.
 */
public class TikaPluginManager extends DefaultPluginManager {

    private static final Logger LOG = LoggerFactory.getLogger(TikaPluginManager.class);

    /**
     * Loads plugin manager from a pre-parsed TikaJsonConfig.
     * This is the preferred method when sharing configuration across
     * core Tika and pipes components.
     *
     * @param tikaJsonConfig the pre-parsed JSON configuration
     * @return the plugin manager
     * @throws TikaConfigException if configuration is invalid
     * @throws IOException if plugin initialization fails
     */
    public static TikaPluginManager load(TikaJsonConfig tikaJsonConfig)
            throws TikaConfigException, IOException {
        TikaConfigs tikaConfigs = TikaConfigs.load(tikaJsonConfig);
        return load(tikaConfigs);
    }

    /**
     * Loads plugin manager from a configuration file.
     * For backwards compatibility - prefer {@link #load(TikaJsonConfig)} when possible.
     *
     * @param configPath the path to the JSON configuration file
     * @return the plugin manager
     * @throws TikaConfigException if configuration is invalid
     * @throws IOException if reading or plugin initialization fails
     */
    public static TikaPluginManager load(Path configPath) throws TikaConfigException, IOException {
        TikaJsonConfig tikaJsonConfig = TikaJsonConfig.load(configPath);
        return load(tikaJsonConfig);
    }

    /**
     * Loads plugin manager from a TikaConfigs instance.
     *
     * @param tikaConfigs the pipes configuration
     * @return the plugin manager
     * @throws TikaConfigException if configuration is invalid
     * @throws IOException if plugin initialization fails
     */
    public static TikaPluginManager load(TikaConfigs tikaConfigs)
            throws TikaConfigException, IOException {
        JsonNode root = tikaConfigs.getRoot();
        JsonNode pluginRoots = root.get("plugin-roots");
        if (pluginRoots == null) {
            throw new TikaConfigException("plugin-roots must be specified");
        }
        List<Path> roots = TikaConfigs.OBJECT_MAPPER.convertValue(pluginRoots,
                new TypeReference<List<Path>>() {});
        if (roots.isEmpty()) {
            throw new TikaConfigException("plugin-roots must not be empty");
        }
        return new TikaPluginManager(roots);
    }

    public TikaPluginManager(List<Path> pluginRoots) throws IOException {
        super(pluginRoots);
        init();
    }

    /**
     * Override to disable classpath scanning for extensions.
     * By default, PF4J's DefaultExtensionFinder scans both plugins AND the classpath:
     * - LegacyExtensionFinder scans for extensions.idx files (causes errors for unpackaged JARs)
     * - ServiceProviderExtensionFinder scans META-INF/services (finds Lombok and other libs)
     *
     * We only want to discover extensions from the configured plugin directories,
     * not from the application classpath. The DefaultExtensionFinder without any
     * additional finders will only scan the loaded plugins.
     */
    @Override
    protected ExtensionFinder createExtensionFinder() {
        // Return a DefaultExtensionFinder without any classpath-scanning finders.
        // This will only discover extensions within the loaded plugin JARs.
        return new DefaultExtensionFinder(this);
    }

    private void init() throws IOException {
        for (Path root : pluginsRoots) {
            unzip(root);
        }
    }

    private void unzip(Path root) throws IOException {
        long start = System.currentTimeMillis();
        if (!Files.isDirectory(root)) {
            return;
        }

        for (File f : root
                .toFile()
                .listFiles()) {
            if (f
                    .getName()
                    .endsWith(".zip")) {
                ThreadSafeUnzipper.unzipPlugin(f.toPath());
            }
        }
        LOG.debug("took {} ms to unzip/check for unzipped plugins", System.currentTimeMillis() - start);
    }
}
