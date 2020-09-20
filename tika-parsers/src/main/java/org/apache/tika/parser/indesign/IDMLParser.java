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
package org.apache.tika.parser.indesign;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.indesign.xmp.XMPMetadataExtractor;
import org.apache.tika.sax.EndDocumentShieldingContentHandler;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Adobe InDesign IDML Parser.
 */
public class IDMLParser extends AbstractParser {

    /**
     * IDML MimeType
     */
    private static final MediaType IDML_CONTENT_TYPE
            = MediaType.application("vnd.adobe.indesign-idml-package");

    /**
     * Supported types set.
     */
    private static final Set<MediaType> SUPPORTED_TYPES = Collections.singleton(IDML_CONTENT_TYPE);

    /**
     * Metadata file name.
     */
    private static final String META_NAME = "META-INF/metadata.xml";

    /**
     * Internal page count.
     */
    private int pageCount = 0;

    /**
     * Internal master spread count.
     */
    private int masterSpreadCount = 0;

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    @Override
    public void parse(InputStream stream, ContentHandler baseHandler, Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {

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

        XHTMLContentHandler xhtml = new XHTMLContentHandler(baseHandler, metadata);
        xhtml.startDocument();
        EndDocumentShieldingContentHandler handler = new EndDocumentShieldingContentHandler(xhtml);

        if (zipFile != null) {
            try {
                handleZipFile(zipFile, metadata, context, handler);
            } finally {
                zipFile.close();
            }
        } else {
            try {
                handleZipStream(zipStream, metadata, context, handler);
            } finally {
                zipStream.close();
            }
        }

        metadata.set("SpreadPageCount", Integer.toString(pageCount));
        metadata.set("MasterSpreadPageCount", Integer.toString(masterSpreadCount));
        metadata.set("TotalPageCount", Integer.toString(pageCount + masterSpreadCount));

        xhtml.endDocument();

        if (handler.getEndDocumentWasCalled()) {
            handler.reallyEndDocument();
        }
    }

    private void handleZipStream(ZipInputStream zipStream, Metadata metadata, ParseContext context,
                                 ContentHandler handler) throws IOException, TikaException, SAXException {
        ZipEntry entry = zipStream.getNextEntry();
        if (entry == null) {
            throw new IOException("No entries found in ZipInputStream");
        }
        do {
            handleZipEntry(entry, zipStream, metadata, context, handler);
            entry = zipStream.getNextEntry();
        } while (entry != null);
    }

    private void handleZipFile(ZipFile zipFile, Metadata metadata, ParseContext context, ContentHandler handler)
            throws IOException, TikaException, SAXException {

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
                                ParseContext context, ContentHandler handler)
            throws IOException, SAXException, TikaException {

        if (entry == null) {
            return;
        }

        if (entry.getName().equals("mimetype")) {
            String type = IOUtils.toString(zip, UTF_8);
            metadata.set(Metadata.CONTENT_TYPE, type);
        } else if (entry.getName().equals("META-INF/metadata.xml")) {
            XMPMetadataExtractor.parse(zip, metadata);
        } else if (entry.getName().contains("MasterSpreads")) {
            Metadata embeddedMeta = new Metadata();
            ContentAndMetadataExtractor.extract(zip, handler, embeddedMeta, context);
            int spreadCount = Integer.parseInt(embeddedMeta.get("PageCount"));
            masterSpreadCount += spreadCount;
        } else if (entry.getName().contains("Spreads/Spread")) {
            Metadata embeddedMeta = new Metadata();
            ContentAndMetadataExtractor.extract(zip, handler, embeddedMeta, context);
            int spreadCount = Integer.parseInt(embeddedMeta.get("PageCount"));
            pageCount += spreadCount;
        }  else if (entry.getName().contains("Stories")) {
            ContentAndMetadataExtractor.extract(zip, handler, new Metadata(), context);
        }

    }
}
