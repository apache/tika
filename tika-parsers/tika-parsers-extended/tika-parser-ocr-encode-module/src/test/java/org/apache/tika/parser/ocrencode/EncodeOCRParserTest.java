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
package org.apache.tika.parser.ocrencode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Base64;

import org.junit.jupiter.api.Test;

import org.apache.tika.TikaTest;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;

public class EncodeOCRParserTest extends TikaTest {

    // Markers as they appear in XML-serialized output
    // (< and > are escaped by ToXMLContentHandler)
    private static final String BEGIN_MARKER_XML =
            "&lt;&lt;&lt;---IMAGE-BASE64-ENCODED-BEGIN---"
                    + "&gt;&gt;&gt;";
    private static final String END_MARKER_XML =
            "&lt;&lt;&lt;---IMAGE-BASE64-ENCODED-END---"
                    + "&gt;&gt;&gt;";

    private Metadata getMetadata(MediaType mediaType) {
        Metadata metadata = new Metadata();
        MediaType ocrMediaType =
                new MediaType(mediaType.getType(),
                        "ocr-" + mediaType.getSubtype());
        metadata.set(
                TikaCoreProperties.CONTENT_TYPE_PARSER_OVERRIDE,
                ocrMediaType.toString());
        return metadata;
    }

    @Test
    public void testBasicEncoding() throws Exception {
        byte[] simplePng = createSimplePng();

        EncodeOCRParser parser = new EncodeOCRParser();
        TikaInputStream tis = TikaInputStream.get(simplePng);
        String xml = getXML(tis, parser,
                getMetadata(MediaType.image("png"))).xml;

        // Should contain the base64 markers
        assertContains(BEGIN_MARKER_XML, xml);
        assertContains(END_MARKER_XML, xml);

        // Should contain base64 encoded content
        assertTrue(xml.contains("iVBOR"),
                "Should contain base64 PNG signature");

        // Should have ocr class div
        assertContains("<div class=\"ocr\">", xml);
    }

    @Test
    public void testBasicEncodingWithResourceFile() throws Exception {
        EncodeOCRParser parser = new EncodeOCRParser();
        String xml = getXML("testOCR_encode.png", parser,
                getMetadata(MediaType.image("png"))).xml;

        assertContains(BEGIN_MARKER_XML, xml);
        assertContains(END_MARKER_XML, xml);
        assertTrue(xml.contains("iVBOR"),
                "Should contain base64 PNG signature");
        assertContains("<div class=\"ocr\">", xml);
    }

    @Test
    public void testJpegEncoding() throws Exception {
        EncodeOCRParser parser = new EncodeOCRParser();
        String xml = getXML("testOCR_encode.jpg", parser,
                getMetadata(MediaType.image("jpeg"))).xml;

        assertContains(BEGIN_MARKER_XML, xml);
        assertContains(END_MARKER_XML, xml);
        // JPEG base64 starts with /9j/
        assertTrue(xml.contains("/9j/"),
                "Should contain base64 JPEG signature");
    }

    @Test
    public void testBase64OutputIsDecodable() throws Exception {
        EncodeOCRParser parser = new EncodeOCRParser();
        String text = getText("testOCR_encode.png", parser,
                getMetadata(MediaType.image("png")));

        String beginLiteral =
                "<<<---IMAGE-BASE64-ENCODED-BEGIN--->>>";
        String endLiteral =
                "<<<---IMAGE-BASE64-ENCODED-END--->>>";

        int start = text.indexOf(beginLiteral);
        int end = text.indexOf(endLiteral);
        assertTrue(start >= 0,
                "Begin marker not found in text output");
        assertTrue(end > start,
                "End marker not found after begin marker");

        String b64 = text.substring(
                start + beginLiteral.length(), end).trim();
        // Use MIME decoder because the output may contain
        // line breaks
        byte[] decoded = Base64.getMimeDecoder().decode(b64);
        // PNG magic bytes
        assertEquals((byte) 0x89, decoded[0]);
        assertEquals((byte) 0x50, decoded[1]); // 'P'
        assertEquals((byte) 0x4E, decoded[2]); // 'N'
        assertEquals((byte) 0x47, decoded[3]); // 'G'
    }

