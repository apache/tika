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
package org.apache.tika.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import org.apache.tika.TikaLoaderHelper;
import org.apache.tika.TikaTest;
import org.apache.tika.config.loader.TikaLoader;
import org.apache.tika.detect.BOMDetector;
import org.apache.tika.detect.CompositeEncodingDetector;
import org.apache.tika.detect.EncodingDetector;
import org.apache.tika.detect.EncodingResult;
import org.apache.tika.detect.MetaEncodingDetector;
import org.apache.tika.detect.MetadataCharsetDetector;
import org.apache.tika.detect.OverrideEncodingDetector;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.langdetect.charsoup.CharSoupEncodingDetector;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.ml.chardetect.NaiveBayesPipelineEncodingDetector;
import org.apache.tika.parser.AbstractEncodingDetectorParser;
import org.apache.tika.parser.CompositeParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.ParserDecorator;
import org.apache.tika.parser.html.HtmlEncodingDetector;
import org.apache.tika.parser.html.charsetdetector.StandardHtmlEncodingDetector;
import org.apache.tika.parser.txt.TXTParser;

public class TikaEncodingDetectorTest extends TikaTest {

    @Test
    public void testDefault() throws TikaConfigException {
        EncodingDetector detector = TikaLoader.loadDefault().loadEncodingDetectors();
        assertTrue(detector instanceof CompositeEncodingDetector);
        List<EncodingDetector> detectors = ((CompositeEncodingDetector) detector).getDetectors();
        // 4 base detectors (BOM, Metadata, ML, StandardHtml) + CharSoupEncodingDetector (MetaEncodingDetector)
        assertEquals(5, detectors.size());
        // meta detector is always last (partitioned by CompositeEncodingDetector)
        assertTrue(detectors.get(4) instanceof MetaEncodingDetector);
        // base detectors — sorted by full class name; check by type
        Set<Class<?>> baseClasses = detectors.subList(0, 4).stream()
                .map(Object::getClass).collect(Collectors.toSet());
        assertTrue(baseClasses.contains(BOMDetector.class));
        assertTrue(baseClasses.contains(MetadataCharsetDetector.class));
        assertTrue(baseClasses.contains(NaiveBayesPipelineEncodingDetector.class));
        assertTrue(baseClasses.contains(StandardHtmlEncodingDetector.class));
    }

    @Test
    public void testExcludeList() throws Exception {
        TikaLoader tikaLoader = TikaLoaderHelper.getLoader("TIKA-2273-exclude-encoding-detector-default.json");
        EncodingDetector detector = tikaLoader.loadEncodingDetectors();
        assertTrue(detector instanceof CompositeEncodingDetector);
        List<EncodingDetector> detectors = ((CompositeEncodingDetector) detector).getDetectors();
        // default-encoding-detector (inner composite) + override-encoding-detector
        // The inner composite now includes CharSoupEncodingDetector from SPI
        assertEquals(2, detectors.size());

        EncodingDetector detector1 = detectors.get(0);
        assertTrue(detector1 instanceof CompositeEncodingDetector);
        List<EncodingDetector> detectors1Children =
                ((CompositeEncodingDetector) detector1).getDetectors();
        // BOM + Metadata + ML base detectors + CharSoup meta (html excluded)
        assertEquals(4, detectors1Children.size());
        Set<Class<?>> innerClasses = detectors1Children.subList(0, 3).stream()
                .map(Object::getClass).collect(Collectors.toSet());
        assertTrue(innerClasses.contains(BOMDetector.class));
        assertTrue(innerClasses.contains(MetadataCharsetDetector.class));
        assertTrue(innerClasses.contains(NaiveBayesPipelineEncodingDetector.class));
        assertTrue(detectors1Children.get(3) instanceof MetaEncodingDetector);

        assertTrue(detectors.get(1) instanceof OverrideEncodingDetector);

    }

    @Test
    public void testParameterization() throws Exception {
        TikaLoader tikaLoader = TikaLoaderHelper.getLoader("TIKA-2273-parameterize-encoding-detector.json");
        EncodingDetector detector = tikaLoader.loadEncodingDetectors();
        assertTrue(detector instanceof CompositeEncodingDetector);
        List<EncodingDetector> detectors = ((CompositeEncodingDetector) detector).getDetectors();
        assertEquals(2, detectors.size());
        assertTrue(detectors.get(0) instanceof HtmlEncodingDetector);
        assertEquals(65000, ((HtmlEncodingDetector) detectors.get(0)).getDefaultConfig().getMarkLimit());
        assertTrue(detectors.get(1) instanceof OverrideEncodingDetector);
    }

