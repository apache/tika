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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.InputStream;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import javax.crypto.Cipher;

import org.junit.jupiter.api.Test;
import org.xml.sax.ContentHandler;

import org.apache.tika.exception.EncryptedDocumentException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseRecord;
import org.apache.tika.parser.PasswordProvider;
import org.apache.tika.sax.BodyContentHandler;

/**
 * Test case for parsing 7z files.
 */
public class Seven7ParserTest extends AbstractPkgTest {
    private static final MediaType TYPE_7ZIP = MediaType.application("x-7z-compressed");

    private static boolean isStrongCryptoAvailable() throws NoSuchAlgorithmException {
        return Cipher.getMaxAllowedKeyLength("AES/ECB/PKCS5Padding") >= 256;
    }

    @Test
    public void test7ZParsing() throws Exception {
        Metadata metadata = new Metadata();

        // Ensure 7zip is a parsable format
        assertTrue(AUTO_DETECT_PARSER.getSupportedTypes(recursingContext).contains(TYPE_7ZIP),
                "No 7zip parser found");

        // Parse
        String content = getText("test-documents.7z", metadata);

        assertEquals(TYPE_7ZIP.toString(), metadata.get(Metadata.CONTENT_TYPE));
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

    @Test
    public void testPasswordProtected() throws Exception {
        ContentHandler handler = new BodyContentHandler();
        Metadata metadata = new Metadata();

        // No password, will fail with EncryptedDocumentException
        boolean ex = false;
        try (InputStream stream = getResourceAsStream(
                "/test-documents/test7Z_protected_passTika.7z")) {
            AUTO_DETECT_PARSER.parse(stream, handler, metadata, recursingContext);
            fail("Shouldn't be able to read a password protected 7z without the password");
        } catch (EncryptedDocumentException e) {
            // Good
            ex = true;
        }

        assertTrue(ex, "test no password");

        // No password, will fail with EncryptedDocumentException
        ex = false;
        try (InputStream stream = getResourceAsStream("/test-documents/full_encrypted.7z")) {
            AUTO_DETECT_PARSER.parse(stream, handler, metadata, recursingContext);
            fail("Shouldn't be able to read a full password protected 7z without the password");
        } catch (EncryptedDocumentException e) {
            // Good
            ex = true;
        } catch (Exception e) {
            ex = false;
        }

        assertTrue(ex, "test no password for full encrypted 7z");

        ex = false;

        // Wrong password currently silently gives no content
        // Ideally we'd like Commons Compress to give an error, but it doesn't...
        recursingContext.set(PasswordProvider.class, metadata1 -> "wrong");
        handler = new BodyContentHandler();
        try (InputStream stream = getResourceAsStream(
                "/test-documents/test7Z_protected_passTika.7z")) {
            AUTO_DETECT_PARSER.parse(stream, handler, metadata, recursingContext);
            fail("Shouldn't be able to read a password protected 7z with wrong password");
        } catch (TikaException e) {
            //if JCE is installed, the cause will be:
            // Caused by: org.tukaani.xz.CorruptedInputException: Compressed data is corrupt
            //if JCE is not installed, the message will include
            // "(do you have the JCE  Unlimited Strength Jurisdiction Policy Files installed?")
            ex = true;
        }
        assertTrue(ex, "TikaException for bad password");
        // Will be empty
        assertEquals("", handler.toString());

        // Right password works fine if JCE Unlimited Strength has been installed!!!
        if (isStrongCryptoAvailable()) {
            recursingContext.set(PasswordProvider.class, metadata12 -> "Tika");
            handler = new BodyContentHandler();
            try (InputStream stream = getResourceAsStream(
                    "/test-documents/test7Z_protected_passTika.7z")) {
                AUTO_DETECT_PARSER.parse(stream, handler, metadata, recursingContext);
            }

            // help debugging problems with commons-compress 1.25.0 -> 1.26.0
            ParseRecord parserRecord = recursingContext.get(ParseRecord.class);
            List<Exception> exceptions = parserRecord.getExceptions();
            if (!exceptions.isEmpty()) {
                System.out.println("Exceptions:");
                exceptions.forEach(e -> e.printStackTrace());
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
            recursingContext.set(PasswordProvider.class, metadata13 -> "Tika");
            handler = new BodyContentHandler();
            try (InputStream stream = getResourceAsStream(
                    "/test-documents/test7Z_protected_passTika.7z")) {
                AUTO_DETECT_PARSER.parse(stream, handler, metadata, recursingContext);
            } catch (TikaException e) {
                ioe = true;
            }
            assertTrue(ioe, "IOException because JCE was not installed");
        }
    }
}
