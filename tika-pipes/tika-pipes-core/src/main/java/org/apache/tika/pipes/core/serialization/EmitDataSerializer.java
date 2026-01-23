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

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import org.apache.tika.pipes.api.emitter.EmitData;
import org.apache.tika.utils.StringUtils;

public class EmitDataSerializer extends JsonSerializer<EmitData> {

    public static final String EMIT_KEY = "emitKey";
    public static final String METADATA_LIST = "metadataList";
    public static final String CONTAINER_STACK_TRACE = "containerStackTrace";

    @Override
    public void serialize(EmitData emitData, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException {
        jsonGenerator.writeStartObject();
        jsonGenerator.writeStringField(EMIT_KEY, emitData.getEmitKey());
        jsonGenerator.writeObjectField(METADATA_LIST, emitData.getMetadataList());
        if (!StringUtils.isBlank(emitData.getContainerStackTrace())) {
            jsonGenerator.writeStringField(CONTAINER_STACK_TRACE, emitData.getContainerStackTrace());
        }
        // ParseContext is NOT serialized - it's restored by PipesClient from the original FetchEmitTuple
        jsonGenerator.writeEndObject();
    }
}
