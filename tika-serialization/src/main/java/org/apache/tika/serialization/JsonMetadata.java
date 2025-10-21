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

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;

import org.apache.tika.config.TikaConfig;
import org.apache.tika.metadata.Metadata;

public class JsonMetadata {

    static volatile boolean PRETTY_PRINT = false;

    private static ObjectMapper OBJECT_MAPPER;
    private static final ObjectMapper PRETTY_SERIALIZER;

    static {
        OBJECT_MAPPER = buildObjectMapper(StreamReadConstraints.DEFAULT_MAX_STRING_LEN);
        PRETTY_SERIALIZER = new ObjectMapper();
        SimpleModule prettySerializerModule = new SimpleModule();
        prettySerializerModule.addSerializer(Metadata.class, new MetadataSerializer(true));
        PRETTY_SERIALIZER.registerModule(prettySerializerModule);
    }

    /**
     * Serializes a Metadata object to Json.  This does not flush or close the writer.
     *
     * @param metadata metadata to write
     * @param writer   writer
     * @throws java.io.IOException if there is an IOException during writing
     */
    public static void toJson(Metadata metadata, Writer writer) throws IOException {
        if (PRETTY_PRINT) {
            PRETTY_SERIALIZER
                    .writerWithDefaultPrettyPrinter()
                    .writeValue(writer, metadata);
        } else {
            OBJECT_MAPPER.writeValue(writer, metadata);
        }
    }

    /**
     * Read metadata from reader.
     * <p>
     * This does not close the reader.
     * <p>
     * This will reset the OBJECT_MAPPER if the max string length differs from that in TikaConfig.
     *
     * @param reader reader to read from
     * @return Metadata or null if nothing could be read from the reader
     * @throws IOException in case of parse failure or IO failure with Reader
     */
    public static Metadata fromJson(Reader reader) throws IOException {
        if (reader == null) {
            return null;
        }
        if (OBJECT_MAPPER
                .getFactory()
                .streamReadConstraints()
                .getMaxStringLength() != TikaConfig.getMaxJsonStringFieldLength()) {
            OBJECT_MAPPER = buildObjectMapper(TikaConfig.getMaxJsonStringFieldLength());
        }
        return OBJECT_MAPPER.readValue(reader, Metadata.class);
    }

    public static void setPrettyPrinting(boolean prettyPrint) {
        PRETTY_PRINT = prettyPrint;
    }

    static ObjectMapper buildObjectMapper(int maxStringLen) {
        JsonFactory factory = new JsonFactory();
        factory.setStreamReadConstraints(StreamReadConstraints
                .builder()
                .maxNestingDepth(10)
                .maxStringLength(maxStringLen)
                .maxNumberLength(500)
                .build());
        ObjectMapper objectMapper = new ObjectMapper(factory);
        SimpleModule baseModule = new SimpleModule();
        baseModule.addDeserializer(Metadata.class, new MetadataDeserializer());
        baseModule.addSerializer(Metadata.class, new MetadataSerializer());
        objectMapper.registerModule(baseModule);
        return objectMapper;
    }
}
