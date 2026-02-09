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
package org.apache.tika.pipes.core.server;

import java.io.IOException;

import org.xml.sax.SAXException;

import org.apache.tika.config.loader.TikaJsonConfig;
import org.apache.tika.config.loader.TikaLoader;
import org.apache.tika.detect.Detector;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentExtractorFactory;
import org.apache.tika.metadata.filter.MetadataFilter;
import org.apache.tika.metadata.writefilter.MetadataWriteLimiterFactory;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.RecursiveParserWrapper;
import org.apache.tika.pipes.core.EmitStrategy;
import org.apache.tika.pipes.core.PipesConfig;
import org.apache.tika.pipes.core.config.ConfigStore;
import org.apache.tika.pipes.core.config.ConfigStoreFactory;
import org.apache.tika.pipes.core.emitter.EmitterManager;
import org.apache.tika.pipes.core.extractor.UnpackExtractorFactory;
import org.apache.tika.pipes.core.fetcher.FetcherManager;
import org.apache.tika.plugins.ExtensionConfig;
import org.apache.tika.plugins.TikaPluginManager;
import org.apache.tika.sax.ContentHandlerFactory;

/**
 * Holds shared resources for a shared PipesServer.
 * <p>
 * These resources are thread-safe and shared among all ConnectionHandlers:
 * <ul>
 *   <li>AutoDetectParser, Detector - immutable after initialization</li>
 *   <li>FetcherManager, EmitterManager - use ConcurrentHashMap internally</li>
 *   <li>MetadataFilter, ContentHandlerFactory - stateless</li>
 * </ul>
 */
public class SharedServerResources {

    private final TikaLoader tikaLoader;
    private final PipesConfig pipesConfig;
    private final AutoDetectParser autoDetectParser;
    private final Detector detector;
    private final RecursiveParserWrapper rMetaParser;
    private final FetcherManager fetcherManager;
    private final EmitterManager emitterManager;
    private final MetadataFilter defaultMetadataFilter;
    private final ContentHandlerFactory defaultContentHandlerFactory;
    private final MetadataWriteLimiterFactory defaultMetadataWriteLimiterFactory;
    private final EmitStrategy emitStrategy;
    private final ConfigStore configStore;

    private SharedServerResources(TikaLoader tikaLoader, PipesConfig pipesConfig,
                                  AutoDetectParser autoDetectParser, Detector detector,
                                  RecursiveParserWrapper rMetaParser, FetcherManager fetcherManager,
                                  EmitterManager emitterManager, MetadataFilter defaultMetadataFilter,
                                  ContentHandlerFactory defaultContentHandlerFactory,
                                  MetadataWriteLimiterFactory defaultMetadataWriteLimiterFactory,
                                  EmitStrategy emitStrategy, ConfigStore configStore) {
        this.tikaLoader = tikaLoader;
        this.pipesConfig = pipesConfig;
        this.autoDetectParser = autoDetectParser;
        this.detector = detector;
        this.rMetaParser = rMetaParser;
        this.fetcherManager = fetcherManager;
        this.emitterManager = emitterManager;
        this.defaultMetadataFilter = defaultMetadataFilter;
        this.defaultContentHandlerFactory = defaultContentHandlerFactory;
        this.defaultMetadataWriteLimiterFactory = defaultMetadataWriteLimiterFactory;
        this.emitStrategy = emitStrategy;
        this.configStore = configStore;
    }

