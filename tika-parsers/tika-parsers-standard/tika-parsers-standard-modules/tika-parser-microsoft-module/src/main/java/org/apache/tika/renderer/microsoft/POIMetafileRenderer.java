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
package org.apache.tika.renderer.microsoft;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Dimension2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import javax.imageio.ImageIO;

import org.apache.poi.hemf.usermodel.HemfPicture;
import org.apache.poi.hwmf.record.HwmfFill;
import org.apache.poi.hwmf.record.HwmfRecord;
import org.apache.poi.hwmf.usermodel.HwmfPicture;

import org.apache.tika.annotation.TikaComponent;
import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentUtil;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Rendering;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.renderer.RenderRequest;
import org.apache.tika.renderer.RenderResult;
import org.apache.tika.renderer.RenderResults;
import org.apache.tika.renderer.Renderer;
import org.apache.tika.renderer.RenderingTracker;

/**
 * Renders EMF and WMF images to a raster image through POI's HEMF and HWMF,
 * the way {@code PDFBoxRenderer} renders PDF pages. The rendering has the
 * configured width, its height follows the image's aspect ratio, and it is
 * drawn on a white canvas. Metafiles have no pages, so the render requests
 * are ignored and a single result is returned.
 * <p>
 * The WMF thumbnails that Word stores in the SummaryInformation of a .doc
 * consist of a window extent and a single {@code dibStretchBlt} record, for
 * which POI cannot compute bounds; those are rendered from the record's
 * bitmap directly.
 */
@TikaComponent(name = "poi-metafile-renderer")
public class POIMetafileRenderer implements Renderer {

    public static final String RENDERED_BY = "poi-metafile-renderer";

    public static final MediaType EMF = MediaType.image("emf");
    public static final MediaType WMF = MediaType.image("wmf");

    private static final Set<MediaType> SUPPORTED_TYPES =
            Collections.unmodifiableSet(new HashSet<>(Arrays.asList(EMF, WMF)));

    private static final int MAX_WIDTH = 10000;

    private int width = 800;
    private String imageFormatName = "png";

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    /**
     * Renders the metafile in the stream, or the {@link HemfPicture} or
     * {@link HwmfPicture} set as the stream's open container. The metadata's
     * {@link TikaCoreProperties#TYPE} tells EMF from WMF when a stream is
     * parsed; it defaults to EMF.
     */
    @Override
    public RenderResults render(TikaInputStream tis, Metadata metadata, ParseContext parseContext,
                                RenderRequest... requests) throws IOException, TikaException {
        Object picture = tis.getOpenContainer();
        if (!(picture instanceof HemfPicture) && !(picture instanceof HwmfPicture)) {
            picture = WMF.toString().equals(metadata.get(TikaCoreProperties.TYPE))
                    ? new HwmfPicture(tis) : new HemfPicture(tis);
        }
        RenderingTracker tracker = parseContext.get(RenderingTracker.class);
        if (tracker == null) {
            tracker = new RenderingTracker();
            parseContext.set(RenderingTracker.class, tracker);
        }
        int id = tracker.getNextId();
        Metadata renderingMetadata = Metadata.newInstance(parseContext);
        renderingMetadata.set(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE,
                TikaCoreProperties.EmbeddedResourceType.RENDERING.name());
        RenderResults results = new RenderResults(new TemporaryResources());
        try {
            long start = System.currentTimeMillis();
            BufferedImage image = picture instanceof HemfPicture
                    ? draw((HemfPicture) picture) : draw((HwmfPicture) picture);
            Path tmpFile = write(image, id);
            renderingMetadata.set(Rendering.RENDERED_MS, System.currentTimeMillis() - start);
            renderingMetadata.add(Rendering.RENDERED_BY, RENDERED_BY);
            renderingMetadata.set(HttpHeaders.CONTENT_TYPE, "image/" + imageFormatName);
            results.add(new RenderResult(RenderResult.STATUS.SUCCESS, id, tmpFile,
                    renderingMetadata));
        } catch (SecurityException e) {
            throw e;
        } catch (Exception e) {
            //record the cause, as PDFBoxRenderer does, so the failure is diagnosable
            EmbeddedDocumentUtil.recordException(e, renderingMetadata, parseContext);
            results.add(new RenderResult(RenderResult.STATUS.EXCEPTION, id, null,
                    renderingMetadata));
        }
        return results;
    }

