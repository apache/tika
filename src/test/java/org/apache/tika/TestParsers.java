/**
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
package org.apache.tika;

import java.io.File;
import java.io.IOException;

import org.apache.tika.config.Content;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.log.TikaLogger;
import org.apache.tika.parser.Parser;
import org.apache.tika.utils.ParseUtils;
import org.apache.tika.utils.Utils;
import org.jdom.JDOMException;

import junit.framework.TestCase;

/**
 * Junit test class for Tika {@link Parser}s. 
 */
public class TestParsers extends TestCase {

    private TikaConfig tc;
    private File testFilesBaseDir; 

    public void setUp() throws JDOMException, IOException {
        /* FIXME the old mechanism does not work anymore when running the tests
         * with Maven - need a resource-based one, but this means more
         * changes to classes which rely on filenames.
         *  
        String sep = File.separator;
        StringTokenizer st = new StringTokenizer(System.getProperty(
                "java.class.path"), File.pathSeparator);

        classDir = new File(st.nextToken());

        config = classDir.getParent() + sep + "config" + sep + "config.xml";

        String log4j = classDir.getParent() + sep + "Config" + sep + "log4j"
                + sep + "log4j.properties";
         */ 

        // FIXME for now, fix filenames according to Maven testing layout
        final String tikaConfigFilename = "target/classes/tika-config.xml";
        final String log4jPropertiesFilename = "target/classes/log4j/log4j.properties";
        testFilesBaseDir = new File("src/test/resources/test-documents");
        
        tc = new TikaConfig(tikaConfigFilename);

        TikaLogger.setLoggerConfigFile(log4jPropertiesFilename);

    }

    public void testPDFExtraction() throws Exception {
        File file = getTestFile("testPDF.pdf");
        String s1 = ParseUtils.getStringContent(file, tc);
        String s2 = ParseUtils.getStringContent(file, tc, "application/pdf");

        Parser parser = ParseUtils.getParser(file, tc);
        String s3 = parser.getStrContent();

        assertEquals(s1, s2);
        assertEquals(s1, s3);
    }

    public void testTXTExtraction() throws Exception {
        File file = getTestFile("testTXT.txt");
        String s1 = ParseUtils.getStringContent(file, tc);
        String s2 = ParseUtils.getStringContent(file, tc, "text/plain");
        assertEquals(s1, s2);
    }

    public void testRTFExtraction() throws Exception {
        File file = getTestFile("testRTF.rtf");
        String s1 = ParseUtils.getStringContent(file, tc);
        String s2 = ParseUtils.getStringContent(file, tc, "application/rtf");
        assertEquals(s1, s2);
    }

    public void testXMLExtraction() throws Exception {
        File file = getTestFile("testXML.xml");
        String s1 = ParseUtils.getStringContent(file, tc);
        String s2 = ParseUtils.getStringContent(file, tc, "application/xml");
        assertEquals(s1, s2);
    }

    public void testPPTExtraction() throws Exception {
        File file = getTestFile("testPPT.ppt");
        String s1 = ParseUtils.getStringContent(file, tc);
        String s2 = ParseUtils.getStringContent(file, tc,
                "application/vnd.ms-powerpoint");
        assertEquals(s1, s2);
    }

    public void testWORDxtraction() throws Exception {
        File file = getTestFile("testWORD.doc");
        String s1 = ParseUtils.getStringContent(file, tc);
        String s2 = ParseUtils.getStringContent(file, tc, "application/msword");
        assertEquals(s1, s2);
    }

    public void testEXCELExtraction() throws Exception {
        File file = getTestFile("testEXCEL.xls");
        String s1 = ParseUtils.getStringContent(file, tc);
        String s2 = ParseUtils.getStringContent(file, tc,
                "application/vnd.ms-excel");
        assertEquals(s1, s2);
    }

    public void testOOExtraction() throws Exception {
        File file = getTestFile("testOpenOffice2.odt");
        String s1 = ParseUtils.getStringContent(file, tc);
        String s2 = ParseUtils.getStringContent(file, tc,
                "application/vnd.oasis.opendocument.text");
        assertEquals(s1, s2);
    }

    public void testHTMLExtraction() throws Exception {
        File file = getTestFile("testHTML.html");
        String s1 = ParseUtils.getStringContent(file, tc);
        String s2 = ParseUtils.getStringContent(file, tc, "text/html");
        assertEquals(s1, s2);

        Parser parser = ParseUtils.getParser(file, tc);
        assertNotNull(parser);
        assertEquals("org.apache.tika.parser.html.HtmlParser", parser.getClass().getName());

        
        Content content = parser.getContent("title");
        assertNotNull(content);
        assertEquals("Title : Test Indexation Html", content.getValue());

        assertEquals("text/html", parser.getMimeType());

        final String text = Utils.toString(parser.getContents());
        final String expected = "Test Indexation Html";
        assertTrue("text contains '" + expected + "'",
                text.contains(expected));
        parser.getInputStream().close();
    }

    private File getTestFile(String filename) {
      return new File(testFilesBaseDir, filename);
    }

}