    @Test
    public void testEncodingDetectorsAreLoaded() {
        EncodingDetector encodingDetector =
                ((AbstractEncodingDetectorParser) new TXTParser()).getEncodingDetector();

        assertTrue(encodingDetector instanceof CompositeEncodingDetector);
    }

    @Test
    public void testEncodingDetectorConfigurability() throws Exception {
        // CP500 (EBCDIC) is now detected by NaiveBayesPipelineEncodingDetector's structural IBM500 rule.
        // We must hint Content-Type=text/plain so that TXTParser is selected; without the filename
        // extension the byte-level MIME sniffer classifies the EBCDIC data as octet-stream.
        Metadata md = new Metadata();
        md.set("Content-Type", "text/plain");
        Metadata metadata = getXML("english.cp500.txt", md).metadata;
        assertNotNull(metadata.get(TikaCoreProperties.DETECTED_ENCODING));

        // Excluding ICU4J from the config (which is already not in the default chain)
        // should still work — ML handles EBCDIC detection.
        TikaLoader tikaLoader = TikaLoaderHelper.getLoader("TIKA-2273-no-icu4j-encoding-detector.json");
        Parser p = tikaLoader.loadAutoDetectParser();
        md = new Metadata();
        md.set("Content-Type", "text/plain");
        metadata = getXML("english.cp500.txt", p, md).metadata;
        assertNotNull(metadata.get(TikaCoreProperties.DETECTED_ENCODING));
    }


    @Test
    public void testNonDetectingDetectorParams() throws Exception {
        TikaLoader tikaLoader = TikaLoaderHelper.getLoader("TIKA-2273-override-detector-params.json");

        Parser p = tikaLoader.loadAutoDetectParser();
        List<Parser> parsers = new ArrayList<>();
        findEncodingDetectionParsers(p, parsers);

        assertEquals(7, parsers.size());
        EncodingDetector encodingDetector =
                ((AbstractEncodingDetectorParser) parsers.get(0)).getEncodingDetector();
        assertTrue(encodingDetector instanceof CompositeEncodingDetector);
        assertEquals(1, ((CompositeEncodingDetector) encodingDetector).getDetectors().size());
        EncodingDetector child =
                ((CompositeEncodingDetector) encodingDetector).getDetectors().get(0);
        assertTrue(child instanceof OverrideEncodingDetector);

        assertEquals(StandardCharsets.UTF_16LE,
                ((OverrideEncodingDetector) child).getCharset());

    }

    @Test
    public void testNonDetectingDetectorParamsBadCharset() throws Exception {
        TikaLoader tikaLoader = TikaLoaderHelper.getLoader("TIKA-2273-non-detecting-params-bad-charset.json");
        assertThrows(TikaConfigException.class, tikaLoader::loadEncodingDetectors);

    }

    @Test
    public void testConfigurabilityOfUserSpecified() throws Exception {
        TikaLoader tikaLoader = TikaLoaderHelper.getLoader("TIKA-2273-encoding-detector-outside-static-init.json");
        Parser p = tikaLoader.loadAutoDetectParser();

        //make sure that all static and non-static parsers are using the same encoding detector!
        List<Parser> parsers = new ArrayList<>();
        findEncodingDetectionParsers(p, parsers);

        assertEquals(8, parsers.size());

        for (Parser encodingDetectingParser : parsers) {
            EncodingDetector encodingDetector =
                    ((AbstractEncodingDetectorParser) encodingDetectingParser)
                            .getEncodingDetector();
            assertTrue(encodingDetector instanceof CompositeEncodingDetector);
            // BOM, Metadata, ML, Html base detectors + CharSoup MetaEncodingDetector
            // (ICU4J is excluded but was already not in the default chain)
            assertEquals(5, ((CompositeEncodingDetector) encodingDetector).getDetectors().size());
            for (EncodingDetector child : ((CompositeEncodingDetector) encodingDetector)
                    .getDetectors()) {
                assertNotContained("cu4j", child.getClass().getCanonicalName());
            }
        }

        // ML handles EBCDIC (IBM500) via structural rules, so CP500 is detectable
        Metadata metadata = getXML("english.cp500.txt", p).metadata;
        assertNotNull(metadata.get(TikaCoreProperties.DETECTED_ENCODING));

    }

