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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.apache.tika.pipes.PipesReporter;
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
                        return;
                    }
                }
            }
        });
        readerThread.start();

        Map<PipesResult.STATUS, Long> total = runBatch(reporter, 10, 200);


        readerThread.interrupt();
        readerThread.join(1000);
        reporter.report(new TotalCountResult(30000, TotalCountResult.STATUS.COMPLETED));
        reporter.close();
        AsyncStatus asyncStatus = objectMapper.readValue(path.toFile(), AsyncStatus.class);
        Map<PipesResult.STATUS, Long> map = asyncStatus.getStatusCounts();
        assertEquals(total.size(), map.size());
        for (Map.Entry<PipesResult.STATUS, Long> e : total.entrySet()) {
            assertTrue(map.containsKey(e.getKey()), e.getKey().toString());
            assertEquals(e.getValue(), map.get(e.getKey()), e.getKey().toString());
        }
        assertEquals(AsyncStatus.ASYNC_STATUS.COMPLETED, asyncStatus.getAsyncStatus());
        assertEquals(30000, asyncStatus.getTotalCountResult().getTotalCount());
        assertEquals(TotalCountResult.STATUS.COMPLETED, asyncStatus.getTotalCountResult().getStatus());
    }

    private Map<PipesResult.STATUS, Long> runBatch(FileSystemStatusReporter reporter,
                                                   int numThreads,
                                                   int numIterations)
            throws ExecutionException, InterruptedException {
        ExecutorService executorService = Executors.newFixedThreadPool(numThreads);
        ExecutorCompletionService executorCompletionService =
                new ExecutorCompletionService(executorService);
        List<ReportWorker> workerList = new ArrayList<>();
        for (int i = 0; i < numThreads; i++) {
            ReportWorker reportWorker = new ReportWorker(reporter, numIterations);
            workerList.add(reportWorker);
            executorCompletionService.submit(reportWorker);
        }

        Map<PipesResult.STATUS, Long> total = new HashMap<>();
        int finished = 0;
        while (finished < numThreads) {
            Future<Integer> future = executorCompletionService.poll();
            if (future != null) {
                future.get();
                finished++;
            }
        }
        for (ReportWorker r : workerList) {
            Map<PipesResult.STATUS, Long> local = r.getWritten();
            for (Map.Entry<PipesResult.STATUS, Long> e : local.entrySet()) {
                Long t = total.get(e.getKey());
                if (t == null) {
                    t = e.getValue();
                } else {
                    t += e.getValue();
                }
                total.put(e.getKey(), t);
            }
        }
        return total;
    }

    private class ReportWorker implements Callable<Integer> {
        Map<PipesResult.STATUS, Long> written = new HashMap<>();

        private final PipesReporter reporter;
        private final int numIterations;
        private ReportWorker(PipesReporter reporter, int numIterations) {
            this.reporter = reporter;
            this.numIterations = numIterations;
        }
        @Override
        public Integer call() throws Exception {
            PipesResult.STATUS[] statuses = PipesResult.STATUS.values();
            Random random = new Random();
            for (int i = 0; i < numIterations; i++) {
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
            return 1;
        }

        Map<PipesResult.STATUS, Long> getWritten() {
            return written;
        }
    }
}
