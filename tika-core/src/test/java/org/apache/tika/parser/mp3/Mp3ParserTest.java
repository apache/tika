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
package org.apache.tika.parser.mp3;

import java.io.InputStream;

import junit.framework.TestCase;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.xml.sax.ContentHandler;

/**
 * Test case for parsing mp3 files.
 */
public class Mp3ParserTest extends TestCase {

    public void testMp3Parsing() throws Exception {
        Parser parser = new AutoDetectParser(); // Should auto-detect!
        ContentHandler handler = new BodyContentHandler();
        Metadata metadata = new Metadata();

        InputStream stream = Mp3ParserTest.class.getResourceAsStream(
                "/test-documents/testMP3.mp3");
        try {
            parser.parse(stream, handler, metadata);
        } finally {
            stream.close();
        }

        assertEquals("audio/mpeg", metadata.get(Metadata.CONTENT_TYPE));
        assertEquals("Test Title", metadata.get(Metadata.TITLE));
        assertEquals("Test Artist", metadata.get(Metadata.AUTHOR));

        String content = handler.toString();
        assertTrue(content.contains("Test Title"));
        assertTrue(content.contains("Test Artist"));
        assertTrue(content.contains("Test Album"));
        assertTrue(content.contains("2008"));
        assertTrue(content.contains("Test Comment"));
        assertTrue(content.contains("Rock"));
    }

}
