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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.imageio.ImageIO;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.apache.tika.http.TikaTestHttpServer;

/**
 * The consumer's path for tk:ocr-warnings: the text of a docx's pictures lands in the docx, so
 * under -J a picture's skip and the VLM's doubt about another appear on the docx itself.
 */
public class TikaCLIOcrWarningsTest {

    @TempDir
    Path tmp;

    @Test
    public void testPictureWarningsReachTheDocx() throws Exception {
        Path docx = tmp.resolve("two-pictures.docx");
        writeDocx(docx);
        try (TikaTestHttpServer server = new TikaTestHttpServer()) {
            server.enqueue(new TikaTestHttpServer.MockResponse(200, "{\"object\":\"list\"}"));
            server.enqueue(new TikaTestHttpServer.MockResponse(200,
                    "{\"choices\":[{\"message\":{\"content\":\"\"}}]}"));
            Path config = tmp.resolve("tika-config.json");
            Files.writeString(config, """
                    {
                      "engines": { "vlm": { "openai-vlm-parser": { "baseUrl": "BASE_URL", "model": "m" } } },
                      "text-recognizers": [ { "engine": "vlm", "checks": [ "blank-image-check" ] } ]
                    }
                    """.replace("BASE_URL", server.url()));

            JsonNode output = new ObjectMapper().readTree(
                    run("--config=" + config, "-J", docx.toUri().toString()));

            assertEquals(2, server.getRequestCount(), "the health check and the inked picture");
            JsonNode root = null;
            for (JsonNode m : output) {
                if (!m.has("tk:embedded-id-path")) {
                    root = m;
                }
            }
            assertNotNull(root);
            assertEquals(Set.of("skipped:blank", "empty"), values(root, "tk:ocr-warnings"));
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

    /** A white picture; the second has a black block. */
    private static byte[] png(int index) throws Exception {
        BufferedImage image = new BufferedImage(200, 200, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < 200; x++) {
            for (int y = 0; y < 200; y++) {
                image.setRGB(x, y, index == 2 && x < 50 ? 0 : 0xFFFFFF);
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    private static Set<String> values(JsonNode node, String field) {
        Set<String> values = new HashSet<>();
        JsonNode value = node.get(field);
        if (value != null && value.isArray()) {
            value.forEach(v -> values.add(v.asText()));
        } else if (value != null) {
            values.add(value.asText());
        }
        return values;
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
