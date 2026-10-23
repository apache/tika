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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javax.imageio.ImageIO;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import org.apache.tika.TikaTest;
import org.apache.tika.exception.TikaException;
import org.apache.tika.exception.WriteLimitReachedException;
import org.apache.tika.extractor.DocumentSelector;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Rendering;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.enricher.CompositeContentEnricher;
import org.apache.tika.parser.enricher.TextRecognizer;
import org.apache.tika.parser.pages.TextPolicy;
import org.apache.tika.renderer.CompositeRenderer;
import org.apache.tika.renderer.ImageType;
import org.apache.tika.renderer.RenderRequest;
import org.apache.tika.renderer.RenderResult;
import org.apache.tika.renderer.RenderResults;
import org.apache.tika.renderer.pdf.pdfbox.PDFBoxRenderer;
import org.apache.tika.sax.ToXMLContentHandler;
import org.apache.tika.sax.WriteOutContentHandler;
import org.apache.tika.sax.XHTMLContentHandler;

/**
 * {@code pages.emit} emits page renders as embedded documents at page end, drawn with
 * {@code pages.render} under the {@code emit.render} overlay; OCR keeps rendering with
 * {@code pages.render}, and the two share one render only when it is the same image.
 */
public class PDFPagesEmitTest extends TikaTest {

    private static final String TWO_PAGES = "testPDF_bookmarks.pdf";

    private static PDFParserConfig emitting() {
        PDFParserConfig config = new PDFParserConfig();
        config.pages().emit().setEnabled(true);
        return config;
    }

    @Test
    public void testEmittedPagesUseTheEmitOverlay() throws Exception {
        PDFParserConfig config = emitting();
        config.pages().emit().render().setDpi(36);
        config.pages().emit().render().setImageType(ImageType.RGB);
        ImageSink pages = new ImageSink();

        parse(config, pages, null);

        assertEquals(2, pages.images.size());
        for (Shape page : pages.images) {
            assertEquals(pixelsAcross(36), page.width, "36 dpi, not render's 300");
            assertEquals(3, page.colorComponents, "RGB, not render's GRAY");
        }
    }

    @Test
    public void testNoOverlayFollowsRender() throws Exception {
        PDFParserConfig config = emitting();
        config.pages().render().setDpi(36);
        ImageSink pages = new ImageSink();

        parse(config, pages, null);

        assertEquals(2, pages.images.size());
        for (Shape page : pages.images) {
            assertEquals(pixelsAcross(36), page.width, "the base render's dpi");
            assertEquals(1, page.colorComponents, "the base render's GRAY");
        }
    }

    /** The 4.0 spellings: imageStrategy turns emission on, ocr.dpi is the base render. */
    @Test
    public void testFourZeroAliases() throws Exception {
        PDFParserConfig config = new PDFParserConfig();
        config.setImageStrategy(PDFParserConfig.IMAGE_STRATEGY.RENDER_PAGES_BEFORE_PARSE);
        OcrConfig ocr = new OcrConfig();
        ocr.setDpi(36);
        config.setOcr(ocr);
        ImageSink pages = new ImageSink();

        parse(config, pages, null);

        assertEquals(2, pages.images.size());
        assertEquals(pixelsAcross(36), pages.images.get(0).width);
    }

    @ParameterizedTest
    @EnumSource(value = TextPolicy.class, names = {"EXTRACT_AND_OCR", "OCR"})
    public void testOcrKeepsItsOwnSettings(TextPolicy text) throws Exception {
        PDFParserConfig config = emitting();
        config.pages().setText(text);
        config.pages().render().setDpi(72);
        config.pages().emit().render().setDpi(36);
        config.pages().emit().render().setImageType(ImageType.RGB);
        ImageSink pages = new ImageSink();
        ImageSink ocr = new ImageSink();

        parse(config, pages, ocr);

        assertEquals(2, ocr.images.size(), "the recognizer saw each page");
        for (Shape page : ocr.images) {
            assertEquals(pixelsAcross(72), page.width, "OCR renders at render.dpi");
            assertEquals(1, page.colorComponents, "OCR renders in render.imageType");
        }
        assertEquals(2, pages.images.size(), "each page was emitted, OCR-only too");
        for (Shape page : pages.images) {
            assertEquals(pixelsAcross(36), page.width);
            assertEquals(3, page.colorComponents);
        }
    }

