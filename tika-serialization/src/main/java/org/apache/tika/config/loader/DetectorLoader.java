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
import org.apache.tika.detect.CompositeDetector;
import org.apache.tika.detect.DefaultDetector;
import org.apache.tika.detect.Detector;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.utils.ServiceLoaderUtils;

/**
 * Loader for detectors with support for SPI fallback via "default-detector" marker.
 */
public class DetectorLoader {

    private static final Logger LOG = LoggerFactory.getLogger(DetectorLoader.class);

    private final ClassLoader classLoader;
    private final ObjectMapper objectMapper;

    public DetectorLoader(ClassLoader classLoader, ObjectMapper objectMapper) {
        this.classLoader = classLoader;
        this.objectMapper = objectMapper;
    }

    /**
     * Loads detectors from JSON config and builds a CompositeDetector.
     * <p>
     * Supports "default-detector" marker for SPI fallback with optional exclusions:
     * <ul>
     *   <li>If "detectors" section exists:
     *     <ul>
     *       <li>If "default-detector" is present: loads configured detectors + SPI detectors (minus exclusions)</li>
     *       <li>If "default-detector" is absent: loads only configured detectors (no SPI)</li>
     *     </ul>
     *   </li>
     *   <li>If "detectors" section missing: uses DefaultDetector to discover all detectors via SPI</li>
     * </ul>
     *
     * @param config the Tika JSON configuration
     * @return the composite detector
     * @throws TikaConfigException if loading fails
     */
    public Detector load(TikaJsonConfig config) throws TikaConfigException {
        // Load configured detectors
        if (config.hasComponentSection("detectors")) {
            List<Detector> detectorList = new ArrayList<>();
            ComponentRegistry registry = new ComponentRegistry("detectors", classLoader);
            List<Map.Entry<String, JsonNode>> detectors = config.getArrayComponents("detectors");

            // Check if "default-detector" is in the list and extract exclusions
            boolean hasDefaultDetector = false;
            Set<Class<? extends Detector>> excludedDetectorClasses = new HashSet<>();

            for (Map.Entry<String, JsonNode> entry : detectors) {
                if ("default-detector".equals(entry.getKey())) {
                    hasDefaultDetector = true;

                    // Parse exclusions from default-detector config
                    JsonNode configNode = entry.getValue();
                    if (configNode != null && configNode.has("exclude")) {
                        JsonNode excludeNode = configNode.get("exclude");
                        if (excludeNode.isArray()) {
                            for (JsonNode excludeName : excludeNode) {
                                if (excludeName.isTextual()) {
                                    String detectorName = excludeName.asText();
                                    try {
                                        Class<?> detectorClass;
                                        // Try as component name first
                                        try {
                                            detectorClass = registry.getComponentClass(detectorName);
                                        } catch (TikaConfigException e) {
                                            // If not found as component name, try as FQCN
                                            try {
                                                detectorClass = Class.forName(detectorName, false, classLoader);
                                            } catch (ClassNotFoundException ex) {
                                                LOG.warn("Unknown detector in default-detector exclude list: {}", detectorName);
                                                continue;
                                            }
                                        }
                                        @SuppressWarnings("unchecked")
                                        Class<? extends Detector> detectorTyped =
                                                (Class<? extends Detector>) detectorClass;
                                        excludedDetectorClasses.add(detectorTyped);
                                        LOG.debug("Excluding detector from SPI: {}", detectorName);
                                    } catch (Exception e) {
                                        LOG.warn("Failed to exclude detector '{}': {}", detectorName, e.getMessage());
                                    }
                                }
                            }
                        }
                    }
                    break;
                }
            }

            // Track configured detector classes to avoid SPI duplicates
            Set<Class<? extends Detector>> configuredDetectorClasses = new HashSet<>();

            // Load configured detectors (skip "default-detector" marker)
            for (Map.Entry<String, JsonNode> entry : detectors) {
                String name = entry.getKey();

                // Skip the special "default-detector" marker
                if ("default-detector".equals(name)) {
                    continue;
                }

                JsonNode configNode = entry.getValue();
                Detector detector = loadConfiguredDetector(name, configNode, registry);
                detectorList.add(detector);
                @SuppressWarnings("unchecked")
                Class<? extends Detector> detectorClass =
                        (Class<? extends Detector>) detector.getClass();
                configuredDetectorClasses.add(detectorClass);
            }

            // Add excluded detectors to the configured set so they won't be loaded from SPI
            configuredDetectorClasses.addAll(excludedDetectorClasses);

            // Add SPI-discovered detectors only if "default-detector" is in config
            // If "default-detector" is present, use SPI fallback for unlisted detectors
            // If "default-detector" is NOT present, only load explicitly configured detectors
            if (hasDefaultDetector) {
                DefaultDetector defaultDetector = createDefaultDetector(configuredDetectorClasses);
                LOG.debug("Loading SPI detectors because 'default-detector' is in config");
                if (detectorList.isEmpty()) {
                    //short-circuit return as is if no other detectors are specified
                    return defaultDetector;
                }
                detectorList.add(0, defaultDetector);
            } else {
                LOG.debug("Skipping SPI detectors - 'default-detector' not in config");
            }

            return new CompositeDetector(TikaLoader.getMediaTypeRegistry(), detectorList);
        } else {
            // No configured detectors - use DefaultDetector to load all from SPI
            return createDefaultDetector(Collections.emptySet());
        }
    }

    private Detector loadConfiguredDetector(String name, JsonNode configNode,
                                             ComponentRegistry registry)
            throws TikaConfigException {
        try {
            // Get detector class
            Class<?> detectorClass = registry.getComponentClass(name);

            // Extract framework config
            FrameworkConfig frameworkConfig = FrameworkConfig.extract(configNode, objectMapper);

            // Instantiate detector
            Detector detector = instantiateDetector(detectorClass, frameworkConfig.getComponentConfigJson());

            return detector;

        } catch (Exception e) {
            throw new TikaConfigException("Failed to load detector '" + name + "'", e);
        }
    }

    @SuppressWarnings("unchecked")
    private Detector instantiateDetector(Class<?> detectorClass, JsonConfig jsonConfig)
            throws TikaConfigException {

        try {
            Detector detector;

            // Try constructor with JsonConfig parameter
            try {
                Constructor<?> constructor = detectorClass.getConstructor(JsonConfig.class);
                detector = (Detector) constructor.newInstance(jsonConfig);
            } catch (NoSuchMethodException e) {
                // Check if JSON config has actual configuration
                if (hasConfiguration(jsonConfig)) {
                    throw new TikaConfigException(
                            "Detector '" + detectorClass.getName() + "' has configuration in JSON, " +
                            "but does not have a constructor that accepts JsonConfig. " +
                            "Please add a constructor: public " + detectorClass.getSimpleName() + "(JsonConfig jsonConfig)");
                }
                // Fall back to zero-arg constructor if no configuration provided
                detector = (Detector) ServiceLoaderUtils.newInstance(detectorClass,
                        new org.apache.tika.config.ServiceLoader(classLoader));
            }

            return detector;
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            throw new TikaConfigException("Failed to instantiate detector: " +
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
     * Creates a DefaultDetector that loads detectors from SPI, excluding the specified classes.
     *
     * @param excludeClasses detector classes to exclude from SPI loading
     * @return the DefaultDetector with SPI-loaded detectors
     */
    private DefaultDetector createDefaultDetector(Collection<Class<? extends Detector>> excludeClasses) {
        return new DefaultDetector(TikaLoader.getMimeTypes(),
                new org.apache.tika.config.ServiceLoader(classLoader),
                excludeClasses);
    }
}
