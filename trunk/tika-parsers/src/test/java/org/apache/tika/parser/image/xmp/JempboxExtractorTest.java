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
package org.apache.tika.parser.image.xmp;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collection;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.image.xmp.JempboxExtractor;

import junit.framework.TestCase;

public class JempboxExtractorTest extends TestCase {
   
    public void testParseJpeg() throws IOException, TikaException {
        Metadata metadata = new Metadata();
        InputStream stream = getClass().getResourceAsStream("/test-documents/testJPEG_commented.jpg");
        // set some values before extraction to see that they are overridden
        metadata.set(Metadata.TITLE, "old title");
        metadata.set(Metadata.DESCRIPTION, "old description");
        metadata.set(Metadata.CREATOR, "previous author");
        // ... or kept in case the field is multi-value
        metadata.add(Metadata.SUBJECT, "oldkeyword");
        
        JempboxExtractor extractor = new JempboxExtractor(metadata);
        extractor.parse(stream);
        
        // DublinCore fields
        assertEquals("Tosteberga \u00C4ngar", metadata.get(Metadata.TITLE));
        assertEquals("Bird site in north eastern Sk\u00E5ne, Sweden.\n(new line)", metadata.get(Metadata.DESCRIPTION));
        assertEquals("Some Tourist", metadata.get(Metadata.CREATOR));
        Collection<String> keywords = Arrays.asList(metadata.getValues(Metadata.SUBJECT));  
        assertTrue(keywords.contains("oldkeyword"));
        assertTrue(keywords.contains("grazelands"));
        assertTrue(keywords.contains("nature reserve"));
        assertTrue(keywords.contains("bird watching"));
        assertTrue(keywords.contains("coast"));
    }

    public void testParseJpegPhotoshop() throws IOException, TikaException {
        Metadata metadata = new Metadata();
        InputStream stream = getClass().getResourceAsStream("/test-documents/testJPEG_commented_pspcs2mac.jpg");
       
        JempboxExtractor extractor = new JempboxExtractor(metadata);
        extractor.parse(stream);
        
        // DublinCore fields
        assertEquals("Tosteberga \u00C4ngar", metadata.get(Metadata.TITLE));
        assertEquals("Bird site in north eastern Sk\u00E5ne, Sweden.\n(new line)", metadata.get(Metadata.DESCRIPTION));
        assertEquals("Some Tourist", metadata.get(Metadata.CREATOR));
        Collection<String> keywords = Arrays.asList(metadata.getValues(Metadata.SUBJECT));  
        assertTrue(keywords.contains("bird watching"));
        assertTrue(keywords.contains("coast"));
    }
    
    public void testParseJpegXnviewmp() throws IOException, TikaException {
        Metadata metadata = new Metadata();
        InputStream stream = getClass().getResourceAsStream("/test-documents/testJPEG_commented_xnviewmp026.jpg");
       
        JempboxExtractor extractor = new JempboxExtractor(metadata);
        extractor.parse(stream);
        
        // XnViewMp fields not understood by Jempbox
        assertEquals("Bird site in north eastern Sk\u00E5ne, Sweden.\n(new line)", metadata.get(Metadata.DESCRIPTION));
        Collection<String> keywords = Arrays.asList(metadata.getValues(Metadata.SUBJECT));
        assertTrue(keywords.contains("coast"));
        assertTrue(keywords.contains("nature reserve"));
    }
    
    public void testJoinCreators() {
        assertEquals("Mr B", new JempboxExtractor(null).joinCreators(
                Arrays.asList("Mr B")));
        // TODO use multi-value property instead?
        assertEquals("Mr B, Mr A", new JempboxExtractor(null).joinCreators(
                Arrays.asList("Mr B", "Mr A")));
    }

}
