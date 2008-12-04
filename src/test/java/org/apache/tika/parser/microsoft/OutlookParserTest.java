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
package org.apache.tika.parser.microsoft;

import java.io.InputStream;

import junit.framework.TestCase;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.xml.sax.ContentHandler;

/**
 * Test case for parsing Outlook files.
 */
public class OutlookParserTest extends TestCase {

    public void testOutlookParsing() throws Exception {
        Parser parser = new AutoDetectParser(); // Should auto-detect!
        ContentHandler handler = new BodyContentHandler();
        Metadata metadata = new Metadata();

        InputStream stream = OutlookParserTest.class.getResourceAsStream(
                "/test-documents/test-outlook.msg");
        try {
            parser.parse(stream, handler, metadata);
        } finally {
            stream.close();
        }

        assertEquals(
                "application/vnd.ms-outlook",
                metadata.get(Metadata.CONTENT_TYPE));
        assertEquals(
                "Microsoft Outlook Express 6",
                metadata.get(Metadata.TITLE));
        // TODO: There's apparently some encoding issue in POI
        //assertEquals(
        //        "L'\u00C9quipe Microsoft Outlook Express",
        //        metadata.get(Metadata.AUTHOR));

        String content = handler.toString();
        assertTrue(content.contains("Microsoft Outlook Express 6"));
        //assertTrue(content.contains("L'\u00C9quipe Microsoft Outlook Express"));
        assertTrue(content.contains("Nouvel utilisateur de Outlook Express"));
        assertTrue(content.contains("Messagerie et groupes de discussion"));
    }

}
