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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility for merging configuration overrides with existing Tika JSON configuration.
 * <p>
 * This class centralizes the config generation logic that was previously duplicated in:
 * <ul>
 *   <li>PipesForkParser.generateJsonConfig()</li>
 *   <li>TikaServerProcess.createDefaultConfig() and ensureServerComponents()</li>
 *   <li>TikaAsyncCLI.PluginsWriter and ensurePluginRoots()</li>
 * </ul>
 * <p>
 * Key design decisions:
 * <ul>
 *   <li>Uses UUID-based names for internal fetchers/emitters to avoid conflicts with
 *       user-configured components</li>
 *   <li>Returns a MergeResult containing the config path and generated names so callers
 *       can use them</li>
 *   <li>Preserves existing config sections when merging</li>
 *   <li>Creates temp files that are marked for deletion on JVM exit</li>
 * </ul>
 * <p>
 * Example usage:
 * <pre>
 * ConfigOverrides overrides = ConfigOverrides.builder()
 *     .addFetcher("my-fetcher", "file-system-fetcher",
 *         Map.of("basePath", "/tmp/input"))
 *     .setPipesConfig(4, 60000, null)
 *     .setEmitStrategy(EmitStrategy.PASSBACK_ALL)
 *     .build();
 *
 * MergeResult result = ConfigMerger.mergeOrCreate(existingConfigPath, overrides);
 * // Use result.configPath() for PipesParser.load()
 * </pre>
 */
public class ConfigMerger {

    private static final Logger LOG = LoggerFactory.getLogger(ConfigMerger.class);

    private ConfigMerger() {
        // Utility class
    }

