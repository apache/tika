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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.annotation.TikaComponent;
import org.apache.tika.config.ParseContextConfig;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.inference.locator.Locators;
import org.apache.tika.inference.locator.PaginatedLocator;
import org.apache.tika.inference.locator.TemporalLocator;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.metadata.TikaPagedText;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.inference.Engine;
import org.apache.tika.parser.inference.InferenceBinding;
import org.apache.tika.parser.inference.InferenceTask;
import org.apache.tika.parser.inference.InferenceUnit;
import org.apache.tika.parser.inference.InputKind;
import org.apache.tika.parser.inference.MediaConfig;
import org.apache.tika.parser.inference.Modality;

/**
 * The {@code embed} task. Media: see {@link #embedMedia}. Images and pages: one vector chunk per unit, written on the
 * unit's destination with the page locator when the unit is a page or has one. Text: the
 * unit's text is chunked and every chunk gets a vector, on the unit's own document.
 * Requests are filled to the engine's batch size; every chunk names the binding as its
 * producer.
 */
@TikaComponent(name = "embed", spi = false)
public class EmbedTask implements InferenceTask {

    private static final Logger LOG = LoggerFactory.getLogger(EmbedTask.class);

    /** The one segmenter; tests swap in a fake. */
    static MediaSegmenter segmenter = new FfmpegSegmenter();

    @Override
    public void validate(InferenceBinding binding, Engine engine) throws TikaConfigException {
        if (!(engine instanceof EmbeddingEngine)) {
            throw new TikaConfigException("task \"embed\" needs an embedding engine; \""
                    + binding.getEngine() + "\" is not one");
        }
        if (binding.getInput() == InputKind.MEDIA) {
            if (!((EmbeddingEngine) engine).supportsMedia()) {
                throw new TikaConfigException("binding \"" + binding.getId() + "\" is on MEDIA "
                        + "but engine \"" + binding.getEngine() + "\" does not embed audio or video");
            }
            if (binding.getModality() != Modality.AUDIO && binding.getModality() != Modality.VISUAL) {
                throw new TikaConfigException("binding \"" + binding.getId() + "\" is on MEDIA "
                        + "and needs \"modality\": \"audio\" or \"visual\"");
            }
            if (!segmenter.available()) {
                // an external tool, so stand by like OCR without tesseract: warn once, skip at run
                LOG.warn("binding \"{}\" is on MEDIA but ffmpeg/ffprobe are not on the PATH; "
                        + "audio and video will be skipped until they are", binding.getId());
            }
        }
    }

    @Override
    public void run(InferenceBinding binding, List<InferenceUnit> units, Engine engine,
                    ParseContext context) throws IOException, TikaException {
        EmbeddingEngine embedder = (EmbeddingEngine) engine;
        if (binding.getInput() == InputKind.TEXT) {
            embedText(binding, units, embedder, context);
            return;
        }
        if (binding.getInput() == InputKind.MEDIA) {
            embedMedia(binding, units, embedder, context);
            return;
        }
        int batchSize = Math.max(1, embedder.getMaxBatchSize());
        TikaException first = null;
        for (int start = 0; start < units.size(); start += batchSize) {
            List<InferenceUnit> batch = units.subList(start, Math.min(start + batchSize, units.size()));
            try {
                embed(binding, batch, embedder, context);
            } catch (TikaException e) {
                if (batch.size() == 1) {
                    first = first == null ? e : first;
                    continue;
                }
                // one bad image fails a whole batch; retry singly so the rest keep their vectors
                for (InferenceUnit unit : batch) {
                    try {
                        embed(binding, List.of(unit), embedder, context);
                    } catch (TikaException single) {
                        first = first == null ? single : first;
                    }
                }
            }
        }
        if (first != null) {
            throw first;
        }
    }

    /** A chunk of text and the document it belongs to. */
    private record Owned(Chunk chunk, Metadata owner) {
    }

