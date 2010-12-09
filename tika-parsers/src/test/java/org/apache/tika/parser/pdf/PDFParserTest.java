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
        assertEquals("Bertrand Delacr\u00e9taz", metadata.get(Metadata.AUTHOR));
        assertEquals("Apache Tika - Apache Tika", metadata.get(Metadata.TITLE));
        
        // Can't reliably test dates yet - see TIKA-451 
//        assertEquals("Sat Sep 15 10:02:31 BST 2007", metadata.get(Metadata.CREATION_DATE));
//        assertEquals("Sat Sep 15 10:02:31 BST 2007", metadata.get(Metadata.LAST_MODIFIED));

        String content = handler.toString();
        assertTrue(content.contains("Apache Tika"));
        assertTrue(content.contains("Tika - Content Analysis Toolkit"));
        assertTrue(content.contains("incubator"));
        assertTrue(content.contains("Apache Software Foundation"));
        // testing how the end of one paragraph is separated from start of the next one
        assertTrue("should have word boundary after headline", 
                !content.contains("ToolkitApache"));
        assertTrue("should have word boundary between paragraphs", 
                !content.contains("libraries.Apache"));
    }

    public void testCustomMetadata() throws Exception {
        Parser parser = new AutoDetectParser(); // Should auto-detect!
        ContentHandler handler = new BodyContentHandler();
        Metadata metadata = new Metadata();
        ParseContext context = new ParseContext();

        InputStream stream = PDFParserTest.class.getResourceAsStream(
                "/test-documents/testPDF-custommetadata.pdf");
        try {
            parser.parse(stream, handler, metadata, context);
        } finally {
            stream.close();
        }

        assertEquals("application/pdf", metadata.get(Metadata.CONTENT_TYPE));
        assertEquals("Document author", metadata.get(Metadata.AUTHOR));
        assertEquals("Document title", metadata.get(Metadata.TITLE));
        
        assertEquals("Custom Value", metadata.get("Custom Property"));
        assertEquals("Array Entry 1", metadata.get("Custom Array"));
        assertEquals(2, metadata.getValues("Custom Array").length);
        assertEquals("Array Entry 1", metadata.getValues("Custom Array")[0]);
        assertEquals("Array Entry 2", metadata.getValues("Custom Array")[1]);
        
        String content = handler.toString();
        assertTrue(content.contains("Hello World!"));
    }
    
    /**
     * PDFs can be "protected" with the default password. This means
     *  they're encrypted (potentially both text and metadata),
     *  but we can decrypt them easily.
     */
    public void testProtectedPDF() throws Exception {
       Parser parser = new AutoDetectParser(); // Should auto-detect!
       ContentHandler handler = new BodyContentHandler();
       Metadata metadata = new Metadata();
       ParseContext context = new ParseContext();

       InputStream stream = PDFParserTest.class.getResourceAsStream(
               "/test-documents/testPDF_protected.pdf");
       try {
           parser.parse(stream, handler, metadata, context);
       } finally {
           stream.close();
       }

       assertEquals("application/pdf", metadata.get(Metadata.CONTENT_TYPE));
       assertEquals("The Bank of England", metadata.get(Metadata.AUTHOR));
       assertEquals("Speeches by Andrew G Haldane", metadata.get(Metadata.SUBJECT));
       assertEquals("Rethinking the Financial Network, Speech by Andrew G Haldane, Executive Director, Financial Stability delivered at the Financial Student Association, Amsterdam on 28 April 2009", metadata.get(Metadata.TITLE));

       String content = handler.toString();
       assertTrue(content.contains("RETHINKING THE FINANCIAL NETWORK"));
       assertTrue(content.contains("On 16 November 2002"));
       assertTrue(content.contains("In many important respects"));
    }
}
