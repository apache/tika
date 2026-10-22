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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.tika.config.EmbeddedLimits;
import org.apache.tika.config.TimeoutLimits;
import org.apache.tika.pipes.core.EmitStrategy;

/**
 * Configuration overrides for merging with or creating Tika JSON configuration.
 * <p>
 * This class provides a fluent builder API to specify fetchers, emitters, pipes
 * configuration, and other settings that should be merged into an existing config
 * or used to create a new one.
 * <p>
 * Example usage:
 * <pre>
 * ConfigOverrides overrides = ConfigOverrides.builder()
 *     .addFetcher("my-fetcher", "file-system-fetcher",
 *         Map.of("basePath", "/tmp/input"))
 *     .setPipesConfig(4, null)
 *     .setEmitStrategy(EmitStrategy.PASSBACK_ALL)
 *     .setPluginRoots("plugins")
 *     .build();
 * </pre>
 */
public class ConfigOverrides {

    private final List<FetcherOverride> fetchers;
    private final List<EmitterOverride> emitters;
    private final PipesConfigOverride pipesConfig;
    private final String pluginRoots;
    private final EmitStrategy emitStrategy;
    private final TimeoutLimits timeoutLimits;
    private final EmbeddedLimits embeddedLimits;
    private final Integer writeLimit;
    private final boolean classpathPlugins;
    private final boolean trustedRequests;
    private final List<String> presets;

    private ConfigOverrides(Builder builder) {
        this.fetchers = Collections.unmodifiableList(new ArrayList<>(builder.fetchers));
        this.emitters = Collections.unmodifiableList(new ArrayList<>(builder.emitters));
        this.pipesConfig = builder.pipesConfig;
        this.pluginRoots = builder.pluginRoots;
        this.emitStrategy = builder.emitStrategy;
        this.timeoutLimits = builder.timeoutLimits;
        this.embeddedLimits = builder.embeddedLimits;
        this.writeLimit = builder.writeLimit;
        this.classpathPlugins = builder.classpathPlugins;
        this.trustedRequests = builder.trustedRequests;
        this.presets = Collections.unmodifiableList(new ArrayList<>(builder.presets));
    }

    public static Builder builder() {
        return new Builder();
    }

    public List<FetcherOverride> getFetchers() {
        return fetchers;
    }

    public List<EmitterOverride> getEmitters() {
        return emitters;
    }

    public PipesConfigOverride getPipesConfig() {
        return pipesConfig;
    }

    public String getPluginRoots() {
        return pluginRoots;
    }

    public EmitStrategy getEmitStrategy() {
        return emitStrategy;
    }

    public TimeoutLimits getTimeoutLimits() {
        return timeoutLimits;
    }

    public EmbeddedLimits getEmbeddedLimits() {
        return embeddedLimits;
    }

    public Integer getWriteLimit() {
        return writeLimit;
    }

    /** Whether the fork is told to load plugins from its classpath; see ConfigMerger. */
    public boolean isClasspathPlugins() {
        return classpathPlugins;
    }

    /** Whether the fork is told its requests are the operator's own; see ConfigMerger. */
    public boolean isTrustedRequests() {
        return trustedRequests;
    }

    /** Catalog presets to activate, as {@code "name": true} would; see ConfigMerger. */
    public List<String> getPresets() {
        return presets;
    }

    /**
     * Represents a fetcher configuration override.
     */
    public static class FetcherOverride {
        private final String id;
        private final String type;
        private final Map<String, Object> config;

        public FetcherOverride(String id, String type, Map<String, Object> config) {
            this.id = id;
            this.type = type;
            this.config = config != null ? new HashMap<>(config) : new HashMap<>();
        }

        public String getId() {
            return id;
        }

        public String getType() {
            return type;
        }

        public Map<String, Object> getConfig() {
            return config;
        }
    }

    /**
     * Represents an emitter configuration override.
     */
    public static class EmitterOverride {
        private final String id;
        private final String type;
        private final Map<String, Object> config;

