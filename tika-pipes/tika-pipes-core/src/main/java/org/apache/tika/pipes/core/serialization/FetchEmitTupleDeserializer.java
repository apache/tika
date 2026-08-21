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
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.pipes.api.ComponentIds;
import org.apache.tika.pipes.api.FetchEmitTuple;
import org.apache.tika.pipes.api.emitter.EmitKey;
import org.apache.tika.pipes.api.fetcher.FetchKey;
import org.apache.tika.serialization.serdes.ParseContextDeserializer;

public class FetchEmitTupleDeserializer extends JsonDeserializer<FetchEmitTuple> {

    private static final Set<String> KNOWN_KEYS = Set.of(
            ID, FETCHER, FETCH_KEY, EMITTER, EMIT_KEY, FETCH_RANGE_START, FETCH_RANGE_END,
            METADATA_KEY, PARSE_CONTEXT, ON_PARSE_EXCEPTION);

    private final boolean restricted;

    /**
     * Deserializer for untrusted input: refuses system component ids. This is the default
     * because forgetting to restrict a new caller must not be the thing that opens the gate.
     */
    public FetchEmitTupleDeserializer() {
        this(true);
    }

    private FetchEmitTupleDeserializer(boolean restricted) {
        this.restricted = restricted;
    }

    /**
     * Deserializer for the parent-to-child IPC, whose tuples the host builds itself and which
     * must therefore be able to name {@code __} components. The parseContext stays restricted in
     * both modes, so a request that slipped past the REST gate still cannot bind a wire-blocked
     * component here.
     */
    public static FetchEmitTupleDeserializer internal() {
        return new FetchEmitTupleDeserializer(false);
    }

    @Override
    public FetchEmitTuple deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException, JacksonException {
        JsonNode root = jsonParser.readValueAsTree();
        if (root.has(PipesRequest.INLINE_BYTES)) {
            // Content is never tuple state; on the IPC it travels beside the tuple in
            // PipesRequest. A tailored message, since this is the field a caller holding
            // content is most tempted to invent.
            throw new IOException("'" + PipesRequest.INLINE_BYTES
                    + "' is not a FetchEmitTuple field; content travels outside the tuple, and"
                    + " only on the host's internal IPC. To parse content you already hold, PUT"
                    + " it to /tika or /rmeta, which inline it for you.");
        }
        rejectUnknownKeys(root);

        String id = readVal(ID, root, null, true);
        String fetcherId = normalizeId(readVal(FETCHER, root, null, true), "fetcher");
        String fetchKey = readVal(FETCH_KEY, root, null, true);
        String emitterName = normalizeId(readVal(EMITTER, root, "", false), "emitter");
        String emitKey = readVal(EMIT_KEY, root, "", false);
        long fetchRangeStart = readLong(FETCH_RANGE_START, root, -1l, false);
        long fetchRangeEnd = readLong(FETCH_RANGE_END, root, -1l, false);
        Metadata metadata = readMetadata(root);
        JsonNode parseContextNode = root.get(PARSE_CONTEXT);
        // A FetchEmitTuple is always untrusted wire input (request body, pipes iterator): restrict
        // its parseContext so it cannot introduce wire-blocked components (parsers, detectors, ...).
        ParseContext parseContext = parseContextNode == null ? new ParseContext()
                : ParseContextDeserializer.readParseContext(parseContextNode, true);
        FetchEmitTuple.ON_PARSE_EXCEPTION onParseException = readOnParseException(root);

        return new FetchEmitTuple(id, new FetchKey(fetcherId, fetchKey, fetchRangeStart, fetchRangeEnd),
                new EmitKey(emitterName, emitKey), metadata, parseContext,
                onParseException);
    }

    /**
     * An absent emitter is the empty string, so blank passes through untouched; anything else is
     * canonicalized here so the id that is checked is the id later used for lookup.
     */
    private String normalizeId(String id, String what) throws IOException {
        if (id == null || id.isBlank()) {
            return id;
        }
        try {
            return restricted
                    ? ComponentIds.requireUserId(id, what, "a request")
                    : ComponentIds.requireLegalId(id, what, "an internal tuple");
        } catch (IllegalArgumentException e) {
            throw new IOException(e.getMessage(), e);
        }
    }

    private static void rejectUnknownKeys(JsonNode root) throws IOException {
        for (Iterator<String> it = root.fieldNames(); it.hasNext(); ) {
            String name = it.next();
            if (!KNOWN_KEYS.contains(name)) {
                throw new IOException("Unrecognized FetchEmitTuple field '" + name
                        + "'. Check for a typo; known fields are " + new TreeSet<>(KNOWN_KEYS) + ".");
            }
        }
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
                    metadata.reconstruct(k, arrVal.textValue(), true);
                }
            } else {
                metadata.reconstruct(k, vals.asText(), false);
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
