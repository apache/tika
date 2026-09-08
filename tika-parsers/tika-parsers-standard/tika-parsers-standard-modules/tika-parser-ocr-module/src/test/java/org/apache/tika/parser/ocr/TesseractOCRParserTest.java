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
package org.apache.tika.parser.ocr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import org.apache.tika.TikaTest;
import org.apache.tika.config.TimeoutLimits;
import org.apache.tika.config.loader.TikaLoader;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;

public class TesseractOCRParserTest extends TikaTest {

    public static boolean canRun() throws TikaConfigException {
        TesseractOCRParser p = new TesseractOCRParser();
        p.initialize();
        return p.hasTesseract();
    }


    @Test
    public void testInterwordSpacing() throws Exception {
        assumeTrue(canRun(), "can run OCR");

        //default
        String xml = getXML("testOCR_spacing.png", getMetadata(MediaType.image("png"))).xml;
        assertContains("The quick", xml);

        TesseractOCRConfig tesseractOCRConfigconfig = new TesseractOCRConfig();
        tesseractOCRConfigconfig.setPreserveInterwordSpacing(true);
        ParseContext parseContext = new ParseContext();
        parseContext.set(TesseractOCRConfig.class, tesseractOCRConfigconfig);

        //with preserve interwordspacing "on"
        //allow some flexibility in case Tesseract is computing spaces
        //somewhat differently in different versions/OS's, etc.
        xml = getXML("testOCR_spacing.png", getMetadata(MediaType.image("png")), parseContext).xml;
        Matcher m = Pattern.compile("The\\s{5,20}quick").matcher(xml);
        assertTrue(m.find());
    }


    // detection routes the image to the image parser, which finds Tesseract as its enricher
    private Metadata getMetadata(MediaType mediaType) {
        return new Metadata();
    }

    @Test
    public void confirmMultiPageTiffHandling() throws Exception {
        assumeTrue(canRun(), "can run OCR");
        //tesseract should handle multipage tiffs by itself
        //let's confirm that
        String xml = getXML("testTIFF_multipage.tif", getMetadata(MediaType.image("tiff"))).xml;
        //TIKA-4043 -- on some OS/versions of tesseract Page?2 is extracted
        xml = xml.replaceAll("[^A-Za-z0-9]", " ");
        assertContains("Page 2", xml);
    }

    @Test
    public void confirmRuntimeSkipOCR() throws Exception {
        assumeTrue(canRun(), "can run OCR");
        TesseractOCRConfig config = new TesseractOCRConfig();
        config.setSkipOcr(true);
        ParseContext context = new ParseContext();
        context.set(TesseractOCRConfig.class, config);
        String xml =
                getXML("testTIFF_multipage.tif", getMetadata(MediaType.image("tiff")), context).xml;
        //TIKA-4043 -- on some OS/versions of tesseract Page?2 is extracted
        xml = xml.replaceAll("[^A-Za-z0-9]", " ");
        assertNotContained("Page 2", xml);
    }

    @Test
    public void testPositiveRotateOCR() throws Exception {
        assumeTrue(canRun());
        TesseractOCRParser p = new TesseractOCRParser();
        assumeTrue(p.hasImageMagick());
        TesseractOCRConfig config = new TesseractOCRConfig();
        config.setApplyRotation(true);
        config.setResize(100);
        config.setEnableImagePreprocessing(true);

        ParseContext parseContext = new ParseContext();
        parseContext.set(TesseractOCRConfig.class, config);
        Metadata metadata = getMetadata(MediaType.image("png"));
        String ocr = getText("testRotated+10.png", p, metadata, parseContext);
        assertEquals("true", metadata.get(TesseractOCRParser.IMAGE_MAGICK));
        assertEquals(10.0, Double.parseDouble(metadata.get(TesseractOCRParser.IMAGE_ROTATION)),
                0.01);
        assertContains("Its had resolving otherwise she contented therefore", ocr);
    }

    @Test
    public void testNegativeRotateOCR() throws Exception {
        TesseractOCRParser p = new TesseractOCRParser();
        assumeTrue(p.hasImageMagick());
        TesseractOCRConfig config = new TesseractOCRConfig();
        config.setApplyRotation(true);
        config.setResize(100);
        ParseContext parseContext = new ParseContext();
        parseContext.set(TesseractOCRConfig.class, config);
        assumeTrue(canRun());
        Metadata metadata = getMetadata(MediaType.image("png"));
        String ocr = getText("testRotated-10.png", p, metadata, parseContext);
        assertEquals("true", metadata.get(TesseractOCRParser.IMAGE_MAGICK));
        assertEquals(-10.0, Double.parseDouble(metadata.get(TesseractOCRParser.IMAGE_ROTATION)),
                0.01);
        assertContains("Its had resolving otherwise she contented therefore", ocr);
    }

