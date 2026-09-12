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
import org.apache.pdfbox.pdmodel.documentinterchange.taggedpdf.PDListAttributeObject;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionURI;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink;
import org.junit.jupiter.api.Test;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import org.apache.tika.TikaTest;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.PDF;
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
            assertContains("<table><tr>\t<td><p>NHG </p>", tags.xml);
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
            config.getOcr().setStrategy(OcrConfig.Strategy.AUTO);
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
        assertEquals(MarkedContentConfig.Strategy.NONE, config.getMarkedContent().getStrategy());
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
