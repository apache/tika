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
import java.util.List;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.serialization.serdes.MetadataDeserializer;
import org.apache.tika.serialization.serdes.MetadataSerializer;

public class JsonMetadataList {

    static volatile boolean PRETTY_PRINT = false;

    /**
     * Default stream read constraints for metadata list serialization.
     */
    private static final StreamReadConstraints DEFAULT_CONSTRAINTS = StreamReadConstraints
            .builder()
            .maxNestingDepth(10)
            .maxStringLength(20_000_000)
            .maxNumberLength(500)
            .build();

    private static volatile StreamReadConstraints streamReadConstraints = DEFAULT_CONSTRAINTS;
    private static volatile ObjectMapper OBJECT_MAPPER;
    private static volatile ObjectMapper PRETTY_SERIALIZER;

    static {
        rebuildObjectMappers();
    }

    private static void rebuildObjectMappers() {
        JsonFactory factory = new JsonFactory();
        factory.setStreamReadConstraints(streamReadConstraints);

        ObjectMapper mapper = new ObjectMapper(factory);
        SimpleModule baseModule = new SimpleModule();
        baseModule.addDeserializer(Metadata.class, new MetadataDeserializer());
        baseModule.addSerializer(Metadata.class, new MetadataSerializer());
        mapper.registerModule(baseModule);
        OBJECT_MAPPER = mapper;

        ObjectMapper prettyMapper = new ObjectMapper(factory);
        SimpleModule prettySerializerModule = new SimpleModule();
        prettySerializerModule.addSerializer(Metadata.class, new MetadataSerializer(true));
        prettyMapper.registerModule(prettySerializerModule);
        PRETTY_SERIALIZER = prettyMapper;
    }

    /**
     * Sets the stream read constraints for JSON parsing of metadata lists.
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
     * @param metadataList list of metadata to write
     * @param writer       writer
     * @param prettyPrint  whether or not to pretty print the output
     * @throws org.apache.tika.exception.TikaException if there is an IOException during writing
     */
    public static void toJson(List<Metadata> metadataList, Writer writer, boolean prettyPrint) throws IOException {
        if (prettyPrint) {
            PRETTY_SERIALIZER.writerWithDefaultPrettyPrinter().writeValue(writer, metadataList);
        } else {
            OBJECT_MAPPER.writeValue(writer, metadataList);
        }
    }

    /**
     * Serializes a Metadata object to Json.  This does not flush or close the writer.
     *
     * @param metadataList list of metadata to write
     * @param writer       writer
     * @throws org.apache.tika.exception.TikaException if there is an IOException during writing
     */
    public static void toJson(List<Metadata> metadataList, Writer writer) throws IOException {
        toJson(metadataList, writer, PRETTY_PRINT);
    }

    /**
     * Read metadata from reader. This does not close the reader.
     *
     * @param reader the reader to read from
     * @return Metadata list or null if reader is null
     * @throws IOException in case of parse failure or IO failure with Reader
     */
    public static List<Metadata> fromJson(Reader reader) throws IOException {
        if (reader == null) {
            return null;
        }
        return OBJECT_MAPPER.readValue(reader, new TypeReference<List<Metadata>>(){});
    }

    public static void setPrettyPrinting(boolean prettyPrint) {
        PRETTY_PRINT = prettyPrint;
    }

}
