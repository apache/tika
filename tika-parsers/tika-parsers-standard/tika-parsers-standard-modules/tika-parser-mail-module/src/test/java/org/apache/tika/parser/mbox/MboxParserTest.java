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
package org.apache.tika.parser.mbox;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xml.sax.ContentHandler;

import org.apache.tika.TikaTest;
import org.apache.tika.detect.TypeDetector;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;

public class MboxParserTest extends TikaTest {

    protected ParseContext recursingContext;
    private Parser autoDetectParser;
    private TypeDetector typeDetector;
    private MboxParser mboxParser;

    @BeforeEach
    public void setUp() throws Exception {
        typeDetector = new TypeDetector();
        autoDetectParser = new AutoDetectParser(typeDetector);
        recursingContext = new ParseContext();
        recursingContext.set(Parser.class, autoDetectParser);

        mboxParser = new MboxParser();
        mboxParser.setTracking(true);
    }

    @Test
    public void testSimple() throws Exception {
        ContentHandler handler = new BodyContentHandler();
        Metadata metadata = new Metadata();

        try (InputStream stream = getResourceAsStream("/test-documents/simple.mbox")) {
            mboxParser.parse(stream, handler, metadata, recursingContext);
        }

        String content = handler.toString();
        assertContains("Test content 1", content);
        assertContains("Test content 2", content);
        assertEquals("application/mbox", metadata.get(Metadata.CONTENT_TYPE));

        Map<Integer, Metadata> mailsMetadata = mboxParser.getTrackingMetadata();
        assertEquals(2, mailsMetadata.size(), "Nb. Of mails");

        Metadata mail1 = mailsMetadata.get(0);
        assertEquals("message/rfc822", mail1.get(Metadata.CONTENT_TYPE));
        assertEquals("envelope-sender-mailbox-name Mon Jun 01 10:00:00 2009",
                mail1.get("MboxParser-from"));

        Metadata mail2 = mailsMetadata.get(1);
        assertEquals("message/rfc822", mail2.get(Metadata.CONTENT_TYPE));
        assertEquals("envelope-sender-mailbox-name Mon Jun 01 11:00:00 2010",
                mail2.get("MboxParser-from"));
    }

    @Test
    public void testHeaders() throws Exception {
        ContentHandler handler = new BodyContentHandler();
        Metadata metadata = new Metadata();

        try (InputStream stream = getResourceAsStream("/test-documents/headers.mbox")) {
            mboxParser.parse(stream, handler, metadata, recursingContext);
        }

        assertContains("Test content", handler.toString());
        assertEquals(1, mboxParser.getTrackingMetadata().size(), "Nb. Of mails");

        Metadata mailMetadata = mboxParser.getTrackingMetadata().get(0);

        assertEquals("2009-06-10T03:58:45Z", mailMetadata.get(TikaCoreProperties.CREATED));
        assertEquals("<author@domain.com>", mailMetadata.get(TikaCoreProperties.CREATOR));
        assertEquals("subject", mailMetadata.get(TikaCoreProperties.SUBJECT));
        assertEquals("message/rfc822", mailMetadata.get(Metadata.CONTENT_TYPE));
        assertEquals("author@domain.com", mailMetadata.get("Message-From"));
        assertEquals("<name@domain.com>", mailMetadata.get("MboxParser-return-path"));
    }

    @Test
    public void testMultilineHeader() throws Exception {
        ContentHandler handler = new BodyContentHandler();
        Metadata metadata = new Metadata();

        try (InputStream stream = getResourceAsStream("/test-documents/multiline.mbox")) {
            mboxParser.parse(stream, handler, metadata, recursingContext);
        }

        assertEquals(1, mboxParser.getTrackingMetadata().size(), "Nb. Of mails");

        Metadata mailMetadata = mboxParser.getTrackingMetadata().get(0);
        assertEquals("from xxx by xxx with xxx; date", mailMetadata.get("MboxParser-received"));
    }

    @Test
    public void testMultilineHeader2() throws Exception {
        //make sure that we aren't injecting body content into headers
        for (Metadata m : getRecursiveMetadata("multiline2.mbox")) {
            for (String mime : m.getValues(Metadata.CONTENT_TYPE)) {
                assertFalse("something".equals(mime));
            }
        }
    }

    @Test
    public void testQuoted() throws Exception {
        ContentHandler handler = new BodyContentHandler();
        Metadata metadata = new Metadata();

        try (InputStream stream = getResourceAsStream("/test-documents/quoted.mbox")) {
            mboxParser.parse(stream, handler, metadata, recursingContext);
        }

        assertContains("Test content", handler.toString());
        assertContains("> quoted stuff", handler.toString());
    }

    @Test
    public void testComplex() throws Exception {
        ContentHandler handler = new BodyContentHandler();
        Metadata metadata = new Metadata();

        try (InputStream stream = getResourceAsStream("/test-documents/complex.mbox")) {
            mboxParser.parse(stream, handler, metadata, recursingContext);
        }

        assertEquals(3, mboxParser.getTrackingMetadata().size(), "Nb. Of mails");

        Metadata firstMail = mboxParser.getTrackingMetadata().get(0);
        assertEquals("Re: question about when shuffle/sort start working",
                firstMail.get(TikaCoreProperties.SUBJECT));
        assertEquals("Re: question about when shuffle/sort start working",
                firstMail.get(TikaCoreProperties.TITLE));
        assertEquals("Jothi Padmanabhan <jothipn@yahoo-inc.com>",
                firstMail.get(TikaCoreProperties.CREATOR));
        assertEquals("core-user@hadoop.apache.org",
                firstMail.get(Metadata.MESSAGE_RECIPIENT_ADDRESS));

        assertContains("When a Mapper completes", handler.toString());
    }

    @Test
    public void testTika2478() throws Exception {
        List<Metadata> metadataList = getRecursiveMetadata("testMBOX_complex.mbox");
        assertEquals(2, metadataList.size());
        assertEquals("application/mbox", metadataList.get(0).get(Metadata.CONTENT_TYPE));
        assertEquals("message/rfc822", metadataList.get(1).get(Metadata.CONTENT_TYPE));
        assertContains("body 2", metadataList.get(1).get(TikaCoreProperties.TIKA_CONTENT));
        assertNotContained("body 1", metadataList.get(1).get(TikaCoreProperties.TIKA_CONTENT));
    }
}
