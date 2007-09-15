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
import java.util.Collection;
import java.util.StringTokenizer;

import junit.framework.TestCase;

import org.apache.tika.config.Content;
import org.apache.tika.config.LiusConfig;
import org.apache.tika.log.LiusLogger;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.ParserFactory;
import org.apache.tika.utils.Utils;
import org.jdom.JDOMException;

/**
 * Junit test class   
 * @author Rida Benjelloun (ridabenjelloun@apache.org)  
 */
public class TestParsers extends TestCase {

    private LiusConfig tc;
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
        final String liusConfigFilename = "target/classes/config.xml";
        final String log4jPropertiesFilename = "target/classes/log4j/log4j.properties";
        testFilesBaseDir = new File("src/test/resources/test-documents");
        
        tc = LiusConfig.getInstance(liusConfigFilename);

        LiusLogger.setLoggerConfigFile(log4jPropertiesFilename);

    }
    
    /*
     * public void testConfig(){ TikaConfig tc =
     * TikaConfig.getInstance("C:\\tika\\config\\tikaConfig2.xml"); ParserConfig
     * pc = tc.getParserConfig("text/html"); assertEquals("parse-html",
     * pc.getName()); }
     */

    public void testPDFExtraction() throws Exception {
        ParserFactory.getParser(getTestFile("testPDF.pdf"), tc);
    }

    public void testTXTExtraction() throws Exception {
        ParserFactory.getParser(getTestFile("testTXT.txt"), tc);
    }

    public void testRTFExtraction() throws Exception {
        ParserFactory.getParser(getTestFile("testRTF.rtf"), tc);
    }

    public void testXMLExtraction() throws Exception {
        ParserFactory.getParser(getTestFile("testXML.xml"), tc);
    }

    public void testPPTExtraction() throws Exception {
        ParserFactory.getParser(getTestFile("testPPT.ppt"), tc);
    }

    public void testWORDxtraction() throws Exception {
        ParserFactory.getParser(getTestFile("testWORD.doc"), tc);
    }

    public void testEXCELExtraction() throws Exception {
        ParserFactory.getParser(getTestFile("testEXCEL.xls"), tc);
    }

    public void testOOExtraction() throws Exception {
        ParserFactory.getParser(getTestFile("testOpenOffice2.odt"), tc);
    }

    public void testHTMLExtraction() throws Exception {
        Parser parser = ParserFactory.getParser(getTestFile("testHTML.html"), tc);
        assertEquals("Title : Test Indexation Html", (parser.getContent("title")).getValue());
        assertEquals("text/html",parser.getMimeType());
        final String text = Utils.toString(parser.getContents());
        
        final String expected = "Test Indexation Html";
        assertTrue("text contains '" + expected + "'",text.indexOf(expected) >= 0);
    }

    private File getTestFile(String filename) {
      return new File(testFilesBaseDir,filename); 
    }

}