    @Test
    public void testOcrRenderIsSharedOnlyWhenItIsTheSameImage() throws Exception {
        PDFParserConfig same = emitting();
        same.pages().setText(TextPolicy.EXTRACT_AND_OCR);
        same.pages().render().setDpi(36);
        assertEquals(2, renders(same), "one render per page feeds both the output and OCR");

        PDFParserConfig different = emitting();
        different.pages().setText(TextPolicy.EXTRACT_AND_OCR);
        different.pages().render().setDpi(36);
        different.pages().emit().render().setImageType(ImageType.RGB);
        assertEquals(4, renders(different), "the RGB page is rendered again for emission");
    }

    @Test
    public void testMaxImagePixelsSkipsThePageWithAWarning() throws Exception {
        PDFParserConfig config = emitting();
        config.pages().emit().render().setDpi(36);
        config.pages().emit().render().setMaxImagePixels(1000L);
        ImageSink pages = new ImageSink();

        Metadata metadata = parse(config, pages, null);

        assertEquals(0, pages.images.size(), "no page fits in 1000 pixels");
        String[] warnings = metadata.getValues(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING);
        assertEquals(2, warnings.length, "one warning per skipped page");
        assertTrue(warnings[0].contains("maxImagePixels"), warnings[0]);
        assertArrayEquals(new String[] {"1", "2"}, metadata.getValues(Rendering.RENDER_FAILED_PAGE),
                "the flag a client filters on");
    }

    /** The box is a ceiling: 300 dpi would be 2550 px across, the box makes it 200. */
    @Test
    public void testBoxScalesDownToFit() throws Exception {
        PDFParserConfig config = emitting();
        config.pages().emit().render().setMaxWidth(200);
        config.pages().emit().render().setMaxHeight(200);
        ImageSink pages = new ImageSink();

        parse(config, pages, null);

        assertEquals(2, pages.images.size());
        for (Shape page : pages.images) {
            assertTrue(page.width <= 200 && page.height <= 200, page.width + "x" + page.height);
            assertTrue(page.width > 100 || page.height > 100, "fits the box, not much smaller");
        }
        // a box wider than the page at the target dpi changes nothing
        config.pages().emit().render().setMaxWidth(100_000);
        config.pages().emit().render().setMaxHeight(100_000);
        pages = new ImageSink();
        parse(config, pages, null);
        assertEquals(pixelsAcross(300), pages.images.get(0).width, "never enlarged, never shrunk");
    }

