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

import org.apache.poi.xwpf.usermodel.UnderlinePatterns;
import org.xml.sax.SAXException;

import org.apache.tika.sax.XHTMLContentHandler;

/**
 * Manages all inline XHTML elements (hyperlinks and formatting tags) as a
 * unified state machine, ensuring proper nesting.  The nesting order from
 * outermost to innermost is:
 * <pre>
 *   {@code <a> <b> <i> <s> <u> text </u> </s> </i> </b> </a>}
 * </pre>
 * <p>
 * When a change occurs to an outer element, all inner elements are closed
 * first, the change is applied, then inner elements are reopened as needed.
 * This prevents generating malformed XHTML with overlapping or unbalanced tags.
 * <p>
 * This class replaces the separate {@code FormattingTagManager} and the
 * {@code wroteHyperlinkStart} boolean that were previously tracked independently
 * in {@link OOXMLTikaBodyPartHandler}.
 */
class InlineTagManager {

    private final XHTMLContentHandler xhtml;

    private boolean hyperlinkOpen = false;
    private boolean isBold = false;
    private boolean isItalics = false;
    private boolean isStrikeThrough = false;
    private boolean isUnderline = false;

    InlineTagManager(XHTMLContentHandler xhtml) {
        this.xhtml = xhtml;
    }

    /**
     * Opens a hyperlink.  Since {@code <a>} is the outermost inline element,
     * any existing inline elements (including a prior hyperlink) are closed
     * first.
     *
     * @param href the link target; if {@code null} this is a no-op
     */
    void openHyperlink(String href) throws SAXException {
        if (href == null) {
            return;
        }
        // Close everything — formatting then any existing hyperlink
        closeAll();
        xhtml.startElement("a", "href", href);
        hyperlinkOpen = true;
    }

    /**
     * Closes the current hyperlink and all formatting inside it.
     * No-op if no hyperlink is open.
     */
    void closeHyperlink() throws SAXException {
        if (!hyperlinkOpen) {
            return;
        }
        closeFormatting();
        xhtml.endElement("a");
        hyperlinkOpen = false;
    }

    /**
     * Returns {@code true} if a hyperlink is currently open.
     */
    boolean isHyperlinkOpen() {
        return hyperlinkOpen;
    }

    /**
     * Reconciles the current formatting state with the given run properties,
     * opening and closing XHTML tags as needed to maintain proper nesting.
     * The nesting order for formatting is: {@code <b> <i> <s> <u>}.
     */
    void applyFormatting(RunProperties runProperties) throws SAXException {
        if (runProperties.isBold() != isBold) {
            // Bold is outermost formatting — close everything inside it
            if (isUnderline) {
                xhtml.endElement("u");
                isUnderline = false;
            }
            if (isStrikeThrough) {
                xhtml.endElement("s");
                isStrikeThrough = false;
            }
            if (isItalics) {
                xhtml.endElement("i");
                isItalics = false;
            }
            if (runProperties.isBold()) {
                xhtml.startElement("b");
            } else {
                xhtml.endElement("b");
            }
            isBold = runProperties.isBold();
        }

        if (runProperties.isItalics() != isItalics) {
            if (isUnderline) {
                xhtml.endElement("u");
                isUnderline = false;
            }
            if (isStrikeThrough) {
                xhtml.endElement("s");
                isStrikeThrough = false;
            }
            if (runProperties.isItalics()) {
                xhtml.startElement("i");
            } else {
                xhtml.endElement("i");
            }
            isItalics = runProperties.isItalics();
        }

        if (runProperties.isStrikeThrough() != isStrikeThrough) {
            if (isUnderline) {
                xhtml.endElement("u");
                isUnderline = false;
            }
            if (runProperties.isStrikeThrough()) {
                xhtml.startElement("s");
            } else {
                xhtml.endElement("s");
            }
            isStrikeThrough = runProperties.isStrikeThrough();
        }

        boolean runIsUnderlined = runProperties.getUnderline() != UnderlinePatterns.NONE;
        if (runIsUnderlined != isUnderline) {
            if (runIsUnderlined) {
                xhtml.startElement("u");
            } else {
                xhtml.endElement("u");
            }
            isUnderline = runIsUnderlined;
        }
    }

    /**
     * Closes all currently open formatting tags in proper nesting order
     * (innermost first: u, s, i, b).  Does NOT close the hyperlink.
     */
    void closeFormatting() throws SAXException {
        if (isUnderline) {
            xhtml.endElement("u");
            isUnderline = false;
        }
        if (isStrikeThrough) {
            xhtml.endElement("s");
            isStrikeThrough = false;
        }
        if (isItalics) {
            xhtml.endElement("i");
            isItalics = false;
        }
        if (isBold) {
            xhtml.endElement("b");
            isBold = false;
        }
    }

    /**
     * Closes ALL open inline elements — formatting first, then hyperlink.
     * This is the primary safety mechanism: call at every structural boundary
     * (end of paragraph, table cell, table row, table, etc.) to guarantee
     * well-formed XHTML.
     */
    void closeAll() throws SAXException {
        closeFormatting();
        if (hyperlinkOpen) {
            xhtml.endElement("a");
            hyperlinkOpen = false;
        }
    }
}
