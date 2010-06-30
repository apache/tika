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
package org.apache.tika.parser.pdf;

import java.io.InputStream;

import junit.framework.TestCase;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.xml.sax.ContentHandler;

/**
 * Test case for parsing pdf files.
 */
public class PDFParserTest extends TestCase {

    public void testPdfParsing() throws Exception {
        Parser parser = new AutoDetectParser(); // Should auto-detect!
        ContentHandler handler = new BodyContentHandler();
        Metadata metadata = new Metadata();
        ParseContext context = new ParseContext();

        InputStream stream = PDFParserTest.class.getResourceAsStream(
                "/test-documents/testPDF.pdf");
        try {
            parser.parse(stream, handler, metadata, context);
        } finally {
            stream.close();
        }

        assertEquals("application/pdf", metadata.get(Metadata.CONTENT_TYPE));
        assertEquals("Bertrand Delacrétaz", metadata.get(Metadata.AUTHOR));
        assertEquals("Apache Tika - Apache Tika", metadata.get(Metadata.TITLE));
        
        // Can't reliably test dates yet - see TIKA-451 
//        assertEquals("Sat Sep 15 10:02:31 BST 2007", metadata.get(Metadata.CREATION_DATE));
//        assertEquals("Sat Sep 15 10:02:31 BST 2007", metadata.get(Metadata.LAST_MODIFIED));

        String content = handler.toString();
        assertTrue(content.contains("Apache Tika"));
        assertTrue(content.contains("Tika - Content Analysis Toolkit"));
        assertTrue(content.contains("incubator"));
        assertTrue(content.contains("Apache Software Foundation"));
    }
}
