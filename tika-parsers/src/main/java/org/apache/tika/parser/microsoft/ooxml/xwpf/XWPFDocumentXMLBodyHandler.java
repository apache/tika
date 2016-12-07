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


import java.util.Date;
import java.util.Map;

import org.apache.tika.utils.DateUtils;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * This class is intended to handle anything that might contain IBodyElements:
 * main document, headers, footers, notes, etc.
 */

public class XWPFDocumentXMLBodyHandler extends DefaultHandler {



    enum EditType {
        NONE,
        INSERT,
        DELETE,
        MOVE_TO,
        MOVE_FROM
    }


    private final static String W_NS = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";
    private final static String MC_NS = "http://schemas.openxmlformats.org/markup-compatibility/2006";
    private final static String O_NS = "urn:schemas-microsoft-com:office:office";
    private final static String PIC_NS = "http://schemas.openxmlformats.org/drawingml/2006/picture";
    private final static String DRAWING_MAIN_NS = "http://schemas.openxmlformats.org/drawingml/2006/main";
    private static final String V_NS = "urn:schemas-microsoft-com:vml";

    private final static String OFFICE_DOC_RELATIONSHIP_NS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships";

    private final static char[] TAB = new char[1];

    static {
        TAB[0] = '\t';
    }

    private final XWPFBodyContentsHandler bodyContentsHandler;
    //private final RelationshipsManager relationshipsManager;
    private final Map<String, String> linkedRelationships;

    private final StringBuilder runBuffer = new StringBuilder();

    private boolean inR = false;
    private boolean inT = false;
    private int pDepth = 0;
    private boolean inRPr = false;
    private boolean inNumPr = false;
    private boolean inDelText = false;

    private boolean inPic = false;
    private boolean inPict = false;
    private String picDescription = null;
    private String picRId = null;
    private String picFilename = null;
    private boolean lastStartElementWasP;

    //alternate content can be embedded in itself.
    //need to track depth.
    //if in alternate, choose fallback, maybe make this configurable?
    private int inACChoiceDepth = 0;
    private int inACFallbackDepth = 0;
    private EditType editType = EditType.NONE;

    private XWPFRunProperties currRunProperties = new XWPFRunProperties();
    private XWPFParagraphProperties currPProperties = new XWPFParagraphProperties();

    public XWPFDocumentXMLBodyHandler(XWPFBodyContentsHandler bodyContentsHandler,
                                      Map<String, String> hyperlinks) {
        this.bodyContentsHandler = bodyContentsHandler;
        this.linkedRelationships = hyperlinks;
    }


    @Override
    public void startDocument() throws SAXException {
    }

    @Override
    public void endDocument() throws SAXException {
    }

    @Override
    public void startPrefixMapping(String prefix, String uri) throws SAXException {
    }

    @Override
    public void endPrefixMapping(String prefix) throws SAXException {
    }