        public EmitterOverride(String id, String type, Map<String, Object> config) {
            this.id = id;
            this.type = type;
            this.config = config != null ? new HashMap<>(config) : new HashMap<>();
        }

        public String getId() {
            return id;
        }

        public String getType() {
            return type;
        }

        public Map<String, Object> getConfig() {
            return config;
        }
    }

    /**
     * Represents pipes configuration overrides.
     */
    public static class PipesConfigOverride {
        private final int numClients;
        private final int maxFilesProcessedPerProcess;
        private final List<String> forkedJvmArgs;
        private final long socketTimeoutMillis;
        private final String javaPath;

        public PipesConfigOverride(int numClients,
                                   int maxFilesProcessedPerProcess,
                                   List<String> forkedJvmArgs) {
            this(numClients, maxFilesProcessedPerProcess, forkedJvmArgs, -1, null);
        }

        /**
         * @param socketTimeoutMillis written when positive; -1 leaves the existing config's value
         * @param javaPath written when non-null; null leaves the existing config's value
         */
        public PipesConfigOverride(int numClients,
                                   int maxFilesProcessedPerProcess,
                                   List<String> forkedJvmArgs,
                                   long socketTimeoutMillis,
                                   String javaPath) {
            this.numClients = numClients;
            this.maxFilesProcessedPerProcess = maxFilesProcessedPerProcess;
            this.forkedJvmArgs = forkedJvmArgs != null ?
                    new ArrayList<>(forkedJvmArgs) : new ArrayList<>();
            this.socketTimeoutMillis = socketTimeoutMillis;
            this.javaPath = javaPath;
        }

        public int getNumClients() {
            return numClients;
        }

        public int getMaxFilesProcessedPerProcess() {
            return maxFilesProcessedPerProcess;
        }

        public List<String> getForkedJvmArgs() {
            return forkedJvmArgs;
        }

        public long getSocketTimeoutMillis() {
            return socketTimeoutMillis;
        }

        public String getJavaPath() {
            return javaPath;
        }
    }

    /**
     * Builder for ConfigOverrides.
     */
    public static class Builder {
        private final List<FetcherOverride> fetchers = new ArrayList<>();
        private final List<EmitterOverride> emitters = new ArrayList<>();
        private PipesConfigOverride pipesConfig;
        private String pluginRoots;
        private EmitStrategy emitStrategy;
        private TimeoutLimits timeoutLimits;
        private EmbeddedLimits embeddedLimits;
        private Integer writeLimit;
        private boolean classpathPlugins;
        private boolean trustedRequests;
        private final List<String> presets = new ArrayList<>();

        private Builder() {
        }

        /**
         * Activates a catalog preset, as {@code "presets": {"name": true}} in the config would,
         * replacing whatever value the existing config has for the name.
         */
        public Builder addPreset(String name) {
            this.presets.add(name);
            return this;
        }

        /**
         * Sets {@code pipes.trustedRequests}: the requests come from the operator, so their
         * parseContext is trusted and their limits and timeouts are used as given.
         */
        public Builder setTrustedRequests(boolean trustedRequests) {
            this.trustedRequests = trustedRequests;
            return this;
        }

        /**
         * Appends {@code -Dtika.plugins.classpath=true} to the fork's JVM args, keeping whatever
         * forkedJvmArgs the existing config or {@link #setPipesConfig} already carry.
         */
        public Builder setClasspathPlugins(boolean classpathPlugins) {
            this.classpathPlugins = classpathPlugins;
            return this;
        }

        /**
         * Add a fetcher configuration.
         *
         * @param id the fetcher ID
         * @param type the fetcher type (e.g., "file-system-fetcher")
         * @param config the fetcher configuration properties
         * @return this builder
         */
        public Builder addFetcher(String id, String type, Map<String, Object> config) {
            fetchers.add(new FetcherOverride(id, type, config));
            return this;
        }

