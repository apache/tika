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


import org.apache.poi.xwpf.usermodel.XWPFRelation;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.microsoft.MSOfficeParserConfig;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

/**
 * This class is intended to handle anything that might contain IBodyElements:
 * main document, headers, footers, notes, etc.
 */

class BodyContentHandler extends PartHandler {


    private enum EditType{
        NONE,
        INSERT,
        DELETE
    };

    private final static String W_NS = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";
    private final static String MC_NS = "http://schemas.openxmlformats.org/markup-compatibility/2006";
    private final static String OFFICE_DOC_RELATIONSHIP_NS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships";

    private final static char[] TAB = new char[1];

    static {
        TAB[0] = '\t';
    }

    private final String partName;
    private final RelationshipsManager relationshipsManager;
    private final XHTMLContentHandler handler;
    private final Metadata metadata;
    private final ParseContext parseContext;
    private final boolean includeDeletedContent;

    private boolean inR = false;
    private boolean inT = false;
    private boolean inRPr = false;
    private boolean inDelText = false;
    private boolean inAlternateContent = false; //in alternate content section
    private boolean inACChoice = false; //if in alternate, choice or fallback?
    private boolean inACFallback = false;
    private boolean hasWrittenAHref = false;
    private boolean hasWrittenFormatting = false;
    private String editAuthor = null;
    private String editDate = null;
    private EditType editType = EditType.NONE;
    private String hyperlink = null;

    private TmpFormatting currFormat = new TmpFormatting();

    public BodyContentHandler(String partName, RelationshipsManager relationshipsManager,
                              XHTMLContentHandler handler, Metadata metadata, ParseContext context) {
        this.partName = partName;
        this.relationshipsManager = relationshipsManager;
        this.handler = handler;
        this.metadata = metadata;
        this.parseContext = context;
        MSOfficeParserConfig config = context.get(MSOfficeParserConfig.class);
        boolean tmpIncludeDeleted = true;
        if (config != null) {
            tmpIncludeDeleted = config.getIncludeDeletedContent();
        }
        includeDeletedContent = tmpIncludeDeleted;
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
        if (uri.equals(MC_NS)) {
            if (localName.equals("AlternateContent")) {
                inAlternateContent = true;
            } else if (localName.equals("Choice")) {
                inACChoice = true;
            } else if (localName.equals("Fallback")) {
                inACFallback = true;
            }
        }
        if (inACFallback) {
            return;
        }

        if (uri.equals(W_NS)) {
            if (localName.equals("p")) {
                handler.startElement("p");
            } else if (localName.equals("r")) {
                inR = true;
            } else if (localName.equals("t")) {
                inT = true;
            } else if (localName.equals("tab")) {
                handler.characters(TAB, 0, 1);
            } else if (localName.equals("tbl")) {
                handler.startElement("table");
            } else if (localName.equals("tc")) {
                handler.startElement("td");
            } else if (localName.equals("tr")) {
                handler.startElement("tr");
            } else if (localName.equals("rPr")) {
                inRPr = true;
            } else if (inR && inRPr && localName.equals("i")) {
                //rprs don't have to be inR; ignore those that aren't
                currFormat.italics = true;
            } else if (inR && inRPr && localName.equals("b")) {
                currFormat.bold = true;
            } else if (localName.equals("delText")) {
                inDelText = true;
            } else if (localName.equals("ins")) {
                editAuthor = atts.getValue(W_NS, "author");
                editDate = atts.getValue(W_NS, "date");
                editType = EditType.INSERT;
            } else if (localName.equals("del")) {
                editAuthor = atts.getValue(W_NS, "author");
                editDate = atts.getValue(W_NS, "date");
                editType = EditType.DELETE;
            } else if (localName.equals("hyperlink")) {
                String hyperlinkId = atts.getValue(OFFICE_DOC_RELATIONSHIP_NS, "id");
                if (hyperlinkId != null) {
                    Relationship relationship = relationshipsManager.getRelationship(getName(), hyperlinkId);
                    if (relationship != null && XWPFRelation.HYPERLINK.getRelation().equals(relationship.getContentType())) {
                        hyperlink = relationship.getTarget();
                        handler.startElement("a", "href", hyperlink);
                        hasWrittenAHref = true;
                    }
                }
            }
        }
    }

    @Override
    public void endElement(String uri, String localName, String qName) throws SAXException {
        if (uri.equals(MC_NS)) {
            if (localName.equals("AlternateContent")) {
                inAlternateContent = false;
            } else if (localName.equals("Choice")) {
                inACChoice = false;
            } else if (localName.equals("Fallback")) {
                inACFallback = false;
            }
        }
        if (uri.equals(W_NS)) {
            if (inACFallback) {
                return;
            }
            if (localName.equals("p")) {
                handler.endElement("p");
            } else if (localName.equals("r")) {
                closeStyleTags();
                inR = false;
                hasWrittenFormatting = false;
            } else if (localName.equals("t")) {
                inT = false;
            } else if (localName.equals("tbl")) {
                handler.endElement("table");
            } else if (localName.equals("tc")) {
                handler.endElement("td");
            } else if (localName.equals("tr")) {
                handler.endElement("tr");
            } else if (localName.equals("rPr")) {
                inRPr = false;
            } else if (localName.equals("delText")) {
                inDelText = false;
            } else if (localName.equals("ins") || localName.equals("del")) {
                editType = EditType.NONE;
                editAuthor = null;
                editDate = null;
            } else if (localName.equals("hyperlink") && hasWrittenAHref) {
                handler.endElement("a");
                hasWrittenAHref = false;
            }
        }
    }

    @Override
    public void characters(char[] ch, int start, int length) throws SAXException {
        if (inACFallback) {
            return;
        }

        if (inR && !hasWrittenFormatting) {
            if (currFormat.bold) {
                handler.startElement("b");
            }
            if (currFormat.italics) {
                handler.startElement("i");
            }
            hasWrittenFormatting = true;
        }
        if (inT) {
            handler.characters(ch, start, length);
        } else if (includeDeletedContent && inDelText) {
            handler.characters(ch, start, length);
        }
    }

    @Override
    public void ignorableWhitespace(char[] ch, int start, int length) throws SAXException {
        if (inACFallback) {
            return;
        }

        if (inT) {
            handler.characters(ch, start, length);
        }
    }

    @Override
    public String getPartContentType() {
        return partName;
    }



    void closeStyleTags() throws SAXException {
        if (hasWrittenFormatting) {
            if (currFormat.italics) {
                handler.endElement("i");
            }
            if (currFormat.bold) {
                handler.endElement("b");
            }
        }

        currFormat.bold = false;
        currFormat.italics = false;
    }

    private class TmpFormatting {
        boolean italics = false;
        boolean bold = false;
    }
}
