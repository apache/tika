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

import junit.framework.TestCase;

import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.image.TiffParser;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.xml.sax.helpers.DefaultHandler;

import java.io.InputStream;
import java.util.Arrays;
import java.util.List;

public class TiffParserTest extends TestCase {
    private final Parser parser = new TiffParser();

    public void testTIFF() throws Exception {
        Metadata metadata = new Metadata();
        metadata.set(Metadata.CONTENT_TYPE, "image/tiff");
        InputStream stream =
            getClass().getResourceAsStream("/test-documents/testTIFF.tif");
        parser.parse(stream, new DefaultHandler(), metadata, new ParseContext());

        assertEquals("Licensed to the Apache Software Foundation (ASF) under one or " +
        		"more contributor license agreements.  See the NOTICE file " +
        		"distributed with this work for additional information regarding " +
        		"copyright ownership.", metadata.get(TikaCoreProperties.DESCRIPTION));
        
        // All EXIF/TIFF tags
        assertEquals("Inch", metadata.get(Metadata.RESOLUTION_UNIT));
        
        // Core EXIF/TIFF tags
        assertEquals("100", metadata.get(Metadata.IMAGE_WIDTH));
        assertEquals("75", metadata.get(Metadata.IMAGE_LENGTH));
        assertEquals("8", metadata.get(Metadata.BITS_PER_SAMPLE));
        assertEquals("3", metadata.get(Metadata.SAMPLES_PER_PIXEL));
        
        // Embedded XMP
        List<String> keywords = Arrays.asList(metadata.getValues(TikaCoreProperties.KEYWORDS));
        assertTrue("got " + keywords, keywords.contains("cat"));
        assertTrue("got " + keywords, keywords.contains("garden"));
        List<String> subject = Arrays.asList(metadata.getValues(Metadata.SUBJECT));
        assertTrue("got " + subject, subject.contains("cat"));
        assertTrue("got " + subject, subject.contains("garden"));
    }
}
