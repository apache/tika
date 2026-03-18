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
 * Manages XHTML formatting tags (b, i, u, s) as a state machine,
 * ensuring proper nesting. Tags are always ordered from outermost to innermost:
 * {@code <b><i><s><u>text</u></s></i></b>}.
 * <p>
 * When a formatting change occurs, all tags that are "inside" the changing tag
 * must be closed first, then the change applied, then inner tags reopened.
 * This avoids generating malformed XHTML with overlapping tags.
 */
class FormattingTagManager {

    private final XHTMLContentHandler xhtml;

    private boolean isBold = false;
    private boolean isItalics = false;
    private boolean isStrikeThrough = false;
    private boolean isUnderline = false;

    FormattingTagManager(XHTMLContentHandler xhtml) {
        this.xhtml = xhtml;
    }

    /**
     * Reconciles the current formatting state with the given run properties,
     * opening and closing XHTML tags as needed to maintain proper nesting.
     */
    void applyFormatting(RunProperties runProperties) throws SAXException {
        if (runProperties.isBold() != isBold) {
            // Bold is outermost — close everything inside it
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
     * Closes all currently open formatting tags in proper nesting order
     * (innermost first: u, s, i, b).
     */
    void closeAll() throws SAXException {
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
