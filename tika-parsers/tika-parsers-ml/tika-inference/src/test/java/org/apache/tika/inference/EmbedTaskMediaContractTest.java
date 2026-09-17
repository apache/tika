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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.apache.tika.exception.TikaException;
import org.apache.tika.http.TikaTestHttpServer;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.inference.InferenceBinding;
import org.apache.tika.parser.inference.InferenceUnit;
import org.apache.tika.parser.inference.InputKind;
import org.apache.tika.parser.inference.Modality;

/**
 * The media path's contract with a fake segmenter, so no ffmpeg: what is written, kept and
 * released on every exit of {@code EmbedTask.embedMedia}.
 */
public class EmbedTaskMediaContractTest {

    /** Probes every file as 70 s of audio and video; cuts are one-byte files. */
    static final class FakeSegmenter implements MediaSegmenter {
        final boolean available;
        final List<Path> cutDirs = new ArrayList<>();
        int cuts;
        Path failProbe;
        int timeoutOnCut = -1;
        int ioOnCut = -1;
        long durationMs = 70_000;

        FakeSegmenter(boolean available) {
            this.available = available;
        }

        @Override
        public boolean available() {
            return available;
        }

        @Override
        public Probe probe(Path media, ParseContext context) throws TikaException {
            if (media.equals(failProbe)) {
                throw new TikaException("ffprobe failed: not a media file");
            }
            return new Probe(durationMs, true, true);
        }

        @Override
        public Path cut(Path media, Cell cell, Modality modality, Path dir, ParseContext context)
                throws IOException, TikaException {
            if (!cutDirs.contains(dir)) {
                cutDirs.add(dir);
            }
            Path out = dir.resolve(cell.index() + ".bin");
            if (Files.exists(out)) {
                return out;
            }
            if (cell.index() == timeoutOnCut) {
                throw new Timeout("budget gone on " + cell.index());
            }
            if (cell.index() == ioOnCut) {
                throw new IOException("disk gone on " + cell.index());
            }
            cuts++;
            return Files.write(out, new byte[]{(byte) cell.index()});
        }
    }

    private TikaTestHttpServer server;
    private OpenAIEmbeddingEngine engine;
    private FakeSegmenter fake;
    private MediaSegmenter real;

    @TempDir
    Path tmp;

    @BeforeEach
    void setUp() throws Exception {
        server = new TikaTestHttpServer();
        engine = new OpenAIEmbeddingEngine();
        engine.setBaseUrl(server.url());
        engine.setModel("m");
        engine.setMediaInput(OpenAIEmbeddingEngine.MEDIA_INPUT_OBJECT);
        engine.setMaxBatchSize(2);
        engine.initialize();
        fake = new FakeSegmenter(true);
        real = EmbedTask.segmenter;
        EmbedTask.segmenter = fake;
    }

    @AfterEach
    void tearDown() throws Exception {
        EmbedTask.segmenter = real;
        engine.close();
        server.shutdown();
    }

    private static String response(int n) {
        StringBuilder sb = new StringBuilder("{\"data\":[");
        for (int i = 0; i < n; i++) {
            sb.append(i > 0 ? "," : "").append("{\"index\":").append(i)
                    .append(",\"embedding\":[").append(i).append(".0]}");
        }
        return sb.append("]}").toString();
    }

    private static InferenceBinding binding() {
        return InferenceBinding.builder("v", "jina", InputKind.MEDIA).modality(Modality.VISUAL).build();
    }

    private InferenceUnit unit(String name) throws Exception {
        Path file = Files.write(tmp.resolve(name), new byte[]{1});
        return new InferenceUnit(InputKind.MEDIA, MediaType.video("mp4"), new Metadata(), null, file);
    }

    private static List<Chunk> chunks(InferenceUnit unit) throws Exception {
        String json = unit.getTarget().get(TikaCoreProperties.TIKA_CHUNKS);
        return json == null ? List.of() : ChunkSerializer.fromJson(json);
    }

    private void assertReleased() {
        assertFalse(fake.cutDirs.isEmpty());
        for (Path dir : fake.cutDirs) {
            assertFalse(Files.exists(dir), "temp dir released: " + dir);
        }
    }

    /** 70 s at 30/5 is three cells: [0,30] [25,55] [50,70]; batch 2 means requests of 2 and 1. */
    @Test
    public void testHappyPathReleasesTheDir() throws Exception {
        server.enqueue(new TikaTestHttpServer.MockResponse(200, response(2)));
        server.enqueue(new TikaTestHttpServer.MockResponse(200, response(1)));
        InferenceUnit unit = unit("a.mp4");
        new EmbedTask().run(binding(), List.of(unit), engine, new ParseContext());
        assertEquals(3, chunks(unit).size());
        assertEquals(3, fake.cuts);
        assertReleased();
    }

