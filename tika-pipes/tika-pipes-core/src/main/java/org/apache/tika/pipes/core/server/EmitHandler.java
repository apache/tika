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


import static org.apache.tika.pipes.core.server.PipesWorker.metadataIsEmpty;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.metadata.filter.IncludeFieldMetadataFilter;
import org.apache.tika.metadata.filter.MetadataFilter;
import org.apache.tika.metadata.filter.NoOpFilter;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.pipes.api.FetchEmitTuple;
import org.apache.tika.pipes.api.ParseMode;
import org.apache.tika.pipes.api.PipesResult;
import org.apache.tika.pipes.api.emitter.EmitKey;
import org.apache.tika.pipes.api.emitter.Emitter;
import org.apache.tika.pipes.api.emitter.StreamEmitter;
import org.apache.tika.pipes.core.EmitStrategy;
import org.apache.tika.pipes.core.EmitStrategyConfig;
import org.apache.tika.pipes.core.PassbackFilter;
import org.apache.tika.pipes.core.emitter.EmitDataImpl;
import org.apache.tika.pipes.core.emitter.EmitterManager;
import org.apache.tika.utils.ExceptionUtils;
import org.apache.tika.utils.StringUtils;

class EmitHandler {

    private static final Logger LOG = LoggerFactory.getLogger(EmitHandler.class);

    private final MetadataFilter defaultMetadataFilter;
    private final EmitStrategy emitStrategy;
    private final EmitterManager emitterManager;
    private final long directEmitThresholdBytes;


    public EmitHandler(MetadataFilter defaultMetadataFilter, EmitStrategy emitStrategy, EmitterManager emitterManager, long directEmitThresholdBytes) {
        this.defaultMetadataFilter = defaultMetadataFilter;
        this.emitStrategy = emitStrategy;
        this.emitterManager = emitterManager;
        this.directEmitThresholdBytes = directEmitThresholdBytes;
    }

    public PipesResult emitParseData(FetchEmitTuple t, MetadataListAndEmbeddedBytes parseData, ParseContext parseContext) {
        long start = System.currentTimeMillis();
        String stack = getContainerStacktrace(t, parseData.getMetadataList());
        //we need to apply the metadata filter after we pull out the stacktrace
        filterMetadata(parseData, parseContext);
        FetchEmitTuple.ON_PARSE_EXCEPTION onParseException = t.getOnParseException();
        if (StringUtils.isBlank(stack) ||
                onParseException == FetchEmitTuple.ON_PARSE_EXCEPTION.EMIT) {
            injectUserMetadata(t.getMetadata(), parseData.getMetadataList());
            EmitKey emitKey = t.getEmitKey();
            if (StringUtils.isBlank(emitKey.getEmitKey())) {
                emitKey = new EmitKey(emitKey.getEmitterId(), t.getFetchKey().getFetchKey());
                t.setEmitKey(emitKey);
            }
            EmitDataImpl emitDataTuple = new EmitDataImpl(t.getEmitKey().getEmitKey(), parseData.getMetadataList(), stack);
            ParseMode parseMode = parseContext.get(ParseMode.class);
            if (shouldEmit(parseMode, parseData, emitDataTuple, parseContext)) {
                return emit(t.getId(), emitKey, parseMode == ParseMode.UNPACK,
                        parseData, stack, parseContext);
            } else {
                if (StringUtils.isBlank(stack)) {
                    return new PipesResult(PipesResult.RESULT_STATUS.PARSE_SUCCESS, emitDataTuple);
                } else {
                    return new PipesResult(PipesResult.RESULT_STATUS.PARSE_SUCCESS_WITH_EXCEPTION, emitDataTuple);
                }
            }
        } else {
            return new PipesResult(PipesResult.RESULT_STATUS.PARSE_EXCEPTION_NO_EMIT, stack);
        }
    }

    private PipesResult emit(String taskId, EmitKey emitKey,
                      boolean isExtractEmbeddedBytes, MetadataListAndEmbeddedBytes parseData,
                      String parseExceptionStack, ParseContext parseContext) {
        if (emitKey == EmitKey.NO_EMIT || emitKey.getEmitterId() == null) {
            LOG.debug("No emitter specified for task id '{}', skipping emission", taskId);
            return new PipesResult(PipesResult.RESULT_STATUS.PARSE_SUCCESS);
        }

        Emitter emitter = null;

        try {
            emitter = emitterManager.getEmitter(emitKey.getEmitterId());
        } catch (org.apache.tika.pipes.api.emitter.EmitterNotFoundException e) {
            String noEmitterMsg = getNoEmitterMsg(taskId);
            LOG.warn(noEmitterMsg);
            return new PipesResult(PipesResult.RESULT_STATUS.EMITTER_NOT_FOUND, noEmitterMsg);
        } catch (IOException | TikaException e) {
            LOG.warn("Couldn't initialize emitter for task id '" + taskId + "'", e);
            return new PipesResult(PipesResult.RESULT_STATUS.EMITTER_INITIALIZATION_EXCEPTION, ExceptionUtils.getStackTrace(e));
        }
        try {
            ParseMode parseMode = parseContext.get(ParseMode.class);
            if (parseMode == ParseMode.CONTENT_ONLY && emitter instanceof StreamEmitter) {
                emitContentOnly((StreamEmitter) emitter, emitKey, parseData, parseContext);
            } else if (isExtractEmbeddedBytes &&
                    parseData.toBePackagedForStreamEmitter()) {
                emitContentsAndBytes(emitter, emitKey, parseData);
            } else {
                emitter.emit(emitKey.getEmitKey(), parseData.getMetadataList(), parseContext);
            }
        } catch (IOException e) {
            LOG.warn("emit exception", e);
            String msg = ExceptionUtils.getStackTrace(e);
            //for now, we're hiding the parse exception if there was also an emit exception
            return new PipesResult(PipesResult.RESULT_STATUS.EMIT_EXCEPTION, msg);
        }
        PassbackFilter passbackFilter = parseContext.get(PassbackFilter.class);
        if (passbackFilter != null) {
            try {
                passbackFilter.filter(parseData.metadataList);
            } catch (TikaException e) {
                LOG.warn("problem filtering for pass back", e);
            }
            if (StringUtils.isBlank(parseExceptionStack)) {
                return new PipesResult(PipesResult.RESULT_STATUS.EMIT_SUCCESS_PASSBACK, new EmitDataImpl(emitKey.getEmitKey(), parseData.metadataList));
            } else {
                return new PipesResult(PipesResult.RESULT_STATUS.EMIT_SUCCESS_PARSE_EXCEPTION, new EmitDataImpl(emitKey.getEmitKey(), parseData.metadataList), parseExceptionStack);
            }

        }
        if (StringUtils.isBlank(parseExceptionStack)) {
            return new PipesResult(PipesResult.RESULT_STATUS.EMIT_SUCCESS);
        } else {
            return new PipesResult(PipesResult.RESULT_STATUS.EMIT_SUCCESS_PARSE_EXCEPTION, parseExceptionStack);
        }
    }