    @Test
    public void testSkipOCR() throws Exception {
        EncodeOCRConfig config = new EncodeOCRConfig();
        config.setSkipOcr(true);
        ParseContext context = new ParseContext();
        context.set(EncodeOCRConfig.class, config);

        EncodeOCRParser parser = new EncodeOCRParser();
        TikaInputStream tis = TikaInputStream.get(createSimplePng());
        String xml = getXML(tis, parser,
                getMetadata(MediaType.image("png")),
                context).xml;

        assertNotContained(BEGIN_MARKER_XML, xml);
        assertNotContained(END_MARKER_XML, xml);
    }

    @Test
    public void testRuntimeSkipOCRViaConstructor() throws Exception {
        EncodeOCRConfig config = new EncodeOCRConfig();
        config.setSkipOcr(true);
        EncodeOCRParser parser = new EncodeOCRParser(config);

        TikaInputStream tis = TikaInputStream.get(createSimplePng());
        String xml = getXML(tis, parser,
                getMetadata(MediaType.image("png"))).xml;

        assertNotContained(BEGIN_MARKER_XML, xml);
    }

    @Test
    public void testMinFileSizeFilter() throws Exception {
        EncodeOCRConfig config = new EncodeOCRConfig();
        config.setMinFileSizeToOcr(10000); // 10KB minimum
        ParseContext context = new ParseContext();
        context.set(EncodeOCRConfig.class, config);

        EncodeOCRParser parser = new EncodeOCRParser();
        TikaInputStream tis = TikaInputStream.get(createSimplePng());
        String xml = getXML(tis, parser,
                getMetadata(MediaType.image("png")),
                context).xml;

        assertNotContained(BEGIN_MARKER_XML, xml);
    }

    @Test
    public void testMaxFileSizeFilter() throws Exception {
        EncodeOCRConfig config = new EncodeOCRConfig();
        config.setMaxFileSizeToOcr(10); // 10 bytes maximum
        ParseContext context = new ParseContext();
        context.set(EncodeOCRConfig.class, config);

        EncodeOCRParser parser = new EncodeOCRParser();
        TikaInputStream tis = TikaInputStream.get(createSimplePng());
        String xml = getXML(tis, parser,
                getMetadata(MediaType.image("png")),
                context).xml;

        assertNotContained(BEGIN_MARKER_XML, xml);
    }

    @Test
    public void testFileSizeWithinRange() throws Exception {
        EncodeOCRConfig config = new EncodeOCRConfig();
        config.setMinFileSizeToOcr(1);
        config.setMaxFileSizeToOcr(100000);
        ParseContext context = new ParseContext();
        context.set(EncodeOCRConfig.class, config);

        EncodeOCRParser parser = new EncodeOCRParser();
        TikaInputStream tis = TikaInputStream.get(createSimplePng());
        String xml = getXML(tis, parser,
                getMetadata(MediaType.image("png")),
                context).xml;

        assertContains(BEGIN_MARKER_XML, xml);
    }

    @Test
    public void testMaxImagesToOcr() throws Exception {
        EncodeOCRConfig config = new EncodeOCRConfig();
        config.setMaxImagesToOcr(1);
        ParseContext context = new ParseContext();
        context.set(EncodeOCRConfig.class, config);

        EncodeOCRParser parser = new EncodeOCRParser();

        // First image should be processed
        TikaInputStream tis1 = TikaInputStream.get(createSimplePng());
        String xml = getXML(tis1, parser,
                getMetadata(MediaType.image("png")),
                context).xml;
        assertContains(BEGIN_MARKER_XML, xml);

        // Second image should be skipped (reusing same context)
        TikaInputStream tis2 = TikaInputStream.get(createSimplePng());
        xml = getXML(tis2, parser,
                getMetadata(MediaType.image("png")),
                context).xml;
        assertNotContained(BEGIN_MARKER_XML, xml);
    }

