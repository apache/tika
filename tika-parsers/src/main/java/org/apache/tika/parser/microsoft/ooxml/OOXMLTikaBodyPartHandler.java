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


import java.math.BigInteger;
import java.util.Date;

import org.apache.poi.xwpf.usermodel.UnderlinePatterns;
import org.apache.tika.parser.microsoft.OfficeParserConfig;
import org.apache.tika.parser.microsoft.WordExtractor;
import org.apache.tika.parser.microsoft.ooxml.xwpf.XWPFStylesShim;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

public class OOXMLTikaBodyPartHandler implements OOXMLWordAndPowerPointTextHandler.XWPFBodyContentsHandler {

    private final static String P = "p";

    private final static char[] NEWLINE = new char[]{'\n'};

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
    private boolean isUnderline = false;
    private boolean isStrikeThrough = false;
    private boolean wroteHyperlinkStart = false;

    //TODO: fix this
    //pWithinCell should be an array/stack of given cell depths
    //so that when you get to the end of an embedded table, e.g.,
    //you know what your paragraph count was in the parent cell.
    //<tc><p/><p/><table><tr><tc></p></p></tc></tr></table>...
    private int tableCellDepth = 0;
    private int pWithinCell = 0;

    //will need to replace this with a stack
    //if we're marking more that the first level <p/> element
    private String paragraphTag = null;

    public OOXMLTikaBodyPartHandler(XHTMLContentHandler xhtml) {
        this.xhtml = xhtml;
        this.styles = XWPFStylesShim.EMPTY_STYLES;
        this.listManager = XWPFListManager.EMPTY_LIST;
        this.includeDeletedText = false;
        this.includeMoveFromText = false;
    }

    public OOXMLTikaBodyPartHandler(XHTMLContentHandler xhtml, XWPFStylesShim styles, XWPFListManager listManager, OfficeParserConfig parserConfig) {
        this.xhtml = xhtml;
        this.styles = styles;
        this.listManager = listManager;
        this.includeDeletedText = parserConfig.getIncludeDeletedContent();
        this.includeMoveFromText = parserConfig.getIncludeMoveFromContent();
    }

    @Override
    public void run(RunProperties runProperties, String contents) {
        try {

            // True if we are currently in the named style tag:
            if (runProperties.isBold() != isBold) {
                if (isStrikeThrough) {
                    xhtml.endElement("strike");
                    isStrikeThrough = false;
                }
                if (isUnderline) {
                    xhtml.endElement("u");
                    isUnderline = false;;
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
                    xhtml.endElement("strike");
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
                    xhtml.startElement("strike");
                } else {
                    xhtml.endElement("strike");
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
    public void startParagraph(ParagraphProperties paragraphProperties) {

        //if you're in a table cell and your after the first paragraph
        //make sure to prepend a \n
        if (tableCellDepth > 0 && pWithinCell > 0) {
            try {
                xhtml.characters(NEWLINE, 0, 1);
            } catch (SAXException e) {
                //swallow
            }
        }

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
            if (pDepth == 1 && tableDepth == 0) {
                xhtml.endElement(paragraphTag);
            } else if (tableCellDepth > 0 && pWithinCell > 0){
                xhtml.characters(NEWLINE, 0, 1);
            } else if (tableCellDepth == 0) {
                xhtml.characters(NEWLINE, 0, 1);
            }
        } catch (SAXException e) {

        }
        if (tableCellDepth > 0) {
            pWithinCell++;
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
        tableCellDepth++;
    }

    @Override
    public void endTableCell() {
        try {
            xhtml.endElement("td");
        } catch (SAXException e) {

        }
        pWithinCell = 0;
        tableCellDepth--;
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
    public void startEditedSection(String editor, Date date, OOXMLWordAndPowerPointTextHandler.EditType editType) {
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

        if (isStrikeThrough) {
            xhtml.endElement("strike");
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
