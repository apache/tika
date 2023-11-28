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
package org.apache.tika.parser.xliff;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.EndDocumentShieldingContentHandler;
import org.apache.tika.sax.XHTMLContentHandler;

/**
 * Parser for XLZ Archives.
 */
public class XLZParser implements Parser {

    /**
     * Serial version UID
     */
    private static final long serialVersionUID = -1877314028666058564L;

    /**
     * Custom XLZ mime type.
     */
    private static final MediaType XLZ_CONTENT_TYPE = MediaType.application("x-xliff+zip");

    /**
     * Supported types set.
     */
    private static final Set<MediaType> SUPPORTED_TYPES = Collections.singleton(XLZ_CONTENT_TYPE);

    /**
     * XLF Extension
     */
    private static final String XLF = ".xlf";
    /**
     * Shared Parser instance.
     */
    private Parser xliffParser = new XLIFF12Parser();

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    public void parse(InputStream stream, ContentHandler baseHandler, Metadata metadata,
                      ParseContext context) throws IOException, SAXException, TikaException {

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

        if (handler.isEndDocumentWasCalled()) {
            handler.reallyEndDocument();
        }
    }

    private void handleZipStream(ZipInputStream zipStream, Metadata metadata, ParseContext context,
                                 EndDocumentShieldingContentHandler handler)
            throws IOException, TikaException, SAXException {

        ZipEntry entry = zipStream.getNextEntry();
        if (entry == null) {
            throw new IOException("No entries found in ZipInputStream");
        }
        while (entry != null) {
            if (entry.getName().contains(XLF)) {
                xliffParser.parse(zipStream, handler, metadata, context);
            }
            entry = zipStream.getNextEntry();
        }
    }

    private void handleZipFile(ZipFile zipFile, Metadata metadata, ParseContext context,
                               EndDocumentShieldingContentHandler handler)
            throws IOException, TikaException, SAXException {

        Enumeration<? extends ZipEntry> entries = zipFile.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            if (entry.getName().contains(XLF)) {
                xliffParser.parse(zipFile.getInputStream(entry), handler, metadata, context);
            }
        }
    }

}
