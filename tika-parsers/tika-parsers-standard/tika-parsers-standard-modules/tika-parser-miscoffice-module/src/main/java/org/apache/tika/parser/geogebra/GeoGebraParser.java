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
package org.apache.tika.parser.geogebra;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import org.apache.tika.annotation.TikaComponent;
import org.apache.tika.exception.TikaException;
import org.apache.tika.exception.WriteLimitReachedException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.extractor.EmbeddedDocumentUtil;
import org.apache.tika.io.BoundedInputStream;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.PageAnchoring;
import org.apache.tika.metadata.PagedText;
import org.apache.tika.metadata.Property;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.EmbeddedContentHandler;
import org.apache.tika.sax.XHTMLContentHandler;
import org.apache.tika.utils.XMLReaderUtils;
import org.apache.tika.zip.utils.ZipFileHelper;

/**
 * Parser for the zip-based GeoGebra formats: worksheets (*.ggb), Notes/Slides
 * (*.ggs) and tools (*.ggt).
 * <p>
 * The construction metadata (title, author, date) and the application
 * name/version are read from {@code geogebra.xml} (or, for a tool, from
 * {@code geogebra_macro.xml}), and the user-visible text (text objects, inline
 * text, captions, tool names and help) is emitted as XHTML paragraphs. For
 * Notes/Slides, each {@code _slideN/geogebra.xml} becomes a
 * {@code <div class="slide">}, in the order given by {@code structure.json}.
 * <p>
 * The representative rendering of the document, {@code geogebra_thumbnail.png}
 * at the root of a worksheet, the icon of a tool ({@code iconFile} of its
 * macro), or the first available slide thumbnail
 * of a Notes/Slides file, is emitted as an embedded document marked with
 * {@link TikaCoreProperties.EmbeddedResourceType#THUMBNAIL}, so that clients
 * (e.g. the unpacker's sidecar metadata) can pick it as the preview image.
 * Thumbnails of the remaining slides are renderings of content that is already
 * extracted, so they are skipped. The document script
 * {@code geogebra_javascript.js} is emitted as a
 * {@link TikaCoreProperties.EmbeddedResourceType#MACRO}, and any other
 * embedded file (e.g. inserted pictures) as an embedded document.
 * <p>
 * A part that cannot be read (an unsupported zip entry, malformed XML) is
 * recorded in the metadata and skipped; the remaining parts are still parsed.
 */
@TikaComponent(name = "geogebra-parser")
public class GeoGebraParser implements Parser {

    /**
     * Serial version UID
     */
    private static final long serialVersionUID = 2114923339149498692L;

    public static final String GEOGEBRA_PREFIX = "geogebra:";

    /**
     * The GeoGebra application flavor the file was written with,
     * e.g. "classic", "notes", "graphing".
     */
    public static final Property APP_NAME =
            Property.internalText(GEOGEBRA_PREFIX + "app-name");

    /**
     * The GeoGebra application version the file was written with.
     */
    public static final Property APP_VERSION =
            Property.internalText(GEOGEBRA_PREFIX + "app-version");

    /**
     * The GeoGebra XML format version.
     */
    public static final Property FORMAT_VERSION =
            Property.internalText(GEOGEBRA_PREFIX + "format-version");

    /**
     * The unique id GeoGebra assigns to the document.
     */
    public static final Property ID = Property.internalText(GEOGEBRA_PREFIX + "id");

    /**
     * The free-form date string of the construction. This is user-entered
     * text, not necessarily a parseable date.
     */
    public static final Property DATE = Property.internalText(GEOGEBRA_PREFIX + "date");

    /**
     * The tool names of the macros in a tool file (or in a worksheet with
     * embedded macros). The name is the {@code toolName} attribute of the
     * macro element.
     */
    public static final Property TOOL_NAME =
            Property.internalTextBag(GEOGEBRA_PREFIX + "toolName");

