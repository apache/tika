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

import org.apache.tika.TikaTest;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.external.ExternalParser;
import org.apache.tika.parser.image.BPGParser;
import org.apache.tika.parser.image.HeifParser;
import org.apache.tika.parser.image.ICNSParser;
import org.apache.tika.parser.image.ImageParser;
import org.apache.tika.parser.image.JpegParser;
import org.apache.tika.parser.image.PSDParser;
import org.apache.tika.parser.image.TiffParser;
import org.apache.tika.parser.image.WebPParser;
import org.junit.Assert;
import org.junit.Test;

import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

public class TesseractOCRParserTest extends TikaTest {

    public static boolean canRun() {
        TesseractOCRConfig config = new TesseractOCRConfig();
        TesseractOCRParserTest tesseractOCRTest = new TesseractOCRParserTest();
        return tesseractOCRTest.canRun(config);
    }

    private boolean canRun(TesseractOCRConfig config) {
        String[] checkCmd = {config.getTesseractPath() + TesseractOCRParser.getTesseractProg()};
        // If Tesseract is not on the path, do not run the test.
        return ExternalParser.check(checkCmd);
    }

    @Test
    public void testImageMagick() throws Exception {
        //TODO -- figure out what the original intention was for this test or remove it.
        TesseractOCRConfig config = new TesseractOCRConfig();
        assumeTrue(TesseractOCRParser.IMAGE_PREPROCESSOR.hasImageMagick(config));
        String[] CheckCmd = {config.getImageMagickPath() + TesseractOCRParser.IMAGE_PREPROCESSOR.getImageMagickProg()};
        assertTrue(ExternalParser.check(CheckCmd));
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
        xml = getXML("testOCR_spacing.png",
                getMetadata(MediaType.image("png")),
                        parseContext).xml;
        Matcher m = Pattern.compile("The\\s{5,20}quick").matcher(xml);
        assertTrue(m.find());
    }

    private Metadata getMetadata(MediaType mediaType) {
        Metadata metadata = new Metadata();
        MediaType ocrMediaType = new MediaType(mediaType.getType(),
                "OCR-"+mediaType.getSubtype());
        metadata.set(TikaCoreProperties.CONTENT_TYPE_OVERRIDE,
                ocrMediaType.toString());
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
        String xml = getXML("testTIFF_multipage.tif",
                getMetadata(MediaType.image("tiff"))).xml;
        assertContains("Page 2", xml);
    }

    @Test
    public void testPositiveRotateOCR() throws Exception {
        TesseractOCRConfig config = new TesseractOCRConfig();
        assumeTrue(TesseractOCRParser.IMAGE_PREPROCESSOR.hasImageMagick(config));
        config.setApplyRotation(true);
        config.setResize(100);
        ParseContext parseContext = new ParseContext();
        parseContext.set(TesseractOCRConfig.class, config);
        assumeTrue(canRun(config));
        Metadata metadata = getMetadata(MediaType.image("png"));
        String ocr = getText("testRotated+10.png", metadata, parseContext);
        assertEquals("true", metadata.get(TesseractOCRParser.IMAGE_MAGICK));
        assertEquals(10.0,
                Double.parseDouble(metadata.get(TesseractOCRParser.IMAGE_ROTATION)), 0.01);
        assertContains("Its had resolving otherwise she contented therefore", ocr);
    }

    @Test
    public void testNegativeRotateOCR() throws Exception {
        TesseractOCRConfig config = new TesseractOCRConfig();
        assumeTrue(TesseractOCRParser.IMAGE_PREPROCESSOR.hasImageMagick(config));
        config.setApplyRotation(true);
        config.setResize(100);
        ParseContext parseContext = new ParseContext();
        parseContext.set(TesseractOCRConfig.class, config);
        assumeTrue(canRun(config));
        Metadata metadata = getMetadata(MediaType.image("png"));
        String ocr = getText("testRotated-10.png", metadata, parseContext);
        assertEquals("true", metadata.get(TesseractOCRParser.IMAGE_MAGICK));
        assertEquals(-10.0,
                Double.parseDouble(metadata.get(TesseractOCRParser.IMAGE_ROTATION)), 0.01);
        assertContains("Its had resolving otherwise she contented therefore", ocr);
    }

    @Test
    public void testConfig() throws Exception {
        try (InputStream is = getResourceAsStream("/org/apache/tika/config/TIKA-2705-tesseract.xml")) {
            TikaConfig config = new TikaConfig(is);
            Parser p = config.getParser();
            Parser tesseractOCRParser = findParser(p, org.apache.tika.parser.ocr.TesseractOCRParser.class);
            assertNotNull(tesseractOCRParser);

            TesseractOCRConfig tesseractOCRConfig = ((TesseractOCRParser)tesseractOCRParser).getDefaultConfig();
            Assert.assertEquals(241, tesseractOCRConfig.getTimeout());
            Assert.assertEquals(TesseractOCRConfig.OUTPUT_TYPE.HOCR, tesseractOCRConfig.getOutputType());
            Assert.assertEquals("ceb", tesseractOCRConfig.getLanguage());
            Assert.assertEquals(false, tesseractOCRConfig.isApplyRotation());
            assertContains("myspecial", tesseractOCRConfig.getTesseractPath());
        }
    }

    //to be used to figure out a) what image media types don't have ocr coverage and
    // b) what ocr media types don't have dedicated image parsers
    //this obv requires that tesseract be installed
    //TODO: convert to actual unit test
    //@Test
    public void showCoverage() throws Exception {
        Set<MediaType> imageParserMimes = new HashSet<>();
        for (Parser p : new Parser[] {
                new BPGParser(),
                new HeifParser(),
                new ICNSParser(),
                new ImageParser(),
                new JpegParser(),
                new PSDParser(),
                new TiffParser(),
                new WebPParser(),
        }) {
            for (MediaType mt : p.getSupportedTypes(new ParseContext())) {
                imageParserMimes.add(mt);
            }
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
            if (! ocrTesseractMimes.contains(mt)) {
                System.out.println("tesseract isn't currently configured to handle: "+mt);
            }
        }

        for (MediaType mt : literalTesseractMimes) {
            System.out.println("We don't have dedicated image parsers " +
                    "for these formats, which are handled by tesseract: "+mt);
        }
    }

}
