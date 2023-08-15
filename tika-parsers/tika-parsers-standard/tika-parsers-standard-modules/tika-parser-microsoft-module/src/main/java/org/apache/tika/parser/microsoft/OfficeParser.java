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
package org.apache.tika.parser.microsoft;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.CloseShieldInputStream;
import org.apache.commons.io.input.UnsynchronizedByteArrayInputStream;
import org.apache.poi.hdgf.extractor.VisioTextExtractor;
import org.apache.poi.hpbf.extractor.PublisherTextExtractor;
import org.apache.poi.poifs.crypt.Decryptor;
import org.apache.poi.poifs.crypt.EncryptionInfo;
import org.apache.poi.poifs.filesystem.DirectoryEntry;
import org.apache.poi.poifs.filesystem.DirectoryNode;
import org.apache.poi.poifs.filesystem.Entry;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.apache.poi.poifs.macros.VBAMacroReader;
import org.apache.poi.util.LocaleUtil;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import org.apache.tika.detect.microsoft.POIFSContainerDetector;
import org.apache.tika.exception.EncryptedDocumentException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.extractor.EmbeddedDocumentUtil;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.PasswordProvider;
import org.apache.tika.parser.microsoft.ooxml.OOXMLParser;
import org.apache.tika.sax.BodyContentHandler;
import org.apache.tika.sax.EmbeddedContentHandler;
import org.apache.tika.sax.XHTMLContentHandler;
import org.apache.tika.utils.StringUtils;

/**
 * Defines a Microsoft document content extractor.
 */
public class OfficeParser extends AbstractOfficeParser {

    /**
     * Serial version UID
     */
    private static final long serialVersionUID = 7393462244028653479L;

