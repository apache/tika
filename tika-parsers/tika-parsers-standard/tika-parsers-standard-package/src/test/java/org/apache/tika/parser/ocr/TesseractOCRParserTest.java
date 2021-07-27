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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import org.apache.tika.TikaTest;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.DefaultParser;
import org.apache.tika.parser.ParseContext;
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
    public void testPDFOCR() throws Exception {
        String resource = "testOCR.pdf";
        String[] nonOCRContains = new String[0];
        testBasicOCR(resource, nonOCRContains, 2);
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
        assertEquals("deflate", metadataList.get(1).get("Compression CompressionTypeName"));

        return contents.toString();
    }

    @Test
    public void testSingleImage() throws Exception {
        assumeTrue(canRun(), "can run OCR");
        String xml = getXML("testOCR.jpg").xml;
        assertContains("OCR Testing", xml);
        //test metadata extraction
        assertContains("<meta name=\"Image Width\" content=\"136 pixels\" />", xml);

        //TIKA-2169
        assertContainsCount("<html", xml, 1);
        assertContainsCount("<title", xml, 1);
        assertContainsCount("</title", xml, 1);
        assertContainsCount("<body", xml, 1);
        assertContainsCount("</body", xml, 1);
        assertContainsCount("</html", xml, 1);

        assertNotContained("<meta name=\"Content-Type\" content=\"image/ocr-jpeg\" />", xml);
    }


    @Test
    public void getNormalMetadataToo() throws Exception {
        //this should be successful whether or not TesseractOCR is installed/active
        //If tesseract is installed, the internal metadata extraction parser should
        //work; and if tesseract isn't installed, the regular parsers should take over.

        //gif
        Metadata m = getXML("testGIF.gif").metadata;
        assertTrue(m.names().length > 20);
        assertEquals("RGB", m.get("Chroma ColorSpaceType"));

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
        assertEquals("UnsignedIntegral", m.get("Data SampleFormat"));

        //tiff
        m = getXML("testTIFF.tif").metadata;
        assertEquals("100", m.get(Metadata.IMAGE_WIDTH));
        assertEquals("75", m.get(Metadata.IMAGE_LENGTH));
        assertEquals("72 dots per inch", m.get("Exif IFD0:Y Resolution"));
    }

    //TODO: add unit tests for jp2/jpx/ppm TIKA-2174

}
