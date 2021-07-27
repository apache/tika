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
package org.apache.tika.parser.sqlite3;


import java.io.InputStream;

import org.junit.jupiter.api.Test;
import org.xml.sax.ContentHandler;

import org.apache.tika.TikaTest;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;

public class SQLite3ParserTest extends TikaTest {
    private final static String TEST_FILE_NAME = "testSqlite3b.db";
    private final static String TEST_FILE1 = "/test-documents/" + TEST_FILE_NAME;

    //make sure that table cells and rows are properly marked to
    //yield \t and \n at the appropriate places
    @Test
    public void testSpacesInBodyContentHandler() throws Exception {
        Metadata metadata = new Metadata();
        metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, TEST_FILE_NAME);
        ContentHandler handler = new BodyContentHandler(-1);
        ParseContext ctx = new ParseContext();
        try (InputStream stream = getResourceAsStream(TEST_FILE1)) {
            TikaTest.AUTO_DETECT_PARSER.parse(stream, handler, metadata, ctx);
        }
        String s = handler.toString();
        TikaTest.assertContains("0\t2.3\t2.4\tlorem", s);
        TikaTest.assertContains("tempor\n", s);
    }

    @Test
    public void testNulls() throws Exception {
        String xml = getXML(TEST_FILE_NAME).xml.replaceAll("\\s+", "");
        //everything except for the first key column should be empty
        TikaTest.assertContains("<tr><td>2</td><td/><td/><td/><td/><td/><td/><td/><td/><td/></tr>",
                xml);
    }

    //code used for creating the test file
/*
    private Connection getConnection(String dbFileName) throws Exception {
        File testDirectory = new File(this.getClass().getResource("/test-documents").toURI());
        System.out.println("Writing to: " + testDirectory.getAbsolutePath());
        File testDB = new File(testDirectory, dbFileName);
        Connection c = null;
        try {
            Class.forName("org.sqlite.JDBC");
            c = DriverManager.getConnection("jdbc:sqlite:" + testDB.getAbsolutePath());
        } catch ( Exception e ) {
            System.err.println( e.getClass().getName() + ": " + e.getMessage() );
            System.exit(0);
        }
        return c;
    }

    @Test
    public void testCreateDB() throws Exception {
        Connection c = getConnection("testSqlite3d.db");
        Statement st = c.createStatement();
        String sql = "DROP TABLE if exists my_table1";
        st.execute(sql);
        sql = "CREATE TABLE my_table1 (" +
                "PK INT PRIMARY KEY, "+
                "INT_COL INTEGER, "+
                "FLOAT_COL FLOAT, " +
                "DOUBLE_COL DOUBLE, " +
                "CHAR_COL CHAR(30), "+
                "VARCHAR_COL VARCHAR(30), "+
                "BOOLEAN_COL BOOLEAN,"+
                "DATE_COL DATE,"+
                "TIME_STAMP_COL TIMESTAMP,"+
                "CLOB_COL CLOB, "+
                "BYTES_COL BYTES" +
        ")";
        st.execute(sql);
        sql = "insert into my_table1 (PK, INT_COL, FLOAT_COL, DOUBLE_COL, CHAR_COL, " +
                "VARCHAR_COL, BOOLEAN_COL, DATE_COL, TIME_STAMP_COL, CLOB_COL, BYTES_COL) " +
                "values (?,?,?,?,?,?,?,?,?,?,?)";
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        java.util.Date d = f.parse("2015-01-03 15:17:03");
        System.out.println(d.getTime());
        long d1Long = 1420229823000L;// 2015-01-02 15:17:03
        long d2Long = 1420316223000L;// 2015-01-03 15:17:03
        PreparedStatement ps = c.prepareStatement(sql);
        ps.setInt(1, 0);
        ps.setInt(2, 10);
        ps.setFloat(3, 2.3f);
        ps.setDouble(4, 2.4d);
        ps.setString(5, "lorem");
        ps.setString(6, "普林斯顿大学");
        ps.setBoolean(7, true);
        ps.setString(8, "2015-01-02");
        ps.setString(9, "2015-01-03 15:17:03");
//        ps.setClob(10, new StringReader(sql));
        ps.setBytes(10, getByteArray(this.getClass()
        .getResourceAsStream("/test-documents/testWORD_1img.doc")));//contains "quick brown fox"
        ps.executeUpdate();
        ps.clearParameters();

        ps.setInt(1, 1);
        ps.setInt(2, 20);
        ps.setFloat(3, 4.6f);
        ps.setDouble(4, 4.8d);
        ps.setString(5, "dolor");
        ps.setString(6, "sit");
        ps.setBoolean(7, false);
        ps.setString(8, "2015-01-04");
        ps.setString(9, "2015-01-03 15:17:03");
        //ps.setClob(9, new StringReader("consectetur adipiscing elit"));
        ps.setBytes(10, getByteArray(this.getClass()
        .getResourceAsStream("/test-documents/testWORD_1img.docx")));//contains "The end!"

        ps.executeUpdate();
        //now add a fully null row
        ps.clearParameters();
        ps.setInt(1, 2);
        ps.setNull(2, Types.INTEGER);
        ps.setNull(3, Types.FLOAT);
        ps.setNull(4, Types.DOUBLE);
        ps.setNull(5, Types.CHAR);
        ps.setNull(6, Types.VARCHAR);
        ps.setNull(7, Types.BOOLEAN);
        ps.setNull(8, Types.DATE);
        ps.setNull(9, Types.TIMESTAMP);
        ps.setNull(10, Types.BLOB);
        ps.executeUpdate();

        //build table2
        sql = "DROP TABLE if exists my_table2";
        st.execute(sql);

        sql = "CREATE TABLE my_table2 (" +
                "INT_COL2 INT PRIMARY KEY, "+
                "VARCHAR_COL2 VARCHAR(64))";
        st.execute(sql);
        sql = "INSERT INTO my_table2 values(0,'sed, do eiusmod tempor')";
        st.execute(sql);
        sql = "INSERT INTO my_table2 values(1,'incididunt \nut labore')";
        st.execute(sql);

        c.close();
    }

    private byte[] getByteArray(InputStream is) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buff = new byte[1024];
        for (int bytesRead; (bytesRead = is.read(buff)) != -1;) {
            bos.write(buff, 0, bytesRead);
        }
        return bos.toByteArray();
    }

*/

}
