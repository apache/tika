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
import java.util.Collections;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.tika.config.GlobalSettings;
import org.apache.tika.detect.Detector;
import org.apache.tika.detect.EncodingDetector;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.metadata.filter.CompositeMetadataFilter;
import org.apache.tika.metadata.filter.MetadataFilter;
import org.apache.tika.metadata.filter.NoOpFilter;
import org.apache.tika.mime.MediaTypeRegistry;
import org.apache.tika.mime.MimeTypes;
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

    // Cached instances (lazy loaded)
    private static MimeTypes mimeTypes;
    private static TikaLoader defaultLoader;
    private Parser parsers;
    private Parser autoDetectParser;
    private Detector detectors;
    private EncodingDetector encodingDetectors;
    private MetadataFilter metadataFilter;
    private Renderer renderers;
    private ConfigLoader configLoader;
    private GlobalSettings globalSettings;

    private TikaLoader(TikaJsonConfig config, ClassLoader classLoader) {
        this.config = config;
        this.classLoader = classLoader;
        this.objectMapper = PolymorphicObjectMapperFactory.getMapper();
    }

    /**
     * Initializes the loader by loading global settings.
     * Should be called by all factory methods after construction.
     *
     * @throws TikaConfigException if loading global settings fails
     */
    private void init() throws TikaConfigException {
        loadGlobalSettings();
    }

    /**
     * Loads a Tika configuration from a file.
     * Global settings are automatically loaded and applied during initialization.
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
     * Global settings are automatically loaded and applied during initialization.
     *
     * @param configPath the path to the JSON configuration file
     * @param classLoader the class loader to use for loading components
     * @return the Tika loader
     * @throws TikaConfigException if loading or parsing fails
     */
    public static TikaLoader load(Path configPath, ClassLoader classLoader)
            throws TikaConfigException {
        TikaJsonConfig config = TikaJsonConfig.load(configPath);
        TikaLoader loader = new TikaLoader(config, classLoader);
        loader.init();
        return loader;
    }

    /**
     * Creates a default Tika loader with no configuration file.
     * All components (parsers, detectors, etc.) will be loaded from SPI.
     * Returns a cached instance if already created.
     *
     * @return the Tika loader
     */
    public static synchronized TikaLoader loadDefault() {
        if (defaultLoader == null) {
            defaultLoader = loadDefault(Thread.currentThread().getContextClassLoader());
        }
        return defaultLoader;
    }

    /**
     * Creates a default Tika loader with no configuration file and a specific class loader.
     * All components (parsers, detectors, etc.) will be loaded from SPI.
     *
     * @param classLoader the class loader to use for loading components
     * @return the Tika loader
     */
    public static TikaLoader loadDefault(ClassLoader classLoader) {
        TikaJsonConfig config = TikaJsonConfig.loadDefault();
        TikaLoader loader = new TikaLoader(config, classLoader);
        try {
            loader.init();
        } catch (TikaConfigException e) {
            // Default config should never throw, but wrap in RuntimeException if it does
            throw new RuntimeException("Failed to initialize default TikaLoader", e);
        }
        return loader;
    }

    /**
     * Loads and returns all parsers.
     * Results are cached - subsequent calls return the same instance.
     * <p>
     * Note: This method ensures EncodingDetectors are loaded first,
     * as some parsers require them during construction (e.g., AbstractEncodingDetectorParser
     * requires an EncodingDetector).
     *
     * @return the parser (typically a CompositeParser internally)
     * @throws TikaConfigException if loading fails
     */
    public synchronized Parser loadParsers() throws TikaConfigException {
        if (parsers == null) {
            // Load EncodingDetectors first - some parsers need them during construction
            EncodingDetector encodingDetector = loadEncodingDetectors();

            // Load Renderers - some parsers need them during construction
            Renderer renderer = loadRenderers();

            ParserLoader loader = new ParserLoader(classLoader, objectMapper, encodingDetector, renderer);
            parsers = loader.load(config);
        }
        return parsers;
    }

    /**
     * Loads and returns all detectors.
     * Supports "default-detector" marker for SPI fallback with optional exclusions.
     * If "detectors" section exists:
     *   - If "default-detector" is present: loads configured detectors + SPI detectors (minus exclusions)
     *   - If "default-detector" is absent: loads only configured detectors (no SPI)
     * If "detectors" section missing: uses SPI to discover all detectors.
     * Results are cached - subsequent calls return the same instance.
     *
     * @return the detector (typically a CompositeDetector internally)
     * @throws TikaConfigException if loading fails
     */
    public synchronized Detector loadDetectors() throws TikaConfigException {
        if (detectors == null) {
            DetectorLoader loader = new DetectorLoader(classLoader, objectMapper);
            detectors = loader.load(config);
        }
        return detectors;
    }

    /**
     * Loads and returns all encoding detectors.
     * Supports "default-encoding-detector" marker for SPI fallback with optional exclusions.
     * If "encoding-detectors" section exists:
     *   - If "default-encoding-detector" is present: loads configured detectors + SPI detectors (minus exclusions)
     *   - If "default-encoding-detector" is absent: loads only configured detectors (no SPI)
     * If "encoding-detectors" section missing: uses SPI to discover encoding detectors.
     * Results are cached - subsequent calls return the same instance.
     *
     * @return the encoding detector (typically a CompositeEncodingDetector internally)
     * @throws TikaConfigException if loading fails
     */
    public synchronized EncodingDetector loadEncodingDetectors() throws TikaConfigException {
        if (encodingDetectors == null) {
            EncodingDetectorLoader loader = new EncodingDetectorLoader(classLoader, objectMapper);
            encodingDetectors = loader.load(config);
        }
        return encodingDetectors;
    }

    /**
     * Loads and returns all metadata filters.
     * Metadata filters are opt-in only - they are NOT loaded from SPI by default.
     * If "metadata-filters" section exists in config, uses only those listed.
     * If section missing, returns an empty filter (no SPI fallback).
     * Results are cached - subsequent calls return the same instance.
     *
     * @return the metadata filter (typically a CompositeMetadataFilter internally)
     * @throws TikaConfigException if loading fails
     */
    public synchronized MetadataFilter loadMetadataFilters() throws TikaConfigException {
        if (metadataFilter == null) {
            List<MetadataFilter> filterList;

            // Check if metadata-filters section exists in config
            if (config.hasComponentSection("metadata-filters")) {
                // Load explicitly configured filters (no SPI fallback)
                CompositeComponentLoader<MetadataFilter> loader = new CompositeComponentLoader<>(
                        MetadataFilter.class, "metadata-filters", "metadata-filters",
                        classLoader, objectMapper);
                filterList = loader.loadFromArray(config);
            } else {
                // No config section - metadata filters are opt-in only, don't load from SPI
                filterList = Collections.emptyList();
            }
            if (filterList.isEmpty()) {
                metadataFilter = NoOpFilter.NOOP_FILTER;
            } else {
                metadataFilter = new CompositeMetadataFilter(filterList);
            }
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

    /**
     * Loads and returns an AutoDetectParser configured with this loader's parsers and detectors.
     * Results are cached - subsequent calls return the same instance.
     *
     * @return the auto-detect parser
     * @throws TikaConfigException if loading fails
     * @throws IOException if loading AutoDetectParserConfig fails
     */
    public synchronized Parser loadAutoDetectParser() throws TikaConfigException, IOException {
        if (autoDetectParser == null) {
            AutoDetectParserConfig adpConfig = configs().load(AutoDetectParserConfig.class);
            if (adpConfig == null) {
                adpConfig = new AutoDetectParserConfig();
            }
            autoDetectParser = AutoDetectParser.build((CompositeParser)loadParsers(), loadDetectors(), adpConfig);
        }
        return autoDetectParser;
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
     * Lazily loads the default registry if not already set.
     * This is a static singleton shared across all TikaLoader instances.
     *
     * @return the media type registry
     */
    public static synchronized MediaTypeRegistry getMediaTypeRegistry() {
        return getMimeTypes().getMediaTypeRegistry();
    }

    public static synchronized MimeTypes getMimeTypes() {
        if (mimeTypes == null) {
            mimeTypes = MimeTypes.getDefaultMimeTypes();
        }
        return mimeTypes;
    }

    /**
     * Loads global configuration settings from the JSON config.
     * These settings are applied to Tika's static configuration when loaded.
     *
     * <p>Settings include:
     * <ul>
     *   <li>maxJsonStringFieldLength - Maximum JSON string field length (static, affects all JSON parsing)</li>
     *   <li>service-loader.initializableProblemHandler - How to handle initialization problems</li>
     *   <li>xml-reader-utils - XML parser security settings</li>
     * </ul>
     *
     * <p>Example JSON:
     * <pre>
     * {
     *   "maxJsonStringFieldLength": 50000000,
     *   "service-loader": {
     *     "initializableProblemHandler": "ignore"
     *   },
     *   "xml-reader-utils": {
     *     "maxEntityExpansions": 1000,
     *     "maxNumReuses": 100,
     *     "poolSize": 10
     *   }
     * }
     * </pre>
     *
     * @return the global settings, or an empty object if no settings are configured
     * @throws TikaConfigException if loading fails
     */
    public synchronized GlobalSettings loadGlobalSettings() throws TikaConfigException {
        if (globalSettings == null) {
            globalSettings = new GlobalSettings();

            // Load maxJsonStringFieldLength from top level and set it statically
            if (config.getRootNode().has("maxJsonStringFieldLength")) {
                GlobalSettings.setMaxJsonStringFieldLength(
                        config.getRootNode().get("maxJsonStringFieldLength").asInt());
            }

            // Load service-loader config
            GlobalSettings.ServiceLoaderConfig serviceLoaderConfig =
                    configs().load("service-loader", GlobalSettings.ServiceLoaderConfig.class);
            if (serviceLoaderConfig != null) {
                globalSettings.setServiceLoader(serviceLoaderConfig);
            }

            // Load xml-reader-utils config
            GlobalSettings.XmlReaderUtilsConfig xmlReaderUtilsConfig =
                    configs().load("xml-reader-utils", GlobalSettings.XmlReaderUtilsConfig.class);
            if (xmlReaderUtilsConfig != null) {
                globalSettings.setXmlReaderUtils(xmlReaderUtilsConfig);
            }
        }
        return globalSettings;
    }

    /**
     * Gets the global settings if they have been loaded.
     *
     * @return the global settings, or null if not yet loaded
     */
    public GlobalSettings getGlobalSettings() {
        return globalSettings;
    }
}
