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
import static org.bouncycastle.crypto.tls.CipherType.stream;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import org.apache.poi.ss.formula.functions.T;
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
        final File imageFile = new File("/home/thejan/Desktop/test.jpg");
        final ContentHandler tesseractHandler = new BodyContentHandler();
        final ContentHandler tess4JHandler = new BodyContentHandler();
        final Metadata metadata = new Metadata();
        final ParseContext tesseractContext = new ParseContext();
        final ParseContext tess4JContext = new ParseContext();

        // Initializing parsers
        final Tess4JOCRParser tess4JParser = new Tess4JOCRParser();
        final TesseractOCRParser tesseractParser = new TesseractOCRParser();
        AutoDetectParser autoDetectParser = new AutoDetectParser();


        // For tess4JParser
        long tess4JStartTime = System.currentTimeMillis();
        tess4JParser.parse(imageFile, tess4JHandler, metadata, tess4JContext);
        long tess4jStopTime = System.currentTimeMillis();
        System.out.println("For tess4JOCRParser: " + (tess4jStopTime - tess4JStartTime) + " ms");

        System.out.println(tess4JHandler.toString());


        // For tesseractParser
        FileInputStream stream = new FileInputStream(imageFile);
        long TesseractStartTime = System.currentTimeMillis();
        tesseractParser.parse(stream, tesseractHandler, metadata, tesseractContext);
        long TesseractStopTime = System.currentTimeMillis();
        System.out.println("For tesseractOCRParser: " + (TesseractStopTime - TesseractStartTime) + " ms");

        System.out.println(tesseractHandler.toString());
        stream.close();



    }
        /* For metadata

        String[] metadataNames = metadata.names();

        for (String name : metadataNames) {
            System.out.println(name + " : " + metadata.get(name));
        }

        */

}



