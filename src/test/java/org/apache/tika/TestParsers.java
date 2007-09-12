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
import org.apache.tika.exception.LiusException;
import org.apache.tika.log.LiusLogger;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.ParserFactory;
import org.apache.tika.utils.Utils;

/**
 * Junit test class   
 * @author Rida Benjelloun (ridabenjelloun@apache.org)  
 */
public class TestParsers extends TestCase {

    private LiusConfig tc;

    private File classDir;

    private String config;

    public void setUp() {
        String sep = File.separator;
        StringTokenizer st = new StringTokenizer(System.getProperty(
                "java.class.path"), File.pathSeparator);

        classDir = new File(st.nextToken());

        config = classDir.getParent() + sep + "config" + sep + "config.xml";

        String log4j = classDir.getParent() + sep + "Config" + sep + "log4j"
                + sep + "log4j.properties";

        tc = LiusConfig.getInstance(config);

        LiusLogger.setLoggerConfigFile(log4j);

    }

    /*
     * public void testConfig(){ TikaConfig tc =
     * TikaConfig.getInstance("C:\\tika\\config\\tikaConfig2.xml"); ParserConfig
     * pc = tc.getParserConfig("text/html"); assertEquals("parse-html",
     * pc.getName()); }
     */

    public void testPDFExtraction() {
        Parser parser = null;
        File testFile = new File(classDir.getParent() + File.separator
                + "testFiles" + File.separator + "testPDF.PDF");
        try {
            parser = ParserFactory.getParser(testFile, tc);
        } catch (IOException e) {
            e.printStackTrace();
        } catch (LiusException e) {
            e.printStackTrace();
        }

    }

    public void testTXTExtraction() {
        Parser parser = null;
        File testFile = new File(classDir.getParent() + File.separator
                + "testFiles" + File.separator + "testTXT.txt");
        try {
            parser = ParserFactory.getParser(testFile, tc);
        } catch (IOException e) {
            e.printStackTrace();
        } catch (LiusException e) {
            e.printStackTrace();
        }

    }

    public void testRTFExtraction() {
        Parser parser = null;
        File testFile = new File(classDir.getParent() + File.separator
                + "testFiles" + File.separator + "testRTF.rtf");
        try {
            parser = ParserFactory.getParser(testFile, tc);
        } catch (IOException e) {
            e.printStackTrace();
        } catch (LiusException e) {
            e.printStackTrace();
        }

    }

    public void testXMLExtraction() {
        Parser parser = null;
        File testFile = new File(classDir.getParent() + File.separator
                + "testFiles" + File.separator + "testXML.xml");
        try {
            parser = ParserFactory.getParser(testFile, tc);
        } catch (IOException e) {
            e.printStackTrace();
        } catch (LiusException e) {
            e.printStackTrace();
        }

    }

    public void testPPTExtraction() {
        Parser parser = null;
        File testFile = new File(classDir.getParent() + File.separator
                + "testFiles" + File.separator + "testPPT.ppt");
        try {
            parser = ParserFactory.getParser(testFile, tc);
            System.out.println(parser.getStrContent());
        } catch (IOException e) {
            e.printStackTrace();
        } catch (LiusException e) {
            e.printStackTrace();
        }

    }

    public void testWORDxtraction() {
        Parser parser = null;
        File testFile = new File(classDir.getParent() + File.separator
                + "testFiles" + File.separator + "testWORD.doc");
        try {
            parser = ParserFactory.getParser(testFile, tc);
            System.out.println(parser.getStrContent());
        } catch (IOException e) {
            e.printStackTrace();
        } catch (LiusException e) {
            e.printStackTrace();
        }

    }

    public void testEXCELExtraction() {
        Parser parser = null;
        File testFile = new File(classDir.getParent() + File.separator
                + "testFiles" + File.separator + "testEXCEL.xls");
        try {
            parser = ParserFactory.getParser(testFile, tc);
            // System.out.println(parser.getStrContent());
            printContentsInfo(parser);
        } catch (IOException e) {
            e.printStackTrace();
        } catch (LiusException e) {
            e.printStackTrace();
        }

    }

    public void testOOExtraction() {
        Parser parser = null;
        File testFile = new File(classDir.getParent() + File.separator
                + "testFiles" + File.separator + "testOO2.odt");
        try {
            parser = ParserFactory.getParser(testFile, tc);
            // System.out.println(parser.getStrContent());
            printContentsInfo(parser);
        } catch (IOException e) {
            e.printStackTrace();
        } catch (LiusException e) {
            e.printStackTrace();
        }
        
    }

    public void testHTMLExtraction() {
        Parser parser = null;
        File testFile = new File(classDir.getParent() + File.separator
                + "testFiles" + File.separator + "testHTML.html");
        try {
            parser = ParserFactory.getParser(testFile, tc);
            assertEquals("Title : Test Indexation Html", (parser.getContent("title")).getValue());
            // System.out.println(parser.getStrContent());
            printContentsInfo(parser);
        } catch (IOException e) {
            e.printStackTrace();
        } catch (LiusException e) {
            e.printStackTrace();
        }

    }

    private void printContentsInfo(Parser parser) {
        String mimeType = parser.getMimeType();
        System.out.println("Mime : " + mimeType);
        String strContent = parser.getStrContent();
        Collection<Content> structuredContent = parser.getContents();
        Utils.print(structuredContent);
        System.out.println("==============");
        // Content title = parser.getContent("title");
    }

}
