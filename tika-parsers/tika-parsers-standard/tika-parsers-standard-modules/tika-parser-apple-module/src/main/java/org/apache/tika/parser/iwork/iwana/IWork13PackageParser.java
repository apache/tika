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
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import com.dd.plist.NSArray;
import com.dd.plist.NSDictionary;
import com.dd.plist.NSObject;
import com.dd.plist.PropertyListParser;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.CloseShieldInputStream;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.extractor.EmbeddedDocumentUtil;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Property;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.XHTMLContentHandler;

public class IWork13PackageParser extends AbstractParser {

    /**
     * All iWork 13 files contain this, so we can detect based on it
     */
    public final static String IWORK13_COMMON_ENTRY = "Metadata/BuildVersionHistory.plist";
    public final static String IWORK13_MAIN_ENTRY = "Index/Document.iwa";

    public static final String IWORKS_PREFIX = "iworks:";
    public static final Property IWORKS_DOC_ID =
            Property.externalText(IWORKS_PREFIX + "document-id");
    public static final Property IWORKS_BUILD_VERSION_HISTORY =
            Property.externalTextBag(IWORKS_PREFIX + "build-version-history");


    private final static Set<MediaType> supportedTypes = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(IWork13DocumentType.KEYNOTE13.getType(),
                    IWork13DocumentType.NUMBERS13.getType(),
                    IWork13DocumentType.PAGES13.getType(),
                    IWork13DocumentType.UNKNOWN13.getType())));

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return supportedTypes;
    }

    @Override
    public void parse(InputStream stream, ContentHandler handler, Metadata metadata,
                      ParseContext context) throws IOException, SAXException, TikaException {
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
        XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
        xhtml.startDocument();

        MediaType type = null;
        if (zipFile != null) {
            type = processZipFile(zipFile, metadata, xhtml, context);
        } else if (zipStream != null) {
            type = processZipStream(zipStream, metadata, xhtml, context);
        }
        if (type != null) {
            if (type == IWork13DocumentType.UNKNOWN13.getType()) {
                type = guessTypeByExtension(metadata);
            }
            metadata.set(Metadata.CONTENT_TYPE, type.toString());
        }
        xhtml.endDocument();

    }

    private MediaType processZipStream(ZipInputStream zipStream, Metadata metadata,
                                       XHTMLContentHandler xhtml, ParseContext parseContext)
            throws TikaException, IOException, SAXException {
        MediaType type = null;
        ZipEntry entry = zipStream.getNextEntry();
        EmbeddedDocumentExtractor embeddedDocumentExtractor =
                EmbeddedDocumentUtil.getEmbeddedDocumentExtractor(parseContext);
        while (entry != null) {
            if (type == null) {
                type = IWork13DocumentType.detectIfPossible(entry);
            }
            processZipEntry(entry, new CloseShieldInputStream(zipStream), metadata, xhtml,
                    parseContext,
                    embeddedDocumentExtractor);
            entry = zipStream.getNextEntry();
        }
        if (type == null) {
            type = IWork13DocumentType.UNKNOWN13.getType();
        }
        return type;
    }

    private MediaType processZipFile(ZipFile zipFile, Metadata metadata,
                                     XHTMLContentHandler xhtml, ParseContext parseContext) throws TikaException {
        MediaType type = null;
        EmbeddedDocumentExtractor embeddedDocumentExtractor =
                EmbeddedDocumentUtil.getEmbeddedDocumentExtractor(parseContext);

        Enumeration<? extends ZipArchiveEntry> entries = zipFile.getEntries();
        Exception ex = null;
        while (entries.hasMoreElements()) {
            ZipArchiveEntry entry = entries.nextElement();

            if (type == null) {
                type = IWork13DocumentType.detectIfPossible(entry);
            }
            try (InputStream is = zipFile.getInputStream(entry)) {
                processZipEntry(entry, is, metadata, xhtml, parseContext, embeddedDocumentExtractor);
            } catch (SecurityException e) {
                throw e;
            } catch (Exception e) {
                ex = e;
            }
        }
        if (type == null) {
            type = IWork13DocumentType.UNKNOWN13.getType();
        }
        if (ex != null) {
            throw new TikaException("problem processing zip file", ex);
        }
        return type;
    }

    private void processZipEntry(ZipEntry entry,
                                 InputStream inputStream,
                                 Metadata metadata, XHTMLContentHandler xhtml,
                                 ParseContext parseContext,
                                 EmbeddedDocumentExtractor embeddedDocumentExtractor)
            throws TikaException, IOException, SAXException {
        String streamName = entry.getName();
        if (streamName == null) {
            return;
        }
        if ("Metadata/Properties.plist".equals(streamName)) {
            extractProperties(inputStream, metadata);
        } else if ("Metadata/BuildVersionHistory.plist".equals(streamName)) {
            extractVersionHistory(inputStream, metadata);
        } else if ("Metadata/DocumentIdentifier".equals(streamName)) {
            extractDocumentIdentifier(inputStream, metadata);
        } else if ("preview.jpg".equals(streamName)) {
            //process thumbnail
            Metadata embeddedMetadata = new Metadata();
            embeddedMetadata.set(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE,
                    TikaCoreProperties.EmbeddedResourceType.THUMBNAIL.toString());
            embeddedMetadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, streamName);
            handleEmbedded(inputStream, embeddedMetadata, xhtml, embeddedDocumentExtractor);
        } else if (streamName.equals("preview-micro.jpg") ||
                streamName.equals("preview-web.jpg")
                || streamName.endsWith(".iwa")) {
            //do nothing
        } else {
            Metadata embeddedMetadata = new Metadata();
            embeddedMetadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, streamName);
            handleEmbedded(inputStream, embeddedMetadata, xhtml, embeddedDocumentExtractor);
        }
    }



    private void handleEmbedded(InputStream inputStream, Metadata embeddedMetadata,
                                XHTMLContentHandler xhtml,
                                EmbeddedDocumentExtractor embeddedDocumentExtractor)
            throws IOException, SAXException {
        if (embeddedDocumentExtractor.shouldParseEmbedded(embeddedMetadata)) {
            embeddedDocumentExtractor.parseEmbedded(inputStream, xhtml, embeddedMetadata, true);
        }
    }

    private void extractVersionHistory(InputStream inputStream, Metadata metadata) throws TikaException {
        try {
            NSObject rootObj = PropertyListParser.parse(inputStream);
            if (rootObj instanceof NSArray) {
                for (NSObject obj : ((NSArray)rootObj).getArray()) {
                    metadata.add(IWORKS_BUILD_VERSION_HISTORY, obj.toString());
                }
            }
        } catch (SecurityException e) {
            throw e;
        } catch (Exception e) {
            throw new TikaException("problem with plist", e);
        }

    }

    private void extractProperties(InputStream inputStream, Metadata metadata) throws TikaException {
        try {
            NSObject rootObj = PropertyListParser.parse(inputStream);
            if (rootObj instanceof NSDictionary) {
                NSDictionary dict = (NSDictionary)rootObj;
                for (String k : dict.keySet()) {
                    String v = dict.get(k).toString();
                    metadata.set(IWORKS_PREFIX + k, v);
                }
            }
        } catch (SecurityException e) {
            throw e;
        } catch (Exception e) {
            throw new TikaException("problem with plist", e);
        }
    }

    private void extractDocumentIdentifier(InputStream inputStream, Metadata metadata)
            throws IOException {
        byte[] bytes = new byte[36];
        int read = IOUtils.read(inputStream, bytes);
        if (read == 36) {
            metadata.set(IWORKS_DOC_ID, new String(bytes, StandardCharsets.ISO_8859_1));
        }
    }

    private MediaType guessTypeByExtension(Metadata metadata) {

        String fName = metadata.get(TikaCoreProperties.RESOURCE_NAME_KEY);
        if (fName == null) {
            return IWork13DocumentType.UNKNOWN13.getType();
        } else if (fName.toLowerCase(Locale.US).endsWith(".numbers")) {
            return IWork13DocumentType.NUMBERS13.getType();
        } else if (fName.toLowerCase(Locale.US).endsWith(".pages")) {
            return IWork13DocumentType.PAGES13.getType();
        } else if (fName.toLowerCase(Locale.US).endsWith(".key")) {
            return IWork13DocumentType.KEYNOTE13.getType();
        }
        return IWork13DocumentType.UNKNOWN13.getType();
    }

    public enum IWork13DocumentType {
        KEYNOTE13(MediaType.application("vnd.apple.keynote.13")),
        NUMBERS13(MediaType.application("vnd.apple.numbers.13")),
        PAGES13(MediaType.application("vnd.apple.pages.13")),
        UNKNOWN13(MediaType.application("vnd.apple.unknown.13"));

        private final MediaType mediaType;

        IWork13DocumentType(MediaType mediaType) {
            this.mediaType = mediaType;
        }

        public static MediaType detect(ZipFile zipFile) {
            MediaType type = null;
            Enumeration<? extends ZipEntry> entries = zipFile.getEntries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                type = IWork13DocumentType.detectIfPossible(entry);
                if (type != null) {
                    return type;
                }
            }

            // If we get here, we don't know what it is
            return UNKNOWN13.getType();
        }

        /**
         * @return Specific type if this identifies one, otherwise null
         */
        public static MediaType detectIfPossible(ZipEntry entry) {
            String name = entry.getName();
            if (!name.endsWith(".iwa")) {
                return null;
            }

            // Is it a uniquely identifying filename?
            if (name.equals("Index/MasterSlide.iwa") || name.startsWith("Index/MasterSlide-")) {
                return KEYNOTE13.getType();
            }
            if (name.equals("Index/Slide.iwa") || name.startsWith("Index/Slide-")) {
                return KEYNOTE13.getType();
            }

            // Is it the main document?
            if (name.equals(IWORK13_MAIN_ENTRY)) {
                // TODO Decode the snappy stream, and check for the Message Type
                // =     2 (TN::SheetArchive), it is a numbers file;
                // = 10000 (TP::DocumentArchive), that's a pages file
                return null;
            }

            // Unknown
            return null;
        }

        public MediaType getType() {
            return mediaType;
        }
    }
}
