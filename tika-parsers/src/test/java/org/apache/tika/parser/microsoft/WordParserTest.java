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
package org.apache.tika.parser.microsoft;

import java.io.InputStream;
import java.io.StringWriter;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamResult;

import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.microsoft.ooxml.OOXMLParserTest;
import org.apache.tika.sax.BodyContentHandler;
import org.xml.sax.ContentHandler;

import junit.framework.TestCase;

public class WordParserTest extends TestCase {

    public void testWordParser() throws Exception {
        InputStream input = WordParserTest.class.getResourceAsStream(
                "/test-documents/testWORD.doc");
        try {
            ContentHandler handler = new BodyContentHandler();
            Metadata metadata = new Metadata();
            new OfficeParser().parse(input, handler, metadata, new ParseContext());

            assertEquals(
                    "application/msword",
                    metadata.get(Metadata.CONTENT_TYPE));
            assertEquals("Sample Word Document", metadata.get(Metadata.TITLE));
            assertEquals("Keith Bennett", metadata.get(Metadata.AUTHOR));
            assertTrue(handler.toString().contains("Sample Word Document"));
        } finally {
            input.close();
        }
    }

    public void testWordWithWAV() throws Exception {
        InputStream input = WordParserTest.class.getResourceAsStream(
                "/test-documents/Doc1_ole.doc");
        try {
            ContentHandler handler = new BodyContentHandler();
            Metadata metadata = new Metadata();
            new OfficeParser().parse(input, handler, metadata, new ParseContext());

            assertTrue(handler.toString().contains("MSj00974840000[1].wav"));
        } finally {
            input.close();
        }
    }

    private static class XMLResult {
        public final String xml;
        public final Metadata metadata;

        public XMLResult(String xml, Metadata metadata) {
            this.xml = xml;
            this.metadata = metadata;
      }
    }

    private XMLResult getXML(String filePath) throws Exception {
        InputStream input = null;
        Metadata metadata = new Metadata();
        
        StringWriter sw = new StringWriter();
        SAXTransformerFactory factory = (SAXTransformerFactory)
                 SAXTransformerFactory.newInstance();
        TransformerHandler handler = factory.newTransformerHandler();
        handler.getTransformer().setOutputProperty(OutputKeys.METHOD, "xml");
        handler.getTransformer().setOutputProperty(OutputKeys.INDENT, "no");
        handler.setResult(new StreamResult(sw));

        // Try with a document containing various tables and formattings
        input = OOXMLParserTest.class.getResourceAsStream(filePath);
        try {
            new OfficeParser().parse(input, handler, metadata, new ParseContext());
            return new XMLResult(sw.toString(), metadata);
        } finally {
            input.close();
        }
    }

    /**
     * Test that the word converter is able to generate the
     *  correct HTML for the document
     */
    public void testWordHTML() throws Exception {

        // Try with a document containing various tables and
        // formattings
        XMLResult result = getXML("/test-documents/testWORD.doc");
        String xml = result.xml;
        Metadata metadata = result.metadata;

        assertEquals(
                     "application/msword",
                     metadata.get(Metadata.CONTENT_TYPE));
        assertEquals("Sample Word Document", metadata.get(Metadata.TITLE));
        assertEquals("Keith Bennett", metadata.get(Metadata.AUTHOR));
        assertTrue(xml.contains("Sample Word Document"));

        // Check that custom headings came through
        assertTrue(xml.contains("<h1 class=\"title\">"));
        // Regular headings
        assertTrue(xml.contains("<h1>Heading Level 1</h1>"));
        assertTrue(xml.contains("<h3>Heading Level 3</h3>"));
        // Bold and italic
        assertTrue(xml.contains("<b>BOLD</b>"));
        assertTrue(xml.contains("<i>ITALIC</i>"));
        // Table
        assertTrue(xml.contains("<table>"));
        assertTrue(xml.contains("<td>"));
        // TODO - Check for the nested table
        // Links
        assertTrue(xml.contains("<a href=\"http://tika.apache.org/\">Tika</a>"));
        // Paragraphs with other styles
        assertTrue(xml.contains("<p class=\"signature\">This one"));
        
        // Try with a document that contains images
        xml = getXML("/test-documents/testWORD_3imgs.doc").xml;

        // Images 1-3
        assertTrue("Image not found in:\n"+xml, xml.contains("src=\"embedded:image1.png\""));
        assertTrue("Image not found in:\n"+xml, xml.contains("src=\"embedded:image2.jpg\""));
        assertTrue("Image not found in:\n"+xml, xml.contains("src=\"embedded:image3.png\""));
            
        // Text too
        assertTrue(xml.contains("<p>The end!"));

        // TIKA-692: test document containing multiple
        // character runs within a bold tag:
        xml = getXML("/test-documents/testWORD_bold_character_runs.doc").xml;

        // Make sure bold text arrived as single
        // contiguous string even though Word parser
        // handled this as 3 character runs
        assertTrue("Bold text wasn't contiguous: "+xml, xml.contains("F<b>oob</b>a<b>r</b>"));

        // TIKA-692: test document containing multiple
        // character runs within a bold tag:
        xml = getXML("/test-documents/testWORD_bold_character_runs2.doc").xml;
            
        // Make sure bold text arrived as single
        // contiguous string even though Word parser
        // handled this as 3 character runs
        assertTrue("Bold text wasn't contiguous: "+xml, xml.contains("F<b>oob</b>a<b>r</b>"));
    }

    public void testWord6Parser() throws Exception {
        InputStream input = WordParserTest.class.getResourceAsStream(
                "/test-documents/testWORD6.doc");
        try {
            ContentHandler handler = new BodyContentHandler();
            Metadata metadata = new Metadata();
            new OfficeParser().parse(input, handler, metadata, new ParseContext());

            assertEquals(
                    "application/msword",
                    metadata.get(Metadata.CONTENT_TYPE));
            assertEquals("The quick brown fox jumps over the lazy dog", metadata.get(Metadata.TITLE));
            assertEquals("Gym class featuring a brown fox and lazy dog", metadata.get(Metadata.SUBJECT));
            assertEquals("Nevin Nollop", metadata.get(Metadata.AUTHOR));
            assertTrue(handler.toString().contains("The quick brown fox jumps over the lazy dog"));
        } finally {
            input.close();
        }
    }
}
