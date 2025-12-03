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

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.config.JsonConfig;
import org.apache.tika.detect.CompositeEncodingDetector;
import org.apache.tika.detect.DefaultEncodingDetector;
import org.apache.tika.detect.EncodingDetector;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.utils.ServiceLoaderUtils;

/**
 * Loader for encoding detectors with support for SPI fallback via "default-encoding-detector" marker.
 */
public class EncodingDetectorLoader {

    private static final Logger LOG = LoggerFactory.getLogger(EncodingDetectorLoader.class);

    private final ClassLoader classLoader;
    private final ObjectMapper objectMapper;

    public EncodingDetectorLoader(ClassLoader classLoader, ObjectMapper objectMapper) {
        this.classLoader = classLoader;
        this.objectMapper = objectMapper;
    }

    /**
     * Loads encoding detectors from JSON config and builds a CompositeEncodingDetector.
     * <p>
     * Supports "default-encoding-detector" marker for SPI fallback with optional exclusions:
     * <ul>
     *   <li>If "encoding-detectors" section exists:
     *     <ul>
     *       <li>If "default-encoding-detector" is present: loads configured detectors + SPI detectors (minus exclusions)</li>
     *       <li>If "default-encoding-detector" is absent: loads only configured detectors (no SPI)</li>
     *     </ul>
     *   </li>
     *   <li>If "encoding-detectors" section missing: uses DefaultEncodingDetector to discover all detectors via SPI</li>
     * </ul>
     *
     * @param config the Tika JSON configuration
     * @return the composite encoding detector
     * @throws TikaConfigException if loading fails
     */
    public EncodingDetector load(TikaJsonConfig config) throws TikaConfigException {
        // Load configured encoding detectors
        if (config.hasComponentSection("encoding-detectors")) {
            List<EncodingDetector> detectorList = new ArrayList<>();
            ComponentRegistry registry = new ComponentRegistry("encoding-detectors", classLoader);
            List<Map.Entry<String, JsonNode>> detectors = config.getArrayComponents("encoding-detectors");

            // Check if "default-encoding-detector" is in the list and extract exclusions
            boolean hasDefaultEncodingDetector = false;
            Set<Class<? extends EncodingDetector>> excludedDetectorClasses = new HashSet<>();

            for (Map.Entry<String, JsonNode> entry : detectors) {
                if ("default-encoding-detector".equals(entry.getKey())) {
                    hasDefaultEncodingDetector = true;

                    // Parse exclusions from default-encoding-detector config
                    JsonNode configNode = entry.getValue();
                    if (configNode != null && configNode.has("exclude")) {
                        JsonNode excludeNode = configNode.get("exclude");
                        if (excludeNode.isArray()) {
                            for (JsonNode excludeName : excludeNode) {
                                if (excludeName.isTextual()) {
                                    String detectorName = excludeName.asText();
                                    try {
                                        Class<?> detectorClass = registry.getComponentClass(detectorName);
                                        @SuppressWarnings("unchecked")
                                        Class<? extends EncodingDetector> detectorTyped =
                                                (Class<? extends EncodingDetector>) detectorClass;
                                        excludedDetectorClasses.add(detectorTyped);
                                        LOG.debug("Excluding encoding detector from SPI: {}", detectorName);
                                    } catch (TikaConfigException e) {
                                        LOG.warn("Unknown encoding detector in default-encoding-detector exclude list: {}",
                                                detectorName);
                                    }
                                }
                            }
                        }
                    }
                    break;
                }
            }

            // Track configured detector classes to avoid SPI duplicates
            Set<Class<? extends EncodingDetector>> configuredDetectorClasses = new HashSet<>();

            // Load configured encoding detectors (skip "default-encoding-detector" marker)
            for (Map.Entry<String, JsonNode> entry : detectors) {
                String name = entry.getKey();

                // Skip the special "default-encoding-detector" marker
                if ("default-encoding-detector".equals(name)) {
                    continue;
                }

                JsonNode configNode = entry.getValue();
                EncodingDetector detector = loadConfiguredEncodingDetector(name, configNode, registry);
                detectorList.add(detector);
                @SuppressWarnings("unchecked")
                Class<? extends EncodingDetector> detectorClass =
                        (Class<? extends EncodingDetector>) detector.getClass();
                configuredDetectorClasses.add(detectorClass);
            }

            // Add excluded detectors to the configured set so they won't be loaded from SPI
            configuredDetectorClasses.addAll(excludedDetectorClasses);

            // Add SPI-discovered detectors only if "default-encoding-detector" is in config
            // If "default-encoding-detector" is present, use SPI fallback for unlisted detectors
            // If "default-encoding-detector" is NOT present, only load explicitly configured detectors
            if (hasDefaultEncodingDetector) {
                DefaultEncodingDetector defaultDetector = createDefaultEncodingDetector(configuredDetectorClasses);
                LOG.debug("Loading SPI encoding detectors because 'default-encoding-detector' is in config");
                if (detectorList.isEmpty()) {
                    return defaultDetector;
                }
                detectorList.add(0, defaultDetector);
            } else {
                LOG.debug("Skipping SPI encoding detectors - 'default-encoding-detector' not in config");
            }

            return new CompositeEncodingDetector(detectorList);
        } else {
            // No configured encoding detectors - use DefaultEncodingDetector to load all from SPI
            return createDefaultEncodingDetector(Collections.emptySet());
        }
    }

