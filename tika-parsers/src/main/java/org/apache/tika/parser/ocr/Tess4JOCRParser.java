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
package org.apache.tika.parser.ocr;

import com.recognition.software.jdeskew.ImageDeskew;
import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.util.ImageHelper;
import net.sourceforge.tess4j.util.LoadLibs;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.mime.MediaTypeRegistry;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.CompositeParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.image.ImageParser;
import org.apache.tika.parser.image.TiffParser;
import org.apache.tika.parser.jpeg.JpegParser;
import org.apache.tika.sax.OfflineContentHandler;
import org.apache.tika.sax.XHTMLContentHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.imageio.ImageIO;
import javax.xml.parsers.SAXParser;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;


public class Tess4JOCRParser extends AbstractParser {

    private static final Logger LOG = LoggerFactory.getLogger(Tess4JOCRParser.class);
    private static final TesseractOCRConfig DEFAULT_CONFIG = new TesseractOCRConfig();
    private static final Set<MediaType> SUPPORTED_TYPES = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(new MediaType[]{
                    MediaType.image("png"), MediaType.image("jpg"), MediaType.image("jpeg"),
                    MediaType.image("tiff"), MediaType.image("bmp"), MediaType.image("gif"),
                    MediaType.image("jp2"), MediaType.image("jpx"), MediaType.image("x-portable-pixmap")
            })));

    //instance variable. The tesseract model is loaded once and it will be reused
    private volatile ITesseract tesseract;

    private ITesseract getOrInit(ParseContext context) {
        if (tesseract == null) {
            synchronized (this) {
                if (tesseract == null) {
                    TesseractOCRConfig config = context.get(TesseractOCRConfig.class, DEFAULT_CONFIG);
                    tesseract = new Tesseract();
                    tesseract.setDatapath(LoadLibs.extractTessResources("tessdata").getParent());
                    tesseract.setTessVariable("debug_file", "/dev/null");

                    /** setting user's custom configs in the tesseract model is as simple as this,
                     *  tesseract.setDatapath("custom datapath")
                     *  tesseract.setLanguage(config.getLanguage());
                     *  tesseract.setPageSegMode(Integer.parseInt(config.getPageSegMode()));
                     *  But instead, we will create a XML based approach to set custom configs, similar to TIKA-1986
                     */
                }
            }
        }
        return tesseract;
    }

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    public void parse(InputStream stream, ContentHandler handler,
                      Metadata metadata, ParseContext parseContext)
            throws IOException, SAXException, TikaException {

        TesseractOCRConfig config = parseContext.get(TesseractOCRConfig.class, DEFAULT_CONFIG);

        XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
        xhtml.startDocument();
        parse(stream, xhtml, parseContext, config);
        xhtml.endDocument();
    }

    private void parse(InputStream stream, XHTMLContentHandler xhtml,
                       ParseContext context, TesseractOCRConfig config) throws IOException {
        try {
            ITesseract instance = getOrInit(context);
            assert instance != null;
            BufferedImage bufferedImage = ImageIO.read(stream);
            if (config.isEnableImageProcessing() == 1) {
                bufferedImage = processImage(bufferedImage, config);
            }
            String ocrData = instance.doOCR(bufferedImage);
            extractOutput(ocrData, xhtml);
        } catch (Exception e) {
            LOG.warn(e.getMessage(), e);
        } catch (Throwable e) {
            LOG.error(e.getMessage(), e);
        }
    }

    // only supports processing rotated text at the moment
    // TODO: adding density, depth, colorspace, filter, resize options
    private BufferedImage processImage(BufferedImage bufferedImage, TesseractOCRConfig config) {
        ImageDeskew id = new ImageDeskew(bufferedImage);
        double imageSkewAngle = id.getSkewAngle();
        final double MINIMUM_DESKEW_THRESHOLD = 0.05d;
        if ((imageSkewAngle > MINIMUM_DESKEW_THRESHOLD || imageSkewAngle < -(MINIMUM_DESKEW_THRESHOLD))) {
            bufferedImage = ImageHelper.rotateImage(bufferedImage, -imageSkewAngle);
        }
        return bufferedImage;
    }

    private void extractOutput(String content, XHTMLContentHandler xhtml)
            throws SAXException {

        xhtml.startElement("div", "class", "ocr");
        xhtml.characters(content);
        xhtml.endElement("div");
    }

    private static class HOCRPassThroughHandler extends DefaultHandler {
        private final ContentHandler xhtml;
        public static final Set<String> IGNORE = unmodifiableSet(
                "html", "head", "title", "meta", "body");

        public HOCRPassThroughHandler(ContentHandler xhtml) {
            this.xhtml = xhtml;
        }

        /**
         * Starts the given element. Table cells and list items are automatically
         * indented by emitting a tab character as ignorable whitespace.
         */
        @Override
        public void startElement(
                String uri, String local, String name, Attributes attributes)
                throws SAXException {
            if (!IGNORE.contains(name)) {
                xhtml.startElement(uri, local, name, attributes);
            }
        }

        /**
         * Ends the given element. Block elements are automatically followed
         * by a newline character.
         */
        @Override
        public void endElement(String uri, String local, String name) throws SAXException {
            if (!IGNORE.contains(name)) {
                xhtml.endElement(uri, local, name);
            }
        }

        /**
         * @see <a href="https://issues.apache.org/jira/browse/TIKA-210">TIKA-210</a>
         */
        @Override
        public void characters(char[] ch, int start, int length) throws SAXException {
            xhtml.characters(ch, start, length);
        }

        private static Set<String> unmodifiableSet(String... elements) {
            return Collections.unmodifiableSet(
                    new HashSet<String>(Arrays.asList(elements)));
        }
    }
}