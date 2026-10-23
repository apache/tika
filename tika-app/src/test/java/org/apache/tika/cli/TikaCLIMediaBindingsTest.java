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
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.apache.tika.http.TikaTestHttpServer;
import org.apache.tika.inference.Chunk;
import org.apache.tika.inference.ChunkSerializer;
import org.apache.tika.parser.inference.Modality;
import org.apache.tika.utils.ProcessUtils;

/**
 * The consumer's path for media: a video on the command line, two bindings on one Jina engine
 * (picture and audio), and -J shows two chunks per 30 s segment sharing the segment's id.
 * Needs ffmpeg on the PATH; the clip is synthesized.
 */
public class TikaCLIMediaBindingsTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @TempDir
    Path tmp;

    private static String response(int n) {
        StringBuilder sb = new StringBuilder("{\"data\":[");
        for (int i = 0; i < n; i++) {
            sb.append(i > 0 ? "," : "").append("{\"index\":").append(i)
                    .append(",\"embedding\":[").append(i).append(".0,0.5]}");
        }
        return sb.append("]}").toString();
    }

    @Test
    public void testSegmentsCarryBothVectors() throws Exception {
        assumeTrue(answers("ffmpeg") && answers("ffprobe"), "ffmpeg not on the PATH");
        Path mp4 = tmp.resolve("clip.mp4");
        assertEquals(0, ProcessUtils.execute(new ProcessBuilder("ffmpeg", "-loglevel", "error",
                "-y", "-f", "lavfi", "-i", "testsrc=duration=65:size=160x120:rate=5",
                "-f", "lavfi", "-i", "sine=frequency=440:duration=65",
                "-c:v", "libx264", "-preset", "ultrafast", "-c:a", "aac", "-shortest",
                mp4.toString()), 60_000, 10_000, 10_000).getExitValue());
        try (TikaTestHttpServer server = new TikaTestHttpServer()) {
            // 65 s at 25/5 is three cells; one request per binding at the default batch size
            server.enqueue(new TikaTestHttpServer.MockResponse(200, response(3)));
            server.enqueue(new TikaTestHttpServer.MockResponse(200, response(3)));
            Path config = tmp.resolve("tika-config.json");
            Files.writeString(config, """
                    {
                      "engines": {
                        "jina": { "openai-embedding-engine": { "baseUrl": "BASE_URL", "apiKey": "x",
                                  "model": "jina-embeddings-v5-omni-small", "mediaInput": "object",
                                  "requestParameters": { "task": "retrieval.passage" } } }
                      },
                      "inference": [
                        { "id": "jina-video", "engine": "jina", "input": "MEDIA", "modality": "visual" },
                        { "id": "jina-audio", "engine": "jina", "input": "MEDIA", "modality": "audio" }
                      ]
                    }
                    """.replace("BASE_URL", server.url()));
            JsonNode output = MAPPER.readTree(run("--config=" + config, "-J", mp4.toUri().toString()));
            assertEquals(2, server.getRequestCount(), "one request per channel");
            JsonNode video = MAPPER.readTree(server.takeRequest().body());
            assertEquals(3, video.get("input").size());
            assertTrue(video.get("input").get(0).get("video").asText().startsWith("data:video/mp4;base64,"));
            assertEquals("retrieval.passage", video.get("task").asText());
            JsonNode audio = MAPPER.readTree(server.takeRequest().body());
            assertTrue(audio.get("input").get(2).get("audio").asText().startsWith("data:audio/ogg;base64,"));

            JsonNode root = output.get(0);
            assertNotNull(root.get("tk:chunks"), root.toString());
            List<Chunk> chunks = ChunkSerializer.fromJson(root.get("tk:chunks").asText());
            assertEquals(6, chunks.size(), "three cells, two channels");
            Chunk video2 = chunks.get(1);
            Chunk audio2 = chunks.get(4);
            assertEquals(20000, video2.getLocators().getTemporal().get(0).getStartMs());
            assertEquals(45000, video2.getLocators().getTemporal().get(0).getEndMs());
            assertEquals("t:20000-45000", video2.getCorrelator());
            assertEquals(video2.getCorrelator(), audio2.getCorrelator());
            assertEquals(Modality.VISUAL, video2.getModality());
            assertEquals(Modality.AUDIO, audio2.getModality());
            assertEquals("jina-audio", audio2.getProducer());
            assertEquals(1.0f, video2.getVector()[0]);
            assertEquals(1.0f, audio2.getVector()[0]);
        }
    }

    private static boolean answers(String tool) {
        try {
            return ProcessUtils.execute(new ProcessBuilder(tool, "-version"), 10_000, 1000, 1000)
                    .getExitValue() == 0;
        } catch (IOException e) {
            return false;
        }
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
