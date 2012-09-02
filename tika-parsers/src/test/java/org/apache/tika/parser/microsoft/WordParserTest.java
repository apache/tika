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
import java.util.Locale;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamResult;

import org.apache.tika.TikaTest;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Office;
import org.apache.tika.metadata.OfficeOpenXMLCore;
import org.apache.tika.metadata.OfficeOpenXMLExtended;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.microsoft.ooxml.OOXMLParserTest;
import org.apache.tika.sax.BodyContentHandler;
import org.xml.sax.ContentHandler;

public class WordParserTest extends TikaTest {

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
            assertEquals("Sample Word Document", metadata.get(TikaCoreProperties.TITLE));
            assertEquals("Keith Bennett", metadata.get(TikaCoreProperties.CREATOR));
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
        assertEquals("Sample Word Document", metadata.get(TikaCoreProperties.TITLE));
        assertEquals("Keith Bennett", metadata.get(TikaCoreProperties.CREATOR));
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

    public void testEmbeddedNames() throws Exception {
        String result = getXML("/test-documents/testWORD_embedded_pdf.doc").xml;

        // Make sure the embedded div comes out after "Here
        // is the pdf file" and before "Bye Bye":
        int i = result.indexOf("Here is the pdf file:");
        assertTrue(i != -1);
        int j = result.indexOf("<div class=\"embedded\" id=\"_1402837031\"/>");
        assertTrue(j != -1);
        int k = result.indexOf("Bye Bye");
        assertTrue(k != -1);

        assertTrue(i < j);
        assertTrue(j < k);
    }

