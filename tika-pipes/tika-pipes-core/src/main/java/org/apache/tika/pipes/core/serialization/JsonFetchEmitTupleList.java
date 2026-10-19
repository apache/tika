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

import java.io.IOException;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;
import java.util.List;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.pipes.api.FetchEmitTuple;
import org.apache.tika.serialization.serdes.MetadataSerializer;
import org.apache.tika.serialization.serdes.ParseContextDeserializer;
import org.apache.tika.serialization.serdes.ParseContextSerializer;

public class JsonFetchEmitTupleList {

    /** Envelope key: the body must be {"tuples":[...]}; a bare array is rejected. */
    public static final String TUPLES = "tuples";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    static {
        SimpleModule module = new SimpleModule();
        module.addDeserializer(FetchEmitTuple.class, new FetchEmitTupleDeserializer());
        module.addSerializer(FetchEmitTuple.class, new FetchEmitTupleSerializer());
        module.addSerializer(Metadata.class, new MetadataSerializer());
        module.addSerializer(ParseContext.class, new ParseContextSerializer());
        module.addDeserializer(ParseContext.class, new ParseContextDeserializer());
        OBJECT_MAPPER.registerModule(module);
    }

    public static List<FetchEmitTuple> fromJson(Reader reader) throws IOException {
        // The request body is an object {"tuples":[...]}; the envelope leaves room for future
        // batch-level fields. A bare top-level array is no longer accepted (4.0.0).
        JsonNode root = OBJECT_MAPPER.readTree(reader);
        JsonNode tuples = root == null ? null : root.get(TUPLES);
        if (tuples == null || !tuples.isArray()) {
            throw new IOException("Expected a JSON object with a \"" + TUPLES + "\" array");
        }
        try {
            return OBJECT_MAPPER.convertValue(tuples, new TypeReference<List<FetchEmitTuple>>() {});
        } catch (IllegalArgumentException e) {
            // convertValue rewraps deserializer IOExceptions; unwrap so callers see IOException.
            Throwable cause = e.getCause() == null ? e : e.getCause();
            throw new IOException(cause.getMessage(), e);
        }
    }

    public static String toJson(List<FetchEmitTuple> list) throws IOException {
        StringWriter writer = new StringWriter();
        toJson(list, writer);
        return writer.toString();
    }

    public static void toJson(List<FetchEmitTuple> list, Writer writer) throws IOException {
        ObjectNode root = OBJECT_MAPPER.createObjectNode();
        root.set(TUPLES, OBJECT_MAPPER.valueToTree(list));
        OBJECT_MAPPER.writeValue(writer, root);
    }
}
