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
package org.apache.tika.serialization;


import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.Arrays;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.StreamReadConstraints;
import org.apache.commons.io.input.CloseShieldReader;
import org.apache.commons.io.output.CloseShieldWriter;

import org.apache.tika.config.TikaConfig;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;

public class JsonMetadata {

    static volatile boolean PRETTY_PRINT = false;

    /**
     * Serializes a Metadata object to Json.  This does not flush or close the writer.
     *
     * @param metadata metadata to write
     * @param writer   writer
     * @throws TikaException if there is an IOException during writing
     */
    public static void toJson(Metadata metadata, Writer writer) throws IOException {
        if (metadata == null) {
            writer.write("null");
            return;
        }
        long max = TikaConfig.getMaxJsonStringFieldLength();
        try (JsonGenerator jsonGenerator = new JsonFactory()
                .setStreamReadConstraints(StreamReadConstraints
                        .builder()
                        .maxStringLength(TikaConfig.getMaxJsonStringFieldLength())
                        .build())
                .createGenerator(new CloseShieldWriter(writer))) {
            if (PRETTY_PRINT) {
                jsonGenerator.useDefaultPrettyPrinter();
            }
            writeMetadataObject(metadata, jsonGenerator, PRETTY_PRINT);
        }
    }

    public static void writeMetadataObject(Metadata metadata, JsonGenerator jsonGenerator, boolean prettyPrint) throws IOException {
        jsonGenerator.writeStartObject();
        String[] names = metadata.names();
        if (prettyPrint) {
            Arrays.sort(names, new PrettyMetadataKeyComparator());
        }
        for (String n : names) {
            String[] vals = metadata.getValues(n);
            if (vals.length == 0) {
                continue;
            } else if (vals.length == 1) {
                jsonGenerator.writeStringField(n, vals[0]);
            } else if (vals.length > 1) {
                jsonGenerator.writeArrayFieldStart(n);
                for (String val : vals) {
                    jsonGenerator.writeString(val);
                }
                jsonGenerator.writeEndArray();
            }
        }
        jsonGenerator.writeEndObject();
    }

    /**
     * Read metadata from reader.
     * <p>
     * This does not close the reader.
     *
     * @param reader reader to read from
     * @return Metadata or null if nothing could be read from the reader
     * @throws IOException in case of parse failure or IO failure with Reader
     */
    public static Metadata fromJson(Reader reader) throws IOException {
        Metadata m = null;
        try (JsonParser jParser = new JsonFactory()
                .setStreamReadConstraints(StreamReadConstraints
                        .builder()
                        .maxStringLength(TikaConfig.getMaxJsonStringFieldLength())
                        .build())
                .createParser(new CloseShieldReader(reader))) {
            m = readMetadataObject(jParser);
        }
        return m;
    }

    /**
     * expects that jParser has not yet started on object or
     * for jParser to be pointing to the start object.
     *
     * @param jParser
     * @return
     * @throws IOException
     */
    public static Metadata readMetadataObject(JsonParser jParser) throws IOException {
        Metadata metadata = new Metadata();
        JsonToken token = jParser.currentToken();
        if (token == null) {
            token = jParser.nextToken();
            if (token != JsonToken.START_OBJECT) {
                throw new IOException("expected start object, but got: " + token.name());
            }
            token = jParser.nextToken();
        } else if (token == JsonToken.START_OBJECT) {
            token = jParser.nextToken();
        }

        while (token != JsonToken.END_OBJECT) {
            token = jParser.currentToken();
            if (token != JsonToken.FIELD_NAME) {
                throw new IOException("expected field name, but got: " + token.name());
            }
            String key = jParser.getCurrentName();
            token = jParser.nextToken();
            if (token == JsonToken.START_ARRAY) {
                while (jParser.nextToken() != JsonToken.END_ARRAY) {
                    metadata.add(key, jParser.getText());
                }
            } else {
                if (token != JsonToken.VALUE_STRING) {
                    throw new IOException("expected string value, but found: " + token.name());
                }
                String value = jParser.getValueAsString();
                metadata.set(key, value);
            }
            token = jParser.nextToken();
        }
        return metadata;
    }

    public static void setPrettyPrinting(boolean prettyPrint) {
        PRETTY_PRINT = prettyPrint;
    }

}
