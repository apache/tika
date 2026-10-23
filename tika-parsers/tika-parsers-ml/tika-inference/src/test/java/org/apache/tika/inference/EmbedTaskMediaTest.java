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
package org.apache.tika.inference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.apache.tika.config.ParseTimeout;
import org.apache.tika.config.TimeoutLimits;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.http.TikaTestHttpServer;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.inference.InferenceBinding;
import org.apache.tika.parser.inference.InferenceUnit;
import org.apache.tika.parser.inference.InputKind;
import org.apache.tika.parser.inference.MediaConfig;
import org.apache.tika.parser.inference.Modality;
import org.apache.tika.utils.ProcessUtils;

/** Needs ffmpeg on the PATH; the clip is synthesized, so nothing is checked in. The contract without ffmpeg: {@link EmbedTaskMediaContractTest}. */
public class EmbedTaskMediaTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private TikaTestHttpServer server;
    private OpenAIEmbeddingEngine engine;

    @TempDir
    Path tmp;

    @BeforeEach
    void setUp() throws Exception {
        assumeTrue(new FfmpegSegmenter().available(), "ffmpeg not on the PATH");
        server = new TikaTestHttpServer();
        engine = new OpenAIEmbeddingEngine();
        engine.setBaseUrl(server.url());
        engine.setModel("jina-embeddings-v5-omni-small");
        engine.setMediaInput(OpenAIEmbeddingEngine.MEDIA_INPUT_OBJECT);
        engine.setRequestParameters(Map.of("task", "retrieval.passage"));
        engine.setMaxBatchSize(2);
        engine.initialize();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (engine != null) {
            engine.close();
        }
        if (server != null) {
            server.shutdown();
        }
    }

    /** {@code seconds} of test pattern plus a tone; audio only when {@code video} is false. */
    private Path clip(int seconds, boolean video) throws Exception {
        Path out = tmp.resolve((video ? "clip" : "tone") + seconds + (video ? ".mp4" : ".m4a"));
        ProcessBuilder pb = video
                ? new ProcessBuilder("ffmpeg", "-loglevel", "error", "-y",
                        "-f", "lavfi", "-i", "testsrc=duration=" + seconds + ":size=160x120:rate=5",
                        "-f", "lavfi", "-i", "sine=frequency=440:duration=" + seconds,
                        "-c:v", "libx264", "-preset", "ultrafast", "-c:a", "aac", "-shortest",
                        out.toString())
                : new ProcessBuilder("ffmpeg", "-loglevel", "error", "-y",
                        "-f", "lavfi", "-i", "sine=frequency=440:duration=" + seconds,
                        "-c:a", "aac", out.toString());
        assertEquals(0, ProcessUtils.execute(pb, 60_000, 10_000, 10_000).getExitValue());
        return out;
    }

    private static String response(int n) {
        StringBuilder sb = new StringBuilder("{\"data\":[");
        for (int i = 0; i < n; i++) {
            int index = n - 1 - i;
            sb.append(i > 0 ? "," : "").append("{\"index\":").append(index)
                    .append(",\"embedding\":[").append(index).append(".0,0.5]}");
        }
        return sb.append("]}").toString();
    }

    private static InferenceBinding binding(String id, Modality modality) {
        return InferenceBinding.builder(id, "jina", InputKind.MEDIA).modality(modality).build();
    }

    private static InferenceUnit unit(Path file, String type) throws Exception {
        return new InferenceUnit(InputKind.MEDIA, MediaType.parse(type), new Metadata(), null, file);
    }

    /** Two bindings on the two channels write two chunks per cell that share the cell's id. */
    @Test
    public void testAudioAndVideoJoinOnTheCell() throws Exception {
        InferenceUnit unit = unit(clip(70, true), "video/mp4");
        // 70 s at 25/5: [0,25] [20,45] [40,65] [60,70]; batch size 2 -> two requests of 2 per channel
        for (int i = 0; i < 4; i++) {
            server.enqueue(new TikaTestHttpServer.MockResponse(200, response(2)));
        }
        EmbedTask task = new EmbedTask();
        InferenceBinding video = binding("jina-video", Modality.VISUAL);
        InferenceBinding audio = binding("jina-audio", Modality.AUDIO);
        task.validate(video, engine);
        task.run(video, List.of(unit), engine, new ParseContext());
        task.run(audio, List.of(unit), engine, new ParseContext());

        assertEquals(4, server.getRequestCount());
        JsonNode first = MAPPER.readTree(server.takeRequest().body());
        assertEquals("retrieval.passage", first.get("task").asText());
        assertEquals("jina-embeddings-v5-omni-small", first.get("model").asText());
        assertEquals(2, first.get("input").size());
        assertTrue(first.get("input").get(0).get("video").asText().startsWith("data:video/mp4;base64,"));
        server.takeRequest();
        JsonNode third = MAPPER.readTree(server.takeRequest().body());
        assertTrue(third.get("input").get(0).get("audio").asText().startsWith("data:audio/ogg;base64,"));

        List<Chunk> chunks = ChunkSerializer.fromJson(
                unit.getTarget().get(TikaCoreProperties.TIKA_CHUNKS));
        assertEquals(8, chunks.size(), "one chunk per cell per channel");
        Chunk video2 = chunks.get(1);
        Chunk audio2 = chunks.get(5);
        assertEquals(20000, video2.getLocators().getTemporal().get(0).getStartMs());
        assertEquals(45000, video2.getLocators().getTemporal().get(0).getEndMs());
        assertEquals(70000, chunks.get(3).getLocators().getTemporal().get(0).getEndMs());
        assertEquals("t:20000-45000", video2.getCorrelator());
        assertEquals(video2.getCorrelator(), audio2.getCorrelator(), "the cell's id groups them");
        assertEquals("jina-video", video2.getProducer());
        assertEquals(Modality.VISUAL, video2.getModality());
        assertEquals(Modality.AUDIO, audio2.getModality());
        assertEquals(1.0f, video2.getVector()[0], "placed by index");
        assertEquals(0.0f, chunks.get(6).getVector()[0], "second audio request, index 0");
    }

    /** The request's media block sets the grid; maxSegments cuts the tail. */
    @Test
    public void testMediaBlockSetsTheGrid() throws Exception {
        InferenceUnit unit = unit(clip(70, true), "video/mp4");
        server.enqueue(new TikaTestHttpServer.MockResponse(200, response(1)));
        MediaConfig config = new MediaConfig();
        config.getSegment().setSeconds(60);
        config.getSegment().setOverlap(0);
        config.setMaxSegments(1);
        ParseContext context = new ParseContext();
        context.set(MediaConfig.class, config);
        new EmbedTask().run(binding("v", Modality.VISUAL), List.of(unit), engine, context);
        List<Chunk> chunks = ChunkSerializer.fromJson(
                unit.getTarget().get(TikaCoreProperties.TIKA_CHUNKS));
        assertEquals(1, chunks.size());
        assertEquals(60000, chunks.get(0).getLocators().getTemporal().get(0).getEndMs());
        assertEquals(1, server.getRequestCount());
    }

    /** A file without the channel is skipped, not failed. */
    @Test
    public void testAudioOnlyFileSkipsTheVisualBinding() throws Exception {
        InferenceUnit unit = unit(clip(10, false), "audio/mp4");
        server.enqueue(new TikaTestHttpServer.MockResponse(200, response(1)));
        new EmbedTask().run(binding("v", Modality.VISUAL), List.of(unit), engine, new ParseContext());
        assertEquals(0, server.getRequestCount());
        assertNull(unit.getTarget().get(TikaCoreProperties.TIKA_CHUNKS));
        new EmbedTask().run(binding("a", Modality.AUDIO), List.of(unit), engine, new ParseContext());
        assertEquals(1, server.getRequestCount());
        assertEquals(1, ChunkSerializer.fromJson(
                unit.getTarget().get(TikaCoreProperties.TIKA_CHUNKS)).size());
    }

    /** Without mediaInput the engine refuses MEDIA at load. */
    @Test
    public void testValidateNeedsMediaInput() throws Exception {
        OpenAIEmbeddingEngine plain = new OpenAIEmbeddingEngine();
        plain.initialize();
        assertThrows(TikaConfigException.class,
                () -> new EmbedTask().validate(binding("a", Modality.AUDIO), plain));
        new EmbedTask().validate(binding("a", Modality.AUDIO), engine);
        plain.setMediaInput("blob");
        assertThrows(TikaConfigException.class, plain::initialize);
    }


    /** An exhausted parse budget stops the task at the first cut instead of failing every cell. */
    @Test
    public void testExhaustedBudgetStopsCutting() throws Exception {
        InferenceUnit unit = unit(clip(70, true), "video/mp4");
        ParseContext context = new ParseContext();
        context.set(ParseTimeout.class, ParseTimeout.start(new TimeoutLimits(1, 1000)));
        Thread.sleep(5);
        MediaSegmenter.Timeout t = assertThrows(MediaSegmenter.Timeout.class,
                () -> new EmbedTask().run(binding("v", Modality.VISUAL), List.of(unit), engine, context));
        assertTrue(t.getMessage().contains("time budget"), t.getMessage());
        assertEquals(0, server.getRequestCount());
        assertNull(unit.getTarget().get(TikaCoreProperties.TIKA_CHUNKS));
    }

    /** Without ffmpeg a document is skipped with a warning, not failed. */
    @Test
    public void testMissingFfmpegSkipsWithAWarning() throws Exception {
        InferenceUnit unit = unit(clip(10, true), "video/mp4");
        MediaSegmenter real = EmbedTask.segmenter;
        EmbedTask.segmenter = new EmbedTaskMediaContractTest.FakeSegmenter(false);
        try {
            new EmbedTask().validate(binding("v", Modality.VISUAL), engine);
            new EmbedTask().run(binding("v", Modality.VISUAL), List.of(unit), engine, new ParseContext());
        } finally {
            EmbedTask.segmenter = real;
        }
        assertEquals(0, server.getRequestCount());
        assertNull(unit.getTarget().get(TikaCoreProperties.TIKA_CHUNKS));
        assertTrue(unit.getTarget().get(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING)
                .contains("ffmpeg"));
    }

}
