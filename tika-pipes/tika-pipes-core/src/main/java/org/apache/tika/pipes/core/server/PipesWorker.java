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

import java.io.Closeable;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.Callable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentByteStoreExtractorFactory;
import org.apache.tika.extractor.EmbeddedDocumentBytesHandler;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.extractor.EmbeddedDocumentExtractorFactory;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.writefilter.MetadataWriteLimiterFactory;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.pipes.api.FetchEmitTuple;
import org.apache.tika.pipes.api.PipesResult;
import org.apache.tika.pipes.core.PipesResults;
import org.apache.tika.pipes.core.emitter.EmitterManager;
import org.apache.tika.pipes.core.extractor.EmittingEmbeddedDocumentBytesHandler;
import org.apache.tika.pipes.core.extractor.RUnpackExtractor;
import org.apache.tika.pipes.core.extractor.UnpackConfig;
import org.apache.tika.utils.ExceptionUtils;
import org.apache.tika.utils.StringUtils;

class PipesWorker implements Callable<PipesResult> {

    private static final Logger LOG = LoggerFactory.getLogger(PipesWorker.class);

    private final FetchEmitTuple fetchEmitTuple;
    private final ParseContext parseContext;
    private final AutoDetectParser autoDetectParser;
    private final EmitterManager emitterManager;
    private final FetchHandler fetchHandler;
    private final ParseHandler parseHandler;
    private final EmitHandler emitHandler;
    private final MetadataWriteLimiterFactory defaultMetadataWriteLimiterFactory;

    public PipesWorker(FetchEmitTuple fetchEmitTuple, ParseContext parseContext, AutoDetectParser autoDetectParser,
                       EmitterManager emitterManager, FetchHandler fetchHandler, ParseHandler parseHandler,
                       EmitHandler emitHandler, MetadataWriteLimiterFactory defaultMetadataWriteLimiterFactory) {
        this.fetchEmitTuple = fetchEmitTuple;
        this.parseContext = parseContext;
        this.autoDetectParser = autoDetectParser;
        this.emitterManager = emitterManager;
        this.fetchHandler = fetchHandler;
        this.parseHandler = parseHandler;
        this.emitHandler = emitHandler;
        this.defaultMetadataWriteLimiterFactory = defaultMetadataWriteLimiterFactory;
    }

    @Override
    public PipesResult call() throws Exception {
        MetadataListAndEmbeddedBytes parseData = null;
        try {
            //this can be null if there is a fetch exception
            ParseDataOrPipesResult parseDataResult = parseFromTuple();

            if (parseDataResult.pipesResult != null) {
                return parseDataResult.pipesResult;
            }

            parseData = parseDataResult.parseDataResult;

            if (parseData == null || metadataIsEmpty(parseData.getMetadataList())) {
                return PipesResults.EMPTY_OUTPUT;
            }
            return emitHandler.emitParseData(fetchEmitTuple, parseData, parseContext);
        } finally {
            if (parseData != null && parseData.hasEmbeddedDocumentByteStore() &&
                    parseData.getEmbeddedDocumentBytesHandler() instanceof Closeable) {
                try {
                    ((Closeable) parseData.getEmbeddedDocumentBytesHandler()).close();
                } catch (IOException e) {
                    LOG.warn("problem closing embedded document byte store", e);
                }
            }
        }
    }


    static boolean metadataIsEmpty(List<Metadata> metadataList) {
        return metadataList == null || metadataList.isEmpty();
    }


    protected ParseDataOrPipesResult parseFromTuple() throws TikaException, InterruptedException {
        //start a new metadata object to gather info from the fetch process
        //we want to isolate and not touch the metadata sent into the fetchEmitTuple
        //so that we can inject it after the filter at the very end
        ParseContext localContext = null;
        try {
            localContext = setupParseContext();
        } catch (IOException e) {
            LOG.warn("fetcher initialization exception id={}", fetchEmitTuple.getId(), e);
            return new ParseDataOrPipesResult(null,
                    new PipesResult(PipesResult.RESULT_STATUS.FETCHER_INITIALIZATION_EXCEPTION, ExceptionUtils.getStackTrace(e)));
        }
        // Use newMetadata() to apply any configured write limits
        Metadata metadata = localContext.newMetadata();
        FetchHandler.TisOrResult tisOrResult = fetchHandler.fetch(fetchEmitTuple, metadata, localContext);
        if (tisOrResult.pipesResult() != null) {
            return new ParseDataOrPipesResult(null, tisOrResult.pipesResult());
        }

        try (TikaInputStream tis = tisOrResult.tis()) {
            return parseHandler.parseWithStream(fetchEmitTuple, tis, metadata, localContext);
        } catch (SecurityException e) {
            LOG.error("security exception id={}", fetchEmitTuple.getId(), e);
            throw e;
        } catch (TikaException | IOException e) {
            LOG.warn("fetch exception id={}", fetchEmitTuple.getId(), e);
            return new ParseDataOrPipesResult(null,
                    new PipesResult(PipesResult.RESULT_STATUS.UNSPECIFIED_CRASH, ExceptionUtils.getStackTrace(e)));
        }
    }



    private ParseContext setupParseContext() throws TikaException, IOException {
        // ContentHandlerFactory and ParseMode are retrieved from ParseContext in ParseHandler.
        // They are set in ParseContext from PipesConfig loaded via TikaLoader at startup.

        // If the parseContext from the FetchEmitTuple doesn't have a MetadataWriteLimiterFactory,
        // use the default one loaded from config in PipesServer
        MetadataWriteLimiterFactory existingFactory = parseContext.get(MetadataWriteLimiterFactory.class);
        if (existingFactory == null && defaultMetadataWriteLimiterFactory != null) {
            parseContext.set(MetadataWriteLimiterFactory.class, defaultMetadataWriteLimiterFactory);
        }

        UnpackConfig unpackConfig = parseContext.get(UnpackConfig.class);
        if (unpackConfig == null) {
            //make sure there's one here -- or do we make this default in fetchemit tuple?
            parseContext.set(UnpackConfig.class, UnpackConfig.SKIP);
            return parseContext;
        }
        EmbeddedDocumentExtractorFactory factory = parseContext.get(EmbeddedDocumentExtractorFactory.class);
        if (factory == null) {
            parseContext.set(EmbeddedDocumentExtractor.class,
                    new RUnpackExtractor(parseContext, Long.MAX_VALUE));
        } else {
            if (!(factory instanceof EmbeddedDocumentByteStoreExtractorFactory)) {
                throw new TikaConfigException("EmbeddedDocumentExtractorFactory must be an " +
                        "instance of EmbeddedDocumentByteStoreExtractorFactory if you want " +
                        "to extract embedded bytes! I see this embedded doc factory: " +
                        factory.getClass() + " and a request: " +
                        unpackConfig);
            }
        }
        // Only set up embedded document bytes handler if an emitter is configured
        if (!StringUtils.isBlank(unpackConfig.getEmitter())) {
            parseContext.set(EmbeddedDocumentBytesHandler.class,
                    new EmittingEmbeddedDocumentBytesHandler(fetchEmitTuple, emitterManager));
        }

        return parseContext;
    }

    //parse data result or a terminal pipesresult
    record ParseDataOrPipesResult(MetadataListAndEmbeddedBytes parseDataResult, PipesResult pipesResult) {

    }


}
