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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import org.apache.tika.TikaTest;
import org.apache.tika.config.ParseContextConfig;
import org.apache.tika.config.loader.TikaLoader;
import org.apache.tika.config.loader.TikaObjectMapperFactory;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.PDF;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.DefaultParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.image.ImageMetadataExtractor;
import org.apache.tika.parser.image.ImageParser;
import org.apache.tika.parser.pdf.PDFParserConfig;
import org.apache.tika.sax.BasicContentHandlerFactory;

public class TesseractOCRParserTest extends TikaTest {

    public static boolean canRun() throws TikaConfigException {
        TesseractOCRParser p = new TesseractOCRParser();
        return p.hasTesseract();
    }


    /*
    Check that if Tesseract is told to skip OCR,
    the TesseractOCRParser claims to not support
    any file types. So, the standard image parser is called instead.
     */
    @Test
    public void offersNoTypesIfNotFound() throws Exception {
        TesseractOCRParser parser = new TesseractOCRParser();
        DefaultParser defaultParser = new DefaultParser();
        MediaType png = MediaType.image("png");

        // With an invalid path, will offer no types
        TesseractOCRConfig skipOcrConfig = new TesseractOCRConfig();
        skipOcrConfig.setSkipOcr(true);

        ParseContext parseContext = new ParseContext();
        parseContext.set(TesseractOCRConfig.class, skipOcrConfig);

        // No types offered
        assertEquals(0, parser.getSupportedTypes(parseContext).size());

        // And DefaultParser won't use us
        assertEquals(ImageParser.class, defaultParser.getParsers(parseContext).get(png).getClass());
    }

    @Test
    public void testDefaultPDFOCR() throws Exception {
        List<Metadata> metadataList = getRecursiveMetadata("testOCR.pdf");
        assertEquals(1, metadataList.size());
        assertEquals(1, metadataList.get(0).getInt(PDF.OCR_PAGE_COUNT));
    }

    @Test
    public void testPDFOCR() throws Exception {
        assumeTrue(canRun(), "can run OCR");
        String resource = "testOCR.pdf";
        String[] nonOCRContains = new String[0];
        testBasicOCR(resource, nonOCRContains, 2);
    }

    @Disabled("this requires manually moving the default tessdata directory")
    @Test
    public void testTessdataConfig() throws Exception {
        TikaLoader loader = TikaLoader.load(
                Paths.get(TesseractOCRParserTest.class.getResource("tesseract-config.json").toURI()));
        Parser p = loader.loadAutoDetectParser();
        List<Metadata> metadataList = getRecursiveMetadata("testOCR.pdf", p);
        assertContains("Happy New Year 2003!", metadataList.get(0).get(TikaCoreProperties.TIKA_CONTENT));
    }

    @Test
    public void testDOCXOCR() throws Exception {
        String resource = "testOCR.docx";
        String[] nonOCRContains = {"This is some text.", "Here is an embedded image:"};
        testBasicOCR(resource, nonOCRContains, 3);
    }

    @Test
    public void testPPTXOCR() throws Exception {
        String resource = "testOCR.pptx";
        String[] nonOCRContains = {"This is some text"};
        testBasicOCR(resource, nonOCRContains, 3);
    }

    @Test
    public void testOCROutputsHOCR() throws Exception {
        assumeTrue(canRun(), "can run OCR");

        String resource = "testOCR.pdf";

        String[] nonOCRContains = new String[0];
        String contents =
                runOCR(resource, nonOCRContains, 2, BasicContentHandlerFactory.HANDLER_TYPE.XML,
                        TesseractOCRConfig.OUTPUT_TYPE.HOCR);

        assertContains("<span class=\"ocrx_word\" id=\"word_1_1\"", contents);
        assertContains("Happy</span>", contents);

    }

    @Test
    public void testParserContentTypeOverride() throws Exception {
        assumeTrue(canRun(), "can run OCR");
        //this tests that the content-type is not overwritten by the ocr parser
        // override content type
        List<Metadata> metadata = getRecursiveMetadata("testOCR.pdf", AUTO_DETECT_PARSER,
                BasicContentHandlerFactory.HANDLER_TYPE.XML);
        assertContains("<meta name=\"Content-Type\" content=\"application/pdf\" />",
                metadata.get(0).get(TikaCoreProperties.TIKA_CONTENT));
    }