    private EncodingDetector loadConfiguredEncodingDetector(String name, JsonNode configNode,
                                                             ComponentRegistry registry)
            throws TikaConfigException {
        try {
            // Get encoding detector class
            Class<?> detectorClass = registry.getComponentClass(name);

            // Extract framework config
            FrameworkConfig frameworkConfig = FrameworkConfig.extract(configNode, objectMapper);

            // Instantiate encoding detector
            EncodingDetector detector = instantiateEncodingDetector(detectorClass,
                    frameworkConfig.getComponentConfigJson());

            return detector;

        } catch (Exception e) {
            throw new TikaConfigException("Failed to load encoding detector '" + name + "'", e);
        }
    }

    @SuppressWarnings("unchecked")
    private EncodingDetector instantiateEncodingDetector(Class<?> detectorClass, JsonConfig jsonConfig)
            throws TikaConfigException {

        try {
            EncodingDetector detector;

            // Try constructor with JsonConfig parameter
            try {
                Constructor<?> constructor = detectorClass.getConstructor(JsonConfig.class);
                detector = (EncodingDetector) constructor.newInstance(jsonConfig);
            } catch (NoSuchMethodException e) {
                // Check if JSON config has actual configuration
                if (hasConfiguration(jsonConfig)) {
                    throw new TikaConfigException(
                            "Encoding detector '" + detectorClass.getName() + "' has configuration in JSON, " +
                            "but does not have a constructor that accepts JsonConfig. " +
                            "Please add a constructor: public " + detectorClass.getSimpleName() + "(JsonConfig jsonConfig)");
                }
                // Fall back to zero-arg constructor if no configuration provided
                detector = (EncodingDetector) ServiceLoaderUtils.newInstance(detectorClass,
                        new org.apache.tika.config.ServiceLoader(classLoader));
            }

            return detector;
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            throw new TikaConfigException("Failed to instantiate encoding detector: " +
                    detectorClass.getName(), e);
        }
    }

    /**
     * Checks if the JsonConfig contains actual configuration (non-empty JSON object with fields).
     *
     * @param jsonConfig the JSON configuration
     * @return true if there's meaningful configuration, false if empty or just "{}"
     */
    private boolean hasConfiguration(JsonConfig jsonConfig) {
        if (jsonConfig == null) {
            return false;
        }
        String json = jsonConfig.json();
        if (json == null || json.trim().isEmpty()) {
            return false;
        }
        // Parse to check if it's an empty object or has actual fields
        try {
            JsonNode node = objectMapper.readTree(json);
            // Check if it's an object and has at least one field
            if (node.isObject() && node.size() > 0) {
                return true;
            }
            return false;
        } catch (Exception e) {
            // If we can't parse it, assume it has configuration to be safe
            return true;
        }
    }

    /**
     * Creates a DefaultEncodingDetector that loads detectors from SPI, excluding the specified classes.
     *
     * @param excludeClasses encoding detector classes to exclude from SPI loading
     * @return the DefaultEncodingDetector with SPI-loaded detectors
     */
    private DefaultEncodingDetector createDefaultEncodingDetector(
            Collection<Class<? extends EncodingDetector>> excludeClasses) {
        return new DefaultEncodingDetector(new org.apache.tika.config.ServiceLoader(classLoader),
                excludeClasses);
    }
}