    /**
     * Merges overrides with an existing config, or creates a new config if none exists.
     * <p>
     * For fetchers and emitters without explicit IDs in the overrides, UUID-based names
     * are generated to avoid conflicts with user-configured components.
     *
     * @param existingConfig path to existing config (may be null)
     * @param overrides the overrides to apply
     * @return MergeResult containing path to merged config and generated fetcher ID
     * @throws IOException if file operations fail
     */
    public static MergeResult mergeOrCreate(Path existingConfig, ConfigOverrides overrides)
            throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);

        ObjectNode root;
        if (existingConfig != null && Files.exists(existingConfig)) {
            JsonNode parsed = mapper.readTree(existingConfig.toFile());
            if (parsed instanceof ObjectNode) {
                root = (ObjectNode) parsed;
            } else {
                root = mapper.createObjectNode();
            }
            LOG.debug("Merging with existing config: {}", existingConfig);
        } else {
            root = mapper.createObjectNode();
            LOG.debug("Creating new config (no existing config provided)");
        }

        // Generate UUID for internal components
        String uuid = UUID.randomUUID().toString();

        // Track generated fetcher/emitter IDs
        List<String> generatedFetcherIds = new ArrayList<>();
        List<String> generatedEmitterIds = new ArrayList<>();

        // Apply fetcher overrides
        if (overrides.getFetchers() != null && !overrides.getFetchers().isEmpty()) {
            ObjectNode fetchersNode = getOrCreateObject(mapper, root, "fetchers");
            for (ConfigOverrides.FetcherOverride fetcher : overrides.getFetchers()) {
                String fetcherId = fetcher.getId();
                if (fetcherId == null || fetcherId.isEmpty()) {
                    fetcherId = "tika-internal-fetcher-" + uuid;
                }
                generatedFetcherIds.add(fetcherId);

                ObjectNode fetcherNode = getOrCreateObject(mapper, fetchersNode, fetcherId);
                ObjectNode typeNode = getOrCreateObject(mapper, fetcherNode, fetcher.getType());
                applyConfigMap(typeNode, fetcher.getConfig());

                LOG.debug("Added/updated fetcher: {} (type: {})", fetcherId, fetcher.getType());
            }
        }

        // Apply emitter overrides
        if (overrides.getEmitters() != null && !overrides.getEmitters().isEmpty()) {
            ObjectNode emittersNode = getOrCreateObject(mapper, root, "emitters");
            for (ConfigOverrides.EmitterOverride emitter : overrides.getEmitters()) {
                String emitterId = emitter.getId();
                if (emitterId == null || emitterId.isEmpty()) {
                    emitterId = "tika-internal-emitter-" + uuid;
                }
                generatedEmitterIds.add(emitterId);

                ObjectNode emitterNode = getOrCreateObject(mapper, emittersNode, emitterId);
                ObjectNode typeNode = getOrCreateObject(mapper, emitterNode, emitter.getType());
                applyConfigMap(typeNode, emitter.getConfig());

                LOG.debug("Added/updated emitter: {} (type: {})", emitterId, emitter.getType());
            }
        }

        // Apply pipes config overrides
        if (overrides.getPipesConfig() != null) {
            ObjectNode pipesNode = getOrCreateObject(mapper, root, "pipes");
            ConfigOverrides.PipesConfigOverride pc = overrides.getPipesConfig();

            if (pc.getNumClients() > 0) {
                pipesNode.put("numClients", pc.getNumClients());
            }
            if (pc.getTimeoutMillis() > 0) {
                pipesNode.put("timeoutMillis", pc.getTimeoutMillis());
            }
            if (pc.getStartupTimeoutMillis() > 0) {
                pipesNode.put("startupTimeoutMillis", pc.getStartupTimeoutMillis());
            }
            if (pc.getMaxFilesProcessedPerProcess() > 0) {
                pipesNode.put("maxFilesProcessedPerProcess", pc.getMaxFilesProcessedPerProcess());
            }

            // Apply forked JVM args
            List<String> jvmArgs = pc.getForkedJvmArgs();
            if (jvmArgs != null && !jvmArgs.isEmpty()) {
                ArrayNode argsArray = mapper.createArrayNode();
                for (String arg : jvmArgs) {
                    argsArray.add(arg);
                }
                pipesNode.set("forkedJvmArgs", argsArray);
            }

            LOG.debug("Applied pipes config: numClients={}, timeoutMillis={}",
                    pc.getNumClients(), pc.getTimeoutMillis());
        }

        // Apply emit strategy
        if (overrides.getEmitStrategy() != null) {
            ObjectNode pipesNode = getOrCreateObject(mapper, root, "pipes");
            ObjectNode emitStrategyNode = getOrCreateObject(mapper, pipesNode, "emitStrategy");
            emitStrategyNode.put("type", overrides.getEmitStrategy().name());
            LOG.debug("Applied emit strategy: {}", overrides.getEmitStrategy());
        }

        // Apply plugin roots if not already set
        if (overrides.getPluginRoots() != null && !root.has("plugin-roots")) {
            root.put("plugin-roots", overrides.getPluginRoots());
            LOG.debug("Set plugin-roots: {}", overrides.getPluginRoots());
        }

        // Write merged config to temp file
        Path tempConfig = Files.createTempFile("tika-config-merged-", ".json");
        mapper.writerWithDefaultPrettyPrinter().writeValue(tempConfig.toFile(), root);
        tempConfig.toFile().deleteOnExit();

        LOG.debug("Created merged config: {}", tempConfig);

        // Return the first generated fetcher/emitter ID (or null if none)
        String primaryFetcherId = generatedFetcherIds.isEmpty() ? null : generatedFetcherIds.get(0);
        String primaryEmitterId = generatedEmitterIds.isEmpty() ? null : generatedEmitterIds.get(0);

        return new MergeResult(tempConfig, primaryFetcherId, primaryEmitterId);
    }

    /**
     * Gets or creates an ObjectNode child of the parent.
     */
    private static ObjectNode getOrCreateObject(ObjectMapper mapper, ObjectNode parent, String key) {
        if (parent.has(key) && parent.get(key).isObject()) {
            return (ObjectNode) parent.get(key);
        }
        ObjectNode child = mapper.createObjectNode();
        parent.set(key, child);
        return child;
    }

    /**
     * Applies a configuration map to an ObjectNode.
     */
    private static void applyConfigMap(ObjectNode node, Map<String, Object> config) {
        if (config == null) {
            return;
        }
        for (Map.Entry<String, Object> entry : config.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof String) {
                node.put(entry.getKey(), (String) value);
            } else if (value instanceof Boolean) {
                node.put(entry.getKey(), (Boolean) value);
            } else if (value instanceof Integer) {
                node.put(entry.getKey(), (Integer) value);
            } else if (value instanceof Long) {
                node.put(entry.getKey(), (Long) value);
            } else if (value instanceof Double) {
                node.put(entry.getKey(), (Double) value);
            } else if (value != null) {
                node.put(entry.getKey(), value.toString());
            }
        }
    }

    /**
     * Result of a config merge operation.
     *
     * @param configPath path to the merged configuration file
     * @param fetcherId the primary generated fetcher ID (may be null if no fetchers were added)
     * @param emitterId the primary generated emitter ID (may be null if no emitters were added)
     */
    public record MergeResult(Path configPath, String fetcherId, String emitterId) {
    }
}
