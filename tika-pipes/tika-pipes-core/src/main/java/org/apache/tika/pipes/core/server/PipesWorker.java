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
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentExtractorFactory;
import org.apache.tika.extractor.UnpackHandler;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.writefilter.MetadataWriteLimiterFactory;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.pipes.api.FetchEmitTuple;
import org.apache.tika.pipes.api.ParseMode;
import org.apache.tika.pipes.api.PipesResult;
import org.apache.tika.pipes.api.emitter.EmitKey;
import org.apache.tika.pipes.api.emitter.Emitter;
import org.apache.tika.pipes.api.emitter.StreamEmitter;
import org.apache.tika.pipes.core.PipesResults;
import org.apache.tika.pipes.core.emitter.EmitterManager;
import org.apache.tika.pipes.core.extractor.EmittingUnpackHandler;
import org.apache.tika.pipes.core.extractor.TempFileUnpackHandler;
import org.apache.tika.pipes.core.extractor.UnpackConfig;
import org.apache.tika.pipes.core.extractor.UnpackExtractorFactory;
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
        TempFileUnpackHandler tempHandler = null;
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

            // Check if we need to zip and emit embedded files
            UnpackHandler handler = parseContext.get(UnpackHandler.class);
            if (handler instanceof TempFileUnpackHandler) {
                tempHandler = (TempFileUnpackHandler) handler;
                PipesResult zipResult = zipAndEmitEmbeddedFiles(tempHandler);
                if (zipResult != null) {
                    // Zipping/emitting failed - return the error
                    return zipResult;
                }
            }

            return emitHandler.emitParseData(fetchEmitTuple, parseData, parseContext);
        } finally {
            // Clean up temp handler if used
            if (tempHandler != null) {
                try {
                    tempHandler.close();
                } catch (IOException e) {
                    LOG.warn("problem closing temp file handler", e);
                }
            } else if (parseData != null && parseData.hasUnpackHandler() &&
                    parseData.getUnpackHandler() instanceof Closeable) {
                try {
                    ((Closeable) parseData.getUnpackHandler()).close();
                } catch (IOException e) {
                    LOG.warn("problem closing unpack handler", e);
                }
            }
        }
    }


    static boolean metadataIsEmpty(List<Metadata> metadataList) {
        return metadataList == null || metadataList.isEmpty();
    }

    /**
     * Zips all embedded files from the temp handler and emits the zip to the user's emitter.
     *
     * @param tempHandler the handler containing embedded files in temp directory
     * @return PipesResult if there was an error, null if successful
     */
    private PipesResult zipAndEmitEmbeddedFiles(TempFileUnpackHandler tempHandler) {
        // Skip if no embedded files
        if (!tempHandler.hasEmbeddedFiles()) {
            LOG.debug("No embedded files to zip");
            return null;
        }

        UnpackConfig unpackConfig = parseContext.get(UnpackConfig.class);
        String emitterName = unpackConfig.getEmitter();
        Emitter emitter;
        try {
            emitter = emitterManager.getEmitter(emitterName);
        } catch (Exception e) {
            LOG.warn("Failed to get emitter for zip: {}", emitterName, e);
            return new PipesResult(PipesResult.RESULT_STATUS.EMITTER_NOT_FOUND,
                    "Emitter not found for zipping: " + emitterName);
        }

        if (!(emitter instanceof StreamEmitter)) {
            LOG.warn("Emitter {} is not a StreamEmitter, cannot emit zip", emitterName);
            return new PipesResult(PipesResult.RESULT_STATUS.EMIT_EXCEPTION,
                    "Emitter must be a StreamEmitter to emit zipped embedded files. Found: " +
                            emitter.getClass().getName());
        }

        StreamEmitter streamEmitter = (StreamEmitter) emitter;
        EmitKey containerEmitKey = fetchEmitTuple.getEmitKey();

        // Create zip file in temp directory
        Path zipFile = tempHandler.getTempDirectory().resolve("embedded-files.zip");
        try {
            createZipFile(zipFile, tempHandler, unpackConfig);
        } catch (IOException e) {
            LOG.warn("Failed to create zip file", e);
            return new PipesResult(PipesResult.RESULT_STATUS.EMIT_EXCEPTION,
                    "Failed to create zip file: " + ExceptionUtils.getStackTrace(e));
        }

        // Emit the zip file
        String zipEmitKey = containerEmitKey.getEmitKey() + "-embedded.zip";
        try (InputStream zipStream = Files.newInputStream(zipFile)) {
            streamEmitter.emit(zipEmitKey, zipStream, new Metadata(), parseContext);
        } catch (IOException e) {
            LOG.warn("Failed to emit zip file", e);
            return new PipesResult(PipesResult.RESULT_STATUS.EMIT_EXCEPTION,
                    "Failed to emit zip file: " + ExceptionUtils.getStackTrace(e));
        }

        LOG.debug("Successfully zipped and emitted {} embedded files to {}",
                tempHandler.getEmbeddedFiles().size(), zipEmitKey);
        return null;
    }

    /**
     * Creates a zip file containing all embedded files.
     */
    private void createZipFile(Path zipFile, TempFileUnpackHandler tempHandler,
                               UnpackConfig unpackConfig) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zipFile))) {
            // Include original document if requested
            if (unpackConfig.isIncludeOriginal() && tempHandler.hasOriginalDocument()) {
                ZipEntry originalEntry = new ZipEntry(tempHandler.getOriginalDocumentName());
                zos.putNextEntry(originalEntry);
                Files.copy(tempHandler.getOriginalDocumentPath(), zos);
                zos.closeEntry();
            }

            for (TempFileUnpackHandler.EmbeddedFileInfo fileInfo : tempHandler.getEmbeddedFiles()) {
                // Add the embedded file
                ZipEntry fileEntry = new ZipEntry(fileInfo.fileName());
                zos.putNextEntry(fileEntry);
                Files.copy(fileInfo.filePath(), zos);
                zos.closeEntry();

                // Add metadata JSON if requested
                if (unpackConfig.isIncludeMetadataInZip()) {
                    String metadataFileName = fileInfo.fileName() + ".metadata.json";
                    ZipEntry metadataEntry = new ZipEntry(metadataFileName);
                    zos.putNextEntry(metadataEntry);
                    writeMetadataAsJson(zos, fileInfo.metadata());
                    zos.closeEntry();
                }
            }
        }
    }

    /**
     * Writes metadata as JSON to the output stream.
     * Note: Does not close the output stream.
     */
    private void writeMetadataAsJson(OutputStream os, Metadata metadata) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        // Disable auto-close so we don't close the zip output stream
        mapper.configure(com.fasterxml.jackson.core.JsonGenerator.Feature.AUTO_CLOSE_TARGET, false);
        // Convert metadata to a map for JSON serialization
        java.util.Map<String, Object> metadataMap = new java.util.LinkedHashMap<>();
        for (String name : metadata.names()) {
            String[] values = metadata.getValues(name);
            if (values.length == 1) {
                metadataMap.put(name, values[0]);
            } else {
                metadataMap.put(name, values);
            }
        }
        mapper.writeValue(os, metadataMap);
    }

    /**
     * Stores the original document to the temp handler for inclusion in the zip.
     * Uses TikaInputStream's internal file caching to avoid consuming the stream.
     */
    private void storeOriginalDocument(TikaInputStream tis, TempFileUnpackHandler tempHandler)
            throws IOException {
        // Get the file name from fetch key
        String fetchKey = fetchEmitTuple.getFetchKey().getFetchKey();
        String fileName = fetchKey;
        int lastSlash = Math.max(fetchKey.lastIndexOf('/'), fetchKey.lastIndexOf('\\'));
        if (lastSlash >= 0 && lastSlash < fetchKey.length() - 1) {
            fileName = fetchKey.substring(lastSlash + 1);
        }

        // TikaInputStream caches to a temp file internally - get that file
        Path originalPath = tis.getPath();
        if (originalPath != null && Files.exists(originalPath)) {
            // Copy from the cached file
            try (InputStream is = Files.newInputStream(originalPath)) {
                tempHandler.storeOriginalDocument(is, fileName);
            }
        } else {
            // Stream hasn't been cached yet - we need to read and reset
            tis.mark(Integer.MAX_VALUE);
            try {
                tempHandler.storeOriginalDocument(tis, fileName);
            } finally {
                tis.reset();
            }
        }
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
            // Store original document for zipping if requested
            UnpackHandler handler = localContext.get(UnpackHandler.class);
            if (handler instanceof TempFileUnpackHandler) {
                TempFileUnpackHandler tempHandler = (TempFileUnpackHandler) handler;
                UnpackConfig uc = localContext.get(UnpackConfig.class);
                if (uc != null && uc.isIncludeOriginal()) {
                    storeOriginalDocument(tis, tempHandler);
                }
            }
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

        ParseMode parseMode = parseContext.get(ParseMode.class);
        UnpackConfig unpackConfig = parseContext.get(UnpackConfig.class);

        // For UNPACK mode, automatically set up byte extraction
        if (parseMode == ParseMode.UNPACK) {
            if (unpackConfig == null) {
                unpackConfig = new UnpackConfig();
                parseContext.set(UnpackConfig.class, unpackConfig);
            }

            // Determine emitter: prefer UnpackConfig, fall back to FetchEmitTuple
            String emitterName = unpackConfig.getEmitter();
            if (StringUtils.isBlank(emitterName)) {
                emitterName = fetchEmitTuple.getEmitKey().getEmitterId();
                if (StringUtils.isBlank(emitterName)) {
                    throw new TikaConfigException(
                            "UNPACK parse mode requires an emitter. Set emitter in UnpackConfig " +
                                    "or specify an emitterId in FetchEmitTuple.emitKey.");
                }
                unpackConfig.setEmitter(emitterName);
            }

            // Set up the extractor factory - the extractor will be created during parsing
            // with the correct context (after RecursiveParserWrapper sets up EmbeddedParserDecorator)
            parseContext.set(EmbeddedDocumentExtractorFactory.class, new UnpackExtractorFactory());

            // Set up the bytes handler - use temp file handler if zipping requested
            if (unpackConfig.isZipEmbeddedFiles()) {
                parseContext.set(UnpackHandler.class,
                        new TempFileUnpackHandler(fetchEmitTuple.getEmitKey(), unpackConfig));
            } else {
                parseContext.set(UnpackHandler.class,
                        new EmittingUnpackHandler(fetchEmitTuple, emitterManager, parseContext));
            }

            return parseContext;
        }

        // For non-UNPACK modes, no byte extraction setup needed
        // UnpackConfig may be present from config file but is only used when ParseMode.UNPACK is set
        return parseContext;
    }

    //parse data result or a terminal pipesresult
    record ParseDataOrPipesResult(MetadataListAndEmbeddedBytes parseDataResult, PipesResult pipesResult) {

    }


}
