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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import junit.framework.TestCase;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
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
            parser.parse(stream, handler, metadata);
        } finally {
            stream.close();
        }

        assertEquals(
                "application/vnd.ms-outlook",
                metadata.get(Metadata.CONTENT_TYPE));
        assertEquals(
                "Microsoft Outlook Express 6",
                metadata.get(Metadata.TITLE));
        assertEquals(
                "Nouvel utilisateur de Outlook Express",
                metadata.get(Metadata.MESSAGE_RECIPIENT_ADDRESS));
        assertEquals(
                "L'\u00C9quipe Microsoft Outlook Express",
                metadata.get(Metadata.AUTHOR));

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
            parser.parse(stream, handler, metadata);
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
            parser.parse(stream, handler, metadata);
        } finally {
            stream.close();
        }

        assertEquals(
                "application/vnd.ms-outlook",
                metadata.get(Metadata.CONTENT_TYPE));
        assertEquals(
                "Welcome to Microsoft Office Outlook 2003",
                metadata.get(Metadata.TITLE));

        String content = handler.toString();
        assertTrue(content.contains("Outlook 2003"));
        assertTrue(content.contains("Streamlined Mail Experience"));
        assertTrue(content.contains("Navigation Pane"));
    }

}
