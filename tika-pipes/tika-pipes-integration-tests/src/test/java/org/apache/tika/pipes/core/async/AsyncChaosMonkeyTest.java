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
package org.apache.tika.pipes.core.async;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.BufferedReader;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.pipes.api.FetchEmitTuple;
import org.apache.tika.pipes.api.emitter.EmitKey;
import org.apache.tika.pipes.api.fetcher.FetchKey;
import org.apache.tika.pipes.api.pipesiterator.PipesIterator;
import org.apache.tika.pipes.core.PluginsTestHelper;
import org.apache.tika.serialization.JsonMetadataList;

public class AsyncChaosMonkeyTest {

    String fetcherPluginId = "fsf";
    String emitterPluginId = "fse";

    private final String OOM = "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>" + "<mock>" +
            "<throw class=\"java.lang.OutOfMemoryError\">oom message</throw>\n</mock>";
    private final String OK = "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>" + "<mock>" +
            "<metadata action=\"add\" name=\"dc:creator\">Nikolai Lobachevsky</metadata>" +
            "<write element=\"p\">main_content</write>" +
            "</mock>";

    private final String TIMEOUT = "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>" + "<mock>" +
            "<metadata action=\"add\" name=\"dc:creator\">Nikolai Lobachevsky</metadata>" +
            "<write element=\"p\">main_content</write>" +
            "<fakeload millis=\"60000\" cpu=\"1\" mb=\"10\"/>" + "</mock>";

    private final String SYSTEM_EXIT = "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>" + "<mock>" +
            "<metadata action=\"add\" name=\"dc:creator\">Nikolai Lobachevsky</metadata>" +
            "<write element=\"p\">main_content</write>" +
            "<system_exit/>" + "</mock>";

    private final int totalFiles = 100;

    private Path inputDir;
    private Path outputDir;

    private int ok = 0;
    private int oom = 0;
    private int timeouts = 0;
    private int crash = 0;


    public Path setUp(Path tmpDir, boolean emitIntermediateResults) throws Exception {
        Path configDir = tmpDir.resolve("config");
        inputDir = tmpDir.resolve("input");
        outputDir = tmpDir.resolve("output");
        Files.createDirectories(configDir);
        Files.createDirectories(inputDir);
        Files.createDirectories(outputDir);
        ok = 0;
        oom = 0;
        timeouts = 0;
        crash = 0;


        Random r = new Random();
        for (int i = 0; i < totalFiles; i++) {
            float f = r.nextFloat();
            if (f < 0.05) {
                Files.write(inputDir.resolve(i + ".xml"), OOM.getBytes(StandardCharsets.UTF_8));
                oom++;
            } else if (f < 0.10) {
                Files.write(inputDir.resolve(i + ".xml"), SYSTEM_EXIT.getBytes(StandardCharsets.UTF_8));
                crash++;
            } else if (f < 0.13) {
                Files.write(inputDir.resolve(i + ".xml"), TIMEOUT.getBytes(StandardCharsets.UTF_8));
                timeouts++;
            } else {
                Files.write(inputDir.resolve(i + ".xml"), OK.getBytes(StandardCharsets.UTF_8));
                ok++;
            }
        }
        MockReporter.RESULTS.clear();
        return PluginsTestHelper.getFileSystemFetcherConfig(configDir, inputDir, outputDir, emitIntermediateResults);
    }

/*
    private void writeLarge(Path resolve) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(resolve, StandardCharsets.UTF_8)) {
            writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\" ?>");
            writer.write("<mock>");
            for (int i = 0; i < 10000000; i++) {
                writer.write("<write element=\"p\">hello hello hello hello hello</write>");
            }
            writer.write("</mock>");
        }
    }
*/


    @Test
    public void testBasic(@TempDir Path tmpDir) throws Exception {
        AsyncProcessor processor = new AsyncProcessor(setUp(tmpDir, false));
        for (int i = 0; i < totalFiles; i++) {
            FetchEmitTuple t = new FetchEmitTuple("myId-" + i,
                    new FetchKey(fetcherPluginId, i + ".xml"),
                    new EmitKey(emitterPluginId, "emit-" + i), new Metadata());
            processor.offer(t, 1000);
        }
        for (int i = 0; i < 10; i++) {
            processor.offer(PipesIterator.COMPLETED_SEMAPHORE, 1000);
        }
        //TODO clean this up
        while (processor.checkActive()) {
            Thread.sleep(100);
        }
        processor.close();
        Set<String> emitKeys = new HashSet<>();
        for (File f : outputDir.toFile().listFiles()) {
            emitKeys.add(f.getName());
        }
        assertEquals(ok, emitKeys.size());
        //TODO -- add mock reporter back
    }

    @Test
    public void testEmitIntermediate(@TempDir Path tmpDir) throws Exception {
        AsyncProcessor processor = new AsyncProcessor(setUp(tmpDir, true));
        for (int i = 0; i < totalFiles; i++) {
            FetchEmitTuple t = new FetchEmitTuple("myId-" + i, new FetchKey(fetcherPluginId, i + ".xml"),
                    new EmitKey(emitterPluginId, "emit-" + i), new Metadata());
            processor.offer(t, 1000);
        }
        for (int i = 0; i < 10; i++) {
            processor.offer(PipesIterator.COMPLETED_SEMAPHORE, 1000);
        }
        //TODO clean this up
        while (processor.checkActive()) {
            Thread.sleep(100);
        }
        processor.close();
        Set<String> emitKeys = new HashSet<>();
        int observedOOM = 0;
        for (File f : outputDir.toFile().listFiles()) {
            emitKeys.add(f.getName());
            List<Metadata> metadataList;
            try (BufferedReader reader = Files.newBufferedReader(f.toPath())) {
                metadataList = JsonMetadataList.fromJson(reader);
            }
            assertEquals(64,
                    metadataList.get(0).get("X-TIKA:digest:SHA-256").trim().length());
            assertEquals("application/mock+xml",
                    metadataList.get(0).get(Metadata.CONTENT_TYPE));
            String val = metadataList.get(0).get(TikaCoreProperties.PIPES_RESULT);
            if ("OOM".equals(val)) {
                observedOOM++;
            }
        }
        assertEquals(totalFiles, emitKeys.size());
        assertEquals(oom, observedOOM);
    }
}
