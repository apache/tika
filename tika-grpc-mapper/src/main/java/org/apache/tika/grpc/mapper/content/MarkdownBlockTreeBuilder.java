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

import java.util.ArrayList;
import java.util.List;

import org.commonmark.Extension;
import org.commonmark.ext.gfm.strikethrough.Strikethrough;
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension;
import org.commonmark.ext.gfm.tables.TableBlock;
import org.commonmark.ext.gfm.tables.TableBody;
import org.commonmark.ext.gfm.tables.TableCell;
import org.commonmark.ext.gfm.tables.TableHead;
import org.commonmark.ext.gfm.tables.TableRow;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.BlockQuote;
import org.commonmark.node.BulletList;
import org.commonmark.node.Code;
import org.commonmark.node.Emphasis;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.HardLineBreak;
import org.commonmark.node.Heading;
import org.commonmark.node.HtmlBlock;
import org.commonmark.node.HtmlInline;
import org.commonmark.node.Image;
import org.commonmark.node.IndentedCodeBlock;
import org.commonmark.node.Link;
import org.commonmark.node.ListItem;
import org.commonmark.node.Node;
import org.commonmark.node.OrderedList;
import org.commonmark.node.Paragraph;
import org.commonmark.node.SoftLineBreak;
import org.commonmark.node.StrongEmphasis;
import org.commonmark.node.Text;
import org.commonmark.node.ThematicBreak;
import org.commonmark.parser.Parser;

/**
 * Converts a markdown string into the {@code Document} proto's structured
 * {@code Block}/{@code Inline} tree.
 * <p>
 * Every Tika parser's SAX output already reaches this module as markdown text (the
 * pipes default content handler renders {@code TikaCoreProperties.TIKA_CONTENT} as
 * markdown via {@code ToMarkdownContentHandler}). Reusing that single rendering path
 * plus commonmark-java's parser means the content tree is built once, generically, for
 * every source format -- no per-format content handling is needed, only per-format
 * metadata handling ({@link org.apache.tika.grpc.mapper.transform.DocumentTransformer}).
 * <p>
 * Naming note: every {@code org.apache.tika.grpc.v1.*} proto type is referenced fully
 * qualified in this file. Many proto message names (Heading, Paragraph, BlockQuote,
 * Emphasis, Code, Link, Image, TableRow, TableCell, ...) collide with commonmark-java's
 * own node class names, which are imported normally; fully qualifying one side avoids
 * any ambiguity.
 */
public final class MarkdownBlockTreeBuilder {

    private static final List<Extension> EXTENSIONS =
            List.of(TablesExtension.create(), StrikethroughExtension.create());
    private static final Parser COMMONMARK = Parser.builder().extensions(EXTENSIONS).build();

    private MarkdownBlockTreeBuilder() {
    }

    /** Parses {@code markdown} into a flat list of top-level {@code Block}s. */
    public static List<org.apache.tika.grpc.v1.Block> toBlocks(String markdown) {
        if (markdown == null || markdown.isEmpty()) {
            return List.of();
        }
        return blockChildren(COMMONMARK.parse(markdown));
    }

    private static List<org.apache.tika.grpc.v1.Block> blockChildren(Node parent) {
        List<org.apache.tika.grpc.v1.Block> blocks = new ArrayList<>();
        for (Node child = parent.getFirstChild(); child != null; child = child.getNext()) {
            org.apache.tika.grpc.v1.Block block = toBlock(child);
            if (block != null) {
                blocks.add(block);
            }
        }
        return blocks;
    }

