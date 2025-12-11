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
                    if (configNode != null && configNode.has("_exclude")) {
                        JsonNode excludeNode = configNode.get("_exclude");
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
            // Special case: mime-types requires the initialized registry from TikaLoader
            // The no-arg constructor creates an empty MimeTypes without the XML-loaded types
            if ("mime-types".equals(name)) {
                LOG.debug("Using TikaLoader.getMimeTypes() for mime-types detector");
                return TikaLoader.getMimeTypes();
            }

            // Get detector class - try component name first, then FQCN fallback
            Class<?> detectorClass;
            try {
                detectorClass = registry.getComponentClass(name);
            } catch (TikaConfigException e) {
                // If not found as component name, try as fully qualified class name
                try {
                    detectorClass = Class.forName(name, false, classLoader);
                    LOG.debug("Loaded detector by FQCN: {}", name);
                } catch (ClassNotFoundException ex) {
                    throw new TikaConfigException("Unknown detector: '" + name +
                            "'. Not found as component name or FQCN.", e);
                }
            }

            // Extract framework config
            FrameworkConfig frameworkConfig = FrameworkConfig.extract(configNode, objectMapper);

            // Instantiate detector
            Detector detector = instantiateDetector(detectorClass, frameworkConfig.getComponentConfigJson());

            return detector;

        } catch (TikaConfigException e) {
            throw e;
        } catch (Exception e) {
            throw new TikaConfigException("Failed to load detector '" + name + "'", e);
        }
    }

    private Detector instantiateDetector(Class<?> detectorClass, JsonConfig jsonConfig)
            throws TikaConfigException {
        return ComponentInstantiator.instantiate(detectorClass, jsonConfig, classLoader,
                "Detector", objectMapper);
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
