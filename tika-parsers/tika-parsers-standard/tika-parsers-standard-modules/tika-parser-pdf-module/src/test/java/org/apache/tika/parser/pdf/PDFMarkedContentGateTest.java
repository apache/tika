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

import static org.apache.tika.parser.pdf.TaggedPdfBuilder.finish;
import static org.apache.tika.parser.pdf.TaggedPdfBuilder.mcid;
import static org.apache.tika.parser.pdf.TaggedPdfBuilder.mcidWithActualText;
import static org.apache.tika.parser.pdf.TaggedPdfBuilder.reference;
import static org.apache.tika.parser.pdf.TaggedPdfBuilder.taggedLine;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Set;

import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDFormContentStream;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDObjectReference;
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDStructureElement;
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDStructureNode;
import org.apache.pdfbox.pdmodel.documentinterchange.taggedpdf.PDListAttributeObject;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionURI;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Node;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import org.apache.tika.TikaTest;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.PDF;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.enricher.TextRecognizer;
import org.apache.tika.sax.ToXMLContentHandler;
import org.apache.tika.sax.XHTMLContentHandler;
import org.apache.tika.utils.XMLReaderUtils;

/** The tagged writer on PDFs built in memory: routing, the gate, fallback, structure. */
public class PDFMarkedContentGateTest extends TikaTest {

    private static final String PAGE_DIV = "<div class=\"page\">";

    private static final class Result {
        final String xml;
        final Metadata metadata;

        Result(String xml, Metadata metadata) {
            this.xml = xml;
            this.metadata = metadata;
        }

        String page(int oneBased) {
            String[] pages = xml.split(java.util.regex.Pattern.quote(PAGE_DIV));
            return pages[oneBased];
        }

        String body() {
            return xml.substring(xml.indexOf("<body>"));
        }
    }

    @Test
    public void testWordsPositionedByOffsetsAreSpaced() throws Exception {
        try (TaggedPdfBuilder b = new TaggedPdfBuilder()) {
            PDPage page = b.page();
            b.leaf("P", b.document, page, 0);
            PDPageContentStream cs = b.text(page);
            cs.beginMarkedContent(COSName.P, mcid(0));
            cs.showTextWithPositioning(new Object[]{"Hello", -1000f, "World"});
            cs.endMarkedContent();
            finish(cs);

            Result tags = parse(b.bytes(), config(MarkedContentConfig.Strategy.TAGS));
            assertContains("<p>Hello World", tags.xml);
            assertEquals(1, tags.metadata.getInt(PDF.MARKED_CONTENT_PAGES_TAGGED));
            assertEquals(0, tags.metadata.getInt(PDF.MARKED_CONTENT_PAGES_FALLBACK));
            assertWellFormed(tags.xml);
        }
    }

    @Test
    public void testNestedSpanKeepsItsPlace() throws Exception {
        try (TaggedPdfBuilder b = new TaggedPdfBuilder()) {
            PDPage page = b.page();
            PDStructureElement p = b.leaf("P", b.document, page, 0);
            b.leaf("Span", p, page, 1);
            PDPageContentStream cs = b.text(page);
            cs.beginMarkedContent(COSName.P, mcid(0));
            cs.showText("Alpha ");
            cs.beginMarkedContent(COSName.getPDFName("Span"), mcid(1));
            cs.showText("beta");
            cs.endMarkedContent();
            cs.showText(" gamma");
            cs.endMarkedContent();
            finish(cs);

            Result tags = parse(b.bytes(), config(MarkedContentConfig.Strategy.TAGS));
            assertContains("<p>Alpha beta gamma", tags.xml);
        }
    }

    @Test
    public void testParagraphAcrossPagesIsOnePerPage() throws Exception {
        try (TaggedPdfBuilder b = new TaggedPdfBuilder()) {
            PDPage one = b.page();
            PDPage two = b.page();
            PDStructureElement p = b.element("P", b.document, null);
            reference(p, null, one, 0);
            reference(p, null, two, 0);
            PDPageContentStream cs = b.text(one);
            taggedLine(cs, COSName.P, 0, "Page one text");
            finish(cs);
            cs = b.text(two);
            taggedLine(cs, COSName.P, 0, "Page two text");
            finish(cs);

            Result tags = parse(b.bytes(), config(MarkedContentConfig.Strategy.TAGS));
            assertContainsCount(PAGE_DIV, tags.xml, 2);
            assertContains("<p>Page one text", tags.page(1));
            assertFalse(tags.page(1).contains("Page two"));
            assertContains("<p>Page two text", tags.page(2));
            assertEquals(2, tags.metadata.getInt(PDF.MARKED_CONTENT_PAGES_TAGGED));
            assertWellFormed(tags.xml);
        }
    }

    @Test
    public void testUntaggedPageFallsBackToStripperOutput() throws Exception {
        try (TaggedPdfBuilder b = new TaggedPdfBuilder()) {
            PDPage one = b.page();
            PDPage two = b.page();
            b.leaf("P", b.document, one, 0);
            PDPageContentStream cs = b.text(one);
            taggedLine(cs, COSName.P, 0, "Tagged page");
            finish(cs);
            cs = b.text(two);
            cs.showText("Plain page, first line");
            cs.newLine();
            cs.showText("second line");
            finish(cs);
            byte[] pdf = b.bytes();

            Result none = parse(pdf, config(MarkedContentConfig.Strategy.NONE));
            Result auto = parse(pdf, config(MarkedContentConfig.Strategy.AUTO));
            Result tags = parse(pdf, config(MarkedContentConfig.Strategy.TAGS));
            assertEquals(none.page(2), auto.page(2));
            assertEquals(none.page(2), tags.page(2));
            assertContains("<p>Tagged page", auto.page(1));
            assertEquals(1, auto.metadata.getInt(PDF.MARKED_CONTENT_PAGES_TAGGED));
            assertEquals(1, auto.metadata.getInt(PDF.MARKED_CONTENT_PAGES_FALLBACK));
            assertEquals("2:no-tagged-text",
                    String.join("|", auto.metadata.getValues(PDF.MARKED_CONTENT_REJECTIONS)));
            assertEquals("2:no-tagged-text",
                    String.join("|", tags.metadata.getValues(PDF.MARKED_CONTENT_REJECTIONS)));
        }
    }

