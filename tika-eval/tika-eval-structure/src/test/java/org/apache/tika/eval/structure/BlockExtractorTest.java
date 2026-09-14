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
package org.apache.tika.eval.structure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

public class BlockExtractorTest {

    private static final String XHTML = "<html xmlns=\"http://www.w3.org/1999/xhtml\"><head>"
            + "<title></title></head><body>"
            + "<div class=\"page\"><h2>Intro</h2><p>First para, one.</p>\n"
            + "<ul><li>Item <p>nested para</p></li></ul>"
            + "<table><tr><td>Cell A</td><td>Cell B</td></tr></table>"
            + "<div alt=\"A chart\" class=\"figure\">A chart\n</div>"
            + "<div class=\"artifact\"><p>Page 1</p></div></div>"
            + "<div class=\"page\"><div class=\"untagged\"><p>Loose text</p></div>"
            + "<p> \n </p></div></body></html>";

    @Test
    public void testBlocks() throws Exception {
        List<Block> blocks = BlockExtractor.extract(XHTML);
        assertEquals(List.of(Block.Kind.H, Block.Kind.P, Block.Kind.LI, Block.Kind.LI,
                        Block.Kind.CELL, Block.Kind.CELL, Block.Kind.OTHER, Block.Kind.P,
                        Block.Kind.P), blocks.stream().map(b -> b.kind).toList());
        assertEquals(List.of("first", "para", "one"), blocks.get(1).tokens);
        assertEquals(List.of("item"), blocks.get(2).tokens);
        assertEquals(List.of("nested", "para"), blocks.get(3).tokens);
        assertEquals(Block.Kind.CELL, blocks.get(4).kind);
        assertEquals(List.of("a", "chart"), blocks.get(6).tokens);
        assertEquals(Block.Kind.OTHER, blocks.get(6).kind);
        assertTrue(blocks.get(7).artifact);
        assertFalse(blocks.get(6).artifact);
        assertEquals(0, blocks.get(7).page);
        assertTrue(blocks.get(8).untagged);
        assertEquals(1, blocks.get(8).page);
        for (int i = 0; i < blocks.size(); i++) {
            assertEquals(i, blocks.get(i).index);
        }
    }

    @Test
    public void testParagraphInCellIsCellContent() throws Exception {
        List<Block> blocks = BlockExtractor.extract("<html><body><table><tr><td><p>one</p>"
                + "<p>two</p></td></tr></table><ul><li><p>three</p></li></ul></body></html>");
        assertEquals(List.of(Block.Kind.CELL, Block.Kind.CELL, Block.Kind.LI),
                blocks.stream().map(b -> b.kind).toList());
    }

    @Test
    public void testTokenize() {
        assertEquals(List.of("hello", "wörld", "42", "x"),
                BlockExtractor.tokenize("Hello, WÖRLD! 42\tx."));
        assertTrue(BlockExtractor.tokenize(" \n\t").isEmpty());
        // ideographs tokenize the same whether or not an extractor spaced them
        assertEquals(BlockExtractor.tokenize("作品名稱"), BlockExtractor.tokenize("作 品 名 稱"));
        assertEquals(List.of("作", "品", "abc", "名"), BlockExtractor.tokenize("作品abc名"));
    }
}
