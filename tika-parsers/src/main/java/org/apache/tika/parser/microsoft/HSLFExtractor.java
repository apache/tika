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
package org.apache.tika.parser.microsoft;

import java.io.IOException;
import java.util.List;

import org.apache.poi.common.usermodel.Hyperlink;
import org.apache.poi.hslf.model.Comment;
import org.apache.poi.hslf.model.HeadersFooters;
import org.apache.poi.hslf.model.OLEShape;
import org.apache.poi.hslf.usermodel.HSLFMasterSheet;
import org.apache.poi.hslf.usermodel.HSLFNotes;
import org.apache.poi.hslf.usermodel.HSLFObjectData;
import org.apache.poi.hslf.usermodel.HSLFPictureData;
import org.apache.poi.hslf.usermodel.HSLFShape;
import org.apache.poi.hslf.usermodel.HSLFSlide;
import org.apache.poi.hslf.usermodel.HSLFSlideShow;
import org.apache.poi.hslf.usermodel.HSLFTable;
import org.apache.poi.hslf.usermodel.HSLFTableCell;
import org.apache.poi.hslf.usermodel.HSLFTextParagraph;
import org.apache.poi.hslf.usermodel.HSLFTextRun;
import org.apache.poi.hslf.usermodel.HSLFTextShape;
import org.apache.poi.poifs.filesystem.DirectoryNode;
import org.apache.poi.poifs.filesystem.NPOIFSFileSystem;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.CloseShieldInputStream;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

public class HSLFExtractor extends AbstractPOIFSExtractor {
    public HSLFExtractor(ParseContext context) {
        super(context);
    }

    protected void parse(
            NPOIFSFileSystem filesystem, XHTMLContentHandler xhtml)
            throws IOException, SAXException, TikaException {
        parse(filesystem.getRoot(), xhtml);
    }

    protected void parse(
            DirectoryNode root, XHTMLContentHandler xhtml)
            throws IOException, SAXException, TikaException {
        HSLFSlideShow ss = new HSLFSlideShow(root);
        List<HSLFSlide> _slides = ss.getSlides();

        xhtml.startElement("div", "class", "slideShow");

      /* Iterate over slides and extract text */
        for (HSLFSlide slide : _slides) {
            xhtml.startElement("div", "class", "slide");

            // Slide header, if present
            HeadersFooters hf = slide.getHeadersFooters();
            if (hf != null && hf.isHeaderVisible() && hf.getHeaderText() != null && !hf.getHeaderText().isEmpty()) {
                xhtml.startElement("div", "class", "slide-header");

                xhtml.characters(hf.getHeaderText());

                xhtml.endElement("div");
            }

            // Slide master, if present
            extractMaster(xhtml, slide.getMasterSheet());

            // Slide text
            {
                xhtml.startElement("div", "class", "slide-content");

                textRunsToText(xhtml, slide.getTextParagraphs());
                

                // Table text
                for (HSLFShape shape : slide.getShapes()) {
                    if (shape instanceof HSLFTable) {
                        extractTableText(xhtml, (HSLFTable) shape);
                    }
                }
                
                xhtml.endElement("div");
            }

            // Slide footer, if present
            if (hf != null && hf.isFooterVisible() && hf.getFooterText() != null && !hf.getFooterText().isEmpty()) {
                xhtml.startElement("div", "class", "slide-footer");

                xhtml.characters(hf.getFooterText());

                xhtml.endElement("div"); //end slide-footer
            }
            
            // Find the Notes for this slide and extract inline
            HSLFNotes notes = slide.getNotes();
            HeadersFooters nhf = ss.getNotesHeadersFooters();

            if (notes != null) {
                xhtml.startElement("div", "class", "slide-notes");

                
                
                // Notes text
                textRunsToText(xhtml, notes.getTextParagraphs());
                
                // Note headers/footers can be extracted, but content will be duplicate
                // Repeat the notes footer, if set
                /*if (nhf != null && nhf.isFooterVisible() && nhf.getFooterText() != null) {
                    xhtml.startElement("p", "class", "footer");
                    xhtml.characters(nhf.getFooterText()); //is this just a repetition of the footers?
                    xhtml.endElement("p");
                }
                
                // Repeat the Notes header, if set
                if (nhf != null && nhf.isHeaderVisible() && nhf.getHeaderText() != null) {
                    xhtml.startElement("p", "class", "header");
                    xhtml.characters(nhf.getHeaderText());
                    xhtml.endElement("p");
                }*/
       
                xhtml.endElement("div");
            }


            // Comments, if present
            StringBuilder authorStringBuilder = new StringBuilder();
            
            Comment[] comments = slide.getComments();
            if (comments.length > 0) {
            
                xhtml.startElement("div", "class", "slide-comments");
                
                for (Comment comment : comments) {
                    authorStringBuilder.setLength(0);
                    xhtml.startElement("p"); //start single slide-comment

                    if (comment.getAuthor() != null) {
                        authorStringBuilder.append(comment.getAuthor());
                    }
                    if (comment.getAuthorInitials() != null) {
                        if (authorStringBuilder.length() > 0) {
                            authorStringBuilder.append(" ");
                        }
                        authorStringBuilder.append("(").append(comment.getAuthorInitials()).append(")");
                    }
                    if (authorStringBuilder.length() > 0) {
                        if (comment.getText() != null) {
                            authorStringBuilder.append(" - ");
                        }
                        xhtml.startElement("b");
                        xhtml.characters(authorStringBuilder.toString());
                        xhtml.endElement("b");
                    }
                    if (comment.getText() != null) {
                        xhtml.characters(comment.getText());
                    }
                    xhtml.endElement("p"); //end single slide-comment
                }
                
                xhtml.endElement("div"); // end slide-comments
            }

            // Now any embedded resources
            handleSlideEmbeddedResources(slide, xhtml);

            // Slide complete
            xhtml.endElement("div"); //end slide
        }

        handleSlideEmbeddedPictures(ss, xhtml);

        // All slides done
        xhtml.endElement("div");
    }

