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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.input.CloseShieldInputStream;
import org.apache.poi.hdgf.extractor.VisioTextExtractor;
import org.apache.poi.hpbf.extractor.PublisherTextExtractor;
import org.apache.poi.poifs.crypt.Decryptor;
import org.apache.poi.poifs.crypt.EncryptionInfo;
import org.apache.poi.poifs.filesystem.DirectoryEntry;
import org.apache.poi.poifs.filesystem.DirectoryNode;
import org.apache.poi.poifs.filesystem.Entry;
import org.apache.poi.poifs.filesystem.NPOIFSFileSystem;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.apache.poi.poifs.macros.VBAMacroReader;
import org.apache.poi.util.IOUtils;
import org.apache.poi.util.LocaleUtil;
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
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * Defines a Microsoft document content extractor.
 */
public class OfficeParser extends AbstractOfficeParser {

    /**
     * Serial version UID
     */
    private static final long serialVersionUID = 7393462244028653479L;

    private static final Set<MediaType> SUPPORTED_TYPES =
            Collections.unmodifiableSet(new HashSet<MediaType>(Arrays.asList(
                    POIFSDocumentType.WORKBOOK.type,
                    POIFSDocumentType.OLE10_NATIVE.type,
                    POIFSDocumentType.WORDDOCUMENT.type,
                    POIFSDocumentType.UNKNOWN.type,
                    POIFSDocumentType.ENCRYPTED.type,
                    POIFSDocumentType.POWERPOINT.type,
                    POIFSDocumentType.PUBLISHER.type,
                    POIFSDocumentType.PROJECT.type,
                    POIFSDocumentType.VISIO.type,
                    // Works isn't supported
                    POIFSDocumentType.XLR.type, // but Works 7.0 Spreadsheet is
                    POIFSDocumentType.OUTLOOK.type,
                    POIFSDocumentType.SOLIDWORKS_PART.type,
                    POIFSDocumentType.SOLIDWORKS_ASSEMBLY.type,
                    POIFSDocumentType.SOLIDWORKS_DRAWING.type
            )));

    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    /**
     * Extracts properties and text from an MS Document input stream
     */
    public void parse(
            InputStream stream, ContentHandler handler,
            Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {

        configure(context);
        XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
        xhtml.startDocument();

        final DirectoryNode root;
        TikaInputStream tstream = TikaInputStream.cast(stream);
        NPOIFSFileSystem mustCloseFs = null;
        try {
            if (tstream == null) {
                mustCloseFs = new NPOIFSFileSystem(new CloseShieldInputStream(stream));
                root = mustCloseFs.getRoot();
            } else {
                final Object container = tstream.getOpenContainer();
                if (container instanceof NPOIFSFileSystem) {
                    root = ((NPOIFSFileSystem) container).getRoot();
                } else if (container instanceof DirectoryNode) {
                    root = (DirectoryNode) container;
                } else {
                    NPOIFSFileSystem fs = null;
                    if (tstream.hasFile()) {
                        fs = new NPOIFSFileSystem(tstream.getFile(), true);
                    } else {
                        fs = new NPOIFSFileSystem(new CloseShieldInputStream(tstream));
                    }
                    //tstream will close the fs, no need to close this below
                    tstream.setOpenContainer(fs);
                    root = fs.getRoot();

                }
            }
            parse(root, context, metadata, xhtml);
            OfficeParserConfig officeParserConfig = context.get(OfficeParserConfig.class);

            if (officeParserConfig.getExtractMacros()) {
                //now try to get macros.
                //Note that macros are handled separately for ppt in HSLFExtractor.

                //We might consider not bothering to check for macros in root,
                //if we know we're processing ppt based on content-type identified in metadata
                extractMacros(root.getNFileSystem(), xhtml,
                            EmbeddedDocumentUtil.getEmbeddedDocumentExtractor(context));

            }
        } finally {
            IOUtils.closeQuietly(mustCloseFs);
        }
        xhtml.endDocument();
    }

    protected void parse(
            DirectoryNode root, ParseContext context, Metadata metadata, XHTMLContentHandler xhtml)
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
                PublisherTextExtractor publisherTextExtractor =
                        new PublisherTextExtractor(root);
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
                VisioTextExtractor visioTextExtractor =
                        new VisioTextExtractor(root);
                for (String text : visioTextExtractor.getAllText()) {
                    xhtml.element("p", text);
                }
                break;
            case OUTLOOK:
                OutlookExtractor extractor =
                        new OutlookExtractor(root, context);

                extractor.parse(xhtml, metadata);
                break;
            case ENCRYPTED:
                EncryptionInfo info = new EncryptionInfo(root);
                Decryptor d = Decryptor.getInstance(info);

                try {
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

                    parser.parse(d.getDataStream(root), new EmbeddedContentHandler(
                                    new BodyContentHandler(xhtml)),
                            metadata, context);
                } catch (GeneralSecurityException ex) {
                    throw new EncryptedDocumentException(ex);
                }
            default:
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

        private final String extension;
        private final MediaType type;

        POIFSDocumentType(String extension, MediaType type) {
            this.extension = extension;
            this.type = type;
        }

        public static POIFSDocumentType detectType(POIFSFileSystem fs) {
            return detectType(fs.getRoot());
        }

        public static POIFSDocumentType detectType(NPOIFSFileSystem fs) {
            return detectType(fs.getRoot());
        }

        public static POIFSDocumentType detectType(DirectoryEntry node) {
            Set<String> names = new HashSet<String>();
            for (Entry entry : node) {
                names.add(entry.getName());
            }
            MediaType type = POIFSContainerDetector.detect(names, node);
            for (POIFSDocumentType poifsType : values()) {
                if (type.equals(poifsType.type)) {
                    return poifsType;
                }
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
     * Helper to extract macros from an NPOIFS/vbaProject.bin
     *
     * As of POI-3.15-final, there are still some bugs in VBAMacroReader.
     * For now, we are swallowing NPE and other runtime exceptions
     *
     * @param fs NPOIFS to extract from
     * @param xhtml SAX writer
     * @param embeddedDocumentExtractor extractor for embedded documents
     * @throws IOException on IOException if it occurs during the extraction of the embedded doc
     * @throws SAXException on SAXException for writing to xhtml
     */
    public static void extractMacros(NPOIFSFileSystem fs, ContentHandler xhtml, EmbeddedDocumentExtractor
            embeddedDocumentExtractor)  throws IOException, SAXException {

        VBAMacroReader reader = null;
        Map<String, String> macros = null;
        try {
            reader = new VBAMacroReader(fs);
            macros = reader.readMacros();
        } catch (Exception e) {
            //swallow
            return;
        }
        for (Map.Entry<String, String> e : macros.entrySet()) {
            Metadata m = new Metadata();
            m.set(Metadata.EMBEDDED_RESOURCE_TYPE, TikaCoreProperties.EmbeddedResourceType.MACRO.toString());
            m.set(Metadata.CONTENT_TYPE, "text/x-vbasic");
            if (embeddedDocumentExtractor.shouldParseEmbedded(m)) {
                embeddedDocumentExtractor.parseEmbedded(
                        new ByteArrayInputStream(e.getValue().getBytes(StandardCharsets.UTF_8)), xhtml, m, true);
            }
        }
    }

}
