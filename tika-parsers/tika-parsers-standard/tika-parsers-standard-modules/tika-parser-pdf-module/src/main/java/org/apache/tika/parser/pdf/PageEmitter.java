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
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import org.apache.tika.exception.EmbeddedLimitReachedException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.exception.WriteLimitReachedException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.extractor.EmbeddedDocumentUtil;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.HttpHeaders;
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
import org.apache.tika.renderer.pdf.pdfbox.PDFBoxRenderer;
import org.apache.tika.renderer.pdf.pdfbox.PDFRenderingState;

/**
 * One parse's page renders: draws a page through the configured engine, and emits renders as
 * RENDERING embedded documents under {@code pages.emit} at the end of each page the writer
 * walks. The OCR render is reused when the emitted image is the same; otherwise the page is
 * rendered again with the emitted settings.
 */
final class PageEmitter {

    private final PDDocument pdDocument;
    private final PagesConfig pages;
    private final Renderer renderer;
    private final Metadata metadata;
    private final ParseContext context;
    private final boolean enabled;
    private int startedThrough = 0;
    private int emittedThrough = 0;

    PageEmitter(PDDocument pdDocument, PagesConfig pages, Renderer renderer, Metadata metadata,
                ParseContext context) {
        this.pdDocument = pdDocument;
        this.pages = pages;
        this.renderer = renderer;
        this.metadata = metadata;
        this.context = context;
        this.enabled = pages.getEmit().applies(metadata, context);
    }

    /**
     * The page drawn by the engine under these settings, OCR-only strategies through PDFBox.
     * The engine gets the open document and, when spooled, the file: each takes what it reads.
     */
    RenderResults render(int pageNo, RenderSettings settings, OcrConfig.RenderingStrategy strategy,
                         Metadata pageMetadata) throws IOException, TikaException {
        PDFRenderingState state = context.get(PDFRenderingState.class);
        TikaInputStream tis = state == null ? TikaInputStream.getPlaceholder()
                : state.getTikaInputStream();
        Object container = tis.getOpenContainer();
        RenderSettings outer = context.get(RenderSettings.class);
        Renderer engine = strategy == OcrConfig.RenderingStrategy.ALL ? renderer : pdfBox();
        tis.setOpenContainer(pdDocument);
        context.set(RenderSettings.class, settings);
        context.set(OcrConfig.RenderingStrategy.class, strategy);
        try {
            return engine.render(tis, pageMetadata, context, new PageRangeRequest(pageNo, pageNo));
        } finally {
            context.set(OcrConfig.RenderingStrategy.class, null);
            context.set(RenderSettings.class, outer);
            tis.setOpenContainer(container);
        }
    }

    private Renderer pdfBox() {
        return renderer instanceof PDFBoxRenderer ? renderer : new PDFBoxRenderer();
    }

    /** The writer started this 1-based page: the one in flight if it never ends. */
    void started(int pageNo) {
        startedThrough = pageNo;
    }

    /** Whether this 1-based page is emitted: on for this document and within {@code maxPages}. */
    boolean wants(int pageNo) {
        return enabled && pages.getEmit().withinBudget(pageNo);
    }

    /**
     * {@link #wants}, and the embedded-document extractor accepts what the child will be: asked
     * before anything is drawn, so a selector that refuses page images costs no render.
     */
    boolean accepts(int pageNo) {
        return wants(pageNo) && extractor().shouldParseEmbedded(probe(pageNo), context);
    }

