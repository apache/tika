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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;

import org.junit.jupiter.api.Test;
import org.xml.sax.ContentHandler;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.sax.BodyContentHandler;

public class ArParserTest extends AbstractPkgTest {


    /**
     * Tests that the ParseContext parser is correctly fired for all the
     * embedded entries.
     */
    @Test
    public void testEmbedded() throws Exception {
        ContentHandler handler = new BodyContentHandler();
        Metadata metadata = new Metadata();

        try (InputStream stream = getResourceAsStream("/test-documents/testARofText.ar")) {
            AUTO_DETECT_PARSER.parse(stream, handler, metadata, trackingContext);
        }

        assertEquals(1, tracker.filenames.size());
        assertEquals(1, tracker.mediatypes.size());
        assertEquals(1, tracker.modifiedAts.size());

        assertEquals("testTXT.txt", tracker.filenames.get(0));

        String modifiedAt = tracker.modifiedAts.get(0);
        assertTrue(modifiedAt.startsWith("201"), "Modified at " + modifiedAt);

        for (String type : tracker.mediatypes) {
            assertNull(type);
        }
        for (String crt : tracker.createdAts) {
            assertNull(crt);
        }

        tracker.reset();
        try (InputStream stream = getResourceAsStream("/test-documents/testARofSND.ar")) {
            AUTO_DETECT_PARSER.parse(stream, handler, metadata, trackingContext);
        }

        assertEquals(1, tracker.filenames.size());
        assertEquals(1, tracker.mediatypes.size());
        assertEquals(1, tracker.modifiedAts.size());
        assertEquals("testAU.au", tracker.filenames.get(0));

        modifiedAt = tracker.modifiedAts.get(0);
        assertTrue(modifiedAt.startsWith("201"), "Modified at " + modifiedAt);

        for (String type : tracker.mediatypes) {
            assertNull(type);
        }
        for (String crt : tracker.createdAts) {
            assertNull(crt);
        }
    }
}
