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

import java.nio.file.Paths;
import java.util.Iterator;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import org.apache.tika.pipes.FetchEmitTuple;

@Disabled("until we can write actual tests")
public class TestJsonPipesIterator {

    @Test
    public void testBasic() throws Exception {
        JsonPipesIterator pipesIterator = new JsonPipesIterator();
        pipesIterator.setJsonPath(Paths
                .get(this
                        .getClass()
                        .getResource("/test-documents/test.json")
                        .toURI())
                .toAbsolutePath()
                .toString());
        Iterator<FetchEmitTuple> it = pipesIterator.iterator();
        while (it.hasNext()) {
            //System.out.println(it.next());
        }
    }

    @Test
    public void testWithEmbDocBytes() throws Exception {
        JsonPipesIterator pipesIterator = new JsonPipesIterator();
        pipesIterator.setJsonPath(Paths
                .get(this
                        .getClass()
                        .getResource("/test-documents/test-with-embedded-bytes.json")
                        .toURI())
                .toAbsolutePath()
                .toString());
        Iterator<FetchEmitTuple> it = pipesIterator.iterator();
        while (it.hasNext()) {
            //System.out.println(it.next());
        }
    }


    /*
    //use this to generate test files
    public static void main(String[] args) throws Exception {
        Path p = Paths.get("/home/tallison/Intellij/tika-main/tika-pipes/tika-pipes-iterators" +
                "/tika-pipes-iterator-json/src/test/resources/test-documents/test-with-embedded" +
                "-bytes.json");
        try (BufferedWriter writer = Files.newBufferedWriter(p, StandardCharsets.UTF_8)) {
            HandlerConfig handlerConfig =
                    new HandlerConfig(BasicContentHandlerFactory.HANDLER_TYPE.TEXT,
                            HandlerConfig.PARSE_MODE.RMETA, -1, -1,
                            false);
            EmbeddedDocumentBytesConfig config = new EmbeddedDocumentBytesConfig(true);
            for (int i = 0; i < 100; i++) {
                String id = "myid-"+i;
                FetchEmitTuple t = new FetchEmitTuple(
                        id,
                        new FetchKey("fs", i + ".xml"),
                        new EmitKey("fs", i + ".xml.json"),
                        new Metadata(),
                        handlerConfig,
                        FetchEmitTuple.ON_PARSE_EXCEPTION.EMIT,
                        config);
                String line = JsonFetchEmitTuple.toJson(t);
                writer.write(line);
                writer.newLine();
            }
        }
    }*/
}
