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
import java.util.List;

import org.apache.tika.annotation.TikaComponent;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.inference.locator.Locators;
import org.apache.tika.inference.locator.PaginatedLocator;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.metadata.TikaPagedText;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.inference.Engine;
import org.apache.tika.parser.inference.InferenceBinding;
import org.apache.tika.parser.inference.InferenceTask;
import org.apache.tika.parser.inference.InferenceUnit;
import org.apache.tika.parser.inference.InputKind;

/**
 * The {@code embed} task: one vector chunk per unit, written where the unit's chunks belong
 * (its parent for an inline picture or a page render, itself otherwise), with the page
 * locator when the unit is a page or has one. Units go to the engine in batches of its size.
 */
@TikaComponent(name = "embed", spi = false)
public class EmbedTask implements InferenceTask {

    @Override
    public void validate(InferenceBinding binding, Engine engine) throws TikaConfigException {
        if (!(engine instanceof EmbeddingEngine)) {
            throw new TikaConfigException("task \"embed\" needs an embedding engine; \""
                    + binding.getEngine() + "\" is not one");
        }
        if (binding.getInput() != InputKind.IMAGES && binding.getInput() != InputKind.PAGES) {
            throw new TikaConfigException("task \"embed\" takes IMAGES or PAGES; binding \""
                    + binding.getId() + "\" is on " + binding.getInput());
        }
    }

    @Override
    public void run(InferenceBinding binding, List<InferenceUnit> units, Engine engine,
                    ParseContext context) throws IOException, TikaException {
        EmbeddingEngine embedder = (EmbeddingEngine) engine;
        int batchSize = Math.max(1, embedder.getMaxBatchSize());
        TikaException first = null;
        for (int start = 0; start < units.size(); start += batchSize) {
            List<InferenceUnit> batch = units.subList(start, Math.min(start + batchSize, units.size()));
            try {
                embed(batch, embedder, context);
            } catch (TikaException e) {
                if (batch.size() == 1) {
                    first = first == null ? e : first;
                    continue;
                }
                // one bad image fails a whole batch; retry singly so the rest keep their vectors
                for (InferenceUnit unit : batch) {
                    try {
                        embed(List.of(unit), embedder, context);
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

    private static void embed(List<InferenceUnit> batch, EmbeddingEngine embedder,
                              ParseContext context) throws IOException, TikaException {
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
            ChunkTarget.resolve(unit.getTarget(), unit.getParent())
                    .write(List.of(chunk), TikaCoreProperties.TIKA_CHUNKS.getName());
        }
    }
}
