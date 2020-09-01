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
import java.util.Map;

import org.apache.poi.xwpf.usermodel.UnderlinePatterns;
import org.apache.tika.parser.microsoft.OfficeParserConfig;
import org.apache.tika.utils.DateUtils;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * This class is intended to handle anything that might contain IBodyElements:
 * main document, headers, footers, notes, slides, etc.
 *
 * <p/>
 *
 * This class does not generally check for namespaces, and it can be applied
 * to PPTX and DOCX for text extraction.
 *
 * <p/>
 * This can be used to scrape content from charts.  It currently ignores
 * formula (&lt;c:f/&gt;) elements
 *
 * <p/>
 * This does not work with .xlsx or .vsdx.
 *
 * TODO: move this into POI?
 *
 */

public class OOXMLWordAndPowerPointTextHandler extends DefaultHandler {


    public enum EditType {
        NONE,
        INSERT,
        DELETE,
        MOVE_TO,
        MOVE_FROM
    }

    private final static String R = "r";
    private final static String FLD = "fld";
    private final static String RPR = "rPr";
    private final static String P = "p";
    private final static String P_STYLE = "pStyle";
    private final static String PPR = "pPr";
    private final static String T = "t";
    private final static String TAB = "tab";
    private final static String B = "b";
    private final static String ILVL = "ilvl";
    private final static String NUM_ID = "numId";
    private final static String TC = "tc";
    private final static String TR = "tr";
    private final static String I = "i";
    private final static String U = "u";
    private final static String STRIKE = "strike";
    private final static String NUM_PR = "numPr";
    private final static String BR = "br";
    private final static String HYPERLINK = "hyperlink";
    private final static String HLINK_CLICK = "hlinkClick"; //pptx hlink
    private final static String TBL = "tbl";
    private final static String PIC = "pic";
    private final static String PICT = "pict";
    private final static String IMAGEDATA = "imagedata";
    private final static String BLIP = "blip";
    private final static String CHOICE = "Choice";
    private final static String FALLBACK = "Fallback";
    private final static String OLE_OBJECT = "OLEObject";
    private final static String CR = "cr";
    private final static String V = "v";
    private final static String RUBY = "ruby"; //phonetic section
    private final static String RT = "rt"; //phonetic run
    private static final String VAL = "val";


    public final static String W_NS = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";
    private final static String MC_NS = "http://schemas.openxmlformats.org/markup-compatibility/2006";
    private final static String O_NS = "urn:schemas-microsoft-com:office:office";
    private final static String PIC_NS = "http://schemas.openxmlformats.org/drawingml/2006/picture";
    private final static String DRAWING_MAIN_NS = "http://schemas.openxmlformats.org/drawingml/2006/main";
    private final static String V_NS = "urn:schemas-microsoft-com:vml";
    private final static String C_NS = "http://schemas.openxmlformats.org/drawingml/2006/chart";

    private final static String OFFICE_DOC_RELATIONSHIP_NS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships";

    private final static char[] TAB_CHAR = new char[]{'\t'};
    private final static char NEWLINE = '\n';
    
    private final static String BOOKMARK_START = "bookmarkStart";
    private final static String BOOKMARK_END = "bookmarkEnd";
    private final static String FOOTNOTE_REFERENCE = "footnoteReference";
    private final static String INS = "ins";
    private final static String DEL = "del";
    private final static String DEL_TEXT = "delText";
    private final static String MOVE_FROM = "moveFrom";
    private final static String MOVE_TO = "moveTo";
    private final static String ENDNOTE_REFERENCE = "endnoteReference";
    private static final String TEXTBOX = "textbox";


    private final XWPFBodyContentsHandler bodyContentsHandler;

    private final Map<String, String> linkedRelationships;

    private boolean inR = false;//in run or in field. TODO: convert this to an integer because you can have a run within a run
    private boolean inT = false;
    private boolean inRPr = false;
    private boolean inNumPr = false;
    private boolean inRt = false;

    private boolean inPic = false;
    private boolean inPict = false;
    private String picDescription = null;
    private String picRId = null;
    private String picFilename = null;

    //mechanism used to determine when to
    //signal the start of the p, and still
    //handle p with pPr and those without
    private boolean lastStartElementWasP = false;
    //have we signaled the start of a p?
    //pPr can happen multiple times within a p
    //<p><pPr/><r><t>text</t></r><pPr></p>
    private boolean pStarted = false;

