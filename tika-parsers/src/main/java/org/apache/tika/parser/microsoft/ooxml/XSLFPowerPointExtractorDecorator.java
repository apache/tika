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

import org.apache.poi.openxml4j.opc.PackagePart;
import org.apache.poi.xslf.XSLFSlideShow;
import org.apache.poi.xslf.extractor.XSLFPowerPointExtractor;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.tika.sax.XHTMLContentHandler;
import org.apache.xmlbeans.XmlException;
import org.openxmlformats.schemas.drawingml.x2006.main.CTRegularTextRun;
import org.openxmlformats.schemas.drawingml.x2006.main.CTTextBody;
import org.openxmlformats.schemas.drawingml.x2006.main.CTTextParagraph;
import org.openxmlformats.schemas.presentationml.x2006.main.CTComment;
import org.openxmlformats.schemas.presentationml.x2006.main.CTCommentList;
import org.openxmlformats.schemas.presentationml.x2006.main.CTGroupShape;
import org.openxmlformats.schemas.presentationml.x2006.main.CTNotesSlide;
import org.openxmlformats.schemas.presentationml.x2006.main.CTShape;
import org.openxmlformats.schemas.presentationml.x2006.main.CTSlide;
import org.openxmlformats.schemas.presentationml.x2006.main.CTSlideIdListEntry;
import org.xml.sax.SAXException;

public class XSLFPowerPointExtractorDecorator extends AbstractOOXMLExtractor {

    public XSLFPowerPointExtractorDecorator(XSLFPowerPointExtractor extractor) {
        super(extractor, "application/vnd.openxmlformats-officedocument.presentationml.presentation");
    }

    /**
     * @see org.apache.poi.xslf.extractor.XSLFPowerPointExtractor#getText()
     */
    @Override
    protected void buildXHTML(XHTMLContentHandler xhtml) throws SAXException,
            XmlException, IOException {
        XSLFSlideShow slideShow = (XSLFSlideShow) extractor.getDocument();
        XMLSlideShow xmlSlideShow = new XMLSlideShow(slideShow);

        XSLFSlide[] slides = xmlSlideShow.getSlides();
        for (XSLFSlide slide : slides) {
            CTSlide rawSlide = slide._getCTSlide();
            CTSlideIdListEntry slideId = slide._getCTSlideId();

            CTNotesSlide notes = xmlSlideShow._getXSLFSlideShow().getNotes(
                    slideId);
            CTCommentList comments = xmlSlideShow._getXSLFSlideShow()
                    .getSlideComments(slideId);

            xhtml.startElement("div");
            extractShapeContent(rawSlide.getCSld().getSpTree(), xhtml);

            if (comments != null) {
                for (CTComment comment : comments.getCmArray()) {
                    xhtml.element("p", comment.getText());
                }
            }

            if (notes != null) {
                extractShapeContent(notes.getCSld().getSpTree(), xhtml);
            }
            xhtml.endElement("div");
        }
    }

    private void extractShapeContent(CTGroupShape gs, XHTMLContentHandler xhtml)
            throws SAXException {
        CTShape[] shapes = gs.getSpArray();
        for (CTShape shape : shapes) {
            CTTextBody textBody = shape.getTxBody();
            if (textBody != null) {
                CTTextParagraph[] paras = textBody.getPArray();
                for (CTTextParagraph textParagraph : paras) {
                    CTRegularTextRun[] textRuns = textParagraph.getRArray();
                    for (CTRegularTextRun textRun : textRuns) {
                        xhtml.element("p", textRun.getT());
                    }
                }
            }
        }
    }
    
    @Override
    protected List<PackagePart> getMainDocumentParts() {
       // TODO
       return new ArrayList<PackagePart>();
    }
}
