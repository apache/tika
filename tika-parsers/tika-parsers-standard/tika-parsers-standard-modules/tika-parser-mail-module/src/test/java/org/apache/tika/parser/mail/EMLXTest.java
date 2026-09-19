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

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import org.apache.tika.TikaTest;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Message;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;

/**
 * Apple Mail emlx: a byte-count line, the RFC822 message, then an XML plist.
 */
public class EMLXTest extends TikaTest {

    private static final String PLIST = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<!DOCTYPE plist PUBLIC \"-//Apple//DTD PLIST 1.0//EN\" " +
            "\"http://www.apple.com/DTDs/PropertyList-1.0.dtd\">\n" +
            "<plist version=\"1.0\">\n<dict>\n\t<key>date-received</key>\n" +
            "\t<integer>1698393810</integer>\n</dict>\n</plist>\n";

    @Test
    public void testLongHeaderBlockWithHtmlAlternative() throws Exception {
        XMLResult r = getXML("testEMLX_multipart_html.emlx");
        assertEquals("message/x-emlx", r.metadata.get(HttpHeaders.CONTENT_TYPE));
        assertEquals("Set the Foundation Straight", r.metadata.get(TikaCoreProperties.TITLE));
        assertEquals("hseldon@example.com", r.metadata.get(Message.MESSAGE_FROM_EMAIL));
        assertEquals("2015-09-26T16:30:20Z", r.metadata.get(TikaCoreProperties.CREATED));
        assertContains("The plan for the Foundation is in this message’s HTML part.", r.xml);
        assertPlistAndCountDropped(r.xml, "2803");
    }

    @Test
    public void testSinglePartHtml() throws Exception {
        XMLResult r = getXML("testEMLX_single_part_html.emlx");
        assertEquals("message/x-emlx", r.metadata.get(HttpHeaders.CONTENT_TYPE));
        assertEquals("Trial balance", r.metadata.get(TikaCoreProperties.TITLE));
        assertContains("The trial balance is ready for review.", r.xml);
        assertPlistAndCountDropped(r.xml, "446");
    }

    @Test
    public void testUnpaddedByteCount() throws Exception {
        String message = "Subject: Unpadded\nFrom: a@example.com\nTo: b@example.com\n\n" +
                "the body\n";
        String emlx = message.length() + "\n" + message + PLIST;
        try (TikaInputStream tis = TikaInputStream.get(emlx.getBytes(StandardCharsets.US_ASCII))) {
            XMLResult r = getXML(tis, new AutoDetectParser(), new Metadata());
            assertEquals("message/x-emlx", r.metadata.get(HttpHeaders.CONTENT_TYPE));
            assertEquals("Unpadded", r.metadata.get(TikaCoreProperties.TITLE));
            assertContains("the body", r.xml);
            assertPlistAndCountDropped(r.xml, Integer.toString(message.length()));
        }
    }

    @Test
    public void testForcedTypeStillStripsFraming() throws Exception {
        // an emlx handed to the parser as message/rfc822 must not leak the plist either
        String message = "Subject: Forced\nFrom: a@example.com\n\nthe body\n";
        String emlx = (message.length() + "      \n") + message + PLIST;
        Metadata metadata = new Metadata();
        metadata.set(HttpHeaders.CONTENT_TYPE, "message/rfc822");
        ParseContext context = new ParseContext();
        context.set(Parser.class, new AutoDetectParser());
        try (TikaInputStream tis = TikaInputStream.get(emlx.getBytes(StandardCharsets.US_ASCII))) {
            XMLResult r = getXML(tis, new RFC822Parser(), metadata, context);
            assertEquals("Forced", r.metadata.get(TikaCoreProperties.TITLE));
            assertContains("the body", r.xml);
            assertPlistAndCountDropped(r.xml, Integer.toString(message.length()));
        }
    }

    private static void assertPlistAndCountDropped(String xml, String byteCount) {
        String body = xml.substring(xml.indexOf("<body"));
        assertNotContained(byteCount, body);
        assertNotContained("date-received", body);
        assertNotContained("plist", body);
    }
}
