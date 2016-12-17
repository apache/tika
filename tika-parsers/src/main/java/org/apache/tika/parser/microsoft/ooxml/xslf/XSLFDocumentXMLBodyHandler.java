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

package org.apache.tika.parser.microsoft.ooxml.xslf;


import java.util.Map;

import org.apache.tika.parser.microsoft.ooxml.AbstractDocumentXMLBodyHandler;
import org.apache.tika.parser.microsoft.ooxml.ParagraphProperties;
import org.apache.tika.parser.microsoft.ooxml.RunProperties;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

/**
 * This class is intended to handle anything that might contain IBodyElements:
 * main document, headers, footers, notes, etc.
 */

public class XSLFDocumentXMLBodyHandler extends AbstractDocumentXMLBodyHandler {


    private final XSLFBodyContentsHandler bodyContentsHandler;
    //private final RelationshipsManager relationshipsManager;


    //alternate content can be embedded in itself.
    //need to track depth.
    //if in alternate, choose fallback, maybe make this configurable?
    private int inACChoiceDepth = 0;
    private int inACFallbackDepth = 0;

    private boolean inHyperlink = false;

    private final Map<String, String> linkedRelationships;

    public XSLFDocumentXMLBodyHandler(XSLFBodyContentsHandler bodyContentsHandler,
                                      Map<String, String> linkedRelationships) {
        this.bodyContentsHandler = bodyContentsHandler;
        this.linkedRelationships = linkedRelationships;
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
            pStarted = true;
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
        //these are sorted descending by frequency
        //in our regression corpus
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
        } else if (FLD.equals(localName)) {
            inR = true;
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
        } else if ("hlinkClick".equals(localName)) {
            String hyperlinkId = atts.getValue(OFFICE_DOC_RELATIONSHIP_NS, "id");
            String hyperlink = null;
            if (hyperlinkId != null) {
                hyperlink = linkedRelationships.get(hyperlinkId);
                bodyContentsHandler.hyperlinkStart(hyperlink);
                inHyperlink = true;
            }/* else {
                String anchor = atts.getValue(W_NS, "anchor");
                if (anchor != null) {
                    anchor = "#" + anchor;
                }
                bodyContentsHandler.hyperlinkStart(anchor);
                inHyperlink = true;
            }*/
        } else if(TBL.equals(localName)) {
            bodyContentsHandler.startTable();
        } else if (BLIP.equals(localName)) { //check for DRAWING_NS
            picRId = atts.getValue(OFFICE_DOC_RELATIONSHIP_NS, "embed");
        } else if ("cNvPr".equals(localName)) { //check for PIC_NS?
            picDescription = atts.getValue("", "descr");
        } else if (PIC.equals(localName)) {
            inPic = true; //check for PIC_NS?
        } else if (IMAGEDATA.equals(localName)) {
            picRId = atts.getValue(OFFICE_DOC_RELATIONSHIP_NS, "id");
            picDescription = atts.getValue(O_NS, "title");
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
        }

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

        if (CHOICE.equals(localName)) {
            inACChoiceDepth--;
        } else if (FALLBACK.equals(localName)) {
            inACFallbackDepth--;
        }
        if (inACChoiceDepth > 0) {
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
        } else if (HYPERLINK.equals(localName)) {
            bodyContentsHandler.hyperlinkEnd();
        } else if (PICT.equals(localName)) {
            handlePict();
        }
    }

    private void handleEndOfRun() {
        bodyContentsHandler.run(currRunProperties, runBuffer.toString());
        if (inHyperlink) {
            bodyContentsHandler.hyperlinkEnd();
            inHyperlink = false;
        }
        inR = false;
        runBuffer.setLength(0);
        currRunProperties.setBold(false);
        currRunProperties.setItalics(false);
    }

    private void handlePict() {
        String picFileName = null;
        if (picRId != null) {
            picFileName = "picId";//TODO: linkedRelationships.get(picRId);
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
         if (inT) {
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
        }
    }


    public interface XSLFBodyContentsHandler {

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

        void embeddedOLERef(String refId);

        void embeddedPicRef(String picFileName, String picDescription);

    }
}
