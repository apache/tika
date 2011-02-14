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
package org.apache.tika.parser.opendocument;

import java.io.InputStream;

import junit.framework.TestCase;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.sax.BodyContentHandler;
import org.xml.sax.ContentHandler;

public class OpenOfficeParserTest extends TestCase {

    public void testXMLParser() throws Exception {
        InputStream input = OpenOfficeParserTest.class.getResourceAsStream(
                "/test-documents/testOpenOffice2.odt");
        try {
            Metadata metadata = new Metadata();
            ContentHandler handler = new BodyContentHandler();
            new OpenOfficeParser().parse(input, handler, metadata);

            assertEquals(
                    "application/vnd.oasis.opendocument.text",
                    metadata.get(Metadata.CONTENT_TYPE));
            assertEquals("2007-09-14T11:07:10", metadata.get(Metadata.DATE));
            assertEquals("2007-09-14T11:06:08", metadata.get(Metadata.CREATION_DATE));
            assertEquals("en-US", metadata.get(Metadata.LANGUAGE));
            assertEquals("PT1M7S", metadata.get(Metadata.EDIT_TIME));
            assertEquals(
                    "NeoOffice/2.2$Unix OpenOffice.org_project/680m18$Build-9161",
                    metadata.get("generator"));
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

    /**
     * Similar to {@link #testXMLParser()}, but using a different
     *  OO2 file with different metadata in it
     */
    public void testOO2Metadata() throws Exception {
       InputStream input = OpenOfficeParserTest.class.getResourceAsStream(
             "/test-documents/testOpenOffice2.odf");
       try {
            Metadata metadata = new Metadata();
            ContentHandler handler = new BodyContentHandler();
            new OpenOfficeParser().parse(input, handler, metadata);
   
            assertEquals(
                    "application/vnd.oasis.opendocument.formula",
                    metadata.get(Metadata.CONTENT_TYPE));
            assertEquals(null, metadata.get(Metadata.DATE));
            assertEquals("2006-01-27T11:55:22", metadata.get(Metadata.CREATION_DATE));
            assertEquals("The quick brown fox jumps over the lazy dog", metadata.get(Metadata.TITLE));
            assertEquals("Gym class featuring a brown fox and lazy dog", metadata.get(Metadata.SUBJECT));
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
       InputStream input = OpenOfficeParserTest.class.getResourceAsStream(
             "/test-documents/testODFwithOOo3.odt");
       try {
            Metadata metadata = new Metadata();
            ContentHandler handler = new BodyContentHandler();
            new OpenOfficeParser().parse(input, handler, metadata);
   
            assertEquals(
                    "application/vnd.oasis.opendocument.text",
                    metadata.get(Metadata.CONTENT_TYPE));
            assertEquals("2009-10-05T21:22:38", metadata.get(Metadata.DATE));
            assertEquals("2009-10-05T19:04:01", metadata.get(Metadata.CREATION_DATE));
            assertEquals("Apache Tika", metadata.get(Metadata.TITLE));
            assertEquals("Test document", metadata.get(Metadata.SUBJECT));
            assertEquals("A rather complex document", metadata.get(Metadata.DESCRIPTION));
            assertEquals("Bart Hanssens", metadata.get(Metadata.CREATOR));
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
            
            // No statistics present
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
}
