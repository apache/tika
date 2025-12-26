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
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.pf4j.DefaultExtensionFinder;
import org.pf4j.DefaultPluginManager;
import org.pf4j.ExtensionFinder;
import org.pf4j.RuntimeMode;
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
    
    private static final String DEV_MODE_PROPERTY = "tika.plugin.dev.mode";
    private static final String DEV_MODE_ENV = "TIKA_PLUGIN_DEV_MODE";

    //we're only using this to convert a single path or a list of paths to a list
    //we don't need all the functionality of the polymorphic objectmapper in tika-serialization
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    static {
        OBJECT_MAPPER.configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true);
    }

    /**
     * Pre-extracts plugin zip files without loading them.
     * <p>
     * Call this method early in parent processes (e.g., AsyncProcessor, PipesParser)
     * before spawning child processes. This ensures plugins are extracted once in
     * the parent, so child processes don't race to extract the same plugins.
     * <p>
     * This method is synchronized to prevent concurrent extraction within the same JVM.
     * For cross-process safety, {@link ThreadSafeUnzipper} uses atomic rename.
     * <p>
     * If plugin-roots is not specified in the config, this method does nothing.
     *
     * @param tikaJsonConfig the configuration containing plugin-roots
     * @throws IOException if extraction fails
     */
    public static synchronized void preExtractPlugins(TikaJsonConfig tikaJsonConfig)
            throws IOException {
        JsonNode root = tikaJsonConfig.getRootNode();
        JsonNode pluginRoots = root.get("plugin-roots");
        if (pluginRoots == null) {
            // No plugins configured - nothing to extract
            return;
        }
        List<Path> roots = OBJECT_MAPPER.convertValue(pluginRoots,
                new TypeReference<List<Path>>() {});
        for (Path pluginRoot : roots) {
            extractPluginsInDirectory(pluginRoot);
        }
    }

    private static void extractPluginsInDirectory(Path root) throws IOException {
        if (!Files.isDirectory(root)) {
            return;
        }
        long start = System.currentTimeMillis();
        File[] files = root.toFile().listFiles();
        if (files == null) {
            return;
        }
        for (File f : files) {
            if (f.getName().endsWith(".zip")) {
                ThreadSafeUnzipper.unzipPlugin(f.toPath());
            }
        }
        LOG.debug("took {} ms to pre-extract plugins in {}",
                System.currentTimeMillis() - start, root);
    }

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

        // Configure pf4j runtime mode before creating the manager
        configurePf4jRuntimeMode();
        
        JsonNode root = tikaJsonConfig.getRootNode();
        JsonNode pluginRoots = root.get("plugin-roots");
        if (pluginRoots == null) {
            throw new TikaConfigException("plugin-roots must be specified");
        }
        List<Path> roots = OBJECT_MAPPER.convertValue(pluginRoots,
                new TypeReference<List<Path>>() {});
        if (roots.isEmpty()) {
            throw new TikaConfigException("plugin-roots must not be empty");
        }
        return new TikaPluginManager(roots);
    }

    /**
     * Loads plugin manager from a configuration file.
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

    public TikaPluginManager(List<Path> pluginRoots) throws IOException {
        this(pluginRoots, true);
    }
    
    /**
     * Internal constructor that allows skipping runtime mode configuration.
     * Used by tests and factory methods that have already configured the mode.
     */
    private TikaPluginManager(List<Path> pluginRoots, boolean configureMode) throws IOException {
        super(configureMode ? configurePf4jRuntimeModeAndGetRoots(pluginRoots) : pluginRoots);
        
        if (getRuntimeMode() == RuntimeMode.DEVELOPMENT) {
            LOG.info("TikaPluginManager running in DEVELOPMENT mode");
        }
        
        init();
    }
    
    /**
     * Helper method to configure PF4J runtime mode and return the plugin roots.
     * This allows mode configuration before super() is called.
     */
    private static List<Path> configurePf4jRuntimeModeAndGetRoots(List<Path> pluginRoots) {
        configurePf4jRuntimeMode();
        return pluginRoots;
    }
    
    /**
     * Set pf4j's runtime mode system property based on Tika's dev mode setting.
     * This must be called before creating TikaPluginManager instance.
     */
    private static void configurePf4jRuntimeMode() {
        if (isDevelopmentMode()) {
            System.setProperty("pf4j.mode", RuntimeMode.DEVELOPMENT.toString());
        } else {
            // Explicitly set to deployment mode to ensure clean state
            System.setProperty("pf4j.mode", RuntimeMode.DEPLOYMENT.toString());
        }
    }
    
    private static boolean isDevelopmentMode() {
        String sysProp = System.getProperty(DEV_MODE_PROPERTY);
        if (sysProp != null) {
            return Boolean.parseBoolean(sysProp);
        }
        String envVar = System.getenv(DEV_MODE_ENV);
        if (envVar != null) {
            return Boolean.parseBoolean(envVar);
        }
        return false;
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
    
    /**
     * Override to prevent scanning subdirectories in development mode.
     * In development mode, the default DevelopmentPluginRepository scans for subdirectories,
     * but we want each path in plugin-roots to be treated as a complete plugin directory.
     */
    @Override
    protected org.pf4j.PluginRepository createPluginRepository() {
        if (getRuntimeMode() == RuntimeMode.DEVELOPMENT) {
            // In development mode, return a repository that treats each path as a plugin
            return new org.pf4j.BasePluginRepository(getPluginsRoots()) {
                @Override
                public List<Path> getPluginPaths() {
                    // Don't scan subdirectories - each configured path IS a plugin
                    return new java.util.ArrayList<>(pluginsRoots);
                }
            };
        }
        return super.createPluginRepository();
    }
    
    /**
     * Override to use PropertiesPluginDescriptorFinder in development mode.
     * In development mode, plugins are in target/classes with plugin.properties,
     * not packaged JARs with META-INF/MANIFEST.MF.
     */
    @Override
    protected org.pf4j.PluginDescriptorFinder createPluginDescriptorFinder() {
        if (getRuntimeMode() == RuntimeMode.DEVELOPMENT) {
            return new org.pf4j.PropertiesPluginDescriptorFinder();
        }
        return super.createPluginDescriptorFinder();
    }

    private void init() throws IOException {
        if (getRuntimeMode() == RuntimeMode.DEPLOYMENT) {
            for (Path root : pluginsRoots) {
                unzip(root);
            }
        } else {
            LOG.debug("Skipping ZIP extraction in DEVELOPMENT mode");
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
