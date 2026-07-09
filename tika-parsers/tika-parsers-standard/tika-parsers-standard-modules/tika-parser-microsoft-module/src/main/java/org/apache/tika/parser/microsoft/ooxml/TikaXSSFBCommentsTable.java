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
package org.apache.tika.parser.microsoft.ooxml;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.apache.poi.ss.util.CellAddress;
import org.apache.poi.util.LittleEndian;
import org.apache.poi.util.LittleEndianConsts;
import org.apache.poi.xssf.binary.XSSFBParser;
import org.apache.poi.xssf.binary.XSSFBRecordType;
import org.apache.poi.xssf.binary.XSSFBUtils;
import org.xml.sax.SAXException;

import org.apache.tika.sax.XHTMLContentHandler;

/**
 * Replacement for POI's {@code XSSFBCommentsTable} that does not depend on
 * {@code XSSFBComment}/{@code XSSFBRichTextString}/{@code XSSFRichTextString}
 * (which pull in poi-ooxml-lite / xmlbeans via {@code CTRst}).
 * <p>
 * Stores comments as plain author + text strings.
 */
class TikaXSSFBCommentsTable extends XSSFBParser {

    private final Map<CellAddress, CommentEntry> comments = new TreeMap<>();
    private final List<String> authors = new ArrayList<>();

    private int authorId = -1;
    private int cellRow = -1;
    private int cellCol = -1;
    private String commentText;
    private final StringBuilder buffer = new StringBuilder();

    TikaXSSFBCommentsTable(InputStream is) throws IOException {
        super(is);
        parse();
    }

    @Override
    public void handleRecord(int id, byte[] data) {
        XSSFBRecordType recordType = XSSFBRecordType.lookup(id);
        switch (recordType) {
            case BrtBeginComment:
                authorId = (int) LittleEndian.getUInt(data, 0);
                // cell range: firstRow at offset 4, firstCol at offset 12
                cellRow = (int) LittleEndian.getUInt(data, LittleEndianConsts.INT_SIZE);
                cellCol = (int) LittleEndian.getUInt(data,
                        LittleEndianConsts.INT_SIZE + 2 * LittleEndianConsts.INT_SIZE);
                break;
            case BrtCommentText:
                buffer.setLength(0);
                XSSFBUtils.readXLWideString(data, 1, buffer);
                commentText = buffer.toString();
                break;
            case BrtEndComment:
                CellAddress addr = new CellAddress(cellRow, cellCol);
                String author = (authorId >= 0 && authorId < authors.size())
                        ? authors.get(authorId) : "";
                comments.put(addr, new CommentEntry(author, commentText));
                authorId = -1;
                cellRow = -1;
                cellCol = -1;
                commentText = null;
                break;
            case BrtCommentAuthor:
                buffer.setLength(0);
                XSSFBUtils.readXLWideString(data, 0, buffer);
                authors.add(buffer.toString());
                break;
            default:
                break;
        }
    }

    CommentEntry get(CellAddress cellAddress) {
        return cellAddress == null ? null : comments.get(cellAddress);
    }

    boolean hasComments() {
        return !comments.isEmpty();
    }

    /**
     * Emits all comments as cell content. Called after sheet processing
     * since we bypass POI's built-in comment handling.
     */
    void emitAllComments(XHTMLContentHandler xhtml) throws SAXException {
        for (Map.Entry<CellAddress, CommentEntry> entry : comments.entrySet()) {
            CommentEntry comment = entry.getValue();
            xhtml.startElement("p", "class", "cell-comment");
            String author = comment.getAuthor();
            if (author != null && !author.isEmpty()) {
                xhtml.characters(author + ": ");
            }
            xhtml.characters(comment.getText());
            xhtml.endElement("p");
        }
    }

    static class CommentEntry {
        private final String author;
        private final String text;

        CommentEntry(String author, String text) {
            this.author = author;
            this.text = text;
        }

        String getAuthor() {
            return author;
        }

        String getText() {
            return text;
        }
    }
}