    //alternate content can be embedded in itself.
    //need to track depth.
    //if in alternate, choose fallback, maybe make this configurable?
    private int inACChoiceDepth = 0;
    private int inACFallbackDepth = 0;

    private final RunProperties currRunProperties = new RunProperties();
    private final ParagraphProperties currPProperties = new ParagraphProperties();
    private final boolean includeTextBox;
    private final boolean concatenatePhoneticRuns;
    private final StringBuilder runBuffer = new StringBuilder();
    private final StringBuilder rubyBuffer = new StringBuilder();//buffers rt in ruby sections (see 17.3.3.25)


    private boolean inDelText = false;
    private boolean inHlinkClick = false;
    private boolean inTextBox = false;
    private boolean inV = false; //in c:v in chart file

    private OOXMLWordAndPowerPointTextHandler.EditType editType = OOXMLWordAndPowerPointTextHandler.EditType.NONE;

    private DateUtils dateUtils = new DateUtils();

    public OOXMLWordAndPowerPointTextHandler(XWPFBodyContentsHandler bodyContentsHandler,
                                             Map<String, String> hyperlinks) {
        this(bodyContentsHandler, hyperlinks, true, true);
    }


    public OOXMLWordAndPowerPointTextHandler(XWPFBodyContentsHandler bodyContentsHandler,
                                             Map<String, String> hyperlinks, boolean includeTextBox, boolean concatenatePhoneticRuns) {
        this.bodyContentsHandler = bodyContentsHandler;
        this.linkedRelationships = hyperlinks;
        this.includeTextBox = includeTextBox;
        this.concatenatePhoneticRuns = concatenatePhoneticRuns;
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
        //TODO: checkBox, textBox, sym, headerReference, footerReference, commentRangeEnd

        if (lastStartElementWasP && ! PPR.equals(localName)) {
            bodyContentsHandler.startParagraph(currPProperties);
        }

        lastStartElementWasP = false;

        if (uri != null && uri.equals(MC_NS)) {
            if (CHOICE.equals(localName)) {
                inACChoiceDepth++;
            } else if (FALLBACK.equals(localName)) {
                inACFallbackDepth++;
            }
        }

        if (inACChoiceDepth > 0) {
            return;
        }

        if (! includeTextBox && localName.equals(TEXTBOX)) {
            inTextBox = true;
            return;
        }
        //these are sorted descending by frequency within docx files
        //in our regression corpus.
        //yes, I know, likely premature optimization...
        if (RPR.equals(localName)) {
            inRPr = true;
        } else if (R.equals(localName)) {
            inR = true;
        } else if (T.equals(localName)) {
            inT = true;
        } else if (TAB.equals(localName)) {
            runBuffer.append(TAB_CHAR);
        } else if (P.equals(localName)) {
            lastStartElementWasP = true;
        } else if (B.equals(localName)) { //TODO: add bCs
            if(inR && inRPr) {
                currRunProperties.setBold(true);
            }
        } else if (TC.equals(localName)) {
            bodyContentsHandler.startTableCell();
        } else if (P_STYLE.equals(localName)) {
            String styleId = atts.getValue(W_NS, "val");
            currPProperties.setStyleID(styleId);
        } else if (I.equals(localName)) { //TODO: add iCs
            //rprs don't have to be inR; ignore those that aren't
            if (inR && inRPr) {
                currRunProperties.setItalics(true);
            }
        } else if (STRIKE.equals(localName)) {
            if (inR && inRPr) {
                currRunProperties.setStrike(true);
            }
        } else if (U.equals(localName)) {
            if (inR && inRPr) {
                currRunProperties.setUnderline(getStringVal(atts));
            }
        } else if (TR.equals(localName)) {
            bodyContentsHandler.startTableRow();
        } else if (NUM_PR.equals(localName)) {
            inNumPr = true;
        } else if (ILVL.equals(localName)) {
            if (inNumPr) {
                currPProperties.setIlvl(getIntVal(atts));
            }
        } else if (NUM_ID.equals(localName)) {
            if (inNumPr) {
                currPProperties.setNumId(getIntVal(atts));
            }
        } else if(BR.equals(localName)) {
            runBuffer.append(NEWLINE);
        } else if (BOOKMARK_START.equals(localName)) {
            String name = atts.getValue(W_NS, "name");
            String id = atts.getValue(W_NS, "id");
            bodyContentsHandler.startBookmark(id, name);
        } else if (BOOKMARK_END.equals(localName)) {
            String id = atts.getValue(W_NS, "id");
            bodyContentsHandler.endBookmark(id);
        } else if (HYPERLINK.equals(localName)) { //docx hyperlink
            String hyperlinkId = atts.getValue(OFFICE_DOC_RELATIONSHIP_NS, "id");
            String hyperlink = null;
            if (hyperlinkId != null) {
                hyperlink = linkedRelationships.get(hyperlinkId);
                bodyContentsHandler.hyperlinkStart(hyperlink);
            } else {
                String anchor = atts.getValue(W_NS, "anchor");
                if (anchor != null) {
                    anchor = "#" + anchor;
                }
                bodyContentsHandler.hyperlinkStart(anchor);
            }
        } else if (HLINK_CLICK.equals(localName)) { //pptx hyperlink
            String hyperlinkId = atts.getValue(OFFICE_DOC_RELATIONSHIP_NS, "id");
            String hyperlink = null;
            if (hyperlinkId != null) {
                hyperlink = linkedRelationships.get(hyperlinkId);
                bodyContentsHandler.hyperlinkStart(hyperlink);
                inHlinkClick = true;
            }
        } else if(TBL.equals(localName)) {
            bodyContentsHandler.startTable();
        } else if (BLIP.equals(localName)) { //check for DRAWING_NS
            picRId = atts.getValue(OFFICE_DOC_RELATIONSHIP_NS, "embed");
        } else if ("cNvPr".equals(localName)) { //check for PIC_NS?
            picDescription = atts.getValue("", "descr");
        } else if (PIC.equals(localName)) {
            inPic = true; //check for PIC_NS?
        } //TODO: add sdt, sdtPr, sdtContent goes here statistically
        else if (FOOTNOTE_REFERENCE.equals(localName)) {
            String id = atts.getValue(W_NS, "id");
            bodyContentsHandler.footnoteReference(id);
        } else if (IMAGEDATA.equals(localName)) {
            picRId = atts.getValue(OFFICE_DOC_RELATIONSHIP_NS, "id");
            picDescription = atts.getValue(O_NS, "title");
        } else if (INS.equals(localName)) {
            startEditedSection(editType.INSERT, atts);
        } else if (DEL_TEXT.equals(localName)) {
            inDelText = true;
        } else if (DEL.equals(localName)) {
            startEditedSection(editType.DELETE, atts);
        } else if (MOVE_TO.equals(localName)) {
            startEditedSection(EditType.MOVE_TO, atts);
        } else if (MOVE_FROM.equals(localName)) {
            startEditedSection(editType.MOVE_FROM, atts);
        } else if (OLE_OBJECT.equals(localName)){ //check for O_NS?
            String type = null;
            String refId = null;
            //TODO: clean this up and ...want to get ProgID?
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
        } else if(CR.equals(localName)) {
            runBuffer.append(NEWLINE);
        } else if (ENDNOTE_REFERENCE.equals(localName)) {
            String id = atts.getValue(W_NS, "id");
            bodyContentsHandler.endnoteReference(id);
        } else if (V.equals(localName) && C_NS.equals(uri)) { // in value in a chart
            inV = true;
        } else if (RT.equals(localName)) {
            inRt = true;
        }

    }

