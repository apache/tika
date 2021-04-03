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
package org.apache.tika.pipes.fetchiterator.jdbc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.IOException;
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

import org.apache.commons.io.FileUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import org.apache.tika.config.TikaConfig;
import org.apache.tika.pipes.fetchiterator.FetchEmitTuple;
import org.apache.tika.pipes.fetchiterator.FetchIterator;

public class TestJDBCFetchIterator {

    static final String TABLE = "fetchkeys";
    static final String db = "mydb";
    private static final int NUM_ROWS = 1000;
    static Connection CONNECTION;
    static Path DB_DIR;

    @BeforeClass
    public static void setUp() throws Exception {
        DB_DIR = Files.createTempDirectory("tika-jdbc-fetchiterator-test-");

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

    @AfterClass
    public static void tearDown() throws IOException {
        FileUtils.deleteDirectory(DB_DIR.toFile());
    }

    @Test
    public void testSimple() throws Exception {
        TikaConfig tk = getConfig();
        int numConsumers = 5;
        FetchIterator fetchIterator = tk.getFetchIterator();
        ExecutorService es = Executors.newFixedThreadPool(numConsumers + 1);
        ExecutorCompletionService<Integer> completionService =
                new ExecutorCompletionService<>(es);
        ArrayBlockingQueue<FetchEmitTuple> queue = new ArrayBlockingQueue<>(100);
        List<MockFetcher> fetchers = new ArrayList<>();
        for (int i = 0; i < numConsumers; i++) {
            MockFetcher mockFetcher = new MockFetcher(queue);
            fetchers.add(mockFetcher);
            completionService.submit(mockFetcher);
        }
        for (FetchEmitTuple t : fetchIterator) {
            queue.offer(t);
        }
        for (int i = 0; i < numConsumers; i++) {
            queue.offer(FetchIterator.COMPLETED_SEMAPHORE);
        }
        int processed = 0;
        int completed = 0;
        while (completed < numConsumers) {
            Future<Integer> f = completionService.take();
            processed += f.get();
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
                assertEquals("id" + num, p.getMetadata().get("MY_ID"));
                assertEquals("project" + aOrB, p.getMetadata().get("MY_PROJECT"));
                assertNull(p.getMetadata().get("fetchKey"));
                assertNull(p.getMetadata().get("MY_FETCHKEY"));
                cnt++;
            }
        }
        assertEquals(NUM_ROWS, cnt);
    }

    private TikaConfig getConfig() throws Exception {
        String config = "<?xml version=\"1.0\" encoding=\"UTF-8\" ?><properties>\n" +
                "    <fetchIterators>\n" +
                "        <fetchIterator " +
                "       class=\"org.apache.tika.pipes.fetchiterator.jdbc.JDBCFetchIterator\">\n" +
                "            <params>\n" +
                "                <param name=\"fetcherName\" type=\"string\">s3f</param>\n" +
                "                <param name=\"emitterName\" type=\"string\">s3e</param>\n" +
                "                <param name=\"queueSize\" type=\"int\">57</param>\n" +
                "                <param name=\"fetchKeyColumn\" " +
                "                     type=\"string\">my_fetchkey</param>\n" +
                "                <param name=\"emitKeyColumn\" " +
                "                    type=\"string\">my_fetchkey</param>\n" +
                "                <param name=\"select\" type=\"string\">" +
                "select id as my_id, project as my_project, fetchKey as my_fetchKey " +
                "from fetchkeys</param>\n" +
                "                <param name=\"connection\" " +
                "                type=\"string\">jdbc:h2:file:" + DB_DIR.toAbsolutePath() + "/" +
                    db + "</param>\n" +
                "            </params>\n" +
                "        </fetchIterator>\n" +
                "    </fetchIterators>\n" +
                "</properties>";
        return new TikaConfig(new ByteArrayInputStream(config.getBytes(StandardCharsets.UTF_8)));
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
                if (t == FetchIterator.COMPLETED_SEMAPHORE) {
                    return pairs.size();
                }
                pairs.add(t);
            }
        }
    }
}
