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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import org.apache.james.mime4j.stream.MimeConfig;
import org.apache.tika.TikaTest;
import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.ContainerExtractor;
import org.apache.tika.extractor.ParserContainerExtractor;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.PasswordProvider;
import org.apache.tika.parser.ocr.TesseractOCRParserTest;
import org.apache.tika.sax.BodyContentHandler;
import org.apache.tika.sax.XHTMLContentHandler;
import org.junit.Test;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.helpers.DefaultHandler;

public class RFC822ParserTest extends TikaTest {

    @Test
    public void testSimple() {
        Parser parser = new RFC822Parser();
        Metadata metadata = new Metadata();
        InputStream stream = getStream("test-documents/testRFC822");
        ContentHandler handler = mock(DefaultHandler.class);

        try {
            parser.parse(stream, handler, metadata, new ParseContext());
            verify(handler).startDocument();
            //just one body
            verify(handler).startElement(eq(XHTMLContentHandler.XHTML), eq("p"), eq("p"), any(Attributes.class));
            verify(handler).endElement(XHTMLContentHandler.XHTML, "p", "p");
            //no multi-part body parts
            verify(handler, never()).startElement(eq(XHTMLContentHandler.XHTML), eq("div"), eq("div"), any(Attributes.class));
            verify(handler, never()).endElement(XHTMLContentHandler.XHTML, "div", "div");
            verify(handler).endDocument();
            //note no leading spaces, and no quotes
            assertEquals("Julien Nioche (JIRA) <jira@apache.org>", metadata.get(TikaCoreProperties.CREATOR));
            assertEquals("[jira] Commented: (TIKA-461) RFC822 messages not parsed", 
                    metadata.get(TikaCoreProperties.TITLE));
            assertEquals("[jira] Commented: (TIKA-461) RFC822 messages not parsed", 
                    metadata.get(Metadata.SUBJECT));
        } catch (Exception e) {
            fail("Exception thrown: " + e.getMessage());
        }
    }

    @Test
    public void testMultipart() {
        Parser parser = new RFC822Parser();
        Metadata metadata = new Metadata();
        InputStream stream = getStream("test-documents/testRFC822-multipart");
        ContentHandler handler = mock(XHTMLContentHandler.class);

        try {
            parser.parse(stream, handler, metadata, new ParseContext());
            verify(handler).startDocument();
            int bodyExpectedTimes = 4, multipackExpectedTimes = 5;
            // TIKA-1422. TesseractOCRParser interferes with the number of times the handler is invoked.
            // But, different versions of Tesseract lead to a different number of invocations. So, we
            // only verify the handler if Tesseract cannot run.
            if (!TesseractOCRParserTest.canRun()) {
                verify(handler, times(bodyExpectedTimes)).startElement(eq(XHTMLContentHandler.XHTML), eq("div"), eq("div"), any(Attributes.class));
                verify(handler, times(bodyExpectedTimes)).endElement(XHTMLContentHandler.XHTML, "div", "div");
            }
            verify(handler, times(multipackExpectedTimes)).startElement(eq(XHTMLContentHandler.XHTML), eq("p"), eq("p"), any(Attributes.class));
            verify(handler, times(multipackExpectedTimes)).endElement(XHTMLContentHandler.XHTML, "p", "p");
            verify(handler).endDocument();
            
        } catch (Exception e) {
            fail("Exception thrown: " + e.getMessage());
        }
        
        //repeat, this time looking at content
        parser = new RFC822Parser();
        metadata = new Metadata();
        stream = getStream("test-documents/testRFC822-multipart");
        handler = new BodyContentHandler();
        try {
            parser.parse(stream, handler, metadata, new ParseContext());
            //tests correct decoding of quoted printable text, including UTF-8 bytes into Unicode
            String bodyText = handler.toString();
            assertTrue(bodyText.contains("body 1"));
            assertTrue(bodyText.contains("body 2"));
            assertFalse(bodyText.contains("R0lGODlhNgE8AMQAA")); //part of encoded gif
        } catch (Exception e) {
            fail("Exception thrown: " + e.getMessage());
        }
    }

