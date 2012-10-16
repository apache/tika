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
import java.io.StringWriter;
import java.util.Locale;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamResult;

import org.apache.tika.TikaTest;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Office;
import org.apache.tika.metadata.OfficeOpenXMLCore;
import org.apache.tika.metadata.OfficeOpenXMLExtended;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.metadata.TikaMetadataKeys;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.xml.sax.ContentHandler;

public class OOXMLParserTest extends TikaTest {

    private Parser parser = new AutoDetectParser();

    private InputStream getTestDocument(String name) {
        return TikaInputStream.get(OOXMLParserTest.class.getResourceAsStream(
                "/test-documents/" + name));
    }

    public void testExcel() throws Exception {
        Metadata metadata = new Metadata(); 
        ContentHandler handler = new BodyContentHandler();
        ParseContext context = new ParseContext();
        context.set(Locale.class, Locale.US);

        InputStream input = getTestDocument("testEXCEL.xlsx");
        try {
            parser.parse(input, handler, metadata, context);

            assertEquals(
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    metadata.get(Metadata.CONTENT_TYPE));
            assertEquals("Simple Excel document", metadata.get(TikaCoreProperties.TITLE));
            assertEquals("Keith Bennett", metadata.get(TikaCoreProperties.CREATOR));
            assertEquals("Keith Bennett", metadata.get(Metadata.AUTHOR));
            String content = handler.toString();
            assertTrue(content.contains("Sample Excel Worksheet"));
            assertTrue(content.contains("Numbers and their Squares"));
            assertTrue(content.contains("9"));
            assertFalse(content.contains("9.0"));
            assertTrue(content.contains("196"));
            assertFalse(content.contains("196.0"));
            assertEquals("false", metadata.get(TikaMetadataKeys.PROTECTED));
        } finally {
            input.close();
        }
    }

    public void testExcelFormats() throws Exception {
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler();
        ParseContext context = new ParseContext();
        context.set(Locale.class, Locale.US);

        InputStream input = getTestDocument("testEXCEL-formats.xlsx");
        try {
            parser.parse(input, handler, metadata, context);

            assertEquals(
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    metadata.get(Metadata.CONTENT_TYPE));

            String content = handler.toString();

            // Number #,##0.00
            assertTrue(content.contains("1,599.99"));
            assertTrue(content.contains("-1,599.99"));

            // Currency $#,##0.00;[Red]($#,##0.00)
            assertTrue(content.contains("$1,599.99"));
            assertTrue(content.contains("$1,599.99)"));

          // Scientific 0.00E+00
          // poi <=3.8beta1 returns 1.98E08, newer versions return 1.98+E08
          assertTrue(content.contains("1.98E08") || content.contains("1.98E+08"));
          assertTrue(content.contains("-1.98E08") || content.contains("-1.98E+08"));

            // Percentage
            assertTrue(content.contains("2.50%"));
            // Excel rounds up to 3%, but that requires Java 1.6 or later
            if(System.getProperty("java.version").startsWith("1.5")) {
                assertTrue(content.contains("2%"));
            } else {
                assertTrue(content.contains("3%"));
            }

            // Time Format: h:mm
            assertTrue(content.contains("6:15"));
            assertTrue(content.contains("18:15"));

            // Date Format: d-mmm-yy
            assertTrue(content.contains("17-May-07"));

            // Currency $#,##0.00;[Red]($#,##0.00)
            assertTrue(content.contains("$1,599.99"));
            assertTrue(content.contains("($1,599.99)"));
            
            // Below assertions represent outstanding formatting issues to be addressed
            // they are included to allow the issues to be progressed with the Apache POI
            // team - See TIKA-103.

            /*************************************************************************
            // Date Format: m/d/yy
            assertTrue(content.contains("03/10/2009"));

            // Date/Time Format
            assertTrue(content.contains("19/01/2008 04:35"));

            // Custom Number (0 "dollars and" .00 "cents")
            assertTrue(content.contains("19 dollars and .99 cents"));

            // Custom Number ("At" h:mm AM/PM "on" dddd mmmm d"," yyyy)
            assertTrue(content.contains("At 4:20 AM on Thursday May 17, 2007"));

            // Fraction (2.5): # ?/?
            assertTrue(content.contains("2 1 / 2"));
            **************************************************************************/
        } finally {
            input.close();
        }
    }

