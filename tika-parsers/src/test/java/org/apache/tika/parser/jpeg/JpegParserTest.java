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
package org.apache.tika.parser.jpeg;

import junit.framework.TestCase;

import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.metadata.Metadata;
import org.xml.sax.helpers.DefaultHandler;

import java.io.InputStream;

public class JpegParserTest extends TestCase {
    private final Parser parser = new JpegParser();

    public void testJPEG() throws Exception {
        Metadata metadata = new Metadata();
        metadata.set(Metadata.CONTENT_TYPE, "image/jpeg");
        InputStream stream =
            getClass().getResourceAsStream("/test-documents/testJPEG_EXIF.jpg");
        parser.parse(stream, new DefaultHandler(), metadata, new ParseContext());

        // All EXIF/TIFF tags
        assertEquals("Canon EOS 40D", metadata.get("Model"));
        
        // Core EXIF/TIFF tags
        assertEquals("100", metadata.get(Metadata.IMAGE_WIDTH));
        assertEquals("68", metadata.get(Metadata.IMAGE_LENGTH));
        assertEquals("8", metadata.get(Metadata.BITS_PER_SAMPLE));
        assertEquals(null, metadata.get(Metadata.SAMPLES_PER_PIXEL));
        
        // Common tags
        assertEquals("Date/Time for when the photo was taken, unspecified time zone",
                "2009-10-02T23:02:49", metadata.get(Metadata.DATE));
        assertEquals("Date/Time Original for when the photo was taken, unspecified time zone",
                "2009-08-11T09:09:45", metadata.get(Metadata.ORIGINAL_DATE));
        assertEquals("canon-55-250 moscow-birds serbor", metadata.get(Metadata.KEYWORDS));
    }

    public void testJPEGGeo() throws Exception {
        Metadata metadata = new Metadata();
        metadata.set(Metadata.CONTENT_TYPE, "image/jpeg");
        InputStream stream =
            getClass().getResourceAsStream("/test-documents/testJPEG_GEO.jpg");
        parser.parse(stream, new DefaultHandler(), metadata, new ParseContext());
        
        // Geo tags
        assertEquals("12.54321", metadata.get(Metadata.LATITUDE));
        assertEquals("-54.1234", metadata.get(Metadata.LONGITUDE));

        // All EXIF/TIFF tags
        assertEquals("Canon EOS 40D", metadata.get("Model"));
        
        // Core EXIF/TIFF tags
        assertEquals("100", metadata.get(Metadata.IMAGE_WIDTH));
        assertEquals("68", metadata.get(Metadata.IMAGE_LENGTH));
        assertEquals("8", metadata.get(Metadata.BITS_PER_SAMPLE));
        assertEquals(null, metadata.get(Metadata.SAMPLES_PER_PIXEL));
        
        // Common tags
        assertEquals("Date/Time Original for when the photo was taken, unspecified time zone",
                "2009-08-11T09:09:45", metadata.get(Metadata.ORIGINAL_DATE));
        assertEquals("This image has different Date/Time than Date/Time Original, so it is probably modification date",
                "2009-10-02T23:02:49", metadata.get(Metadata.DATE));
        assertEquals("canon-55-250 moscow-birds serbor", metadata.get(Metadata.KEYWORDS));
    }
    
    public void testJPEGTitleAndDescription() throws Exception {
        Metadata metadata = new Metadata();
        metadata.set(Metadata.CONTENT_TYPE, "image/jpeg");
        InputStream stream =
            getClass().getResourceAsStream("/test-documents/testJPEG_commented.jpg");
        parser.parse(stream, new DefaultHandler(), metadata, new ParseContext());
          
        // embedded comments with non-ascii characters
        //assertEquals("Tosteberga \u00C4ngar", metadata.get(Metadata.TITLE));
        assertEquals("Tosteberga " + new String(new byte[]{-61, -124}) + "ngar", metadata.get(Metadata.TITLE));
        //assertEquals("Bird site in north eastern Sk\u00E5ne, Sweden.\n(new line)", metadata.get(Metadata.DESCRIPTION));
        assertEquals("Bird site in north eastern Sk" + new String(new byte[]{-61, -91}) + 
        		"ne, Sweden.\n(new line)", metadata.get(Metadata.DESCRIPTION));
        assertEquals("Some Tourist", metadata.get(Metadata.AUTHOR));
        assertEquals("grazelands nature reserve bird watching coast", metadata.get(Metadata.KEYWORDS));
    }
}
