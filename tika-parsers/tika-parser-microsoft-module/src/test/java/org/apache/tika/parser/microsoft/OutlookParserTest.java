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
package org.apache.tika.parser.microsoft;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamResult;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.tika.TikaTest;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.metadata.Message;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Office;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.RecursiveParserWrapper;
import org.apache.tika.sax.BodyContentHandler;
import org.junit.Test;
import org.xml.sax.ContentHandler;

/**
 * Test case for parsing Outlook files.
 */
public class OutlookParserTest extends TikaTest {

    @Test
    public void testOutlookParsing() throws Exception {
        ContentHandler handler = new BodyContentHandler();
        Metadata metadata = new Metadata();

        try (InputStream stream = OutlookParserTest.class.getResourceAsStream(
                "/test-documents/test-outlook.msg")) {
            AUTO_DETECT_PARSER.parse(stream, handler, metadata, new ParseContext());
        }
        assertEquals(
                "application/vnd.ms-outlook",
                metadata.get(Metadata.CONTENT_TYPE));
        assertEquals(
                "Microsoft Outlook Express 6",
                metadata.get(TikaCoreProperties.TITLE));
        assertEquals(
                "Nouvel utilisateur de Outlook Express",
                metadata.get(Metadata.MESSAGE_RECIPIENT_ADDRESS));
        assertEquals(
                "L'\u00C9quipe Microsoft Outlook Express",
                metadata.get(TikaCoreProperties.CREATOR));

        //ensure that "raw" header is correctly decoded
        assertEquals(
                "L'\u00C9quipe Microsoft Outlook Express <msoe@microsoft.com>",
                metadata.get(Metadata.MESSAGE_RAW_HEADER_PREFIX+"From"));

        assertEquals("Nouvel utilisateur de Outlook Express",
                metadata.get(Message.MESSAGE_TO_EMAIL));

        assertEquals("",
                metadata.get(Message.MESSAGE_TO_NAME));

        assertEquals("Nouvel utilisateur de Outlook Express",
                metadata.get(Message.MESSAGE_TO_DISPLAY_NAME));

        // Stored as Thu, 5 Apr 2007 09:26:06 -0700
        assertEquals(
                "2007-04-05T16:26:06Z",
                metadata.get(TikaCoreProperties.CREATED));

        String content = handler.toString();
        assertContains("Microsoft Outlook Express 6", content);
        assertContains("L'\u00C9quipe Microsoft Outlook Express", content);
        assertContains("Nouvel utilisateur de Outlook Express", content);
        assertContains("Messagerie et groupes de discussion", content);
    }

    /**
     * Test case for TIKA-197
     *
     * @see <a href="https://issues.apache.org/jira/browse/TIKA-197">TIKA-197</a>
     */
    @Test
    public void testMultipleCopies() throws Exception {
        ContentHandler handler = new BodyContentHandler();
        Metadata metadata = new Metadata();

        try (InputStream stream = OutlookParserTest.class.getResourceAsStream(
                "/test-documents/testMSG.msg")) {
            AUTO_DETECT_PARSER.parse(stream, handler, metadata, new ParseContext());
        }

        assertEquals(
                "application/vnd.ms-outlook",
                metadata.get(Metadata.CONTENT_TYPE));

        String content = handler.toString();
        Pattern pattern = Pattern.compile("From");
        Matcher matcher = pattern.matcher(content);
        assertTrue(matcher.find());
        assertFalse(matcher.find());

        //test that last header is added
        assertContains("29 Jan 2009 19:17:10.0163 (UTC) FILETIME=[2ED25E30:01C98246]",
                Arrays.asList(metadata.getValues("Message:Raw-Header:X-OriginalArrivalTime")));
        //confirm next line is added correctly
        assertContains("from athena.apache.org (HELO athena.apache.org) (140.211.11.136)\n" +
                "    by apache.org (qpsmtpd/0.29) with ESMTP; Thu, 29 Jan 2009 11:17:08 -0800",
                Arrays.asList(metadata.getValues("Message:Raw-Header:Received")));
        assertEquals("EX", metadata.get(Office.MAPI_SENT_BY_SERVER_TYPE));
        assertEquals("NOTE", metadata.get(Office.MAPI_MESSAGE_CLASS));
        assertEquals("Jukka Zitting", metadata.get(Message.MESSAGE_FROM_NAME));
        assertEquals("jukka.zitting@gmail.com", metadata.get(Message.MESSAGE_FROM_EMAIL));
        assertEquals("Jukka Zitting", metadata.get(Office.MAPI_FROM_REPRESENTING_NAME));
        assertEquals("jukka.zitting@gmail.com", metadata.get(Office.MAPI_FROM_REPRESENTING_EMAIL));

        //to-name is empty, make sure that we get an empty string.
        assertEquals("tika-dev@lucene.apache.org", metadata.get(Message.MESSAGE_TO_EMAIL));
        assertEquals("tika-dev@lucene.apache.org", metadata.get(Message.MESSAGE_TO_DISPLAY_NAME));
        assertEquals("", metadata.get(Message.MESSAGE_TO_NAME));
    }

