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
package org.apache.tika.detect;

import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.apache.tika.TikaTest;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.textquality.TextQualityResult;
import org.apache.tika.textquality.TextQualityScorer;

public class TextQualityEncodingDetectorTest extends TikaTest {

    /**
     * Mock detector that always returns a fixed charset.
     */
    private static class FixedEncodingDetector implements EncodingDetector {
        private static final long serialVersionUID = 1L;
        private final Charset charset;

        FixedEncodingDetector(Charset charset) {
            this.charset = charset;
        }

        @Override
        public Charset detect(TikaInputStream tis, Metadata metadata,
                              ParseContext parseContext) {
            return charset;
        }
    }

    /**
     * Mock detector that always returns null.
     */
    private static class NullEncodingDetector implements EncodingDetector {
        private static final long serialVersionUID = 1L;

        @Override
        public Charset detect(TikaInputStream tis, Metadata metadata,
                              ParseContext parseContext) {
            return null;
        }
    }

    @Test
    public void testUnanimousAgreement() throws Exception {
        List<EncodingDetector> detectors = Arrays.asList(
                new FixedEncodingDetector(UTF_8),
                new FixedEncodingDetector(UTF_8));
        TextQualityEncodingDetector detector =
                new TextQualityEncodingDetector(detectors);

        byte[] data = "Hello, world!".getBytes(UTF_8);
        try (TikaInputStream tis = TikaInputStream.get(
                new ByteArrayInputStream(data))) {
            Metadata metadata = new Metadata();
            ParseContext parseContext = new ParseContext();
            Charset result = detector.detect(tis, metadata, parseContext);
            assertEquals(UTF_8, result);
            assertEquals("UTF-8",
                    metadata.get(TikaCoreProperties.DETECTED_ENCODING));
        }
    }

    @Test
    public void testSingleDetector() throws Exception {
        List<EncodingDetector> detectors =
                Collections.singletonList(new FixedEncodingDetector(UTF_8));
        TextQualityEncodingDetector detector =
                new TextQualityEncodingDetector(detectors);

        byte[] data = "Test".getBytes(UTF_8);
        try (TikaInputStream tis = TikaInputStream.get(
                new ByteArrayInputStream(data))) {
            Metadata metadata = new Metadata();
            ParseContext parseContext = new ParseContext();
            Charset result = detector.detect(tis, metadata, parseContext);
            assertEquals(UTF_8, result);
        }
    }

    @Test
    public void testNoDetectorsMatch() throws Exception {
        List<EncodingDetector> detectors =
                Collections.singletonList(new NullEncodingDetector());
        TextQualityEncodingDetector detector =
                new TextQualityEncodingDetector(detectors);

        byte[] data = "Test".getBytes(UTF_8);
        try (TikaInputStream tis = TikaInputStream.get(
                new ByteArrayInputStream(data))) {
            Metadata metadata = new Metadata();
            ParseContext parseContext = new ParseContext();
            Charset result = detector.detect(tis, metadata, parseContext);
            assertNull(result);
        }
    }

    @Test
    public void testScorerIsAvailable() {
        // Verify the real scorer is on the classpath, not the no-op
        TextQualityScorer scorer = TextQualityScorer.getDefault();
        TextQualityResult result = scorer.score("This is a test of English text quality.");
        // The real scorer returns non-zero scores; the no-op returns 0.0
        assertNotEquals(0.0, result.getScore(),
                "Expected real TextQualityScorer, got no-op. " +
                "Is tika-eval-lite on the test classpath?");
    }

    @Test
    public void testDisagreementArbitration() throws Exception {
        // With tika-eval-lite on test classpath, the real scorer should
        // pick windows-1256 over UTF-8 for Arabic bytes
        Charset windows1256 = Charset.forName("windows-1256");

        // Several paragraphs of Arabic text to ensure enough bigrams for scoring
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

        List<EncodingDetector> detectors = Arrays.asList(
                new FixedEncodingDetector(UTF_8),
                new FixedEncodingDetector(windows1256));
        TextQualityEncodingDetector detector =
                new TextQualityEncodingDetector(detectors);

        try (TikaInputStream tis = TikaInputStream.get(
                new ByteArrayInputStream(arabicBytes))) {
            Metadata metadata = new Metadata();
            ParseContext parseContext = new ParseContext();
            Charset result = detector.detect(tis, metadata, parseContext);
            // The real scorer should prefer windows-1256 since the bytes
            // decode to valid Arabic with it but to replacement chars with UTF-8
            assertEquals(windows1256, result);
        }
    }

    @Test
    public void testStreamResetAfterDetection() throws Exception {
        List<EncodingDetector> detectors = Arrays.asList(
                new FixedEncodingDetector(UTF_8),
                new FixedEncodingDetector(ISO_8859_1));
        TextQualityEncodingDetector detector =
                new TextQualityEncodingDetector(detectors);

        byte[] data = "Hello, world!".getBytes(UTF_8);
        try (TikaInputStream tis = TikaInputStream.get(
                new ByteArrayInputStream(data))) {
            Metadata metadata = new Metadata();
            ParseContext parseContext = new ParseContext();
            detector.detect(tis, metadata, parseContext);

            // Verify stream is back at the start
            byte[] readBack = new byte[data.length];
            int bytesRead = tis.read(readBack);
            assertEquals(data.length, bytesRead);
            assertEquals("Hello, world!", new String(readBack, UTF_8));
        }
    }

    @Test
    public void testContextRemovedAfterDetection() throws Exception {
        List<EncodingDetector> detectors =
                Collections.singletonList(new FixedEncodingDetector(UTF_8));
        TextQualityEncodingDetector detector =
                new TextQualityEncodingDetector(detectors);

        byte[] data = "Test".getBytes(UTF_8);
        try (TikaInputStream tis = TikaInputStream.get(
                new ByteArrayInputStream(data))) {
            ParseContext parseContext = new ParseContext();
            detector.detect(tis, new Metadata(), parseContext);
            assertNull(parseContext.get(EncodingDetectorContext.class));
        }
    }

