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

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.ContentHandlerFactory;
import org.apache.tika.serialization.ParseContextUtils;
import org.apache.tika.serialization.serdes.ParseContextDeserializer;

/**
 * Named, vetted parse-context fragments a caller selects whole, by name only ("presets").
 * A preset is operator config, not caller input: resolved with the same trust as the
 * config's own {@code parse-context} block, and fully resolved at load so a bad preset
 * fails startup, not its first request.
 * <p>
 * Nothing is active unless the config's {@code presets} block names it: {@code true}
 * activates the classpath-catalog definition of that name (error if absent), an object
 * defines the preset in place (replacing any catalog definition wholesale),
 * {@code false}/{@code null} is an explicit no-op. Catalog jars can never activate
 * themselves. Presets do not compose.
 * <p>
 * The catalog is discovered from {@code META-INF/tika/preset-catalog.properties}
 * resources (hand-authored, unlike the annotation processor's generated
 * {@code META-INF/tika/*.idx}), each line {@code name=/classpath/resource.json};
 * blank lines and {@code #} comments ignored.
 * <pre>
 * "presets": {
 *   "some-catalog-preset": true,
 *   "ocr-heavy": { "pdf-parser": { "ocr": { "strategy": "OCR_AND_TEXT_EXTRACTION" } } }
 * }
 * </pre>
 *
 * @since Apache Tika 4.1.0
 */
public final class PresetRegistry {

    public static final String CONFIG_KEY = "presets";

    private static final String INDEX_RESOURCE = "META-INF/tika/preset-catalog.properties";

