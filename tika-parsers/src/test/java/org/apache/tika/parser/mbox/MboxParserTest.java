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

import static org.apache.tika.TikaTest.assertContains;
import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import org.apache.http.annotation.NotThreadSafe;
import org.apache.tika.detect.Detector;
import org.apache.tika.detect.TypeDetector;
import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.extractor.ParsingEmbeddedDocumentExtractor;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.junit.Test;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/** Contains tests for the {@link MboxParser} */
public class MboxParserTest {
    private static InputStream getStream(String name) {
        return MboxParserTest.class.getClass().getResourceAsStream(name);
    }
    
    /** Container for results of parsing */
    interface ParseResults {
        /** @return Number of emails parsed */
        int getNumParsedEmails();
        /** @return {@link Metadata} for the nth email */
        Metadata getMetadataForEmail(int n);
        /** @return Original {@link Metadata} */
        Metadata getMetadata();
        /** @return Body content */
        String getBody();
    }
    
    /** Helper class which tracks the Mbox records parsed by the {@link MboxParser} */
    @NotThreadSafe
    private static class TrackingEmbeddedDocumentExtractor extends ParsingEmbeddedDocumentExtractor {
        private int currentEmailCount = 0;
        private final Map<Integer, Metadata> metadata = new HashMap<>();
        
        private TrackingEmbeddedDocumentExtractor(ParseContext context) {
            super(context);
        }
        
        @Override
        public void parseEmbedded(
                InputStream stream, ContentHandler handler, Metadata metadata, boolean outputHtml)
                throws SAXException, IOException {
            super.parseEmbedded(stream, handler, metadata, outputHtml);
            
            // Only register the metadata for the email if we're called from the MboxParser
            if (metadata.get(MboxParser.EMAIL_FROMLINE_METADATA) != null) {
                this.metadata.put(currentEmailCount++, metadata);
            }
        }
    }
    
    /** 
     * Parse the given {@link InputStream} with an {@link MboxParser}
     * and return {@link ParseResults}
     */
    private ParseResults parse(InputStream stream) throws IOException, TikaException, SAXException {
        Detector typeDetector = new TypeDetector();
        AutoDetectParser autoDetectParser = new AutoDetectParser(typeDetector);
        ParseContext context = new ParseContext();
        final TrackingEmbeddedDocumentExtractor trackingExtractor = new TrackingEmbeddedDocumentExtractor(context);
        context.set(Parser.class, autoDetectParser);
        context.set(EmbeddedDocumentExtractor.class, trackingExtractor);
        
        final Metadata metadata = new Metadata();
        final BodyContentHandler handler = new BodyContentHandler();
        
        new MboxParser().parse(stream, handler, metadata, context);
        
        return new ParseResults() {
            @Override
            public int getNumParsedEmails() {
                return trackingExtractor.metadata.size();
            }

            @Override
            public Metadata getMetadataForEmail(int n) {
                return trackingExtractor.metadata.get(n);
            }
            
            @Override
            public Metadata getMetadata() {
                return metadata;
            }

            @Override
            public String getBody() {
                return handler.toString();
            }
        };
    }

    /** Test parsing a simple mbox file */
    @Test
    public void testSimple() throws IOException, TikaException, SAXException {
        ParseResults results;
        try (InputStream stream = getStream("/test-documents/simple.mbox")) {
            results = parse(stream);
        }
        
        assertContains("Test content 1", results.getBody());
        assertContains("Test content 2", results.getBody());
        assertEquals("application/mbox", results.getMetadata().get(Metadata.CONTENT_TYPE));

        assertEquals("Number of emails", 2, results.getNumParsedEmails());

        Metadata mail1 = results.getMetadataForEmail(0);
        assertEquals("message/rfc822", mail1.get(Metadata.CONTENT_TYPE));
        assertEquals("envelope-sender-mailbox-name Mon Jun 01 10:00:00 2009", mail1.get("MboxParser-from"));

        Metadata mail2 = results.getMetadataForEmail(1);
        assertEquals("message/rfc822", mail2.get(Metadata.CONTENT_TYPE));
        assertEquals("envelope-sender-mailbox-name Mon Jun 01 11:00:00 2010", mail2.get("MboxParser-from"));
    }

    /** Test that headers are captured properly */
    @Test
    public void testHeaders() throws IOException, TikaException, SAXException {
        ParseResults results;
        try (InputStream stream = getStream("/test-documents/headers.mbox")) {
            results = parse(stream);
        }

        assertContains("Test content", results.getBody());
        assertEquals("Number of emails", 1, results.getNumParsedEmails());

        Metadata mailMetadata = results.getMetadataForEmail(0);

        assertEquals("2009-06-10T03:58:45Z", mailMetadata.get(TikaCoreProperties.CREATED));
        assertEquals("<author@domain.com>", mailMetadata.get(TikaCoreProperties.CREATOR));
        assertEquals("subject", mailMetadata.get(Metadata.SUBJECT));
        assertEquals("<author@domain.com>", mailMetadata.get(Metadata.AUTHOR));
        assertEquals("message/rfc822", mailMetadata.get(Metadata.CONTENT_TYPE));
        assertEquals("author@domain.com", mailMetadata.get("Message-From"));
        assertEquals("<name@domain.com>", mailMetadata.get("MboxParser-return-path"));
    }

    /** Test a header consisting of multiple lines */
    @Test
    public void testMultilineHeader() throws IOException, TikaException, SAXException {
        ParseResults results;
        try (InputStream stream = getStream("/test-documents/multiline.mbox")) {
            results = parse(stream);
        }

        assertEquals("Number of emails", 1, results.getNumParsedEmails());

        Metadata mailMetadata = results.getMetadataForEmail(0);
        assertEquals("from xxx by xxx with xxx; date", mailMetadata.get("MboxParser-received"));
    }

