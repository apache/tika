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

package org.apache.tika.eval;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.tika.eval.db.Cols;
import org.apache.tika.eval.db.H2Util;
import org.apache.tika.eval.db.TableInfo;
import org.apache.tika.eval.io.ExtractReaderException;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
@Ignore
public class ProfilerBatchTest {

    public final static String COMPARER_PROCESS_CLASS = "org.apache.tika.batch.fs.FSBatchProcessCLI";

    private static Path dbDir;
    private static Connection conn;

    private final static String profileTable = ExtractProfiler.PROFILE_TABLE.getName();
    private final static String exTable = ExtractProfiler.EXCEPTION_TABLE.getName();
    private final static String fpCol = Cols.FILE_PATH.name();

    @BeforeClass
    public static void setUp() throws Exception {

        Path inputRoot = Paths.get(new ComparerBatchTest().getClass().getResource("/test-dirs/extractsA").toURI());
        dbDir = Files.createTempDirectory(inputRoot, "tika-test-db-dir-");
        Map<String, String> args = new HashMap<>();
        Path db = dbDir.resolve("profiler_test");
        args.put("-db", db.toString());

        //for debugging, you can use this to select only one file pair to load
        //args.put("-includeFilePat", "file8.*");

       /* BatchProcessTestExecutor ex = new BatchProcessTestExecutor(COMPARER_PROCESS_CLASS, args,
                "/single-file-profiler-crawl-input-config.xml");
        StreamStrings streamStrings = ex.execute();
        System.out.println(streamStrings.getErrString());
        System.out.println(streamStrings.getOutString());*/
        H2Util dbUtil = new H2Util(db);
        conn = dbUtil.getConnection();
    }
    @AfterClass
    public static void tearDown() throws IOException {

        try{
            conn.close();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }


        DirectoryStream<Path> dStream = Files.newDirectoryStream(dbDir);
        Iterator<Path> it = dStream.iterator();
        while (it.hasNext()) {
            Path p = it.next();
            Files.delete(p);
        }
        dStream.close();
        Files.delete(dbDir);
    }

    @Test
    public void testSimpleDBWriteAndRead() throws Exception {

        Statement st = null;
        List<String> fNameList = new ArrayList<>();
        try {
            String sql = "select * from "+ ExtractProfiler.CONTAINER_TABLE.getName();
            st = conn.createStatement();
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
        debugTable(ExtractProfiler.CONTAINER_TABLE);
        debugTable(ExtractProfiler.PROFILE_TABLE);
        debugTable(ExtractProfiler.CONTENTS_TABLE);
        debugTable(ExtractProfiler.EXCEPTION_TABLE);
        debugTable(ExtractProfiler.EXTRACT_EXCEPTION_TABLE);
        assertEquals(10, fNameList.size());
        assertTrue("file1.pdf", fNameList.contains("file1.pdf"));
        assertTrue("file2_attachANotB.doc", fNameList.contains("file2_attachANotB.doc"));
        assertTrue("file3_attachBNotA.doc", fNameList.contains("file3_attachBNotA.doc"));
        assertTrue("file4_emptyB.pdf", fNameList.contains("file4_emptyB.pdf"));
        assertTrue("file7_badJson.pdf", fNameList.contains("file7_badJson.pdf"));
    }

    @Test
    public void testExtractErrors() throws Exception {
        String sql = "select EXTRACT_EXCEPTION_ID from extract_exceptions e" +
                " join containers c on c.container_id = e.container_id "+
                " where c.file_path='file9_noextract.txt'";

        assertEquals("missing extract: file9_noextract.txt", "0",
                getSingleResult(sql));
        debugTable(ExtractProfiler.CONTAINER_TABLE);
        debugTable(ExtractProfiler.PROFILE_TABLE);
        debugTable(ExtractProfiler.CONTENTS_TABLE);
        debugTable(ExtractProfiler.EXCEPTION_TABLE);
        debugTable(ExtractProfiler.EXTRACT_EXCEPTION_TABLE);

        sql = "select EXTRACT_EXCEPTION_ID from errors e" +
                " join containers c on c.container_id = e.container_id "+
                " where c.file_path='file5_emptyA.pdf'";
        assertEquals("empty extract: file5_emptyA.pdf", "1",
                getSingleResult(sql));

        sql = "select EXTRACT_EXCEPTION_ID from errors e" +
                " join containers c on c.container_id = e.container_id "+
                " where c.file_path='file7_badJson.pdf'";
        assertEquals("extract error:file7_badJson.pdf", "2",
                getSingleResult(sql));

    }

    @Test
    public void testParseErrors() throws Exception {
        debugTable(ExtractProfiler.EXTRACT_EXCEPTION_TABLE);
        String sql = "select file_path from errors where container_id is null";
        assertEquals("file10_permahang.txt",
                getSingleResult(sql));

        sql = "select extract_error_id from extract_exceptions " +
                "where file_path='file11_oom.txt'";
        assertEquals(Integer.toString(
                        ExtractReaderException.TYPE.ZERO_BYTE_EXTRACT_FILE.ordinal()),
                getSingleResult(sql));

        sql = "select parse_error_id from extract_exceptions where file_path='file11_oom.txt'";
        assertEquals(Integer.toString(AbstractProfiler.
                        PARSE_ERROR_TYPE.
                        OOM.ordinal()),
                getSingleResult(sql));

    }

    @Test
    public void testParseExceptions() throws Exception {
        debugTable(ExtractProfiler.EXCEPTION_TABLE);
    }

    private String getSingleResult(String sql) throws Exception {
        Statement st = null;
        st = conn.createStatement();
        ResultSet rs = st.executeQuery(sql);
        int hits = 0;
        String val = "";
        while (rs.next()) {
            assertEquals("must have only one column in result",
                    1, rs.getMetaData().getColumnCount());
            val = rs.getString(1);
            hits++;
        }
        assertEquals("must have only one hit", 1, hits);
        return val;
    }

    //TODO: lots more testing!

    public void debugTable(TableInfo table) throws Exception {
        Statement st = null;
        try {
            String sql = "select * from "+table.getName();
            st = conn.createStatement();
            ResultSet rs = st.executeQuery(sql);
            int colCount = rs.getMetaData().getColumnCount();
            System.out.println("TABLE: "+table.getName());
            for (int i = 1; i <= colCount; i++) {
                if (i > 1) {
                    System.out.print(" | ");
                }
                System.out.print(rs.getMetaData().getColumnName(i));
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