    @Override
    public void startElement(String uri, String localName, String qName, Attributes atts) throws SAXException {

        if (lastStartElementWasP && ! "pPr".equals(localName)) {
            bodyContentsHandler.startParagraph(currPProperties);
        }
        lastStartElementWasP = false;

        if (uri != null && uri.equals(MC_NS)) {
            if (localName.equals("Choice")) {
                inACChoiceDepth++;
            } else if (localName.equals("Fallback")) {
                inACFallbackDepth++;
            }
        }

        if (inACChoiceDepth > 0) {
            return;
        }
        if (uri == null || uri.equals(O_NS)) {
            if (localName.equals("OLEObject")) {
                String type = null;
                String refId = null;
                //TODO: want to get ProgID?
                for (int i = 0; i < atts.getLength(); i++) {
                    String attLocalName = atts.getLocalName(i);
                    String attValue = atts.getValue(i);
                    if (attLocalName.equals("Type")) {
                        type = attValue;
                    } else if (OFFICE_DOC_RELATIONSHIP_NS.equals(atts.getURI(i)) && attLocalName.equals("id")) {
                        refId = attValue;
                    }
                }
                if ("Embed".equals(type)) {
                    bodyContentsHandler.embeddedOLERef(refId);
                }
            }
        }

        if (uri == null || uri.equals(PIC_NS)) {
            if ("pic".equals(localName)) {
                inPic = true;
            } else if ("cNvPr".equals(localName)) {
                picDescription = atts.getValue("", "descr");
            }
        }

        if (uri == null || uri.equals(DRAWING_MAIN_NS)) {
            if ("blip".equals(localName)) {
                picRId = atts.getValue(OFFICE_DOC_RELATIONSHIP_NS, "embed");
            }
        }

        if (uri == null || uri.equals(V_NS)) {
            if ("imagedata".equals(localName)) {
                picRId = atts.getValue(OFFICE_DOC_RELATIONSHIP_NS, "id");
                picDescription = atts.getValue(O_NS, "title");
            }
        }

        if (uri == null || uri.equals(W_NS)) {
            if (localName.equals("p")) {
                lastStartElementWasP = true;
                pDepth++;
            } else if (localName.equals("r")) {
                inR = true;
            } else if (localName.equals("t")) {
                inT = true;
            } else if (localName.equals("tab")) {
                runBuffer.append("\t");
            } else if (localName.equals("tbl")) {
                bodyContentsHandler.startTable();
            } else if (localName.equals("tc")) {
                bodyContentsHandler.startTableCell();
            } else if (localName.equals("tr")) {
                bodyContentsHandler.startTableRow();
            } else if (localName.equals("numPr")) {
                inNumPr = true;
            } else if (localName.equals("rPr")) {
                inRPr = true;
            } else if (inR && inRPr && localName.equals("i")) {
                //rprs don't have to be inR; ignore those that aren't
                currRunProperties.setItalics(true);
            } else if (inR && inRPr && localName.equals("b")) {
                currRunProperties.setBold(true);
            } else if (localName.equals("delText")) {
                inDelText = true;
            } else if (localName.equals("ins")) {
                startEditedSection(editType.INSERT, atts);
            } else if (localName.equals("del")) {
                startEditedSection(editType.DELETE, atts);
            } else if (localName.equals("moveTo")) {
                startEditedSection(EditType.MOVE_TO, atts);
            } else if (localName.equals("moveFrom")) {
                startEditedSection(editType.MOVE_FROM, atts);
            } else if (localName.equals("hyperlink")) {
                String hyperlinkId = atts.getValue(OFFICE_DOC_RELATIONSHIP_NS, "id");
                String hyperlink = null;
                if (hyperlinkId != null) {
                    hyperlink = linkedRelationships.get(hyperlinkId);
                    bodyContentsHandler.hyperlinkStart(hyperlink);
                } else {
                    String anchor = atts.getValue(W_NS, "anchor");
                    if (anchor != null) {
                        anchor = "#"+anchor;
                    }
                    bodyContentsHandler.hyperlinkStart(anchor);
                }
            } else if (localName.equals("footnoteReference")) {
                String id = atts.getValue(W_NS, "id");
                bodyContentsHandler.footnoteReference(id);
            } else if (localName.equals("endnoteReference")) {
                String id = atts.getValue(W_NS, "id");
                bodyContentsHandler.endnoteReference(id);
            } else if ("pStyle".equals(localName)) {
                String styleId = atts.getValue(W_NS, "val");
                currPProperties.setStyleID(styleId);
            } else if (inNumPr && "ilvl".equals(localName)) {
                currPProperties.setIlvl(getIntVal(atts));
            } else if (inNumPr && "numId".equals(localName)) {
                currPProperties.setNumId(getIntVal(atts));
            } else if ("bookmarkStart".equals(localName)) {
                String name = atts.getValue(W_NS, "name");
                String id = atts.getValue(W_NS, "id");
                bodyContentsHandler.startBookmark(id, name);
            } else if ("bookmarkEnd".equals(localName)) {
                String id = atts.getValue(W_NS, "id");
                bodyContentsHandler.endBookmark(id);
            }
            /*else if (localName.equals("headerReference")) {
                //TODO
            } else if (localName.equals("footerReference")) {
                //TODO
            } else if (localName.equals("commentRangeEnd")) {
                //TODO
            }*/

        }
    }

    private void startEditedSection(EditType editType, Attributes atts) {
        String editAuthor = atts.getValue(W_NS, "author");
        String editDateString = atts.getValue(W_NS, "date");
        Date editDate = null;
        if (editDateString != null) {
            editDate = DateUtils.tryToParse(editDateString);
        }
        bodyContentsHandler.startEditedSection(editAuthor, editDate, editType);
        this.editType = editType;
    }

