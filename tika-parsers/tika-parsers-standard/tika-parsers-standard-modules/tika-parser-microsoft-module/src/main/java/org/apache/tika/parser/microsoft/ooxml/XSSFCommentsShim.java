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
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.poi.ss.util.CellAddress;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import org.apache.tika.exception.TikaException;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.utils.XMLReaderUtils;

/**
 * SAX-based shim that parses {@code xl/commentsN.xml} without XMLBeans.
 * Replaces POI's {@code CommentsTable} (which depends on poi-ooxml-lite)
 * for Tika's text extraction needs.
 *
 * <p>Only extracts what Tika needs: cell reference → (author, text) mapping.</p>
 */
class XSSFCommentsShim {

    private final Map<CellAddress, CommentData> commentsByCell;

    /**
     * Simple holder for comment data needed by Tika.
     */
    static class CommentData {
        private final String author;
        private final String text;

        CommentData(String author, String text) {
            this.author = author;
            this.text = text;
        }

        public String getAuthor() {
            return author;
        }

        public String getText() {
            return text;
        }
    }

    /**
     * Parse a comments XML stream.
     *
     * @param is           the {@code xl/commentsN.xml} stream (may be null)
     * @param parseContext parse context for SAX parser configuration
     */
    XSSFCommentsShim(InputStream is, ParseContext parseContext)
            throws IOException, TikaException, SAXException {
        commentsByCell = new LinkedHashMap<>();
        if (is != null) {
            CommentsHandler handler = new CommentsHandler();
            XMLReaderUtils.parseSAX(is, handler, parseContext);
        }
    }

    /**
     * @return the number of comments parsed
     */
    int getNumberOfComments() {
        return commentsByCell.size();
    }

    /**
     * Find comment data for a given cell address.
     *
     * @return CommentData or null if no comment at that cell
     */
    CommentData findCellComment(CellAddress cellAddress) {
        return commentsByCell.get(cellAddress);
    }

    /**
     * @return iterator over all cell addresses that have comments, in document order
     */
    Iterator<CellAddress> getCellAddresses() {
        return commentsByCell.keySet().iterator();
    }

    /**
     * SAX handler for comments XML.  Structure:
     * <pre>
     * &lt;comments&gt;
     *   &lt;authors&gt;
     *     &lt;author&gt;Name&lt;/author&gt;
     *   &lt;/authors&gt;
     *   &lt;commentList&gt;
     *     &lt;comment ref="A1" authorId="0"&gt;
     *       &lt;text&gt;
     *         &lt;r&gt;&lt;t&gt;Comment text&lt;/t&gt;&lt;/r&gt;
     *         or plain &lt;t&gt;Comment text&lt;/t&gt;
     *       &lt;/text&gt;
     *     &lt;/comment&gt;
     *   &lt;/commentList&gt;
     * &lt;/comments&gt;
     * </pre>
     */
    private class CommentsHandler extends DefaultHandler {

        private final List<String> authors = new ArrayList<>();
        private final StringBuilder textBuffer = new StringBuilder();

        private boolean inAuthor;
        private boolean inT;
        private boolean inText;

        private String currentRef;
        private int currentAuthorId;
        private final StringBuilder commentText = new StringBuilder();

        @Override
        public void startElement(String uri, String localName, String qName,
                                 Attributes atts) {
            if ("author".equals(localName)) {
                inAuthor = true;
                textBuffer.setLength(0);
            } else if ("comment".equals(localName)) {
                currentRef = atts.getValue("ref");
                String authorIdStr = atts.getValue("authorId");
                currentAuthorId = authorIdStr != null ? Integer.parseInt(authorIdStr) : -1;
                commentText.setLength(0);
            } else if ("text".equals(localName)) {
                inText = true;
            } else if ("t".equals(localName) && inText) {
                inT = true;
                textBuffer.setLength(0);
            }
        }

        @Override
        public void endElement(String uri, String localName, String qName) {
            if ("author".equals(localName)) {
                inAuthor = false;
                authors.add(textBuffer.toString());
            } else if ("t".equals(localName) && inT) {
                inT = false;
                if (commentText.length() > 0) {
                    commentText.append(' ');
                }
                commentText.append(textBuffer);
            } else if ("text".equals(localName)) {
                inText = false;
            } else if ("comment".equals(localName)) {
                if (currentRef != null) {
                    String author = (currentAuthorId >= 0 && currentAuthorId < authors.size())
                            ? authors.get(currentAuthorId) : "";
                    commentsByCell.put(new CellAddress(currentRef),
                            new CommentData(author, commentText.toString()));
                }
                currentRef = null;
            }
        }

        @Override
        public void characters(char[] ch, int start, int length) {
            if (inAuthor || inT) {
                textBuffer.append(ch, start, length);
            }
        }
    }
}
