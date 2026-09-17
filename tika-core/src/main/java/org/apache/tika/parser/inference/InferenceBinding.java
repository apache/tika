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
package org.apache.tika.parser.inference;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TIFF;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.enricher.ContentEnrichers;

/** One entry of the {@code "inference"} list: engine, input, tasks, filters, budget. */
public final class InferenceBinding {

    /** Below this, in either dimension, an image is a spacer, not a picture. */
    public static final int DEFAULT_MIN_PIXELS = ContentEnrichers.MIN_PIXELS;

    private final String id;
    private final String engine;
    private final InputKind input;
    private final List<String> tasks;
    private final Set<MediaType> include;
    private final Set<MediaType> exclude;
    private final int maxChunks;
    private final long maxBytes;
    private final boolean enabled;
    private final TextChunker chunker;
    private final int minWidth;
    private final int minHeight;

    public InferenceBinding(String id, String engine, InputKind input, List<String> tasks,
                            Set<MediaType> include, Set<MediaType> exclude, int maxChunks,
                            long maxBytes, boolean enabled) {
        this(id, engine, input, tasks, include, exclude, maxChunks, maxBytes, enabled, null);
    }

    public InferenceBinding(String id, String engine, InputKind input, List<String> tasks,
                            Set<MediaType> include, Set<MediaType> exclude, int maxChunks,
                            long maxBytes, boolean enabled, TextChunker chunker) {
        this(id, engine, input, tasks, include, exclude, maxChunks, maxBytes, enabled, chunker,
                DEFAULT_MIN_PIXELS, DEFAULT_MIN_PIXELS);
    }

    public InferenceBinding(String id, String engine, InputKind input, List<String> tasks,
                            Set<MediaType> include, Set<MediaType> exclude, int maxChunks,
                            long maxBytes, boolean enabled, TextChunker chunker, int minWidth,
                            int minHeight) {
        this.chunker = chunker;
        this.minWidth = minWidth;
        this.minHeight = minHeight;
        this.id = id;
        this.engine = engine;
        this.input = input;
        this.tasks = List.copyOf(tasks);
        this.include = include == null ? Collections.emptySet() : Set.copyOf(include);
        this.exclude = exclude == null ? Collections.emptySet() : Set.copyOf(exclude);
        this.maxChunks = maxChunks;
        this.maxBytes = maxBytes;
        this.enabled = enabled;
    }

    public String getId() {
        return id;
    }

    public String getEngine() {
        return engine;
    }

    public InputKind getInput() {
        return input;
    }

    public List<String> getTasks() {
        return tasks;
    }

    /** Units this binding may enrich per document; -1 for no limit. */
    public int getMaxChunks() {
        return maxChunks;
    }

    /** Largest unit this binding accepts, in bytes; -1 for no limit. */
    public long getMaxBytes() {
        return maxBytes;
    }

    public boolean isEnabled() {
        return enabled;
    }

    /** How a TEXT binding cuts a document's text; null means the whole text is one chunk. */
    public TextChunker getChunker() {
        return chunker;
    }

    /** Narrowest image this binding takes, in pixels; images of unknown size are taken. */
    public int getMinWidth() {
        return minWidth;
    }

    public int getMinHeight() {
        return minHeight;
    }

    /**
     * As {@link #accepts(InputKind, MediaType)}, and for an image its recorded dimensions
     * ({@code tiff:ImageWidth}, {@code tiff:ImageLength}) must reach the minimum when known.
     */
    public boolean accepts(InputKind kind, MediaType type, Metadata target) {
        if (!accepts(kind, type)) {
            return false;
        }
        if (kind != InputKind.IMAGES || target == null) {
            return true;
        }
        Integer width = target.getInt(TIFF.IMAGE_WIDTH);
        Integer height = target.getInt(TIFF.IMAGE_LENGTH);
        return (width == null || width >= minWidth) && (height == null || height >= minHeight);
    }

    public boolean accepts(InputKind kind, MediaType type) {
        if (kind != input || type == null) {
            return false;
        }
        MediaType base = type.getBaseType();
        if (!include.isEmpty() && !matches(include, base)) {
            return false;
        }
        return !matches(exclude, base);
    }

    private static boolean matches(Set<MediaType> set, MediaType base) {
        for (MediaType t : set) {
            if (t.equals(base) || (t.getSubtype().equals("*") && t.getType().equals(base.getType()))) {
                return true;
            }
        }
        return false;
    }
}