    @Test
    public void testFormXObjectWithStructParentsHasItsOwnMcids() throws Exception {
        try (TaggedPdfBuilder b = new TaggedPdfBuilder()) {
            PDPage page = b.page();
            PDFormXObject form = new PDFormXObject(b.doc);
            form.setBBox(new PDRectangle(0, 0, 612, 792));
            form.setResources(new PDResources());
            form.setStructParents(0);
            try (PDFormContentStream fcs = new PDFormContentStream(form)) {
                fcs.beginText();
                fcs.setFont(b.font, 12);
                fcs.newLineAtOffset(72, 600);
                taggedLine(fcs, COSName.P, 0, "Inside the form");
                fcs.endText();
            }
            // the page uses MCID 0 too: the form's MCID 0 must not be confused with it
            b.leaf("P", b.document, page, 0);
            PDStructureElement formP = b.element("P", b.document, null);
            reference(formP, form.getCOSObject(), null, 0);
            PDPageContentStream cs = b.text(page);
            taggedLine(cs, COSName.P, 0, "On the page");
            cs.endText();
            cs.drawForm(form);
            cs.close();

            Result tags = parse(b.bytes(), config(MarkedContentConfig.Strategy.TAGS));
            assertContains("<p>On the page", tags.xml);
            assertContains("<p>Inside the form", tags.xml);
            assertFalse(tags.xml.contains("class=\"untagged\""));
        }
    }

    @Test
    public void testFormXObjectWithoutStructParentsInheritsThePage() throws Exception {
        try (TaggedPdfBuilder b = new TaggedPdfBuilder()) {
            PDPage page = b.page();
            PDFormXObject form = new PDFormXObject(b.doc);
            form.setBBox(new PDRectangle(0, 0, 612, 792));
            form.setResources(new PDResources());
            try (PDFormContentStream fcs = new PDFormContentStream(form)) {
                fcs.beginText();
                fcs.setFont(b.font, 12);
                fcs.newLineAtOffset(72, 600);
                taggedLine(fcs, COSName.P, 7, "Inside the form");
                fcs.endText();
            }
            b.leaf("P", b.document, page, 7);
            PDPageContentStream cs = new PDPageContentStream(b.doc, page);
            cs.drawForm(form);
            cs.close();

            Result tags = parse(b.bytes(), config(MarkedContentConfig.Strategy.TAGS));
            assertContains("<p>Inside the form", tags.xml);
            assertFalse(tags.xml.contains("class=\"untagged\""));
        }
    }

    @Test
    public void testActualTextIsWrittenOnce() throws Exception {
        try (TaggedPdfBuilder b = new TaggedPdfBuilder()) {
            PDPage page = b.page();
            PDStructureElement p = b.leaf("P", b.document, page, 0);
            b.leaf("Span", p, page, 1);
            PDPageContentStream cs = b.text(page);
            cs.beginMarkedContent(COSName.P, mcid(0));
            cs.showText("See ");
            cs.beginMarkedContent(COSName.getPDFName("Span"), mcidWithActualText(1, "Actual"));
            cs.showText("Gly");
            cs.endMarkedContent();
            cs.endMarkedContent();
            finish(cs);

            Result tags = parse(b.bytes(), config(MarkedContentConfig.Strategy.TAGS));
            assertContains("<p>See Actual", tags.xml);
            assertFalse(tags.body().contains("Gly"));
            assertContainsCount("Actual", tags.body(), 1);
        }
    }

    @Test
    public void testWordDrawnAcrossCellsIsSplit() throws Exception {
        try (TaggedPdfBuilder b = new TaggedPdfBuilder()) {
            PDPage page = b.page();
            PDStructureElement table = b.element("Table", b.document, page);
            PDStructureElement tr = b.element("TR", table, page);
            b.leaf("P", b.element("TD", tr, page), page, 0);
            b.leaf("P", b.element("TD", tr, page), page, 1);
            PDPageContentStream cs = b.text(page);
            // cells separated only by a space glyph: one word to the stripper
            cs.beginMarkedContent(COSName.P, mcid(0));
            cs.showText("NHG ");
            cs.endMarkedContent();
            cs.beginMarkedContent(COSName.P, mcid(1));
            cs.showText("STRING");
            cs.endMarkedContent();
            finish(cs);

            Result tags = parse(b.bytes(), config(MarkedContentConfig.Strategy.TAGS));
            assertContains("<table><tr>\t<td><p>NHG</p>", tags.xml);
            assertContains("\t<td><p>STRING", tags.xml);
            assertWellFormed(tags.xml);
        }
    }

    @Test
    public void testArtifactsFollowTheTaggedContent() throws Exception {
        try (TaggedPdfBuilder b = new TaggedPdfBuilder()) {
            PDPage page = b.page();
            b.leaf("P", b.document, page, 0);
            PDPageContentStream cs = b.text(page);
            cs.beginMarkedContent(COSName.ARTIFACT);
            cs.showText("Running header");
            cs.endMarkedContent();
            cs.newLine();
            taggedLine(cs, COSName.P, 0, "Body text");
            finish(cs);

            Result tags = parse(b.bytes(), config(MarkedContentConfig.Strategy.TAGS));
            assertContains("<div class=\"artifact\"><p>Running header", tags.xml);
            assertTrue(tags.xml.indexOf("Body text") < tags.xml.indexOf("Running header"));
            assertWellFormed(tags.xml);
        }
    }

    @Test
    public void testCoverageGate() throws Exception {
        try (TaggedPdfBuilder b = new TaggedPdfBuilder()) {
            PDPage page = b.page();
            b.leaf("P", b.document, page, 0);
            PDPageContentStream cs = b.text(page);
            taggedLine(cs, COSName.P, 0, "Tag");
            cs.showText("This untagged text is much longer than the tagged word");
            finish(cs);
            byte[] pdf = b.bytes();

            Result auto = parse(pdf, config(MarkedContentConfig.Strategy.AUTO));
            assertEquals(1, auto.metadata.getInt(PDF.MARKED_CONTENT_PAGES_FALLBACK));
            assertTrue(auto.metadata.get(PDF.MARKED_CONTENT_REJECTIONS).startsWith("1:coverage="),
                    auto.metadata.get(PDF.MARKED_CONTENT_REJECTIONS));
            assertFalse(auto.xml.contains("class=\"untagged\""));

            Result tags = parse(pdf, config(MarkedContentConfig.Strategy.TAGS));
            assertEquals(1, tags.metadata.getInt(PDF.MARKED_CONTENT_PAGES_TAGGED));
            assertContains("<p>Tag", tags.xml);
            assertContains("<div class=\"untagged\"><p>This untagged text", tags.xml);

            PDFParserConfig lenient = config(MarkedContentConfig.Strategy.AUTO);
            lenient.getMarkedContent().setMinCoverage(0.01f);
            assertEquals(1, parse(pdf, lenient).metadata.getInt(PDF.MARKED_CONTENT_PAGES_TAGGED));
        }
    }

