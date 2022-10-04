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
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;

import org.apache.tika.TikaTest;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.CompositeParser;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.ParserDecorator;
import org.apache.tika.parser.gdal.GDALParser;

public class TestOCR extends TikaTest {

    @BeforeAll
    public static void checkTesseract() throws Exception {
        TesseractOCRParser p = new TesseractOCRParser();
        assumeTrue(p.hasTesseract());
    }

    @Test
    public void testJPEG() throws Exception {
        List<Metadata> metadataList = getRecursiveMetadata("testOCR.jpg", loadParser());
        assertContains("OCR Testing", metadataList.get(0).get(TikaCoreProperties.TIKA_CONTENT));
    }

    @Test
    public void testPNG() throws Exception {
        List<Metadata> metadataList = getRecursiveMetadata("testOCR.png", loadParser());
        assertContains("file contains", metadataList.get(0).get(TikaCoreProperties.TIKA_CONTENT));
    }

    @Test
    public void testOthers() throws Exception {
        Parser p = loadParser();
        if (p instanceof CompositeParser) {
            Map<MediaType, Parser> parsers = ((CompositeParser)p).getParsers();
            Class clz = getParser(MediaType.application("x-netcdf"), parsers);
            assertEquals(GDALParser.class, clz);
        }
    }

    private Class getParser(MediaType mediaType, Map<MediaType, Parser> parsers) {
        //this is fragile, but works well enough for a unit test
        Parser p = parsers.get(mediaType);
        if (p instanceof CompositeParser) {
            return getParser(mediaType, ((CompositeParser)p).getParsers());
        } else if (p instanceof ParserDecorator) {
            Parser decorated = ((ParserDecorator)p).getWrappedParser();
            return decorated.getClass();
        }
        return p.getClass();
    }

    private Parser loadParser() throws IOException, TikaException, SAXException {
        try (InputStream is = TestOCR.class.getResourceAsStream(
                "/config/tika-config-restricted-gdal.xml")) {
            TikaConfig tikaConfig = new TikaConfig(is);
            return new AutoDetectParser(tikaConfig);
        }
    }

}
