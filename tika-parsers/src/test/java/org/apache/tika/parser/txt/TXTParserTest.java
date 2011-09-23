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
import org.apache.tika.parser.ParseContext;
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
                metadata,
                new ParseContext());
        String content = writer.toString();

        assertEquals("text/plain", metadata.get(Metadata.CONTENT_TYPE));
        
        // TIKA-501: Remove language detection from TXTParser
        assertNull(metadata.get(Metadata.CONTENT_LANGUAGE));
        assertNull(metadata.get(Metadata.LANGUAGE));

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
                handler, metadata, new ParseContext());
        assertEquals("text/plain", metadata.get(Metadata.CONTENT_TYPE));
        assertEquals("UTF-8", metadata.get(Metadata.CONTENT_ENCODING));

        assertTrue(handler.toString().contains(text));
    }

    public void testEmptyText() throws Exception {
        ContentHandler handler = new BodyContentHandler();
        Metadata metadata = new Metadata();
        parser.parse(
                new ByteArrayInputStream(new byte[0]), handler, metadata, new ParseContext());
        assertEquals("text/plain", metadata.get(Metadata.CONTENT_TYPE));
        assertEquals("\n", handler.toString());
    }

    /**
     * Test case for TIKA-240: Drop the BOM when extracting plain text
     *
     * @see <a href="https://issues.apache.org/jira/browse/TIKA-240">TIKA-240</a> 
     */
    public void testDropByteOrderMark() throws Exception {
        assertExtractText("UTF-8 BOM", "test", new byte[] {
                (byte) 0xEF, (byte) 0xBB, (byte) 0xBF, 't', 'e', 's', 't' });
        assertExtractText("UTF-16 BE BOM", "test", new byte[] {
                (byte) 0xFE, (byte) 0xFF, 0, 't', 0, 'e', 0, 's', 0, 't'});
        assertExtractText("UTF-16 LE BOM", "test", new byte[] {
                (byte) 0xFF, (byte) 0xFE, 't', 0, 'e', 0, 's', 0, 't', 0});
    }

    /**
     * Test case for TIKA-335: using incoming charset
     *
     * @see <a href="https://issues.apache.org/jira/browse/TIKA-335">TIKA-335</a> 
     */
    public void testUseIncomingCharsetAsHint() throws Exception {
        // Could be UTF-8 or ISO 8859-1 or ...
        // u00e1 is latin small letter a with acute
        final String test2 = "the name is \u00e1ndre";

        Metadata metadata = new Metadata();
        parser.parse(
                new ByteArrayInputStream(test2.getBytes("UTF-8")),
                new BodyContentHandler(),  metadata, new ParseContext());
        
        assertEquals("UTF-8", metadata.get(Metadata.CONTENT_ENCODING));

        metadata.set(Metadata.CONTENT_ENCODING, "ISO-8859-1");
        parser.parse(
                new ByteArrayInputStream(test2.getBytes("UTF-8")),
                new BodyContentHandler(),  metadata, new ParseContext());
        
        assertEquals("ISO-8859-1", metadata.get(Metadata.CONTENT_ENCODING));
    }

    /**
     * Test case for TIKA-341: using charset in content-type
     *
     * @see <a href="https://issues.apache.org/jira/browse/TIKA-341">TIKA-341</a> 
     */
    public void testUsingCharsetInContentTypeHeader() throws Exception {
        // Could be UTF-8 or ISO 8859-1 or ...
        // u00e1 is latin small letter a with acute
        final String test2 = "the name is \u00e1ndre";

        Metadata metadata = new Metadata();
        parser.parse(
                new ByteArrayInputStream(test2.getBytes("UTF-8")),
                new BodyContentHandler(),  metadata, new ParseContext());

        assertEquals("UTF-8", metadata.get(Metadata.CONTENT_ENCODING));

        metadata = new Metadata();
        metadata.set(Metadata.CONTENT_TYPE, "text/html; charset=ISO-8859-1");
        parser.parse(
                new ByteArrayInputStream(test2.getBytes("UTF-8")),
                new BodyContentHandler(),  metadata, new ParseContext());

        assertEquals("ISO-8859-1", metadata.get(Metadata.CONTENT_ENCODING));
    }

    private void assertExtractText(String msg, String expected, byte[] input)
            throws Exception {
        ContentHandler handler = new BodyContentHandler() {
            public void ignorableWhitespace(char[] ch, int off, int len) {
                // Ignore the whitespace added by XHTMLContentHandler
            }
        };
        Metadata metadata = new Metadata();
        parser.parse(new ByteArrayInputStream(input), handler, metadata, new ParseContext());
        assertEquals("text/plain", metadata.get(Metadata.CONTENT_TYPE));
        assertEquals(msg, expected, handler.toString());
    }

    /**
     * Test case for TIKA-339: don't override incoming language
     *
     * @see <a href="https://issues.apache.org/jira/browse/TIKA-335">TIKA-335</a> 
     */
    public void testRetainIncomingLanguage() throws Exception {
        final String test = "Simple Content";

        Metadata metadata = new Metadata();
        metadata.set(Metadata.LANGUAGE, "en");

        parser.parse(
                new ByteArrayInputStream(test.getBytes("UTF-8")),
                new BodyContentHandler(),  metadata, new ParseContext());

        assertEquals("en", metadata.get(Metadata.LANGUAGE));
    }

    public void testCP866() throws Exception {
        Metadata metadata = new Metadata();
        StringWriter writer = new StringWriter();
        parser.parse(
                TXTParserTest.class.getResourceAsStream("/test-documents/russian.cp866.txt"),
                new WriteOutContentHandler(writer),
                metadata,
                new ParseContext());

        assertEquals("text/plain", metadata.get(Metadata.CONTENT_TYPE));
        assertEquals("IBM866", metadata.get(Metadata.CONTENT_ENCODING));
    }

    public void testEBCDIC_CP500() throws Exception {
        Metadata metadata = new Metadata();
        StringWriter writer = new StringWriter();
        parser.parse(
                TXTParserTest.class.getResourceAsStream("/test-documents/english.cp500.txt"),
                new WriteOutContentHandler(writer),
                metadata,
                new ParseContext());

        assertEquals("text/plain", metadata.get(Metadata.CONTENT_TYPE));
        assertEquals("IBM500", metadata.get(Metadata.CONTENT_ENCODING));
        
        // Additional check that it isn't too eager on short blocks of text
        metadata = new Metadata();
        writer = new StringWriter();
        parser.parse(
                new ByteArrayInputStream("<html><body>hello world</body></html>".getBytes("UTF-8")),
                new WriteOutContentHandler(writer),
                metadata,
                new ParseContext());

        assertNotSame("IBM500", metadata.get(Metadata.CONTENT_ENCODING));
    }

}
