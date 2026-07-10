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
package org.apache.tika.grpc.mapper;

import java.util.List;

import org.apache.tika.TikaTest;
import org.apache.tika.grpc.v1.Block;
import org.apache.tika.grpc.v1.Document;
import org.apache.tika.grpc.v1.Inline;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.ToMarkdownContentHandler;

/**
 * Shared helpers: parse Tika test fixtures, map them through {@link DocumentBuilder},
 * and read plain text back out of the block tree for content assertions.
 */
public abstract class ParseFixtureSupport extends TikaTest {

    /**
     * Result of parsing a fixture with markdown body extraction enabled.
     */
    public record ParseFixture(Metadata primary, List<Metadata> allMetadata, String body) {
    }

    protected ParseFixture parseBody(String fileName) throws Exception {
        java.io.StringWriter writer = new java.io.StringWriter();
        ToMarkdownContentHandler handler = new ToMarkdownContentHandler(writer);
        Metadata metadata = new Metadata();
        try (TikaInputStream input = getResourceAsStream("/test-documents/" + fileName)) {
            AUTO_DETECT_PARSER.parse(input, handler, metadata, new ParseContext());
        }
        return new ParseFixture(metadata, List.of(metadata), writer.toString());
    }

    protected Document map(ParseFixture fixture, String docId, long fetchParseTimeMs) {
        // "PARSE_SUCCESS" mirrors a real org.apache.tika.pipes.api.PipesResult.RESULT_STATUS
        // name -- there is no "OK" constant on that enum.
        return DocumentBuilder.build(
                fixture.primary(), fixture.allMetadata(), fixture.body(), docId, "PARSE_SUCCESS",
                fetchParseTimeMs, false);
    }

    protected Document map(ParseFixture fixture, String docId) {
        return map(fixture, docId, 1L);
    }

    /**
     * Concatenates the plain text carried by a document's block tree, so content
     * assertions can read the canonical representation instead of the optional flat
     * rendering. Formatting wrappers (emphasis, links, ...) contribute their inner text;
     * structural nodes recurse.
     */
    protected static String blockText(Document document) {
        StringBuilder text = new StringBuilder();
        for (Block block : document.getBlocksList()) {
            appendBlockText(block, text);
        }
        return text.toString();
    }

    private static void appendBlockText(Block block, StringBuilder text) {
        switch (block.getBlockCase()) {
            case HEADING -> appendInlineText(block.getHeading().getContentList(), text);
            case PARAGRAPH -> appendInlineText(block.getParagraph().getContentList(), text);
            case BLOCK_QUOTE -> block.getBlockQuote().getBlocksList()
                    .forEach(b -> appendBlockText(b, text));
            case BULLET_LIST -> block.getBulletList().getItemsList().forEach(
                    item -> item.getBlocksList().forEach(b -> appendBlockText(b, text)));
            case ORDERED_LIST -> block.getOrderedList().getItemsList().forEach(
                    item -> item.getBlocksList().forEach(b -> appendBlockText(b, text)));
            case CODE_BLOCK -> text.append(block.getCodeBlock().getLiteral());
            case TABLE -> {
                block.getTable().getHeaderList()
                        .forEach(cell -> appendInlineText(cell.getContentList(), text));
                block.getTable().getRowsList().forEach(row -> row.getCellsList()
                        .forEach(cell -> appendInlineText(cell.getContentList(), text)));
            }
            default -> {
            }
        }
        text.append('\n');
    }

    private static void appendInlineText(List<Inline> inlines, StringBuilder text) {
        for (Inline inline : inlines) {
            switch (inline.getInlineCase()) {
                case TEXT -> text.append(inline.getText());
                case EMPHASIS -> appendInlineText(inline.getEmphasis().getContentList(), text);
                case STRONG -> appendInlineText(inline.getStrong().getContentList(), text);
                case STRIKETHROUGH ->
                        appendInlineText(inline.getStrikethrough().getContentList(), text);
                case CODE -> text.append(inline.getCode().getLiteral());
                case LINK -> appendInlineText(inline.getLink().getContentList(), text);
                default -> {
                }
            }
        }
    }

}
