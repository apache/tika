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

import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSStream;
import org.apache.pdfbox.filter.MissingImageReaderException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.color.PDDeviceGray;
import org.apache.pdfbox.pdmodel.graphics.color.PDDeviceRGB;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.apache.pdfbox.tools.imageio.ImageIOUtil;
import org.apache.pdfbox.util.Matrix;
import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentUtil;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.EmbeddedContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Writer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Utility class that overrides the {@link PDFTextStripper} functionality
 * to produce a semi-structured XHTML SAX events instead of a plain text
 * stream.
 */
class PDF2XHTML extends AbstractPDF2XHTML {


    private static final List<String> JPEG = Arrays.asList(
            COSName.DCT_DECODE.getName(),
            COSName.DCT_DECODE_ABBREVIATION.getName());

    private static final List<String> JP2 =
            Arrays.asList(COSName.JPX_DECODE.getName());

    private static final List<String> JB2 = Arrays.asList(
            COSName.JBIG2_DECODE.getName());

    /**
     * This keeps track of the pdf object ids for inline
     * images that have been processed.
     * If {@link PDFParserConfig#getExtractUniqueInlineImagesOnly()
     * is true, this will be checked before extracting an embedded image.
     * The integer keeps track of the inlineImageCounter for that image.
     * This integer is used to identify images in the markup.
     *
     * This is used across the document.  To avoid infinite recursion
     * TIKA-1742, we're limiting the export to one image per page.
     */
    private Map<COSStream, Integer> processedInlineImages = new HashMap<>();
    private int inlineImageCounter = 0;
    private PDF2XHTML(PDDocument document, ContentHandler handler, ParseContext context, Metadata metadata,
                      PDFParserConfig config)
            throws IOException {
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
    public static void process(
            PDDocument document, ContentHandler handler, ParseContext context, Metadata metadata,
            PDFParserConfig config)
            throws SAXException, TikaException {
        PDF2XHTML pdf2XHTML = null;
        try {
            // Extract text using a dummy Writer as we override the
            // key methods to output to the given content
            // handler.
            if (config.getDetectAngles()) {
                pdf2XHTML = new AngleDetectingPDF2XHTML(document, handler, context, metadata, config);
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
        }
    }

    @Override
    protected void endPage(PDPage page) throws IOException {
        try {
            writeParagraphEnd();
            try {
                extractImages(page.getResources(), new HashSet<COSBase>());
            } catch (IOException e) {
                handleCatchableIOE(e);
            }
            super.endPage(page);
        } catch (SAXException e) {
            throw new IOException("Unable to end a page", e);
        } catch (IOException e) {
            exceptions.add(e);
        }
    }

    private void extractImages(PDResources resources, Set<COSBase> seenThisPage) throws SAXException, IOException {
        if (resources == null || config.getExtractInlineImages() == false) {
            return;
        }

        for (COSName name : resources.getXObjectNames()) {

            PDXObject object = null;
            try {
                object = resources.getXObject(name);
            } catch (MissingImageReaderException e) {
                EmbeddedDocumentUtil.recordException(e, metadata);
                continue;
            } catch (IOException e) {
                EmbeddedDocumentUtil.recordEmbeddedStreamException(e, metadata);
                continue;
            }

            if (object == null) {
                continue;
            }
            COSStream cosStream = object.getCOSObject();
            if (seenThisPage.contains(cosStream)) {
                //avoid infinite recursion TIKA-1742
                continue;
            }
            seenThisPage.add(cosStream);

            if (object instanceof PDFormXObject) {
                extractImages(((PDFormXObject) object).getResources(), seenThisPage);
            } else if (object instanceof PDImageXObject) {

                PDImageXObject image = (PDImageXObject) object;

                Metadata embeddedMetadata = new Metadata();
                String extension = image.getSuffix();
                
                if (extension == null || extension.equals("png")) {
                    embeddedMetadata.set(Metadata.CONTENT_TYPE, "image/png");
                    extension = "png";
                } else if (extension.equals("jpg")) {
                    embeddedMetadata.set(Metadata.CONTENT_TYPE, "image/jpeg");
                } else if (extension.equals("tiff")) {
                    embeddedMetadata.set(Metadata.CONTENT_TYPE, "image/tiff");
                    extension = "tif";
                } else if (extension.equals("jpx")) {
                    embeddedMetadata.set(Metadata.CONTENT_TYPE, "image/jp2");
                } else if (extension.equals("jb2")) {
                    embeddedMetadata.set(
                            Metadata.CONTENT_TYPE, "image/x-jbig2");
                } else {
                    //TODO: determine if we need to add more image types
//                    throw new RuntimeException("EXTEN:" + extension);
                }
                Integer imageNumber = processedInlineImages.get(cosStream);
                if (imageNumber == null) {
                    imageNumber = inlineImageCounter++;
                }
                String fileName = "image" + imageNumber + "."+extension;
                embeddedMetadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, fileName);

                // Output the img tag
                AttributesImpl attr = new AttributesImpl();
                attr.addAttribute("", "src", "src", "CDATA", "embedded:" + fileName);
                attr.addAttribute("", "alt", "alt", "CDATA", fileName);
                xhtml.startElement("img", attr);
                xhtml.endElement("img");

                //Do we only want to process unique COSObject ids?
                //If so, have we already processed this one?
                if (config.getExtractUniqueInlineImagesOnly() == true) {
                    if (processedInlineImages.containsKey(cosStream)) {
                        continue;
                    }
                    processedInlineImages.put(cosStream, imageNumber);
                }

                embeddedMetadata.set(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE,
                        TikaCoreProperties.EmbeddedResourceType.INLINE.toString());

                if (embeddedDocumentExtractor.shouldParseEmbedded(embeddedMetadata)) {
                    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                    try {
                        //TODO: handle image.getMetadata()?
                        try {
                            writeToBuffer(image, extension, buffer);
                        } catch (IOException e) {
                            EmbeddedDocumentUtil.recordEmbeddedStreamException(e, metadata);
                            continue;
                        }
                        try (InputStream embeddedIs = TikaInputStream.get(buffer.toByteArray())) {
                            embeddedDocumentExtractor.parseEmbedded(
                                    embeddedIs,
                                    new EmbeddedContentHandler(xhtml),
                                    embeddedMetadata, false);
                        }
                    } catch (IOException e) {
                        handleCatchableIOE(e);
                    }
                }
            }
        }
    }

    //nearly directly copied from PDFBox ExtractImages
    private void writeToBuffer(PDImageXObject pdImage, String suffix, OutputStream out)
            throws IOException {

        BufferedImage image = pdImage.getImage();
        if (image != null) {
            if ("jpg".equals(suffix)) {
                String colorSpaceName = pdImage.getColorSpace().getName();
                //TODO: figure out if we want directJPEG as a configuration
                //previously: if (directJPeg || PDDeviceGray....
                if (PDDeviceGray.INSTANCE.getName().equals(colorSpaceName) ||
                        PDDeviceRGB.INSTANCE.getName().equals(colorSpaceName)) {
                    // RGB or Gray colorspace: get and write the unmodifiedJPEG stream
                    InputStream data = pdImage.getStream().createInputStream(JPEG);
                    org.apache.pdfbox.io.IOUtils.copy(data, out);
                    org.apache.pdfbox.io.IOUtils.closeQuietly(data);
                } else {
                    // for CMYK and other "unusual" colorspaces, the JPEG will be converted
                    ImageIOUtil.writeImage(image, suffix, out);
                }
            } else if ("jp2".equals(suffix) || "jpx".equals(suffix)) {
                InputStream data = pdImage.createInputStream(JP2);
                org.apache.pdfbox.io.IOUtils.copy(data, out);
                org.apache.pdfbox.io.IOUtils.closeQuietly(data);
            } else if ("jb2".equals(suffix)) {
                InputStream data = pdImage.createInputStream(JB2);
                org.apache.pdfbox.io.IOUtils.copy(data, out);
                org.apache.pdfbox.io.IOUtils.closeQuietly(data);
            } else{
                ImageIOUtil.writeImage(image, suffix, out);
            }
        }
        out.flush();
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
            throw new IOException(
                    "Unable to write a string: " + text, e);
        }
    }