    /** A rejected request is retried one cell at a time from the same cut files. */
    @Test
    public void testRejectedBatchRetriesSinglyWithoutRecutting() throws Exception {
        server.enqueue(new TikaTestHttpServer.MockResponse(400, "{\"error\":\"too big\"}"));
        server.enqueue(new TikaTestHttpServer.MockResponse(200, response(1)));
        server.enqueue(new TikaTestHttpServer.MockResponse(400, "{\"error\":\"bad cell\"}"));
        server.enqueue(new TikaTestHttpServer.MockResponse(200, response(1)));
        InferenceUnit unit = unit("a.mp4");
        TikaException e = assertThrows(TikaException.class,
                () -> new EmbedTask().run(binding(), List.of(unit), engine, new ParseContext()));
        assertTrue(e.getMessage().contains("bad cell"), e.getMessage());
        List<Chunk> chunks = chunks(unit);
        assertEquals(2, chunks.size(), "the good cells keep their vectors");
        assertEquals(0, chunks.get(0).getLocators().getTemporal().get(0).getStartMs());
        assertEquals(50_000, chunks.get(1).getLocators().getTemporal().get(0).getStartMs());
        assertEquals(3, fake.cuts, "no cell is cut twice");
        assertEquals(4, server.getRequestCount());
        assertReleased();
    }

    /** The budget runs out under the tool on cell 2: cells 0-1 are written, nothing more is tried. */
    @Test
    public void testTimeoutKeepsWhatWasEmbedded() throws Exception {
        server.enqueue(new TikaTestHttpServer.MockResponse(200, response(2)));
        fake.timeoutOnCut = 2;
        InferenceUnit first = unit("a.mp4");
        InferenceUnit second = unit("b.mp4");
        MediaSegmenter.Timeout t = assertThrows(MediaSegmenter.Timeout.class,
                () -> new EmbedTask().run(binding(), List.of(first, second), engine, new ParseContext()));
        assertTrue(t.getMessage().contains("budget"), t.getMessage());
        assertEquals(2, chunks(first).size(), "paid for, so written");
        assertNull(second.getTarget().get(TikaCoreProperties.TIKA_CHUNKS), "no later unit runs");
        assertEquals(1, server.getRequestCount(), "no single retries after a timeout");
        assertReleased();
    }

    /** An unreadable file costs only itself; the units after it still run. */
    @Test
    public void testProbeFailureSkipsOnlyThatUnit() throws Exception {
        for (int i = 0; i < 2; i++) {
            server.enqueue(new TikaTestHttpServer.MockResponse(200, response(2)));
            server.enqueue(new TikaTestHttpServer.MockResponse(200, response(1)));
        }
        InferenceUnit good = unit("a.mp4");
        InferenceUnit bad = unit("b.mp4");
        InferenceUnit alsoGood = unit("c.mp4");
        fake.failProbe = bad.getPath();
        TikaException e = assertThrows(TikaException.class, () -> new EmbedTask().run(binding(),
                List.of(good, bad, alsoGood), engine, new ParseContext()));
        assertTrue(e.getMessage().contains("ffprobe failed"), e.getMessage());
        assertEquals(3, chunks(good).size());
        assertEquals(3, chunks(alsoGood).size());
        assertNull(bad.getTarget().get(TikaCoreProperties.TIKA_CHUNKS));
        assertReleased();
    }

    /** Infrastructure failure mid-unit: the batch already embedded is written, then it propagates. */
    @Test
    public void testIOExceptionWritesWhatWasEmbedded() throws Exception {
        server.enqueue(new TikaTestHttpServer.MockResponse(200, response(2)));
        fake.ioOnCut = 2;
        InferenceUnit unit = unit("a.mp4");
        IOException e = assertThrows(IOException.class,
                () -> new EmbedTask().run(binding(), List.of(unit), engine, new ParseContext()));
        assertTrue(e.getMessage().contains("disk gone"));
        assertEquals(2, chunks(unit).size());
        assertReleased();
    }

    /** A cut over maxBytes is deleted at once and fails only its own cell. */
    @Test
    public void testOverMaxBytesCellIsDroppedAndDeleted() throws Exception {
        server.enqueue(new TikaTestHttpServer.MockResponse(200, response(1)));
        InferenceBinding tiny = InferenceBinding.builder("v", "jina", InputKind.MEDIA)
                .modality(Modality.VISUAL).maxBytes(0).maxChunks(1).build();
        InferenceUnit unit = unit("a.mp4");
        TikaException e = assertThrows(TikaException.class,
                () -> new EmbedTask().run(tiny, List.of(unit), engine, new ParseContext()));
        assertTrue(e.getMessage().contains("over maxBytes"), e.getMessage());
        assertEquals(0, server.getRequestCount());
        assertNull(unit.getTarget().get(TikaCoreProperties.TIKA_CHUNKS));
        assertReleased();
    }

    /** maxChunks on the binding and maxSegments in the block both cap cells; the smaller wins. */
    @Test
    public void testCapsBoundACLaimedDuration() throws Exception {
        server.enqueue(new TikaTestHttpServer.MockResponse(200, response(2)));
        fake.durationMs = Long.MAX_VALUE / 4;
        InferenceBinding two = InferenceBinding.builder("v", "jina", InputKind.MEDIA)
                .modality(Modality.VISUAL).maxChunks(2).build();
        InferenceUnit unit = unit("a.mp4");
        new EmbedTask().run(two, List.of(unit), engine, new ParseContext());
        assertEquals(2, chunks(unit).size());
        assertEquals(2, fake.cuts);
        assertSame(fake, EmbedTask.segmenter);
    }
}
