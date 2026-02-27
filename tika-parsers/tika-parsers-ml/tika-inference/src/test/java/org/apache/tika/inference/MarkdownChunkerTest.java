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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

public class MarkdownChunkerTest {

    @Test
    void testSimpleHeadingSplit() {
        String md = "# Heading 1\n\nParagraph one.\n\n# Heading 2\n\nParagraph two.";
        MarkdownChunker chunker = new MarkdownChunker(500, 0);
        List<Chunk> chunks = chunker.chunk(md);

        assertEquals(2, chunks.size());
        assertTrue(chunks.get(0).getText().startsWith("# Heading 1"));
        assertTrue(chunks.get(1).getText().startsWith("# Heading 2"));
    }

    @Test
    void testOffsets() {
        String md = "# A\n\nText A.\n\n# B\n\nText B.";
        MarkdownChunker chunker = new MarkdownChunker(500, 0);
        List<Chunk> chunks = chunker.chunk(md);

        assertEquals(2, chunks.size());
        // Verify offsets point into the original string
        assertEquals(chunks.get(0).getText(),
                md.substring(chunks.get(0).getStartOffset(),
                        chunks.get(0).getEndOffset()));
        assertEquals(chunks.get(1).getText(),
                md.substring(chunks.get(1).getStartOffset(),
                        chunks.get(1).getEndOffset()));
    }

    @Test
    void testParagraphSplitWhenSectionTooLarge() {
        // Two paragraphs in one heading section, each ~30 chars
        String md = "# Big Section\n\n"
                + "Paragraph one is here now.\n\n"
                + "Paragraph two is here too.";

        // Max 50 chars forces a paragraph-level split
        MarkdownChunker chunker = new MarkdownChunker(50, 0);
        List<Chunk> chunks = chunker.chunk(md);

        assertTrue(chunks.size() >= 2,
                "Should split at paragraph boundary, got " + chunks.size());
    }

    @Test
    void testHardSplitOnLongParagraph() {
        String longPara = "A".repeat(200);
        MarkdownChunker chunker = new MarkdownChunker(50, 0);
        List<Chunk> chunks = chunker.chunk(longPara);

        assertTrue(chunks.size() >= 4,
                "200 chars / 50 max = at least 4 chunks");
        for (Chunk c : chunks) {
            assertTrue(c.getText().length() <= 50);
        }
    }

    @Test
    void testOverlap() {
        String md = "# A\n\nAAAAAAAAAA\n\n# B\n\nBBBBBBBBBB";
        MarkdownChunker chunker = new MarkdownChunker(500, 5);
        List<Chunk> chunks = chunker.chunk(md);

        assertEquals(2, chunks.size());
        // Second chunk should start earlier than the heading boundary
        // due to overlap pulling back into the first chunk's text
        assertTrue(chunks.get(1).getStartOffset() < md.indexOf("# B"));
    }

    @Test
    void testEmptyInput() {
        MarkdownChunker chunker = new MarkdownChunker(500, 0);
        assertEquals(0, chunker.chunk("").size());
        assertEquals(0, chunker.chunk(null).size());
    }

    @Test
    void testNoHeadings() {
        String md = "Just a plain paragraph with no headings at all.";
        MarkdownChunker chunker = new MarkdownChunker(500, 0);
        List<Chunk> chunks = chunker.chunk(md);
        assertEquals(1, chunks.size());
        assertEquals(md, chunks.get(0).getText());
    }

    @Test
    void testMultipleLevelHeadings() {
        String md = "# H1\n\nText.\n\n## H2\n\nMore text.\n\n### H3\n\nDeep text.";
        MarkdownChunker chunker = new MarkdownChunker(500, 0);
        List<Chunk> chunks = chunker.chunk(md);
        assertEquals(3, chunks.size());
    }

    @Test
    void testInvalidConfig() {
        assertThrows(IllegalArgumentException.class,
                () -> new MarkdownChunker(0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new MarkdownChunker(100, -1));
        assertThrows(IllegalArgumentException.class,
                () -> new MarkdownChunker(100, 100));
    }

    @Test
    void testSingleSmallChunk() {
        String md = "# Title\n\nShort.";
        MarkdownChunker chunker = new MarkdownChunker(500, 0);
        List<Chunk> chunks = chunker.chunk(md);
        assertEquals(1, chunks.size());
        assertEquals(0, chunks.get(0).getStartOffset());
    }
}
