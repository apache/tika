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
package org.apache.tika.serialization.pipes;

import java.io.IOException;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.io.IOExceptionWithCause;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.pipes.FetchEmitTuple;
import org.apache.tika.pipes.emitter.EmitKey;
import org.apache.tika.pipes.fetcher.FetchKey;
import org.apache.tika.serialization.JsonMetadata;
import org.apache.tika.serialization.ParseContextSerializer;
import org.apache.tika.serialization.TikaJsonDeserializer;
import org.apache.tika.serialization.TikaJsonSerializer;
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
    public static final String PARSE_CONTEXT = "parseContext";

    public static FetchEmitTuple fromJson(Reader reader) throws IOException {
        //try (JsonParser jParser = new JsonFactory().setStreamReadConstraints(StreamReadConstraints.builder()
        //      .maxStringLength(TikaConfig.getMaxJsonStringFieldLength()).build()).createParser(reader)) {

        JsonNode root = new ObjectMapper().readTree(reader);
        return parseFetchEmitTuple(root);
    }


    static FetchEmitTuple parseFetchEmitTuple(JsonNode root) throws IOException {
        String id = readVal(ID, root, null, true);
        String fetcherName = readVal(FETCHER, root, null, true);
        String fetchKey = readVal(FETCHKEY, root, null, true);
        String emitterName = readVal(EMITTER, root, "", false);
        String emitKey = readVal(EMITKEY, root, "", false);
        long fetchRangeStart = readLong(FETCH_RANGE_START, root, -1l, false);
        long fetchRangeEnd = readLong(FETCH_RANGE_END, root, -1l, false);
        Metadata metadata = readMetadata(root);
        ParseContext parseContext = readParseContext(root);
        FetchEmitTuple.ON_PARSE_EXCEPTION onParseException = readOnParseException(root);

        return new FetchEmitTuple(id, new FetchKey(fetcherName, fetchKey, fetchRangeStart, fetchRangeEnd), new EmitKey(emitterName, emitKey), metadata, parseContext,
                onParseException);
    }

    private static FetchEmitTuple.ON_PARSE_EXCEPTION readOnParseException(JsonNode root) throws IOException {
        JsonNode onParseExNode = root.get(ON_PARSE_EXCEPTION);
        if (onParseExNode == null) {
            return FetchEmitTuple.ON_PARSE_EXCEPTION.EMIT;
        }
        String txt = onParseExNode.asText();
        if ("skip".equalsIgnoreCase(txt)) {
            return FetchEmitTuple.ON_PARSE_EXCEPTION.SKIP;
        } else if ("emit".equalsIgnoreCase(txt)) {
            return FetchEmitTuple.ON_PARSE_EXCEPTION.EMIT;
        } else {
            throw new IOException(ON_PARSE_EXCEPTION + " must be either 'skip' or 'emit'");
        }
    }

    private static ParseContext readParseContext(JsonNode root) throws IOException {
        JsonNode contextNode = root.get(PARSE_CONTEXT);
        if (contextNode == null) {
            return new ParseContext();
        }
        ParseContext parseContext = new ParseContext();
        Iterator<Map.Entry<String, JsonNode>> it = contextNode.fields();
        while (it.hasNext()) {
            Map.Entry<String, JsonNode> e = it.next();
            String clazzName = e.getKey();
            JsonNode obj = e.getValue();
            String className = readVal(TikaJsonSerializer.INSTANTIATED_CLASS_KEY, obj, null, true);
            String superClassName = readVal(TikaJsonSerializer.SUPER_CLASS_KEY, obj, className, false);
            try {
                Class clazz = Class.forName(className);
                Class superClazz = clazz.equals(superClassName) ? clazz : Class.forName(superClassName);
                parseContext.set(clazz, TikaJsonDeserializer.deserialize(clazz, superClazz, obj));
            } catch (ReflectiveOperationException ex) {
                throw new IOExceptionWithCause(ex);
            }
        }
        return parseContext;
    }

    private static Metadata readMetadata(JsonNode root) {
        JsonNode metadataNode = root.get(METADATAKEY);
        if (metadataNode == null) {
            return new Metadata();
        }
        Metadata metadata = new Metadata();
        Iterator<Map.Entry<String, JsonNode>> it = metadataNode.fields();
        while (it.hasNext()) {
            Map.Entry<String, JsonNode> e = it.next();
            JsonNode vals = e.getValue();
            String k = e.getKey();
            if (vals.isArray()) {
                Iterator<JsonNode> arrIt = vals.iterator();
                while (arrIt.hasNext()) {
                    JsonNode arrVal = arrIt.next();
                    metadata.add(k, arrVal.textValue());
                }
            } else {
                metadata.set(k, vals.asText());
            }
        }
        return metadata;
    }

    private static String readVal(String key, JsonNode jsonObj, String defaultRet, boolean isRequired) throws IOException {
        JsonNode valNode = jsonObj.get(key);
        if (valNode == null) {
            if (isRequired) {
                throw new IOException("required value string, but see: " + key);
            }
            return defaultRet;
        }
        return valNode.asText();
    }

    private static long readLong(String key, JsonNode jsonObj, long defaultVal, boolean isRequired) throws IOException {
        JsonNode val = jsonObj.get(key);
        if (val == null) {
            if (isRequired) {
                throw new IOException("required value long, but see: " + key);
            }
            return defaultVal;
        }
        return val.longValue();
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
        jsonGenerator.writeStringField(FETCHER, t
                .getFetchKey()
                .getFetcherName());
        jsonGenerator.writeStringField(FETCHKEY, t
                .getFetchKey()
                .getFetchKey());
        if (t
                .getFetchKey()
                .hasRange()) {
            jsonGenerator.writeNumberField(FETCH_RANGE_START, t
                    .getFetchKey()
                    .getRangeStart());
            jsonGenerator.writeNumberField(FETCH_RANGE_END, t
                    .getFetchKey()
                    .getRangeEnd());
        }
        jsonGenerator.writeStringField(EMITTER, t
                .getEmitKey()
                .getEmitterName());
        if (!StringUtils.isBlank(t
                .getEmitKey()
                .getEmitKey())) {
            jsonGenerator.writeStringField(EMITKEY, t
                    .getEmitKey()
                    .getEmitKey());
        }
        if (t
                .getMetadata()
                .size() > 0) {
            jsonGenerator.writeFieldName(METADATAKEY);
            JsonMetadata.writeMetadataObject(t.getMetadata(), jsonGenerator, false);
        }

        jsonGenerator.writeStringField(ON_PARSE_EXCEPTION, t
                .getOnParseException()
                .name()
                .toLowerCase(Locale.US));
        if (!t
                .getParseContext()
                .isEmpty()) {
            ParseContextSerializer s = new ParseContextSerializer();
            s.serialize(t.getParseContext(), jsonGenerator, null);
        }
        jsonGenerator.writeEndObject();

    }
}
