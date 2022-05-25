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

import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.io.IOExceptionWithCause;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSStream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.apache.pdfbox.util.Matrix;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.pdf.image.ImageGraphicsEngine;
import org.apache.tika.renderer.PageRangeRequest;
import org.apache.tika.renderer.RenderRequest;
import org.apache.tika.renderer.RenderResult;
import org.apache.tika.renderer.RenderResults;
import org.apache.tika.renderer.Renderer;
import org.apache.tika.renderer.pdf.pdfbox.PDFRenderingState;

/**
 * Utility class that overrides the {@link PDFTextStripper} functionality
 * to produce a semi-structured XHTML SAX events instead of a plain text
 * stream.
 */
class PDF2XHTML extends AbstractPDF2XHTML {


    /**
     * This keeps track of the pdf object ids for inline
     * images that have been processed.
     * If {@link PDFParserConfig#isExtractUniqueInlineImagesOnly()
     * is true, this will be checked before extracting an embedded image.
     * The integer keeps track of the inlineImageCounter for that image.
     * This integer is used to identify images in the markup.
     * <p>
     * This is used across the document.  To avoid infinite recursion
     * TIKA-1742, we're limiting the export to one image per page.
     */
    private Map<COSStream, Integer> processedInlineImages = new HashMap<>();
    private AtomicInteger inlineImageCounter = new AtomicInteger(0);

    PDF2XHTML(PDDocument document, ContentHandler handler, ParseContext context, Metadata metadata,
              PDFParserConfig config) throws IOException {
        super(document, handler, context, metadata, config);
    }

    /**
     * Converts the given PDF document (and related metadata) to a stream
     * of XHTML SAX events sent to the given content handler.
     *
     * @param document PDF document
     * @param handler  SAX content handler
     * @param metadata PDF metadata
     * @throws SAXException  if the content handler fails to process SAX events
     * @throws TikaException if there was an exception outside of per page processing
     */
    public static void process(PDDocument document, ContentHandler handler, ParseContext context,
                               Metadata metadata, PDFParserConfig config)
            throws SAXException, TikaException {
        PDF2XHTML pdf2XHTML = null;
        try {
            // Extract text using a dummy Writer as we override the
            // key methods to output to the given content
            // handler.
            if (config.isDetectAngles()) {
                pdf2XHTML =
                        new AngleDetectingPDF2XHTML(document, handler, context, metadata, config);
            } else {
                pdf2XHTML = new PDF2XHTML(document, handler, context, metadata, config);
            }
            config.configure(pdf2XHTML);

            pdf2XHTML.writeText(document, new Writer() {
                @Override
                public void write(char[] cbuf, int off, int len) {
                }

                @Override
                public void flush() {
                }

                @Override
                public void close() {
                }
            });
        } catch (IOException e) {
            if (e.getCause() instanceof SAXException) {
                throw (SAXException) e.getCause();
            } else {
                throw new TikaException("Unable to extract PDF content", e);
            }
        }
        if (pdf2XHTML.exceptions.size() > 0) {
            //throw the first
            throw new TikaException("Unable to extract PDF content", pdf2XHTML.exceptions.get(0));
        }
    }

    @Override
    public void processPage(PDPage page) throws IOException {
        try {
            super.processPage(page);
        } catch (IOException e) {
            handleCatchableIOE(e);
            endPage(page);
        }
    }

    @Override
    protected void endPage(PDPage page) throws IOException {
        try {
            writeParagraphEnd();
            try {
                extractImages(page);
                renderPage(page);
            } catch (IOException e) {
                handleCatchableIOE(e);
            }
            super.endPage(page);
        } catch (SAXException e) {
            throw new IOException("Unable to end a page", e);
        } catch (IOException e) {
            handleCatchableIOE(e);
        }
    }

    private void renderPage(PDPage page) throws IOException {
        if (config.getImageStrategy() != PDFParserConfig.IMAGE_STRATEGY.RENDER_PAGES_AT_PAGE_END) {
            return;
        }
        PDFRenderingState state = context.get(PDFRenderingState.class);
        //this is the document's inputstream/PDDocument
        //TODO: figure out if we can send in the PDPage in the TikaInputStream
        TikaInputStream tis = state.getTikaInputStream();
        Renderer renderer = config.getRenderer();
        RenderRequest request = new PageRangeRequest(getCurrentPageNo(), getCurrentPageNo());
        Metadata renderedMetadata = new Metadata();
        renderedMetadata.set(TikaCoreProperties.TYPE, PDFParser.MEDIA_TYPE.toString());
        try (RenderResults results = renderer.render(tis, renderedMetadata, context, request)) {
            for (RenderResult result : results.getResults()) {
                if (result.getStatus() == RenderResult.STATUS.SUCCESS) {
                    if (embeddedDocumentExtractor.shouldParseEmbedded(result.getMetadata())) {

                        try (InputStream is = result.getInputStream()) {
                            //TODO: add markup here?
                            embeddedDocumentExtractor.parseEmbedded(is, xhtml,
                                    result.getMetadata(), true);
                        }
                    }
                }
            }
        } catch (SecurityException e) {
            throw e;
        } catch (Exception e) {
            handleCatchableIOE(new IOExceptionWithCause(e));
        }
    }

