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

import java.io.InputStream;
import java.io.StringWriter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamResult;

import junit.framework.TestCase;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.xml.sax.ContentHandler;

/**
 * Test case for parsing Outlook files.
 */
public class OutlookParserTest extends TestCase {

    public void testOutlookParsing() throws Exception {
        Parser parser = new AutoDetectParser(); // Should auto-detect!
        ContentHandler handler = new BodyContentHandler();
        Metadata metadata = new Metadata();

        InputStream stream = OutlookParserTest.class.getResourceAsStream(
                "/test-documents/test-outlook.msg");
        try {
            parser.parse(stream, handler, metadata, new ParseContext());
        } finally {
            stream.close();
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
        assertEquals(
                "L'\u00C9quipe Microsoft Outlook Express",
                metadata.get(Metadata.AUTHOR));
        
        // Stored as Thu, 5 Apr 2007 09:26:06 -0700
        assertEquals(
                "2007-04-05T16:26:06Z",
                metadata.get(TikaCoreProperties.CREATED));

        String content = handler.toString();
        assertTrue(content.contains(""));
        assertTrue(content.contains("Microsoft Outlook Express 6"));
        assertTrue(content.contains("L'\u00C9quipe Microsoft Outlook Express"));
        assertTrue(content.contains("Nouvel utilisateur de Outlook Express"));
        assertTrue(content.contains("Messagerie et groupes de discussion"));
    }

    /**
     * Test case for TIKA-197
     *
     * @see <a href="https://issues.apache.org/jira/browse/TIKA-197">TIKA-197</a>
     */
    public void testMultipleCopies() throws Exception {
        Parser parser = new AutoDetectParser();
        ContentHandler handler = new BodyContentHandler();
        Metadata metadata = new Metadata();

        InputStream stream = OutlookParserTest.class.getResourceAsStream(
                "/test-documents/testMSG.msg");
        try {
            parser.parse(stream, handler, metadata, new ParseContext());
        } finally {
            stream.close();
        }

        assertEquals(
                "application/vnd.ms-outlook",
                metadata.get(Metadata.CONTENT_TYPE));

        String content = handler.toString();
        Pattern pattern = Pattern.compile("From");
        Matcher matcher = pattern.matcher(content);
        assertTrue(matcher.find());
        assertFalse(matcher.find());
    }

    /**
     * Test case for TIKA-395, to ensure parser works for new Outlook formats. 
     *
     * @see <a href="https://issues.apache.org/jira/browse/TIKA-395">TIKA-395</a>
     */
    public void testOutlookNew() throws Exception {
        Parser parser = new AutoDetectParser();
        ContentHandler handler = new BodyContentHandler();
        Metadata metadata = new Metadata();

        InputStream stream = OutlookParserTest.class.getResourceAsStream(
                "/test-documents/test-outlook2003.msg");
        try {
            parser.parse(stream, handler, metadata, new ParseContext());
        } finally {
            stream.close();
        }

        assertEquals(
                "application/vnd.ms-outlook",
                metadata.get(Metadata.CONTENT_TYPE));
        assertEquals(
                "Welcome to Microsoft Office Outlook 2003",
                metadata.get(TikaCoreProperties.TITLE));

        String content = handler.toString();
        assertTrue(content.contains("Outlook 2003"));
        assertTrue(content.contains("Streamlined Mail Experience"));
        assertTrue(content.contains("Navigation Pane"));
    }
     
    public void testOutlookHTMLVersion() throws Exception {
        Parser parser = new AutoDetectParser();
        Metadata metadata = new Metadata();
       
        // Check the HTML version
        StringWriter sw = new StringWriter();
        SAXTransformerFactory factory = (SAXTransformerFactory)
                 SAXTransformerFactory.newInstance();
        TransformerHandler handler = factory.newTransformerHandler();
        handler.getTransformer().setOutputProperty(OutputKeys.METHOD, "xml");
        handler.getTransformer().setOutputProperty(OutputKeys.INDENT, "yes");
        handler.setResult(new StreamResult(sw));

        InputStream stream = OutlookParserTest.class.getResourceAsStream(
               "/test-documents/testMSG_chinese.msg");
        try {
           parser.parse(stream, handler, metadata, new ParseContext());
        } finally {
           stream.close();
        }
         
        // As the HTML version should have been processed, ensure
        //  we got some of the links
        String content = sw.toString();
        assertTrue(content.contains("<dd>tests.chang@fengttt.com</dd>"));
        assertTrue(content.contains("<p>Alfresco MSG format testing"));
        assertTrue(content.contains("<li>1"));
        assertTrue(content.contains("<li>2"));
        
        // Make sure we don't have nested html docs
        assertEquals(2, content.split("<body>").length);
        assertEquals(2, content.split("<\\/body>").length);
    }

    public void testOutlookForwarded() throws Exception {
        Parser parser = new AutoDetectParser();
        Metadata metadata = new Metadata();
       
        // Check the HTML version
        StringWriter sw = new StringWriter();
        SAXTransformerFactory factory = (SAXTransformerFactory)
                 SAXTransformerFactory.newInstance();
        TransformerHandler handler = factory.newTransformerHandler();
        handler.getTransformer().setOutputProperty(OutputKeys.METHOD, "xml");
        handler.getTransformer().setOutputProperty(OutputKeys.INDENT, "yes");
        handler.setResult(new StreamResult(sw));

        InputStream stream = OutlookParserTest.class.getResourceAsStream(
               "/test-documents/testMSG_forwarded.msg");
        try {
           parser.parse(stream, handler, metadata, new ParseContext());
        } finally {
           stream.close();
        }
         
        // Make sure we don't have nested docs
        String content = sw.toString();
        assertEquals(2, content.split("<body>").length);
        assertEquals(2, content.split("<\\/body>").length);
    }
    
    public void testOutlookHTMLfromRTF() throws Exception {
        Parser parser = new AutoDetectParser();
        Metadata metadata = new Metadata();
       
        // Check the HTML version
        StringWriter sw = new StringWriter();
        SAXTransformerFactory factory = (SAXTransformerFactory)
                 SAXTransformerFactory.newInstance();
        TransformerHandler handler = factory.newTransformerHandler();
        handler.getTransformer().setOutputProperty(OutputKeys.METHOD, "xml");
        handler.getTransformer().setOutputProperty(OutputKeys.INDENT, "yes");
        handler.setResult(new StreamResult(sw));

        InputStream stream = OutlookParserTest.class.getResourceAsStream(
                "/test-documents/test-outlook2003.msg");
        try {
           parser.parse(stream, handler, metadata, new ParseContext());
        } finally {
           stream.close();
        }
         
        // As the HTML version should have been processed, ensure
        //  we got some of the links
        String content = sw.toString().replaceAll("<p>\\s+","<p>");
        assertTrue(content.contains("<dd>New Outlook User</dd>"));
        assertTrue(content.contains("designed <i>to help you"));
        assertTrue(content.contains("<p><a href=\"http://r.office.microsoft.com/r/rlidOutlookWelcomeMail10?clid=1033\">Cached Exchange Mode</a>"));
        
        // Link - check text around it, and the link itself
        assertTrue(content.contains("sign up for a free subscription"));
        assertTrue(content.contains("Office Newsletter"));
        assertTrue(content.contains("newsletter will be sent to you"));
        assertTrue(content.contains("http://r.office.microsoft.com/r/rlidNewsletterSignUp?clid=1033"));
        
        // Make sure we don't have nested html docs
        assertEquals(2, content.split("<body>").length);
        //assertEquals(2, content.split("<\\/body>").length); // TODO Fix
    }
}
