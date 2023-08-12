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
package org.apache.tika.parser.pdf.image;

import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.io.output.UnsynchronizedByteArrayOutputStream;
import org.apache.pdfbox.contentstream.PDFGraphicsStreamEngine;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSStream;
import org.apache.pdfbox.filter.MissingImageReaderException;
import org.apache.pdfbox.io.IOUtils;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.graphics.color.PDColor;
import org.apache.pdfbox.pdmodel.graphics.color.PDDeviceGray;
import org.apache.pdfbox.pdmodel.graphics.color.PDDeviceRGB;
import org.apache.pdfbox.pdmodel.graphics.color.PDPattern;
import org.apache.pdfbox.pdmodel.graphics.form.PDTransparencyGroup;
import org.apache.pdfbox.pdmodel.graphics.image.PDImage;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.graphics.pattern.PDAbstractPattern;
import org.apache.pdfbox.pdmodel.graphics.pattern.PDTilingPattern;
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState;
import org.apache.pdfbox.pdmodel.graphics.state.PDSoftMask;
import org.apache.pdfbox.pdmodel.graphics.state.RenderingMode;
import org.apache.pdfbox.tools.imageio.ImageIOUtil;
import org.apache.pdfbox.util.Matrix;
import org.apache.pdfbox.util.Vector;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

import org.apache.tika.exception.TikaException;
import org.apache.tika.exception.TikaMemoryLimitException;
import org.apache.tika.exception.ZeroByteFileException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.extractor.EmbeddedDocumentUtil;
import org.apache.tika.io.BoundedInputStream;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.metadata.TikaPagedText;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.pdf.PDFParserConfig;
import org.apache.tika.parser.pdf.PDMetadataExtractor;
import org.apache.tika.sax.EmbeddedContentHandler;
import org.apache.tika.sax.XHTMLContentHandler;

/**
 * Copied nearly verbatim from PDFBox
 */
public class ImageGraphicsEngine extends PDFGraphicsStreamEngine {

    //We're currently copying images to byte[].  We should
    //limit the length to avoid OOM on crafted files.
    protected static final long MAX_IMAGE_LENGTH_BYTES = 100 * 1024 * 1024;

    protected static final List<String> JPEG =
            Arrays.asList(COSName.DCT_DECODE.getName(), COSName.DCT_DECODE_ABBREVIATION.getName());


    protected static final List<String> JP2 =
            Collections.singletonList(COSName.JPX_DECODE.getName());

    protected static final List<String> JB2 =
            Collections.singletonList(COSName.JBIG2_DECODE.getName());
    final List<IOException> exceptions = new ArrayList<>();
    protected final int pageNumber;
    protected final EmbeddedDocumentExtractor embeddedDocumentExtractor;
    protected final PDFParserConfig pdfParserConfig;
    protected final Map<COSStream, Integer> processedInlineImages;
    protected final AtomicInteger imageCounter;
    protected final Metadata parentMetadata;
    protected final XHTMLContentHandler xhtml;
    protected final ParseContext parseContext;
    protected final boolean extractInlineImageMetadataOnly;
    //TODO: parameterize this ?
    protected boolean useDirectJPEG = false;

    //TODO: this is an embarrassment of an initializer...fix
    protected ImageGraphicsEngine(PDPage page,
                                  int pageNumber,
                                  EmbeddedDocumentExtractor embeddedDocumentExtractor,
                                  PDFParserConfig pdfParserConfig,
                                  Map<COSStream, Integer> processedInlineImages,
                                  AtomicInteger imageCounter, XHTMLContentHandler xhtml,
                                  Metadata parentMetadata, ParseContext parseContext) {
        super(page);
        this.pageNumber = pageNumber;
        this.embeddedDocumentExtractor = embeddedDocumentExtractor;
        this.pdfParserConfig = pdfParserConfig;
        this.processedInlineImages = processedInlineImages;
        this.imageCounter = imageCounter;
        this.xhtml = xhtml;
        this.parentMetadata = parentMetadata;
        this.parseContext = parseContext;
        this.extractInlineImageMetadataOnly = pdfParserConfig.isExtractInlineImageMetadataOnly();
    }