    /** A producer that marks the body /Artifact and tags fragments has not described the page. */
    @Test
    public void testArtifactHeavyPageFallsBack() throws Exception {
        try (TaggedPdfBuilder b = new TaggedPdfBuilder()) {
            PDPage page = b.page();
            b.leaf("P", b.document, page, 0);
            PDPageContentStream cs = b.text(page);
            cs.beginMarkedContent(COSName.ARTIFACT);
            cs.showText("Check sensor connection and verify the sensor data sheet and co");
            cs.endMarkedContent();
            cs.newLine();
            taggedLine(cs, COSName.P, 0, "mpatibility");
            finish(cs);
            byte[] pdf = b.bytes();

            Result auto = parse(pdf, config(MarkedContentConfig.Strategy.AUTO));
            assertTrue(auto.metadata.get(PDF.MARKED_CONTENT_REJECTIONS).startsWith("1:coverage="),
                    auto.metadata.get(PDF.MARKED_CONTENT_REJECTIONS));
            assertContains("sheet and co\nmpatibility", auto.xml);
            Result tags = parse(pdf, config(MarkedContentConfig.Strategy.TAGS));
            assertContains("<p>mpatibility", tags.xml);
            assertContains("<div class=\"artifact\"><p>Check sensor", tags.xml);
        }
    }

    /** A space drawn under its own MCID between two untagged words still separates them. */
    @Test
    public void testUntaggedWordsAroundATaggedSpaceStayApart() throws Exception {
        try (TaggedPdfBuilder b = new TaggedPdfBuilder()) {
            PDPage page = b.page();
            b.leaf("P", b.document, page, 0);
            b.leaf("P", b.document, page, 1);
            PDPageContentStream cs = b.text(page);
            taggedLine(cs, COSName.P, 0, "Tagged line to keep the page tagged");
            cs.showText("foo");
            cs.beginMarkedContent(COSName.P, mcid(1));
            cs.showText(" ");
            cs.endMarkedContent();
            cs.showText("bar");
            finish(cs);

            Result tags = parse(b.bytes(), config(MarkedContentConfig.Strategy.TAGS));
            assertContains("<div class=\"untagged\"><p>foo bar", tags.xml);
            assertFalse(tags.xml.contains("foobar"));
        }
    }

    /** A tree 300 levels deep must not trip the XML nesting guard: containers collapse. */
    @Test
    public void testDeepTreeStaysUnderTheNestingBudget() throws Exception {
        try (TaggedPdfBuilder b = new TaggedPdfBuilder()) {
            PDPage page = b.page();
            PDStructureElement parent = b.document;
            for (int i = 0; i < 300; i++) {
                parent = b.element(i % 2 == 0 ? "Sect" : "Div", parent, page);
            }
            PDStructureElement list = b.element("L", parent, page);
            b.leaf("P", b.element("LBody", b.element("LI", list, page), page), page, 0);
            PDPageContentStream cs = b.text(page);
            taggedLine(cs, COSName.P, 0, "Deep down");
            finish(cs);

            Result tags = parse(b.bytes(), config(MarkedContentConfig.Strategy.TAGS));
            assertContains("<ul>\t<li><p>Deep down", tags.xml);
            assertEquals(1, tags.metadata.getInt(PDF.MARKED_CONTENT_PAGES_TAGGED));
            assertNull(tags.metadata.get(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING));
            int max = depth(XMLReaderUtils.buildDOM(new ByteArrayInputStream(
                    tags.xml.getBytes(StandardCharsets.UTF_8))).getDocumentElement());
            assertTrue(max <= PDFMarkedContent2XHTML.MAX_OPEN_ELEMENTS + 4, "nesting " + max);
        }
    }