    private static org.apache.tika.grpc.v1.Block toBlock(Node node) {
        if (node instanceof Heading) {
            Heading heading = (Heading) node;
            return org.apache.tika.grpc.v1.Block.newBuilder()
                    .setHeading(org.apache.tika.grpc.v1.Heading.newBuilder()
                            .setLevel(heading.getLevel())
                            .addAllContent(inlineChildren(heading)))
                    .build();
        }
        if (node instanceof Paragraph) {
            return org.apache.tika.grpc.v1.Block.newBuilder()
                    .setParagraph(org.apache.tika.grpc.v1.Paragraph.newBuilder()
                            .addAllContent(inlineChildren(node)))
                    .build();
        }
        if (node instanceof BlockQuote) {
            return org.apache.tika.grpc.v1.Block.newBuilder()
                    .setBlockQuote(org.apache.tika.grpc.v1.BlockQuote.newBuilder()
                            .addAllBlocks(blockChildren(node)))
                    .build();
        }
        if (node instanceof BulletList) {
            BulletList bulletList = (BulletList) node;
            return org.apache.tika.grpc.v1.Block.newBuilder()
                    .setBulletList(org.apache.tika.grpc.v1.BulletList.newBuilder()
                            .setMarker(String.valueOf(bulletList.getBulletMarker()))
                            .setTight(bulletList.isTight())
                            .addAllItems(listItems(bulletList)))
                    .build();
        }
        if (node instanceof OrderedList) {
            OrderedList orderedList = (OrderedList) node;
            return org.apache.tika.grpc.v1.Block.newBuilder()
                    .setOrderedList(org.apache.tika.grpc.v1.OrderedList.newBuilder()
                            .setStart(orderedList.getStartNumber())
                            .setDelimiter(String.valueOf(orderedList.getDelimiter()))
                            .setTight(orderedList.isTight())
                            .addAllItems(listItems(orderedList)))
                    .build();
        }
        if (node instanceof FencedCodeBlock) {
            FencedCodeBlock codeBlock = (FencedCodeBlock) node;
            return org.apache.tika.grpc.v1.Block.newBuilder()
                    .setCodeBlock(org.apache.tika.grpc.v1.CodeBlock.newBuilder()
                            .setFenced(true)
                            .setInfo(nullToEmpty(codeBlock.getInfo()))
                            .setLiteral(nullToEmpty(codeBlock.getLiteral())))
                    .build();
        }
        if (node instanceof IndentedCodeBlock) {
            return org.apache.tika.grpc.v1.Block.newBuilder()
                    .setCodeBlock(org.apache.tika.grpc.v1.CodeBlock.newBuilder()
                            .setFenced(false)
                            .setLiteral(nullToEmpty(((IndentedCodeBlock) node).getLiteral())))
                    .build();
        }
        if (node instanceof HtmlBlock) {
            return org.apache.tika.grpc.v1.Block.newBuilder()
                    .setHtmlBlock(org.apache.tika.grpc.v1.HtmlBlock.newBuilder()
                            .setLiteral(nullToEmpty(((HtmlBlock) node).getLiteral())))
                    .build();
        }
        if (node instanceof ThematicBreak) {
            return org.apache.tika.grpc.v1.Block.newBuilder()
                    .setThematicBreak(org.apache.tika.grpc.v1.ThematicBreak.getDefaultInstance())
                    .build();
        }
        if (node instanceof TableBlock) {
            return org.apache.tika.grpc.v1.Block.newBuilder().setTable(toTable(node)).build();
        }
        // Link reference definitions and any other unmodeled block produce no rendered output.
        return null;
    }

    private static List<org.apache.tika.grpc.v1.ListItem> listItems(Node list) {
        List<org.apache.tika.grpc.v1.ListItem> items = new ArrayList<>();
        for (Node child = list.getFirstChild(); child != null; child = child.getNext()) {
            if (child instanceof ListItem) {
                items.add(org.apache.tika.grpc.v1.ListItem.newBuilder()
                        .addAllBlocks(blockChildren(child))
                        .build());
            }
        }
        return items;
    }

    private static org.apache.tika.grpc.v1.Table toTable(Node tableBlock) {
        org.apache.tika.grpc.v1.Table.Builder table = org.apache.tika.grpc.v1.Table.newBuilder();
        for (Node section = tableBlock.getFirstChild(); section != null; section = section.getNext()) {
            if (section instanceof TableHead) {
                for (Node row = section.getFirstChild(); row != null; row = row.getNext()) {
                    if (!(row instanceof TableRow)) {
                        continue;
                    }
                    for (Node cell = row.getFirstChild(); cell != null; cell = cell.getNext()) {
                        if (cell instanceof TableCell) {
                            table.addHeader(toTableCell((TableCell) cell));
                        }
                    }
                }
            } else if (section instanceof TableBody) {
                for (Node row = section.getFirstChild(); row != null; row = row.getNext()) {
                    if (!(row instanceof TableRow)) {
                        continue;
                    }
                    org.apache.tika.grpc.v1.TableRow.Builder rowBuilder = org.apache.tika.grpc.v1.TableRow.newBuilder();
                    for (Node cell = row.getFirstChild(); cell != null; cell = cell.getNext()) {
                        if (cell instanceof TableCell) {
                            rowBuilder.addCells(toTableCell((TableCell) cell));
                        }
                    }
                    table.addRows(rowBuilder.build());
                }
            }
        }
        return table.build();
    }

    private static org.apache.tika.grpc.v1.TableCell toTableCell(TableCell cell) {
        return org.apache.tika.grpc.v1.TableCell.newBuilder()
                .setAlignment(toAlignment(cell.getAlignment()))
                .addAllContent(inlineChildren(cell))
                .build();
    }

