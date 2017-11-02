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

import static java.nio.charset.StandardCharsets.US_ASCII;
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
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.text.DateFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import org.apache.james.mime4j.stream.MimeConfig;
import org.apache.tika.TikaTest;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.ContainerExtractor;
import org.apache.tika.extractor.ParserContainerExtractor;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Message;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.PasswordProvider;
import org.apache.tika.parser.RecursiveParserWrapper;
import org.apache.tika.parser.ocr.TesseractOCRParserTest;
import org.apache.tika.sax.BodyContentHandler;
import org.apache.tika.sax.XHTMLContentHandler;
import org.junit.BeforeClass;
import org.junit.Test;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.helpers.DefaultHandler;

public class RFC822ParserTest extends TikaTest {

    private static InputStream getStream(String name) {
        InputStream stream = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream(name);
        assertNotNull("Test file not found " + name, stream);
        return stream;
    }

    //legacy RFC822 behavior...extract every alternative part
    private static Parser EXTRACT_ALL_ALTERNATIVES_PARSER;
    private static TikaConfig TIKA_CONFIG;

    @BeforeClass
    public static void setUp() throws Exception {

        try (InputStream is = getStream("org/apache/tika/parser/mail/tika-config-extract-all-alternatives.xml")) {
            TIKA_CONFIG = new TikaConfig(is);
        }
        EXTRACT_ALL_ALTERNATIVES_PARSER = new AutoDetectParser(TIKA_CONFIG);
    }

