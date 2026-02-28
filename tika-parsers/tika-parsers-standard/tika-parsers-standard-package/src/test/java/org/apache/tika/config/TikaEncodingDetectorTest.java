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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.apache.tika.Tika;
import org.apache.tika.TikaLoaderHelper;
import org.apache.tika.TikaTest;
import org.apache.tika.config.loader.TikaLoader;
import org.apache.tika.detect.CompositeEncodingDetector;
import org.apache.tika.detect.EncodingDetector;
import org.apache.tika.detect.MetaEncodingDetector;
import org.apache.tika.detect.OverrideEncodingDetector;
import org.apache.tika.detect.encoding.BomEncodingDetector;
import org.apache.tika.detect.encoding.HttpHeaderEncodingDetector;
import org.apache.tika.detect.encoding.Icu4jEncodingDetector;
import org.apache.tika.detect.encoding.MlEncodingDetector;
import org.apache.tika.detect.encoding.UniversalEncodingDetector;
import org.apache.tika.detect.html.HtmlEncodingDetector;
import org.apache.tika.detect.html.StandardHtmlEncodingDetector;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AbstractEncodingDetectorParser;
import org.apache.tika.parser.CompositeParser;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.ParserDecorator;
import org.apache.tika.parser.txt.TXTParser;

public class TikaEncodingDetectorTest extends TikaTest {

    @Test
    public void testDefault() throws TikaConfigException {
        EncodingDetector detector = TikaLoader.loadDefault().loadEncodingDetectors();
        assertTrue(detector instanceof CompositeEncodingDetector);
        List<EncodingDetector> detectors = ((CompositeEncodingDetector) detector).getDetectors();
        // At minimum: HttpHeader, BOM, StandardHtml, ML, CharSoup(Meta).
        // Optional modules (e.g. universal) auto-register when their jars are present.
        assertTrue(detectors.size() >= 5, "Expected at least 5 detectors, got: " + detectors.size());
        assertTrue(detectors.stream().anyMatch(d -> d instanceof HttpHeaderEncodingDetector));
        assertTrue(detectors.stream().anyMatch(d -> d instanceof BomEncodingDetector));
        assertTrue(detectors.stream().anyMatch(d -> d instanceof StandardHtmlEncodingDetector));
        assertTrue(detectors.stream().anyMatch(d -> d instanceof MlEncodingDetector));
        assertTrue(detectors.stream().anyMatch(d -> d instanceof MetaEncodingDetector),
                "Expected a MetaEncodingDetector (CharSoupEncodingDetector)");
        // MetaEncodingDetector must be last
        assertTrue(detectors.get(detectors.size() - 1) instanceof MetaEncodingDetector);
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
        // New chain minus bom-encoding-detector: HttpHeader, StandardHtml, ML, optional Universal, CharSoup(Meta)
        assertTrue(detectors1Children.size() >= 4, "Expected >=4, got: " + detectors1Children.size());
        assertTrue(detectors1Children.stream().anyMatch(d -> d instanceof HttpHeaderEncodingDetector));
        assertTrue(detectors1Children.stream().anyMatch(d -> d instanceof StandardHtmlEncodingDetector));
        assertTrue(detectors1Children.stream().anyMatch(d -> d instanceof MlEncodingDetector));
        assertTrue(detectors1Children.stream().anyMatch(d -> d instanceof MetaEncodingDetector));
        assertTrue(detectors1Children.stream().noneMatch(d -> d instanceof BomEncodingDetector));

        assertTrue(detectors.get(1) instanceof OverrideEncodingDetector);

    }

