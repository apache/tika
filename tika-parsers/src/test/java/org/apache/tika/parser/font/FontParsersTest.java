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
package org.apache.tika.parser.font;

import static org.apache.tika.TikaTest.assertContains;
import static org.apache.tika.parser.font.AdobeFontMetricParser.MET_FONT_FAMILY_NAME;
import static org.apache.tika.parser.font.AdobeFontMetricParser.MET_FONT_FULL_NAME;
import static org.apache.tika.parser.font.AdobeFontMetricParser.MET_FONT_NAME;
import static org.apache.tika.parser.font.AdobeFontMetricParser.MET_FONT_SUB_FAMILY_NAME;
import static org.apache.tika.parser.font.AdobeFontMetricParser.MET_FONT_VERSION;
import static org.apache.tika.parser.font.AdobeFontMetricParser.MET_FONT_WEIGHT;
import static org.apache.tika.parser.font.AdobeFontMetricParser.MET_PS_NAME;
import static org.junit.Assert.assertEquals;

import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.junit.Test;
import org.xml.sax.ContentHandler;

/**
 * Test case for parsing various different font files.
 */
public class FontParsersTest {
    @Test
    public void testAdobeFontMetricParsing() throws Exception {
        Parser parser = new AutoDetectParser(); // Should auto-detect!
        ContentHandler handler = new BodyContentHandler();
        Metadata metadata = new Metadata();
        ParseContext context = new ParseContext();
        TikaInputStream stream = TikaInputStream.get(
                FontParsersTest.class.getResource(
                        "/test-documents/testAFM.afm"));

        try {
            parser.parse(stream, handler, metadata, context);
        } finally {
            stream.close();
        }

        assertEquals("application/x-font-adobe-metric", metadata.get(Metadata.CONTENT_TYPE));
        assertEquals("TestFullName", metadata.get(TikaCoreProperties.TITLE));
        assertEquals("Fri Jul 15 17:50:51 2011", metadata.get(Metadata.CREATION_DATE));
        
        assertEquals("TestFontName", metadata.get(MET_FONT_NAME));
        assertEquals("TestFullName", metadata.get(MET_FONT_FULL_NAME));
        assertEquals("TestSymbol",   metadata.get(MET_FONT_FAMILY_NAME));
        
        assertEquals("Medium",  metadata.get(MET_FONT_WEIGHT));
        assertEquals("001.008", metadata.get(MET_FONT_VERSION));

        String content = handler.toString();

        // Test that the comments got extracted
        assertContains("Comments", content);
        assertContains("This is a comment in a sample file", content);
        assertContains("UniqueID 12345", content);
    }
    
    @Test
    public void testTTFParsing() throws Exception {
        Parser parser = new AutoDetectParser(); // Should auto-detect!
        ContentHandler handler = new BodyContentHandler();
        Metadata metadata = new Metadata();
        ParseContext context = new ParseContext();
        //Open Sans font is ASL 2.0 according to 
        //http://www.google.com/fonts/specimen/Open+Sans
        //...despite the copyright in the file's metadata.
        TikaInputStream stream = TikaInputStream.get(
                FontParsersTest.class.getResource(
                        "/test-documents/testTrueType3.ttf"));
        
        try {
            parser.parse(stream, handler, metadata, context);
        } finally {
            stream.close();
        }

        assertEquals("application/x-font-ttf", metadata.get(Metadata.CONTENT_TYPE));
        assertEquals("Open Sans Bold", metadata.get(TikaCoreProperties.TITLE));

        assertEquals("2010-12-30T11:04:00Z", metadata.get(Metadata.CREATION_DATE));
        assertEquals("2010-12-30T11:04:00Z", metadata.get(TikaCoreProperties.CREATED));
        assertEquals("2011-05-05T12:37:53Z", metadata.get(TikaCoreProperties.MODIFIED));
        
        assertEquals("Open Sans Bold", metadata.get(MET_FONT_NAME));
        assertEquals("Open Sans", metadata.get(MET_FONT_FAMILY_NAME));
        assertEquals("Bold", metadata.get(MET_FONT_SUB_FAMILY_NAME));
        assertEquals("OpenSans-Bold", metadata.get(MET_PS_NAME));
        
        assertEquals("Digitized", metadata.get("Copyright").substring(0, 9));
        assertEquals("Open Sans", metadata.get("Trademark").substring(0, 9));
        
        // Not extracted
        assertEquals(null, metadata.get(MET_FONT_FULL_NAME));
        assertEquals(null, metadata.get(MET_FONT_WEIGHT));
        assertEquals(null, metadata.get(MET_FONT_VERSION));

        // Currently, the parser doesn't extract any contents
        String content = handler.toString();
        assertEquals("", content);
    }
}
