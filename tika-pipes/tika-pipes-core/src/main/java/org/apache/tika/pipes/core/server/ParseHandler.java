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
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import org.apache.tika.detect.Detector;
import org.apache.tika.digest.Digester;
import org.apache.tika.digest.SkipContainerDocumentDigest;
import org.apache.tika.exception.EncryptedDocumentException;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.extractor.DocumentSelector;
import org.apache.tika.extractor.EmbeddedDocumentBytesHandler;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.RecursiveParserWrapper;
import org.apache.tika.pipes.api.FetchEmitTuple;
import org.apache.tika.pipes.api.HandlerConfig;
import org.apache.tika.pipes.core.extractor.EmbeddedDocumentBytesConfig;
import org.apache.tika.sax.BasicContentHandlerFactory;
import org.apache.tika.sax.ContentHandlerFactory;
import org.apache.tika.sax.RecursiveParserWrapperHandler;
import org.apache.tika.utils.ExceptionUtils;

class ParseHandler {

    private static final Logger LOG = LoggerFactory.getLogger(ParseHandler.class);

    private final Detector detector;
    private final Digester digester;
    private final ArrayBlockingQueue<Metadata> intermediateResult;
    private final CountDownLatch countDownLatch;
    private final AutoDetectParser autoDetectParser;
    private final RecursiveParserWrapper recursiveParserWrapper;


    ParseHandler(Detector detector, Digester digester, ArrayBlockingQueue<Metadata> intermediateResult,
                 CountDownLatch countDownLatch, AutoDetectParser autoDetectParser,
                 RecursiveParserWrapper recursiveParserWrapper) {
        this.detector = detector;
        this.digester = digester;
        this.intermediateResult = intermediateResult;
        this.countDownLatch = countDownLatch;
        this.autoDetectParser = autoDetectParser;
        this.recursiveParserWrapper = recursiveParserWrapper;
    }

    PipesWorker.ParseDataOrPipesResult parseWithStream(FetchEmitTuple fetchEmitTuple, TikaInputStream stream, Metadata metadata, ParseContext parseContext)
            throws TikaConfigException, InterruptedException {

        List<Metadata> metadataList;
        //this adds the EmbeddedDocumentByteStore to the parsecontext
        HandlerConfig handlerConfig = parseContext.get(HandlerConfig.class);
        if (handlerConfig.getParseMode() == HandlerConfig.PARSE_MODE.RMETA) {
            metadataList =
                    parseRecursive(fetchEmitTuple, handlerConfig, stream, metadata, parseContext);
        } else {
            metadataList = parseConcatenated(fetchEmitTuple, handlerConfig, stream, metadata,
                    parseContext);
        }

        return new PipesWorker.ParseDataOrPipesResult(new MetadataListAndEmbeddedBytes(metadataList,
                parseContext.get(EmbeddedDocumentBytesHandler.class)), null);
    }



    private void _preParse(FetchEmitTuple t, TikaInputStream tis, Metadata metadata,
                           ParseContext parseContext) {
        if (digester != null) {
            try {
                digester.digest(tis, metadata, parseContext);
                // Mark that we've already digested the container document so AutoDetectParser
                // won't re-digest it during parsing
                parseContext.set(SkipContainerDocumentDigest.class,
                        SkipContainerDocumentDigest.INSTANCE);
            } catch (IOException e) {
                LOG.warn("problem digesting: " + t.getId(), e);
            }
        }
        try {
            MediaType mt = detector.detect(tis, metadata, parseContext);
            metadata.set(Metadata.CONTENT_TYPE, mt.toString());
            metadata.set(TikaCoreProperties.CONTENT_TYPE_PARSER_OVERRIDE, mt.toString());
        } catch (IOException e) {
            LOG.warn("problem detecting: " + t.getId(), e);
        }
        EmbeddedDocumentBytesConfig embeddedDocumentBytesConfig = parseContext.get(EmbeddedDocumentBytesConfig.class);
        if (embeddedDocumentBytesConfig != null &&
                embeddedDocumentBytesConfig.isIncludeOriginal()) {
            EmbeddedDocumentBytesHandler embeddedDocumentByteStore = parseContext.get(EmbeddedDocumentBytesHandler.class);
            try (InputStream is = Files.newInputStream(tis.getPath())) {
                embeddedDocumentByteStore.add(0, metadata, is);
            } catch (IOException e) {
                LOG.warn("problem reading source file into embedded document byte store", e);
            }
        }
    }

