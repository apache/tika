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
package org.apache.tika.serialization.serdes;

import java.io.IOException;
import java.io.StringWriter;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.util.TokenBuffer;

import org.apache.tika.metadata.Metadata;

/**
 * Reads what {@link MetadataSerializer} writes, typed or not: a scalar is one value, an array of
 * scalars is several, and an object, or an array holding an object or array, is one JSON value.
 * Needs no knowledge of the keys' types.
 */
public class MetadataDeserializer extends JsonDeserializer<Metadata> {

    private static final JsonFactory JSON_FACTORY = new JsonFactory();

    @Override
    public Metadata deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException, JacksonException {
        Metadata metadata = new Metadata();
        boolean keepGoing = true;
        while (keepGoing) {
            keepGoing = addField(jsonParser, deserializationContext, metadata);
        }
        return metadata;
    }

    private boolean addField(JsonParser jsonParser, DeserializationContext ctxt, Metadata metadata)
            throws IOException {
        String field = jsonParser.nextFieldName();
        if (field == null) {
            return false;
        }
        JsonToken token = jsonParser.nextValue();

        if (token == null) {
            return false;
        }

        if (token.isScalarValue()) {
            metadata.reconstruct(field, jsonParser.getText(), false);
            return true;
        }
        if (!token.isStructStart()) {
            return false;
        }
        TokenBuffer buffer = new TokenBuffer(jsonParser, ctxt);
        buffer.copyCurrentStructure(jsonParser);
        if (isOneValue(buffer)) {
            metadata.reconstruct(field, toJson(buffer), false);
            return true;
        }
        try (JsonParser values = buffer.asParser()) {
            values.nextToken();
            while (values.nextToken() != JsonToken.END_ARRAY) {
                metadata.reconstruct(field, values.getText(), true);
            }
        }
        return true;
    }

    /** True for an object, or an array holding an object or array; false for an array of scalars. */
    static boolean isOneValue(TokenBuffer buffer) throws IOException {
        try (JsonParser parser = buffer.asParser()) {
            if (parser.nextToken() != JsonToken.START_ARRAY) {
                return parser.currentToken() == JsonToken.START_OBJECT;
            }
            JsonToken token;
            while ((token = parser.nextToken()) != JsonToken.END_ARRAY) {
                if (token.isStructStart()) {
                    return true;
                }
            }
            return false;
        }
    }

    private static String toJson(TokenBuffer buffer) throws IOException {
        StringWriter writer = new StringWriter();
        try (JsonGenerator generator = JSON_FACTORY.createGenerator(writer)) {
            buffer.serialize(generator);
        }
        return writer.toString();
    }
}