    private static final Set<MediaType> SUPPORTED_TYPES = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(POIFSDocumentType.WORKBOOK.type,
                    POIFSDocumentType.OLE10_NATIVE.type, POIFSDocumentType.WORDDOCUMENT.type,
                    POIFSDocumentType.UNKNOWN.type, POIFSDocumentType.ENCRYPTED.type,
                    POIFSDocumentType.DRMENCRYPTED.type,
                    POIFSDocumentType.POWERPOINT.type, POIFSDocumentType.PUBLISHER.type,
                    POIFSDocumentType.PROJECT.type, POIFSDocumentType.VISIO.type,
                    // Works isn't supported
                    POIFSDocumentType.XLR.type, // but Works 7.0 Spreadsheet is
                    POIFSDocumentType.OUTLOOK.type, POIFSDocumentType.SOLIDWORKS_PART.type,
                    POIFSDocumentType.SOLIDWORKS_ASSEMBLY.type,
                    POIFSDocumentType.SOLIDWORKS_DRAWING.type)));

    /**
     * Helper to extract macros from an NPOIFS/vbaProject.bin
     * <p>
     * As of POI-3.15-final, there are still some bugs in VBAMacroReader.
     * For now, we are swallowing NPE and other runtime exceptions
     *
     * @param fs                        NPOIFS to extract from
     * @param xhtml                     SAX writer
     * @param embeddedDocumentExtractor extractor for embedded documents
     * @throws IOException  on IOException if it occurs during the extraction of the embedded doc
     * @throws SAXException on SAXException for writing to xhtml
     */
    public static void extractMacros(POIFSFileSystem fs, ContentHandler xhtml,
                                     EmbeddedDocumentExtractor embeddedDocumentExtractor)
            throws IOException, SAXException {

        VBAMacroReader reader = null;
        Map<String, String> macros = null;
        try {
            reader = new VBAMacroReader(fs);
            macros = reader.readMacros();
        } catch (SecurityException e) {
            throw e;
        } catch (Exception e) {
            Metadata m = new Metadata();
            m.set(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE,
                    TikaCoreProperties.EmbeddedResourceType.MACRO.toString());
            m.set(Metadata.CONTENT_TYPE, "text/x-vbasic");
            EmbeddedDocumentUtil.recordException(e, m);
            if (embeddedDocumentExtractor.shouldParseEmbedded(m)) {
                embeddedDocumentExtractor.parseEmbedded(
                        //pass in space character so that we don't trigger a zero-byte exception
                        new UnsynchronizedByteArrayInputStream(new byte[]{'\u0020'}), xhtml, m, true);
            }
            return;
        }
        for (Map.Entry<String, String> e : macros.entrySet()) {
            Metadata m = new Metadata();
            m.set(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE,
                    TikaCoreProperties.EmbeddedResourceType.MACRO.toString());
            m.set(Metadata.CONTENT_TYPE, "text/x-vbasic");
            if (!StringUtils.isBlank(e.getKey())) {
                m.set(TikaCoreProperties.RESOURCE_NAME_KEY, e.getKey());
            }
            if (embeddedDocumentExtractor.shouldParseEmbedded(m)) {
                embeddedDocumentExtractor.parseEmbedded(
                        new UnsynchronizedByteArrayInputStream(e.getValue().getBytes(StandardCharsets.UTF_8)),
                        xhtml, m, true);
            }
        }
    }

    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    /**
     * Extracts properties and text from an MS Document input stream
     */
    public void parse(InputStream stream, ContentHandler handler, Metadata metadata,
                      ParseContext context) throws IOException, SAXException, TikaException {

        configure(context);
        XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
        xhtml.startDocument();

        final DirectoryNode root;
        TikaInputStream tstream = TikaInputStream.cast(stream);
        POIFSFileSystem mustCloseFs = null;
        boolean isDirectoryNode = false;
        try {
            if (tstream == null) {
                mustCloseFs = new POIFSFileSystem(CloseShieldInputStream.wrap(stream));
                root = mustCloseFs.getRoot();
            } else {
                final Object container = tstream.getOpenContainer();
                if (container instanceof POIFSFileSystem) {
                    root = ((POIFSFileSystem) container).getRoot();
                } else if (container instanceof DirectoryNode) {
                    root = (DirectoryNode) container;
                    isDirectoryNode = true;
                } else {
                    POIFSFileSystem fs = null;
                    if (tstream.hasFile()) {
                        fs = new POIFSFileSystem(tstream.getFile(), true);
                    } else {
                        fs = new POIFSFileSystem(CloseShieldInputStream.wrap(tstream));
                    }
                    //tstream will close the fs, no need to close this below
                    tstream.setOpenContainer(fs);
                    root = fs.getRoot();

                }
            }
            parse(root, context, metadata, xhtml);
            OfficeParserConfig officeParserConfig = context.get(OfficeParserConfig.class);

            if (officeParserConfig.isExtractMacros()) {
                //now try to get macros.
                //Note that macros are handled separately for ppt in HSLFExtractor.

                //We might consider not bothering to check for macros in root,
                //if we know we're processing ppt based on content-type identified in metadata
                if (! isDirectoryNode) {
                    // if the "root" is a directory node, we assume that the macros have already
                    // been extracted from the parent's fileSystem -- TIKA-4116
                    extractMacros(root.getFileSystem(), xhtml, EmbeddedDocumentUtil.getEmbeddedDocumentExtractor(context));
                }

            }
        } finally {
            IOUtils.closeQuietly(mustCloseFs);
        }
        xhtml.endDocument();
    }

    protected void parse(DirectoryNode root, ParseContext context, Metadata metadata,
                         XHTMLContentHandler xhtml)
            throws IOException, SAXException, TikaException {

        // Parse summary entries first, to make metadata available early
        new SummaryExtractor(metadata).parseSummaries(root);

        // Parse remaining document entries
        POIFSDocumentType type = POIFSDocumentType.detectType(root);

        if (type != POIFSDocumentType.UNKNOWN) {
            setType(metadata, type.getType());
        }

        switch (type) {
            case SOLIDWORKS_PART:
            case SOLIDWORKS_ASSEMBLY:
            case SOLIDWORKS_DRAWING:
                break;
            case PUBLISHER:
                PublisherTextExtractor publisherTextExtractor = new PublisherTextExtractor(root);
                xhtml.element("p", publisherTextExtractor.getText());
                break;
            case WORDDOCUMENT:
                new WordExtractor(context, metadata).parse(root, xhtml);
                break;
            case POWERPOINT:
                new HSLFExtractor(context, metadata).parse(root, xhtml);
                break;
            case WORKBOOK:
            case XLR:
                Locale locale = context.get(Locale.class, LocaleUtil.getUserLocale());
                new ExcelExtractor(context, metadata).parse(root, xhtml, locale);
                break;
            case PROJECT:
                // We currently can't do anything beyond the metadata
                break;
            case VISIO:
                VisioTextExtractor visioTextExtractor = new VisioTextExtractor(root);
                for (String text : visioTextExtractor.getAllText()) {
                    xhtml.element("p", text);
                }
                break;
            case OUTLOOK:
                OutlookExtractor extractor = new OutlookExtractor(root, metadata, context);

                extractor.parse(xhtml);
                break;
            case ENCRYPTED:

                try {
                    EncryptionInfo info = new EncryptionInfo(root);
                    Decryptor d = Decryptor.getInstance(info);
                    // By default, use the default Office Password
                    String password = Decryptor.DEFAULT_PASSWORD;

                    // If they supplied a Password Provider, ask that for the password,
                    //  and use the provider given one if available (stick with default if not)
                    PasswordProvider passwordProvider = context.get(PasswordProvider.class);
                    if (passwordProvider != null) {
                        String suppliedPassword = passwordProvider.getPassword(metadata);
                        if (suppliedPassword != null) {
                            password = suppliedPassword;
                        }
                    }

                    // Check if we've the right password or not
                    if (!d.verifyPassword(password)) {
                        throw new EncryptedDocumentException();
                    }

                    // Decrypt the OLE2 stream, and delegate the resulting OOXML
                    //  file to the regular OOXML parser for normal handling
                    OOXMLParser parser = new OOXMLParser();
                    try (TikaInputStream tis = TikaInputStream.get(d.getDataStream(root))) {
                        parser.parse(tis, new EmbeddedContentHandler(new BodyContentHandler(xhtml)),
                                metadata, context);
                    }
                } catch (GeneralSecurityException ex) {
                    throw new EncryptedDocumentException(ex);
                } catch (FileNotFoundException ex) {
                    //this can happen because POI may not support case-insensitive ole2 object
                    //lookups
                    throw new EncryptedDocumentException(ex);
                }
                break;
            case DRMENCRYPTED:
                throw new EncryptedDocumentException("DRM encrypted document is not yet supported" +
                        " by Apache POI");
            default:
                if (root.hasEntry("EncryptedPackage")) {
                    throw new EncryptedDocumentException("OLE2 file with an unrecognized " +
                            "EncryptedPackage entry");
                }
                // For unsupported / unhandled types, just the metadata
                //  is extracted, which happened above
                break;
        }
    }

    private void setType(Metadata metadata, MediaType type) {
        metadata.set(Metadata.CONTENT_TYPE, type.toString());
    }

    public enum POIFSDocumentType {
        WORKBOOK("xls", MediaType.application("vnd.ms-excel")),
        OLE10_NATIVE("ole", POIFSContainerDetector.OLE10_NATIVE),
        COMP_OBJ("ole", POIFSContainerDetector.COMP_OBJ),
        WORDDOCUMENT("doc", MediaType.application("msword")),
        UNKNOWN("unknown", MediaType.application("x-tika-msoffice")),
        DRMENCRYPTED("ole", MediaType.application("x-tika-ole-drm-encrypted")),
        ENCRYPTED("ole", MediaType.application("x-tika-ooxml-protected")),
        POWERPOINT("ppt", MediaType.application("vnd.ms-powerpoint")),
        PUBLISHER("pub", MediaType.application("x-mspublisher")),
        PROJECT("mpp", MediaType.application("vnd.ms-project")),
        VISIO("vsd", MediaType.application("vnd.visio")),
        WORKS("wps", MediaType.application("vnd.ms-works")),
        XLR("xlr", MediaType.application("x-tika-msworks-spreadsheet")),
        OUTLOOK("msg", MediaType.application("vnd.ms-outlook")),
        SOLIDWORKS_PART("sldprt", MediaType.application("sldworks")),
        SOLIDWORKS_ASSEMBLY("sldasm", MediaType.application("sldworks")),
        SOLIDWORKS_DRAWING("slddrw", MediaType.application("sldworks")),
        GRAPH("", MediaType.application("vnd.ms-graph"));

        static Map<MediaType, POIFSDocumentType> TYPE_MAP = new HashMap<>();

        static {
            for (POIFSDocumentType t : values()) {
                TYPE_MAP.put(t.type, t);
            }
        }
        private final String extension;
        private final MediaType type;

        POIFSDocumentType(String extension, MediaType type) {
            this.extension = extension;
            this.type = type;
        }

        public static POIFSDocumentType detectType(POIFSFileSystem fs) {
            return detectType(fs.getRoot());
        }

        public static POIFSDocumentType detectType(DirectoryEntry node) {
            Set<String> names = new HashSet<>();
            for (Entry entry : node) {
                names.add(entry.getName());
            }
            MediaType type = POIFSContainerDetector.detect(names, node);
            if (TYPE_MAP.containsKey(type)) {
                return TYPE_MAP.get(type);
            }
            return UNKNOWN;
        }

        public String getExtension() {
            return extension;
        }

        public MediaType getType() {
            return type;
        }
    }

    /**
     * Looks for entry within root (non-recursive) that has an upper-cased
     * name that equals ucTarget
     * @param root
     * @param ucTarget
     * @return
     */
    public static Entry getUCEntry(DirectoryEntry root, String ucTarget) {
        Iterator<Entry> it = root.getEntries();
        while (it.hasNext()) {
            Entry e = it.next();
            if (e.getName().toUpperCase(Locale.US).equals(ucTarget)) {
                return e;
            }
        }
        return null;
    }

}
