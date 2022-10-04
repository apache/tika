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
package org.apache.tika.pipes.reporters.fs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.LongAdder;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.apache.tika.pipes.PipesResult;
import org.apache.tika.pipes.pipesiterator.PipesIterator;

public class TestFileSystemStatusReporter {

    @Test
    public void testBasic(@TempDir Path tmpDir) throws Exception {
        FileSystemStatusReporter reporter = new FileSystemStatusReporter();
        Path path = Files.createTempFile(tmpDir, "tika-fssr-", ".xml");
        reporter.setStatusFile(path.toAbsolutePath().toString());
        reporter.setReportUpdateMillis(100);
        reporter.initialize(new HashMap<>());
        final ObjectMapper objectMapper = new ObjectMapper();
        Thread readerThread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (true) {
                    try {
                        Map<PipesResult.STATUS, LongAdder> map =
                                objectMapper.readValue(path.toFile(), Map.class);
                    } catch (IOException e) {
                        //there will be problems reading from the file
                        //before it is originally written, etc.  Ignore these
                        //problems
                    }
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException e) {
                        //throw new RuntimeException(e);
                    }
                }
            }
        });
        readerThread.start();
        PipesResult.STATUS[] statuses = PipesResult.STATUS.values();
        Map<PipesResult.STATUS, Long> written = new HashMap<>();
        Random random = new Random();
        for (int i = 0; i < 1000; i++) {
            PipesResult.STATUS status = statuses[random.nextInt(statuses.length)];
            PipesResult pipesResult = new PipesResult(status);
            reporter.report(PipesIterator.COMPLETED_SEMAPHORE, pipesResult, 100l);
            Long cnt = written.get(status);
            if (cnt == null) {
                written.put(status, new Long(1));
            } else {
                cnt++;
                written.put(status, cnt);
            }
            if (i % 100 == 0) {
                Thread.sleep(94);
            }
        }
        reporter.close();
        readerThread.interrupt();
        TypeReference<HashMap<PipesResult.STATUS, Long>> typeRef
                = new TypeReference<HashMap<PipesResult.STATUS, Long>>() {};
        Map<PipesResult.STATUS, Long> map = objectMapper.readValue(path.toFile(), typeRef);


        assertEquals(written.size(), map.size());
        for (Map.Entry<PipesResult.STATUS, Long> e : written.entrySet()) {
            assertTrue(map.containsKey(e.getKey()), e.getKey().toString());
            assertEquals(e.getValue(), map.get(e.getKey()), e.getKey().toString());
        }
    }

}