    /**
     * Chunks every unit, then fills each request with the next {@code maxBatchSize} chunks in
     * order, so one request may hold a container's last chunks and an attachment's first.
     * {@code maxChunks} caps the chunks of the whole list. A rejected request is retried one
     * document at a time.
     */
    private static void embedText(InferenceBinding binding, List<InferenceUnit> units,
                                  EmbeddingEngine embedder, ParseContext context)
            throws IOException, TikaException {
        List<Owned> owned = new ArrayList<>();
        for (InferenceUnit unit : units) {
            String text = unit.getText();
            List<int[]> spans = binding.getChunker() == null
                    ? List.of(new int[]{0, text.length()}) : binding.getChunker().spans(text);
            for (int[] span : spans) {
                Chunk chunk = new Chunk(text.substring(span[0], span[1]), span[0], span[1]);
                chunk.setProducer(binding.getId());
                chunk.setModality(Modality.TEXT);
                owned.add(new Owned(chunk, unit.getDestination()));
            }
        }
        if (binding.getMaxChunks() >= 0 && owned.size() > binding.getMaxChunks()) {
            owned = owned.subList(0, binding.getMaxChunks());
        }
        int batchSize = Math.max(1, embedder.getMaxBatchSize());
        TikaException first = null;
        List<Owned> embedded = new ArrayList<>();
        for (int start = 0; start < owned.size(); start += batchSize) {
            List<Owned> batch = owned.subList(start, Math.min(start + batchSize, owned.size()));
            try {
                vectors(batch, embedder, context);
                embedded.addAll(batch);
            } catch (TikaException e) {
                if (batch.size() == 1) {
                    first = first == null ? e : first;
                    continue;
                }
                for (List<Owned> byOwner : byOwner(batch)) {
                    try {
                        vectors(byOwner, embedder, context);
                        embedded.addAll(byOwner);
                    } catch (TikaException single) {
                        first = first == null ? single : first;
                    }
                }
            }
        }
        for (List<Owned> byOwner : byOwner(embedded)) {
            List<Chunk> chunks = new ArrayList<>(byOwner.size());
            for (Owned o : byOwner) {
                chunks.add(o.chunk());
            }
            ChunkTarget.self(byOwner.get(0).owner())
                    .write(chunks, TikaCoreProperties.TIKA_CHUNKS.getName());
        }
        if (first != null) {
            throw first;
        }
    }

    private static void vectors(List<Owned> batch, EmbeddingEngine embedder,
                                ParseContext context) throws IOException, TikaException {
        List<String> texts = new ArrayList<>(batch.size());
        for (Owned o : batch) {
            texts.add(o.chunk().getText());
        }
        List<float[]> vectors = embedder.embedTexts(texts, context);
        for (int i = 0; i < batch.size(); i++) {
            batch.get(i).chunk().setVector(vectors.get(i));
        }
    }

    /** Runs of chunks sharing a document, in first-seen order. */
    private static List<List<Owned>> byOwner(List<Owned> owned) {
        List<List<Owned>> groups = new ArrayList<>();
        Map<Metadata, List<Owned>> byOwner = new IdentityHashMap<>();
        for (Owned o : owned) {
            List<Owned> group = byOwner.get(o.owner());
            if (group == null) {
                group = new ArrayList<>();
                byOwner.put(o.owner(), group);
                groups.add(group);
            }
            group.add(o);
        }
        return groups;
    }

    /**
     * Cuts every unit on the document's {@code media} grid and embeds one channel per cell;
     * the cell's id is the chunk's correlator, so a second binding on the other channel writes
     * a chunk that groups with this one.
     * <p>
     * Contract, per unit: a unit without the channel is skipped; a unit whose probe fails is
     * reported once and the next unit runs; a request the engine rejects is retried one cell at
     * a time, re-reading the cut files, never re-cutting; whatever was embedded before any
     * failure is written on the unit before the failure is reported; the unit's temp dir is
     * gone on every exit. A {@link MediaSegmenter.Timeout} (the parse budget ran out under the
     * tool) stops the whole task after that write, so an exhausted budget does not iterate
     * every remaining cell. An {@link IOException} is infrastructure and stops the task the
     * same way, after the write.
     */
    private static void embedMedia(InferenceBinding binding, List<InferenceUnit> units,
                                   EmbeddingEngine embedder, ParseContext context)
            throws IOException, TikaException {
        MediaConfig config;
        try {
            config = ParseContextConfig.getConfig(context, "media", MediaConfig.class,
                    new MediaConfig());
            // a programmatic block skipped the loader's check
            config.initialize();
        } catch (TikaConfigException e) {
            throw new TikaException("invalid \"media\" in parse-context", e);
        }
        if (!segmenter.available()) {
            for (InferenceUnit unit : units) {
                unit.getDestination().add(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING,
                        "inference binding " + binding.getId()
                                + ": skipped, ffmpeg/ffprobe not on the PATH");
            }
            return;
        }
        Modality modality = binding.getModality();
        int cap = config.getMaxSegments() >= 0 ? config.getMaxSegments() : Integer.MAX_VALUE;
        if (binding.getMaxChunks() >= 0) {
            cap = Math.min(cap, binding.getMaxChunks());
        }
        int batchSize = Math.max(1, embedder.getMaxBatchSize());
        TikaException first = null;
        for (InferenceUnit unit : units) {
            MediaSegmenter.Probe probe;
            try {
                probe = segmenter.probe(unit.getPath(), context);
            } catch (MediaSegmenter.Timeout t) {
                throw t;
            } catch (TikaException e) {
                // one unreadable file does not cost the others their vectors
                first = first == null ? e : first;
                continue;
            }
            if (modality == Modality.AUDIO ? !probe.hasAudio() : !probe.hasVideo()) {
                continue;
            }
            List<MediaSegmenter.Cell> cells = MediaSegmenter.cells(probe.durationMs(), config, cap);
            Path dir = Files.createTempDirectory("tika-media-");
            List<Chunk> chunks = new ArrayList<>();
            try {
                for (int start = 0; start < cells.size(); start += batchSize) {
                    List<MediaSegmenter.Cell> batch =
                            cells.subList(start, Math.min(start + batchSize, cells.size()));
                    try {
                        chunks.addAll(embedCells(binding, unit, batch, modality, dir, embedder,
                                context));
                    } catch (MediaSegmenter.Timeout t) {
                        throw t;
                    } catch (TikaException e) {
                        if (batch.size() == 1) {
                            first = first == null ? e : first;
                            continue;
                        }
                        for (MediaSegmenter.Cell cell : batch) {
                            try {
                                chunks.addAll(embedCells(binding, unit, List.of(cell), modality,
                                        dir, embedder, context));
                            } catch (MediaSegmenter.Timeout t) {
                                throw t;
                            } catch (TikaException single) {
                                first = first == null ? single : first;
                            }
                        }
                    }
                }
            } catch (IOException | RuntimeException e) {
                write(unit, chunks, e);
                throw e;
            } catch (MediaSegmenter.Timeout t) {
                write(unit, chunks, t);
                throw t;
            } finally {
                deleteTree(dir);
            }
            write(unit, chunks, null);
        }
        if (first != null) {
            throw first;
        }
    }