    @Test
    public void testSimple() throws Exception {
        Metadata metadata = new Metadata();
        InputStream stream = getStream("test-documents/testRFC822");
        ContentHandler handler = mock(DefaultHandler.class);

        try {
            EXTRACT_ALL_ALTERNATIVES_PARSER.parse(stream, handler, metadata, new ParseContext());
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
    public void testExtendedToFromMetadata() throws Exception {
        Metadata m = getXML("testRFC822").metadata;
        assertEquals("Julien Nioche (JIRA)", m.get(Message.MESSAGE_FROM_NAME));
        assertEquals("jira@apache.org", m.get(Message.MESSAGE_FROM_EMAIL));

        m = getXML("testRFC822-multipart").metadata;
        assertEquals("DigitalPebble", m.get(Message.MESSAGE_FROM_NAME));
        assertEquals("julien@digitalpebble.com", m.get(Message.MESSAGE_FROM_EMAIL));

        m = getXML("testRFC822_quoted").metadata;
        assertEquals("Another Person", m.get(Message.MESSAGE_FROM_NAME));
        assertEquals("another.person@another-example.com", m.get(Message.MESSAGE_FROM_EMAIL));

        m = getXML("testRFC822_i18nheaders").metadata;
        assertEquals("Keld Jørn Simonsen", m.get(Message.MESSAGE_FROM_NAME));
        assertEquals("keld@dkuug.dk", m.get(Message.MESSAGE_FROM_EMAIL));

        //this is currently detected as mbox!!!
        m = getXML("testEmailWithPNGAtt.eml", new RFC822Parser()).metadata;
        assertEquals("Tika Test", m.get(Message.MESSAGE_FROM_NAME));
        assertEquals("XXXX@apache.org", m.get(Message.MESSAGE_FROM_EMAIL));

    }

    @Test
    public void testMultipart() {
        Metadata metadata = new Metadata();
        InputStream stream = getStream("test-documents/testRFC822-multipart");
        ContentHandler handler = mock(XHTMLContentHandler.class);
        ParseContext context = new ParseContext();
        context.set(Parser.class, EXTRACT_ALL_ALTERNATIVES_PARSER);
        try {
            EXTRACT_ALL_ALTERNATIVES_PARSER.parse(stream, handler, metadata, context);
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
        metadata = new Metadata();
        stream = getStream("test-documents/testRFC822-multipart");
        handler = new BodyContentHandler();
        try {
            EXTRACT_ALL_ALTERNATIVES_PARSER.parse(stream, handler, metadata, context);
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
        Metadata metadata = new Metadata();
        InputStream stream = getStream("test-documents/testRFC822_quoted");
        ContentHandler handler = new BodyContentHandler();
        ParseContext context = new ParseContext();
        context.set(Parser.class, new AutoDetectParser());

        try {
            EXTRACT_ALL_ALTERNATIVES_PARSER.parse(stream, handler, metadata, context);
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
    public void testBase64() throws Exception  {
        Metadata metadata = new Metadata();
        InputStream stream = getStream("test-documents/testRFC822_base64");
        ContentHandler handler = new BodyContentHandler();
        ParseContext context = new ParseContext();
        context.set(Parser.class, EXTRACT_ALL_ALTERNATIVES_PARSER);
        //need to pass in hint.  Autodetects text/plain
        metadata.set(Metadata.CONTENT_TYPE, "message/rfc822");
        try {
            EXTRACT_ALL_ALTERNATIVES_PARSER.parse(stream, handler, metadata, context);
            //tests correct decoding of base64 text, including ISO-8859-1 bytes into Unicode
            assertContains("Here is some text, with international characters, voil\u00E0!", handler.toString());
        } catch (Exception e) {
            fail("Exception thrown: " + e.getMessage());
        }
    }

    @Test
    public void testI18NHeaders() {
        Metadata metadata = new Metadata();
        InputStream stream = getStream("test-documents/testRFC822_i18nheaders");
        ContentHandler handler = mock(DefaultHandler.class);

        try {
            EXTRACT_ALL_ALTERNATIVES_PARSER.parse(stream, handler, metadata, new ParseContext());
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
        Metadata metadata = new Metadata();
        InputStream stream = getStream("test-documents/testRFC822_oddfrom");
        ContentHandler handler = mock(DefaultHandler.class);

        EXTRACT_ALL_ALTERNATIVES_PARSER.parse(stream, handler, metadata, new ParseContext());
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
        byte[] data = ("From: " + name + "\r\n\r\n").getBytes(US_ASCII);

        ContentHandler handler = new DefaultHandler();
        Metadata metadata = new Metadata();
        ParseContext context = new ParseContext();

        try {
            EXTRACT_ALL_ALTERNATIVES_PARSER.parse(
                    new ByteArrayInputStream(data), handler, metadata, context);
            fail();
        } catch (TikaException expected) {
        }

        MimeConfig config = new MimeConfig.Builder().setMaxHeaderLen(-1).setMaxLineLen(-1).build();
        context.set(MimeConfig.class, config);
        EXTRACT_ALL_ALTERNATIVES_PARSER.parse(
                new ByteArrayInputStream(data), handler, metadata, context);
        assertEquals(name.trim(), metadata.get(TikaCoreProperties.CREATOR));
    }

    /**
     * Test for TIKA-678 - not all headers may be present
     */
    @Test
    public void testSomeMissingHeaders() throws Exception {
        Metadata metadata = new Metadata();
        InputStream stream = getStream("test-documents/testRFC822-limitedheaders");
        ContentHandler handler = new BodyContentHandler();
        ParseContext context = new ParseContext();
        context.set(Parser.class, EXTRACT_ALL_ALTERNATIVES_PARSER);

        EXTRACT_ALL_ALTERNATIVES_PARSER.parse(stream, handler, metadata, context);
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
        context.set(PasswordProvider.class, new PasswordProvider() {
            public String getPassword(Metadata metadata) {
                return "test";
            }
        });
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
     * TIKA-1222 When requested, ensure that the various attachments of
     * the mail come through properly as embedded resources
     */
    @Test
    public void testGetAttachmentsAsEmbeddedResources() throws Exception {
        TrackingHandler tracker = new TrackingHandler();
        ContainerExtractor ex = new ParserContainerExtractor(TIKA_CONFIG);
        try (TikaInputStream tis = TikaInputStream.get(getStream("test-documents/testRFC822-multipart"))) {
            assertEquals(true, ex.isSupported(tis));
            ex.extract(tis, ex, tracker);
        }

        // Check we found all 3 parts
        assertEquals(3, tracker.filenames.size());
        assertEquals(3, tracker.mediaTypes.size());

        // No filenames available
        assertEquals(null, tracker.filenames.get(0));
        assertEquals(null, tracker.filenames.get(1));
        // Except for this using Content-Disposition filename field
        assertEquals("logo.gif", tracker.filenames.get(2));
        // Types are available
        assertEquals(MediaType.TEXT_PLAIN, tracker.mediaTypes.get(0));
        assertEquals(MediaType.TEXT_HTML, tracker.mediaTypes.get(1));
        assertEquals(MediaType.image("gif"), tracker.mediaTypes.get(2));
    }

    @Test
    public void testDetection() throws Exception {
        //test simple text file
        XMLResult r = getXML("testRFC822_date_utf8");
        assertEquals("message/rfc822", r.metadata.get(Metadata.CONTENT_TYPE));

        //test without extension
        r = getXML("testRFC822_eml");
        assertEquals("message/rfc822", r.metadata.get(Metadata.CONTENT_TYPE));
    }

    @Test
    public void testDates() throws Exception {
        //tests non-standard dates that mime4j can't parse
        XMLResult r = getXML("testRFC822_date_utf8");
        assertEquals("2016-05-16T08:30:32Z", r.metadata.get(TikaCoreProperties.CREATED));

        r = getXML("testRFC822_eml");
        assertEquals("2016-05-16T08:30:32Z", r.metadata.get(TikaCoreProperties.CREATED));


        String expected = "2016-05-15T01:32:00Z";

        for (String dateString : new String[]{
                "Sun, 15 May 2016 01:32:00 UTC", //make sure this test basically works
                "Sun, 15 May 2016 01:32:00", //no timezone
                "Sunday, May 15 2016 1:32 AM",
                "May 15 2016 1:32am",
                "May 15 2016 1:32 am",
                "2016-05-15 01:32:00",
                "      Sun, 15 May 2016 3:32:00 +0200",//format correctly handled by mime4j if no leading whitespace
                "      Sun, 14 May 2016 20:32:00 EST",
        }) {
            testDate(dateString, expected);
        }

        //now try days without times
        expected = "2016-05-15T12:00:00Z";
        for (String dateString : new String[]{
                "May 15, 2016",
                "Sun, 15 May 2016",
                "15 May 2016",
        }) {
            testDate(dateString, expected);
        }
    }

    @Test
    public void testTrickyDates() throws Exception {
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd", new DateFormatSymbols(Locale.US));
        //make sure there are no mis-parses of e.g. 90 = year 90 A.D, not 1990
        Date date1980 = df.parse("1980-01-01");
        for (String dateString : new String[] {
                "Mon, 29 Jan 96 14:02 GMT",
                "7/20/95 1:12pm",
                "08/14/2000  12:48 AM",
                "06/24/2008, Tuesday, 11 AM",
                "11/14/08",
                "12/02/1996",
                "96/12/02",
        }) {
            Date parsedDate = getDate(dateString);
            if (parsedDate != null) {
                assertTrue("date must be after 1980:"+dateString, parsedDate.getTime() > date1980.getTime());
            }
        }
        //TODO: mime4j misparses these to pre 1980 dates
        //"Wed, 27 Dec 95 11:20:40 EST",
        //"26 Aug 00 11:14:52 EDT"
        //
        //We are still misparsing: 8/1/03 to a pre 1980 date

    }

    private void testDate(String dateString, String expected) throws Exception {
        Date parsedDate = getDate(dateString);
        assertNotNull("couldn't parse " + dateString, parsedDate);
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'",
                new DateFormatSymbols(Locale.US));
        String parsedDateString = df.format(parsedDate);
        assertEquals("failed to match: "+dateString, expected, parsedDateString);
    }

    private Date getDate(String dateString) throws Exception {
        String mail = "From: dev@tika.apache.org\n"+
                "Date: "+dateString+"\n";
        Parser p = new RFC822Parser();
        Metadata m = new Metadata();
        try (InputStream is = TikaInputStream.get(mail.getBytes(StandardCharsets.UTF_8))) {
            p.parse(is, new DefaultHandler(), m, new ParseContext());
        }
        return m.getDate(TikaCoreProperties.CREATED);
    }


    @Test
    public void testMultipleSubjects() throws Exception {
        //adapted from govdocs1 303710.txt
        String s = "From: Shawn Jones [chiroshawn@yahoo.com]\n" +
                "Subject: 2006N-3502\n" +
                "Subject: I Urge You to Require Notice of Mercury";
        Parser p = new RFC822Parser();
        Metadata m = new Metadata();
        p.parse(TikaInputStream.get(s.getBytes(StandardCharsets.UTF_8)), new DefaultHandler(), m, new ParseContext());
        assertEquals("I Urge You to Require Notice of Mercury", m.get(TikaCoreProperties.TITLE));
    }


    @Test
    public void testExtractAttachments() throws Exception {
        List<Metadata> metadataList = getRecursiveMetadata("testEmailWithPNGAtt.eml",
                EXTRACT_ALL_ALTERNATIVES_PARSER);
        // Check we get the metadata
        assertEquals("Tika Test <XXXX@apache.org>", metadataList.get(3).get(Metadata.MESSAGE_FROM));
        assertEquals("Test Attachment Email", metadataList.get(3).get(TikaCoreProperties.TITLE));

        // Check attachments
        assertEquals(4, metadataList.size());
        assertEquals("text/plain; charset=UTF-8", metadataList.get(1).get(Metadata.CONTENT_TYPE));
        assertEquals("image/png", metadataList.get(2).get(Metadata.CONTENT_TYPE));
        assertEquals("testPNG.png", metadataList.get(2).get(Metadata.RESOURCE_NAME_KEY));
        assertContains("This email has a PNG attachment included in it", metadataList.get(1).get(RecursiveParserWrapper.TIKA_CONTENT));
        assertEquals(null, metadataList.get(1).get(Metadata.CONTENT_DISPOSITION));
        assertEquals("attachment; filename=\"testPNG.png\"", metadataList.get(2).get(Metadata.CONTENT_DISPOSITION));
    }

    @Test
    public void testEmbeddedMetadata() throws Exception {
        List<Metadata> seenMetadata = getRecursiveMetadata("testRFC822-multipart",
                EXTRACT_ALL_ALTERNATIVES_PARSER);

        assertEquals(4, seenMetadata.size());
        assertEquals(null, seenMetadata.get(1).get(Metadata.CONTENT_DISPOSITION));
        assertEquals("text/plain; charset=UTF-8", seenMetadata.get(1).get(Metadata.CONTENT_TYPE));
        assertEquals("UTF-8", seenMetadata.get(1).get(Metadata.CONTENT_ENCODING));
        assertEquals(null, seenMetadata.get(2).get(Metadata.CONTENT_DISPOSITION));
        assertEquals("text/html; charset=UTF-8", seenMetadata.get(2).get(Metadata.CONTENT_TYPE));
        assertEquals("UTF-8", seenMetadata.get(2).get(Metadata.CONTENT_ENCODING));
        assertEquals("attachment; filename=\"logo.gif\"", seenMetadata.get(3).get(Metadata.CONTENT_DISPOSITION));
        assertEquals("logo.gif", seenMetadata.get(3).get(Metadata.RESOURCE_NAME_KEY));
        assertEquals("image/gif", seenMetadata.get(3).get(Metadata.CONTENT_TYPE));
    }

    @Test
    public void testMultipartFlags() throws Exception {
        List<Metadata> metadataList = getRecursiveMetadata("testRFC822-multipart", EXTRACT_ALL_ALTERNATIVES_PARSER);
        // Check the root metadata.
        assertEquals("mixed", metadataList.get(0).get(Message.MULTIPART_SUBTYPE));
        assertEquals("0016e64606800312ee04913db790", metadataList.get(0).get(Message.MULTIPART_BOUNDARY));

        // Check the metadata of the first alternative.
        assertTrue(metadataList.get(1).get(Metadata.CONTENT_TYPE).equals("text/plain; charset=UTF-8"));
        assertTrue(metadataList.get(1).get(Message.MULTIPART_SUBTYPE).equals("alternative"));
        assertTrue(metadataList.get(1).get(Message.MULTIPART_BOUNDARY).equals("0016e64606800312ea04913db78e"));

        // Check the metadata of the second alternative.
        assertTrue(metadataList.get(2).get(Metadata.CONTENT_TYPE).equals("text/html; charset=UTF-8"));
        assertTrue(metadataList.get(2).get(Message.MULTIPART_SUBTYPE).equals("alternative"));
        assertTrue(metadataList.get(2).get(Message.MULTIPART_BOUNDARY).equals("0016e64606800312ea04913db78e"));

        // Check the metadata of the attached GIF.
        assertTrue(metadataList.get(3).get(Metadata.CONTENT_TYPE).equals("image/gif"));
        assertEquals("mixed", metadataList.get(3).get(Message.MULTIPART_SUBTYPE));
        assertEquals("0016e64606800312ee04913db790", metadataList.get(3).get(Message.MULTIPART_BOUNDARY));
    }

    @Test
    public void testBasicAlternativeBodyHandling() throws Exception {
        /*
            multi-part/mixed
                multi-part/alternative
                    text
                    html
                gif
         */
        List<Metadata> metadataList = getRecursiveMetadata("testRFC822-multipart");
        assertEquals(2, metadataList.size());
        String body = metadataList.get(0).get(RecursiveParserWrapper.TIKA_CONTENT);
        assertContains("body 2", body);
        assertNotContained("body 1", body);
        assertEquals("message/rfc822", metadataList.get(0).get(Metadata.CONTENT_TYPE));
        assertEquals("image/gif", metadataList.get(1).get(Metadata.CONTENT_TYPE));
        assertEquals("/logo.gif", metadataList.get(1).get(RecursiveParserWrapper.EMBEDDED_RESOURCE_PATH));
    }

    @Test
    public void testMixedRelatedMultipart() throws Exception {
        /*
            multipart/mixed (..6)
                multipart/related (..5)
                    multipart/alternative  (..4)
                        text/plain
                        text/html
                image/jpeg (inline) Mary with cooler.jpeg  (..5)
            image/jpeg (attachment) mary-coffee.jpg (..6)

         */

        List<Metadata> metadataList = getRecursiveMetadata("testRFC822-mixed-simple");
        assertEquals(3, metadataList.size());

        assertContains("body 2", metadataList.get(0).get(RecursiveParserWrapper.TIKA_CONTENT));
        assertNotContained("body 1", metadataList.get(0).get(RecursiveParserWrapper.TIKA_CONTENT));
        assertEquals("message/rfc822", metadataList.get(0).get(Metadata.CONTENT_TYPE));

        assertEquals("image/jpeg", metadataList.get(1).get(Metadata.CONTENT_TYPE));
        assertEquals("/Mary with cooler.jpeg", metadataList.get(1).get(RecursiveParserWrapper.EMBEDDED_RESOURCE_PATH));
        assertEquals(TikaCoreProperties.EmbeddedResourceType.INLINE.toString(),
                metadataList.get(1).get(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE));

        assertEquals("image/jpeg", metadataList.get(2).get(Metadata.CONTENT_TYPE));
        assertEquals("/mary-coffee.jpg", metadataList.get(2).get(RecursiveParserWrapper.EMBEDDED_RESOURCE_PATH));
        assertEquals(TikaCoreProperties.EmbeddedResourceType.ATTACHMENT.toString(),
                metadataList.get(2).get(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE));
    }

    @Test
    public void testAlternativeWithComplexMixedChild() throws Exception {
        /*
        This tests that both html body chunks are stitched back
        together in the body text for the main email.

            multi-part/alternative
                    text
                    multipart/mixed
                        html body chunk 1
                        pdf
                        html body chunk 2

         */
        List<Metadata> metadataList = getRecursiveMetadata("testRFC822-mixed-with-pdf-inline");
        assertEquals(2, metadataList.size());
        String body = metadataList.get(0).get(RecursiveParserWrapper.TIKA_CONTENT);
        assertContains("body 2", body);
        assertContains("body 3", body);
        assertNotContained("body 1", body);
        assertEquals("message/rfc822", metadataList.get(0).get(Metadata.CONTENT_TYPE));
        assertEquals("application/pdf", metadataList.get(1).get(Metadata.CONTENT_TYPE));
        assertEquals("/tzora-titan-4-hummer-xl-manual.pdf", metadataList.get(1).get(RecursiveParserWrapper.EMBEDDED_RESOURCE_PATH));
    }
}
