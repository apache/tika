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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.imageio.ImageIO;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.apache.tika.http.TikaTestHttpServer;
import org.apache.tika.inference.Chunk;
import org.apache.tika.inference.ChunkSerializer;
import org.apache.tika.metadata.TikaCoreProperties;

/**
 * The consumer's path for the bindings shape: an engine named once, one inference binding on
 * IMAGES, and -J shows the two pictures of a docx embedded in one request, their vectors on
 * the docx itself, each naming the picture it came from.
 */
public class TikaCLIInferenceBindingsTest {

    private static final String EMBEDDING_RESPONSE = "{\"data\":["
            + "{\"index\":1,\"embedding\":[1.0,0.2,0.3]},"
            + "{\"index\":0,\"embedding\":[0.0,0.2,0.3]}]}";

    @TempDir
    Path tmp;

    @Test
    public void testOneRequestPerDocumentVectorsOnTheDocx() throws Exception {
        Path docx = tmp.resolve("two-pictures.docx");
        writeDocx(docx);
        try (TikaTestHttpServer server = new TikaTestHttpServer()) {
            server.enqueue(new TikaTestHttpServer.MockResponse(200, EMBEDDING_RESPONSE));
            Path config = tmp.resolve("tika-config.json");
            Files.writeString(config, """
                    {
                      "engines": {
                        "clip": { "openai-embedding-engine": { "baseUrl": "BASE_URL", "model": "clip" } }
                      },
                      "inference": [
                        { "id": "picture-vectors", "engine": "clip", "input": "IMAGES",
                          "tasks": ["embed"], "maxChunks": 10 }
                      ]
                    }
                    """.replace("BASE_URL", server.url()));

            JsonNode output = new ObjectMapper().readTree(
                    run("--config=" + config, "-J", docx.toUri().toString()));

            assertEquals(1, server.getRequestCount(), "both pictures in one request");
            JsonNode request = new ObjectMapper().readTree(server.takeRequest().body());
            assertEquals(2, request.get("input").size());

            JsonNode root = null;
            List<JsonNode> pictures = new ArrayList<>();
            for (JsonNode m : output) {
                if (!m.has("tk:embedded-id-path")) {
                    root = m;
                } else if ("INLINE".equals(text(m, "tk:embedded-resource-type"))) {
                    pictures.add(m);
                }
            }
            assertNotNull(root);
            assertEquals(2, pictures.size());
            Set<String> picturePaths = new HashSet<>();
            for (JsonNode picture : pictures) {
                assertFalse(picture.has("tk:chunks"), "chunks land on the docx, not the picture");
                picturePaths.add(text(picture, "tk:embedded-id-path"));
            }
            List<Chunk> chunks = ChunkSerializer.fromJson(text(root, "tk:chunks"));
            assertEquals(2, chunks.size());
            Set<String> origins = new HashSet<>();
            for (Chunk chunk : chunks) {
                assertEquals(3, chunk.getVector().length);
                origins.add(chunk.getLocators().getEmbedded().get(0).getIdPath());
            }
            assertEquals(picturePaths, origins);
        }
    }

