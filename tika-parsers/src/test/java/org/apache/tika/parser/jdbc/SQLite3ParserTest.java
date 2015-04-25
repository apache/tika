package org.apache.tika.parser.jdbc;

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

import static org.junit.Assert.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.tika.TikaTest;
import org.apache.tika.extractor.EmbeddedResourceHandler;
import org.apache.tika.extractor.ParserContainerExtractor;
import org.apache.tika.io.IOUtils;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Database;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.RecursiveParserWrapper;
import org.apache.tika.sax.BasicContentHandlerFactory;
import org.apache.tika.sax.BodyContentHandler;
import org.apache.tika.sax.ToXMLContentHandler;
import org.junit.Test;
import org.xml.sax.ContentHandler;

public class SQLite3ParserTest extends TikaTest {
    private final static String TEST_FILE_NAME = "testSqlite3b.db";
    private final static String TEST_FILE1 = "/test-documents/"+TEST_FILE_NAME;;

    @Test
    public void testBasic() throws Exception {
        Parser p = new AutoDetectParser();

        //test different types of input streams
        //actual inputstream, memory buffered bytearray and literal file
        InputStream[] streams = new InputStream[3];
        streams[0] = getResourceAsStream(TEST_FILE1);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        IOUtils.copy(getResourceAsStream(TEST_FILE1), bos);
        streams[1] = new ByteArrayInputStream(bos.toByteArray());
        streams[2] = TikaInputStream.get(getResourceAsFile(TEST_FILE1));
        int tests = 0;
        for (InputStream stream : streams) {
            Metadata metadata = new Metadata();
            metadata.set(Metadata.RESOURCE_NAME_KEY, TEST_FILE_NAME);
            //1) getXML closes the stream
            //2) getXML runs recursively on the contents, so the embedded docs should show up
            XMLResult result = getXML(stream, p, metadata);
            String x = result.xml;
            //first table name
            assertContains("<table name=\"my_table1\"><thead><tr>\t<th>INT_COL</th>", x);
            //non-ascii
            assertContains("<td>普林斯顿大学</td>", x);
            //boolean
            assertContains("<td>true</td>\t<td>2015-01-02</td>", x);
            //date test
            assertContains("2015-01-04", x);
            //timestamp test
            assertContains("2015-01-03 15:17:03", x);
            //first embedded doc's image tag
            assertContains("alt=\"image1.png\"", x);
            //second embedded doc's image tag
            assertContains("alt=\"A description...\"", x);
            //second table name
            assertContains("<table name=\"my_table2\"><thead><tr>\t<th>INT_COL2</th>", x);

            Metadata post = result.metadata;
            String[] tableNames = post.getValues(Database.TABLE_NAME);
            assertEquals(2, tableNames.length);
            assertEquals("my_table1", tableNames[0]);
            assertEquals("my_table2", tableNames[1]);
            tests++;
        }
        assertEquals(3, tests);
    }

    //make sure that table cells and rows are properly marked to
    //yield \t and \n at the appropriate places
    @Test
    public void testSpacesInBodyContentHandler()  throws Exception {
        Parser p = new AutoDetectParser();
        InputStream stream = null;
        Metadata metadata = new Metadata();
        metadata.set(Metadata.RESOURCE_NAME_KEY, TEST_FILE_NAME);
        ContentHandler handler = new BodyContentHandler(-1);
        ParseContext ctx = new ParseContext();
        ctx.set(Parser.class, p);
        try {
            stream = getResourceAsStream(TEST_FILE1);
            p.parse(stream, handler, metadata, ctx);
        } finally {
            stream.close();
        }
        String s = handler.toString();
        assertContains("0\t2.3\t2.4\tlorem", s);
        assertContains("tempor\n", s);
    }