    void extractImages(PDPage page) throws SAXException, IOException {
        if (config.isExtractInlineImages() == false &&
                config.isExtractInlineImageMetadataOnly() == false) {
            return;
        }
        //TODO: modernize to ImageStratey != rawImages
        ImageGraphicsEngine engine =
                config.getImageGraphicsEngineFactory().newEngine(
                        page, getCurrentPageNo(), embeddedDocumentExtractor, config,
                        processedInlineImages, inlineImageCounter, xhtml, metadata, context);
        engine.run();
        List<IOException> engineExceptions = engine.getExceptions();
        if (engineExceptions.size() > 0) {
            IOException first = engineExceptions.remove(0);
            if (config.isCatchIntermediateIOExceptions()) {
                exceptions.addAll(engineExceptions);
            }
            throw first;
        }
    }

    @Override
    protected void writeParagraphStart() throws IOException {
        super.writeParagraphStart();
        try {
            xhtml.startElement("p");
        } catch (SAXException e) {
            throw new IOException("Unable to start a paragraph", e);
        }
    }

    @Override
    protected void writeParagraphEnd() throws IOException {
        super.writeParagraphEnd();
        try {
            xhtml.endElement("p");
        } catch (SAXException e) {
            throw new IOException("Unable to end a paragraph", e);
        }
    }

    @Override
    protected void writeString(String text) throws IOException {
        try {
            xhtml.characters(text);
        } catch (SAXException e) {
            throw new IOException("Unable to write a string: " + text, e);
        }
    }

    @Override
    protected void writeCharacters(TextPosition text) throws IOException {
        try {
            xhtml.characters(text.getUnicode());
        } catch (SAXException e) {
            throw new IOException("Unable to write a character: " + text.getUnicode(), e);
        }
    }

    @Override
    protected void writeWordSeparator() throws IOException {
        try {
            xhtml.characters(getWordSeparator());
        } catch (SAXException e) {
            throw new IOException("Unable to write a space character", e);
        }
    }

    @Override
    protected void writeLineSeparator() throws IOException {
        try {
            xhtml.newline();
        } catch (SAXException e) {
            throw new IOException("Unable to write a newline character", e);
        }
    }

    private static class AngleDetectingPDF2XHTML extends PDF2XHTML {

        private AngleDetectingPDF2XHTML(PDDocument document, ContentHandler handler,
                                        ParseContext context, Metadata metadata,
                                        PDFParserConfig config) throws IOException {
            super(document, handler, context, metadata, config);
        }

        @Override
        protected void startPage(PDPage page) throws IOException {
            //no-op
        }

        @Override
        protected void endPage(PDPage page) throws IOException {
            //no-op
        }

        @Override
        public void processPage(PDPage page) throws IOException {
            try {
                super.startPage(page);
                detectAnglesAndProcessPage(page);
            } catch (IOException e) {
                handleCatchableIOE(e);
            } finally {
                super.endPage(page);
            }
        }

        private void detectAnglesAndProcessPage(PDPage page) throws IOException {
            //copied and pasted from https://issues.apache.org/jira/secure/attachment/12947452/ExtractAngledText.java
            //PDFBOX-4371
            AngleCollector angleCollector = new AngleCollector(); // alternatively, reset angles
            angleCollector.setStartPage(getCurrentPageNo());
            angleCollector.setEndPage(getCurrentPageNo());
            angleCollector.getText(document);

            int rotation = page.getRotation();
            page.setRotation(0);

            for (Integer angle : angleCollector.getAngles()) {
                if (angle == 0) {
                    try {
                        super.processPage(page);
                    } catch (IOException e) {
                        handleCatchableIOE(e);
                    }
                } else {
                    // prepend a transformation
                    try (PDPageContentStream cs = new PDPageContentStream(document, page,
                            PDPageContentStream.AppendMode.PREPEND, false)) {
                        cs.transform(Matrix.getRotateInstance(-Math.toRadians(angle), 0, 0));
                    }

                    try {
                        super.processPage(page);
                    } catch (IOException e) {
                        handleCatchableIOE(e);
                    }

                    // remove transformation
                    COSArray contents = (COSArray) page.getCOSObject().getItem(COSName.CONTENTS);
                    contents.remove(0);
                }
            }
            page.setRotation(rotation);
        }

        @Override
        protected void processTextPosition(TextPosition text) {
            Matrix m = text.getTextMatrix();
            m.concatenate(text.getFont().getFontMatrix());
            int angle = (int) Math.round(Math.toDegrees(Math.atan2(m.getShearY(), m.getScaleY())));
            if (angle == 0) {
                super.processTextPosition(text);
            }
        }
    }

    static class AngleCollector extends PDFTextStripper {
        Set<Integer> angles = new HashSet<>();

        /**
         * Instantiate a new PDFTextStripper object.
         *
         * @throws IOException If there is an error loading the properties.
         */
        AngleCollector() throws IOException {
        }

        public Set<Integer> getAngles() {
            return angles;
        }

        @Override
        protected void processTextPosition(TextPosition text) {
            Matrix m = text.getTextMatrix();
            m.concatenate(text.getFont().getFontMatrix());
            int angle = (int) Math.round(Math.toDegrees(Math.atan2(m.getShearY(), m.getScaleY())));
            angle = (angle + 360) % 360;
            angles.add(angle);
        }
    }
}

