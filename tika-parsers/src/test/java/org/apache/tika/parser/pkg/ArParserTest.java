/**
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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.InputStream;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.junit.Test;
import org.xml.sax.ContentHandler;

public class ArParserTest extends AbstractPkgTest {
    @Test
    public void testArParsing() throws Exception {
        Parser parser = new AutoDetectParser();

        ContentHandler handler = new BodyContentHandler();
        Metadata metadata = new Metadata();

        InputStream stream = ArParserTest.class.getResourceAsStream(
                "/test-documents/testARofText.ar");
        try {
            parser.parse(stream, handler, metadata, recursingContext);
        } finally {
            stream.close();
        }

        assertEquals("application/x-archive",
                     metadata.get(Metadata.CONTENT_TYPE));
        String content = handler.toString();
        assertContains("testTXT.txt", content);
        assertContains("Test d'indexation de Txt", content);
        assertContains("http://www.apache.org", content);

        stream = ArParserTest.class.getResourceAsStream(
                "/test-documents/testARofSND.ar");
        try {
            parser.parse(stream, handler, metadata, recursingContext);
        } finally {
            stream.close();
        }

        assertEquals("application/x-archive",
                     metadata.get(Metadata.CONTENT_TYPE));
        content = handler.toString();
        assertContains("testAU.au", content);
    }

    /**
     * Tests that the ParseContext parser is correctly fired for all the
     * embedded entries.
     */
    @Test
    public void testEmbedded() throws Exception {
        Parser parser = new AutoDetectParser(); // Should auto-detect!
        ContentHandler handler = new BodyContentHandler();
        Metadata metadata = new Metadata();

        InputStream stream = ArParserTest.class.getResourceAsStream(
                "/test-documents/testARofText.ar");
        try {
            parser.parse(stream, handler, metadata, trackingContext);
        } finally {
            stream.close();
        }

        assertEquals(1, tracker.filenames.size());
        assertEquals(1, tracker.mediatypes.size());
        assertEquals(1, tracker.modifiedAts.size());

        assertEquals("testTXT.txt", tracker.filenames.get(0));

        String modifiedAt = tracker.modifiedAts.get(0);
        assertTrue("Modified at " + modifiedAt, modifiedAt.startsWith("201"));

        for (String type : tracker.mediatypes) {
            assertNull(type);
        }
        for(String crt : tracker.createdAts) {
            assertNull(crt);
        }

        tracker.reset();
        stream = ArParserTest.class.getResourceAsStream(
                "/test-documents/testARofSND.ar");
        try {
            parser.parse(stream, handler, metadata, trackingContext);
        } finally {
            stream.close();
        }

        assertEquals(1, tracker.filenames.size());
        assertEquals(1, tracker.mediatypes.size());
        assertEquals(1, tracker.modifiedAts.size());
        assertEquals("testAU.au", tracker.filenames.get(0));

        modifiedAt = tracker.modifiedAts.get(0);
        assertTrue("Modified at " + modifiedAt, modifiedAt.startsWith("201"));
        
        for (String type : tracker.mediatypes) {
            assertNull(type);
        }
        for(String crt : tracker.createdAts) {
            assertNull(crt);
        }
    }
}
