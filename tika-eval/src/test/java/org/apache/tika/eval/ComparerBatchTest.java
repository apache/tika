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

import java.io.File;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.apache.tika.batch.fs.FSBatchTestBase;
import org.apache.tika.eval.db.Cols;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

@Ignore("move these tests to TikaEvalCLITest")
public class ComparerBatchTest extends FSBatchTestBase {

    public final static String COMPARER_PROCESS_CLASS = "org.apache.tika.batch.fs.FSBatchProcessCLI";

    private static Path dbDir;
    private static Connection conn;

    private final static String compJoinCont = "";
    /*ExtractComparer.COMPARISONS_TABLE+" cmp " +
            "join "+ExtractComparer.CONTAINERS_TABLE + " cnt "+
            "on cmp."+AbstractProfiler.CONTAINER_HEADERS.CONTAINER_ID+
            " = cnt."+AbstractProfiler.CONTAINER_HEADERS.CONTAINER_ID;*/

    @BeforeClass
    public static void setUp() throws Exception {

        File inputRoot = new File(ComparerBatchTest.class.getResource("/test-dirs").toURI());
        dbDir = Files.createTempDirectory(inputRoot.toPath(), "tika-test-db-dir-");
        Map<String, String> args = new HashMap<>();
        Path db = FileSystems.getDefault().getPath(dbDir.toString(), "comparisons_test");
        args.put("-db", db.toString());

        //for debugging, you can use this to select only one file pair to load
        //args.put("-includeFilePat", "file8.*");
/*
        BatchProcessTestExecutor ex = new BatchProcessTestExecutor(COMPARER_PROCESS_CLASS, args,
                "/tika-batch-comparison-eval-config.xml");
        StreamStrings streamStrings = ex.execute();
        System.out.println(streamStrings.getErrString());
        System.out.println(streamStrings.getOutString());
        H2Util dbUtil = new H2Util(db);
        conn = dbUtil.getConnection();*/
    }

    @AfterClass
    public static void tearDown() throws Exception {

        conn.close();

        FileUtils.deleteDirectory(dbDir.toFile());
    }


    @Test
    public void testSimpleDBWriteAndRead() throws Exception {
        Set<String> set = new HashSet<>();
        //filenames
        List<String> list = getColStrings(Cols.FILE_NAME.name(),
                ExtractComparer.PROFILES_A.getName(), "");
        assertEquals(7, list.size());
        assertTrue(list.contains("file1.pdf"));

        //container ids in comparisons table
        list = getColStrings(Cols.CONTAINER_ID.name(),
                ExtractComparer.COMPARISON_CONTAINERS.getName(),"");
        assertEquals(10, list.size());
        set.clear(); set.addAll(list);
        assertEquals(10, set.size());
/*
        //ids in comparisons table
        list = getColStrings(AbstractProfiler.HEADERS.ID.name(),
                compTable,"");
        assertEquals(9, list.size());
        set.clear(); set.addAll(list);
        assertEquals(9, set.size());*/
    }



