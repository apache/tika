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

import static org.junit.Assert.assertEquals;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import org.apache.commons.io.ByteOrderMark;
import org.apache.tika.TikaTest;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.Parser;
import org.junit.Test;

public class CSVParserTest extends TikaTest {

    private static byte[] CSV_UTF8 =
            ("the,quick,brown\tfox\n" +
              "jumped \tover,the\tlazy,\tdog\n"+
              "and then,ran,down\tthe\tstreet").getBytes(StandardCharsets.UTF_8);

    private static byte[] CSV_UTF_16LE =
            ("the,quick,brown\tfox\n" +
                    "jumped \tover,the\tlazy,\tdog\n"+
                    "and then,ran,down\tthe\tstreet").getBytes(StandardCharsets.UTF_16LE);


    private static byte[] TSV_UTF8 =
            ("the\tquick\tbrown,fox\n" +
                    "jumped ,over\tthe,lazy\t,dog\n"+
                    "and then\tran\tdown,the,street").getBytes(StandardCharsets.UTF_8);

    private static byte[] TSV_UTF_16LE =
            ("the\tquick\tbrown,fox\n" +
                    "jumped ,over\tthe,lazy\t,dog\n"+
                    "and then\tran\tdown,the,street").getBytes(StandardCharsets.UTF_16LE);


    private static String EXPECTED_TSV = ("<table><tr> <td>the</td> <td>quick</td> <td>brown,fox</td></tr>\n" +
            "<tr> <td>jumped ,over</td> <td>the,lazy</td> <td>,dog</td></tr>\n" +
            "<tr> <td>and then</td> <td>ran</td> <td>down,the,street</td></tr>\n" +
            "</table>").replaceAll("[\r\n\t ]+", " ");

    private static String EXPECTED_CSV = EXPECTED_TSV.replaceAll(",+", " ");

    private static Parser PARSER = new AutoDetectParser();

    @Test
    public void testCSV_UTF8() throws Exception {
        Metadata metadata = new Metadata();
        metadata.set(Metadata.RESOURCE_NAME_KEY, "test.csv");
        XMLResult xmlResult = getXML(new ByteArrayInputStream(CSV_UTF8), PARSER, metadata);
        assertEquals("comma", xmlResult.metadata.get(CSVParser.DELIMITER));
        assertEquals("text/csv; charset=ISO-8859-1", xmlResult.metadata.get(Metadata.CONTENT_TYPE));
        assertContainsIgnoreWhiteSpaceDiffs(EXPECTED_CSV, xmlResult.xml);
    }

    @Test
    public void testCSV_UTF8_TypeOverride() throws Exception {
        Metadata metadata = new Metadata();
        metadata.set(TikaCoreProperties.CONTENT_TYPE_OVERRIDE, "text/csv; charset=UTF-8");
        XMLResult xmlResult = getXML(new ByteArrayInputStream(CSV_UTF8), PARSER, metadata);
        assertEquals("comma", xmlResult.metadata.get(CSVParser.DELIMITER));
        assertEquals("text/csv; charset=UTF-8", xmlResult.metadata.get(Metadata.CONTENT_TYPE));
        assertContainsIgnoreWhiteSpaceDiffs(EXPECTED_CSV, xmlResult.xml);
    }

    @Test
    public void testCSV_UTF8_Type() throws Exception {
        Metadata metadata = new Metadata();
        metadata.set(Metadata.CONTENT_TYPE, "text/csv");
        XMLResult xmlResult = getXML(new ByteArrayInputStream(CSV_UTF8), PARSER, metadata);
        assertEquals("comma", xmlResult.metadata.get(CSVParser.DELIMITER));
        assertEquals("text/csv; charset=ISO-8859-1", xmlResult.metadata.get(Metadata.CONTENT_TYPE));
        assertContainsIgnoreWhiteSpaceDiffs(EXPECTED_CSV, xmlResult.xml);
    }

