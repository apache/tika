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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.tika.TikaTest;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.extractor.ParsingEmbeddedDocumentExtractor;
import org.apache.tika.metadata.Message;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Office;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.ToHTMLContentHandler;
import org.junit.Test;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

public class OutlookPSTParserTest extends TikaTest {

    private Parser parser = new OutlookPSTParser();

    @Test
    public void testAccept() throws Exception {
        assertTrue((parser.getSupportedTypes(null).contains(MediaType.application("vnd.ms-outlook-pst"))));
    }

    @Test
    public void testParse() throws Exception {
        Parser pstParser = new AutoDetectParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new ToHTMLContentHandler();

        ParseContext context = new ParseContext();
        EmbeddedTrackingExtrator trackingExtrator = new EmbeddedTrackingExtrator(context);
        context.set(EmbeddedDocumentExtractor.class, trackingExtrator);
        context.set(Parser.class, new AutoDetectParser());

        pstParser.parse(getResourceAsStream("/test-documents/testPST.pst"), handler, metadata, context);

        String output = handler.toString();

        assertFalse(output.isEmpty());
        assertTrue(output.contains("<meta name=\"Content-Length\" content=\"271360\">"));
        assertTrue(output.contains("<meta name=\"Content-Type\" content=\"application/vnd.ms-outlook-pst\">"));

        assertTrue(output.contains("<body><div class=\"email-folder\"><h1>"));
        assertTrue(output.contains("<div class=\"embedded\" id=\"&lt;530D9CAC.5080901@gmail.com&gt;\"><h1>Re: Feature Generators</h1>"));
        assertTrue(output.contains("<div class=\"embedded\" id=\"&lt;1393363252.28814.YahooMailNeo@web140906.mail.bf1.yahoo.com&gt;\"><h1>Re: init tokenizer fails: \"Bad type in putfield/putstatic\"</h1>"));
        assertTrue(output.contains("Gary Murphy commented on TIKA-1250:"));

        assertTrue(output.contains("<div class=\"email-folder\"><h1>Racine (pour la recherche)</h1>"));


        List<Metadata> metaList = trackingExtrator.trackingMetadata;
        assertEquals(6, metaList.size());

        Metadata firstMail = metaList.get(0);
        assertEquals("Jörn Kottmann", firstMail.get(TikaCoreProperties.CREATOR));
        assertEquals("Re: Feature Generators", firstMail.get(TikaCoreProperties.TITLE));
        assertEquals("kottmann@gmail.com", firstMail.get("senderEmailAddress"));
        assertEquals("users@opennlp.apache.org", firstMail.get("displayTo"));
        assertEquals("", firstMail.get("displayCC"));
        assertEquals("", firstMail.get("displayBCC"));

    }


    private class EmbeddedTrackingExtrator extends ParsingEmbeddedDocumentExtractor {
        List<Metadata> trackingMetadata = new ArrayList<Metadata>();

        public EmbeddedTrackingExtrator(ParseContext context) {
            super(context);
        }

        @Override
        public boolean shouldParseEmbedded(Metadata metadata) {
            return true;
        }

        @Override
        public void parseEmbedded(InputStream stream, ContentHandler handler, Metadata metadata, boolean outputHtml) throws SAXException, IOException {
            this.trackingMetadata.add(metadata);
            super.parseEmbedded(stream, handler, metadata, outputHtml);
        }
    }

    @Test
    public void testExtendedMetadata() throws Exception {
        List<Metadata> metadataList = getRecursiveMetadata("testPST.pst");
        Metadata m1 = metadataList.get(1);
        assertEquals("Jörn Kottmann", m1.get(Message.MESSAGE_FROM_NAME));
        assertEquals("kottmann@gmail.com", m1.get(Message.MESSAGE_FROM_EMAIL));
        assertEquals("Jörn Kottmann", m1.get(Office.MAPI_FROM_REPRESENTING_NAME));
        assertEquals("kottmann@gmail.com", m1.get(Office.MAPI_FROM_REPRESENTING_EMAIL));
        assertEquals("NOTE", m1.get(Office.MAPI_MESSAGE_CLASS));

        Metadata m6 = metadataList.get(6);
        assertEquals("Couchbase", m6.get(Message.MESSAGE_FROM_NAME));
        assertEquals("couchbase@couchbase.com", m6.get(Message.MESSAGE_FROM_EMAIL));
        assertEquals("Couchbase", m6.get(Office.MAPI_FROM_REPRESENTING_NAME));
        assertEquals("couchbase@couchbase.com", m6.get(Office.MAPI_FROM_REPRESENTING_EMAIL));
        assertEquals("NOTE", m1.get(Office.MAPI_MESSAGE_CLASS));
        //test full EX email
        assertEquals("/o=ExchangeLabs/ou=Exchange Administrative Group (FYDIBOHF23SPDLT)/cn=Recipients/cn=polyspot1.onmicrosoft.com-50609-Hong-Thai.Ng",
                m6.get(Message.MESSAGE_TO_EMAIL));
        assertEquals("Hong-Thai Nguyen",
                m6.get(Message.MESSAGE_TO_DISPLAY_NAME));

        assertEquals("Couchbase",m6.get(Message.MESSAGE_FROM_NAME));
        assertEquals("couchbase@couchbase.com", m6.get(Message.MESSAGE_FROM_EMAIL));

    }

    @Test
    public void testOverrideDetector() throws Exception {
        List<Metadata> metadataList = getRecursiveMetadata("testPST_variousBodyTypes.pst");
        assertEquals(5, metadataList.size());//before the fix that prevents the RFC parser, this was 6
        for (Metadata metadata : metadataList) {
            for (String v : metadata.getValues("X-Parsed-By")) {
                if (v.contains("RFC822Parser")) {
                    fail("RFCParser should never be called");
                }
            }
        }
        //TODO: figure out why the bold markup isn't coming through if we do extract then parse the bodyhtml
    }
}
