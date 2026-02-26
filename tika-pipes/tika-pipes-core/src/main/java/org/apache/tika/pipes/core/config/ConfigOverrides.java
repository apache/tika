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

    private ConfigOverrides(Builder builder) {
        this.fetchers = Collections.unmodifiableList(new ArrayList<>(builder.fetchers));
        this.emitters = Collections.unmodifiableList(new ArrayList<>(builder.emitters));
        this.pipesConfig = builder.pipesConfig;
        this.pluginRoots = builder.pluginRoots;
        this.emitStrategy = builder.emitStrategy;
        this.timeoutLimits = builder.timeoutLimits;
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
        private final long startupTimeoutMillis;
        private final int maxFilesProcessedPerProcess;
        private final List<String> forkedJvmArgs;

        public PipesConfigOverride(int numClients,
                                   long startupTimeoutMillis, int maxFilesProcessedPerProcess,
                                   List<String> forkedJvmArgs) {
            this.numClients = numClients;
            this.startupTimeoutMillis = startupTimeoutMillis;
            this.maxFilesProcessedPerProcess = maxFilesProcessedPerProcess;
            this.forkedJvmArgs = forkedJvmArgs != null ?
                    new ArrayList<>(forkedJvmArgs) : new ArrayList<>();
        }

        public int getNumClients() {
            return numClients;
        }

        public long getStartupTimeoutMillis() {
            return startupTimeoutMillis;
        }

        public int getMaxFilesProcessedPerProcess() {
            return maxFilesProcessedPerProcess;
        }

        public List<String> getForkedJvmArgs() {
            return forkedJvmArgs;
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

        private Builder() {
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
                    org.apache.tika.pipes.core.PipesConfig.DEFAULT_STARTUP_TIMEOUT_MILLIS,
                    org.apache.tika.pipes.core.PipesConfig.DEFAULT_MAX_FILES_PROCESSED_PER_PROCESS,
                    forkedJvmArgs);
        }

        /**
         * Set pipes configuration with all options.
         *
         * @param numClients number of forked JVM clients
         * @param startupTimeoutMillis startup timeout in milliseconds
         * @param maxFilesProcessedPerProcess max files before process restart
         * @param forkedJvmArgs JVM arguments for forked processes (may be null)
         * @return this builder
         */
        public Builder setPipesConfig(int numClients,
                                      long startupTimeoutMillis, int maxFilesProcessedPerProcess,
                                      List<String> forkedJvmArgs) {
            this.pipesConfig = new PipesConfigOverride(numClients,
                    startupTimeoutMillis, maxFilesProcessedPerProcess, forkedJvmArgs);
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
