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
package org.apache.tika.parser.odf;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.InputStream;
import java.util.List;

import org.apache.tika.TikaTest;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Office;
import org.apache.tika.metadata.OfficeOpenXMLCore;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.EmptyParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.opendocument.OpenOfficeParser;
import org.apache.tika.sax.BodyContentHandler;
import org.junit.Test;
import org.xml.sax.ContentHandler;

public class ODFParserTest extends TikaTest {
    /**
     * For now, allow us to run some tests against both
     *  the old and the new parser
     */
    private Parser[] getParsers() {
       return new Parser[] {
             new OpenDocumentParser(),
             new OpenOfficeParser()
       };
    }

    @Test
    public void testOO3() throws Exception {
       for (Parser parser : getParsers()) {
           try (InputStream input = ODFParserTest.class.getResourceAsStream(
                   "/test-documents/testODFwithOOo3.odt")) {
               Metadata metadata = new Metadata();
               ContentHandler handler = new BodyContentHandler();
               parser.parse(input, handler, metadata, new ParseContext());

               assertEquals(
                       "application/vnd.oasis.opendocument.text",
                       metadata.get(Metadata.CONTENT_TYPE));

               String content = handler.toString();
               assertContains("Tika is part of the Lucene project.", content);
               assertContains("Solr", content);
               assertContains("one embedded", content);
               assertContains("Rectangle Title", content);
               assertContains("a blue background and dark border", content);
           }
       }
    }

    @Test
    public void testOO2() throws Exception {
       for (Parser parser : getParsers()) {
           try (InputStream input = ODFParserTest.class.getResourceAsStream(
                   "/test-documents/testOpenOffice2.odt")) {
               Metadata metadata = new Metadata();
               ContentHandler handler = new BodyContentHandler();
               parser.parse(input, handler, metadata, new ParseContext());

               assertEquals(
                       "application/vnd.oasis.opendocument.text",
                       metadata.get(Metadata.CONTENT_TYPE));
               assertEquals("en-US", metadata.get(Metadata.LANGUAGE));
               assertEquals("PT1M7S", metadata.get(Metadata.EDIT_TIME));
               assertEquals(
                       "NeoOffice/2.2$Unix OpenOffice.org_project/680m18$Build-9161",
                       metadata.get("generator"));

               // Check date metadata, both old-style and new-style
               assertEquals("2007-09-14T11:07:10", metadata.get(TikaCoreProperties.MODIFIED));
               assertEquals("2007-09-14T11:07:10", metadata.get(Metadata.MODIFIED));
               assertEquals("2007-09-14T11:07:10", metadata.get(Metadata.DATE));
               assertEquals("2007-09-14T11:06:08", metadata.get(TikaCoreProperties.CREATED));
               assertEquals("2007-09-14T11:06:08", metadata.get(Metadata.CREATION_DATE));

               // Check the document statistics
               assertEquals("1", metadata.get(Office.PAGE_COUNT));
               assertEquals("1", metadata.get(Office.PARAGRAPH_COUNT));
               assertEquals("14", metadata.get(Office.WORD_COUNT));
               assertEquals("78", metadata.get(Office.CHARACTER_COUNT));
               assertEquals("0", metadata.get(Office.TABLE_COUNT));
               assertEquals("0", metadata.get(Office.OBJECT_COUNT));
               assertEquals("0", metadata.get(Office.IMAGE_COUNT));

               // Check the Tika-1.0 style document statistics
               assertEquals("1", metadata.get(Metadata.PAGE_COUNT));
               assertEquals("1", metadata.get(Metadata.PARAGRAPH_COUNT));
               assertEquals("14", metadata.get(Metadata.WORD_COUNT));
               assertEquals("78", metadata.get(Metadata.CHARACTER_COUNT));
               assertEquals("0", metadata.get(Metadata.TABLE_COUNT));
               assertEquals("0", metadata.get(Metadata.OBJECT_COUNT));
               assertEquals("0", metadata.get(Metadata.IMAGE_COUNT));

               // Check the very old style statistics (these will be removed shortly)
               assertEquals("0", metadata.get("nbTab"));
               assertEquals("0", metadata.get("nbObject"));
               assertEquals("0", metadata.get("nbImg"));
               assertEquals("1", metadata.get("nbPage"));
               assertEquals("1", metadata.get("nbPara"));
               assertEquals("14", metadata.get("nbWord"));
               assertEquals("78", metadata.get("nbCharacter"));

               // Custom metadata tags present but without values
               assertEquals(null, metadata.get("custom:Info 1"));
               assertEquals(null, metadata.get("custom:Info 2"));
               assertEquals(null, metadata.get("custom:Info 3"));
               assertEquals(null, metadata.get("custom:Info 4"));

               String content = handler.toString();
               assertTrue(content.contains(
                       "This is a sample Open Office document,"
                               + " written in NeoOffice 2.2.1 for the Mac."));
           }
       }
   }

