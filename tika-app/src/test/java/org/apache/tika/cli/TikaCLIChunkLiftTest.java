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

/**
 * The consumer's path: a JSON config names the image embedder in text-recognizers, and -J
 * shows the vectors of the pictures in a docx body on the docx itself, each naming the
 * picture it came from. No other configuration: a picture's vector belongs to its document.
 */
public class TikaCLIChunkLiftTest {

    private static final String EMBEDDING_RESPONSE =
            "{\"data\":[{\"embedding\":[0.1,0.2,0.3]}]}";

    @TempDir
    Path tmp;

    @Test
    public void testPictureVectorsLiftIntoTheDocx() throws Exception {
        Path docx = tmp.resolve("two-pictures.docx");
        writeDocx(docx);
        try (TikaTestHttpServer server = new TikaTestHttpServer()) {
            server.enqueue(new TikaTestHttpServer.MockResponse(200, EMBEDDING_RESPONSE));
            server.enqueue(new TikaTestHttpServer.MockResponse(200, EMBEDDING_RESPONSE));
            Path config = tmp.resolve("tika-config.json");
            Files.writeString(config, """
                    {
                      "text-recognizers": [
                        { "openai-image-embedding-parser": { "baseUrl": "BASE_URL", "model": "clip" } }
                      ]
                    }
                    """.replace("BASE_URL", server.url()));

            JsonNode output = new ObjectMapper().readTree(
                    run("--config=" + config, "-J", docx.toUri().toString()));

            assertEquals(2, server.getRequestCount(), "one embedding request per picture");
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
                assertFalse(picture.has("tk:chunks"), "chunks moved off the picture");
                picturePaths.add(text(picture, "tk:embedded-id-path"));
            }

            List<Chunk> chunks = ChunkSerializer.fromJson(text(root, "tk:chunks"));
            assertEquals(2, chunks.size());
            Set<String> origins = new HashSet<>();
            for (Chunk chunk : chunks) {
                assertEquals(3, chunk.getVector().length);
                origins.add(chunk.getLocators().getEmbedded().get(0).getIdPath());
                assertTrue(chunk.getLocators().getEmbedded().get(0).getName().endsWith(".png"));
            }
            assertEquals(picturePaths, origins);
        }
    }

    /** A minimal docx: one paragraph and two pictures related to the main document part. */
    private static void writeDocx(Path docx) throws Exception {
        String rel = "http://schemas.openxmlformats.org/officeDocument/2006/relationships/";
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(docx))) {
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
