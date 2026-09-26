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
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.BufferedReader;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.apache.tika.metadata.HttpHeaders;
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

    // hangs 60s and the expected timeout count is asserted exactly, so the default config's
    // (tika-config-basic.json) short progressTimeoutMillis is what detects it: keep it short
    private final String TIMEOUT = "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>" + "<mock>" +
            "<metadata action=\"add\" name=\"dc:creator\">Nikolai Lobachevsky</metadata>" +
            "<write element=\"p\">main_content</write>" +
            "<fakeload millis=\"60000\" cpu=\"1\" mb=\"10\"/>" + "</mock>";

    private final String SYSTEM_EXIT = "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>" + "<mock>" +
            "<metadata action=\"add\" name=\"dc:creator\">Nikolai Lobachevsky</metadata>" +
            "<write element=\"p\">main_content</write>" +
            "<system_exit/>" + "</mock>";

    private final String STACK_OVERFLOW = "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>" + "<mock>" +
            "<throw class=\"java.lang.StackOverflowError\">stack overflow message</throw>\n</mock>";

    private final int totalFiles = 20;

    private Path inputDir;
    private Path outputDir;

    private int ok = 0;
    private int oom = 0;
    private int timeouts = 0;
    private int systemExit = 0;
    private int stackOverflow = 0;


    public Path setUp(Path tmpDir, boolean emitIntermediateResults) throws Exception {
        Path configDir = tmpDir.resolve("config");
        inputDir = tmpDir.resolve("input");
        outputDir = tmpDir.resolve("output");
        Files.createDirectories(configDir);
        Files.createDirectories(inputDir);
        Files.createDirectories(outputDir);

        // fixed mix, shuffled deterministically; every crash costs a fork restart
        oom = 2;
        systemExit = 2;
        timeouts = 1;
        stackOverflow = 2;
        ok = totalFiles - oom - systemExit - timeouts - stackOverflow;
        List<String> contents = new ArrayList<>();
        contents.addAll(Collections.nCopies(oom, OOM));
        contents.addAll(Collections.nCopies(systemExit, SYSTEM_EXIT));
        contents.addAll(Collections.nCopies(timeouts, TIMEOUT));
        contents.addAll(Collections.nCopies(stackOverflow, STACK_OVERFLOW));
        contents.addAll(Collections.nCopies(ok, OK));
        Collections.shuffle(contents, new Random(42));
        for (int i = 0; i < totalFiles; i++) {
            Files.writeString(inputDir.resolve(i + ".xml"), contents.get(i), StandardCharsets.UTF_8);
        }
        MockReporter.RESULTS.clear();
        Path config = PluginsTestHelper.getFileSystemFetcherConfig(configDir, inputDir, outputDir, emitIntermediateResults);
        // two workers: enough to show a crash in one leaves the other alone
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode root = (ObjectNode) mapper.readTree(config.toFile());
        ((ObjectNode) root.get("pipes")).put("numClients", 2);
        mapper.writeValue(config.toFile(), root);
        return config;
    }

    @Test
    public void testBasic(@TempDir Path tmpDir) throws Exception {
        AsyncProcessor processor = AsyncProcessor.load(setUp(tmpDir, false));
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

        // When emitIntermediateResults = false, only successful files are emitted
        assertEquals(ok, emitKeys.size());


    }

    @Test
    public void testEmitIntermediate(@TempDir Path tmpDir) throws Exception {
        AsyncProcessor processor = AsyncProcessor.load(setUp(tmpDir, true));
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
        int observedTimeout = 0;
        int observedUnspecifiedCrash = 0;
        int observedSuccess = 0;

        for (File f : outputDir.toFile().listFiles()) {
            emitKeys.add(f.getName());
            List<Metadata> metadataList;
            try (BufferedReader reader = Files.newBufferedReader(f.toPath())) {
                metadataList = JsonMetadataList.fromJson(reader);
            }
            String sha = metadataList.get(0).get("tk:digest:SHA-256");
            assertNotNull(sha, "no SHA-256 digest on " + f.getName() + " (pipesResult=" +
                    metadataList.get(0).get(TikaCoreProperties.PIPES_RESULT) + ")");
            assertEquals(64, sha.trim().length());
            assertEquals("application/mock+xml",
                    metadataList.get(0).get(HttpHeaders.CONTENT_TYPE));
            String val = metadataList.get(0).get(TikaCoreProperties.PIPES_RESULT);
            if (val == null) {
                // Null means success (no crash status was set)
                observedSuccess++;
            } else if ("OOM".equals(val)) {
                observedOOM++;
            } else if ("TIMEOUT".equals(val)) {
                observedTimeout++;
            } else if ("UNSPECIFIED_CRASH".equals(val)) {
                observedUnspecifiedCrash++;
            }
        }

        // When emitIntermediateResults = true, all files are emitted (success and crashes)
        assertEquals(totalFiles, emitKeys.size());

        // Verify each crash type was counted correctly
        assertEquals(ok, observedSuccess, "Expected " + ok + " successful files but observed " + observedSuccess);
        assertEquals(oom, observedOOM, "Expected " + oom + " OOM crashes but observed " + observedOOM);
        assertEquals(timeouts, observedTimeout, "Expected " + timeouts + " timeout crashes but observed " + observedTimeout);

        // System exit and stack overflow should both be UNSPECIFIED_CRASH
        int expectedUnspecifiedCrash = systemExit + stackOverflow;
        assertEquals(expectedUnspecifiedCrash, observedUnspecifiedCrash,
                "Expected " + expectedUnspecifiedCrash + " unspecified crashes (systemExit=" +
                systemExit + " + stackOverflow=" + stackOverflow + ") but observed " + observedUnspecifiedCrash);

    }
}
