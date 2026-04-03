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

import java.util.Objects;

import org.apache.poi.xwpf.usermodel.UnderlinePatterns;
import org.xml.sax.SAXException;

import org.apache.tika.sax.XHTMLContentHandler;

/**
 * Single owner of all run-scoped XHTML wrapper tags, ensuring proper nesting.
 * Nesting order from outermost to innermost:
 * {@code <a href="..."><b><i><s><u>text</u></s></i></b></a>}.
 * <p>
 * Hyperlinks come from two OOXML sources with different lifecycles:
 * <ul>
 *   <li><b>Wrapper hyperlinks</b> (DOCX {@code <w:hyperlink>}, field-code HYPERLINK):
 *       opened/closed explicitly via {@link #openHyperlink}/{@link #closeHyperlink},
 *       span multiple runs.</li>
 *   <li><b>Run-property hyperlinks</b> (PPTX {@code <a:hlinkClick>}):
 *       set on {@link RunProperties#setHlinkClickUrl}, managed automatically
 *       by {@link #applyFormatting} per-run.</li>
 * </ul>
 * Both emit the same {@code <a href="...">} XHTML. Wrapper hyperlinks take
 * precedence — run properties cannot override an active wrapper.
 */
class FormattingTagManager {

    private final XHTMLContentHandler xhtml;

    // Outermost to innermost: hyperlink > bold > italic > strike > underline
    private String currentHyperlink = null;
    private boolean wrapperHyperlinkActive = false;
    private boolean isBold = false;
    private boolean isItalics = false;
    private boolean isStrikeThrough = false;
    private boolean isUnderline = false;

    FormattingTagManager(XHTMLContentHandler xhtml) {
        this.xhtml = xhtml;
    }

    /**
     * Opens a wrapper-style hyperlink (DOCX {@code <w:hyperlink>} or field-code).
     * Closes any open formatting tags first to maintain nesting.
     * No-op if url is null.
     */
    void openHyperlink(String url) throws SAXException {
        if (url == null) {
            return;
        }
        closeFormattingTags();
        if (currentHyperlink != null) {
            xhtml.endElement("a");
        }
        xhtml.startElement("a", "href", url);
        currentHyperlink = url;
        wrapperHyperlinkActive = true;
    }

    /**
     * Closes the active wrapper-style hyperlink. No-op if none was opened.
     */
    void closeHyperlink() throws SAXException {
        if (currentHyperlink != null && wrapperHyperlinkActive) {
            closeFormattingTags();
            xhtml.endElement("a");
            currentHyperlink = null;
            wrapperHyperlinkActive = false;
        }
    }

    /**
     * Returns true if any hyperlink (wrapper or run-property) is currently open.
     */
    boolean isHyperlinkActive() {
        return currentHyperlink != null;
    }

    /**
     * Reconciles the current formatting state with the given run properties,
     * opening and closing XHTML tags as needed to maintain proper nesting.
     */
    void applyFormatting(RunProperties runProperties) throws SAXException {
        // Run-property hyperlinks only when no wrapper is active
        if (!wrapperHyperlinkActive) {
            String newHyperlink = runProperties.getHlinkClickUrl();
            if (!Objects.equals(newHyperlink, currentHyperlink)) {
                closeFormattingTags();
                if (currentHyperlink != null) {
                    xhtml.endElement("a");
                }
                if (newHyperlink != null) {
                    xhtml.startElement("a", "href", newHyperlink);
                }
                currentHyperlink = newHyperlink;
            }
        }

        if (runProperties.isBold() != isBold) {
            if (isStrikeThrough) {
                xhtml.endElement("s");
                isStrikeThrough = false;
            }
            if (isUnderline) {
                xhtml.endElement("u");
                isUnderline = false;
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
            if (isStrikeThrough) {
                xhtml.endElement("s");
                isStrikeThrough = false;
            }
            if (isUnderline) {
                xhtml.endElement("u");
                isUnderline = false;
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
     * Closes all currently open tags in proper nesting order.
     */
    void closeAll() throws SAXException {
        closeFormattingTags();
        if (currentHyperlink != null) {
            xhtml.endElement("a");
            currentHyperlink = null;
            wrapperHyperlinkActive = false;
        }
    }

    private void closeFormattingTags() throws SAXException {
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
}
