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
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;

import org.apache.commons.compress.PasswordRequiredException;
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
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.apache.commons.io.input.CloseShieldInputStream;
import org.apache.tika.exception.EncryptedDocumentException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.extractor.EmbeddedDocumentUtil;
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
    private static final MediaType ARJ = MediaType.application("x-arj");
    private static final MediaType CPIO = MediaType.application("x-cpio");
    private static final MediaType DUMP = MediaType.application("x-tika-unix-dump");
    private static final MediaType TAR = MediaType.application("x-tar");
    private static final MediaType SEVENZ = MediaType.application("x-7z-compressed");

    private static final MediaType TIKA_OOXML = MediaType.application("x-tika-ooxml");
    private static final MediaType GTAR = MediaType.application("x-gtar");
    private static final MediaType KMZ = MediaType.application("vnd.google-earth.kmz");


    private static final Set<MediaType> SUPPORTED_TYPES =
            MediaType.set(ZIP, JAR, AR, ARJ, CPIO, DUMP, TAR, SEVENZ);

    //We used to avoid overwriting file types if the file type
    //was a specialization of zip/tar.  We determined specialization of zip
    //via TikaConfig at parse time.
    //However, TIKA-2483 showed that TikaConfig is not serializable
    //and this causes an exception in the ForkParser.
    //The following is an inelegant hack, but until we can serialize TikaConfig,
    //or dramatically rework the ForkParser to avoid serialization
    //of parsers, this is what we have.
    //There is at least a test in PackageParserTest that makes sure that we
    //keep this list updated.
    static final Set<MediaType> PACKAGE_SPECIALIZATIONS =
            loadPackageSpecializations();

    // the mark limit used for stream
    private static final int MARK_LIMIT = 100 * 1024 * 1024; // 100M

    // count of the entries in the archive, this is used for zip requires Data Descriptor
    private int entryCnt = 0;

    static final Set<MediaType> loadPackageSpecializations() {
        Set<MediaType> zipSpecializations = new HashSet<>();
        for (String mediaTypeString : new String[]{
                //specializations of ZIP
                "application/bizagi-modeler",
                "application/epub+zip",
                "application/java-archive",
                "application/vnd.adobe.air-application-installer-package+zip",
                "application/vnd.android.package-archive",
                "application/vnd.apple.iwork",
                "application/vnd.apple.keynote",
                "application/vnd.apple.numbers",
                "application/vnd.apple.pages",
                "application/vnd.etsi.asic-e+zip",
                "application/vnd.etsi.asic-s+zip",
                "application/vnd.google-earth.kmz",
                "application/vnd.mindjet.mindmanager",
                "application/vnd.ms-excel.addin.macroenabled.12",
                "application/vnd.ms-excel.sheet.binary.macroenabled.12",
                "application/vnd.ms-excel.sheet.macroenabled.12",
                "application/vnd.ms-excel.template.macroenabled.12",
                "application/vnd.ms-powerpoint.addin.macroenabled.12",
                "application/vnd.ms-powerpoint.presentation.macroenabled.12",
                "application/vnd.ms-powerpoint.slide.macroenabled.12",
                "application/vnd.ms-powerpoint.slideshow.macroenabled.12",
                "application/vnd.ms-powerpoint.template.macroenabled.12",
                "application/vnd.ms-visio.drawing",
                "application/vnd.ms-visio.drawing.macroenabled.12",
                "application/vnd.ms-visio.stencil",
                "application/vnd.ms-visio.stencil.macroenabled.12",
                "application/vnd.ms-visio.template",
                "application/vnd.ms-visio.template.macroenabled.12",
                "application/vnd.ms-word.document.macroenabled.12",
                "application/vnd.ms-word.template.macroenabled.12",
                "application/vnd.ms-xpsdocument",
                "application/vnd.oasis.opendocument.formula",
                "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                "application/vnd.openxmlformats-officedocument.presentationml.slide",
                "application/vnd.openxmlformats-officedocument.presentationml.slideshow",
                "application/vnd.openxmlformats-officedocument.presentationml.template",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.template",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.template",
                "application/x-ibooks+zip",
                "application/x-itunes-ipa",
                "application/x-tika-iworks-protected",
                "application/x-tika-java-enterprise-archive",
                "application/x-tika-java-web-archive",
                "application/x-tika-ooxml",
                "application/x-tika-ooxml-protected",
                "application/x-tika-visio-ooxml",
                "application/x-xliff+zip",
                "application/x-xmind",
                "model/vnd.dwfx+xps",
                "application/vnd.sun.xml.calc",
                "application/vnd.sun.xml.writer",
                "application/vnd.sun.xml.writer.template",
                "application/vnd.sun.xml.draw",
                "application/vnd.sun.xml.impress",
                "application/vnd.openofficeorg.autotext",
                "application/vnd.adobe.indesign-idml-package",


                "application/x-gtar" //specialization of tar
        }) {
            zipSpecializations.add(MediaType.parse(mediaTypeString));
        }
        return Collections.unmodifiableSet(zipSpecializations);
    }

    @Deprecated
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

    static MediaType getMediaType(String name) {
        if (ArchiveStreamFactory.JAR.equals(name)) {
            return JAR;
        } else if (ArchiveStreamFactory.ZIP.equals(name)) {
            return ZIP;
        } else if (ArchiveStreamFactory.AR.equals(name)) {
            return AR;
        } else if (ArchiveStreamFactory.ARJ.equals(name)) {
            return ARJ;
        } else if (ArchiveStreamFactory.CPIO.equals(name)) {
            return CPIO;
        } else if (ArchiveStreamFactory.DUMP.equals(name)) {
            return DUMP;
        } else if (ArchiveStreamFactory.TAR.equals(name)) {
            return TAR;
        } else if (ArchiveStreamFactory.SEVEN_Z.equals(name)) {
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
        if (! stream.markSupported()) {
            stream = new BufferedInputStream(stream);
        }
        
        TemporaryResources tmp = new TemporaryResources();
        ArchiveInputStream ais = null;
        String encoding = null;
        try {
            ArchiveStreamFactory factory = context.get(ArchiveStreamFactory.class, new ArchiveStreamFactory());
            encoding = factory.getEntryEncoding();
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
                try{
                    if (password == null) {
                        sevenz = new SevenZFile(tstream.getFile());
                    } else {
                        sevenz = new SevenZFile(tstream.getFile(), password.getBytes("UnicodeLittleUnmarked"));
                    }
                }catch(PasswordRequiredException e){
                    throw new EncryptedDocumentException(e);
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

        updateMediaType(ais, metadata);
        // Use the delegate parser to parse the contained document
        EmbeddedDocumentExtractor extractor = EmbeddedDocumentUtil.getEmbeddedDocumentExtractor(context);

        XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
        xhtml.startDocument();

        // mark before we start parsing entries for potential reset
        stream.mark(MARK_LIMIT);
        entryCnt = 0;
        try {
            parseEntries(false, ais, metadata, extractor, xhtml);
        } catch (UnsupportedZipFeatureException zfe) {
            // If this is a zip archive which requires a data descriptor, parse it again
            if (zfe.getFeature() == Feature.DATA_DESCRIPTOR) {
                // Close archive input stream and create a new one that could handle data descriptor
                ais.close();
                // An exception would be thrown if MARK_LIMIT is not big enough
                stream.reset();
                ais = new ZipArchiveInputStream(new CloseShieldInputStream(stream), encoding, true, true);
                parseEntries(true, ais, metadata, extractor, xhtml);
            }
        } finally {
            ais.close();
            tmp.close();
            // reset the entryCnt
            entryCnt = 0;
        }

        xhtml.endDocument();
    }

    /**
     * Parse the entries of the zip archive
     *
     * @param shouldUseDataDescriptor indicates if a data descriptor is required or not
     * @param ais archive input stream
     * @param metadata document metadata (input and output)
     * @param extractor the delegate parser
     * @param xhtml the xhtml handler
     * @throws TikaException if the document could not be parsed
     * @throws IOException if a UnsupportedZipFeatureException is met
     * @throws SAXException if the SAX events could not be processed
     */
    private void parseEntries(boolean shouldUseDataDescriptor, ArchiveInputStream ais, Metadata metadata,
                              EmbeddedDocumentExtractor extractor, XHTMLContentHandler xhtml)
            throws TikaException, IOException, SAXException {
        try {
            ArchiveEntry entry = ais.getNextEntry();
            while (entry != null) {
                if (shouldUseDataDescriptor && entryCnt > 0) {
                    // With shouldUseDataDescriptor being true, we are reading
                    // the zip once again. The number of entryCnt entries have
                    // already been parsed in the last time, so we can just
                    // skip these entries.
                    entryCnt--;
                    entry = ais.getNextEntry();
                    continue;
                }

                if (!entry.isDirectory()) {
                    parseEntry(ais, entry, extractor, metadata, xhtml);
                }

                if (!shouldUseDataDescriptor) {
                    // Record the number of entries we have read, this is used
                    // for zip archives using Data Descriptor. It's used for
                    // skipping the entries we have already read
                    entryCnt++;
                }

                entry = ais.getNextEntry();
            }
        } catch (UnsupportedZipFeatureException zfe) {

            // If it's an encrypted document of unknown password, report as such
            if (zfe.getFeature() == Feature.ENCRYPTION) {
                throw new EncryptedDocumentException(zfe);
            }

            if (zfe.getFeature() == Feature.DATA_DESCRIPTOR) {
                throw zfe;
            }
            // Otherwise throw the exception
            throw new TikaException("UnsupportedZipFeature", zfe);
        } catch (PasswordRequiredException pre) {
            throw new EncryptedDocumentException(pre);
        }
    }

    private void updateMediaType(ArchiveInputStream ais, Metadata metadata) {
        MediaType type = getMediaType(ais);
        if (type.equals(MediaType.OCTET_STREAM)) {
            return;
        }

        //now see if the user or an earlier step has passed in a content type
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

        if (! PACKAGE_SPECIALIZATIONS.contains(incomingMediaType)) {
            metadata.set(Metadata.CONTENT_TYPE, type.toString());
        }
    }

    private void parseEntry(
            ArchiveInputStream archive, ArchiveEntry entry,
            EmbeddedDocumentExtractor extractor, Metadata parentMetadata, XHTMLContentHandler xhtml)
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
        } else {
            name = (name == null) ? "" : name;
            if (entry instanceof ZipArchiveEntry) {
                ZipArchiveEntry zipArchiveEntry = (ZipArchiveEntry) entry;
                boolean usesEncryption = zipArchiveEntry.getGeneralPurposeBit().usesEncryption();
                if (usesEncryption) {
                    EmbeddedDocumentUtil.recordEmbeddedStreamException(
                            new EncryptedDocumentException("stream ("+name+") is encrypted"), parentMetadata);
                }

                // do not write to the handler if UnsupportedZipFeatureException.Feature.DATA_DESCRIPTOR
                // is met, we will catch this exception and read the zip archive once again
                boolean usesDataDescriptor = zipArchiveEntry.getGeneralPurposeBit().usesDataDescriptor();
                if (usesDataDescriptor && zipArchiveEntry.getMethod() == ZipEntry.STORED) {
                    throw new UnsupportedZipFeatureException(UnsupportedZipFeatureException.Feature.DATA_DESCRIPTOR, zipArchiveEntry);
                }
            } else {
                EmbeddedDocumentUtil.recordEmbeddedStreamException(
                        new TikaException("Can't read archive stream ("+name+")"), parentMetadata);
            }
            if (name.length() > 0) {
                xhtml.element("p", name);
            }
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