    /**
     * Loads shared server resources from configuration.
     *
     * @param tikaLoader the tika loader
     * @param pipesConfig the pipes configuration
     * @return initialized shared resources
     * @throws TikaException if initialization fails
     * @throws IOException if I/O error occurs
     * @throws SAXException if XML parsing error occurs
     */
    public static SharedServerResources load(TikaLoader tikaLoader, PipesConfig pipesConfig)
            throws TikaException, IOException, SAXException {

        TikaJsonConfig tikaJsonConfig = tikaLoader.getConfig();
        TikaPluginManager tikaPluginManager = TikaPluginManager.load(tikaJsonConfig);

        // Create ConfigStore if specified
        ConfigStore configStore = createConfigStore(pipesConfig, tikaPluginManager);

        // Load managers
        FetcherManager fetcherManager = FetcherManager.load(tikaPluginManager, tikaJsonConfig, true, configStore);
        EmitterManager emitterManager = EmitterManager.load(tikaPluginManager, tikaJsonConfig, true, configStore);

        // Load parser and detector
        AutoDetectParser autoDetectParser = (AutoDetectParser) tikaLoader.loadAutoDetectParser();
        Detector detector = autoDetectParser.getDetector();
        RecursiveParserWrapper rMetaParser = new RecursiveParserWrapper(autoDetectParser);

        // Load filters and factories
        MetadataFilter metadataFilter = tikaLoader.loadMetadataFilters();
        ContentHandlerFactory contentHandlerFactory = tikaLoader.loadContentHandlerFactory();
        MetadataWriteLimiterFactory metadataWriteLimiterFactory =
                tikaLoader.loadParseContext().get(MetadataWriteLimiterFactory.class);

        EmitStrategy emitStrategy = pipesConfig.getEmitStrategy().getType();

        return new SharedServerResources(tikaLoader, pipesConfig, autoDetectParser, detector,
                rMetaParser, fetcherManager, emitterManager, metadataFilter, contentHandlerFactory,
                metadataWriteLimiterFactory, emitStrategy, configStore);
    }

    private static ConfigStore createConfigStore(PipesConfig pipesConfig, TikaPluginManager tikaPluginManager)
            throws TikaException {
        String configStoreType = pipesConfig.getConfigStoreType();
        String configStoreParams = pipesConfig.getConfigStoreParams();

        if (configStoreType == null || "memory".equals(configStoreType)) {
            return null;
        }

        ExtensionConfig storeConfig = new ExtensionConfig(
                configStoreType, configStoreType, configStoreParams);

        return ConfigStoreFactory.createConfigStore(
                tikaPluginManager,
                configStoreType,
                storeConfig);
    }

    /**
     * Creates a merged ParseContext with defaults from tika-config overlaid with request values.
     *
     * @param requestContext the ParseContext from FetchEmitTuple
     * @return a new ParseContext with defaults + request overrides
     */
    public ParseContext createMergedParseContext(ParseContext requestContext) throws TikaConfigException {
        ParseContext mergedContext = tikaLoader.loadParseContext();
        if (mergedContext.get(EmbeddedDocumentExtractorFactory.class) == null) {
            mergedContext.set(EmbeddedDocumentExtractorFactory.class, new UnpackExtractorFactory());
        }
        mergedContext.copyFrom(requestContext);
        return mergedContext;
    }

    public TikaLoader getTikaLoader() {
        return tikaLoader;
    }

    public PipesConfig getPipesConfig() {
        return pipesConfig;
    }

    public AutoDetectParser getAutoDetectParser() {
        return autoDetectParser;
    }

    public Detector getDetector() {
        return detector;
    }

    public RecursiveParserWrapper getRMetaParser() {
        return rMetaParser;
    }

    public FetcherManager getFetcherManager() {
        return fetcherManager;
    }

    public EmitterManager getEmitterManager() {
        return emitterManager;
    }

    public MetadataFilter getDefaultMetadataFilter() {
        return defaultMetadataFilter;
    }

    public ContentHandlerFactory getDefaultContentHandlerFactory() {
        return defaultContentHandlerFactory;
    }

    public MetadataWriteLimiterFactory getDefaultMetadataWriteLimiterFactory() {
        return defaultMetadataWriteLimiterFactory;
    }

    public EmitStrategy getEmitStrategy() {
        return emitStrategy;
    }

    public ConfigStore getConfigStore() {
        return configStore;
    }
}