    private static org.apache.tika.grpc.v1.Alignment toAlignment(TableCell.Alignment alignment) {
        if (alignment == null) {
            return org.apache.tika.grpc.v1.Alignment.ALIGN_NONE;
        }
        switch (alignment) {
            case LEFT:
                return org.apache.tika.grpc.v1.Alignment.ALIGN_LEFT;
            case CENTER:
                return org.apache.tika.grpc.v1.Alignment.ALIGN_CENTER;
            case RIGHT:
                return org.apache.tika.grpc.v1.Alignment.ALIGN_RIGHT;
            default:
                return org.apache.tika.grpc.v1.Alignment.ALIGN_NONE;
        }
    }

    private static List<org.apache.tika.grpc.v1.Inline> inlineChildren(Node parent) {
        List<org.apache.tika.grpc.v1.Inline> inlines = new ArrayList<>();
        for (Node child = parent.getFirstChild(); child != null; child = child.getNext()) {
            org.apache.tika.grpc.v1.Inline inline = toInline(child);
            if (inline != null) {
                inlines.add(inline);
            }
        }
        return inlines;
    }

    private static org.apache.tika.grpc.v1.Inline toInline(Node node) {
        if (node instanceof Text) {
            return org.apache.tika.grpc.v1.Inline.newBuilder().setText(((Text) node).getLiteral()).build();
        }
        if (node instanceof Emphasis) {
            return org.apache.tika.grpc.v1.Inline.newBuilder()
                    .setEmphasis(org.apache.tika.grpc.v1.Emphasis.newBuilder().addAllContent(inlineChildren(node)))
                    .build();
        }
        if (node instanceof StrongEmphasis) {
            return org.apache.tika.grpc.v1.Inline.newBuilder()
                    .setStrong(org.apache.tika.grpc.v1.Strong.newBuilder().addAllContent(inlineChildren(node)))
                    .build();
        }
        if (node instanceof Strikethrough) {
            return org.apache.tika.grpc.v1.Inline.newBuilder()
                    .setStrikethrough(org.apache.tika.grpc.v1.Strikethrough.newBuilder().addAllContent(inlineChildren(node)))
                    .build();
        }
        if (node instanceof Code) {
            return org.apache.tika.grpc.v1.Inline.newBuilder()
                    .setCode(org.apache.tika.grpc.v1.Code.newBuilder().setLiteral(((Code) node).getLiteral()))
                    .build();
        }
        if (node instanceof Link) {
            Link link = (Link) node;
            org.apache.tika.grpc.v1.Link.Builder linkBuilder = org.apache.tika.grpc.v1.Link.newBuilder()
                    .setDestination(nullToEmpty(link.getDestination()))
                    .addAllContent(inlineChildren(link));
            if (link.getTitle() != null) {
                linkBuilder.setTitle(link.getTitle());
            }
            return org.apache.tika.grpc.v1.Inline.newBuilder().setLink(linkBuilder).build();
        }
        if (node instanceof Image) {
            Image image = (Image) node;
            org.apache.tika.grpc.v1.Image.Builder imageBuilder = org.apache.tika.grpc.v1.Image.newBuilder()
                    .setDestination(nullToEmpty(image.getDestination()))
                    .setAlt(collectText(image));
            if (image.getTitle() != null) {
                imageBuilder.setTitle(image.getTitle());
            }
            return org.apache.tika.grpc.v1.Inline.newBuilder().setImage(imageBuilder).build();
        }
        if (node instanceof HardLineBreak) {
            return org.apache.tika.grpc.v1.Inline.newBuilder()
                    .setLineBreak(org.apache.tika.grpc.v1.LineBreak.newBuilder().setHard(true))
                    .build();
        }
        if (node instanceof SoftLineBreak) {
            return org.apache.tika.grpc.v1.Inline.newBuilder()
                    .setLineBreak(org.apache.tika.grpc.v1.LineBreak.newBuilder().setHard(false))
                    .build();
        }
        if (node instanceof HtmlInline) {
            return org.apache.tika.grpc.v1.Inline.newBuilder().setHtml(((HtmlInline) node).getLiteral()).build();
        }
        // Reference definitions and any other unmodeled inline node produce no rendered output.
        return null;
    }

    /** Flattens inline content to plain text (for image alt), losing no literals. */
    private static String collectText(Node node) {
        StringBuilder sb = new StringBuilder();
        for (Node child = node.getFirstChild(); child != null; child = child.getNext()) {
            if (child instanceof Text) {
                sb.append(((Text) child).getLiteral());
            } else if (child instanceof Code) {
                sb.append(((Code) child).getLiteral());
            } else if (child instanceof HtmlInline) {
                sb.append(((HtmlInline) child).getLiteral());
            } else if (child instanceof SoftLineBreak || child instanceof HardLineBreak) {
                sb.append(' ');
            } else {
                sb.append(collectText(child));
            }
        }
        return sb.toString();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
