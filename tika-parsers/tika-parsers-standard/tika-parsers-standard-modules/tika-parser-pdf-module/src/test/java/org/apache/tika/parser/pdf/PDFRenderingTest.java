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

package org.apache.tika.parser.pdf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import org.apache.tika.TikaTest;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.extractor.ParsingEmbeddedDocumentExtractor;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.metadata.TikaPagedText;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;

public class PDFRenderingTest extends TikaTest {

    @Test
    public void testDefault() throws Exception {
        List<Metadata> metadataList = getRecursiveMetadata("testPDF.pdf");
        assertEquals(1, metadataList.size());
    }

    @Test
    public void testBasic() throws Exception {
        ParseContext parseContext = configureParseContext();
        TikaConfig config = getConfig("tika-rendering-config.xml");
        Parser p = new AutoDetectParser(config);
        List<Metadata> metadataList = getRecursiveMetadata("testPDF.pdf", p, parseContext);
        Map<Integer, byte[]> embedded =
                ((RenderCaptureExtractor)parseContext.get(EmbeddedDocumentExtractor.class))
                        .getEmbedded();
        assertEquals(1, embedded.size());
        assertTrue(embedded.containsKey(0));
        //what else can we do to test this?  File type == tiff? Run OCR?
        assertTrue(embedded.get(0).length > 1000);

        assertEquals(2, metadataList.size());
        Metadata tiffMetadata = metadataList.get(1);
        assertEquals("RENDERING", tiffMetadata.get(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE));
        assertEquals(1, tiffMetadata.getInt(TikaPagedText.PAGE_NUMBER));
    }

    @Test
    public void testRotated() throws Exception {
        ParseContext parseContext = configureParseContext();
        TikaConfig config = getConfig("tika-rendering-config.xml");
        Parser p = new AutoDetectParser(config);
        List<Metadata> metadataList = getRecursiveMetadata("testPDF_rotated.pdf", p, parseContext);
        Map<Integer, byte[]> embedded =
                ((RenderCaptureExtractor)parseContext.get(EmbeddedDocumentExtractor.class))
                        .getEmbedded();

        assertEquals(1, embedded.size());
        assertTrue(embedded.containsKey(0));
        //what else can we do to test this?  File type == tiff? Run OCR?
        assertTrue(embedded.get(0).length > 1000);

        assertEquals(2, metadataList.size());
        Metadata tiffMetadata = metadataList.get(1);
        assertEquals("RENDERING", tiffMetadata.get(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE));
        assertEquals(1, tiffMetadata.getInt(TikaPagedText.PAGE_NUMBER));
        assertEquals(90.0, Double.parseDouble(tiffMetadata.get(TikaPagedText.PAGE_ROTATION)), 0.1);
    }

    private TikaConfig getConfig(String path) throws TikaException, IOException, SAXException {
        try (InputStream is = PDFRenderingTest.class.getResourceAsStream(path)) {
            return new TikaConfig(is);
        }
    }

    private ParseContext configureParseContext() {
        ParseContext parseContext = new ParseContext();
        parseContext.set(EmbeddedDocumentExtractor.class, new RenderCaptureExtractor(parseContext));
        return parseContext;
    }


    private class RenderCaptureExtractor extends ParsingEmbeddedDocumentExtractor {
        private int count = 0;
        Map<Integer, byte[]> embedded = new HashMap<>();

        public RenderCaptureExtractor(ParseContext context) {
            super(context, 0);
        }

        public void parseEmbedded(InputStream stream, ContentHandler handler, Metadata metadata,
                                  boolean outputHtml) throws SAXException, IOException {
            TikaInputStream tstream = TikaInputStream.get(stream);
            byte[] bytes = Files.readAllBytes(tstream.getPath());
            embedded.put(count++, bytes);
            try (InputStream is = Files.newInputStream(tstream.getPath())) {
                super.parseEmbedded(is, handler, metadata, outputHtml);
            }
        }

        public Map<Integer, byte[]> getEmbedded() {
            return embedded;
        }
    }
}