   /**
    * Similar to {@link #testOO2()}, but using a different
    *  OO2 file with different metadata in it
    */
    @Test
    public void testOO2Metadata() throws Exception {
        try (InputStream input = ODFParserTest.class.getResourceAsStream(
                "/test-documents/testOpenOffice2.odf")) {
            Metadata metadata = new Metadata();
            ContentHandler handler = new BodyContentHandler();
            new OpenDocumentParser().parse(input, handler, metadata);

            assertEquals(
                    "application/vnd.oasis.opendocument.formula",
                    metadata.get(Metadata.CONTENT_TYPE));
            assertEquals(null, metadata.get(TikaCoreProperties.MODIFIED));
            assertEquals("2006-01-27T11:55:22", metadata.get(Metadata.CREATION_DATE));
            assertEquals("The quick brown fox jumps over the lazy dog",
                    metadata.get(TikaCoreProperties.TITLE));
            assertEquals("Gym class featuring a brown fox and lazy dog",
                    metadata.get(TikaCoreProperties.DESCRIPTION));
            assertEquals("Gym class featuring a brown fox and lazy dog",
                    metadata.get(OfficeOpenXMLCore.SUBJECT));
            assertEquals("Gym class featuring a brown fox and lazy dog",
                    metadata.get(Metadata.SUBJECT));
            assertEquals("PT0S", metadata.get(Metadata.EDIT_TIME));
            assertEquals("1", metadata.get("editing-cycles"));
            assertEquals(
                    "OpenOffice.org/2.2$Win32 OpenOffice.org_project/680m14$Build-9134",
                    metadata.get("generator"));
            assertEquals("Pangram, fox, dog", metadata.get(Metadata.KEYWORDS));

            // User defined metadata
            assertEquals("Text 1", metadata.get("custom:Info 1"));
            assertEquals("2", metadata.get("custom:Info 2"));
            assertEquals("false", metadata.get("custom:Info 3"));
            assertEquals("true", metadata.get("custom:Info 4"));

            // No statistics present
            assertEquals(null, metadata.get(Metadata.PAGE_COUNT));
            assertEquals(null, metadata.get(Metadata.PARAGRAPH_COUNT));
            assertEquals(null, metadata.get(Metadata.WORD_COUNT));
            assertEquals(null, metadata.get(Metadata.CHARACTER_COUNT));
            assertEquals(null, metadata.get(Metadata.TABLE_COUNT));
            assertEquals(null, metadata.get(Metadata.OBJECT_COUNT));
            assertEquals(null, metadata.get(Metadata.IMAGE_COUNT));
            assertEquals(null, metadata.get("nbTab"));
            assertEquals(null, metadata.get("nbObject"));
            assertEquals(null, metadata.get("nbImg"));
            assertEquals(null, metadata.get("nbPage"));
            assertEquals(null, metadata.get("nbPara"));
            assertEquals(null, metadata.get("nbWord"));
            assertEquals(null, metadata.get("nbCharacter"));

            // Note - contents of maths files not currently supported
            String content = handler.toString().trim();
            assertEquals("", content);
        }
   }