    private static final Set<MediaType> SUPPORTED_TYPES = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(MediaType.application("vnd.geogebra.file"),
                    MediaType.application("vnd.geogebra.slides"),
                    MediaType.application("vnd.geogebra.tool"))));

    private static final String GEOGEBRA_XML = "geogebra.xml";
    private static final String MACRO_XML = "geogebra_macro.xml";
    private static final String STRUCTURE_JSON = "structure.json";
    private static final String THUMBNAIL_PNG = "geogebra_thumbnail.png";
    private static final String JAVASCRIPT_JS = "geogebra_javascript.js";

    /**
     * Housekeeping entries at the root or in a slide directory that carry no
     * user content of their own. The XML files are parsed for text and the
     * thumbnails handled separately.
     */
    private static final Set<String> HOUSEKEEPING_NAMES = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(GEOGEBRA_XML, MACRO_XML, THUMBNAIL_PNG,
                    "geogebra_defaults2d.xml", "geogebra_defaults3d.xml")));

    private static final String SLIDE_DIR_PREFIX = "_slide";

    private static final Pattern SLIDE_XML_PATTERN =
            Pattern.compile("^(" + SLIDE_DIR_PREFIX + "\\d+)/" + Pattern.quote(GEOGEBRA_XML) + "$");

    /**
     * structure.json only lists chapters, pages and element ids; a real one is
     * a few kilobytes.
     */
    private static final long MAX_STRUCTURE_JSON_LENGTH = 1024 * 1024;

    static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    @Override
    public void parse(TikaInputStream tis, ContentHandler handler, Metadata metadata,
                      ParseContext context) throws IOException, SAXException, TikaException {
        EmbeddedDocumentExtractor embeddedDocumentExtractor =
                EmbeddedDocumentUtil.getEmbeddedDocumentExtractor(context);

        ZipFile zipFile;
        Object container = tis.getOpenContainer();
        if (container instanceof ZipFile) {
            zipFile = (ZipFile) container;
        } else {
            zipFile = ZipFileHelper.open(tis, null);
            tis.setOpenContainer(zipFile);
        }

        XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata, context);
        xhtml.startDocument();
        List<String> slideIds = getSlideIds(zipFile);
        ZipArchiveEntry rootXml = zipFile.getEntry(GEOGEBRA_XML);
        ZipArchiveEntry macroXml = zipFile.getEntry(MACRO_XML);
        //document metadata comes from the first XML parsed: a worksheet's
        //geogebra.xml, a tool's geogebra_macro.xml, or the first slide
        boolean documentMetadataPending = true;
        List<String> iconFiles = new ArrayList<>();
        if (rootXml != null) {
            documentMetadataPending = false;
            parseGeoGebraXml(zipFile, rootXml, xhtml, metadata, true, context);
        }
        if (macroXml != null) {
            //a worksheet with macros carries both XMLs; the macro one only
            //contributes the tool names then, not the document metadata
            iconFiles = parseGeoGebraXml(zipFile, macroXml, xhtml, metadata,
                    documentMetadataPending, context);
            documentMetadataPending = false;
        }
        Map<String, Integer> pageNumbers = new HashMap<>();
        if (!slideIds.isEmpty()) {
            metadata.set(PagedText.N_PAGES, slideIds.size());
            int page = 1;
            for (String slideId : slideIds) {
                pageNumbers.put(slideId, page++);
                xhtml.startElement("div", "class", "slide");
                try {
                    ZipArchiveEntry slideXml = zipFile.getEntry(slideId + "/" + GEOGEBRA_XML);
                    parseGeoGebraXml(zipFile, slideXml, xhtml, metadata, documentMetadataPending,
                            context);
                    documentMetadataPending = false;
                } finally {
                    xhtml.endElement("div");
                }
            }
        }
        String thumbnail = handleThumbnail(zipFile, slideIds, iconFiles, xhtml, metadata, context,
                embeddedDocumentExtractor);
        handleOtherEntries(zipFile, pageNumbers, thumbnail, xhtml, metadata, context,
                embeddedDocumentExtractor);
        xhtml.endDocument();
    }

    /**
     * Returns the ordered slide directory names of a Notes/Slides file, or an
     * empty list if there are no slides. The slides are the
     * {@code _slideN/geogebra.xml} entries; {@code structure.json} only
     * supplies their order, slides it does not list (or all of them, if it is
     * missing or unreadable) follow in numeric order.
     */
    private List<String> getSlideIds(ZipFile zipFile) {
        List<String> numericallySorted = new ArrayList<>();
        Enumeration<ZipArchiveEntry> entries = zipFile.getEntries();
        while (entries.hasMoreElements()) {
            Matcher m = SLIDE_XML_PATTERN.matcher(entries.nextElement().getName());
            if (m.matches()) {
                numericallySorted.add(m.group(1));
            }
        }
        if (numericallySorted.isEmpty()) {
            return Collections.emptyList();
        }
        numericallySorted.sort(GeoGebraParser::compareSlideIds);

        Set<String> ordered = new LinkedHashSet<>();
        ZipArchiveEntry structure = zipFile.getEntry(STRUCTURE_JSON);
        if (structure != null && zipFile.canReadEntryData(structure)) {
            Set<String> knownSlideIds = new HashSet<>(numericallySorted);
            try (InputStream is = new BoundedInputStream(MAX_STRUCTURE_JSON_LENGTH,
                    zipFile.getInputStream(structure))) {
                JsonNode root = OBJECT_MAPPER.readTree(is);
                for (JsonNode chapter : root.path("chapters")) {
                    for (JsonNode page : chapter.path("pages")) {
                        for (JsonNode element : page.path("elements")) {
                            String id = element.path("id").asText("");
                            if (knownSlideIds.contains(id)) {
                                ordered.add(id);
                            }
                        }
                    }
                }
            } catch (IOException e) {
                //fall through to the numeric order
            }
        }
        ordered.addAll(numericallySorted);
        return new ArrayList<>(ordered);
    }

    /**
     * Compares the digit suffixes of two slide ids numerically without
     * parsing them (a crafted id may carry more digits than a long holds):
     * leading zeros aside, a shorter digit string is the smaller number and
     * equal lengths compare lexicographically.
     */
    private static int compareSlideIds(String a, String b) {
        String da = stripLeadingZeros(a.substring(SLIDE_DIR_PREFIX.length()));
        String db = stripLeadingZeros(b.substring(SLIDE_DIR_PREFIX.length()));
        if (da.length() != db.length()) {
            return Integer.compare(da.length(), db.length());
        }
        int byValue = da.compareTo(db);
        return byValue != 0 ? byValue : a.compareTo(b);
    }

    private static String stripLeadingZeros(String digits) {
        int i = 0;
        while (i < digits.length() - 1 && digits.charAt(i) == '0') {
            i++;
        }
        return digits.substring(i);
    }

    /**
     * Parses one GeoGebra XML for its text and, if {@code documentMetadata}
     * is set, the document metadata. A part that cannot be read or is not
     * well-formed is recorded in the metadata and skipped.
     *
     * @return the icon files of the macros in the XML, in document order
     */
    private List<String> parseGeoGebraXml(ZipFile zipFile, ZipArchiveEntry entry,
                                          XHTMLContentHandler xhtml, Metadata metadata,
                                          boolean documentMetadata, ParseContext context)
            throws SAXException {
        if (entry == null) {
            return Collections.emptyList();
        }
        if (!zipFile.canReadEntryData(entry)) {
            EmbeddedDocumentUtil.recordEmbeddedStreamException(
                    new IOException("Unsupported zip entry: " + entry.getName()), metadata, context);
            return Collections.emptyList();
        }
        GeoGebraXMLHandler xmlHandler = new GeoGebraXMLHandler(xhtml, metadata, documentMetadata);
        try (InputStream is = zipFile.getInputStream(entry)) {
            XMLReaderUtils.parseSAX(is, new EmbeddedContentHandler(xmlHandler), context);
        } catch (SAXException e) {
            if (WriteLimitReachedException.isWriteLimitReached(e)) {
                throw e;
            }
            EmbeddedDocumentUtil.recordEmbeddedStreamException(e, metadata, context);
        } catch (IOException | TikaException e) {
            EmbeddedDocumentUtil.recordEmbeddedStreamException(e, metadata, context);
        }
        return xmlHandler.getIconFiles();
    }

    /**
     * Emits the representative thumbnail: the root one of a worksheet, the
     * first slide thumbnail (in slide order) of a Notes/Slides file, or the
     * icon of the first tool that has one. A tool file has no rendering of
     * its own; its icon (a picture in a directory with a generated name,
     * referenced by the macro's {@code iconFile}) is what GeoGebra shows for
     * it.
     *
     * @return the name of the entry emitted, or null if there is none
     */
    private String handleThumbnail(ZipFile zipFile, List<String> slideIds, List<String> iconFiles,
                                   XHTMLContentHandler xhtml, Metadata metadata,
                                   ParseContext context,
                                   EmbeddedDocumentExtractor embeddedDocumentExtractor)
            throws IOException, SAXException {
        ZipArchiveEntry entry = zipFile.getEntry(THUMBNAIL_PNG);
        for (int i = 0; entry == null && i < slideIds.size(); i++) {
            entry = zipFile.getEntry(slideIds.get(i) + "/" + THUMBNAIL_PNG);
        }
        for (int i = 0; entry == null && i < iconFiles.size(); i++) {
            entry = zipFile.getEntry(iconFiles.get(i));
        }
        if (entry == null) {
            return null;
        }
        handleEmbedded(zipFile, entry, TikaCoreProperties.EmbeddedResourceType.THUMBNAIL,
                null, xhtml, metadata, context, embeddedDocumentExtractor);
        return entry.getName();
    }

    /**
     * Emits everything that is not GeoGebra housekeeping: the document script
     * as a macro, and inserted pictures and other files as embedded documents.
     * Housekeeping is matched at the root and in the slide directories only,
     * so a file of the same name elsewhere is still emitted.
     */
    private void handleOtherEntries(ZipFile zipFile, Map<String, Integer> pageNumbers,
                                    String thumbnail, XHTMLContentHandler xhtml,
                                    Metadata metadata, ParseContext context,
                                    EmbeddedDocumentExtractor embeddedDocumentExtractor)
            throws IOException, SAXException {
        Enumeration<ZipArchiveEntry> entries = zipFile.getEntries();
        while (entries.hasMoreElements()) {
            ZipArchiveEntry entry = entries.nextElement();
            if (entry.isDirectory()) {
                continue;
            }
            String name = entry.getName();
            if (name.equals(thumbnail)) {
                //already emitted as the thumbnail (a tool icon)
                continue;
            }
            String dir = "";
            String basename = name;
            int slash = name.indexOf('/');
            if (slash >= 0) {
                dir = name.substring(0, slash);
                basename = name.substring(slash + 1);
            }
            boolean knownDir = dir.isEmpty() || pageNumbers.containsKey(dir);
            if (knownDir && (HOUSEKEEPING_NAMES.contains(basename)
                    || (dir.isEmpty() && STRUCTURE_JSON.equals(basename)))) {
                continue;
            }
            TikaCoreProperties.EmbeddedResourceType type = null;
            if (knownDir && JAVASCRIPT_JS.equals(basename)) {
                type = TikaCoreProperties.EmbeddedResourceType.MACRO;
            }
            handleEmbedded(zipFile, entry, type, pageNumbers.get(dir), xhtml, metadata, context,
                    embeddedDocumentExtractor);
        }
    }

    /**
     * Emits one zip entry as an embedded document. Without a given resource
     * type, pictures are marked {@link TikaCoreProperties.EmbeddedResourceType#INLINE}
     * and other files {@link TikaCoreProperties.EmbeddedResourceType#ATTACHMENT}.
     * An entry in a slide directory is tagged with the slide's page number.
     */
    private void handleEmbedded(ZipFile zipFile, ZipArchiveEntry entry,
                                TikaCoreProperties.EmbeddedResourceType type, Integer page,
                                XHTMLContentHandler xhtml, Metadata parentMetadata,
                                ParseContext context,
                                EmbeddedDocumentExtractor embeddedDocumentExtractor)
            throws IOException, SAXException {
        if (!zipFile.canReadEntryData(entry)) {
            EmbeddedDocumentUtil.recordEmbeddedStreamException(
                    new IOException("Unsupported zip entry: " + entry.getName()), parentMetadata, context);
            return;
        }
        Metadata embeddedMetadata = Metadata.newInstance(context);
        embeddedMetadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, entry.getName());
        embeddedMetadata.set(TikaCoreProperties.INTERNAL_PATH, entry.getName());
        if (page != null) {
            PageAnchoring.applyPageMetadata(embeddedMetadata, Collections.singleton(page));
        }
        try (TikaInputStream tisZip = TikaInputStream.get(zipFile.getInputStream(entry))) {
            if (type == null) {
                //spool so the stream can be rewound after detection
                tisZip.getFile();
                MediaType mediaType = EmbeddedDocumentUtil.getDetector(context)
                        .detect(tisZip, embeddedMetadata, context);
                tisZip.reset();
                if (mediaType != null) {
                    embeddedMetadata.set(HttpHeaders.CONTENT_TYPE, mediaType.toString());
                }
                type = mediaType != null && "image".equals(mediaType.getType())
                        ? TikaCoreProperties.EmbeddedResourceType.INLINE
                        : TikaCoreProperties.EmbeddedResourceType.ATTACHMENT;
            }
            embeddedMetadata.set(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE, type.toString());
            if (embeddedDocumentExtractor.shouldParseEmbedded(embeddedMetadata, context)) {
                embeddedDocumentExtractor.parseEmbedded(tisZip, new EmbeddedContentHandler(xhtml),
                        embeddedMetadata, context, false);
            }
        } catch (IOException e) {
            EmbeddedDocumentUtil.recordEmbeddedStreamException(e, parentMetadata, context);
        }
    }
}