    private Metadata preParse(FetchEmitTuple t, TikaInputStream tis, Metadata metadata,
                          ParseContext parseContext) {
        _preParse(t, tis, metadata, parseContext);
        return metadata;
    }

    public List<Metadata> parseRecursive(FetchEmitTuple fetchEmitTuple,
                                              HandlerConfig handlerConfig, TikaInputStream stream,
                                              Metadata metadata, ParseContext parseContext) throws InterruptedException {
        //Intentionally do not add the metadata filter here!
        //We need to let stacktraces percolate
        RecursiveParserWrapperHandler handler = new RecursiveParserWrapperHandler(
                new BasicContentHandlerFactory(handlerConfig.getType(),
                        handlerConfig.getWriteLimit(), handlerConfig.isThrowOnWriteLimitReached(),
                        parseContext), handlerConfig.getMaxEmbeddedResources());

        long start = System.currentTimeMillis();

        preParse(fetchEmitTuple, stream, metadata, parseContext);
        //queue better be empty. we deserve an exception if not
        intermediateResult.add(metadata);
        countDownLatch.await();
        try {
            recursiveParserWrapper.parse(stream, handler, metadata, parseContext);
        } catch (SAXException e) {
            LOG.warn("sax problem:" + fetchEmitTuple.getId(), e);
        } catch (EncryptedDocumentException e) {
            LOG.warn("encrypted document:" + fetchEmitTuple.getId(), e);
        } catch (SecurityException e) {
            LOG.warn("security exception:" + fetchEmitTuple.getId(), e);
            throw e;
        } catch (Exception e) {
            LOG.warn("parse exception: " + fetchEmitTuple.getId(), e);
        } finally {
            if (LOG.isTraceEnabled()) {
                LOG.trace("timer -- parse only time: {} ms", System.currentTimeMillis() - start);
            }
        }
        return handler.getMetadataList();
    }

    public List<Metadata> parseConcatenated(FetchEmitTuple fetchEmitTuple,
                                             HandlerConfig handlerConfig, TikaInputStream stream,
                                             Metadata metadata, ParseContext parseContext) {

        ContentHandlerFactory contentHandlerFactory =
                new BasicContentHandlerFactory(handlerConfig.getType(),
                        handlerConfig.getWriteLimit(), handlerConfig.isThrowOnWriteLimitReached(),
                        parseContext);

        ContentHandler handler = contentHandlerFactory.getNewContentHandler();
        parseContext.set(DocumentSelector.class, new DocumentSelector() {
            final int maxEmbedded = handlerConfig.getMaxEmbeddedResources();
            int embedded = 0;

            @Override
            public boolean select(Metadata metadata) {
                if (maxEmbedded < 0) {
                    return true;
                }
                return embedded++ < maxEmbedded;
            }
        });

        String containerException = null;
        long start = System.currentTimeMillis();
        preParse(fetchEmitTuple, stream, metadata, parseContext);
        //TODO -- add intermediate
        try {
            autoDetectParser.parse(stream, handler, metadata, parseContext);
        } catch (SAXException e) {
            containerException = ExceptionUtils.getStackTrace(e);
            LOG.warn("sax problem:" + fetchEmitTuple.getId(), e);
        } catch (EncryptedDocumentException e) {
            containerException = ExceptionUtils.getStackTrace(e);
            LOG.warn("encrypted document:" + fetchEmitTuple.getId(), e);
        } catch (SecurityException e) {
            LOG.warn("security exception:" + fetchEmitTuple.getId(), e);
            throw e;
        } catch (Exception e) {
            containerException = ExceptionUtils.getStackTrace(e);
            LOG.warn("parse exception: " + fetchEmitTuple.getId(), e);
        } finally {
            metadata.add(TikaCoreProperties.TIKA_CONTENT, handler.toString());
            if (containerException != null) {
                metadata.add(TikaCoreProperties.CONTAINER_EXCEPTION, containerException);
            }
            if (LOG.isTraceEnabled()) {
                LOG.trace("timer -- parse only time: {} ms", System.currentTimeMillis() - start);
            }
        }
        return Collections.singletonList(metadata);
    }

}