    @Test
    public void testQuotedPrintable() {
        Parser parser = new RFC822Parser();
        Metadata metadata = new Metadata();
        InputStream stream = getStream("test-documents/testRFC822_quoted");
        ContentHandler handler = new BodyContentHandler();

        try {
            parser.parse(stream, handler, metadata, new ParseContext());
            //tests correct decoding of quoted printable text, including UTF-8 bytes into Unicode
            String bodyText = handler.toString();
            assertTrue(bodyText.contains("D\u00FCsseldorf has non-ascii."));
            assertTrue(bodyText.contains("Lines can be split like this."));
            assertTrue(bodyText.contains("Spaces at the end of a line \r\nmust be encoded.\r\n"));
            assertFalse(bodyText.contains("=")); //there should be no escape sequences
        } catch (Exception e) {
            fail("Exception thrown: " + e.getMessage());
        }
    }

    @Test
    public void testBase64() {
        Parser parser = new RFC822Parser();
        Metadata metadata = new Metadata();
        InputStream stream = getStream("test-documents/testRFC822_base64");
        ContentHandler handler = new BodyContentHandler();

        try {
            parser.parse(stream, handler, metadata, new ParseContext());
            //tests correct decoding of base64 text, including ISO-8859-1 bytes into Unicode
            assertContains("Here is some text, with international characters, voil\u00E0!", handler.toString());
        } catch (Exception e) {
            fail("Exception thrown: " + e.getMessage());
        }
    }

    @Test
    public void testI18NHeaders() {
        Parser parser = new RFC822Parser();
        Metadata metadata = new Metadata();
        InputStream stream = getStream("test-documents/testRFC822_i18nheaders");
        ContentHandler handler = mock(DefaultHandler.class);

        try {
            parser.parse(stream, handler, metadata, new ParseContext());
            //tests correct decoding of internationalized headers, both
            //quoted-printable (Q) and Base64 (B).
            assertEquals("Keld J\u00F8rn Simonsen <keld@dkuug.dk>", 
                    metadata.get(TikaCoreProperties.CREATOR));
            assertEquals("If you can read this you understand the example.", 
                    metadata.get(TikaCoreProperties.TITLE));
            assertEquals("If you can read this you understand the example.", 
                    metadata.get(Metadata.SUBJECT));
        } catch (Exception e) {
            fail("Exception thrown: " + e.getMessage());
        }
    }
    
    /**
     * The from isn't in the usual form.
     * See TIKA-618
     */
    @Test
    public void testUnusualFromAddress() throws Exception {
       Parser parser = new RFC822Parser();
       Metadata metadata = new Metadata();
       InputStream stream = getStream("test-documents/testRFC822_oddfrom");
       ContentHandler handler = mock(DefaultHandler.class);

       parser.parse(stream, handler, metadata, new ParseContext());
       assertEquals("Saved by Windows Internet Explorer 7", 
               metadata.get(TikaCoreProperties.CREATOR));
       assertEquals("Air Permit Programs | Air & Radiation | US EPA", 
               metadata.get(TikaCoreProperties.TITLE));
       assertEquals("Air Permit Programs | Air & Radiation | US EPA", 
               metadata.get(Metadata.SUBJECT));
    }

    /**
     * Test for TIKA-640, increase header max beyond 10k bytes
     */
    @Test
    public void testLongHeader() throws Exception {
        StringBuilder inputBuilder = new StringBuilder();
        for (int i = 0; i < 2000; ++i) {
            inputBuilder.append( //len > 50
                    "really really really really really really long name ");
        }
        String name = inputBuilder.toString();
        byte[] data = ("From: " + name + "\r\n\r\n").getBytes("US-ASCII");

        Parser parser = new RFC822Parser();
        ContentHandler handler = new DefaultHandler();
        Metadata metadata = new Metadata();
        ParseContext context = new ParseContext();

        try {
            parser.parse(
                    new ByteArrayInputStream(data), handler, metadata, context);
            fail();
        } catch (TikaException expected) {
        }

        MimeConfig config = new MimeConfig();
        config.setMaxHeaderLen(-1);
        config.setMaxLineLen(-1);
        context.set(MimeConfig.class, config);
        parser.parse(
                new ByteArrayInputStream(data), handler, metadata, context);
        assertEquals(name.trim(), metadata.get(TikaCoreProperties.CREATOR));
    }
    
