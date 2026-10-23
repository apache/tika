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
import java.util.Arrays;
import java.util.Comparator;
import java.util.regex.Pattern;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.util.TokenBuffer;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Property;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.serialization.MetadataValueTypes;

public class MetadataSerializer extends JsonSerializer<Metadata> {
    private static final String TIKA_CONTENT_KEY = TikaCoreProperties.TIKA_CONTENT.getName();

    private static final Pattern JSON_INTEGER = Pattern.compile("-?(?:0|[1-9][0-9]*)");
    private static final Pattern JSON_NUMBER =
            Pattern.compile("-?(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?(?:[eE][+-]?[0-9]+)?");

    //always sort the content at the end
    private static final Comparator<String> METADATA_KEY_COMPARATOR = new Comparator<String>() {
        @Override
        public int compare(String o1, String o2) {
            if (o1.equals(TIKA_CONTENT_KEY)) {
                return 1;
            }
            if (o2.equals(TIKA_CONTENT_KEY)) {
                return -1;
            }
            return o1.compareTo(o2);
        }
    };

    private boolean prettyPrint = false;

    // null: every value is written as a string
    private final JsonFactory typedValueFactory;

    public MetadataSerializer() {
        this(false);
    }

    public MetadataSerializer(boolean prettyPrint) {
        this(prettyPrint, null);
    }

    /**
     * @param typedValueConstraints if non-null, values of registered {@link Property.ValueType#INTEGER},
     *                              {@link Property.ValueType#REAL}, {@link Property.ValueType#BOOLEAN}
     *                              and {@link Property.ValueType#JSON} keys are written as JSON numbers,
     *                              booleans and structures. A value that is malformed for its type, or
     *                              that a reader held to these constraints could not read back, stays
     *                              a string.
     */
    public MetadataSerializer(boolean prettyPrint, StreamReadConstraints typedValueConstraints) {
        this.prettyPrint = prettyPrint;
        if (typedValueConstraints == null) {
            this.typedValueFactory = null;
        } else {
            this.typedValueFactory = new JsonFactory();
            this.typedValueFactory.setStreamReadConstraints(typedValueConstraints);
        }
    }

    @Override
    public void serialize(Metadata metadata, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException {
        jsonGenerator.writeStartObject();
        String[] names = metadata.names();
        if (prettyPrint) {
            Arrays.sort(names, METADATA_KEY_COMPARATOR);
        }
        for (String n : names) {
            String[] v = metadata.getValues(n);
            if (v.length == 0) {
                continue;
            }
            Property.ValueType type = valueType(n);
            jsonGenerator.writeFieldName(n);
            if (v.length == 1) {
                if (type != Property.ValueType.JSON || !writeJson(v[0], jsonGenerator)) {
                    writeValue(type, v[0], jsonGenerator);
                }
            } else {
                jsonGenerator.writeStartArray();
                for (String s : v) {
                    writeValue(type, s, jsonGenerator);
                }
                jsonGenerator.writeEndArray();
            }
        }
        jsonGenerator.writeEndObject();
    }

    private Property.ValueType valueType(String name) {
        if (typedValueFactory == null) {
            return null;
        }
        return MetadataValueTypes.get(name);
    }

    private void writeValue(Property.ValueType type, String value, JsonGenerator jsonGenerator)
            throws IOException {
        if (type == Property.ValueType.BOOLEAN && ("true".equals(value) || "false".equals(value))) {
            jsonGenerator.writeBoolean(Boolean.parseBoolean(value));
        } else if ((type == Property.ValueType.INTEGER && isNumber(JSON_INTEGER, value))
                || (type == Property.ValueType.REAL && isNumber(JSON_NUMBER, value))) {
            jsonGenerator.writeNumber(value);
        } else {
            jsonGenerator.writeString(value);
        }
    }

    private boolean isNumber(Pattern pattern, String value) {
        return value.length() <= typedValueFactory.streamReadConstraints().getMaxNumberLength()
                && pattern.matcher(value).matches();
    }

    /**
     * Writes the value as JSON if it is one JSON document that {@link MetadataDeserializer}
     * reads back as this one value, not as several.
     */
    private boolean writeJson(String value, JsonGenerator jsonGenerator) throws IOException {
        TokenBuffer buffer;
        try (JsonParser parser = typedValueFactory.createParser(value)) {
            if (parser.nextToken() == null) {
                return false;
            }
            buffer = new TokenBuffer(parser);
            buffer.copyCurrentStructure(parser);
            if (parser.nextToken() != null) {
                return false;
            }
        } catch (JsonProcessingException e) {
            return false;
        }
        if (!MetadataDeserializer.isOneValue(buffer)) {
            return false;
        }
        // the buffer, not writeRawValue: keeps number text exact and honors pretty printing
        buffer.serialize(jsonGenerator);
        return true;
    }

}
