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

import org.apache.tika.TikaTest;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.DefaultParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.external.ExternalParser;
import org.apache.tika.parser.image.ImageParser;
import org.apache.tika.parser.pdf.PDFParserConfig;
import org.apache.tika.sax.BodyContentHandler;
import org.junit.Test;

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
        
        
        // With a correct path, with offer the usual types
        TesseractOCRConfig normalConfig = new TesseractOCRConfig();
        assumeTrue(canRun(normalConfig));
        parseContext.set(TesseractOCRConfig.class, normalConfig);
        
        assertEquals(5, parser.getSupportedTypes(parseContext).size());
        assertTrue(parser.getSupportedTypes(parseContext).contains(png));
        
        // DefaultParser now will
        assertEquals(TesseractOCRParser.class, defaultParser.getParsers(parseContext).get(png).getClass());
    }

    @Test
    public void testPDFOCR() throws Exception {
        TesseractOCRConfig config = new TesseractOCRConfig();
        assumeTrue(canRun(config));

        Parser parser = new AutoDetectParser();
        BodyContentHandler handler = new BodyContentHandler();
        Metadata metadata = new Metadata();

        PDFParserConfig pdfConfig = new PDFParserConfig();
        pdfConfig.setExtractInlineImages(true);

        ParseContext parseContext = new ParseContext();
        parseContext.set(TesseractOCRConfig.class, config);
        parseContext.set(Parser.class, new TesseractOCRParser());
        parseContext.set(PDFParserConfig.class, pdfConfig);

        InputStream stream = TesseractOCRParserTest.class.getResourceAsStream(
                "/test-documents/testOCR.pdf");

        try {
            parser.parse(stream, handler, metadata, parseContext);
            assertContains("Happy New Year 2003!", handler.toString());
        } finally {
            stream.close();
        }
    }

    @Test
    public void testDOCXOCR() throws Exception {
        TesseractOCRConfig config = new TesseractOCRConfig();
        assumeTrue(canRun(config));

        Parser parser = new AutoDetectParser();
        BodyContentHandler handler = new BodyContentHandler();
        Metadata metadata = new Metadata();

        ParseContext parseContext = new ParseContext();
        parseContext.set(TesseractOCRConfig.class, config);
        parseContext.set(Parser.class, new TesseractOCRParser());

        InputStream stream = TesseractOCRParserTest.class.getResourceAsStream(
                "/test-documents/testOCR.docx");

        try {
            parser.parse(stream, handler, metadata, parseContext);

            assertContains("Happy New Year 2003!", handler.toString());
            assertContains("This is some text.", handler.toString());
            assertContains("Here is an embedded image:", handler.toString());
        } finally {
            stream.close();
        }
    }

    @Test
    public void testPPTXOCR() throws Exception {
        TesseractOCRConfig config = new TesseractOCRConfig();
        assumeTrue(canRun(config));

        Parser parser = new AutoDetectParser();
        BodyContentHandler handler = new BodyContentHandler();
        Metadata metadata = new Metadata();

        ParseContext parseContext = new ParseContext();
        parseContext.set(TesseractOCRConfig.class, config);
        parseContext.set(Parser.class, new TesseractOCRParser());

        InputStream stream = TesseractOCRParserTest.class.getResourceAsStream(
                "/test-documents/testOCR.pptx");

        try {
            parser.parse(stream, handler, metadata, parseContext);

            assertTrue("Check for the image's text.", handler.toString().contains("Happy New Year 2003!"));
            assertTrue("Check for the standard text.", handler.toString().contains("This is some text"));
        } finally {
            stream.close();
        }
    }
    
    @Test
    public void getNormalMetadataToo() throws Exception {
        TesseractOCRConfig config = new TesseractOCRConfig();
        assumeTrue(canRun(config));

        Parser parser = new AutoDetectParser();
        BodyContentHandler handler = new BodyContentHandler();
        Metadata metadata = new Metadata();

        ParseContext parseContext = new ParseContext();
        parseContext.set(TesseractOCRConfig.class, config);
        parseContext.set(Parser.class, new TesseractOCRParser());

        InputStream stream = TesseractOCRParserTest.class.getResourceAsStream(
                "/test-documents/testOCR.jpg");

        try {
            parser.parse(stream, handler, metadata, parseContext);
            
            // OCR text
            assertContains("Apache", handler.toString());
            assertContains("OCR Testing", handler.toString());

            // Core JPEG properties from JPEGParser should still come through
            assertEquals("136", metadata.get(Metadata.IMAGE_WIDTH));
            assertEquals("66", metadata.get(Metadata.IMAGE_LENGTH));
            assertEquals("8", metadata.get(Metadata.BITS_PER_SAMPLE));
            assertEquals(null, metadata.get(Metadata.SAMPLES_PER_PIXEL));
            assertContains("This is a test Apache Tika imag", metadata.get(Metadata.COMMENTS));
        } finally {
            stream.close();
        }
    }
}