    /**
     * We have a number of different powerpoint files,
     *  such as presentation, macro-enabled etc
     */
    public void testPowerPoint() throws Exception {
       String[] extensions = new String[] {
             "pptx", "pptm", "ppsm", "ppsx", "potm"
             //"thmx", // TIKA-418: Will be supported in POI 3.7 beta 2 
             //"xps" // TIKA-418: Not yet supported by POI
       };

        String[] mimeTypes = new String[] {
                "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                "application/vnd.ms-powerpoint.presentation.macroenabled.12",
                "application/vnd.ms-powerpoint.slideshow.macroenabled.12",
                "application/vnd.openxmlformats-officedocument.presentationml.slideshow",
                "application/vnd.ms-powerpoint.template.macroenabled.12"
        };

        for (int i=0; i<extensions.length; i++) {
            String extension = extensions[i];
            String filename = "testPPT." + extension;

            Parser parser = new AutoDetectParser();
            Metadata metadata = new Metadata();
            // TODO: should auto-detect without the resource name
            metadata.set(Metadata.RESOURCE_NAME_KEY, filename);
            ContentHandler handler = new BodyContentHandler();
            ParseContext context = new ParseContext();
    
            InputStream input = getTestDocument(filename);
            try {
                parser.parse(input, handler, metadata, context);
    
                assertEquals(
                        "Mime-type checking for " + filename,
                        mimeTypes[i],
                        metadata.get(Metadata.CONTENT_TYPE));
                assertEquals("Attachment Test", metadata.get(TikaCoreProperties.TITLE));
                assertEquals("Rajiv", metadata.get(TikaCoreProperties.CREATOR));
                assertEquals("Rajiv", metadata.get(Metadata.AUTHOR));
                
                String content = handler.toString();
                // Theme files don't have the text in them
                if(extension.equals("thmx")) {
                    assertEquals("", content);
                } else {
                    assertTrue(
                    	"Text missing for " + filename + "\n" + content, 
                    	content.contains("Attachment Test")
                    );
                    assertTrue(
                    	"Text missing for " + filename + "\n" + content, 
                    	content.contains("This is a test file data with the same content")
                    );
                    assertTrue(
                    	"Text missing for " + filename + "\n" + content, 
                    	content.contains("content parsing")
                    );
                    assertTrue(
                    	"Text missing for " + filename + "\n" + content, 
                    	content.contains("Different words to test against")
                    );
                    assertTrue(
                    	"Text missing for " + filename + "\n" + content, 
                    	content.contains("Mystery")
                    );
                }
            } finally {
                input.close();
            }
        }
    }
    
    /**
     * For the PowerPoint formats we don't currently support, ensure that
     *  we don't break either
     */
    public void testUnsupportedPowerPoint() throws Exception {
       String[] extensions = new String[] { "xps", "thmx" };
       String[] mimeTypes = new String[] {
             "application/vnd.ms-xpsdocument",
             "application/vnd.openxmlformats-officedocument" // Is this right?
       };
       
       for (int i=0; i<extensions.length; i++) {
          String extension = extensions[i];
          String filename = "testPPT." + extension;

          Parser parser = new AutoDetectParser();
          Metadata metadata = new Metadata();
          metadata.set(Metadata.RESOURCE_NAME_KEY, filename);
          ContentHandler handler = new BodyContentHandler();
          ParseContext context = new ParseContext();
  
          InputStream input = getTestDocument(filename);
          try {
              parser.parse(input, handler, metadata, context);

              // Should get the metadata
              assertEquals(
                    "Mime-type checking for " + filename,
                    mimeTypes[i],
                    metadata.get(Metadata.CONTENT_TYPE));

              // But that's about it
          } finally {
             input.close();
         }
       }
    }
    
    /**
     * Test the plain text output of the Word converter
     * @throws Exception
     */
    public void testWord() throws Exception {
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler();
        ParseContext context = new ParseContext();

        InputStream input = getTestDocument("testWORD.docx");
        try {
            parser.parse(input, handler, metadata, context);
            assertEquals(
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    metadata.get(Metadata.CONTENT_TYPE));
            assertEquals("Sample Word Document", metadata.get(TikaCoreProperties.TITLE));
            assertEquals("Keith Bennett", metadata.get(TikaCoreProperties.CREATOR));
            assertEquals("Keith Bennett", metadata.get(Metadata.AUTHOR));
            assertTrue(handler.toString().contains("Sample Word Document"));
        } finally {
            input.close();
        }
    }

