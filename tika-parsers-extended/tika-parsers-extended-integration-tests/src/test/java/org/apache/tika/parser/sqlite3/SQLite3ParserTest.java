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

import org.apache.tika.TikaTest;
import org.apache.tika.extractor.EmbeddedResourceHandler;
import org.apache.tika.extractor.ParserContainerExtractor;
import org.apache.tika.io.IOUtils;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Database;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.EmptyParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.RecursiveParserWrapper;
import org.apache.tika.sax.AbstractRecursiveParserWrapperHandler;
import org.apache.tika.sax.BasicContentHandlerFactory;
import org.apache.tika.sax.RecursiveParserWrapperHandler;
import org.apache.tika.sax.ToXMLContentHandler;
import org.junit.Test;
import org.xml.sax.ContentHandler;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;

public class SQLite3ParserTest extends TikaTest {
    private final static String TEST_FILE_NAME = "testSqlite3b.db";
    private final static String TEST_FILE1 = "/test-documents/" + TEST_FILE_NAME;

    @Test
    public void testBasic() throws Exception {

        //test different types of input streams
        //actual inputstream, memory buffered bytearray and literal file
        try (InputStream stream = getResourceAsStream(TEST_FILE1)) {
            _testBasic(stream);
        }
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        IOUtils.copy(getResourceAsStream(TEST_FILE1), bos);
        try (InputStream stream = new ByteArrayInputStream(bos.toByteArray())) {
            _testBasic(stream);
        }
        try (TikaInputStream outer = TikaInputStream.get(getResourceAsStream(TEST_FILE1))) {
            try (TikaInputStream inner = TikaInputStream.get(outer.getFile())) {
                _testBasic(inner);
            }
        }
    }

    private void _testBasic(InputStream stream) throws Exception {
        Metadata metadata = new Metadata();
        metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, TEST_FILE_NAME);
        //1) getXML closes the stream
        //2) getXML runs recursively on the contents, so the embedded docs should show up
        XMLResult result = getXML(stream, AUTO_DETECT_PARSER, metadata);
        String x = result.xml;
        //first table name
        assertContains("<table name=\"my_table1\"><thead><tr>\t<th>PK</th>", x);
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
    }

    //test what happens if the user does not want embedded docs handled
    @Test
    public void testNotAddingEmbeddedParserToParseContext() throws Exception {
        ContentHandler handler = new ToXMLContentHandler();
        Metadata metadata = new Metadata();
        ParseContext parseContext = new ParseContext();
        parseContext.set(Parser.class, new EmptyParser());
        try (InputStream is = getResourceAsStream(TEST_FILE1)) {
            metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, TEST_FILE_NAME);
            AUTO_DETECT_PARSER.parse(is, handler, metadata, parseContext);
        }
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

        RecursiveParserWrapper wrapper =
                new RecursiveParserWrapper(AUTO_DETECT_PARSER);
        Metadata metadata = new Metadata();
        RecursiveParserWrapperHandler handler = new RecursiveParserWrapperHandler(
                new BasicContentHandlerFactory(
                        BasicContentHandlerFactory.HANDLER_TYPE.BODY, -1)
        );

        try (InputStream is = getResourceAsStream(TEST_FILE1)) {
            metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, TEST_FILE_NAME);
            wrapper.parse(is, handler, metadata, new ParseContext());
        }
        List<Metadata> metadataList = handler.getMetadataList();
        int i = 0;
        assertEquals(5, metadataList.size());
        //make sure the \t are inserted in a body handler

        String table = metadataList.get(0).get(AbstractRecursiveParserWrapperHandler.TIKA_CONTENT);
        assertContains("0\t2.3\t2.4\tlorem", table);
        assertContains("普林斯顿大学", table);

        //make sure the \n is inserted
        String table2 = metadataList.get(0).get(AbstractRecursiveParserWrapperHandler.TIKA_CONTENT);
        assertContains("do eiusmod tempor\n", table2);

        assertContains("The quick brown fox", metadataList.get(2).get(AbstractRecursiveParserWrapperHandler.TIKA_CONTENT));
        assertContains("The quick brown fox", metadataList.get(4).get(AbstractRecursiveParserWrapperHandler.TIKA_CONTENT));

        //confirm .doc was added to blob
        assertEquals("/BYTES_COL_0.doc/image1.png", metadataList.get(1).get(AbstractRecursiveParserWrapperHandler.EMBEDDED_RESOURCE_PATH));
    }

    @Test
    public void testParserContainerExtractor() throws Exception {
        //There should be 6 embedded documents:
        //2x tables -- UTF-8 csv representations of the tables
        //2x word files, one doc and one docx
        //2x png files, the same image embedded in each of the doc and docx

        ParserContainerExtractor ex = new ParserContainerExtractor();
        ByteCopyingHandler byteCopier = new ByteCopyingHandler();
        Metadata metadata = new Metadata();
        try (TikaInputStream is = TikaInputStream.get(getResourceAsStream(TEST_FILE1))) {
            metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, TEST_FILE_NAME);
            ex.extract(is, ex, byteCopier);
        }
        assertEquals(4, byteCopier.bytes.size());
        String[] strings = new String[4];
        for (int i = 1; i < byteCopier.bytes.size(); i++) {
            byte[] byteArr = byteCopier.bytes.get(i);
            String s = new String(byteArr, 0, Math.min(byteArr.length, 1000), UTF_8);
            strings[i] = s;
        }
        byte[] oleBytes = new byte[]{
                (byte) -48,
                (byte) -49,
                (byte) 17,
                (byte) -32,
                (byte) -95,
                (byte) -79,
                (byte) 26,
                (byte) -31,
                (byte) 0,
                (byte) 0,
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
        Metadata metadata = new Metadata();
        metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, TEST_FILE_NAME);
        try (TikaInputStream outer = TikaInputStream.get(getResourceAsStream(TEST_FILE1))) {
            try (TikaInputStream tis = TikaInputStream.get(outer.getPath())) {
                ex.extract(tis, ex, byteCopier);
                tis.reset();
            }
        }
        assertEquals(8, byteCopier.bytes.size());
    }



    public static class InputStreamResettingHandler implements EmbeddedResourceHandler {

        public List<byte[]> bytes = new ArrayList<byte[]>();

        @Override
        public void handle(String filename, MediaType mediaType,
                           InputStream stream) {
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            if (!stream.markSupported()) {
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

}
