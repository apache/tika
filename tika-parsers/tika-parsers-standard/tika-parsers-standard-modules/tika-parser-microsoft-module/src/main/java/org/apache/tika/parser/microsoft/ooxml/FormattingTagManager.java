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
import org.apache.poi.xwpf.usermodel.VerticalAlign;
import org.xml.sax.SAXException;

import org.apache.tika.sax.XHTMLContentHandler;

/**
 * Single owner of all run-scoped XHTML wrapper tags, ensuring proper nesting.
 * Nesting order from outermost to innermost:
 * {@code <a href="..."><b><i><s><u><sup>text</sup></u></s></i></b></a>}
 * ({@code <sub>} in place of {@code <sup>}).
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

    // Outermost to innermost: hyperlink > math > span > bold > italic > strike > underline
    private String currentHyperlink = null;
    // inMath spans the equation; mathSpanOpen tracks the span, which a hyperlink change closes
    private boolean inMath = false;
    private boolean mathSpanOpen = false;
    private String currentSpanClass = null;
    private boolean wrapperHyperlinkActive = false;
    private boolean isBold = false;
    private boolean isItalics = false;
    private boolean isStrikeThrough = false;
    private boolean isUnderline = false;
    // "sup", "sub" or null; innermost
    private String vertTag = null;

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
        closeInlineTags();
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
            closeInlineTags();
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
        applyFormatting(runProperties, null);
    }

    /**
     * As {@link #applyFormatting(RunProperties)}, additionally wrapping the run in
     * {@code <span class="spanClass">} when spanClass is non-null.
     */
    void applyFormatting(RunProperties runProperties, String spanClass) throws SAXException {
        // Run-property hyperlinks only when no wrapper is active
        if (!wrapperHyperlinkActive) {
            String newHyperlink = runProperties.getHlinkClickUrl();
            if (!Objects.equals(newHyperlink, currentHyperlink)) {
                closeInlineTags();
                if (currentHyperlink != null) {
                    xhtml.endElement("a");
                }
                if (newHyperlink != null) {
                    xhtml.startElement("a", "href", newHyperlink);
                }
                currentHyperlink = newHyperlink;
            }
        }

        if (inMath && !mathSpanOpen) {
            closeStyledTags();
            xhtml.startElement("span", "class", "omml");
            mathSpanOpen = true;
        }

        if (!Objects.equals(spanClass, currentSpanClass)) {
            closeStyledTags();
            if (spanClass != null) {
                xhtml.startElement("span", "class", spanClass);
            }
            currentSpanClass = spanClass;
        }

        boolean runIsUnderlined = runProperties.getUnderline() != UnderlinePatterns.NONE;
        String runVertTag = toVertTag(runProperties.getVerticalAlign());
        if (vertTag != null && (!vertTag.equals(runVertTag) || runProperties.isBold() != isBold
                || runProperties.isItalics() != isItalics
                || runProperties.isStrikeThrough() != isStrikeThrough
                || runIsUnderlined != isUnderline)) {
            xhtml.endElement(vertTag);
            vertTag = null;
        }

        if (runProperties.isBold() != isBold) {
            // Close inner tags before flipping <b>. Nesting is <b><i><s><u>
            // (outermost to innermost), so close innermost first: u, s, i.
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
            // Close inner tags before flipping <i>: u then s (u is innermost).
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

        if (runIsUnderlined != isUnderline) {
            if (runIsUnderlined) {
                xhtml.startElement("u");
            } else {
                xhtml.endElement("u");
            }
            isUnderline = runIsUnderlined;
        }

        if (runVertTag != null && vertTag == null) {
            xhtml.startElement(runVertTag);
            vertTag = runVertTag;
        }
    }

    private static String toVertTag(VerticalAlign align) {
        switch (align) {
            case SUPERSCRIPT:
                return "sup";
            case SUBSCRIPT:
                return "sub";
            default:
                return null;
        }
    }

    /**
     * Starts an OMML equation, whose text is flattened to plain characters: each of its
     * runs is wrapped in {@code <span class="omml">}, reopened inside any hyperlink that
     * starts or ends mid-equation.
     */
    void openMath() throws SAXException {
        inMath = true;
    }

    void closeMath() throws SAXException {
        closeMathSpan();
        inMath = false;
    }

    /**
     * Closes all currently open tags in proper nesting order.
     */
    void closeAll() throws SAXException {
        closeInlineTags();
        if (currentHyperlink != null) {
            xhtml.endElement("a");
            currentHyperlink = null;
            wrapperHyperlinkActive = false;
        }
    }

    private void closeInlineTags() throws SAXException {
        closeStyledTags();
        closeMathSpan();
    }

    private void closeMathSpan() throws SAXException {
        if (mathSpanOpen) {
            closeStyledTags();
            xhtml.endElement("span");
            mathSpanOpen = false;
        }
    }

    private void closeStyledTags() throws SAXException {
        closeFormattingTags();
        if (currentSpanClass != null) {
            xhtml.endElement("span");
            currentSpanClass = null;
        }
    }

    private void closeFormattingTags() throws SAXException {
        if (vertTag != null) {
            xhtml.endElement(vertTag);
            vertTag = null;
        }
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
