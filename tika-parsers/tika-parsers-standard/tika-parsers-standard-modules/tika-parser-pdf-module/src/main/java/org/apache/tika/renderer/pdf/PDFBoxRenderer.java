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
import java.util.Set;

import org.apache.commons.io.IOExceptionWithCause;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.tools.imageio.ImageIOUtil;

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
import org.apache.tika.renderer.RenderResult;
import org.apache.tika.renderer.RenderResults;
import org.apache.tika.renderer.Renderer;

public class PDFBoxRenderer implements Renderer {

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
    public RenderResults render(InputStream is, Metadata metadata, ParseContext parseContext) throws IOException,
            TikaException {


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

            PDFRenderer renderer = new PDFRenderer(pdDocument);

            for (int i = 0; i < pdDocument.getNumberOfPages(); i++) {
                Metadata m = new Metadata();
                m.set(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE,
                        TikaCoreProperties.EmbeddedResourceType.RENDERING.name());
                try {
                    m.set(Rendering.PAGE_NUMBER, i + 1);
                    Path imagePath = renderPage(renderer, i, m);
                    results.add(new RenderResult(RenderResult.STATUS.SUCCESS, imagePath, m));
                } catch (IOException e) {
                    EmbeddedDocumentUtil.recordException(e, m);
                    results.add(new RenderResult(RenderResult.STATUS.EXCEPTION, null, m));
                }
            }
        } finally {
            if (mustClose) {
                pdDocument.close();
            }
        }
        return results;
    }

    private Path renderPage(PDFRenderer renderer, int pageIndex, Metadata metadata)
            throws IOException {

        Path tmpFile = Files.createTempFile("tika-pdfbox-rendering-",
                "-" + (pageIndex + 1) + "." + imageFormatName);
        try {
            long start = System.currentTimeMillis();
            BufferedImage image = renderer.renderImageWithDPI(pageIndex, dpi, imageType);
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

}