    @Test
    public void testMarkLimitUnit() throws Exception {
        TikaLoader tikaLoader = TikaLoaderHelper.getLoader("TIKA-2485-encoding-detector-mark-limits.json");
        Parser p = tikaLoader.loadAutoDetectParser();
        List<Parser> parsers = new ArrayList<>();
        findEncodingDetectionParsers(p, parsers);

        assertEquals(7, parsers.size());
        for (Parser childParser : parsers) {
            EncodingDetector encodingDetector =
                    ((AbstractEncodingDetectorParser) childParser).getEncodingDetector();
            assertTrue(encodingDetector instanceof CompositeEncodingDetector);
            List<EncodingDetector> children =
                    ((CompositeEncodingDetector) encodingDetector).getDetectors();
            // 3 base detectors + 1 MetaEncodingDetector (CharSoup) = 4 total
            assertEquals(4, children.size(), childParser.getClass().toString());
            assertTrue(children.get(0) instanceof NaiveBayesPipelineEncodingDetector,
                    childParser.getClass().toString());
            HtmlEncodingDetector htmlDet = (HtmlEncodingDetector) children.get(1);
            assertEquals(100000, htmlDet.getDefaultConfig().getMarkLimit(),
                    childParser.getClass().toString());
            assertTrue(children.get(2) instanceof StandardHtmlEncodingDetector,
                    childParser.getClass().toString());
            assertTrue(children.get(3) instanceof CharSoupEncodingDetector,
                    childParser.getClass().toString());
        }
    }

    @Test
    public void testMarkLimitIntegration() throws Exception {
        StringBuilder sb = new StringBuilder();
        sb.append("<html><head><script>");
        // script length = ~80000 bytes, beyond the default mark limit of 65536
        for (int i = 0; i < 16000; i++) {
            sb.append("blah ");
        }
        sb.append("</script>");
        sb.append("<meta charset=\"utf-8\">").append("</head><body>");
        sb.append("<p>Frugt, gr&#248;nd og mere</p>");
        sb.append("<p>5 kg \u00f8kologisk frugt og gr\u00F8nt, lige til at g\u00E5 til</p>");
        sb.append("</body></html>");

        byte[] bytes = sb.toString().getBytes(StandardCharsets.UTF_8);

        // Default: the meta charset is buried at ~byte 80,000, past the default
        // mark limit of 65536. The detector falls back to windows-1252 for the
        // pure-ASCII probe. HTML entities (&#248;) render correctly regardless;
        // raw UTF-8 multibyte sequences (e.g. ø in "økologisk") are garbled.
        // Raise the mark limit via config to fix this (see below).
        Parser p = AUTO_DETECT_PARSER;

        Metadata metadata = new Metadata();
        String xml = getXML(TikaInputStream.get(bytes), p, metadata).xml;

        assertContains("gr\u00F8nd", xml);       // &#248; entity — correct regardless
        assertNotContained("\u00f8kologisk", xml); // raw UTF-8 bytes — garbled by default
        assertNotContained("gr\u00F8nt", xml);
        assertNotContained("g\u00E5 til", xml);

        // With a raised mark limit the detector reaches the meta charset and
        // correctly decodes UTF-8 content.
        p = TikaLoaderHelper.getLoader("TIKA-2485-encoding-detector-mark-limits.json").loadAutoDetectParser();

        metadata = new Metadata();
        xml = getXML(TikaInputStream.get(bytes), p, metadata).xml;

        assertContains("gr\u00F8nd", xml);
        assertContains("\u00f8kologisk", xml);
        assertContains("gr\u00F8nt", xml);
        assertContains("g\u00E5 til", xml);
    }


    @Test
    public void testExcludeCharSoupEncodingDetector() throws Exception {
        TikaLoader tikaLoader = TikaLoaderHelper.getLoader(
                "TIKA-4671-exclude-charsoup-encoding-detector.json");
        EncodingDetector detector = tikaLoader.loadEncodingDetectors();
        assertTrue(detector instanceof CompositeEncodingDetector);
        List<EncodingDetector> detectors =
                ((CompositeEncodingDetector) detector).getDetectors();
        // 4 base detectors (BOM + Metadata + ML + StandardHtml), no MetaEncodingDetector
        assertEquals(4, detectors.size());
        Set<Class<?>> excludedCharSoupClasses = detectors.stream()
                .map(Object::getClass).collect(Collectors.toSet());
        assertTrue(excludedCharSoupClasses.contains(BOMDetector.class));
        assertTrue(excludedCharSoupClasses.contains(MetadataCharsetDetector.class));
        assertTrue(excludedCharSoupClasses.contains(NaiveBayesPipelineEncodingDetector.class));
        assertTrue(excludedCharSoupClasses.contains(StandardHtmlEncodingDetector.class));
        for (EncodingDetector d : detectors) {
            assertNotContained("CharSoup", d.getClass().getSimpleName());
        }
    }

    // -----------------------------------------------------------------------
    // Solr integration-test regression (TIKA-4662)
    // -----------------------------------------------------------------------

