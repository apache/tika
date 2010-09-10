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
import java.util.Iterator;
import java.util.List;

import org.apache.poi.openxml4j.opc.PackagePart;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.model.XWPFCommentsDecorator;
import org.apache.poi.xwpf.model.XWPFHeaderFooterPolicy;
import org.apache.poi.xwpf.model.XWPFHyperlinkDecorator;
import org.apache.poi.xwpf.model.XWPFParagraphDecorator;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.tika.sax.XHTMLContentHandler;
import org.apache.xmlbeans.XmlException;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTBookmark;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTP;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTRow;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTbl;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTc;
import org.xml.sax.SAXException;

public class XWPFWordExtractorDecorator extends AbstractOOXMLExtractor {

    public XWPFWordExtractorDecorator(XWPFWordExtractor extractor) {
        super(extractor, "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
    }

    /**
     * @see org.apache.poi.xwpf.extractor.XWPFWordExtractor#getText()
     */
    @Override
    protected void buildXHTML(XHTMLContentHandler xhtml)
            throws SAXException, XmlException, IOException {
        XWPFDocument document = (XWPFDocument) extractor.getDocument();
        XWPFHeaderFooterPolicy hfPolicy = document.getHeaderFooterPolicy();

        // headers
        extractHeaders(xhtml, hfPolicy);

        // first all paragraphs
        Iterator<XWPFParagraph> i = document.getParagraphsIterator();
        while (i.hasNext()) {
            XWPFParagraph paragraph = i.next();

            CTSectPr ctSectPr = null;
            if (paragraph.getCTP().getPPr() != null) {
                ctSectPr = paragraph.getCTP().getPPr().getSectPr();
            }

            XWPFHeaderFooterPolicy headerFooterPolicy = null;

            if (ctSectPr != null) {
                headerFooterPolicy =
                    new XWPFHeaderFooterPolicy(document, ctSectPr);
                extractHeaders(xhtml, headerFooterPolicy);
            }

            XWPFParagraphDecorator decorator = new XWPFCommentsDecorator(
                    new XWPFHyperlinkDecorator(paragraph, null, true));

            CTBookmark[] bookmarks = paragraph.getCTP().getBookmarkStartArray();
            for (CTBookmark bookmark : bookmarks) {
                xhtml.element("p", bookmark.getName());
            }

            xhtml.element("p", decorator.getText());

            if (ctSectPr != null) {
                extractFooters(xhtml, headerFooterPolicy);
            }
        }

        // then all document tables
        extractTableContent(document, xhtml);
        extractFooters(xhtml, hfPolicy);
    }

    private void extractFooters(
            XHTMLContentHandler xhtml, XWPFHeaderFooterPolicy hfPolicy)
            throws SAXException {
        // footers
        if (hfPolicy.getFirstPageFooter() != null) {
            xhtml.element("p", hfPolicy.getFirstPageFooter().getText());
        }
        if (hfPolicy.getEvenPageFooter() != null) {
            xhtml.element("p", hfPolicy.getEvenPageFooter().getText());
        }
        if (hfPolicy.getDefaultFooter() != null) {
            xhtml.element("p", hfPolicy.getDefaultFooter().getText());
        }
    }

    private void extractHeaders(
            XHTMLContentHandler xhtml, XWPFHeaderFooterPolicy hfPolicy)
            throws SAXException {
        if (hfPolicy.getFirstPageHeader() != null) {
            xhtml.element("p", hfPolicy.getFirstPageHeader().getText());
        }
        if (hfPolicy.getEvenPageHeader() != null) {
            xhtml.element("p", hfPolicy.getEvenPageHeader().getText());
        }
        if (hfPolicy.getDefaultHeader() != null) {
            xhtml.element("p", hfPolicy.getDefaultHeader().getText());
        }
    }

    /**
     * Low level structured parsing of document tables.
     */
    private void extractTableContent(XWPFDocument doc, XHTMLContentHandler xhtml)
            throws SAXException {
        for (CTTbl table : doc.getDocument().getBody().getTblArray()) {
            xhtml.startElement("table");
            xhtml.startElement("tbody");
            CTRow[] rows = table.getTrArray();
            for (CTRow row : rows) {
                xhtml.startElement("tr");
                CTTc[] cells = row.getTcArray();
                for (CTTc tc : cells) {
                    xhtml.startElement("td");
                    CTP[] content = tc.getPArray();
                    for (CTP ctp : content) {
                        XWPFParagraph p = new MyXWPFParagraph(ctp, doc);

                        XWPFParagraphDecorator decorator = new XWPFCommentsDecorator(
                                new XWPFHyperlinkDecorator(p, null, true));

                        xhtml.element("p", decorator.getText());
                    }

                    xhtml.endElement("td");
                }
                xhtml.endElement("tr");
            }
            xhtml.endElement("tbody");
            xhtml.endElement("table");
        }
    }

    /**
     * Word documents are simple, they only have the one
     *  main part
     */
    @Override
    protected List<PackagePart> getMainDocumentParts() {
       XWPFDocument document = (XWPFDocument) extractor.getDocument();
       
       List<PackagePart> parts = new ArrayList<PackagePart>();
       parts.add( document.getPackagePart() );
       return parts;
    }


    /**
     * Private wrapper class that makes the protected {@link XWPFParagraph}
     * constructor available.
     */
    private static class MyXWPFParagraph extends XWPFParagraph {
        private MyXWPFParagraph(CTP ctp, XWPFDocument xwpfDocument) {
            super(ctp, xwpfDocument);
        }
    }
}