    //test what happens if the user forgets to pass in a parser via context
    //to handle embedded documents
    @Test
    public void testNotAddingEmbeddedParserToParseContext() throws Exception {
        Parser p = new AutoDetectParser();

        InputStream is = getResourceAsStream(TEST_FILE1);
        Metadata metadata = new Metadata();
        metadata.set(Metadata.RESOURCE_NAME_KEY, TEST_FILE_NAME);
        ContentHandler handler = new ToXMLContentHandler();
        p.parse(is, handler, metadata, new ParseContext());
        String xml = handler.toString();
        //just includes headers for embedded documents
        assertContains("<table name=\"my_table1\"><thead><tr>", xml);
        assertContains("<td><span type=\"blob\" column_name=\"BYTES_COL\" row_number=\"0\"><div class=\"package-entry\"><h1>BYTES_COL_0.doc</h1>", xml);
        //but no other content
        assertNotContained("dog", xml);
        assertNotContained("alt=\"image1.png\"", xml);
        //second embedded doc's image tag
        assertNotContained("alt=\"A description...\"", xml);
    }

    @Test
    public void testRecursiveParserWrapper() throws Exception {
        Parser p = new AutoDetectParser();

        RecursiveParserWrapper wrapper =
                new RecursiveParserWrapper(p, new BasicContentHandlerFactory(
                        BasicContentHandlerFactory.HANDLER_TYPE.BODY, -1));
        InputStream is = getResourceAsStream(TEST_FILE1);
        Metadata metadata = new Metadata();
        metadata.set(Metadata.RESOURCE_NAME_KEY, TEST_FILE_NAME);
        wrapper.parse(is, new BodyContentHandler(-1), metadata, new ParseContext());
        List<Metadata> metadataList = wrapper.getMetadata();
        int i = 0;
        assertEquals(5, metadataList.size());
        //make sure the \t are inserted in a body handler

        String table = metadataList.get(0).get(RecursiveParserWrapper.TIKA_CONTENT);
        assertContains("0\t2.3\t2.4\tlorem", table);
        assertContains("普林斯顿大学", table);

        //make sure the \n is inserted
        String table2 = metadataList.get(0).get(RecursiveParserWrapper.TIKA_CONTENT);
        assertContains("do eiusmod tempor\n", table2);

        assertContains("The quick brown fox", metadataList.get(2).get(RecursiveParserWrapper.TIKA_CONTENT));
        assertContains("The quick brown fox", metadataList.get(4).get(RecursiveParserWrapper.TIKA_CONTENT));

        //confirm .doc was added to blob
        assertEquals("testSqlite3b.db/BYTES_COL_0.doc/image1.png", metadataList.get(1).get(RecursiveParserWrapper.EMBEDDED_RESOURCE_PATH));
    }

    @Test
    public void testParserContainerExtractor() throws Exception {
        //There should be 6 embedded documents:
        //2x tables -- UTF-8 csv representations of the tables
        //2x word files, one doc and one docx
        //2x png files, the same image embedded in each of the doc and docx

        ParserContainerExtractor ex = new ParserContainerExtractor();
        ByteCopyingHandler byteCopier = new ByteCopyingHandler();
        InputStream is = getResourceAsStream(TEST_FILE1);
        Metadata metadata = new Metadata();
        metadata.set(Metadata.RESOURCE_NAME_KEY, TEST_FILE_NAME);
        ex.extract(TikaInputStream.get(is), ex, byteCopier);

        assertEquals(4, byteCopier.bytes.size());
        String[] strings = new String[4];
        for (int i = 1; i < byteCopier.bytes.size(); i++) {
            byte[] byteArr = byteCopier.bytes.get(i);
            String s = new String(byteArr, 0, Math.min(byteArr.length,1000), "UTF-8");
            strings[i] = s;
        }
        byte[] oleBytes = new byte[]{
                (byte)-48,
                (byte)-49,
                (byte)17,
                (byte)-32,
                (byte)-95,
                (byte)-79,
                (byte)26,
                (byte)-31,
                (byte)0,
                (byte)0,
        };
        //test OLE
        for (int i = 0; i < 10; i++) {
            assertEquals(oleBytes[i], byteCopier.bytes.get(0)[i]);
        }
        assertContains("PNG", strings[1]);
        assertContains("PK", strings[2]);
        assertContains("PNG", strings[3]);
    }