        /**
         * Add an emitter configuration.
         *
         * @param id the emitter ID
         * @param type the emitter type (e.g., "file-system-emitter")
         * @param config the emitter configuration properties
         * @return this builder
         */
        public Builder addEmitter(String id, String type, Map<String, Object> config) {
            emitters.add(new EmitterOverride(id, type, config));
            return this;
        }

        /**
         * Set pipes configuration with basic options.
         *
         * @param numClients number of forked JVM clients
         * @param forkedJvmArgs JVM arguments for forked processes (may be null)
         * @return this builder
         */
        public Builder setPipesConfig(int numClients,
                                      List<String> forkedJvmArgs) {
            return setPipesConfig(numClients,
                    org.apache.tika.pipes.core.PipesConfig.DEFAULT_MAX_FILES_PROCESSED_PER_PROCESS,
                    forkedJvmArgs);
        }

        /**
         * Set pipes configuration with all options.
         *
         * @param numClients number of forked JVM clients
         * @param maxFilesProcessedPerProcess max files before process restart
         * @param forkedJvmArgs JVM arguments for forked processes (may be null)
         * @return this builder
         */
        public Builder setPipesConfig(int numClients,
                                      int maxFilesProcessedPerProcess,
                                      List<String> forkedJvmArgs) {
            return setPipesConfig(numClients, maxFilesProcessedPerProcess, forkedJvmArgs,
                    -1, null);
        }

        /**
         * Set pipes configuration including the parent-side process settings.
         *
         * @param socketTimeoutMillis socket read timeout for the forked process, -1 to leave
         *                            the existing config's value
         * @param javaPath java executable for the forked process, null to leave the existing
         *                 config's value
         * @return this builder
         */
        public Builder setPipesConfig(int numClients,
                                      int maxFilesProcessedPerProcess,
                                      List<String> forkedJvmArgs,
                                      long socketTimeoutMillis,
                                      String javaPath) {
            this.pipesConfig = new PipesConfigOverride(numClients,
                    maxFilesProcessedPerProcess, forkedJvmArgs, socketTimeoutMillis, javaPath);
            return this;
        }

        /**
         * Set the timeout limits to write to the parse-context section.
         *
         * @param timeoutLimits the timeout limits (may be null to use defaults)
         * @return this builder
         */
        public Builder setTimeoutLimits(TimeoutLimits timeoutLimits) {
            this.timeoutLimits = timeoutLimits;
            return this;
        }

        /**
         * Set the embedded limits to write to the parse-context section. Config-tier limits
         * are the ceiling a request's own limits are clamped to.
         *
         * @param embeddedLimits the embedded limits (may be null to use defaults)
         * @return this builder
         */
        public Builder setEmbeddedLimits(EmbeddedLimits embeddedLimits) {
            this.embeddedLimits = embeddedLimits;
            return this;
        }

        /**
         * Set {@code output-limits.writeLimit} in the parse-context section.
         *
         * @param writeLimit the write limit, negative for unlimited (may be null to leave unset)
         * @return this builder
         */
        public Builder setWriteLimit(Integer writeLimit) {
            this.writeLimit = writeLimit;
            return this;
        }

        /**
         * Set the plugin roots path.
         *
         * @param pluginRoots path to the plugins directory
         * @return this builder
         */
        public Builder setPluginRoots(String pluginRoots) {
            this.pluginRoots = pluginRoots;
            return this;
        }

        /**
         * Set the emit strategy.
         *
         * @param emitStrategy the emit strategy
         * @return this builder
         */
        public Builder setEmitStrategy(EmitStrategy emitStrategy) {
            this.emitStrategy = emitStrategy;
            return this;
        }

        /**
         * Build the ConfigOverrides instance.
         *
         * @return the ConfigOverrides
         */
        public ConfigOverrides build() {
            return new ConfigOverrides(this);
        }
    }
}
