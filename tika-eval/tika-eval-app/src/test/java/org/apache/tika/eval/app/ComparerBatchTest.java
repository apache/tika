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
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import org.apache.tika.eval.app.db.H2Util;
import org.apache.tika.eval.app.reports.ResultsReporter;

/** End-to-end Compare + Report with the pipes crash ledger and run-info joined in. */
public class ComparerBatchTest {

    private static Path DB_DIR;
    private static Path DB;
    private static Path REPORTS;

    @BeforeAll
    public static void setUp() throws Exception {
        DB_DIR = Files.createTempDirectory("comparer-batch-test");
        DB = DB_DIR.resolve("mydb");
        REPORTS = DB_DIR.resolve("reports");
        Path testDirs = Paths.get(ComparerBatchTest.class.getResource("/test-dirs").toURI());
        Path pr = testDirs.resolve("pipes-reports");
        ExtractComparerRunner.main(new String[]{
                "-i", testDirs.resolve("raw_input").toString(),
                "-a", testDirs.resolve("extractsA").toString(),
                "-b", testDirs.resolve("extractsB").toString(),
                "-d", DB.toAbsolutePath().toString(),
                "-pa", pr.resolve("crashes-run-a1.jsonl").toString(),
                "-pb", pr.resolve("crashes-run-b1.jsonl").toString(),
                "-ra", pr.resolve("run-info-run-a1.json").toString(),
                "-rb", pr.resolve("run-info-run-b1.json").toString(),
                "-r", "-rd", REPORTS.toString()
        });
    }

    @AfterAll
    public static void tearDown() throws Exception {
        FileUtils.deleteDirectory(DB_DIR.toFile());
    }

    @Test
    public void testPipesColumns() throws Exception {
        assertEquals("OOM", one("select pipes_status_a from containers where file_path='file9_noextract.txt'"));
        assertEquals("TIMEOUT", one("select pipes_status_b from containers where file_path='file9_noextract.txt'"));
        assertNull(one("select pipes_status_a from containers where file_path='file2_attachANotB.doc'"));
        assertEquals("UNSPECIFIED_CRASH", one("select pipes_status_a from containers where file_path='file12_es.txt'"));
        assertTrue(one("select pipes_message_a from containers where file_path='file12_es.txt'").contains("EOFException"));
    }

    @Test
    public void testClassification() throws Exception {
        assertEquals("CRASH", one("select p.classification from pipes_class_a p join containers c on c.container_id = p.container_id " +
                "where c.file_path='file9_noextract.txt'"));
        assertEquals("CRASH", one("select p.classification from pipes_class_b p join containers c on c.container_id = p.container_id " +
                "where c.file_path='file9_noextract.txt'"));
        // B's ledger has no line for file10 and B has no extract for it
        assertEquals("NO_PIPES_RECORD", one("select p.classification from pipes_class_b p join containers c on c.container_id = p.container_id " +
                "where c.file_path='file10_permahang.txt'"));
        assertEquals("CRASH", one("select p.classification from pipes_class_a p join containers c on c.container_id = p.container_id " +
                "where c.file_path='file10_permahang.txt'"));
        assertEquals("5", one("select run_value from run_info_a where run_key='pipes_report.joined'"));
        assertEquals("2", one("select run_value from run_info_b where run_key='pipes_report.joined'"), "sub\\file9 never joins");
    }

    /** A db written before the ledger columns/tables existed: Report must skip what it cannot run, not abort. */
    @Test
    public void testReportOnPreLedgerDb() throws Exception {
        Path old = DB_DIR.resolve("olddb");
        Files.copy(DB_DIR.resolve("mydb.mv.db"), DB_DIR.resolve("olddb.mv.db"));
        try (Connection c = new H2Util(old).getConnection(); Statement st = c.createStatement()) {
            for (String t : new String[]{"run_info", "run_info_a", "run_info_b", "pipes_class_a", "pipes_class_b"}) {
                st.execute("drop table " + t);
            }
            for (String col : new String[]{"pipes_status_a", "pipes_message_a", "pipes_status_b", "pipes_message_b"}) {
                st.execute("alter table containers drop column " + col);
            }
        }
        Path reports = DB_DIR.resolve("old-reports");
        ResultsReporter.main(new String[]{"-db", old.toAbsolutePath().toString(), "-rd", reports.toString()});
        assertTrue(Files.isRegularFile(reports.resolve("summary.md")));
        assertFalse(Files.exists(reports.resolve("exceptions/extract_exceptions_by_pipes_status_a.xlsx")));
        String summary = Files.readString(reports.resolve("summary.md"), StandardCharsets.UTF_8);
        assertFalse(summary.contains("## Run Info"), summary);
        assertTrue(summary.contains("## Overview"), summary);
    }

    @Test
    public void testRunInfoTables() throws Exception {
        assertEquals("run-a1", one("select run_value from run_info_a where run_key='batch.run.id'"));
        assertEquals("run-b1", one("select run_value from run_info_b where run_key='batch.run.id'"));
        assertEquals("16", one("select run_value from run_info_a where run_key='extracts.count'"));
        assertEquals("1", one("select count(1) from run_info where run_key='eval.end'"));
    }

    @Test
    public void testReportsAndSummary() throws Exception {
        assertTrue(Files.isRegularFile(REPORTS.resolve("exceptions/extract_exceptions_by_pipes_status_a.xlsx")));
        assertTrue(Files.isRegularFile(REPORTS.resolve("exceptions/crash_status_extract_present_b.xlsx")));
        String summary = Files.readString(REPORTS.resolve("summary.md"), StandardCharsets.UTF_8);
        assertTrue(summary.contains("## Run Info"), summary);
        assertTrue(summary.contains("| batch.run.id | run-a1 |"), summary);
        assertTrue(summary.contains("| CRASH | NO_EXTRACT_FILE |"), summary);
        // file12_es.txt has an extract in A but a crash status: success with status lost, not a failure
        assertTrue(summary.contains("| A | file12_es.txt | UNSPECIFIED_CRASH |"), summary);
    }

    private static String one(String sql) throws Exception {
        try (Connection c = new H2Util(DB).getConnection(); Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            assertTrue(rs.next(), "no row for: " + sql);
            return rs.getString(1);
        }
    }
}
