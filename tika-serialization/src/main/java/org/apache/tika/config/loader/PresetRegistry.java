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
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.tika.exception.TikaConfigException;

/**
 * Named, vetted parse-context fragments a caller can select whole ("presets").
 * A preset's content has the shape of a {@code parse-context} block: parser and
 * component configurations keyed by friendly name. A caller references a preset
 * by name only, so the configuration itself stays in Tika and in the server's
 * config rather than in consuming applications.
 * <p>
 * Nothing is active unless the config's {@code presets} block names it: an
 * entry with value {@code true} activates the catalog definition of that name
 * (content shipped on the classpath, so it tracks the Tika version); an object
 * value defines the preset in place (replacing any catalog definition
 * wholesale); {@code false} or {@code null} is an explicit no-op. Catalog jars
 * can never activate themselves -- every active preset is a visible line in
 * the operator's config. Presets do not compose.
 * <p>
 * The catalog is discovered from {@code META-INF/tika/presets.idx} resources,
 * each line {@code name=/classpath/resource.json}. Blank lines and {@code #}
 * comments are ignored.
 * <pre>
 * "presets": {
 *   "some-catalog-preset": true,
 *   "ocr-heavy": { "pdf-parser": { "ocr": { "strategy": "OCR_AND_TEXT_EXTRACTION" } } }
 * }
 * </pre>
 */
public final class PresetRegistry {

    public static final String CONFIG_KEY = "presets";

    private static final String INDEX_RESOURCE = "META-INF/tika/presets.idx";

    // Names ride in URL paths and config keys
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,99}");

    private final Map<String, String> presets;

    private PresetRegistry(Map<String, String> presets) {
        this.presets = presets;
    }

    /**
     * Builds the active roster from the config's {@code presets} block: only
     * names it lists are active. {@code true} activates a catalog definition
     * (startup error if the catalog has no such name); an object defines the
     * preset in place; {@code false}/{@code null} deactivates explicitly.
     *
     * @param config the loaded config, may be null (empty roster)
     * @param classLoader loader to scan for catalog preset indexes, may be null
     *                    for the thread context loader
     */
    public static PresetRegistry load(TikaJsonConfig config, ClassLoader classLoader)
            throws TikaConfigException {
        Map<String, String> presets = new LinkedHashMap<>();
        if (config != null && config.hasKey(CONFIG_KEY)) {
            JsonNode block = config.getRootNode().get(CONFIG_KEY);
            if (block == null || !block.isObject()) {
                throw new TikaConfigException(
                        "'" + CONFIG_KEY + "' must be an object of preset definitions");
            }
            // load the inert catalog only when the config can reference it
            ClassLoader loader = classLoader != null ? classLoader
                    : Thread.currentThread().getContextClassLoader();
            Map<String, String> catalog = loadCatalog(loader);
            Iterator<Map.Entry<String, JsonNode>> fields = block.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> e = fields.next();
                String name = e.getKey();
                JsonNode value = e.getValue();
                if (value.isNull() || (value.isBoolean() && !value.asBoolean())) {
                    presets.remove(name);
                } else if (value.isBoolean()) {
                    String content = catalog.get(name);
                    if (content == null) {
                        throw new TikaConfigException("preset '" + name +
                                "': true activates a catalog preset, but no catalog " +
                                "on the classpath defines that name");
                    }
                    presets.put(validName(name), content);
                } else if (value.isObject()) {
                    presets.put(validName(name), value.toString());
                } else {
                    throw new TikaConfigException("preset '" + name + "' must be an " +
                            "object, true (activate catalog definition), or false/null");
                }
            }
        }
        return new PresetRegistry(presets);
    }

    private static Map<String, String> loadCatalog(ClassLoader loader)
            throws TikaConfigException {
        Map<String, String> presets = new LinkedHashMap<>();
        ObjectMapper mapper = new ObjectMapper();
        try {
            Enumeration<URL> indexes = loader.getResources(INDEX_RESOURCE);
            while (indexes.hasMoreElements()) {
                URL index = indexes.nextElement();
                for (String line : new String(index.openStream().readAllBytes(),
                        StandardCharsets.UTF_8).split("\n")) {
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
                        presets.put(name, content.toString());
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
        if (!NAME.matcher(name).matches()) {
            throw new TikaConfigException("invalid preset name (letters, digits, " +
                    "'.', '_', '-'; max 100 chars): '" + name + "'");
        }
        return name;
    }

    public Set<String> names() {
        return Collections.unmodifiableSet(presets.keySet());
    }

    /**
     * The preset's content -- a {@code parse-context}-shaped JSON object of
     * component configurations -- or null if no preset has this name.
     */
    public String parseContextJson(String name) {
        return name == null ? null : presets.get(name);
    }
}
