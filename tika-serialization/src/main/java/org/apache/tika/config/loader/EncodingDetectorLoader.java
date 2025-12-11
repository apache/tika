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

import org.apache.tika.detect.CompositeEncodingDetector;
import org.apache.tika.detect.DefaultEncodingDetector;
import org.apache.tika.detect.EncodingDetector;
import org.apache.tika.exception.TikaConfigException;

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
            List<Map.Entry<String, JsonNode>> detectors = config.getArrayComponents("encoding-detectors");

            // Check if "default-encoding-detector" is in the list and extract exclusions
            boolean hasDefaultEncodingDetector = false;
            Set<Class<? extends EncodingDetector>> excludedDetectorClasses = new HashSet<>();

            for (Map.Entry<String, JsonNode> entry : detectors) {
                if ("default-encoding-detector".equals(entry.getKey())) {
                    hasDefaultEncodingDetector = true;
                    excludedDetectorClasses.addAll(parseExclusions(entry.getValue()));
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

                // Use Jackson with mixins to deserialize
                EncodingDetector detector = deserializeEncodingDetector(name, entry.getValue());
                detectorList.add(detector);
                @SuppressWarnings("unchecked")
                Class<? extends EncodingDetector> detectorClass =
                        (Class<? extends EncodingDetector>) detector.getClass();
                configuredDetectorClasses.add(detectorClass);
            }

            // Add excluded detectors to the configured set so they won't be loaded from SPI
            configuredDetectorClasses.addAll(excludedDetectorClasses);

            // Add SPI-discovered detectors only if "default-encoding-detector" is in config
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

    /**
     * Deserializes an encoding detector, trying JsonConfig constructor first, then Jackson bean deserialization.
     */
    private EncodingDetector deserializeEncodingDetector(String name, JsonNode configNode)
            throws TikaConfigException {
        return ComponentInstantiator.instantiate(name, configNode, objectMapper, classLoader);
    }

    /**
     * Parses exclusion list from default-encoding-detector config.
     */
    @SuppressWarnings("unchecked")
    private Set<Class<? extends EncodingDetector>> parseExclusions(JsonNode configNode) {
        Set<Class<? extends EncodingDetector>> excluded = new HashSet<>();
        if (configNode == null || !configNode.has("_exclude")) {
            return excluded;
        }

        JsonNode excludeNode = configNode.get("_exclude");
        if (!excludeNode.isArray()) {
            return excluded;
        }

        for (JsonNode excludeName : excludeNode) {
            if (!excludeName.isTextual()) {
                continue;
            }
            String detectorName = excludeName.asText();
            try {
                Class<?> detectorClass = resolveClass(detectorName);
                excluded.add((Class<? extends EncodingDetector>) detectorClass);
                LOG.debug("Excluding encoding detector from SPI: {}", detectorName);
            } catch (Exception e) {
                LOG.warn("Unknown encoding detector in exclude list: {}", detectorName);
            }
        }
        return excluded;
    }

    /**
     * Resolves a name to a class, trying friendly name lookup first then FQCN.
     */
    private Class<?> resolveClass(String name) throws ClassNotFoundException {
        return org.apache.tika.serialization.ComponentNameResolver
                .resolveClass(name, classLoader);
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
