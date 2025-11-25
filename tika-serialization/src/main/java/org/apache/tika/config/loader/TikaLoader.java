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
import java.nio.file.Path;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.tika.detect.CompositeDetector;
import org.apache.tika.detect.CompositeEncodingDetector;
import org.apache.tika.detect.Detector;
import org.apache.tika.detect.EncodingDetector;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.metadata.filter.CompositeMetadataFilter;
import org.apache.tika.metadata.filter.MetadataFilter;
import org.apache.tika.mime.MediaTypeRegistry;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.AutoDetectParserConfig;
import org.apache.tika.parser.CompositeParser;
import org.apache.tika.parser.Parser;
import org.apache.tika.renderer.CompositeRenderer;
import org.apache.tika.renderer.Renderer;

/**
 * Main entry point for loading Tika components from JSON configuration.
 * Provides lazy loading of component types - only loads classes when requested.
 *
 * <p>Usage:
 * <pre>
 * TikaLoader loader = TikaLoader.load(Path.of("tika-config.json"));
 * Parser parser = loader.loadParsers();
 * Detector detector = loader.loadDetectors();
 * </pre>
 *
 * <p>JSON configuration format:
 * <pre>
 * {
 *   "parsers": {
 *     "pdf-parser": {
 *       "_priority": 10,
 *       "_decorate": {
 *         "mimeInclude": ["application/pdf"],
 *         "mimeExclude": ["application/pdf+fdf"],
 *         "fallbacks": ["empty-parser"]
 *       },
 *       "ocrStrategy": "AUTO",
 *       "extractInlineImages": true
 *     }
 *   },
 *   "detectors": {
 *     "mime-magic-detector": { ... }
 *   }
 * }
 * </pre>
 */
public class TikaLoader {

    private final TikaJsonConfig config;
    private final ClassLoader classLoader;
    private final ObjectMapper objectMapper;
    private final MediaTypeRegistry mediaTypeRegistry;

    // Cached instances (lazy loaded)
    private Parser parsers;
    private Detector detectors;
    private EncodingDetector encodingDetectors;
    private MetadataFilter metadataFilter;
    private Renderer renderers;
    private ConfigLoader configLoader;

    private TikaLoader(TikaJsonConfig config, ClassLoader classLoader,
                       MediaTypeRegistry mediaTypeRegistry) {
        this.config = config;
        this.classLoader = classLoader;
        this.objectMapper = TikaJsonConfig.getObjectMapper();
        this.mediaTypeRegistry = mediaTypeRegistry;
    }

    /**
     * Loads a Tika configuration from a file.
     *
     * @param configPath the path to the JSON configuration file
     * @return the Tika loader
     * @throws TikaConfigException if loading or parsing fails
     */
    public static TikaLoader load(Path configPath) throws TikaConfigException {
        return load(configPath, Thread.currentThread().getContextClassLoader());
    }

    /**
     * Loads a Tika configuration from a file with a specific class loader.
     *
     * @param configPath the path to the JSON configuration file
     * @param classLoader the class loader to use for loading components
     * @return the Tika loader
     * @throws TikaConfigException if loading or parsing fails
     */
    public static TikaLoader load(Path configPath, ClassLoader classLoader)
            throws TikaConfigException {
        TikaJsonConfig config = TikaJsonConfig.load(configPath);
        MediaTypeRegistry registry = MediaTypeRegistry.getDefaultRegistry();
        return new TikaLoader(config, classLoader, registry);
    }

    /**
     * Loads a Tika configuration with custom media type registry.
     *
     * @param configPath the path to the JSON configuration file
     * @param classLoader the class loader to use for loading components
     * @param mediaTypeRegistry the media type registry to use
     * @return the Tika loader
     * @throws TikaConfigException if loading or parsing fails
     */
    public static TikaLoader load(Path configPath, ClassLoader classLoader,
                                   MediaTypeRegistry mediaTypeRegistry)
            throws TikaConfigException {
        TikaJsonConfig config = TikaJsonConfig.load(configPath);
        return new TikaLoader(config, classLoader, mediaTypeRegistry);
    }

    /**
     * Loads and returns all parsers.
     * Results are cached - subsequent calls return the same instance.
     *
     * @return the parser (typically a CompositeParser internally)
     * @throws TikaConfigException if loading fails
     */
    public synchronized Parser loadParsers() throws TikaConfigException {
        if (parsers == null) {
            ParserLoader loader = new ParserLoader(classLoader, objectMapper, mediaTypeRegistry);
            parsers = loader.load(config);
        }
        return parsers;
    }

