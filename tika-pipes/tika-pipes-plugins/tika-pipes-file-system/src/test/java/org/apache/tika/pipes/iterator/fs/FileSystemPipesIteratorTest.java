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
package org.apache.tika.pipes.iterator.fs;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;


public class FileSystemPipesIteratorTest {

    public static List<Path> listFiles(Path path) throws IOException {

        List<Path> result;
        try (Stream<Path> walk = Files.walk(path)) {
            result = walk.filter(Files::isRegularFile).collect(Collectors.toList());
        }
        return result;

    }

    @Test
    public void testOne() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        //PipesIteratorBaseConfig pipesIteratorBaseConfig = new PipesIteratorBaseConfig("fsf", "fse");
        FileSystemPipesIteratorConfig c = new FileSystemPipesIteratorConfig();
        StringWriter sw = new StringWriter();
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(sw, c);

        FileSystemPipesIteratorConfig deserialized = objectMapper.readValue(sw.toString(), FileSystemPipesIteratorConfig.class);
        assertEquals(c, deserialized);
    }
    /**
        TODO -- turn this back on
    @Test
    @Timeout(30000)
    public void testBasic() throws Exception {
        URL url =
                FileSystemPipesIteratorTest.class.getResource("/test-documents");
        Path root = Paths.get(url.toURI());
        List<Path> files = listFiles(root);
        Set<String> truthSet = new HashSet<>();
        for (Path p : files) {
            String fetchString = root.relativize(p).toString();
            truthSet.add(fetchString);
        }

        String fetcherName = "file-system-fetcher";
        PipesIteratorBase it = new FileSystemPipesIterator(root);
        it.setFetcherId(fetcherName);
        it.setQueueSize(2);

        Set<String> iteratorSet = new HashSet<>();
        for (FetchEmitTuple p : it) {
            iteratorSet.add(p.getFetchKey().getFetchKey());
        }

        for (String t : truthSet) {
            assertTrue(iteratorSet.contains(t), "missing in iterator set " + t);
        }
        for (String i : iteratorSet) {
            assertTrue(truthSet.contains(i), "missing in truth set " + i);
        }
    }
    **/
}
