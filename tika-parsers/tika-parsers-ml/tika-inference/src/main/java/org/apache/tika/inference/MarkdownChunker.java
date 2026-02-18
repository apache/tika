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

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Splits markdown text into chunks that respect structural boundaries.
 * <p>
 * The chunker first splits on markdown heading boundaries ({@code # ...}).
 * If a section exceeds the maximum chunk size, it is further split on
 * paragraph boundaries (double newlines). As a last resort, oversized
 * paragraphs are split at the character limit.
 * <p>
 * Consecutive chunks can overlap by a configurable number of characters
 * to avoid losing context at boundaries.
 */
public class MarkdownChunker {

    /**
     * Matches a markdown heading at the start of a line (e.g. {@code ## Foo}).
     * The pattern matches 1-6 {@code #} characters followed by a space.
     */
    private static final Pattern HEADING_PATTERN = Pattern.compile("(?m)^#{1,6} ");

    private final int maxChunkChars;
    private final int overlapChars;

    public MarkdownChunker(int maxChunkChars, int overlapChars) {
        if (maxChunkChars <= 0) {
            throw new IllegalArgumentException("maxChunkChars must be > 0");
        }
        if (overlapChars < 0) {
            throw new IllegalArgumentException("overlapChars must be >= 0");
        }
        if (overlapChars >= maxChunkChars) {
            throw new IllegalArgumentException(
                    "overlapChars must be < maxChunkChars");
        }
        this.maxChunkChars = maxChunkChars;
        this.overlapChars = overlapChars;
    }

    /**
     * Chunk the given markdown text.
     *
     * @param text the full markdown content
     * @return ordered list of chunks with offsets relative to {@code text}
     */
    public List<Chunk> chunk(String text) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }

        // Split into sections at heading boundaries
        List<int[]> sections = splitAtHeadings(text);
        List<Chunk> chunks = new ArrayList<>();

        for (int[] section : sections) {
            int secStart = section[0];
            int secEnd = section[1];
            String secText = text.substring(secStart, secEnd);

            if (secText.length() <= maxChunkChars) {
                addChunk(chunks, text, secStart, secEnd);
            } else {
                // Further split on paragraph boundaries
                splitOnParagraphs(chunks, text, secStart, secEnd);
            }
        }

        // Apply overlap
        if (overlapChars > 0 && chunks.size() > 1) {
            chunks = applyOverlap(text, chunks);
        }

        return chunks;
    }

    /**
     * Find section boundaries by splitting at markdown headings.
     * Returns a list of [start, end) offset pairs.
     */
    private List<int[]> splitAtHeadings(String text) {
        List<int[]> sections = new ArrayList<>();
        Matcher matcher = HEADING_PATTERN.matcher(text);

        int prevStart = 0;
        while (matcher.find()) {
            int headingStart = matcher.start();
            if (headingStart > prevStart) {
                sections.add(new int[]{prevStart, headingStart});
            }
            prevStart = headingStart;
        }
        if (prevStart < text.length()) {
            sections.add(new int[]{prevStart, text.length()});
        }
        return sections;
    }

    /**
     * Split a section that exceeds maxChunkChars on double-newline
     * paragraph boundaries. Falls back to hard splits if needed.
     */
    private void splitOnParagraphs(List<Chunk> chunks, String fullText,
                                   int secStart, int secEnd) {
        String secText = fullText.substring(secStart, secEnd);
        String[] paragraphs = secText.split("\n\n");

        StringBuilder buffer = new StringBuilder();
        int bufferStart = secStart;
        int offset = secStart;

        for (String para : paragraphs) {
            // Account for the \n\n separator
            int paraLen = para.length();

            if (buffer.length() > 0
                    && buffer.length() + 2 + paraLen > maxChunkChars) {
                // Flush the buffer as a chunk
                addChunk(chunks, fullText, bufferStart,
                        bufferStart + buffer.length());
                buffer.setLength(0);
                bufferStart = offset;
            }

            if (buffer.length() > 0) {
                buffer.append("\n\n");
            }
            buffer.append(para);

            // If a single paragraph exceeds max, hard-split it
            if (buffer.length() > maxChunkChars) {
                hardSplit(chunks, fullText, bufferStart,
                        bufferStart + buffer.length());
                buffer.setLength(0);
                bufferStart = offset + paraLen + 2;
            }

            offset += paraLen + 2; // +2 for \n\n
        }

        if (buffer.length() > 0) {
            addChunk(chunks, fullText, bufferStart,
                    bufferStart + buffer.length());
        }
    }

    /**
     * Hard-split text at maxChunkChars boundaries when no structural
     * break is available.
     */
    private void hardSplit(List<Chunk> chunks, String fullText,
                           int start, int end) {
        int pos = start;
        while (pos < end) {
            int chunkEnd = Math.min(pos + maxChunkChars, end);
            addChunk(chunks, fullText, pos, chunkEnd);
            pos = chunkEnd;
        }
    }

    private void addChunk(List<Chunk> chunks, String fullText,
                          int start, int end) {
        // Trim trailing whitespace but keep the offsets honest
        String text = fullText.substring(start, end);
        String trimmed = text.stripTrailing();
        if (!trimmed.isEmpty()) {
            chunks.add(new Chunk(trimmed, start, start + trimmed.length()));
        }
    }

    /**
     * Rebuild chunks with overlap: each chunk (except the first) is
     * extended backwards by overlapChars into the previous chunk's text.
     */
    private List<Chunk> applyOverlap(String fullText, List<Chunk> original) {
        List<Chunk> result = new ArrayList<>(original.size());
        result.add(original.get(0));

        for (int i = 1; i < original.size(); i++) {
            Chunk cur = original.get(i);
            int newStart = Math.max(
                    original.get(i - 1).getEndOffset() - overlapChars,
                    original.get(i - 1).getStartOffset());
            String overlapped = fullText.substring(newStart, cur.getEndOffset());
            result.add(new Chunk(overlapped.stripTrailing(), newStart,
                    newStart + overlapped.stripTrailing().length()));
        }
        return result;
    }
}
