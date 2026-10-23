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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.StringWriter;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.smile.SmileFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import org.apache.tika.metadata.Audio;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.pipes.core.emitter.EmitDataImpl;
import org.apache.tika.serialization.JsonMetadataList;

/**
 * A pipes parent serializes metadata whose keys are declared by classes only the fork loaded,
 * so the fork sends their types along.
 */
public class EmitDataValueTypesTest {

    private static final ObjectMapper SMILE = new ObjectMapper(new SmileFactory());

    @AfterEach
    public void reset() {
        JsonMetadataList.setTypedValues(false);
    }

    @Test
    public void testForkReportsTypedKeys() throws Exception {
        JsonMetadataList.setTypedValues(true);
        JsonNode types = SMILE.readTree(JsonPipesIpc.toBytes(emitData())).get("valueTypes");
        assertEquals("{\"audio:channels\":\"INTEGER\"}", types.toString());
    }

    @Test
    public void testNoTypesWhenUntyped() throws Exception {
        assertNull(SMILE.readTree(JsonPipesIpc.toBytes(emitData())).get("valueTypes"));
    }

    @Test
    public void testParentWritesReportedTypes() throws Exception {
        // a key this JVM does not declare, as a plugin-declared key is in tika-server
        String key = "test-emit-value-types:count";
        ObjectNode wire = SMILE.createObjectNode();
        wire.put("emitKey", "key");
        wire.putArray("metadataList").addObject().put(key, "7");
        wire.putObject("valueTypes").put(key, "INTEGER");
        EmitDataImpl back = JsonPipesIpc.fromBytes(SMILE.writeValueAsBytes(wire), EmitDataImpl.class);

        JsonMetadataList.setTypedValues(true);
        StringWriter writer = new StringWriter();
        JsonMetadataList.toJson(back.getMetadataList(), writer);
        assertEquals(7, new ObjectMapper().readTree(writer.toString()).get(0).get(key).intValue());
    }

    private static EmitDataImpl emitData() {
        Metadata m = new Metadata();
        m.set(Audio.CHANNELS, "2");
        m.set(TikaCoreProperties.TITLE, "t");
        return new EmitDataImpl("key", List.of(m));
    }
}
