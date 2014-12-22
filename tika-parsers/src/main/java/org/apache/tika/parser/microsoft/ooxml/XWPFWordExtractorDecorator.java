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

import org.apache.poi.openxml4j.opc.PackagePart;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.model.XWPFCommentsDecorator;
import org.apache.poi.xwpf.model.XWPFHeaderFooterPolicy;
import org.apache.poi.xwpf.usermodel.BodyType;
import org.apache.poi.xwpf.usermodel.IBody;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.ICell;
import org.apache.poi.xwpf.usermodel.IRunElement;
import org.apache.poi.xwpf.usermodel.ISDTContent;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFHeaderFooter;
import org.apache.poi.xwpf.usermodel.XWPFHyperlink;
import org.apache.poi.xwpf.usermodel.XWPFHyperlinkRun;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFPicture;
import org.apache.poi.xwpf.usermodel.XWPFPictureData;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFSDT;
import org.apache.poi.xwpf.usermodel.XWPFSDTCell;
import org.apache.poi.xwpf.usermodel.XWPFStyle;
import org.apache.poi.xwpf.usermodel.XWPFStyles;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.microsoft.WordExtractor;
import org.apache.tika.parser.microsoft.WordExtractor.TagAndStyle;
import org.apache.tika.sax.XHTMLContentHandler;
import org.apache.xmlbeans.XmlCursor;
import org.apache.xmlbeans.XmlException;
import org.apache.xmlbeans.XmlObject;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTBookmark;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTObject;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTP;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

public class XWPFWordExtractorDecorator extends AbstractOOXMLExtractor {
    private XWPFDocument document;
    private XWPFStyles styles;

    public XWPFWordExtractorDecorator(ParseContext context, XWPFWordExtractor extractor) {
        super(context, extractor);
        
        document = (XWPFDocument) extractor.getDocument();
        styles = document.getStyles();
    }

    /**
     * @see org.apache.poi.xwpf.extractor.XWPFWordExtractor#getText()
     */
    @Override
    protected void buildXHTML(XHTMLContentHandler xhtml)
            throws SAXException, XmlException, IOException {
        XWPFHeaderFooterPolicy hfPolicy = document.getHeaderFooterPolicy();

        // headers
        if (hfPolicy!=null) {
            extractHeaders(xhtml, hfPolicy);
        }

        // process text in the order that it occurs in
        extractIBodyText(document, xhtml);

        // then all document tables
        if (hfPolicy!=null) {
            extractFooters(xhtml, hfPolicy);
        }
    }

    private void extractIBodyText(IBody bodyElement, XHTMLContentHandler xhtml)
            throws SAXException, XmlException, IOException {
       for(IBodyElement element : bodyElement.getBodyElements()) {
          if(element instanceof XWPFParagraph) {
             XWPFParagraph paragraph = (XWPFParagraph)element;
             extractParagraph(paragraph, xhtml);
          }
          if(element instanceof XWPFTable) {
             XWPFTable table = (XWPFTable)element;
             extractTable(table, xhtml);
          }
          if (element instanceof XWPFSDT){
             extractSDT((XWPFSDT) element, xhtml);
          }

      }
    }
    
    private void extractSDT(XWPFSDT element, XHTMLContentHandler xhtml) throws SAXException, 
    XmlException, IOException {
       ISDTContent content = element.getContent();
       String tag = "p";
       xhtml.startElement(tag);
       xhtml.characters(content.getText());
       xhtml.endElement(tag);
    }
    
