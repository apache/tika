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
package org.apache.tika.parser.iwork.iwana;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.commons.io.input.CloseShieldInputStream;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import org.apache.tika.annotation.TikaComponent;
import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.extractor.EmbeddedDocumentUtil;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.XHTMLContentHandler;

/**
 * Detects the newer 2018 .keynote, .numbers, .pages files and emits their
 * preview image as a thumbnail; the content itself is not parsed yet.
 */
@TikaComponent
public class IWork18PackageParser implements Parser {

    private final static Set<MediaType> supportedTypes = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(IWork18DocumentType.KEYNOTE18.getType(),
                    IWork18DocumentType.NUMBERS18.getType(),
                    IWork18DocumentType.PAGES18.getType())));

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return supportedTypes;
    }

    @Override
    public void parse(TikaInputStream tis, ContentHandler handler, Metadata metadata,
                      ParseContext context) throws IOException, SAXException, TikaException {
        // Open the Zip stream
        // Use a File if we can, and an already open zip is even better
        ZipFile zipFile = null;
        ZipInputStream zipStream = null;
        Object container = tis.getOpenContainer();
        if (container instanceof ZipFile) {
            zipFile = (ZipFile) container;
        } else if (tis.hasFile()) {
            zipFile = ZipFile.builder().setFile(tis.getFile()).get();
            //closed with the stream, as the zip container detector does it
            tis.setOpenContainer(zipFile);
        } else {
            zipStream = new ZipInputStream(tis);
        }

        // Detect the type, and emit the document preview as the thumbnail;
        // the content itself is not parsed yet
        XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata, context);
        xhtml.startDocument();
        MediaType type = null;
        if (zipFile != null) {
            Enumeration<? extends ZipArchiveEntry> entries = zipFile.getEntries();
            while (entries.hasMoreElements()) {
                ZipArchiveEntry entry = entries.nextElement();
                if (type == null) {
                    type = IWork18DocumentType.detectIfPossible(entry);
                }
                if (isPreview(entry) && zipFile.canReadEntryData(entry)) {
                    try (TikaInputStream previewStream =
                                 TikaInputStream.get(zipFile.getInputStream(entry))) {
                        handleThumbnail(entry, previewStream, xhtml, context);
                    }
                }
            }
        } else {
            ZipEntry entry = zipStream.getNextEntry();
            while (entry != null) {
                if (type == null) {
                    type = IWork18DocumentType.detectIfPossible(entry);
                }
                if (isPreview(entry)) {
                    try (TikaInputStream previewStream =
                                 TikaInputStream.get(CloseShieldInputStream.wrap(zipStream))) {
                        handleThumbnail(entry, previewStream, xhtml, context);
                    }
                }
                entry = zipStream.getNextEntry();
            }
        }
        if (type != null) {
            metadata.set(HttpHeaders.CONTENT_TYPE, type.toString());
        }
        xhtml.endDocument();
    }

    /**
     * The document preview, {@code preview.jpg} inside the package's
     * document directory (e.g. {@code Presentation.key/preview.jpg}).
     */
    private static boolean isPreview(ZipEntry entry) {
        String name = entry.getName();
        return name.equals("preview.jpg") || name.endsWith("/preview.jpg");
    }

    private static void handleThumbnail(ZipEntry entry, TikaInputStream previewStream,
                                        XHTMLContentHandler xhtml, ParseContext context)
            throws IOException, SAXException {
        EmbeddedDocumentExtractor extractor =
                EmbeddedDocumentUtil.getEmbeddedDocumentExtractor(context);
        Metadata embeddedMetadata = Metadata.newInstance(context);
        embeddedMetadata.set(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE,
                TikaCoreProperties.EmbeddedResourceType.THUMBNAIL.toString());
        embeddedMetadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, entry.getName());
        embeddedMetadata.set(HttpHeaders.CONTENT_TYPE, "image/jpeg");
        if (extractor.shouldParseEmbedded(embeddedMetadata, context)) {
            extractor.parseEmbedded(previewStream, xhtml, embeddedMetadata, context, true);
        }
    }

    public enum IWork18DocumentType {
        KEYNOTE18(MediaType.application("vnd.apple.keynote.18")),
        NUMBERS18(MediaType.application("vnd.apple.numbers.18")),
        PAGES18(MediaType.application("vnd.apple.pages.18"));

        private final MediaType mediaType;

        IWork18DocumentType(MediaType mediaType) {
            this.mediaType = mediaType;
        }

        /**
         * @param zipFile
         * @return mime if detected or null
         */
        public static MediaType detect(ZipFile zipFile) {
            MediaType type = null;
            Enumeration<? extends ZipEntry> entries = zipFile.getEntries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                type = IWork18DocumentType.detectIfPossible(entry);
                if (type != null) {
                    return type;
                }
            }

            // If we get here, we don't know what it is
            return null;
        }

        /**
         * @return Specific type if this identifies one, otherwise null
         */
        public static MediaType detectIfPossible(ZipEntry entry) {
            String name = entry.getName();
            if (name.endsWith(".numbers/Metadata/BuildVersionHistory.plist")) {
                return IWork18DocumentType.NUMBERS18.getType();
            } else if (name.endsWith(".pages/Metadata/BuildVersionHistory.plist")) {
                return IWork18DocumentType.PAGES18.getType();
            } else if (name.endsWith(".key/Metadata/BuildVersionHistory.plist")) {
                return IWork18DocumentType.KEYNOTE18.getType();
            }
            // Unknown
            return null;
        }

        public MediaType getType() {
            return mediaType;
        }
    }
}
