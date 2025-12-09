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

import static org.apache.tika.pipes.api.pipesiterator.PipesIteratorBaseConfig.DEFAULT_HANDLER_CONFIG;

import java.io.Closeable;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
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
import org.apache.tika.extractor.RUnpackExtractor;
import org.apache.tika.extractor.RUnpackExtractorFactory;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.pipes.api.FetchEmitTuple;
import org.apache.tika.pipes.api.HandlerConfig;
import org.apache.tika.pipes.api.PipesResult;
import org.apache.tika.pipes.core.PipesResults;
import org.apache.tika.pipes.core.emitter.EmitterManager;
import org.apache.tika.pipes.core.extractor.BasicEmbeddedDocumentBytesHandler;
import org.apache.tika.pipes.core.extractor.EmbeddedDocumentBytesConfig;
import org.apache.tika.pipes.core.extractor.EmittingEmbeddedDocumentBytesHandler;
import org.apache.tika.utils.ExceptionUtils;
import org.apache.tika.utils.StringUtils;

class PipesWorker implements Callable<PipesResult> {

    private static final Logger LOG = LoggerFactory.getLogger(PipesWorker.class);

    public enum EMIT_STRATEGY {
        EMIT_ALL,
        PASSBACK_ALL,
        DYNAMIC
    }

    private final FetchEmitTuple fetchEmitTuple;
    private final AutoDetectParser autoDetectParser;
    private final EmitterManager emitterManager;
    private final FetchHandler fetchHandler;
    private final ParseHandler parseHandler;
    private final EmitHandler emitHandler;

    public PipesWorker(FetchEmitTuple fetchEmitTuple, AutoDetectParser autoDetectParser, EmitterManager emitterManager, FetchHandler fetchHandler, ParseHandler parseHandler,
                       EmitHandler emitHandler) {
        this.fetchEmitTuple = fetchEmitTuple;
        this.autoDetectParser = autoDetectParser;
        this.emitterManager = emitterManager;
        this.fetchHandler = fetchHandler;
        this.parseHandler = parseHandler;
        this.emitHandler = emitHandler;
    }

    @Override
    public PipesResult call() throws Exception {
        Instant start = Instant.now();

        if (LOG.isTraceEnabled()) {
            LOG.trace("timer -- got fetcher: {}ms", Duration.between(start, Instant.now()).toMillis());
        }
        start = Instant.now();
        MetadataListAndEmbeddedBytes parseData = null;
        try {
            //this can be null if there is a fetch exception
            ParseDataOrPipesResult parseDataResult = parseFromTuple();
            if (parseDataResult.pipesResult != null) {
                return parseDataResult.pipesResult;
            }

            if (LOG.isTraceEnabled()) {
                LOG.trace("timer -- to parse: {} ms", Duration.between(start, Instant.now()).toMillis());
            }
            parseData = parseDataResult.parseDataResult;

            if (parseData == null || metadataIsEmpty(parseData.getMetadataList())) {
                return PipesResults.EMPTY_OUTPUT;
            }
            return emitHandler.emitParseData(fetchEmitTuple, parseData);
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
        Metadata metadata = new Metadata();
        FetchHandler.TisOrResult tisOrResult = fetchHandler.fetch(fetchEmitTuple, metadata);
        if (tisOrResult.pipesResult() != null) {
            return new ParseDataOrPipesResult(null, tisOrResult.pipesResult());
        }

        ParseContext parseContext = setupParseContext(fetchEmitTuple);
        try (TikaInputStream tis = tisOrResult.tis()) {
            return parseHandler.parseWithStream(fetchEmitTuple, tis, metadata, parseContext);
        } catch (SecurityException e) {
            LOG.error("security exception id={}", fetchEmitTuple.getId(), e);
            throw e;
        } catch (TikaException | IOException e) {
            LOG.warn("fetch exception id={}", fetchEmitTuple.getId(), e);
            return new ParseDataOrPipesResult(null,
                    new PipesResult(PipesResult.RESULT_STATUS.UNSPECIFIED_CRASH, ExceptionUtils.getStackTrace(e)));
        }
    }



    private ParseContext setupParseContext(FetchEmitTuple fetchEmitTuple)
            throws TikaConfigException {
        ParseContext parseContext = fetchEmitTuple.getParseContext();
        if (parseContext.get(HandlerConfig.class) == null) {
            parseContext.set(HandlerConfig.class, DEFAULT_HANDLER_CONFIG);
        }
        EmbeddedDocumentBytesConfig embeddedDocumentBytesConfig = parseContext.get(EmbeddedDocumentBytesConfig.class);
        if (embeddedDocumentBytesConfig == null) {
            //make sure there's one here -- or do we make this default in fetchemit tuple?
            parseContext.set(EmbeddedDocumentBytesConfig.class, EmbeddedDocumentBytesConfig.SKIP);
            return parseContext;
        }
        EmbeddedDocumentExtractorFactory factory = autoDetectParser
                .getAutoDetectParserConfig().getEmbeddedDocumentExtractorFactory();
        if (factory == null) {
            parseContext.set(EmbeddedDocumentExtractor.class, new RUnpackExtractor(parseContext,
                    RUnpackExtractorFactory.DEFAULT_MAX_EMBEDDED_BYTES_FOR_EXTRACTION));
        } else {
            if (! (factory instanceof EmbeddedDocumentByteStoreExtractorFactory)) {
                throw new TikaConfigException("EmbeddedDocumentExtractorFactory must be an " +
                        "instance of EmbeddedDocumentByteStoreExtractorFactory if you want" +
                        "to extract embedded bytes! I see this embedded doc factory: " +
                        factory.getClass() + "and a request: " +
                        embeddedDocumentBytesConfig);
            }
        }
        //TODO: especially clean this up.
        if (!StringUtils.isBlank(embeddedDocumentBytesConfig.getEmitter())) {
            parseContext.set(EmbeddedDocumentBytesHandler.class,
                    new EmittingEmbeddedDocumentBytesHandler(fetchEmitTuple, emitterManager));
        } else {
            parseContext.set(EmbeddedDocumentBytesHandler.class,
                    new BasicEmbeddedDocumentBytesHandler(embeddedDocumentBytesConfig));
        }
        return parseContext;
    }

    //parse data result or a terminal pipesresult
    record ParseDataOrPipesResult(MetadataListAndEmbeddedBytes parseDataResult, PipesResult pipesResult) {

    }


}
