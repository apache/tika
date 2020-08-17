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
package org.apache.tika.parser.pkg;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import javax.crypto.Cipher;

import java.io.InputStream;
import java.security.NoSuchAlgorithmException;

import org.apache.tika.exception.EncryptedDocumentException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.PasswordProvider;
import org.apache.tika.sax.BodyContentHandler;
import org.junit.Test;
import org.xml.sax.ContentHandler;

/**
 * Test case for parsing 7z files.
 */
public class Seven7ParserTest extends AbstractPkgTest {

    /**
     * Tests that the ParseContext parser is correctly
     *  fired for all the embedded entries.
     */
    @Test
    public void testEmbedded() throws Exception {
       ContentHandler handler = new BodyContentHandler();
       Metadata metadata = new Metadata();

        try (InputStream stream = Seven7ParserTest.class.getResourceAsStream(
                "/test-documents/test-documents.7z")) {
            AUTO_DETECT_PARSER.parse(stream, handler, metadata, trackingContext);
        }
       
       // Should have found all 9 documents, but not the directory
       assertEquals(9, tracker.filenames.size());
       assertEquals(9, tracker.mediatypes.size());
       assertEquals(9, tracker.modifiedAts.size());
       
       // Should have names but not content types, as 7z doesn't
       //  store the content types
       assertEquals("test-documents/testEXCEL.xls", tracker.filenames.get(0));
       assertEquals("test-documents/testHTML.html", tracker.filenames.get(1));
       assertEquals("test-documents/testOpenOffice2.odt", tracker.filenames.get(2));
       assertEquals("test-documents/testPDF.pdf", tracker.filenames.get(3));
       assertEquals("test-documents/testPPT.ppt", tracker.filenames.get(4));
       assertEquals("test-documents/testRTF.rtf", tracker.filenames.get(5));
       assertEquals("test-documents/testTXT.txt", tracker.filenames.get(6));
       assertEquals("test-documents/testWORD.doc", tracker.filenames.get(7));
       assertEquals("test-documents/testXML.xml", tracker.filenames.get(8));
       
       for(String type : tracker.mediatypes) {
          assertNull(type);
       }
       for(String mod : tracker.modifiedAts) {
           assertNotNull(mod);
           assertTrue("Modified at " + mod, mod.startsWith("20"));
       }
    }
}
