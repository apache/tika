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
package org.apache.tika.fetcher;

import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;


public class FileSystemFetchIteratorTest {

    @Test
    public void testBasic() throws Exception {
        Path root = Paths.get(".");
        String fetchPrefix = "fs";
        ExecutorService es = Executors.newFixedThreadPool(1);
        ExecutorCompletionService cs = new ExecutorCompletionService(es);
        FetchIterator it = new FileSystemFetchIterator(fetchPrefix, root);

        cs.submit(it);
        Set<String> iteratorSet = new HashSet<>();
        int i = 0;
        for (FetchMetadataPair p : it) {
            iteratorSet.add(p.getFetcherString());
        }
        Future f = cs.take();
        f.get();
        List<Path> files = listFiles(root);
        Set<String> truthSet = new HashSet<>();
        for (Path p : files) {
            String fetchString = fetchPrefix+":"+root.relativize(p);
            truthSet.add(fetchString);
        }
        assertEquals(truthSet, iteratorSet);
    }

    public static List<Path> listFiles(Path path) throws IOException {

        List<Path> result;
        try (Stream<Path> walk = Files.walk(path)) {
            result = walk.filter(Files::isRegularFile)
                    .collect(Collectors.toList());
        }
        return result;

    }
}
