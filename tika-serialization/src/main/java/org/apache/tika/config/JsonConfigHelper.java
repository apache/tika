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
package org.apache.tika.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;

/**
 * Helper class for loading JSON config templates with placeholder replacement.
 * <p>
 * This provides a type-safe alternative to String.replace() for JSON configs,
 * properly handling paths (converting backslashes to forward slashes on Windows)
 * and different value types (strings, integers, doubles, booleans).
 * <p>
 * Example template JSON:
 * <pre>
 * {
 *   "fetchers": {
 *     "fs": {
 *       "file-system-fetcher": {
 *         "basePath": "FETCHER_BASE_PATH"
 *       }
 *     }
 *   },
 *   "pipes": {
 *     "maxFiles": "MAX_FILES",
 *     "enabled": "ENABLED"
 *   }
 * }
 * </pre>
 * <p>
 * Usage:
 * <pre>
 * Map&lt;String, Object&gt; replacements = Map.of(
 *     "FETCHER_BASE_PATH", tmpDir.resolve("input"),  // Path
 *     "MAX_FILES", 100,                               // Integer
 *     "ENABLED", true                                 // Boolean
 * );
 * JsonNode config = JsonConfigHelper.load(templatePath, replacements);
 * </pre>
 */
public class JsonConfigHelper {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Loads a JSON config template from a resource path and applies replacements.
     *
     * @param resourcePath path to template resource (e.g., "/configs/template.json")
     * @param clazz class to use for resource loading
     * @param replacements map of placeholder names to replacement values
     * @return the modified JsonNode tree
     * @throws IOException if the template cannot be read or parsed
     */
    public static JsonNode loadFromResource(String resourcePath, Class<?> clazz,
                                            Map<String, Object> replacements) throws IOException {
        try (InputStream is = clazz.getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IOException("Resource not found: " + resourcePath);
            }
            JsonNode root = MAPPER.readTree(is);
            return applyReplacements(root, replacements);
        }
    }

    /**
     * Loads a JSON config template from a file path and applies replacements.
     *
     * @param templatePath path to the template file
     * @param replacements map of placeholder names to replacement values
     * @return the modified JsonNode tree
     * @throws IOException if the template cannot be read or parsed
     */
    public static JsonNode load(Path templatePath, Map<String, Object> replacements)
            throws IOException {
        JsonNode root = MAPPER.readTree(templatePath.toFile());
        return applyReplacements(root, replacements);
    }

    /**
     * Loads a JSON config template from a string and applies replacements.
     *
     * @param jsonTemplate the JSON template string
     * @param replacements map of placeholder names to replacement values
     * @return the modified JsonNode tree
     * @throws IOException if the template cannot be parsed
     */
    public static JsonNode loadFromString(String jsonTemplate, Map<String, Object> replacements)
            throws IOException {
        JsonNode root = MAPPER.readTree(jsonTemplate);
        return applyReplacements(root, replacements);
    }

    /**
     * Loads a template, applies replacements, and writes to an output file.
     *
     * @param templatePath path to the template file
     * @param replacements map of placeholder names to replacement values
     * @param outputPath path to write the result
     * @return the output path
     * @throws IOException if reading or writing fails
     */
    public static Path writeConfig(Path templatePath, Map<String, Object> replacements,
                                   Path outputPath) throws IOException {
        JsonNode config = load(templatePath, replacements);
        String json = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(config);
        Files.writeString(outputPath, json, StandardCharsets.UTF_8);
        return outputPath;
    }

    /**
     * Loads a template from resources, applies replacements, and writes to an output file.
     *
     * @param resourcePath path to template resource
     * @param clazz class to use for resource loading
     * @param replacements map of placeholder names to replacement values
     * @param outputPath path to write the result
     * @return the output path
     * @throws IOException if reading or writing fails
     */
    public static Path writeConfigFromResource(String resourcePath, Class<?> clazz,
                                               Map<String, Object> replacements,
                                               Path outputPath) throws IOException {
        JsonNode config = loadFromResource(resourcePath, clazz, replacements);
        String json = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(config);
        Files.writeString(outputPath, json, StandardCharsets.UTF_8);
        return outputPath;
    }

    /**
     * Applies replacements to a JsonNode tree, modifying it in place.
     *
     * @param root the root node to modify
     * @param replacements map of placeholder names to replacement values
     * @return the modified root node
     */
    public static JsonNode applyReplacements(JsonNode root, Map<String, Object> replacements) {
        if (root.isObject()) {
            applyToObject((ObjectNode) root, replacements);
        } else if (root.isArray()) {
            applyToArray((ArrayNode) root, replacements);
        }
        return root;
    }

    private static void applyToObject(ObjectNode node, Map<String, Object> replacements) {
        Iterator<String> fieldNames = node.fieldNames();
        while (fieldNames.hasNext()) {
            String fieldName = fieldNames.next();
            JsonNode child = node.get(fieldName);

            if (child.isTextual()) {
                String text = child.asText();
                if (replacements.containsKey(text)) {
                    node.set(fieldName, toJsonNode(replacements.get(text)));
                }
            } else if (child.isObject()) {
                applyToObject((ObjectNode) child, replacements);
            } else if (child.isArray()) {
                applyToArray((ArrayNode) child, replacements);
            }
        }
    }

    private static void applyToArray(ArrayNode array, Map<String, Object> replacements) {
        for (int i = 0; i < array.size(); i++) {
            JsonNode child = array.get(i);

            if (child.isTextual()) {
                String text = child.asText();
                if (replacements.containsKey(text)) {
                    array.set(i, toJsonNode(replacements.get(text)));
                }
            } else if (child.isObject()) {
                applyToObject((ObjectNode) child, replacements);
            } else if (child.isArray()) {
                applyToArray((ArrayNode) child, replacements);
            }
        }
    }

    /**
     * Converts a Java object to the appropriate JsonNode type.
     * <p>
     * Supported types:
     * <ul>
     *   <li>JsonNode - used directly (for complex objects or arrays)</li>
     *   <li>List - converted to ArrayNode</li>
     *   <li>Path - converted to forward-slash string (Windows compatible)</li>
     *   <li>String - TextNode</li>
     *   <li>Integer, Long - numeric node</li>
     *   <li>Float, Double - numeric node</li>
     *   <li>Boolean - boolean node</li>
     *   <li>null - null node</li>
     * </ul>
     *
     * @param value the value to convert
     * @return the appropriate JsonNode
     */
    private static JsonNode toJsonNode(Object value) {
        if (value == null) {
            return MAPPER.nullNode();
        }
        if (value instanceof JsonNode) {
            // Already a JsonNode, use directly
            return (JsonNode) value;
        }
        if (value instanceof List) {
            // Convert List to ArrayNode
            ArrayNode arrayNode = MAPPER.createArrayNode();
            for (Object item : (List<?>) value) {
                arrayNode.add(toJsonNode(item));
            }
            return arrayNode;
        }
        if (value instanceof Path) {
            // Convert path to forward slashes for JSON (Windows compatibility)
            return new TextNode(toJsonPath((Path) value));
        }
        if (value instanceof String) {
            return new TextNode((String) value);
        }
        if (value instanceof Integer) {
            return MAPPER.getNodeFactory().numberNode((Integer) value);
        }
        if (value instanceof Long) {
            return MAPPER.getNodeFactory().numberNode((Long) value);
        }
        if (value instanceof Double) {
            return MAPPER.getNodeFactory().numberNode((Double) value);
        }
        if (value instanceof Float) {
            return MAPPER.getNodeFactory().numberNode((Float) value);
        }
        if (value instanceof Boolean) {
            return MAPPER.getNodeFactory().booleanNode((Boolean) value);
        }
        // Fallback: convert to string
        return new TextNode(value.toString());
    }

    /**
     * Converts a Path to a JSON-safe string with forward slashes.
     * This handles Windows paths correctly.
     *
     * @param path the path to convert
     * @return the path string with forward slashes
     */
    public static String toJsonPath(Path path) {
        return path.toAbsolutePath().toString().replace("\\", "/");
    }

    /**
     * Returns the ObjectMapper used by this helper.
     * Useful for additional JSON operations.
     *
     * @return the ObjectMapper instance
     */
    public static ObjectMapper getMapper() {
        return MAPPER;
    }
}
