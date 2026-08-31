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
package org.apache.tika.eval.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.apache.tika.eval.app.db.H2Util;

/** Profile crawling the extracts dir itself (no -i), with and without a .run-info to discover. */
public class ProfilerDiscoveryTest {

    private static Path testDirs() throws Exception {
        return Paths.get(ProfilerDiscoveryTest.class.getResource("/test-dirs").toURI());
    }

    @Test
    public void testDiscoversRunInfoAndSkipsItInCrawl(@TempDir Path tmp) throws Exception {
        Path db = tmp.resolve("db");
        ExtractProfileRunner.main(new String[]{"-e", testDirs().resolve("extractsB").toString(), "-d", db.toAbsolutePath().toString()});
        try (Connection c = new H2Util(db).getConnection(); Statement st = c.createStatement()) {
            assertEquals("run-b1", one(st, "select run_value from run_info where run_key='batch.run.id'"));
            assertEquals("EMIT_SUCCESS", one(st, "select pipes_status from containers where file_path='file1.pdf'"));
            assertEquals("0", one(st, "select count(1) from containers where file_path like '.run-info%'"), ".run-info skipped by the crawl");
            assertEquals("14", one(st, "select run_value from run_info where run_key='extracts.count'"));
            // file9 crashed, so it has no extract and this crawl never sees it: only file1 joins
            assertEquals("1", one(st, "select run_value from run_info where run_key='pipes_report.joined'"));
        }
    }

    @Test
    public void testNoLedgerNoRunInfo(@TempDir Path tmp) throws Exception {
        Path db = tmp.resolve("db");
        ExtractProfileRunner.main(new String[]{"-e", testDirs().resolve("extractsA").toString(), "-d", db.toAbsolutePath().toString()});
        try (Connection c = new H2Util(db).getConnection(); Statement st = c.createStatement()) {
            assertEquals("0", one(st, "select count(1) from run_info where run_key like 'batch.%' or run_key like 'pipes_report.%'"));
            assertEquals("0", one(st, "select count(1) from containers where pipes_status is not null"));
            assertTrue(Integer.parseInt(one(st, "select count(1) from containers")) > 10);
            assertFalse(one(st, "select run_value from run_info where run_key='eval.end'").isBlank());
        }
    }

    private static String one(Statement st, String sql) throws Exception {
        try (ResultSet rs = st.executeQuery(sql)) {
            assertTrue(rs.next(), "no row for: " + sql);
            return rs.getString(1);
        }
    }
}
