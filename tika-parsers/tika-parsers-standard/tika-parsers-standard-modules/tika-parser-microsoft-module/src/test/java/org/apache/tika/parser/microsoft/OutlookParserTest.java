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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamResult;

import org.junit.jupiter.api.Test;
import org.xml.sax.ContentHandler;

import org.apache.tika.TikaTest;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.metadata.MAPI;
import org.apache.tika.metadata.Message;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.RTFMetadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BasicContentHandlerFactory;
import org.apache.tika.sax.BodyContentHandler;
import org.apache.tika.utils.XMLReaderUtils;

/**
 * Test case for parsing Outlook files.
 */
public class OutlookParserTest extends TikaTest {

    @Test
    public void testOutlookParsing() throws Exception {

        ContentHandler handler = new BodyContentHandler();
        Metadata metadata = new Metadata();

        try (InputStream stream = getResourceAsStream("/test-documents/test-outlook.msg")) {
            AUTO_DETECT_PARSER.parse(stream, handler, metadata, new ParseContext());
        }
        assertEquals("application/vnd.ms-outlook", metadata.get(Metadata.CONTENT_TYPE));
        assertEquals("Microsoft Outlook Express 6", metadata.get(TikaCoreProperties.TITLE));
        assertEquals("Nouvel utilisateur de Outlook Express",
                metadata.get(Metadata.MESSAGE_RECIPIENT_ADDRESS));
        assertEquals("L'\u00C9quipe Microsoft Outlook Express",
                metadata.get(TikaCoreProperties.CREATOR));

        //ensure that "raw" header is correctly decoded
        assertEquals("L'\u00C9quipe Microsoft Outlook Express <msoe@microsoft.com>",
                metadata.get(Metadata.MESSAGE_RAW_HEADER_PREFIX + "From"));

        assertEquals("Nouvel utilisateur de Outlook Express",
                metadata.get(Message.MESSAGE_TO_EMAIL));

        assertEquals("", metadata.get(Message.MESSAGE_TO_NAME));

        assertEquals("Nouvel utilisateur de Outlook Express",
                metadata.get(Message.MESSAGE_TO_DISPLAY_NAME));

        // Stored as Thu, 5 Apr 2007 09:26:06 -0700
        assertEquals("2007-04-05T16:26:06Z", metadata.get(TikaCoreProperties.CREATED));

        String content = handler.toString();
        assertTrue(content.startsWith("Microsoft Outlook Express 6"));
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

        try (InputStream stream = getResourceAsStream("/test-documents/testMSG.msg")) {
            AUTO_DETECT_PARSER.parse(stream, handler, metadata, new ParseContext());
        }

        assertEquals("application/vnd.ms-outlook", metadata.get(Metadata.CONTENT_TYPE));

        String content = handler.toString();
        Pattern pattern = Pattern.compile("From");
        Matcher matcher = pattern.matcher(content);
        assertTrue(matcher.find());

        //test that last header is added
        assertContains("29 Jan 2009 19:17:10.0163 (UTC) FILETIME=[2ED25E30:01C98246]",
                Arrays.asList(metadata.getValues("Message:Raw-Header:X-OriginalArrivalTime")));
        //confirm next line is added correctly
        assertContains("from athena.apache.org (HELO athena.apache.org) (140.211.11.136)\n" +
                        "    by apache.org (qpsmtpd/0.29) with ESMTP; Thu, 29 Jan 2009 11:17:08 " +
                        "-0800",
                Arrays.asList(metadata.getValues("Message:Raw-Header:Received")));
        assertEquals("EX", metadata.get(MAPI.SENT_BY_SERVER_TYPE));
        assertEquals("NOTE", metadata.get(MAPI.MESSAGE_CLASS));
        assertEquals("Jukka Zitting", metadata.get(Message.MESSAGE_FROM_NAME));
        assertEquals("jukka.zitting@gmail.com", metadata.get(Message.MESSAGE_FROM_EMAIL));
        assertEquals("Jukka Zitting", metadata.get(MAPI.FROM_REPRESENTING_NAME));
        assertEquals("jukka.zitting@gmail.com", metadata.get(MAPI.FROM_REPRESENTING_EMAIL));

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

        try (InputStream stream = getResourceAsStream("/test-documents/test-outlook2003.msg")) {
            AUTO_DETECT_PARSER.parse(stream, handler, metadata, new ParseContext());
        }
        assertEquals("application/vnd.ms-outlook", metadata.get(Metadata.CONTENT_TYPE));
        assertEquals("Welcome to Microsoft Office Outlook 2003",
                metadata.get(TikaCoreProperties.TITLE));
        assertEquals("Welcome to Microsoft Office Outlook 2003",
                metadata.get(TikaCoreProperties.SUBJECT));
        assertEquals("Welcome to Microsoft Office Outlook 2003",
                metadata.get(TikaCoreProperties.DESCRIPTION));

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
        SAXTransformerFactory factory = XMLReaderUtils.getSAXTransformerFactory();
        TransformerHandler handler = factory.newTransformerHandler();
        handler.getTransformer().setOutputProperty(OutputKeys.METHOD, "xml");
        handler.getTransformer().setOutputProperty(OutputKeys.INDENT, "yes");
        handler.setResult(new StreamResult(sw));

        try (InputStream stream = getResourceAsStream("/test-documents/testMSG_chinese.msg")) {
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

        assertEquals("Tests Chang@FT (張毓倫)", metadata.get(MAPI.FROM_REPRESENTING_NAME));
        assertEquals("/O=FT GROUP/OU=FT/CN=RECIPIENTS/CN=LYDIACHANG",
                metadata.get(MAPI.FROM_REPRESENTING_EMAIL));

        assertEquals("c=TW;a= ;p=FT GROUP;l=FTM02-110329085248Z-89735\u0000",
                metadata.get(MAPI.SUBMISSION_ID));
        assertEquals("<EBB9951D34EA4B41B70AB946CF3FB6EC1A297D98@ftm02.FT.FTG.COM>",
                metadata.get(MAPI.INTERNET_MESSAGE_ID));
        assertTrue(metadata.get(MAPI.SUBMISSION_ACCEPTED_AT_TIME).startsWith("2011-03-29"));
        assertTrue(metadata.get("mapi:client-submit-time").startsWith("2011-03-29"));
        assertTrue(metadata.get("mapi:message-delivery-time").startsWith("2011-03-29"));
        assertTrue(metadata.get("mapi:last-modification-time").startsWith("2011-03-29"));
        assertTrue(metadata.get("mapi:creation-time").startsWith("2011-03-29"));
    }

    @Test
    public void testOutlookForwarded() throws Exception {
        Metadata metadata = new Metadata();

        // Check the HTML version
        StringWriter sw = new StringWriter();
        SAXTransformerFactory factory = XMLReaderUtils.getSAXTransformerFactory();
        TransformerHandler handler = factory.newTransformerHandler();
        handler.getTransformer().setOutputProperty(OutputKeys.METHOD, "xml");
        handler.getTransformer().setOutputProperty(OutputKeys.INDENT, "yes");
        handler.setResult(new StreamResult(sw));

        try (InputStream stream = getResourceAsStream("/test-documents/testMSG_forwarded.msg")) {
            AUTO_DETECT_PARSER.parse(stream, handler, metadata, new ParseContext());
        }

        // Make sure we don't have nested docs
        String content = sw.toString();
        assertEquals(2, content.split("<body>").length);
        assertEquals(2, content.split("<\\/body>").length);
        assertEquals("01ccb5408a75b6cf3ad7837949b698499034202313ef000002a160", metadata.get(MAPI.CONVERSATION_INDEX));
        assertEquals("<C8508767C15DBF40A21693142739EA8D564D18FDA1@EXVMBX018-1.exch018.msoutlookonline.net>",
                metadata.get(MAPI.INTERNET_REFERENCES));
        assertEquals("<C8508767C15DBF40A21693142739EA8D564D18FDA1@EXVMBX018-1.exch018.msoutlookonline.net>",
                metadata.get(MAPI.IN_REPLY_TO_ID));

        assertEquals("true", metadata.get(RTFMetadata.CONTAINS_ENCAPSULATED_HTML));
    }

    @Test
    public void testEmbeddedPath() throws Exception {
        List<Metadata> metadataList = getRecursiveMetadata("testMSG_att_msg.msg");
        assertEquals("/Test Attachment.msg", metadataList.get(1).get(TikaCoreProperties.EMBEDDED_RESOURCE_PATH));
        assertEquals("/smbprn.00009008.KdcPjl.pdf", metadataList.get(2).get(TikaCoreProperties.EMBEDDED_RESOURCE_PATH));
        assertEquals("true", metadataList.get(0).get(RTFMetadata.CONTAINS_ENCAPSULATED_HTML));
    }

    @Test
    public void testOutlookHTMLfromRTF() throws Exception {

        //test default behavior
        List<Metadata> metadataList = getRecursiveMetadata("test-outlook2003.msg");
        assertContains("<dd>New Outlook User</dd>", metadataList.get(0).get(TikaCoreProperties.TIKA_CONTENT));

        //test legacy behavior with the configuration set
        Metadata metadata = new Metadata();

        // Check the HTML version
        StringWriter sw = new StringWriter();
        SAXTransformerFactory factory = XMLReaderUtils.getSAXTransformerFactory();
        TransformerHandler handler = factory.newTransformerHandler();
        handler.getTransformer().setOutputProperty(OutputKeys.METHOD, "xml");
        handler.getTransformer().setOutputProperty(OutputKeys.INDENT, "yes");
        handler.setResult(new StreamResult(sw));

        try (InputStream stream = getResourceAsStream("/test-documents/test-outlook2003.msg")) {
            AUTO_DETECT_PARSER.parse(stream, handler, metadata, new ParseContext());
        }

        // As the HTML version should have been processed, ensure
        //  we got some of the links
        String content = sw.toString().replaceAll("[\\r\\n\\t]+", " ").replaceAll(" +", " ");
        assertContains("<dd>New Outlook User</dd>", content);
        assertContains("designed <i>to help you", content);
        assertContains(
                "<p> <a href=\"http://r.office.microsoft.com/r/rlidOutlookWelcomeMail10?clid=1033\">Cached Exchange Mode</a>",
                content);

        // Link - check text around it, and the link itself
        assertContains("sign up for a free subscription", content);
        assertContains("Office Newsletter", content);
        assertContains("newsletter will be sent to you", content);
        assertContains("http://r.office.microsoft.com/r/rlidNewsletterSignUp?clid=1033", content);

        // Make sure we don't have nested html docs
        assertEquals(2, content.split("<body>").length);
        assertEquals(2, content.split("<\\/body>").length);

        assertEquals("true", metadata.get(RTFMetadata.CONTAINS_ENCAPSULATED_HTML));
    }

    @Test
    public void testMAPIMessageClasses() throws Exception {

        for (String messageClass : new String[]{"Appointment", "Contact", "Post", "StickyNote",
                "Task"}) {
            testMsgClass(messageClass, getXML("testMSG_" + messageClass + ".msg").metadata);
        }

        testMsgClass("NOTE", getXML("test-outlook2003.msg").metadata);
    }

    private void testMsgClass(String expected, Metadata metadata) {
        assertTrue(expected.equalsIgnoreCase(
                        metadata.get(MAPI.MESSAGE_CLASS).replaceAll("_", "")),
                expected + ", but got: " + metadata.get(MAPI.MESSAGE_CLASS));
    }

    @Test
    public void testAppointmentExtendedMetadata() throws Exception {
        OfficeParserConfig officeParserConfig = new OfficeParserConfig();
        officeParserConfig.setExtractExtendedMsgProperties(true);
        ParseContext parseContext = new ParseContext();
        parseContext.set(OfficeParserConfig.class, officeParserConfig);

        List<Metadata> metadataList = getRecursiveMetadata("testMSG_Appointment.msg", parseContext);
        Metadata m = metadataList.get(0);
        assertTrue(m.get("mapi:property:PidLidAppointmentEndWhole").contains("2017-02-28T19"));
        assertTrue(m.get("mapi:property:PidLidAppointmentStartWhole").contains("2017-02-28T18"));
        assertTrue(m.get("mapi:property:PidLidClipStart").contains("2017-02-28T18"));
        assertTrue(m.get("mapi:property:PidLidClipEnd").contains("2017-02-28T19"));
        assertTrue(m.get("mapi:property:PidLidCommonStart").contains("2017-02-28T18"));
        assertTrue(m.get("mapi:property:PidLidCommonEnd").contains("2017-02-28T19"));
        assertTrue(m.get("mapi:property:PidLidReminderSignalTime").contains("4501-01-01T00"));
        assertTrue(m.get("mapi:property:PidLidReminderTime").contains("2017-02-28T18"));
        assertTrue(m.get("mapi:property:PidLidValidFlagStringProof").contains("2017-02-28T18:42"));
        assertEquals("0", m.get("mapi:property:PidLidAppointmentSequence"));
        assertEquals("false", m.get("mapi:property:PidLidRecurring"));
        assertEquals("true", m.get(RTFMetadata.CONTAINS_ENCAPSULATED_HTML));

    }

    @Test
    public void testTaskExtendedMetadata() throws Exception {
        OfficeParserConfig officeParserConfig = new OfficeParserConfig();
        officeParserConfig.setExtractExtendedMsgProperties(true);
        ParseContext parseContext = new ParseContext();
        parseContext.set(OfficeParserConfig.class, officeParserConfig);
        List<Metadata> metadataList = getRecursiveMetadata("testMSG_Task.msg", parseContext);
        Metadata m = metadataList.get(0);
        assertTrue(m.get("mapi:property:PidLidToDoOrdinalDate").contains("2017-02-28T18:44"));
        assertTrue(m.get("mapi:property:PidLidValidFlagStringProof").contains("2017-02-28T18:44"));
        assertEquals("0", m.get("mapi:property:PidLidTaskActualEffort"));
        assertEquals("false", m.get("mapi:property:PidLidTeamTask"));
    }

    @Test
    public void testContactExtendedMetadata() throws Exception {
        OfficeParserConfig officeParserConfig = new OfficeParserConfig();
        officeParserConfig.setExtractExtendedMsgProperties(true);
        ParseContext parseContext = new ParseContext();
        parseContext.set(OfficeParserConfig.class, officeParserConfig);

        List<Metadata> metadataList = getRecursiveMetadata("testMSG_Contact.msg", parseContext);
        Metadata m = metadataList.get(0);
        assertEquals("2017-02-28T18:41:37Z", m.get("mapi:property:PidLidValidFlagStringProof"));
    }


    @Test
    public void testPostExtendedMetadata() throws Exception {
        OfficeParserConfig officeParserConfig = new OfficeParserConfig();
        officeParserConfig.setExtractExtendedMsgProperties(true);
        ParseContext parseContext = new ParseContext();
        parseContext.set(OfficeParserConfig.class, officeParserConfig);

        List<Metadata> metadataList = getRecursiveMetadata("testMSG_Post.msg", parseContext);
        Metadata m = metadataList.get(0);
        assertEquals("2017-02-28T18:47:11Z", m.get("mapi:property:PidLidValidFlagStringProof"));
    }


    @Test
    public void testHandlingAllAlternativesBodies() throws Exception {
        //test that default only has one body
        List<Metadata> metadataList = getRecursiveMetadata("testMSG.msg");
        assertEquals(1, metadataList.size());
        assertContains("breaking your application",
                metadataList.get(0).get(TikaCoreProperties.TIKA_CONTENT));
        assertEquals("application/vnd.ms-outlook", metadataList.get(0).get(Metadata.CONTENT_TYPE));

        //now try extracting all bodies
        //they should each appear as standalone attachments
        //with no content in the body of the msg level
        try (InputStream is = getResourceAsStream("tika-config-extract-all-alternatives-msg.xml")) {
            TikaConfig tikaConfig = new TikaConfig(is);
            Parser p = new AutoDetectParser(tikaConfig);

            metadataList = getRecursiveMetadata("testMSG.msg", p);
            assertEquals(3, metadataList.size());

            assertNotContained("breaking your application",
                    metadataList.get(0).get(TikaCoreProperties.TIKA_CONTENT));
            assertEquals("application/vnd.ms-outlook",
                    metadataList.get(0).get(Metadata.CONTENT_TYPE));

            assertContains("breaking your application",
                    metadataList.get(1).get(TikaCoreProperties.TIKA_CONTENT));
            assertEquals("application/rtf", metadataList.get(1).get(Metadata.CONTENT_TYPE));

            assertContains("breaking your application",
                    metadataList.get(2).get(TikaCoreProperties.TIKA_CONTENT));
            assertTrue(metadataList.get(2).get(Metadata.CONTENT_TYPE).startsWith("text/plain"));
        }

    }

    @Test
    public void testNewlinesInRTFBody() throws Exception {
        List<Metadata> metadataList = getRecursiveMetadata("test-outlook.msg", AUTO_DETECT_PARSER,
                BasicContentHandlerFactory.HANDLER_TYPE.BODY);
        assertContains("annuaires\t \n" + " Synchronisation", metadataList.get(0).get(TikaCoreProperties.TIKA_CONTENT));
    }

    @Test
    public void testHeadersInBody() throws Exception {
        //test default behavior -- headers
        ParseContext parseContext = new ParseContext();
        String xml = getText("testMSG.msg", new Metadata(), parseContext);
        xml = xml.replaceAll("\\s+", " ");
        assertTrue(xml.startsWith("MIME registry use cases"));
        assertContains("From Jukka Zitting", xml);

        //test configurable behavior
        OfficeParserConfig officeParserConfig = new OfficeParserConfig();
        officeParserConfig.setWriteSelectHeadersInBody(false);
        parseContext.set(OfficeParserConfig.class, officeParserConfig);
        xml = getText("testMSG.msg", new Metadata(), parseContext);
        assertTrue(xml.startsWith("Hi,"));
    }

}