    /**
     * ASCII HTML with an explicit {@code <meta charset="UTF-8">} must be
     * detected as UTF-8.  The full detection chain is required: the HTML
     * detector produces a DECLARATIVE UTF-8 result; CharSoupEncodingDetector
     * sees that both UTF-8 and the statistical winner (windows-1252) decode
     * the pure-ASCII bytes identically and therefore defers to the declaration.
     */
    @Test
    public void testAsciiHtmlWithMetaIsDetectedAsUtf8() throws Exception {
        byte[] bytes =
                "<html><head><meta charset=\"UTF-8\"></head><body>initial</body></html>"
                        .getBytes(StandardCharsets.UTF_8);
        EncodingDetector detector = TikaLoader.loadDefault().loadEncodingDetectors();
        try (TikaInputStream tis = TikaInputStream.get(bytes)) {
            List<EncodingResult> results =
                    detector.detect(tis, new Metadata(), new ParseContext());
            assertFalse(results.isEmpty(), "detector returned no result for ASCII HTML with meta");
            assertEquals(StandardCharsets.UTF_8, results.get(0).getCharset(),
                    "ASCII HTML with <meta charset=UTF-8> should be detected as UTF-8, got: "
                            + results.get(0).getCharset().name());
        }
    }

    @Test
    public void testArabicMisleadingCharsetHtml() throws Exception {
        // This HTML file is encoded in windows-1256 but declares charset=UTF-8
        // in the meta tag. The CharSoupEncodingDetector should override the
        // misleading HTML meta and detect that the actual content is Arabic
        // (windows-1256) because windows-1256 decoded text produces a higher
        // language detection score.
        Metadata metadata = new Metadata();
        XMLResult result = getXML("testArabicMisleadingCharset.html", metadata);
        // Verify encoding was detected as windows-1256, not the misleading UTF-8
        assertEquals("windows-1256",
                metadata.get(TikaCoreProperties.DETECTED_ENCODING));
        // Verify extracted text contains readable Arabic, not mojibake
        // \u0627\u0644\u0639\u0631\u0628\u064a\u0629 = "العربية" (Arabic)
        assertContains("\u0627\u0644\u0639\u0631\u0628\u064a\u0629", result.xml);
    }

    /**
     * Demonstrates loading a config file that sets {@code skipBOM=false} on
     * {@code StandardHtmlEncodingDetector} for standalone use (without
     * {@code BOMDetector} in the chain).  With this config, the HTML detector
     * handles BOM detection itself, so BOM takes precedence over
     * {@code <meta charset>} per the HTML5 spec.
     *
     * @see configs/tika-config-html-standalone-bom.json
     */
    @Test
    public void testStandaloneHtmlBomConfig() throws Exception {
        TikaLoader tikaLoader =
                TikaLoaderHelper.getLoader("tika-config-html-standalone-bom.json");
        EncodingDetector detector = tikaLoader.loadEncodingDetectors();
        assertTrue(detector instanceof CompositeEncodingDetector);
        List<EncodingDetector> detectors =
                ((CompositeEncodingDetector) detector).getDetectors();
        assertEquals(1, detectors.size());
        assertTrue(detectors.get(0) instanceof StandardHtmlEncodingDetector);
        assertFalse(((StandardHtmlEncodingDetector) detectors.get(0)).isSkipBOM(),
                "skipBOM should be false for standalone use");

        // BOM-prefixed HTML with a contradicting <meta charset>:
        // with skipBOM=false the BOM wins (HTML5 spec behaviour).
        byte[] html = "\ufeff<meta charset='WINDOWS-1252'>"
                .getBytes(StandardCharsets.UTF_8);
        try (TikaInputStream tis = TikaInputStream.get(html)) {
            List<EncodingResult> results =
                    detector.detect(tis, new Metadata(), new ParseContext());
            assertFalse(results.isEmpty());
            assertEquals(StandardCharsets.UTF_8, results.get(0).getCharset(),
                    "standalone skipBOM=false: BOM should override <meta charset>");
        }
    }

    private void findEncodingDetectionParsers(Parser p, List<Parser> encodingDetectionParsers) {

        if (p instanceof CompositeParser) {
            for (Parser child : ((CompositeParser) p).getAllComponentParsers()) {
                findEncodingDetectionParsers(child, encodingDetectionParsers);
            }
        } else if (p instanceof ParserDecorator) {
            findEncodingDetectionParsers(((ParserDecorator) p), encodingDetectionParsers);
        }

        if (p instanceof AbstractEncodingDetectorParser) {
            encodingDetectionParsers.add(p);
        }
    }

}