    /**
     * Test case for TIKA-395, to ensure parser works for new Outlook formats.
     *
     * @see <a href="https://issues.apache.org/jira/browse/TIKA-395">TIKA-395</a>
     */
    @Test
    public void testOutlookNew() throws Exception {
        ContentHandler handler = new BodyContentHandler();
        Metadata metadata = new Metadata();

        try (InputStream stream = OutlookParserTest.class.getResourceAsStream(
                "/test-documents/test-outlook2003.msg")) {
            AUTO_DETECT_PARSER.parse(stream, handler, metadata, new ParseContext());
        }
        assertEquals(
                "application/vnd.ms-outlook",
                metadata.get(Metadata.CONTENT_TYPE));
        assertEquals(
                "Welcome to Microsoft Office Outlook 2003",
                metadata.get(TikaCoreProperties.TITLE));

        String content = handler.toString();
        assertContains("Outlook 2003", content);
        assertContains("Streamlined Mail Experience", content);
        assertContains("Navigation Pane", content);

        //make sure these are parallel
        assertEquals("", metadata.get(Message.MESSAGE_TO_EMAIL));
        assertEquals("New Outlook User", metadata.get(Message.MESSAGE_TO_NAME));
        assertEquals("New Outlook User", metadata.get(Message.MESSAGE_TO_DISPLAY_NAME));

    }

    @Test
    public void testOutlookHTMLVersion() throws Exception {
        Metadata metadata = new Metadata();

        // Check the HTML version
        StringWriter sw = new StringWriter();
        SAXTransformerFactory factory = (SAXTransformerFactory)
                SAXTransformerFactory.newInstance();
        TransformerHandler handler = factory.newTransformerHandler();
        handler.getTransformer().setOutputProperty(OutputKeys.METHOD, "xml");
        handler.getTransformer().setOutputProperty(OutputKeys.INDENT, "yes");
        handler.setResult(new StreamResult(sw));

        try (InputStream stream = OutlookParserTest.class.getResourceAsStream(
                "/test-documents/testMSG_chinese.msg")) {
            AUTO_DETECT_PARSER.parse(stream, handler, metadata, new ParseContext());
        }

        // As the HTML version should have been processed, ensure
        //  we got some of the links
        String content = sw.toString();
        assertContains("<dd>tests.chang@fengttt.com</dd>", content);
        assertContains("<p>Alfresco MSG format testing", content);
        assertContains("<li>1", content);
        assertContains("<li>2", content);

        // Make sure we don't have nested html docs
        assertEquals(2, content.split("<body>").length);
        assertEquals(2, content.split("<\\/body>").length);

        // Make sure that the Chinese actually came through
        assertContains("\u5F35\u6BD3\u502B", metadata.get(TikaCoreProperties.CREATOR));
        assertContains("\u9673\u60E0\u73CD", content);

        assertEquals("tests.chang@fengttt.com", metadata.get(Message.MESSAGE_TO_EMAIL));

        assertEquals("Tests Chang@FT (張毓倫)", metadata.get(Office.MAPI_FROM_REPRESENTING_NAME));
        assertEquals("/O=FT GROUP/OU=FT/CN=RECIPIENTS/CN=LYDIACHANG",
                metadata.get(Office.MAPI_FROM_REPRESENTING_EMAIL));
    }

    @Test
    public void testOutlookForwarded() throws Exception {
        Metadata metadata = new Metadata();

        // Check the HTML version
        StringWriter sw = new StringWriter();
        SAXTransformerFactory factory = (SAXTransformerFactory)
                SAXTransformerFactory.newInstance();
        TransformerHandler handler = factory.newTransformerHandler();
        handler.getTransformer().setOutputProperty(OutputKeys.METHOD, "xml");
        handler.getTransformer().setOutputProperty(OutputKeys.INDENT, "yes");
        handler.setResult(new StreamResult(sw));

        try (InputStream stream = OutlookParserTest.class.getResourceAsStream(
                "/test-documents/testMSG_forwarded.msg")) {
            AUTO_DETECT_PARSER.parse(stream, handler, metadata, new ParseContext());
        }

        // Make sure we don't have nested docs
        String content = sw.toString();
        assertEquals(2, content.split("<body>").length);
        assertEquals(2, content.split("<\\/body>").length);
    }

