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
package org.apache.tika.renderer.pdf.pdfbox;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Locale;
import java.util.Set;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.RandomAccessRead;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.tools.imageio.ImageIOUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.annotation.TikaComponent;
import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentUtil;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Property;
import org.apache.tika.metadata.Rendering;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.metadata.TikaPagedText;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.pdf.PDFParser;
import org.apache.tika.parser.pdf.PDFRandomAccess;
import org.apache.tika.renderer.ImageFormat;
import org.apache.tika.renderer.PageBasedRenderResults;
import org.apache.tika.renderer.PageRangeRequest;
import org.apache.tika.renderer.RenderRequest;
import org.apache.tika.renderer.RenderResult;
import org.apache.tika.renderer.RenderResults;
import org.apache.tika.renderer.RenderSettings;
import org.apache.tika.renderer.RenderingTracker;

@TikaComponent(name = "pdfbox-renderer")
public class PDFBoxRenderer implements PDDocumentRenderer {

    Set<MediaType> SUPPORTED_TYPES = Collections.singleton(PDFParser.MEDIA_TYPE);

    protected static final Logger LOG = LoggerFactory.getLogger(PDFBoxRenderer.class);

    /**
     * This is the amount of time it takes for PDFBox to render the page
     * to a BufferedImage
     *
     * @see Rendering#PDFBOX_RENDERING_TIME_MS the curated Property this aliases; {@code tk:}
     * Properties mint only in org.apache.tika.metadata (see Property mint-time validation)
     */
    public static final Property PDFBOX_RENDERING_TIME_MS = Rendering.PDFBOX_RENDERING_TIME_MS;

    /**
     * This is the amount of time it takes for PDFBox/java to write the image after
     * it has been rendered into a BufferedImage.  Some formats take much longer
     * to encode than others.
     *
     * @see Rendering#PDFBOX_IMAGE_WRITING_TIME_MS the curated Property this aliases
     */
    public static final Property PDFBOX_IMAGE_WRITING_TIME_MS =
            Rendering.PDFBOX_IMAGE_WRITING_TIME_MS;

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    /** What this renderer draws when the parse scopes no settings: the entry's own. */
    private final RenderSettings defaults = RenderSettings.defaults();

    /** PDFBox's image type for a core one. */
    public static ImageType toPdfBox(org.apache.tika.renderer.ImageType imageType) {
        return imageType == org.apache.tika.renderer.ImageType.RGB ? ImageType.RGB : ImageType.GRAY;
    }


    @Override
    public RenderResults render(TikaInputStream tis, Metadata metadata, ParseContext parseContext,
                                RenderRequest... requests) throws IOException, TikaException {


        PDDocument pdDocument;
        boolean mustClose = false;
        if (tis.getOpenContainer() != null) {
            pdDocument = (PDDocument) tis.getOpenContainer();
        } else {
            // a fresh reader from byte 0 each time, so per-page renders do not depend on
            // where the stream was left; the document closes it -- but only once it exists,
            // so a failed load must close the reader itself or its channel pin (and the
            // budget behind it) leaks for the life of the stream
            RandomAccessRead ra = PDFRandomAccess.open(tis, parseContext);
            try {
                pdDocument = Loader.loadPDF(ra);
            } catch (IOException | RuntimeException e) {
                try {
                    ra.close();
                } catch (IOException closeFailure) {
                    e.addSuppressed(closeFailure);
                }
                throw e;
            }
            mustClose = true;
        }
        PageBasedRenderResults results = new PageBasedRenderResults(new TemporaryResources());
        try {
            for (RenderRequest renderRequest : requests) {
                processRequest(renderRequest, pdDocument, metadata, parseContext, results);
            }
        } catch (Throwable t) {
            // results never reach the caller; nothing else would delete the pages
            try {
                results.close();
            } catch (IOException e) {
                t.addSuppressed(e);
            }
            throw t;
        } finally {
            if (mustClose) {
                pdDocument.close();
            }
        }
        return results;
    }

    private void processRequest(RenderRequest renderRequest, PDDocument pdDocument,
                                Metadata metadata, ParseContext parseContext,
                                PageBasedRenderResults results) {
        if (renderRequest == PageRangeRequest.RENDER_ALL || renderRequest.equals(PageRangeRequest.RENDER_ALL)) {
            renderRange(pdDocument, 1, pdDocument.getNumberOfPages(),
                    metadata, parseContext, results);
        } else if (renderRequest instanceof PageRangeRequest) {
            int start = ((PageRangeRequest)renderRequest).getFrom();
            int toInclusive = ((PageRangeRequest)renderRequest).getTo();
            int numberOfPages = pdDocument.getNumberOfPages();
            // a range that runs past the last page ends there: "the first N pages" of a
            // shorter document are all of its pages. A range that starts past the last
            // page still asks for a page that does not exist, and getPage throws on it.
            if (start <= numberOfPages) {
                toInclusive = Math.min(toInclusive, numberOfPages);
            }
            renderRange(pdDocument, start, toInclusive, metadata, parseContext, results);
        }
    }

