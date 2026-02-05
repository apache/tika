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

import static org.apache.tika.detect.zip.PackageConstants.AR;
import static org.apache.tika.detect.zip.PackageConstants.ARJ;
import static org.apache.tika.detect.zip.PackageConstants.CPIO;
import static org.apache.tika.detect.zip.PackageConstants.DUMP;
import static org.apache.tika.detect.zip.PackageConstants.GTAR;
import static org.apache.tika.detect.zip.PackageConstants.TAR;

import java.io.IOException;
import java.util.Set;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.commons.compress.archivers.ar.ArArchiveInputStream;
import org.apache.commons.compress.archivers.arj.ArjArchiveInputStream;
import org.apache.commons.compress.archivers.cpio.CpioArchiveInputStream;
import org.apache.commons.compress.archivers.dump.DumpArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import org.apache.tika.config.TikaComponent;
import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.extractor.EmbeddedDocumentUtil;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.XHTMLContentHandler;

/**
 * Parser for streaming archive formats: AR, ARJ, CPIO, DUMP, TAR.
 * <p>
 * Package entries will be written to the XHTML event stream as
 * &lt;div class="package-entry"&gt; elements that contain the (optional)
 * entry name as a &lt;h1&gt; element and the full structured body content
 * of the parsed entry.
 * <p>
 * For ZIP/JAR archives, see {@link ZipParser}.
 * For 7z archives, see {@link SevenZParser}.
 */
@TikaComponent
public class PackageParser extends AbstractArchiveParser {

    private static final long serialVersionUID = -5331043266963888708L;

    private static final Set<MediaType> SUPPORTED_TYPES =
            MediaType.set(AR, ARJ, CPIO, DUMP, TAR);

    public PackageParser() {
        super();
    }

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    @Override
    public void parse(TikaInputStream tis, ContentHandler handler, Metadata metadata,
                      ParseContext context) throws IOException, SAXException, TikaException {
        tis.setCloseShield();
        try {
            doParse(tis, handler, metadata, context);
        } finally {
            tis.removeCloseShield();
        }
    }

    private void doParse(TikaInputStream tis, ContentHandler handler, Metadata metadata,
                         ParseContext context) throws TikaException, IOException, SAXException {
        ArchiveInputStream ais;
        try {
            ArchiveStreamFactory factory =
                    context.get(ArchiveStreamFactory.class, new ArchiveStreamFactory());
            ais = factory.createArchiveInputStream(tis);
        } catch (ArchiveException e) {
            throw new TikaException("Unable to unpack document stream", e);
        }

        updateMediaType(ais, metadata);

        EmbeddedDocumentExtractor extractor =
                EmbeddedDocumentUtil.getEmbeddedDocumentExtractor(context);

        XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata, context);
        xhtml.startDocument();

        try {
            ArchiveEntry entry = ais.getNextEntry();
            while (entry != null) {
                if (!entry.isDirectory()) {
                    parseEntry(ais, entry, extractor, metadata, xhtml, context);
                }
                entry = ais.getNextEntry();
            }
        } finally {
            ais.close();
            xhtml.endDocument();
        }
    }

    private void updateMediaType(ArchiveInputStream ais, Metadata metadata) {
        MediaType type = getMediaType(ais);
        if (type.equals(MediaType.OCTET_STREAM)) {
            return;
        }

        String incomingContentTypeString = metadata.get(Metadata.CONTENT_TYPE);
        if (incomingContentTypeString == null) {
            metadata.set(Metadata.CONTENT_TYPE, type.toString());
            return;
        }

        MediaType incomingMediaType = MediaType.parse(incomingContentTypeString);
        if (incomingMediaType == null) {
            metadata.set(Metadata.CONTENT_TYPE, type.toString());
            return;
        }

        // Don't overwrite if incoming type is a TAR specialization (e.g., gtar)
        if (!incomingMediaType.equals(GTAR)) {
            metadata.set(Metadata.CONTENT_TYPE, type.toString());
        }
    }

    private static MediaType getMediaType(ArchiveInputStream stream) {
        if (stream instanceof ArArchiveInputStream) {
            return AR;
        } else if (stream instanceof ArjArchiveInputStream) {
            return ARJ;
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

    private void parseEntry(ArchiveInputStream archive, ArchiveEntry entry,
                            EmbeddedDocumentExtractor extractor, Metadata parentMetadata,
                            XHTMLContentHandler xhtml, ParseContext context)
            throws SAXException, IOException, TikaException {

        String name = entry.getName();

        if (archive.canReadEntryData(entry)) {
            Metadata entrydata = handleEntryMetadata(
                    name, null, entry.getLastModifiedDate(), entry.getSize(),
                    xhtml, context);

            if (extractor.shouldParseEmbedded(entrydata)) {
                TemporaryResources tmp = new TemporaryResources();
                try {
                    TikaInputStream tis = TikaInputStream.get(archive, tmp, entrydata);
                    extractor.parseEmbedded(tis, xhtml, entrydata, new ParseContext(), true);
                } finally {
                    tmp.dispose();
                }
            }
        } else {
            EmbeddedDocumentUtil.recordEmbeddedStreamException(
                    new TikaException("Can't read archive stream (" + name + ")"),
                    parentMetadata);
            if (name != null && !name.isEmpty()) {
                xhtml.element("p", name);
            }
        }
    }
}
