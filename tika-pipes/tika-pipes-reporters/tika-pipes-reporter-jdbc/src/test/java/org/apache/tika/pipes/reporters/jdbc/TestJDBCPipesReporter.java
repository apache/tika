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
package org.apache.tika.pipes.reporters.jdbc;

import static org.apache.tika.pipes.PipesResult.STATUS.PARSE_SUCCESS;
import static org.apache.tika.pipes.PipesResult.STATUS.PARSE_SUCCESS_WITH_EXCEPTION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
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
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import org.apache.tika.pipes.FetchEmitTuple;
import org.apache.tika.pipes.PipesReporter;
import org.apache.tika.pipes.PipesResult;
import org.apache.tika.pipes.async.AsyncConfig;
import org.apache.tika.pipes.emitter.EmitKey;
import org.apache.tika.pipes.fetcher.FetchKey;
import org.apache.tika.pipes.pipesiterator.TotalCountResult;

public class TestJDBCPipesReporter {

    @Test
    public void testBasic() throws Exception {
        int numThreads = 10;
        int numIterations = 200;
        String connectionString = "jdbc:h2:mem:test_tika";
        JDBCPipesReporter reporter = new JDBCPipesReporter();
        reporter.setConnection(connectionString);
        reporter.initialize(new HashMap<>());

        Map<PipesResult.STATUS, Long> expected = runBatch(reporter, numThreads, numIterations);
        reporter.close();
        Map<PipesResult.STATUS, Long> total = countReported(connectionString);
        assertEquals(expected.size(), total.size());
        long sum = 0;
        for (Map.Entry<PipesResult.STATUS, Long> e : expected.entrySet()) {
            assertTrue(total.containsKey(e.getKey()), e.getKey().toString());
            assertEquals(e.getValue(), total.get(e.getKey()), e.getKey().toString());
            sum += e.getValue();
        }
        assertEquals(numThreads * numIterations, sum);
    }

    @Test
    public void testIncludes() throws Exception {
        Path p = Paths.get(this.getClass().getResource("/tika-config-includes.xml").toURI());
        AsyncConfig asyncConfig = AsyncConfig.load(p);
        PipesReporter reporter = asyncConfig.getPipesReporter();
        int numThreads = 10;
        int numIterations = 200;
        String connectionString = "jdbc:h2:mem:test_tika";

        Map<PipesResult.STATUS, Long> expected = runBatch(reporter, numThreads, numIterations);
        reporter.close();
        Map<PipesResult.STATUS, Long> total = countReported(connectionString);
        assertEquals(2, total.size());
        long sum = 0;
        for (Map.Entry<PipesResult.STATUS, Long> e : expected.entrySet()) {
            if (e.getKey() == PARSE_SUCCESS || e.getKey() == PARSE_SUCCESS_WITH_EXCEPTION) {
                assertTrue(total.containsKey(e.getKey()), e.getKey().toString());
                assertEquals(e.getValue(), total.get(e.getKey()), e.getKey().toString());
            } else {
                assertFalse(total.containsKey(e.getKey()), e.getKey().toString());
            }
            sum += e.getValue();
        }
        assertEquals(numThreads * numIterations, sum);
    }

    @Test
    public void testExcludes() throws Exception {
        Path p = Paths.get(this.getClass().getResource("/tika-config-excludes.xml").toURI());
        AsyncConfig asyncConfig = AsyncConfig.load(p);
        PipesReporter reporter = asyncConfig.getPipesReporter();
        int numThreads = 10;
        int numIterations = 200;
        String connectionString = "jdbc:h2:mem:test_tika";

        Map<PipesResult.STATUS, Long> expected = runBatch(reporter, numThreads, numIterations);
        reporter.close();
        Map<PipesResult.STATUS, Long> total = countReported(connectionString);
        assertEquals(15, total.size());
        long sum = 0;
        for (Map.Entry<PipesResult.STATUS, Long> e : expected.entrySet()) {
            if (e.getKey() != PARSE_SUCCESS && e.getKey() != PARSE_SUCCESS_WITH_EXCEPTION) {
                assertTrue(total.containsKey(e.getKey()), e.getKey().toString());
                assertEquals(e.getValue(), total.get(e.getKey()), e.getKey().toString());
            } else {
                assertFalse(total.containsKey(e.getKey()), e.getKey().toString());
            }
            sum += e.getValue();
        }
        assertEquals(numThreads * numIterations, sum);
    }


    private Map<PipesResult.STATUS, Long> countReported(String connectionString) throws
            SQLException {
        Map<PipesResult.STATUS, Long> counts = new HashMap<>();
        try (Connection connection = DriverManager.getConnection(connectionString)) {
            try (Statement st = connection.createStatement()) {
                String sql = "select * from tika_status";
                try (ResultSet rs = st.executeQuery(sql)) {
                    while (rs.next()) {
                        String fetchKey = rs.getString(1);
                        String name = rs.getString(2);
                        PipesResult.STATUS status = PipesResult.STATUS.valueOf(name);
                        Long cnt = counts.get(status);
                        if (cnt == null) {
                            cnt = 1L;
                        } else {
                            cnt++;
                        }
                        counts.put(status, cnt);
                    }
                }
            }
        }
        return counts;
    }

    private Map<PipesResult.STATUS, Long> runBatch(PipesReporter reporter,
                                                   int numThreads,
                                                   int numIterations)
            throws ExecutionException, InterruptedException {
        ExecutorService executorService = Executors.newFixedThreadPool(numThreads);
        ExecutorCompletionService<Integer> executorCompletionService =
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

    private static class ReportWorker implements Callable<Integer> {
        Map<PipesResult.STATUS, Long> written = new HashMap<>();
        private static final AtomicInteger TOTAL_ADDED = new AtomicInteger(0);
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
                String id = "id " + TOTAL_ADDED.getAndIncrement();
                FetchEmitTuple t = new FetchEmitTuple(id,
                        new FetchKey("fetcher", "fetchKey"),
                        new EmitKey("emitter", id)
                );

                reporter.report(t, pipesResult, 100L);
                Long cnt = written.get(status);
                if (cnt == null) {
                    written.put(status, 1L);
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
