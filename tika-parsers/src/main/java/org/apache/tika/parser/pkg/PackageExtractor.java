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

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.GZIPInputStream;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.ar.ArArchiveInputStream;
import org.apache.commons.compress.archivers.cpio.CpioArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;
import org.apache.commons.compress.compressors.gzip.GzipUtils;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.CloseShieldInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.EmptyParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.apache.tika.sax.EmbeddedContentHandler;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * Abstract base class for parsers that deal with package formats.
 * Subclasses can call the
 * {@link #parseArchive(ArchiveInputStream, ContentHandler, Metadata, ParseContext)}
 * method to parse the package stream. Package entries will be written
 * to the XHTML event stream as &lt;div class="package-entry"&gt; elements
 * that contain the (optional) entry name as a &lt;h1&gt; element and the full
 * structured body content of the parsed entry.
 */
class PackageExtractor {

    private final ContentHandler handler;

    private final Metadata metadata;

    private final ParseContext context;

    private final Parser parser;

    public PackageExtractor(
            ContentHandler handler, Metadata metadata, ParseContext context) {
        this.handler = handler;
        this.metadata = metadata;
        this.context = context;
        this.parser = context.get(Parser.class, EmptyParser.INSTANCE);
    }

    public void parse(InputStream stream)
            throws IOException, SAXException, TikaException {
        XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
        xhtml.startDocument();

        // At the end we want to close the package/compression stream to
        // release any associated resources, but the underlying document
        // stream should not be closed
        stream = new CloseShieldInputStream(stream);

        // Capture the first byte to determine the packaging/compression format
        if (!stream.markSupported()) {
            stream = new BufferedInputStream(stream);
        }
        stream.mark(1);
        int b = stream.read();
        stream.reset();

        if (b == 'B') { // BZh...
            metadata.set(Metadata.CONTENT_TYPE, "application/x-bzip");
            parseBZip2(stream, xhtml);
        } else if (b == 0x1f) { // \037\213...
            metadata.set(Metadata.CONTENT_TYPE, "application/x-gzip");
            parseGZIP(stream, xhtml);
        } else if (b == 'P') { // PK\003\004...
            metadata.set(Metadata.CONTENT_TYPE, "application/zip");
            parse(new ZipArchiveInputStream(stream), xhtml);
        } else if (b == '0' || b == 0x71 || b == 0xc7) { // looks like cpio
            metadata.set(Metadata.CONTENT_TYPE, "application/x-cpio");
            parse(new CpioArchiveInputStream(stream), xhtml);
        } else if (b == '=') { // =<ar> or =!<arch>
            metadata.set(Metadata.CONTENT_TYPE, "application/x-archive");
            parse(new ArArchiveInputStream(stream), xhtml);
        } else { // assume tar
            metadata.set(Metadata.CONTENT_TYPE, "application/x-tar");
            parse(new TarArchiveInputStream(stream), xhtml);
        }

        xhtml.endDocument();
    }

    private void parseGZIP(InputStream stream, XHTMLContentHandler xhtml)
            throws IOException, SAXException, TikaException {
        InputStream gzip = new GZIPInputStream(stream);
        try {
            Metadata entrydata = new Metadata();
            String name = metadata.get(Metadata.RESOURCE_NAME_KEY);
            if (name != null && name.length() > 0) {
                entrydata.set(
                        Metadata.RESOURCE_NAME_KEY,
                        GzipUtils.getUncompressedFilename(name));
            }
            // Use the delegate parser to parse the compressed document
            parser.parse(
                    new CloseShieldInputStream(gzip),
                    new EmbeddedContentHandler(
                            new BodyContentHandler(xhtml)),
                    entrydata, context);
        } finally {
            gzip.close();
        }
    }

    private void parseBZip2(InputStream stream, XHTMLContentHandler xhtml)
            throws IOException, SAXException, TikaException {
        InputStream bzip2 = new BZip2CompressorInputStream(stream);
        try {
            Metadata entrydata = new Metadata();
            String name = metadata.get(Metadata.RESOURCE_NAME_KEY);
            if (name != null) {
                if (name.endsWith(".tbz")) {
                    name = name.substring(0, name.length() - 4) + ".tar";
                } else if (name.endsWith(".tbz2")) {
                    name = name.substring(0, name.length() - 5) + ".tar";
                } else if (name.endsWith(".bz")) {
                    name = name.substring(0, name.length() - 3);
                } else if (name.endsWith(".bz2")) {
                    name = name.substring(0, name.length() - 4);
                }
                entrydata.set(Metadata.RESOURCE_NAME_KEY, name);
            }
            // Use the delegate parser to parse the compressed document
            parser.parse(
                    new CloseShieldInputStream(bzip2),
                    new EmbeddedContentHandler(
                            new BodyContentHandler(xhtml)),
                    entrydata, context);
        } finally {
            bzip2.close();
        }
    }

    /**
     * Parses the given stream as a package of multiple underlying files.
     * The package entries are parsed using the delegate parser instance.
     * It is not an error if the entry can not be parsed, in that case
     * just the entry name (if given) is emitted.
     *
     * @param stream package stream
     * @param handler content handler
     * @param metadata package metadata
     * @throws IOException if an IO error occurs
     * @throws SAXException if a SAX error occurs
     */
    public void parse(ArchiveInputStream archive, XHTMLContentHandler xhtml)
            throws IOException, SAXException {
        try {
            ArchiveEntry entry = archive.getNextEntry();
            while (entry != null) {
                if (!entry.isDirectory()) {
                    xhtml.startElement("div", "class", "package-entry");
                    Metadata entrydata = new Metadata();
                    String name = entry.getName();
                    if (name != null && name.length() > 0) {
                        entrydata.set(Metadata.RESOURCE_NAME_KEY, name);
                        xhtml.element("h1", name);
                    }
                    try {
                        // Use the delegate parser to parse this entry
                        parser.parse(
                                new CloseShieldInputStream(archive),
                                new EmbeddedContentHandler(
                                        new BodyContentHandler(xhtml)),
                                        entrydata, context);
                    } catch (TikaException e) {
                        // Could not parse the entry, just skip the content
                    }
                    xhtml.endElement("div");
                }
                entry = archive.getNextEntry();
            }
        } finally {
            archive.close();
        }
    }

}
