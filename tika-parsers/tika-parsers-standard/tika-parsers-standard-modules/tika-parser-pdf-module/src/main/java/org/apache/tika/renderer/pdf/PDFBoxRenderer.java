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
package org.apache.tika.renderer.pdf;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.IOExceptionWithCause;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.tools.imageio.ImageIOUtil;

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
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.renderer.PageRangeRequest;
import org.apache.tika.renderer.RenderRequest;
import org.apache.tika.renderer.RenderResult;
import org.apache.tika.renderer.RenderResults;
import org.apache.tika.renderer.Renderer;
import org.apache.tika.renderer.RenderingTracker;

public class PDFBoxRenderer implements PDDocumentRenderer, Initializable {

    Set<MediaType> SUPPORTED_TYPES = Collections.singleton(MediaType.application("pdf"));

    /**
     * This is the amount of time it takes for PDFBox to render the page
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

    private int dpi = 300;
    private ImageType imageType = ImageType.GRAY;
    private String imageFormatName = "tiff";


    @Override
    public RenderResults render(InputStream is, Metadata metadata, ParseContext parseContext,
                                RenderRequest... requests) throws IOException, TikaException {


        PDDocument pdDocument;
        TikaInputStream tis = TikaInputStream.get(is);
        boolean mustClose = false;
        if (tis.getOpenContainer() != null) {
            pdDocument = (PDDocument) tis.getOpenContainer();
        } else {
            pdDocument = PDDocument.load(is);
            mustClose = true;
        }
        RenderResults results = new RenderResults(new TemporaryResources());
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
                                RenderResults results) {
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
                                    ParseContext parseContext, RenderResults results) {
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
                m.set(Rendering.PAGE_NUMBER, i);
                Path imagePath = renderPage(renderer, id, i, m);
                results.add(new RenderResult(RenderResult.STATUS.SUCCESS, id, imagePath, m));
            } catch (IOException e) {
                EmbeddedDocumentUtil.recordException(e, m);
                results.add(new RenderResult(RenderResult.STATUS.EXCEPTION, id, null, m));
            }
        }
    }


    private Path renderPage(PDFRenderer renderer, int id, int pageNumber, Metadata metadata)
            throws IOException {

        Path tmpFile = Files.createTempFile("tika-pdfbox-rendering-",
                "-" + id + "-" + pageNumber + "." + imageFormatName);
        try {
            long start = System.currentTimeMillis();
            BufferedImage image = renderer.renderImageWithDPI(pageNumber - 1, dpi, imageType);
            long renderingElapsed = System.currentTimeMillis() - start;
            metadata.set(PDFBOX_RENDERING_TIME_MS, renderingElapsed);
            start = System.currentTimeMillis();
            try (OutputStream os = Files.newOutputStream(tmpFile)) {
                ImageIOUtil.writeImage(image, imageFormatName, os, dpi);
            }
            long elapsedWrite = System.currentTimeMillis() - start;
            metadata.set(PDFBOX_IMAGE_WRITING_TIME_MS, elapsedWrite);
            metadata.set(Rendering.RENDERED_MS, renderingElapsed + elapsedWrite);
        } catch (SecurityException e) {
            //throw SecurityExceptions immediately
            throw e;
        } catch (IOException | RuntimeException e) {
            throw new IOExceptionWithCause(e);
        }
        return tmpFile;
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
        this.dpi = dpi;
    }


    public void setImageType(ImageType imageType) {
        this.imageType = imageType;
    }

    public void setImageFormatName(String imageFormatName) {
        this.imageFormatName = imageFormatName;
    }
}
