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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javax.imageio.ImageIO;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import org.apache.tika.TikaTest;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.enricher.CompositeContentEnricher;
import org.apache.tika.parser.enricher.TextRecognizer;
import org.apache.tika.renderer.RenderResult;
import org.apache.tika.renderer.pdf.pdfbox.PDFBoxRenderer;
import org.apache.tika.sax.ToXMLContentHandler;
import org.apache.tika.sax.XHTMLContentHandler;

/**
 * {@code "rendering"} sets the page images the parser emits; OCR keeps rendering with the
 * {@code ocr} settings, and the two share one render only when it would be the same image.
 */
public class PDFRenderingConfigTest extends TikaTest {

    private static final String TWO_PAGES = "testPDF_bookmarks.pdf";

    @ParameterizedTest
    @EnumSource(value = PDFParserConfig.IMAGE_STRATEGY.class,
            names = {"RENDER_PAGES_BEFORE_PARSE", "RENDER_PAGES_AT_PAGE_END"})
    public void testPageImageUsesRenderingBlock(PDFParserConfig.IMAGE_STRATEGY strategy)
            throws Exception {
        PDFParserConfig config = new PDFParserConfig();
        config.setImageStrategy(strategy);
        config.getRendering().setDpi(36);
        config.getRendering().setImageType(OcrConfig.ImageType.RGB);
        ImageSink pages = new ImageSink();

        parse(config, pages, null);

        assertEquals(2, pages.images.size());
        for (Shape page : pages.images) {
            assertEquals(pixelsAcross(36), page.width, "36 dpi, not ocr's 300");
            assertEquals(3, page.colorComponents, "RGB, not ocr's GRAY");
        }
    }

    @ParameterizedTest
    @EnumSource(value = PDFParserConfig.IMAGE_STRATEGY.class,
            names = {"RENDER_PAGES_BEFORE_PARSE", "RENDER_PAGES_AT_PAGE_END"})
    public void testNoBlockFollowsOcr(PDFParserConfig.IMAGE_STRATEGY strategy) throws Exception {
        PDFParserConfig config = new PDFParserConfig();
        config.setImageStrategy(strategy);
        config.getOcr().setDpi(36);
        ImageSink pages = new ImageSink();

        parse(config, pages, null);

        assertEquals(2, pages.images.size());
        for (Shape page : pages.images) {
            assertEquals(pixelsAcross(36), page.width, "the 4.0 behaviour: ocr's dpi");
            assertEquals(1, page.colorComponents, "the 4.0 behaviour: ocr's GRAY");
        }
    }

    @ParameterizedTest
    @EnumSource(value = PDFParserConfig.IMAGE_STRATEGY.class,
            names = {"RENDER_PAGES_BEFORE_PARSE", "RENDER_PAGES_AT_PAGE_END"})
    public void testOcrKeepsItsOwnSettings(PDFParserConfig.IMAGE_STRATEGY strategy)
            throws Exception {
        PDFParserConfig config = new PDFParserConfig();
        config.setImageStrategy(strategy);
        // OCR-only text goes through OCR2XHTML, which emits no page images at page end
        config.setText(PDFParserConfig.TextPolicy.EXTRACT_AND_OCR);
        config.getOcr().setDpi(72);
        config.getRendering().setDpi(36);
        config.getRendering().setImageType(OcrConfig.ImageType.RGB);
        ImageSink pages = new ImageSink();
        ImageSink ocr = new ImageSink();

        parse(config, pages, ocr);

        assertEquals(2, ocr.images.size(), "the recognizer saw each page");
        for (Shape page : ocr.images) {
            assertEquals(pixelsAcross(72), page.width, "OCR renders at ocr.dpi");
            assertEquals(1, page.colorComponents, "OCR renders in ocr.imageType");
        }
        assertEquals(2, pages.images.size(), "each page was emitted");
        for (Shape page : pages.images) {
            assertEquals(pixelsAcross(36), page.width);
            assertEquals(3, page.colorComponents);
        }
    }