    private void startEditedSection(EditType editType, Attributes atts) {
        String editAuthor = atts.getValue(W_NS, "author");
        String editDateString = atts.getValue(W_NS, "date");
        Date editDate = null;
        if (editDateString != null) {
            editDate = dateUtils.tryToParse(editDateString);
        }
        bodyContentsHandler.startEditedSection(editAuthor, editDate, editType);
        this.editType = editType;
    }

    private String getStringVal(Attributes atts) {
        String valString = atts.getValue(W_NS, VAL);
        if (valString != null) {
            return valString;
        }
        return "";
    }

    private int getIntVal(Attributes atts) {
        String valString = atts.getValue(W_NS, VAL);
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

        if (CHOICE.equals(localName)) {
            inACChoiceDepth--;
        } else if (FALLBACK.equals(localName)) {
            inACFallbackDepth--;
        }
        if (inACChoiceDepth > 0) {
            return;
        }

        if (! includeTextBox && localName.equals(TEXTBOX)) {
            inTextBox = false;
            return;
        }
        if (PIC.equals(localName)) { //PIC_NS
            handlePict();
            inPic = false;
            return;
        } else if (RPR.equals(localName)) {
            inRPr = false;
        } else if (R.equals(localName)) {
            handleEndOfRun();
        } else if (T.equals(localName)) {
            inT = false;
        } else if (PPR.equals(localName)) {
            if (!pStarted) {
                bodyContentsHandler.startParagraph(currPProperties);
                pStarted = true;
            }
            currPProperties.reset();
        } else if (P.equals(localName)) {
            if (runBuffer.length() > 0) {
                //<p><tab></p>...this will treat that as if it were
                //a run...TODO: should we swallow whitespace that doesn't occur in a run?
                bodyContentsHandler.run(currRunProperties, runBuffer.toString());
                runBuffer.setLength(0);
            }
            pStarted = false;
            bodyContentsHandler.endParagraph();
        } else if (TC.equals(localName)) {
            bodyContentsHandler.endTableCell();
        } else if (TR.equals(localName)) {
            bodyContentsHandler.endTableRow();
        } else if (TBL.equals(localName)) {
            bodyContentsHandler.endTable();
        } else if (FLD.equals(localName)) {
            handleEndOfRun();
        } else if (DEL_TEXT.equals(localName)) {
            inDelText = false;
        } else if (INS.equals(localName) || DEL.equals(localName) ||
                MOVE_TO.equals(localName) || MOVE_FROM.equals(localName)) {
            editType = EditType.NONE;
        } else if (HYPERLINK.equals(localName)) {
            bodyContentsHandler.hyperlinkEnd();
        } else if (PICT.equals(localName)) {
            handlePict();
        } else if (V.equals(localName) && C_NS.equals(uri)) { // in value in a chart
            inV = false;
            handleEndOfRun();
        } else if (RT.equals(localName)) {
            inRt = false;
        } else if (RUBY.equals(localName)) {
            handleEndOfRuby();
        }
    }

