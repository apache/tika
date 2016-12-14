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

package org.apache.tika.parser.microsoft.ooxml.xwpf;


import java.math.BigInteger;
import java.util.Date;

import org.apache.tika.parser.microsoft.OfficeParserConfig;
import org.apache.tika.parser.microsoft.WordExtractor;
import org.apache.tika.parser.microsoft.ooxml.XWPFListManager;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

public class XWPFTikaBodyPartHandler implements XWPFDocumentXMLBodyHandler.XWPFBodyContentsHandler {

    private final static String P = "p";

    private final static char[] NEWLINE = new char[]{'\n'};
    private final static char[] TAB = new char[]{'\t'};

    private final XHTMLContentHandler xhtml;
    private final XWPFListManager listManager;
    private final boolean includeDeletedText;
    private final boolean includeMoveFromText;
    private final XWPFStylesShim styles;

    private int pDepth = 0; //paragraph depth
    private int tableDepth = 0;//table depth
    private int sdtDepth = 0;//
    private boolean isItalics = false;
    private boolean isBold = false;
    private boolean wroteHyperlinkStart = false;

    //will need to replace this with a stack
    //if we're marking more that the first level <p/> element
    private String paragraphTag = null;

    public XWPFTikaBodyPartHandler(XHTMLContentHandler xhtml, XWPFStylesShim styles, XWPFListManager listManager, OfficeParserConfig parserConfig) {
        this.xhtml = xhtml;
        this.styles = styles;
        this.listManager = listManager;
        this.includeDeletedText = parserConfig.getIncludeDeletedContent();
        this.includeMoveFromText = parserConfig.getIncludeMoveFromContent();
    }

    @Override
    public void run(XWPFRunProperties runProperties, String contents) {
        try {
            // True if we are currently in the named style tag:
            if (runProperties.getBold() != isBold) {
                if (isItalics) {
                    xhtml.endElement("i");
                    isItalics = false;
                }
                if (runProperties.getBold()) {
                    xhtml.startElement("b");
                    isBold = true;
                } else {
                    xhtml.endElement("b");
                    isBold = false;
                }
            }

            if (runProperties.getItalics() != isItalics) {
                if (runProperties.getItalics()) {
                    xhtml.startElement("i");
                    isItalics = true;
                } else {
                    xhtml.endElement("i");
                    isItalics = false;
                }
            }

            xhtml.characters(contents);

        } catch (SAXException e) {

        }
    }

    @Override
    public void hyperlinkStart(String link) {
        try {
            if (link != null) {
                xhtml.startElement("a", "href", link);
                wroteHyperlinkStart = true;
            }
        } catch (SAXException e) {

        }
    }

    @Override
    public void hyperlinkEnd() {
        try {
            if (wroteHyperlinkStart) {
                closeStyleTags();
                wroteHyperlinkStart = false;
                xhtml.endElement("a");
            }
        } catch (SAXException e) {

        }
    }

    @Override
    public void startParagraph(XWPFParagraphProperties paragraphProperties) {
        if (pDepth == 0 && tableDepth == 0 && sdtDepth == 0) {
            paragraphTag = P;
            String styleClass = null;
            //TIKA-2144 check that styles is not null
            if (paragraphProperties.getStyleID() != null && styles != null) {
                String styleName = styles.getStyleName(
                        paragraphProperties.getStyleID()
                );
                if (styleName != null) {
                    WordExtractor.TagAndStyle tas = WordExtractor.buildParagraphTagAndStyle(
                            styleName, false);
                    paragraphTag = tas.getTag();
                    styleClass = tas.getStyleClass();
                }
            }


            try {
                if (styleClass == null) {
                    xhtml.startElement(paragraphTag);
                } else {
                    xhtml.startElement(paragraphTag, "class", styleClass);
                }
            } catch (SAXException e) {

            }
        }

        try {
            writeParagraphNumber(paragraphProperties.getNumId(),
                    paragraphProperties.getIlvl(), listManager, xhtml);
        } catch (SAXException e) {

        }
        pDepth++;
    }

