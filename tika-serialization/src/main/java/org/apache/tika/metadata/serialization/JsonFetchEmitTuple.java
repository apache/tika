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
package org.apache.tika.metadata.serialization;

import java.io.IOException;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Locale;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.StreamReadConstraints;

import org.apache.tika.config.TikaConfig;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.pipes.FetchEmitTuple;
import org.apache.tika.pipes.HandlerConfig;
import org.apache.tika.pipes.extractor.EmbeddedDocumentBytesConfig;
import org.apache.tika.pipes.emitter.EmitKey;
import org.apache.tika.pipes.fetcher.FetchKey;
import org.apache.tika.sax.BasicContentHandlerFactory;
import org.apache.tika.utils.StringUtils;

public class JsonFetchEmitTuple {

    public static final String ID = "id";
    public static final String FETCHER = "fetcher";
    public static final String FETCHKEY = "fetchKey";
    public static final String FETCH_RANGE_START = "fetchRangeStart";
    public static final String FETCH_RANGE_END = "fetchRangeEnd";
    public static final String EMITTER = "emitter";
    public static final String EMITKEY = "emitKey";
    public static final String METADATAKEY = "metadata";
    public static final String HANDLER_CONFIG = "handlerConfig";
    public static final String ON_PARSE_EXCEPTION = "onParseException";
    private static final String HANDLER_CONFIG_TYPE = "type";
    private static final String HANDLER_CONFIG_WRITE_LIMIT = "writeLimit";
    private static final String HANDLER_CONFIG_MAX_EMBEDDED_RESOURCES = "maxEmbeddedResources";
    private static final String HANDLER_CONFIG_PARSE_MODE = "parseMode";

    private static final String EMBEDDED_DOCUMENT_BYTES_CONFIG = "embeddedDocumentBytesConfig";


    public static FetchEmitTuple fromJson(Reader reader) throws IOException {
        try (JsonParser jParser = new JsonFactory().setStreamReadConstraints(StreamReadConstraints.builder()
                .maxStringLength(TikaConfig.getMaxJsonStringFieldLength()).build()).createParser(reader)) {
            JsonToken token = jParser.nextToken();
            if (token != JsonToken.START_OBJECT) {
                throw new IOException("require start object, but see: " + token.name());
            }
            return parseFetchEmitTuple(jParser);
        }
    }


    static FetchEmitTuple parseFetchEmitTuple(JsonParser jParser) throws IOException {
        JsonToken token = jParser.nextToken();
        if (token == JsonToken.START_OBJECT) {
            token = jParser.nextToken();
        }
        String id = null;
        String fetcherName = null;
        String fetchKey = null;
        String emitterName = null;
        String emitKey = null;
        long fetchRangeStart = -1l;
        long fetchRangeEnd = -1l;

        FetchEmitTuple.ON_PARSE_EXCEPTION onParseException =
                FetchEmitTuple.DEFAULT_ON_PARSE_EXCEPTION;
        HandlerConfig handlerConfig = HandlerConfig.DEFAULT_HANDLER_CONFIG;
        Metadata metadata = new Metadata();
        EmbeddedDocumentBytesConfig embeddedDocumentBytesConfig = EmbeddedDocumentBytesConfig.SKIP;

        while (token != JsonToken.END_OBJECT) {
            if (token != JsonToken.FIELD_NAME) {
                throw new IOException("required field name, but see: " + token.name());
            }
            String name = jParser.getCurrentName();
            if (ID.equals(name)) {
                id = getValue(jParser);
            } else if (FETCHER.equals(name)) {
                fetcherName = getValue(jParser);
            } else if (FETCHKEY.equals(name)) {
                fetchKey = getValue(jParser);
            } else if (EMITTER.equals(name)) {
                emitterName = getValue(jParser);
            } else if (EMITKEY.equals(name)) {
                emitKey = getValue(jParser);
            } else if (METADATAKEY.equals(name)) {
                token = jParser.nextToken();
                if (token != JsonToken.START_OBJECT) {
                    throw new IOException("required start object, but see: " + token.name());
                }
                metadata = JsonMetadata.readMetadataObject(jParser);
            } else if (ON_PARSE_EXCEPTION.equals(name)) {
                String value = getValue(jParser);
                if ("skip".equalsIgnoreCase(value)) {
                    onParseException = FetchEmitTuple.ON_PARSE_EXCEPTION.SKIP;
                } else if ("emit".equalsIgnoreCase(value)) {
                    onParseException = FetchEmitTuple.ON_PARSE_EXCEPTION.EMIT;
                } else {
                    throw new IOException(ON_PARSE_EXCEPTION + " must be either 'skip' or 'emit'");
                }
            } else if (HANDLER_CONFIG.equals(name)) {
                handlerConfig = getHandlerConfig(jParser);
            } else if (FETCH_RANGE_START.equals(name)) {
                fetchRangeStart = getLong(jParser);
            } else if (FETCH_RANGE_END.equals(name)) {
                fetchRangeEnd = getLong(jParser);
            } else if (EMBEDDED_DOCUMENT_BYTES_CONFIG.equals(name)) {
                embeddedDocumentBytesConfig = getEmbeddedDocumentBytesConfig(jParser);
            }
            token = jParser.nextToken();
        }
        if (id == null) {
            id = fetchKey;
        }
        return new FetchEmitTuple(id, new FetchKey(fetcherName, fetchKey, fetchRangeStart, fetchRangeEnd),
                new EmitKey(emitterName, emitKey), metadata, handlerConfig, onParseException,
                embeddedDocumentBytesConfig);
    }