    @Test
    public void testMaxImagesToOcrZero() throws Exception {
        EncodeOCRConfig config = new EncodeOCRConfig();
        config.setMaxImagesToOcr(0);
        ParseContext context = new ParseContext();
        context.set(EncodeOCRConfig.class, config);

        EncodeOCRParser parser = new EncodeOCRParser();
        TikaInputStream tis = TikaInputStream.get(createSimplePng());
        String xml = getXML(tis, parser,
                getMetadata(MediaType.image("png")),
                context).xml;
        assertNotContained(BEGIN_MARKER_XML, xml);
    }

    @Test
    public void testMaxImagesToOcrMultiple() throws Exception {
        EncodeOCRConfig config = new EncodeOCRConfig();
        config.setMaxImagesToOcr(3);
        ParseContext context = new ParseContext();
        context.set(EncodeOCRConfig.class, config);

        EncodeOCRParser parser = new EncodeOCRParser();

        // Images 1-3 should be processed
        for (int i = 0; i < 3; i++) {
            TikaInputStream tis =
                    TikaInputStream.get(createSimplePng());
            String xml = getXML(tis, parser,
                    getMetadata(MediaType.image("png")),
                    context).xml;
            assertContains(BEGIN_MARKER_XML, xml);
        }

        // Image 4 should be skipped
        TikaInputStream tis = TikaInputStream.get(createSimplePng());
        String xml = getXML(tis, parser,
                getMetadata(MediaType.image("png")),
                context).xml;
        assertNotContained(BEGIN_MARKER_XML, xml);
    }

    @Test
    public void testSupportedTypes() {
        EncodeOCRParser parser = new EncodeOCRParser();
        ParseContext context = new ParseContext();

        assertTrue(parser.getSupportedTypes(context)
                .contains(MediaType.image("ocr-png")));
        assertTrue(parser.getSupportedTypes(context)
                .contains(MediaType.image("ocr-jpeg")));
        assertTrue(parser.getSupportedTypes(context)
                .contains(MediaType.image("ocr-tiff")));
        assertTrue(parser.getSupportedTypes(context)
                .contains(MediaType.image("ocr-bmp")));
        assertTrue(parser.getSupportedTypes(context)
                .contains(MediaType.image("ocr-gif")));

        assertTrue(parser.getSupportedTypes(context)
                .contains(MediaType.image("jp2")));
        assertTrue(parser.getSupportedTypes(context)
                .contains(MediaType.image("jpx")));
        assertTrue(parser.getSupportedTypes(context)
                .contains(MediaType.image("x-portable-pixmap")));

        assertTrue(parser.getSupportedTypes(context)
                .contains(MediaType.image("ocr-jp2")));
        assertTrue(parser.getSupportedTypes(context)
                .contains(MediaType.image("ocr-jpx")));
        assertTrue(parser.getSupportedTypes(context)
                .contains(MediaType.image(
                        "ocr-x-portable-pixmap")));
    }

    @Test
    public void testSupportedTypesWithSkipOcr() {
        EncodeOCRParser parser = new EncodeOCRParser();
        EncodeOCRConfig config = new EncodeOCRConfig();
        config.setSkipOcr(true);
        ParseContext context = new ParseContext();
        context.set(EncodeOCRConfig.class, config);

        assertEquals(0, parser.getSupportedTypes(context).size());
    }

