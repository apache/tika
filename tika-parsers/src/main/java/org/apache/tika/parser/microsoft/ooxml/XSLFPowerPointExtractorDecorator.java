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

import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.openxml4j.opc.PackagePart;
import org.apache.poi.openxml4j.opc.PackagePartName;
import org.apache.poi.openxml4j.opc.PackageRelationship;
import org.apache.poi.openxml4j.opc.PackagingURIHelper;
import org.apache.poi.openxml4j.opc.TargetMode;
import org.apache.poi.xslf.XSLFSlideShow;
import org.apache.poi.xslf.extractor.XSLFPowerPointExtractor;
import org.apache.poi.xslf.usermodel.DrawingParagraph;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFCommonSlideData;
import org.apache.poi.xslf.usermodel.XSLFRelation;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFSlideMaster;
import org.apache.tika.exception.TikaException;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.XHTMLContentHandler;
import org.apache.xmlbeans.XmlException;
import org.openxmlformats.schemas.presentationml.x2006.main.CTComment;
import org.openxmlformats.schemas.presentationml.x2006.main.CTCommentList;
import org.openxmlformats.schemas.presentationml.x2006.main.CTNotesSlide;
import org.openxmlformats.schemas.presentationml.x2006.main.CTSlideIdListEntry;
import org.xml.sax.SAXException;

public class XSLFPowerPointExtractorDecorator extends AbstractOOXMLExtractor {

    public XSLFPowerPointExtractorDecorator(ParseContext context, XSLFPowerPointExtractor extractor) {
        super(context, extractor, null);
    }

    /**
     * @see org.apache.poi.xslf.extractor.XSLFPowerPointExtractor#getText()
     */
    @Override
    protected void buildXHTML(XHTMLContentHandler xhtml) throws SAXException,
            XmlException, IOException {
        XMLSlideShow slideShow = (XMLSlideShow) extractor.getDocument();
        XSLFSlideShow rawSlideShow = null;
        try {
           rawSlideShow = slideShow._getXSLFSlideShow(); // TODO Avoid this in future
        } catch(Exception e) {
           throw new IOException(e.getMessage()); // Shouldn't happen
        }

        XSLFSlide[] slides = slideShow.getSlides();
        for (XSLFSlide slide : slides) {
           // Find the ID, until we ditch the raw slideshow
           CTSlideIdListEntry slideId = null;
           for(CTSlideIdListEntry id : rawSlideShow.getSlideReferences().getSldIdList()) {
              if(rawSlideShow.getSlidePart(id).getPartName().equals(slide.getPackagePart().getPartName())) {
                 slideId = id;
              }
           }
           if(slideId == null) {
              // This shouldn't normally happen
              continue;
           }
           
            XSLFSlideMaster master = slide.getMasterSheet();
            CTNotesSlide notes = rawSlideShow.getNotes(slideId);
            CTCommentList comments = rawSlideShow.getSlideComments(slideId);

            // TODO In POI 3.8 beta 5, improve how we get this
            xhtml.startElement("div");
            XSLFCommonSlideData common = new XSLFCommonSlideData(slide.getXmlObject().getCSld());
            extractShapeContent(common, xhtml);

            // If there are comments, extract them
            if (comments != null) {
                for (CTComment comment : comments.getCmArray()) {
                    xhtml.element("p", comment.getText());
                }
            }
            
            // Get text from the master slide
            // TODO: re-enable this once we fix TIKA-712
            /*
            if(master != null) {
               // TODO In POI 3.8 beta 5, improve how we get this
               extractShapeContent(new XSLFCommonSlideData(master.getXmlObject().getCSld()), xhtml);
            }
            */

            if (notes != null) {
               // TODO In POI 3.8 beta 5, improve how we get this
                extractShapeContent(new XSLFCommonSlideData(notes.getCSld()), xhtml);
            }
            xhtml.endElement("div");
        }
    }

    private void extractShapeContent(XSLFCommonSlideData data, XHTMLContentHandler xhtml)
            throws SAXException {
        for (DrawingParagraph p : data.getText()) {
            xhtml.element("p", p.getText().toString());
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
