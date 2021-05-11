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
package org.apache.tika.parser.mail;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assume.assumeTrue;

import java.io.InputStream;
import java.util.List;

import org.junit.BeforeClass;
import org.junit.Test;
import org.xml.sax.ContentHandler;

import org.apache.tika.TikaTest;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.PasswordProvider;
import org.apache.tika.sax.BodyContentHandler;

public class RFC822ParserTest extends TikaTest {

    //legacy RFC822 behavior...extract every alternative part
    private static Parser EXTRACT_ALL_ALTERNATIVES_PARSER;
    private static TikaConfig TIKA_CONFIG;

    private static InputStream getStream(String name) {
        InputStream stream =
                Thread.currentThread().getContextClassLoader().getResourceAsStream(name);
        assertNotNull("Test file not found " + name, stream);
        return stream;
    }

    @BeforeClass
    public static void setUp() throws Exception {

        try (InputStream is = getStream(
                "org/apache/tika/parser/mail/tika-config-extract-all-alternatives.xml")) {
            TIKA_CONFIG = new TikaConfig(is);
        }
        EXTRACT_ALL_ALTERNATIVES_PARSER = new AutoDetectParser(TIKA_CONFIG);
    }

    /**
     * Test TIKA-1028 - Ensure we can get the contents of an
     * un-encrypted zip file
     */
    @Test
    public void testNormalZipAttachment() throws Exception {
        Metadata metadata = new Metadata();
        ParseContext context = new ParseContext();
        context.set(Parser.class, EXTRACT_ALL_ALTERNATIVES_PARSER);
        InputStream stream = getStream("test-documents/testRFC822_normal_zip");
        ContentHandler handler = new BodyContentHandler();
        EXTRACT_ALL_ALTERNATIVES_PARSER.parse(stream, handler, metadata, context);

        // Check we go the metadata
        assertEquals("Juha Haaga <juha.haaga@gmail.com>", metadata.get(Metadata.MESSAGE_FROM));
        assertEquals("Test mail for Tika", metadata.get(TikaCoreProperties.TITLE));

        // Check we got the message text, for both Plain Text and HTML
        assertContains("Includes a normal, unencrypted zip file", handler.toString());
        assertContains("This is the Plain Text part", handler.toString());
        assertContains("This is the HTML part", handler.toString());

        // We get both name and contents of the zip file's contents
        assertContains("text.txt", handler.toString());
        assertContains("TEST DATA FOR TIKA.", handler.toString());
        assertContains("This is text inside an unencrypted zip file", handler.toString());
        assertContains("TIKA-1028", handler.toString());
        assertEquals("<juha.haaga@gmail.com>", metadata.get("Message:Raw-Header:Return-Path"));
    }

    /**
     * Test TIKA-1028 - If the mail contains an encrypted attachment (or
     * an attachment that others triggers an error), parsing should carry
     * on for the remainder regardless
     */
    @Test
    public void testEncryptedZipAttachment() throws Exception {
        Metadata metadata = new Metadata();
        ParseContext context = new ParseContext();
        context.set(Parser.class, EXTRACT_ALL_ALTERNATIVES_PARSER);
        InputStream stream = getStream("test-documents/testRFC822_encrypted_zip");
        ContentHandler handler = new BodyContentHandler();
        EXTRACT_ALL_ALTERNATIVES_PARSER.parse(stream, handler, metadata, context);

        // Check we go the metadata
        assertEquals("Juha Haaga <juha.haaga@gmail.com>", metadata.get(Metadata.MESSAGE_FROM));
        assertEquals("Test mail for Tika", metadata.get(TikaCoreProperties.TITLE));

        // Check we got the message text, for both Plain Text and HTML
        assertContains("Includes encrypted zip file", handler.toString());
        assertContains("password is \"test\".", handler.toString());
        assertContains("This is the Plain Text part", handler.toString());
        assertContains("This is the HTML part", handler.toString());

        // We won't get the contents of the zip file, but we will get the name
        assertContains("text.txt", handler.toString());
        assertNotContained("ENCRYPTED ZIP FILES", handler.toString());

        // Try again, this time with the password supplied
        // Check that we also get the zip's contents as well
        context.set(PasswordProvider.class, metadata1 -> "test");
        stream = getStream("test-documents/testRFC822_encrypted_zip");
        handler = new BodyContentHandler();
        EXTRACT_ALL_ALTERNATIVES_PARSER.parse(stream, handler, metadata, context);

        assertContains("Includes encrypted zip file", handler.toString());
        assertContains("password is \"test\".", handler.toString());
        assertContains("This is the Plain Text part", handler.toString());
        assertContains("This is the HTML part", handler.toString());

        // We do get the name of the file in the encrypted zip file
        assertContains("text.txt", handler.toString());

        // TODO Upgrade to a version of Commons Compress with Encryption
        //  support, then verify we get the contents of the text file
        //  held within the encrypted zip
        assumeTrue(false); // No Zip Encryption support yet
        assertContains("TEST DATA FOR TIKA.", handler.toString());
        assertContains("ENCRYPTED ZIP FILES", handler.toString());
        assertContains("TIKA-1028", handler.toString());
    }


    @Test
    public void testMainBody() throws Exception {
        //test that the first text or html chunk is processed in the main body
        //not treated as an attachment. TIKA-2547
        List<Metadata> metadataList = getRecursiveMetadata("testRFC822_oddfrom");
        assertEquals(7, metadataList.size());
        assertContains("Air Quality Planning",
                metadataList.get(0).get(TikaCoreProperties.TIKA_CONTENT));

        //Make sure text alternative doesn't get treated as an attachment
        metadataList = getRecursiveMetadata("testRFC822_normal_zip");
        assertEquals(3, metadataList.size());
        assertContains("This is the HTML part",
                metadataList.get(0).get(TikaCoreProperties.TIKA_CONTENT));
        assertEquals("application/zip", metadataList.get(2).get(Metadata.CONTENT_TYPE));

        metadataList = getRecursiveMetadata("testRFC822-txt-body");
        assertEquals(2, metadataList.size());
        assertContains("body 1", metadataList.get(0).get(TikaCoreProperties.TIKA_CONTENT));
    }
}