    private void extractParagraph(XWPFParagraph paragraph, XHTMLContentHandler xhtml)
            throws SAXException, XmlException, IOException {
       // If this paragraph is actually a whole new section, then
       //  it could have its own headers and footers
       // Check and handle if so
       XWPFHeaderFooterPolicy headerFooterPolicy = null;
       if (paragraph.getCTP().getPPr() != null) {
           CTSectPr ctSectPr = paragraph.getCTP().getPPr().getSectPr();
           if(ctSectPr != null) {
              headerFooterPolicy =
                  new XWPFHeaderFooterPolicy(document, ctSectPr);
              extractHeaders(xhtml, headerFooterPolicy);
           }
       }
       
       // Is this a paragraph, or a heading?
       String tag = "p";
       String styleClass = null;
       if(paragraph.getStyleID() != null) {
          XWPFStyle style = styles.getStyle(
                paragraph.getStyleID()
          );

          if (style != null && style.getName() != null) {
             TagAndStyle tas = WordExtractor.buildParagraphTagAndStyle(
                   style.getName(), paragraph.getPartType() == BodyType.TABLECELL
             );
             tag = tas.getTag();
             styleClass = tas.getStyleClass();
          }
       }
       
       if(styleClass == null) {
          xhtml.startElement(tag);
       } else {
          xhtml.startElement(tag, "class", styleClass);
       }

       // Output placeholder for any embedded docs:

       // TODO: replace w/ XPath/XQuery:
       for(XWPFRun run : paragraph.getRuns()) {
          XmlCursor c = run.getCTR().newCursor();
          c.selectPath("./*");
          while (c.toNextSelection()) {
             XmlObject o = c.getObject();
             if (o instanceof CTObject) {
                XmlCursor c2 = o.newCursor();
                c2.selectPath("./*");
                while (c2.toNextSelection()) {
                   XmlObject o2 = c2.getObject();

                   XmlObject embedAtt = o2.selectAttribute(new QName("Type"));
                   if (embedAtt != null && embedAtt.getDomNode().getNodeValue().equals("Embed")) {
                      // Type is "Embed"
                      XmlObject relIDAtt = o2.selectAttribute(new QName("http://schemas.openxmlformats.org/officeDocument/2006/relationships", "id"));
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
                c2.dispose();
             }
          }

          c.dispose();
       }
       
       // Attach bookmarks for the paragraph
       // (In future, we might put them in the right place, for now
       //  we just put them in the correct paragraph)
       for (int i = 0; i < paragraph.getCTP().sizeOfBookmarkStartArray(); i++) {
          CTBookmark bookmark = paragraph.getCTP().getBookmarkStartArray(i);
          xhtml.startElement("a", "name", bookmark.getName());
          xhtml.endElement("a");
       }
       
       TmpFormatting fmtg = new TmpFormatting(false, false);
       
       // Do the iruns
       for(IRunElement run : paragraph.getIRuns()) {
          if (run instanceof XWPFSDT){
             fmtg = closeStyleTags(xhtml, fmtg);
             processSDTRun((XWPFSDT)run, xhtml);
             //for now, we're ignoring formatting in sdt
             //if you hit an sdt reset to false
             fmtg.setBold(false);
             fmtg.setItalic(false);
          } else {
             fmtg = processRun((XWPFRun)run, paragraph, xhtml, fmtg);
          }
       }
       closeStyleTags(xhtml, fmtg);
       
       
       // Now do any comments for the paragraph
       XWPFCommentsDecorator comments = new XWPFCommentsDecorator(paragraph, null);
       String commentText = comments.getCommentText();
       if(commentText != null && commentText.length() > 0) {
          xhtml.characters(commentText);
       }

       String footnameText = paragraph.getFootnoteText();
       if(footnameText != null && footnameText.length() > 0) {
          xhtml.characters(footnameText + "\n");
       }

       // Also extract any paragraphs embedded in text boxes:
       for (XmlObject embeddedParagraph : paragraph.getCTP().selectPath("declare namespace w='http://schemas.openxmlformats.org/wordprocessingml/2006/main' declare namespace wps='http://schemas.microsoft.com/office/word/2010/wordprocessingShape' .//*/wps:txbx/w:txbxContent/w:p")) {
           extractParagraph(new XWPFParagraph(CTP.Factory.parse(embeddedParagraph.xmlText()), paragraph.getBody()), xhtml);
       }

       // Finish this paragraph
       xhtml.endElement(tag);

       if (headerFooterPolicy != null) {
           extractFooters(xhtml, headerFooterPolicy);
       }
    }

    private TmpFormatting closeStyleTags(XHTMLContentHandler xhtml,
          TmpFormatting fmtg) throws SAXException {
       // Close any still open style tags
       if (fmtg.isItalic()) {
          xhtml.endElement("i");
          fmtg.setItalic(false);
       }
       if (fmtg.isBold()) {
          xhtml.endElement("b");
          fmtg.setBold(false);
       }
       return fmtg;
    }

    private TmpFormatting processRun(XWPFRun run, XWPFParagraph paragraph, 
          XHTMLContentHandler xhtml, TmpFormatting tfmtg) 
          throws SAXException, XmlException, IOException{
       // True if we are currently in the named style tag:
       if (run.isBold() != tfmtg.isBold()) {
          if (tfmtg.isItalic()) {
             xhtml.endElement("i");
             tfmtg.setItalic(false);
          }
          if (run.isBold()) {
             xhtml.startElement("b");
          } else {
             xhtml.endElement("b");
          }
          tfmtg.setBold(run.isBold());
       }

       if (run.isItalic() != tfmtg.isItalic()) {
          if (run.isItalic()) {
             xhtml.startElement("i");
          } else {
             xhtml.endElement("i");
          }
          tfmtg.setItalic(run.isItalic());
       }

       boolean addedHREF = false;
       if(run instanceof XWPFHyperlinkRun) {
          XWPFHyperlinkRun linkRun = (XWPFHyperlinkRun)run;
          XWPFHyperlink link = linkRun.getHyperlink(document);
          if(link != null && link.getURL() != null) {
             xhtml.startElement("a", "href", link.getURL());
             addedHREF = true;
          } else if(linkRun.getAnchor() != null && linkRun.getAnchor().length() > 0) {
             xhtml.startElement("a", "href", "#" + linkRun.getAnchor());
             addedHREF = true;
          }
       }

       xhtml.characters(run.toString());

       // If we have any pictures, output them
       for(XWPFPicture picture : run.getEmbeddedPictures()) {
          if(paragraph.getDocument() != null) {
             XWPFPictureData data = picture.getPictureData();
             if(data != null) {
                AttributesImpl attr = new AttributesImpl();

                attr.addAttribute("", "src", "src", "CDATA", "embedded:" + data.getFileName());
                attr.addAttribute("", "alt", "alt", "CDATA", picture.getDescription());

                xhtml.startElement("img", attr);
                xhtml.endElement("img");
             }
          }
       }

       if (addedHREF) {
          xhtml.endElement("a");
       }

       return tfmtg;
    }

    private void processSDTRun(XWPFSDT run, XHTMLContentHandler xhtml)
          throws SAXException, XmlException, IOException{
       xhtml.characters(run.getContent().getText());
    }

    private void extractTable(XWPFTable table, XHTMLContentHandler xhtml)
            throws SAXException, XmlException, IOException {
       xhtml.startElement("table");
       xhtml.startElement("tbody");
       for(XWPFTableRow row : table.getRows()) {
          xhtml.startElement("tr");
          for(ICell cell : row.getTableICells()){
              xhtml.startElement("td");
              if (cell instanceof XWPFTableCell) {
                  extractIBodyText((XWPFTableCell)cell, xhtml);
              } else if (cell instanceof XWPFSDTCell) {
                  xhtml.characters(((XWPFSDTCell)cell).getContent().getText());
              }
              xhtml.endElement("td");
          }
          xhtml.endElement("tr");
       }
       xhtml.endElement("tbody");
       xhtml.endElement("table");
    }
    
    private void extractFooters(
            XHTMLContentHandler xhtml, XWPFHeaderFooterPolicy hfPolicy)
            throws SAXException, XmlException, IOException {
        // footers
        if (hfPolicy.getFirstPageFooter() != null) {
            extractHeaderText(xhtml, hfPolicy.getFirstPageFooter());
        }
        if (hfPolicy.getEvenPageFooter() != null) {
            extractHeaderText(xhtml, hfPolicy.getEvenPageFooter());
        }
        if (hfPolicy.getDefaultFooter() != null) {
            extractHeaderText(xhtml, hfPolicy.getDefaultFooter());
        }
    }

    private void extractHeaders(
            XHTMLContentHandler xhtml, XWPFHeaderFooterPolicy hfPolicy)
            throws SAXException, XmlException, IOException {
        if (hfPolicy == null) return;
       
        if (hfPolicy.getFirstPageHeader() != null) {
            extractHeaderText(xhtml, hfPolicy.getFirstPageHeader());
        }

        if (hfPolicy.getEvenPageHeader() != null) {
            extractHeaderText(xhtml, hfPolicy.getEvenPageHeader());
        }

        if (hfPolicy.getDefaultHeader() != null) {
            extractHeaderText(xhtml, hfPolicy.getDefaultHeader());
        }
    }

    private void extractHeaderText(XHTMLContentHandler xhtml, XWPFHeaderFooter header) throws SAXException, XmlException, IOException {

        for (IBodyElement e : header.getBodyElements()){
           if (e instanceof XWPFParagraph){
              extractParagraph((XWPFParagraph)e, xhtml);
           } else if (e instanceof XWPFTable){
              extractTable((XWPFTable)e, xhtml);
           } else if (e instanceof XWPFSDT){
              extractSDT((XWPFSDT)e, xhtml);
           }
        }
    }

    /**
     * Word documents are simple, they only have the one
     *  main part
     */
    @Override
    protected List<PackagePart> getMainDocumentParts() {
       List<PackagePart> parts = new ArrayList<PackagePart>();
       parts.add( document.getPackagePart() );
       return parts;
    }
    
    private class TmpFormatting{
       private boolean bold = false;
       private boolean italic = false;
       private TmpFormatting(boolean bold, boolean italic){
          this.bold = bold;
          this.italic = italic;
       }
       public boolean isBold() {
          return bold;
       }
       public void setBold(boolean bold) {
          this.bold = bold;
       }
       public boolean isItalic() {
          return italic;
       }
       public void setItalic(boolean italic) {
          this.italic = italic;
       }
       
    }

}
