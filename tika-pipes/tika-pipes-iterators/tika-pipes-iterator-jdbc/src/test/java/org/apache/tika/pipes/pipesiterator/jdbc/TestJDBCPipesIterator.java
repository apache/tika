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
package org.apache.tika.pipes.pipesiterator.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.fail;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.apache.tika.pipes.FetchEmitTuple;
import org.apache.tika.pipes.pipesiterator.PipesIterator;

public class TestJDBCPipesIterator {

    static final String TABLE = "fetchkeys";
    static final String db = "mydb";
    private static final int NUM_ROWS = 1000;
    static Connection CONNECTION;

    @TempDir
    static Path DB_DIR;

    @BeforeAll
    public static void setUp() throws Exception {

        CONNECTION =
                DriverManager.getConnection("jdbc:h2:file:" +
                        DB_DIR.toAbsolutePath() + "/" + db);
        String sql = "create table " + TABLE + " (id varchar(128), " +
                "project varchar(128), " +
                "fetchKey varchar(128))";
        CONNECTION.createStatement().execute(sql);

        for (int i = 0; i < NUM_ROWS; i++) {
            sql = "insert into " + TABLE + " (id, project, fetchKey) values ('id" + i +
                    "','project" + (i % 2 == 0 ? "a" : "b") + "','fk" + i + "')";
            CONNECTION.createStatement().execute(sql);
        }
        sql = "select count(1) from " + TABLE;
        ResultSet rs = CONNECTION.createStatement().executeQuery(sql);
        while (rs.next()) {
            int cnt = rs.getInt(1);
            assertEquals(NUM_ROWS, cnt);
        }
    }

    @AfterAll
    public static void tearDown() throws Exception {
        CONNECTION.close();
    }

    @Test
    public void testSimple() throws Exception {
        int numConsumers = 5;

        PipesIterator pipesIterator = getConfig();
        ExecutorService es = Executors.newFixedThreadPool(numConsumers);
        ExecutorCompletionService<Integer> completionService =
                new ExecutorCompletionService<>(es);
        ArrayBlockingQueue<FetchEmitTuple> queue = new ArrayBlockingQueue<>(100);
        List<MockFetcher> fetchers = new ArrayList<>();
        for (int i = 0; i < numConsumers; i++) {
            MockFetcher mockFetcher = new MockFetcher(queue);
            fetchers.add(mockFetcher);
            completionService.submit(mockFetcher);
        }
        int offered = 0;
        for (FetchEmitTuple t : pipesIterator) {
            queue.put(t);
            offered++;
        }
        assertEquals(NUM_ROWS, offered);
        for (int i = 0; i < numConsumers; i++) {
            queue.put(PipesIterator.COMPLETED_SEMAPHORE);
        }
        int processed = 0;
        int completed = 0;
        while (completed < numConsumers) {
            Future<Integer> f = completionService.take();
            int fetched = f.get();
            processed += fetched;
            completed++;
        }
        assertEquals(NUM_ROWS, processed);
        int cnt = 0;
        Matcher m = Pattern.compile("fk(\\d+)").matcher("");
        for (MockFetcher f : fetchers) {
            for (FetchEmitTuple p : f.pairs) {
                String k = p.getFetchKey().getFetchKey();
                String num = "";
                if (m.reset(k).find()) {
                    num = m.group(1);
                } else {
                    fail("failed to find key pattern: " + k);
                }
                String aOrB = Integer.parseInt(num) % 2 == 0 ? "a" : "b";
                assertEquals("id" + num, p.getId());
                assertEquals("project" + aOrB, p.getMetadata().get("MY_PROJECT"));
                assertNull(p.getMetadata().get("fetchKey"));
                assertNull(p.getMetadata().get("MY_FETCHKEY"));
                cnt++;
            }
        }
        assertEquals(NUM_ROWS, cnt);
    }

    private PipesIterator getConfig() throws Exception {
        String config = "<?xml version=\"1.0\" encoding=\"UTF-8\" ?><properties>\n" +
                "        <pipesIterator " +
                "       class=\"org.apache.tika.pipes.pipesiterator.jdbc.JDBCPipesIterator\">\n" +
                "            <params>\n" +
                "                <fetcherName>s3f</fetcherName>\n" +
                "                <emitterName>s3e</emitterName>\n" +
                "                <queueSize>57</queueSize>\n" +
                "                <idColumn>my_id</idColumn>\n" +
                "                <fetchKeyColumn>my_fetchkey</fetchKeyColumn>\n" +
                "                <emitKeyColumn>my_fetchkey</emitKeyColumn>\n" +
                "                <select>" +
                "select id as my_id, project as my_project, fetchKey as my_fetchKey " +
                "from fetchkeys</select>\n" +
                "                <connection>jdbc:h2:file:" + DB_DIR.toAbsolutePath() + "/" +
                    db + "</connection>\n" +
                "            </params>\n" +
                "        </pipesIterator>\n" +
                "</properties>";
        Path tmp = Files.createTempFile("tika-jdbc-", ".xml");
        Files.write(tmp, config.getBytes(StandardCharsets.UTF_8));
        PipesIterator manager =
                PipesIterator.build(tmp);
        Files.delete(tmp);
        return manager;
    }

    private static class MockFetcher implements Callable<Integer> {
        private final ArrayBlockingQueue<FetchEmitTuple> queue;
        private final List<FetchEmitTuple> pairs = new ArrayList<>();

        private MockFetcher(ArrayBlockingQueue<FetchEmitTuple> queue) {
            this.queue = queue;
        }

        @Override
        public Integer call() throws Exception {
            while (true) {
                FetchEmitTuple t = queue.poll(1, TimeUnit.HOURS);
                if (t == PipesIterator.COMPLETED_SEMAPHORE) {
                    return pairs.size();
                }
                pairs.add(t);
            }
        }
    }
}
