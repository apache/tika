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

import org.apache.tika.exception.TikaException;
import org.apache.tika.parser.ocr.Tess4JOCRParser;
import org.apache.tika.sax.BodyContentHandler;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

public class Tess4JOCRParserTest extends TikaTest {

    // This is a test I wrote to compare content extraction times between terseractocrpasser and tess4jocrparser
    @Test
    public void testImages() throws IOException, TikaException, SAXException, URISyntaxException {

        ContentHandler tesseractHandler = new BodyContentHandler(-1);
        ContentHandler tess4JHandler = new BodyContentHandler(-1);
        Metadata metadata = new Metadata();
        ParseContext tesseractContext = new ParseContext();
        ParseContext tess4JContext = new ParseContext();

        URL dirUrl = getClass().getClassLoader().getResource("test-documents/OCR_Compare/");
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
            if (file.isFile()) {
                // For tess4JParser
                try (FileInputStream stream = new FileInputStream(file)) {
                    long startTime = System.currentTimeMillis();
                    tess4JParser.parse(stream, tess4JHandler, metadata, tess4JContext);
                    tess4JElapsedTime += System.currentTimeMillis() - startTime;
                }
                // Uncomment this if you want to see what is being printed
                // System.out.println(tess4JHandler.toString());

                // For tesseractParser
                try (FileInputStream stream = new FileInputStream(file)) {
                    long startTime = System.currentTimeMillis();
                    tesseractParser.parse(stream, tesseractHandler, metadata, tesseractContext);
                    tesseractElapsedTime += System.currentTimeMillis() - startTime;
                }
                // Uncomment this if you want to see what is being printed
                // System.out.println(tesseractHandler.toString());
            }
            progress += 1;
            if (progress==10){
                break;
            }
            System.out.println("Current Progress: " + progress + "%");
        }
        System.out.println("For tess4JOCRParser: " + tess4JElapsedTime / 1000 + " S");
        System.out.println("For tesseractOCRParser: " + tesseractElapsedTime / 1000 + " S");
    }
}