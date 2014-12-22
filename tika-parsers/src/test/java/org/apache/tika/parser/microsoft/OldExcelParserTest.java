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

import static org.apache.tika.parser.microsoft.AbstractPOIContainerExtractionTest.getTestFile;
import static org.junit.Assert.assertEquals;

import org.apache.tika.TikaTest;
import org.apache.tika.detect.DefaultDetector;
import org.apache.tika.detect.Detector;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.junit.Ignore;
import org.junit.Test;
import org.xml.sax.ContentHandler;

/**
 * Tests for the Old Excel (2-4) parser
 */
public class OldExcelParserTest extends TikaTest {
    private static final String file = "testEXCEL_4.xls";

    @Test
    public void testDetection() throws Exception {
        TikaInputStream stream = getTestFile(file);
        Detector detector = new DefaultDetector();
        try {
            assertEquals(
                    MediaType.application("vnd.ms-excel.sheet.4"),
                    detector.detect(stream, new Metadata()));
        } finally {
            stream.close();
        }
    }

    // Disabled, until we can get the POI code to tell us the version
    @Test
    @Ignore
    public void testMetadata() throws Exception {
        TikaInputStream stream = getTestFile(file);

        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler();

        OldExcelParser parser = new OldExcelParser();
        parser.parse(stream, handler, metadata, new ParseContext());

        // We can get the content type
        assertEquals("application/vnd.ms-excel.sheet.4", metadata.get(Metadata.CONTENT_TYPE));
        
        // But no other metadata
        assertEquals(null, metadata.get(TikaCoreProperties.TITLE));
        assertEquals(null, metadata.get(Metadata.SUBJECT));
    }
    
    /**
     * Check we can get the plain text properly
     */
    @Test
    public void testPlainText() throws Exception {
        ContentHandler handler = new BodyContentHandler();
        Metadata metadata = new Metadata();

        TikaInputStream stream = getTestFile(file);
        try {
            new OldExcelParser().parse(stream, handler, metadata, new ParseContext());
        } finally {
            stream.close();
        }
        
        String text = handler.toString();
        
        // Check we find a few words we expect in there
        assertContains("Size", text);
        assertContains("Returns", text);

        // Check we find a few numbers we expect in there
        assertContains("11", text);
        assertContains("784", text);
    }

    /**
     * Check the HTML version comes through correctly
     */
    @Test
    public void testHTML() throws Exception {
        XMLResult result = getXML(file);
        String xml = result.xml;
        
        // Sheet name not found - only 5+ have sheet names
        assertNotContained("<p>Sheet 1</p>", xml);
        
        // String cells
        assertContains("<p>Table 10 -", xml);
        assertContains("<p>Tax</p>", xml);
        assertContains("<p>N/A</p>", xml);
        
        // Number cells
        assertContains("<p>(1)</p>", xml);
        assertContains("<p>5.0</p>", xml);
    }
}
