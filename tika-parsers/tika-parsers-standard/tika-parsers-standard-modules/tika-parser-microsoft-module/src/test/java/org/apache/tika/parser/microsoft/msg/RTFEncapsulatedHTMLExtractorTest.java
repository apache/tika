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
package org.apache.tika.parser.microsoft.msg;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class RTFEncapsulatedHTMLExtractorTest {

    @Test
    public void testNullAndEmpty() {
        assertNull(RTFEncapsulatedHTMLExtractor.extract(null));
        assertNull(RTFEncapsulatedHTMLExtractor.extract(new byte[0]));
    }

    @Test
    public void testNonEncapsulatedRtf() {
        String rtf = "{\\rtf1\\ansi\\deff0 Hello world}";
        assertNull(RTFEncapsulatedHTMLExtractor.extract(rtf.getBytes(US_ASCII)));
    }

    @Test
    public void testSimpleEncapsulatedHtml() {
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
        String html = RTFEncapsulatedHTMLExtractor.extract(rtf.getBytes(US_ASCII));
        assertNotNull(html);
        assertTrue(html.contains("<html>"));
        assertTrue(html.contains("<p>"));
        assertTrue(html.contains("Hello world"));
        assertTrue(html.contains("</html>"));
    }

    @Test
    public void testImgCidExtraction() {
        String rtf = "{\\rtf1\\ansi\\ansicpg1252\\fromhtml1 \\deff0\n" +
                "{\\*\\htmltag19 <html>}\n" +
                "{\\*\\htmltag50 <body>}\n" +
                "{\\*\\htmltag84 <img src=\"cid:image001.png@01DC5A2C.E674FE00\">}\n" +
                "{\\*\\htmltag58 </body>}\n" +
                "{\\*\\htmltag27 </html>}\n" +
                "}";
        String html = RTFEncapsulatedHTMLExtractor.extract(rtf.getBytes(US_ASCII));
        assertNotNull(html);
        assertTrue(html.contains("cid:image001.png@01DC5A2C.E674FE00"),
                "CID reference should be preserved in extracted HTML");
    }

    @Test
    public void testParAndTabDecoding() {
        String rtf = "{\\rtf1\\ansi\\ansicpg1252\\fromhtml1 \\deff0\n" +
                "{\\*\\htmltag241 <style>}\n" +
                "{\\*\\htmltag241 body \\{\\par \\tab color: red;\\par \\}}\n" +
                "{\\*\\htmltag249 </style>}\n" +
                "}";
        String html = RTFEncapsulatedHTMLExtractor.extract(rtf.getBytes(US_ASCII));
        assertNotNull(html);
        assertTrue(html.contains("<style>"));
        assertTrue(html.contains("body {"));
        assertTrue(html.contains("\tcolor: red;"));
        assertTrue(html.contains("</style>"));
    }

    @Test
    public void testHexEscapeDecoding() {
        // \'e9 = 0xE9 = 'é' in windows-1252
        String rtf = "{\\rtf1\\ansi\\ansicpg1252\\fromhtml1 \\deff0\n" +
                "{\\*\\htmltag84 caf\\'e9}\n" +
                "}";
        String html = RTFEncapsulatedHTMLExtractor.extract(rtf.getBytes(US_ASCII));
        assertNotNull(html);
        assertEquals("café", html);
    }

    @Test
    public void testMultiByteHexEscape() {
        // UTF-8 encoded 'ü' = 0xC3 0xBC in code page 65001 (UTF-8)
        // But more commonly: \'fc in windows-1252 = 'ü'
        String rtf = "{\\rtf1\\ansi\\ansicpg1252\\fromhtml1 \\deff0\n" +
                "{\\*\\htmltag84 gr\\'fc\\'dfe}\n" +
                "}";
        String html = RTFEncapsulatedHTMLExtractor.extract(rtf.getBytes(US_ASCII));
        assertNotNull(html);
        assertEquals("grüße", html);
    }

    @Test
    public void testCodePage1254Turkish() {
        // \'fe in windows-1254 = 'þ' (U+00FE, LATIN SMALL LETTER THORN)
        // \'fd in windows-1254 = 'ý' (U+00FD)
        String rtf = "{\\rtf1\\ansi\\ansicpg1254\\fromhtml1 \\deff0\n" +
                "{\\*\\htmltag84 Say\\'fdn}\n" +
                "}";
        String html = RTFEncapsulatedHTMLExtractor.extract(rtf.getBytes(US_ASCII));
        assertNotNull(html);
        assertEquals("Sayın", html);
    }

    @Test
    public void testHtmlrtfSkipping() {
        // Content between \htmlrtf and \htmlrtf0 should be skipped
        String rtf = "{\\rtf1\\ansi\\ansicpg1252\\fromhtml1 \\deff0\n" +
                "{\\*\\htmltag84 Hello}\n" +
                "\\htmlrtf {\\b bold rtf only}\\htmlrtf0\n" +
                "{\\*\\htmltag84  World}\n" +
                "}";
        String html = RTFEncapsulatedHTMLExtractor.extract(rtf.getBytes(US_ASCII));
        assertNotNull(html);
        assertEquals("Hello World", html);
    }

    @Test
    public void testEscapedBracesAndBackslash() {
        String rtf = "{\\rtf1\\ansi\\ansicpg1252\\fromhtml1 \\deff0\n" +
                "{\\*\\htmltag241 a \\{ b \\} c \\\\d}\n" +
                "}";
        String html = RTFEncapsulatedHTMLExtractor.extract(rtf.getBytes(US_ASCII));
        assertNotNull(html);
        assertEquals("a { b } c \\d", html);
    }

    @Test
    public void testEmptyHtmltag() {
        String rtf = "{\\rtf1\\ansi\\ansicpg1252\\fromhtml1 \\deff0\n" +
                "{\\*\\htmltag72}\n" +
                "{\\*\\htmltag84 text}\n" +
                "}";
        String html = RTFEncapsulatedHTMLExtractor.extract(rtf.getBytes(US_ASCII));
        assertNotNull(html);
        assertEquals("text", html);
    }

    @Test
    public void testInterTagTextContent() {
        // Realistic pattern: text content appears BETWEEN htmltag groups,
        // with \htmlrtf blocks that should be skipped
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
        String html = RTFEncapsulatedHTMLExtractor.extract(rtf.getBytes(US_ASCII));
        assertNotNull(html);
        assertTrue(html.contains("<p>"), "should contain HTML tags");
        assertTrue(html.contains("Hello from the message body"),
                "should contain inter-tag text content");
        assertTrue(html.contains("Second paragraph"),
                "should contain second paragraph text");
        assertTrue(html.contains("</html>"), "should contain closing tag");
    }

    @Test
    public void testInterTagHexEscapes() {
        // Text between htmltag groups can also have \'xx escapes
        String rtf = "{\\rtf1\\ansi\\ansicpg1252\\fromhtml1 \\deff0\n" +
                "{\\*\\htmltag64 <p>}\n" +
                "\\htmlrtf {\\htmlrtf0\n" +
                "caf\\'e9\n" +
                "\\htmlrtf }\\htmlrtf0\n" +
                "{\\*\\htmltag72 </p>}\n" +
                "}";
        String html = RTFEncapsulatedHTMLExtractor.extract(rtf.getBytes(US_ASCII));
        assertNotNull(html);
        assertTrue(html.contains("café"), "hex escapes in inter-tag text should be decoded");
    }

    @Test
    public void testLineControlWord() {
        String rtf = "{\\rtf1\\ansi\\ansicpg1252\\fromhtml1 \\deff0\n" +
                "{\\*\\htmltag84 line1\\line line2}\n" +
                "}";
        String html = RTFEncapsulatedHTMLExtractor.extract(rtf.getBytes(US_ASCII));
        assertNotNull(html);
        assertEquals("line1<br>line2", html);
    }
}
