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
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

import org.apache.commons.io.IOUtils;
import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.extractor.EmbeddedDocumentUtil;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.metadata.TikaMetadataKeys;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.EmbeddedContentHandler;
import org.apache.tika.sax.EndDocumentShieldingContentHandler;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * OpenOffice parser
 */
public class OpenDocumentParser extends AbstractParser {

    /**
     * Serial version UID
     */
    private static final long serialVersionUID = -6410276875438618287L;

    private static final Set<MediaType> SUPPORTED_TYPES =
            Collections.unmodifiableSet(new HashSet<MediaType>(Arrays.asList(
                    MediaType.application("vnd.sun.xml.writer"),
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

    private Parser meta = new OpenDocumentMetaParser();

    private Parser content = new OpenDocumentContentParser();

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

    public void parse(
            InputStream stream, ContentHandler baseHandler,
            Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {

        // Open the Zip stream
        // Use a File if we can, and an already open zip is even better
        ZipFile zipFile = null;
        ZipInputStream zipStream = null;
        if (stream instanceof TikaInputStream) {
            TikaInputStream tis = (TikaInputStream) stream;
            Object container = ((TikaInputStream) stream).getOpenContainer();
            if (container instanceof ZipFile) {
                zipFile = (ZipFile) container;
            } else if (tis.hasFile()) {
                zipFile = new ZipFile(tis.getFile());
            } else {
                zipStream = new ZipInputStream(stream);
            }
        } else {
            zipStream = new ZipInputStream(stream);
        }

        // Prepare to handle the content
        XHTMLContentHandler xhtml = new XHTMLContentHandler(baseHandler, metadata);

        // As we don't know which of the metadata or the content
        //  we'll hit first, catch the endDocument call initially
        EndDocumentShieldingContentHandler handler =
                new EndDocumentShieldingContentHandler(xhtml);

        if (zipFile != null) {
            try {
                handleZipFile(zipFile, metadata, context, handler);
            } finally {
                //Do we want to close silently == catch an exception here?
                zipFile.close();
            }
        } else {
            try {
                handleZipStream(zipStream, metadata, context, handler);
            } finally {
                //Do we want to close silently == catch an exception here?
                zipStream.close();
            }
        }

        // Only now call the end document
        if (handler.getEndDocumentWasCalled()) {
            handler.reallyEndDocument();
        }
    }

    private void handleZipStream(ZipInputStream zipStream, Metadata metadata, ParseContext context, EndDocumentShieldingContentHandler handler) throws IOException, TikaException, SAXException {
        ZipEntry entry = zipStream.getNextEntry();
        while (entry != null) {
            handleZipEntry(entry, zipStream, metadata, context, handler);
            entry = zipStream.getNextEntry();
        }
    }

    private void handleZipFile(ZipFile zipFile, Metadata metadata,
                               ParseContext context, EndDocumentShieldingContentHandler handler)
            throws IOException, TikaException, SAXException {
        // If we can, process the metadata first, then the
        //  rest of the file afterwards (TIKA-1353)
        // Only possible to guarantee that when opened from a file not a stream

        ZipEntry entry = zipFile.getEntry(META_NAME);
        if (entry != null) {
            handleZipEntry(entry, zipFile.getInputStream(entry), metadata, context, handler);
        }

        Enumeration<? extends ZipEntry> entries = zipFile.entries();
        while (entries.hasMoreElements()) {
            entry = entries.nextElement();
            if (!META_NAME.equals(entry.getName())) {
                handleZipEntry(entry, zipFile.getInputStream(entry), metadata, context, handler);
            }
        }
    }
    private void handleZipEntry(ZipEntry entry, InputStream zip, Metadata metadata,
                                ParseContext context, EndDocumentShieldingContentHandler handler)
            throws IOException, SAXException, TikaException {
        if (entry == null) return;

        if (entry.getName().equals("mimetype")) {
            String type = IOUtils.toString(zip, UTF_8);
            metadata.set(Metadata.CONTENT_TYPE, type);
        } else if (entry.getName().equals(META_NAME)) {
            meta.parse(zip, new DefaultHandler(), metadata, context);
        } else if (entry.getName().endsWith("content.xml")) {
            if (content instanceof OpenDocumentContentParser) {
                ((OpenDocumentContentParser) content).parseInternal(zip, handler, metadata, context);
            } else {
                // Foreign content parser was set:
                content.parse(zip, handler, metadata, context);
            }
        } else if (entry.getName().endsWith("styles.xml")) {
            if (content instanceof OpenDocumentContentParser) {
                ((OpenDocumentContentParser) content).parseInternal(zip, handler, metadata, context);
            } else {
                // Foreign content parser was set:
                content.parse(zip, handler, metadata, context);
            }
        } else {
            String embeddedName = entry.getName();
            //scrape everything under Thumbnails/ and Pictures/
            if (embeddedName.contains("Thumbnails/") ||
                    embeddedName.contains("Pictures/")) {
                EmbeddedDocumentExtractor embeddedDocumentExtractor =
                        EmbeddedDocumentUtil.getEmbeddedDocumentExtractor(context);
                Metadata embeddedMetadata = new Metadata();
                embeddedMetadata.set(TikaCoreProperties.ORIGINAL_RESOURCE_NAME, entry.getName());
                /* if (embeddedName.startsWith("Thumbnails/")) {
                    embeddedMetadata.set(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE,
                            TikaCoreProperties.EmbeddedResourceType.THUMBNAIL);
                }*/
                if (embeddedName.contains("Pictures/")) {
                    embeddedMetadata.set(TikaMetadataKeys.EMBEDDED_RESOURCE_TYPE,
                            TikaCoreProperties.EmbeddedResourceType.INLINE.toString());
                }
                if (embeddedDocumentExtractor.shouldParseEmbedded(embeddedMetadata)) {
                    embeddedDocumentExtractor.parseEmbedded(zip,
                            new EmbeddedContentHandler(handler), embeddedMetadata, false);
                }
            }

        }
    }
}
