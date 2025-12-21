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

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.apache.tika.config.GlobalSettings;
import org.apache.tika.config.ServiceLoader;
import org.apache.tika.detect.CompositeDetector;
import org.apache.tika.detect.CompositeEncodingDetector;
import org.apache.tika.detect.DefaultDetector;
import org.apache.tika.detect.DefaultEncodingDetector;
import org.apache.tika.detect.Detector;
import org.apache.tika.detect.EncodingDetector;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.language.translate.DefaultTranslator;
import org.apache.tika.language.translate.Translator;
import org.apache.tika.metadata.filter.CompositeMetadataFilter;
import org.apache.tika.metadata.filter.MetadataFilter;
import org.apache.tika.metadata.filter.NoOpFilter;
import org.apache.tika.mime.MediaType;
import org.apache.tika.mime.MediaTypeRegistry;
import org.apache.tika.mime.MimeTypes;
import org.apache.tika.parser.AbstractEncodingDetectorParser;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.AutoDetectParserConfig;
import org.apache.tika.parser.CompositeParser;
import org.apache.tika.parser.DefaultParser;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.ParserDecorator;
import org.apache.tika.parser.RenderingParser;
import org.apache.tika.renderer.CompositeRenderer;
import org.apache.tika.renderer.Renderer;
import org.apache.tika.serialization.ComponentConfig;
import org.apache.tika.serialization.ComponentNameResolver;
import org.apache.tika.serialization.JsonMetadata;
import org.apache.tika.serialization.JsonMetadataList;

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
 *   "parsers": [
 *     {
 *       "pdf-parser": {
 *         "_mime-include": ["application/pdf"],
 *         "_mime-exclude": ["application/pdf+fdf"],
 *         "ocrStrategy": "AUTO",
 *         "extractInlineImages": true
 *       }
 *     }
 *   ],
 *   "detectors": [
 *     { "mime-magic-detector": { ... } }
 *   ]
 * }
 * </pre>
 */
public class TikaLoader {

    // Static registration of component configurations
    static {
        registerComponentConfigs();
    }

    private static void registerComponentConfigs() {
        // Register how each component type should be loaded
        ComponentConfig.builder("parsers", Parser.class)
                .loadAsList()
                .wrapWith(list -> new CompositeParser(getMediaTypeRegistry(), (List<Parser>) list))
                .defaultProvider(DefaultParser::new)
                .register();

        ComponentConfig.builder("detectors", Detector.class)
                .loadAsList()
                .wrapWith(list -> new CompositeDetector(getMediaTypeRegistry(), (List<Detector>) list))
                .defaultProvider(DefaultDetector::new)
                .register();

        ComponentConfig.builder("encoding-detectors", EncodingDetector.class)
                .loadAsList()
                .wrapWith(list -> {
                    if (list.isEmpty()) {
                        return new DefaultEncodingDetector();
                    } else if (list.size() == 1 && list.get(0) instanceof CompositeEncodingDetector) {
                        // Don't double-wrap if single item is already a CompositeEncodingDetector
                        // (e.g., DefaultEncodingDetector with exclusions)
                        return (EncodingDetector) list.get(0);
                    } else {
                        return new org.apache.tika.detect.CompositeEncodingDetector((List<EncodingDetector>) list);
                    }
                })
                .defaultProvider(DefaultEncodingDetector::new)
                .register();

        ComponentConfig.builder("metadata-filters", MetadataFilter.class)
                .loadAsList()
                .wrapWith(list -> list.isEmpty()
                        ? NoOpFilter.NOOP_FILTER
                        : new CompositeMetadataFilter((List<MetadataFilter>) list))
                .defaultProvider(() -> NoOpFilter.NOOP_FILTER)
                .register();

        ComponentConfig.builder("renderers", Renderer.class)
                .loadAsList()
                .wrapWith(list -> new CompositeRenderer((List<Renderer>) list))
                .register();

        ComponentConfig.builder("translator", Translator.class)
                .loadAsList()
                .wrapWith(list -> list.isEmpty() ? null : (Translator) list.get(0))
                .defaultProvider(DefaultTranslator::new)
                .register();
    }

    private final TikaJsonConfig config;
    private final ClassLoader classLoader;
    private final ObjectMapper objectMapper;

    // Cache of loaded components (keyed by component class)
    private final Map<Class<?>, Object> componentCache = new ConcurrentHashMap<>();

