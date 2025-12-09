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
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.config.JsonConfig;
import org.apache.tika.detect.EncodingDetector;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractEncodingDetectorParser;
import org.apache.tika.parser.CompositeParser;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.ParserDecorator;
import org.apache.tika.parser.RenderingParser;
import org.apache.tika.parser.multiple.AbstractMultipleParser.MetadataPolicy;
import org.apache.tika.parser.multiple.FallbackParser;
import org.apache.tika.renderer.Renderer;
import org.apache.tika.utils.ServiceLoaderUtils;

/**
 * Loader for parsers with support for decoration (mime type filtering, fallbacks).
 */
public class ParserLoader {

    private static final Logger LOG = LoggerFactory.getLogger(ParserLoader.class);

    private final ClassLoader classLoader;
    private final ObjectMapper objectMapper;
    private final EncodingDetector encodingDetector;
    private final Renderer renderer;

    /**
     * Holds parsed config data before decoration is applied.
     */
    private static class ParsedParserConfig {
        final String name;
        final Parser parser;
        final FrameworkConfig.ParserDecoration decoration;

        ParsedParserConfig(String name, Parser parser,
                           FrameworkConfig.ParserDecoration decoration) {
            this.name = name;
            this.parser = parser;
            this.decoration = decoration;
        }
    }

    public ParserLoader(ClassLoader classLoader, ObjectMapper objectMapper,
                        EncodingDetector encodingDetector, Renderer renderer) {
        this.classLoader = classLoader;
        this.objectMapper = objectMapper;
        this.encodingDetector = encodingDetector;
        this.renderer = renderer;
    }

    /**
     * Loads parsers from JSON config and builds a CompositeParser.
     *
     * @param config the Tika JSON configuration
     * @return the composite parser
     * @throws TikaConfigException if loading fails
     */
    public CompositeParser load(TikaJsonConfig config) throws TikaConfigException {
        //TODO -- need to handle multiparsers at some point
        //TODO -- add special handling for external parsers?
        List<Parser> parserList = new ArrayList<>();

        // Load configured parsers
        if (config.hasComponentSection("parsers")) {
            ComponentRegistry registry = new ComponentRegistry("parsers", classLoader);
            List<Map.Entry<String, JsonNode>> parsers = config.getArrayComponents("parsers");

            // Check if "default-parser" is in the list and extract exclusions
            boolean hasDefaultParser = false;
            Set<Class<?>> excludedParserClasses = new HashSet<>();

            for (Map.Entry<String, JsonNode> entry : parsers) {
                if ("default-parser".equals(entry.getKey())) {
                    hasDefaultParser = true;

                    // Parse exclusions from default-parser config
                    JsonNode configNode = entry.getValue();

                    // Check for common mistake: using "excludes" instead of "exclude"
                    if (configNode != null && configNode.has("excludes")) {
                        throw new TikaConfigException(
                            "Invalid configuration for default-parser: found 'excludes' but the correct " +
                            "field name is 'exclude' (singular). Please change 'excludes' to 'exclude' " +
                            "in your configuration.");
                    }

                    if (configNode != null && configNode.has("exclude")) {
                        JsonNode excludeNode = configNode.get("exclude");
                        if (excludeNode.isArray()) {
                            for (JsonNode excludeName : excludeNode) {
                                if (excludeName.isTextual()) {
                                    String parserName = excludeName.asText();
                                    try {
                                        Class<?> parserClass;
                                        // Try as component name first
                                        try {
                                            parserClass = registry.getComponentClass(parserName);
                                        } catch (TikaConfigException e) {
                                            // If not found as component name, try as FQCN
                                            try {
                                                parserClass = Class.forName(parserName, false, classLoader);
                                            } catch (ClassNotFoundException ex) {
                                                LOG.warn("Unknown parser in default-parser exclude list: {}", parserName);
                                                continue;
                                            }
                                        }
                                        excludedParserClasses.add(parserClass);
                                        LOG.debug("Excluding parser from SPI: {}", parserName);
                                    } catch (Exception e) {
                                        LOG.warn("Failed to exclude parser '{}': {}", parserName, e.getMessage());
                                    }
                                }
                            }
                        }
                    }
                    break;
                }
            }

            // First pass: parse configs and instantiate parsers
            // Skip "default-parser" - it's a special marker for SPI fallback, not a real parser
            Map<String, ParsedParserConfig> parsedConfigs = new LinkedHashMap<>();
            for (Map.Entry<String, JsonNode> entry : parsers) {
                String name = entry.getKey();

                // Skip the special "default-parser" marker
                if ("default-parser".equals(name)) {
                    continue;
                }

                JsonNode configNode = entry.getValue();
                ParsedParserConfig parsed = loadConfiguredParser(name, configNode, registry);
                parsedConfigs.put(name, parsed);
            }

            // Track configured parser classes (before decoration) to avoid SPI duplicates
            Set<Class<?>> configuredParserClasses = new HashSet<>();
            for (ParsedParserConfig parsed : parsedConfigs.values()) {
                configuredParserClasses.add(parsed.parser.getClass());
            }

            // Add excluded parsers to the configured set so they won't be loaded from SPI
            configuredParserClasses.addAll(excludedParserClasses);

            // Second pass: apply decorations that may reference other parsers
            for (ParsedParserConfig parsed : parsedConfigs.values()) {
                Parser parser = parsed.parser;

                // Apply decorations if present
                if (parsed.decoration != null) {
                    // Apply mime type filtering
                    if (parsed.decoration.hasFiltering()) {
                        parser = applyMimeFiltering(parser, parsed.decoration);
                    }

                    // Apply fallbacks
                    if (parsed.decoration.hasFallbacks()) {
                        parser = applyFallbacks(parser, parsed.decoration, parsedConfigs);
                    }
                }

                parserList.add(parser);
            }

            // Add SPI-discovered parsers only if "default-parser" is in config
            // If "default-parser" is present, use SPI fallback for unlisted parsers
            // If "default-parser" is NOT present, only load explicitly configured parsers
            if (hasDefaultParser) {
                List<Parser> spiParsers = loadSpiParsers(configuredParserClasses);
                parserList.addAll(spiParsers);
                LOG.debug("Loading SPI parsers because 'default-parser' is in config");
            } else {
                LOG.debug("Skipping SPI parsers - 'default-parser' not in config");
            }
        } else {
            // No configured parsers - load all from SPI
            List<Parser> spiParsers = loadSpiParsers(Collections.emptySet());
            parserList.addAll(spiParsers);
        }

        return new CompositeParser(TikaLoader.getMediaTypeRegistry(), parserList);
    }

