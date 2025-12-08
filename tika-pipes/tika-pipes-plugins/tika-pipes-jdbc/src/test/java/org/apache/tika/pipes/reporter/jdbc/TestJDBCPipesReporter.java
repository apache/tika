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
package org.apache.tika.pipes.reporter.jdbc;

import static org.apache.tika.pipes.api.PipesResult.RESULT_STATUS.PARSE_SUCCESS;
import static org.apache.tika.pipes.api.PipesResult.RESULT_STATUS.PARSE_SUCCESS_WITH_EXCEPTION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.apache.tika.pipes.api.FetchEmitTuple;
import org.apache.tika.pipes.api.PipesResult;
import org.apache.tika.pipes.api.emitter.EmitKey;
import org.apache.tika.pipes.api.fetcher.FetchKey;
import org.apache.tika.pipes.api.pipesiterator.TotalCountResult;
import org.apache.tika.pipes.api.reporter.PipesReporter;
import org.apache.tika.plugins.ExtensionConfig;


public class TestJDBCPipesReporter {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String JSON_TEMPLATE = """
            {
                "connectionString":"CONNECTION_STRING"
            }
            """;

    private static final String JSON_TEMPLATE_INCLUDES = """
            {
                "connectionString":"CONNECTION_STRING",
                "includes": ["PARSE_SUCCESS", "PARSE_SUCCESS_WITH_EXCEPTION"]
            }
            """;

    private static final String JSON_TEMPLATE_EXCLUDES = """
            {
                "connectionString":"CONNECTION_STRING",
                "excludes": ["PARSE_SUCCESS", "PARSE_SUCCESS_WITH_EXCEPTION"]
            }
            """;


    @Test
    public void testBasic(@TempDir Path tmpDir) throws Exception {
        Files.createDirectories(tmpDir.resolve("db"));
        Path dbDir = tmpDir.resolve("db/h2");
        String connectionString = "jdbc:h2:file:" + dbDir.toAbsolutePath();

        int numThreads = 10;
        int numIterations = 200;
        String json = JSON_TEMPLATE.replace("CONNECTION_STRING", connectionString)
                .replace("\\", "/");
        JDBCPipesReporter reporter = JDBCPipesReporter.build(new ExtensionConfig("test-jdbc", "jdbc-reporter", json));

        Map<PipesResult.RESULT_STATUS, Long> expected = runBatch(reporter, numThreads, numIterations);
        reporter.close();

        Map<PipesResult.RESULT_STATUS, Long> total = countReported(connectionString);
        assertEquals(expected.size(), total.size());
        long sum = 0;
        for (Map.Entry<PipesResult.RESULT_STATUS, Long> e : expected.entrySet()) {
            assertTrue(total.containsKey(e.getKey()), e.getKey().toString());
            assertEquals(e.getValue(), total.get(e.getKey()), e.getKey().toString());
            sum += e.getValue();
        }
        assertEquals(numThreads * numIterations, sum);
    }

    @Test
    public void testIncludes(@TempDir Path tmpDir) throws Exception {
        Files.createDirectories(tmpDir.resolve("db"));
        Path dbDir = tmpDir.resolve("db/h2");
        String connectionString = "jdbc:h2:file:" + dbDir.toAbsolutePath();


        String json = JSON_TEMPLATE_INCLUDES.replace("CONNECTION_STRING", connectionString)
                .replace("\\", "/");
        JDBCPipesReporter reporter = JDBCPipesReporter.build(new ExtensionConfig("", "", json));
        int numThreads = 10;
        int numIterations = 200;

        Map<PipesResult.RESULT_STATUS, Long> expected = runBatch(reporter, numThreads, numIterations);
        reporter.close();
        Map<PipesResult.RESULT_STATUS, Long> total = countReported(connectionString);
        assertEquals(2, total.size());
        long sum = 0;
        for (Map.Entry<PipesResult.RESULT_STATUS, Long> e : expected.entrySet()) {
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
    public void testExcludes(@TempDir Path tmpDir) throws Exception {
        Files.createDirectories(tmpDir.resolve("db"));
        Path dbDir = tmpDir.resolve("db/h2");
        String connectionString = "jdbc:h2:file:" + dbDir.toAbsolutePath();

        String json = JSON_TEMPLATE_EXCLUDES.replace("CONNECTION_STRING", connectionString)
                .replace("\\", "/");
        JDBCPipesReporter reporter = JDBCPipesReporter.build(new ExtensionConfig("", "", json));
        int numThreads = 10;
        int numIterations = 200;

        Map<PipesResult.RESULT_STATUS, Long> expected = runBatch(reporter, numThreads, numIterations);
        reporter.close();
        Map<PipesResult.RESULT_STATUS, Long> total = countReported(connectionString);
        assertEquals(16, total.size());
        long sum = 0;
        for (Map.Entry<PipesResult.RESULT_STATUS, Long> e : expected.entrySet()) {
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

    private Map<PipesResult.RESULT_STATUS, Long> countReported(String connectionString) throws
            SQLException {
        Map<PipesResult.RESULT_STATUS, Long> counts = new HashMap<>();
        try (Connection connection = DriverManager.getConnection(connectionString)) {
            try (Statement st = connection.createStatement()) {
                String sql = "select * from tika_status";
                try (ResultSet rs = st.executeQuery(sql)) {
                    while (rs.next()) {
                        String fetchKey = rs.getString(1);
                        String name = rs.getString(2);
                        PipesResult.RESULT_STATUS status = PipesResult.RESULT_STATUS.valueOf(name);
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

    private Map<PipesResult.RESULT_STATUS, Long> runBatch(PipesReporter reporter,
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

        Map<PipesResult.RESULT_STATUS, Long> total = new HashMap<>();
        int finished = 0;
        while (finished < numThreads) {
            Future<Integer> future = executorCompletionService.poll();
            if (future != null) {
                future.get();
                finished++;
            }
        }
        for (ReportWorker r : workerList) {
            Map<PipesResult.RESULT_STATUS, Long> local = r.getWritten();
            for (Map.Entry<PipesResult.RESULT_STATUS, Long> e : local.entrySet()) {
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
        Map<PipesResult.RESULT_STATUS, Long> written = new HashMap<>();
        private static final AtomicInteger TOTAL_ADDED = new AtomicInteger(0);
        private final PipesReporter reporter;
        private final int numIterations;
        private ReportWorker(PipesReporter reporter, int numIterations) {
            this.reporter = reporter;
            this.numIterations = numIterations;
        }
        @Override
        public Integer call() throws Exception {
            PipesResult.RESULT_STATUS[] statuses = PipesResult.RESULT_STATUS.values();
            Random random = new Random();
            for (int i = 0; i < numIterations; i++) {
                PipesResult.RESULT_STATUS status = statuses[random.nextInt(statuses.length)];
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

        Map<PipesResult.RESULT_STATUS, Long> getWritten() {
            return written;
        }
    }

}