    private static EmbeddedDocumentBytesConfig getEmbeddedDocumentBytesConfig(JsonParser jParser) throws IOException {
        JsonToken token = jParser.nextToken();
        if (token != JsonToken.START_OBJECT) {
            throw new IOException("required start object, but see: " + token.name());
        }
        String fieldName = jParser.nextFieldName();
        EmbeddedDocumentBytesConfig config = new EmbeddedDocumentBytesConfig(true);
        while (fieldName != null) {
            switch (fieldName) {
                //TODO: fill in more here!
                case "extractEmbeddedDocumentBytes":
                    boolean extract = jParser.nextBooleanValue();
                    if (! extract) {
                        return new EmbeddedDocumentBytesConfig(false);
                    }
                    break;
                case "includeOriginal":
                    config.setIncludeOriginal(jParser.nextBooleanValue());
                    break;
                case "emitter":
                    config.setEmitter(jParser.nextTextValue());
                    break;
                default:
                    throw new IllegalArgumentException("I regret I don't understand '" + fieldName +
                            "' in the context of an embeddedDocumentBytesConfig");
            }
            fieldName = jParser.nextFieldName();
        }
        return EmbeddedDocumentBytesConfig.SKIP;
    }

    private static HandlerConfig getHandlerConfig(JsonParser jParser) throws IOException {

        JsonToken token = jParser.nextToken();
        if (token != JsonToken.START_OBJECT) {
            throw new IOException("required start object, but see: " + token.name());
        }
        BasicContentHandlerFactory.HANDLER_TYPE handlerType =
                BasicContentHandlerFactory.HANDLER_TYPE.TEXT;
        int writeLimit = -1;
        int maxEmbeddedResources = -1;
        HandlerConfig.PARSE_MODE parseMode = HandlerConfig.PARSE_MODE.RMETA;
        String fieldName = jParser.nextFieldName();
        while (fieldName != null) {
            switch (fieldName) {
                case HANDLER_CONFIG_TYPE:
                    String value = jParser.nextTextValue();
                    handlerType = BasicContentHandlerFactory
                            .parseHandlerType(value, HandlerConfig.DEFAULT_HANDLER_CONFIG.getType());
                    break;
                case HANDLER_CONFIG_WRITE_LIMIT:
                    writeLimit = jParser.nextIntValue(-1);
                    break;
                case HANDLER_CONFIG_MAX_EMBEDDED_RESOURCES:
                    maxEmbeddedResources = jParser.nextIntValue(-1);
                    break;
                case HANDLER_CONFIG_PARSE_MODE:
                    String modeString = jParser.nextTextValue();
                    parseMode = HandlerConfig.PARSE_MODE.parseMode(modeString);
                    break;
                default:
                    throw new IllegalArgumentException("I regret I don't understand '" + fieldName +
                                                       "' in the context of a handler config");
            }
            fieldName = jParser.nextFieldName();
        }
        //TODO: implement configuration of throwOnWriteLimitReached
        return new HandlerConfig(handlerType, parseMode, writeLimit, maxEmbeddedResources,
                true);
    }

