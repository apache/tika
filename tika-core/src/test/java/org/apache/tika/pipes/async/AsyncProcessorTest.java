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

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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

    private Path tikaConfigPath;

    @TempDir
    private Path inputDir;

    @TempDir
    private Path configDir;

    private int ok = 0;
    private int oom = 0;
    private int timeouts = 0;
    private int crash = 0;


    @BeforeEach
    public void setUp() throws SQLException, IOException {
        tikaConfigPath = Files.createTempFile(configDir, "tika-config-", ".xml");
        String xml =
                "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>" + "<properties>" + "  <emitters>" +
                "  <emitter class=\"org.apache.tika.pipes.async.MockEmitter\">\n" +
                "         <name>mock</name>\n" + "  </emitter>" +
                "  </emitters>" + "  <fetchers>" +
                "    <fetcher class=\"org.apache.tika.pipes.fetcher.fs.FileSystemFetcher\">" +
                "      <name>mock</name>\n" + "      <basePath>" +
                ProcessUtils.escapeCommandLine(inputDir.toAbsolutePath().toString()) +
                "</basePath>\n" + "    </fetcher>" + "  </fetchers>" +
                        "<async><tikaConfig>" +
                        ProcessUtils.escapeCommandLine(tikaConfigPath.toAbsolutePath().toString()) +
                        "</tikaConfig><forkedJvmArgs><arg>-Xmx512m</arg" +
                        "></forkedJvmArgs><maxForEmitBatchBytes>1000000</maxForEmitBatchBytes>" +
                        "<timeoutMillis>5000</timeoutMillis>" +
                        "<numClients>4</numClients></async>" +
                        "</properties>";
        Files.write(tikaConfigPath, xml.getBytes(StandardCharsets.UTF_8));
        Random r = new Random();
        for (int i = 0; i < totalFiles; i++) {
            float f = r.nextFloat();
            if (f < 0.05) {
                Files.write(inputDir.resolve(i + ".xml"), OOM.getBytes(StandardCharsets.UTF_8));
                oom++;
            } else if (f < 0.10) {
                Files.write(inputDir.resolve(i + ".xml"), TIMEOUT.getBytes(StandardCharsets.UTF_8));
                timeouts++;
            } else if (f < 0.15) {
                Files.write(inputDir.resolve(i + ".xml"), SYSTEM_EXIT.getBytes(StandardCharsets.UTF_8));
                crash++;
            } else {
                Files.write(inputDir.resolve(i + ".xml"), OK.getBytes(StandardCharsets.UTF_8));
                ok++;
            }
        }
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
