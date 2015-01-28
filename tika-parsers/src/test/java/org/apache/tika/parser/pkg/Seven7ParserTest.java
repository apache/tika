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
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.PasswordProvider;
import org.apache.tika.sax.BodyContentHandler;
import org.junit.Test;
import org.xml.sax.ContentHandler;

/**
 * Test case for parsing 7z files.
 */
public class Seven7ParserTest extends AbstractPkgTest {
    private static final MediaType TYPE_7ZIP = MediaType.application("x-7z-compressed");
    
    @Test
    public void test7ZParsing() throws Exception {
        Parser parser = new AutoDetectParser(); // Should auto-detect!
        ContentHandler handler = new BodyContentHandler();
        Metadata metadata = new Metadata();
        
        // Ensure 7zip is a parsable format
        assertTrue("No 7zip parser found", 
                parser.getSupportedTypes(recursingContext).contains(TYPE_7ZIP));
        
        // Parse
        InputStream stream = Seven7ParserTest.class.getResourceAsStream(
                "/test-documents/test-documents.7z");
        try {
            parser.parse(stream, handler, metadata, recursingContext);
        } finally {
            stream.close();
        }

        assertEquals(TYPE_7ZIP.toString(), metadata.get(Metadata.CONTENT_TYPE));
        String content = handler.toString();
        assertContains("test-documents/testEXCEL.xls", content);
        assertContains("Sample Excel Worksheet", content);
        assertContains("test-documents/testHTML.html", content);
        assertContains("Test Indexation Html", content);
        assertContains("test-documents/testOpenOffice2.odt", content);
        assertContains("This is a sample Open Office document", content);
        assertContains("test-documents/testPDF.pdf", content);
        assertContains("Apache Tika", content);
        assertContains("test-documents/testPPT.ppt", content);
        assertContains("Sample Powerpoint Slide", content);
        assertContains("test-documents/testRTF.rtf", content);
        assertContains("indexation Word", content);
        assertContains("test-documents/testTXT.txt", content);
        assertContains("Test d'indexation de Txt", content);
        assertContains("test-documents/testWORD.doc", content);
        assertContains("This is a sample Microsoft Word Document", content);
        assertContains("test-documents/testXML.xml", content);
        assertContains("Rida Benjelloun", content);
    }

    /**
     * Tests that the ParseContext parser is correctly
     *  fired for all the embedded entries.
     */
    @Test
    public void testEmbedded() throws Exception {
       Parser parser = new AutoDetectParser(); // Should auto-detect!
       ContentHandler handler = new BodyContentHandler();
       Metadata metadata = new Metadata();

       InputStream stream = Seven7ParserTest.class.getResourceAsStream(
               "/test-documents/test-documents.7z");
       try {
           parser.parse(stream, handler, metadata, trackingContext);
       } finally {
           stream.close();
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

    @Test
    public void testPasswordProtected() throws Exception {
        Parser parser = new AutoDetectParser();
        ContentHandler handler = new BodyContentHandler();
        Metadata metadata = new Metadata();
        
        // No password, will fail with EncryptedDocumentException
        InputStream stream = Seven7ParserTest.class.getResourceAsStream(
                "/test-documents/test7Z_protected_passTika.7z");
        boolean ex = false;
        try {
            parser.parse(stream, handler, metadata, recursingContext);
            fail("Shouldn't be able to read a password protected 7z without the password");
        } catch (EncryptedDocumentException e) {
            // Good
            ex = true;
        } finally {
            stream.close();
        }
        
        assertTrue("test no password", ex);

        ex = false;
        
        // Wrong password currently silently gives no content
        // Ideally we'd like Commons Compress to give an error, but it doesn't...
        recursingContext.set(PasswordProvider.class, new PasswordProvider() {
            @Override
            public String getPassword(Metadata metadata) {
                return "wrong";
            }
        });
        handler = new BodyContentHandler();
        stream = Seven7ParserTest.class.getResourceAsStream(
                "/test-documents/test7Z_protected_passTika.7z");
        try {
            parser.parse(stream, handler, metadata, recursingContext);
            fail("Shouldn't be able to read a password protected 7z with wrong password");
        } catch (TikaException e) {
            //if JCE is installed, the cause will be: Caused by: org.tukaani.xz.CorruptedInputException: Compressed data is corrupt
            //if JCE is not installed, the message will include
            // "(do you have the JCE  Unlimited Strength Jurisdiction Policy Files installed?")
            ex = true;
        } finally {
            stream.close();
        }
        assertTrue("TikaException for bad password", ex);
        // Will be empty
        assertEquals("", handler.toString());

        ex = false;
        // Right password works fine if JCE Unlimited Strength has been installed!!!
        if (isStrongCryptoAvailable()) {
            recursingContext.set(PasswordProvider.class, new PasswordProvider() {
                @Override
                public String getPassword(Metadata metadata) {
                    return "Tika";
                }
            });
            handler = new BodyContentHandler();
            stream = Seven7ParserTest.class.getResourceAsStream(
                    "/test-documents/test7Z_protected_passTika.7z");
            try {
                parser.parse(stream, handler, metadata, recursingContext);
            } finally {
                stream.close();
            }

            assertEquals(TYPE_7ZIP.toString(), metadata.get(Metadata.CONTENT_TYPE));
            String content = handler.toString();

            // Should get filename
            assertContains("text.txt", content);

            // Should get contents from the text file in the 7z file
            assertContains("TEST DATA FOR TIKA.", content);
            assertContains("This is text inside an encrypted 7zip (7z) file.", content);
            assertContains("It should be processed by Tika just fine!", content);
            assertContains("TIKA-1521", content);
        } else {
            //if jce is not installed, test for IOException wrapped in TikaException
            boolean ioe = false;
            recursingContext.set(PasswordProvider.class, new PasswordProvider() {
                @Override
                public String getPassword(Metadata metadata) {
                    return "Tika";
                }
            });
            handler = new BodyContentHandler();
            stream = Seven7ParserTest.class.getResourceAsStream(
                    "/test-documents/test7Z_protected_passTika.7z");
            try {
                parser.parse(stream, handler, metadata, recursingContext);
            } catch (TikaException e) {
                ioe = true;
            } finally {
                stream.close();
            }
            assertTrue("IOException because JCE was not installed", ioe);
        }
    }

    private static boolean isStrongCryptoAvailable() throws NoSuchAlgorithmException {
        return Cipher.getMaxAllowedKeyLength("AES/ECB/PKCS5Padding") >= 256;
    }
}
