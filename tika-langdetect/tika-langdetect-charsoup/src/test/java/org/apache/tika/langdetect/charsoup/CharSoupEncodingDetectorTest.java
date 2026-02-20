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
package org.apache.tika.langdetect.charsoup;

import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.Charset;

import org.junit.jupiter.api.Test;

import org.apache.tika.detect.EncodingDetectorContext;
import org.apache.tika.detect.MetaEncodingDetector;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;

public class CharSoupEncodingDetectorTest {

    @Test
    public void testIsMetaEncodingDetector() {
        assertTrue(new CharSoupEncodingDetector() instanceof MetaEncodingDetector);
    }

    @Test
    public void testUnanimous() throws Exception {
        CharSoupEncodingDetector detector = new CharSoupEncodingDetector();
        EncodingDetectorContext context = new EncodingDetectorContext();
        context.addResult(UTF_8, "DetectorA");
        context.addResult(UTF_8, "DetectorB");

        ParseContext parseContext = new ParseContext();
        parseContext.set(EncodingDetectorContext.class, context);

        byte[] data = "Hello, world!".getBytes(UTF_8);
        try (TikaInputStream tis = TikaInputStream.get(
                new ByteArrayInputStream(data))) {
            Charset result = detector.detect(tis, new Metadata(), parseContext);
            assertEquals(UTF_8, result);
            assertEquals("unanimous", context.getArbitrationInfo());
        }
    }

    @Test
    public void testNoContext() throws Exception {
        CharSoupEncodingDetector detector = new CharSoupEncodingDetector();
        ParseContext parseContext = new ParseContext();

        byte[] data = "Test".getBytes(UTF_8);
        try (TikaInputStream tis = TikaInputStream.get(
                new ByteArrayInputStream(data))) {
            Charset result = detector.detect(tis, new Metadata(), parseContext);
            assertNull(result);
        }
    }

    @Test
    public void testEmptyResults() throws Exception {
        CharSoupEncodingDetector detector = new CharSoupEncodingDetector();
        EncodingDetectorContext context = new EncodingDetectorContext();

        ParseContext parseContext = new ParseContext();
        parseContext.set(EncodingDetectorContext.class, context);

        byte[] data = "Test".getBytes(UTF_8);
        try (TikaInputStream tis = TikaInputStream.get(
                new ByteArrayInputStream(data))) {
            Charset result = detector.detect(tis, new Metadata(), parseContext);
            assertNull(result);
        }
    }

    @Test
    public void testArabicEncodingArbitration() throws Exception {
        // Arabic text encoded in windows-1256.
        // When decoded as UTF-8 it produces replacement chars / garbage.
        // When decoded as windows-1256 it produces valid Arabic.
        // The language detector should pick windows-1256.
        Charset windows1256 = Charset.forName("windows-1256");

        String arabicText =
                "\u0641\u064a \u0642\u0631\u064a\u0629 \u0645\u0646 " +
                "\u0627\u0644\u0642\u0631\u0649 \u0643\u0627\u0646 " +
                "\u0647\u0646\u0627\u0643 \u0631\u062c\u0644 " +
                "\u062d\u0643\u064a\u0645 \u064a\u0639\u0631\u0641 " +
                "\u0643\u0644 \u0634\u064a\u0621 \u0639\u0646 " +
                "\u0627\u0644\u062d\u064a\u0627\u0629 \u0648\u0643\u0627\u0646 " +
                "\u064a\u0639\u0644\u0645 \u0627\u0644\u0646\u0627\u0633 " +
                "\u0643\u064a\u0641 \u064a\u0639\u064a\u0634\u0648\u0646 " +
                "\u0628\u0633\u0644\u0627\u0645 \u0648\u0627\u0646\u0633\u062c\u0627\u0645. " +
                "\u0627\u0644\u0644\u063a\u0629 \u0627\u0644\u0639\u0631\u0628\u064a\u0629 " +
                "\u0647\u064a \u0648\u0627\u062d\u062f\u0629 \u0645\u0646 " +
                "\u0623\u0643\u062b\u0631 \u0627\u0644\u0644\u063a\u0627\u062a " +
                "\u0627\u0646\u062a\u0634\u0627\u0631\u0627 \u0641\u064a " +
                "\u0627\u0644\u0639\u0627\u0644\u0645 \u0648\u064a\u062a\u062d\u062b\u0647\u0627 " +
                "\u0623\u0643\u062b\u0631 \u0645\u0646 \u062b\u0644\u0627\u062b\u0645\u0627\u0626\u0629 " +
                "\u0645\u0644\u064a\u0648\u0646 \u0625\u0646\u0633\u0627\u0646.";
        byte[] arabicBytes = arabicText.getBytes(windows1256);

        EncodingDetectorContext context = new EncodingDetectorContext();
        context.addResult(UTF_8, "HtmlEncodingDetector");
        context.addResult(windows1256, "Icu4jEncodingDetector");

        ParseContext parseContext = new ParseContext();
        parseContext.set(EncodingDetectorContext.class, context);

        CharSoupEncodingDetector detector = new CharSoupEncodingDetector();
        try (TikaInputStream tis = TikaInputStream.get(
                new ByteArrayInputStream(arabicBytes))) {
            Charset result = detector.detect(tis, new Metadata(), parseContext);
            assertEquals(windows1256, result);
            assertEquals("scored", context.getArbitrationInfo());
        }
    }

