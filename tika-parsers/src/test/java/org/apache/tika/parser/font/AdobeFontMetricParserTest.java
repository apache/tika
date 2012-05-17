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

import junit.framework.TestCase;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.xml.sax.ContentHandler;
import org.apache.tika.io.TikaInputStream;

/**
 * Test case for parsing afm files.
 */
public class AdobeFontMetricParserTest extends TestCase {
    public void testAdobeFontMetricParsing() throws Exception {
        Parser parser = new AutoDetectParser(); // Should auto-detect!
        ContentHandler handler = new BodyContentHandler();
        Metadata metadata = new Metadata();
        ParseContext context = new ParseContext();
        TikaInputStream stream = TikaInputStream.get(
                AdobeFontMetricParserTest.class.getResource(
                        "/test-documents/testAFM.afm"));

        try {
            parser.parse(stream, handler, metadata, context);
        } finally {
            stream.close();
        }

        assertEquals("application/x-font-adobe-metric", metadata.get(Metadata.CONTENT_TYPE));
        assertEquals("TestFullName", metadata.get(TikaCoreProperties.TITLE));
        assertEquals("Fri Jul 15 17:50:51 2011", metadata.get(Metadata.CREATION_DATE));
        
        assertEquals("TestFontName", metadata.get("FontName"));
        assertEquals("TestFullName", metadata.get("FontFullName"));
        assertEquals("TestSymbol",   metadata.get("FontFamilyName"));
        
        assertEquals("Medium",  metadata.get("FontWeight"));
        assertEquals("001.008", metadata.get("FontVersion"));

        String content = handler.toString();

        // Test that the comments got extracted
        assertTrue(content.contains("Comments"));
        assertTrue(content.contains("This is a comment in a sample file"));
        assertTrue(content.contains("UniqueID 12345"));
    }
}