    private static String getValue(JsonParser jParser) throws IOException {
        JsonToken token = jParser.nextToken();
        if (token != JsonToken.VALUE_STRING) {
            throw new IOException("required value string, but see: " + token.name());
        }
        return jParser.getValueAsString();
    }

    private static long getLong(JsonParser jParser) throws IOException {
        JsonToken token = jParser.nextToken();
        if (token != JsonToken.VALUE_NUMBER_INT) {
            throw new IOException("required value long, but see: " + token.name());
        }
        return jParser.getValueAsLong();
    }

    public static String toJson(FetchEmitTuple t) throws IOException {
        StringWriter writer = new StringWriter();
        toJson(t, writer);
        return writer.toString();
    }

    public static void toJson(FetchEmitTuple t, Writer writer) throws IOException {

        try (JsonGenerator jsonGenerator = new JsonFactory().createGenerator(writer)) {
            writeTuple(t, jsonGenerator);
        }
    }

    static void writeTuple(FetchEmitTuple t, JsonGenerator jsonGenerator) throws IOException {
        jsonGenerator.writeStartObject();
        jsonGenerator.writeStringField(ID, t.getId());
        jsonGenerator.writeStringField(FETCHER, t.getFetchKey().getFetcherName());
        jsonGenerator.writeStringField(FETCHKEY, t.getFetchKey().getFetchKey());
        if (t.getFetchKey().hasRange()) {
            jsonGenerator.writeNumberField(FETCH_RANGE_START, t.getFetchKey().getRangeStart());
            jsonGenerator.writeNumberField(FETCH_RANGE_END, t.getFetchKey().getRangeEnd());
        }
        jsonGenerator.writeStringField(EMITTER, t.getEmitKey().getEmitterName());
        if (!StringUtils.isBlank(t.getEmitKey().getEmitKey())) {
            jsonGenerator.writeStringField(EMITKEY, t.getEmitKey().getEmitKey());
        }
        if (t.getMetadata().size() > 0) {
            jsonGenerator.writeFieldName(METADATAKEY);
            JsonMetadata.writeMetadataObject(t.getMetadata(), jsonGenerator, false);
        }
        if (t.getHandlerConfig() != HandlerConfig.DEFAULT_HANDLER_CONFIG) {
            jsonGenerator.writeFieldName(HANDLER_CONFIG);
            jsonGenerator.writeStartObject();
            jsonGenerator.writeStringField(HANDLER_CONFIG_TYPE,
                    t.getHandlerConfig().getType().name().toLowerCase(Locale.ROOT));
            jsonGenerator.writeStringField(HANDLER_CONFIG_PARSE_MODE,
                    t.getHandlerConfig().getParseMode().name().toLowerCase(Locale.ROOT));
            jsonGenerator.writeNumberField(HANDLER_CONFIG_WRITE_LIMIT,
                    t.getHandlerConfig().getWriteLimit());
            jsonGenerator.writeNumberField(HANDLER_CONFIG_MAX_EMBEDDED_RESOURCES,
                    t.getHandlerConfig().getMaxEmbeddedResources());
            jsonGenerator.writeEndObject();
        }
        jsonGenerator.writeStringField(ON_PARSE_EXCEPTION,
                t.getOnParseException().name().toLowerCase(Locale.US));
        jsonGenerator.writeEndObject();

    }
}
