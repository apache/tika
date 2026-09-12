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
package org.apache.tika.parser.pdf;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDFormContentStream;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDMarkInfo;
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDMarkedContentReference;
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDStructureElement;
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDStructureNode;
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDStructureTreeRoot;
import org.apache.pdfbox.pdmodel.documentinterchange.markedcontent.PDPropertyList;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

/** Builds small tagged PDFs in memory for the marked-content tests. */
final class TaggedPdfBuilder implements AutoCloseable {

    static final float LEFT = 72f;
    static final float TOP = 700f;
    static final float LEADING = 16f;

    final PDDocument doc = new PDDocument();
    final PDStructureTreeRoot root = new PDStructureTreeRoot();
    final PDStructureElement document;
    final PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

    TaggedPdfBuilder() {
        doc.getDocumentCatalog().setStructureTreeRoot(root);
        PDMarkInfo markInfo = new PDMarkInfo();
        markInfo.setMarked(true);
        doc.getDocumentCatalog().setMarkInfo(markInfo);
        document = element("Document", root, null);
    }

    PDPage page() {
        PDPage page = new PDPage(PDRectangle.LETTER);
        doc.addPage(page);
        return page;
    }

    PDStructureElement element(String type, PDStructureNode parent, PDPage page) {
        PDStructureElement element = new PDStructureElement(type, parent);
        if (page != null) {
            element.setPage(page);
        }
        parent.appendKid(element);
        return element;
    }

    /** An element whose content is the given MCID on the page. */
    PDStructureElement leaf(String type, PDStructureNode parent, PDPage page, int mcid) {
        PDStructureElement element = element(type, parent, page);
        element.appendKid(mcid);
        return element;
    }

    /** Points an element at an MCID in another content stream (a form XObject, another page). */
    static void reference(PDStructureElement element, COSDictionary stream, PDPage page,
                          int mcid) {
        PDMarkedContentReference mcr = new PDMarkedContentReference();
        mcr.setMCID(mcid);
        if (page != null) {
            mcr.setPage(page);
        }
        if (stream != null) {
            mcr.getCOSObject().setItem(COSName.getPDFName("Stm"), stream);
        }
        element.appendKid(mcr);
    }

    static PDPropertyList mcid(int mcid) {
        COSDictionary dict = new COSDictionary();
        dict.setInt(COSName.MCID, mcid);
        return PDPropertyList.create(dict);
    }

    static PDPropertyList mcidWithActualText(int mcid, String actualText) {
        COSDictionary dict = new COSDictionary();
        dict.setInt(COSName.MCID, mcid);
        dict.setString(COSName.ACTUAL_TEXT, actualText);
        return PDPropertyList.create(dict);
    }

    /** A content stream positioned at the page's first text line, font set. */
    PDPageContentStream text(PDPage page) throws IOException {
        PDPageContentStream cs = new PDPageContentStream(doc, page);
        cs.beginText();
        cs.setFont(font, 12);
        cs.setLeading(LEADING);
        cs.newLineAtOffset(LEFT, TOP);
        return cs;
    }

    /** One tagged line: {@code /tag <</MCID n>> BDC (text) Tj EMC} then a new line. */
    static void taggedLine(PDPageContentStream cs, COSName tag, int mcid, String text)
            throws IOException {
        cs.beginMarkedContent(tag, mcid(mcid));
        cs.showText(text);
        cs.endMarkedContent();
        cs.newLine();
    }

    /** The common content-stream base class is not public, hence the copy. */
    static void taggedLine(PDFormContentStream cs, COSName tag, int mcid, String text)
            throws IOException {
        cs.beginMarkedContent(tag, mcid(mcid));
        cs.showText(text);
        cs.endMarkedContent();
        cs.newLine();
    }

    /** Ends the text object and closes the stream. */
    static void finish(PDPageContentStream cs) throws IOException {
        cs.endText();
        cs.close();
    }

    byte[] bytes() throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        doc.save(bos);
        return bos.toByteArray();
    }

    @Override
    public void close() throws IOException {
        doc.close();
    }
}
