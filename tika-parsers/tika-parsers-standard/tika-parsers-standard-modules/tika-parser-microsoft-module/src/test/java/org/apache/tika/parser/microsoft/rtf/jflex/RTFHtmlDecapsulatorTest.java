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
package org.apache.tika.parser.microsoft.rtf.jflex;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import org.apache.tika.exception.TikaException;
import org.apache.tika.parser.ParseContext;

/**
 * Tests for {@link RTFHtmlDecapsulator}, mirroring the original
 * RTFEncapsulatedHTMLExtractorTest to verify parity.
 */
public class RTFHtmlDecapsulatorTest {

    private static String extract(byte[] rtfBytes)
            throws IOException, SAXException, TikaException {
        return new RTFHtmlDecapsulator(new DefaultHandler(), new ParseContext())
                .extract(rtfBytes);
    }

    @Test
    public void testNullAndEmpty() throws Exception {
        assertNull(extract(null));
        assertNull(extract(new byte[0]));
    }

    @Test
    public void testNonEncapsulatedRtf() throws Exception {
        String rtf = "{\\rtf1\\ansi\\deff0 Hello world}";
        assertNull(extract(rtf.getBytes(US_ASCII)));
    }

    @Test
    public void testSimpleEncapsulatedHtml() throws Exception {
        String rtf = "{\\rtf1\\ansi\\ansicpg1252\\fromhtml1 \\deff0\n" +
                "{\\*\\htmltag19 <html>}\n" +
                "{\\*\\htmltag34 <head>}\n" +
                "{\\*\\htmltag41 </head>}\n" +
                "{\\*\\htmltag50 <body>}\n" +
                "\\htmlrtf {\\htmlrtf0\n" +
                "{\\*\\htmltag64 <p>}\n" +
                "{\\*\\htmltag84 Hello world}\n" +
                "{\\*\\htmltag72 </p>}\n" +
                "\\htmlrtf }\\htmlrtf0\n" +
                "{\\*\\htmltag58 </body>}\n" +
                "{\\*\\htmltag27 </html>}\n" +
                "}";
        String html = extract(rtf.getBytes(US_ASCII));
        assertNotNull(html);
        assertTrue(html.contains("<html>"));
        assertTrue(html.contains("<p>"));
        assertTrue(html.contains("Hello world"));
        assertTrue(html.contains("</html>"));
    }

    @Test
    public void testImgCidExtraction() throws Exception {
        String rtf = "{\\rtf1\\ansi\\ansicpg1252\\fromhtml1 \\deff0\n" +
                "{\\*\\htmltag19 <html>}\n" +
                "{\\*\\htmltag50 <body>}\n" +
                "{\\*\\htmltag84 <img src=\"cid:image001.png@01DC5A2C.E674FE00\">}\n" +
                "{\\*\\htmltag58 </body>}\n" +
                "{\\*\\htmltag27 </html>}\n" +
                "}";
        String html = extract(rtf.getBytes(US_ASCII));
        assertNotNull(html);
        assertTrue(html.contains("cid:image001.png@01DC5A2C.E674FE00"),
                "CID reference should be preserved in extracted HTML");
    }

    @Test
    public void testParAndTabDecoding() throws Exception {
        String rtf = "{\\rtf1\\ansi\\ansicpg1252\\fromhtml1 \\deff0\n" +
                "{\\*\\htmltag241 <style>}\n" +
                "{\\*\\htmltag241 body \\{\\par \\tab color: red;\\par \\}}\n" +
                "{\\*\\htmltag249 </style>}\n" +
                "}";
        String html = extract(rtf.getBytes(US_ASCII));
        assertNotNull(html);
        assertTrue(html.contains("<style>"));
        assertTrue(html.contains("body {"));
        assertTrue(html.contains("\tcolor: red;"));
        assertTrue(html.contains("</style>"));
    }

    @Test
    public void testHexEscapeDecoding() throws Exception {
        // \'e9 = 0xE9 = 'e' in windows-1252
        String rtf = "{\\rtf1\\ansi\\ansicpg1252\\fromhtml1 \\deff0\n" +
                "{\\*\\htmltag84 caf\\'e9}\n" +
                "}";
        String html = extract(rtf.getBytes(US_ASCII));
        assertNotNull(html);
        assertEquals("caf\u00e9", html);
    }

    @Test
    public void testMultiByteHexEscape() throws Exception {
        // \'fc = 'u' and \'df = 'ss' in windows-1252
        String rtf = "{\\rtf1\\ansi\\ansicpg1252\\fromhtml1 \\deff0\n" +
                "{\\*\\htmltag84 gr\\'fc\\'dfe}\n" +
                "}";
        String html = extract(rtf.getBytes(US_ASCII));
        assertNotNull(html);
        assertEquals("gr\u00fc\u00dfe", html);
    }

    @Test
    public void testCodePage1254Turkish() throws Exception {
        // \'fd in windows-1254 = 0xFD, decoded by Java's windows-1254 charset
        String rtf = "{\\rtf1\\ansi\\ansicpg1254\\fromhtml1 \\deff0\n" +
                "{\\*\\htmltag84 Say\\'fdn}\n" +
                "}";
        String html = extract(rtf.getBytes(US_ASCII));
        assertNotNull(html);
        // Verify the byte 0xFD is decoded through windows-1254
        byte[] expected = new byte[] { 'S', 'a', 'y', (byte) 0xFD, 'n' };
        assertEquals(new String(expected, java.nio.charset.Charset.forName("windows-1254")), html);
    }

