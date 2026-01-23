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

import org.apache.tika.pipes.api.PipesResult;
import org.apache.tika.utils.StringUtils;

public class PipesResultSerializer extends JsonSerializer<PipesResult> {

    public static final String STATUS = "status";
    public static final String EMIT_DATA = "emitData";
    public static final String MESSAGE = "message";

    @Override
    public void serialize(PipesResult pipesResult, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException {
        jsonGenerator.writeStartObject();
        jsonGenerator.writeStringField(STATUS, pipesResult.status().name());
        if (pipesResult.emitData() != null) {
            jsonGenerator.writeObjectField(EMIT_DATA, pipesResult.emitData());
        }
        if (!StringUtils.isBlank(pipesResult.message())) {
            jsonGenerator.writeStringField(MESSAGE, pipesResult.message());
        }
        jsonGenerator.writeEndObject();
    }
}
