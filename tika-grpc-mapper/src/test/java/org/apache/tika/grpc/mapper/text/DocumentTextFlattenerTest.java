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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import org.apache.tika.grpc.v1.Block;
import org.apache.tika.grpc.v1.BlockQuote;
import org.apache.tika.grpc.v1.CodeBlock;
import org.apache.tika.grpc.v1.Document;
import org.apache.tika.grpc.v1.Emphasis;
import org.apache.tika.grpc.v1.Heading;
import org.apache.tika.grpc.v1.Inline;
import org.apache.tika.grpc.v1.Paragraph;
import org.apache.tika.grpc.v1.SourceOrigin;
import org.apache.tika.grpc.v1.Table;
import org.apache.tika.grpc.v1.TableCell;
import org.apache.tika.grpc.v1.TableRow;

class DocumentTextFlattenerTest {

    private static Inline text(String value) {
        return Inline.newBuilder().setText(value).build();
    }

    private static Block paragraph(String value) {
        return Block.newBuilder()
                .setParagraph(Paragraph.newBuilder().addContent(text(value))).build();
    }

    private static TableCell cell(String value) {
        return TableCell.newBuilder().addContent(text(value)).build();
    }

    @Test
    void anchorsAreExactByConstruction() {
        Document document = Document.newBuilder()
                .setId("doc-1")
                .setOrigin(SourceOrigin.newBuilder().setSha256("abc123"))
                .addBlocks(Block.newBuilder().setHeading(Heading.newBuilder()
                        .setLevel(1).addContent(text("Quarterly Report"))))
                .addBlocks(paragraph("Revenue grew in EMEA."))
                .build();

        FlattenedText flat = DocumentTextFlattener.flatten(document);

        assertEquals("Quarterly Report\n\nRevenue grew in EMEA.", flat.text());
        assertEquals("doc-1", flat.documentId());
        assertEquals("abc123", flat.sourceSha256());
        assertEquals(DocumentTextFlattener.FLATTEN_VERSION, flat.flattenVersion());
        // every anchor's range must reproduce its block's text exactly
        assertEquals(2, flat.ranges().size());
        for (FlattenedText.BlockRange range : flat.ranges()) {
            String slice = flat.text().substring(range.start(), range.end());
            assertTrue(!slice.isBlank() && !slice.contains("\n\n"),
                    "anchor " + range.path() + " must cover exactly one block: " + slice);
        }
        assertEquals("0", flat.ranges().get(0).path());
        assertEquals("1", flat.ranges().get(1).path());
    }

    @Test
    void formattingWrappersContributeInnerTextOnly() {
        Document document = Document.newBuilder()
                .addBlocks(Block.newBuilder().setParagraph(Paragraph.newBuilder()
                        .addContent(text("The "))
                        .addContent(Inline.newBuilder().setEmphasis(
                                Emphasis.newBuilder().addContent(text("quick"))))
                        .addContent(text(" fox"))))
                .build();

        assertEquals("The quick fox", DocumentTextFlattener.flatten(document).text());
    }

    @Test
    void nestedBlocksGetDottedPaths() {
        Document document = Document.newBuilder()
                .addBlocks(paragraph("Intro."))
                .addBlocks(Block.newBuilder().setBlockQuote(BlockQuote.newBuilder()
                        .addBlocks(paragraph("Quoted line one."))
                        .addBlocks(paragraph("Quoted line two."))))
                .build();

        FlattenedText flat = DocumentTextFlattener.flatten(document);

        assertEquals(List.of("0", "1.0", "1.1"),
                flat.ranges().stream().map(FlattenedText.BlockRange::path).toList());
    }

    @Test
    void tablesLinearizeRowWiseWithCellAnchors() {
        Table table = Table.newBuilder()
                .addHeader(cell("Region")).addHeader(cell("Revenue"))
                .addRows(TableRow.newBuilder().addCells(cell("EMEA")).addCells(cell("4.2M")))
                .build();
        Document document = Document.newBuilder()
                .addBlocks(Block.newBuilder().setTable(table))
                .build();

        FlattenedText flat = DocumentTextFlattener.flatten(document);

        assertEquals("Region | Revenue\n\nEMEA | 4.2M", flat.text());
        assertEquals(List.of("0.header.cells.0", "0.header.cells.1",
                        "0.rows.0.cells.0", "0.rows.0.cells.1"),
                flat.ranges().stream().map(FlattenedText.BlockRange::path).toList());
        // the revenue figure's anchor points at exactly its cell text
        FlattenedText.BlockRange revenue = flat.ranges().get(3);
        assertEquals("4.2M", flat.text().substring(revenue.start(), revenue.end()));
    }

    @Test
    void codeAndHtmlBlocksAreSkippedAndReported() {
        Document document = Document.newBuilder()
                .addBlocks(paragraph("Before."))
                .addBlocks(Block.newBuilder().setCodeBlock(
                        CodeBlock.newBuilder().setLiteral("int x = 1;")))
                .addBlocks(paragraph("After."))
                .build();

        FlattenedText flat = DocumentTextFlattener.flatten(document);

        assertEquals("Before.\n\nAfter.", flat.text());
        assertEquals(List.of("1"), flat.skippedPaths());
        assertEquals(List.of("0", "2"),
                flat.ranges().stream().map(FlattenedText.BlockRange::path).toList());
    }

    @Test
    void projectionMapsSpansBackToBlockLocalCoordinates() {
        Document document = Document.newBuilder()
                .addBlocks(paragraph("Alice went to Paris."))
                .addBlocks(paragraph("Bob stayed home."))
                .build();
        FlattenedText flat = DocumentTextFlattener.flatten(document);

        int paris = flat.text().indexOf("Paris");
        List<FlattenedText.Projection> projections = flat.project(paris, paris + 5);

        assertEquals(1, projections.size());
        assertEquals("0", projections.get(0).path());
        assertEquals("Alice went to Paris.".indexOf("Paris"), projections.get(0).localStart());
        assertEquals(projections.get(0).localStart() + 5, projections.get(0).localEnd());

        // a second-block span projects into the second block's local coordinates
        int bob = flat.text().indexOf("Bob");
        List<FlattenedText.Projection> second = flat.project(bob, bob + 3);
        assertEquals("1", second.get(0).path());
        assertEquals(0, second.get(0).localStart());

        assertThrows(IllegalArgumentException.class, () -> flat.project(5, 5));
        assertThrows(IllegalArgumentException.class,
                () -> flat.project(0, flat.text().length() + 1));
    }

    @Test
    void emptyBlocksLeaveNoAnchorAndNoSeparator() {
        Document document = Document.newBuilder()
                .addBlocks(paragraph("Only real content."))
                .addBlocks(Block.newBuilder().setParagraph(Paragraph.newBuilder()))
                .build();

        FlattenedText flat = DocumentTextFlattener.flatten(document);

        assertEquals("Only real content.", flat.text());
        assertEquals(1, flat.ranges().size());
    }
}
