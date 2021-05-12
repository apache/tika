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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import java.io.File;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.Assert;
import org.junit.Test;

import org.apache.tika.TikaTest;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.image.BPGParser;
import org.apache.tika.parser.image.HeifParser;
import org.apache.tika.parser.image.ICNSParser;
import org.apache.tika.parser.image.ImageParser;
import org.apache.tika.parser.image.JpegParser;
import org.apache.tika.parser.image.PSDParser;
import org.apache.tika.parser.image.TiffParser;
import org.apache.tika.parser.image.WebPParser;

public class TesseractOCRParserTest extends TikaTest {

    public static boolean canRun() throws TikaConfigException {
        TesseractOCRParser p = new TesseractOCRParser();
        p.initialize(Collections.EMPTY_MAP);
        return p.hasTesseract();
    }


    @Test
    public void testInterwordSpacing() throws Exception {
        assumeTrue("can run OCR", canRun());

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


    private Metadata getMetadata(MediaType mediaType) {
        Metadata metadata = new Metadata();
        MediaType ocrMediaType =
                new MediaType(mediaType.getType(), "OCR-" + mediaType.getSubtype());
        metadata.set(TikaCoreProperties.CONTENT_TYPE_PARSER_OVERRIDE, ocrMediaType.toString());
        return metadata;
    }

    private MediaType deOCR(MediaType mediaType) {
        String subtype = mediaType.getSubtype();
        if (subtype.startsWith("ocr-")) {
            subtype = subtype.substring(4);
        }
        return new MediaType(mediaType.getType(), subtype);
    }

    @Test
    public void confirmMultiPageTiffHandling() throws Exception {
        assumeTrue("can run OCR", canRun());
        //tesseract should handle multipage tiffs by itself
        //let's confirm that
        String xml = getXML("testTIFF_multipage.tif", getMetadata(MediaType.image("tiff"))).xml;
        assertContains("Page 2", xml);
    }

    @Test
    public void confirmRuntimeSkipOCR() throws Exception {
        assumeTrue("can run OCR", canRun());
        TesseractOCRConfig config = new TesseractOCRConfig();
        config.setSkipOcr(true);
        ParseContext context = new ParseContext();
        context.set(TesseractOCRConfig.class, config);
        String xml =
                getXML("testTIFF_multipage.tif", getMetadata(MediaType.image("tiff")), context).xml;
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
        ParseContext parseContext = new ParseContext();
        parseContext.set(TesseractOCRConfig.class, config);
        Metadata metadata = getMetadata(MediaType.image("png"));
        String ocr = getText("testRotated+10.png", metadata, parseContext);
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
        String ocr = getText("testRotated-10.png", metadata, parseContext);
        assertEquals("true", metadata.get(TesseractOCRParser.IMAGE_MAGICK));
        assertEquals(-10.0, Double.parseDouble(metadata.get(TesseractOCRParser.IMAGE_ROTATION)),
                0.01);
        assertContains("Its had resolving otherwise she contented therefore", ocr);
    }

    @Test
    public void testConfig() throws Exception {
        try (InputStream is = getResourceAsStream("/test-configs/TIKA-2705-tesseract.xml")) {
            TikaConfig config = new TikaConfig(is);
            Parser p = config.getParser();
            Parser tesseractOCRParser =
                    findParser(p, org.apache.tika.parser.ocr.TesseractOCRParser.class);
            assertNotNull(tesseractOCRParser);

            TesseractOCRConfig tesseractOCRConfig =
                    ((TesseractOCRParser) tesseractOCRParser).getDefaultConfig();
            Assert.assertEquals(241, tesseractOCRConfig.getTimeoutSeconds());
            Assert.assertEquals(TesseractOCRConfig.OUTPUT_TYPE.HOCR,
                    tesseractOCRConfig.getOutputType());
            Assert.assertEquals("ceb", tesseractOCRConfig.getLanguage());
            Assert.assertEquals(false, tesseractOCRConfig.isApplyRotation());
//            assertContains("myspecial", tesseractOCRConfig.getTesseractPath());
        }
    }

    @Test
    public void testPreloadLangs() throws Exception {
        assumeTrue(canRun());
        TikaConfig config;
        try (InputStream is = getResourceAsStream(
                "/test-configs/tika-config-tesseract-load-langs.xml")) {
            config = new TikaConfig(is);
        }
        Parser p = config.getParser();
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
            getRecursiveMetadata("testOCR_spacing.png", new AutoDetectParser(config),
                    getMetadata(MediaType.image("png")), parseContext, false);
            fail("should have thrown exception");
        } catch (TikaException e) {
            //expected
        }

    }