    /*
        @Test
        public void testFile1PDFRow() throws Exception {
            String where = fp+"='file1.pdf'";
            Map<String, String> data = getRow(compJoinCont, where);
            String result = data.get(ExtractComparer.COMPARISON_HEADERS.TOP_10_UNIQUE_TOKEN_DIFFS + "_A");
            assertTrue(result.startsWith("over: 1"));

            result = data.get(ExtractComparer.COMPARISON_HEADERS.TOP_10_UNIQUE_TOKEN_DIFFS + "_B");
            assertTrue(result.startsWith("aardvark: 3 | bear: 2"));


            assertEquals("aardvark: 3 | bear: 2",
                    data.get(ExtractComparer.COMPARISON_HEADERS.TOP_10_MORE_IN_B.toString()));
            assertEquals("fox: 2 | lazy: 1 | over: 1",
                    data.get(ExtractComparer.COMPARISON_HEADERS.TOP_10_MORE_IN_A.toString()));
            assertEquals("12", data.get(ExtractComparer.HEADERS.NUM_TOKENS+"_A"));
            assertEquals("13", data.get(ExtractComparer.HEADERS.NUM_TOKENS+"_B"));
            assertEquals("8", data.get(ExtractComparer.HEADERS.NUM_UNIQUE_TOKENS+"_A"));
            assertEquals("9", data.get(ExtractComparer.HEADERS.NUM_UNIQUE_TOKENS+"_B"));

            assertEquals(ExtractComparer.COMPARISON_HEADERS.OVERLAP.name(),
                    0.64f, Float.parseFloat(data.get("OVERLAP")), 0.0001f);

            assertEquals(ExtractComparer.COMPARISON_HEADERS.DICE_COEFFICIENT.name(),
                    0.8235294f, Float.parseFloat(data.get("DICE_COEFFICIENT")), 0.0001f);

            assertEquals(ExtractComparer.HEADERS.TOKEN_LENGTH_MEAN+"_A", 3.83333d,
                    Double.parseDouble(
                            data.get(ExtractComparer.HEADERS.TOKEN_LENGTH_MEAN+"_A")), 0.0001d);

            assertEquals(ExtractComparer.HEADERS.TOKEN_LENGTH_MEAN+"_B", 4.923d,
                    Double.parseDouble(
                            data.get(ExtractComparer.HEADERS.TOKEN_LENGTH_MEAN+"_B")), 0.0001d);

            assertEquals(ExtractComparer.HEADERS.TOKEN_LENGTH_STD_DEV+"_A", 1.0298d,
                    Double.parseDouble(
                            data.get(ExtractComparer.HEADERS.TOKEN_LENGTH_STD_DEV+"_A")), 0.0001d);

            assertEquals(ExtractComparer.HEADERS.TOKEN_LENGTH_STD_DEV+"_B", 1.9774d,
                    Double.parseDouble(data.get(ExtractComparer.HEADERS.TOKEN_LENGTH_STD_DEV+"_B")), 0.0001d);

            assertEquals(ExtractComparer.HEADERS.TOKEN_LENGTH_SUM+"_A", 46,
                    Integer.parseInt(
                            data.get(ExtractComparer.HEADERS.TOKEN_LENGTH_SUM+"_A")));

            assertEquals(ExtractComparer.HEADERS.TOKEN_LENGTH_SUM+"_B", 64,
                    Integer.parseInt(data.get(ExtractComparer.HEADERS.TOKEN_LENGTH_SUM+"_B")));

            assertEquals("TOKEN_ENTROPY_RATE_A", 0.237949,
                    Double.parseDouble(data.get("TOKEN_ENTROPY_RATE_A")), 0.0001d);

            assertEquals("TOKEN_ENTROPY_RATE_B", 0.232845,
                    Double.parseDouble(data.get("TOKEN_ENTROPY_RATE_B")), 0.0001d);

        }


        @Test
        public void testEmpty() throws Exception {
            String where = fp+"='file4_emptyB.pdf'";
            Map<String, String> data = getRow(contTable, where);
            assertNull(data.get(AbstractProfiler.CONTAINER_HEADERS.JSON_EX +
                    ExtractComparer.aExtension));
            assertTrue(data.get(AbstractProfiler.CONTAINER_HEADERS.JSON_EX +
                    ExtractComparer.bExtension).equals(AbstractProfiler.JSON_PARSE_EXCEPTION));

            where = fp+"='file5_emptyA.pdf'";
            data = getRow(contTable, where);
            assertNull(data.get(AbstractProfiler.CONTAINER_HEADERS.JSON_EX +
                    ExtractComparer.bExtension));
            assertTrue(data.get(AbstractProfiler.CONTAINER_HEADERS.JSON_EX+
                    ExtractComparer.aExtension).equals(AbstractProfiler.JSON_PARSE_EXCEPTION));
        }

            @Test
            public void testMissingAttachment() throws Exception {
                String where = fp+"='file2_attachANotB.doc' and "+AbstractProfiler.HEADERS.EMBEDDED_FILE_PATH+
                        "='inner.txt'";
                Map<String, String> data = getRow(compJoinCont, where);
                assertContains("attachment: 1", data.get(ExtractComparer.COMPARISON_HEADERS.TOP_10_MORE_IN_A.name()));
                assertNotContained("fox", data.get(ExtractComparer.COMPARISON_HEADERS.TOP_10_MORE_IN_B.name()));
                assertNull(data.get(ExtractComparer.HEADERS.TOP_N_TOKENS +
                        ExtractComparer.bExtension));
                assertNotContained("fox", data.get(ExtractComparer.COMPARISON_HEADERS.TOP_10_UNIQUE_TOKEN_DIFFS +
                        ExtractComparer.bExtension));

                assertEquals("3", data.get("NUM_METADATA_VALUES_A"));
                assertNull(data.get("DIFF_NUM_ATTACHMENTS"));
                assertNull(data.get("NUM_METADATA_VALUES_B"));
                assertEquals("0", data.get("NUM_UNIQUE_TOKENS_B"));
                assertNull(data.get("TOKEN_ENTROPY_RATE_B"));
                assertNull(data.get("NUM_EN_STOPS_TOP_N_B"));

                where = fp+"='file3_attachBNotA.doc' and "+AbstractProfiler.HEADERS.EMBEDDED_FILE_PATH+
                        "='inner.txt'";
                data = getRow(compJoinCont, where);
                assertContains("attachment: 1", data.get(ExtractComparer.COMPARISON_HEADERS.TOP_10_MORE_IN_B.name()));
                assertNotContained("fox", data.get(ExtractComparer.COMPARISON_HEADERS.TOP_10_MORE_IN_A.name()));
                assertNull(data.get(ExtractComparer.HEADERS.TOP_N_TOKENS +
                        ExtractComparer.aExtension));
                assertNotContained("fox", data.get(ExtractComparer.COMPARISON_HEADERS.TOP_10_UNIQUE_TOKEN_DIFFS +
                        ExtractComparer.aExtension));

                assertEquals("3", data.get("NUM_METADATA_VALUES_B"));
                assertNull(data.get("DIFF_NUM_ATTACHMENTS"));
                assertNull(data.get("NUM_METADATA_VALUES_A"));
                assertEquals("0", data.get("NUM_UNIQUE_TOKENS_A"));
                assertNull(data.get("TOKEN_ENTROPY_RATE_A"));
                assertNull(data.get("NUM_EN_STOPS_TOP_N_A"));

            }

            @Test
            public void testBothBadJson() throws Exception {
                debugDumpAll(contTable);
                String where = fp+"='file7_badJson.pdf'";
                Map<String, String> data = getRow(contTable, where);
                assertEquals(AbstractProfiler.JSON_PARSE_EXCEPTION,
                        data.get(AbstractProfiler.CONTAINER_HEADERS.JSON_EX+ ExtractComparer.aExtension));
                assertEquals(AbstractProfiler.JSON_PARSE_EXCEPTION,
                        data.get(AbstractProfiler.CONTAINER_HEADERS.JSON_EX+ ExtractComparer.bExtension));
                assertEquals("file7_badJson.pdf",
                        data.get(AbstractProfiler.CONTAINER_HEADERS.FILE_PATH.name()));
                assertEquals("61", data.get("JSON_FILE_LENGTH_A"));
                assertEquals("0", data.get("JSON_FILE_LENGTH_B"));
                assertEquals("pdf", data.get(AbstractProfiler.CONTAINER_HEADERS.FILE_EXTENSION.name()));

            }

            @Test
            public void testAccessPermissionException() throws Exception {
                String sql = "select "+
                        AbstractProfiler.EXCEPTION_HEADERS.ACCESS_PERMISSION_EXCEPTION.name() +
                        " from " + AbstractProfiler.EXCEPTIONS_TABLE+"_A exA "+
                        " join " + ExtractComparer.COMPARISONS_TABLE + " cmp on cmp.ID=exA.ID "+
                        " join " + ExtractComparer.CONTAINERS_TABLE + " cont on cmp.CONTAINER_ID=cont.CONTAINER_ID "+
                        " where "+fp+"='file6_accessEx.pdf'";
                Statement st = conn.createStatement();
                ResultSet rs = st.executeQuery(sql);
                List<String> results = new ArrayList<String>();
                while (rs.next()) {
                    results.add(rs.getString(1));
                }
                assertEquals(1, results.size());
                assertEquals("TRUE", results.get(0));

                sql = "select "+
                        AbstractProfiler.EXCEPTION_HEADERS.ACCESS_PERMISSION_EXCEPTION.name() +
                        " from " + AbstractProfiler.EXCEPTIONS_TABLE+"_B exB "+
                        " join " + ExtractComparer.COMPARISONS_TABLE + " cmp on cmp.ID=exB.ID "+
                        " join " + ExtractComparer.CONTAINERS_TABLE + " cont on cmp.CONTAINER_ID=cont.CONTAINER_ID "+
                        " where "+fp+"='file6_accessEx.pdf'";
                st = conn.createStatement();
                rs = st.executeQuery(sql);
                results = new ArrayList<String>();
                while (rs.next()) {
                    results.add(rs.getString(1));
                }
                assertEquals(1, results.size());
                assertEquals("TRUE", results.get(0));

            }

            @Test
            public void testContainerException() throws Exception {
                String sql = "select * "+
                        " from " + AbstractProfiler.EXCEPTIONS_TABLE+"_A exA "+
                        " join " + ExtractComparer.COMPARISONS_TABLE + " cmp on cmp.ID=exA.ID "+
                        " join " + ExtractComparer.CONTAINERS_TABLE + " cont on cmp.CONTAINER_ID=cont.CONTAINER_ID "+
                        "where "+fp+"='file8_IOEx.pdf'";
                Statement st = conn.createStatement();
                ResultSet rs = st.executeQuery(sql);

                Map<String, String> data = new HashMap<String,String>();
                ResultSetMetaData rsM = rs.getMetaData();
                while (rs.next()) {
                    for (int i = 1; i <= rsM.getColumnCount(); i++)
                    data.put(rsM.getColumnName(i), rs.getString(i));
                }

                String sortStack = data.get(AbstractProfiler.EXCEPTION_HEADERS.SORT_STACK_TRACE.name());
                sortStack = sortStack.replaceAll("[\r\n]", "<N>");
                assertTrue(sortStack.startsWith("java.lang.RuntimeException<N>"));

                String fullStack = data.get(AbstractProfiler.EXCEPTION_HEADERS.ORIG_STACK_TRACE.name());
                assertTrue(
                        fullStack.startsWith("java.lang.RuntimeException: java.io.IOException: Value is not an integer"));
            }

        private void debugDumpAll(String table) throws Exception {
            Statement st = conn.createStatement();
            String sql = "select * from "+table;
            ResultSet rs = st.executeQuery(sql);
            ResultSetMetaData m = rs.getMetaData();
            for (int i = 1; i <= m.getColumnCount(); i++) {
                System.out.print(m.getColumnName(i) + ", ");
            }
            System.out.println("\n");
            while (rs.next()) {
                for (int i = 1; i <= m.getColumnCount(); i++) {
                    System.out.print(rs.getString(i)+", ");
                }
                System.out.println("\n");
            }
            st.close();

        }
        */
    private void debugShowColumns(String table) throws Exception {
        Statement st = conn.createStatement();
        String sql = "select * from "+table;
        ResultSet rs = st.executeQuery(sql);
        ResultSetMetaData m = rs.getMetaData();
        for (int i = 1; i <= m.getColumnCount(); i++) {
            System.out.println(i+" : "+m.getColumnName(i));
        }
        st.close();
    }