    // TIKA-982
    public void testEmbeddedRTF() throws Exception {
        String result = getXML("/test-documents/testWORD_embedded_rtf.doc").xml;
        assertTrue(result.indexOf("<div class=\"embedded\" id=\"_1404039792\"/>") != -1);
        assertTrue(result.indexOf("_1404039792.rtf") != -1);
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
            assertEquals("The quick brown fox jumps over the lazy dog", metadata.get(TikaCoreProperties.TITLE));
            assertEquals("Gym class featuring a brown fox and lazy dog", metadata.get(OfficeOpenXMLCore.SUBJECT));
            assertEquals("Gym class featuring a brown fox and lazy dog", metadata.get(Metadata.SUBJECT));
            assertEquals("Nevin Nollop", metadata.get(TikaCoreProperties.CREATOR));
            assertEquals("Nevin Nollop", metadata.get(Metadata.AUTHOR));
            assertTrue(handler.toString().contains("The quick brown fox jumps over the lazy dog"));
        } finally {
            input.close();
        }
    }

    public void testVarious() throws Exception {
        ContentHandler handler = new BodyContentHandler();
        Metadata metadata = new Metadata();

        InputStream stream = WordParserTest.class.getResourceAsStream(
                "/test-documents/testWORD_various.doc");
        try {
            new OfficeParser().parse(stream, handler, metadata, new ParseContext());
        } finally {
            stream.close();
        }

        String content = handler.toString();
        //content = content.replaceAll("\\s+"," ");
        assertContains("Footnote appears here", content);
        assertContains("This is a footnote.", content);
        assertContains("This is the header text.", content);
        assertContains("This is the footer text.", content);
        assertContains("Here is a text box", content);
        assertContains("Bold", content);
        assertContains("italic", content);
        assertContains("underline", content);
        assertContains("superscript", content);
        assertContains("subscript", content);
        assertContains("Here is a citation:", content);
        assertContains("Figure 1 This is a caption for Figure 1", content);
        assertContains("(Kramer)", content);
        assertContains("Row 1 Col 1 Row 1 Col 2 Row 1 Col 3 Row 2 Col 1 Row 2 Col 2 Row 2 Col 3", content.replaceAll("\\s+"," "));
        assertContains("Row 1 column 1 Row 2 column 1 Row 1 column 2 Row 2 column 2", content.replaceAll("\\s+"," "));
        assertContains("This is a hyperlink", content);
        assertContains("Here is a list:", content);
        for(int row=1;row<=3;row++) {
            //assertContains("·\tBullet " + row, content);
            //assertContains("\u00b7\tBullet " + row, content);
            assertContains("Bullet " + row, content);
        }
        assertContains("Here is a numbered list:", content);
        for(int row=1;row<=3;row++) {
            //assertContains(row + ")\tNumber bullet " + row, content);
            //assertContains(row + ") Number bullet " + row, content);
            // TODO: WordExtractor fails to number the bullets:
            assertContains("Number bullet " + row, content);
        }

        for(int row=1;row<=2;row++) {
            for(int col=1;col<=3;col++) {
                assertContains("Row " + row + " Col " + col, content);
            }
        }

        assertContains("Keyword1 Keyword2", content);
        assertEquals("Keyword1 Keyword2",
                     metadata.get(TikaCoreProperties.KEYWORDS));

        assertContains("Subject is here", content);
        // TODO: Move to OO subject in Tika 2.0
        assertEquals("Subject is here",
                     metadata.get(Metadata.SUBJECT));
        assertEquals("Subject is here",
                     metadata.get(OfficeOpenXMLCore.SUBJECT));

        assertContains("Suddenly some Japanese text:", content);
        // Special version of (GHQ)
        assertContains("\uff08\uff27\uff28\uff31\uff09", content);
        // 6 other characters
        assertContains("\u30be\u30eb\u30b2\u3068\u5c3e\u5d0e\u3001\u6de1\u3005\u3068\u6700\u671f", content);

        assertContains("And then some Gothic text:", content);
        assertContains("\uD800\uDF32\uD800\uDF3f\uD800\uDF44\uD800\uDF39\uD800\uDF43\uD800\uDF3A", content);
    }
    
    /**
     * Ensures that custom OLE2 (HPSF) properties are extracted
     */
    public void testCustomProperties() throws Exception {
       InputStream input = WordParserTest.class.getResourceAsStream(
             "/test-documents/testWORD_custom_props.doc");
       Metadata metadata = new Metadata();
       
       try {
          ContentHandler handler = new BodyContentHandler(-1);
          ParseContext context = new ParseContext();
          context.set(Locale.class, Locale.US);
          new OfficeParser().parse(input, handler, metadata, context);
       } finally {
          input.close();
       }
       
       assertEquals("application/msword",   metadata.get(Metadata.CONTENT_TYPE));
       assertEquals("EJ04325S",             metadata.get(TikaCoreProperties.CREATOR));
       assertEquals("Etienne Jouvin",       metadata.get(TikaCoreProperties.MODIFIER));
       assertEquals("Etienne Jouvin",       metadata.get(Metadata.LAST_AUTHOR));
       assertEquals("2012-01-03T22:14:00Z", metadata.get(TikaCoreProperties.MODIFIED));
       assertEquals("2012-01-03T22:14:00Z", metadata.get(Metadata.DATE));
       assertEquals("2010-10-05T09:03:00Z", metadata.get(TikaCoreProperties.CREATED));
       assertEquals("2010-10-05T09:03:00Z", metadata.get(Metadata.CREATION_DATE));
       assertEquals("Microsoft Office Word",metadata.get(OfficeOpenXMLExtended.APPLICATION));
       assertEquals("1",                    metadata.get(Office.PAGE_COUNT));
       assertEquals("2",                    metadata.get(Office.WORD_COUNT));
       assertEquals("My Title",             metadata.get(TikaCoreProperties.TITLE));
       assertEquals("My Keyword",           metadata.get(TikaCoreProperties.KEYWORDS));
       assertEquals("Normal.dotm",          metadata.get(OfficeOpenXMLExtended.TEMPLATE));
       assertEquals("My Comments",          metadata.get(TikaCoreProperties.COMMENTS));
       // TODO: Move to OO subject in Tika 2.0
       assertEquals("My subject",           metadata.get(Metadata.SUBJECT));
       assertEquals("My subject",           metadata.get(OfficeOpenXMLCore.SUBJECT));
       assertEquals("EDF-DIT",              metadata.get(OfficeOpenXMLExtended.COMPANY));
       assertEquals("MyStringValue",        metadata.get("custom:MyCustomString"));
       assertEquals("2010-12-30T23:00:00Z", metadata.get("custom:MyCustomDate"));
    }
}