    /**
     * Test the plain text output of the Word converter
     * @throws Exception
     */
    public void testWordFootnote() throws Exception {
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler();
        ParseContext context = new ParseContext();

        InputStream input = getTestDocument("footnotes.docx");
        try {
            parser.parse(input, handler, metadata, context);
            assertEquals(
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    metadata.get(Metadata.CONTENT_TYPE));
            assertTrue(handler.toString().contains("snoska"));
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

    private XMLResult getXML(String name) throws Exception {
        StringWriter sw = new StringWriter();
        SAXTransformerFactory factory = (SAXTransformerFactory)
                 SAXTransformerFactory.newInstance();
        TransformerHandler handler = factory.newTransformerHandler();
        handler.getTransformer().setOutputProperty(OutputKeys.METHOD, "xml");
        handler.getTransformer().setOutputProperty(OutputKeys.INDENT, "yes");
        handler.setResult(new StreamResult(sw));

        // Try with a document containing various tables and formattings
        InputStream input = getTestDocument(name);
        try {
            Metadata metadata = new Metadata();
            parser.parse(input, handler, metadata, new ParseContext());
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

      XMLResult result = getXML("testWORD.docx");
      String xml = result.xml;
      Metadata metadata = result.metadata;
      assertEquals(
                   "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                   metadata.get(Metadata.CONTENT_TYPE));
      assertEquals("Sample Word Document", metadata.get(TikaCoreProperties.TITLE));
      assertEquals("Keith Bennett", metadata.get(TikaCoreProperties.CREATOR));
      assertEquals("Keith Bennett", metadata.get(Metadata.AUTHOR));
      assertTrue(xml.contains("Sample Word Document"));
            
      // Check that custom headings came through
      assertTrue(xml.contains("<h1 class=\"title\">"));
      // Regular headings
      assertTrue(xml.contains("<h1>Heading Level 1</h1>"));
      assertTrue(xml.contains("<h2>Heading Level 2</h2>"));
      // Headings with anchor tags in them
      assertTrue(xml.replaceAll("\r?\n", "").contains("<h3><a name=\"OnLevel3\"/>Heading Level 3</h3>"));
      // Bold and italic
      assertTrue(xml.contains("<b>BOLD</b>"));
      assertTrue(xml.contains("<i>ITALIC</i>"));
      // Table
      assertTrue(xml.contains("<table>"));
      assertTrue(xml.contains("<td>"));
      // Links
      assertTrue(xml.contains("<a href=\"http://tika.apache.org/\">Tika</a>"));
      // Anchor links
      assertTrue(xml.contains("<a href=\"#OnMainHeading\">The Main Heading Bookmark</a>"));
      // Paragraphs with other styles
      assertTrue(xml.contains("<p class=\"signature\">This one"));

      result = getXML("testWORD_3imgs.docx");
      xml = result.xml;

      // Images 2-4 (there is no 1!)
      assertTrue("Image not found in:\n"+xml, xml.contains("<img src=\"embedded:image2.png\" alt=\"A description...\"/>"));
      assertTrue("Image not found in:\n"+xml, xml.contains("<img src=\"embedded:image3.jpeg\" alt=\"A description...\"/>"));
      assertTrue("Image not found in:\n"+xml, xml.contains("<img src=\"embedded:image4.png\" alt=\"A description...\"/>"));
            
      // Text too
      assertTrue(xml.contains("<p>The end!</p>"));

      // TIKA-692: test document containing multiple
      // character runs within a bold tag:
      xml = getXML("testWORD_bold_character_runs.docx").xml;

      // Make sure bold text arrived as single
      // contiguous string even though Word parser
      // handled this as 3 character runs
      assertTrue("Bold text wasn't contiguous: "+xml, xml.contains("F<b>oob</b>a<b>r</b>"));

      // TIKA-692: test document containing multiple
      // character runs within a bold tag:
      xml = getXML("testWORD_bold_character_runs2.docx").xml;
            
      // Make sure bold text arrived as single
      // contiguous string even though Word parser
      // handled this as 3 character runs
      assertTrue("Bold text wasn't contiguous: "+xml, xml.contains("F<b>oob</b>a<b>r</b>"));
    }

    /**
     * Test that we can extract image from docx header
     */
    public void testWordPicturesInHeader() throws Exception {
        Metadata metadata = new Metadata();
        ParseContext context = new ParseContext();

        StringWriter sw = new StringWriter();
        SAXTransformerFactory factory = (SAXTransformerFactory)
                 SAXTransformerFactory.newInstance();
        TransformerHandler handler = factory.newTransformerHandler();
        handler.getTransformer().setOutputProperty(OutputKeys.METHOD, "xml");
        handler.getTransformer().setOutputProperty(OutputKeys.INDENT, "yes");
        handler.setResult(new StreamResult(sw));

        // Try with a document containing various tables and formattings
        InputStream input = getTestDocument("headerPic.docx");
        try {
            parser.parse(input, handler, metadata, context);
            String xml = sw.toString();
            assertEquals(
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    metadata.get(Metadata.CONTENT_TYPE));
            // Check that custom headings came through
            assertTrue(xml.contains("<img"));
        } finally {
            input.close();
        }
    }

    /**
     * Documents with some sheets are protected, but not all. 
     * See TIKA-364.
     */
    public void testProtectedExcelSheets() throws Exception {
        InputStream input = OOXMLParserTest.class
                .getResourceAsStream("/test-documents/protectedSheets.xlsx");

        Parser parser = new AutoDetectParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler();
        ParseContext context = new ParseContext();

        try {
            parser.parse(input, handler, metadata, context);

            assertEquals(
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    metadata.get(Metadata.CONTENT_TYPE));

            assertEquals("true", metadata.get(TikaMetadataKeys.PROTECTED));
        } finally {
            input.close();
        }
    }

    /**
     * An excel document which is password protected. 
     * See TIKA-437.
     */
    public void testProtectedExcelFile() throws Exception {

        Parser parser = new AutoDetectParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler();
        ParseContext context = new ParseContext();

        InputStream input = getTestDocument("protectedFile.xlsx");
        try {
            parser.parse(input, handler, metadata, context);

            assertEquals(
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    metadata.get(Metadata.CONTENT_TYPE));

            assertEquals("true", metadata.get(TikaMetadataKeys.PROTECTED));
            
            String content = handler.toString();
            assertTrue(content.contains("Office"));
        } finally {
            input.close();
        }
    }

    /**
     * Test docx without headers
     * TIKA-633
     */
    public void testNullHeaders() throws Exception {
        Parser parser = new AutoDetectParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler();
        ParseContext context = new ParseContext();

        InputStream input = getTestDocument("NullHeader.docx");
        try {
            parser.parse(input, handler, metadata, context);
            assertFalse(handler.toString().length()==0);
        } finally {
            input.close();
        }
    }

    public void testVarious() throws Exception {
        ContentHandler handler = new BodyContentHandler();
        Metadata metadata = new Metadata();

        InputStream stream = OOXMLParserTest.class.getResourceAsStream(
                "/test-documents/testWORD_various.docx");
        try {
            new AutoDetectParser().parse(stream, handler, metadata, new ParseContext());
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
            // TODO: OOXMLExtractor fails to number the bullets:
            assertContains("Number bullet " + row, content);
        }

        for(int row=1;row<=2;row++) {
            for(int col=1;col<=3;col++) {
                assertContains("Row " + row + " Col " + col, content);
            }
        }

        assertContains("Keyword1 Keyword2", content);
        assertEquals("Keyword1 Keyword2",
                     metadata.get(Metadata.KEYWORDS));

        assertContains("Subject is here", content);
        // TODO: Remove subject in Tika 2.0
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

    public void testVariousPPTX() throws Exception {
        ContentHandler handler = new BodyContentHandler();
        Metadata metadata = new Metadata();

        InputStream stream = OOXMLParserTest.class.getResourceAsStream(
                "/test-documents/testPPT_various.pptx");
        try {
            new AutoDetectParser().parse(stream, handler, metadata, new ParseContext());
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
            // TODO: OOXMLExtractor fails to number the bullets:
            assertContains("Number bullet " + row, content);
        }

        for(int row=1;row<=2;row++) {
            for(int col=1;col<=3;col++) {
                assertContains("Row " + row + " Col " + col, content);
            }
        }

        assertContains("Keyword1 Keyword2", content);
        assertEquals("Keyword1 Keyword2",
                     metadata.get(Metadata.KEYWORDS));

        assertContains("Subject is here", content);
        // TODO: Remove subject in Tika 2.0
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


    public void testMasterFooter() throws Exception {
        ContentHandler handler = new BodyContentHandler();
        Metadata metadata = new Metadata();

        InputStream stream = OOXMLParserTest.class.getResourceAsStream(
                "/test-documents/testPPT_masterFooter.pptx");
        try {
            new AutoDetectParser().parse(stream, handler, metadata, new ParseContext());
        } finally {
            stream.close();
        }

        String content = handler.toString();
        assertContains("Master footer is here", content);
    }

    // TODO: once we fix TIKA-712, re-enable this
    /*
    public void testMasterText() throws Exception {
        ContentHandler handler = new BodyContentHandler();
        Metadata metadata = new Metadata();

        InputStream stream = OOXMLParserTest.class.getResourceAsStream(
                "/test-documents/testPPT_masterText.pptx");
        try {
            new AutoDetectParser().parse(stream, handler, metadata, new ParseContext());
        } finally {
            stream.close();
        }

        String content = handler.toString();
        assertContains("Text that I added to the master slide", content);
    }
    */

    // TODO: once we fix TIKA-712, re-enable this
    /*
    public void testMasterText2() throws Exception {
        ContentHandler handler = new BodyContentHandler();
        Metadata metadata = new Metadata();

        InputStream stream = OOXMLParserTest.class.getResourceAsStream(
                "/test-documents/testPPT_masterText2.pptx");
        try {
            new AutoDetectParser().parse(stream, handler, metadata, new ParseContext());
        } finally {
            stream.close();
        }

        String content = handler.toString();
        assertContains("Text that I added to the master slide", content);
    }
    */

    public void testWordArt() throws Exception {
        ContentHandler handler = new BodyContentHandler();
        Metadata metadata = new Metadata();

        InputStream stream = OOXMLParserTest.class.getResourceAsStream(
                "/test-documents/testWordArt.pptx");
        try {
            new AutoDetectParser().parse(stream, handler, metadata, new ParseContext());
        } finally {
            stream.close();
        }
        String content = handler.toString();
        assertContains("Here is some red word Art", content);
    }

    /**
     * Ensures that custom OOXML properties are extracted
     */
    public void testExcelCustomProperties() throws Exception {
       InputStream input = OOXMLParserTest.class.getResourceAsStream(
             "/test-documents/testEXCEL_custom_props.xlsx");
       Metadata metadata = new Metadata();
       
       try {
          ContentHandler handler = new BodyContentHandler(-1);
          ParseContext context = new ParseContext();
          context.set(Locale.class, Locale.US);
          new OOXMLParser().parse(input, handler, metadata, context);
       } finally {
          input.close();
       }
       
       assertEquals(
             "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", 
             metadata.get(Metadata.CONTENT_TYPE));
       assertEquals(null,                   metadata.get(TikaCoreProperties.CREATOR));
       assertEquals(null,                   metadata.get(TikaCoreProperties.MODIFIER));
       assertEquals("2006-09-12T15:06:44Z", metadata.get(TikaCoreProperties.CREATED));
       assertEquals("2006-09-12T15:06:44Z", metadata.get(Metadata.CREATION_DATE));
       assertEquals("2011-08-22T14:24:38Z", metadata.get(Metadata.LAST_MODIFIED));
       assertEquals("2011-08-22T14:24:38Z", metadata.get(TikaCoreProperties.MODIFIED));
       assertEquals("2011-08-22T14:24:38Z", metadata.get(Metadata.DATE));
       assertEquals("Microsoft Excel",      metadata.get(Metadata.APPLICATION_NAME));
       assertEquals("Microsoft Excel",      metadata.get(OfficeOpenXMLExtended.APPLICATION));
       assertEquals("true",                 metadata.get("custom:myCustomBoolean"));
       assertEquals("3",                    metadata.get("custom:myCustomNumber"));
       assertEquals("MyStringValue",        metadata.get("custom:MyCustomString"));
       assertEquals("2010-12-30T22:00:00Z", metadata.get("custom:MyCustomDate"));
       assertEquals("2010-12-29T22:00:00Z", metadata.get("custom:myCustomSecondDate"));
    }
    public void testWordCustomProperties() throws Exception {
       InputStream input = OOXMLParserTest.class.getResourceAsStream(
             "/test-documents/testWORD_custom_props.docx");
       Metadata metadata = new Metadata();

       try {
          ContentHandler handler = new BodyContentHandler(-1);
          ParseContext context = new ParseContext();
          context.set(Locale.class, Locale.US);
          new OOXMLParser().parse(input, handler, metadata, context);
       } finally {
          input.close();
       }

       assertEquals(
             "application/vnd.openxmlformats-officedocument.wordprocessingml.document", 
             metadata.get(Metadata.CONTENT_TYPE));
       assertEquals("EJ04325S",             metadata.get(TikaCoreProperties.CREATOR));
       assertEquals("Etienne Jouvin",       metadata.get(TikaCoreProperties.MODIFIER));
       assertEquals("Etienne Jouvin",       metadata.get(Metadata.LAST_AUTHOR));
       assertEquals("2011-07-29T16:52:00Z", metadata.get(TikaCoreProperties.CREATED));
       assertEquals("2011-07-29T16:52:00Z", metadata.get(Metadata.CREATION_DATE));
       assertEquals("2012-01-03T22:14:00Z", metadata.get(TikaCoreProperties.MODIFIED));
       assertEquals("2012-01-03T22:14:00Z", metadata.get(Metadata.DATE));
       assertEquals("Microsoft Office Word",metadata.get(Metadata.APPLICATION_NAME));
       assertEquals("Microsoft Office Word",metadata.get(OfficeOpenXMLExtended.APPLICATION));
       assertEquals("1",                    metadata.get(Office.PAGE_COUNT));
       assertEquals("2",                    metadata.get(Office.WORD_COUNT));
       assertEquals("My Title",             metadata.get(TikaCoreProperties.TITLE));
       assertEquals("My Keyword",           metadata.get(TikaCoreProperties.KEYWORDS));
       assertEquals("Normal.dotm",          metadata.get(Metadata.TEMPLATE));
       assertEquals("Normal.dotm",          metadata.get(OfficeOpenXMLExtended.TEMPLATE));
       // TODO: Remove subject in Tika 2.0
       assertEquals("My subject",           metadata.get(Metadata.SUBJECT));
       assertEquals("My subject",           metadata.get(OfficeOpenXMLCore.SUBJECT));
       assertEquals("EDF-DIT",              metadata.get(TikaCoreProperties.PUBLISHER));
       assertEquals("true",                 metadata.get("custom:myCustomBoolean"));
       assertEquals("3",                    metadata.get("custom:myCustomNumber"));
       assertEquals("MyStringValue",        metadata.get("custom:MyCustomString"));
       assertEquals("2010-12-30T23:00:00Z", metadata.get("custom:MyCustomDate"));
       assertEquals("2010-12-29T22:00:00Z", metadata.get("custom:myCustomSecondDate"));
    }
    public void testPowerPointCustomProperties() throws Exception {
       InputStream input = OOXMLParserTest.class.getResourceAsStream(
             "/test-documents/testPPT_custom_props.pptx");
       Metadata metadata = new Metadata();

       try {
          ContentHandler handler = new BodyContentHandler(-1);
          ParseContext context = new ParseContext();
          context.set(Locale.class, Locale.US);
          new OOXMLParser().parse(input, handler, metadata, context);
       } finally {
          input.close();
       }

       assertEquals(
             "application/vnd.openxmlformats-officedocument.presentationml.presentation", 
             metadata.get(Metadata.CONTENT_TYPE));
       assertEquals("JOUVIN ETIENNE",       metadata.get(TikaCoreProperties.CREATOR));
       assertEquals("EJ04325S",             metadata.get(TikaCoreProperties.MODIFIER));
       assertEquals("EJ04325S",             metadata.get(Metadata.LAST_AUTHOR));
       assertEquals("2011-08-22T13:30:53Z", metadata.get(TikaCoreProperties.CREATED));
       assertEquals("2011-08-22T13:30:53Z", metadata.get(Metadata.CREATION_DATE));
       assertEquals("2011-08-22T13:32:49Z", metadata.get(TikaCoreProperties.MODIFIED));
       assertEquals("2011-08-22T13:32:49Z", metadata.get(Metadata.DATE));
       assertEquals("1",                    metadata.get(Office.SLIDE_COUNT));
       assertEquals("3",                    metadata.get(Office.WORD_COUNT));
       assertEquals("Test extraction properties pptx", metadata.get(TikaCoreProperties.TITLE));
       assertEquals("true",                 metadata.get("custom:myCustomBoolean"));
       assertEquals("3",                    metadata.get("custom:myCustomNumber"));
       assertEquals("MyStringValue",        metadata.get("custom:MyCustomString"));
       assertEquals("2010-12-30T22:00:00Z", metadata.get("custom:MyCustomDate"));
       assertEquals("2010-12-29T22:00:00Z", metadata.get("custom:myCustomSecondDate"));
    }

    // TIKA-989:
    public void testEmbeddedPDF() throws Exception {
       InputStream input = OOXMLParserTest.class.getResourceAsStream(
             "/test-documents/testWORD_embedded_pdf.docx");
       Metadata metadata = new Metadata();
       StringWriter sw = new StringWriter();
       SAXTransformerFactory factory = (SAXTransformerFactory)
                SAXTransformerFactory.newInstance();
       TransformerHandler handler = factory.newTransformerHandler();
       handler.getTransformer().setOutputProperty(OutputKeys.METHOD, "xml");
       handler.getTransformer().setOutputProperty(OutputKeys.INDENT, "no");
       handler.setResult(new StreamResult(sw));

       try {
          new OOXMLParser().parse(input, handler, metadata, new ParseContext());
       } finally {
          input.close();
       }
       String xml = sw.toString();
       int i = xml.indexOf("Here is the pdf file:");
       int j = xml.indexOf("<div class=\"embedded\" id=\"rId5\"/>");
       int k = xml.indexOf("Bye Bye");
       int l = xml.indexOf("<div class=\"embedded\" id=\"rId6\"/>");
       int m = xml.indexOf("Bye for real.");
       assertTrue(i != -1);
       assertTrue(j != -1);
       assertTrue(k != -1);
       assertTrue(l != -1);
       assertTrue(m != -1);
       assertTrue(i < j);
       assertTrue(j < k);
       assertTrue(k < l);
       assertTrue(l < m);
    }

    // TIKA-997:
    public void testEmbeddedZipInPPTX() throws Exception {
        InputStream input = OOXMLParserTest.class.getResourceAsStream(
              "/test-documents/test_embedded_zip.pptx");
        Metadata metadata = new Metadata();
        StringWriter sw = new StringWriter();
        SAXTransformerFactory factory = (SAXTransformerFactory)
                 SAXTransformerFactory.newInstance();
        TransformerHandler handler = factory.newTransformerHandler();
        handler.getTransformer().setOutputProperty(OutputKeys.METHOD, "xml");
        handler.getTransformer().setOutputProperty(OutputKeys.INDENT, "no");
        handler.setResult(new StreamResult(sw));

        try {
            new OOXMLParser().parse(input, handler, metadata, new ParseContext());
        } finally {
            input.close();
        }
        String xml = sw.toString();
        int h = xml.indexOf("<div class=\"embedded\" id=\"rId3\"/>");
        int i = xml.indexOf("Send me a note");
        int j = xml.indexOf("<div class=\"embedded\" id=\"rId4\"/>");
        int k = xml.indexOf("<p>No title</p>");
        assertTrue(h != -1);
        assertTrue(i != -1);
        assertTrue(j != -1);
        assertTrue(k != -1);
        assertTrue(h < i);
        assertTrue(i < j);
        assertTrue(j < k);
    }
  
    // TIKA-1006
    public void testWordNullStyle() throws Exception {
      String xml = getXML("testWORD_null_style.docx").xml;        
      assertContains("Test av styrt dokument", xml);
    }

    // TIKA-1005:
    public void testTextInsideTextBox() throws Exception {
        String xml = getXML("testWORD_text_box.docx").xml;
        assertContains("This text is directly in the body of the document.", xml);
        assertContains("This text is inside of a text box in the body of the document.", xml);
        assertContains("This text is inside of a text box in the header of the document.", xml);
        assertContains("This text is inside of a text box in the footer of the document.", xml);
    }
}