    @Test
    public void testConfig() throws Exception {
        TikaLoader loader = TikaLoader.load(
                getConfigPath(TesseractOCRParserTest.class, "tika-config-tesseract-full.json"));
        Parser p = loader.loadParsers();
        Parser tesseractOCRParser =
                findParser(p, org.apache.tika.parser.ocr.TesseractOCRParser.class);
        assertNotNull(tesseractOCRParser);

        TesseractOCRConfig tesseractOCRConfig =
                ((TesseractOCRParser) tesseractOCRParser).getDefaultConfig();
        assertEquals(240_000, tesseractOCRConfig.getTimeoutMillis());
        assertEquals(TesseractOCRConfig.OUTPUT_TYPE.HOCR,
                tesseractOCRConfig.getOutputType());
        assertEquals("ceb", tesseractOCRConfig.getLanguage());
        assertEquals(true, tesseractOCRConfig.isApplyRotation());
    }


    @Test
    public void testTimeoutOverride() throws Exception {
        assumeTrue(canRun(), "can run OCR");

        try {
            Parser p = TikaLoader.load(
                    getConfigPath(TesseractOCRParserTest.class, "TIKA-3582-tesseract.json"))
                    .loadAutoDetectParser();
            Metadata m = new Metadata();
            ParseContext parseContext = new ParseContext();
            parseContext.set(TimeoutLimits.class, new TimeoutLimits(50, 50));
            getXML("testRotated+10.png", p, m, parseContext);
            fail("should have thrown a timeout");
        } catch (TikaException e) {
            assertContains("timeout", e.getMessage());
        }
    }

    @Test
    public void testPSM0() throws Exception {
        assumeTrue(canRun(), "can run OCR");
        //this test may be too brittle...e.g. with different versions of tesseract installed
        Parser p = TikaLoader.load(
                getConfigPath(TesseractOCRParserTest.class, "tika-config-psm0.json"))
                .loadAutoDetectParser();
        Metadata m = new Metadata();
        getXML("testRotated+10.png", p, m);
        assertEquals(0, m.getInt(TesseractOCRParser.PSM0_PAGE_NUMBER));
        assertEquals(180, m.getInt(TesseractOCRParser.PSM0_ORIENTATION));
        assertEquals(180, m.getInt(TesseractOCRParser.PSM0_ROTATE));
        assertEquals(5.71,
                Double.parseDouble(m.get(TesseractOCRParser.PSM0_ORIENTATION_CONFIDENCE)), 0.1);
        assertEquals(0.83,
                Double.parseDouble(m.get(TesseractOCRParser.PSM0_SCRIPT_CONFIDENCE)),
                0.1);
        assertEquals("Latin", m.get(TesseractOCRParser.PSM0_SCRIPT));
    }

    @Test
    public void testPreloadLangs() throws Exception {
        assumeTrue(canRun());
        TikaLoader loader = TikaLoader.load(
                getConfigPath(TesseractOCRParserTest.class, "tika-config-tesseract-load-langs.json"));
        Parser p = loader.loadParsers();
        Parser tesseractOCRParser =
                findParser(p, org.apache.tika.parser.ocr.TesseractOCRParser.class);
        assertNotNull(tesseractOCRParser);
        Set<String> langs = ((TesseractOCRParser) tesseractOCRParser).getLangs();
        assertTrue(langs.size() > 0);

        TesseractOCRConfig tesseractOCRConfig = new TesseractOCRConfig();
        tesseractOCRConfig.setLanguage("zzz");
        ParseContext parseContext = new ParseContext();
        parseContext.set(TesseractOCRConfig.class, tesseractOCRConfig);
        try {
            getRecursiveMetadata("testOCR_spacing.png", loader.loadAutoDetectParser(),
                    getMetadata(MediaType.image("png")), parseContext, false);
            fail("should have thrown exception");
        } catch (TikaException e) {
            //expected
        }

    }

    @Test
    public void testArbitraryParams() throws Exception {
        TikaLoader loader = TikaLoader.load(
                getConfigPath(TesseractOCRParserTest.class, "tika-config-tesseract-arbitrary.json"));
        Parser p = loader.loadParsers();
        Parser tesseractOCRParser =
                findParser(p, org.apache.tika.parser.ocr.TesseractOCRParser.class);
        assertNotNull(tesseractOCRParser);
        TesseractOCRConfig tesseractOCRConfig =
                ((TesseractOCRParser) tesseractOCRParser).getDefaultConfig();
        assertEquals("0.75",
                tesseractOCRConfig.getOtherTesseractConfig().get("textord_initialx_ile"));

        assertEquals("0.15625",
                tesseractOCRConfig.getOtherTesseractConfig().get("textord_noise_hfract"));
    }


    @Test
    public void testThreadJoinInLoadingLangs() throws Exception {
        assumeTrue(canRun());
        //make sure that the stream is fully read and
        //we're getting the same answers on several iterations
        Set<String> langs = getLangs();
        assumeTrue(langs.size() > 0);
        for (int i = 0; i < 20; i++) {
            assertEquals(langs, getLangs());
        }
    }

    private Set<String> getLangs() throws Exception {
        TesseractOCRParser p = new TesseractOCRParser();
        p.getDefaultConfig().setPreloadLangs(true);
        p.initialize();
        return p.getLangs();
    }
}