    /**
     * Emits the page: {@code shared} when it is the OCR render of the same image (a failed one
     * was already reported, nothing is emitted), else a render with the emitted settings.
     * Failures are recorded on the PDF, never thrown: an unrenderable page is not a failed parse.
     * A caller passing {@code shared} has asked {@link #accepts} already.
     */
    void emit(int pageNo, RenderResult shared, ContentHandler handler) throws SAXException {
        if (!wants(pageNo)) {
            return;
        }
        emittedThrough = Math.max(emittedThrough, pageNo);
        try {
            if (shared == null && !accepts(pageNo)) {
                return;
            }
            if (shared != null) {
                if (shared.getStatus() == RenderResult.STATUS.SUCCESS) {
                    emitShared(pageNo, shared, handler);
                }
                return;
            }
            // too small to hold anything: a policy skip, not a failure
            double[] size = PDFBoxRenderer.pageSize(pdDocument.getPage(pageNo - 1));
            if (pages.emittedRender().belowMinimum(size[0], size[1])) {
                return;
            }
            Metadata pageMetadata = Metadata.newInstance(context);
            pageMetadata.set(TikaCoreProperties.TYPE, PDFParser.MEDIA_TYPE.toString());
            try (RenderResults results = render(pageNo, pages.emittedRender(),
                    OcrConfig.RenderingStrategy.ALL, pageMetadata)) {
                for (RenderResult result : results.getResults()) {
                    if (result.getStatus() == RenderResult.STATUS.SUCCESS) {
                        try (TikaInputStream tis = result.getInputStream()) {
                            emit(tis, result.getMetadata(), handler);
                        }
                    } else {
                        PDFParser.carryRenderWarnings(result, metadata);
                    }
                }
            }
        } catch (SecurityException | EmbeddedLimitReachedException e) {
            throw e;
        } catch (Exception e) {
            if (WriteLimitReachedException.isWriteLimitReached(e)) {
                throw e instanceof SAXException sax ? sax : new SAXException(e);
            }
            EmbeddedDocumentUtil.recordException(e, metadata, context);
        }
    }

    /**
     * The page in flight when the writer died: started, never ended. Nothing when the caller
     * asked to stop; what it throws rides on the failure.
     */
    void emitUnfinished(ContentHandler handler, Throwable failure) {
        if (failure == null || failure instanceof SecurityException
                || failure instanceof EmbeddedLimitReachedException
                || WriteLimitReachedException.isWriteLimitReached(failure)
                || startedThrough <= emittedThrough) {
            return;
        }
        try {
            emit(startedThrough, null, handler);
        } catch (SecurityException e) {
            throw e;
        } catch (Exception e) {
            failure.addSuppressed(e);
        }
    }

    /** Every page within the budgets, for a writer that walks none. */
    void emitAll(ContentHandler handler, int maxPages) throws SAXException {
        int last = pdDocument.getNumberOfPages();
        if (maxPages > 0) {
            last = Math.min(last, maxPages);
        }
        for (int pageNo = 1; pageNo <= last && wants(pageNo); pageNo++) {
            emit(pageNo, null, handler);
        }
    }

    /** The OCR render's file under the embedded document's own metadata, not the OCR step's. */
    private void emitShared(int pageNo, RenderResult ocrRender, ContentHandler handler)
            throws IOException, SAXException {
        Metadata embedded = Metadata.newInstance(context);
        embedded.set(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE,
                TikaCoreProperties.EmbeddedResourceType.RENDERING.name());
        embedded.set(TikaPagedText.PAGE_NUMBER, pageNo);
        String rotation = ocrRender.getMetadata().get(TikaPagedText.PAGE_ROTATION);
        if (rotation != null) {
            embedded.set(TikaPagedText.PAGE_ROTATION, rotation);
        }
        try (TikaInputStream source = ocrRender.getInputStream();
                TikaInputStream tis = TikaInputStream.get(source.getPath(), embedded)) {
            emit(tis, embedded, handler);
        }
    }

    private void emit(TikaInputStream tis, Metadata embedded, ContentHandler handler)
            throws IOException, SAXException {
        // the page step enriches the render itself; the embedded copy is bytes and metadata
        try (ContentEnrichers.Suspension suspension = ContentEnrichers.suspend(context)) {
            extractor().parseEmbedded(tis, handler, embedded, context, false);
        }
    }

    private EmbeddedDocumentExtractor extractor() {
        return EmbeddedDocumentUtil.getEmbeddedDocumentExtractor(context);
    }

    /** What the child will be, for a selector to accept or refuse before the render. */
    private Metadata probe(int pageNo) {
        Metadata probe = Metadata.newInstance(context);
        probe.set(TikaCoreProperties.TYPE, PDFParser.MEDIA_TYPE.toString());
        probe.set(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE,
                TikaCoreProperties.EmbeddedResourceType.RENDERING.name());
        probe.set(TikaPagedText.PAGE_NUMBER, pageNo);
        probe.set(HttpHeaders.CONTENT_TYPE,
                "image/" + pages.emittedRender().getImageFormat().getFormatName());
        return probe;
    }
}
