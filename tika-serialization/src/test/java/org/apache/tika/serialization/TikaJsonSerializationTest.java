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
package org.apache.tika.serialization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.Optional;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import org.apache.tika.serialization.mocks.ClassC;

public class TikaJsonSerializationTest {

    @Test
    public void testBasic() throws Exception {
        StringWriter sw = new StringWriter();
        ClassC classA = new ClassC();
        try (JsonGenerator jsonGenerator = new ObjectMapper().createGenerator(sw)) {
            TikaJsonSerializer.serialize(classA, jsonGenerator);
        }
        JsonNode root = new ObjectMapper().readTree(new StringReader(sw.toString()));
        Optional opt = TikaJsonDeserializer.deserializeObject(root);
        assertTrue(opt.isPresent());
        assertEquals(classA, opt.get());

    }

}
