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

import static org.apache.tika.metadata.HttpHeaders.CONTENT_TYPE;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.commons.compress.archivers.ar.ArArchiveInputStream;
import org.apache.commons.compress.archivers.cpio.CpioArchiveInputStream;
import org.apache.commons.compress.archivers.dump.DumpArchiveInputStream;
import org.apache.commons.compress.archivers.jar.JarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.extractor.ParsingEmbeddedDocumentExtractor;
import org.apache.tika.io.CloseShieldInputStream;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * Parser for various packaging formats. Package entries will be written to
 * the XHTML event stream as &lt;div class="package-entry"&gt; elements that
 * contain the (optional) entry name as a &lt;h1&gt; element and the full
 * structured body content of the parsed entry.
 */
public class PackageParser extends AbstractParser {

    /** Serial version UID */
    private static final long serialVersionUID = -5331043266963888708L;

    private static final MediaType ZIP = MediaType.APPLICATION_ZIP;
    private static final MediaType JAR = MediaType.application("java-archive");
    private static final MediaType AR = MediaType.application("x-archive");
    private static final MediaType CPIO = MediaType.application("x-cpio");
    private static final MediaType DUMP = MediaType.application("x-tika-unix-dump");
    private static final MediaType TAR = MediaType.application("x-tar");

    private static final Set<MediaType> SUPPORTED_TYPES =
            MediaType.set(ZIP, JAR, AR, CPIO, DUMP, TAR);

    static MediaType getMediaType(ArchiveInputStream stream) {
        if (stream instanceof JarArchiveInputStream) {
            return JAR;
        } else if (stream instanceof ZipArchiveInputStream) {
            return ZIP;
        } else if (stream instanceof ArArchiveInputStream) {
            return AR;
        } else if (stream instanceof CpioArchiveInputStream) {
            return CPIO;
        } else if (stream instanceof DumpArchiveInputStream) {
            return DUMP;
        } else if (stream instanceof TarArchiveInputStream) {
            return TAR;
        } else {
            return MediaType.OCTET_STREAM;
        }
    }

    static boolean isZipArchive(MediaType type) {
        return type.equals(ZIP) || type.equals(JAR);
    }

    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    public void parse(
            InputStream stream, ContentHandler handler,
            Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {
        // At the end we want to close the archive stream to release
        // any associated resources, but the underlying document stream
        // should not be closed
        stream = new CloseShieldInputStream(stream);

        // Ensure that the stream supports the mark feature
        stream = new BufferedInputStream(stream);

        ArchiveInputStream ais;
        try {
            ArchiveStreamFactory factory = new ArchiveStreamFactory();
            ais = factory.createArchiveInputStream(stream);
        } catch (ArchiveException e) {
            throw new TikaException("Unable to unpack document stream", e);
        }

        MediaType type = getMediaType(ais);
        if (!type.equals(MediaType.OCTET_STREAM)) {
            metadata.set(CONTENT_TYPE, type.toString());
        }

        // Use the delegate parser to parse the contained document
        EmbeddedDocumentExtractor extractor = context.get(
                EmbeddedDocumentExtractor.class,
                new ParsingEmbeddedDocumentExtractor(context));

        XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
        xhtml.startDocument();

        try {
            ArchiveEntry entry = ais.getNextEntry();
            while (entry != null) {
                if (!entry.isDirectory()) {
                    parseEntry(ais, entry, extractor, xhtml);
                }
                entry = ais.getNextEntry();
            }
        } finally {
            ais.close();
        }

        xhtml.endDocument();
    }

    private void parseEntry(
            ArchiveInputStream archive, ArchiveEntry entry,
            EmbeddedDocumentExtractor extractor, XHTMLContentHandler xhtml)
            throws SAXException, IOException, TikaException {
        String name = entry.getName();
        if (archive.canReadEntryData(entry)) {
            Metadata entrydata = new Metadata();
            if (name != null && name.length() > 0) {
                entrydata.set(Metadata.RESOURCE_NAME_KEY, name);
            }
            if (extractor.shouldParseEmbedded(entrydata)) {
                // For detectors to work, we need a mark/reset supporting
                // InputStream, which ArchiveInputStream isn't, so wrap
                TemporaryResources tmp = new TemporaryResources();
                try {
                    TikaInputStream tis = TikaInputStream.get(archive, tmp);
                    extractor.parseEmbedded(tis, xhtml, entrydata, true);
                } finally {
                    tmp.dispose();
                }
            }
        } else if (name != null && name.length() > 0) {
            xhtml.element("p", name);
        }
    }

}