    /** Writes what was embedded; a write failure rides on the failure already in flight. */
    private static void write(InferenceUnit unit, List<Chunk> chunks, Exception inFlight)
            throws IOException {
        if (chunks.isEmpty()) {
            return;
        }
        try {
            ChunkTarget.of(unit).write(chunks, TikaCoreProperties.TIKA_CHUNKS.getName());
        } catch (IOException e) {
            if (inFlight == null) {
                throw e;
            }
            inFlight.addSuppressed(e);
        }
    }

    private static void deleteTree(Path dir) {
        try (Stream<Path> paths = Files.list(dir)) {
            paths.forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // best effort; the directory is under the JVM's temp dir
                }
            });
            Files.deleteIfExists(dir);
        } catch (IOException ignored) {
            // as above
        }
    }

    /** One request for a batch of cells; the cut files are deleted only once it succeeded. */
    private static List<Chunk> embedCells(InferenceBinding binding, InferenceUnit unit,
                                          List<MediaSegmenter.Cell> cells, Modality modality,
                                          Path dir, EmbeddingEngine embedder,
                                          ParseContext context)
            throws IOException, TikaException {
        List<MediaInput> inputs = new ArrayList<>(cells.size());
        List<Path> cuts = new ArrayList<>(cells.size());
        String mime = MediaSegmenter.mimeType(modality);
        for (MediaSegmenter.Cell cell : cells) {
            Path cut = segmenter.cut(unit.getPath(), cell, modality, dir, context);
            if (binding.getMaxBytes() >= 0 && Files.size(cut) > binding.getMaxBytes()) {
                Files.deleteIfExists(cut);
                throw new TikaException("segment " + cell.index() + " is over maxBytes");
            }
            inputs.add(new MediaInput(Files.readAllBytes(cut), mime, modality));
            cuts.add(cut);
        }
        List<float[]> vectors = embedder.embedMedia(inputs, context);
        for (Path cut : cuts) {
            Files.deleteIfExists(cut);
        }
        List<Chunk> chunks = new ArrayList<>(cells.size());
        for (int i = 0; i < cells.size(); i++) {
            MediaSegmenter.Cell cell = cells.get(i);
            Chunk chunk = new Chunk(null, new Locators()
                    .addTemporal(new TemporalLocator(cell.startMs(), cell.endMs())));
            chunk.setVector(vectors.get(i));
            chunk.setProducer(binding.getId());
            chunk.setModality(modality);
            chunk.setCorrelator(MediaSegmenter.correlator(unit.getTargetIdPath(), cell));
            chunks.add(chunk);
        }
        return chunks;
    }

    private static void embed(InferenceBinding binding, List<InferenceUnit> batch,
                              EmbeddingEngine embedder, ParseContext context)
            throws IOException, TikaException {
        List<byte[]> images = new ArrayList<>(batch.size());
        List<String> mimeTypes = new ArrayList<>(batch.size());
        for (InferenceUnit unit : batch) {
            images.add(unit.getBytes());
            mimeTypes.add(unit.getType().getBaseType().toString());
        }
        List<float[]> vectors = embedder.embedImages(images, mimeTypes, context);
        for (int i = 0; i < batch.size(); i++) {
            InferenceUnit unit = batch.get(i);
            Locators locators = new Locators();
            String page = unit.getTarget().get(TikaPagedText.PAGE_NUMBER);
            if (unit.getPage() > 0) {
                locators.addPaginated(new PaginatedLocator(unit.getPage()));
            } else if (page != null) {
                locators.addPaginated(new PaginatedLocator(Integer.parseInt(page)));
            }
            Chunk chunk = new Chunk(null, locators);
            chunk.setVector(vectors.get(i));
            chunk.setProducer(binding.getId());
            chunk.setModality(binding.getModality());
            ChunkTarget.of(unit).write(List.of(chunk), TikaCoreProperties.TIKA_CHUNKS.getName());
        }
    }
}
