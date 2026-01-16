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

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.serialization.ComponentNameResolver;
import org.apache.tika.serialization.TikaModule;

/**
 * Factory for creating ObjectMappers configured for Tika serialization.
 * <p>
 * Configures strict validation settings and loads component registries
 * for friendly name resolution.
 */
public class TikaObjectMapperFactory {

    private static final Logger LOG = LoggerFactory.getLogger(TikaObjectMapperFactory.class);

    /**
     * Index file names for component registries.
     */
    private static final String[] REGISTRY_INDEX_FILES = {
            "parsers",
            "detectors",
            "encoding-detectors",
            "metadata-filters",
            "renderers",
            "translators",
            "digester-factories",
            "content-handler-factories",
            "other-configs"
    };

    private static ObjectMapper MAPPER = null;

    public static synchronized ObjectMapper getMapper() {
        if (MAPPER == null) {
            MAPPER = createMapper();
        }
        return MAPPER;
    }

    /**
     * Creates an ObjectMapper configured for Tika serialization.
     *
     * @return configured ObjectMapper
     */
    public static ObjectMapper createMapper() {
        ObjectMapper mapper = new ObjectMapper();

        // Allow comments in JSON config files (// and /* */ style)
        mapper.configure(JsonParser.Feature.ALLOW_COMMENTS, true);

        // Fail on unknown properties to catch configuration errors early
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);

        // Prevent null values being assigned to primitive fields (int, boolean, etc.)
        mapper.configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, true);

        // Ensure enums are properly validated (not just numeric values)
        mapper.configure(DeserializationFeature.FAIL_ON_NUMBERS_FOR_ENUMS, true);

        // Catch duplicate keys in JSON objects
        mapper.configure(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY, true);

        // Need to allow creation of classes without setters/getters -- we may want to revisit this
        mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);

        // Load component registries for name resolution
        loadComponentRegistries();

        // Register TikaModule for compact serialization/deserialization of registered components
        TikaModule tikaModule = new TikaModule();
        mapper.registerModule(tikaModule);

        // Set the shared mapper for TikaModule's deserializers
        TikaModule.setSharedMapper(mapper);

        return mapper;
    }

    /**
     * Loads component registries for name resolution.
     * Registries are loaded from index files and registered with the ComponentNameResolver.
     * Missing registries are silently ignored (may not be on classpath).
     */
    private static void loadComponentRegistries() {
        ClassLoader classLoader = TikaObjectMapperFactory.class.getClassLoader();

        for (String indexFile : REGISTRY_INDEX_FILES) {
            try {
                ComponentRegistry registry = new ComponentRegistry(indexFile, classLoader);
                ComponentNameResolver.registerRegistry(indexFile, registry);
                LOG.debug("Loaded component registry: {}", indexFile);
            } catch (TikaConfigException e) {
                // Registry not available - this is expected if the module isn't on classpath
                LOG.debug("Component registry not available: {} - {}", indexFile, e.getMessage());
            }
        }
    }
}