    //This confirms that reading the stream twice is not
    //quadrupling the number of attachments.
    @Test
    public void testInputStreamReset() throws Exception {
        //There should be 8 embedded documents:
        //4x word files, two docs and two docxs
        //4x png files, the same image embedded in each of the doc and docx

        ParserContainerExtractor ex = new ParserContainerExtractor();
        InputStreamResettingHandler byteCopier = new InputStreamResettingHandler();
        InputStream is = getResourceAsStream(TEST_FILE1);
        Metadata metadata = new Metadata();
        metadata.set(Metadata.RESOURCE_NAME_KEY, TEST_FILE_NAME);
        ex.extract(TikaInputStream.get(is), ex, byteCopier);
        is.reset();
        assertEquals(8, byteCopier.bytes.size());
    }



    public static class InputStreamResettingHandler implements EmbeddedResourceHandler {

        public List<byte[]> bytes = new ArrayList<byte[]>();

        @Override
        public void handle(String filename, MediaType mediaType,
                           InputStream stream) {
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            if (! stream.markSupported()) {
                stream = TikaInputStream.get(stream);
            }
            stream.mark(1000000);
            try {
                IOUtils.copy(stream, os);
                bytes.add(os.toByteArray());
                stream.reset();
                //now try again
                os.reset();
                IOUtils.copy(stream, os);
                bytes.add(os.toByteArray());
                stream.reset();
            } catch (IOException e) {
                //swallow
            }
        }
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
        Connection c = getConnection("testSQLLite3b.db");
        Statement st = c.createStatement();
        String sql = "DROP TABLE if exists my_table1";
        st.execute(sql);
        sql = "CREATE TABLE my_table1 (" +
                "INT_COL INT PRIMARY KEY, "+
                "FLOAT_COL FLOAT, " +
                "DOUBLE_COL DOUBLE, " +
                "CHAR_COL CHAR(30), "+
                "VARCHAR_COL VARCHAR(30), "+
                "BOOLEAN_COL BOOLEAN,"+
                "DATE_COL DATE,"+
                "TIME_STAMP_COL TIMESTAMP,"+
                "BYTES_COL BYTES" +
        ")";
        st.execute(sql);
        sql = "insert into my_table1 (INT_COL, FLOAT_COL, DOUBLE_COL, CHAR_COL, " +
                "VARCHAR_COL, BOOLEAN_COL, DATE_COL, TIME_STAMP_COL, BYTES_COL) " +
                "values (?,?,?,?,?,?,?,?,?)";
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        java.util.Date d = f.parse("2015-01-03 15:17:03");
        System.out.println(d.getTime());
        long d1Long = 1420229823000L;// 2015-01-02 15:17:03
        long d2Long = 1420316223000L;// 2015-01-03 15:17:03
        PreparedStatement ps = c.prepareStatement(sql);
        ps.setInt(1, 0);
        ps.setFloat(2, 2.3f);
        ps.setDouble(3, 2.4d);
        ps.setString(4, "lorem");
        ps.setString(5, "普林斯顿大学");
        ps.setBoolean(6, true);
        ps.setString(7, "2015-01-02");
        ps.setString(8, "2015-01-03 15:17:03");
//        ps.setClob(9, new StringReader(clobString));
        ps.setBytes(9, getByteArray(this.getClass().getResourceAsStream("/test-documents/testWORD_1img.doc")));//contains "quick brown fox"
        ps.executeUpdate();
        ps.clearParameters();

        ps.setInt(1, 1);
        ps.setFloat(2, 4.6f);
        ps.setDouble(3, 4.8d);
        ps.setString(4, "dolor");
        ps.setString(5, "sit");
        ps.setBoolean(6, false);
        ps.setString(7, "2015-01-04");
        ps.setString(8, "2015-01-03 15:17:03");
        //ps.setClob(9, new StringReader("consectetur adipiscing elit"));
        ps.setBytes(9, getByteArray(this.getClass().getResourceAsStream("/test-documents/testWORD_1img.docx")));//contains "The end!"

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
