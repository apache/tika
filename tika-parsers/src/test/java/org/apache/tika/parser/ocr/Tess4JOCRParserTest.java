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

    private String runTess4JOCR(String resource, ParseContext parseContext) throws IOException, TikaException, SAXException {

        final String testResourcesDataPath = "src/test/resources/test-documents/tess4JOCR/";
        FileInputStream stream = new FileInputStream(testResourcesDataPath + resource);
        Tess4JOCRParser tess4JParser = new Tess4JOCRParser();
        ContentHandler handler = new BodyContentHandler(-1);
        Metadata metadata = new Metadata();
        tess4JParser.parse(stream, handler, metadata, parseContext);
        return handler.toString();
    }
}