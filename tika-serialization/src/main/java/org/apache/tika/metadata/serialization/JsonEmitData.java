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
import java.io.Writer;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.StreamReadConstraints;

import org.apache.tika.config.TikaConfig;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.pipes.emitter.EmitData;
import org.apache.tika.pipes.emitter.EmitKey;
import org.apache.tika.serialization.JsonFetchEmitTuple;

public class JsonEmitData {

    public static void toJson(EmitData emitData, Writer writer) throws IOException {
        try (JsonGenerator jsonGenerator = new JsonFactory()
                .setStreamReadConstraints(StreamReadConstraints.builder()
                            .maxStringLength(TikaConfig.getMaxJsonStringFieldLength())
                        .build()).createGenerator(writer)) {
            jsonGenerator.writeStartObject();
            EmitKey key = emitData.getEmitKey();
            jsonGenerator.writeStringField(JsonFetchEmitTuple.EMITTER, key.getEmitterName());
            jsonGenerator.writeStringField(JsonFetchEmitTuple.EMITKEY, key.getEmitKey());
            if (! emitData.getParseContext().isEmpty()) {
                jsonGenerator.writeObject(emitData.getParseContext());
            }
            jsonGenerator.writeFieldName("data");
            jsonGenerator.writeStartArray();
            for (Metadata m : emitData.getMetadataList()) {
                JsonMetadata.writeMetadataObject(m, jsonGenerator, false);
            }
            jsonGenerator.writeEndArray();
            jsonGenerator.writeEndObject();
        }
    }
}
