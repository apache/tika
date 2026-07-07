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

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import org.apache.tika.TikaLoaderHelper;
import org.apache.tika.TikaTest;
import org.apache.tika.config.loader.TikaLoader;
import org.apache.tika.detect.CompositeEncodingDetector;
import org.apache.tika.detect.EncodingDetector;
import org.apache.tika.detect.EncodingResult;
import org.apache.tika.detect.MetaEncodingDetector;
import org.apache.tika.detect.OverrideEncodingDetector;
import org.apache.tika.detect.html.HtmlEncodingDetector;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.ml.chardetect.MojibusterEncodingDetector;
import org.apache.tika.parser.AbstractEncodingDetectorParser;
import org.apache.tika.parser.CompositeParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.ParserDecorator;
import org.apache.tika.parser.html.charsetdetector.StandardHtmlEncodingDetector;
import org.apache.tika.parser.txt.TXTParser;

public class TikaEncodingDetectorTest extends TikaTest {

    @Test
    public void testDefault() throws TikaConfigException {
        EncodingDetector detector = TikaLoader.loadDefault().loadEncodingDetectors();
        assertTrue(detector instanceof CompositeEncodingDetector);
        List<EncodingDetector> detectors = ((CompositeEncodingDetector) detector).getDetectors();
        // 4.x default chain: BOM, metadata, html, mojibuster, junk-filter.
        assertEquals(5, detectors.size());
        Set<String> baseClassNames = detectors.stream()
                .map(d -> d.getClass().getName()).collect(Collectors.toSet());
        assertTrue(baseClassNames.contains("org.apache.tika.detect.BOMDetector"));
        assertTrue(baseClassNames.contains("org.apache.tika.detect.MetadataCharsetDetector"));
        assertTrue(baseClassNames.contains(HtmlEncodingDetector.class.getName()));
        assertTrue(baseClassNames.contains(MojibusterEncodingDetector.class.getName()));
        assertTrue(baseClassNames.contains(
                "org.apache.tika.ml.junkdetect.JunkFilterEncodingDetector"));
    }

    @Test
    public void testExcludeList() throws Exception {
        TikaLoader tikaLoader = TikaLoaderHelper.getLoader("TIKA-2273-exclude-encoding-detector-default.json");
        EncodingDetector detector = tikaLoader.loadEncodingDetectors();
        assertTrue(detector instanceof CompositeEncodingDetector);
        List<EncodingDetector> detectors = ((CompositeEncodingDetector) detector).getDetectors();
        // default-encoding-detector (inner composite) + override-encoding-detector
        assertEquals(2, detectors.size());

        EncodingDetector detector1 = detectors.get(0);
        assertTrue(detector1 instanceof CompositeEncodingDetector);
        List<EncodingDetector> detectors1Children =
                ((CompositeEncodingDetector) detector1).getDetectors();
        // default chain minus excluded html: BOM, metadata, mojibuster, junk-filter.
        assertEquals(4, detectors1Children.size());
        Set<String> innerClassNames = detectors1Children.stream()
                .map(d -> d.getClass().getName()).collect(Collectors.toSet());
        assertFalse(innerClassNames.contains(HtmlEncodingDetector.class.getName()));
        assertTrue(innerClassNames.contains("org.apache.tika.detect.BOMDetector"));
        assertTrue(innerClassNames.contains("org.apache.tika.detect.MetadataCharsetDetector"));
        assertTrue(innerClassNames.contains(MojibusterEncodingDetector.class.getName()));
        assertTrue(innerClassNames.contains(
                "org.apache.tika.ml.junkdetect.JunkFilterEncodingDetector"));

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
        // CP500/EBCDIC: mojibuster's IBM500 rule detects it. Hint text/plain so
        // TXTParser runs (else the byte sniffer calls it octet-stream).
        Metadata md = new Metadata();
        md.set("Content-Type", "text/plain");
        Metadata metadata = getXML("english.cp500.txt", md).metadata;
        assertNotNull(metadata.get(TikaCoreProperties.DETECTED_ENCODING));

        // Excluding the (already-absent) icu4j still works; ML handles EBCDIC.
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

        assertEquals(8, parsers.size());
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

        assertEquals(9, parsers.size());

        for (Parser encodingDetectingParser : parsers) {
            EncodingDetector encodingDetector =
                    ((AbstractEncodingDetectorParser) encodingDetectingParser)
                            .getEncodingDetector();
            assertTrue(encodingDetector instanceof CompositeEncodingDetector);
            // icu4j not in default chain; excluding it is a no-op -> full chain (5).
            assertEquals(5, ((CompositeEncodingDetector) encodingDetector).getDetectors().size());
            for (EncodingDetector child : ((CompositeEncodingDetector) encodingDetector)
                    .getDetectors()) {
                assertNotContained("cu4j", child.getClass().getCanonicalName());
            }
        }

        // CP500/EBCDIC detection is covered by testEncodingDetectorConfigurability
        // (needs a text/plain hint, omitted here).
    }

