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

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.apache.tika.http.TikaTestHttpServer;
import org.apache.tika.inference.Chunk;
import org.apache.tika.inference.ChunkSerializer;

/**
 * The consumer's path for text: a TEXT binding with a chunker, and -J shows every document of
 * a zip with its own text vectors, all from one request that mixed the documents' chunks.
 */
public class TikaCLITextVectorsTest {

    /** Vector[0] is the input index, so placement is checkable. */
    private static final String EMBEDDING_RESPONSE = "{\"data\":["
            + "{\"index\":3,\"embedding\":[3.0,0.5]},"
            + "{\"index\":0,\"embedding\":[0.0,0.5]},"
            + "{\"index\":2,\"embedding\":[2.0,0.5]},"
            + "{\"index\":1,\"embedding\":[1.0,0.5]}]}";

    @TempDir
    Path tmp;

    @Test
    public void testOneRequestForTheTreeVectorsOnEachDocument() throws Exception {
        Path zip = tmp.resolve("two-notes.zip");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
            entry(out, "alpha.txt", "alpha is the first note");
            entry(out, "beta.txt", "beta is the second note");
        }
        try (TikaTestHttpServer server = new TikaTestHttpServer()) {
            server.enqueue(new TikaTestHttpServer.MockResponse(200, EMBEDDING_RESPONSE));
            Path config = tmp.resolve("tika-config.json");
            Files.writeString(config, """
                    {
                      "engines": {
                        "embedder": { "openai-embedding-engine": { "baseUrl": "BASE_URL", "model": "bge" } }
                      },
                      "inference": [
                        { "id": "text-vectors", "engine": "embedder", "input": "TEXT", "tasks": ["embed"],
                          "chunker": { "markdown-chunker": { "maxChunkChars": 2000, "overlapChars": 0 } } }
                      ]
                    }
                    """.replace("BASE_URL", server.url()));

            JsonNode output = new ObjectMapper().readTree(
                    run("--config=" + config, "-J", zip.toUri().toString()));

            assertEquals(3, output.size(), "the zip and its two notes");
            assertEquals(1, server.getRequestCount(), "one request for the whole tree");
            JsonNode request = new ObjectMapper().readTree(server.takeRequest().body());
            // the zip's own markdown content is two headings, so two chunks; one per note
            assertEquals(4, request.get("input").size(), "chunks of all three documents in one request");
            assertEquals("# alpha.txt", request.get("input").get(0).asText());
            assertEquals("alpha is the first note", request.get("input").get(2).asText());

            for (JsonNode document : output) {
                String content = text(document, "tk:content");
                List<Chunk> chunks = ChunkSerializer.fromJson(text(document, "tk:chunks"));
                boolean container = content.startsWith("# alpha.txt");
                assertEquals(container ? 2 : 1, chunks.size(), content);
                for (Chunk chunk : chunks) {
                    assertEquals("text-vectors", chunk.getProducer());
                    assertEquals(2, chunk.getVector().length);
                    assertTrue(content.contains(chunk.getText()), "the chunk is this document's own text");
                    int start = chunk.getLocators().getText().get(0).getStartOffset();
                    assertEquals(chunk.getText(), content.substring(start, start + chunk.getText().length()));
                }
                if (content.startsWith("beta")) {
                    assertEquals(3.0f, chunks.get(0).getVector()[0], "fourth input, placed by index");
                }
            }
        }
    }

    private static void entry(ZipOutputStream zip, String name, String text) throws Exception {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(text.getBytes(UTF_8));
        zip.closeEntry();
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
