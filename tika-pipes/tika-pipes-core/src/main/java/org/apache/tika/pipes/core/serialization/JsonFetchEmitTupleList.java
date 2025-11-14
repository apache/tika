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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.pipes.api.FetchEmitTuple;
import org.apache.tika.serialization.MetadataSerializer;
import org.apache.tika.serialization.ParseContextSerializer;

public class JsonFetchEmitTupleList {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    static {
        SimpleModule module = new SimpleModule();
        module.addDeserializer(FetchEmitTuple.class, new FetchEmitTupleDeserializer());
        module.addSerializer(FetchEmitTuple.class, new FetchEmitTupleSerializer());
        module.addSerializer(Metadata.class, new MetadataSerializer());
        module.addSerializer(ParseContext.class, new ParseContextSerializer());
        OBJECT_MAPPER.registerModule(module);
    }

    public static List<FetchEmitTuple> fromJson(Reader reader) throws IOException {
        return OBJECT_MAPPER.readValue(reader, new TypeReference<List<FetchEmitTuple>>() {});
    }

    public static String toJson(List<FetchEmitTuple> list) throws IOException {
        StringWriter writer = new StringWriter();
        toJson(list, writer);
        return writer.toString();
    }

    public static void toJson(List<FetchEmitTuple> list, Writer writer) throws IOException {
        OBJECT_MAPPER.writeValue(writer, list);
    }
}