    private void extractMaster(XHTMLContentHandler xhtml, HSLFMasterSheet master) throws SAXException {
        if (master == null) {
            return;
        }
        List<HSLFShape> shapes = master.getShapes();
        if (shapes == null || shapes.isEmpty()) {
            return;
        }

        xhtml.startElement("div", "class", "slide-master-content");
        for (HSLFShape shape : shapes) {
            if (shape != null && !HSLFMasterSheet.isPlaceholder(shape)) {
                if (shape instanceof HSLFTextShape) {
                    HSLFTextShape tsh = (HSLFTextShape) shape;
                    String text = tsh.getText();
                    if (text != null) {
                        xhtml.element("p", text);
                    }
                }
            }
        }
        xhtml.endElement("div");
    }

    private void extractTableText(XHTMLContentHandler xhtml, HSLFTable shape) throws SAXException {
        xhtml.startElement("table");
        for (int row = 0; row < shape.getNumberOfRows(); row++) {
            xhtml.startElement("tr");
            for (int col = 0; col < shape.getNumberOfColumns(); col++) {
                HSLFTableCell cell = shape.getCell(row, col);
                //insert empty string for empty cell if cell is null
                String txt = "";
                if (cell != null) {
                    txt = cell.getText();
                }
                xhtml.element("td", txt);
            }
            xhtml.endElement("tr");
        }
        xhtml.endElement("table");
    }

