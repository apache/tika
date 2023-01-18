/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.tika.parser.csv;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.io.ByteOrderMark;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import org.apache.tika.TikaTest;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.Parser;

public class TextAndCSVParserTest extends TikaTest {

    private static byte[] CSV_UTF8 = ("the,quick,brown\tfox\n" + "jumped \tover,the\tlazy,\tdog\n" +
            "and then,ran,down\tthe\tstreet").getBytes(StandardCharsets.UTF_8);

    private static byte[] CSV_UTF_16LE =
            ("the,quick,brown\tfox\n" + "jumped \tover,the\tlazy,\tdog\n" +
                    "and then,ran,down\tthe\tstreet").getBytes(StandardCharsets.UTF_16LE);


    private static byte[] TSV_UTF8 = ("the\tquick\tbrown,fox\n" + "jumped ,over\tthe,lazy\t,dog\n" +
            "and then\tran\tdown,the,street").getBytes(StandardCharsets.UTF_8);

    private static byte[] TSV_UTF_16LE =
            ("the\tquick\tbrown,fox\n" + "jumped ,over\tthe,lazy\t,dog\n" +
                    "and then\tran\tdown,the,street").getBytes(StandardCharsets.UTF_16LE);


    private static String EXPECTED_TSV =
            ("<table><tr> <td>the</td> <td>quick</td> <td>brown,fox</td></tr>\n" +
                    "<tr> <td>jumped ,over</td> <td>the,lazy</td> <td>,dog</td></tr>\n" +
                    "<tr> <td>and then</td> <td>ran</td> <td>down,the,street</td></tr>\n" +
                    "</table>").replaceAll("[\r\n\t ]+", " ");

    private static String EXPECTED_CSV = EXPECTED_TSV.replaceAll(",+", " ");

    private static Parser PARSER;

    @BeforeAll
    public static void setUp() throws Exception {

        try (InputStream is = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream("org/apache/tika/parser/csv/tika-config.xml")) {
            PARSER = new AutoDetectParser(new TikaConfig(is));
        }
    }

    private static void assertMediaTypeEquals(String csv, String charset, String delimiter,
                                              String mediaTypeString) {
        if (mediaTypeString == null) {
            fail("media type string must not be null");
        }
        MediaType expected = mediaType(csv, charset, delimiter);
        MediaType observed = MediaType.parse(mediaTypeString);
        assertEquals(expected, observed);
    }

    private static MediaType mediaType(String csv, String charset, String delimiter) {
        Map<String, String> attrs = new HashMap<>();
        attrs.put("charset", charset);
        attrs.put("delimiter", delimiter);
        return new MediaType(MediaType.text(csv), attrs);
    }

    private static byte[] concat(byte[] bytesA, byte[] bytesB) {
        byte[] ret = new byte[bytesA.length + bytesB.length];
        System.arraycopy(bytesA, 0, ret, 0, bytesA.length);
        System.arraycopy(bytesB, 0, ret, bytesA.length, bytesB.length);
        return ret;
    }