    private void testBasicOCR(String resource, String[] nonOCRContains, int numMetadatas)
            throws Exception {
        assumeTrue(canRun(), "can run OCR");

        String contents = runOCR(resource, nonOCRContains, numMetadatas,
                BasicContentHandlerFactory.HANDLER_TYPE.TEXT, TesseractOCRConfig.OUTPUT_TYPE.TXT);
        if (canRun()) {
            if (resource.substring(resource.lastIndexOf('.')).equals(".jpg")) {
                assertContains("Apache", contents);
            } else {
                assertContains("Happy New Year 2003!", contents);
            }
        }
    }

    private String runOCR(String resource, String[] nonOCRContains, int numMetadatas,
                          BasicContentHandlerFactory.HANDLER_TYPE handlerType,
                          TesseractOCRConfig.OUTPUT_TYPE outputType) throws Exception {
        TesseractOCRConfig config = new TesseractOCRConfig();
        config.setOutputType(outputType);

        PDFParserConfig pdfConfig = new PDFParserConfig();
        pdfConfig.setExtractInlineImages(true);

        ParseContext parseContext = new ParseContext();
        parseContext.set(TesseractOCRConfig.class, config);
        parseContext.set(PDFParserConfig.class, pdfConfig);

        List<Metadata> metadataList =
                getRecursiveMetadata(resource, AUTO_DETECT_PARSER, handlerType, parseContext);
        assertEquals(numMetadatas, metadataList.size());

        StringBuilder contents = new StringBuilder();
        for (Metadata m : metadataList) {
            contents.append(m.get(TikaCoreProperties.TIKA_CONTENT));
        }

        for (String needle : nonOCRContains) {
            assertContains(needle, contents.toString());
        }
        assertTrue(metadataList.get(0).names().length > 10);
        assertTrue(metadataList.get(1).names().length > 10);
        //test at least one value
        assertEquals("deflate", metadataList.get(1).get("img:Compression CompressionTypeName"));

        //make sure that tesseract is showing up in the full set of "parsed bys"
        assertContains(TesseractOCRParser.class.getName(),
                Arrays.asList(metadataList.get(0).getValues(TikaCoreProperties.TIKA_PARSED_BY_FULL_SET)));

        return contents.toString();
    }

    @Test
    public void testSingleImage() throws Exception {
        assumeTrue(canRun(), "can run OCR");
        String xml = getXML("testOCR.jpg").xml;
        assertContains("OCR Testing", xml);
        //test metadata extraction
        assertContains("<meta name=\"img:Image Width\" content=\"136 pixels\" />", xml);

        //TIKA-2169
        assertContainsCount("<html", xml, 1);
        assertContainsCount("<title", xml, 1);
        assertContainsCount("</title", xml, 1);
        assertContainsCount("<body", xml, 1);
        assertContainsCount("</body", xml, 1);
        assertContainsCount("</html", xml, 1);

        //content type should be either image/jpeg or image/ocr-jpeg depending on
        //how the parser was routed
        assertTrue(xml.contains("content=\"image/jpeg\"") ||
                        xml.contains("content=\"image/ocr-jpeg\""),
                "Expected content type image/jpeg or image/ocr-jpeg in xml");
    }


