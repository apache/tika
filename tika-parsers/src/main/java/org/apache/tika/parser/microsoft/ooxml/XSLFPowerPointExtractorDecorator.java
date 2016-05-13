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

import javax.xml.namespace.QName;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.poi.common.usermodel.Hyperlink;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.openxml4j.opc.PackagePart;
import org.apache.poi.openxml4j.opc.PackagePartName;
import org.apache.poi.openxml4j.opc.PackageRelationship;
import org.apache.poi.openxml4j.opc.PackagingURIHelper;
import org.apache.poi.openxml4j.opc.TargetMode;
import org.apache.poi.sl.usermodel.Placeholder;
import org.apache.poi.xslf.extractor.XSLFPowerPointExtractor;
import org.apache.poi.xslf.usermodel.*;
import org.apache.tika.exception.TikaException;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.XHTMLContentHandler;
import org.apache.xmlbeans.XmlException;
import org.apache.xmlbeans.XmlObject;
import org.openxmlformats.schemas.presentationml.x2006.main.CTComment;
import org.openxmlformats.schemas.presentationml.x2006.main.CTCommentAuthor;
import org.openxmlformats.schemas.presentationml.x2006.main.CTPicture;
import org.openxmlformats.schemas.presentationml.x2006.main.CTSlideIdList;
import org.openxmlformats.schemas.presentationml.x2006.main.CTSlideIdListEntry;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

public class XSLFPowerPointExtractorDecorator extends AbstractOOXMLExtractor {
    public XSLFPowerPointExtractorDecorator(ParseContext context, XSLFPowerPointExtractor extractor) {
        super(context, extractor);
    }

    /**
     * @see org.apache.poi.xslf.extractor.XSLFPowerPointExtractor#getText()
     */
    protected void buildXHTML(XHTMLContentHandler xhtml) throws SAXException, IOException {
        XMLSlideShow slideShow = (XMLSlideShow) extractor.getDocument();
        XSLFCommentAuthors commentAuthors = slideShow.getCommentAuthors();

        List<XSLFSlide> slides = slideShow.getSlides();
        for (XSLFSlide slide : slides) {
            String slideDesc;
            if (slide.getPackagePart() != null && slide.getPackagePart().getPartName() != null) {
                slideDesc = getJustFileName(slide.getPackagePart().getPartName().toString());
                slideDesc += "_";
            } else {
                slideDesc = null;
            }

            // slide content
            xhtml.startElement("div", "class", "slide-content");
            extractContent(slide.getShapes(), false, xhtml, slideDesc);
            xhtml.endElement("div");

            // slide layout which is the master sheet for this slide
            xhtml.startElement("div", "class", "slide-master-content");
            XSLFSlideLayout slideLayout = slide.getMasterSheet();
            extractContent(slideLayout.getShapes(), true, xhtml, null);
            xhtml.endElement("div");

            // slide master which is the master sheet for all text layouts
            XSLFSheet slideMaster = slideLayout.getMasterSheet();
            extractContent(slideMaster.getShapes(), true, xhtml, null);

            // notes (if present)
            XSLFNotes slideNotes = slide.getNotes();
            if (slideNotes != null) {
                xhtml.startElement("div", "class", "slide-notes");

                extractContent(slideNotes.getShapes(), false, xhtml, slideDesc);

                // master sheet for this notes
                XSLFNotesMaster notesMaster = slideNotes.getMasterSheet();
                extractContent(notesMaster.getShapes(), true, xhtml, null);
                xhtml.endElement("div");
            }

            // comments (if present)
            XSLFComments comments = slide.getComments();
            if (comments != null) {
                StringBuilder authorStringBuilder = new StringBuilder();
                for (int i = 0; i < comments.getNumberOfComments(); i++) {
                    authorStringBuilder.setLength(0);
                    CTComment comment = comments.getCommentAt(i);
                    xhtml.startElement("p", "class", "slide-comment");
                    CTCommentAuthor cta = commentAuthors.getAuthorById(comment.getAuthorId());
                    if (cta != null) {
                        if (cta.getName() != null) {
                            authorStringBuilder.append(cta.getName());
                        }
                        if (cta.getInitials() != null) {
                            if (authorStringBuilder.length() > 0) {
                                authorStringBuilder.append(" ");
                            }
                            authorStringBuilder.append("("+cta.getInitials()+")");
                        }
                        if (comment.getText() != null && authorStringBuilder.length() > 0) {
                            authorStringBuilder.append(" - ");
                        }
                        if (authorStringBuilder.length() > 0) {
                            xhtml.startElement("b");
                            xhtml.characters(authorStringBuilder.toString());
                            xhtml.endElement("b");
                        }
                    }
                    xhtml.characters(comment.getText());
                    xhtml.endElement("p");
                }
            }
        }
    }

