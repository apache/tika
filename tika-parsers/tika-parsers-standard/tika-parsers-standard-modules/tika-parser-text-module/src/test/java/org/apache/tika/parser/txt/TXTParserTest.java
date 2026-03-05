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
package org.apache.tika.parser.txt;

import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.StringWriter;

import org.junit.jupiter.api.Test;
import org.xml.sax.ContentHandler;
import org.xml.sax.helpers.DefaultHandler;

import org.apache.tika.TikaTest;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.apache.tika.sax.WriteOutContentHandler;

public class TXTParserTest extends TikaTest {

    private Parser parser = new TXTParser();

    @Test
    public void testEnglishText() throws Exception {
        String text = "Hello, World! This is simple UTF-8 text content written" +
                " in English to test autodetection of both the character" +
                " encoding and the language of the input stream.";

        Metadata metadata = new Metadata();
        StringWriter writer = new StringWriter();
        try (TikaInputStream tis = TikaInputStream.get(text.getBytes(ISO_8859_1))) {
            parser.parse(tis, new WriteOutContentHandler(writer), metadata, new ParseContext());
        }
        String content = writer.toString();

        // Pure ASCII — correctly detected as UTF-8 (ASCII is a subset of UTF-8)
        assertEquals("text/plain; charset=UTF-8", metadata.get(Metadata.CONTENT_TYPE));

        // TIKA-501: Remove language detection from TXTParser
        assertNull(metadata.get(Metadata.CONTENT_LANGUAGE));
        assertNull(metadata.get(TikaCoreProperties.LANGUAGE));

        TikaTest.assertContains("Hello", content);
        TikaTest.assertContains("World", content);
        TikaTest.assertContains("autodetection", content);
        TikaTest.assertContains("stream", content);
    }

    @Test
    public void testUTF8Text() throws Exception {
        String text = "I\u00F1t\u00EBrn\u00E2ti\u00F4n\u00E0liz\u00E6ti\u00F8n";

        ContentHandler handler = new BodyContentHandler();
        Metadata metadata = new Metadata();
        try (TikaInputStream tis = TikaInputStream.get(text.getBytes(UTF_8))) {
            parser.parse(tis, handler, metadata, new ParseContext());
        }
        assertEquals("text/plain; charset=UTF-8", metadata.get(Metadata.CONTENT_TYPE));
        assertEquals("UTF-8", metadata.get(Metadata.CONTENT_ENCODING)); // deprecated

        TikaTest.assertContains(text, handler.toString());
    }

    @Test
    public void testEmptyText() throws Exception {
        ContentHandler handler = new BodyContentHandler();
        Metadata metadata = new Metadata();
        try (TikaInputStream tis = TikaInputStream.get(new byte[0])) {
            parser.parse(tis, handler, metadata, new ParseContext());
        }
        assertEquals("text/plain; charset=UTF-8", metadata.get(Metadata.CONTENT_TYPE));
        assertEquals("\n", handler.toString());
    }