    private static int depth(Node node) {
        int max = 0;
        for (Node child = node.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                max = Math.max(max, depth(child));
            }
        }
        return max + 1;
    }

    /** Element mappings ask their ancestors; a long chain of custom types must not recurse. */
    @Test
    public void testDeepCustomTypeChainDoesNotOverflow() throws Exception {
        try (TaggedPdfBuilder b = new TaggedPdfBuilder()) {
            PDPage page = b.page();
            PDStructureElement parent = b.document;
            for (int i = 0; i < 3000; i++) {
                parent = b.element("Widget" + (i % 7), parent, page);
            }
            b.leaf("P", parent, page, 0);
            PDPageContentStream cs = b.text(page);
            taggedLine(cs, COSName.P, 0, "At the bottom");
            finish(cs);

            Result tags = parse(b.bytes(), config(MarkedContentConfig.Strategy.TAGS));
            assertContains("<p>At the bottom", tags.xml);
            assertEquals(1, tags.metadata.getInt(PDF.MARKED_CONTENT_PAGES_TAGGED));
            assertNull(tags.metadata.get(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING));
        }
    }

    /**
     * A tree written as directly nested dictionaries, deeper than PDFBox's parser will
     * recurse (500 levels): PDFBox drops the object and every strategy writes the page as the
     * stripper does. The same file with the nesting under a kid, so the root survives, is
     * walked past the unreadable kid.
     */
    @Test
    public void testDirectlyNestedTreePastPdfboxRecursionCapFallsBack() throws Exception {
        for (boolean underKid : new boolean[] {false, true}) {
            try (TaggedPdfBuilder b = new TaggedPdfBuilder()) {
                PDPage page = b.page();
                PDStructureElement holder = underKid ? b.element("Div", b.document, page)
                        : b.document;
                holder.getCOSObject().setString(COSName.K, "SPLICE-HERE");
                PDPageContentStream cs = b.text(page);
                taggedLine(cs, COSName.P, 0, "Text on the page");
                finish(cs);
                StringBuilder chain = new StringBuilder();
                for (int i = 0; i < 3000; i++) {
                    chain.append("<< /Type /StructElem /S /Div /K ");
                }
                chain.append("<< /Type /StructElem /S /P /K 0 >>");
                for (int i = 0; i < 3000; i++) {
                    chain.append(" >>");
                }
                byte[] pdf = b.bytesSplicing(chain.toString());

                for (MarkedContentConfig.Strategy strategy : MarkedContentConfig.Strategy.values()) {
                    Result r = parse(pdf, config(strategy));
                    assertContains("Text on the page", r.xml);
                    assertNull(r.metadata.get(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING));
                    // the root's own kid unreadable: no tree at all; a kid below it: an
                    // empty tree, and the page falls back
                    Integer tagged = r.metadata.getInt(PDF.MARKED_CONTENT_PAGES_TAGGED);
                    assertTrue(tagged == null || tagged == 0, String.valueOf(tagged));
                }
            }
        }
    }

    /** A tree of bare spans gives the page no paragraphs; AUTO keeps the stripper's. */
    @Test
    public void testPageWithoutBlockElementsFallsBack() throws Exception {
        try (TaggedPdfBuilder b = new TaggedPdfBuilder()) {
            PDPage page = b.page();
            b.leaf("Span", b.document, page, 0);
            b.leaf("Span", b.document, page, 1);
            PDPageContentStream cs = b.text(page);
            taggedLine(cs, COSName.getPDFName("Span"), 0, "First line of text");
            taggedLine(cs, COSName.getPDFName("Span"), 1, "Second line of text");
            finish(cs);
            byte[] pdf = b.bytes();

            Result auto = parse(pdf, config(MarkedContentConfig.Strategy.AUTO));
            assertEquals("1:no-block-structure", auto.metadata.get(PDF.MARKED_CONTENT_REJECTIONS));
            assertContains("<p>First line of text", auto.xml);
            Result tags = parse(pdf, config(MarkedContentConfig.Strategy.TAGS));
            assertEquals(1, tags.metadata.getInt(PDF.MARKED_CONTENT_PAGES_TAGGED));
        }
        // a tree of bare containers is no better than one of bare spans
        try (TaggedPdfBuilder b = new TaggedPdfBuilder()) {
            PDPage page = b.page();
            PDStructureElement div = b.element("Div", b.document, page);
            b.leaf("NonStruct", div, page, 0);
            b.leaf("NonStruct", div, page, 1);
            PDPageContentStream cs = b.text(page);
            taggedLine(cs, COSName.getPDFName("NonStruct"), 0, "First line of text");
            taggedLine(cs, COSName.getPDFName("NonStruct"), 1, "Second line of text");
            finish(cs);
            byte[] pdf = b.bytes();

            Result auto = parse(pdf, config(MarkedContentConfig.Strategy.AUTO));
            assertEquals("1:no-block-structure", auto.metadata.get(PDF.MARKED_CONTENT_REJECTIONS));
            Result tags = parse(pdf, config(MarkedContentConfig.Strategy.TAGS));
            assertContains("<div class=\"div\"><p>First line of text\nSecond line of text</p>",
                    tags.xml);
        }
    }

    /**
     * Text the tree leaves straight in a container gets the stripper's paragraphs, as the flat
     * page would; text inside a real block does not.
     */
    @Test
    public void testLooseTextTakesTheStrippersParagraphs() throws Exception {
        try (TaggedPdfBuilder b = new TaggedPdfBuilder()) {
            PDPage page = b.page();
            PDStructureElement div = b.element("Div", b.document, page);
            b.leaf("H1", div, page, 0);
            b.leaf("NonStruct", div, page, 1);
            b.leaf("NonStruct", div, page, 2);
            b.leaf("NonStruct", div, page, 3);
            PDStructureElement p = b.element("P", div, page);
            b.leaf("Span", p, page, 4);
            b.leaf("Span", p, page, 5);
            PDPageContentStream cs = b.text(page);
            taggedLine(cs, COSName.getPDFName("H1"), 0, "Heading");
            taggedLine(cs, COSName.getPDFName("NonStruct"), 1, "First line");
            taggedLine(cs, COSName.getPDFName("NonStruct"), 2, "Second line");
            // a gap the stripper reads as a paragraph break
            cs.newLine();
            cs.newLine();
            taggedLine(cs, COSName.getPDFName("NonStruct"), 3, "Third line");
            taggedLine(cs, COSName.getPDFName("Span"), 4, "Inside a paragraph");
            cs.newLine();
            cs.newLine();
            taggedLine(cs, COSName.getPDFName("Span"), 5, "still one paragraph");
            finish(cs);

            Result auto = parse(b.bytes(), config(MarkedContentConfig.Strategy.AUTO));
            assertWellFormed(auto.xml);
            assertEquals(1, auto.metadata.getInt(PDF.MARKED_CONTENT_PAGES_TAGGED));
            assertContains("<h1>Heading</h1>\n<p>First line\nSecond line</p>\n<p>Third line</p>",
                    auto.xml);
            assertContains("<p>Inside a paragraph\nstill one paragraph</p>", auto.xml);
        }
    }

    /** Text straight in a table, a row or a list sits in the child that holds text there. */
    @Test
    public void testTextStraightInATableOrListGetsItsChild() throws Exception {
        try (TaggedPdfBuilder b = new TaggedPdfBuilder()) {
            PDPage page = b.page();
            PDStructureElement table = b.element("Table", b.document, page);
            b.leaf("NonStruct", table, page, 0);
            PDStructureElement tr = b.element("TR", table, page);
            b.leaf("TD", tr, page, 1);
            b.leaf("NonStruct", tr, page, 2);
            PDStructureElement list = b.element("L", b.document, page);
            b.leaf("NonStruct", list, page, 3);
            PDStructureElement li = b.element("LI", list, page);
            b.leaf("LBody", li, page, 4);
            PDStructureElement nested = b.element("L", list, page);
            PDStructureElement nestedItem = b.element("LI", nested, page);
            b.leaf("LBody", nestedItem, page, 5);
            PDPageContentStream cs = b.text(page);
            taggedLine(cs, COSName.getPDFName("NonStruct"), 0, "Table title");
            taggedLine(cs, COSName.getPDFName("TD"), 1, "Cell");
            taggedLine(cs, COSName.getPDFName("NonStruct"), 2, "Stray");
            taggedLine(cs, COSName.getPDFName("NonStruct"), 3, "Loose entry");
            taggedLine(cs, COSName.getPDFName("LBody"), 4, "Item");
            taggedLine(cs, COSName.getPDFName("LBody"), 5, "Nested item");
            finish(cs);

            Result tags = parse(b.bytes(), config(MarkedContentConfig.Strategy.TAGS));
            assertWellFormed(tags.xml);
            assertContains("<table><caption>Table title</caption>\n<tr>\t<td>Cell</td>\t<td>Stray</td>",
                    tags.xml);
            assertContains("<ul>\t<li>Loose entry</li>\n\t<li>Item</li>\n\t<li><ul>\t<li>Nested item",
                    tags.xml);
        }
    }

    /**
     * A block inside a paragraph or heading closes it, as an HTML parser would; a paragraph
     * inside a link or span is inline there, its edges still separating words.
     */
    @Test
    public void testBlockInsideAParagraphClosesIt() throws Exception {
        try (TaggedPdfBuilder b = new TaggedPdfBuilder()) {
            PDPage page = b.page();
            PDStructureElement p = b.element("P", b.document, page);
            b.leaf("Span", p, page, 0);
            PDStructureElement inner = b.element("P", p, page);
            b.leaf("Span", inner, page, 1);
            PDStructureElement figure = b.element("Figure", p, page);
            figure.setAlternateDescription("A chart");
            b.leaf("Span", figure, page, 2);
            PDStructureElement table = b.element("Table", p, page);
            PDStructureElement tr = b.element("TR", table, page);
            PDStructureElement td = b.element("TD", tr, page);
            b.leaf("P", td, page, 3);
            PDStructureElement h = b.element("H1", b.document, page);
            b.leaf("Span", h, page, 4);
            PDStructureElement inHeading = b.element("P", h, page);
            b.leaf("Span", inHeading, page, 5);
            PDPageContentStream cs = b.text(page);
            taggedLine(cs, COSName.getPDFName("Span"), 0, "Outer");
            taggedLine(cs, COSName.getPDFName("Span"), 1, "inner");
            taggedLine(cs, COSName.getPDFName("Span"), 2, "caption");
            taggedLine(cs, COSName.getPDFName("P"), 3, "Cell");
            taggedLine(cs, COSName.getPDFName("Span"), 4, "Heading");
            taggedLine(cs, COSName.getPDFName("Span"), 5, "Not the heading");
            finish(cs);

            Result tags = parse(b.bytes(), config(MarkedContentConfig.Strategy.TAGS));
            assertWellFormed(tags.xml);
            assertContains("<p>Outer</p>\n<p>inner</p>\n<div alt=\"A chart\" class=\"figure\">"
                    + "A chart\n<p>caption</p>\n</div>\n<table><tr>\t<td><p>Cell</p>", tags.xml);
            assertContains("<h1>Heading</h1>\n<p>Not the heading</p>", tags.xml);
        }
        // a paragraph's own text after a block nested in it gets a paragraph of its own
        try (TaggedPdfBuilder b = new TaggedPdfBuilder()) {
            PDPage page = b.page();
            PDStructureElement p = b.element("P", b.document, page);
            PDStructureElement inner = b.element("P", p, page);
            b.leaf("Span", inner, page, 0);
            b.leaf("Span", p, page, 1);
            PDPageContentStream cs = b.text(page);
            taggedLine(cs, COSName.getPDFName("Span"), 0, "Nested first");
            taggedLine(cs, COSName.getPDFName("Span"), 1, "then the outer text");
            finish(cs);

            Result tags = parse(b.bytes(), config(MarkedContentConfig.Strategy.TAGS));
            assertWellFormed(tags.xml);
            assertContains("<p>Nested first</p>\n<p>then the outer text</p>", tags.xml);
        }
        // a chain of paragraphs nested in each other is a sequence of paragraphs, however deep
        try (TaggedPdfBuilder b = new TaggedPdfBuilder()) {
            PDPage page = b.page();
            PDStructureNode parent = b.document;
            PDPageContentStream cs = b.text(page);
            for (int i = 0; i < 80; i++) {
                PDStructureElement p = b.element("P", parent, page);
                b.leaf("Span", p, page, i);
                taggedLine(cs, COSName.getPDFName("Span"), i, "Paragraph " + i);
                parent = p;
            }
            finish(cs);

            Result tags = parse(b.bytes(), config(MarkedContentConfig.Strategy.TAGS));
            assertWellFormed(tags.xml);
            assertContains("<p>Paragraph 0</p>\n<p>Paragraph 1</p>", tags.xml);
            assertContains("<p>Paragraph 78</p>\n<p>Paragraph 79</p>", tags.xml);
            assertNotContained("<span", tags.xml);
            assertNotContained("<p />", tags.xml);
        }
        // inside a link the paragraph is inline, and its edge separates words even when no
        // separator was drawn
        try (TaggedPdfBuilder b = new TaggedPdfBuilder()) {
            PDPage page = b.page();
            PDStructureElement p = b.element("P", b.document, page);
            b.leaf("Span", p, page, 0);
            PDStructureElement span = b.element("Span", p, page);
            span.setLanguage("de-DE");
            PDStructureElement inner = b.element("P", span, page);
            b.leaf("Span", inner, page, 1);
            PDPageContentStream cs = b.text(page);
            cs.beginMarkedContent(COSName.getPDFName("Span"), TaggedPdfBuilder.mcid(0));
            cs.showText("Known Data Problems:");
            cs.endMarkedContent();
            cs.beginMarkedContent(COSName.getPDFName("Span"), TaggedPdfBuilder.mcid(1));
            cs.showText("https://example.com");
            cs.endMarkedContent();
            finish(cs);

            Result tags = parse(b.bytes(), config(MarkedContentConfig.Strategy.TAGS));
            assertContains("<p>Known Data Problems:<span lang=\"de-DE\">\n<span class=\"p\">"
                    + "https://example.com</span></span></p>", tags.xml);
        }
    }

    /**
     * Inside a table or a row, a block that is not a row or cell is inline, and its text takes
     * the caption or cell the table gives text there; a Caption element under a table is the
     * table's caption. Loose text around a heading stays on its side of the heading.
     */
    @Test
    public void testBlockInsideATableIsInline() throws Exception {
        try (TaggedPdfBuilder b = new TaggedPdfBuilder()) {
            PDPage page = b.page();
            PDStructureElement table = b.element("Table", b.document, page);
            b.leaf("Caption", table, page, 0);
            PDStructureElement sect = b.element("Sect", table, page);
            b.leaf("P", sect, page, 1);
            PDStructureElement tr = b.element("TR", table, page);
            b.leaf("TD", tr, page, 2);
            PDStructureElement strayRow = b.element("TR", table, page);
            PDStructureElement div = b.element("Div", strayRow, page);
            b.leaf("P", div, page, 3);
            b.leaf("Caption", b.document, page, 4);
            PDPageContentStream cs = b.text(page);
            taggedLine(cs, COSName.getPDFName("Caption"), 0, "Table 1");
            taggedLine(cs, COSName.getPDFName("P"), 1, "Chart label");
            taggedLine(cs, COSName.getPDFName("TD"), 2, "Cell");
            taggedLine(cs, COSName.getPDFName("P"), 3, "Stray");
            taggedLine(cs, COSName.getPDFName("Caption"), 4, "Standalone");
            finish(cs);

            Result tags = parse(b.bytes(), config(MarkedContentConfig.Strategy.TAGS));
            assertWellFormed(tags.xml);
            // nested demoted blocks collapse to the innermost
            assertContains("<table><caption>Table 1</caption>\n<caption><span class=\"p\">"
                    + "Chart label</span></caption>\n<tr>\t<td>Cell</td>", tags.xml);
            assertContains("<tr>\t<td><span class=\"p\">Stray</span></td>", tags.xml);
            assertContains("<div class=\"caption\"><p>Standalone</p>", tags.xml);
        }
    }

    /** An item outside a list and a row outside a table get the container the tree left out. */
    @Test
    public void testItemOutsideAListAndRowOutsideATableGetTheirContainer() throws Exception {
        try (TaggedPdfBuilder b = new TaggedPdfBuilder()) {
            PDPage page = b.page();
            PDStructureElement sect = b.element("Sect", b.document, page);
            PDStructureElement li = b.element("LI", sect, page);
            b.leaf("LBody", li, page, 0);
            PDStructureElement tr = b.element("TR", sect, page);
            b.leaf("TD", tr, page, 1);
            PDStructureElement list = b.element("L", sect, page);
            PDStructureElement item = b.element("LI", list, page);
            b.leaf("LBody", item, page, 2);
            PDPageContentStream cs = b.text(page);
            taggedLine(cs, COSName.getPDFName("LBody"), 0, "Lone item");
            taggedLine(cs, COSName.getPDFName("TD"), 1, "Lone cell");
            taggedLine(cs, COSName.getPDFName("LBody"), 2, "Listed item");
            finish(cs);

            Result tags = parse(b.bytes(), config(MarkedContentConfig.Strategy.TAGS));
            assertWellFormed(tags.xml);
            assertContains("<div class=\"sect\"><ul>\t<li>Lone item</li>\n</ul>\n<table><tr>\t<td>"
                    + "Lone cell</td></tr>\n</table>\n<ul>\t<li>Listed item</li>", tags.xml);
        }
    }

    /** A link around loose text sits inside the paragraph the text gets, not around it. */
    @Test
    public void testLinkAroundLooseTextSitsInItsParagraph() throws Exception {
        try (TaggedPdfBuilder b = new TaggedPdfBuilder()) {
            PDPage page = b.page();
            PDStructureElement outer = b.element("NonStruct", b.document, page);
            b.leaf("NonStruct", outer, page, 0);
            PDStructureElement link = b.element("Link", outer, page);
            link.setLanguage("en-US");
            b.leaf("NonStruct", link, page, 1);
            PDPageContentStream cs = b.text(page);
            taggedLine(cs, COSName.getPDFName("NonStruct"), 0, "RSVP for the lecture");
            taggedLine(cs, COSName.getPDFName("NonStruct"), 1, "here.");
            finish(cs);

            Result tags = parse(b.bytes(), config(MarkedContentConfig.Strategy.TAGS));
            assertWellFormed(tags.xml);
            assertContains("<p>RSVP for the lecture\n<span lang=\"en-US\" class=\"link\">here.</span>"
                    + "</p>", tags.xml);
        }
        // loose text on both sides of a heading stays on its side
        try (TaggedPdfBuilder b = new TaggedPdfBuilder()) {
            PDPage page = b.page();
            b.leaf("Span", b.document, page, 0);
            b.leaf("H1", b.document, page, 1);
            b.leaf("Span", b.document, page, 2);
            PDPageContentStream cs = b.text(page);
            taggedLine(cs, COSName.getPDFName("Span"), 0, "Before");
            taggedLine(cs, COSName.getPDFName("H1"), 1, "Heading");
            taggedLine(cs, COSName.getPDFName("Span"), 2, "After");
            finish(cs);

            Result tags = parse(b.bytes(), config(MarkedContentConfig.Strategy.TAGS));
            assertContains("<p>Before</p>\n<h1>Heading</h1>\n<p>After</p>", tags.xml);
        }
    }

    /** Paragraphs nested in each other under a table are a sequence of spans in its caption. */
    @Test
    public void testNestedParagraphsUnderATableStayFlat() throws Exception {
        try (TaggedPdfBuilder b = new TaggedPdfBuilder()) {
            PDPage page = b.page();
            PDStructureElement table = b.element("Table", b.document, page);
            PDStructureNode parent = table;
            PDPageContentStream cs = b.text(page);
            for (int i = 0; i < 80; i++) {
                PDStructureElement p = b.element("P", parent, page);
                b.leaf("Span", p, page, i);
                taggedLine(cs, COSName.getPDFName("Span"), i, "Line " + i);
                parent = p;
            }
            PDStructureElement tr = b.element("TR", table, page);
            b.leaf("TD", tr, page, 80);
            taggedLine(cs, COSName.getPDFName("TD"), 80, "Cell");
            finish(cs);

            Result tags = parse(b.bytes(), config(MarkedContentConfig.Strategy.TAGS));
            assertWellFormed(tags.xml);
            assertContains("<table><caption><span class=\"p\">Line 0</span>\n<span class=\"p\">Line 1"
                    + "</span>", tags.xml);
            assertContains("<span class=\"p\">Line 79</span></caption>\n<tr>\t<td>Cell</td>",
                    tags.xml);
        }
    }

    /** A custom container type is a division, and text straight in it gets paragraphs too. */
    @Test
    public void testCustomContainerTextTakesParagraphs() throws Exception {
        try (TaggedPdfBuilder b = new TaggedPdfBuilder()) {
            PDPage page = b.page();
            PDStructureElement article = b.element("Article", b.document, page);
            b.leaf("NonStruct", article, page, 0);
            b.leaf("NonStruct", article, page, 1);
            PDPageContentStream cs = b.text(page);
            taggedLine(cs, COSName.getPDFName("NonStruct"), 0, "First line");
            taggedLine(cs, COSName.getPDFName("NonStruct"), 1, "Second line");
            finish(cs);

            Result tags = parse(b.bytes(), config(MarkedContentConfig.Strategy.TAGS));
            assertContains("<div class=\"article\"><p>First line\nSecond line</p>", tags.xml);
        }
    }

    /** Whitespace at the edges of a block is layout; separators still sit outside links. */
    @Test
    public void testBlockEdgesAreTrimmed() throws Exception {
        try (TaggedPdfBuilder b = new TaggedPdfBuilder()) {
            PDPage page = b.page();
            PDStructureElement p = b.leaf("P", b.document, page, 0);
            b.leaf("Span", p, page, 1).setLanguage("de-DE");
            b.leaf("P", b.document, page, 2);
            PDPageContentStream cs = b.text(page);
            cs.beginMarkedContent(COSName.P, mcid(0));
            cs.showText(" Lead ");
            cs.beginMarkedContent(COSName.getPDFName("Span"), mcid(1));
            cs.showText("mitte ");
            cs.endMarkedContent();
            cs.showText("tail ");
            cs.endMarkedContent();
            cs.newLine();
            taggedLine(cs, COSName.P, 2, " Next ");
            finish(cs);

            Result tags = parse(b.bytes(), config(MarkedContentConfig.Strategy.TAGS));
            assertContains("<p>Lead <span lang=\"de-DE\">mitte</span> tail</p>", tags.xml);
            assertContains("<p>Next</p>", tags.xml);
        }
    }

    /** Text in a list item before its nested paragraph keeps the space between them. */
    @Test
    public void testSpaceBeforeNestedBlockIsKept() throws Exception {
        try (TaggedPdfBuilder b = new TaggedPdfBuilder()) {
            PDPage page = b.page();
            PDStructureElement li = b.element("LI", b.element("L", b.document, page), page);
            li.appendKid(0);
            b.leaf("P", li, page, 1);
            PDPageContentStream cs = b.text(page);
            cs.beginMarkedContent(COSName.getPDFName("LI"), mcid(0));
            cs.showText("Item ");
            cs.endMarkedContent();
            cs.beginMarkedContent(COSName.P, mcid(1));
            cs.showText("nested");
            cs.endMarkedContent();
            finish(cs);

            Result tags = parse(b.bytes(), config(MarkedContentConfig.Strategy.TAGS));
            assertContains("<li>Item <p>nested</p>", tags.xml);
        }
    }

    @Test
    public void testDanglingGate() throws Exception {
        try (TaggedPdfBuilder b = new TaggedPdfBuilder()) {
            PDPage page = b.page();
            for (int i = 0; i < 10; i++) {
                b.leaf("P", b.document, page, i);
            }
            PDPageContentStream cs = b.text(page);
            taggedLine(cs, COSName.P, 0, "Only this one exists");
            finish(cs);
            byte[] pdf = b.bytes();

            Result auto = parse(pdf, config(MarkedContentConfig.Strategy.AUTO));
            assertEquals("1:dangling=0.90", auto.metadata.get(PDF.MARKED_CONTENT_REJECTIONS));
            Result tags = parse(pdf, config(MarkedContentConfig.Strategy.TAGS));
            assertEquals(1, tags.metadata.getInt(PDF.MARKED_CONTENT_PAGES_TAGGED));
            assertContains("<p>Only this one exists", tags.xml);
        }
    }

    @Test
    public void testLinkWithUriBecomesAnchor() throws Exception {
        try (TaggedPdfBuilder b = new TaggedPdfBuilder()) {
            PDPage page = b.page();
            PDAnnotationLink link = new PDAnnotationLink();
            link.setRectangle(new PDRectangle(72, 690, 100, 14));
            PDActionURI action = new PDActionURI();
            action.setURI("http://example.com/");
            link.setAction(action);
            page.getAnnotations().add(link);
            PDStructureElement p = b.leaf("P", b.document, page, 0);
            PDStructureElement linkElement = b.leaf("Link", p, page, 1);
            PDObjectReference objr = new PDObjectReference();
            objr.setReferencedObject(link);
            linkElement.appendKid(objr);
            PDPageContentStream cs = b.text(page);
            cs.beginMarkedContent(COSName.P, mcid(0));
            cs.showText("Visit ");
            cs.beginMarkedContent(COSName.LINK, mcid(1));
            cs.showText("example");
            cs.endMarkedContent();
            cs.showText(" today");
            cs.endMarkedContent();
            finish(cs);

            Result tags = parse(b.bytes(), config(MarkedContentConfig.Strategy.TAGS));
            assertContains("<p>Visit <a href=\"http://example.com/\">example</a> today",
                    tags.xml);
        }
    }

    @Test
    public void testFigureWithoutTextIsPlacedWithItsAlt() throws Exception {
        try (TaggedPdfBuilder b = new TaggedPdfBuilder()) {
            PDPage page = b.page();
            b.leaf("P", b.document, page, 0);
            PDStructureElement figure = b.element("Figure", b.document, page);
            figure.setAlternateDescription("A bar chart of sales by region");
            PDImageXObject image = LosslessFactory.createFromImage(b.doc,
                    new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB));
            PDObjectReference objr = new PDObjectReference();
            objr.setReferencedObject(image);
            figure.appendKid(objr);
            b.leaf("P", b.document, page, 1);
            PDPageContentStream cs = b.text(page);
            taggedLine(cs, COSName.P, 0, "Before the figure");
            taggedLine(cs, COSName.P, 1, "After the figure");
            cs.endText();
            cs.drawImage(image, 72, 400, 100, 100);
            cs.close();

            Result tags = parse(b.bytes(), config(MarkedContentConfig.Strategy.TAGS));
            String body = tags.body();
            String div = "alt=\"A bar chart of sales by region\" class=\"figure\">"
                    + "A bar chart of sales by region";
            assertContains(div, body);
            assertTrue(body.indexOf("Before the figure") < body.indexOf(div));
            assertTrue(body.indexOf(div) < body.indexOf("After the figure"));
            assertWellFormed(tags.xml);
        }
    }

    @Test
    public void testFigureAltPrecedesItsTextOnce() throws Exception {
        try (TaggedPdfBuilder b = new TaggedPdfBuilder()) {
            PDPage page = b.page();
            PDStructureElement figure = b.leaf("Figure", b.document, page, 0);
            figure.setAlternateDescription("Diagram of the pump");
            PDPageContentStream cs = b.text(page);
            taggedLine(cs, COSName.P, 0, "inlet");
            cs.beginMarkedContent(COSName.P, mcid(0));
            cs.showText("outlet");
            cs.endMarkedContent();
            finish(cs);

            String body = parse(b.bytes(), config(MarkedContentConfig.Strategy.TAGS)).body();
            assertContains("class=\"figure\">Diagram of the pump\n<p>inlet\noutlet</p>", body);
            assertContainsCount("Diagram of the pump", body, 2);
        }
    }

    @Test
    public void testListNumberingSelectsOrderedList() throws Exception {
        try (TaggedPdfBuilder b = new TaggedPdfBuilder()) {
            PDPage page = b.page();
            PDStructureElement ordered = b.element("L", b.document, page);
            PDListAttributeObject numbering = new PDListAttributeObject();
            numbering.setListNumbering(PDListAttributeObject.LIST_NUMBERING_DECIMAL);
            ordered.addAttribute(numbering);
            PDStructureElement li = b.element("LI", ordered, page);
            b.leaf("P", b.element("LBody", li, page), page, 0);
            PDStructureElement unordered = b.element("L", b.document, page);
            PDStructureElement li2 = b.element("LI", unordered, page);
            b.leaf("P", b.element("LBody", li2, page), page, 1);
            PDPageContentStream cs = b.text(page);
            taggedLine(cs, COSName.P, 0, "First item");
            taggedLine(cs, COSName.P, 1, "A bullet");
            finish(cs);

            Result tags = parse(b.bytes(), config(MarkedContentConfig.Strategy.TAGS));
            assertContains("<ol>\t<li><p>First item", tags.xml);
            assertContains("<ul>\t<li><p>A bullet", tags.xml);
            assertWellFormed(tags.xml);
        }
    }

    @Test
    public void testHeadingLevelFollowsSectionDepth() throws Exception {
        try (TaggedPdfBuilder b = new TaggedPdfBuilder()) {
            PDPage page = b.page();
            b.leaf("H", b.document, page, 0);
            PDStructureElement sect = b.element("Sect", b.document, page);
            b.leaf("H", sect, page, 1);
            b.leaf("H3", sect, page, 2);
            PDPageContentStream cs = b.text(page);
            taggedLine(cs, COSName.P, 0, "Top heading");
            taggedLine(cs, COSName.P, 1, "Section heading");
            taggedLine(cs, COSName.P, 2, "Explicit level");
            finish(cs);

            Result tags = parse(b.bytes(), config(MarkedContentConfig.Strategy.TAGS));
            assertContains("<h1>Top heading", tags.xml);
            assertContains("<div class=\"sect\"><h2>Section heading", tags.xml);
            assertContains("<h3>Explicit level", tags.xml);
        }
    }

    @Test
    public void testMaxPagesIsHonored() throws Exception {
        try (TaggedPdfBuilder b = new TaggedPdfBuilder()) {
            for (int i = 0; i < 3; i++) {
                PDPage page = b.page();
                b.leaf("P", b.document, page, 0);
                PDPageContentStream cs = b.text(page);
                taggedLine(cs, COSName.P, 0, "Page " + (i + 1));
                finish(cs);
            }
            PDFParserConfig config = config(MarkedContentConfig.Strategy.TAGS);
            config.setMaxPages(2);
            Result tags = parse(b.bytes(), config);
            assertContainsCount(PAGE_DIV, tags.xml, 2);
            assertFalse(tags.xml.contains("Page 3"));
            assertEquals(2, tags.metadata.getInt(PDF.MARKED_CONTENT_PAGES_TAGGED));
        }
    }

    @Test
    public void testDetectAnglesUsesTheStripper() throws Exception {
        try (TaggedPdfBuilder b = new TaggedPdfBuilder()) {
            PDPage page = b.page();
            b.leaf("P", b.document, page, 0);
            PDPageContentStream cs = b.text(page);
            taggedLine(cs, COSName.P, 0, "Some text");
            finish(cs);
            byte[] pdf = b.bytes();

            PDFParserConfig angles = config(MarkedContentConfig.Strategy.TAGS);
            angles.setDetectAngles(true);
            PDFParserConfig none = config(MarkedContentConfig.Strategy.NONE);
            none.setDetectAngles(true);
            Result withAngles = parse(pdf, angles);
            assertEquals(parse(pdf, none).body(), withAngles.body());
            assertNull(withAngles.metadata.get(PDF.MARKED_CONTENT_PAGES_TAGGED));
        }
    }

    @Test
    public void testAutoOcrVerdictSeesTaggedText() throws Exception {
        try (TaggedPdfBuilder b = new TaggedPdfBuilder()) {
            PDPage full = b.page();
            PDPage sparse = b.page();
            b.leaf("P", b.document, full, 0);
            b.leaf("P", b.document, sparse, 0);
            PDPageContentStream cs = b.text(full);
            taggedLine(cs, COSName.P, 0, "Enough tagged text to keep the page as it is");
            finish(cs);
            cs = b.text(sparse);
            taggedLine(cs, COSName.P, 0, "Hi");
            finish(cs);

            PDFParserConfig config = config(MarkedContentConfig.Strategy.TAGS);
            config.setText(PDFParserConfig.TextPolicy.AUTO);
            config.getOcr().setStrategyAuto(new OcrConfig.StrategyAuto(0.02f, 10));
            Result result = parse(b.bytes(), config, mockOcrParser(config, "MOCK_OCR_CONTENT"));
            assertContains("<p>Enough tagged text", result.page(1));
            assertFalse(result.page(1).contains("MOCK_OCR_CONTENT"));
            assertContains("MOCK_OCR_CONTENT", result.page(2));
            assertFalse(result.page(2).contains("Hi"));
            assertEquals(1, result.metadata.getInt(PDF.OCR_PAGE_COUNT));
            assertEquals(2, result.metadata.getInt(PDF.MARKED_CONTENT_PAGES_TAGGED));
            assertWellFormed(result.xml);
        }
    }

    @Test
    public void testDeprecatedFlagMapsToStrategy() {
        PDFParserConfig config = new PDFParserConfig();
        assertEquals(MarkedContentConfig.Strategy.AUTO, config.getMarkedContent().getStrategy());
        config.setExtractMarkedContent(true);
        assertEquals(MarkedContentConfig.Strategy.TAGS, config.getMarkedContent().getStrategy());
        config.setExtractMarkedContent(false);
        assertEquals(MarkedContentConfig.Strategy.NONE, config.getMarkedContent().getStrategy());
    }

    // ---- helpers ----

    private static PDFParserConfig config(MarkedContentConfig.Strategy strategy) {
        PDFParserConfig config = new PDFParserConfig();
        config.getMarkedContent().setStrategy(strategy);
        return config;
    }

    private static Result parse(byte[] pdf, PDFParserConfig config) throws Exception {
        return parse(pdf, config, null);
    }

    private static Result parse(byte[] pdf, PDFParserConfig config, Parser ocr)
            throws Exception {
        ParseContext context = new ParseContext();
        context.set(PDFParserConfig.class, config);
        if (ocr != null) {
            context.set(Parser.class, ocr);
        }
        Metadata metadata = new Metadata();
        ToXMLContentHandler handler = new ToXMLContentHandler();
        try (TikaInputStream tis = TikaInputStream.get(pdf)) {
            new PDFParser().parse(tis, handler, metadata, context);
        }
        return new Result(handler.toString(), metadata);
    }

    private static void assertWellFormed(String xml) throws Exception {
        XMLReaderUtils.buildDOM(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }

    private abstract static class MockEngine implements Parser, TextRecognizer {
        private static final long serialVersionUID = 1L;
    }

    private static Parser mockOcrParser(PDFParserConfig config, String text) {
        MediaType type = MediaType.image(config.getOcr().getImageFormat().getFormatName());
        return new MockEngine() {
            @Override
            public Set<MediaType> getSupportedTypes(ParseContext context) {
                return Collections.singleton(type);
            }

            @Override
            public void parse(TikaInputStream tis, ContentHandler handler, Metadata metadata,
                              ParseContext context) throws IOException, SAXException, TikaException {
                XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
                xhtml.startDocument();
                xhtml.characters(text);
                xhtml.endDocument();
            }
        };
    }
}