    private BufferedImage draw(HemfPicture picture) throws IOException {
        Dimension2D size = picture.getSize();
        BufferedImage image = canvas(size);
        Graphics2D graphics = image.createGraphics();
        try {
            picture.draw(graphics, new Rectangle2D.Double(0, 0, image.getWidth(),
                    image.getHeight()));
        } finally {
            graphics.dispose();
        }
        return image;
    }

    private BufferedImage draw(HwmfPicture picture) throws IOException {
        Dimension2D size;
        try {
            size = picture.getSize();
        } catch (RuntimeException e) {
            //no usable window records: a bitmap wrapped in a metafile
            BufferedImage bitmap = firstBitmap(picture);
            if (bitmap == null) {
                throw new IOException("WMF without bounds and without a bitmap", e);
            }
            return scale(bitmap);
        }
        BufferedImage image = canvas(size);
        Graphics2D graphics = image.createGraphics();
        try {
            picture.draw(graphics, new Rectangle2D.Double(0, 0, image.getWidth(),
                    image.getHeight()));
        } finally {
            graphics.dispose();
        }
        return image;
    }

    private static BufferedImage firstBitmap(HwmfPicture picture) {
        for (HwmfRecord record : picture.getRecords()) {
            if (record instanceof HwmfFill.HwmfImageRecord) {
                BufferedImage image = ((HwmfFill.HwmfImageRecord) record).getImage();
                if (image != null) {
                    return image;
                }
            }
        }
        return null;
    }

    private BufferedImage canvas(Dimension2D size) throws IOException {
        if (size == null || size.getWidth() <= 0 || size.getHeight() <= 0) {
            throw new IOException("metafile without a usable size: " + size);
        }
        int height = (int) Math.max(1, Math.round(size.getHeight() * width / size.getWidth()));
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING,
                    RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, width, height);
        } finally {
            graphics.dispose();
        }
        return image;
    }

    private BufferedImage scale(BufferedImage bitmap) {
        int height = (int) Math.max(1,
                Math.round((double) bitmap.getHeight() * width / bitmap.getWidth()));
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, width, height);
            graphics.drawImage(bitmap, 0, 0, width, height, null);
        } finally {
            graphics.dispose();
        }
        return image;
    }

    private Path write(BufferedImage image, int id) throws IOException {
        Path tmpFile = Files.createTempFile("tika-metafile-rendering-",
                "-" + id + "." + imageFormatName);
        try (OutputStream os = Files.newOutputStream(tmpFile)) {
            if (!ImageIO.write(image, imageFormatName, os)) {
                throw new IOException("no ImageIO writer for " + imageFormatName);
            }
        } catch (IOException | RuntimeException e) {
            Files.deleteIfExists(tmpFile);
            throw e;
        }
        return tmpFile;
    }

    public int getWidth() {
        return width;
    }

    /**
     * @param width the rendering's width in pixels, 1 to 10000; the height
     *              follows the image's aspect ratio. Default 800.
     */
    public void setWidth(int width) {
        if (width < 1 || width > MAX_WIDTH) {
            throw new IllegalArgumentException(
                    "width must be between 1 and " + MAX_WIDTH + ", got: " + width);
        }
        this.width = width;
    }

    public String getImageFormatName() {
        return imageFormatName;
    }

    /**
     * @param imageFormatName an ImageIO format name, "png" (default) or "jpeg"
     */
    public void setImageFormatName(String imageFormatName) {
        this.imageFormatName = imageFormatName;
    }
}