    @Test
    public void testArbitraryParams() throws Exception {
        try (InputStream is = getResourceAsStream(
                "/test-configs/tika-config-tesseract-arbitrary.xml")) {
            TikaConfig config = new TikaConfig(is);
            Parser p = config.getParser();
            Parser tesseractOCRParser =
                    findParser(p, org.apache.tika.parser.ocr.TesseractOCRParser.class);
            assertNotNull(tesseractOCRParser);
            TesseractOCRConfig tesseractOCRConfig =
                    ((TesseractOCRParser) tesseractOCRParser).getDefaultConfig();
            Assert.assertEquals("0.75",
                    tesseractOCRConfig.getOtherTesseractConfig().get("textord_initialx_ile"));

            Assert.assertEquals("0.15625",
                    tesseractOCRConfig.getOtherTesseractConfig().get("textord_noise_hfract"));
        }
    }


    //to be used to figure out a) what image media types don't have ocr coverage and
    // b) what ocr media types don't have dedicated image parsers
    //this obv requires that tesseract be installed
    //TODO: convert to actual unit test
    //@Test
    public void showCoverage() throws Exception {
        Set<MediaType> imageParserMimes = new HashSet<>();
        for (Parser p : new Parser[]{new BPGParser(), new HeifParser(), new ICNSParser(),
                new ImageParser(), new JpegParser(), new PSDParser(), new TiffParser(),
                new WebPParser(),}) {
            imageParserMimes.addAll(p.getSupportedTypes(new ParseContext()));
        }
        //mime types that Tesseract will cover if there is no existing parser
        //that in turn will call tesseract .. e.g. the mime subtype doesn't start
        //with ocr-
        Set<MediaType> literalTesseractMimes = new HashSet<>();

        //mimes whose subtimes start with ocr-
        Set<MediaType> ocrTesseractMimes = new HashSet<>();
        for (MediaType mt : new TesseractOCRParser().getSupportedTypes(new ParseContext())) {
            if (mt.getSubtype().startsWith("ocr-")) {
                ocrTesseractMimes.add(deOCR(mt));
            } else {
                literalTesseractMimes.add(mt);
            }
        }

        for (MediaType mt : imageParserMimes) {
            if (!ocrTesseractMimes.contains(mt)) {
                System.out.println("tesseract isn't currently configured to handle: " + mt);
            }
        }

        for (MediaType mt : literalTesseractMimes) {
            System.out.println("We don't have dedicated image parsers " +
                    "for these formats, which are handled by tesseract: " + mt);
        }
    }

    @Test
    public void testTrailingSlashInPathBehavior() {

        TesseractOCRParser parser = new TesseractOCRParser();
        parser.setTesseractPath("blah");
        assertEquals("blah" + File.separator, parser.getTesseractPath());
        parser.setTesseractPath("blah" + File.separator);
        assertEquals("blah" + File.separator, parser.getTesseractPath());
        parser.setTesseractPath("");
        assertEquals("", parser.getTesseractPath());

        parser.setTessdataPath("blahdata");
        assertEquals("blahdata" + File.separator, parser.getTessdataPath());
        parser.setTessdataPath("blahdata" + File.separator);
        assertEquals("blahdata" + File.separator, parser.getTessdataPath());
        parser.setTessdataPath("");
        assertEquals("", parser.getTessdataPath());

        parser.setImageMagickPath("imagemagickpath");
        assertEquals("imagemagickpath" + File.separator, parser.getImageMagickPath());
        parser.setImageMagickPath("imagemagickpath" + File.separator);
        assertEquals("imagemagickpath" + File.separator, parser.getImageMagickPath());
        parser.setImageMagickPath("");
        assertEquals("", parser.getImageMagickPath());
    }

    @Test
    public void testBogusPathCheck() {
        //allow path that doesn't actually exist
        TesseractOCRParser parser = new TesseractOCRParser();
        parser.setTesseractPath("blahdeblahblah");
        assertEquals("blahdeblahblah" + File.separator, parser.getTesseractPath());
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
        p.setPreloadLangs(true);
        p.initialize(Collections.EMPTY_MAP);
        return p.getLangs();
    }
}
