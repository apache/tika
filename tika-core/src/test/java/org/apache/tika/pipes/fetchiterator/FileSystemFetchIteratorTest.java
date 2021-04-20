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
package org.apache.tika.pipes.fetchiterator;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.Test;

import org.apache.tika.pipes.FetchEmitTuple;


public class FileSystemFetchIteratorTest {

    public static List<Path> listFiles(Path path) throws IOException {

        List<Path> result;
        try (Stream<Path> walk = Files.walk(path)) {
            result = walk.filter(Files::isRegularFile).collect(Collectors.toList());
        }
        return result;

    }

    @Test(timeout = 30000)
    public void testBasic() throws Exception {
        Path root = Paths.get(".");

        List<Path> files = listFiles(root);
        Set<String> truthSet = new HashSet<>();
        for (Path p : files) {
            String fetchString = root.relativize(p).toString();
            truthSet.add(fetchString);
        }

        String fetcherName = "fs";
        FetchIterator it = new FileSystemFetchIterator(root);
        it.setFetcherName(fetcherName);
        it.setQueueSize(2);

        Set<String> iteratorSet = new HashSet<>();
        for (FetchEmitTuple p : it) {
            iteratorSet.add(p.getFetchKey().getFetchKey());
        }

        assertEquals(truthSet, iteratorSet);
    }
}
