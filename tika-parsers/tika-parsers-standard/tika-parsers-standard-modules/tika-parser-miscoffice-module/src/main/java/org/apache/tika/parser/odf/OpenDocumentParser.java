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
package org.apache.tika.parser.odf;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.commons.io.IOUtils;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import org.apache.tika.config.ConfigDeserializer;
import org.apache.tika.config.JsonConfig;
import org.apache.tika.config.TikaComponent;
import org.apache.tika.exception.EncryptedDocumentException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentUtil;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.PageAnchoring;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.metadata.TikaPagedText;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.EmbeddedContentHandler;
import org.apache.tika.sax.EndDocumentShieldingContentHandler;
import org.apache.tika.sax.XHTMLContentHandler;
import org.apache.tika.utils.XMLReaderUtils;

/**
 * OpenOffice parser
 */
@TikaComponent
public class OpenDocumentParser implements Parser {

    /**
     * Serial version UID
     */
    private static final long serialVersionUID = -6410276875438618287L;

    /**
     * Configuration class for JSON deserialization.
     */
    public static class Config {
        public boolean extractMacros = false;
    }

    private static final Set<MediaType> SUPPORTED_TYPES = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(MediaType.application("vnd.sun.xml.writer"),
                    MediaType.application("vnd.oasis.opendocument.text"),
                    MediaType.application("vnd.oasis.opendocument.graphics"),
                    MediaType.application("vnd.oasis.opendocument.presentation"),
                    MediaType.application("vnd.oasis.opendocument.spreadsheet"),
                    MediaType.application("vnd.oasis.opendocument.chart"),
                    MediaType.application("vnd.oasis.opendocument.image"),
                    MediaType.application("vnd.oasis.opendocument.formula"),
                    MediaType.application("vnd.oasis.opendocument.text-master"),
                    MediaType.application("vnd.oasis.opendocument.text-web"),
                    MediaType.application("vnd.oasis.opendocument.text-template"),
                    MediaType.application("vnd.oasis.opendocument.graphics-template"),
                    MediaType.application("vnd.oasis.opendocument.presentation-template"),
                    MediaType.application("vnd.oasis.opendocument.spreadsheet-template"),
                    MediaType.application("vnd.oasis.opendocument.chart-template"),
                    MediaType.application("vnd.oasis.opendocument.image-template"),
                    MediaType.application("vnd.oasis.opendocument.formula-template"),
                    MediaType.application("x-vnd.oasis.opendocument.text"),
                    MediaType.application("x-vnd.oasis.opendocument.graphics"),
                    MediaType.application("x-vnd.oasis.opendocument.presentation"),
                    MediaType.application("x-vnd.oasis.opendocument.spreadsheet"),
                    MediaType.application("x-vnd.oasis.opendocument.chart"),
                    MediaType.application("x-vnd.oasis.opendocument.image"),
                    MediaType.application("x-vnd.oasis.opendocument.formula"),
                    MediaType.application("x-vnd.oasis.opendocument.text-master"),
                    MediaType.application("x-vnd.oasis.opendocument.text-web"),
                    MediaType.application("x-vnd.oasis.opendocument.text-template"),
                    MediaType.application("x-vnd.oasis.opendocument.graphics-template"),
                    MediaType.application("x-vnd.oasis.opendocument.presentation-template"),
                    MediaType.application("x-vnd.oasis.opendocument.spreadsheet-template"),
                    MediaType.application("x-vnd.oasis.opendocument.chart-template"),
                    MediaType.application("x-vnd.oasis.opendocument.image-template"),
                    MediaType.application("x-vnd.oasis.opendocument.formula-template"))));

    private static final String META_NAME = "meta.xml";
    private static final String MANIFEST_NAME = "META-INF/manifest.xml";

    private Parser meta = new OpenDocumentMetaParser();

    private Parser content = new OpenDocumentContentParser();
    private boolean extractMacros = false;

    public OpenDocumentParser() {
    }

    /**
     * Constructor with explicit Config object.
     *
     * @param config the configuration
     */
    public OpenDocumentParser(Config config) {
        this.extractMacros = config.extractMacros;
    }

    /**
     * Constructor for JSON configuration.
     * Requires Jackson on the classpath.
     *
     * @param jsonConfig JSON configuration
     */
    public OpenDocumentParser(JsonConfig jsonConfig) {
        this(ConfigDeserializer.buildConfig(jsonConfig, Config.class));
    }

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

    public void parse(TikaInputStream tis, ContentHandler baseHandler, Metadata metadata,
                      ParseContext context) throws IOException, SAXException, TikaException {

        EmbeddedDocumentUtil embeddedDocumentUtil = new EmbeddedDocumentUtil(context);

        // Open the Zip stream
        // Use a File if we can, and an already open zip is even better
        ZipFile zipFile = null;
        Object container = tis.getOpenContainer();
        if (container instanceof ZipFile) {
            zipFile = (ZipFile) container;
        } else {
            zipFile = ZipFile.builder().setFile(tis.getFile()).get();
            tis.setOpenContainer(zipFile);
        }
        // Prepare to handle the content
        XHTMLContentHandler xhtml = new XHTMLContentHandler(baseHandler, metadata, context);
        xhtml.startDocument();
        // As we don't know which of the metadata or the content
        //  we'll hit first, catch the endDocument call initially
        EndDocumentShieldingContentHandler handler = new EndDocumentShieldingContentHandler(xhtml);

        try {
            handleZipFile(zipFile, metadata, context, handler, embeddedDocumentUtil);
        } catch (SAXException e) {
            if (e.getCause() instanceof EncryptedDocumentException) {
                throw (EncryptedDocumentException)e.getCause();
            }
            throw e;
        }

        // Only now call the end document
        if (handler.isEndDocumentWasCalled()) {
            handler.reallyEndDocument();
        }
    }

    public void setExtractMacros(boolean extractMacros) {
        this.extractMacros = extractMacros;
    }

    public boolean isExtractMacros() {
        return extractMacros;
    }

    private void handleZipFile(ZipFile zipFile, Metadata metadata, ParseContext context,
                               EndDocumentShieldingContentHandler handler,
                               EmbeddedDocumentUtil embeddedDocumentUtil)
            throws IOException, TikaException, SAXException {
        // If we can, process the metadata first, then the
        //  rest of the file afterwards (TIKA-1353)
        // Only possible to guarantee that when opened from a file not a stream

        // Pre-scan content.xml to build a picture→draw:page map.  We need
        // this before the main loop because the Pictures/ entries are
        // emitted lazily in zip-iteration order, and we want each emitted
        // picture's metadata to carry the set of slides on which it
        // appears.  The map is best-effort: if the scan fails, embedded
        // pictures simply go out without TikaPagedText metadata.
        Map<String, Set<Integer>> picturePages = scanPicturePages(zipFile, context);

        ZipArchiveEntry entry = zipFile.getEntry(MANIFEST_NAME);
        if (entry != null) {
            try (TikaInputStream tisZip = TikaInputStream.get(zipFile.getInputStream(entry))) {
                handleZipArchiveEntry(entry, tisZip, metadata, context, handler,
                        embeddedDocumentUtil, picturePages);
            }
        }

        entry = zipFile.getEntry(META_NAME);
        if (entry != null) {
            try (TikaInputStream tisZip = TikaInputStream.get(zipFile.getInputStream(entry))) {
                handleZipArchiveEntry(entry, tisZip, metadata, context, handler,
                        embeddedDocumentUtil, picturePages);
            }
        }

        Enumeration<ZipArchiveEntry> entries = zipFile.getEntries();
        while (entries.hasMoreElements()) {
            entry = entries.nextElement();
            if (!META_NAME.equals(entry.getName())) {
                try (TikaInputStream tis = TikaInputStream.get(zipFile.getInputStream(entry))) {
                    handleZipArchiveEntry(entry, tis, metadata, context, handler,
                            embeddedDocumentUtil, picturePages);
                }
            }
        }
    }

    /**
     * Pre-scans {@code content.xml} for {@code draw:page} and
     * {@code draw:image[xlink:href]} elements, returning a map from
     * picture href (typically {@code "Pictures/<name>"}, matching the
     * picture's zip entry name) to the set of 1-based draw:page indices
     * referencing it.  Returns an empty map if {@code content.xml} is
     * missing or cannot be parsed; per-page metadata is best-effort
     * enrichment, not load-bearing for the parse itself.
     */
    private static Map<String, Set<Integer>> scanPicturePages(ZipFile zipFile,
                                                              ParseContext context) {
        ZipArchiveEntry contentEntry = zipFile.getEntry("content.xml");
        if (contentEntry == null) {
            return Collections.emptyMap();
        }
        PicturePageHandler scan = new PicturePageHandler();
        try (InputStream is = zipFile.getInputStream(contentEntry)) {
            XMLReaderUtils.parseSAX(is, scan, context);
        } catch (IOException | SAXException | TikaException e) {
            return Collections.emptyMap();
        }
        return scan.getPicturePages();
    }

    private void handleZipArchiveEntry(ZipArchiveEntry entry, TikaInputStream tisZip, Metadata metadata,
                                ParseContext context, ContentHandler handler,
                                EmbeddedDocumentUtil embeddedDocumentUtil,
                                Map<String, Set<Integer>> picturePages)
            throws IOException, SAXException, TikaException {

        if (entry.isDirectory()) {
            return;
        }
        if (entry.getName().contains("manifest.xml")) {
            checkForEncryption(tisZip, context);
        } else if (entry.getName().equals("mimetype")) {
            String type = IOUtils.toString(tisZip, UTF_8);
            metadata.set(Metadata.CONTENT_TYPE, type);
        } else if (entry.getName().equals(META_NAME)) {
                meta.parse(tisZip, new DefaultHandler(), metadata, context);
        } else if (entry.getName().endsWith("content.xml")) {
            if (content instanceof OpenDocumentContentParser) {
                    ((OpenDocumentContentParser) content).parseInternal(tisZip, handler, metadata, context);
            } else {
                // Foreign content parser was set:
                content.parse(TikaInputStream.get(tisZip), handler, metadata, context);
            }
        } else if (entry.getName().endsWith("styles.xml")) {
            if (content instanceof OpenDocumentContentParser) {
                    ((OpenDocumentContentParser) content).parseInternal(tisZip, handler, metadata, context);
            } else {
                // Foreign content parser was set:
                content.parse(TikaInputStream.get(tisZip), handler, metadata, context);
            }
        } else {
            String embeddedName = entry.getName();
            //scrape everything under Thumbnails/ and Pictures/
            if (embeddedName.contains("Thumbnails/") || embeddedName.contains("Pictures/")) {

                Metadata embeddedMetadata = Metadata.newInstance(context);
                embeddedMetadata.set(TikaCoreProperties.INTERNAL_PATH, embeddedName);

                    embeddedMetadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, entry.getName());
                    if (embeddedName.startsWith("Thumbnails/")) {
                        embeddedMetadata.set(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE, TikaCoreProperties.EmbeddedResourceType.THUMBNAIL.toString());
                    }

                    if (embeddedName.contains("Pictures/")) {
                        embeddedMetadata.set(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE, TikaCoreProperties.EmbeddedResourceType.INLINE.toString());
                        //spool
                        tisZip.getFile();
                        MediaType embeddedMimeType = embeddedDocumentUtil
                                .getDetector()
                                .detect(tisZip, embeddedMetadata, context);
                        if (embeddedMimeType != null) {
                            embeddedMetadata.set(Metadata.CONTENT_TYPE, embeddedMimeType.toString());
                        }
                        tisZip.reset();
                        // Tag the picture with the draw:page indices it
                        // appears on (set populated by scanPicturePages).
                        // A null lookup means "not referenced by any
                        // draw:page" — leaves PAGE_NUMBERS unset, matching
                        // the "unknown" branch of the convention.
                        Collection<Integer> pages = picturePages.get(embeddedName);
                        if (pages != null) {
                            PageAnchoring.applyPageMetadata(embeddedMetadata, pages);
                        }
                    }

                if (embeddedDocumentUtil.shouldParseEmbedded(embeddedMetadata)) {
                    embeddedDocumentUtil.parseEmbedded(tisZip, new EmbeddedContentHandler(handler),
                            embeddedMetadata, false);
                }
            } else if (extractMacros && embeddedName.contains("Basic/")) {
                //process all files under Basic/; let maybeHandleMacro figure
                //out if it is a macro or not
                maybeHandleMacro(tisZip, embeddedName, handler, context);
            }

        }
    }

    private void maybeHandleMacro(TikaInputStream tisZip, String embeddedName, ContentHandler handler,
                                  ParseContext context)
            throws TikaException, IOException, SAXException {
        //should probably run XMLRootExtractor on the inputstream
        //or read the macro manifest for the names of the macros
        //rather than relying on the script file name
        if (ignoreScriptFile(embeddedName)) {
            return;
        }
        Metadata embeddedMetadata = Metadata.newInstance(context);
        embeddedMetadata.set(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE,
                TikaCoreProperties.EmbeddedResourceType.MACRO.toString());
        embeddedMetadata.set(TikaCoreProperties.INTERNAL_PATH, embeddedName);
        handler = new OpenDocumentMacroHandler(handler, context);
        try {
            tisZip.setCloseShield();
            XMLReaderUtils.parseSAX(tisZip, new EmbeddedContentHandler(handler), context);
        } finally {
            tisZip.removeCloseShield();
        }
    }

    private void checkForEncryption(InputStream stream, ParseContext context)
            throws SAXException, TikaException, IOException {
        try {
            XMLReaderUtils.parseSAX(stream,
                    new EmbeddedContentHandler(
                            new OpenDocumentManifestHandler()), context);
        } catch (SAXException e) {
            if (e.getCause() != null
                    && e.getCause() instanceof EncryptedDocumentException) {
                throw (EncryptedDocumentException)e.getCause();
            }
            //otherwise...swallow
        }
    }

    private boolean ignoreScriptFile(String embeddedName) {
        if (embeddedName.contains("Basic/")) {
            if (embeddedName.contains("script-lb.xml")) {
                return true;
            } else if (embeddedName.contains("script-lc.xml")) {
                return true;
            }
        } else {
            //shouldn't ever get here, but if it isn't under Basic/, ignore it
            return true;
        }
        return false;
    }

    /**
     * SAX handler that pre-scans an ODP {@code content.xml} to record which
     * {@code draw:page} elements reference each {@code draw:image}.  The
     * result is keyed by the picture's {@code xlink:href} attribute value,
     * which for embedded pictures is the zip-entry path (e.g.
     * {@code Pictures/abc.png}).  Used to populate
     * {@link TikaPagedText#PAGE_NUMBERS} on the embedded picture's metadata.
     *
     * <p>Only draw:page is treated as a page boundary; draw:image elements
     * outside any draw:page (e.g. on a master-page template) are ignored
     * because no slide number meaningfully applies.  Their metadata is left
     * without page tagging — the "unknown" branch of the convention.
     */
    static final class PicturePageHandler extends DefaultHandler {
        private static final String DRAW_NS =
                "urn:oasis:names:tc:opendocument:xmlns:drawing:1.0";
        private static final String XLINK_NS =
                "http://www.w3.org/1999/xlink";

        private final Map<String, Set<Integer>> picturePages = new HashMap<>();
        private int currentPage = 0;

        @Override
        public void startElement(String uri, String localName, String qName,
                                 Attributes attrs) {
            if (!DRAW_NS.equals(uri)) {
                return;
            }
            if ("page".equals(localName)) {
                currentPage++;
            } else if ("image".equals(localName) && currentPage > 0) {
                String href = attrs.getValue(XLINK_NS, "href");
                if (href != null) {
                    picturePages
                            .computeIfAbsent(href, k -> new LinkedHashSet<>())
                            .add(currentPage);
                }
            }
        }

        Map<String, Set<Integer>> getPicturePages() {
            return picturePages;
        }
    }
}
