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

import java.io.InputStream;
import java.util.Arrays;
import java.util.List;

import junit.framework.TestCase;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TIFF;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.xml.sax.helpers.DefaultHandler;

public class JpegParserTest extends TestCase {
    private final Parser parser = new JpegParser();
    
    public void testJPEG() throws Exception {
        Metadata metadata = new Metadata();
        metadata.set(Metadata.CONTENT_TYPE, "image/jpeg");
        InputStream stream =
            getClass().getResourceAsStream("/test-documents/testJPEG_EXIF.jpg");
        parser.parse(stream, new DefaultHandler(), metadata, new ParseContext());

        // Core EXIF/TIFF tags
        assertEquals("100", metadata.get(Metadata.IMAGE_WIDTH));
        assertEquals("68", metadata.get(Metadata.IMAGE_LENGTH));
        assertEquals("8", metadata.get(Metadata.BITS_PER_SAMPLE));
        assertEquals(null, metadata.get(Metadata.SAMPLES_PER_PIXEL));
        
        assertEquals("6.25E-4", metadata.get(Metadata.EXPOSURE_TIME)); // 1/1600
        assertEquals("5.6", metadata.get(Metadata.F_NUMBER));
        assertEquals("false", metadata.get(Metadata.FLASH_FIRED));
        assertEquals("194.0", metadata.get(Metadata.FOCAL_LENGTH));
        assertEquals("400", metadata.get(Metadata.ISO_SPEED_RATINGS));
        assertEquals("Canon", metadata.get(Metadata.EQUIPMENT_MAKE));
        assertEquals("Canon EOS 40D", metadata.get(Metadata.EQUIPMENT_MODEL));
        assertEquals("Adobe Photoshop CS3 Macintosh", metadata.get(Metadata.SOFTWARE));
        assertEquals(null, metadata.get(Metadata.ORIENTATION)); // Not present
        assertEquals("240.0", metadata.get(Metadata.RESOLUTION_HORIZONTAL));
        assertEquals("240.0", metadata.get(Metadata.RESOLUTION_VERTICAL));
        assertEquals("Inch", metadata.get(Metadata.RESOLUTION_UNIT));
        
        // Check that EXIF/TIFF tags come through with their raw values too
        // (This may be removed for Tika 1.0, as we support more of them
        //  with explicit Metadata entries)
        assertEquals("Canon EOS 40D", metadata.get("Model"));
        
        // Common tags
        //assertEquals("2009-10-02T23:02:49", metadata.get(Metadata.LAST_MODIFIED));
        assertEquals("Date/Time Original for when the photo was taken, unspecified time zone",
                "2009-08-11T09:09:45", metadata.get(TikaCoreProperties.CREATED));
        List<String> keywords = Arrays.asList(metadata.getValues(TikaCoreProperties.KEYWORDS));
        assertTrue("'canon-55-250' expected in " + keywords, keywords.contains("canon-55-250"));
        assertTrue("'moscow-birds' expected in " + keywords, keywords.contains("moscow-birds")); 
        assertTrue("'serbor' expected in " + keywords, keywords.contains("serbor"));
        assertFalse(keywords.contains("canon-55-250 moscow-birds serbor"));
        List<String> subject = Arrays.asList(metadata.getValues(Metadata.SUBJECT));
        assertTrue("'canon-55-250' expected in " + subject, subject.contains("canon-55-250"));
        assertTrue("'moscow-birds' expected in " + subject, subject.contains("moscow-birds")); 
        assertTrue("'serbor' expected in " + subject, subject.contains("serbor"));
        assertFalse(subject.contains("canon-55-250 moscow-birds serbor"));
    }