    @Test
    public void testHtmlrtfSkipping() throws Exception {
        String rtf = "{\\rtf1\\ansi\\ansicpg1252\\fromhtml1 \\deff0\n" +
                "{\\*\\htmltag84 Hello}\n" +
                "\\htmlrtf {\\b bold rtf only}\\htmlrtf0\n" +
                "{\\*\\htmltag84  World}\n" +
                "}";
        String html = extract(rtf.getBytes(US_ASCII));
        assertNotNull(html);
        assertEquals("Hello World", html);
    }

    @Test
    public void testEscapedBracesAndBackslash() throws Exception {
        String rtf = "{\\rtf1\\ansi\\ansicpg1252\\fromhtml1 \\deff0\n" +
                "{\\*\\htmltag241 a \\{ b \\} c \\\\d}\n" +
                "}";
        String html = extract(rtf.getBytes(US_ASCII));
        assertNotNull(html);
        assertEquals("a { b } c \\d", html);
    }

    @Test
    public void testEmptyHtmltag() throws Exception {
        String rtf = "{\\rtf1\\ansi\\ansicpg1252\\fromhtml1 \\deff0\n" +
                "{\\*\\htmltag72}\n" +
                "{\\*\\htmltag84 text}\n" +
                "}";
        String html = extract(rtf.getBytes(US_ASCII));
        assertNotNull(html);
        assertEquals("text", html);
    }

    @Test
    public void testInterTagTextContent() throws Exception {
        String rtf = "{\\rtf1\\ansi\\ansicpg1252\\fromhtml1 \\deff0\n" +
                "{\\*\\htmltag19 <html>}\n" +
                "{\\*\\htmltag50 <body>}\n" +
                "{\\*\\htmltag64 <p>}\n" +
                "\\htmlrtf {\\htmlrtf0\n" +
                "Hello from the message body\n" +
                "\\htmlrtf\\par}\\htmlrtf0\n" +
                "{\\*\\htmltag72 </p>}\n" +
                "{\\*\\htmltag64 <p>}\n" +
                "\\htmlrtf {\\htmlrtf0\n" +
                "Second paragraph\n" +
                "\\htmlrtf\\par}\\htmlrtf0\n" +
                "{\\*\\htmltag72 </p>}\n" +
                "{\\*\\htmltag58 </body>}\n" +
                "{\\*\\htmltag27 </html>}\n" +
                "}";
        String html = extract(rtf.getBytes(US_ASCII));
        assertNotNull(html);
        assertTrue(html.contains("<p>"), "should contain HTML tags");
        assertTrue(html.contains("Hello from the message body"),
                "should contain inter-tag text content");
        assertTrue(html.contains("Second paragraph"),
                "should contain second paragraph text");
        assertTrue(html.contains("</html>"), "should contain closing tag");
    }

    @Test
    public void testInterTagHexEscapes() throws Exception {
        String rtf = "{\\rtf1\\ansi\\ansicpg1252\\fromhtml1 \\deff0\n" +
                "{\\*\\htmltag64 <p>}\n" +
                "\\htmlrtf {\\htmlrtf0\n" +
                "caf\\'e9\n" +
                "\\htmlrtf }\\htmlrtf0\n" +
                "{\\*\\htmltag72 </p>}\n" +
                "}";
        String html = extract(rtf.getBytes(US_ASCII));
        assertNotNull(html);
        assertTrue(html.contains("caf\u00e9"), "hex escapes in inter-tag text should be decoded");
    }

    @Test
    public void testLineControlWord() throws Exception {
        String rtf = "{\\rtf1\\ansi\\ansicpg1252\\fromhtml1 \\deff0\n" +
                "{\\*\\htmltag84 line1\\line line2}\n" +
                "}";
        String html = extract(rtf.getBytes(US_ASCII));
        assertNotNull(html);
        assertEquals("line1<br>line2", html);
    }

    @Test
    public void testFontAwareCodePageDecoding() throws Exception {
        // f0 = ANSI (fcharset 0 = windows-1252), f1 = Greek (fcharset 161 = cp1253)
        // \'e1 in windows-1252 = U+00E1 (a with acute)
        // \'e1 in cp1253 = U+03B1 (GREEK SMALL LETTER ALPHA)
        String rtf = "{\\rtf1\\ansi\\ansicpg1252\\fromhtml1 \\deff0\n" +
                "{\\fonttbl{\\f0\\fcharset0 Times;}{\\f1\\fcharset161 Greek;}}\n" +
                "{\\*\\htmltag84 \\f0 caf\\'e9}\n" +
                "{\\*\\htmltag84 \\f1 \\'e1}\n" +
                "}";
        String html = extract(rtf.getBytes(US_ASCII));
        assertNotNull(html);
        // f0: \'e9 in windows-1252 = e with acute
        assertTrue(html.contains("caf\u00e9"), "f0 should decode as windows-1252");
        // f1: \'e1 in cp1253 = Greek alpha
        assertTrue(html.contains("\u03b1"), "f1 should decode as cp1253 (Greek)");
    }

    @Test
    public void testUnicodeEscapeWithAnsiShadow() throws Exception {
        // \u8212 is em dash (U+2014). The \'97 is the ANSI shadow and should be skipped.
        String rtf = "{\\rtf1\\ansi\\ansicpg1252\\fromhtml1 \\deff0\n" +
                "{\\fonttbl{\\f0\\fcharset0 Times;}}\n" +
                "{\\*\\htmltag84 A\\u8212\\'97B}\n" +
                "}";
        String html = extract(rtf.getBytes(US_ASCII));
        assertNotNull(html);
        assertEquals("A\u2014B", html);
    }
}
