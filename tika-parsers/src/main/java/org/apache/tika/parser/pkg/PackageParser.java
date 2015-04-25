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
import java.util.Date;
import java.util.Set;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.commons.compress.archivers.StreamingNotSupportedException;
import org.apache.commons.compress.archivers.ar.ArArchiveInputStream;
import org.apache.commons.compress.archivers.cpio.CpioArchiveInputStream;
import org.apache.commons.compress.archivers.dump.DumpArchiveInputStream;
import org.apache.commons.compress.archivers.jar.JarArchiveInputStream;
import org.apache.commons.compress.archivers.sevenz.SevenZFile;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.zip.UnsupportedZipFeatureException;
import org.apache.commons.compress.archivers.zip.UnsupportedZipFeatureException.Feature;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.apache.tika.exception.EncryptedDocumentException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.extractor.ParsingEmbeddedDocumentExtractor;
import org.apache.tika.io.CloseShieldInputStream;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.PasswordProvider;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

/**
 * Parser for various packaging formats. Package entries will be written to
 * the XHTML event stream as &lt;div class="package-entry"&gt; elements that
 * contain the (optional) entry name as a &lt;h1&gt; element and the full
 * structured body content of the parsed entry.
 * <p>
 * User must have JCE Unlimited Strength jars installed for encryption to
 * work with 7Z files (see: COMPRESS-299 and TIKA-1521).  If the jars
 * are not installed, an IOException will be thrown, and potentially
 * wrapped in a TikaException.
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
    private static final MediaType SEVENZ = MediaType.application("x-7z-compressed");

    private static final Set<MediaType> SUPPORTED_TYPES =
            MediaType.set(ZIP, JAR, AR, CPIO, DUMP, TAR, SEVENZ);

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
        } else if (stream instanceof SevenZWrapper) {
            return SEVENZ;
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
       
        // Ensure that the stream supports the mark feature
        if (! TikaInputStream.isTikaInputStream(stream))
            stream = new BufferedInputStream(stream);
        
        
        TemporaryResources tmp = new TemporaryResources();
        ArchiveInputStream ais = null;
        try {
            ArchiveStreamFactory factory = context.get(ArchiveStreamFactory.class, new ArchiveStreamFactory());
            // At the end we want to close the archive stream to release
            // any associated resources, but the underlying document stream
            // should not be closed
            ais = factory.createArchiveInputStream(new CloseShieldInputStream(stream));
            
        } catch (StreamingNotSupportedException sne) {
            // Most archive formats work on streams, but a few need files
            if (sne.getFormat().equals(ArchiveStreamFactory.SEVEN_Z)) {
                // Rework as a file, and wrap
                stream.reset();
                TikaInputStream tstream = TikaInputStream.get(stream, tmp);
                
                // Seven Zip suports passwords, was one given?
                String password = null;
                PasswordProvider provider = context.get(PasswordProvider.class);
                if (provider != null) {
                    password = provider.getPassword(metadata);
                }
                
                SevenZFile sevenz;
                if (password == null) {
                    sevenz = new SevenZFile(tstream.getFile());
                } else {
                    sevenz = new SevenZFile(tstream.getFile(), password.getBytes("UnicodeLittleUnmarked"));
                }
                
                // Pending a fix for COMPRESS-269 / TIKA-1525, this bit is a little nasty
                ais = new SevenZWrapper(sevenz);
            } else {
                tmp.close();
                throw new TikaException("Unknown non-streaming format " + sne.getFormat(), sne);
            }
        } catch (ArchiveException e) {
            tmp.close();
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
        } catch (UnsupportedZipFeatureException zfe) {
            // If it's an encrypted document of unknown password, report as such
            if (zfe.getFeature() == Feature.ENCRYPTION) {
                throw new EncryptedDocumentException(zfe);
            }
            // Otherwise fall through to raise the exception as normal
        } catch (IOException ie) {
            // Is this a password protection error?
            // (COMPRESS-298 should give a nicer way when implemented, see TIKA-1525)
            if ("Cannot read encrypted files without a password".equals(ie.getMessage())) {
                throw new EncryptedDocumentException();
            }
            // Otherwise fall through to raise the exception as normal
            throw ie;
        } finally {
            ais.close();
            tmp.close();
        }

        xhtml.endDocument();
    }

    private void parseEntry(
            ArchiveInputStream archive, ArchiveEntry entry,
            EmbeddedDocumentExtractor extractor, XHTMLContentHandler xhtml)
            throws SAXException, IOException, TikaException {
        String name = entry.getName();
        if (archive.canReadEntryData(entry)) {
            // Fetch the metadata on the entry contained in the archive
            Metadata entrydata = handleEntryMetadata(name, null, 
                    entry.getLastModifiedDate(), entry.getSize(), xhtml);
            
            // Recurse into the entry if desired
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
    
    protected static Metadata handleEntryMetadata(
            String name, Date createAt, Date modifiedAt,
            Long size, XHTMLContentHandler xhtml)
            throws SAXException, IOException, TikaException {
        Metadata entrydata = new Metadata();
        if (createAt != null) {
            entrydata.set(TikaCoreProperties.CREATED, createAt);
        }
        if (modifiedAt != null) {
            entrydata.set(TikaCoreProperties.MODIFIED, modifiedAt);
        }
        if (size != null) {
            entrydata.set(Metadata.CONTENT_LENGTH, Long.toString(size));
        }
        if (name != null && name.length() > 0) {
            name = name.replace("\\", "/");
            entrydata.set(Metadata.RESOURCE_NAME_KEY, name);
            AttributesImpl attributes = new AttributesImpl();
            attributes.addAttribute("", "class", "class", "CDATA", "embedded");
            attributes.addAttribute("", "id", "id", "CDATA", name);
            xhtml.startElement("div", attributes);
            xhtml.endElement("div");

            entrydata.set(Metadata.EMBEDDED_RELATIONSHIP_ID, name);
        }
        return entrydata;
    }

    // Pending a fix for COMPRESS-269, we have to wrap ourselves
    private static class SevenZWrapper extends ArchiveInputStream {
        private SevenZFile file;
        private SevenZWrapper(SevenZFile file) {
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

        @Override
        public ArchiveEntry getNextEntry() throws IOException {
            return file.getNextEntry();
        }
        
        @Override
        public void close() throws IOException {
            file.close();
        }
    }
}