    private void handleEndOfRuby() {
        if (rubyBuffer.length() > 0) {
            if (concatenatePhoneticRuns) {
                bodyContentsHandler.run(currRunProperties, " (" + rubyBuffer.toString() + ")");
            }
            rubyBuffer.setLength(0);
        }
    }

    private void handleEndOfRun() {
        bodyContentsHandler.run(currRunProperties, runBuffer.toString());
        if (inHlinkClick) {
            bodyContentsHandler.hyperlinkEnd();
            inHlinkClick = false;
        }
        inR = false;
        runBuffer.setLength(0);
        currRunProperties.setBold(false);
        currRunProperties.setItalics(false);
        currRunProperties.setStrike(false);
        currRunProperties.setUnderline(UnderlinePatterns.NONE.name());
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
        } else if (! includeTextBox && inTextBox) {
            return;
        }

        if (editType.equals(EditType.MOVE_FROM) && inT) {
            if (bodyContentsHandler.getIncludeMoveFromText()) {
                appendToBuffer(ch, start, length);
            }
        } else if (inT) {
            appendToBuffer(ch, start, length);
        } else if (bodyContentsHandler.getIncludeDeletedText() && editType.equals(EditType.DELETE)) {
            appendToBuffer(ch, start, length);
        } else if (inV) {
            appendToBuffer(ch, start, length);
            appendToBuffer(TAB_CHAR, 0, 1);
        }
    }

    @Override
    public void ignorableWhitespace(char[] ch, int start, int length) throws SAXException {
        if (inACChoiceDepth > 0) {
            return;
        } else if (! includeTextBox && inTextBox) {
            return;
        }

        if (inT) {
            appendToBuffer(ch, start, length);
        } else if (bodyContentsHandler.getIncludeDeletedText() && inDelText) {
            appendToBuffer(ch, start, length);
        }
    }

    private void appendToBuffer(char[] ch, int start, int length) throws SAXException {
        if (inRt) {
            rubyBuffer.append(ch, start, length);
        } else {
            runBuffer.append(ch, start, length);
        }
    }

    public interface XWPFBodyContentsHandler {

        void run(RunProperties runProperties, String contents);

        /**
         * @param link the link; can be null
         */
        void hyperlinkStart(String link);

        void hyperlinkEnd();

        void startParagraph(ParagraphProperties paragraphProperties);

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
