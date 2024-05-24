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
import org.apache.tika.parser.ParseContext;
import org.apache.tika.pipes.FetchEmitTuple;
import org.apache.tika.pipes.HandlerConfig;
import org.apache.tika.pipes.emitter.EmitKey;
import org.apache.tika.pipes.extractor.EmbeddedDocumentBytesConfig;
import org.apache.tika.pipes.fetcher.FetchKey;
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
    public static final String ON_PARSE_EXCEPTION = "onParseException";

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
        //TODO -- I added a stub for the ParseContext -- we need to parse the parse context!!!
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
            } else if (FETCH_RANGE_START.equals(name)) {
                fetchRangeStart = getLong(jParser);
            } else if (FETCH_RANGE_END.equals(name)) {
                fetchRangeEnd = getLong(jParser);
            }
            token = jParser.nextToken();
        }
        if (id == null) {
            id = fetchKey;
        }
        return new FetchEmitTuple(id, new FetchKey(fetcherName, fetchKey, fetchRangeStart, fetchRangeEnd),
                new EmitKey(emitterName, emitKey), metadata, ParseContext.EMPTY, onParseException);
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

        jsonGenerator.writeStringField(ON_PARSE_EXCEPTION,
                t.getOnParseException().name().toLowerCase(Locale.US));
        jsonGenerator.writeEndObject();

    }
}
