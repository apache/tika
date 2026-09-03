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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.pipes.api.PipesResult;
import org.apache.tika.pipes.core.emitter.EmitDataImpl;

public class EmitDataContentBytesTest {

    @Test
    public void testContentBytesRoundTrip() throws Exception {
        Metadata m = new Metadata();
        m.set("k", "v");
        EmitDataImpl emitData = new EmitDataImpl("key", List.of(m));
        byte[] content = "the quick UTF-8 café 中文".getBytes(StandardCharsets.UTF_8);
        emitData.setContentBytes(content);
        PipesResult result = new PipesResult(PipesResult.RESULT_STATUS.PARSE_SUCCESS, emitData);

        byte[] wire = JsonPipesIpc.toBytes(result);
        PipesResult back = JsonPipesIpc.fromBytes(wire, PipesResult.class);

        assertEquals(PipesResult.RESULT_STATUS.PARSE_SUCCESS, back.status());
        assertArrayEquals(content, back.emitData().getContentBytes());
        assertEquals("v", back.emitData().getMetadataList().get(0).get("k"));
    }

    @Test
    public void testAbsentContentBytesStaysNull() throws Exception {
        EmitDataImpl emitData = new EmitDataImpl("key", List.of(new Metadata()));
        PipesResult result = new PipesResult(PipesResult.RESULT_STATUS.PARSE_SUCCESS, emitData);
        PipesResult back = JsonPipesIpc.fromBytes(JsonPipesIpc.toBytes(result), PipesResult.class);
        assertNull(back.emitData().getContentBytes());
    }
}