    @Test
    public void testCSV_UTF16LE() throws Exception {
        Metadata metadata = new Metadata();
        metadata.set(Metadata.RESOURCE_NAME_KEY, "test.csv");
        XMLResult xmlResult = getXML(new ByteArrayInputStream(CSV_UTF_16LE), PARSER, metadata);
        assertEquals("comma", xmlResult.metadata.get(CSVParser.DELIMITER));
        assertEquals("text/csv; charset=UTF-16LE", xmlResult.metadata.get(Metadata.CONTENT_TYPE));
        assertContainsIgnoreWhiteSpaceDiffs(EXPECTED_CSV, xmlResult.xml);
    }

    @Test
    public void testCSV_UTF16LE_BOM() throws Exception {
        Metadata metadata = new Metadata();
        metadata.set(Metadata.RESOURCE_NAME_KEY, "test.csv");
        XMLResult xmlResult = getXML(new ByteArrayInputStream(
                concat(ByteOrderMark.UTF_16LE.getBytes(), CSV_UTF_16LE)), PARSER, metadata);
        assertEquals("comma", xmlResult.metadata.get(CSVParser.DELIMITER));
        assertEquals("text/csv; charset=UTF-16LE", xmlResult.metadata.get(Metadata.CONTENT_TYPE));
        assertContainsIgnoreWhiteSpaceDiffs(EXPECTED_CSV, xmlResult.xml);
    }

    private static byte[] concat(byte[] bytesA, byte[] bytesB) {
        byte[] ret = new byte[bytesA.length+bytesB.length];
        System.arraycopy(bytesA, 0, ret, 0, bytesA.length);
        System.arraycopy(bytesB, 0, ret, bytesA.length, bytesB.length);
        return ret;
    }

    @Test
    public void testTSV_UTF8() throws Exception {
        Metadata metadata = new Metadata();
        metadata.set(Metadata.RESOURCE_NAME_KEY, "test.csv");
        XMLResult xmlResult = getXML(new ByteArrayInputStream(TSV_UTF8), PARSER, metadata);
        assertEquals("tab", xmlResult.metadata.get(CSVParser.DELIMITER));
        assertEquals("text/tsv; charset=ISO-8859-1", xmlResult.metadata.get(Metadata.CONTENT_TYPE));
        assertContainsIgnoreWhiteSpaceDiffs(EXPECTED_TSV, xmlResult.xml);
    }

    @Test
    public void testTSV_UTF16LE() throws Exception {
        Metadata metadata = new Metadata();
        metadata.set(Metadata.RESOURCE_NAME_KEY, "test.csv");
        XMLResult xmlResult = getXML(new ByteArrayInputStream(TSV_UTF_16LE), PARSER, metadata);
        assertEquals("tab", xmlResult.metadata.get(CSVParser.DELIMITER));
        assertEquals("text/tsv; charset=UTF-16LE", xmlResult.metadata.get(Metadata.CONTENT_TYPE));
        assertContainsIgnoreWhiteSpaceDiffs(EXPECTED_TSV, xmlResult.xml);
    }

    @Test
    public void testBadCsv() throws Exception {
        //this causes an IllegalStateException during delimiter detection
        //when trying to parse with ','; therefore, the parser backs off to '\t'.
        //this isn't necessarily the best outcome, but we want to make sure
        //that an IllegalStateException during delimiter guessing doesn't
        //make the parse fail.

        byte[] csv = ("the,quick\n" +
                "brown,\"la\"zy\"\n" +
                "brown,\"dog\n").getBytes(StandardCharsets.UTF_8);
        Metadata metadata = new Metadata();
        metadata.set(Metadata.RESOURCE_NAME_KEY, "test.csv");
        XMLResult xmlResult = getXML(new ByteArrayInputStream(csv), PARSER, metadata);
        assertEquals("tab", xmlResult.metadata.get(CSVParser.DELIMITER));
        assertEquals("text/tsv; charset=ISO-8859-1", xmlResult.metadata.get(Metadata.CONTENT_TYPE));
    }

    private void assertContainsIgnoreWhiteSpaceDiffs(String expected, String xml) {
        assertContains(expected, xml.replaceAll("[\r\n\t ]", " "));
    }
}
