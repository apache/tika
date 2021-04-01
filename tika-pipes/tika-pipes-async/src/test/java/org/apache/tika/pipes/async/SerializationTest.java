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
package org.apache.tika.pipes.async;

import static org.junit.Assert.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.junit.Test;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.serialization.JsonMetadataDeserializer;

public class SerializationTest {

    @Test
    public void testBasic() throws Exception {
        String json = "{\"taskId\":49,\"fetchKey\":{\"fetcherName\":\"mock\"," +
                "\"fetchKey\":\"key-48\"},\"emitKey\":{\"emitterName\":\"mock\"," +
                "\"emitKey\":\"emit-48\"},\"onParseException\":\"EMIT\",\"metadataList\":" +
                "[{\"X-TIKA:Parsed-By\":" +
                "\"org.apache.tika.parser.EmptyParser\",\"X-TIKA:parse_time_millis\":" +
                "\"0\",\"X-TIKA:embedded_depth\":\"0\"}]}";

        ObjectMapper mapper = new ObjectMapper();
        SimpleModule module = new SimpleModule();
        module.addDeserializer(Metadata.class, new JsonMetadataDeserializer());
        mapper.registerModule(module);
        AsyncData asyncData = mapper.readValue(json, AsyncData.class);
        assertEquals(49, asyncData.getTaskId());
        assertEquals("mock", asyncData.getFetchKey().getFetcherName());
        assertEquals(1, asyncData.getMetadataList().size());
    }

}