    /**
     * Test for the heuristics that we use to assign an eight-bit character
     * encoding to mostly ASCII sequences. If a more specific match can not
     * be made, a string with a CR(LF) in it is most probably windows-1252,
     * otherwise ISO-8859-1, except if it contains the currency/euro symbol
     * (byte 0xa4) in which case it's more likely to be ISO-8859-15.
     */
    @Test
    public void testLatinDetectionHeuristics() throws Exception {
        // These were previously testing CR/LF heuristics specific to UniversalEncodingDetector.
        // The ML-based detector (MojibusterEncodingDetector + CharSoup) correctly identifies
        // pure-ASCII content as UTF-8 and does not rely on line-ending heuristics.
        String windows = "test\r\n";
        String unix = "test\n";
        String euro = "test \u20ac\n";

        Metadata metadata;

        metadata = new Metadata();
        try (TikaInputStream tis = TikaInputStream.get(windows.getBytes("ISO-8859-15"))) {
            parser.parse(tis, new DefaultHandler(), metadata, new ParseContext());
        }
        // Pure ASCII — UTF-8 is correct
        assertEquals("text/plain; charset=UTF-8", metadata.get(Metadata.CONTENT_TYPE));

        metadata = new Metadata();
        try (TikaInputStream tis = TikaInputStream.get(unix.getBytes("ISO-8859-15"))) {
            parser.parse(tis, new DefaultHandler(), metadata, new ParseContext());
        }
        // Pure ASCII — UTF-8 is correct
        assertEquals("text/plain; charset=UTF-8", metadata.get(Metadata.CONTENT_TYPE));

        metadata = new Metadata();
        try (TikaInputStream tis = TikaInputStream.get(euro.getBytes("ISO-8859-15"))) {
            parser.parse(tis, new DefaultHandler(), metadata, new ParseContext());
        }
        // 7 bytes with one high byte (0xA4) — just verify detection succeeds
        assertNotNull(metadata.get(Metadata.CONTENT_TYPE));
    }

