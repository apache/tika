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
package org.apache.tika.pipes.pipesiterator.json;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import org.apache.tika.pipes.api.FetchEmitTuple;
import org.apache.tika.plugins.ExtensionConfig;

@Disabled("until we can write actual tests")
public class TestJsonPipesIterator {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    public void testBasic() throws Exception {
        Path jsonPath = Paths
                .get(this
                        .getClass()
                        .getResource("/test-documents/test.json")
                        .toURI())
                .toAbsolutePath();
        JsonPipesIterator pipesIterator = createIterator(jsonPath);
        Iterator<FetchEmitTuple> it = pipesIterator.iterator();
        while (it.hasNext()) {
            //System.out.println(it.next());
        }
    }

    @Test
    public void testWithEmbDocBytes() throws Exception {
        Path jsonPath = Paths
                .get(this
                        .getClass()
                        .getResource("/test-documents/test-with-embedded-bytes.json")
                        .toURI())
                .toAbsolutePath();
        JsonPipesIterator pipesIterator = createIterator(jsonPath);
        Iterator<FetchEmitTuple> it = pipesIterator.iterator();
        while (it.hasNext()) {
            //System.out.println(it.next());
        }
    }

    private JsonPipesIterator createIterator(Path jsonPath) throws Exception {
        ObjectNode jsonConfig = OBJECT_MAPPER.createObjectNode();
        jsonConfig.put("jsonPath", jsonPath.toAbsolutePath().toString());

        ExtensionConfig extensionConfig = new ExtensionConfig("test-json-iterator", "json-pipes-iterator",
                OBJECT_MAPPER.writeValueAsString(jsonConfig));
        return JsonPipesIterator.build(extensionConfig);
    }
}