    @Test
    public void getNormalMetadataToo() throws Exception {
        //this should be successful whether or not TesseractOCR is installed/active
        //If tesseract is installed, the internal metadata extraction parser should
        //work; and if tesseract isn't installed, the regular parsers should take over.

        //gif
        Metadata m = getXML("testGIF.gif").metadata;
        assertTrue(m.names().length > 20);
        assertEquals("RGB", m.get(ImageMetadataExtractor.UNKNOWN_IMG_NS + "Chroma ColorSpaceType"));

        //jpg
        m = getXML("testOCR.jpg").metadata;
        assertEquals("136", m.get(Metadata.IMAGE_WIDTH));
        assertEquals("66", m.get(Metadata.IMAGE_LENGTH));
        assertEquals("8", m.get(Metadata.BITS_PER_SAMPLE));
        assertEquals(null, m.get(Metadata.SAMPLES_PER_PIXEL));
        assertContains("This is a test Apache Tika imag", m.get(TikaCoreProperties.COMMENTS));

        //bmp
        m = getXML("testBMP.bmp").metadata;
        assertEquals("100", m.get(Metadata.IMAGE_WIDTH));
        assertEquals("75", m.get(Metadata.IMAGE_LENGTH));

        //png
        m = getXML("testPNG.png").metadata;
        assertEquals("100", m.get(Metadata.IMAGE_WIDTH));
        assertEquals("75", m.get(Metadata.IMAGE_LENGTH));
        assertEquals("UnsignedIntegral", m.get(ImageMetadataExtractor.UNKNOWN_IMG_NS + "Data SampleFormat"));

        //tiff
        m = getXML("testTIFF.tif").metadata;
        assertEquals("100", m.get(Metadata.IMAGE_WIDTH));
        assertEquals("75", m.get(Metadata.IMAGE_LENGTH));
        assertEquals("72 dots per inch", m.get(ImageMetadataExtractor.UNKNOWN_IMG_NS + "Exif IFD0:Y Resolution"));
    }

    @Test
    public void testInlining() throws Exception {
        assumeTrue(canRun(), "can run OCR");
        TesseractOCRConfig config = new TesseractOCRConfig();
        config.setInlineContent(true);
        ParseContext context = new ParseContext();
        context.set(TesseractOCRConfig.class, config);
        List<Metadata> metadataList = getRecursiveMetadata("testOCR.pptx", context);

        //0 is main doc, 1 is embedded image, 2 is thumbnail
        assertEquals(3, metadataList.size());
        assertContains("This is some text", metadataList.get(0).get(TikaCoreProperties.TIKA_CONTENT));
        assertNotContained("This is some text", metadataList.get(1).get(TikaCoreProperties.TIKA_CONTENT));
        assertNotContained("This is some text", metadataList.get(2).get(TikaCoreProperties.TIKA_CONTENT));

        assertContains("Happy New Year 2003", metadataList.get(0).get(TikaCoreProperties.TIKA_CONTENT));
        assertContains("Happy New Year 2003", metadataList.get(1).get(TikaCoreProperties.TIKA_CONTENT));
    }
    //TODO: add unit tests for jp2/jpx/ppm TIKA-2174

    @Test
    public void testUpdatingConfigs() throws Exception {
        ObjectMapper mapper = TikaObjectMapperFactory.getMapper();

        // Create default config (simulating parser initialization)
        TesseractOCRConfig defaultConfig = new TesseractOCRConfig();
        defaultConfig.setLanguage("eng");
        defaultConfig.setMinFileSizeToOcr(100);
        defaultConfig.setOutputType(TesseractOCRConfig.OUTPUT_TYPE.TXT);
        defaultConfig.addOtherTesseractConfig("k1", "a1");
        defaultConfig.addOtherTesseractConfig("k2", "a2");

        // Create runtime config updates (simulating per-request config)
        Map<String, Object> runtimeUpdates = new HashMap<>();
        runtimeUpdates.put("language", "fra");
        runtimeUpdates.put("minFileSizeToOcr", 1000);
        runtimeUpdates.put("outputType", "HOCR");
        Map<String, String> otherConfig = new HashMap<>();
        otherConfig.put("k1", "b1");
        otherConfig.put("k2", "b2");
        runtimeUpdates.put("otherTesseractConfig", otherConfig);

        // Store runtime config in ParseContext
        ParseContext context = new ParseContext();
        context.setJsonConfig("tesseract-ocr-parser", mapper.writeValueAsString(runtimeUpdates));

        // Merge configs using ParseContextConfig
        TesseractOCRConfig mergedConfig = ParseContextConfig.getConfig(
                context, "tesseract-ocr-parser", TesseractOCRConfig.class, defaultConfig);

        // Verify merged config has runtime overrides
        assertEquals("fra", mergedConfig.getLanguage());
        assertEquals(1000, mergedConfig.getMinFileSizeToOcr());
        assertEquals(TesseractOCRConfig.OUTPUT_TYPE.HOCR, mergedConfig.getOutputType());
        assertEquals("b1", mergedConfig.getOtherTesseractConfig().get("k1"));
        assertEquals("b2", mergedConfig.getOtherTesseractConfig().get("k2"));

        // Verify default config was NOT modified (immutability)
        assertEquals("eng", defaultConfig.getLanguage());
        assertEquals(100, defaultConfig.getMinFileSizeToOcr());
        assertEquals(TesseractOCRConfig.OUTPUT_TYPE.TXT, defaultConfig.getOutputType());
        assertEquals("a1", defaultConfig.getOtherTesseractConfig().get("k1"));
        assertEquals("a2", defaultConfig.getOtherTesseractConfig().get("k2"));
    }