    /**
     * Test for a file with Geographic information (lat, long etc) in it
     */
    public void testJPEGGeo() throws Exception {
        Metadata metadata = new Metadata();
        metadata.set(Metadata.CONTENT_TYPE, "image/jpeg");
        InputStream stream =
            getClass().getResourceAsStream("/test-documents/testJPEG_GEO.jpg");
        parser.parse(stream, new DefaultHandler(), metadata, new ParseContext());
        
        // Geo tags
        assertEquals("12.54321", metadata.get(Metadata.LATITUDE));
        assertEquals("-54.1234", metadata.get(Metadata.LONGITUDE));
        
        // Core EXIF/TIFF tags
        assertEquals("100", metadata.get(Metadata.IMAGE_WIDTH));
        assertEquals("68", metadata.get(Metadata.IMAGE_LENGTH));
        assertEquals("8", metadata.get(Metadata.BITS_PER_SAMPLE));
        assertEquals(null, metadata.get(Metadata.SAMPLES_PER_PIXEL));
        
        assertEquals("6.25E-4", metadata.get(Metadata.EXPOSURE_TIME)); // 1/1600
        assertEquals("5.6", metadata.get(Metadata.F_NUMBER));
        assertEquals("false", metadata.get(Metadata.FLASH_FIRED));
        assertEquals("194.0", metadata.get(Metadata.FOCAL_LENGTH));
        assertEquals("400", metadata.get(Metadata.ISO_SPEED_RATINGS));
        assertEquals("Canon", metadata.get(Metadata.EQUIPMENT_MAKE));
        assertEquals("Canon EOS 40D", metadata.get(Metadata.EQUIPMENT_MODEL));
        assertEquals("Adobe Photoshop CS3 Macintosh", metadata.get(Metadata.SOFTWARE));
        assertEquals(null, metadata.get(Metadata.ORIENTATION)); // Not present
        assertEquals("240.0", metadata.get(Metadata.RESOLUTION_HORIZONTAL));
        assertEquals("240.0", metadata.get(Metadata.RESOLUTION_VERTICAL));
        assertEquals("Inch", metadata.get(Metadata.RESOLUTION_UNIT));
        
        // Common tags
        assertEquals("Date/Time Original for when the photo was taken, unspecified time zone",
                "2009-08-11T09:09:45", metadata.get(TikaCoreProperties.CREATED));
        assertEquals("This image has different Date/Time than Date/Time Original, so it is probably modification date",
                "2009-10-02T23:02:49", metadata.get(Metadata.LAST_MODIFIED));
        assertEquals("Date/Time Original should be stored in EXIF field too",
                "2009-08-11T09:09:45", metadata.get(TIFF.ORIGINAL_DATE));
        assertEquals("canon-55-250", metadata.getValues(TikaCoreProperties.KEYWORDS)[0]);
        assertEquals("canon-55-250", metadata.getValues(Metadata.KEYWORDS)[0]);
    }

    /**
     * Test for an image with the geographic information stored in a slightly
     *  different way, see TIKA-915 for details
     * Disabled for now, pending a fix to the underlying library
     */
    public void testJPEGGeo2() throws Exception {
       Metadata metadata = new Metadata();
       metadata.set(Metadata.CONTENT_TYPE, "image/jpeg");
       InputStream stream =
          getClass().getResourceAsStream("/test-documents/testJPEG_GEO_2.jpg");
       parser.parse(stream, new DefaultHandler(), metadata, new ParseContext());

       // Geo tags should be there with 5dp, and not rounded
       assertEquals("51.575762", metadata.get(Metadata.LATITUDE));
       assertEquals("-1.567886", metadata.get(Metadata.LONGITUDE));
    }
    
    public void testJPEGTitleAndDescription() throws Exception {
        Metadata metadata = new Metadata();
        metadata.set(Metadata.CONTENT_TYPE, "image/jpeg");
        InputStream stream =
            getClass().getResourceAsStream("/test-documents/testJPEG_commented.jpg");
        parser.parse(stream, new DefaultHandler(), metadata, new ParseContext());
          
        // embedded comments with non-ascii characters
        assertEquals("Tosteberga \u00C4ngar", metadata.get(TikaCoreProperties.TITLE));
        assertEquals("Bird site in north eastern Sk\u00E5ne, Sweden.\n(new line)", metadata.get(TikaCoreProperties.DESCRIPTION));
        assertEquals("Some Tourist", metadata.get(TikaCoreProperties.CREATOR)); // Dublin Core
        // xmp handles spaces in keywords, returns "bird watching, nature reserve, coast, grazelands"
        // but we have to replace them with underscore
        
        List<String> keywords = Arrays.asList(metadata.getValues(Metadata.KEYWORDS));
        assertTrue(keywords.contains("coast"));
        assertTrue(keywords.contains("bird watching"));
        assertEquals(keywords, Arrays.asList(metadata.getValues(TikaCoreProperties.KEYWORDS)));
        
        // Core EXIF/TIFF tags
        assertEquals("103", metadata.get(Metadata.IMAGE_WIDTH));
        assertEquals("77", metadata.get(Metadata.IMAGE_LENGTH));
        assertEquals("8", metadata.get(Metadata.BITS_PER_SAMPLE));
        assertEquals(null, metadata.get(Metadata.SAMPLES_PER_PIXEL));
        
        assertEquals("1.0E-6", metadata.get(Metadata.EXPOSURE_TIME)); // 1/1000000
        assertEquals("2.8", metadata.get(Metadata.F_NUMBER));
        assertEquals("4.6", metadata.get(Metadata.FOCAL_LENGTH));
        assertEquals("114", metadata.get(Metadata.ISO_SPEED_RATINGS));
        assertEquals(null, metadata.get(Metadata.EQUIPMENT_MAKE));
        assertEquals(null, metadata.get(Metadata.EQUIPMENT_MODEL));
        assertEquals(null, metadata.get(Metadata.SOFTWARE));
        assertEquals("1", metadata.get(Metadata.ORIENTATION)); // Not present
        assertEquals("300.0", metadata.get(Metadata.RESOLUTION_HORIZONTAL));
        assertEquals("300.0", metadata.get(Metadata.RESOLUTION_VERTICAL));
        assertEquals("Inch", metadata.get(Metadata.RESOLUTION_UNIT));          
    }
    