    // Names ride in URL paths and config keys
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,99}");

    private final Map<String, JsonNode> presets;
    private final Set<String> withContentHandlerFactory;
    private final ClassLoader classLoader;

    private PresetRegistry(Map<String, JsonNode> presets, Set<String> withContentHandlerFactory,
                           ClassLoader classLoader) {
        this.presets = presets;
        this.withContentHandlerFactory = withContentHandlerFactory;
        this.classLoader = classLoader;
    }

    /**
     * Builds the active roster from the config's {@code presets} block (see the class
     * javadoc for the value semantics), resolving every active preset.
     *
     * @param config the loaded config, may be null (empty roster)
     * @param classLoader for catalog scanning and component resolution; null for the
     *                    thread context loader
     */
    public static PresetRegistry load(TikaJsonConfig config, ClassLoader classLoader)
            throws TikaConfigException {
        ClassLoader loader = classLoader != null ? classLoader
                : Thread.currentThread().getContextClassLoader();
        Map<String, JsonNode> presets = new LinkedHashMap<>();
        if (config != null && config.hasKey(CONFIG_KEY)) {
            JsonNode block = config.getRootNode().get(CONFIG_KEY);
            if (block == null || !block.isObject()) {
                throw new TikaConfigException(
                        "'" + CONFIG_KEY + "' must be an object of preset definitions");
            }
            // load the inert catalog only when the config can reference it
            Map<String, JsonNode> catalog = loadCatalog(loader);
            Iterator<Map.Entry<String, JsonNode>> fields = block.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> e = fields.next();
                String name = e.getKey();
                JsonNode value = e.getValue();
                if (value.isNull() || (value.isBoolean() && !value.asBoolean())) {
                    continue; // explicit no-op
                } else if (value.isBoolean()) {
                    JsonNode content = catalog.get(name);
                    if (content == null) {
                        throw new TikaConfigException("preset '" + name +
                                "': true activates a catalog preset, but no catalog " +
                                "on the classpath defines that name");
                    }
                    presets.put(validName(name), content);
                } else if (value.isObject()) {
                    presets.put(validName(name), value);
                } else {
                    throw new TikaConfigException("preset '" + name + "' must be an " +
                            "object, true (activate catalog definition), or false/null");
                }
            }
        }
        Set<String> withContentHandlerFactory = new HashSet<>();
        for (Map.Entry<String, JsonNode> e : presets.entrySet()) {
            ParseContext resolved = resolve(e.getKey(), e.getValue(), loader);
            if (resolved.get(ContentHandlerFactory.class) != null) {
                withContentHandlerFactory.add(e.getKey());
            }
        }
        return new PresetRegistry(presets, withContentHandlerFactory, loader);
    }

    // Trusted-tier resolution: operator config, so no wire-block screening.
    private static ParseContext resolve(String name, JsonNode content, ClassLoader loader)
            throws TikaConfigException {
        try {
            ParseContext context = ParseContextDeserializer.readParseContext(content, false);
            ParseContextUtils.resolveAll(context, loader);
            return context;
        } catch (IOException | TikaConfigException e) {
            throw new TikaConfigException(
                    "preset '" + name + "' failed to resolve: " + e.getMessage(), e);
        }
    }

    private static Map<String, JsonNode> loadCatalog(ClassLoader loader)
            throws TikaConfigException {
        Map<String, JsonNode> presets = new LinkedHashMap<>();
        Map<String, URL> sources = new LinkedHashMap<>();
        // Same JSON dialect as the config itself (comments allowed, duplicate keys refused)
        ObjectMapper mapper = TikaObjectMapperFactory.getMapper();
        try {
            Enumeration<URL> indexes = loader.getResources(INDEX_RESOURCE);
            while (indexes.hasMoreElements()) {
                URL index = indexes.nextElement();
                String indexContent;
                try (InputStream is = index.openStream()) {
                    indexContent = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                }
                for (String line : indexContent.split("\n")) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) {
                        continue;
                    }
                    int eq = line.indexOf('=');
                    if (eq <= 0) {
                        throw new TikaConfigException(
                                "bad line in " + index + ": " + line);
                    }
                    String name = validName(line.substring(0, eq).trim());
                    String resource = line.substring(eq + 1).trim();
                    try (InputStream is = loader.getResourceAsStream(
                            stripLeadingSlash(resource))) {
                        if (is == null) {
                            throw new TikaConfigException("preset '" + name +
                                    "' names a missing resource: " + resource);
                        }
                        JsonNode content = mapper.readTree(is);
                        if (!content.isObject()) {
                            throw new TikaConfigException("preset '" + name +
                                    "' must contain a JSON object: " + resource);
                        }
                        JsonNode previous = presets.put(name, content);
                        // classpath order is not a config statement: refuse a silent last-wins
                        if (previous != null && !previous.equals(content)) {
                            throw new TikaConfigException("catalog preset '" + name +
                                    "' is defined with different content by " +
                                    sources.get(name) + " and " + index);
                        }
                        sources.put(name, index);
                    }
                }
            }
        } catch (IOException e) {
            throw new TikaConfigException("failed to load the preset catalog", e);
        }
        return presets;
    }

    private static String stripLeadingSlash(String resource) {
        return resource.startsWith("/") ? resource.substring(1) : resource;
    }

    private static String validName(String name) throws TikaConfigException {
        if (!isValidName(name)) {
            throw new TikaConfigException("invalid preset name (letters, digits, " +
                    "'.', '_', '-'; max 100 chars; may not start with 'config', which is " +
                    "reserved so preset URL routes stay distinct from /config endpoint " +
                    "gating): '" + name + "'");
        }
        return name;
    }

    /**
     * Legal preset name: the NAME rule, and not "config"-prefixed (tika-server gates
     * {@code /config} endpoints on that path fragment). Public so wire deserializers
     * can bound a preset-name field with the same rule.
     */
    public static boolean isValidName(String name) {
        return name != null && NAME.matcher(name).matches()
                && !name.regionMatches(true, 0, "config", 0, 6);
    }

    public Set<String> names() {
        return Collections.unmodifiableSet(presets.keySet());
    }

    public boolean hasPreset(String name) {
        return name != null && presets.containsKey(name);
    }

    /** The preset's {@code parse-context}-shaped JSON, or null for an unknown name. */
    public String parseContextJson(String name) {
        JsonNode node = name == null ? null : presets.get(name);
        return node == null ? null : node.toString();
    }

    /**
     * A fully resolved ParseContext for the preset, or null for an unknown name.
     * Fresh per call: callers mutate the result per request.
     */
    public ParseContext newParseContext(String name) throws TikaConfigException {
        JsonNode content = name == null ? null : presets.get(name);
        return content == null ? null : resolve(name, content, classLoader);
    }

    /** True if the preset binds a {@link ContentHandlerFactory} (it then owns the
     * output format on routes with no explicit format segment). */
    public boolean suppliesContentHandlerFactory(String name) {
        return name != null && withContentHandlerFactory.contains(name);
    }
}
