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
import org.apache.tika.digest.DigesterFactory;
import org.apache.tika.digest.SkipContainerDocumentDigest;
import org.apache.tika.exception.EncryptedDocumentException;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.exception.WriteLimitReachedException;
import org.apache.tika.extractor.UnpackHandler;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.ParseRecord;
import org.apache.tika.parser.RecursiveParserWrapper;
import org.apache.tika.pipes.api.FetchEmitTuple;
import org.apache.tika.pipes.api.ParseMode;
import org.apache.tika.pipes.core.extractor.UnpackConfig;
import org.apache.tika.sax.AbstractRecursiveParserWrapperHandler;
import org.apache.tika.sax.ContentHandlerFactory;
import org.apache.tika.sax.RecursiveParserWrapperHandler;
import org.apache.tika.utils.ExceptionUtils;

class ParseHandler {

    private static final Logger LOG = LoggerFactory.getLogger(ParseHandler.class);

    private final Detector detector;
    private final ArrayBlockingQueue<Metadata> intermediateResult;
    private final CountDownLatch countDownLatch;
    private final AutoDetectParser autoDetectParser;
    private final RecursiveParserWrapper recursiveParserWrapper;
    private final ContentHandlerFactory defaultContentHandlerFactory;
    private final ParseMode defaultParseMode;


    ParseHandler(Detector detector, ArrayBlockingQueue<Metadata> intermediateResult,
                 CountDownLatch countDownLatch, AutoDetectParser autoDetectParser,
                 RecursiveParserWrapper recursiveParserWrapper, ContentHandlerFactory defaultContentHandlerFactory,
                 ParseMode defaultParseMode) {
        this.detector = detector;
        this.intermediateResult = intermediateResult;
        this.countDownLatch = countDownLatch;
        this.autoDetectParser = autoDetectParser;
        this.recursiveParserWrapper = recursiveParserWrapper;
        this.defaultContentHandlerFactory = defaultContentHandlerFactory;
        this.defaultParseMode = defaultParseMode;
    }

    PipesWorker.ParseDataOrPipesResult parseWithStream(FetchEmitTuple fetchEmitTuple, TikaInputStream stream, Metadata metadata, ParseContext parseContext)
            throws TikaConfigException, InterruptedException {

        List<Metadata> metadataList;
        //this adds the EmbeddedDocumentByteStore to the parsecontext
        ParseMode parseMode = getParseMode(parseContext);
        ContentHandlerFactory contentHandlerFactory = getContentHandlerFactory(parseContext);
        if (parseMode == ParseMode.NO_PARSE) {
            metadataList = detectOnly(fetchEmitTuple, stream, metadata, parseContext);
        } else if (parseMode == ParseMode.RMETA || parseMode == ParseMode.UNPACK) {
            // UNPACK uses the same recursive parsing as RMETA
            // The difference is in setup (PipesWorker) - UNPACK has mandatory byte extraction
            metadataList =
                    parseRecursive(fetchEmitTuple, contentHandlerFactory, stream, metadata, parseContext);
        } else {
            metadataList = parseConcatenated(fetchEmitTuple, contentHandlerFactory, stream, metadata,
                    parseContext);
        }

        return new PipesWorker.ParseDataOrPipesResult(new MetadataListAndEmbeddedBytes(metadataList,
                parseContext.get(UnpackHandler.class)), null);
    }

    private ParseMode getParseMode(ParseContext parseContext) {
        ParseMode mode = parseContext.get(ParseMode.class);
        if (mode != null) {
            return mode;
        }
        // Fall back to default loaded from TikaLoader
        return defaultParseMode;
    }

    private ContentHandlerFactory getContentHandlerFactory(ParseContext parseContext) {
        ContentHandlerFactory factory = parseContext.get(ContentHandlerFactory.class);
        if (factory != null) {
            return factory;
        }
        // Fall back to default loaded from TikaLoader
        return defaultContentHandlerFactory;
    }



    private void _preParse(FetchEmitTuple t, TikaInputStream tis, Metadata metadata,
                           ParseContext parseContext) {
        // Get DigesterFactory from ParseContext (configured via other-configs)
        DigesterFactory digesterFactory = parseContext.get(DigesterFactory.class);
        if (digesterFactory != null && !digesterFactory.isSkipContainerDocumentDigest()) {
            try {
                Digester digester = digesterFactory.build();
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
        UnpackConfig unpackConfig = parseContext.get(UnpackConfig.class);
        if (unpackConfig != null &&
                unpackConfig.isIncludeOriginal()) {
            UnpackHandler unpackHandler = parseContext.get(UnpackHandler.class);
            try (InputStream is = Files.newInputStream(tis.getPath())) {
                unpackHandler.add(0, metadata, is);
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

    /**
     * Performs digest (if configured) and content type detection only, without parsing.
     */
    private List<Metadata> detectOnly(FetchEmitTuple fetchEmitTuple, TikaInputStream stream,
                                      Metadata metadata, ParseContext parseContext) {
        _preParse(fetchEmitTuple, stream, metadata, parseContext);
        return Collections.singletonList(metadata);
    }

    public List<Metadata> parseRecursive(FetchEmitTuple fetchEmitTuple,
                                              ContentHandlerFactory contentHandlerFactory, TikaInputStream stream,
                                              Metadata metadata, ParseContext parseContext) throws InterruptedException {
        //Intentionally do not add the metadata filter here!
        //We need to let stacktraces percolate
        // Embedded limits are now configured via EmbeddedLimits in ParseContext
        RecursiveParserWrapperHandler handler = new RecursiveParserWrapperHandler(
                contentHandlerFactory);

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
                                             ContentHandlerFactory contentHandlerFactory, TikaInputStream stream,
                                             Metadata metadata, ParseContext parseContext) throws InterruptedException {

        ContentHandler handler = contentHandlerFactory.createHandler();

        // Configure ParseRecord for embedded document limits
        // ParseRecord.newInstance reads from EmbeddedLimits in ParseContext
        ParseRecord parseRecord = parseContext.get(ParseRecord.class);
        if (parseRecord == null) {
            parseRecord = ParseRecord.newInstance(parseContext);
            parseContext.set(ParseRecord.class, parseRecord);
        }

        String containerException = null;
        long start = System.currentTimeMillis();
        preParse(fetchEmitTuple, stream, metadata, parseContext);
        //queue better be empty. we deserve an exception if not
        intermediateResult.add(metadata);
        countDownLatch.await();
        boolean writeLimitReached = false;
        try {
            autoDetectParser.parse(stream, handler, metadata, parseContext);
        } catch (SAXException e) {
            containerException = ExceptionUtils.getStackTrace(e);
            LOG.warn("sax problem:" + fetchEmitTuple.getId(), e);
            if (WriteLimitReachedException.isWriteLimitReached(e)) {
                writeLimitReached = true;
            }
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
            if (writeLimitReached) {
                metadata.set(TikaCoreProperties.WRITE_LIMIT_REACHED, true);
            }
            // Set limit reached flags from ParseRecord
            if (parseRecord.isEmbeddedCountLimitReached()) {
                metadata.set(AbstractRecursiveParserWrapperHandler.EMBEDDED_RESOURCE_LIMIT_REACHED, true);
            }
            if (parseRecord.isEmbeddedDepthLimitReached()) {
                metadata.set(AbstractRecursiveParserWrapperHandler.EMBEDDED_DEPTH_LIMIT_REACHED, true);
            }
            if (LOG.isTraceEnabled()) {
                LOG.trace("timer -- parse only time: {} ms", System.currentTimeMillis() - start);
            }
        }
        return Collections.singletonList(metadata);
    }

}
