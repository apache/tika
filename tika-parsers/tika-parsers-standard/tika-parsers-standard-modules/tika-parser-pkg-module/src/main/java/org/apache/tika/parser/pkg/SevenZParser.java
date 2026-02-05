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

import static org.apache.tika.detect.zip.PackageConstants.SEVENZ;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Set;

import org.apache.commons.compress.PasswordRequiredException;
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry;
import org.apache.commons.compress.archivers.sevenz.SevenZFile;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import org.apache.tika.config.TikaComponent;
import org.apache.tika.exception.EncryptedDocumentException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.extractor.EmbeddedDocumentUtil;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.PasswordProvider;
import org.apache.tika.sax.XHTMLContentHandler;

/**
 * Parser for 7z (Seven Zip) archives.
 * <p>
 * This parser requires file-based access (not streaming) because
 * the 7z format requires random access to the archive.
 * <p>
 * User must have JCE Unlimited Strength jars installed for encryption
 * to work with 7Z files (see: COMPRESS-299 and TIKA-1521). If the jars
 * are not installed, an IOException will be thrown, and potentially
 * wrapped in a TikaException.
 */
@TikaComponent
public class SevenZParser extends AbstractArchiveParser {

    private static final long serialVersionUID = -5331043266963888710L;

    private static final Set<MediaType> SUPPORTED_TYPES = Collections.singleton(SEVENZ);

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    @Override
    public void parse(TikaInputStream tis, ContentHandler handler, Metadata metadata,
                      ParseContext context) throws IOException, SAXException, TikaException {

        // Seven Zip supports passwords, was one given?
        String password = null;
        PasswordProvider provider = context.get(PasswordProvider.class);
        if (provider != null) {
            password = provider.getPassword(metadata);
        }

        SevenZFile sevenZFile;
        try {
            SevenZFile.Builder builder = new SevenZFile.Builder().setFile(tis.getFile());
            if (password == null) {
                sevenZFile = builder.get();
            } else {
                sevenZFile = builder.setPassword(password.toCharArray()).get();
            }
        } catch (PasswordRequiredException e) {
            throw new EncryptedDocumentException(e);
        }

        metadata.set(Metadata.CONTENT_TYPE, SEVENZ.toString());

        EmbeddedDocumentExtractor extractor =
                EmbeddedDocumentUtil.getEmbeddedDocumentExtractor(context);

        XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata, context);
        xhtml.startDocument();

        try {
            SevenZArchiveEntry entry = sevenZFile.getNextEntry();
            while (entry != null) {
                if (!entry.isDirectory()) {
                    parseEntry(sevenZFile, entry, extractor, metadata, xhtml, context);
                }
                entry = sevenZFile.getNextEntry();
            }
        } catch (PasswordRequiredException e) {
            throw new EncryptedDocumentException(e);
        } finally {
            sevenZFile.close();
            xhtml.endDocument();
        }
    }

    private void parseEntry(SevenZFile sevenZFile, SevenZArchiveEntry entry,
                            EmbeddedDocumentExtractor extractor, Metadata parentMetadata,
                            XHTMLContentHandler xhtml, ParseContext context)
            throws SAXException, IOException, TikaException {

        String name = entry.getName();
        Metadata entrydata = handleEntryMetadata(
                name,
                entry.getHasCreationDate() ? entry.getCreationDate() : null,
                entry.getHasLastModifiedDate() ? entry.getLastModifiedDate() : null,
                entry.getSize(),
                xhtml,
                context);

        if (extractor.shouldParseEmbedded(entrydata)) {
            TemporaryResources tmp = new TemporaryResources();
            try {
                TikaInputStream tis = TikaInputStream.get(
                        new SevenZEntryInputStream(sevenZFile), tmp, entrydata);
                extractor.parseEmbedded(tis, xhtml, entrydata, new ParseContext(), true);
            } finally {
                tmp.dispose();
            }
        }
    }

    /**
     * InputStream wrapper for reading the current entry from a SevenZFile.
     */
    private static class SevenZEntryInputStream extends InputStream {
        private final SevenZFile file;

        SevenZEntryInputStream(SevenZFile file) {
            this.file = file;
        }

        @Override
        public int read() throws IOException {
            return file.read();
        }

        @Override
        public int read(byte[] b) throws IOException {
            return file.read(b);
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            return file.read(b, off, len);
        }
    }
}
