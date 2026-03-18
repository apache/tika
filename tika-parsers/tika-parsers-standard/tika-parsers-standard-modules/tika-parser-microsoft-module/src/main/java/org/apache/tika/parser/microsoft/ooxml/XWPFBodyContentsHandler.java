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

import java.util.Date;

import org.xml.sax.SAXException;

/**
 * Callback interface for receiving structured document events from the
 * OOXML SAX dispatcher. Implementations convert these events into output
 * formats (e.g., XHTML, Markdown, plain text).
 */
public interface XWPFBodyContentsHandler {

    void run(RunProperties runProperties, String contents) throws SAXException;

    /**
     * @param link the link; can be null
     */
    void hyperlinkStart(String link) throws SAXException;

    /**
     * Called when a hyperlink is found via a field code (instrText HYPERLINK).
     * Distinct from relationship-based hyperlinks for security tracking purposes.
     *
     * @param link the link URL
     */
    default void fieldCodeHyperlinkStart(String link) throws SAXException {
        hyperlinkStart(link);
    }

    void hyperlinkEnd() throws SAXException;

    void startParagraph(ParagraphProperties paragraphProperties) throws SAXException;

    void endParagraph() throws SAXException;

    void startTable() throws SAXException;

    void endTable() throws SAXException;

    void startTableRow() throws SAXException;

    void endTableRow() throws SAXException;

    void startTableCell() throws SAXException;

    void endTableCell() throws SAXException;

    void startSDT() throws SAXException;

    void endSDT() throws SAXException;

    void startEditedSection(String editor, Date date, EditType editType) throws SAXException;

    void endEditedSection() throws SAXException;

    boolean isIncludeDeletedText() throws SAXException;

    void footnoteReference(String id) throws SAXException;

    void endnoteReference(String id) throws SAXException;

    /**
     * Called when a comment reference is encountered in the document body.
     *
     * @param id the comment ID
     */
    void commentReference(String id) throws SAXException;

    boolean isIncludeMoveFromText() throws SAXException;

    void embeddedOLERef(String refId) throws SAXException;

    /**
     * Called when a linked (vs embedded) OLE object is found.
     * These reference external files and are a security concern.
     */
    void linkedOLERef(String refId) throws SAXException;

    void embeddedPicRef(String picFileName, String picDescription) throws SAXException;

    void startBookmark(String id, String name) throws SAXException;

    void endBookmark(String id) throws SAXException;

    /**
     * Called when an external reference URL is found in a field code.
     * This includes INCLUDEPICTURE, INCLUDETEXT, IMPORT, LINK fields,
     * and DrawingML/VML hyperlinks on shapes.
     *
     * @param fieldType the type of field (e.g., "INCLUDEPICTURE", "hlinkHover", "vml-href")
     * @param url the external URL
     */
    default void externalRef(String fieldType, String url) throws SAXException {
        // Default no-op implementation for backward compatibility
    }
}
