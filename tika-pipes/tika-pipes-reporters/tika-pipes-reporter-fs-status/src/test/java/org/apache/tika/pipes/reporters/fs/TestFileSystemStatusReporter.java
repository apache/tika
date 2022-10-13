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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.apache.tika.pipes.PipesResult;
import org.apache.tika.pipes.async.AsyncStatus;
import org.apache.tika.pipes.pipesiterator.PipesIterator;
import org.apache.tika.pipes.pipesiterator.TotalCountResult;

public class TestFileSystemStatusReporter {

    @Test
    public void testBasic(@TempDir Path tmpDir) throws Exception {
        FileSystemStatusReporter reporter = new FileSystemStatusReporter();
        Path path = Files.createTempFile(tmpDir, "tika-fssr-", ".xml");
        reporter.setStatusFile(path.toAbsolutePath().toString());
        reporter.setReportUpdateMillis(100);
        reporter.initialize(new HashMap<>());
        final ObjectMapper objectMapper = JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .build();
        Thread readerThread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (true) {
                    try {
                        AsyncStatus asyncStatus =
                                objectMapper.readValue(path.toFile(), AsyncStatus.class);
                        assertEquals(TotalCountResult.STATUS.NOT_COMPLETED,
                                asyncStatus.getTotalCountResult().getStatus());

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
                written.put(status, 1l);
            } else {
                cnt++;
                written.put(status, cnt);
            }
            if (i % 100 == 0) {
                Thread.sleep(94);
                reporter.report(new TotalCountResult(Math.round((100 + (double) i / (double) 1000)),
                        TotalCountResult.STATUS.NOT_COMPLETED));
            }
        }
        readerThread.interrupt();
        readerThread.join(1000);
        reporter.report(new TotalCountResult(30000, TotalCountResult.STATUS.COMPLETED));
        reporter.close();
        AsyncStatus asyncStatus = objectMapper.readValue(path.toFile(), AsyncStatus.class);
        Map<PipesResult.STATUS, Long> map = asyncStatus.getStatusCounts();
        assertEquals(written.size(), map.size());
        for (Map.Entry<PipesResult.STATUS, Long> e : written.entrySet()) {
            assertTrue(map.containsKey(e.getKey()), e.getKey().toString());
            assertEquals(e.getValue(), map.get(e.getKey()), e.getKey().toString());
        }
        assertEquals(AsyncStatus.ASYNC_STATUS.COMPLETED, asyncStatus.getAsyncStatus());
        assertEquals(30000, asyncStatus.getTotalCountResult().getTotalCount());
        assertEquals(TotalCountResult.STATUS.COMPLETED, asyncStatus.getTotalCountResult().getStatus());
    }

    /*@Test
    //need to turn this into an actual test
    public void oneOff() throws Exception {
        Path config = Paths.get("");
        AsyncProcessor.main(new String[]{ config.toAbsolutePath().toString()});
    }*/

}