    private int getIntVal(Attributes atts) {
        String valString = atts.getValue(W_NS, "val");
        if (valString != null) {
            try {
                return Integer.parseInt(valString);
            } catch (NumberFormatException e) {
                //swallow
            }
        }
        return -1;
    }


    @Override
    public void endElement(String uri, String localName, String qName) throws SAXException {
        if (uri.equals(MC_NS)) {
            if (localName.equals("Choice")) {
                inACChoiceDepth--;
            } else if (localName.equals("Fallback")) {
                inACFallbackDepth--;
            }
        }

        if (PIC_NS.equals(uri)) {
            if ("pic".equals(localName)) {
                handlePict();
                inPic = false;
                return;
            }

        }
        if (uri == null || uri.equals(W_NS)) {
            if (inACChoiceDepth > 0) {
                return;
            }


            if (localName.equals("r")) {
                bodyContentsHandler.run(currRunProperties, runBuffer.toString());
                inR = false;
                runBuffer.setLength(0);
                currRunProperties.setBold(false);
                currRunProperties.setItalics(false);
            } else if (localName.equals("p")) {
                bodyContentsHandler.endParagraph();
                pDepth--;
            } else if (localName.equals("t")) {
                inT = false;
            } else if (localName.equals("tbl")) {
                bodyContentsHandler.endTable();
            } else if (localName.equals("tc")) {
                bodyContentsHandler.endTableCell();
            } else if (localName.equals("tr")) {
                bodyContentsHandler.endTableRow();
            } else if (localName.equals("rPr")) {
                inRPr = false;
            } else if (localName.equals("delText")) {
                inDelText = false;
            } else if (localName.equals("ins") || localName.equals("del") ||
                    localName.equals("moveTo") || localName.equals("moveFrom")) {
                editType = EditType.NONE;
            } else if (localName.equals("hyperlink")) {
                bodyContentsHandler.hyperlinkEnd();
            } else if ("pict".equals(localName)) {
                handlePict();
            } else if ("pPr".equals(localName)) {
                bodyContentsHandler.startParagraph(currPProperties);
                currPProperties.reset();
            }
        }
    }

    private void handlePict() {
        String picFileName = null;
        if (picRId != null) {
            picFileName = linkedRelationships.get(picRId);
        }
        bodyContentsHandler.embeddedPicRef(picFileName, picDescription);
        picDescription = null;
        picRId = null;
        inPic = false;
    }

    @Override
    public void characters(char[] ch, int start, int length) throws SAXException {

        if (inACChoiceDepth > 0) {
            return;
        }
        if (editType.equals(EditType.MOVE_FROM) && inT) {
            if (bodyContentsHandler.getIncludeMoveFromText()) {
                runBuffer.append(ch, start, length);
            }
        } else if (inT) {
            runBuffer.append(ch, start, length);
        } else if (bodyContentsHandler.getIncludeDeletedText() && editType.equals(EditType.DELETE)) {
            runBuffer.append(ch, start, length);
        }
    }

    @Override
    public void ignorableWhitespace(char[] ch, int start, int length) throws SAXException {
        if (inACChoiceDepth > 0) {
            return;
        }

        if (inT) {
            runBuffer.append(ch, start, length);
        } else if (bodyContentsHandler.getIncludeDeletedText() && inDelText) {
            runBuffer.append(ch, start, length);
        }
    }


    public interface XWPFBodyContentsHandler {

        void run(XWPFRunProperties runProperties, String contents);

        /**
         *
         * @param link the link; can be null
         */
        void hyperlinkStart(String link);

        void hyperlinkEnd();

        void startParagraph(XWPFParagraphProperties paragraphProperties);

        void endParagraph();

        void startTable();

        void endTable();

        void startTableRow();

        void endTableRow();

        void startTableCell();

        void endTableCell();

        void startSDT();

        void endSDT();

        void startEditedSection(String editor, Date date, EditType editType);

        void endEditedSection();

        boolean getIncludeDeletedText();

        void footnoteReference(String id);

        void endnoteReference(String id);

        boolean getIncludeMoveFromText();

        void embeddedOLERef(String refId);

        void embeddedPicRef(String picFileName, String picDescription);

        void startBookmark(String id, String name);

        void endBookmark(String id);
    }
}
