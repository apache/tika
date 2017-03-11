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
    public void testImages() throws IOException, TikaException, SAXException {

        final ContentHandler tesseractHandler = new BodyContentHandler();
        final ContentHandler tess4JHandler = new BodyContentHandler();
        final Metadata metadata = new Metadata();
        final ParseContext tesseractContext = new ParseContext();
        final ParseContext tess4JContext = new ParseContext();

        // OCR Test Images
        File folder = new File("/home/thejan/IdeaProjects/GSoC/tika/tika-parsers/src/test/resources/test-documents/OCR_Compare/");
        File[] listOfFiles = folder.listFiles();


        // Initializing parsers
        final Tess4JOCRParser tess4JParser = new Tess4JOCRParser();
        final TesseractOCRParser tesseractParser = new TesseractOCRParser();
        final AutoDetectParser autoDetectParser = new AutoDetectParser();


        if (listOfFiles != null) {
            long tess4JElapsedTime = 0;
            long tesseractElapsedTime = 0;
            int progress = 0;
            for (File file : listOfFiles) {
                if (file.isFile()) {

                    // For tess4JParser
                    long tess4JStartTime = System.currentTimeMillis();
                    tess4JParser.parse(file, tess4JHandler, metadata, tess4JContext);
                    long tess4jStopTime = System.currentTimeMillis();
                    tess4JElapsedTime += tess4jStopTime - tess4JStartTime;

                    // Uncomment this if you want to see what is being printed
                    // System.out.println(tess4JHandler.toString());

                    // For tesseractParser
                    FileInputStream stream = new FileInputStream(file);
                    long tesseractStartTime = System.currentTimeMillis();
                    tesseractParser.parse(stream, tesseractHandler, metadata, tesseractContext);
                    long tesseractStopTime = System.currentTimeMillis();
                    tesseractElapsedTime += tesseractStopTime - tesseractStartTime;
                    stream.close();

                    // Uncomment this if you want to see what is being printed
                    // System.out.println(tesseractHandler.toString());

                }
                progress += 1;
                System.out.println("Current Progress: " + progress + "%");
            }
            System.out.println("For tess4JOCRParser: " + tess4JElapsedTime / 1000 + " s");
            System.out.println("For tesseractOCRParser: " + tesseractElapsedTime / 1000 + " s");
        }
    }
        /* For metadata

        String[] metadataNames = metadata.names();

        for (String name : metadataNames) {
            System.out.println(name + " : " + metadata.get(name));
        }

        */

}



