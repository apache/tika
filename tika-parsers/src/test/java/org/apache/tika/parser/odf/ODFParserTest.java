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

import java.io.InputStream;

import org.apache.tika.TikaTest;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Office;
import org.apache.tika.metadata.OfficeOpenXMLCore;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.opendocument.OpenOfficeParser;
import org.apache.tika.sax.BodyContentHandler;
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

    public void testOO3() throws Exception {
       for (Parser parser : getParsers()) {
          InputStream input = ODFParserTest.class.getResourceAsStream(
                "/test-documents/testODFwithOOo3.odt");
          try {
             Metadata metadata = new Metadata();
             ContentHandler handler = new BodyContentHandler();
             parser.parse(input, handler, metadata, new ParseContext());

             assertEquals(
                   "application/vnd.oasis.opendocument.text",
                   metadata.get(Metadata.CONTENT_TYPE));

             String content = handler.toString();
             assertTrue(content.contains("Tika is part of the Lucene project."));
             assertTrue(content.contains("Solr"));
             assertTrue(content.contains("one embedded"));
             assertTrue(content.contains("Rectangle Title"));
             assertTrue(content.contains("a blue background and dark border"));        
          } finally {
             input.close();
          }
       }
    }

    public void testOO2() throws Exception {
       for (Parser parser : getParsers()) {
          InputStream input = ODFParserTest.class.getResourceAsStream(
                 "/test-documents/testOpenOffice2.odt");
          try {
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
          } finally {
             input.close();
          }
       }
   }

   /**
    * Similar to {@link #testXMLParser()}, but using a different
    *  OO2 file with different metadata in it
    */
   public void testOO2Metadata() throws Exception {
      InputStream input = ODFParserTest.class.getResourceAsStream(
            "/test-documents/testOpenOffice2.odf");
      try {
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
           String content = handler.toString();
           assertEquals("", content);
      } finally {
          input.close();
      }
   }

   /**
    * Similar to {@link #testXMLParser()}, but using an OO3 file
    */
   public void testOO3Metadata() throws Exception {
      InputStream input = ODFParserTest.class.getResourceAsStream(
            "/test-documents/testODFwithOOo3.odt");
      try {
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
      } finally {
          input.close();
      }
   }

    public void testODPMasterFooter() throws Exception {
        InputStream input = ODFParserTest.class.getResourceAsStream(
            "/test-documents/testMasterFooter.odp");
        try {
            Metadata metadata = new Metadata();
            ContentHandler handler = new BodyContentHandler();
            new AutoDetectParser().parse(input, handler, metadata);
  
            String content = handler.toString();
            assertContains("Master footer is here", content);
        } finally {
            input.close();
        }
    }  

    public void testODTFooter() throws Exception {
        InputStream input = ODFParserTest.class.getResourceAsStream(
            "/test-documents/testFooter.odt");
        try {
            Metadata metadata = new Metadata();
            ContentHandler handler = new BodyContentHandler();
            new AutoDetectParser().parse(input, handler, metadata);
  
            String content = handler.toString();
            assertContains("Here is some text...", content);
            assertContains("Here is some text on page 2", content);
            assertContains("Here is footer text", content);
        } finally {
            input.close();
        }
    }  

    public void testODSFooter() throws Exception {
        InputStream input = ODFParserTest.class.getResourceAsStream(
            "/test-documents/testFooter.ods");
        try {
            Metadata metadata = new Metadata();
            ContentHandler handler = new BodyContentHandler();
            new AutoDetectParser().parse(input, handler, metadata);
  
            String content = handler.toString();
            assertContains("Here is a footer in the center area", content);
        } finally {
            input.close();
        }
    }  
}
