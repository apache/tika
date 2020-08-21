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
import static org.junit.Assume.assumeTrue;

import java.io.InputStream;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.tika.TikaTest;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.DefaultParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.external.ExternalParser;
import org.apache.tika.parser.image.ImageParser;
import org.apache.tika.parser.pdf.PDFParserConfig;
import org.apache.tika.sax.AbstractRecursiveParserWrapperHandler;
import org.apache.tika.sax.BasicContentHandlerFactory;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

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

    /*
    Check that if Tesseract is not found, the TesseractOCRParser claims to not support
    any file types. So, the standard image parser is called instead.
     */
    @Test
    public void offersNoTypesIfNotFound() throws Exception {
        TesseractOCRParser parser = new TesseractOCRParser();
        DefaultParser defaultParser = new DefaultParser();
        MediaType png = MediaType.image("png");

        // With an invalid path, will offer no types
        TesseractOCRConfig invalidConfig = new TesseractOCRConfig();
        invalidConfig.setTesseractPath("/made/up/path");

        ParseContext parseContext = new ParseContext();
        parseContext.set(TesseractOCRConfig.class, invalidConfig);

        // No types offered
        Assert.assertEquals(0, parser.getSupportedTypes(parseContext).size());

        // And DefaultParser won't use us
        assertEquals(ImageParser.class, defaultParser.getParsers(parseContext).get(png).getClass());
    }

    /*
    If Tesseract is found, test we retrieve the proper number of supporting Parsers.
     */
    @Test
    public void offersTypesIfFound() throws Exception {
        TesseractOCRParser parser = new TesseractOCRParser();
        DefaultParser defaultParser = new DefaultParser();

        ParseContext parseContext = new ParseContext();
        MediaType png = MediaType.image("png");

        // Assuming that Tesseract is on the path, we should find 5 Parsers that support PNG.
        assumeTrue("can run OCR", canRun());

        Assert.assertEquals(8, parser.getSupportedTypes(parseContext).size());
        assertTrue(parser.getSupportedTypes(parseContext).contains(png));

        // DefaultParser will now select the TesseractOCRParser.
        assertEquals(TesseractOCRParser.class, defaultParser.getParsers(parseContext).get(png).getClass());
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
        String[] nonOCRContains = {
                "This is some text.",
                "Here is an embedded image:"
        };
        testBasicOCR(resource, nonOCRContains, 3);
    }

    @Test
    public void testPPTXOCR() throws Exception {
        String resource = "testOCR.pptx";
        String[] nonOCRContains = {
                "This is some text"
        };
        testBasicOCR(resource, nonOCRContains, 3);
    }
    
    @Test
    public void testOCROutputsHOCR() throws Exception {
        assumeTrue("can run OCR", canRun());

        String resource = "testOCR.pdf";

        String[] nonOCRContains = new String[0];
        String contents = runOCR(resource, nonOCRContains, 2,
                BasicContentHandlerFactory.HANDLER_TYPE.XML,
                TesseractOCRConfig.OUTPUT_TYPE.HOCR);

        assertContains("<span class=\"ocrx_word\" id=\"word_1_1\"", contents);
        assertContains("Happy</span>", contents);

    }

    private void testBasicOCR(String resource, String[] nonOCRContains, int numMetadatas) throws Exception{
        Assume.assumeTrue("can run OCR", canRun());

        String contents = runOCR(resource, nonOCRContains, numMetadatas,
                BasicContentHandlerFactory.HANDLER_TYPE.TEXT, TesseractOCRConfig.OUTPUT_TYPE.TXT);
        if (canRun()) {
        	if(resource.substring(resource.lastIndexOf('.'), resource.length()).equals(".jpg")) {
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

        List<Metadata> metadataList = getRecursiveMetadata(resource,
                AUTO_DETECT_PARSER, handlerType, parseContext);

        assertEquals(numMetadatas, metadataList.size());

        StringBuilder contents = new StringBuilder();
        for (Metadata m : metadataList) {
            contents.append(m.get(AbstractRecursiveParserWrapperHandler.TIKA_CONTENT));
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
        Assume.assumeTrue("can run OCR", canRun());

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
    }

    @Test
    public void testImageMagick() throws Exception {
    	InputStream stream = TesseractOCRConfig.class.getResourceAsStream(
                "/test-properties/TesseractOCR.properties");
    	TesseractOCRConfig config = new TesseractOCRConfig(stream);
    	String[] CheckCmd = {config.getImageMagickPath() + TesseractOCRParser.getImageMagickProg()};
    	assumeTrue(ExternalParser.check(CheckCmd));
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

    @Test
    public void testInterwordSpacing() throws Exception {
        assumeTrue("can run OCR", canRun());
        //default
        String xml = getXML("testOCR_spacing.png").xml;
        assertContains("The quick", xml);

        TesseractOCRConfig tesseractOCRConfigconfig = new TesseractOCRConfig();
        tesseractOCRConfigconfig.setPreserveInterwordSpacing(true);
        ParseContext parseContext = new ParseContext();
        parseContext.set(TesseractOCRConfig.class, tesseractOCRConfigconfig);

        //with preserve interwordspacing "on"
        //allow some flexibility in case Tesseract is computing spaces
        //somewhat differently in different versions/OS's, etc.
        xml = getXML("testOCR_spacing.png", parseContext).xml;
        Matcher m = Pattern.compile("The\\s{5,20}quick").matcher(xml);
        assertTrue(m.find());
    }

    @Test
    public void confirmMultiPageTiffHandling() throws Exception {
        assumeTrue("can run OCR", canRun());
        //tesseract should handle multipage tiffs by itself
        //let's confirm that
        String xml = getXML("testTIFF_multipage.tif").xml;
        assertContains("Page 2", xml);
    }

    @Test
    public void testRotatedOCR() throws Exception {
        if (TesseractOCRParser.hasPython()) {
            TesseractOCRConfig config = new TesseractOCRConfig();
            config.setApplyRotation(true);
            config.setEnableImageProcessing(1);
            ParseContext parseContext = new ParseContext();
            parseContext.set(TesseractOCRConfig.class, config);
            assumeTrue(canRun(config));

            String ocr = getText("testRotated.png", new Metadata(), parseContext);
            assertContains("Its had resolving otherwise she contented therefore", ocr);
        }
    }

    @Test
    public void testConfig() throws Exception {
        TikaConfig config = new TikaConfig(getResourceAsStream("/org/apache/tika/config/TIKA-2705-tesseract.xml"));
        Parser p = config.getParser();
        Parser tesseractOCRParser = findParser(p, org.apache.tika.parser.ocr.TesseractOCRParser.class);
        assertNotNull(tesseractOCRParser);

        TesseractOCRConfig tesseractOCRConfig = ((TesseractOCRParser)tesseractOCRParser).getDefaultConfig();
        Assert.assertEquals(241, tesseractOCRConfig.getTimeout());
        Assert.assertEquals(TesseractOCRConfig.OUTPUT_TYPE.HOCR, tesseractOCRConfig.getOutputType());
        Assert.assertEquals("ceb", tesseractOCRConfig.getLanguage());
        Assert.assertEquals(false, tesseractOCRConfig.getApplyRotation());
        assertContains("myspecial", tesseractOCRConfig.getTesseractPath());
    }

}
