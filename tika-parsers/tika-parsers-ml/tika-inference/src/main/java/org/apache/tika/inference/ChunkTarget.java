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
import java.util.List;
import java.util.Set;

import org.apache.tika.extractor.ParentMetadata;
import org.apache.tika.inference.locator.EmbeddedLocator;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;

/**
 * Where the chunks produced for a document land. A picture in the body of a docx or an
 * email, or a rendering of a page, is part of its parent, so its chunks go onto the parent
 * with an {@link EmbeddedLocator} naming the child. An attachment, a top-level file, or a
 * child parsed outside the recursive wrapper keeps its own.
 */
public final class ChunkTarget {

    private static final Set<String> LIFTED = Set.of(
            TikaCoreProperties.EmbeddedResourceType.INLINE.name(),
            TikaCoreProperties.EmbeddedResourceType.RENDERING.name());

    private final Metadata metadata;
    private final EmbeddedLocator locator;

    private ChunkTarget(Metadata metadata, EmbeddedLocator locator) {
        this.metadata = metadata;
        this.locator = locator;
    }

    public static ChunkTarget resolve(Metadata target, ParseContext context) {
        ParentMetadata parent = context.get(ParentMetadata.class);
        String idPath = target.get(TikaCoreProperties.EMBEDDED_ID_PATH);
        String type = target.get(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE);
        if (parent == null || idPath == null || type == null || !LIFTED.contains(type)) {
            return new ChunkTarget(target, null);
        }
        return new ChunkTarget(parent.getMetadata(),
                new EmbeddedLocator(idPath, target.get(TikaCoreProperties.RESOURCE_NAME_KEY)));
    }

    /** The target keeps its own chunks. */
    public static ChunkTarget self(Metadata target) {
        return new ChunkTarget(target, null);
    }

    public Metadata getMetadata() {
        return metadata;
    }

    /** Names the child the chunks came from; null when they stay on that child. */
    public EmbeddedLocator getLocator() {
        return locator;
    }

    /** Tags the chunks with the locator, if any, and appends them to the field. */
    public void write(List<Chunk> chunks, String field) throws IOException {
        if (locator != null) {
            for (Chunk chunk : chunks) {
                if (chunk.getLocators() != null) {
                    chunk.getLocators().addEmbedded(locator);
                }
            }
        }
        ChunkSerializer.mergeInto(metadata, chunks, field);
    }
}
