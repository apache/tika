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
package org.apache.tika.parser.pkg;

import java.io.InputStream;

import junit.framework.TestCase;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.xml.sax.ContentHandler;

/**
 * Test case for parsing zip files.
 */
public class ZipParserTest extends TestCase {

    public void testZipParsing() throws Exception {
        Parser parser = new AutoDetectParser(); // Should auto-detect!
        ContentHandler handler = new BodyContentHandler();
        Metadata metadata = new Metadata();

        InputStream stream = ZipParserTest.class.getResourceAsStream(
                "/test-documents/test-documents.zip");
        try {
            parser.parse(stream, handler, metadata);
        } finally {
            stream.close();
        }

        assertEquals("application/zip", metadata.get(Metadata.CONTENT_TYPE));
        String content = handler.toString();
        assertTrue(content.contains("testEXCEL.xls"));
        assertTrue(content.contains("Sample Excel Worksheet"));
        assertTrue(content.contains("testHTML.html"));
        assertTrue(content.contains("Test Indexation Html"));
        assertTrue(content.contains("testOpenOffice2.odt"));
        assertTrue(content.contains("This is a sample Open Office document"));
        assertTrue(content.contains("testPDF.pdf"));
        assertTrue(content.contains("Apache Tika"));
        assertTrue(content.contains("testPPT.ppt"));
        assertTrue(content.contains("Sample Powerpoint Slide"));
        assertTrue(content.contains("testRTF.rtf"));
        assertTrue(content.contains("indexation Word"));
        assertTrue(content.contains("testTXT.txt"));
        assertTrue(content.contains("Test d'indexation de Txt"));
        assertTrue(content.contains("testWORD.doc"));
        assertTrue(content.contains("This is a sample Microsoft Word Document"));
        assertTrue(content.contains("testXML.xml"));
        assertTrue(content.contains("Rida Benjelloun"));
    }

}
