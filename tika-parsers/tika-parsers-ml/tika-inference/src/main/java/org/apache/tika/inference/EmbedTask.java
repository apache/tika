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
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import org.apache.tika.annotation.TikaComponent;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.inference.locator.Locators;
import org.apache.tika.inference.locator.PaginatedLocator;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.metadata.TikaPagedText;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.inference.Engine;
import org.apache.tika.parser.inference.InferenceBinding;
import org.apache.tika.parser.inference.InferenceTask;
import org.apache.tika.parser.inference.InferenceUnit;
import org.apache.tika.parser.inference.InputKind;

/**
 * The {@code embed} task. Images and pages: one vector chunk per unit, written where the
 * unit's chunks belong (its parent for an inline picture or a page render, itself otherwise),
 * with the page locator when the unit is a page or has one. Text: the unit's text is chunked
 * and every chunk gets a vector, on the unit's own document. Requests are filled to the
 * engine's batch size; every chunk names the binding as its producer.
 */
@TikaComponent(name = "embed", spi = false)
public class EmbedTask implements InferenceTask {

    @Override
    public void validate(InferenceBinding binding, Engine engine) throws TikaConfigException {
        if (!(engine instanceof EmbeddingEngine)) {
            throw new TikaConfigException("task \"embed\" needs an embedding engine; \""
                    + binding.getEngine() + "\" is not one");
        }
        if (binding.getInput() == InputKind.MEDIA) {
            throw new TikaConfigException("task \"embed\" takes TEXT, IMAGES or PAGES; binding \""
                    + binding.getId() + "\" is on " + binding.getInput());
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
                owned.add(new Owned(chunk, unit.getTarget()));
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
            ChunkTarget.resolve(unit.getTarget(), unit.getParent())
                    .write(List.of(chunk), TikaCoreProperties.TIKA_CHUNKS.getName());
        }
    }
}