    /**
     * Loads and returns all detectors.
     * If "detectors" section exists in config, uses only those listed (no SPI fallback).
     * If section missing, uses SPI to discover detectors.
     * Results are cached - subsequent calls return the same instance.
     *
     * @return the detector (typically a CompositeDetector internally)
     * @throws TikaConfigException if loading fails
     */
    public synchronized Detector loadDetectors() throws TikaConfigException {
        if (detectors == null) {
            CompositeComponentLoader<Detector> loader = new CompositeComponentLoader<>(
                    Detector.class, "detectors", "detectors", classLoader, objectMapper);
            List<Detector> detectorList = loader.loadFromArray(config);
            detectors = new CompositeDetector(mediaTypeRegistry, detectorList);
        }
        return detectors;
    }

    /**
     * Loads and returns all encoding detectors.
     * If "encodingDetectors" section exists in config, uses only those listed (no SPI fallback).
     * If section missing, uses SPI to discover encoding detectors.
     * Results are cached - subsequent calls return the same instance.
     *
     * @return the encoding detector (typically a CompositeEncodingDetector internally)
     * @throws TikaConfigException if loading fails
     */
    public synchronized EncodingDetector loadEncodingDetectors() throws TikaConfigException {
        if (encodingDetectors == null) {
            CompositeComponentLoader<EncodingDetector> loader = new CompositeComponentLoader<>(
                    EncodingDetector.class, "encodingDetectors", "encoding-detectors",
                    classLoader, objectMapper);
            List<EncodingDetector> detectorList = loader.loadFromArray(config);
            encodingDetectors = new CompositeEncodingDetector(detectorList);
        }
        return encodingDetectors;
    }

    /**
     * Loads and returns all metadata filters.
     * If "metadataFilters" section exists in config, uses only those listed (no SPI fallback).
     * If section missing, uses SPI to discover metadata filters.
     * Results are cached - subsequent calls return the same instance.
     *
     * @return the metadata filter (typically a CompositeMetadataFilter internally)
     * @throws TikaConfigException if loading fails
     */
    public synchronized MetadataFilter loadMetadataFilters() throws TikaConfigException {
        if (metadataFilter == null) {
            CompositeComponentLoader<MetadataFilter> loader = new CompositeComponentLoader<>(
                    MetadataFilter.class, "metadataFilters", "metadata-filters",
                    classLoader, objectMapper);
            List<MetadataFilter> filterList = loader.loadFromArray(config);
            metadataFilter = new CompositeMetadataFilter(filterList);
        }
        return metadataFilter;
    }

    /**
     * Loads and returns all renderers.
     * If "renderers" section exists in config, uses only those listed (no SPI fallback).
     * If section missing, uses SPI to discover renderers.
     * Results are cached - subsequent calls return the same instance.
     *
     * @return the renderer (typically a CompositeRenderer internally)
     * @throws TikaConfigException if loading fails
     */
    public synchronized Renderer loadRenderers() throws TikaConfigException {
        if (renderers == null) {
            CompositeComponentLoader<Renderer> loader = new CompositeComponentLoader<>(
                    Renderer.class, "renderers", "renderers", classLoader, objectMapper);
            List<Renderer> rendererList = loader.loadFromArray(config);
            renderers = new CompositeRenderer(rendererList);
        }
        return renderers;
    }

    public Parser loadAutoDetectParser() throws TikaConfigException, IOException {
        AutoDetectParserConfig adpConfig = configs().load(AutoDetectParserConfig.class);
        if (adpConfig == null) {
            adpConfig = new AutoDetectParserConfig();
        }
        return AutoDetectParser.build((CompositeParser)loadParsers(), loadDetectors(), adpConfig);
    }

    /**
     * Returns a ConfigLoader for loading simple configuration objects.
     * <p>
     * Use this for POJOs and simple config classes. For complex components like
     * Parsers, Detectors, etc., use the specific load methods on TikaLoader.
     *
     * <p>Usage:
     * <pre>
     * HandlerConfig config = loader.configs().load("handler-config", HandlerConfig.class);
     * // Or use kebab-case auto-conversion:
     * HandlerConfig config = loader.configs().load(HandlerConfig.class);
     * </pre>
     *
     * @return the ConfigLoader instance
     */
    public synchronized ConfigLoader configs() {
        if (configLoader == null) {
            configLoader = new ConfigLoader(config, objectMapper);
        }
        return configLoader;
    }

    /**
     * Gets the underlying JSON configuration.
     *
     * @return the JSON configuration
     */
    public TikaJsonConfig getConfig() {
        return config;
    }

    /**
     * Gets the class loader used for loading components.
     *
     * @return the class loader
     */
    public ClassLoader getClassLoader() {
        return classLoader;
    }

    /**
     * Gets the media type registry.
     *
     * @return the media type registry
     */
    public MediaTypeRegistry getMediaTypeRegistry() {
        return mediaTypeRegistry;
    }
}
