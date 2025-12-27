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
package org.apache.tika.config.loader;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.serialization.ComponentNameResolver;

/**
 * Base loader for components that support SPI fallback with exclusions.
 * <p>
 * Handles the common pattern for loading Parsers, Detectors, and EncodingDetectors:
 * <ol>
 *   <li>Check if config section exists</li>
 *   <li>Find "default-xxx" marker and extract exclusions</li>
 *   <li>Load explicitly configured components</li>
 *   <li>Auto-exclude configured component classes from SPI</li>
 *   <li>Create Default* composite with combined exclusions</li>
 *   <li>Post-process (e.g., inject dependencies)</li>
 * </ol>
 *
 * @param <T> the component type (Parser, Detector, EncodingDetector)
 */
public abstract class AbstractSpiComponentLoader<T> implements ComponentLoader<T> {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractSpiComponentLoader.class);

    private final String sectionName;
    private final String defaultMarkerName;
    private final Class<T> componentClass;

    /**
     * Creates a new SPI component loader.
     *
     * @param sectionName the JSON config section name (e.g., "parsers")
     * @param defaultMarkerName the default marker name (e.g., "default-parser")
     * @param componentClass the component interface class
     */
    protected AbstractSpiComponentLoader(String sectionName, String defaultMarkerName,
                                          Class<T> componentClass) {
        this.sectionName = sectionName;
        this.defaultMarkerName = defaultMarkerName;
        this.componentClass = componentClass;
    }

    @Override
    public T load(TikaJsonConfig config, LoaderContext context) throws TikaConfigException {
        if (!config.hasComponentSection(sectionName)) {
            // No config section - use full SPI default
            T defaultComposite = createDefaultComposite(Collections.emptySet(), context);
            return postProcess(defaultComposite, context);
        }

        List<Map.Entry<String, JsonNode>> entries = config.getArrayComponents(sectionName);

        if (entries.isEmpty()) {
            T defaultComposite = createDefaultComposite(Collections.emptySet(), context);
            return postProcess(defaultComposite, context);
        }

        // First pass: find default marker and its exclusions
        DefaultMarkerConfig<T> markerConfig = findDefaultMarker(entries, context);

        // Second pass: load configured components
        List<T> components = new ArrayList<>();
        Set<Class<? extends T>> configuredClasses = new HashSet<>();

        for (Map.Entry<String, JsonNode> entry : entries) {
            String name = entry.getKey();

            if (defaultMarkerName.equals(name)) {
                continue;  // Skip marker, handled separately
            }

            // Check for special handling (e.g., "mime-types" for detectors)
            T special = handleSpecialName(name, entry.getValue(), context);
            if (special != null) {
                components.add(special);
                configuredClasses.add((Class<? extends T>) special.getClass());
                continue;
            }

            T component = loadComponent(name, entry.getValue(), context);
            components.add(component);
            configuredClasses.add((Class<? extends T>) component.getClass());
        }

        // Combine exclusions: explicit from config + auto (configured classes)
        Set<Class<? extends T>> allExclusions = new HashSet<>(markerConfig.exclusions());
        allExclusions.addAll(configuredClasses);

        // Add SPI components if default marker present
        if (markerConfig.present()) {
            T defaultComposite = createDefaultComposite(allExclusions, context);
            // Allow subclasses to decorate (e.g., mime filtering for parsers)
            defaultComposite = decorateDefaultComposite(defaultComposite,
                    markerConfig.configNode(), context);

            if (components.isEmpty()) {
                return postProcess(defaultComposite, context);
            }

            // Insert at marker position to preserve ordering
            int insertIndex = Math.min(markerConfig.index(), components.size());
            components.add(insertIndex, defaultComposite);
            LOG.debug("Loading SPI {} because '{}' is in config", sectionName, defaultMarkerName);
        } else {
            LOG.debug("Skipping SPI {} - '{}' not in config", sectionName, defaultMarkerName);
        }

        // Post-process all components (e.g., inject dependencies)
        components = postProcessList(components, context);

        return wrapInComposite(components, context);
    }

    // ==================== Abstract methods for subclasses ====================

    /**
     * Load a single component from config.
     * Subclasses can apply decorations (e.g., mime filtering for parsers).
     *
     * @param name the component name (friendly name or FQCN)
     * @param configNode the JSON configuration for this component
     * @param context the loader context
     * @return the loaded component
     * @throws TikaConfigException if loading fails
     */
    protected abstract T loadComponent(String name, JsonNode configNode,
                                        LoaderContext context) throws TikaConfigException;

    /**
     * Create the SPI-backed default composite with exclusions.
     * E.g., new DefaultParser(..., exclusions) or new DefaultDetector(..., exclusions)
     *
     * @param exclusions classes to exclude from SPI loading
     * @param context the loader context
     * @return the default composite
     */
    protected abstract T createDefaultComposite(Set<Class<? extends T>> exclusions,
                                                 LoaderContext context);

    /**
     * Wrap a list of components in a composite.
     * E.g., new CompositeParser(registry, list) or new CompositeDetector(registry, list)
     *
     * @param components the list of components
     * @param context the loader context
     * @return the composite component
     */
    protected abstract T wrapInComposite(List<T> components, LoaderContext context);

    // ==================== Optional hooks for subclasses ====================

    /**
     * Post-process a single component (e.g., inject dependencies).
     * Default: returns the component unchanged.
     *
     * @param component the component to post-process
     * @param context the loader context
     * @return the post-processed component
     * @throws TikaConfigException if post-processing fails
     */
    protected T postProcess(T component, LoaderContext context) throws TikaConfigException {
        return component;
    }

    /**
     * Post-process a list of components (e.g., inject dependencies).
     * Default: calls postProcess on each component.
     *
     * @param components the components to post-process
     * @param context the loader context
     * @return the post-processed components
     * @throws TikaConfigException if post-processing fails
     */
    protected List<T> postProcessList(List<T> components, LoaderContext context)
            throws TikaConfigException {
        List<T> result = new ArrayList<>();
        for (T component : components) {
            result.add(postProcess(component, context));
        }
        return result;
    }

    /**
     * Handle special component names that require custom loading.
     * E.g., "mime-types" for detectors returns TikaLoader.getMimeTypes().
     * Return null for normal handling.
     *
     * @param name the component name
     * @param configNode the JSON configuration
     * @param context the loader context
     * @return the special component, or null for normal handling
     * @throws TikaConfigException if loading fails
     */
    protected T handleSpecialName(String name, JsonNode configNode,
                                   LoaderContext context) throws TikaConfigException {
        return null;
    }

    /**
     * Decorate the default composite with additional behavior.
     * E.g., for parsers, apply mime filtering from _mime-include/_mime-exclude.
     * Default: returns the composite unchanged.
     *
     * @param composite the default composite
     * @param configNode the JSON configuration for the default marker (may be null)
     * @param context the loader context
     * @return the decorated composite
     * @throws TikaConfigException if decoration fails
     */
    protected T decorateDefaultComposite(T composite, JsonNode configNode,
                                          LoaderContext context) throws TikaConfigException {
        return composite;
    }

    // ==================== Shared implementation ====================

    private DefaultMarkerConfig<T> findDefaultMarker(List<Map.Entry<String, JsonNode>> entries,
                                                      LoaderContext context) {
        int index = 0;
        for (Map.Entry<String, JsonNode> entry : entries) {
            if (defaultMarkerName.equals(entry.getKey())) {
                Set<Class<? extends T>> exclusions =
                        parseExclusions(entry.getValue(), context);
                return new DefaultMarkerConfig<>(true, index, exclusions, entry.getValue());
            }
            index++;
        }
        return new DefaultMarkerConfig<>(false, -1, Collections.emptySet(), null);
    }

    @SuppressWarnings("unchecked")
    private Set<Class<? extends T>> parseExclusions(JsonNode configNode,
                                                     LoaderContext context) {
        Set<Class<? extends T>> exclusions = new HashSet<>();

        if (configNode == null || !configNode.isObject()) {
            return exclusions;
        }

        JsonNode excludeNode = configNode.get("exclude");

        if (excludeNode == null || !excludeNode.isArray()) {
            return exclusions;
        }

        for (JsonNode item : excludeNode) {
            if (!item.isTextual()) {
                continue;
            }

            String typeName = item.asText();
            try {
                Class<?> clazz = ComponentNameResolver.resolveClass(
                        typeName, context.getClassLoader());
                exclusions.add((Class<? extends T>) clazz);
                LOG.debug("Excluding {} from SPI: {}", sectionName, typeName);
            } catch (ClassNotFoundException e) {
                LOG.warn("Unknown {} in exclude list: {}", sectionName, typeName);
            }
        }

        return exclusions;
    }

    /**
     * Configuration for the default marker (e.g., "default-parser").
     */
    private record DefaultMarkerConfig<U>(
            boolean present,
            int index,
            Set<Class<? extends U>> exclusions,
            JsonNode configNode
    ) {}

    // ==================== Accessors for subclasses ====================

    protected String getSectionName() {
        return sectionName;
    }

    protected String getDefaultMarkerName() {
        return defaultMarkerName;
    }

    protected Class<T> getComponentClass() {
        return componentClass;
    }
}
