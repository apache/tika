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

import org.apache.tika.exception.TikaException;
import org.apache.tika.parser.ocr.Tess4JOCRParser;
import org.apache.tika.sax.BodyContentHandler;

import java.io.*;
import java.util.List;
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

//    This is a test I wrote to compare content extraction times between terseractocrpasser and tess4jocrparser
    @Test
    public void testImages() throws IOException, TikaException, SAXException {
        File imageFile = new File("/home/thejan/Desktop/test2.jpg");
//        Image stream = ImageIO.read(new File("/home/thejan/Desktop/test.jpg"));
//        FileInputStream stream = new FileInputStream(imageFile);
        ContentHandler handler = new BodyContentHandler();
        Metadata metadata = new Metadata();
        ParseContext context = new ParseContext();

        Tess4JOCRParser tess4JParser = new Tess4JOCRParser();
        TesseractOCRParser tesseractParser = new TesseractOCRParser();
//        AutoDetectParser tessParser = new AutoDetectParser();

        tess4JParser.parse(imageFile, handler, metadata, context);
//        stream.close();
        System.out.println(handler.toString());

//        String[] metadataNames = metadata.names();
//
//        for (String name : metadataNames) {
//            System.out.println(name + " : " + metadata.get(name));
//        }
    }


}