    @Override
    public void endParagraph() {
        try {
            closeStyleTags();
            if (pDepth == 1 && tableDepth == 0 && sdtDepth == 0) {
                xhtml.endElement(paragraphTag);
                paragraphTag = null;
            } else {
                xhtml.characters(NEWLINE, 0, 1);
            }
        } catch (SAXException e) {

        }
        pDepth--;
    }

    @Override
    public void startTable() {
        try {
            xhtml.startElement("table");
            tableDepth++;
        } catch (SAXException e) {

        }
    }

    @Override
    public void endTable() {
        try {
            xhtml.endElement("table");
            tableDepth--;
        } catch (SAXException e) {

        }
    }

    @Override
    public void startTableRow() {
        try {
            xhtml.startElement("tr");
        } catch (SAXException e) {

        }
    }

    @Override
    public void endTableRow() {
        try {
            xhtml.endElement("tr");
        } catch (SAXException e) {

        }
    }

    @Override
    public void startTableCell() {
        try {
            xhtml.startElement("td");
        } catch (SAXException e) {

        }
    }

    @Override
    public void endTableCell() {
        try {
            xhtml.endElement("td");
        } catch (SAXException e) {

        }
    }

    @Override
    public void startSDT() {
        try {
            closeStyleTags();
            sdtDepth++;
        } catch (SAXException e) {

        }
    }

    @Override
    public void endSDT() {
        sdtDepth--;
    }

    @Override
    public void startEditedSection(String editor, Date date, XWPFDocumentXMLBodyHandler.EditType editType) {
        //no-op
    }

    @Override
    public void endEditedSection() {
        //no-op
    }

    @Override
    public boolean getIncludeDeletedText() {
        return includeDeletedText;
    }

    @Override
    public void footnoteReference(String id) {
        if (id != null) {
            try {
                xhtml.characters("[");
                xhtml.characters(id);
                xhtml.characters("]");
            } catch (SAXException e) {

            }
        }
    }

    @Override
    public void endnoteReference(String id) {
        if (id != null) {
            try {
                xhtml.characters("[");
                xhtml.characters(id);
                xhtml.characters("]");
            } catch (SAXException e) {

            }
        }
    }

    @Override
    public boolean getIncludeMoveFromText() {
        return includeMoveFromText;
    }

    @Override
    public void embeddedOLERef(String relId) {
        if (relId == null) {
            return;
        }
        try {
            AttributesImpl attributes = new AttributesImpl();
            attributes.addAttribute("", "class", "class", "CDATA", "embedded");
            attributes.addAttribute("", "id", "id", "CDATA", relId);
            xhtml.startElement("div", attributes);
            xhtml.endElement("div");

        } catch (SAXException e) {

        }
    }

    @Override
    public void embeddedPicRef(String picFileName, String picDescription) {

        try {
            AttributesImpl attr = new AttributesImpl();
            if (picFileName != null) {
                attr.addAttribute("", "src", "src", "CDATA", "embedded:" + picFileName);
            }
            if (picDescription != null) {
                attr.addAttribute("", "alt", "alt", "CDATA", picDescription);
            }

            xhtml.startElement("img", attr);
            xhtml.endElement("img");

        } catch (SAXException e) {

        }
    }

    @Override
    public void startBookmark(String id, String name) {
        //skip bookmarks within hyperlinks
        if (name != null && ! wroteHyperlinkStart) {
            try {
                xhtml.startElement("a", "name", name);
                xhtml.endElement("a");
            } catch (SAXException e) {

            }
        }
    }

    @Override
    public void endBookmark(String id) {
        //no-op
    }

    private void closeStyleTags() throws SAXException {
        if (isItalics) {
            xhtml.endElement("i");
            isItalics = false;
        }
        if (isBold) {
            xhtml.endElement("b");
            isBold = false;
        }
    }

    private void writeParagraphNumber(int numId, int ilvl,
                                      XWPFListManager listManager,
                                      XHTMLContentHandler xhtml) throws SAXException {

        if (ilvl < 0 || numId < 0 || listManager == null) {
            return;
        }
        String number = listManager.getFormattedNumber(BigInteger.valueOf(numId), ilvl);
        if (number != null) {
            xhtml.characters(number);
        }

    }
}
