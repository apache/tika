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
package org.apache.tika.grpc.mapper.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import org.apache.tika.grpc.v1.Alignment;
import org.apache.tika.grpc.v1.Block;
import org.apache.tika.grpc.v1.Inline;

class MarkdownBlockTreeBuilderTest {

    @Test
    void emptyOrNullMarkdownYieldsNoBlocks() {
        assertTrue(MarkdownBlockTreeBuilder.toBlocks(null).isEmpty());
        assertTrue(MarkdownBlockTreeBuilder.toBlocks("").isEmpty());
    }

    @Test
    void nestedBulletListInsideAnOrderedListItemIsPreserved() {
        String markdown = "1. first\n"
                + "   - nested a\n"
                + "   - nested b\n"
                + "2. second\n";

        List<Block> blocks = MarkdownBlockTreeBuilder.toBlocks(markdown);
        assertEquals(1, blocks.size());
        Block ordered = blocks.get(0);
        assertTrue(ordered.hasOrderedList());
        assertEquals(2, ordered.getOrderedList().getItemsCount());

        org.apache.tika.grpc.v1.ListItem first = ordered.getOrderedList().getItems(0);
        boolean sawNestedBulletList = first.getBlocksList().stream().anyMatch(Block::hasBulletList);
        assertTrue(sawNestedBulletList, "the nested bullet list under item 1 must survive the recursive conversion");

        org.apache.tika.grpc.v1.BulletList nested = first.getBlocksList().stream()
                .filter(Block::hasBulletList)
                .map(Block::getBulletList)
                .findFirst()
                .orElseThrow();
        assertEquals(2, nested.getItemsCount());
    }

    @Test
    void gfmTableWithAlignmentAndMultipleRows() {
        String markdown = "| Left | Center | Right |\n"
                + "|:---|:---:|---:|\n"
                + "| a | b | c |\n"
                + "| d | e | f |\n";

        List<Block> blocks = MarkdownBlockTreeBuilder.toBlocks(markdown);
        assertEquals(1, blocks.size());
        assertTrue(blocks.get(0).hasTable());

        org.apache.tika.grpc.v1.Table table = blocks.get(0).getTable();
        assertEquals(3, table.getHeaderCount());
        assertEquals(Alignment.ALIGN_LEFT, table.getHeader(0).getAlignment());
        assertEquals(Alignment.ALIGN_CENTER, table.getHeader(1).getAlignment());
        assertEquals(Alignment.ALIGN_RIGHT, table.getHeader(2).getAlignment());

        assertEquals(2, table.getRowsCount());
        assertEquals(3, table.getRows(0).getCellsCount());
        assertEquals(3, table.getRows(1).getCellsCount());
    }

    @Test
    void linkAndImageCarryDestinationAndOptionalTitle() {
        String markdown = "[link text](https://example.com/page \"a title\")\n\n"
                + "![alt text](https://example.com/img.png)\n";

        List<Block> blocks = MarkdownBlockTreeBuilder.toBlocks(markdown);
        assertEquals(2, blocks.size());

        Inline linkInline = blocks.get(0).getParagraph().getContent(0);
        assertTrue(linkInline.hasLink());
        assertEquals("https://example.com/page", linkInline.getLink().getDestination());
        assertEquals("a title", linkInline.getLink().getTitle());

        Inline imageInline = blocks.get(1).getParagraph().getContent(0);
        assertTrue(imageInline.hasImage());
        assertEquals("https://example.com/img.png", imageInline.getImage().getDestination());
        assertEquals("alt text", imageInline.getImage().getAlt());
        assertEquals("", imageInline.getImage().getTitle(), "no title given in the markdown -> unset, not null-ish garbage");
    }

    @Test
    void imageAltTextKeepsCodeSpansAndLineBreaks() {
        String markdown = "![see `config.json` for\ndetails](img.png)\n";

        List<Block> blocks = MarkdownBlockTreeBuilder.toBlocks(markdown);
        Inline imageInline = blocks.get(0).getParagraph().getContent(0);
        assertTrue(imageInline.hasImage());
        assertEquals("see config.json for details", imageInline.getImage().getAlt(),
                "code-span literals and soft breaks inside alt must not be dropped");
    }
}
