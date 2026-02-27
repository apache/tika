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

import org.apache.tika.inference.locator.Locators;
import org.apache.tika.inference.locator.TextLocator;

/**
 * A content chunk with multimodal locators and an optional embedding vector.
 * <p>
 * The {@code text} field holds the textual content of the chunk (may be
 * null for non-text chunks such as image regions or audio segments).
 * <p>
 * The {@link Locators} object identifies where this chunk comes from in
 * the original content across multiple modalities (text offsets, page/bbox,
 * spatial regions, temporal ranges).
 */
public class Chunk {

    private final String text;
    private final Locators locators;
    private float[] vector;

    public Chunk(String text, Locators locators) {
        this.text = text;
        this.locators = locators != null ? locators : new Locators();
    }

    /**
     * Convenience constructor for text-only chunks with character offsets.
     */
    public Chunk(String text, int startOffset, int endOffset) {
        this(text, new Locators().addText(
                new TextLocator(startOffset, endOffset)));
    }

    public String getText() {
        return text;
    }

    public Locators getLocators() {
        return locators;
    }

    /**
     * Convenience: returns the start offset from the first
     * {@link TextLocator}, or -1 if none.
     */
    public int getStartOffset() {
        if (locators.getText() != null && !locators.getText().isEmpty()) {
            return locators.getText().get(0).getStartOffset();
        }
        return -1;
    }

    /**
     * Convenience: returns the end offset from the first
     * {@link TextLocator}, or -1 if none.
     */
    public int getEndOffset() {
        if (locators.getText() != null && !locators.getText().isEmpty()) {
            return locators.getText().get(0).getEndOffset();
        }
        return -1;
    }

    public float[] getVector() {
        return vector;
    }

    public void setVector(float[] vector) {
        this.vector = vector;
    }
}
