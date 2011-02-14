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

import junit.framework.TestCase;

import org.apache.tika.config.TikaConfig;
import org.apache.tika.detect.ContainerAwareDetector;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaMetadataKeys;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.xml.sax.ContentHandler;

public class OOXMLParserTest extends TestCase {
    private Parser parser;
   
    @Override
    protected void setUp() throws Exception {
       TikaConfig config = TikaConfig.getDefaultConfig();
       ContainerAwareDetector detector = new ContainerAwareDetector(
             config.getMimeRepository()
       );
       parser = new AutoDetectParser(detector);
    }

   public void testExcel() throws Exception {
        InputStream input = OOXMLParserTest.class
                .getResourceAsStream("/test-documents/testEXCEL.xlsx");
        assertNotNull(input);

        Metadata metadata = new Metadata(); 
        ContentHandler handler = new BodyContentHandler();
        ParseContext context = new ParseContext();
        context.set(Locale.class, Locale.US);

        try {
            parser.parse(TikaInputStream.get(input), handler, metadata, context);

            assertEquals(
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
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
            assertEquals("false", metadata.get(TikaMetadataKeys.PROTECTED));
        } finally {
            input.close();
        }
    }

    public void testExcelFormats() throws Exception {
        InputStream input = OOXMLParserTest.class
                .getResourceAsStream("/test-documents/testEXCEL-formats.xlsx");

        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler();
        ParseContext context = new ParseContext();
        context.set(Locale.class, Locale.US);

        try {
            parser.parse(TikaInputStream.get(input), handler, metadata, context);

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
            assertTrue(content.contains("1.98E08"));
            assertTrue(content.contains("-1.98E08"));

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
		"pptx", "pptm", "ppsm", "ppsx",
		//"thmx", // TIKA-418: Will be supported in POI 3.7 beta 2 
		//"xps" // TIKA-418: Not yet supported by POI
	};
	for(String extension : extensions) {
	    String filename = "testPPT." + extension;
            InputStream input = OOXMLParserTest.class
                    .getResourceAsStream("/test-documents/"+filename);
    
            Parser parser = new AutoDetectParser();
            Metadata metadata = new Metadata();
            // TODO: should auto-detect without the resource name
            metadata.set(Metadata.RESOURCE_NAME_KEY, filename);
            ContentHandler handler = new BodyContentHandler();
            ParseContext context = new ParseContext();
    
            try {
                parser.parse(input, handler, metadata, context);
    
                assertEquals(
                        "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                        metadata.get(Metadata.CONTENT_TYPE));
                assertEquals("Attachment Test", metadata.get(Metadata.TITLE));
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
     * Test the plain text output of the Word converter
     * @throws Exception
     */
    public void testWord() throws Exception {
        InputStream input = OOXMLParserTest.class
                .getResourceAsStream("/test-documents/testWORD.docx");

        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler();
        ParseContext context = new ParseContext();

        try {
            parser.parse(TikaInputStream.get(input), handler, metadata, context);
            assertEquals(
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    metadata.get(Metadata.CONTENT_TYPE));
            assertEquals("Sample Word Document", metadata.get(Metadata.TITLE));
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
        InputStream input = OOXMLParserTest.class
                .getResourceAsStream("/test-documents/footnotes.docx");

        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler();
        ParseContext context = new ParseContext();

        try {
            parser.parse(TikaInputStream.get(input), handler, metadata, context);
            assertEquals(
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    metadata.get(Metadata.CONTENT_TYPE));
            assertTrue(handler.toString().contains("snoska"));
        } finally {
            input.close();
        }
    }

    /**
     * Test that the word converter is able to generate the
     *  correct HTML for the document
     */
    public void testWordHTML() throws Exception {
        InputStream input = null;
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
        input = OOXMLParserTest.class.getResourceAsStream("/test-documents/testWORD.docx");
        try {
            parser.parse(TikaInputStream.get(input), handler, metadata, context);
            String xml = sw.toString();
            assertEquals(
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    metadata.get(Metadata.CONTENT_TYPE));
            assertEquals("Sample Word Document", metadata.get(Metadata.TITLE));
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
        } finally {
            input.close();
        }
        
        // Try with a document that contains images
        sw = new StringWriter();
        handler.setResult(new StreamResult(sw));
        input = OOXMLParserTest.class.getResourceAsStream("/test-documents/testWORD_3imgs.docx");
        try {
            parser.parse(TikaInputStream.get(input), handler, metadata, context);
            String xml = sw.toString();
            
            // Images 2-4 (there is no 1!)
//            assertTrue("Image not found in:\n"+xml, xml.contains("<img src=\"embedded:image2.png\"/>"));
//            assertTrue("Image not found in:\n"+xml, xml.contains("<img src=\"embedded:image3.jpeg\"/>"));
//            assertTrue("Image not found in:\n"+xml, xml.contains("<img src=\"embedded:image4.png\"/>"));
            
            // Text too
            assertTrue(xml.contains("<p>The end!</p>"));
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
        InputStream input = OOXMLParserTest.class
                .getResourceAsStream("/test-documents/protectedFile.xlsx");

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
            
            String content = handler.toString();
            assertTrue(content.contains("Office"));
        } finally {
            input.close();
        }
    }

}