   /**
    * Similar to {@link #testOO2()} )}, but using an OO3 file
    */
    @Test
   public void testOO3Metadata() throws Exception {
        try (InputStream input = ODFParserTest.class.getResourceAsStream(
                "/test-documents/testODFwithOOo3.odt")) {
            Metadata metadata = new Metadata();
            ContentHandler handler = new BodyContentHandler();
            new OpenDocumentParser().parse(input, handler, metadata);

            assertEquals(
                    "application/vnd.oasis.opendocument.text",
                    metadata.get(Metadata.CONTENT_TYPE));
            assertEquals("2009-10-05T21:22:38", metadata.get(TikaCoreProperties.MODIFIED));
            assertEquals("2009-10-05T19:04:01", metadata.get(TikaCoreProperties.CREATED));
            assertEquals("2009-10-05T19:04:01", metadata.get(Metadata.CREATION_DATE));
            assertEquals("Apache Tika", metadata.get(TikaCoreProperties.TITLE));
            assertEquals("Test document", metadata.get(OfficeOpenXMLCore.SUBJECT));
            assertEquals("Test document", metadata.get(Metadata.SUBJECT));
            assertEquals("A rather complex document", metadata.get(TikaCoreProperties.DESCRIPTION));
            assertEquals("Bart Hanssens", metadata.get(TikaCoreProperties.CREATOR));
            assertEquals("Bart Hanssens", metadata.get("initial-creator"));
            assertEquals("2", metadata.get("editing-cycles"));
            assertEquals("PT02H03M24S", metadata.get(Metadata.EDIT_TIME));
            assertEquals(
                    "OpenOffice.org/3.1$Unix OpenOffice.org_project/310m19$Build-9420",
                    metadata.get("generator"));
            assertEquals("Apache, Lucene, Tika", metadata.get(Metadata.KEYWORDS));

            // User defined metadata
            assertEquals("Bart Hanssens", metadata.get("custom:Editor"));
            assertEquals(null, metadata.get("custom:Info 2"));
            assertEquals(null, metadata.get("custom:Info 3"));
            assertEquals(null, metadata.get("custom:Info 4"));

            // Check the document statistics
            assertEquals("2", metadata.get(Office.PAGE_COUNT));
            assertEquals("13", metadata.get(Office.PARAGRAPH_COUNT));
            assertEquals("54", metadata.get(Office.WORD_COUNT));
            assertEquals("351", metadata.get(Office.CHARACTER_COUNT));
            assertEquals("0", metadata.get(Office.TABLE_COUNT));
            assertEquals("2", metadata.get(Office.OBJECT_COUNT));
            assertEquals("0", metadata.get(Office.IMAGE_COUNT));

            // Check the Tika-1.0 style document statistics
            assertEquals("2", metadata.get(Metadata.PAGE_COUNT));
            assertEquals("13", metadata.get(Metadata.PARAGRAPH_COUNT));
            assertEquals("54", metadata.get(Metadata.WORD_COUNT));
            assertEquals("351", metadata.get(Metadata.CHARACTER_COUNT));
            assertEquals("0", metadata.get(Metadata.TABLE_COUNT));
            assertEquals("2", metadata.get(Metadata.OBJECT_COUNT));
            assertEquals("0", metadata.get(Metadata.IMAGE_COUNT));

            // Check the old style statistics (these will be removed shortly)
            assertEquals("0", metadata.get("nbTab"));
            assertEquals("2", metadata.get("nbObject"));
            assertEquals("0", metadata.get("nbImg"));
            assertEquals("2", metadata.get("nbPage"));
            assertEquals("13", metadata.get("nbPara"));
            assertEquals("54", metadata.get("nbWord"));
            assertEquals("351", metadata.get("nbCharacter"));

            String content = handler.toString();
            assertTrue(content.contains(
                    "Apache Tika Tika is part of the Lucene project."
            ));
        }
   }

    @Test
    public void testODPMasterFooter() throws Exception {
        try (InputStream input = ODFParserTest.class.getResourceAsStream(
                "/test-documents/testMasterFooter.odp")) {
            Metadata metadata = new Metadata();
            ContentHandler handler = new BodyContentHandler();
            new AutoDetectParser().parse(input, handler, metadata);

            String content = handler.toString();
            assertContains("Master footer is here", content);
        }
    }  

    @Test
    public void testODTFooter() throws Exception {
        try (InputStream input = ODFParserTest.class.getResourceAsStream(
                "/test-documents/testFooter.odt")) {
            Metadata metadata = new Metadata();
            ContentHandler handler = new BodyContentHandler();
            new AutoDetectParser().parse(input, handler, metadata);

            String content = handler.toString();
            assertContains("Here is some text...", content);
            assertContains("Here is some text on page 2", content);
            assertContains("Here is footer text", content);
        }
    }  

    @Test
    public void testODSFooter() throws Exception {
        try (InputStream input = ODFParserTest.class.getResourceAsStream(
                "/test-documents/testFooter.ods")) {
            Metadata metadata = new Metadata();
            ContentHandler handler = new BodyContentHandler();
            new AutoDetectParser().parse(input, handler, metadata);

            String content = handler.toString();
            assertContains("Here is a footer in the center area", content);
        }
    }  
    