    @Test
    public void testParameterization() throws Exception {
        TikaLoader tikaLoader = TikaLoaderHelper.getLoader("TIKA-2273-parameterize-encoding-detector.json");
        EncodingDetector detector = tikaLoader.loadEncodingDetectors();
        assertTrue(detector instanceof CompositeEncodingDetector);
        List<EncodingDetector> detectors = ((CompositeEncodingDetector) detector).getDetectors();
        assertEquals(2, detectors.size());
        assertTrue(((Icu4jEncodingDetector) detectors.get(0)).getDefaultConfig().stripMarkup);
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
        // Config excludes ml-encoding-detector; without it, EBCDIC (cp500) cannot be detected
        TikaLoader tikaLoader = TikaLoaderHelper.getLoader("TIKA-2273-no-icu4j-encoding-detector.json");
        Parser p = tikaLoader.loadAutoDetectParser();

        try {
            Metadata metadata = getXML("english.cp500.txt", p).metadata;
            fail("can't detect w/out ML detector");
        } catch (TikaException e) {
            assertContains("Failed to detect", e.getMessage());
        }

        Tika tika = new Tika(tikaLoader.loadDetectors(), tikaLoader.loadAutoDetectParser());
        Path tmp = null;
        try {
            tmp = Files.createTempFile("tika-encoding-test", ".txt");
            Files.copy(getResourceAsStream("/test-documents/english.cp500.txt"), tmp,
                    StandardCopyOption.REPLACE_EXISTING);
            String txt = tika.parseToString(tmp);
            fail("can't detect w/out ML detector");
        } catch (TikaException e) {
            assertContains("Failed to detect", e.getMessage());
        } finally {
            Files.delete(tmp);
        }
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
            // New chain minus bom-encoding-detector: HttpHeader, StandardHtml, ML, optional Universal, CharSoup(Meta)
            assertTrue(((CompositeEncodingDetector) encodingDetector).getDetectors().size() >= 4);
            for (EncodingDetector child : ((CompositeEncodingDetector) encodingDetector)
                    .getDetectors()) {
                assertNotContained("Bom", child.getClass().getSimpleName());
            }
        }

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
            assertEquals(3, ((CompositeEncodingDetector) encodingDetector).getDetectors().size());
            for (EncodingDetector childEncodingDetector :
                    ((CompositeEncodingDetector) encodingDetector).getDetectors()) {
                if (childEncodingDetector instanceof HtmlEncodingDetector) {
                    assertEquals(64000,
                            ((HtmlEncodingDetector) childEncodingDetector).getDefaultConfig().getMarkLimit(),
                            childParser.getClass().toString());
                } else if (childEncodingDetector instanceof UniversalEncodingDetector) {
                    assertEquals(64001,
                            ((UniversalEncodingDetector) childEncodingDetector).getDefaultConfig().getMarkLimit(),
                            childParser.getClass().toString());
                } else if (childEncodingDetector instanceof Icu4jEncodingDetector) {
                    assertEquals(64002,
                            ((Icu4jEncodingDetector) childEncodingDetector).getDefaultConfig().getMarkLimit(),
                            childParser.getClass().toString());
                }
            }
        }
    }

    @Test
    public void testMarkLimitIntegration() throws Exception {
        StringBuilder sb = new StringBuilder();
        sb.append("<html><head><script>");
        for (int i = 0; i < 4000; i++) { //script length = 20000
            sb.append("blah ");
        }
        sb.append("</script>");
        sb.append("<meta charset=\"utf-8\">").append("</head><body>");
        sb.append("<p>Frugt, gr&#248;nd og mere</p>");
        sb.append("<p>5 kg \u00f8kologisk frugt og gr\u00F8nt, lige til at g\u00E5 til</p>");
        sb.append("</body></html>");

        byte[] bytes = sb.toString().getBytes(StandardCharsets.UTF_8);

        // With the new default chain (ML detector), ASCII content before the meta tag is
        // correctly classified as UTF-8 even without reading the meta charset tag.
        // The ML detector returns UTF-8 for pure ASCII content (valid UTF-8 subset).
        Parser p = AUTO_DETECT_PARSER;

        Metadata metadata = new Metadata();
        String xml = getXML(TikaInputStream.get(bytes), p, metadata).xml;

        assertContains("gr\u00F8nd", xml);
        assertContains("\u00f8kologisk", xml);
        assertContains("gr\u00F8nt", xml);
        assertContains("g\u00E5 til", xml);

        //now test that fix works
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
        // At least 4 base detectors (HttpHeader, BOM, StandardHtml, ML), no MetaEncodingDetector.
        // Optional modules (e.g. universal) auto-register when present.
        assertTrue(detectors.size() >= 4, "Expected at least 4 detectors, got: " + detectors.size());
        assertTrue(detectors.stream().anyMatch(d -> d instanceof HttpHeaderEncodingDetector));
        assertTrue(detectors.stream().anyMatch(d -> d instanceof BomEncodingDetector));
        assertTrue(detectors.stream().anyMatch(d -> d instanceof StandardHtmlEncodingDetector));
        assertTrue(detectors.stream().anyMatch(d -> d instanceof MlEncodingDetector));
        for (EncodingDetector d : detectors) {
            assertNotContained("CharSoup", d.getClass().getSimpleName());
        }
        assertTrue(detectors.stream().noneMatch(d -> d instanceof MetaEncodingDetector));
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