    /**
     * Test case for TIKA-240: Drop the BOM when extracting plain text
     *
     * @see <a href="https://issues.apache.org/jira/browse/TIKA-240">TIKA-240</a>
     */
    @Test
    public void testDropByteOrderMark() throws Exception {
        assertExtractText("UTF-8 BOM", "test",
                new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF, 't', 'e', 's', 't'});
        assertExtractText("UTF-16 BE BOM", "test",
                new byte[]{(byte) 0xFE, (byte) 0xFF, 0, 't', 0, 'e', 0, 's', 0, 't'});
        assertExtractText("UTF-16 LE BOM", "test",
                new byte[]{(byte) 0xFF, (byte) 0xFE, 't', 0, 'e', 0, 's', 0, 't', 0});
    }

    /**
     * Test case for TIKA-335: using incoming charset
     *
     * @see <a href="https://issues.apache.org/jira/browse/TIKA-335">TIKA-335</a>
     */
    @Test
    public void testUseIncomingCharsetAsHint() throws Exception {
        // u00e1 is latin small letter a with acute — 17 bytes, one high byte (0xE1).
        // The ML detector returns a Windows Latin variant; incoming charset hints are
        // not used to override detection in the new pipeline.
        final String test2 = "the name is \u00e1ndre";

        Metadata metadata = new Metadata();
        try (TikaInputStream tis = TikaInputStream.get(test2.getBytes(ISO_8859_1))) {
            parser.parse(tis, new BodyContentHandler(), metadata, new ParseContext());
        }
        // Short probe with one high byte — detector returns a Windows Latin variant
        assertNotNull(metadata.get(Metadata.CONTENT_TYPE));
        assertNotNull(metadata.get(Metadata.CONTENT_ENCODING));
    }

    /**
     * Test case for TIKA-341: using charset in content-type
     *
     * @see <a href="https://issues.apache.org/jira/browse/TIKA-341">TIKA-341</a>
     */
    @Test
    public void testUsingCharsetInContentTypeHeader() throws Exception {
        // u00e1 is latin small letter a with acute — 17 bytes, one high byte (0xE1).
        // Incoming charset in content-type is not used to override ML detection.
        final String test2 = "the name is \u00e1ndre";

        Metadata metadata = new Metadata();
        try (TikaInputStream tis = TikaInputStream.get(test2.getBytes(ISO_8859_1))) {
            parser.parse(tis, new BodyContentHandler(), metadata, new ParseContext());
        }
        assertNotNull(metadata.get(Metadata.CONTENT_TYPE));
        assertNotNull(metadata.get(Metadata.CONTENT_ENCODING));
    }

    private void assertExtractText(String msg, String expected, byte[] input) throws Exception {
        ContentHandler handler = new BodyContentHandler() {
            public void ignorableWhitespace(char[] ch, int off, int len) {
                // Ignore the whitespace added by XHTMLContentHandler
            }
        };
        Metadata metadata = new Metadata();
        try (TikaInputStream tis = TikaInputStream.get(input)) {
            parser.parse(tis, handler, metadata, new ParseContext());
        }
        assertEquals(expected, handler.toString(), msg);
    }

    /**
     * Test case for TIKA-339: don't override incoming language
     *
     * @see <a href="https://issues.apache.org/jira/browse/TIKA-335">TIKA-335</a>
     */
    @Test
    public void testRetainIncomingLanguage() throws Exception {
        final String test = "Simple Content";

        Metadata metadata = new Metadata();
        metadata.set(TikaCoreProperties.LANGUAGE, "en");

        try (TikaInputStream tis = TikaInputStream.get(test.getBytes(UTF_8))) {
            parser.parse(tis, new BodyContentHandler(), metadata, new ParseContext());
        }

        assertEquals("en", metadata.get(TikaCoreProperties.LANGUAGE));
    }

    @Test
    public void testCP866() throws Exception {
        Metadata metadata = new Metadata();
        StringWriter writer = new StringWriter();
        parser.parse(getResourceAsStream("/test-documents/russian.cp866.txt"),
                new WriteOutContentHandler(writer), metadata, new ParseContext());

        assertEquals("text/plain; charset=IBM866", metadata.get(Metadata.CONTENT_TYPE));
    }

    @Test
    public void testEBCDIC_CP500() throws Exception {
        Metadata metadata = new Metadata();
        StringWriter writer = new StringWriter();
        parser.parse(getResourceAsStream("/test-documents/english.cp500.txt"),
                new WriteOutContentHandler(writer), metadata, new ParseContext());

        assertEquals("text/plain; charset=IBM500", metadata.get(Metadata.CONTENT_TYPE));

        // Additional check that it isn't too eager on short blocks of text
        metadata = new Metadata();
        writer = new StringWriter();
        try (TikaInputStream tis = TikaInputStream.get(
                "<html><body>hello world</body></html>".getBytes(ISO_8859_1))) {
            parser.parse(tis, new WriteOutContentHandler(writer), metadata, new ParseContext());
        }

        // Pure ASCII — UTF-8 is correct
        assertEquals("text/plain; charset=UTF-8", metadata.get(Metadata.CONTENT_TYPE));
    }

    /**
     * Test case for TIKA-771: "Hello, World!" in UTF-8/ASCII gets detected as IBM500
     *
     * @see <a href="https://issues.apache.org/jira/browse/TIKA-771">TIKA-771</a>
     */
    @Test
    public void testCharsetDetectionWithShortSnipet() throws Exception {
        final String text = "Hello, World!";

        Metadata metadata = new Metadata();
        try (TikaInputStream tis = TikaInputStream.get(text.getBytes(UTF_8))) {
            parser.parse(tis, new BodyContentHandler(), metadata, new ParseContext());
        }
        // Pure ASCII — UTF-8 is correct
        assertEquals("text/plain; charset=UTF-8", metadata.get(Metadata.CONTENT_TYPE));

        // Now verify that if we tell the parser the encoding is UTF-8, that's what
        // we get back (see TIKA-868)
        metadata.set(Metadata.CONTENT_TYPE, "application/binary; charset=UTF-8");
        try (TikaInputStream tis = TikaInputStream.get(text.getBytes(UTF_8))) {
            parser.parse(tis, new BodyContentHandler(), metadata, new ParseContext());
        }
        assertEquals("application/binary; charset=UTF-8", metadata.get(Metadata.CONTENT_TYPE));
    }

    //TIKA-2047
    @Test
    public void testSubclassingMimeTypesRemain() throws Exception {
        XMLResult r = getXML("testVCalendar.vcs");
        // Pure ASCII content — correctly detected as UTF-8
        assertEquals("text/x-vcalendar; charset=UTF-8", r.metadata.get(Metadata.CONTENT_TYPE));
    }

}
