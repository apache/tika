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
package org.apache.tika.metadata.serialization;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.StreamReadConstraints;
import org.apache.commons.io.input.CloseShieldReader;
import org.apache.commons.io.output.CloseShieldWriter;

import org.apache.tika.config.TikaConfig;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;

public class JsonMetadataList {
    static volatile boolean PRETTY_PRINT = false;

    /**
     * Serializes a Metadata object to Json.  This does not flush or close the writer.
     *
     * @param metadataList list of metadata to write
     * @param writer       writer
     * @param prettyPrint  whether or not to pretty print the output
     * @throws org.apache.tika.exception.TikaException if there is an IOException during writing
     */
    public static void toJson(List<Metadata> metadataList, Writer writer, boolean prettyPrint)
            throws IOException {
        if (metadataList == null) {
            writer.write("null");
            return;
        }
        try (JsonGenerator jsonGenerator = new JsonFactory().setStreamReadConstraints(
                        StreamReadConstraints.builder().maxStringLength(
                                TikaConfig.getMaxJsonStringFieldLength()).build())
                .createGenerator(new CloseShieldWriter(writer))) {
            if (prettyPrint) {
                jsonGenerator.useDefaultPrettyPrinter();
            }
            jsonGenerator.writeStartArray();
            for (Metadata m : metadataList) {
                JsonMetadata.writeMetadataObject(m, jsonGenerator, prettyPrint);
            }
            jsonGenerator.writeEndArray();
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
     * Read metadata from reader. This does not close the reader
     *
     * @param reader
     * @return Metadata or null if nothing could be read from the reader
     * @throws IOException in case of parse failure or IO failure with Reader
     */
    public static List<Metadata> fromJson(Reader reader) throws IOException {
        List<Metadata> ms = null;
        if (reader == null) {
            return ms;
        }
        ms = new ArrayList<>();
        try (JsonParser jParser = new JsonFactory().setStreamReadConstraints(StreamReadConstraints.builder()
                .maxStringLength(TikaConfig.getMaxJsonStringFieldLength()).build())
                .createParser(new CloseShieldReader(reader))) {

            JsonToken token = jParser.nextToken();
            if (token != JsonToken.START_ARRAY) {
                throw new IOException(
                        "metadata list must start with an array, but I see: " + token.name());
            }
            token = jParser.nextToken();
            while (token != JsonToken.END_ARRAY) {
                Metadata m = JsonMetadata.readMetadataObject(jParser);
                ms.add(m);
                token = jParser.nextToken();
            }

        }
        if (ms == null) {
            return null;
        }
        //if the last object is the main document,
        //as happens with the streaming serializer,
        //flip it to be the first element.
        if (ms.size() > 1) {
            Metadata last = ms.get(ms.size() - 1);
            String embResourcePath = last.get(TikaCoreProperties.EMBEDDED_RESOURCE_PATH);
            if (embResourcePath == null &&
                    ms.get(0).get(TikaCoreProperties.EMBEDDED_RESOURCE_PATH) != null) {
                ms.add(0, ms.remove(ms.size() - 1));
            }
        }
        return ms;
    }

    public static void setPrettyPrinting(boolean prettyPrint) {
        PRETTY_PRINT = prettyPrint;
    }


}
