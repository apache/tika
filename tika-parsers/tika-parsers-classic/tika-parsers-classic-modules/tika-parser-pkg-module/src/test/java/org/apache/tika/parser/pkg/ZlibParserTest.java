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

import org.junit.Test;
import org.xml.sax.ContentHandler;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.sax.BodyContentHandler;

/**
 * Test case for parsing zlib compressed
 */
public class ZlibParserTest extends AbstractPkgTest {

    /**
     * Tests that the ParseContext parser is correctly
     * fired for all the embedded entries.
     */
    @Test
    public void testEmbedded() throws Exception {
        ContentHandler handler = new BodyContentHandler();
        Metadata metadata = new Metadata();

        try (InputStream stream = getResourceAsStream("/test-documents/testTXT.zlib")) {
            AUTO_DETECT_PARSER.parse(stream, handler, metadata, trackingContext);
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
