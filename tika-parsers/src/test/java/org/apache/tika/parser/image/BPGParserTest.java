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
package org.apache.tika.parser.image;

import java.io.InputStream;
import java.util.Arrays;
import java.util.List;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Photoshop;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.junit.Test;
import org.xml.sax.helpers.DefaultHandler;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class BPGParserTest {
    private final Parser parser = new BPGParser();

    /**
     * Tests a very basic file, without much metadata
     */
    @Test
    public void testBPG() throws Exception {
        Metadata metadata = new Metadata();
        metadata.set(Metadata.CONTENT_TYPE, "image/x-bpg");
        InputStream stream =
            getClass().getResourceAsStream("/test-documents/testBPG.bpg");
        parser.parse(stream, new DefaultHandler(), metadata, new ParseContext());

        assertEquals("100", metadata.get(Metadata.IMAGE_WIDTH));
        assertEquals("75", metadata.get(Metadata.IMAGE_LENGTH));
        assertEquals("10", metadata.get(Metadata.BITS_PER_SAMPLE));
        assertEquals("YCbCr Colour", metadata.get(Photoshop.COLOR_MODE));
    }

    /**
     * Tests a file with comments
     */
    @Test
    public void testBPG_Commented() throws Exception {
        Metadata metadata = new Metadata();
        metadata.set(Metadata.CONTENT_TYPE, "image/x-bpg");
        InputStream stream =
            getClass().getResourceAsStream("/test-documents/testBPG_commented.bpg");
        parser.parse(stream, new DefaultHandler(), metadata, new ParseContext());

        assertEquals("103", metadata.get(Metadata.IMAGE_WIDTH));
        assertEquals("77", metadata.get(Metadata.IMAGE_LENGTH));
        assertEquals("10", metadata.get(Metadata.BITS_PER_SAMPLE));
        assertEquals("YCbCr Colour", metadata.get(Photoshop.COLOR_MODE));
        
        // TODO Get the exif comment data to be properly extracted, see TIKA-1495
        if (false) {
            assertEquals("Tosteberga \u00C4ngar", metadata.get(TikaCoreProperties.TITLE));
            assertEquals("Bird site in north eastern Sk\u00E5ne, Sweden.\n(new line)", metadata.get(TikaCoreProperties.DESCRIPTION));
            List<String> keywords = Arrays.asList(metadata.getValues(Metadata.KEYWORDS));
            assertTrue(keywords.contains("coast"));
            assertTrue(keywords.contains("bird watching"));
            assertEquals(keywords, Arrays.asList(metadata.getValues(TikaCoreProperties.KEYWORDS)));
        }
        
        // TODO Get the exif data to be properly extracted, see TIKA-1495
        if (false) {
            assertEquals("1.0E-6", metadata.get(Metadata.EXPOSURE_TIME)); // 1/1000000
            assertEquals("2.8", metadata.get(Metadata.F_NUMBER));
            assertEquals("4.6", metadata.get(Metadata.FOCAL_LENGTH));
            assertEquals("114", metadata.get(Metadata.ISO_SPEED_RATINGS));
            assertEquals(null, metadata.get(Metadata.EQUIPMENT_MAKE));
            assertEquals(null, metadata.get(Metadata.EQUIPMENT_MODEL));
            assertEquals(null, metadata.get(Metadata.SOFTWARE));
            assertEquals("1", metadata.get(Metadata.ORIENTATION));
            assertEquals("300.0", metadata.get(Metadata.RESOLUTION_HORIZONTAL));
            assertEquals("300.0", metadata.get(Metadata.RESOLUTION_VERTICAL));
            assertEquals("Inch", metadata.get(Metadata.RESOLUTION_UNIT));          
        }
    }

    /**
     * Tests a file with geographic information in it
     */
    @Test
    public void testBPG_Geo() throws Exception {
        Metadata metadata = new Metadata();
        metadata.set(Metadata.CONTENT_TYPE, "image/x-bpg");
        InputStream stream =
            getClass().getResourceAsStream("/test-documents/testBPG_GEO.bpg");
        parser.parse(stream, new DefaultHandler(), metadata, new ParseContext());

        assertEquals("100", metadata.get(Metadata.IMAGE_WIDTH));
        assertEquals("68", metadata.get(Metadata.IMAGE_LENGTH));
        assertEquals("10", metadata.get(Metadata.BITS_PER_SAMPLE));
        assertEquals("YCbCr Colour", metadata.get(Photoshop.COLOR_MODE));
        
        // TODO Get the geographic data to be properly extracted, see TIKA-1495
        if (false) {
            assertEquals("12.54321", metadata.get(Metadata.LATITUDE));
            assertEquals("-54.1234", metadata.get(Metadata.LONGITUDE));
        }
        
        // TODO Get the exif data to be properly extracted, see TIKA-1495
        if (false) {
            assertEquals("6.25E-4", metadata.get(Metadata.EXPOSURE_TIME)); // 1/1600
            assertEquals("5.6", metadata.get(Metadata.F_NUMBER));
            assertEquals("false", metadata.get(Metadata.FLASH_FIRED));
            assertEquals("194.0", metadata.get(Metadata.FOCAL_LENGTH));
            assertEquals("400", metadata.get(Metadata.ISO_SPEED_RATINGS));
            assertEquals("Canon", metadata.get(Metadata.EQUIPMENT_MAKE));
            assertEquals("Canon EOS 40D", metadata.get(Metadata.EQUIPMENT_MODEL));
            assertEquals("Adobe Photoshop CS3 Macintosh", metadata.get(Metadata.SOFTWARE));
            assertEquals("240.0", metadata.get(Metadata.RESOLUTION_HORIZONTAL));
            assertEquals("240.0", metadata.get(Metadata.RESOLUTION_VERTICAL));
            assertEquals("Inch", metadata.get(Metadata.RESOLUTION_UNIT));
        }
    }
}