    public void testJPEGTitleAndDescriptionPhotoshop() throws Exception {
        Metadata metadata = new Metadata();
        metadata.set(Metadata.CONTENT_TYPE, "image/jpeg");
        InputStream stream =
            getClass().getResourceAsStream("/test-documents/testJPEG_commented_pspcs2mac.jpg");
        parser.parse(stream, new DefaultHandler(), metadata, new ParseContext());
          
        // embedded comments with non-ascii characters
        assertEquals("Tosteberga \u00C4ngar", metadata.get(TikaCoreProperties.TITLE));
        assertEquals("Bird site in north eastern Sk\u00E5ne, Sweden.\n(new line)", metadata.get(TikaCoreProperties.DESCRIPTION));
        assertEquals("Some Tourist", metadata.get(TikaCoreProperties.CREATOR));
        List<String> keywords = Arrays.asList(metadata.getValues(TikaCoreProperties.KEYWORDS));
        assertTrue("got " + keywords, keywords.contains("bird watching")); 
        List<String> subject = Arrays.asList(metadata.getValues(Metadata.SUBJECT));
        assertTrue("got " + subject, subject.contains("bird watching")); 
    }
    
    public void testJPEGTitleAndDescriptionXnviewmp() throws Exception {
        Metadata metadata = new Metadata();
        metadata.set(Metadata.CONTENT_TYPE, "image/jpeg");
        InputStream stream =
            getClass().getResourceAsStream("/test-documents/testJPEG_commented_xnviewmp026.jpg");
        parser.parse(stream, new DefaultHandler(), metadata, new ParseContext());
          
        // XnViewMp's default comment dialog has only comment, not headline.
        // Comment is embedded only if "Write comments in XMP" is enabled in settings
        assertEquals("Bird site in north eastern Sk\u00E5ne, Sweden.\n(new line)", metadata.get(TikaCoreProperties.DESCRIPTION));
        // xmp handles spaces in keywords, returns "bird watching, nature reserve, coast, grazelands"
        // but we have to replace them with underscore
        String[] subject = metadata.getValues(TikaCoreProperties.KEYWORDS);
        List<String> keywords = Arrays.asList(subject);
        assertTrue("'coast'" + " not in " + keywords, keywords.contains("coast"));
        assertTrue("'nature reserve'" + " not in " + keywords, keywords.contains("nature reserve"));     
    }
    
    public void testJPEGoddTagComponent() throws Exception {
       Metadata metadata = new Metadata();
       metadata.set(Metadata.CONTENT_TYPE, "image/jpeg");
       InputStream stream =
           getClass().getResourceAsStream("/test-documents/testJPEG_oddTagComponent.jpg");
       parser.parse(stream, new DefaultHandler(), metadata, new ParseContext());
       
       assertEquals(null, metadata.get(TikaCoreProperties.TITLE));
       assertEquals(null, metadata.get(TikaCoreProperties.DESCRIPTION));
       assertEquals("251", metadata.get(Metadata.IMAGE_WIDTH));
       assertEquals("384", metadata.get(Metadata.IMAGE_LENGTH));
    }
}
