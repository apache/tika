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
import java.util.Objects;
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
    private final Modality modality;

    private InferenceBinding(Builder b) {
        this.id = b.id;
        this.engine = b.engine;
        this.input = b.input;
        this.tasks = List.copyOf(b.tasks);
        this.include = b.include == null ? Collections.emptySet() : Set.copyOf(b.include);
        this.exclude = b.exclude == null ? Collections.emptySet() : Set.copyOf(b.exclude);
        this.maxChunks = b.maxChunks;
        this.maxBytes = b.maxBytes;
        this.enabled = b.enabled;
        this.chunker = b.chunker;
        this.minWidth = b.minWidth;
        this.minHeight = b.minHeight;
        this.modality = b.modality == null ? Modality.implied(b.input) : b.modality;
    }

    /** The three things every binding needs; everything else has a default. */
    public static Builder builder(String id, String engine, InputKind input) {
        return new Builder(id, engine, input);
    }

    public static final class Builder {
        private final String id;
        private final String engine;
        private final InputKind input;
        private List<String> tasks = List.of("embed");
        private Set<MediaType> include;
        private Set<MediaType> exclude;
        private int maxChunks = -1;
        private long maxBytes = -1;
        private boolean enabled = true;
        private TextChunker chunker;
        private int minWidth = DEFAULT_MIN_PIXELS;
        private int minHeight = DEFAULT_MIN_PIXELS;
        private Modality modality;

        private Builder(String id, String engine, InputKind input) {
            this.id = Objects.requireNonNull(id, "id");
            this.engine = Objects.requireNonNull(engine, "engine");
            this.input = Objects.requireNonNull(input, "input");
        }

        public Builder tasks(List<String> tasks) {
            this.tasks = tasks;
            return this;
        }

        public Builder include(Set<MediaType> include) {
            this.include = include;
            return this;
        }

        public Builder exclude(Set<MediaType> exclude) {
            this.exclude = exclude;
            return this;
        }

        /** Units per document; -1 (the default) for no limit. */
        public Builder maxChunks(int maxChunks) {
            this.maxChunks = maxChunks;
            return this;
        }

        /** Largest unit accepted, in bytes; -1 (the default) for no limit. */
        public Builder maxBytes(long maxBytes) {
            this.maxBytes = maxBytes;
            return this;
        }

        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public Builder chunker(TextChunker chunker) {
            this.chunker = chunker;
            return this;
        }

        /** IMAGES only: the smallest image taken, in pixels; 2 x 2 by default, 0 for any. */
        public Builder minSize(int minWidth, int minHeight) {
            this.minWidth = minWidth;
            this.minHeight = minHeight;
            return this;
        }

        /** Required for MEDIA; implied by the input kind otherwise. */
        public Builder modality(Modality modality) {
            this.modality = modality;
            return this;
        }

        public InferenceBinding build() {
            return new InferenceBinding(this);
        }
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

    /** What the engine is shown; implied by the input kind except for MEDIA, where it is required. */
    public Modality getModality() {
        return modality;
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