    @Test
    public void testCSV_UTF8() throws Exception {
        Metadata metadata = new Metadata();
        metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, "test.csv");
        XMLResult xmlResult = getXML(new ByteArrayInputStream(CSV_UTF8), PARSER, metadata);
        assertEquals("comma", xmlResult.metadata.get(TextAndCSVParser.DELIMITER_PROPERTY));
        assertMediaTypeEquals("csv", "ISO-8859-1", "comma",
                xmlResult.metadata.get(Metadata.CONTENT_TYPE));
        assertContainsIgnoreWhiteSpaceDiffs(EXPECTED_CSV, xmlResult.xml);
        assertEquals(3, metadata.getInt(TextAndCSVParser.NUM_COLUMNS));
        assertEquals(3, metadata.getInt(TextAndCSVParser.NUM_ROWS));
    }

    @Test
    public void testCSV_UTF8_TypeOverride() throws Exception {
        Metadata metadata = new Metadata();
        metadata.set(TikaCoreProperties.CONTENT_TYPE_USER_OVERRIDE, "text/csv; charset=UTF-8");
        XMLResult xmlResult = getXML(new ByteArrayInputStream(CSV_UTF8), PARSER, metadata);
        assertEquals("comma", xmlResult.metadata.get(TextAndCSVParser.DELIMITER_PROPERTY));
        assertMediaTypeEquals("csv", "UTF-8", "comma",
                xmlResult.metadata.get(Metadata.CONTENT_TYPE));

        assertContainsIgnoreWhiteSpaceDiffs(EXPECTED_CSV, xmlResult.xml);
    }

    @Test
    public void testCSV_UTF8_Type() throws Exception {
        Metadata metadata = new Metadata();
        metadata.set(Metadata.CONTENT_TYPE, "text/csv");
        XMLResult xmlResult = getXML(new ByteArrayInputStream(CSV_UTF8), PARSER, metadata);
        assertEquals("comma", xmlResult.metadata.get(TextAndCSVParser.DELIMITER_PROPERTY));
        assertMediaTypeEquals("csv", "ISO-8859-1", "comma",
                xmlResult.metadata.get(Metadata.CONTENT_TYPE));
        assertContainsIgnoreWhiteSpaceDiffs(EXPECTED_CSV, xmlResult.xml);
    }

    @Test
    public void testCSV_UTF16LE() throws Exception {
        Metadata metadata = new Metadata();
        metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, "test.csv");
        XMLResult xmlResult = getXML(new ByteArrayInputStream(CSV_UTF_16LE), PARSER, metadata);
        assertEquals("comma", xmlResult.metadata.get(TextAndCSVParser.DELIMITER_PROPERTY));
        assertMediaTypeEquals("csv", "UTF-16LE", "comma",
                xmlResult.metadata.get(Metadata.CONTENT_TYPE));
        assertContainsIgnoreWhiteSpaceDiffs(EXPECTED_CSV, xmlResult.xml);
    }

    @Test
    public void testCSV_UTF16LE_BOM() throws Exception {
        Metadata metadata = new Metadata();
        metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, "test.csv");
        XMLResult xmlResult = getXML(new ByteArrayInputStream(
                concat(ByteOrderMark.UTF_16LE.getBytes(), CSV_UTF_16LE)), PARSER, metadata);
        assertEquals("comma", xmlResult.metadata.get(TextAndCSVParser.DELIMITER_PROPERTY));
        assertMediaTypeEquals("csv", "UTF-16LE", "comma",
                xmlResult.metadata.get(Metadata.CONTENT_TYPE));
        assertContainsIgnoreWhiteSpaceDiffs(EXPECTED_CSV, xmlResult.xml);
    }

    @Test
    public void testTSV_UTF8() throws Exception {
        Metadata metadata = new Metadata();
        metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, "test.csv");
        XMLResult xmlResult = getXML(new ByteArrayInputStream(TSV_UTF8), PARSER, metadata);
        assertEquals("tab", xmlResult.metadata.get(TextAndCSVParser.DELIMITER_PROPERTY));
        assertMediaTypeEquals("tsv", "ISO-8859-1", "tab",
                xmlResult.metadata.get(Metadata.CONTENT_TYPE));
        assertContainsIgnoreWhiteSpaceDiffs(EXPECTED_TSV, xmlResult.xml);
    }

    @Test
    public void testTSV_UTF16LE() throws Exception {
        Metadata metadata = new Metadata();
        metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, "test.csv");
        XMLResult xmlResult = getXML(new ByteArrayInputStream(TSV_UTF_16LE), PARSER, metadata);
        assertEquals("tab", xmlResult.metadata.get(TextAndCSVParser.DELIMITER_PROPERTY));
        assertMediaTypeEquals("tsv", "UTF-16LE", "tab",
                xmlResult.metadata.get(Metadata.CONTENT_TYPE));
        assertContainsIgnoreWhiteSpaceDiffs(EXPECTED_TSV, xmlResult.xml);
    }

    @Test
    public void testBadCsv() throws Exception {
        //this causes an IllegalStateException during delimiter detection
        //when trying to parse with ','; therefore, the parser backs off to
        //treating this as straight text.
        //This isn't necessarily the best outcome, but we want to make sure
        //that an IllegalStateException during delimiter guessing doesn't
        //make the parse fail.

        byte[] csv = ("the,quick\n" + "brown,\"la\"zy\"\n" + "brown,\"dog\n")
                .getBytes(StandardCharsets.UTF_8);
        Metadata metadata = new Metadata();
        metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, "test.csv");
        XMLResult xmlResult = getXML(new ByteArrayInputStream(csv), PARSER, metadata);
        assertNull(xmlResult.metadata.get(TextAndCSVParser.DELIMITER_PROPERTY));
        assertEquals("text/plain; charset=ISO-8859-1",
                xmlResult.metadata.get(Metadata.CONTENT_TYPE));
        assertContains("the,quick", xmlResult.xml);
    }

    @Test //TIKA-2836
    public void testNonCSV() throws Exception {

        byte[] bytes =
                ("testcsv\n" + "testcsv testcsv;;; testcsv").getBytes(StandardCharsets.UTF_8);
        Metadata metadata = new Metadata();
        metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, "test.csv");
        XMLResult xmlResult = getXML(new ByteArrayInputStream(bytes), PARSER, metadata);
        assertContains("text/plain", xmlResult.metadata.get(Metadata.CONTENT_TYPE));

        metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, "test.txt");
        xmlResult = getXML(new ByteArrayInputStream(bytes), PARSER, metadata);
        assertContains("text/plain", xmlResult.metadata.get(Metadata.CONTENT_TYPE));
    }

    @Test
    public void testLong() throws Exception {
        //test mark/reset worked on the sniffers
        StringBuilder sb = new StringBuilder();
        for (int rows = 0; rows < 1000; rows++) {
            for (int cols = 0; cols < 10; cols++) {
                sb.append("2").append(",");
            }
            sb.append("\n");
        }
        Metadata metadata = new Metadata();
        XMLResult xmlResult =
                getXML(new ByteArrayInputStream(sb.toString().getBytes(StandardCharsets.UTF_8)),
                        PARSER, metadata);
        assertMediaTypeEquals("csv", "ISO-8859-1", "comma",
                xmlResult.metadata.get(Metadata.CONTENT_TYPE));
    }

    //TIKA-2047
    @Test
    public void testSubclassingMimeTypesRemain() throws Exception {
        XMLResult r = getXML("testVCalendar.vcs");
        assertEquals("text/x-vcalendar; charset=ISO-8859-1", r.metadata.get(Metadata.CONTENT_TYPE));
    }

    private void assertContainsIgnoreWhiteSpaceDiffs(String expected, String xml) {
        assertContains(expected, xml.replaceAll("[\r\n\t ]", " "));
    }

}