    @Test
    public void testSupportedTypesWithSkipOcrViaDefaultConfig() {
        EncodeOCRConfig config = new EncodeOCRConfig();
        config.setSkipOcr(true);
        EncodeOCRParser parser = new EncodeOCRParser(config);

        // Empty context — skipOcr comes from defaultConfig
        ParseContext context = new ParseContext();
        assertEquals(0, parser.getSupportedTypes(context).size(),
                "getSupportedTypes should respect defaultConfig.skipOcr");
    }

    @Test
    public void testSupportedTypesCount() {
        EncodeOCRParser parser = new EncodeOCRParser();
        ParseContext context = new ParseContext();
        // 5 ocr- types + 3 non-ocr + 3 ocr- versions of non-ocr = 11
        assertEquals(11, parser.getSupportedTypes(context).size());
    }

    @Test
    public void testDefaultConfig() {
        EncodeOCRParser parser = new EncodeOCRParser();
        EncodeOCRConfig config = parser.getDefaultConfig();
        assertNotNull(config);
        assertFalse(config.isSkipOcr());
        assertEquals(0, config.getMinFileSizeToOcr());
        assertEquals(EncodeOCRConfig.DEFAULT_MAX_FILE_SIZE_TO_OCR,
                config.getMaxFileSizeToOcr());
        assertEquals(50, config.getMaxImagesToOcr());
    }

    @Test
    public void testConstructorWithConfig() {
        EncodeOCRConfig config = new EncodeOCRConfig();
        config.setMaxFileSizeToOcr(5000);
        config.setMinFileSizeToOcr(100);
        config.setMaxImagesToOcr(10);
        config.setSkipOcr(true);

        EncodeOCRParser parser = new EncodeOCRParser(config);
        EncodeOCRConfig defaultConfig = parser.getDefaultConfig();
        assertEquals(5000, defaultConfig.getMaxFileSizeToOcr());
        assertEquals(100, defaultConfig.getMinFileSizeToOcr());
        assertEquals(10, defaultConfig.getMaxImagesToOcr());
        assertTrue(defaultConfig.isSkipOcr());
    }

    @Test
    public void testInitialize() throws Exception {
        EncodeOCRParser parser = new EncodeOCRParser();
        // initialize should be a no-op and not throw
        parser.initialize();
    }

    @Test
    public void testCheckInitialization() throws Exception {
        EncodeOCRParser parser = new EncodeOCRParser();
        // checkInitialization should be a no-op and not throw
        parser.checkInitialization();
    }

    @Test
    public void testParseWithNoConfig() throws Exception {
        EncodeOCRParser parser = new EncodeOCRParser();
        ParseContext context = new ParseContext();

        TikaInputStream tis = TikaInputStream.get(createSimplePng());
        String xml = getXML(tis, parser,
                getMetadata(MediaType.image("png")),
                context).xml;
        assertContains(BEGIN_MARKER_XML, xml);
    }

    @Test
    public void testUserConfigOverridesDefault() throws Exception {
        EncodeOCRConfig parserConfig = new EncodeOCRConfig();
        parserConfig.setMaxImagesToOcr(5);
        EncodeOCRParser parser = new EncodeOCRParser(parserConfig);

        EncodeOCRConfig userConfig = new EncodeOCRConfig();
        userConfig.setMinFileSizeToOcr(1);

        ParseContext context = new ParseContext();
        context.set(EncodeOCRConfig.class, userConfig);

        // parser's defaultConfig is unaffected by ParseContext config
        assertEquals(5, parser.getDefaultConfig().getMaxImagesToOcr());

        TikaInputStream tis = TikaInputStream.get(createSimplePng());
        String xml = getXML(tis, parser,
                getMetadata(MediaType.image("png")),
                context).xml;
        assertContains(BEGIN_MARKER_XML, xml);
    }

    /**
     * Creates a minimal valid 1x1 PNG image for testing.
     */
    private byte[] createSimplePng() {
        String base64Png = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAY"
                + "AAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAA"
                + "BJRU5ErkJggg==";
        return Base64.getDecoder().decode(base64Png);
    }
}
