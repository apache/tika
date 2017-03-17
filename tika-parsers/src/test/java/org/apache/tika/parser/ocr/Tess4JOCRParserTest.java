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

import java.io.*;
import java.net.URISyntaxException;
import java.net.URL;

import net.sourceforge.tess4j.util.LoggHelper;
import org.apache.tika.exception.TikaException;
import org.apache.tika.sax.BodyContentHandler;
import org.apache.tika.TikaTest;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

public class Tess4JOCRParserTest extends TikaTest {

    private static final Logger logger = LoggerFactory.getLogger(new LoggHelper().toString());
    private final String testResourcesDataPath = "src/test/resources/test-documents/tess4JOCR/";

    @Test
    public void testSingleImageBMP() throws Exception {

        String output = runTess4JOCR("eurotext.bmp", new ParseContext());
        // logger.info(output);          // Uncomment this to witness OCR output
        String expOutput = "The (quick) [brown] {fox} jumps!\nOver the $43,456.78 <lazy> #90 dog";
        assertEquals(expOutput, output.substring(0, expOutput.length()));
    }

    @Test
    public void testSingleImageGIF() throws Exception {

        String output = runTess4JOCR("eurotext.gif", new ParseContext());
        // logger.info(output);          // Uncomment this to witness OCR output
        String expOutput = "The (quick) [brown] {fox} jumps!\nOver the $43,456.78 <lazy> #90 dog";
        assertEquals(expOutput, output.substring(0, expOutput.length()));
    }

    @Test
    public void testSingleImageJP2() throws Exception {

        String output = runTess4JOCR("eurotext.jp2", new ParseContext());
        // logger.info(output);          // Uncomment this to witness OCR output
        String expOutput = "The (quick) [brown] {fox} jumps!\nOver the $43,456.78 <lazy> #90 dog";
        assertEquals(expOutput, output.substring(0, expOutput.length()));
    }

    @Test
    public void testSingleImageJPEG() throws Exception {

        String output = runTess4JOCR("eurotext.jpeg", new ParseContext());
        // logger.info(output);          // Uncomment this to witness OCR output
        String expOutput = "The (quick) [brown] {fox} jumps!\nOver the $43,456.78 <lazy> #90 dog";
        assertEquals(expOutput, output.substring(0, expOutput.length()));
    }

    @Test
    public void testSingleImageJPG() throws Exception {

        String output = runTess4JOCR("eurotext.jpg", new ParseContext());
        // logger.info(output);          // Uncomment this to witness OCR output
        String expOutput = "The (quick) [brown] {fox} jumps!\nOver the $43,456.78 <lazy> #90 dog";
        assertEquals(expOutput, output.substring(0, expOutput.length()));
    }

    @Test
    public void testSingleImageJPX() throws Exception {

        String output = runTess4JOCR("eurotext.jpx", new ParseContext());
        // logger.info(output);          // Uncomment this to witness OCR output
        String expOutput = "The (quick) [brown] {fox} jumps!\nOver the $43,456.78 <lazy> #90 dog";
        assertEquals(expOutput, output.substring(0, expOutput.length()));
    }

    @Test
    public void testSingleImagePNG() throws Exception {

        String output = runTess4JOCR("eurotext.png", new ParseContext());
        // logger.info(output);          // Uncomment this to witness OCR output
        String expOutput = "The (quick) [brown] {fox} jumps!\nOver the $43,456.78 <lazy> #90 dog";
        assertEquals(expOutput, output.substring(0, expOutput.length()));
    }

    @Test
    public void testSingleImagePPM() throws Exception {

        String output = runTess4JOCR("eurotext.ppm", new ParseContext());
        // logger.info(output);          // Uncomment this to witness OCR output
        String expOutput = "The (quick) [brown] {fox} jumps!\nOver the $43,456.78 <lazy> #90 dog";
        assertEquals(expOutput, output.substring(0, expOutput.length()));
    }

    @Test
    public void testSingleImageTIFF() throws Exception {

        String output = runTess4JOCR("eurotext.tif", new ParseContext());
        // logger.info(output);          // Uncomment this to witness OCR output
        String expOutput = "The (quick) [brown] {fox} jumps!\nOver the $43,456.78 <lazy> #90 dog";
        assertEquals(expOutput, output.substring(0, expOutput.length()));
    }