    @Test
    public void testRuntimeConfigPathValidation() throws Exception {
        ObjectMapper mapper = new ObjectMapper();

        // Test that setting tesseractPath at runtime throws exception
        Map<String, Object> configWithTesseractPath = new HashMap<>();
        configWithTesseractPath.put("tesseractPath", "/some/path");

        ParseContext context = new ParseContext();
        context.setJsonConfig("tesseract-ocr-parser", mapper.writeValueAsString(configWithTesseractPath));

        IOException exception = assertThrows(IOException.class, () -> {
            ParseContextConfig.getConfig(
                    context,
                    "tesseract-ocr-parser",
                    TesseractOCRConfig.RuntimeConfig.class,
                    new TesseractOCRConfig.RuntimeConfig());
        });
        assertTrue(exception.getMessage().contains("Cannot modify tesseractPath at runtime"));

        // Test that setting tessdataPath at runtime throws exception
        Map<String, Object> configWithTessdataPath = new HashMap<>();
        configWithTessdataPath.put("tessdataPath", "/some/path");

        context.setJsonConfig("tesseract-ocr-parser", mapper.writeValueAsString(configWithTessdataPath));

        exception = assertThrows(IOException.class, () -> {
            ParseContextConfig.getConfig(
                    context,
                    "tesseract-ocr-parser",
                    TesseractOCRConfig.RuntimeConfig.class,
                    new TesseractOCRConfig.RuntimeConfig());
        });
        assertTrue(exception.getMessage().contains("Cannot modify tessdataPath at runtime"));

        // Test that setting imageMagickPath at runtime throws exception
        Map<String, Object> configWithImageMagickPath = new HashMap<>();
        configWithImageMagickPath.put("imageMagickPath", "/some/path");

        context.setJsonConfig("tesseract-ocr-parser", mapper.writeValueAsString(configWithImageMagickPath));

        exception = assertThrows(IOException.class, () -> {
            ParseContextConfig.getConfig(
                    context,
                    "tesseract-ocr-parser",
                    TesseractOCRConfig.RuntimeConfig.class,
                    new TesseractOCRConfig.RuntimeConfig());
        });
        assertTrue(exception.getMessage().contains("Cannot modify imageMagickPath at runtime"));

        // Test that setting non-path fields works fine
        Map<String, Object> validRuntimeConfig = new HashMap<>();
        validRuntimeConfig.put("language", "fra");
        validRuntimeConfig.put("skipOcr", true);

        context.setJsonConfig("tesseract-ocr-parser", mapper.writeValueAsString(validRuntimeConfig));

        // This should not throw
        TesseractOCRConfig.RuntimeConfig runtimeConfig = ParseContextConfig.getConfig(
                context,
                "tesseract-ocr-parser",
                TesseractOCRConfig.RuntimeConfig.class,
                new TesseractOCRConfig.RuntimeConfig());
        assertEquals("fra", runtimeConfig.getLanguage());
        assertTrue(runtimeConfig.isSkipOcr());
    }
}