    @Test
    public void testOutlookHTMLfromRTF() throws Exception {
        Metadata metadata = new Metadata();

        // Check the HTML version
        StringWriter sw = new StringWriter();
        SAXTransformerFactory factory = (SAXTransformerFactory)
                SAXTransformerFactory.newInstance();
        TransformerHandler handler = factory.newTransformerHandler();
        handler.getTransformer().setOutputProperty(OutputKeys.METHOD, "xml");
        handler.getTransformer().setOutputProperty(OutputKeys.INDENT, "yes");
        handler.setResult(new StreamResult(sw));

        try (InputStream stream = OutlookParserTest.class.getResourceAsStream(
                "/test-documents/test-outlook2003.msg")) {
            AUTO_DETECT_PARSER.parse(stream, handler, metadata, new ParseContext());
        }

        // As the HTML version should have been processed, ensure
        //  we got some of the links
        String content = sw.toString().replaceAll("[\\r\\n\\t]+", " ").replaceAll(" +", " ");
        assertContains("<dd>New Outlook User</dd>", content);
        assertContains("designed <i>to help you", content);
        assertContains("<p> <a href=\"http://r.office.microsoft.com/r/rlidOutlookWelcomeMail10?clid=1033\">Cached Exchange Mode</a>", content);

        // Link - check text around it, and the link itself
        assertContains("sign up for a free subscription", content);
        assertContains("Office Newsletter", content);
        assertContains("newsletter will be sent to you", content);
        assertContains("http://r.office.microsoft.com/r/rlidNewsletterSignUp?clid=1033", content);

        // Make sure we don't have nested html docs
        assertEquals(2, content.split("<body>").length);
        assertEquals(2, content.split("<\\/body>").length);
    }

    @Test
    public void testMAPIMessageClasses() throws Exception {

        for (String messageClass : new String[]{
                "Appointment", "Contact", "Post", "StickyNote", "Task"
        }) {
            testMsgClass(messageClass,
                    getXML("testMSG_" + messageClass + ".msg").metadata);
        }

        testMsgClass("NOTE", getXML("test-outlook2003.msg").metadata);

    }

    private void testMsgClass(String expected, Metadata metadata) {
        assertTrue(expected + ", but got: " + metadata.get(Office.MAPI_MESSAGE_CLASS),
                expected.equalsIgnoreCase(metadata.get(Office.MAPI_MESSAGE_CLASS).replaceAll("_", "")));
    }

    @Test
    public void testHandlingAllAlternativesBodies() throws Exception {
        //test that default only has one body
        List<Metadata> metadataList = getRecursiveMetadata("testMSG.msg");
        assertEquals(1, metadataList.size());
        assertContains("breaking your application",
                metadataList.get(0).get(RecursiveParserWrapper.TIKA_CONTENT));
        assertEquals("application/vnd.ms-outlook",
                metadataList.get(0).get(Metadata.CONTENT_TYPE));

        //now try extracting all bodies
        //they should each appear as standalone attachments
        //with no content in the body of the msg level
        TikaConfig tikaConfig = new TikaConfig(getResourceAsStream("tika-config-extract-all-alternatives-msg.xml"));
        Parser p = new AutoDetectParser(tikaConfig);

        metadataList = getRecursiveMetadata("testMSG.msg", p);
        assertEquals(3, metadataList.size());

        assertNotContained("breaking your application",
                metadataList.get(0).get(RecursiveParserWrapper.TIKA_CONTENT));
        assertEquals("application/vnd.ms-outlook",
                metadataList.get(0).get(Metadata.CONTENT_TYPE));

        assertContains("breaking your application",
                metadataList.get(1).get(RecursiveParserWrapper.TIKA_CONTENT));
        assertEquals("application/rtf",
                metadataList.get(1).get(Metadata.CONTENT_TYPE));

        assertContains("breaking your application",
                metadataList.get(2).get(RecursiveParserWrapper.TIKA_CONTENT));
        assertTrue(metadataList.get(2).get(Metadata.CONTENT_TYPE).startsWith("text/plain"));

    }
}
