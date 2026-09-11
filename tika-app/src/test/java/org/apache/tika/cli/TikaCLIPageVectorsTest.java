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
package org.apache.tika.cli;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.apache.tika.http.TikaTestHttpServer;
import org.apache.tika.inference.Chunk;
import org.apache.tika.inference.ChunkSerializer;

/**
 * The consumer's path for pages: a PAGES binding and pdf-parser.inference.input, and -J shows
 * one vector per page on the PDF itself, each naming its page, from one request.
 */
public class TikaCLIPageVectorsTest {

    private static final String EMBEDDING_RESPONSE = "{\"data\":["
            + "{\"index\":1,\"embedding\":[1.0,0.2,0.3]},"
            + "{\"index\":0,\"embedding\":[0.0,0.2,0.3]}]}";

    @TempDir
    Path tmp;

    @Test
    public void testOneVectorPerPageOnThePdf() throws Exception {
        Path pdf = tmp.resolve("two-pages.pdf");
        writePdf(pdf, 2);
        try (TikaTestHttpServer server = new TikaTestHttpServer()) {
            server.enqueue(new TikaTestHttpServer.MockResponse(200, EMBEDDING_RESPONSE));
            Path config = tmp.resolve("tika-config.json");
            Files.writeString(config, """
                    {
                      "engines": {
                        "clip": { "openai-embedding-engine": { "baseUrl": "BASE_URL", "model": "clip" } }
                      },
                      "inference": [
                        { "id": "page-vectors", "engine": "clip", "input": "PAGES",
                          "tasks": ["embed"], "maxChunks": 50 }
                      ],
                      "parsers": [
                        { "default-parser": {} },
                        { "pdf-parser": { "ocr": { "strategy": "NO_OCR", "dpi": 20 },
                                          "inference": { "input": ["PAGES"] } } }
                      ]
                    }
                    """.replace("BASE_URL", server.url()));

            JsonNode output = new ObjectMapper().readTree(
                    run("--config=" + config, "-J", pdf.toUri().toString()));

            assertEquals(1, server.getRequestCount(), "both pages in one request");
            JsonNode request = new ObjectMapper().readTree(server.takeRequest().body());
            assertEquals(2, request.get("input").size());
            assertTrue(request.get("input").get(0).get("image").asText()
                    .startsWith("data:image/png;base64,"));

            assertEquals(1, output.size(), "the pages are the PDF's: no embedded documents");
            JsonNode root = output.get(0);
            assertTrue(text(root, "tk:content").contains("This is page 2"),
                    "text extraction is untouched");
            List<Chunk> chunks = ChunkSerializer.fromJson(text(root, "tk:chunks"));
            assertEquals(2, chunks.size());
            Set<Integer> pages = new HashSet<>();
            for (Chunk chunk : chunks) {
                assertEquals(3, chunk.getVector().length);
                pages.add(chunk.getLocators().getPaginated().get(0).getPage());
                assertNull(chunk.getLocators().getEmbedded());
            }
            assertEquals(Set.of(1, 2), pages);
        }
    }

    private static void writePdf(Path path, int pages) throws IOException {
        try (PDDocument document = new PDDocument()) {
            for (int i = 1; i <= pages; i++) {
                PDPage page = new PDPage(PDRectangle.LETTER);
                document.addPage(page);
                try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                    content.beginText();
                    content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    content.newLineAtOffset(72, 700);
                    content.showText("This is page " + i + " of the test document.");
                    content.endText();
                }
            }
            document.save(path.toFile());
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        assertNotNull(value, field);
        return value.isArray() ? value.get(0).asText() : value.asText();
    }

    private static String run(String... args) throws Exception {
        PrintStream stdout = System.out;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(out, true, UTF_8.name()));
            TikaCLI.main(args);
        } finally {
            System.setOut(stdout);
        }
        String json = out.toString(UTF_8);
        assertTrue(json.startsWith("["), json);
        return json;
    }
}