    @Test
    public void testSingleSkewedImageTess4J() throws Exception {

        TesseractOCRConfig config = new TesseractOCRConfig();
        config.setEnableImageProcessing(1);
        ParseContext parseContext = new ParseContext();
        parseContext.set(TesseractOCRConfig.class, config);
        String output = runTess4JOCR("eurotext_deskew.png", parseContext);
        // logger.info(output);          // Uncomment this to witness OCR output
        String expOutput = "The (quick) [brown] {fox} jumps!\nOver the $43,456.78 <lazy> #90 dog";
        assertEquals(expOutput, output.substring(0, expOutput.length()));
    }

    // This test uses TesseractOCRParser's "Rotation.py" python script to rotate the image.
    // But this doesn't seem to be working.
    @Test
    public void testSingleSkewedImageTesseract() throws Exception {

        TesseractOCRConfig config = new TesseractOCRConfig();
        config.setEnableImageProcessing(1);
        ParseContext parseContext = new ParseContext();
        parseContext.set(TesseractOCRConfig.class, config);
        String output = runTesseractOCR("eurotext_deskew.png", parseContext);
        // logger.info(output);          // Uncomment this to witness OCR output
        String expOutput = "The (quick) [brown] {fox} jumps!\nOver the $43,456.78 <lazy> #90 dog";
        assertEquals(expOutput, output.substring(0, expOutput.length()));
    }

    // TesseractOCRParser vs Tess4JOCRParser
    @Test
    public void runBenchmark() throws IOException, TikaException, SAXException, URISyntaxException {

        logger.info("Running Benchmark");
        ContentHandler tesseractHandler = new BodyContentHandler(-1);
        ContentHandler tess4JHandler = new BodyContentHandler(-1);
        Metadata tesseractMetadata = new Metadata();
        Metadata tess4JMetadata = new Metadata();
        ParseContext tesseractContext = new ParseContext();
        ParseContext tess4JContext = new ParseContext();

        URL dirUrl = getClass().getClassLoader().getResource("test-documents/tess4JOCR/OCR_Compare/");
        assert dirUrl != null;
        File folder = new File(dirUrl.toURI());
        File[] listOfFiles = folder.listFiles();
        assert listOfFiles != null;

        // Initializing parsers
        Tess4JOCRParser tess4JParser = new Tess4JOCRParser();
        TesseractOCRParser tesseractParser = new TesseractOCRParser();

        long tess4JElapsedTime = 0;
        long tesseractElapsedTime = 0;
        int progress = 0;

        for (File file : listOfFiles) {
            progress += 1;
            if (file.isFile()) {

                // For tess4JParser
                try (FileInputStream stream = new FileInputStream(file)) {
                    long startTime = System.currentTimeMillis();
                    tess4JParser.parse(stream, tess4JHandler, tess4JMetadata, tess4JContext);
                    tess4JElapsedTime += System.currentTimeMillis() - startTime;
                }
                // logger.info(tess4JHandler.toString());           // Uncomment this to witness OCR output of Tess4J

                // For tesseractParser
                try (FileInputStream stream = new FileInputStream(file)) {
                    long startTime = System.currentTimeMillis();
                    tesseractParser.parse(stream, tesseractHandler, tesseractMetadata, tesseractContext);
                    tesseractElapsedTime += System.currentTimeMillis() - startTime;
                }
                // logger.info(tesseractHandler.toString());        // Uncomment this to witness OCR output of Tesseract
            }
            logger.info("Current Progress: " + progress + "% |" +
                    " Tess4JOCRParser: " + tess4JElapsedTime / 1000 + " S |" +
                    " TesseractOCRParser: " + tesseractElapsedTime / 1000 + " S");
        }
    }

    private String runTesseractOCR(String resource, ParseContext parseContext) throws IOException, TikaException, SAXException {

        FileInputStream stream = new FileInputStream(testResourcesDataPath + resource);
        TesseractOCRParser tesseractParser = new TesseractOCRParser();
        ContentHandler handler = new BodyContentHandler(-1);
        Metadata metadata = new Metadata();
        tesseractParser.parse(stream, handler, metadata, parseContext);
        return handler.toString();
    }

    private String runTess4JOCR(String resource, ParseContext parseContext) throws IOException, TikaException, SAXException {

        FileInputStream stream = new FileInputStream(testResourcesDataPath + resource);
        Tess4JOCRParser tess4JParser = new Tess4JOCRParser();
        ContentHandler handler = new BodyContentHandler(-1);
        Metadata metadata = new Metadata();
        tess4JParser.parse(stream, handler, metadata, parseContext);
        return handler.toString();
    }
}