    //return the string value for one cell
    private String getString(String colName, String table, String where) throws Exception {
        List<String> results = getColStrings(colName, table, where);
        if (results.size() > 1) {
            throw new RuntimeException("more than one result");
        } else if (results.size() == 0) {
            throw new RuntimeException("no results");
        }

        return results.get(0);
    }


    private Map<String, String> getRow(String table, String where) throws Exception {
        String sql = getSql("*", table, where);
        Map<String, String> results = new HashMap<String, String>();
        Statement st = null;

        try {
            st = conn.createStatement();
            ResultSet rs = st.executeQuery(sql);
            ResultSetMetaData m = rs.getMetaData();
            int rows = 0;
            while (rs.next()) {
                if (rows > 0) {
                    throw new RuntimeException("returned more than one row!");
                }
                for (int i = 1; i <= m.getColumnCount(); i++) {
                    results.put(m.getColumnName(i), rs.getString(i));
                }
                rows++;
            }
        } finally {
            if (st != null) {
                st.close();
            }
        }
        return results;

    }

    //return the string representations of the column values for one column
    //as a list of strings
    private List<String> getColStrings(String colName) throws Exception {
        return getColStrings(colName, ExtractComparer.CONTENT_COMPARISONS.getName(), null);
    }

    private List<String> getColStrings(String colName, String table, String where) throws Exception {
        String sql = getSql(colName, table, where);
        List<String> results = new ArrayList<>();
        Statement st = null;
        try {
            st = conn.createStatement();
            System.out.println("SQL: "+sql);
            ResultSet rs = st.executeQuery(sql);
            while (rs.next()) {
                results.add(rs.getString(1));
            }
        } finally {
            if (st != null) {
                st.close();
            }
        }
        return results;
    }

    private String getSql(String colName, String table, String where) {
        StringBuilder sb = new StringBuilder();
        sb.append("select ").append(colName).append(" from ").append(table);
        if (where != null && ! where.equals("")) {
            sb.append(" where ").append(where);
        }
        return sb.toString();
    }

}
