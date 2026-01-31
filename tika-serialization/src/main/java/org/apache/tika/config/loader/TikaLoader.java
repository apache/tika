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
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.apache.tika.config.GlobalSettings;
import org.apache.tika.detect.CompositeDetector;
import org.apache.tika.detect.CompositeEncodingDetector;
import org.apache.tika.detect.Detector;
import org.apache.tika.detect.EncodingDetector;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.language.translate.DefaultTranslator;
import org.apache.tika.language.translate.Translator;
import org.apache.tika.metadata.filter.CompositeMetadataFilter;
import org.apache.tika.metadata.filter.MetadataFilter;
import org.apache.tika.metadata.filter.NoOpFilter;
import org.apache.tika.mime.MediaTypeRegistry;
import org.apache.tika.mime.MimeTypes;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.AutoDetectParserConfig;
import org.apache.tika.parser.CompositeParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.renderer.CompositeRenderer;
import org.apache.tika.renderer.Renderer;
import org.apache.tika.sax.BasicContentHandlerFactory;
import org.apache.tika.sax.ContentHandlerFactory;
import org.apache.tika.serialization.ComponentConfig;
import org.apache.tika.serialization.ComponentNameResolver;
import org.apache.tika.serialization.JsonMetadata;
import org.apache.tika.serialization.JsonMetadataList;
import org.apache.tika.serialization.ParseContextUtils;
import org.apache.tika.serialization.serdes.ParseContextDeserializer;

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
        // Complex components with custom loaders
        ComponentConfig.builder("parsers", Parser.class)
                .customLoader(new ParserLoader())
                .register();

        ComponentConfig.builder("detectors", Detector.class)
                .customLoader(new DetectorLoader())
                .register();

        ComponentConfig.builder("encoding-detectors", EncodingDetector.class)
                .customLoader(new EncodingDetectorLoader())
                .register();

        // Simple components with default list-based loading
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

    // Lazy-initialized loader context
    private LoaderContext loaderContext;

    // Special cached instances that aren't standard components
    private Parser autoDetectParser;
    private Detector detectors;
    private EncodingDetector encodingDetectors;
    private MetadataFilter metadataFilter;
    private ContentHandlerFactory contentHandlerFactory;
    private Renderer renderers;
    private Translator translator;
    private ConfigLoader configLoader;
    private GlobalSettings globalSettings;

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
        return get(Parser.class);
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
        return get(Detector.class);
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
        return get(EncodingDetector.class);
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
        return get(MetadataFilter.class);
    }

    /**
     * Loads and returns the content handler factory.
     * If "content-handler-factory" section exists in config, uses that factory.
     * If section missing, returns a default BasicContentHandlerFactory with TEXT handler.
     * Results are cached - subsequent calls return the same instance.
     *
     * <p>Example JSON:
     * <pre>
     * {
     *   "content-handler-factory": {
     *     "basic-content-handler-factory": {
     *       "type": "HTML",
     *       "writeLimit": 100000
     *     }
     *   }
     * }
     * </pre>
     *
     * @return the content handler factory
     * @throws TikaConfigException if loading fails
     */
    public synchronized ContentHandlerFactory loadContentHandlerFactory() throws TikaConfigException {
        if (contentHandlerFactory == null) {
            // Check if content-handler-factory section exists in config
            if (config.hasComponentSection("content-handler-factory")) {
                try {
                    contentHandlerFactory = config.deserialize("content-handler-factory",
                            ContentHandlerFactory.class);
                } catch (IOException e) {
                    throw new TikaConfigException("Failed to load content-handler-factory", e);
                }
            }
            // Default to BasicContentHandlerFactory with TEXT handler if not configured
            if (contentHandlerFactory == null) {
                contentHandlerFactory = new BasicContentHandlerFactory(
                        BasicContentHandlerFactory.HANDLER_TYPE.TEXT, -1);
            }
        }
        return contentHandlerFactory;
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
        return get(Renderer.class);
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
        return get(Translator.class);
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
            // Load directly from root-level config (not via configs() which only looks in "parse-context")
            AutoDetectParserConfig adpConfig = loadAutoDetectParserConfig();
            if (adpConfig == null) {
                adpConfig = new AutoDetectParserConfig();
            }
            autoDetectParser = AutoDetectParser.build((CompositeParser)loadParsers(), loadDetectors(), adpConfig);
        }
        return autoDetectParser;
    }

    /**
     * Loads and returns a ParseContext populated with components from the "parse-context" section.
     * <p>
     * This method deserializes the parse-context JSON and resolves all component references
     * using the component registry. Components are looked up by their friendly names
     * (e.g., "embedded-limits", "pdf-parser-config") and deserialized to their appropriate types.
     * <p>
     * Use this method when you need a pre-configured ParseContext for parsing operations.
     *
     * <p>Example usage:
     * <pre>
     * TikaLoader loader = TikaLoader.load(configPath);
     * Parser parser = loader.loadAutoDetectParser();
     * ParseContext context = loader.loadParseContext();
     * Metadata metadata = Metadata.newInstance(context);
     * parser.parse(stream, handler, metadata, context);
     * </pre>
     *
     * @return a ParseContext populated with configured components
     * @throws TikaConfigException if loading fails
     */
    public ParseContext loadParseContext() throws TikaConfigException {
        JsonNode parseContextNode = config.getRootNode().get("parse-context");
        if (parseContextNode == null) {
            return new ParseContext();
        }
        try {
            ParseContext context =
                    ParseContextDeserializer.readParseContext(parseContextNode, objectMapper);
            ParseContextUtils.resolveAll(context, classLoader);
            return context;
        } catch (IOException e) {
            throw new TikaConfigException("Failed to load parse-context", e);
        }
    }

    /**
     * Loads a configuration object from the "parse-context" section, merging with defaults.
     * <p>
     * This method is useful when you have a base configuration (e.g., from code defaults or
     * a previous load) and want to overlay values from the JSON config. Properties not
     * specified in the JSON retain their default values.
     * <p>
     * The original defaults object is NOT modified - a new instance is returned.
     *
     * <p>Example usage for PDFParserConfig:
     * <pre>
     * // Load base config from tika-config.json at init time
     * TikaLoader loader = TikaLoader.load(configPath);
     * PDFParserConfig baseConfig = loader.loadConfig(PDFParserConfig.class, new PDFParserConfig());
     *
     * // At runtime, create per-request overrides
     * PDFParserConfig requestConfig = new PDFParserConfig();
     * requestConfig.setOcrStrategy(PDFParserConfig.OCR_STRATEGY.NO_OCR);
     *
     * // Merge: base config values + request overrides
     * // (Note: for runtime merging, use JsonMergeUtils directly or loadConfig on a runtime loader)
     * </pre>
     *
     * @param clazz the class to deserialize into
     * @param defaults the default values to use for properties not in the JSON config
     * @param <T> the configuration type
     * @return a new instance with defaults merged with JSON config, or the original defaults if not configured
     * @throws TikaConfigException if loading fails
     */
    public <T> T loadConfig(Class<T> clazz, T defaults) throws TikaConfigException {
        return configs().loadWithDefaults(clazz, defaults);
    }

    /**
     * Loads a configuration object from the "parse-context" section by explicit key, merging with defaults.
     * <p>
     * This method is useful when the JSON key doesn't match the class name's kebab-case conversion,
     * or when you want to load from a specific key.
     *
     * @param key the JSON key in the "parse-context" section
     * @param clazz the class to deserialize into
     * @param defaults the default values to use for properties not in the JSON config
     * @param <T> the configuration type
     * @return a new instance with defaults merged with JSON config, or the original defaults if not configured
     * @throws TikaConfigException if loading fails
     */
    public <T> T loadConfig(String key, Class<T> clazz, T defaults) throws TikaConfigException {
        return configs().loadWithDefaults(key, clazz, defaults);
    }

    /**
     * Returns a ConfigLoader for loading simple configuration objects.
     * <p>
     * This is internal - external code should use {@link #loadParseContext()} or
     * {@link #loadConfig(Class, Object)} instead.
     *
     * @return the ConfigLoader instance
     */
    private synchronized ConfigLoader configs() {
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
     * @throws TikaConfigException if loading fails
     */
    @SuppressWarnings("unchecked")
    public <T> T get(Class<T> componentClass) throws TikaConfigException {
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

        // Load the component
        T component = loadComponent(componentConfig);

        // Cache and return
        if (component != null) {
            componentCache.put(componentClass, component);
        }
        return component;
    }

    /**
     * Gets a component by its JSON field name.
     * Components are loaded lazily and cached.
     *
     * @param jsonField the JSON field name (e.g., "parsers", "detectors")
     * @return the loaded component
     * @throws TikaConfigException if loading fails
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String jsonField) throws TikaConfigException {
        // Get component config from registry by field name
        ComponentConfig<?> componentConfig = ComponentNameResolver.getComponentConfig(jsonField);
        if (componentConfig == null) {
            throw new IllegalArgumentException("No component registered for field: " + jsonField);
        }

        // Delegate to get by class (which handles caching)
        return (T) get(componentConfig.getComponentClass());
    }

    /**
     * Load a component using its configuration.
     * Delegates to custom loader if available, otherwise uses default list-based loading.
     */
    private <T> T loadComponent(ComponentConfig<T> componentConfig) throws TikaConfigException {
        if (componentConfig.hasCustomLoader()) {
            // Use custom loader for complex components
            return componentConfig.getCustomLoader().load(config, getLoaderContext());
        } else {
            // Use default list-based loading for simple components
            return loadDefaultComponent(componentConfig);
        }
    }

    /**
     * Default loading for simple components.
     * No special handling - just deserialize, wrap, done.
     */
    private <T> T loadDefaultComponent(ComponentConfig<T> componentConfig) throws TikaConfigException {
        List<T> components = loadComponentList(
                componentConfig.getJsonField(),
                componentConfig.getComponentClass());

        if (components.isEmpty()) {
            return componentConfig.hasDefault()
                    ? componentConfig.getDefault()
                    : null;
        }

        return componentConfig.hasListWrapper()
                ? componentConfig.wrapList(components)
                : components.get(0);
    }

    /**
     * Get the loader context, creating it lazily.
     */
    private synchronized LoaderContext getLoaderContext() {
        if (loaderContext == null) {
            loaderContext = new LoaderContext(classLoader, objectMapper, this::get);
        }
        return loaderContext;
    }

    // ==================== Component List Loading ====================

    /**
     * Loads a list of components from the JSON configuration.
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
        for (Map.Entry<String, JsonNode> entry : entries) {
            String typeName = entry.getKey();
            JsonNode configNode = entry.getValue();

            try {
                // Create wrapper node: { "type-name": {...config...} }
                ObjectNode wrapperNode = objectMapper.createObjectNode();
                wrapperNode.set(typeName, configNode);

                // Deserialize using Jackson (TikaModule handles type resolution)
                T component = objectMapper.treeToValue(wrapperNode, componentClass);
                components.add(component);
            } catch (Exception e) {
                throw new TikaConfigException(
                        "Failed to load " + componentClass.getSimpleName() + ": " + typeName, e);
            }
        }

        return components;
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
        } else if (component instanceof CompositeEncodingDetector ced) {
            return ced.getDetectors();
        }
        // For types without accessor methods (like CompositeRenderer), return as-is
        return component;
    }
}