    /** A page below the minimum is not rendered for anything: no emission, no OCR, no warning. */
    @Test
    public void testMinimumSkipsTinyPages(@TempDir Path tmp) throws Exception {
        Path pdf = tmp.resolve("hairline.pdf");
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage(new PDRectangle(600, 0.5f)));
            document.addPage(new PDPage(PDRectangle.LETTER));
            document.save(pdf.toFile());
        }
        PDFParserConfig config = emitting();
        config.pages().setText(TextPolicy.OCR);
        config.pages().render().setDpi(36);
        config.pages().render().setMinHeight(4);
        ImageSink pages = new ImageSink();
        ImageSink ocr = new ImageSink();
        PDFParser parser = new PDFParser();
        parser.setContentEnrichers(new CompositeContentEnricher(List.of(ocr)));
        ParseContext context = new ParseContext();
        context.set(PDFParserConfig.class, config);
        context.set(Parser.class, new AutoDetectParser(pages));
        Metadata metadata = new Metadata();
        try (TikaInputStream tis = TikaInputStream.get(pdf)) {
            parser.parse(tis, new ToXMLContentHandler(), metadata, context);
        }
        assertEquals(1, pages.images.size(), "the letter page only");
        assertEquals(1, ocr.images.size(), "OCR saw the letter page only");
        assertEquals(0, metadata.getValues(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING).length,
                "a policy skip is not a warning");
        assertEquals(0, metadata.getValues(Rendering.RENDER_FAILED_PAGE).length,
                "nor a failed page");
    }

    /** A writer that dies still emits the page it was on; pages it never started are not rendered. */
    @Test
    public void testEmissionSurvivesAFailedTextPass() throws Exception {
        PDFParserConfig config = emitting();
        config.pages().render().setDpi(36);
        ImageSink pages = new ImageSink();
        PDFParser parser = new PDFParser();
        ParseContext context = new ParseContext();
        context.set(PDFParserConfig.class, config);
        context.set(Parser.class, new AutoDetectParser(pages));
        // dies once, on the first text the writer sends; the emitted renders come after
        ContentHandler dying = new ToXMLContentHandler() {
            private boolean died;

            @Override
            public void characters(char[] ch, int start, int length) {
                if (!died && !new String(ch, start, length).isBlank()) {
                    died = true;
                    throw new IllegalStateException("handler died");
                }
            }
        };
        try (TikaInputStream tis = TikaInputStream.get(
                getResourceAsStream("/test-documents/" + TWO_PAGES))) {
            assertThrows(IllegalStateException.class,
                    () -> parser.parse(tis, dying, new Metadata(), context));
        }
        assertEquals(1, pages.images.size(), "the page in flight, at document end");
    }

    /** The write limit is a stop: nothing more is rendered, nothing is written past it. */
    @Test
    public void testWriteLimitStopsEmission() throws Exception {
        PDFParserConfig config = emitting();
        config.pages().render().setDpi(36);
        CountingRenderer renderer = new CountingRenderer();
        PDFParser parser = new PDFParser();
        parser.setRenderer(renderer);
        ParseContext context = new ParseContext();
        context.set(PDFParserConfig.class, config);
        context.set(Parser.class, new AutoDetectParser(new ImageSink()));
        Metadata metadata = new Metadata();
        try (TikaInputStream tis = TikaInputStream.get(
                getResourceAsStream("/test-documents/" + TWO_PAGES))) {
            assertThrows(WriteLimitReachedException.class, () -> parser.parse(tis,
                    new WriteOutContentHandler(new ToXMLContentHandler(), 5), metadata, context));
        }
        assertEquals(0, renderer.pages, "the limit hit on page 1 before its end");
        assertEquals("true", metadata.get(TikaCoreProperties.WRITE_LIMIT_REACHED));
    }

    /** The RENDERING child sits inside its page, as bytes and metadata: no file name as text. */
    @Test
    public void testEmittedPageSitsInsideThePage() throws Exception {
        PDFParserConfig config = emitting();
        config.pages().render().setDpi(36);
        config.pages().emit().setMaxPages(1);
        PDFParser parser = new PDFParser();
        ParseContext context = new ParseContext();
        context.set(PDFParserConfig.class, config);
        context.set(Parser.class, new AutoDetectParser(new ImageSink()));
        ToXMLContentHandler handler = new ToXMLContentHandler();
        try (TikaInputStream tis = TikaInputStream.get(
                getResourceAsStream("/test-documents/" + TWO_PAGES))) {
            parser.parse(tis, handler, new Metadata(), context);
        }
        String xml = handler.toString();
        int page1 = xml.indexOf("<div class=\"page\">");
        int page2 = xml.indexOf("<div class=\"page\">", page1 + 1);
        int embedded = xml.indexOf("seen");
        assertTrue(page1 < embedded && embedded < page2, xml);
        assertEquals(-1, xml.indexOf("<h1>"), "no file name in the text: " + xml);
        assertEquals(-1, xml.indexOf("tika-pdfbox-rendering"), xml);
    }

    /** OCR renders through the configured engine, its leaf when composite, sharing the open document. */
    @Test
    public void testConfiguredEngineGetsTheOpenDocument() throws Exception {
        PDFParserConfig config = emitting();
        config.pages().setText(TextPolicy.EXTRACT_AND_OCR);
        config.pages().render().setDpi(36);
        config.pages().emit().render().setImageType(ImageType.RGB);
        CountingRenderer renderer = new CountingRenderer();
        PDFParser parser = new PDFParser();
        parser.setRenderer(new CompositeRenderer(List.of(renderer)));
        parser.setContentEnrichers(new CompositeContentEnricher(List.of(new ImageSink())));
        ParseContext context = new ParseContext();
        context.set(PDFParserConfig.class, config);
        context.set(Parser.class, new AutoDetectParser(new ImageSink()));
        try (TikaInputStream tis = TikaInputStream.get(
                getResourceAsStream("/test-documents/" + TWO_PAGES))) {
            parser.parse(tis, new ToXMLContentHandler(), new Metadata(), context);
        }
        assertEquals(4, renderer.pages, "OCR and emit, both through the leaf");
        assertEquals(4, renderer.withOpenDocument, "never reloaded from the spool");
    }

    /** A render that throws is asked for once per page, however many consumers want it. */
    @Test
    public void testFailedRenderIsNotRetried() throws Exception {
        PDFParserConfig config = emitting();
        config.pages().setText(TextPolicy.EXTRACT_AND_OCR);
        int[] calls = new int[1];
        PDFParser parser = new PDFParser();
        parser.setRenderer(new PDFBoxRenderer() {
            @Override
            public RenderResults render(TikaInputStream tis, Metadata metadata,
                                        ParseContext parseContext, RenderRequest... requests)
                    throws IOException {
                calls[0]++;
                throw new IOException("engine down");
            }
        });
        parser.setContentEnrichers(new CompositeContentEnricher(List.of(new ImageSink())));
        ParseContext context = new ParseContext();
        context.set(PDFParserConfig.class, config);
        context.set(Parser.class, new AutoDetectParser(new ImageSink()));
        Metadata metadata = new Metadata();
        try (TikaInputStream tis = TikaInputStream.get(
                getResourceAsStream("/test-documents/" + TWO_PAGES))) {
            // recorded per page, rethrown at the end
            assertThrows(TikaException.class,
                    () -> parser.parse(tis, new ToXMLContentHandler(), metadata, context));
        }
        assertEquals(2, calls[0], "once per page: OCR asked, emission did not ask again");
        assertEquals(2, metadata.getValues(Rendering.RENDER_FAILED_PAGE).length,
                "one failure per page, none re-recorded after the parse threw");
    }

    /** A page whose render already failed is not rendered again when the writer then dies on it. */
    @Test
    public void testFailedPageIsNotRetriedWhenTheWriterDies() throws Exception {
        PDFParserConfig config = emitting();
        config.pages().setText(TextPolicy.EXTRACT_AND_OCR);
        int[] calls = new int[1];
        PDFParser parser = new PDFParser();
        parser.setRenderer(new PDFBoxRenderer() {
            @Override
            public RenderResults render(TikaInputStream tis, Metadata metadata,
                                        ParseContext parseContext, RenderRequest... requests)
                    throws IOException {
                calls[0]++;
                throw new IOException("engine down");
            }
        });
        parser.setContentEnrichers(new CompositeContentEnricher(List.of(new ImageSink())));
        ParseContext context = new ParseContext();
        context.set(PDFParserConfig.class, config);
        context.set(Parser.class, new AutoDetectParser(new ImageSink()));
        Metadata metadata = new Metadata();
        ContentHandler dying = new ToXMLContentHandler() {
            int pageDivs = 0;
            @Override
            public void endElement(String uri, String localName, String qName) throws SAXException {
                super.endElement(uri, localName, qName);
                if ("div".equals(localName) && ++pageDivs == 2) {
                    throw new SAXException("writer died on page 2");
                }
            }
        };
        try (TikaInputStream tis = TikaInputStream.get(
                getResourceAsStream("/test-documents/" + TWO_PAGES))) {
            assertThrows(Exception.class, () -> parser.parse(tis, dying, metadata, context));
        }
        assertEquals(2, calls[0], "page 2's failed render is not attempted again");
        assertEquals(2, metadata.getValues(Rendering.RENDER_FAILED_PAGE).length);
    }

    /**
     * A page the engine cannot render is a warning whichever consumer asked first: emission
     * sharing OCR's render (the default), emission with its own render, and the 4.0 alias.
     */
    @Test
    public void testRenderFailureIsAWarningWhoeverAsked() throws Exception {
        PDFParserConfig shared = new PDFParserConfig();
        shared.pages().setText(TextPolicy.EXTRACT);
        shared.pages().emit().setEnabled(true);
        PDFParserConfig own = new PDFParserConfig();
        own.pages().setText(TextPolicy.EXTRACT);
        own.pages().emit().setEnabled(true);
        own.pages().emit().render().setDpi(72);
        PDFParserConfig alias = new PDFParserConfig();
        alias.pages().setText(TextPolicy.EXTRACT);
        alias.setImageStrategy(PDFParserConfig.IMAGE_STRATEGY.RENDER_PAGES_AT_PAGE_END);
        for (PDFParserConfig config : List.of(shared, own, alias)) {
            // a broken page tree throws from the page accessors, outside the draw
            String text = parseWith(config, new PDFBoxRenderer() {
                @Override
                protected RenderResult renderPage(PDFRenderer renderer, PDPage page, int id,
                                                  int pageNumber, Metadata metadata,
                                                  ParseContext parseContext) {
                    throw new IllegalStateException("broken page");
                }
            });
            assertContains("Denmark bookmark is here", text);
            // an engine that fails as a whole, as pdftoppm does on a page it cannot read
            text = parseWith(config, new PDFBoxRenderer() {
                @Override
                public RenderResults render(TikaInputStream tis, Metadata metadata,
                                            ParseContext parseContext, RenderRequest... requests)
                        throws TikaException {
                    throw new TikaException("engine failed");
                }
            });
            assertContains("Denmark bookmark is here", text);
        }
    }

    /** The parse completes; the failure is a warning per page, and the text is all there. */
    private String parseWith(PDFParserConfig config, PDFBoxRenderer renderer) throws Exception {
        PDFParser parser = new PDFParser();
        parser.setRenderer(renderer);
        ParseContext context = new ParseContext();
        context.set(PDFParserConfig.class, config);
        context.set(Parser.class, new AutoDetectParser(new ImageSink()));
        Metadata metadata = new Metadata();
        ToXMLContentHandler handler = new ToXMLContentHandler();
        try (TikaInputStream tis = TikaInputStream.get(
                getResourceAsStream("/test-documents/" + TWO_PAGES))) {
            parser.parse(tis, handler, metadata, context);
        }
        assertEquals(2, metadata.getValues(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING).length,
                "one warning per page");
        assertArrayEquals(new String[] {"1", "2"}, metadata.getValues(Rendering.RENDER_FAILED_PAGE),
                "each page flagged once");
        return handler.toString();
    }

    /** A selector that refuses the page image is asked before anything is drawn. */
    @Test
    public void testSelectorIsAskedBeforeTheRender() throws Exception {
        PDFParserConfig config = emitting();
        config.pages().setText(TextPolicy.EXTRACT);
        CountingRenderer renderer = new CountingRenderer();
        PDFParser parser = new PDFParser();
        parser.setRenderer(renderer);
        ParseContext context = new ParseContext();
        context.set(PDFParserConfig.class, config);
        context.set(Parser.class, new AutoDetectParser(new ImageSink()));
        context.set(DocumentSelector.class,
                metadata -> !"image/png".equals(metadata.get(HttpHeaders.CONTENT_TYPE)));
        try (TikaInputStream tis = TikaInputStream.get(
                getResourceAsStream("/test-documents/" + TWO_PAGES))) {
            parser.parse(tis, new ToXMLContentHandler(), new Metadata(), context);
        }
        assertEquals(0, renderer.pages, "refused before the render");
    }

    /** emit.maxDepth 0 renders the document sent, not the PDFs it attaches. */
    @Test
    public void testMaxDepthKeepsAttachmentsUnrendered() throws Exception {
        ParseContext context = new ParseContext();
        context.setJsonConfig("pages", "{\"emit\": {\"enabled\": true, \"maxPages\": 1,"
                + " \"render\": {\"dpi\": 20}}}");
        assertEquals(List.of(1, 2, 2), renderingDepths("testPDFPackage.pdf", context),
                "the package and its two attached PDFs");
        context = new ParseContext();
        context.setJsonConfig("pages", "{\"emit\": {\"enabled\": true, \"maxPages\": 1,"
                + " \"maxDepth\": 0, \"render\": {\"dpi\": 20}}}");
        assertEquals(List.of(1), renderingDepths("testPDFPackage.pdf", context));
    }

    private List<Integer> renderingDepths(String pdf, ParseContext context) throws Exception {
        List<Integer> depths = new ArrayList<>();
        for (Metadata m : getRecursiveMetadata(pdf, context)) {
            if (TikaCoreProperties.EmbeddedResourceType.RENDERING.name()
                    .equals(m.get(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE))) {
                depths.add(m.getInt(TikaCoreProperties.EMBEDDED_DEPTH));
            }
        }
        return depths;
    }

    /** An OCR-only strategy draws a different page: the emitted one is a full render. */
    @Test
    public void testOcrOnlyStrategyIsNotEmitted() throws Exception {
        PDFParserConfig config = emitting();
        config.pages().setText(TextPolicy.EXTRACT_AND_OCR);
        config.pages().render().setDpi(36);
        config.setRenderingStrategy(OcrConfig.RenderingStrategy.NO_TEXT);
        assertEquals(4, renders(config), "two OCR renders without text, two full ones emitted");
    }

    /** A page the engine refuses is warned about once, whoever asked for the render. */
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    public void testOcrRenderRefusedWarnsOnce(boolean emit) throws Exception {
        PDFParserConfig config = new PDFParserConfig();
        config.pages().emit().setEnabled(emit);
        config.pages().setText(TextPolicy.OCR);
        config.pages().render().setMaxImagePixels(1000L);
        ImageSink pages = new ImageSink();
        ImageSink ocr = new ImageSink();

        Metadata metadata = parse(config, pages, ocr);

        assertEquals(0, ocr.images.size());
        assertEquals(0, pages.images.size());
        assertEquals(2, metadata.getValues(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING).length,
                "one per page");
    }

    /** The 4.0 aliases and the canonical spelling are one overlay: a request's alias wins. */
    @Test
    public void testRequestAliasOverridesConfigPages() throws Exception {
        PDFParser parser = new PDFParser();
        parser.getPDFParserConfig().pages().render().setDpi(72);
        parser.getPDFParserConfig().pages().setText(TextPolicy.EXTRACT);
        ParseContext context = new ParseContext();
        context.setJsonConfig("pdf-parser", "{\"ocr\": {\"dpi\": 36, \"strategy\": \"OCR_ONLY\"},"
                + " \"imageStrategy\": \"RENDER_PAGES_AT_PAGE_END\"}");
        ImageSink pages = new ImageSink();
        ImageSink ocr = new ImageSink();
        parser.setContentEnrichers(new CompositeContentEnricher(List.of(ocr)));
        context.set(Parser.class, new AutoDetectParser(pages));
        try (TikaInputStream tis = TikaInputStream.get(
                getResourceAsStream("/test-documents/" + TWO_PAGES))) {
            parser.parse(tis, new ToXMLContentHandler(), new Metadata(), context);
        }
        assertEquals(2, ocr.images.size(), "OCR_ONLY over the config's EXTRACT");
        assertEquals(pixelsAcross(36), ocr.images.get(0).width, "ocr.dpi over pages.render.dpi");
        assertEquals(2, pages.images.size(), "imageStrategy turned emission on");

        // and the other way: a request's NONE turns a config's RENDER_PAGES off
        parser.getPDFParserConfig().setImageStrategy(
                PDFParserConfig.IMAGE_STRATEGY.RENDER_PAGES_BEFORE_PARSE);
        context = new ParseContext();
        context.setJsonConfig("pdf-parser", "{\"imageStrategy\": \"NONE\"}");
        pages = new ImageSink();
        context.set(Parser.class, new AutoDetectParser(pages));
        try (TikaInputStream tis = TikaInputStream.get(
                getResourceAsStream("/test-documents/" + TWO_PAGES))) {
            parser.parse(tis, new ToXMLContentHandler(), new Metadata(), context);
        }
        assertEquals(0, pages.images.size());
    }

    @Test
    public void testJsonConfig() throws Exception {
        ParseContext context = new ParseContext();
        context.setJsonConfig("pdf-parser", "{\"pages\": {\"emit\": {\"enabled\": true,"
                + " \"render\": {\"dpi\": 36, \"imageType\": \"RGB\", \"imageFormat\": \"JPEG\","
                + " \"imageQuality\": 0.9, \"maxImagePixels\": -1}}}}");
        List<Metadata> metadataList = getRecursiveMetadata(TWO_PAGES, context);
        List<Metadata> renderings = new ArrayList<>();
        for (Metadata m : metadataList) {
            if (TikaCoreProperties.EmbeddedResourceType.RENDERING.name()
                    .equals(m.get(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE))) {
                renderings.add(m);
            }
        }
        assertEquals(2, renderings.size());
        assertEquals("image/jpeg", renderings.get(0).get(HttpHeaders.CONTENT_TYPE));
    }

    /** The top-level "pages" is the default the parser's own overlay refines. */
    @Test
    public void testContextPagesIsTheDefaultLayer() throws Exception {
        ParseContext context = new ParseContext();
        context.setJsonConfig("pages", "{\"emit\": {\"enabled\": true, \"render\": {\"dpi\": 36}}}");
        context.setJsonConfig("pdf-parser", "{\"pages\": {\"emit\": {\"maxPages\": 1,"
                + " \"render\": {\"imageType\": \"RGB\"}}}}");
        ImageSink pages = new ImageSink();
        context.set(Parser.class, new AutoDetectParser(pages));
        try (TikaInputStream tis = TikaInputStream.get(
                getResourceAsStream("/test-documents/" + TWO_PAGES))) {
            new PDFParser().parse(tis, new ToXMLContentHandler(), new Metadata(), context);
        }
        assertEquals(1, pages.images.size(), "the parser's maxPages");
        assertEquals(pixelsAcross(36), pages.images.get(0).width, "the context's dpi");
        assertEquals(3, pages.images.get(0).colorComponents, "the parser's imageType");
    }

    /** Parses with a PDFParser whose embedded image parser and (optional) recognizer record what they see. */
    private Metadata parse(PDFParserConfig config, ImageSink pages, ImageSink ocr)
            throws Exception {
        PDFParser parser = new PDFParser();
        if (ocr != null) {
            parser.setContentEnrichers(new CompositeContentEnricher(List.of(ocr)));
        }
        ParseContext context = new ParseContext();
        context.set(PDFParserConfig.class, config);
        context.set(Parser.class, new AutoDetectParser(pages));
        Metadata metadata = new Metadata();
        try (TikaInputStream tis = TikaInputStream.get(
                getResourceAsStream("/test-documents/" + TWO_PAGES))) {
            parser.parse(tis, new ToXMLContentHandler(), metadata, context);
        }
        return metadata;
    }

    private int renders(PDFParserConfig config) throws Exception {
        CountingRenderer renderer = new CountingRenderer();
        PDFParser parser = new PDFParser();
        parser.setRenderer(renderer);
        parser.setContentEnrichers(new CompositeContentEnricher(List.of(new ImageSink())));
        ParseContext context = new ParseContext();
        context.set(PDFParserConfig.class, config);
        context.set(Parser.class, new AutoDetectParser(new ImageSink()));
        try (TikaInputStream tis = TikaInputStream.get(
                getResourceAsStream("/test-documents/" + TWO_PAGES))) {
            parser.parse(tis, new ToXMLContentHandler(), new Metadata(), context);
        }
        return renderer.pages;
    }

    private int pixelsAcross(int dpi) throws IOException {
        try (InputStream is = getResourceAsStream("/test-documents/" + TWO_PAGES);
                PDDocument document = Loader.loadPDF(is.readAllBytes())) {
            // PDFBox floors the scaled page width
            PDPage page = document.getPage(0);
            return (int) Math.max(1, Math.floor(page.getMediaBox().getWidth() * dpi / 72f));
        }
    }

    private static final class Shape {
        final int width;
        final int height;
        final int colorComponents;

        Shape(BufferedImage image) {
            this.width = image.getWidth();
            this.height = image.getHeight();
            this.colorComponents = image.getColorModel().getNumColorComponents();
        }
    }

    /** Reads every image it is handed, as an embedded-document parser or as the OCR engine. */
    private static final class ImageSink implements Parser, TextRecognizer {
        private static final long serialVersionUID = 1L;
        final List<Shape> images = new ArrayList<>();

        @Override
        public boolean recognizesText(ParseContext context) {
            return true;
        }

        @Override
        public Set<MediaType> getSupportedTypes(ParseContext context) {
            return Set.of(MediaType.image("png"), MediaType.image("jpeg"));
        }

        @Override
        public void parse(TikaInputStream tis, ContentHandler handler, Metadata metadata,
                          ParseContext context) throws IOException, SAXException, TikaException {
            BufferedImage image = ImageIO.read(tis);
            assertNotNull(image, "not an image: " + metadata.get(HttpHeaders.CONTENT_TYPE));
            images.add(new Shape(image));
            XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
            xhtml.startDocument();
            xhtml.characters("seen");
            xhtml.endDocument();
        }
    }

    private static final class CountingRenderer extends PDFBoxRenderer {
        private static final long serialVersionUID = 1L;
        int pages;
        int withOpenDocument;

        @Override
        public RenderResults render(TikaInputStream tis, Metadata metadata,
                                    ParseContext parseContext, RenderRequest... requests)
                throws IOException, TikaException {
            if (tis.getOpenContainer() instanceof PDDocument) {
                withOpenDocument++;
            }
            return super.render(tis, metadata, parseContext, requests);
        }

        @Override
        protected RenderResult renderPage(PDFRenderer renderer, PDPage page, int id,
                                          int pageNumber, Metadata metadata,
                                          ParseContext parseContext) throws IOException {
            pages++;
            return super.renderPage(renderer, page, id, pageNumber, metadata, parseContext);
        }
    }
}
