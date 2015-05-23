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
package org.apache.tika.parser.pkg;

import static org.junit.Assert.assertEquals;

import java.io.InputStream;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.junit.Ignore;
import org.junit.Test;
import org.xml.sax.ContentHandler;

/**
 * Test case for parsing zlib compressed
 * 
 * Note - currently disabled, pending a fix for COMPRESS-316
 */
public class ZlibParserTest extends AbstractPkgTest {
    @Test
    @Ignore
    public void testZlibParsing() throws Exception {
        Parser parser = new AutoDetectParser(); // Should auto-detect!
        ContentHandler handler = new BodyContentHandler();
        Metadata metadata = new Metadata();

        InputStream stream = ZipParserTest.class.getResourceAsStream(
                "/test-documents/testTXT.zlib");
        try {
            parser.parse(stream, handler, metadata, recursingContext);
        } finally {
            stream.close();
        }

        assertEquals("application/zlib", metadata.get(Metadata.CONTENT_TYPE));
        String content = handler.toString();
        assertContains("Test d'indexation de Txt", content);
        assertContains("http://www.apache.org", content);
    }

    /**
     * Tests that the ParseContext parser is correctly
     *  fired for all the embedded entries.
     */
    @Test
    @Ignore
    public void testEmbedded() throws Exception {
       Parser parser = new AutoDetectParser(); // Should auto-detect!
       ContentHandler handler = new BodyContentHandler();
       Metadata metadata = new Metadata();

       InputStream stream = ZipParserTest.class.getResourceAsStream(
               "/test-documents/testTXT.zlib");
       try {
           parser.parse(stream, handler, metadata, trackingContext);
       } finally {
           stream.close();
       }
       
       // Should have found a single text document inside
       assertEquals(1, tracker.filenames.size());
       assertEquals(1, tracker.mediatypes.size());
       assertEquals(1, tracker.modifiedAts.size());
       
       // Won't have names, dates or types, as zlib doesn't have that 
       assertEquals(null, tracker.filenames.get(0));
       assertEquals(null, tracker.mediatypes.get(0));
       assertEquals(null, tracker.createdAts.get(0));
       assertEquals(null, tracker.modifiedAts.get(0));
    }
}