    /** A docx inside a zip: the vectors land on the docx as it appears in the output list. */
    @Test
    public void testNestedContainerAndAttachment() throws Exception {
        Path zip = tmp.resolve("bundle.zip");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
            entry(out, "two-pictures.docx", docxBytes());
            entry(out, "solo.png", png(3));
        }
        try (TikaTestHttpServer server = new TikaTestHttpServer()) {
            server.enqueue(new TikaTestHttpServer.MockResponse(200, "{\"data\":["
                    + "{\"index\":0,\"embedding\":[0.0,0.2,0.3]},"
                    + "{\"index\":1,\"embedding\":[1.0,0.2,0.3]},"
                    + "{\"index\":2,\"embedding\":[2.0,0.2,0.3]}]}"));
            Path config = tmp.resolve("tika-config.json");
            Files.writeString(config, """
                    {
                      "engines": {
                        "clip": { "openai-embedding-engine": { "baseUrl": "BASE_URL", "model": "clip" } }
                      },
                      "inference": [ { "engine": "clip", "input": "IMAGES", "tasks": ["embed"] } ]
                    }
                    """.replace("BASE_URL", server.url()));
            JsonNode output = new ObjectMapper().readTree(
                    run("--config=" + config, "-J", zip.toUri().toString()));
            assertEquals(1, server.getRequestCount(), "all three images in one request");

            JsonNode docx = null;
            JsonNode solo = null;
            int pictures = 0;
            for (JsonNode m : output) {
                String nameKey = TikaCoreProperties.RESOURCE_NAME_KEY.getName();
                String name = m.has(nameKey) ? text(m, nameKey) : "";
                if (name.endsWith("two-pictures.docx")) {
                    docx = m;
                } else if (name.endsWith("solo.png")) {
                    solo = m;
                } else if (m.has("tk:embedded-resource-type")
                        && "INLINE".equals(text(m, "tk:embedded-resource-type"))) {
                    pictures++;
                    assertFalse(m.has("tk:chunks"));
                }
            }
            assertNotNull(docx, "the docx is in the output list");
            assertNotNull(solo, "the attached png is in the output list");
            assertEquals(2, pictures);
            assertEquals(2, ChunkSerializer.fromJson(text(docx, "tk:chunks")).size(),
                    "the pictures' vectors land on the docx the wrapper kept, not the zip");
            assertEquals(1, ChunkSerializer.fromJson(text(solo, "tk:chunks")).size(),
                    "an attachment keeps its own vector");
            assertFalse(output.get(0).has("tk:chunks"), "nothing lands on the zip");
        }
    }

    @Test
    public void testOffForOneRequest() throws Exception {
        Path docx = tmp.resolve("two-pictures.docx");
        writeDocx(docx);
        try (TikaTestHttpServer server = new TikaTestHttpServer()) {
            Path config = tmp.resolve("tika-config.json");
            Files.writeString(config, """
                    {
                      "engines": {
                        "clip": { "openai-embedding-engine": { "baseUrl": "BASE_URL", "model": "clip" } }
                      },
                      "inference": [ { "engine": "clip", "input": "IMAGES", "tasks": ["embed"] } ],
                      "parse-context": { "inference": { "enabled": false } }
                    }
                    """.replace("BASE_URL", server.url()));
            JsonNode output = new ObjectMapper().readTree(
                    run("--config=" + config, "-J", docx.toUri().toString()));
            assertEquals(0, server.getRequestCount(), "switched off for this request");
            assertFalse(output.get(0).has("tk:chunks"));
        }
    }

    private static void writeDocx(Path docx) throws Exception {
        Files.write(docx, docxBytes());
    }

    /** A minimal docx: one paragraph and two pictures related to the main document part. */
    private static byte[] docxBytes() throws Exception {
        String rel = "http://schemas.openxmlformats.org/officeDocument/2006/relationships/";
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            entry(zip, "[Content_Types].xml", """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                    <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                    <Default Extension="xml" ContentType="application/xml"/>
                    <Default Extension="png" ContentType="image/png"/>
                    <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
                    </Types>
                    """.getBytes(UTF_8));
            entry(zip, "_rels/.rels", ("""
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                    <Relationship Id="rId1" Type="RELofficeDocument" Target="word/document.xml"/>
                    </Relationships>
                    """).replace("REL", rel).getBytes(UTF_8));
            entry(zip, "word/document.xml", """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                    <w:body><w:p><w:r><w:t>two pictures</w:t></w:r></w:p></w:body></w:document>
                    """.getBytes(UTF_8));
            entry(zip, "word/_rels/document.xml.rels", ("""
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                    <Relationship Id="rId1" Type="RELimage" Target="media/image1.png"/>
                    <Relationship Id="rId2" Type="RELimage" Target="media/image2.png"/>
                    </Relationships>
                    """).replace("REL", rel).getBytes(UTF_8));
            entry(zip, "word/media/image1.png", png(1));
            entry(zip, "word/media/image2.png", png(2));
        }
        return bytes.toByteArray();
    }

    private static void entry(ZipOutputStream zip, String name, byte[] bytes) throws Exception {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(bytes);
        zip.closeEntry();
    }

    private static byte[] png(int shade) throws Exception {
        BufferedImage image = new BufferedImage(20, 20, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < 20; x++) {
            for (int y = 0; y < 20; y++) {
                image.setRGB(x, y, shade * 0x3F3F3F);
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
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