    @Test
    public void testFromFile() throws Exception {
        try (TikaInputStream tis = TikaInputStream.get(this.getClass().getResource(
                "/test-documents/testODFwithOOo3.odt"))) {
            assertEquals(true, tis.hasFile());
            OpenDocumentParser parser = new OpenDocumentParser();
            Metadata metadata = new Metadata();
            ContentHandler handler = new BodyContentHandler();
            parser.parse(tis, handler, metadata, new ParseContext());

            assertEquals(
                    "application/vnd.oasis.opendocument.text",
                    metadata.get(Metadata.CONTENT_TYPE));

            String content = handler.toString();
            assertContains("Tika is part of the Lucene project.", content);
        }
    }
    
    @Test
    public void testNPEFromFile() throws Exception {
        OpenDocumentParser parser = new OpenDocumentParser();
        try (TikaInputStream tis = TikaInputStream.get(this.getClass().getResource(
                "/test-documents/testNPEOpenDocument.odt"))) {
            Metadata metadata = new Metadata();
            ContentHandler handler = new BodyContentHandler();
            parser.parse(tis, handler, metadata, new ParseContext());

            assertEquals(
                    "application/vnd.oasis.opendocument.text",
                    metadata.get(Metadata.CONTENT_TYPE));

            String content = handler.toString();
            assertContains("primero hay que generar un par de claves", content);
        }
    }

    // TIKA-1063: Test basic style support.
    @Test
    public void testODTStyles() throws Exception {
        String xml = getXML("testStyles.odt").xml;
        assertContains("This <i>is</i> <b>just</b> a <u>test</u>", xml);
        assertContains("<p>And <b>another <i>test</i> is</b> here.</p>", xml);
        assertContains("<ol>\t<li><p>One</p>", xml);
        assertContains("</ol>", xml);
        assertContains("<ul>\t<li><p>First</p>", xml);
        assertContains("</ul>", xml);
    }

    //TIKA-1600: Test that null pointer doesn't break parsing.
    @Test
    public void testNullStylesInODTFooter() throws Exception {
        Parser parser = new OpenDocumentParser();
        try (InputStream input = ODFParserTest.class.getResourceAsStream("/test-documents/testODT-TIKA-6000.odt")) {
            Metadata metadata = new Metadata();
            ContentHandler handler = new BodyContentHandler();
            parser.parse(input, handler, metadata, getNonRecursingParseContext());

            assertEquals("application/vnd.oasis.opendocument.text", metadata.get(Metadata.CONTENT_TYPE));

            String content = handler.toString();

            assertContains("Utilisation de ce document", content);
            assertContains("Copyright and License", content);
            assertContains("Changer la langue", content);
            assertContains("La page d’accueil permet de faire une recherche simple", content);
        }
    }

    @Test  //TIKA-1916
    public void testMissingMeta() throws Exception {
        String xml = getXML("testODTNoMeta.odt").xml;
        assertContains("Test text", xml);
    }

    @Test //TIKA-2242
    public void testParagraphLevelFontStyles() throws Exception {
        String xml = getXML("testODTStyles2.odt", getNonRecursingParseContext()).xml;
        //test text span font-style properties
        assertContains("<p><b>name</b>, advocaat", xml);
        //test paragraph's font-style properties
        assertContains("<p><b>Publicatie Onbekwaamverklaring", xml);
    }

    @Test //TIKA-2242
    public void testAnnotationsAndPDepthGt1() throws Exception {
        //not allowed in html: <p> <annotation> <p> this is an annotation </p> </annotation> </p>
        String xml = getXML("testODTStyles3.odt").xml;
        assertContains("<p><b>WOUTERS Rolf</b><span class=\"annotation\"> Beschermde persoon is overleden </annotation>", xml);
    }

    @Test
    public void testEmbedded() throws Exception {
        List<Metadata> metadataList = getRecursiveMetadata("testODTEmbedded.odt");
        assertEquals(3, metadataList.size());
    }

    private ParseContext getNonRecursingParseContext() {
        ParseContext parseContext = new ParseContext();
        parseContext.set(Parser.class, new EmptyParser());
        return parseContext;
    }
}
