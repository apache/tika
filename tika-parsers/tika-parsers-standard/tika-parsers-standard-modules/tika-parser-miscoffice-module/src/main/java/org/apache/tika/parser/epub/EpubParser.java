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
package org.apache.tika.parser.epub;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.CloseShieldInputStream;
import org.apache.commons.lang3.StringUtils;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import org.apache.tika.config.Field;
import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.extractor.EmbeddedDocumentUtil;
import org.apache.tika.io.FilenameUtils;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.xml.DcXMLParser;
import org.apache.tika.sax.BodyContentHandler;
import org.apache.tika.sax.EmbeddedContentHandler;
import org.apache.tika.sax.XHTMLContentHandler;
import org.apache.tika.utils.ParserUtils;
import org.apache.tika.utils.XMLReaderUtils;
import org.apache.tika.zip.utils.ZipSalvager;

/**
 * Epub parser
 */
public class EpubParser extends AbstractParser {

    /**
     * Serial version UID
     */
    private static final long serialVersionUID = 215176772484050550L;

    private static final Set<MediaType> SUPPORTED_TYPES = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(MediaType.application("epub+zip"),
                    MediaType.application("x-ibooks+zip"))));
    @Field
    boolean streaming = false;
    private Parser meta = new DcXMLParser();
    private Parser content = new EpubContentParser();

    public Parser getMetaParser() {
        return meta;
    }

    public void setMetaParser(Parser meta) {
        this.meta = meta;
    }

    public Parser getContentParser() {
        return content;
    }

    public void setContentParser(Parser content) {
        this.content = content;
    }

    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    public void parse(InputStream stream, ContentHandler handler, Metadata metadata,
                      ParseContext context) throws IOException, SAXException, TikaException {
        // Because an EPub file is often made up of multiple XHTML files,
        //  we need explicit control over the start and end of the document
        XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
        xhtml.startDocument();
        IOException caughtException = null;
        ContentHandler childHandler = new EmbeddedContentHandler(new BodyContentHandler(xhtml));
        if (streaming) {
            try {
                streamingParse(stream, childHandler, metadata, context);
            } catch (IOException e) {
                caughtException = e;
            }
        } else {
            try {
                bufferedParse(stream, childHandler, xhtml, metadata, context);
            } catch (IOException e) {
                caughtException = e;
            }
        }
        // Finish everything
        xhtml.endDocument();
        if (caughtException != null) {
            throw caughtException;
        }
    }

    private void streamingParse(InputStream stream, ContentHandler bodyHandler, Metadata metadata,
                                ParseContext context)
            throws IOException, TikaException, SAXException {
        ZipArchiveInputStream zip = new ZipArchiveInputStream(stream);

        ZipArchiveEntry entry = zip.getNextZipEntry();
        while (entry != null) {
            if (entry.getName().equals("mimetype")) {
                updateMimeType(zip, metadata);
            } else if (entry.getName().equals("metadata.xml")) {
                meta.parse(zip, new DefaultHandler(), metadata, context);
            } else if (entry.getName().endsWith(".opf")) {
                meta.parse(zip, new DefaultHandler(), metadata, context);
            } else if (entry.getName().endsWith(".htm") || entry.getName().endsWith(".html") ||
                    entry.getName().endsWith(".xhtml") || entry.getName().endsWith(".xml")) {
                content.parse(zip, bodyHandler, metadata, context);
            }
            entry = zip.getNextZipEntry();
        }
    }

    private void updateMimeType(InputStream is, Metadata metadata) throws IOException {
        String type = IOUtils.toString(is, UTF_8);
        //often has trailing new lines
        if (type != null) {
            type = type.trim();
        }
        metadata.set(Metadata.CONTENT_TYPE, type);

    }

    private void bufferedParse(InputStream stream, ContentHandler bodyHandler,
                               XHTMLContentHandler xhtml, Metadata metadata, ParseContext context)
            throws IOException, TikaException, SAXException {
        TikaInputStream tis;
        TemporaryResources temporaryResources = null;
        if (TikaInputStream.isTikaInputStream(stream)) {
            tis = TikaInputStream.cast(stream);
            if (tis.getOpenContainer() instanceof ZipFile) {
                bufferedParseZipFile((ZipFile) tis.getOpenContainer(), bodyHandler, xhtml, metadata,
                        context, true);
                return;
            }
        } else {
            temporaryResources = new TemporaryResources();
            tis = TikaInputStream.get(new CloseShieldInputStream(stream), temporaryResources, metadata);
        }
        ZipFile zipFile = null;
        try {
            zipFile = new ZipFile(tis.getPath().toFile());
        } catch (IOException e) {
            ParserUtils.recordParserFailure(this, e, metadata);
            trySalvage(tis.getPath(), bodyHandler, xhtml, metadata, context);
            return;
        } finally {
            //if we had to wrap tis
            if (temporaryResources != null) {
                tis.close();
            }
        }
        try {
            bufferedParseZipFile(zipFile, bodyHandler, xhtml, metadata, context, true);
        } finally {
            zipFile.close();
        }
    }

    private void trySalvage(Path brokenZip, ContentHandler bodyHandler, XHTMLContentHandler xhtml,
                            Metadata metadata, ParseContext context)
            throws IOException, TikaException, SAXException {
        try (TemporaryResources resources = new TemporaryResources()) {
            Path salvaged =
                    resources.createTempFile(FilenameUtils.getSuffixFromPath(brokenZip.getFileName().toString()));
            ZipSalvager.salvageCopy(brokenZip.toFile(), salvaged.toFile());
            boolean success = false;
            try (ZipFile zipFile = new ZipFile(salvaged.toFile())) {
                success =
                        bufferedParseZipFile(zipFile, bodyHandler, xhtml, metadata, context, false);
            }
            if (!success) {
                try (InputStream is = TikaInputStream.get(salvaged)) {
                    streamingParse(is, xhtml, metadata, context);
                }
            }
        }
    }

    private boolean bufferedParseZipFile(ZipFile zipFile, ContentHandler bodyHandler,
                                         XHTMLContentHandler xhtml, Metadata metadata,
                                         ParseContext context, boolean isStrict)
            throws IOException, TikaException, SAXException {
        String rootOPF = getRoot(zipFile, context);
        if (rootOPF == null) {
            return false;
        }
        ZipArchiveEntry zae = zipFile.getEntry(rootOPF);
        if (zae == null || !zipFile.canReadEntryData(zae)) {
            return false;
        }
        meta.parse(zipFile.getInputStream(zae), new DefaultHandler(), metadata, context);

        ContentOrderScraper contentOrderScraper = new ContentOrderScraper();
        try (InputStream is = zipFile.getInputStream(zae)) {
            XMLReaderUtils.parseSAX(is, contentOrderScraper, context);
        }
        //if no content items, false
        if (contentOrderScraper.contentItems.size() == 0) {
            return false;
        }
        String relativePath = "";
        if (rootOPF.lastIndexOf("/") > -1) {
            relativePath = rootOPF.substring(0, rootOPF.lastIndexOf("/") + 1);
        }

        if (isStrict) {
            int found = 0;
            for (String id : contentOrderScraper.contentItems) {
                HRefMediaPair hRefMediaPair = contentOrderScraper.locationMap.get(id);
                if (hRefMediaPair != null && hRefMediaPair.href != null) {
                    zae = zipFile.getEntry(relativePath + hRefMediaPair.href);
                    if (zae != null && zipFile.canReadEntryData(zae)) {
                        found++;
                    }
                }
            }
            //if not perfect match btwn items and readable items
            //return false
            if (found != contentOrderScraper.contentItems.size()) {
                return false;
            }
        }

        extractMetadata(zipFile, metadata, context);
        Set<String> processed = new HashSet<>();
        for (String id : contentOrderScraper.contentItems) {
            HRefMediaPair hRefMediaPair = contentOrderScraper.locationMap.get(id);
            if (hRefMediaPair != null && hRefMediaPair.href != null) {
                //we need to test for xhtml/xml because the content parser
                //expects that.
                boolean shouldParse = false;
                String href = hRefMediaPair.href.toLowerCase(Locale.US);
                if (hRefMediaPair.media != null) {
                    String mediaType = hRefMediaPair.media.toLowerCase(Locale.US);
                    if (mediaType.contains("html")) {
                        shouldParse = true;
                    }
                } else if (href.endsWith("htm") || href.endsWith("html") || href.endsWith(".xml")) {
                    shouldParse = true;
                }
                if (shouldParse) {
                    zae = zipFile.getEntry(relativePath + hRefMediaPair.href);
                    if (zae != null) {
                        try (InputStream is = zipFile.getInputStream(zae)) {
                            content.parse(is, bodyHandler, metadata, context);
                            processed.add(id);
                        }
                    }
                }
            }
        }

        //now handle embedded files
        EmbeddedDocumentExtractor embeddedDocumentExtractor =
                EmbeddedDocumentUtil.getEmbeddedDocumentExtractor(context);
        for (String id : contentOrderScraper.locationMap.keySet()) {
            if (!processed.contains(id)) {
                HRefMediaPair hRefMediaPair = contentOrderScraper.locationMap.get(id);
                if (shouldHandleEmbedded(hRefMediaPair.media)) {
                    handleEmbedded(zipFile, relativePath, hRefMediaPair, embeddedDocumentExtractor,
                            xhtml, metadata);
                }
            }
        }
        return true;
    }

    private boolean shouldHandleEmbedded(String media) {
        if (media == null) {
            return true;
        }
        String lc = media.toLowerCase(Locale.US);
        if (lc.contains("css")) {
            return false;
        } else if (lc.contains("svg")) {
            return false;
        } else if (lc.endsWith("/xml")) {
            return false;
        } else if (lc.contains("x-ibooks")) {
            return false;
        } else if (lc.equals("application/x-dtbncx+xml")) {
            return false;
        }
        return true;
    }

    private void handleEmbedded(ZipFile zipFile, String relativePath, HRefMediaPair hRefMediaPair,
                                EmbeddedDocumentExtractor embeddedDocumentExtractor,
                                XHTMLContentHandler xhtml, Metadata parentMetadata)
            throws IOException, SAXException {
        if (hRefMediaPair.href == null) {
            return;
        }
        String fullPath = relativePath + hRefMediaPair.href;

        ZipArchiveEntry ze = zipFile.getEntry(fullPath);
        if (ze == null || !zipFile.canReadEntryData(ze)) {
            return;
        }
        Metadata embeddedMetadata = new Metadata();
        if (!StringUtils.isBlank(hRefMediaPair.media)) {
            embeddedMetadata.set(Metadata.CONTENT_TYPE, hRefMediaPair.media);
        }
        if (!embeddedDocumentExtractor.shouldParseEmbedded(embeddedMetadata)) {
            return;
        }

        TikaInputStream stream = null;
        try {
            stream = TikaInputStream.get(zipFile.getInputStream(ze));
        } catch (IOException e) {
            //store this exception in the parent's metadata
            EmbeddedDocumentUtil.recordEmbeddedStreamException(e, parentMetadata);
            return;
        }

        xhtml.startElement("div", "class", "embedded");
        try {
            embeddedDocumentExtractor
                    .parseEmbedded(stream, new EmbeddedContentHandler(xhtml), embeddedMetadata,
                            true);

        } finally {
            IOUtils.closeQuietly(stream);
        }
        xhtml.endElement("div");
    }

    private void extractMetadata(ZipFile zipFile, Metadata metadata, ParseContext context)
            throws IOException, TikaException, SAXException {
        ZipArchiveEntry zae = zipFile.getEntry("mimetype");
        if (zae != null && zipFile.canReadEntryData(zae)) {
            try (InputStream is = zipFile.getInputStream(zae)) {
                updateMimeType(is, metadata);
            }
        }
        zae = zipFile.getEntry("metadata.xml");
        if (zae != null && zipFile.canReadEntryData(zae)) {
            try (InputStream is = zipFile.getInputStream(zae)) {
                meta.parse(is, new DefaultHandler(), metadata, context);
            }
        }
    }

    private String getRoot(ZipFile zipFile, ParseContext context)
            throws IOException, TikaException, SAXException {
        ZipArchiveEntry container = zipFile.getEntry("META-INF/container.xml");
        if (container != null) {
            RootFinder rootFinder = new RootFinder();
            try (InputStream is = zipFile.getInputStream(container)) {
                XMLReaderUtils.parseSAX(is, rootFinder, context);
            }
            return rootFinder.root;
        } else {
            Enumeration<ZipArchiveEntry> entryEnum = zipFile.getEntries();
            while (entryEnum.hasMoreElements()) {
                ZipArchiveEntry ze = entryEnum.nextElement();
                if (ze.getName().toLowerCase(Locale.US).endsWith(".opf") &&
                        zipFile.canReadEntryData(ze)) {
                    return ze.getName();
                }
            }
            return null;
        }
    }

    private static class RootFinder extends DefaultHandler {
        String root = null;

        @Override
        public void startElement(String uri, String localName, String name, Attributes atts)
                throws SAXException {
            if ("rootfile".equalsIgnoreCase(localName)) {
                root = XMLReaderUtils.getAttrValue("full-path", atts);
            }
        }
    }

    private static class ContentOrderScraper extends DefaultHandler {

        Map<String, HRefMediaPair> locationMap = new HashMap<>();
        List<String> contentItems = new ArrayList<>();
        boolean inManifest = false;
        boolean inSpine = false;

        @Override
        public void startElement(String uri, String localName, String name, Attributes atts)
                throws SAXException {
            if ("manifest".equalsIgnoreCase(localName)) {
                inManifest = true;
            } else if ("spine".equalsIgnoreCase(localName)) {
                inSpine = true;
            }
            if (inManifest) {
                if ("item".equalsIgnoreCase(localName)) {
                    String id = XMLReaderUtils.getAttrValue("id", atts);
                    String href = XMLReaderUtils.getAttrValue("href", atts);
                    String mime = XMLReaderUtils.getAttrValue("media-type", atts);
                    if (id != null && href != null) {
                        try {
                            href = URLDecoder.decode(href, UTF_8.name());
                        } catch (UnsupportedEncodingException e) {
                            //swallow
                        }
                        locationMap.put(id, new HRefMediaPair(href, mime));
                    }
                }
            }
            if (inSpine) {
                if ("itemRef".equalsIgnoreCase(localName)) {
                    String id = XMLReaderUtils.getAttrValue("idref", atts);
                    if (id != null) {
                        contentItems.add(id);
                    }
                }
            }
        }


        @Override
        public void endElement(String uri, String localName, String name) throws SAXException {
            if ("manifest".equalsIgnoreCase(localName)) {
                inManifest = false;
            } else if ("spine".equalsIgnoreCase(localName)) {
                inSpine = false;
            }
        }
    }

    private static class HRefMediaPair {
        private final String href;
        private final String media;

        HRefMediaPair(String href, String media) {
            this.href = href;
            this.media = media;
        }

        @Override
        public String toString() {
            return "HRefMediaPair{" + "href='" + href + '\'' + ", media='" + media + '\'' + '}';
        }
    }
}
