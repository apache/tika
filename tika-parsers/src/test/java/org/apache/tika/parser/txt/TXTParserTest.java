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
package org.apache.tika.parser.txt;

import java.io.ByteArrayInputStream;
import java.io.StringWriter;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.apache.tika.sax.WriteOutContentHandler;
import org.xml.sax.ContentHandler;

import junit.framework.TestCase;

public class TXTParserTest extends TestCase {

    private Parser parser = new TXTParser();

    public void testEnglishText() throws Exception {
        String text =
            "Hello, World! This is simple UTF-8 text content written"
            + " in English to test autodetection of both the character"
            + " encoding and the language of the input stream.";

        Metadata metadata = new Metadata();
        StringWriter writer = new StringWriter();
        parser.parse(
                new ByteArrayInputStream(text.getBytes("UTF-8")),
                new WriteOutContentHandler(writer),
                metadata);
        String content = writer.toString();

        assertEquals("text/plain", metadata.get(Metadata.CONTENT_TYPE));
        assertEquals("en", metadata.get(Metadata.CONTENT_LANGUAGE));
        assertEquals("en", metadata.get(Metadata.LANGUAGE));
        // TODO: ICU reports the content encoding as ISO-8859-1, even though
        // it could just as well be ASCII or UTF-8, so  for now we won't
        // test for the Metadata.CONTENT_ENCODING field

        assertTrue(content.contains("Hello"));
        assertTrue(content.contains("World"));
        assertTrue(content.contains("autodetection"));
        assertTrue(content.contains("stream"));
    }

    public void testUTF8Text() throws Exception {
        String text = "I\u00F1t\u00EBrn\u00E2ti\u00F4n\u00E0liz\u00E6ti\u00F8n";

        ContentHandler handler = new BodyContentHandler();
        Metadata metadata = new Metadata();
        parser.parse(
                new ByteArrayInputStream(text.getBytes("UTF-8")),
                handler, metadata);
        assertEquals("text/plain", metadata.get(Metadata.CONTENT_TYPE));
        assertEquals("UTF-8", metadata.get(Metadata.CONTENT_ENCODING));

        assertTrue(handler.toString().contains(text));
    }

    public void testEmptyText() throws Exception {
        ContentHandler handler = new BodyContentHandler();
        Metadata metadata = new Metadata();
        parser.parse(
                new ByteArrayInputStream(new byte[0]), handler, metadata);
        assertEquals("text/plain", metadata.get(Metadata.CONTENT_TYPE));
        assertEquals("\n", handler.toString());
    }

}