    private ParsedParserConfig loadConfiguredParser(String name, JsonNode configNode,
                                                    ComponentRegistry registry)
            throws TikaConfigException {
        try {
            // Get parser class - try component name first, then FQCN fallback
            Class<?> parserClass;
            try {
                parserClass = registry.getComponentClass(name);
            } catch (TikaConfigException e) {
                // If not found as component name, try as fully qualified class name
                try {
                    parserClass = Class.forName(name, false, classLoader);
                    LOG.debug("Loaded parser by FQCN: {}", name);
                } catch (ClassNotFoundException ex) {
                    throw new TikaConfigException("Unknown parser: '" + name +
                            "'. Not found as component name or FQCN.", e);
                }
            }

            // Extract framework config
            FrameworkConfig frameworkConfig = FrameworkConfig.extract(configNode, objectMapper);

            // Instantiate parser
            Parser parser = instantiateParser(parserClass, frameworkConfig.getComponentConfigJson());

            return new ParsedParserConfig(name, parser, frameworkConfig.getDecoration());

        } catch (TikaConfigException e) {
            throw e;
        } catch (Exception e) {
            throw new TikaConfigException("Failed to load parser '" + name + "'", e);
        }
    }

    @SuppressWarnings("unchecked")
    private Parser instantiateParser(Class<?> parserClass, JsonConfig jsonConfig)
            throws TikaConfigException {

        try {
            Parser parser;

            // Try constructor with JsonConfig parameter
            try {
                Constructor<?> constructor = parserClass.getConstructor(JsonConfig.class);
                parser = (Parser) constructor.newInstance(jsonConfig);
            } catch (NoSuchMethodException e) {
                // Check if JSON config has actual configuration
                if (ComponentInstantiator.hasConfiguration(jsonConfig, objectMapper)) {
                    throw new TikaConfigException(
                            "Parser '" + parserClass.getName() + "' has configuration in JSON, " +
                            "but does not have a constructor that accepts JsonConfig. " +
                            "Please add a constructor: public " + parserClass.getSimpleName() + "(JsonConfig jsonConfig)");
                }

                // Try constructor with EncodingDetector parameter (for AbstractEncodingDetectorParser)
                if (AbstractEncodingDetectorParser.class.isAssignableFrom(parserClass)) {
                    try {
                        Constructor<?> constructor = parserClass.getConstructor(EncodingDetector.class);
                        parser = (Parser) constructor.newInstance(encodingDetector);
                    } catch (NoSuchMethodException ex) {
                        // Fall back to zero-arg constructor
                        parser = (Parser) ServiceLoaderUtils.newInstance(parserClass,
                                new org.apache.tika.config.ServiceLoader(classLoader));
                    }
                } else {
                    // Fall back to zero-arg constructor
                    parser = (Parser) ServiceLoaderUtils.newInstance(parserClass,
                            new org.apache.tika.config.ServiceLoader(classLoader));
                }
            }

            // Inject renderer for RenderingParser instances
            if (parser instanceof RenderingParser && renderer != null) {
                ((RenderingParser) parser).setRenderer(renderer);
            }

            return parser;
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            throw new TikaConfigException("Failed to instantiate parser: " +
                    parserClass.getName(), e);
        }
    }

