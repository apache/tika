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
package org.apache.tika.pipes.core.serialization;


import static org.apache.tika.pipes.core.serialization.FetchEmitTupleSerializer.EMITTER;
import static org.apache.tika.pipes.core.serialization.FetchEmitTupleSerializer.EMIT_KEY;
import static org.apache.tika.pipes.core.serialization.FetchEmitTupleSerializer.FETCHER;
import static org.apache.tika.pipes.core.serialization.FetchEmitTupleSerializer.FETCH_KEY;
import static org.apache.tika.pipes.core.serialization.FetchEmitTupleSerializer.FETCH_RANGE_END;
import static org.apache.tika.pipes.core.serialization.FetchEmitTupleSerializer.FETCH_RANGE_START;
import static org.apache.tika.pipes.core.serialization.FetchEmitTupleSerializer.ID;
import static org.apache.tika.pipes.core.serialization.FetchEmitTupleSerializer.METADATA_KEY;
import static org.apache.tika.pipes.core.serialization.FetchEmitTupleSerializer.ON_PARSE_EXCEPTION;
import static org.apache.tika.serialization.serdes.ParseContextSerializer.PARSE_CONTEXT;

import java.io.IOException;
import java.util.Map;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.pipes.api.FetchEmitTuple;
import org.apache.tika.pipes.api.emitter.EmitKey;
import org.apache.tika.pipes.api.fetcher.FetchKey;
import org.apache.tika.serialization.ParseContextUtils;
import org.apache.tika.serialization.serdes.ParseContextDeserializer;

public class FetchEmitTupleDeserializer extends JsonDeserializer<FetchEmitTuple> {

    @Override
    public FetchEmitTuple deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException, JacksonException {
        JsonNode root = jsonParser.readValueAsTree();
        ObjectMapper mapper = (ObjectMapper) jsonParser.getCodec();

        String id = readVal(ID, root, null, true);
        String fetcherId = readVal(FETCHER, root, null, true);
        String fetchKey = readVal(FETCH_KEY, root, null, true);
        String emitterName = readVal(EMITTER, root, "", false);
        String emitKey = readVal(EMIT_KEY, root, "", false);
        long fetchRangeStart = readLong(FETCH_RANGE_START, root, -1l, false);
        long fetchRangeEnd = readLong(FETCH_RANGE_END, root, -1l, false);
        Metadata metadata = readMetadata(root);
        JsonNode parseContextNode = root.get(PARSE_CONTEXT);
        ParseContext parseContext = parseContextNode == null ? new ParseContext() : ParseContextDeserializer.readParseContext(parseContextNode, mapper);
        // Resolve all friendly-named components from jsonConfigs to actual objects
        try {
            ParseContextUtils.resolveAll(parseContext, FetchEmitTupleDeserializer.class.getClassLoader());
        } catch (TikaConfigException e) {
            throw new IOException("Failed to resolve parse-context components", e);
        }
        FetchEmitTuple.ON_PARSE_EXCEPTION onParseException = readOnParseException(root);

        return new FetchEmitTuple(id, new FetchKey(fetcherId, fetchKey, fetchRangeStart, fetchRangeEnd),
                new EmitKey(emitterName, emitKey), metadata, parseContext,
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

    private static Metadata readMetadata(JsonNode root) {
        JsonNode metadataNode = root.get(METADATA_KEY);
        if (metadataNode == null) {
            return new Metadata();
        }
        Metadata metadata = new Metadata();
        for (Map.Entry<String, JsonNode> e : metadataNode.properties()) {
            JsonNode vals = e.getValue();
            String k = e.getKey();
            if (vals.isArray()) {
                for (JsonNode arrVal : vals) {
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

}
