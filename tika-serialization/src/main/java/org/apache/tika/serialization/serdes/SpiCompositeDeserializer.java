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
package org.apache.tika.serialization.serdes;

import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;

import org.apache.tika.serialization.ComponentNameResolver;

/**
 * Abstract base deserializer for SPI-loaded composite types that support exclusions.
 * <p>
 * Handles JSON like:
 * <pre>
 * { "exclude": ["html-detector", "zip-detector"] }
 * </pre>
 * or simply an empty object:
 * <pre>
 * {}
 * </pre>
 * <p>
 * Note: The outer type wrapper (e.g., "default-detector") is handled by TikaModule.
 * This deserializer receives just the inner config object.
 * <p>
 * Subclasses implement {@link #createInstance(Collection)} to create the appropriate
 * composite type with the exclusions applied.
 *
 * @param <T> the composite type (e.g., DefaultDetector, DefaultParser)
 */
public abstract class SpiCompositeDeserializer<T> extends JsonDeserializer<T> {

    @Override
    public T deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        JsonNode node = p.readValueAsTree();
        Collection<Class<?>> excludedClasses = parseExclusions(node);
        return createInstance(excludedClasses);
    }

    /**
     * Parse exclusions from config node.
     * Supports both "exclude" and "_exclude" field names.
     */
    protected Collection<Class<?>> parseExclusions(JsonNode node) throws IOException {
        Set<Class<?>> excludedClasses = new HashSet<>();

        if (node == null || !node.isObject()) {
            return excludedClasses;
        }

        // Support both "exclude" and "_exclude" for compatibility
        JsonNode excludeNode = node.has("exclude") ? node.get("exclude") : node.get("_exclude");

        if (excludeNode != null && excludeNode.isArray()) {
            for (JsonNode item : excludeNode) {
                String typeName = item.asText();
                try {
                    Class<?> clazz = ComponentNameResolver.resolveClass(typeName,
                            Thread.currentThread().getContextClassLoader());
                    excludedClasses.add(clazz);
                } catch (ClassNotFoundException e) {
                    throw new IOException("Unknown type in exclude list: " + typeName, e);
                }
            }
        }

        return excludedClasses;
    }

    /**
     * Create an instance of the composite type with the specified exclusions.
     *
     * @param excludedClasses classes to exclude from SPI loading
     * @return the new instance
     */
    protected abstract T createInstance(Collection<Class<?>> excludedClasses);
}
