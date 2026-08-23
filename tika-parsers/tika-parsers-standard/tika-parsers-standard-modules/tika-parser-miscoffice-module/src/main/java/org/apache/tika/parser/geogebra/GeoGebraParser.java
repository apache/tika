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
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
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
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.extractor.EmbeddedDocumentUtil;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
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
 * {@code geogebra_macro.xml}), and the user-visible text (text objects, ink
 * notes, captions) is emitted as XHTML paragraphs. For Notes/Slides, the slide
 * order is taken from {@code structure.json} and each slide becomes a
 * {@code <div class="slide">}.
 * <p>
 * The representative rendering of the document — {@code geogebra_thumbnail.png}
 * at the root of a worksheet or tool, or the first slide's thumbnail of a
 * Notes/Slides file — is emitted as an embedded document marked with
 * {@link TikaCoreProperties.EmbeddedResourceType#THUMBNAIL}, so that clients
 * (e.g. the unpacker's sidecar metadata) can pick it as the preview image.
 * Thumbnails of the remaining slides are renderings of content that is already
 * extracted, so they are skipped. Any other embedded file (e.g. inserted
 * pictures) is emitted as an embedded document.
 */
@TikaComponent
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
            Property.internalText(GEOGEBRA_PREFIX + "appName");

    /**
     * The GeoGebra application version the file was written with.
     */
    public static final Property APP_VERSION =
            Property.internalText(GEOGEBRA_PREFIX + "appVersion");

    /**
     * The GeoGebra XML format version.
     */
    public static final Property FORMAT_VERSION =
            Property.internalText(GEOGEBRA_PREFIX + "formatVersion");

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
     * embedded macros).
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

    /**
     * Housekeeping entries every ggb-like container may carry; everything
     * else is user content and worth emitting as an embedded document.
     */
    private static final Set<String> KNOWN_ENTRY_NAMES = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(GEOGEBRA_XML, MACRO_XML, THUMBNAIL_PNG,
                    "geogebra_defaults2d.xml", "geogebra_defaults3d.xml",
                    "geogebra_javascript.js")));

    private static final Pattern SLIDE_XML_PATTERN =
            Pattern.compile("^(_slide\\d+)/" + Pattern.quote(GEOGEBRA_XML) + "$");

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

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
        if (!slideIds.isEmpty()) {
            parseSlides(zipFile, slideIds, xhtml, metadata, context, embeddedDocumentExtractor);
        } else {
            parseWorksheet(zipFile, xhtml, metadata, context, embeddedDocumentExtractor);
        }
        xhtml.endDocument();
    }

    /**
     * Returns the ordered slide directory names of a Notes/Slides file, or an
     * empty list if this is not a Notes/Slides file. The order comes from
     * {@code structure.json}; slides present in the zip but missing from
     * {@code structure.json} are appended in numeric order.
     */
    private List<String> getSlideIds(ZipFile zipFile) {
        Set<String> inZip = new LinkedHashSet<>();
        List<String> numericallySorted = new ArrayList<>();
        Enumeration<ZipArchiveEntry> entries = zipFile.getEntries();
        while (entries.hasMoreElements()) {
            Matcher m = SLIDE_XML_PATTERN.matcher(entries.nextElement().getName());
            if (m.matches()) {
                numericallySorted.add(m.group(1));
            }
        }
        numericallySorted.sort((a, b) -> Integer.compare(
                Integer.parseInt(a.substring("_slide".length())),
                Integer.parseInt(b.substring("_slide".length()))));

        ZipArchiveEntry structure = zipFile.getEntry(STRUCTURE_JSON);
        if (structure == null || numericallySorted.isEmpty()) {
            return Collections.emptyList();
        }
        Set<String> knownSlideIds = new HashSet<>(numericallySorted);
        try (InputStream is = zipFile.getInputStream(structure)) {
            JsonNode root = OBJECT_MAPPER.readTree(is);
            for (JsonNode chapter : root.path("chapters")) {
                for (JsonNode page : chapter.path("pages")) {
                    for (JsonNode element : page.path("elements")) {
                        String id = element.path("id").asText("");
                        if (knownSlideIds.contains(id)) {
                            inZip.add(id);
                        }
                    }
                }
            }
        } catch (IOException e) {
            //fall through to the numeric order
        }
        for (String id : numericallySorted) {
            inZip.add(id);
        }
        return new ArrayList<>(inZip);
    }

    private void parseWorksheet(ZipFile zipFile, XHTMLContentHandler xhtml, Metadata metadata,
                                ParseContext context, EmbeddedDocumentExtractor embeddedDocumentExtractor)
            throws IOException, SAXException, TikaException {
        //a worksheet with macros carries both geogebra.xml and geogebra_macro.xml
        for (String xmlName : new String[]{GEOGEBRA_XML, MACRO_XML}) {
            ZipArchiveEntry contentXml = zipFile.getEntry(xmlName);
            if (contentXml != null) {
                parseGeoGebraXml(zipFile, contentXml, xhtml, metadata, context);
            }
        }
        handleThumbnail(zipFile, zipFile.getEntry(THUMBNAIL_PNG), xhtml, context,
                embeddedDocumentExtractor);
        handleOtherEntries(zipFile, xhtml, context, embeddedDocumentExtractor);
    }

    private void parseSlides(ZipFile zipFile, List<String> slideIds, XHTMLContentHandler xhtml,
                             Metadata metadata, ParseContext context,
                             EmbeddedDocumentExtractor embeddedDocumentExtractor)
            throws IOException, SAXException, TikaException {
        metadata.set(PagedText.N_PAGES, slideIds.size());
        boolean first = true;
        for (String slideId : slideIds) {
            xhtml.startElement("div", "class", "slide");
            ZipArchiveEntry contentXml = zipFile.getEntry(slideId + "/" + GEOGEBRA_XML);
            if (contentXml != null) {
                //document-level metadata comes from the first slide
                parseGeoGebraXml(zipFile, contentXml, xhtml, first ? metadata : null, context);
            }
            xhtml.endElement("div");
            if (first) {
                handleThumbnail(zipFile, zipFile.getEntry(slideId + "/" + THUMBNAIL_PNG), xhtml,
                        context, embeddedDocumentExtractor);
            }
            first = false;
        }
        handleOtherEntries(zipFile, xhtml, context, embeddedDocumentExtractor);
    }

    private void parseGeoGebraXml(ZipFile zipFile, ZipArchiveEntry entry,
                                  XHTMLContentHandler xhtml, Metadata metadata,
                                  ParseContext context)
            throws IOException, SAXException, TikaException {
        try (InputStream is = zipFile.getInputStream(entry)) {
            XMLReaderUtils.parseSAX(is,
                    new EmbeddedContentHandler(new GeoGebraXMLHandler(xhtml, metadata)), context);
        }
    }

    /**
     * Emits the representative thumbnail as an embedded document marked
     * {@link TikaCoreProperties.EmbeddedResourceType#THUMBNAIL}.
     */
    private void handleThumbnail(ZipFile zipFile, ZipArchiveEntry entry, XHTMLContentHandler xhtml,
                                 ParseContext context, EmbeddedDocumentExtractor embeddedDocumentExtractor)
            throws IOException, SAXException {
        if (entry == null) {
            return;
        }
        Metadata embeddedMetadata = Metadata.newInstance(context);
        embeddedMetadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, entry.getName());
        embeddedMetadata.set(TikaCoreProperties.INTERNAL_PATH, entry.getName());
        embeddedMetadata.set(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE,
                TikaCoreProperties.EmbeddedResourceType.THUMBNAIL.toString());
        embeddedMetadata.set(HttpHeaders.CONTENT_TYPE, "image/png");
        if (embeddedDocumentExtractor.shouldParseEmbedded(embeddedMetadata, context)) {
            try (TikaInputStream tisZip = TikaInputStream.get(zipFile.getInputStream(entry))) {
                embeddedDocumentExtractor.parseEmbedded(tisZip, new EmbeddedContentHandler(xhtml),
                        embeddedMetadata, context, false);
            }
        }
    }

    /**
     * Emits everything that is not GeoGebra housekeeping (e.g. inserted
     * pictures) as an embedded document. Housekeeping entries are matched by
     * their basename so the rule covers slide subdirectories, too.
     */
    private void handleOtherEntries(ZipFile zipFile, XHTMLContentHandler xhtml,
                                    ParseContext context,
                                    EmbeddedDocumentExtractor embeddedDocumentExtractor)
            throws IOException, SAXException {
        Enumeration<ZipArchiveEntry> entries = zipFile.getEntries();
        while (entries.hasMoreElements()) {
            ZipArchiveEntry entry = entries.nextElement();
            if (entry.isDirectory()) {
                continue;
            }
            String name = entry.getName();
            if (STRUCTURE_JSON.equals(name)) {
                continue;
            }
            String basename = name.substring(name.lastIndexOf('/') + 1);
            if (KNOWN_ENTRY_NAMES.contains(basename)) {
                continue;
            }
            Metadata embeddedMetadata = Metadata.newInstance(context);
            embeddedMetadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, name);
            embeddedMetadata.set(TikaCoreProperties.INTERNAL_PATH, name);
            embeddedMetadata.set(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE,
                    TikaCoreProperties.EmbeddedResourceType.INLINE.toString());
            if (embeddedDocumentExtractor.shouldParseEmbedded(embeddedMetadata, context)) {
                try (TikaInputStream tisZip =
                             TikaInputStream.get(zipFile.getInputStream(entry))) {
                    embeddedDocumentExtractor.parseEmbedded(tisZip, new EmbeddedContentHandler(xhtml),
                            embeddedMetadata, context, false);
                }
            }
        }
    }
}
