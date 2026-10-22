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

import org.apache.tika.metadata.Metadata;
import org.apache.tika.serialization.serdes.MetadataSerializer;

public class JsonMetadata {

    static volatile boolean PRETTY_PRINT = false;

    /**
     * Default stream read constraints for metadata serialization.
     */
    private static final StreamReadConstraints DEFAULT_CONSTRAINTS = StreamReadConstraints
            .builder()
            .maxNestingDepth(10)
            .maxStringLength(20_000_000)
            .maxNumberLength(500)
            .build();

    private static volatile StreamReadConstraints streamReadConstraints = DEFAULT_CONSTRAINTS;
    private static volatile boolean typedValues = false;
    private static volatile ObjectMapper OBJECT_MAPPER;
    private static volatile ObjectMapper PRETTY_SERIALIZER;

    static {
        rebuildObjectMappers();
    }

    private static void rebuildObjectMappers() {
        JsonFactory factory = new JsonFactory();
        factory.setStreamReadConstraints(streamReadConstraints);

        // Use TikaModule which includes Metadata serializers
        ObjectMapper mapper = new ObjectMapper(factory);
        mapper.registerModule(new TikaModule());
        StreamReadConstraints valueConstraints = typedValueConstraints();
        if (valueConstraints != null) {
            // registered after TikaModule, so this serializer wins
            mapper.registerModule(new SimpleModule("TypedMetadata")
                    .addSerializer(Metadata.class, new MetadataSerializer(false, valueConstraints)));
        }
        OBJECT_MAPPER = mapper;

        // Pretty printer needs custom serializer with sort flag
        ObjectMapper prettyMapper = new ObjectMapper(factory);
        SimpleModule prettySerializerModule = new SimpleModule();
        prettySerializerModule.addSerializer(Metadata.class, new MetadataSerializer(true, valueConstraints));
        prettyMapper.registerModule(prettySerializerModule);
        PRETTY_SERIALIZER = prettyMapper;
    }

    /**
     * Sets the stream read constraints for JSON parsing of metadata.
     * This affects all subsequent calls to {@link #fromJson(Reader)}.
     * <p>
     * Typically called by TikaLoader during initialization based on the
     * "metadata-list" configuration section.
     *
     * @param constraints the constraints to use
     */
    public static synchronized void setStreamReadConstraints(StreamReadConstraints constraints) {
        streamReadConstraints = constraints;
        rebuildObjectMappers();
    }

    /**
     * The constraints a value nested in a metadata object must meet so that {@link #fromJson(Reader)}
     * can read the whole back; null when values are written as strings.
     */
    private static StreamReadConstraints typedValueConstraints() {
        if (!typedValues) {
            return null;
        }
        return streamReadConstraints.rebuild()
                .maxNestingDepth(Math.max(0, streamReadConstraints.getMaxNestingDepth() - 1))
                .build();
    }

    /**
     * Opt-in: write values of registered INTEGER, REAL and BOOLEAN keys as JSON numbers and
     * booleans, and of JSON keys (e.g. {@code tk:chunks}) as JSON rather than an escaped string.
     * Off by default for backwards compatibility. {@link #fromJson(Reader)} reads either form.
     * <p>
     * Typically called by TikaLoader from the "metadata-list" configuration section.
     */
    public static synchronized void setTypedValues(boolean typed) {
        typedValues = typed;
        rebuildObjectMappers();
    }

    public static boolean isTypedValues() {
        return typedValues;
    }

    /**
     * Gets the current stream read constraints.
     *
     * @return the current constraints
     */
    public static StreamReadConstraints getStreamReadConstraints() {
        return streamReadConstraints;
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
     * Read metadata from reader. This does not close the reader.
     *
     * @param reader reader to read from
     * @return Metadata or null if reader is null
     * @throws IOException in case of parse failure or IO failure with Reader
     */
    public static Metadata fromJson(Reader reader) throws IOException {
        if (reader == null) {
            return null;
        }
        return OBJECT_MAPPER.readValue(reader, Metadata.class);
    }

    public static void setPrettyPrinting(boolean prettyPrint) {
        PRETTY_PRINT = prettyPrint;
    }
}