    @Test
    public void testNullStream() throws Exception {
        List<EncodingDetector> detectors = Arrays.asList(
                new FixedEncodingDetector(UTF_8),
                new FixedEncodingDetector(ISO_8859_1));
        TextQualityEncodingDetector detector =
                new TextQualityEncodingDetector(detectors);

        Metadata metadata = new Metadata();
        ParseContext parseContext = new ParseContext();
        Charset result = detector.detect(null, metadata, parseContext);
        assertEquals(UTF_8, result);
    }

    @Test
    public void testStripTags() {
        assertEquals("Hello world",
                TextQualityEncodingDetector.stripTags(
                        "<html><body>Hello world</body></html>"));
        assertEquals("no tags here",
                TextQualityEncodingDetector.stripTags("no tags here"));
        assertEquals("  ",
                TextQualityEncodingDetector.stripTags(
                        "<p> </p> <br/>"));
        assertEquals("",
                TextQualityEncodingDetector.stripTags("<empty/>"));
    }

    @Test
    public void testDecode() {
        byte[] utf8Bytes = "caf\u00e9".getBytes(UTF_8);
        String decoded = TextQualityEncodingDetector.decode(utf8Bytes, UTF_8);
        assertEquals("caf\u00e9", decoded);

        byte[] asciiBytes = "hello".getBytes(US_ASCII);
        assertEquals("hello",
                TextQualityEncodingDetector.decode(asciiBytes, UTF_8));
        assertEquals("hello",
                TextQualityEncodingDetector.decode(asciiBytes, ISO_8859_1));
    }

    @Test
    public void testDetectorNameSetInMetadata() throws Exception {
        List<EncodingDetector> detectors =
                Collections.singletonList(new FixedEncodingDetector(UTF_8));
        TextQualityEncodingDetector detector =
                new TextQualityEncodingDetector(detectors);

        byte[] data = "Test".getBytes(UTF_8);
        try (TikaInputStream tis = TikaInputStream.get(
                new ByteArrayInputStream(data))) {
            Metadata metadata = new Metadata();
            ParseContext parseContext = new ParseContext();
            detector.detect(tis, metadata, parseContext);
            assertNotNull(
                    metadata.get(TikaCoreProperties.ENCODING_DETECTOR));
            assertEquals("FixedEncodingDetector",
                    metadata.get(TikaCoreProperties.ENCODING_DETECTOR));
        }
    }

    @Test
    public void testEmptyDetectorList() throws Exception {
        TextQualityEncodingDetector detector =
                new TextQualityEncodingDetector(Collections.emptyList());

        byte[] data = "Test".getBytes(UTF_8);
        try (TikaInputStream tis = TikaInputStream.get(
                new ByteArrayInputStream(data))) {
            Metadata metadata = new Metadata();
            ParseContext parseContext = new ParseContext();
            Charset result = detector.detect(tis, metadata, parseContext);
            assertNull(result);
        }
    }

    @Test
    public void testMixOfNullAndNonNullDetectors() throws Exception {
        List<EncodingDetector> detectors = Arrays.asList(
                new NullEncodingDetector(),
                new FixedEncodingDetector(UTF_8),
                new NullEncodingDetector());
        TextQualityEncodingDetector detector =
                new TextQualityEncodingDetector(detectors);

        byte[] data = "Test".getBytes(UTF_8);
        try (TikaInputStream tis = TikaInputStream.get(
                new ByteArrayInputStream(data))) {
            Metadata metadata = new Metadata();
            ParseContext parseContext = new ParseContext();
            Charset result = detector.detect(tis, metadata, parseContext);
            assertEquals(UTF_8, result);
        }
    }

    @Test
    public void testReadLimitGetterSetter() {
        TextQualityEncodingDetector detector =
                new TextQualityEncodingDetector(Collections.emptyList());
        assertEquals(16384, detector.getReadLimit());
        detector.setReadLimit(4096);
        assertEquals(4096, detector.getReadLimit());
    }

    @Test
    public void testGetDetectors() {
        List<EncodingDetector> input = Arrays.asList(
                new FixedEncodingDetector(UTF_8),
                new FixedEncodingDetector(ISO_8859_1));
        TextQualityEncodingDetector detector =
                new TextQualityEncodingDetector(input);
        List<EncodingDetector> returned = detector.getDetectors();
        assertEquals(2, returned.size());
    }

    @Test
    public void testIsInstanceOfEncodingDetector() {
        TextQualityEncodingDetector detector =
                new TextQualityEncodingDetector(Collections.emptyList());
        assertTrue(detector instanceof EncodingDetector);
    }

    @Test
    public void testArabicMisleadingCharsetHtml() throws Exception {
        // This HTML file is encoded in windows-1256 but declares charset=UTF-8
        // The text quality arbitration should override the misleading HTML meta
        // and detect that the actual content is Arabic (windows-1256)
        Metadata metadata = new Metadata();
        XMLResult result = getXML("testArabicMisleadingCharset.html", metadata);
        // Verify encoding was detected as windows-1256, not the misleading UTF-8
        assertEquals("windows-1256",
                metadata.get(TikaCoreProperties.DETECTED_ENCODING));
        // Verify extracted text contains readable Arabic, not mojibake
        // \u0627\u0644\u0639\u0631\u0628\u064a\u0629 = "العربية" (Arabic)
        assertContains("\u0627\u0644\u0639\u0631\u0628\u064a\u0629", result.xml);
    }
}
