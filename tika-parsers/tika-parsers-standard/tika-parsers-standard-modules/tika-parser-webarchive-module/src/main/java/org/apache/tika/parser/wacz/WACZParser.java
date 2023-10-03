/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.tika.parser.wacz;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.GZIPInputStream;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.compress.utils.IOUtils;
import org.apache.commons.io.input.CloseShieldInputStream;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.extractor.EmbeddedDocumentUtil;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.XHTMLContentHandler;

public class WACZParser implements Parser {

    private static final Set<MediaType> SUPPORTED_TYPES = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(MediaType.application("x-wacz"))));

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    @Override
    public void parse(InputStream stream, ContentHandler handler, Metadata metadata,
                      ParseContext context) throws IOException, SAXException, TikaException {

        XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
        xhtml.startDocument();
        EmbeddedDocumentExtractor embeddedDocumentExtractor =
                EmbeddedDocumentUtil.getEmbeddedDocumentExtractor(context);
        if (stream instanceof TikaInputStream) {
            ZipFile zip = (ZipFile) ((TikaInputStream) stream).getOpenContainer();
            if (zip == null && ((TikaInputStream)stream).hasFile()) {
                zip = new ZipFile(((TikaInputStream)stream).getFile());
            }
            if (zip != null) {
                try {
                    processZip(zip, xhtml, metadata, embeddedDocumentExtractor);
                } finally {
                    zip.close();
                }
            } else {
                processStream(stream, xhtml, metadata, embeddedDocumentExtractor);
            }
        } else {
            processStream(stream, xhtml, metadata, embeddedDocumentExtractor);
        }
        xhtml.endDocument();
    }

    private void processStream(InputStream stream, XHTMLContentHandler xhtml, Metadata metadata,
                               EmbeddedDocumentExtractor ex) throws SAXException, IOException {
        try (ZipArchiveInputStream zais = new ZipArchiveInputStream(
                CloseShieldInputStream.wrap(stream))) {
            ZipArchiveEntry zae = zais.getNextZipEntry();
            while (zae != null) {
                String name = zae.getName();
                if (name.startsWith("archive/")) {
                    name = name.substring(8);
                    processWARC(zais, zae, name, xhtml, metadata, ex);
                } else if ("datapackage.json".equals(name)) {
                    //no-op
                    processDataPackage(zais, zae, xhtml, metadata);
                }
                //TODO -- process pages (jsonl); process indexes?!

                zae = zais.getNextZipEntry();
            }
        }
    }

    private void processDataPackage(InputStream is, ZipArchiveEntry zae,
                                    XHTMLContentHandler xhtml, Metadata metadata)
            throws IOException {
        //no-op
    }

    private void processWARC(InputStream zais, ZipArchiveEntry zae,
                             String name, XHTMLContentHandler xhtml, Metadata parentMetadata,
                             EmbeddedDocumentExtractor ex) throws IOException, SAXException {
        Metadata metadata = new Metadata();
        metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, name);
        metadata.set(Metadata.CONTENT_LENGTH, Long.toString(zae.getSize()));
        try (InputStream inputStream = getMaybeGzipInputStream(TikaInputStream.get(zais))) {
            if (ex.shouldParseEmbedded(metadata)) {
                ex.parseEmbedded(inputStream, xhtml, metadata, true);
            }
        }
    }

    private InputStream getMaybeGzipInputStream(InputStream is) throws IOException {
        is.mark(2);
        byte[] firstTwo = new byte[2];
        try {
            IOUtils.readFully(is, firstTwo);
        } finally {
            is.reset();
        }
        int magic = ((firstTwo[1] & 0xff) << 8) | (firstTwo[0] & 0xff);
        if (GZIPInputStream.GZIP_MAGIC == magic) {
            return new GzipCompressorInputStream(is);
        } else {
            return is;
        }
    }

    private void processZip(ZipFile zip, XHTMLContentHandler xhtml, Metadata metadata,
                            EmbeddedDocumentExtractor ex) throws IOException, SAXException {

        Enumeration<ZipArchiveEntry> zaeEnum = zip.getEntries();
        while (zaeEnum.hasMoreElements()) {
            ZipArchiveEntry zae = zaeEnum.nextElement();
            String name = zae.getName();
            if (name.startsWith("archive/")) {
                name = name.substring(8);
                processWARC(TikaInputStream.get(zip.getInputStream(zae)), zae, name, xhtml,
                        metadata, ex);
            } else if ("datapackage.json".equals(name)) {
                //no-op
                processDataPackage(TikaInputStream.get(zip.getInputStream(zae)), zae, xhtml,
                        metadata);
            }
        }
    }


}
