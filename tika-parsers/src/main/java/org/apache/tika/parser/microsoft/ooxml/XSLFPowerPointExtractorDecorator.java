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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.xml.namespace.QName;

import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.openxml4j.opc.PackagePart;
import org.apache.poi.openxml4j.opc.PackagePartName;
import org.apache.poi.openxml4j.opc.PackageRelationship;
import org.apache.poi.openxml4j.opc.PackagingURIHelper;
import org.apache.poi.openxml4j.opc.TargetMode;
import org.apache.poi.xslf.XSLFSlideShow;
import org.apache.poi.xslf.extractor.XSLFPowerPointExtractor;
import org.apache.poi.xslf.usermodel.Placeholder;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFComments;
import org.apache.poi.xslf.usermodel.XSLFGraphicFrame;
import org.apache.poi.xslf.usermodel.XSLFGroupShape;
import org.apache.poi.xslf.usermodel.XSLFPictureShape;
import org.apache.poi.xslf.usermodel.XSLFRelation;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFSheet;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTable;
import org.apache.poi.xslf.usermodel.XSLFTableCell;
import org.apache.poi.xslf.usermodel.XSLFTableRow;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.apache.tika.exception.TikaException;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.XHTMLContentHandler;
import org.apache.xmlbeans.XmlException;
import org.apache.xmlbeans.XmlObject;
import org.openxmlformats.schemas.presentationml.x2006.main.CTComment;
import org.openxmlformats.schemas.presentationml.x2006.main.CTPicture;
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

        XSLFSlide[] slides = slideShow.getSlides();
        for (XSLFSlide slide : slides) {
            // slide
            extractContent(slide.getShapes(), false, xhtml);

            // slide layout which is the master sheet for this slide
            XSLFSheet slideLayout = slide.getMasterSheet();
            extractContent(slideLayout.getShapes(), true, xhtml);

            // slide master which is the master sheet for all text layouts
            XSLFSheet slideMaster = slideLayout.getMasterSheet();
            extractContent(slideMaster.getShapes(), true, xhtml);

            // notes (if present)
            XSLFSheet slideNotes = slide.getNotes();
            if (slideNotes != null) {
                extractContent(slideNotes.getShapes(), false, xhtml);

                // master sheet for this notes
                XSLFSheet notesMaster = slideNotes.getMasterSheet();
                extractContent(notesMaster.getShapes(), true, xhtml);
            }

            // comments (if present)
            XSLFComments comments = slide.getComments();
            if (comments != null) {
                for (CTComment comment : comments.getCTCommentsList().getCmList()) {
                    xhtml.element("p", comment.getText());
                }
            }
        }
    }

    private void extractContent(XSLFShape[] shapes, boolean skipPlaceholders, XHTMLContentHandler xhtml)
            throws SAXException {
        for (XSLFShape sh : shapes) {
            if (sh instanceof XSLFTextShape) {
                XSLFTextShape txt = (XSLFTextShape) sh;
                Placeholder ph = txt.getTextType();
                if (skipPlaceholders && ph != null) {
                    continue;
                }
                xhtml.element("p", txt.getText());
            } else if (sh instanceof XSLFGroupShape){
                // recurse into groups of shapes
                XSLFGroupShape group = (XSLFGroupShape)sh;
                extractContent(group.getShapes(), skipPlaceholders, xhtml);
            } else if (sh instanceof XSLFTable) {
                XSLFTable tbl = (XSLFTable)sh;
                for(XSLFTableRow row : tbl){
                    List<XSLFTableCell> cells = row.getCells();
                    extractContent(cells.toArray(new XSLFTableCell[cells.size()]), skipPlaceholders, xhtml);
                }
            } else if (sh instanceof XSLFGraphicFrame) {
                XSLFGraphicFrame frame = (XSLFGraphicFrame) sh;
                XmlObject[] sp = frame.getXmlObject().selectPath(
                                   "declare namespace p='http://schemas.openxmlformats.org/presentationml/2006/main' .//*/p:oleObj");
                if (sp != null) {
                    for(XmlObject emb : sp) {
                        XmlObject relIDAtt = emb.selectAttribute(new QName("http://schemas.openxmlformats.org/officeDocument/2006/relationships", "id"));
                        if (relIDAtt != null) {
                            String relID = relIDAtt.getDomNode().getNodeValue();
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
    
    /**
     * In PowerPoint files, slides have things embedded in them,
     *  and slide drawings which have the images
     */
    @Override
    protected List<PackagePart> getMainDocumentParts() throws TikaException {
       List<PackagePart> parts = new ArrayList<PackagePart>();
       XMLSlideShow slideShow = (XMLSlideShow) extractor.getDocument();
       XSLFSlideShow document = null;
       try {
          document = slideShow._getXSLFSlideShow(); // TODO Avoid this in future
       } catch(Exception e) {
          throw new TikaException(e.getMessage()); // Shouldn't happen
       }
       
       for (CTSlideIdListEntry ctSlide : document.getSlideReferences().getSldIdList()) {
          // Add the slide
          PackagePart slidePart;
          try {
             slidePart = document.getSlidePart(ctSlide);
          } catch(IOException e) {
             throw new TikaException("Broken OOXML file", e);
          } catch(XmlException xe) {
             throw new TikaException("Broken OOXML file", xe);
          }
          parts.add(slidePart);
          
          // If it has drawings, return those too
          try {
             for(PackageRelationship rel : slidePart.getRelationshipsByType(XSLFRelation.VML_DRAWING.getRelation())) {
               if(rel.getTargetMode() == TargetMode.INTERNAL) {
                   PackagePartName relName = PackagingURIHelper.createPartName(rel.getTargetURI());
                   parts.add( rel.getPackage().getPart(relName) );
                }
             }
          } catch(InvalidFormatException e) {
             throw new TikaException("Broken OOXML file", e);
          }
       }

       return parts;
    }
}