    private void renderRange(PDDocument pdDocument, int start, int endInclusive, Metadata metadata,
                             ParseContext parseContext, PageBasedRenderResults results) {
        PDFRenderer renderer = new PDFRenderer(pdDocument);
        RenderingTracker tracker = parseContext.get(RenderingTracker.class);
        if (tracker == null) {
            tracker = new RenderingTracker();
            parseContext.set(RenderingTracker.class, tracker);
        }
        for (int i = start; i <= endInclusive; i++) {
            int id = tracker.getNextId();
            Metadata m = Metadata.newInstance(parseContext);
            m.set(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE,
                    TikaCoreProperties.EmbeddedResourceType.RENDERING.name());
            try {
                m.set(TikaPagedText.PAGE_NUMBER, i);
                m.set(TikaPagedText.PAGE_ROTATION, (double)pdDocument.getPage(i - 1).getRotation());
                results.add(renderPage(renderer, pdDocument.getPage(i - 1), id, i, m,
                        parseContext));
            } catch (IOException e) {
                EmbeddedDocumentUtil.recordException(e, m, parseContext);
                results.add(new RenderResult(RenderResult.STATUS.EXCEPTION, id, null, m));
            }
        }
    }

    protected RenderResult renderPage(PDFRenderer renderer, PDPage page, int id, int pageNumber,
                                      Metadata metadata, ParseContext parseContext)
            throws IOException {
        // the minimum is the parser's policy, applied before it asks; the cap is a safety net
        RenderSettings settings = settings(parseContext);
        PDRectangle mediaBox = page.getMediaBox();
        double width = mediaBox.getWidth();
        double height = mediaBox.getHeight();
        float dpi = settings.effectiveDpi(width, height);
        long estPixels = settings.estimatedPixels(width, height);
        if (settings.exceedsMaxPixels(estPixels)) {
            throw new IOException("page " + pageNumber + " would render to " + estPixels
                    + " pixels at " + dpi + " dpi, above maxImagePixels "
                    + settings.getMaxImagePixels());
        }
        String formatName = settings.getImageFormat().getFormatName();
        Path tmpFile = Files.createTempFile("tika-pdfbox-rendering-",
                "-" + id + "-" + pageNumber + "." + formatName);
        try {
            long start = System.currentTimeMillis();
            //TODO: parameterize whether or not to un-rotate page?
            BufferedImage image = renderer.renderImageWithDPI(
                    pageNumber - 1, dpi, toPdfBox(settings.getImageType()));
            long renderingElapsed = System.currentTimeMillis() - start;
            metadata.set(PDFBOX_RENDERING_TIME_MS, renderingElapsed);
            start = System.currentTimeMillis();
            try (OutputStream os = Files.newOutputStream(tmpFile)) {
                ImageIOUtil.writeImage(image, formatName, os, Math.round(dpi),
                        settings.getImageQuality());
            }
            long elapsedWrite = System.currentTimeMillis() - start;
            metadata.set(PDFBOX_IMAGE_WRITING_TIME_MS, elapsedWrite);
            metadata.set(Rendering.RENDERED_MS, renderingElapsed + elapsedWrite);
        } catch (SecurityException e) {
            //throw SecurityExceptions immediately
            throw e;
        } catch (Exception e) {
            try {
                Files.delete(tmpFile);
            } catch (IOException ex) {
                LOG.warn("couldn't delete " + tmpFile, ex);
            }
            throw new IOException(e);
        }
        return new RenderResult(RenderResult.STATUS.SUCCESS, id, tmpFile, metadata);
    }

    public void setDPI(int dpi) {
        defaults.setDpi(dpi);
    }

    public void setImageType(ImageType imageType) {
        defaults.setImageType(imageType == ImageType.RGB
                ? org.apache.tika.renderer.ImageType.RGB : org.apache.tika.renderer.ImageType.GRAY);
    }

    public void setImageFormatName(String imageFormatName) {
        defaults.setImageFormat(ImageFormat.valueOf(imageFormatName.toUpperCase(Locale.ROOT)));
    }

    public void setImageQuality(float imageQuality) {
        defaults.setImageQuality(imageQuality);
    }

    public void setMaxImagePixels(long maxImagePixels) {
        defaults.setMaxImagePixels(maxImagePixels);
    }

    /** The settings the parser scoped around this render, else this renderer's own. */
    private RenderSettings settings(ParseContext parseContext) {
        RenderSettings scoped = parseContext.get(RenderSettings.class);
        return scoped == null ? defaults : defaults.over(scoped);
    }
}
