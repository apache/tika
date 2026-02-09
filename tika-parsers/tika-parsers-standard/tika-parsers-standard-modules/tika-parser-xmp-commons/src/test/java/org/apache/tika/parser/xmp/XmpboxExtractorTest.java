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
package org.apache.tika.parser.xmp;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;

import org.apache.commons.io.output.UnsynchronizedByteArrayOutputStream;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import org.apache.tika.TikaTest;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.metadata.XMPMM;

/**
 *
 * @author Tilman Hausherr
 */
@Disabled //TODO enable with XMPBox 3.0.7
public class XmpboxExtractorTest extends TikaTest {

    private final XMPPacketScanner scanner = new XMPPacketScanner();

    @Test // parsing fails because of bad date "2010-07-28T11:02:12.000CEST" = UTC+02:00
    public void testParseJpeg() throws IOException, TikaException {
        Metadata metadata = new Metadata();
        try (TikaInputStream tis = getResourceAsStream("/test-documents/testJPEG_commented.jpg")) {
            UnsynchronizedByteArrayOutputStream xmpraw = UnsynchronizedByteArrayOutputStream.builder().get();
            boolean parsed = scanner.parse(tis, xmpraw);
            assertTrue(parsed);

            // set some values before extraction to see that they are overridden

            //TODO this doesn't work here because this extractor uses addMetadata() which works
            // differently than metadata.set(). We may want to fix one or the other.
//            metadata.set(TikaCoreProperties.TITLE, "old title");
//            metadata.set(TikaCoreProperties.DESCRIPTION, "old description");
//            metadata.set(TikaCoreProperties.CREATOR, "previous author");
            // ... or kept in case the field is multi-value
            metadata.add(TikaCoreProperties.SUBJECT, "oldkeyword");

            // xmpbox fails parsing on bad dates
            String s = xmpraw.toString(StandardCharsets.UTF_8);
            s = s.replace("CEST\"", "+02:00\"");

            XMPMetadataExtractor.parse(new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8)), metadata);

            // DublinCore fields
            assertEquals("Tosteberga \u00C4ngar", metadata.get(TikaCoreProperties.TITLE));
            assertEquals("Bird site in north eastern Sk\u00E5ne, Sweden.\n(new line)",
                    metadata.get(TikaCoreProperties.DESCRIPTION));
            assertEquals("Some Tourist", metadata.get(TikaCoreProperties.CREATOR));
            Collection<String> keywords =
                    Arrays.asList(metadata.getValues(TikaCoreProperties.SUBJECT));
            assertTrue(keywords.contains("oldkeyword"));
            assertTrue(keywords.contains("grazelands"));
            assertTrue(keywords.contains("nature reserve"));
            assertTrue(keywords.contains("bird watching"));
            assertTrue(keywords.contains("coast"));
        }
    }

    @Test
    public void testParseJpegPhotoshop() throws IOException, TikaException {
        Metadata metadata = new Metadata();
        try (TikaInputStream tis = getResourceAsStream(
                "/test-documents/testJPEG_commented_pspcs2mac.jpg")) {
            UnsynchronizedByteArrayOutputStream xmpraw = UnsynchronizedByteArrayOutputStream.builder().get();
            boolean parsed = scanner.parse(tis, xmpraw);
            assertTrue(parsed);

            try (InputStream is = xmpraw.toInputStream()) {
                XMPMetadataExtractor.parse(is, metadata);
            }

            // DublinCore fields
            assertEquals("Tosteberga \u00C4ngar", metadata.get(TikaCoreProperties.TITLE));
            assertEquals("Bird site in north eastern Sk\u00E5ne, Sweden.\n(new line)",
                    metadata.get(TikaCoreProperties.DESCRIPTION));
            assertEquals("Some Tourist", metadata.get(TikaCoreProperties.CREATOR));
            Collection<String> keywords =
                    Arrays.asList(metadata.getValues(TikaCoreProperties.SUBJECT));
            assertTrue(keywords.contains("bird watching"));
            assertTrue(keywords.contains("coast"));
        }
    }

    @Test
    public void testParseJpegXnviewmp() throws IOException, TikaException {
        Metadata metadata = new Metadata();
        try (TikaInputStream tis = getResourceAsStream(
                "/test-documents/testJPEG_commented_xnviewmp026.jpg")) {
            UnsynchronizedByteArrayOutputStream xmpraw = UnsynchronizedByteArrayOutputStream.builder().get();
            boolean parsed = scanner.parse(tis, xmpraw);
            assertTrue(parsed);

            try (InputStream is = xmpraw.toInputStream()) {
                XMPMetadataExtractor.parse(is, metadata);
            }

            assertEquals("Bird site in north eastern Sk\u00E5ne, Sweden.\n(new line)",
                    metadata.get(TikaCoreProperties.DESCRIPTION));
            Collection<String> keywords =
                    Arrays.asList(metadata.getValues(TikaCoreProperties.SUBJECT));
            assertTrue(keywords.contains("coast"));
            assertTrue(keywords.contains("nature reserve"));
        }
    }

    @Test
    public void testMaxXMPMMHistory() throws Exception {
        Metadata metadata = new Metadata();
        int maxHistory = XMPMetadataExtractor.getMaxXMPMMHistory();
        try {
            try (TikaInputStream tis = getResourceAsStream("/test-documents/testXMP.xmp")) {
                UnsynchronizedByteArrayOutputStream xmpraw = UnsynchronizedByteArrayOutputStream.builder().get();
                boolean parsed = scanner.parse(tis, xmpraw);
                assertTrue(parsed);

                try (InputStream is = xmpraw.toInputStream()) {
                    XMPMetadataExtractor.parse(is, metadata);
                }

                assertEquals(7, metadata.getValues(XMPMM.HISTORY_EVENT_INSTANCEID).length);
                
                XMPMetadataExtractor.setMaxXMPMMHistory(5);
                metadata = new Metadata();
                try (InputStream is = xmpraw.toInputStream()) {
                    XMPMetadataExtractor.parse(is, metadata);
                }

                assertEquals(5, metadata.getValues(XMPMM.HISTORY_EVENT_INSTANCEID).length);
            }
        }
        finally {
            //if something goes wrong, make sure to set this back to what it was
            XMPMetadataExtractor.setMaxXMPMMHistory(maxHistory);
        }
    }

}
