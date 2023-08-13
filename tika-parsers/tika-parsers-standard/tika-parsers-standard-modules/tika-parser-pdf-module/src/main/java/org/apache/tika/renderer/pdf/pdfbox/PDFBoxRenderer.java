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
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.tools.imageio.ImageIOUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.config.Initializable;
import org.apache.tika.config.InitializableProblemHandler;
import org.apache.tika.config.Param;
import org.apache.tika.exception.TikaConfigException;
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
import org.apache.tika.parser.pdf.PDFParserConfig;
import org.apache.tika.renderer.PageBasedRenderResults;
import org.apache.tika.renderer.PageRangeRequest;
import org.apache.tika.renderer.RenderRequest;
import org.apache.tika.renderer.RenderResult;
import org.apache.tika.renderer.RenderResults;
import org.apache.tika.renderer.RenderingTracker;

public class PDFBoxRenderer implements PDDocumentRenderer, Initializable {

    Set<MediaType> SUPPORTED_TYPES = Collections.singleton(PDFParser.MEDIA_TYPE);

    protected static final Logger LOG = LoggerFactory.getLogger(PDFBoxRenderer.class);

    /**
     * This is the amount of time it takes for PDFBox to render the page
     * to a BufferedImage
     */
    public static Property PDFBOX_RENDERING_TIME_MS =
            Property.externalReal(Rendering.RENDERING_PREFIX + "pdfbox-rendering-ms");

    /**
     * This is the amount of time it takes for PDFBox/java to write the image after
     * it has been rendered into a BufferedImage.  Some formats take much longer
     * to encode than others.
     */
    public static Property PDFBOX_IMAGE_WRITING_TIME_MS =
            Property.externalReal(Rendering.RENDERING_PREFIX + "pdfbox-image-writing-ms");

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    private int defaultDPI = 300;
    private ImageType defaultImageType = ImageType.GRAY;
    private String defaultImageFormatName = "png";


    @Override
    public RenderResults render(InputStream is, Metadata metadata, ParseContext parseContext,
                                RenderRequest... requests) throws IOException, TikaException {


        PDDocument pdDocument;
        TikaInputStream tis = TikaInputStream.get(is);
        boolean mustClose = false;
        if (tis.getOpenContainer() != null) {
            pdDocument = (PDDocument) tis.getOpenContainer();
        } else {
            //TODO PDFBOX30 use Loader.loadPDF(new RandomAccessReadBuffer(is))
            pdDocument = PDDocument.load(is);
            mustClose = true;
        }
        PageBasedRenderResults results = new PageBasedRenderResults(new TemporaryResources());
        try {
            for (RenderRequest renderRequest : requests) {
                processRequest(renderRequest, pdDocument, metadata, parseContext, results);
            }
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
            Metadata m = new Metadata();
            m.set(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE,
                    TikaCoreProperties.EmbeddedResourceType.RENDERING.name());
            try {
                m.set(TikaPagedText.PAGE_NUMBER, i);
                m.set(TikaPagedText.PAGE_ROTATION, (double)pdDocument.getPage(i - 1).getRotation());
                results.add(renderPage(renderer, id, i, m, parseContext));
            } catch (IOException e) {
                EmbeddedDocumentUtil.recordException(e, m);
                results.add(new RenderResult(RenderResult.STATUS.EXCEPTION, id, null, m));
            }
        }
    }

    protected RenderResult renderPage(PDFRenderer renderer, int id, int pageNumber,
                                      Metadata metadata, ParseContext parseContext)
            throws IOException {

        Path tmpFile = Files.createTempFile("tika-pdfbox-rendering-",
                "-" + id + "-" + pageNumber + "." + getImageFormatName(parseContext));
        try {
            long start = System.currentTimeMillis();
            //TODO: parameterize whether or not to un-rotate page?
            BufferedImage image = renderer.renderImageWithDPI(
                    pageNumber - 1,
                    getDPI(parseContext),
                    getImageType(parseContext));
            long renderingElapsed = System.currentTimeMillis() - start;
            metadata.set(PDFBOX_RENDERING_TIME_MS, renderingElapsed);
            start = System.currentTimeMillis();
            try (OutputStream os = Files.newOutputStream(tmpFile)) {
                ImageIOUtil.writeImage(image, getImageFormatName(parseContext), os, getDPI(parseContext));
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

    @Override
    public void initialize(Map<String, Param> params) throws TikaConfigException {
        //check file format names
    }

    @Override
    public void checkInitialization(InitializableProblemHandler problemHandler)
            throws TikaConfigException {

    }

    public void setDPI(int dpi) {
        this.defaultDPI = dpi;
    }

    public void setImageType(ImageType imageType) {
        this.defaultImageType = imageType;
    }

    public void setImageFormatName(String imageFormatName) {
        this.defaultImageFormatName = imageFormatName;
    }

    protected int getDPI(ParseContext parseContext) {
        PDFParserConfig pdfParserConfig = parseContext.get(PDFParserConfig.class);
        if (pdfParserConfig == null) {
            return defaultDPI;
        }
        return pdfParserConfig.getOcrDPI();
    }

    protected ImageType getImageType(ParseContext parseContext) {
        PDFParserConfig pdfParserConfig = parseContext.get(PDFParserConfig.class);
        if (pdfParserConfig == null) {
            return defaultImageType;
        }
        return pdfParserConfig.getOcrImageType();
    }

    protected String getImageFormatName(ParseContext parseContext) {
        PDFParserConfig pdfParserConfig = parseContext.get(PDFParserConfig.class);
        if (pdfParserConfig == null) {
            return defaultImageFormatName;
        }
        return pdfParserConfig.getOcrImageFormatName();
    }
}