    private void extractContent(List<? extends XSLFShape> shapes, boolean skipPlaceholders, XHTMLContentHandler xhtml, String slideDesc)
            throws SAXException {
        for (XSLFShape sh : shapes) {
            if (sh instanceof XSLFTextShape) {
                XSLFTextShape txt = (XSLFTextShape) sh;
                Placeholder ph = txt.getTextType();
                if (skipPlaceholders && ph != null) {
                    continue;
                }
                boolean inHyperlink = false;
                for (XSLFTextParagraph p : txt.getTextParagraphs()) {
                    xhtml.startElement("p");

                    for (XSLFTextRun run : p.getTextRuns()) {
                        //TODO: add check for targetmode=external into POI
                        //then check to confirm that the urls are actually
                        //external and not footnote refs via the current hack
                        Hyperlink hyperlink = run.getHyperlink();

                        if (hyperlink != null && hyperlink.getAddress() != null
                                && !hyperlink.getAddress().contains("#_ftn")) {
                            xhtml.startElement("a", "href", hyperlink.getAddress());
                            inHyperlink = true;
                        }
                        xhtml.characters(run.getRawText());
                        if (inHyperlink == true) {
                            xhtml.endElement("a");
                        }
                        inHyperlink = false;
                    }
                    xhtml.endElement("p");
                }
            } else if (sh instanceof XSLFGroupShape) {
                // recurse into groups of shapes
                XSLFGroupShape group = (XSLFGroupShape) sh;
                extractContent(group.getShapes(), skipPlaceholders, xhtml, slideDesc);
            } else if (sh instanceof XSLFTable) {
                //unlike tables in Word, ppt/x can't have recursive tables...I don't think
                extractTable((XSLFTable)sh, xhtml);
            } else if (sh instanceof XSLFGraphicFrame) {
                XSLFGraphicFrame frame = (XSLFGraphicFrame) sh;
                XmlObject[] sp = frame.getXmlObject().selectPath(
                        "declare namespace p='http://schemas.openxmlformats.org/presentationml/2006/main' .//*/p:oleObj");
                if (sp != null) {
                    for (XmlObject emb : sp) {
                        XmlObject relIDAtt = emb.selectAttribute(new QName("http://schemas.openxmlformats.org/officeDocument/2006/relationships", "id"));
                        if (relIDAtt != null) {
                            String relID = relIDAtt.getDomNode().getNodeValue();
                            if (slideDesc != null) {
                                relID = slideDesc + relID;
                            }
                            AttributesImpl attributes = new AttributesImpl();
                            attributes.addAttribute("", "class", "class", "CDATA", "embedded");
                            attributes.addAttribute("", "id", "id", "CDATA", relID);
                            xhtml.startElement("div", attributes);
                            xhtml.endElement("div");
                        }
                    }
                }
            } else if (sh instanceof XSLFPictureShape) {
                if (!skipPlaceholders && (sh.getXmlObject() instanceof CTPicture)) {
                    CTPicture ctPic = ((CTPicture) sh.getXmlObject());
                    if (ctPic.getBlipFill() != null && ctPic.getBlipFill().getBlip() != null) {
                        String relID = ctPic.getBlipFill().getBlip().getEmbed();
                        if (relID != null) {
                            if (slideDesc != null) {
                                relID = slideDesc + relID;
                            }
                            AttributesImpl attributes = new AttributesImpl();
                            attributes.addAttribute("", "class", "class", "CDATA", "embedded");
                            attributes.addAttribute("", "id", "id", "CDATA", relID);
                            xhtml.startElement("div", attributes);
                            xhtml.endElement("div");
                        }
                    }
                }
            }
        }
    }

    private void extractTable(XSLFTable tbl, XHTMLContentHandler xhtml) throws SAXException {
        xhtml.startElement("table");
        for (XSLFTableRow row : tbl) {
            xhtml.startElement("tr");
            for (XSLFTableCell c : row.getCells()) {
                xhtml.startElement("td");
                //TODO: Need to wait for fix in POI to test for hyperlink first
                //shouldn't need to catch NPE...
                XSLFHyperlink hyperlink = null;
                try {
                    hyperlink = c.getHyperlink();
                } catch (NullPointerException e) {
                    //swallow
                }
                if (hyperlink != null && hyperlink.getAddress() != null) {
                    xhtml.startElement("a", "href", hyperlink.getAddress());
                }
                xhtml.characters(c.getText());
                if (hyperlink != null && hyperlink.getAddress() != null) {
                    xhtml.endElement("a");
                }
                xhtml.endElement("td");
            }
            xhtml.endElement("tr");
        }
        xhtml.endElement("table");

    }

    /**
     * In PowerPoint files, slides have things embedded in them,
     * and slide drawings which have the images
     */
    @Override
    protected List<PackagePart> getMainDocumentParts() throws TikaException {
        List<PackagePart> parts = new ArrayList<>();
        XSLFSlideShow document = null;
        try {
            document = new XSLFSlideShow(extractor.getPackage());
        } catch (Exception e) {
            throw new TikaException(e.getMessage()); // Shouldn't happen
        }

        CTSlideIdList ctSlideIdList = document.getSlideReferences();
        if (ctSlideIdList != null) {
            for (int i = 0; i < ctSlideIdList.sizeOfSldIdArray(); i++) {
                CTSlideIdListEntry ctSlide = ctSlideIdList.getSldIdArray(i);
                // Add the slide
                PackagePart slidePart;
                try {
                    slidePart = document.getSlidePart(ctSlide);
                } catch (IOException e) {
                    throw new TikaException("Broken OOXML file", e);
                } catch (XmlException xe) {
                    throw new TikaException("Broken OOXML file", xe);
                }
                parts.add(slidePart);

                // If it has drawings, return those too
                try {
                    for (PackageRelationship rel : slidePart.getRelationshipsByType(XSLFRelation.VML_DRAWING.getRelation())) {
                        if (rel.getTargetMode() == TargetMode.INTERNAL) {
                            PackagePartName relName = PackagingURIHelper.createPartName(rel.getTargetURI());
                            parts.add(rel.getPackage().getPart(relName));
                        }
                    }
                } catch (InvalidFormatException e) {
                    throw new TikaException("Broken OOXML file", e);
                }
            }
        }
        return parts;
    }
}
