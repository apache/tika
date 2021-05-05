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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.CloseShieldInputStream;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import org.apache.tika.config.Field;
import org.apache.tika.exception.EncryptedDocumentException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.exception.WriteLimitReachedException;
import org.apache.tika.extractor.EmbeddedDocumentUtil;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.EmbeddedContentHandler;
import org.apache.tika.sax.EndDocumentShieldingContentHandler;
import org.apache.tika.sax.OfflineContentHandler;
import org.apache.tika.sax.XHTMLContentHandler;
import org.apache.tika.utils.XMLReaderUtils;

/**
 * OpenOffice parser
 */
public class OpenDocumentParser extends AbstractParser {

    /**
     * Serial version UID
     */
    private static final long serialVersionUID = -6410276875438618287L;

    private static final Set<MediaType> SUPPORTED_TYPES = Collections.unmodifiableSet(
            new HashSet<MediaType>(Arrays.asList(MediaType.application("vnd.sun.xml.writer"),
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

    public void parse(InputStream stream, ContentHandler baseHandler, Metadata metadata,
                      ParseContext context) throws IOException, SAXException, TikaException {

        EmbeddedDocumentUtil embeddedDocumentUtil = new EmbeddedDocumentUtil(context);

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
        xhtml.startDocument();
        // As we don't know which of the metadata or the content
        //  we'll hit first, catch the endDocument call initially
        EndDocumentShieldingContentHandler handler = new EndDocumentShieldingContentHandler(xhtml);

        try {
            if (zipFile != null) {
                try {
                    handleZipFile(zipFile, metadata, context, handler, embeddedDocumentUtil);
                } finally {
                    //Do we want to close silently == catch an exception here?
                    zipFile.close();
                }
            } else {
                try {
                    handleZipStream(zipStream, metadata, context, handler, embeddedDocumentUtil);
                } finally {
                    //Do we want to close silently == catch an exception here?
                    zipStream.close();
                }
            }
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

    @Field
    public void setExtractMacros(boolean extractMacros) {
        this.extractMacros = extractMacros;
    }

    private void handleZipStream(ZipInputStream zipStream, Metadata metadata, ParseContext context,
                                 EndDocumentShieldingContentHandler handler,
                                 EmbeddedDocumentUtil embeddedDocumentUtil)
            throws IOException, TikaException, SAXException {
        ZipEntry entry = zipStream.getNextEntry();
        if (entry == null) {
            throw new IOException("No entries found in ZipInputStream");
        }
        List<SAXException> exceptions = new ArrayList<>();
        do {
            try {
                handleZipEntry(entry, zipStream, metadata, context, handler,
                        embeddedDocumentUtil);
            } catch (SAXException e) {
                WriteLimitReachedException.throwIfWriteLimitReached(e);
                if (e.getCause() instanceof EncryptedDocumentException) {
                    throw (EncryptedDocumentException)e.getCause();
                } else {
                    exceptions.add(e);
                }
            }
            entry = zipStream.getNextEntry();
        } while (entry != null);

        if (exceptions.size() > 0) {
            throw exceptions.get(0);
        }
    }

    private void handleZipFile(ZipFile zipFile, Metadata metadata, ParseContext context,
                               EndDocumentShieldingContentHandler handler,
                               EmbeddedDocumentUtil embeddedDocumentUtil)
            throws IOException, TikaException, SAXException {
        // If we can, process the metadata first, then the
        //  rest of the file afterwards (TIKA-1353)
        // Only possible to guarantee that when opened from a file not a stream

        ZipEntry entry = zipFile.getEntry(MANIFEST_NAME);
        if (entry != null) {
            handleZipEntry(entry, zipFile.getInputStream(entry), metadata, context,
                    handler, embeddedDocumentUtil);
        }

        entry = zipFile.getEntry(META_NAME);
        if (entry != null) {
            handleZipEntry(entry, zipFile.getInputStream(entry), metadata, context,
                    handler, embeddedDocumentUtil);
        }

        Enumeration<? extends ZipEntry> entries = zipFile.entries();
        while (entries.hasMoreElements()) {
            entry = entries.nextElement();
            if (!META_NAME.equals(entry.getName())) {
                handleZipEntry(entry, zipFile.getInputStream(entry), metadata,
                        context, handler, embeddedDocumentUtil);
            }
        }
    }

    private void handleZipEntry(ZipEntry entry, InputStream zip, Metadata metadata,
                                ParseContext context, ContentHandler handler,
                                EmbeddedDocumentUtil embeddedDocumentUtil)
            throws IOException, SAXException, TikaException {


        if (entry.getName().contains("manifest.xml")) {
            checkForEncryption(zip, context);
        } else if (entry.getName().equals("mimetype")) {
            String type = IOUtils.toString(zip, UTF_8);
            metadata.set(Metadata.CONTENT_TYPE, type);
        } else if (entry.getName().equals(META_NAME)) {
            meta.parse(zip, new DefaultHandler(), metadata, context);
        } else if (entry.getName().endsWith("content.xml")) {
            if (content instanceof OpenDocumentContentParser) {
                ((OpenDocumentContentParser) content)
                        .parseInternal(zip, handler, metadata, context);
            } else {
                // Foreign content parser was set:
                content.parse(zip, handler, metadata, context);
            }
        } else if (entry.getName().endsWith("styles.xml")) {
            if (content instanceof OpenDocumentContentParser) {
                ((OpenDocumentContentParser) content)
                        .parseInternal(zip, handler, metadata, context);
            } else {
                // Foreign content parser was set:
                content.parse(zip, handler, metadata, context);
            }
        } else {
            String embeddedName = entry.getName();
            //scrape everything under Thumbnails/ and Pictures/
            if (embeddedName.contains("Thumbnails/") || embeddedName.contains("Pictures/")) {

                Metadata embeddedMetadata = new Metadata();
                TikaInputStream stream = TikaInputStream.get(zip);

                embeddedMetadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, entry.getName());
                if (embeddedName.startsWith("Thumbnails/")) {
                    embeddedMetadata.set(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE,
                            TikaCoreProperties.EmbeddedResourceType.THUMBNAIL.toString());
                }

                if (embeddedName.contains("Pictures/")) {
                    embeddedMetadata.set(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE,
                            TikaCoreProperties.EmbeddedResourceType.INLINE.toString());

                    MediaType embeddedMimeType =
                            embeddedDocumentUtil.getDetector().detect(stream, embeddedMetadata);
                    if (embeddedMimeType != null) {
                        embeddedMetadata.set(Metadata.CONTENT_TYPE, embeddedMimeType.toString());
                    }
                    stream.reset();
                }

                if (embeddedDocumentUtil.shouldParseEmbedded(embeddedMetadata)) {
                    embeddedDocumentUtil.parseEmbedded(stream, new EmbeddedContentHandler(handler),
                            embeddedMetadata, false);
                }
            } else if (extractMacros && embeddedName.contains("Basic/")) {
                //process all files under Basic/; let maybeHandleMacro figure
                //out if it is a macro or not
                maybeHandleMacro(zip, embeddedName, handler, context);
            }

        }
    }

    private void maybeHandleMacro(InputStream is, String embeddedName, ContentHandler handler,
                                  ParseContext context)
            throws TikaException, IOException, SAXException {
        //should probably run XMLRootExtractor on the inputstream
        //or read the macro manifest for the names of the macros
        //rather than relying on the script file name
        if (ignoreScriptFile(embeddedName)) {
            return;
        }
        Metadata embeddedMetadata = new Metadata();
        embeddedMetadata.set(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE,
                TikaCoreProperties.EmbeddedResourceType.MACRO.toString());
        handler = new OpenDocumentMacroHandler(handler, context);
        XMLReaderUtils.parseSAX(new CloseShieldInputStream(is),
                new OfflineContentHandler(new EmbeddedContentHandler(handler)), context);
    }

    private void checkForEncryption(InputStream stream, ParseContext context)
            throws SAXException, TikaException, IOException {
        try {
            XMLReaderUtils.parseSAX(new CloseShieldInputStream(stream),
                    new OfflineContentHandler(new EmbeddedContentHandler(
                            new OpenDocumentManifestHandler())), context);
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


}
