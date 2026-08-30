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
package org.apache.tika.server.core.resource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.apache.tika.config.loader.TikaJsonConfig;
import org.apache.tika.parser.ParseContext;

/**
 * The parser configuration that makes a parse yield the document thumbnail
 * as a raster image: the first PDF page rendered, the EMF/WMF thumbnail of
 * an Office document rendered (that one only, not the pictures of embedded
 * objects), in colour: the renderer's default is the grayscale OCR wants.
 * The stored thumbnails of the other formats need no configuration.
 * <p>
 * Applied by {@code renderThumbnails=true} on {@code /rmeta}, {@code /unpack}
 * and {@code /unpack/all}, and by {@code /unpack/thumbnail}. Three layers,
 * each overriding the one before: the built-in defaults below, a
 * {@code thumbnail-defaults} block in the server config with the same shape
 * as a request config (parser configurations keyed by component name), and
 * the request's own config part.
 * <pre>
 * "thumbnail-defaults": {
 *   "pdf-parser": {"imageStrategy": "RENDER_PAGES_AT_PAGE_END", "maxRenderedPages": 1,
 *                  "ocr": {"dpi": 150}}
 * }
 * </pre>
 * A configuration for a parser that is not installed is never read, so the
 * defaults are harmless on a server without that parser.
 */
public final class ThumbnailDefaults {

    public static final String CONFIG_KEY = "thumbnail-defaults";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String BUILT_IN = """
            {
              "pdf-parser": {
                "imageStrategy": "RENDER_PAGES_AT_PAGE_END",
                "maxRenderedPages": 1,
                "ocr": {"dpi": 96, "imageType": "RGB"}
              },
              "emf-parser": {"renderImage": true, "renderOnlyEmbeddedResourceTypes": ["THUMBNAIL"]},
              "wmf-parser": {"renderImage": true, "renderOnlyEmbeddedResourceTypes": ["THUMBNAIL"]}
            }
            """;

    /**
     * Parser configurations keyed by component name, in application order.
     */
    private final Map<String, ObjectNode> components;

    private ThumbnailDefaults(Map<String, ObjectNode> components) {
        this.components = components;
    }

    /**
     * No defaults at all, a base to {@link #with(String)} settings on.
     */
    public static ThumbnailDefaults none() {
        return new ThumbnailDefaults(new LinkedHashMap<>());
    }

    /**
     * These defaults with another set merged in, field by field.
     */
    public ThumbnailDefaults with(ThumbnailDefaults other) {
        ThumbnailDefaults merged = this;
        for (Map.Entry<String, ObjectNode> component : other.components.entrySet()) {
            merged = merged.with("{\"" + component.getKey() + "\": " + component.getValue() + "}");
        }
        return merged;
    }

    public static ThumbnailDefaults builtIn() {
        return new ThumbnailDefaults(readComponents(parse(BUILT_IN)));
    }

    /**
     * The built-in defaults, with every component the server config's
     * {@code thumbnail-defaults} block names replaced by the config's version.
     *
     * @param config the server config, may be null
     */
    public static ThumbnailDefaults fromConfig(TikaJsonConfig config) {
        Map<String, ObjectNode> components = readComponents(parse(BUILT_IN));
        if (config != null && config.hasKey(CONFIG_KEY)) {
            JsonNode block = config.getRootNode().get(CONFIG_KEY);
            if (!block.isObject()) {
                throw new IllegalArgumentException(
                        CONFIG_KEY + " must be an object of parser configurations");
            }
            components.putAll(readComponents(block));
        }
        return new ThumbnailDefaults(components);
    }

    /**
     * Sets every component the context does not configure itself, so a
     * request's own configuration wins over the defaults.
     */
    public void applyTo(ParseContext context) {
        for (Map.Entry<String, ObjectNode> component : components.entrySet()) {
            if (context.getJsonConfig(component.getKey()) == null) {
                context.setJsonConfig(component.getKey(), component.getValue().toString());
            }
        }
    }

    /**
     * These defaults with the given settings merged in, field by field;
     * for {@code /unpack/thumbnail}, which does not want the OCR the
     * indexing request would run on the rendering.
     */
    public ThumbnailDefaults with(String json) {
        Map<String, ObjectNode> merged = new LinkedHashMap<>();
        for (Map.Entry<String, ObjectNode> component : components.entrySet()) {
            merged.put(component.getKey(), component.getValue().deepCopy());
        }
        for (Map.Entry<String, ObjectNode> component : readComponents(parse(json)).entrySet()) {
            ObjectNode existing = merged.get(component.getKey());
            if (existing == null) {
                merged.put(component.getKey(), component.getValue());
            } else {
                deepMerge(existing, component.getValue());
            }
        }
        return new ThumbnailDefaults(merged);
    }

    /**
     * The configuration of one component as JSON, or null if the defaults
     * do not cover it.
     */
    String get(String component) {
        ObjectNode node = components.get(component);
        return node == null ? null : node.toString();
    }

    private static void deepMerge(ObjectNode target, ObjectNode source) {
        Iterator<Map.Entry<String, JsonNode>> fields = source.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            JsonNode existing = target.get(field.getKey());
            if (existing != null && existing.isObject() && field.getValue().isObject()) {
                deepMerge((ObjectNode) existing, (ObjectNode) field.getValue());
            } else {
                target.set(field.getKey(), field.getValue());
            }
        }
    }

    private static Map<String, ObjectNode> readComponents(JsonNode block) {
        Map<String, ObjectNode> components = new LinkedHashMap<>();
        Iterator<Map.Entry<String, JsonNode>> fields = block.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            if (!field.getValue().isObject()) {
                throw new IllegalArgumentException(CONFIG_KEY + ": the configuration of "
                        + field.getKey() + " must be an object");
            }
            components.put(field.getKey(), (ObjectNode) field.getValue());
        }
        return components;
    }

    private static JsonNode parse(String json) {
        try {
            return MAPPER.readTree(json);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
