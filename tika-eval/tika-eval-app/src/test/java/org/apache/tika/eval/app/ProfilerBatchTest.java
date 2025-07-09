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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import org.apache.tika.eval.app.db.Cols;
import org.apache.tika.eval.app.db.H2Util;
import org.apache.tika.eval.app.db.TableInfo;

public class ProfilerBatchTest {

    private static Connection CONN;
    private static Path DB_DIR;
    private static Path DB;

    @BeforeAll
    public static void setUp() throws Exception {
        DB_DIR = Files.createTempDirectory("profiler-test");
        Path extractsRoot = Paths.get(ProfilerBatchTest.class
                .getResource("/test-dirs/extractsA")
                .toURI());

        Path inputRoot = Paths.get(ProfilerBatchTest.class
                .getResource("/test-dirs/raw_input")
                .toURI());

        DB = DB_DIR.resolve("mydb");
        String[] args = new String[]{
            "-i", inputRoot.toAbsolutePath().toString(),
            "-e", extractsRoot.toAbsolutePath().toString(),
                "-d", "jdbc:h2:file:" + DB.toAbsolutePath().toString()
        };

        ExtractProfileRunner.main(args);
    }

    @AfterEach
    public void tearDown() throws IOException {
        try {
            CONN.close();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        FileUtils.deleteDirectory(DB_DIR.toFile());

    }

    @BeforeEach
    public void setUpEach() throws SQLException {
        H2Util dbUtil = new H2Util(DB);
        CONN = dbUtil.getConnection();
    }

    @AfterEach
    public void tearDownEach() throws SQLException {
        CONN.close();
    }

    @Test
    public void testSimpleDBWriteAndRead() throws Exception {
        Statement st = null;
        List<String> fNameList = new ArrayList<>();
        try {
            String sql = "select * from " + ExtractProfiler.CONTAINER_TABLE.getName();
            st = CONN.createStatement();
            ResultSet rs = st.executeQuery(sql);
            while (rs.next()) {
                String fileName = rs.getString(Cols.FILE_PATH.name());
                fNameList.add(fileName);
            }
        } finally {
            if (st != null) {
                st.close();
            }
        }
        /*
        debugTable(ExtractProfiler.CONTAINER_TABLE);
        debugTable(ExtractProfiler.PROFILE_TABLE);
        debugTable(ExtractProfiler.CONTENTS_TABLE);
        debugTable(ExtractProfiler.EXCEPTION_TABLE);
        debugTable(ExtractProfiler.EXTRACT_EXCEPTION_TABLE);*/
        assertEquals(17, fNameList.size());
        assertTrue(fNameList.contains("file1.pdf"), "file1.pdf");
        assertTrue(fNameList.contains("file2_attachANotB.doc"), "file2_attachANotB.doc");
        assertTrue(fNameList.contains("file3_attachBNotA.doc"), "file3_attachBNotA.doc");
        assertTrue(fNameList.contains("file4_emptyB.pdf"), "file4_emptyB.pdf");
        assertTrue(fNameList.contains("file7_badJson.pdf"), "file4_emptyB.pdf");
        assertTrue(fNameList.contains("file9_noextract.txt"), "file9_noextract.txt");
    }

    @Test
    public void testExtractErrors() throws Exception {
        String sql =
                "select EXTRACT_EXCEPTION_ID from extract_exceptions e" + " join containers c on c.container_id = e.container_id " + " where c.file_path='file9_noextract.txt'";

        /*debugTable(ExtractProfiler.CONTAINER_TABLE);
        debugTable(ExtractProfiler.PROFILE_TABLE);
        debugTable(ExtractProfiler.CONTENTS_TABLE);
        debugTable(ExtractProfiler.EXCEPTION_TABLE);
        debugTable(ExtractProfiler.EXTRACT_EXCEPTION_TABLE);*/
        assertEquals("0", getSingleResult(sql), "missing extract: file9_noextract.txt");

        sql = "select EXTRACT_EXCEPTION_ID from extract_exceptions e" + " join containers c on c.container_id = e.container_id " + " where c.file_path='file5_emptyA.pdf'";
        assertEquals("1", getSingleResult(sql), "empty extract: file5_emptyA.pdf");

        sql = "select EXTRACT_EXCEPTION_ID from extract_exceptions e" + " join containers c on c.container_id = e.container_id " + " where c.file_path='file7_badJson.pdf'";
        assertEquals("2", getSingleResult(sql), "extract error:file7_badJson.pdf");
    }

    @Test
    @Disabled("create actual unit test")
    public void testParseExceptions() throws Exception {
        debugTable(ExtractProfiler.EXCEPTION_TABLE);
    }

    private String getSingleResult(String sql) throws Exception {
        Statement st = null;
        st = CONN.createStatement();
        ResultSet rs = st.executeQuery(sql);
        int hits = 0;
        String val = "";
        while (rs.next()) {
            assertEquals(1, rs
                    .getMetaData()
                    .getColumnCount(), "must have only one column in result");
            val = rs.getString(1);
            hits++;
        }
        assertEquals(1, hits, "must have only one hit");
        return val;
    }

    //TODO: lots more testing!

    public void debugTable(TableInfo table) throws Exception {
        Statement st = null;
        try {
            String sql = "select * from " + table.getName();
            st = CONN.createStatement();
            ResultSet rs = st.executeQuery(sql);
            int colCount = rs
                    .getMetaData()
                    .getColumnCount();
            System.out.println("TABLE: " + table.getName());
            for (int i = 1; i <= colCount; i++) {
                if (i > 1) {
                    System.out.print(" | ");
                }
                System.out.print(rs
                        .getMetaData()
                        .getColumnName(i));
            }
            System.out.println("");
            int rowCount = 0;
            while (rs.next()) {
                for (int i = 1; i <= colCount; i++) {
                    if (i > 1) {
                        System.out.print(" | ");
                    }
                    System.out.print(rs.getString(i));
                    rowCount++;
                }
                System.out.println("");
            }
            if (rowCount == 0) {
                System.out.println(table.getName() + " was empty");
            }
        } finally {
            if (st != null) {
                st.close();
            }
        }

    }
}