    private void textRunsToText(XHTMLContentHandler xhtml, List<List<HSLFTextParagraph>> paragraphsList) throws SAXException {
        if (paragraphsList == null) {
            return;
        }

        for (List<HSLFTextParagraph> run : paragraphsList) {
            // Leaving in wisdom from TIKA-712 for easy revert.
            // Avoid boiler-plate text on the master slide (0
            // = TextHeaderAtom.TITLE_TYPE, 1 = TextHeaderAtom.BODY_TYPE):
            //if (!isMaster || (run.getRunType() != 0 && run.getRunType() != 1)) {

            boolean isBullet = false;
            for (HSLFTextParagraph htp : run) {
                boolean nextBullet = htp.isBullet();
                // TODO: identify bullet/list type
                if (isBullet != nextBullet) {
                    isBullet = nextBullet;
                    if (isBullet) {
                        xhtml.startElement("ul");
                    } else {
                        xhtml.endElement("ul");
                    }
                }

                List<HSLFTextRun> textRuns = htp.getTextRuns();
                String firstLine = removePBreak(textRuns.get(0).getRawText());
                boolean showBullet = (isBullet && (textRuns.size() > 1 || !"".equals(firstLine)));
                String paraTag = showBullet ? "li" : "p";

                xhtml.startElement(paraTag);
                boolean runIsHyperLink = false;
                for (HSLFTextRun htr : textRuns) {
                    Hyperlink link = htr.getHyperlink();
                    if (link != null) {
                        String address = link.getAddress();
                        if (address != null && ! address.startsWith("_ftn")) {
                            xhtml.startElement("a", "href", link.getAddress());
                            runIsHyperLink = true;
                        }
                    }
                    String line = htr.getRawText();
                    if (line != null) {
                        boolean isfirst = true;
                        for (String fragment : line.split("\\u000b")) {
                            if (!isfirst) {
                                xhtml.startElement("br");
                                xhtml.endElement("br");
                            }
                            isfirst = false;
                            xhtml.characters(removePBreak(fragment));
                        }
                        if (line.endsWith("\u000b")) {
                            xhtml.startElement("br");
                            xhtml.endElement("br");
                        }
                    }
                    if (runIsHyperLink) {
                        xhtml.endElement("a");
                    }
                    runIsHyperLink = false;
                }
                xhtml.endElement(paraTag);
            }
            if (isBullet) {
                xhtml.endElement("ul");
            }
        }
    }

    // remove trailing paragraph break
    private static String removePBreak(String fragment) {
        // the last text run of a text paragraph contains the paragraph break (\r)
        // line breaks (\\u000b) can happen more often
        return fragment.replaceFirst("\\r$", "");
    }

    private void handleSlideEmbeddedPictures(HSLFSlideShow slideshow, XHTMLContentHandler xhtml)
            throws TikaException, SAXException, IOException {
        for (HSLFPictureData pic : slideshow.getPictureData()) {
            String mediaType;

            switch (pic.getType()) {
                case EMF:
                    mediaType = "application/x-emf";
                    break;
                case WMF:
                    mediaType = "application/x-msmetafile";
                    break;
                case DIB:
                    mediaType = "image/bmp";
                    break;
                default:
                    mediaType = pic.getContentType();
                    break;
            }

            handleEmbeddedResource(
                    TikaInputStream.get(pic.getData()), null, null,
                    mediaType, xhtml, false);
        }
    }

    private void handleSlideEmbeddedResources(HSLFSlide slide, XHTMLContentHandler xhtml)
            throws TikaException, SAXException, IOException {
        List<HSLFShape> shapes;
        try {
            shapes = slide.getShapes();
        } catch (NullPointerException e) {
            // Sometimes HSLF hits problems
            // Please open POI bugs for any you come across!
            return;
        }

        for (HSLFShape shape : shapes) {
            if (shape instanceof OLEShape) {
                OLEShape oleShape = (OLEShape) shape;
                HSLFObjectData data = null;
                try {
                    data = oleShape.getObjectData();
                } catch (NullPointerException e) {
                /* getObjectData throws NPE some times. */
                }

                if (data != null) {
                    String objID = Integer.toString(oleShape.getObjectID());

                    // Embedded Object: add a <div
                    // class="embedded" id="X"/> so consumer can see where
                    // in the main text each embedded document
                    // occurred:
                    AttributesImpl attributes = new AttributesImpl();
                    attributes.addAttribute("", "class", "class", "CDATA", "embedded");
                    attributes.addAttribute("", "id", "id", "CDATA", objID);
                    xhtml.startElement("div", attributes);
                    xhtml.endElement("div");

                    try (TikaInputStream stream = TikaInputStream.get(data.getData())) {
                        String mediaType = null;
                        if ("Excel.Chart.8".equals(oleShape.getProgID())) {
                            mediaType = "application/vnd.ms-excel";
                        } else {
                            MediaType mt = getTikaConfig().getDetector().detect(stream, new Metadata());
                            mediaType = mt.toString();
                        }
                        if (mediaType.equals("application/x-tika-msoffice-embedded; format=comp_obj")) {
                            try(NPOIFSFileSystem npoifs = new NPOIFSFileSystem(new CloseShieldInputStream(stream))) {
                                handleEmbeddedOfficeDoc(npoifs.getRoot(), objID, xhtml);
                            }
                        } else {
                            handleEmbeddedResource(
                                    stream, objID, objID,
                                    mediaType, xhtml, false);
                        }
                    }
                }
            }
        }
    }
}
