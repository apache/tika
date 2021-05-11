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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.pipes.FetchEmitTuple;
import org.apache.tika.pipes.emitter.EmitData;
import org.apache.tika.pipes.emitter.EmitKey;
import org.apache.tika.pipes.fetcher.FetchKey;
import org.apache.tika.pipes.pipesiterator.PipesIterator;
import org.apache.tika.utils.ProcessUtils;

public class AsyncProcessorTest {

    private final String OOM = "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>" + "<mock>" +
            "<throw class=\"java.lang.OutOfMemoryError\">oom message</throw>\n</mock>";
    private final String OK = "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>" + "<mock>" +
            "<metadata action=\"add\" name=\"dc:creator\">Nikolai Lobachevsky</metadata>" +
            "<write element=\"p\">main_content</write>" + "</mock>";
    private final int totalFiles = 100;
    private Path tikaConfigPath;
    private Path inputDir;
    private int ok = 0;
    private int oom = 0;

    @Before
    public void setUp() throws SQLException, IOException {
        inputDir = Files.createTempDirectory("tika-async-");
        tikaConfigPath = Files.createTempFile("tika-config-", ".xml");
        String xml =
                "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>" + "<properties>" + "  <emitters>" +
                "  <emitter class=\"org.apache.tika.pipes.async.MockEmitter\">\n" +
                "    <params>\n" + "      <name>mock</name>\n" + "    </params>" + "  </emitter>" +
                "  </emitters>" + "  <fetchers>" +
                "    <fetcher class=\"org.apache.tika.pipes.fetcher.FileSystemFetcher\">" +
                "      <params><name>mock</name>\n" + "      <basePath>" +
                ProcessUtils.escapeCommandLine(inputDir.toAbsolutePath().toString()) +
                "</basePath></params>\n" + "    </fetcher>" + "  </fetchers>" +
                        "<async><params><tikaConfig>" +
                        ProcessUtils.escapeCommandLine(tikaConfigPath.toAbsolutePath().toString()) +
                        "</tikaConfig><forkedJvmArgs><arg>-Xmx256m</arg" +
                        "></forkedJvmArgs><maxForEmitBatchBytes>1000000</maxForEmitBatchBytes>" +
                        "</params></async>" +
                        "</properties>";
        Files.write(tikaConfigPath, xml.getBytes(StandardCharsets.UTF_8));
        Random r = new Random();
        for (int i = 0; i < totalFiles; i++) {
            if (r.nextFloat() < 0.1) {
                Files.write(inputDir.resolve(i + ".xml"), OOM.getBytes(StandardCharsets.UTF_8));
                oom++;
            } else {
                Files.write(inputDir.resolve(i + ".xml"), OK.getBytes(StandardCharsets.UTF_8));
                ok++;
            }
        }
    }

    @After
    public void tearDown() throws SQLException, IOException {
        Files.delete(tikaConfigPath);
        FileUtils.deleteDirectory(inputDir.toFile());
    }

    @Test
    public void testBasic() throws Exception {
        AsyncProcessor processor = new AsyncProcessor(tikaConfigPath);
        for (int i = 0; i < totalFiles; i++) {
            FetchEmitTuple t = new FetchEmitTuple("myId",
                    new FetchKey("mock", i + ".xml"),
                    new EmitKey("mock", "emit-" + i), new Metadata());
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
        for (EmitData d : MockEmitter.EMIT_DATA) {
            emitKeys.add(d.getEmitKey().getEmitKey());
        }
        assertEquals(ok, emitKeys.size());
    }
}
