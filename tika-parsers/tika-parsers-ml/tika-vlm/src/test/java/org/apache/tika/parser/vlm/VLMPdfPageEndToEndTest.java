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
package org.apache.tika.parser.vlm;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.xml.sax.helpers.DefaultHandler;

import org.apache.tika.http.TikaTestHttpServer;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.pages.TextPolicy;
import org.apache.tika.parser.pdf.PDFParser;
import org.apache.tika.parser.pdf.PDFParserConfig;

/** A rendered PDF page, not a bare image, reaches the VLM parser: its warnings reach the PDF. */
public class VLMPdfPageEndToEndTest {

    private TikaTestHttpServer server;
    private OpenAIVLMParser vlm;

    @BeforeEach
    void setUp() throws Exception {
        server = new TikaTestHttpServer();
        VLMOCRConfig config = new VLMOCRConfig();
        config.setBaseUrl(server.url());
        config.setModel("test-model");
        config.setTimeoutMillis(10_000);
        server.enqueue(new TikaTestHttpServer.MockResponse(200, "{\"object\":\"list\"}"));
        vlm = new OpenAIVLMParser(config);
        vlm.initialize();
        server.clearRequests();
    }

    @AfterEach
    void tearDown() {
        server.shutdown();
    }

    private List<String> warningsOfPdfPage(Path dir, String modelAnswer, boolean ink)
            throws Exception {
        Path pdf = dir.resolve("page.pdf");
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            document.addPage(page);
            if (ink) {
                try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                    content.addRect(72, 400, 300, 200);
                    content.fill();
                }
            }
            document.save(pdf.toFile());
        }
        server.enqueue(new TikaTestHttpServer.MockResponse(200,
                "{\"choices\":[{\"message\":{\"content\":\"" + modelAnswer + "\"}}]}"));

        PDFParserConfig pdfConfig = new PDFParserConfig();
        pdfConfig.pages().setText(TextPolicy.OCR);
        ParseContext context = new ParseContext();
        context.set(PDFParserConfig.class, pdfConfig);
        context.set(Parser.class, vlm);

        Metadata metadata = new Metadata();
        try (TikaInputStream tis = TikaInputStream.get(pdf)) {
            new PDFParser().parse(tis, new DefaultHandler(), metadata, context);
        }
        assertEquals(1, server.getRequestCount());
        return Arrays.asList(metadata.getValues(TikaCoreProperties.OCR_WARNINGS));
    }

    @Test
    void testAnEmptyAnswerIsFlaggedOnThePdf(@TempDir Path dir) throws Exception {
        assertEquals(List.of("empty"), warningsOfPdfPage(dir, " ", true));
    }

    @Test
    void testOrdinaryAnswerIsNotFlagged(@TempDir Path dir) throws Exception {
        assertEquals(List.of(), warningsOfPdfPage(dir, "Hello", true));
    }
}