    @Test
    public void testBeforeParseRenderServesOcrOnlyWhenItIsTheSameImage() throws Exception {
        PDFParserConfig same = new PDFParserConfig();
        same.setImageStrategy(PDFParserConfig.IMAGE_STRATEGY.RENDER_PAGES_BEFORE_PARSE);
        same.setText(PDFParserConfig.TextPolicy.EXTRACT_AND_OCR);
        same.getOcr().setDpi(36);
        assertEquals(2, renders(same), "one render per page feeds both the output and OCR");

        PDFParserConfig different = new PDFParserConfig();
        different.setImageStrategy(PDFParserConfig.IMAGE_STRATEGY.RENDER_PAGES_BEFORE_PARSE);
        different.setText(PDFParserConfig.TextPolicy.EXTRACT_AND_OCR);
        different.getOcr().setDpi(36);
        different.getRendering().setImageType(OcrConfig.ImageType.RGB);
        assertEquals(4, renders(different), "OCR renders again rather than read the RGB page");
    }

    @ParameterizedTest
    @EnumSource(value = PDFParserConfig.IMAGE_STRATEGY.class,
            names = {"RENDER_PAGES_BEFORE_PARSE", "RENDER_PAGES_AT_PAGE_END"})
    public void testMaxImagePixelsSkipsThePageWithAWarning(
            PDFParserConfig.IMAGE_STRATEGY strategy) throws Exception {
        PDFParserConfig config = new PDFParserConfig();
        config.setImageStrategy(strategy);
        config.getRendering().setDpi(36);
        config.getRendering().setMaxImagePixels(1000L);
        ImageSink pages = new ImageSink();

        Metadata metadata = parse(config, pages, null);

        assertEquals(0, pages.images.size(), "no page fits in 1000 pixels");
        String[] warnings = metadata.getValues(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING);
        assertEquals(2, warnings.length, "one warning per skipped page");
        assertTrue(warnings[0].contains("maxImagePixels"), warnings[0]);
    }

    @Test
    public void testJsonConfig() throws Exception {
        ParseContext context = new ParseContext();
        context.setJsonConfig("pdf-parser", "{\"imageStrategy\": \"RENDER_PAGES_AT_PAGE_END\","
                + " \"rendering\": {\"dpi\": 36, \"imageType\": \"RGB\", \"imageFormat\": \"JPEG\","
                + " \"imageQuality\": 0.9, \"maxImagePixels\": -1}}");
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

    @Test
    public void testValidation() {
        RenderingConfig config = new RenderingConfig();
        assertThrows(IllegalArgumentException.class, () -> config.setDpi(0));
        assertThrows(IllegalArgumentException.class, () -> config.setImageQuality(1.5f));
        assertThrows(IllegalArgumentException.class, () -> config.setMaxImagePixels(0L));
        config.setDpi(null);
        config.setMaxImagePixels(-1L);
    }

    @Test
    public void testResolveFallsBackPerField() {
        OcrConfig ocr = new OcrConfig();
        ocr.setDpi(150);
        RenderingConfig block = new RenderingConfig();
        block.setImageType(OcrConfig.ImageType.RGB);
        RenderingConfig resolved = block.resolve(ocr);
        assertEquals(150, resolved.getDpi());
        assertEquals(OcrConfig.ImageType.RGB, resolved.getImageType());
        assertEquals(ocr.getImageFormat(), resolved.getImageFormat());
        assertTrue(new RenderingConfig().resolve(ocr).rendersSameImageAs(RenderingConfig.from(ocr)));
        assertTrue(!resolved.rendersSameImageAs(RenderingConfig.from(ocr)));
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
        final int colorComponents;

        Shape(BufferedImage image) {
            this.width = image.getWidth();
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

        @Override
        protected RenderResult renderPage(PDFRenderer renderer, PDPage page, int id,
                                          int pageNumber, Metadata metadata,
                                          ParseContext parseContext) throws IOException {
            pages++;
            return super.renderPage(renderer, page, id, pageNumber, metadata, parseContext);
        }
    }
}
