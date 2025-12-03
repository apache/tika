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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Factory for creating ObjectMappers with consistent polymorphic type handling
 * across Tika configuration and ParseContext serialization.
 */
public class PolymorphicObjectMapperFactory {

    private static final Logger LOG = LoggerFactory.getLogger(PolymorphicObjectMapperFactory.class);

    /**
     * Classpath resource file where users can specify additional package prefixes
     * to allow for polymorphic deserialization. One package prefix per line.
     * Comments (lines starting with #) and blank lines are ignored.
     *
     * Example content:
     * <pre>
     * # Allow com.acme classes
     * com.acme
     * # Allow com.example classes
     * com.example
     * </pre>
     */
    public static final String ALLOWED_PACKAGES_RESOURCE = "META-INF/tika-serialization-allowlist.txt";

    private static ObjectMapper MAPPER = null;

    public static synchronized ObjectMapper getMapper() {
        if (MAPPER == null) {
            MAPPER = createPolymorphicMapper();
        }
        return MAPPER;
    }

    /**
     * Creates an ObjectMapper with polymorphic type handling for Tika configuration.
     * Configures security validation to allow Tika classes and any additional
     * packages specified via {@link #ALLOWED_PACKAGES_RESOURCE} files on the classpath.
     *
     * @return configured ObjectMapper
     */
    public static ObjectMapper createPolymorphicMapper() {
        ObjectMapper mapper = new ObjectMapper();

        // Fail on unknown properties to catch configuration errors early
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);

        // Prevent null values being assigned to primitive fields (int, boolean, etc.)
        mapper.configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, true);

        // Ensure enums are properly validated (not just numeric values)
        mapper.configure(DeserializationFeature.FAIL_ON_NUMBERS_FOR_ENUMS, true);

        // Catch duplicate keys in JSON objects
        mapper.configure(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY, true);

        //Need to allow creation of classes without setters/getters -- we may want to revisit this
        mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);

        // Build polymorphic type validator
        BasicPolymorphicTypeValidator.Builder builder = BasicPolymorphicTypeValidator.builder()
                .allowIfSubType("org.apache.tika.")
                .allowIfSubType("java.util.")
                .allowIfSubType("java.nio.file.");

        // Add user-specified packages from classpath
        List<String> additionalPackages = loadAllowedPackages();
        for (String packagePrefix : additionalPackages) {
            builder.allowIfSubType(packagePrefix);
        }

        PolymorphicTypeValidator typeValidator = builder.build();

        // Use OBJECT_AND_NON_CONCRETE to add type info when static type is:
        // - Object.class (for objects in maps)
        // - Abstract classes or interfaces (for polymorphic fields)
        mapper.activateDefaultTyping(typeValidator, ObjectMapper.DefaultTyping.OBJECT_AND_NON_CONCRETE, JsonTypeInfo.As.PROPERTY);

        return mapper;
    }

    /**
     * Loads additional package prefixes from classpath resources.
     * Scans all {@link #ALLOWED_PACKAGES_RESOURCE} files on the classpath.
     *
     * @return list of additional package prefixes to allow
     */
    private static List<String> loadAllowedPackages() {
        List<String> packages = new ArrayList<>();
        try {
            Enumeration<URL> resources = PolymorphicObjectMapperFactory.class.getClassLoader()
                    .getResources(ALLOWED_PACKAGES_RESOURCE);

            while (resources.hasMoreElements()) {
                URL resource = resources.nextElement();
                LOG.debug("Loading allowed packages from: {}", resource);

                try (InputStream is = resource.openStream();
                         BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {

                    String line;
                    while ((line = reader.readLine()) != null) {
                        line = line.trim();
                        // Skip comments and empty lines
                        if (line.isEmpty() || line.startsWith("#")) {
                            continue;
                        }
                        packages.add(line);
                        LOG.info("Allowing polymorphic deserialization for package: {}", line);
                    }
                } catch (IOException e) {
                    LOG.warn("Failed to read allowed packages from: {}", resource, e);
                }
            }
        } catch (IOException e) {
            LOG.warn("Failed to load allowed packages resources", e);
        }
        return packages;
    }
}
