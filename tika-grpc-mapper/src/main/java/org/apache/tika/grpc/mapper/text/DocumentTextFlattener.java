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
package org.apache.tika.grpc.mapper.text;

import java.util.ArrayList;
import java.util.List;

import org.apache.tika.grpc.v1.Block;
import org.apache.tika.grpc.v1.Document;
import org.apache.tika.grpc.v1.Inline;
import org.apache.tika.grpc.v1.Table;
import org.apache.tika.grpc.v1.TableCell;
import org.apache.tika.grpc.v1.TableRow;

/**
 * Flattens a Tika Document's canonical block tree into analyzable text, constructing the
 * exact block-path offset map as it walks (see DESIGN.md for the per-type policy). The
 * mapping is exact by construction: every text-bearing block records the range it
 * contributed while contributing it, so no offsets are required from, or stamped into,
 * the Tika contract.
 *
 * <p>Blocks are separated by a blank line so sentence detection treats block boundaries
 * as hard boundaries. Code blocks and raw html are skipped for analysis and reported in
 * {@link FlattenedText#skippedPaths()}. Embedded documents are flattened separately
 * by the caller ({@link #flatten(Document)} handles one document's own blocks only).</p>
 */
public final class DocumentTextFlattener {

    /**
     * Offsets are only meaningful against the exact flattening that produced them; store
     * this next to any persisted annotation, and bump it on any policy change.
     */
    public static final String FLATTEN_VERSION = "tika-block-flatten/1";

    private static final String BLOCK_SEPARATOR = "\n\n";
    private static final String CELL_SEPARATOR = " | ";

    private final StringBuilder text = new StringBuilder();
    private final List<FlattenedText.BlockRange> ranges = new ArrayList<>();
    private final List<String> skipped = new ArrayList<>();

    private DocumentTextFlattener() {
    }

    /**
     * Flattens one document's block tree.
     *
     * @param document the parsed document; must not be null
     * @return the flattened text with anchors and provenance carried over
     */
    public static FlattenedText flatten(Document document) {
        DocumentTextFlattener flattener = new DocumentTextFlattener();
        List<Block> blocks = document.getBlocksList();
        for (int i = 0; i < blocks.size(); i++) {
            flattener.block(blocks.get(i), String.valueOf(i));
        }
        return new FlattenedText(
                document.getId(),
                document.getOrigin().getSha256(),
                FLATTEN_VERSION,
                flattener.text.toString(),
                List.copyOf(flattener.ranges),
                List.copyOf(flattener.skipped));
    }

    private void block(Block block, String path) {
        switch (block.getBlockCase()) {
            case HEADING -> anchored(path, () -> inlines(block.getHeading().getContentList()));
            case PARAGRAPH -> anchored(path, () -> inlines(block.getParagraph().getContentList()));
            case BLOCK_QUOTE -> children(block.getBlockQuote().getBlocksList(), path);
            case BULLET_LIST -> items(block.getBulletList().getItemsList().stream()
                    .map(item -> item.getBlocksList()).toList(), path);
            case ORDERED_LIST -> items(block.getOrderedList().getItemsList().stream()
                    .map(item -> item.getBlocksList()).toList(), path);
            case TABLE -> table(block.getTable(), path);
            case CODE_BLOCK, HTML_BLOCK -> skipped.add(path);
            default -> {
                // ThematicBreak and future block types carry no analyzable text.
            }
        }
    }

    private void children(List<Block> blocks, String parentPath) {
        for (int i = 0; i < blocks.size(); i++) {
            block(blocks.get(i), parentPath + "." + i);
        }
    }

    private void items(List<List<Block>> itemBlocks, String parentPath) {
        for (int i = 0; i < itemBlocks.size(); i++) {
            children(itemBlocks.get(i), parentPath + "." + i);
        }
    }

    private void table(Table table, String path) {
        if (!table.getHeaderList().isEmpty()) {
            row(table.getHeaderList(), path + ".header");
        }
        List<TableRow> rows = table.getRowsList();
        for (int r = 0; r < rows.size(); r++) {
            row(rows.get(r).getCellsList(), path + ".rows." + r);
        }
    }

    // One line per row; every cell gets its own anchor so table-fact consumers can point
    // at the exact cell, and the row line as a whole reads as one statement.
    private void row(List<TableCell> cells, String rowPath) {
        boolean wroteAny = false;
        for (int c = 0; c < cells.size(); c++) {
            String cellText = renderInlines(cells.get(c).getContentList());
            if (cellText.isEmpty()) {
                continue;
            }
            if (wroteAny) {
                text.append(CELL_SEPARATOR);
            } else {
                separateFromPrevious();
            }
            int start = text.length();
            text.append(cellText);
            ranges.add(new FlattenedText.BlockRange(rowPath + ".cells." + c, start,
                    text.length()));
            wroteAny = true;
        }
    }

    // Runs the emitter between separator handling and anchor recording; empty output
    // leaves no anchor and no separator.
    private void anchored(String path, Runnable emitter) {
        int lengthBefore = text.length();
        separateFromPrevious();
        int start = text.length();
        emitter.run();
        if (text.length() == start) {
            text.setLength(lengthBefore);
            return;
        }
        ranges.add(new FlattenedText.BlockRange(path, start, text.length()));
    }

    private void separateFromPrevious() {
        if (!text.isEmpty()) {
            text.append(BLOCK_SEPARATOR);
        }
    }

    private void inlines(List<Inline> content) {
        text.append(renderInlines(content));
    }

    private static String renderInlines(List<Inline> content) {
        StringBuilder out = new StringBuilder();
        renderInto(content, out);
        return out.toString();
    }

    private static void renderInto(List<Inline> content, StringBuilder out) {
        for (Inline inline : content) {
            switch (inline.getInlineCase()) {
                case TEXT -> out.append(inline.getText());
                case EMPHASIS -> renderInto(inline.getEmphasis().getContentList(), out);
                case STRONG -> renderInto(inline.getStrong().getContentList(), out);
                case STRIKETHROUGH ->
                        renderInto(inline.getStrikethrough().getContentList(), out);
                case CODE -> out.append(inline.getCode().getLiteral());
                case LINK -> renderInto(inline.getLink().getContentList(), out);
                case IMAGE -> out.append(inline.getImage().getAlt());
                case LINE_BREAK -> out.append(inline.getLineBreak().getHard() ? "\n" : " ");
                default -> {
                    // raw inline html contributes nothing analyzable
                }
            }
        }
    }
}
