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

import static org.apache.tika.pipes.core.serialization.PipesResultSerializer.EMIT_DATA;
import static org.apache.tika.pipes.core.serialization.PipesResultSerializer.MESSAGE;
import static org.apache.tika.pipes.core.serialization.PipesResultSerializer.STATUS;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.tika.pipes.api.PipesResult;
import org.apache.tika.pipes.core.emitter.EmitDataImpl;

public class PipesResultDeserializer extends JsonDeserializer<PipesResult> {

    @Override
    public PipesResult deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException {
        JsonNode root = jsonParser.readValueAsTree();
        ObjectMapper mapper = (ObjectMapper) jsonParser.getCodec();

        String statusStr = readString(STATUS, root, null, true);
        PipesResult.RESULT_STATUS status = PipesResult.RESULT_STATUS.valueOf(statusStr);

        EmitDataImpl emitData = null;
        JsonNode emitDataNode = root.get(EMIT_DATA);
        if (emitDataNode != null && !emitDataNode.isNull()) {
            emitData = mapper.treeToValue(emitDataNode, EmitDataImpl.class);
        }

        String message = readString(MESSAGE, root, null, false);

        return new PipesResult(status, emitData, message);
    }

    private static String readString(String key, JsonNode root, String defaultVal, boolean required) throws IOException {
        JsonNode node = root.get(key);
        if (node == null || node.isNull()) {
            if (required) {
                throw new IOException("Required field missing: " + key);
            }
            return defaultVal;
        }
        return node.asText();
    }
}