    //nearly directly copied from PDFBox ExtractImages
    protected BufferedImage writeToBuffer(PDImage pdImage, String suffix, boolean directJPEG,
                                      OutputStream out) throws IOException, TikaException {

        if ("jpg".equals(suffix)) {

            String colorSpaceName = pdImage.getColorSpace().getName();
            if (directJPEG || (PDDeviceGray.INSTANCE.getName().equals(colorSpaceName) ||
                    PDDeviceRGB.INSTANCE.getName().equals(colorSpaceName))) {
                // RGB or Gray colorspace: get and write the unmodified JPEG stream
                InputStream data = pdImage.createInputStream(JPEG);
                try {
                    copyUpToMaxLength(data, out);
                } finally {
                    IOUtils.closeQuietly(data);
                }
                return null;
            } else {
                BufferedImage image = pdImage.getImage();
                if (image != null) {
                    // for CMYK and other "unusual" colorspaces, the JPEG will be converted
                    ImageIOUtil.writeImage(image, suffix, out);
                }
                return image;
            }
        } else if ("jp2".equals(suffix)) {
            String colorSpaceName = pdImage.getColorSpace().getName();
            if (directJPEG || !hasMasks(pdImage) &&
                    (PDDeviceGray.INSTANCE.getName().equals(colorSpaceName) ||
                            PDDeviceRGB.INSTANCE.getName().equals(colorSpaceName))) {
                // RGB or Gray colorspace: get and write the unmodified JPEG2000 stream
                InputStream data = pdImage.createInputStream(JP2);
                try {
                    copyUpToMaxLength(data, out);
                } finally {
                    IOUtils.closeQuietly(data);
                }
                return null;
            } else {
                // for CMYK and other "unusual" colorspaces, the image will be converted
                BufferedImage image = pdImage.getImage();
                if (image != null) {
                    // for CMYK and other "unusual" colorspaces, the JPEG will be converted
                    ImageIOUtil.writeImage(image, "jpeg2000", out);
                }
                return image;
            }
        } else if ("tif".equals(suffix) && pdImage.getColorSpace().equals(PDDeviceGray.INSTANCE)) {
            BufferedImage image = pdImage.getImage();
            //TODO: log or otherwise report
            if (image == null) {
                return null;
            }
            // CCITT compressed images can have a different colorspace, but this one is B/W
            // This is a bitonal image, so copy to TYPE_BYTE_BINARY
            // so that a G4 compressed TIFF image is created by ImageIOUtil.writeImage()
            int w = image.getWidth();
            int h = image.getHeight();
            BufferedImage bitonalImage = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_BINARY);
            // copy image the old fashioned way - ColorConvertOp is slower!
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    bitonalImage.setRGB(x, y, image.getRGB(x, y));
                }
            }
            ImageIOUtil.writeImage(bitonalImage, suffix, out);
            return image;
        } else if ("jb2".equals(suffix)) {
            InputStream data = pdImage.createInputStream(JB2);
            try {
                copyUpToMaxLength(data, out);
            } finally {
                IOUtils.closeQuietly(data);
            }
        } else {
            BufferedImage image = pdImage.getImage();
            if (image == null) {
                return null;
            }
            ImageIOUtil.writeImage(image, suffix, out);
            return image;
        }

        out.flush();
        return null;
    }

    protected static void copyUpToMaxLength(InputStream is, OutputStream os)
            throws IOException, TikaException {
        BoundedInputStream bis = new BoundedInputStream(MAX_IMAGE_LENGTH_BYTES, is);
        IOUtils.copy(bis, os);
        if (bis.hasHitBound()) {
            throw new TikaMemoryLimitException(
                    "Image size is larger than allowed (" + MAX_IMAGE_LENGTH_BYTES + ")");
        }
    }

    protected static boolean hasMasks(PDImage pdImage) throws IOException {
        if (pdImage instanceof PDImageXObject) {
            PDImageXObject ximg = (PDImageXObject) pdImage;
            return ximg.getMask() != null || ximg.getSoftMask() != null;
        }
        return false;
    }

    public void run() throws IOException {
        PDPage page = getPage();

        //TODO: is there a better way to do this rather than reprocessing the page
        //can we process the text and images in one go?
        processPage(page);
        PDResources res = page.getResources();
        if (res == null) {
            return;
        }

        for (COSName name : res.getExtGStateNames()) {
            PDExtendedGraphicsState extendedGraphicsState = res.getExtGState(name);
            if (extendedGraphicsState != null) {
                PDSoftMask softMask = extendedGraphicsState.getSoftMask();

                if (softMask != null) {
                    try {
                        PDTransparencyGroup group = softMask.getGroup();

                        if (group != null) {
                            // PDFBOX-4327: without this line NPEs will occur
                            res.getExtGState(name).copyIntoGraphicsState(getGraphicsState());

                            processSoftMask(group);
                        }
                    } catch (IOException e) {
                        handleCatchableIOE(e);
                    }
                }
            }
        }
    }

    @Override
    public void drawImage(PDImage pdImage) throws IOException {
        int imageNumber = 0;
        if (pdImage instanceof PDImageXObject) {
            if (pdImage.isStencil()) {
                processColor(getGraphicsState().getNonStrokingColor());
            }

            PDImageXObject xobject = (PDImageXObject) pdImage;
            //TODO: handle image metadata: xobject.getMetadata()
            Integer cachedNumber = processedInlineImages.get(xobject.getCOSObject());
            if (cachedNumber != null && pdfParserConfig.isExtractUniqueInlineImagesOnly()) {
                // skip duplicate image
                return;
            }
            if (cachedNumber == null) {
                imageNumber = imageCounter.getAndIncrement();
                processedInlineImages.put(xobject.getCOSObject(), imageNumber);
            }
        } else {
            imageNumber = imageCounter.getAndIncrement();
        }
        //TODO: should we use the hash of the PDImage to check for seen
        //For now, we're relying on the cosobject, but this could lead to
        //duplicates if the pdImage is not a PDImageXObject?
        try {
            processImage(pdImage, imageNumber);
        } catch (TikaException | SAXException e) {
            throw new IOException(e);
        } catch (IOException e) {
            handleCatchableIOE(e);
        }
    }

    @Override
    public void appendRectangle(Point2D p0, Point2D p1, Point2D p2, Point2D p3) throws IOException {

    }

    @Override
    public void clip(int windingRule) throws IOException {

    }

    @Override
    public void moveTo(float x, float y) throws IOException {

    }

    @Override
    public void lineTo(float x, float y) throws IOException {

    }

    @Override
    public void curveTo(float x1, float y1, float x2, float y2, float x3, float y3)
            throws IOException {

    }

    @Override
    public Point2D getCurrentPoint() throws IOException {
        return new Point2D.Float(0, 0);
    }

    @Override
    public void closePath() throws IOException {

    }

    @Override
    public void endPath() throws IOException {

    }

    @Override
    protected void showGlyph(Matrix textRenderingMatrix, PDFont font, int code,
                             Vector displacement) throws IOException {

        RenderingMode renderingMode = getGraphicsState().getTextState().getRenderingMode();
        if (renderingMode.isFill()) {
            processColor(getGraphicsState().getNonStrokingColor());
        }

        if (renderingMode.isStroke()) {
            processColor(getGraphicsState().getStrokingColor());
        }
    }

    @Override
    public void strokePath() throws IOException {
        processColor(getGraphicsState().getStrokingColor());
    }

    @Override
    public void fillPath(int windingRule) throws IOException {
        processColor(getGraphicsState().getNonStrokingColor());
    }

    @Override
    public void fillAndStrokePath(int windingRule) throws IOException {
        processColor(getGraphicsState().getNonStrokingColor());
    }

    @Override
    public void shadingFill(COSName shadingName) throws IOException {

    }

    // find out if it is a tiling pattern, then process that one
    private void processColor(PDColor color) throws IOException {
        if (color.getColorSpace() instanceof PDPattern) {
            PDPattern pattern = (PDPattern) color.getColorSpace();
            PDAbstractPattern abstractPattern = pattern.getPattern(color);

            if (abstractPattern instanceof PDTilingPattern) {
                processTilingPattern((PDTilingPattern) abstractPattern, null, null);
            }
        }
    }

    protected void processImage(PDImage pdImage, int imageNumber)
            throws IOException, TikaException, SAXException {
        //this is the metadata for this particular image
        Metadata metadata = new Metadata();
        String suffix = getSuffix(pdImage, metadata);
        String fileName = "image" + imageNumber + "." + suffix;


        AttributesImpl attr = new AttributesImpl();
        attr.addAttribute("", "src", "src", "CDATA", "embedded:" + fileName);
        attr.addAttribute("", "alt", "alt", "CDATA", fileName);
        xhtml.startElement("img", attr);
        xhtml.endElement("img");


        metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, fileName);
        metadata.set(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE,
                TikaCoreProperties.EmbeddedResourceType.INLINE.toString());
        metadata.set(TikaPagedText.PAGE_NUMBER, pageNumber);

        //TODO -- should we look for image rotation metadata in the PDImage or elsewhere?

        if (extractInlineImageMetadataOnly) {
            extractInlineImageMetadataOnly(pdImage, metadata);
            return;
        }

        if (embeddedDocumentExtractor.shouldParseEmbedded(metadata)) {
            UnsynchronizedByteArrayOutputStream buffer = new UnsynchronizedByteArrayOutputStream();
            if (pdImage instanceof PDImageXObject) {
                //extract the metadata contained outside of the image
                PDMetadataExtractor
                        .extract(((PDImageXObject) pdImage).getMetadata(), metadata, parseContext);
            }
            BufferedImage bufferedImage = null;
            try {
                bufferedImage = writeToBuffer(pdImage, suffix, useDirectJPEG, buffer);
            } catch (MissingImageReaderException e) {
                EmbeddedDocumentUtil.recordException(e, parentMetadata);
                return;
            } catch (IOException e) {
                EmbeddedDocumentUtil.recordEmbeddedStreamException(e, metadata);
                return;
            }
            try (TikaInputStream tis = TikaInputStream.get(buffer.toByteArray())) {
                if (bufferedImage != null) {
                    tis.setOpenContainer(bufferedImage);
                }
                embeddedDocumentExtractor
                        .parseEmbedded(tis, new EmbeddedContentHandler(xhtml), metadata,
                                false);
            }
        }

    }

    protected void extractInlineImageMetadataOnly(PDImage pdImage, Metadata metadata)
            throws IOException, SAXException {
        if (pdImage instanceof PDImageXObject) {
            PDMetadataExtractor
                    .extract(((PDImageXObject) pdImage).getMetadata(), metadata, parseContext);
        }
        metadata.set(Metadata.IMAGE_WIDTH, pdImage.getWidth());
        metadata.set(Metadata.IMAGE_LENGTH, pdImage.getHeight());
        //TODO: what else can we extract from the PDImage without rendering?
        ZeroByteFileException.IgnoreZeroByteFileException before =
                parseContext.get(ZeroByteFileException.IgnoreZeroByteFileException.class);
        try {
            parseContext.set(ZeroByteFileException.IgnoreZeroByteFileException.class,
                    ZeroByteFileException.IGNORE_ZERO_BYTE_FILE_EXCEPTION);
            embeddedDocumentExtractor.parseEmbedded(TikaInputStream.get(new byte[0]),
                    new EmbeddedContentHandler(xhtml), metadata, false);
        } finally {
            //replace whatever was there before
            parseContext.set(ZeroByteFileException.IgnoreZeroByteFileException.class, before);
        }
    }

    protected String getSuffix(PDImage pdImage, Metadata metadata) throws IOException {
        String suffix = pdImage.getSuffix();

        if (suffix == null || suffix.equals("png")) {
            metadata.set(Metadata.CONTENT_TYPE, "image/png");
            suffix = "png";
        } else if (suffix.equals("jpg")) {
            metadata.set(Metadata.CONTENT_TYPE, "image/jpeg");
        } else if (suffix.equals("tiff")) {
            metadata.set(Metadata.CONTENT_TYPE, "image/tiff");
            suffix = "tif";
        } else if (suffix.equals("jpx")) {
            metadata.set(Metadata.CONTENT_TYPE, "image/jp2");
            // use jp2 suffix for file because jpx not known by windows
            suffix = "jp2";
        } else if (suffix.equals("jb2")) {
            //PDFBox resets suffix to png when image's suffix == jb2
            metadata.set(Metadata.CONTENT_TYPE, "image/x-jbig2");
        } else {
            //TODO: determine if we need to add more image types
//                    throw new RuntimeException("EXTEN:" + extension);
        }
        if (hasMasks(pdImage)) {
            // TIKA-3040, PDFBOX-4771: can't save ARGB as JPEG
            suffix = "png";
        }
        return suffix;
    }

    protected void handleCatchableIOE(IOException e) throws IOException {
        if (pdfParserConfig.isCatchIntermediateIOExceptions()) {
            if (e.getCause() instanceof SAXException && e.getCause().getMessage() != null &&
                    e.getCause().getMessage().contains("Your document contained more than")) {
                //TODO -- is there a cleaner way of checking for:
                // WriteOutContentHandler.WriteLimitReachedException?
                throw e;
            }

            String msg = e.getMessage();
            if (msg == null) {
                msg = "IOException, no message";
            }
            parentMetadata.add(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING, msg);
            exceptions.add(e);
        } else {
            throw e;
        }
    }

    public List<IOException> getExceptions() {
        return exceptions;
    }
}