    private void emitContentOnly(StreamEmitter emitter, EmitKey emitKey,
                                  MetadataListAndEmbeddedBytes parseData,
                                  ParseContext parseContext) throws IOException {
        List<Metadata> metadataList = parseData.getMetadataList();
        String content = "";
        if (metadataList != null && !metadataList.isEmpty()) {
            String val = metadataList.get(0).get(TikaCoreProperties.TIKA_CONTENT);
            if (val != null) {
                content = val;
            }
        }
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        try (InputStream is = new ByteArrayInputStream(bytes)) {
            emitter.emit(emitKey.getEmitKey(), is, new Metadata(), parseContext);
        }
    }

    private void emitContentsAndBytes(Emitter emitter, EmitKey emitKey,
                                      MetadataListAndEmbeddedBytes parseData) {
        if (!(emitter instanceof StreamEmitter)) {
            throw new IllegalArgumentException("The emitter for embedded document byte store must" +
                    " be a StreamEmitter. I see: " + emitter.getClass());
        }
        //TODO: implement this
        throw new UnsupportedOperationException("this is not yet implemented");
    }


    private boolean shouldEmit(ParseMode parseMode, MetadataListAndEmbeddedBytes parseData,
                               EmitDataImpl emitDataTuple, ParseContext parseContext) {
        EmitStrategy strategy = emitStrategy;
        long thresholdBytes = directEmitThresholdBytes;

        EmitStrategyConfig overrideConfig = parseContext.get(EmitStrategyConfig.class);
        if (overrideConfig != null) {
            strategy = overrideConfig.getType();
            if (overrideConfig.getThresholdBytes() != null) {
                thresholdBytes = overrideConfig.getThresholdBytes();
            }
        }

        // UNPACK mode: bytes are already emitted during parsing
        // For PASSBACK_ALL, don't emit metadata - pass it back to client instead
        // For other strategies, also emit metadata
        if (parseMode == ParseMode.UNPACK) {
            if (strategy == EmitStrategy.PASSBACK_ALL) {
                // Bytes were emitted during parsing, metadata will be passed back
                return false;
            }
            return true;
        }

        if (strategy == EmitStrategy.EMIT_ALL) {
            return true;
        } else if (strategy == EmitStrategy.PASSBACK_ALL) {
            return false;
        } else if (strategy == EmitStrategy.DYNAMIC) {
            if (emitDataTuple.getEstimatedSizeBytes() >= thresholdBytes) {
                return true;
            }
        }
        return false;
    }

    private static String getContainerStacktrace(FetchEmitTuple t, List<Metadata> metadataList) {
        if (metadataIsEmpty(metadataList)) {
            return StringUtils.EMPTY;
        }
        String stack = metadataList.get(0).get(TikaCoreProperties.CONTAINER_EXCEPTION);
        return (stack != null) ? stack : StringUtils.EMPTY;
    }

    private void injectUserMetadata(Metadata userMetadata, List<Metadata> metadataList) {
        for (String n : userMetadata.names()) {
            //overwrite whatever was there
            metadataList.get(0).set(n, null);
            for (String val : userMetadata.getValues(n)) {
                metadataList.get(0).add(n, val);
            }
        }
    }

    private void filterMetadata(MetadataListAndEmbeddedBytes parseData, ParseContext parseContext) {
        MetadataFilter filter = parseContext.get(MetadataFilter.class);
        if (filter == null) {
            ParseMode parseMode = parseContext.get(ParseMode.class);
            if (parseMode == ParseMode.CONTENT_ONLY) {
                filter = new IncludeFieldMetadataFilter(
                        Set.of(TikaCoreProperties.TIKA_CONTENT.getName(),
                                TikaCoreProperties.CONTAINER_EXCEPTION.getName()));
            } else {
                filter = defaultMetadataFilter;
            }
        }
        if (filter instanceof NoOpFilter) {
            return;
        }
        try {
            parseData.filter(filter);
        } catch (TikaException e) {
            LOG.warn("failed to filter metadata list", e);
        }
    }

    private String getNoEmitterMsg(String emitterName) {
        StringBuilder sb = new StringBuilder();
        sb.append("Emitter '").append(emitterName).append("'");
        sb.append(" not found.");
        sb.append("\nThe configured emitterManager supports:");
        int i = 0;
        for (String e : emitterManager.getSupported()) {
            if (i++ > 0) {
                sb.append(", ");
            }
            sb.append(e);
        }
        return sb.toString();
    }

}
