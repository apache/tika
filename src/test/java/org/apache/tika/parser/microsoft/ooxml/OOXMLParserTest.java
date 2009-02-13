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
package org.apache.tika.parser.microsoft.ooxml;

import java.io.InputStream;

import junit.framework.TestCase;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.opendocument.OpenOfficeParserTest;
import org.apache.tika.sax.BodyContentHandler;
import org.xml.sax.ContentHandler;

import org.apache.tika.parser.AutoDetectParser;

public class OOXMLParserTest extends TestCase {

    public void testExcel() throws Exception {
        InputStream input = OpenOfficeParserTest.class
                .getResourceAsStream("/test-documents/testEXCEL.xlsx");

        Parser parser = new AutoDetectParser();
        Metadata metadata = new Metadata();
        // TODO: should auto-detect without the resource name
        metadata.set(Metadata.RESOURCE_NAME_KEY, "testEXCEL.xlsx");
        ContentHandler handler = new BodyContentHandler();

        try {
            parser.parse(input, handler, metadata);
            
            assertEquals(
                    "application/vnd.openxmlformats-package.core-properties+xml",
                    metadata.get(Metadata.CONTENT_TYPE));
            assertEquals("Simple Excel document", metadata.get(Metadata.TITLE));
            assertEquals("Keith Bennett", metadata.get(Metadata.AUTHOR));
            String content = handler.toString();
            assertTrue(content.contains("Sample Excel Worksheet"));
            assertTrue(content.contains("Numbers and their Squares"));
            assertTrue(content.contains("9"));
            assertFalse(content.contains("9.0"));
            assertTrue(content.contains("196"));
            assertFalse(content.contains("196.0"));
        } finally {
            input.close();
        }
    }

    public void testPowerPoint() throws Exception {
        InputStream input = OpenOfficeParserTest.class
                .getResourceAsStream("/test-documents/testPPT.pptx");

        Parser parser = new AutoDetectParser();
        Metadata metadata = new Metadata();
        // TODO: should auto-detect without the resource name
        metadata.set(Metadata.RESOURCE_NAME_KEY, "testPPT.pptx");
        ContentHandler handler = new BodyContentHandler();

        try {
            parser.parse(input, handler, metadata);
            
            assertEquals(
                    "application/vnd.openxmlformats-package.core-properties+xml",
                    metadata.get(Metadata.CONTENT_TYPE));
            assertEquals("Sample Powerpoint Slide", metadata.get(Metadata.TITLE));
            assertEquals("Keith Bennett", metadata.get(Metadata.AUTHOR));
            String content = handler.toString();
            assertTrue(content.contains("Sample Powerpoint Slide"));
            assertTrue(content.contains("Powerpoint X for Mac"));
        } finally {
            input.close();
        }

    }

    public void testWord() throws Exception {
        InputStream input = OpenOfficeParserTest.class
                .getResourceAsStream("/test-documents/testWORD.docx");

        Parser parser = new AutoDetectParser();
        Metadata metadata = new Metadata();
        // TODO: should auto-detect without the resource name
        metadata.set(Metadata.RESOURCE_NAME_KEY, "testWORD.docx");
        ContentHandler handler = new BodyContentHandler();

        try {
            parser.parse(input, handler, metadata);
            
            assertEquals(
                    "application/vnd.openxmlformats-package.core-properties+xml",
                    metadata.get(Metadata.CONTENT_TYPE));
            assertEquals("Sample Word Document", metadata.get(Metadata.TITLE));
            assertEquals("Keith Bennett", metadata.get(Metadata.AUTHOR));
            assertTrue(handler.toString().contains("Sample Word Document"));
        } finally {
            input.close();
        }
    }

}