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

import static org.apache.tika.pipes.core.serialization.EmitDataSerializer.CONTAINER_STACK_TRACE;
import static org.apache.tika.pipes.core.serialization.EmitDataSerializer.EMIT_KEY;
import static org.apache.tika.pipes.core.serialization.EmitDataSerializer.METADATA_LIST;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.pipes.core.emitter.EmitDataImpl;
import org.apache.tika.utils.StringUtils;

public class EmitDataDeserializer extends JsonDeserializer<EmitDataImpl> {

    @Override
    public EmitDataImpl deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException {
        JsonNode root = jsonParser.readValueAsTree();
        ObjectMapper mapper = (ObjectMapper) jsonParser.getCodec();

        String emitKey = readString(EMIT_KEY, root, null, true);
        List<Metadata> metadataList = readMetadataList(root, mapper);
        String containerStackTrace = readString(CONTAINER_STACK_TRACE, root, StringUtils.EMPTY, false);

        // ParseContext is NOT deserialized - it's restored by PipesClient from the original FetchEmitTuple
        return new EmitDataImpl(emitKey, metadataList, containerStackTrace);
    }

    private static List<Metadata> readMetadataList(JsonNode root, ObjectMapper mapper) throws IOException {
        JsonNode metadataListNode = root.get(METADATA_LIST);
        if (metadataListNode == null || !metadataListNode.isArray()) {
            return new ArrayList<>();
        }
        List<Metadata> metadataList = new ArrayList<>();
        for (JsonNode metadataNode : metadataListNode) {
            Metadata metadata = mapper.treeToValue(metadataNode, Metadata.class);
            metadataList.add(metadata);
        }
        return metadataList;
    }

    private static String readString(String key, JsonNode root, String defaultVal, boolean required) throws IOException {
        JsonNode node = root.get(key);
        if (node == null) {
            if (required) {
                throw new IOException("Required field missing: " + key);
            }
            return defaultVal;
        }
        return node.asText();
    }
}
