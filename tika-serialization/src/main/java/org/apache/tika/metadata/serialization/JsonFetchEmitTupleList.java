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
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.StreamReadConstraints;

import org.apache.tika.config.TikaConfig;
import org.apache.tika.pipes.FetchEmitTuple;

public class JsonFetchEmitTupleList {

    public static List<FetchEmitTuple> fromJson(Reader reader) throws IOException {
        List<FetchEmitTuple> list;
        try (JsonParser jParser = new JsonFactory().setStreamReadConstraints(StreamReadConstraints.builder()
                .maxStringLength(TikaConfig.getMaxJsonStringFieldLength()).build()).createParser(reader)) {
            JsonToken token = jParser.nextToken();
            if (token != JsonToken.START_ARRAY) {
                throw new IOException("require start array, but see: " + token.name());
            }
            list = new ArrayList<>();
            while (token != JsonToken.END_ARRAY) {
                list.add(JsonFetchEmitTuple.parseFetchEmitTuple(jParser));
                token = jParser.nextToken();
            }
        }
        return list;
    }

    public static String toJson(List<FetchEmitTuple> list) throws IOException {
        StringWriter writer = new StringWriter();
        toJson(list, writer);
        return writer.toString();
    }

    public static void toJson(List<FetchEmitTuple> list, Writer writer) throws IOException {

        try (JsonGenerator jsonGenerator = new JsonFactory().setStreamReadConstraints(StreamReadConstraints.builder()
                .maxStringLength(TikaConfig.getMaxJsonStringFieldLength()).build()).createGenerator(writer)) {
            jsonGenerator.writeStartArray();
            for (FetchEmitTuple t : list) {
                JsonFetchEmitTuple.writeTuple(t, jsonGenerator);
            }
            jsonGenerator.writeEndArray();
        }
    }
}
