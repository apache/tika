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

import static org.junit.Assert.assertEquals;
import static org.apache.tika.TikaTest.assertContains;

import java.util.TimeZone;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.xml.sax.ContentHandler;
import org.apache.tika.io.TikaInputStream;
import org.junit.Test;

import static org.apache.tika.parser.font.AdobeFontMetricParser.MET_FONT_NAME;
import static org.apache.tika.parser.font.AdobeFontMetricParser.MET_FONT_FULL_NAME;
import static org.apache.tika.parser.font.AdobeFontMetricParser.MET_FONT_FAMILY_NAME;
import static org.apache.tika.parser.font.AdobeFontMetricParser.MET_FONT_WEIGHT;
import static org.apache.tika.parser.font.AdobeFontMetricParser.MET_FONT_VERSION;
import static org.apache.tika.parser.font.AdobeFontMetricParser.MET_FONT_SUB_FAMILY_NAME;
import static org.apache.tika.parser.font.AdobeFontMetricParser.MET_PS_NAME;

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
        TikaInputStream stream = TikaInputStream.get(
                FontParsersTest.class.getResource(
                        "/test-documents/testTrueType.ttf"));

        //Pending PDFBOX-2122's integration (PDFBox 1.8.6)
        //we must set the default timezone to something
        //standard for this test.
        //TODO: once we upgrade to PDFBox 1.8.6, remove
        //this timezone code.
        TimeZone defaultTimeZone = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));

        try {
            parser.parse(stream, handler, metadata, context);
        } finally {
            //make sure to reset default timezone
            TimeZone.setDefault(defaultTimeZone);
            stream.close();
        }

        assertEquals("application/x-font-ttf", metadata.get(Metadata.CONTENT_TYPE));
        assertEquals("NewBaskervilleEF-Roman", metadata.get(TikaCoreProperties.TITLE));

        assertEquals("1904-01-01T00:00:00Z",   metadata.get(Metadata.CREATION_DATE));
        assertEquals("1904-01-01T00:00:00Z",   metadata.get(TikaCoreProperties.CREATED));
        assertEquals("1904-01-01T00:00:00Z",   metadata.get(TikaCoreProperties.MODIFIED));
        
        assertEquals("NewBaskervilleEF-Roman", metadata.get(MET_FONT_NAME));
        assertEquals("NewBaskerville",         metadata.get(MET_FONT_FAMILY_NAME));
        assertEquals("Regular",                metadata.get(MET_FONT_SUB_FAMILY_NAME));
        assertEquals("NewBaskervilleEF-Roman", metadata.get(MET_PS_NAME));
        
        assertEquals("Copyright",           metadata.get("Copyright").substring(0, 9));
        assertEquals("ITC New Baskerville", metadata.get("Trademark").substring(0, 19));
        
        // Not extracted
        assertEquals(null, metadata.get(MET_FONT_FULL_NAME));
        assertEquals(null, metadata.get(MET_FONT_WEIGHT));
        assertEquals(null, metadata.get(MET_FONT_VERSION));

        // Currently, the parser doesn't extract any contents
        String content = handler.toString();
        assertEquals("", content);
    }
}