    @Test
    public void testStreamResetAfterDetection() throws Exception {
        EncodingDetectorContext context = new EncodingDetectorContext();
        context.addResult(UTF_8, "DetectorA");
        context.addResult(ISO_8859_1, "DetectorB");

        ParseContext parseContext = new ParseContext();
        parseContext.set(EncodingDetectorContext.class, context);

        byte[] data = "Hello, world! This is a test of encoding detection.".getBytes(UTF_8);
        CharSoupEncodingDetector detector = new CharSoupEncodingDetector();
        try (TikaInputStream tis = TikaInputStream.get(
                new ByteArrayInputStream(data))) {
            detector.detect(tis, new Metadata(), parseContext);

            // Verify stream is back at the start
            byte[] readBack = new byte[data.length];
            int bytesRead = tis.read(readBack);
            assertEquals(data.length, bytesRead);
            assertEquals("Hello, world! This is a test of encoding detection.",
                    new String(readBack, UTF_8));
        }
    }

    @Test
    public void testStripTags() {
        assertEquals("Hello world",
                CharSoupEncodingDetector.stripTags(
                        "<html><body>Hello world</body></html>"));
        assertEquals("no tags here",
                CharSoupEncodingDetector.stripTags("no tags here"));
        assertEquals("",
                CharSoupEncodingDetector.stripTags("<empty/>"));
    }

    @Test
    public void testDecode() {
        byte[] utf8Bytes = "caf\u00e9".getBytes(UTF_8);
        assertEquals("caf\u00e9",
                CharSoupEncodingDetector.decode(utf8Bytes, UTF_8));
    }

    @Test
    public void testReadLimitGetterSetter() {
        CharSoupEncodingDetector detector = new CharSoupEncodingDetector();
        assertEquals(16384, detector.getReadLimit());
        detector.setReadLimit(4096);
        assertEquals(4096, detector.getReadLimit());
    }

    @Test
    public void testJunkRatio() {
        // Clean text — no junk
        assertEquals(0f,
                CharSoupLanguageDetector.junkRatio("Hello, world!"), 0.001f);

        // U+FFFD replacement chars
        assertEquals(0.5f,
                CharSoupLanguageDetector.junkRatio("ab\uFFFD\uFFFD"), 0.001f);

        // C1 control chars (U+0080-U+009F are isISOControl)
        assertEquals(0.25f,
                CharSoupLanguageDetector.junkRatio("abc\u0080"), 0.001f);

        // Mixed: \r\n are control chars too
        assertEquals(2f / 13f,
                CharSoupLanguageDetector.junkRatio("hello world\r\n"), 0.001f);

        // Empty/null
        assertEquals(0f, CharSoupLanguageDetector.junkRatio(""), 0.001f);
        assertEquals(0f, CharSoupLanguageDetector.junkRatio(null), 0.001f);
    }
}
