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
package org.apache.tika.parser;

import java.io.InputStream;
import java.io.StringWriter;

import org.apache.tika.metadata.Metadata;
import org.xml.sax.ContentHandler;

import junit.framework.TestCase;

public class AutoDetectParserTest extends TestCase {

    private void assertAutoDetect(
            String resource, String type, String content) throws Exception {
        InputStream input =
            AutoDetectParserTest.class.getResourceAsStream(resource);
        try {
            Metadata metadata = new Metadata();
            metadata.set("filename", resource);
            metadata.set(Metadata.CONTENT_TYPE, type);
            StringWriter writer = new StringWriter();
            ContentHandler handler = new WriteOutContentHandler(writer);
            new AutoDetectParser().parse(input, handler, metadata);

            assertEquals(type, metadata.get(Metadata.CONTENT_TYPE));
            System.out.println(writer.toString());
            assertTrue(writer.toString().contains(content));
        } finally {
            input.close();
        }
    }

    public void testAutoDetect() throws Exception {
        assertAutoDetect(
                "/test-documents/testEXCEL.xls",
                "application/vnd.ms-excel",
                "Sample Excel Worksheet");
        assertAutoDetect(
                "/test-documents/testHTML.html",
                "text/html",
                "Test Indexation Html");
        /* FIXME: OpenDocument autodetection doesn't work
        assertAutoDetect(
                "/test-documents/testOpenOffice2.odt",
                "application/vnd.oasis.opendocument.text",
                "This is a sample Open Office document");
         */
        assertAutoDetect(
                "/test-documents/testPDF.pdf",
                "application/pdf",
                "Content Analysis Toolkit");
        assertAutoDetect(
                "/test-documents/testPPT.ppt",
                "application/vnd.ms-powerpoint",
                "Sample Powerpoint Slide");
        assertAutoDetect(
                "/test-documents/testRTF.rtf",
                "application/rtf",
                "indexation Word");
        assertAutoDetect(
                "/test-documents/testTXT.txt",
                "text/plain",
                "indexation de Txt");
        assertAutoDetect(
                "/test-documents/testWORD.doc",
                "application/msword",
                "Sample Word Document");
        assertAutoDetect(
                "/test-documents/testXML.xml",
                "application/xml",
                "ArchimÃ¨de et Lius");
    }

}