    @Test
    public void testMarkLimitUnit() throws Exception {
        TikaLoader tikaLoader = TikaLoaderHelper.getLoader("TIKA-2485-encoding-detector-mark-limits.json");
        Parser p = tikaLoader.loadAutoDetectParser();
        List<Parser> parsers = new ArrayList<>();
        findEncodingDetectionParsers(p, parsers);

        assertEquals(8, parsers.size());
        for (Parser childParser : parsers) {
            EncodingDetector encodingDetector =
                    ((AbstractEncodingDetectorParser) childParser).getEncodingDetector();
            assertTrue(encodingDetector instanceof CompositeEncodingDetector);
            List<EncodingDetector> children =
                    ((CompositeEncodingDetector) encodingDetector).getDetectors();
            // 3 base detectors + 1 MetaEncodingDetector (JunkFilter)
            assertEquals(4, children.size(), childParser.getClass().toString());
            assertTrue(children.get(0) instanceof MojibusterEncodingDetector,
                    childParser.getClass().toString());
            HtmlEncodingDetector htmlDet = (HtmlEncodingDetector) children.get(1);
            assertEquals(700000, htmlDet.getDefaultConfig().getMarkLimit(),
                    childParser.getClass().toString());
            assertTrue(children.get(2) instanceof StandardHtmlEncodingDetector,
                    childParser.getClass().toString());
            assertTrue(children.get(3) instanceof MetaEncodingDetector,
                    childParser.getClass().toString());
        }
    }

    @Test
    public void testMarkLimitIntegration() throws Exception {
        StringBuilder sb = new StringBuilder();
        sb.append("<html><head><script>");
        // ~600 KB of script: past mojibuster's 512 KB probe and the html mark
        // limit, so the buried meta + UTF-8 body aren't reached by default.
        for (int i = 0; i < 120000; i++) {
            sb.append("blah ");
        }
        sb.append("</script>");
        sb.append("<meta charset=\"utf-8\">").append("</head><body>");
        sb.append("<p>Frugt, gr&#248;nd og mere</p>");
        sb.append("<p>5 kg \u00f8kologisk frugt og gr\u00F8nt, lige til at g\u00E5 til</p>");
        sb.append("</body></html>");

        byte[] bytes = sb.toString().getBytes(StandardCharsets.UTF_8);

        // Default: meta buried at ~byte 600,000, past mojibuster's probe and the
        // html mark limit, so mojibuster sees pure ASCII and returns windows-1252.
        // Entities (&#248;) survive; raw UTF-8 (ø in "økologisk") is garbled.
        // Raised mark limit fixes it (see below).
        Parser p = AUTO_DETECT_PARSER;

        Metadata metadata = new Metadata();
        String xml = getXML(TikaInputStream.get(bytes), p, metadata).xml;

        assertContains("gr\u00F8nd", xml);       // &#248; entity — correct regardless
        assertNotContained("\u00f8kologisk", xml); // raw UTF-8 bytes — garbled by default
        assertNotContained("gr\u00F8nt", xml);
        assertNotContained("g\u00E5 til", xml);

        // Raised mark limit reaches the meta and decodes UTF-8.
        p = TikaLoaderHelper.getLoader("TIKA-2485-encoding-detector-mark-limits.json").loadAutoDetectParser();

        metadata = new Metadata();
        xml = getXML(TikaInputStream.get(bytes), p, metadata).xml;

        assertContains("gr\u00F8nd", xml);
        assertContains("\u00f8kologisk", xml);
        assertContains("gr\u00F8nt", xml);
        assertContains("g\u00E5 til", xml);
    }


    // -----------------------------------------------------------------------
    // Solr integration-test regression (TIKA-4662)
    // -----------------------------------------------------------------------

    /**
     * Pure-ASCII HTML with {@code <meta charset="UTF-8">} detects as UTF-8.
     * The bytes decode identically as UTF-8 and windows-1252, so the statistical
     * signal is neutral and the declarative hint is left to decide. Strong
     * statistical evidence would override the declarative hint.
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

    /**
     * Strong statistical evidence overrides a wrong declaration: a clearly-UTF-8
     * body that declares windows-1252 is still detected as UTF-8.
     */
    @Test
    public void testStatisticalOverridesWrongDeclaration() throws Exception {
        String body = "Съешь же ещё этих мягких французских булок да выпей чаю. ".repeat(10);
        byte[] bytes = ("<html><head><meta charset=\"windows-1252\"></head><body>"
                + body + "</body></html>").getBytes(StandardCharsets.UTF_8);
        EncodingDetector detector = TikaLoader.loadDefault().loadEncodingDetectors();
        try (TikaInputStream tis = TikaInputStream.get(bytes)) {
            List<EncodingResult> results =
                    detector.detect(tis, new Metadata(), new ParseContext());
            assertFalse(results.isEmpty(), "no result for strong-UTF-8 body");
            assertEquals(StandardCharsets.UTF_8, results.get(0).getCharset(),
                    "strong UTF-8 must override the windows-1252 declaration, got: "
                            + results.get(0).getCharset().name());
        }
    }

    /**
     * Strong statistical evidence overrides a wrong BOM: a windows-1252 body that
     * is invalid as UTF-8 but carries a UTF-8 BOM is still detected as
     * windows-1252.
     */
    @Test
    public void testStatisticalOverridesWrongBom() throws Exception {
        Charset win1252 = Charset.forName("windows-1252");
        String body = ("He said “Hello” — it’s 100% © "
                + "café, naïve, résumé. ").repeat(8);
        byte[] win = body.getBytes(win1252);
        byte[] bytes = new byte[3 + win.length];
        bytes[0] = (byte) 0xEF;
        bytes[1] = (byte) 0xBB;
        bytes[2] = (byte) 0xBF; // UTF-8 BOM
        System.arraycopy(win, 0, bytes, 3, win.length);
        EncodingDetector detector = TikaLoader.loadDefault().loadEncodingDetectors();
        try (TikaInputStream tis = TikaInputStream.get(bytes)) {
            List<EncodingResult> results =
                    detector.detect(tis, new Metadata(), new ParseContext());
            assertFalse(results.isEmpty(), "no result for windows-1252 body with UTF-8 BOM");
            assertEquals(win1252, results.get(0).getCharset(),
                    "windows-1252 body must override the UTF-8 BOM, got: "
                            + results.get(0).getCharset().name());
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
