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

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.xml.sax.ContentHandler;

import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.extractor.EmbeddedDocumentUtil;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.metadata.TikaPagedText;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.enricher.ContentEnrichers;
import org.apache.tika.parser.pages.PagesConfig;
import org.apache.tika.renderer.PageRangeRequest;
import org.apache.tika.renderer.RenderResult;
import org.apache.tika.renderer.RenderResults;
import org.apache.tika.renderer.RenderSettings;
import org.apache.tika.renderer.Renderer;
import org.apache.tika.renderer.pdf.pdfbox.PDDocumentRenderer;
import org.apache.tika.renderer.pdf.pdfbox.PDFRenderingState;

/**
 * Emits page renders as RENDERING embedded documents under {@code pages.emit}: at the end
 * of each page the writer walks, and at document end for any page within the budget whose
 * end was never reached, so emission does not depend on the text pass. The page's OCR render
 * is reused when the emitted image is the same; otherwise the page is rendered again with
 * the emitted settings.
 */
final class PageEmitter {

    private final PDDocument pdDocument;
    private final PagesConfig pages;
    private final Renderer renderer;
    private final Metadata metadata;
    private final ParseContext context;
    private final boolean enabled;
    private int emittedThrough = 0;

    PageEmitter(PDDocument pdDocument, PagesConfig pages, Renderer renderer, Metadata metadata,
                ParseContext context) {
        this.pdDocument = pdDocument;
        this.pages = pages;
        this.renderer = renderer;
        this.metadata = metadata;
        this.context = context;
        this.enabled = pages.getEmit().applies(metadata) && renderer != null;
    }

    /** Whether this 1-based page is emitted: on for this document and within {@code maxPages}. */
    boolean wants(int pageNo) {
        return enabled && pages.getEmit().withinBudget(pageNo);
    }

    /**
     * Emits the page, reusing {@code ocrRender} when the emitted image is that same image.
     * Failures are recorded on the PDF, never thrown: an unrenderable page is not a failed parse.
     */
    void emit(int pageNo, RenderResult ocrRender, ContentHandler handler) {
        if (!wants(pageNo)) {
            return;
        }
        emittedThrough = Math.max(emittedThrough, pageNo);
        // too small to hold anything: a policy skip, not a failure
        PDRectangle mediaBox = pdDocument.getPage(pageNo - 1).getMediaBox();
        if (pages.emittedRender().belowMinimum(mediaBox.getWidth(), mediaBox.getHeight())) {
            return;
        }
        try {
            if (ocrRender != null && ocrRender.getStatus() == RenderResult.STATUS.SUCCESS
                    && pages.emitsSameImage()) {
                emitShared(pageNo, ocrRender, handler);
                return;
            }
            try (RenderResults results = render(pageNo)) {
                for (RenderResult result : results.getResults()) {
                    emitResult(result, handler);
                }
            }
        } catch (SecurityException e) {
            throw e;
        } catch (Exception e) {
            EmbeddedDocumentUtil.recordException(e, metadata, context);
        }
    }

    /**
     * Emits every page within the budget past the last one emitted, bounded by the parse
     * budget: called once the writer is done, whether it finished or threw.
     */
    void emitRemaining(ContentHandler handler, int maxPages) {
        if (!enabled) {
            return;
        }
        int last = pdDocument.getNumberOfPages();
        if (maxPages > 0) {
            last = Math.min(last, maxPages);
        }
        for (int pageNo = emittedThrough + 1; pageNo <= last && wants(pageNo); pageNo++) {
            emit(pageNo, null, handler);
        }
    }

    private RenderResults render(int pageNo) throws IOException, TikaException {
        Metadata renderedMetadata = Metadata.newInstance(context);
        renderedMetadata.set(TikaCoreProperties.TYPE, PDFParser.MEDIA_TYPE.toString());
        PageRangeRequest request = new PageRangeRequest(pageNo, pageNo);
        RenderSettings outer = context.get(RenderSettings.class);
        context.set(RenderSettings.class, pages.emittedRender());
        try {
            if (renderer instanceof PDDocumentRenderer) {
                // the placeholder must stay open: it is the parser's own document
                TikaInputStream tis = TikaInputStream.getPlaceholder();
                tis.setOpenContainer(pdDocument);
                return renderer.render(tis, renderedMetadata, context, request);
            }
            PDFRenderingState state = context.get(PDFRenderingState.class);
            if (state == null) {
                throw new TikaException("no spooled document to render page " + pageNo + " from");
            }
            return renderer.render(state.getTikaInputStream(), renderedMetadata, context, request);
        } finally {
            context.set(RenderSettings.class, outer);
        }
    }

    /** The OCR render's file under the embedded document's own metadata, not the OCR step's. */
    private void emitShared(int pageNo, RenderResult ocrRender, ContentHandler handler)
            throws IOException, TikaException {
        Metadata embedded = Metadata.newInstance(context);
        embedded.set(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE,
                TikaCoreProperties.EmbeddedResourceType.RENDERING.name());
        embedded.set(TikaPagedText.PAGE_NUMBER, pageNo);
        String rotation = ocrRender.getMetadata().get(TikaPagedText.PAGE_ROTATION);
        if (rotation != null) {
            embedded.set(TikaPagedText.PAGE_ROTATION, rotation);
        }
        try (TikaInputStream source = ocrRender.getInputStream()) {
            if (!source.hasFile()) {
                throw new TikaException("OCR render of page " + pageNo + " is not a file");
            }
            try (TikaInputStream tis = TikaInputStream.get(source.getPath(), embedded)) {
                emit(tis, embedded, handler);
            }
        }
    }

    private void emitResult(RenderResult result, ContentHandler handler)
            throws IOException, TikaException {
        if (result.getStatus() != RenderResult.STATUS.SUCCESS) {
            PDFParser.carryRenderWarnings(result, metadata);
            return;
        }
        try (TikaInputStream tis = result.getInputStream()) {
            emit(tis, result.getMetadata(), handler);
        }
    }

    private void emit(TikaInputStream tis, Metadata embedded, ContentHandler handler)
            throws IOException, TikaException {
        EmbeddedDocumentExtractor extractor =
                EmbeddedDocumentUtil.getEmbeddedDocumentExtractor(context);
        if (!extractor.shouldParseEmbedded(embedded, context)) {
            return;
        }
        // the page step enriches the render itself; the embedded copy is bytes and metadata
        try (ContentEnrichers.Suspension suspension = ContentEnrichers.suspend(context)) {
            extractor.parseEmbedded(tis, handler, embedded, context, true);
        } catch (SecurityException e) {
            throw e;
        } catch (Exception e) {
            EmbeddedDocumentUtil.recordException(e, metadata, context);
        }
    }
}