    private Parser applyMimeFiltering(Parser parser, FrameworkConfig.ParserDecoration decoration) {
        List<String> includes = decoration.getMimeInclude();
        List<String> excludes = decoration.getMimeExclude();

        if (!includes.isEmpty()) {
            Set<MediaType> includeTypes = new HashSet<>();
            for (String mimeStr : includes) {
                includeTypes.add(MediaType.parse(mimeStr));
            }
            parser = ParserDecorator.withTypes(parser, includeTypes);
        }

        if (!excludes.isEmpty()) {
            Set<MediaType> excludeTypes = new HashSet<>();
            for (String mimeStr : excludes) {
                excludeTypes.add(MediaType.parse(mimeStr));
            }
            parser = ParserDecorator.withoutTypes(parser, excludeTypes);
        }

        return parser;
    }

    private Parser applyFallbacks(Parser parser, FrameworkConfig.ParserDecoration decoration,
                                   Map<String, ParsedParserConfig> parsedConfigs)
            throws TikaConfigException {

        List<String> fallbackNames = decoration.getFallbacks();
        List<Parser> fallbackParsers = new ArrayList<>();
        fallbackParsers.add(parser); // Primary parser first

        for (String fallbackName : fallbackNames) {
            ParsedParserConfig fallbackConfig = parsedConfigs.get(fallbackName);
            if (fallbackConfig == null) {
                throw new TikaConfigException("Unknown fallback parser: " + fallbackName);
            }
            fallbackParsers.add(fallbackConfig.parser);
        }

        return new FallbackParser(TikaLoader.getMediaTypeRegistry(), MetadataPolicy.KEEP_ALL, fallbackParsers);
    }

    private List<Parser> loadSpiParsers(Set<Class<?>> excludeClasses) {
        List<Parser> result = new ArrayList<>();
        ServiceLoader<Parser> serviceLoader = ServiceLoader.load(Parser.class, classLoader);

        Iterator<Parser> iterator = serviceLoader.iterator();
        while (iterator.hasNext()) {
            try {
                Parser parser = iterator.next();

                // Skip if this parser class was already loaded from config
                if (excludeClasses.contains(parser.getClass())) {
                    LOG.debug("Skipping SPI parser {} - already configured",
                            parser.getClass().getName());
                    continue;
                }
                if (AbstractEncodingDetectorParser.class.isAssignableFrom(parser.getClass())) {
                    ((AbstractEncodingDetectorParser)parser).setEncodingDetector(encodingDetector);
                }
                if (parser instanceof RenderingParser && renderer != null) {
                    ((RenderingParser) parser).setRenderer(renderer);
                }
                result.add(parser);
            } catch (Exception e) {
                // Log and skip problematic SPI providers
                LOG.warn("Failed to load SPI parser: {}", e.getMessage(), e);
            }
        }

        return result;
    }
}
