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
package org.apache.tika.parser.microsoft.pst;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.List;

import org.junit.jupiter.api.Test;

import org.apache.tika.TikaTest;
import org.apache.tika.metadata.Message;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Office;
import org.apache.tika.metadata.PST;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.Parser;

public class OutlookPSTParserTest extends TikaTest {

    private Parser parser = new OutlookPSTParser();

    @Test
    public void testAccept() throws Exception {
        assertTrue((parser.getSupportedTypes(null)
                .contains(MediaType.application("vnd.ms-outlook-pst"))));
    }

    @Test
    public void testLegacyXML() throws Exception {
        String output = getXML("testPST.pst").xml;
        assertTrue(output.contains("<meta name=\"Content-Length\" content=\"2302976\""));
        assertTrue(output.contains("<meta name=\"Content-Type\" content=\"application/vnd.ms-outlook-pst\""));

        assertTrue(output.contains("<body><div class=\"email-folder\"><h1>"));
        assertTrue(output.contains("<div class=\"embedded\" id=\"&lt;530D9CAC.5080901@gmail.com&gt;\">" + "<h1>Re: Feature Generators</h1>"));
        assertTrue(output.contains(
                "<div class=\"embedded\" id=\"&lt;1393363252.28814.YahooMailNeo@web140906.mail" + ".bf1.yahoo.com&gt;\"><h1>Re: init tokenizer fails: \"Bad type in " +
                        "putfield/putstatic\"</h1>"));
        assertTrue(output.contains("Gary Murphy commented on TIKA-1250:"));

        assertTrue(output.contains("<div class=\"email-folder\"><h1>Racine (pour la recherche)</h1>"));

        assertTrue(output.contains("This is a docx attachment."));
    }

    @Test
    public void testExtendedMetadata() throws Exception {
        List<Metadata> metadataList = getRecursiveMetadata("testPST.pst");
        Metadata m1 = metadataList.get(1);
        assertEquals("Jörn Kottmann", m1.get(Message.MESSAGE_FROM_NAME));
        assertEquals("Jörn Kottmann", m1.get(TikaCoreProperties.CREATOR));
        assertEquals("Re: Feature Generators", m1.get(TikaCoreProperties.TITLE));
        assertEquals("users@opennlp.apache.org", m1.get(Message.MESSAGE_TO_DISPLAY_NAME));
        assertEquals("", m1.get(Message.MESSAGE_CC_DISPLAY_NAME));
        assertEquals("", m1.get(Message.MESSAGE_BCC_DISPLAY_NAME));
        assertEquals("kottmann@gmail.com", m1.get(Message.MESSAGE_FROM_EMAIL));
        assertEquals("Jörn Kottmann", m1.get(Office.MAPI_FROM_REPRESENTING_NAME));
        assertEquals("kottmann@gmail.com", m1.get(Office.MAPI_FROM_REPRESENTING_EMAIL));
        assertEquals("NOTE", m1.get(Office.MAPI_MESSAGE_CLASS));
        assertEquals("/Début du fichier de données Outlook", m1.get(PST.PST_FOLDER_PATH));

        Metadata m6 = metadataList.get(6);
        assertEquals("Couchbase", m6.get(Message.MESSAGE_FROM_NAME));
        assertEquals("couchbase@couchbase.com", m6.get(Message.MESSAGE_FROM_EMAIL));
        assertEquals("Couchbase", m6.get(Office.MAPI_FROM_REPRESENTING_NAME));
        assertEquals("couchbase@couchbase.com", m6.get(Office.MAPI_FROM_REPRESENTING_EMAIL));
        assertEquals("NOTE", m1.get(Office.MAPI_MESSAGE_CLASS));
        assertNull(m1.get(Office.MAPI_RECIPIENTS_STRING));
        assertContains("2014-02-26", m1.get(Office.MAPI_MESSAGE_CLIENT_SUBMIT_TIME));

        //test full EX email
        assertEquals(
                "/o=ExchangeLabs/ou=Exchange Administrative Group (FYDIBOHF23SPDLT)" +
                        "/cn=Recipients/cn=polyspot1.onmicrosoft.com-50609-Hong-Thai.Ng",
                m6.get(Message.MESSAGE_TO_EMAIL));
        assertEquals("Hong-Thai Nguyen", m6.get(Message.MESSAGE_TO_DISPLAY_NAME));

        assertEquals("Couchbase", m6.get(Message.MESSAGE_FROM_NAME));
        assertEquals("couchbase@couchbase.com", m6.get(Message.MESSAGE_FROM_EMAIL));

        Metadata m7 = metadataList.get(7);
        assertEquals("/<2915856a7d3449e68529f3e61b8d26bc@pf.gov.br>/<3148510c2360443396a78d35e0888de9@pf.gov.br>/attachment.docx",
                m7.get(TikaCoreProperties.EMBEDDED_RESOURCE_PATH));
        assertEquals("/7/8/9", m7.get(TikaCoreProperties.EMBEDDED_ID_PATH));
    }

    @Test
    public void testOverrideDetector() throws Exception {
        List<Metadata> metadataList = getRecursiveMetadata("testPST_variousBodyTypes.pst");
        assertEquals(5,
                metadataList.size());//before the fix that prevents the RFC parser, this was 6
        for (Metadata metadata : metadataList) {
            for (String v : metadata.getValues(TikaCoreProperties.TIKA_PARSED_BY)) {
                if (v.contains("RFC822Parser")) {
                    fail("RFCParser should never be called");
                }
            }
        }
        //TODO: figure out why the bold markup isn't coming through if we do extract then parse
        // the bodyhtml
    }
}
