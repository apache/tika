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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;

import org.apache.tika.eval.app.db.H2Util;

/** A 3.x baseline has no jsonl ledger; B's is discovered from extractsB/.run-info without flags. */
public class ComparerOneSidedLedgerTest {

    @Test
    public void testOnlyB() throws Exception {
        Path dir = Files.createTempDirectory("comparer-one-sided");
        try {
            Path db = dir.resolve("db");
            Path reports = dir.resolve("reports");
            Path testDirs = Paths.get(getClass().getResource("/test-dirs").toURI());
            ExtractComparerRunner.main(new String[]{
                    "-i", testDirs.resolve("raw_input").toString(),
                    "-a", testDirs.resolve("extractsA").toString(),
                    "-b", testDirs.resolve("extractsB").toString(),
                    "-d", db.toAbsolutePath().toString(),
                    "-r", "-rd", reports.toString()
            });
            try (Connection c = new H2Util(db).getConnection(); Statement st = c.createStatement()) {
                try (ResultSet rs = st.executeQuery("select pipes_status_a, pipes_status_b from containers where file_path='file9_noextract.txt'")) {
                    assertTrue(rs.next());
                    assertNull(rs.getString(1));
                    assertEquals("TIMEOUT", rs.getString(2));
                }
                try (ResultSet rs = st.executeQuery("select run_value from run_info_b where run_key='batch.run.id'")) {
                    assertTrue(rs.next());
                    assertEquals("run-b1", rs.getString(1));
                }
                try (ResultSet rs = st.executeQuery("select run_value from run_info_b where run_key='extracts.count'")) {
                    assertTrue(rs.next());
                    assertEquals("14", rs.getString(1), ".run-info excluded from the fingerprint walk");
                }
                try (ResultSet rs = st.executeQuery("select count(1) from containers where file_path like '.run-info%'")) {
                    assertTrue(rs.next());
                    assertEquals(0, rs.getInt(1), ".run-info skipped by the crawl");
                }
                try (ResultSet rs = st.executeQuery("select count(1) from run_info_a")) {
                    assertTrue(rs.next());
                    assertEquals(3, rs.getInt(1), "extracts.* only");
                }
            }
            try (Connection c = new H2Util(db).getConnection(); Statement st = c.createStatement()) {
                try (ResultSet rs = st.executeQuery("select p.classification from pipes_class_a p join containers c on c.container_id = p.container_id " +
                        "where c.file_path='file9_noextract.txt'")) {
                    assertTrue(rs.next());
                    assertEquals("NO_PIPES_REPORT_SUPPLIED", rs.getString(1));
                }
                try (ResultSet rs = st.executeQuery("select p.classification, count(1) from pipes_class_b p join containers c on c.container_id = p.container_id " +
                        "where c.file_path in ('file9_noextract.txt', 'file10_permahang.txt', 'file1.pdf') group by p.classification order by 1")) {
                    assertTrue(rs.next());
                    assertEquals("CRASH", rs.getString(1));
                    assertEquals(1, rs.getInt(2));
                    assertTrue(rs.next());
                    assertEquals("EMIT_SUCCESS", rs.getString(1));
                    assertTrue(rs.next());
                    assertEquals("NO_PIPES_RECORD", rs.getString(1));
                }
            }
            String summary = Files.readString(reports.resolve("summary.md"), StandardCharsets.UTF_8);
            int section = summary.indexOf("## Extract File Issues by Pipes Status");
            int a = summary.indexOf("### Extract A", section);
            int b = summary.indexOf("### Extract B", section);
            assertTrue(summary.substring(a, b).contains("| NO_PIPES_REPORT_SUPPLIED | NO_EXTRACT_FILE |"), summary);
            assertTrue(summary.substring(b).contains("| CRASH | NO_EXTRACT_FILE |"), summary);
        } finally {
            FileUtils.deleteDirectory(dir.toFile());
        }
    }
}