    // Static instances
    private static MimeTypes mimeTypes;
    private static TikaLoader defaultLoader;

    // Special cached instances that aren't standard components
    private Parser autoDetectParser;
    private ConfigLoader configLoader;
    private GlobalSettings globalSettings;

    // Pending configs for deferred creation of DefaultParser/DefaultDetector/DefaultEncodingDetector
    // These are created in post-processing to avoid double-creation
    // TODO -- we need to clean up this abomination
    private JsonNode pendingDefaultParserConfig;
    private int pendingDefaultParserIndex = -1;
    private JsonNode pendingDefaultDetectorConfig;
    private int pendingDefaultDetectorIndex = -1;
    private JsonNode pendingDefaultEncodingDetectorConfig;
    private int pendingDefaultEncodingDetectorIndex = -1;

    private TikaLoader(TikaJsonConfig config, ClassLoader classLoader) {
        this.config = config;
        this.classLoader = classLoader;
        this.objectMapper = TikaObjectMapperFactory.getMapper();
    }

    /**
     * Initializes the loader by loading global settings.
     * Should be called by all factory methods after construction.
     *
     * @throws TikaConfigException if loading global settings fails
     */
    private void init() throws TikaConfigException, IOException {
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
    public static TikaLoader load(Path configPath) throws TikaConfigException, IOException {
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
            throws TikaConfigException, IOException {
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
        } catch (IOException | TikaConfigException e) {
            // Default config should never throw, but wrap in RuntimeException if it does
            throw new RuntimeException("Failed to initialize default TikaLoader", e);
        }
        return loader;
    }

    /**
     * Loads and returns all parsers.
     * Syntactic sugar for {@code get(Parser.class)}.
     * Results are cached - subsequent calls return the same instance.
     *
     * @return the parser (typically a CompositeParser internally)
     * @throws TikaConfigException if loading fails
     */
    public Parser loadParsers() throws TikaConfigException {
        try {
            return get(Parser.class);
        } catch (IOException e) {
            throw new TikaConfigException("Failed to load parsers", e);
        }
    }

    /**
     * Loads and returns all detectors.
     * Syntactic sugar for {@code get(Detector.class)}.
     * Results are cached - subsequent calls return the same instance.
     *
     * @return the detector (typically a CompositeDetector internally)
     * @throws TikaConfigException if loading fails
     */
    public Detector loadDetectors() throws TikaConfigException {
        try {
            return get(Detector.class);
        } catch (IOException e) {
            throw new TikaConfigException("Failed to load detectors", e);
        }
    }

    /**
     * Loads and returns all encoding detectors.
     * Syntactic sugar for {@code get(EncodingDetector.class)}.
     * Results are cached - subsequent calls return the same instance.
     *
     * @return the encoding detector (typically a CompositeEncodingDetector internally)
     * @throws TikaConfigException if loading fails
     */
    public EncodingDetector loadEncodingDetectors() throws TikaConfigException {
        try {
            return get(EncodingDetector.class);
        } catch (IOException e) {
            throw new TikaConfigException("Failed to load encoding detectors", e);
        }
    }

    /**
     * Loads and returns all metadata filters.
     * Syntactic sugar for {@code get(MetadataFilter.class)}.
     * Results are cached - subsequent calls return the same instance.
     *
     * @return the metadata filter (typically a CompositeMetadataFilter internally)
     * @throws TikaConfigException if loading fails
     */
    public MetadataFilter loadMetadataFilters() throws TikaConfigException {
        try {
            return get(MetadataFilter.class);
        } catch (IOException e) {
            throw new TikaConfigException("Failed to load metadata filters", e);
        }
    }

    /**
     * Loads and returns all renderers.
     * Syntactic sugar for {@code get(Renderer.class)}.
     * Results are cached - subsequent calls return the same instance.
     *
     * @return the renderer (typically a CompositeRenderer internally)
     * @throws TikaConfigException if loading fails
     */
    public Renderer loadRenderers() throws TikaConfigException {
        try {
            return get(Renderer.class);
        } catch (IOException e) {
            throw new TikaConfigException("Failed to load renderers", e);
        }
    }

    /**
     * Loads and returns the translator.
     * Syntactic sugar for {@code get(Translator.class)}.
     * Results are cached - subsequent calls return the same instance.
     *
     * @return the translator
     * @throws TikaConfigException if loading fails
     */
    public Translator loadTranslator() throws TikaConfigException {
        try {
            return get(Translator.class);
        } catch (IOException e) {
            throw new TikaConfigException("Failed to load translator", e);
        }
    }

    /**
     * Loads and returns the AutoDetectParserConfig from the "auto-detect-parser" section.
     * Returns null if the section is not present in the config.
     *
     * @return the AutoDetectParserConfig, or null if not configured
     * @throws IOException if loading fails
     */
    private AutoDetectParserConfig loadAutoDetectParserConfig() throws IOException {
        return config.deserialize("auto-detect-parser", AutoDetectParserConfig.class);
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
            // Load directly from root-level config (not via configs() which only looks in "other-configs")
            AutoDetectParserConfig adpConfig = loadAutoDetectParserConfig();
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
     *   <li>metadata-list - Jackson StreamReadConstraints for JsonMetadata/JsonMetadataList serialization</li>
     *   <li>service-loader - Service loader configuration</li>
     *   <li>xml-reader-utils - XML parser security settings</li>
     * </ul>
     *
     * <p>Example JSON:
     * <pre>
     * {
     *   "metadata-list": {
     *     "maxStringLength": 50000000,
     *     "maxNestingDepth": 10,
     *     "maxNumberLength": 500
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
    public synchronized GlobalSettings loadGlobalSettings() throws IOException, TikaConfigException {
        if (globalSettings == null) {
            globalSettings = new GlobalSettings();

            // Load metadata-list config for JsonMetadata/JsonMetadataList serialization
            loadMetadataListConfig();

            // Load service-loader config (official Tika config at root level)
            GlobalSettings.ServiceLoaderConfig serviceLoaderConfig =
                    config.deserialize("service-loader", GlobalSettings.ServiceLoaderConfig.class);
            if (serviceLoaderConfig != null) {
                globalSettings.setServiceLoader(serviceLoaderConfig);
            }

            // Load xml-reader-utils config (official Tika config at root level)
            GlobalSettings.XmlReaderUtilsConfig xmlReaderUtilsConfig =
                    config.deserialize("xml-reader-utils", GlobalSettings.XmlReaderUtilsConfig.class);
            if (xmlReaderUtilsConfig != null) {
                globalSettings.setXmlReaderUtils(xmlReaderUtilsConfig);
            }
        }
        return globalSettings;
    }

    /**
     * Loads the metadata-list configuration section and applies it to
     * JsonMetadata and JsonMetadataList serializers.
     * <p>
     * Configuration uses Jackson's StreamReadConstraints property names:
     * <pre>
     * {
     *   "metadata-list": {
     *     "maxStringLength": 20000000,
     *     "maxNestingDepth": 10,
     *     "maxNumberLength": 500
     *   }
     * }
     * </pre>
     */
    private void loadMetadataListConfig() {
        JsonNode metadataListNode = config.getRootNode().get("metadata-list");
        if (metadataListNode == null) {
            return;
        }

        StreamReadConstraints.Builder builder = StreamReadConstraints.builder();

        if (metadataListNode.has("maxStringLength")) {
            builder.maxStringLength(metadataListNode.get("maxStringLength").asInt());
        }
        if (metadataListNode.has("maxNestingDepth")) {
            builder.maxNestingDepth(metadataListNode.get("maxNestingDepth").asInt());
        }
        if (metadataListNode.has("maxNumberLength")) {
            builder.maxNumberLength(metadataListNode.get("maxNumberLength").asInt());
        }

        StreamReadConstraints constraints = builder.build();
        JsonMetadata.setStreamReadConstraints(constraints);
        JsonMetadataList.setStreamReadConstraints(constraints);
    }

    /**
     * Gets the global settings if they have been loaded.
     *
     * @return the global settings, or null if not yet loaded
     */
    public GlobalSettings getGlobalSettings() {
        return globalSettings;
    }

    // ==================== Generic Component Access ====================

    /**
     * Gets a component by its class type.
     * Components are loaded lazily and cached.
     *
     * @param componentClass the component class (e.g., Parser.class, Detector.class)
     * @return the loaded component
     * @throws IOException if loading fails
     */
    @SuppressWarnings("unchecked")
    public <T> T get(Class<T> componentClass) throws IOException {
        // Check cache first
        if (componentCache.containsKey(componentClass)) {
            return (T) componentCache.get(componentClass);
        }

        // Get component config from registry
        ComponentConfig<T> componentConfig = ComponentNameResolver.getComponentConfig(componentClass);
        if (componentConfig == null) {
            throw new IllegalArgumentException(
                    "No component registered for class: " + componentClass.getName());
        }

        try {
            // Load the component
            T component = loadComponent(componentConfig);

            // Cache and return
            if (component != null) {
                componentCache.put(componentClass, component);
            }
            return component;
        } catch (TikaConfigException e) {
            throw new IOException("Failed to load component: " + componentClass.getName(), e);
        }
    }

    /**
     * Gets a component by its JSON field name.
     * Components are loaded lazily and cached.
     *
     * @param jsonField the JSON field name (e.g., "parsers", "detectors")
     * @return the loaded component
     * @throws IOException if loading fails
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String jsonField) throws IOException {
        // Get component config from registry by field name
        ComponentConfig<?> componentConfig = ComponentNameResolver.getComponentConfig(jsonField);
        if (componentConfig == null) {
            throw new IllegalArgumentException("No component registered for field: " + jsonField);
        }

        // Delegate to get by class (which handles caching)
        return (T) get(componentConfig.getComponentClass());
    }

    /**
     * Generic component loading using ComponentConfig.
     */
    @SuppressWarnings("unchecked")
    private <T> T loadComponent(ComponentConfig<T> componentConfig) throws TikaConfigException, IOException {
        Class<T> componentClass = componentConfig.getComponentClass();

        // Load the component list
        List<T> componentList = loadComponentList(componentConfig.getJsonField(), componentClass);

        // Apply post-processing (auto-exclusions for Parser/Detector, configure dependencies)
        componentList = applyPostProcessing(componentClass, componentList);

        // If empty and has default, use default
        if (componentList.isEmpty()) {
            if (componentConfig.hasDefault()) {
                T defaultComponent = componentConfig.getDefault();
                // For Parser defaults, configure dependencies (encoding detector, renderer)
                if (componentClass == Parser.class && defaultComponent instanceof Parser) {
                    List<Parser> singletonList = new ArrayList<>();
                    singletonList.add((Parser) defaultComponent);
                    configureParserDependencies(singletonList);
                }
                return defaultComponent;
            }
            // For components that wrap empty lists
            if (componentConfig.hasListWrapper()) {
                return componentConfig.wrapList(componentList);
            }
            return null;
        }

        // Wrap the list if configured
        if (componentConfig.hasListWrapper()) {
            return componentConfig.wrapList(componentList);
        }

        // For single-value components
        return componentList.isEmpty() ? null : componentList.get(0);
    }

    /**
     * Applies post-processing to component lists.
     * Handles auto-exclusions and deferred creation for Parser, Detector, and EncodingDetector.
     * Also sets EncodingDetector and Renderer on parsers that implement the appropriate interfaces.
     */
    @SuppressWarnings("unchecked")
    private <T> List<T> applyPostProcessing(Class<T> componentClass, List<T> list) throws IOException {
        if (componentClass == Parser.class) {
            List<Parser> parsers = applyParserAutoExclusions((List<Parser>) list);
            configureParserDependencies(parsers);
            return (List<T>) parsers;
        } else if (componentClass == Detector.class) {
            return (List<T>) applyDetectorAutoExclusions((List<Detector>) list);
        } else if (componentClass == EncodingDetector.class) {
            return (List<T>) applyEncodingDetectorAutoExclusions((List<EncodingDetector>) list);
        }
        return list;
    }

    /**
     * Configures EncodingDetector and Renderer on all parsers.
     * Recursively walks through CompositeParser children (including DefaultParser).
     */
    private void configureParserDependencies(List<Parser> parsers) throws IOException {
        EncodingDetector encodingDetector = get(EncodingDetector.class);
        Renderer renderer = get(Renderer.class);

        for (Parser parser : parsers) {
            // Recursively configure all parsers including DefaultParser's children
            configureParserRecursively(parser, encodingDetector, renderer);
        }
    }

    /**
     * Recursively configures a parser and its children with EncodingDetector and Renderer.
     */
    private void configureParserRecursively(Parser parser, EncodingDetector encodingDetector,
                                            Renderer renderer) {
        if (encodingDetector != null && parser instanceof AbstractEncodingDetectorParser) {
            ((AbstractEncodingDetectorParser) parser).setEncodingDetector(encodingDetector);
        }
        if (renderer != null && parser instanceof RenderingParser) {
            ((RenderingParser) parser).setRenderer(renderer);
        }
        if (parser instanceof CompositeParser) {
            for (Parser child : ((CompositeParser) parser).getAllComponentParsers()) {
                configureParserRecursively(child, encodingDetector, renderer);
            }
        } else if (parser instanceof ParserDecorator) {
            configureParserRecursively(((ParserDecorator) parser).getWrappedParser(),
                    encodingDetector, renderer);
        }
    }

    // ==================== Component List Loading ====================

    /**
     * Loads a list of components from the JSON configuration.
     * <p>
     * DefaultParser and DefaultDetector are handled specially - their configs are stored
     * for deferred creation in post-processing to avoid double-creation when auto-exclusions
     * are needed.
     *
     * @param jsonField the JSON field name (e.g., "parsers", "detectors")
     * @param componentClass the component class
     * @return list of loaded components (may be empty, never null)
     */
    private <T> List<T> loadComponentList(String jsonField, Class<T> componentClass)
            throws TikaConfigException {
        List<Map.Entry<String, JsonNode>> entries = config.getArrayComponents(jsonField);

        if (entries.isEmpty()) {
            return new ArrayList<>();
        }

        List<T> components = new ArrayList<>();
        int index = 0;
        for (Map.Entry<String, JsonNode> entry : entries) {
            String typeName = entry.getKey();
            JsonNode configNode = entry.getValue();

            // Defer DefaultParser/DefaultDetector/DefaultEncodingDetector creation to post-processing
            // Track the index where it was defined to preserve ordering
            if ("default-parser".equals(typeName) && componentClass == Parser.class) {
                pendingDefaultParserConfig = configNode;
                pendingDefaultParserIndex = index;
                index++;
                continue;
            }
            if ("default-detector".equals(typeName) && componentClass == Detector.class) {
                pendingDefaultDetectorConfig = configNode;
                pendingDefaultDetectorIndex = index;
                index++;
                continue;
            }
            if ("default-encoding-detector".equals(typeName) && componentClass == EncodingDetector.class) {
                pendingDefaultEncodingDetectorConfig = configNode;
                pendingDefaultEncodingDetectorIndex = index;
                index++;
                continue;
            }

            try {
                // Create wrapper node: { "type-name": {...config...} }
                ObjectNode wrapperNode = objectMapper.createObjectNode();
                wrapperNode.set(typeName, configNode);

                // Deserialize using Jackson (TikaModule handles type resolution)
                T component = objectMapper.treeToValue(wrapperNode, componentClass);
                components.add(component);
                index++;
            } catch (Exception e) {
                throw new TikaConfigException(
                        "Failed to load " + componentClass.getSimpleName() + ": " + typeName, e);
            }
        }

        return components;
    }

    // ==================== Auto-Exclusion Post-Processing ====================

    /**
     * Creates DefaultParser (if configured) with config exclusions + auto-exclusions.
     * Auto-exclusions are the explicit parser types to prevent duplicates.
     * Inserts at the original position to preserve ordering.
     * Also applies mime filtering (_mime-include/_mime-exclude) if configured.
     * <p>
     * Note: EncodingDetector and Renderer are configured later in configureParserDependencies.
     */
    @SuppressWarnings("unchecked")
    private List<Parser> applyParserAutoExclusions(List<Parser> parsers) throws IOException {
        // If no DefaultParser was configured, just return the list
        if (pendingDefaultParserConfig == null) {
            return parsers;
        }

        // Parse exclusions from config
        Set<Class<? extends Parser>> exclusions = parseExclusions(pendingDefaultParserConfig, Parser.class);

        // Add auto-exclusions (explicit parser types)
        for (Parser p : parsers) {
            exclusions.add((Class<? extends Parser>) p.getClass());
        }

        // Create DefaultParser with all exclusions
        Parser defaultParser = new DefaultParser(
                getMediaTypeRegistry(),
                new ServiceLoader(classLoader),
                exclusions);

        // Apply mime filtering if configured
        Set<MediaType> includeTypes = extractMimeTypes(pendingDefaultParserConfig, "_mime-include");
        Set<MediaType> excludeTypes = extractMimeTypes(pendingDefaultParserConfig, "_mime-exclude");
        if (!includeTypes.isEmpty() || !excludeTypes.isEmpty()) {
            defaultParser = ParserDecorator.withMimeFilters(defaultParser, includeTypes, excludeTypes);
        }

        // Insert at original position to preserve ordering
        List<Parser> result = new ArrayList<>(parsers);
        int insertIndex = Math.min(pendingDefaultParserIndex, result.size());
        result.add(insertIndex, defaultParser);

        pendingDefaultParserConfig = null;
        pendingDefaultParserIndex = -1;
        return result;
    }

    /**
     * Extracts mime types from a config node field.
     */
    private Set<MediaType> extractMimeTypes(JsonNode configNode, String fieldName) {
        Set<MediaType> types = new HashSet<>();
        if (configNode == null || !configNode.has(fieldName)) {
            return types;
        }
        JsonNode arrayNode = configNode.get(fieldName);
        if (arrayNode.isArray()) {
            for (JsonNode typeNode : arrayNode) {
                types.add(MediaType.parse(typeNode.asText()));
            }
        }
        return types;
    }

    /**
     * Creates DefaultDetector (if configured) with config exclusions + auto-exclusions.
     * Auto-exclusions are the explicit detector types to prevent duplicates.
     * Inserts at the original position to preserve ordering.
     */
    @SuppressWarnings("unchecked")
    private List<Detector> applyDetectorAutoExclusions(List<Detector> detectors) throws IOException {
        // If no DefaultDetector was configured, just return the list
        if (pendingDefaultDetectorConfig == null) {
            return detectors;
        }

        // Parse exclusions from config
        Set<Class<? extends Detector>> exclusions = parseExclusions(pendingDefaultDetectorConfig, Detector.class);

        // Add auto-exclusions (explicit detector types)
        for (Detector d : detectors) {
            exclusions.add((Class<? extends Detector>) d.getClass());
        }

        // Create DefaultDetector with all exclusions
        DefaultDetector defaultDetector = new DefaultDetector(
                getMimeTypes(),
                new ServiceLoader(classLoader),
                exclusions);

        // Insert at original position to preserve ordering
        List<Detector> result = new ArrayList<>(detectors);
        int insertIndex = Math.min(pendingDefaultDetectorIndex, result.size());
        result.add(insertIndex, defaultDetector);

        pendingDefaultDetectorConfig = null;
        pendingDefaultDetectorIndex = -1;
        return result;
    }

    /**
     * Creates DefaultEncodingDetector (if configured) with config exclusions + auto-exclusions.
     * Auto-exclusions are the explicit encoding detector types to prevent duplicates.
     * Inserts at the original position to preserve ordering.
     */
    @SuppressWarnings("unchecked")
    private List<EncodingDetector> applyEncodingDetectorAutoExclusions(List<EncodingDetector> encodingDetectors)
            throws IOException {
        // If no DefaultEncodingDetector was configured, just return the list
        if (pendingDefaultEncodingDetectorConfig == null) {
            return encodingDetectors;
        }

        // Parse exclusions from config
        Set<Class<? extends EncodingDetector>> exclusions =
                parseExclusions(pendingDefaultEncodingDetectorConfig, EncodingDetector.class);

        // Add auto-exclusions (explicit encoding detector types)
        for (EncodingDetector ed : encodingDetectors) {
            exclusions.add((Class<? extends EncodingDetector>) ed.getClass());
        }

        // Create DefaultEncodingDetector with all exclusions
        DefaultEncodingDetector defaultEncodingDetector = new DefaultEncodingDetector(
                new ServiceLoader(classLoader),
                exclusions);

        // Insert at original position to preserve ordering
        List<EncodingDetector> result = new ArrayList<>(encodingDetectors);
        int insertIndex = Math.min(pendingDefaultEncodingDetectorIndex, result.size());
        result.add(insertIndex, defaultEncodingDetector);

        pendingDefaultEncodingDetectorConfig = null;
        pendingDefaultEncodingDetectorIndex = -1;
        return result;
    }

    /**
     * Parses exclusions from a config node.
     * Supports both "exclude" and "_exclude" field names.
     */
    @SuppressWarnings("unchecked")
    private <T> Set<Class<? extends T>> parseExclusions(JsonNode configNode, Class<T> componentClass)
            throws IOException {
        Set<Class<? extends T>> exclusions = new HashSet<>();

        if (configNode == null || !configNode.isObject()) {
            return exclusions;
        }

        JsonNode excludeNode = configNode.has("exclude") ?
                configNode.get("exclude") : configNode.get("_exclude");

        if (excludeNode != null && excludeNode.isArray()) {
            for (JsonNode item : excludeNode) {
                String typeName = item.asText();
                try {
                    Class<?> clazz = ComponentNameResolver.resolveClass(typeName, classLoader);
                    exclusions.add((Class<? extends T>) clazz);
                } catch (ClassNotFoundException e) {
                    throw new IOException("Unknown type in exclude list: " + typeName, e);
                }
            }
        }

        return exclusions;
    }

    // ==================== Serialization ====================

    /**
     * Saves the current configuration to a JSON file (pretty-printed).
     */
    public void save(File file) throws IOException {
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(file, buildOutputNode());
    }

    /**
     * Saves the current configuration to an output stream (pretty-printed).
     */
    public void save(OutputStream outputStream) throws IOException {
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(outputStream, buildOutputNode());
    }

    /**
     * Converts the current configuration to a JSON string (pretty-printed).
     */
    public String toJson() throws IOException {
        return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(buildOutputNode());
    }

    private ObjectNode buildOutputNode() throws IOException {
        ObjectNode output = objectMapper.createObjectNode();

        // Serialize loaded components from cache
        if (componentCache.containsKey(Parser.class)) {
            output.set("parsers", serializeComponent(componentCache.get(Parser.class), "parsers"));
        } else if (config.hasArrayComponents("parsers")) {
            output.set("parsers", config.getRootNode().get("parsers"));
        }

        if (componentCache.containsKey(Detector.class)) {
            output.set("detectors", serializeComponent(componentCache.get(Detector.class), "detectors"));
        } else if (config.hasArrayComponents("detectors")) {
            output.set("detectors", config.getRootNode().get("detectors"));
        }

        if (componentCache.containsKey(EncodingDetector.class)) {
            output.set("encoding-detectors", serializeComponent(componentCache.get(EncodingDetector.class), "encoding-detectors"));
        } else if (config.hasArrayComponents("encoding-detectors")) {
            output.set("encoding-detectors", config.getRootNode().get("encoding-detectors"));
        }

        Object metadataFilter = componentCache.get(MetadataFilter.class);
        if (metadataFilter != null && metadataFilter != NoOpFilter.NOOP_FILTER) {
            output.set("metadata-filters", serializeComponent(metadataFilter, "metadata-filters"));
        } else if (config.hasArrayComponents("metadata-filters")) {
            output.set("metadata-filters", config.getRootNode().get("metadata-filters"));
        }

        if (componentCache.containsKey(Renderer.class)) {
            output.set("renderers", serializeComponent(componentCache.get(Renderer.class), "renderers"));
        } else if (config.hasArrayComponents("renderers")) {
            output.set("renderers", config.getRootNode().get("renderers"));
        }

        // Preserve auto-detect-parser config if present
        JsonNode adpNode = config.getRootNode().get("auto-detect-parser");
        if (adpNode != null && !adpNode.isNull()) {
            output.set("auto-detect-parser", adpNode);
        }

        return output;
    }

    private JsonNode serializeComponent(Object component, String jsonField) throws IOException {
        Object toSerialize = unwrapForSerialization(component);
        if (toSerialize == null) {
            return objectMapper.createArrayNode();
        }
        return objectMapper.valueToTree(toSerialize);
    }

    @SuppressWarnings("unchecked")
    private Object unwrapForSerialization(Object component) {
        if (component instanceof CompositeParser cp) {
            Map<org.apache.tika.mime.MediaType, Parser> parserMap = cp.getParsers();
            // Get unique parsers from the map
            return new ArrayList<>(new HashSet<>(parserMap.values()));
        } else if (component instanceof CompositeDetector cd) {
            return cd.getDetectors();
        } else if (component instanceof CompositeMetadataFilter cmf) {
            return cmf.getFilters();
        } else if (component instanceof org.apache.tika.detect.CompositeEncodingDetector ced) {
            return ced.getDetectors();
        }
        // For types without accessor methods (like CompositeRenderer), return as-is
        return component;
    }
}
