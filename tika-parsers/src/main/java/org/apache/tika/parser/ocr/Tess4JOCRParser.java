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

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.*;


import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.mime.MediaTypeRegistry;
import org.apache.tika.parser.CompositeParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.external.ExternalParser;
import org.apache.tika.parser.image.ImageParser;
import org.apache.tika.parser.image.TiffParser;
import org.apache.tika.parser.jpeg.JpegParser;
import org.apache.tika.sax.OfflineContentHandler;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import net.sourceforge.tess4j.util.LoadLibs;
import net.sourceforge.tess4j.*;

import javax.imageio.ImageIO;
import javax.xml.parsers.SAXParser;

import static java.nio.charset.StandardCharsets.UTF_8;


public class Tess4JOCRParser extends AbstractParser {

    private static final TesseractOCRConfig DEFAULT_CONFIG = new TesseractOCRConfig();
    private static final Set<MediaType> SUPPORTED_TYPES = Collections.unmodifiableSet(
            new HashSet<MediaType>(Arrays.asList(new MediaType[]{
                    MediaType.image("png"), MediaType.image("jpeg"), MediaType.image("tiff"),
                    MediaType.image("bmp"), MediaType.image("gif"), MediaType.image("jp2"),
                    MediaType.image("jpx"), MediaType.image("x-portable-pixmap")
            })));
    private static Map<String, Boolean> TESSERACT_PRESENT = new HashMap<String, Boolean>();


    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        // If Tesseract is installed, offer our supported image types
        TesseractOCRConfig config = context.get(TesseractOCRConfig.class, DEFAULT_CONFIG);
        if (hasTesseract(config))
            return SUPPORTED_TYPES;

        // Otherwise don't advertise anything, so the other image parsers
        //  can be selected instead
        return Collections.emptySet();
    }

    // not needed in the future
    public boolean hasTesseract(TesseractOCRConfig config) {
        // Fetch where the config says to find Tesseract
        String tesseract = config.getTesseractPath() + getTesseractProg();

        // Have we already checked for a copy of Tesseract there?
        if (TESSERACT_PRESENT.containsKey(tesseract)) {
            return TESSERACT_PRESENT.get(tesseract);
        }
        // Try running Tesseract from there, and see if it exists + works
        String[] checkCmd = {tesseract};
        boolean hasTesseract = ExternalParser.check(checkCmd);
        TESSERACT_PRESENT.put(tesseract, hasTesseract);
        return hasTesseract;

    }

    // not needed in the future
    static String getTesseractProg() {
        return System.getProperty("os.name").startsWith("Windows") ? "tesseract.exe" : "tesseract";
    }

    // TIKA-1445 workaround parser
    // might not need in the future
    private static Parser _TMP_IMAGE_METADATA_PARSER = new Tess4JOCRParser.CompositeImageParser();

    private static class CompositeImageParser extends CompositeParser {
        private static List<Parser> imageParsers = Arrays.asList(new Parser[]{
                new ImageParser(), new JpegParser(), new TiffParser()
        });

        CompositeImageParser() {
            super(new MediaTypeRegistry(), imageParsers);
        }
    }

    public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext parseContext)
            throws IOException, SAXException, TikaException {

    }


    public void parse(File file, ContentHandler handler, Metadata metadata, ParseContext parseContext)
            throws IOException, SAXException, TikaException {

        TesseractOCRConfig config = parseContext.get(TesseractOCRConfig.class, DEFAULT_CONFIG);
        // If Tesseract is not on the path with the current config, do not try to run OCR
        // getSupportedTypes shouldn't have listed us as handling it, so this should only
        //  occur if someone directly calls this parser, not via DefaultParser or similar
        if (!hasTesseract(config))
            return;

//        TemporaryResources tmp = new TemporaryResources();

//            _TMP_IMAGE_METADATA_PARSER.parse(tikaStream, new DefaultHandler(), metadata, parseContext);

        XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
        xhtml.startDocument();
        parse(file, xhtml, parseContext, config);
        xhtml.endDocument();

    }

    private void parse(File file, XHTMLContentHandler xhtml, ParseContext parseContext, TesseractOCRConfig config) throws IOException {

        ITesseract instance = new Tesseract();

        instance.setLanguage(config.getLanguage());
        instance.setPageSegMode(Integer.parseInt(config.getPageSegMode()));

        List<String> confs = new ArrayList<String>();


//        if (config.getPreserveInterwordSpacing()) {
//            confs.add(0, "preserve_interword_spaces=1");
//            instance.setConfigs(confs);
//        } else {
//            confs.add(0, "preserve_interword_spaces=0");
//            instance.setConfigs(confs);
//        }

        try {
            // We can set our own data path if we have it
            instance.setDatapath(LoadLibs.extractTessResources("tessdata").getParent());

            String ocrData = instance.doOCR(file);

            InputStream is = new ByteArrayInputStream(ocrData.getBytes(StandardCharsets.UTF_8));

            if (config.getOutputType().equals(TesseractOCRConfig.OUTPUT_TYPE.HOCR)) {
                extractHOCROutput(is, parseContext, xhtml);
            } else {
                extractOutput(is, xhtml);
            }

        } catch (TesseractException e) {
            e.printStackTrace();
        } catch (TikaException e) {
            e.printStackTrace();
        } catch (SAXException e) {
            e.printStackTrace();
        }
    }


    // copied from tesseract ocr passer

    private void extractOutput(InputStream stream, XHTMLContentHandler xhtml) throws SAXException, IOException {

        xhtml.startElement("div", "class", "ocr");
        try (Reader reader = new InputStreamReader(stream, UTF_8)) {
            char[] buffer = new char[1024];
            for (int n = reader.read(buffer); n != -1; n = reader.read(buffer)) {
                if (n > 0) {
                    xhtml.characters(buffer, 0, n);
                }
            }
        }
        xhtml.endElement("div");
    }

    private void extractHOCROutput(InputStream is, ParseContext parseContext,
                                   XHTMLContentHandler xhtml) throws TikaException, IOException, SAXException {
        if (parseContext == null) {
            parseContext = new ParseContext();
        }
        SAXParser parser = parseContext.getSAXParser();
        xhtml.startElement("div", "class", "ocr");
        parser.parse(is, new OfflineContentHandler(new Tess4JOCRParser.HOCRPassThroughHandler(xhtml)));
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