    /** Test quoted body */
    @Test
    public void testQuoted() throws IOException, TikaException, SAXException {
        ParseResults results;
        try (InputStream stream = getStream("/test-documents/quoted.mbox")) {
            results = parse(stream);
        }

        assertContains("Test content", results.getBody());
        assertContains("> quoted stuff", results.getBody());
    }

    /** Test a series of realistic email messages */
    @Test
    public void testComplex() throws Exception {
        ParseResults results;
        try (InputStream stream = getStream("/test-documents/complex.mbox")) {
            results = parse(stream);
        }

        assertEquals("Number of emails", 3, results.getNumParsedEmails());

        Metadata firstMail = results.getMetadataForEmail(0);
        assertEquals("Re: question about when shuffle/sort start working", firstMail.get(Metadata.SUBJECT));
        assertEquals("Re: question about when shuffle/sort start working", firstMail.get(TikaCoreProperties.TITLE));
        assertEquals("Jothi Padmanabhan <jothipn@yahoo-inc.com>", firstMail.get(Metadata.AUTHOR));
        assertEquals("Jothi Padmanabhan <jothipn@yahoo-inc.com>", firstMail.get(TikaCoreProperties.CREATOR));
        assertEquals("core-user@hadoop.apache.org", firstMail.get(Metadata.MESSAGE_RECIPIENT_ADDRESS));

        assertContains("When a Mapper completes", results.getBody());
    }
    
    /** Test a series of realistic email messages */
    @Test
    public void testComplex2() throws Exception {
        ParseResults results;
        try (InputStream stream = getStream("/test-documents/complex2.mbox")) {
            results = parse(stream);
        }
        
        assertEquals("Number of emails", 3, results.getNumParsedEmails());
        
        Metadata firstMail = results.getMetadataForEmail(0);
        assertEquals("Big news.", firstMail.get(TikaCoreProperties.TITLE));
        assertEquals("=?UTF-8?B?TGV2aSdzwq4gU3RvcmVz?= <levis@e.levi.com>", firstMail.get(TikaCoreProperties.CREATOR));
        assertEquals("mboxtest@gmail.com", firstMail.get(Metadata.MESSAGE_RECIPIENT_ADDRESS));
        
        Metadata secondMail = results.getMetadataForEmail(1);
        assertEquals("Term Sheet: Jan. 20, 2017", secondMail.get(TikaCoreProperties.TITLE));
        assertEquals("\"Erin Griffith\" <fortune@email.fortune.com>", secondMail.get(TikaCoreProperties.CREATOR));
        assertEquals("mboxtest@gmail.com", secondMail.get(Metadata.MESSAGE_RECIPIENT_ADDRESS));
        
        Metadata thirdMail = results.getMetadataForEmail(2);
        assertEquals("🍾 Look great this holiday season in these fresh new arrivals (+ take 40% off!)", thirdMail.get(TikaCoreProperties.TITLE));
        assertEquals("\"Banana Republic\" <bananarepublic@email.bananarepublic.com>", thirdMail.get(TikaCoreProperties.CREATOR));
        assertEquals("mboxtest@gmail.com", thirdMail.get(Metadata.MESSAGE_RECIPIENT_ADDRESS));
    }
    
    /** Test when "From:" is in the body of a message */
    @Test
    public void testFromColonInBody() throws Exception {
        ParseResults results;
        try (InputStream stream = getStream("/test-documents/from_colon_in_body.mbox")) {
            results = parse(stream);
            
            assertEquals("Number of emails", 1, results.getNumParsedEmails());
            
            Metadata firstMail = results.getMetadataForEmail(0);
            assertEquals("From: in body test", firstMail.get(TikaCoreProperties.TITLE));
            assertEquals("Mbox Test <mboxtest@gmail.com>", firstMail.get(TikaCoreProperties.CREATOR));
            assertEquals("mboxtest@gmail.com", firstMail.get(Metadata.MESSAGE_RECIPIENT_ADDRESS));
        }
    }
    
    /** Test when ">From " is in the body of a message */
    @Test
    public void testFromInBody() throws Exception {
        ParseResults results;
        try (InputStream stream = getStream("/test-documents/from_in_body.mbox")) {
            results = parse(stream);
            
            assertEquals("Number of emails", 1, results.getNumParsedEmails());
            
            Metadata firstMail = results.getMetadataForEmail(0);
            assertEquals("From in body test", firstMail.get(TikaCoreProperties.TITLE));
            assertEquals("Mbox Test <mboxtest@gmail.com>", firstMail.get(TikaCoreProperties.CREATOR));
            assertEquals("mboxtest@gmail.com", firstMail.get(Metadata.MESSAGE_RECIPIENT_ADDRESS));
        }
    }
    
    /** Test when "From " is in the body of a message but not followed by a newline */
    @Test
    public void testFromContrivedInBody() throws Exception {
        ParseResults results;
        try (InputStream stream = getStream("/test-documents/from_contrived.mbox")) {
            results = parse(stream);
            
            assertEquals("Number of emails", 1, results.getNumParsedEmails());
            
            Metadata firstMail = results.getMetadataForEmail(0);
            assertEquals("mbox from in body nonescaped", firstMail.get(TikaCoreProperties.TITLE));
            assertEquals("Mbox Test <mboxtest@gmail.com>", firstMail.get(TikaCoreProperties.CREATOR));
            assertEquals("mboxtest@gmail.com", firstMail.get(Metadata.MESSAGE_RECIPIENT_ADDRESS));
        }
    }
}