    @Override
    protected void writeCharacters(TextPosition text) throws IOException {
        try {
            xhtml.characters(text.getUnicode());
        } catch (SAXException e) {
            throw new IOException(
                    "Unable to write a character: " + text.getUnicode(), e);
        }
    }

    @Override
    protected void writeWordSeparator() throws IOException {
        try {
            xhtml.characters(getWordSeparator());
        } catch (SAXException e) {
            throw new IOException(
                    "Unable to write a space character", e);
        }
    }

    @Override
    protected void writeLineSeparator() throws IOException {
        try {
            xhtml.newline();
        } catch (SAXException e) {
            throw new IOException(
                    "Unable to write a newline character", e);
        }
    }

    class AngleCollector extends PDFTextStripper {
        Set<Integer> angles = new HashSet<>();

        public Set<Integer> getAngles() {
            return angles;
        }

        /**
         * Instantiate a new PDFTextStripper object.
         *
         * @throws IOException If there is an error loading the properties.
         */
        AngleCollector() throws IOException {
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

    private static class AngleDetectingPDF2XHTML extends PDF2XHTML {

        private AngleDetectingPDF2XHTML(PDDocument document, ContentHandler handler, ParseContext context, Metadata metadata, PDFParserConfig config) throws IOException {
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
                    try (PDPageContentStream cs = new PDPageContentStream(document, page, PDPageContentStream.AppendMode.PREPEND, false)) {
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
}

