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
package org.apache.tika.parser.pkg;

import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import org.apache.tika.exception.TikaException;
import org.apache.tika.io.BoundedInputStream;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.utils.XMLReaderUtils;
import org.apache.tika.zip.utils.ZipEntryPaths;

/**
 * Picks the entry of a zip-based format that is its preview image, so
 * {@link ZipParser} can emit it as the
 * {@link org.apache.tika.metadata.TikaCoreProperties.EmbeddedResourceType#THUMBNAIL}.
 * Only the {@link ZipFile} path picks one: some formats need the whole central
 * directory to decide, which a streamed zip does not have up front.
 */
final class ZipPreview {

    private static final Set<String> IMAGE_EXTENSIONS =
            Set.of("jpg", "jpeg", "png", "gif", "webp", "bmp", "avif", "jxl");

    private static final Map<MediaType, Function<ZipFile, String>> SELECTORS = Map.of(
            MediaType.application("vnd.comicbook+zip"), ZipPreview::firstImageByName,
            MediaType.application("x-krita"), entry("preview.png"),
            MediaType.image("openraster"), entry("Thumbnails/thumbnail.png"),
            MediaType.application("x-extension-fcstd"), entry("thumbnails/Thumbnail.png"),
            MediaType.application("x-procreate"), entry("QuickLook/Thumbnail.png"),
            MediaType.application("x-sketch"), entry("previews/preview.png"),
            MediaType.parse("model/3mf"), ZipPreview::opcPackageThumbnail);

    private static final String OPC_PACKAGE_RELATIONSHIPS = "_rels/.rels";

    private static final String OPC_THUMBNAIL_RELATIONSHIP =
            "http://schemas.openxmlformats.org/package/2006/relationships/metadata/thumbnail";

    /** An OPC package's relationships are a few hundred bytes; this bounds a bad one. */
    static final int MAX_OPC_RELATIONSHIPS_LENGTH = 1024 * 1024;

    private ZipPreview() {
    }

    /**
     * @return the raw name of the preview entry, or null when the type has
     * no preview or the package does not carry one
     */
    static String thumbnailEntry(Metadata metadata, ZipFile zipFile) {
        MediaType type = MediaType.parse(metadata.get(HttpHeaders.CONTENT_TYPE));
        if (type == null) {
            return null;
        }
        Function<ZipFile, String> selector = SELECTORS.get(type.getBaseType());
        return selector == null ? null : selector.apply(zipFile);
    }

    private static Function<ZipFile, String> entry(String name) {
        return zipFile -> zipFile.getEntry(name) == null ? null : name;
    }

    /**
     * The target of the thumbnail relationship of an OPC package (the container of 3MF, as
     * of OOXML), as 3MF names its thumbnail; a part may carry thumbnails of its own, which
     * are not the package's.
     */
    private static String opcPackageThumbnail(ZipFile zipFile) {
        ZipArchiveEntry relationships = zipFile.getEntry(OPC_PACKAGE_RELATIONSHIPS);
        if (relationships == null) {
            return null;
        }
        OpcThumbnailTarget handler = new OpcThumbnailTarget();
        try (InputStream in = new BoundedInputStream(MAX_OPC_RELATIONSHIPS_LENGTH,
                zipFile.getInputStream(relationships))) {
            XMLReaderUtils.parseSAX(in, handler, new ParseContext());
        } catch (IOException | SAXException | TikaException e) {
            return null;
        }
        String name = ZipEntryPaths.resolve("", handler.target);
        return name == null || zipFile.getEntry(name) == null ? null : name;
    }

    private static final class OpcThumbnailTarget extends DefaultHandler {
        private String target;

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attrs) {
            if (target == null && "Relationship".equals(localName)
                    && OPC_THUMBNAIL_RELATIONSHIP.equals(attrs.getValue("Type"))
                    && !"External".equals(attrs.getValue("TargetMode"))) {
                target = attrs.getValue("Target");
            }
        }
    }

    /**
     * A comic's cover is its first page, and the pages are ordered by name.
     */
    private static String firstImageByName(ZipFile zipFile) {
        String first = null;
        String firstKey = null;
        Enumeration<ZipArchiveEntry> entries = zipFile.getEntries();
        while (entries.hasMoreElements()) {
            ZipArchiveEntry entry = entries.nextElement();
            String name = entry.getName();
            if (entry.isDirectory() || !isPage(name)) {
                continue;
            }
            String key = name.toLowerCase(Locale.ROOT);
            if (firstKey == null || key.compareTo(firstKey) < 0) {
                first = name;
                firstKey = key;
            }
        }
        return first;
    }

    private static boolean isPage(String name) {
        if (name.startsWith("__MACOSX/")) {
            return false;
        }
        String base = name.substring(name.lastIndexOf('/') + 1);
        int dot = base.lastIndexOf('.');
        if (base.startsWith(".") || dot < 0) {
            return false;
        }
        return IMAGE_EXTENSIONS.contains(base.substring(dot + 1).toLowerCase(Locale.ROOT));
    }
}