    /**
     * Test for TIKA-678 - not all headers may be present
     */
    @Test
    public void testSomeMissingHeaders() throws Exception {
       Parser parser = new RFC822Parser();
       Metadata metadata = new Metadata();
       InputStream stream = getStream("test-documents/testRFC822-limitedheaders");
       ContentHandler handler = new BodyContentHandler();

       parser.parse(stream, handler, metadata, new ParseContext());
       assertEquals(true, metadata.isMultiValued(TikaCoreProperties.CREATOR));
       assertEquals("xyz", metadata.getValues(TikaCoreProperties.CREATOR)[0]);
       assertEquals("abc", metadata.getValues(TikaCoreProperties.CREATOR)[1]);
       assertEquals(true, metadata.isMultiValued(Metadata.MESSAGE_FROM));
       assertEquals("xyz", metadata.getValues(Metadata.MESSAGE_FROM)[0]);
       assertEquals("abc", metadata.getValues(Metadata.MESSAGE_FROM)[1]);
       assertEquals(true, metadata.isMultiValued(Metadata.MESSAGE_TO));
       assertEquals("abc", metadata.getValues(Metadata.MESSAGE_TO)[0]);
       assertEquals("def", metadata.getValues(Metadata.MESSAGE_TO)[1]);
       assertEquals("abcd", metadata.get(TikaCoreProperties.TITLE));
       assertEquals("abcd", metadata.get(Metadata.SUBJECT));
       assertContains("bar biz bat", handler.toString());
    }
    
    /**
     * Test TIKA-1028 - If the mail contains an encrypted attachment (or
     *  an attachment that others triggers an error), parsing should carry
     *  on for the remainder regardless
     */
    @Test
    public void testEncryptedZipAttachment() throws Exception {
        Parser parser = new RFC822Parser();
        Metadata metadata = new Metadata();
        ParseContext context = new ParseContext();
        InputStream stream = getStream("test-documents/testRFC822_encrypted_zip");
        ContentHandler handler = new BodyContentHandler();
        parser.parse(stream, handler, metadata, context);
        
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
        context.set(PasswordProvider.class, new PasswordProvider() {
            public String getPassword(Metadata metadata) {
                return "test";
            }
         });
        stream = getStream("test-documents/testRFC822_encrypted_zip");
        handler = new BodyContentHandler();
        parser.parse(stream, handler, metadata, context);
        
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
    
    /**
     * Test TIKA-1028 - Ensure we can get the contents of an
     *  un-encrypted zip file
     */
    @Test
    public void testNormalZipAttachment() throws Exception {
        Parser parser = new RFC822Parser();
        Metadata metadata = new Metadata();
        ParseContext context = new ParseContext();
        InputStream stream = getStream("test-documents/testRFC822_normal_zip");
        ContentHandler handler = new BodyContentHandler();
        parser.parse(stream, handler, metadata, context);
        
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
    }
    
    /**
     * TIKA-1222 When requested, ensure that the various attachments of
     *  the mail come through properly as embedded resources
     */
    @Test
    public void testGetAttachmentsAsEmbeddedResources() throws Exception {
        TrackingHandler tracker = new TrackingHandler();
        TikaInputStream tis = null;
        ContainerExtractor ex = new ParserContainerExtractor();
        try {
            tis = TikaInputStream.get(getStream("test-documents/testRFC822-multipart"));
            assertEquals(true, ex.isSupported(tis));
            ex.extract(tis, ex, tracker);
        } finally {
            if (tis != null)
                tis.close();
        }
        
        // Check we found all 3 parts
        assertEquals(3, tracker.filenames.size());
        assertEquals(3, tracker.mediaTypes.size());
        
        // No filenames available
        assertEquals(null, tracker.filenames.get(0));
        assertEquals(null, tracker.filenames.get(1));
        assertEquals(null, tracker.filenames.get(2));
        // Types are available
        assertEquals(MediaType.TEXT_PLAIN, tracker.mediaTypes.get(0));
        assertEquals(MediaType.TEXT_HTML, tracker.mediaTypes.get(1));
        assertEquals(MediaType.image("gif"), tracker.mediaTypes.get(2));
    }

    private static InputStream getStream(String name) {
        InputStream stream = Thread.currentThread().getContextClassLoader()
                                    .getResourceAsStream(name);
        assertNotNull("Test file not found " + name, stream);
        return stream;
    }
}
