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

import static org.apache.tika.parser.ocr.TesseractOCRParser.getTesseractProg;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import java.io.InputStream;
import java.util.List;

import org.apache.tika.TikaTest;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.DefaultParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.RecursiveParserWrapper;
import org.apache.tika.parser.external.ExternalParser;
import org.apache.tika.parser.image.ImageParser;
import org.apache.tika.parser.pdf.PDFParserConfig;
import org.apache.tika.sax.BasicContentHandlerFactory;
import org.junit.Test;
import org.xml.sax.helpers.DefaultHandler;

public class TesseractOCRParserTest extends TikaTest {

    public static boolean canRun() {
        TesseractOCRConfig config = new TesseractOCRConfig();
        TesseractOCRParserTest tesseractOCRTest = new TesseractOCRParserTest();
        return tesseractOCRTest.canRun(config);
    }

    private boolean canRun(TesseractOCRConfig config) {
        String[] checkCmd = {config.getTesseractPath() + getTesseractProg()};
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
        assertEquals(0, parser.getSupportedTypes(parseContext).size());

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
        assumeTrue(canRun());

        assertEquals(5, parser.getSupportedTypes(parseContext).size());
        assertTrue(parser.getSupportedTypes(parseContext).contains(png));

        // DefaultParser will now select the TesseractOCRParser.
        assertEquals(TesseractOCRParser.class, defaultParser.getParsers(parseContext).get(png).getClass());
    }

    @Test
    public void testPDFOCR() throws Exception {
        String resource = "/test-documents/testOCR.pdf";
        String[] nonOCRContains = new String[0];
        testBasicOCR(resource, nonOCRContains, 2);
    }

    @Test
    public void testDOCXOCR() throws Exception {
        String resource = "/test-documents/testOCR.docx";
        String[] nonOCRContains = {
                "This is some text.",
                "Here is an embedded image:"
        };
        testBasicOCR(resource, nonOCRContains, 3);
    }

    @Test
    public void testPPTXOCR() throws Exception {
        String resource = "/test-documents/testOCR.pptx";
        String[] nonOCRContains = {
                "This is some text"
        };
        testBasicOCR(resource, nonOCRContains, 3);
    }

    private void testBasicOCR(String resource, String[] nonOCRContains, int numMetadatas) throws Exception {
        TesseractOCRConfig config = new TesseractOCRConfig();
        Parser parser = new RecursiveParserWrapper(new AutoDetectParser(),
                new BasicContentHandlerFactory(
                        BasicContentHandlerFactory.HANDLER_TYPE.TEXT, -1));

        PDFParserConfig pdfConfig = new PDFParserConfig();
        pdfConfig.setExtractInlineImages(true);

        ParseContext parseContext = new ParseContext();
        parseContext.set(TesseractOCRConfig.class, config);
        parseContext.set(Parser.class, parser);
        parseContext.set(PDFParserConfig.class, pdfConfig);

        InputStream stream = TesseractOCRParserTest.class.getResourceAsStream(resource);

        try {
            parser.parse(stream, new DefaultHandler(), new Metadata(), parseContext);
        } finally {
            stream.close();
        }
        List<Metadata> metadataList = ((RecursiveParserWrapper) parser).getMetadata();
        assertEquals(numMetadatas, metadataList.size());

        StringBuilder contents = new StringBuilder();
        for (Metadata m : metadataList) {
            contents.append(m.get(RecursiveParserWrapper.TIKA_CONTENT));
        }
        if (canRun()) {
            assertTrue(contents.toString().contains("Happy New Year 2003!"));
        }
        for (String needle : nonOCRContains) {
            assertContains(needle, contents.toString());
        }
        assertTrue(metadataList.get(0).names().length > 10);
        assertTrue(metadataList.get(1).names().length > 10);
        //test at least one value
        assertEquals("deflate", metadataList.get(1).get("Compression CompressionTypeName"));
    }

    @Test
    public void testSingleImage() throws Exception {
        assumeTrue(canRun());
        String xml = getXML("testOCR.jpg").xml;
        assertContains("OCR Testing", xml);
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
        assertContains("This is a test Apache Tika imag", m.get(Metadata.COMMENTS));

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
        assertEquals("72 dots per inch", m.get("Y Resolution"));
    }
}
