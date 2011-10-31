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

import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Service;
import org.apache.poi.hdgf.extractor.VisioTextExtractor;
import org.apache.poi.hpbf.extractor.PublisherTextExtractor;
import org.apache.poi.poifs.crypt.Decryptor;
import org.apache.poi.poifs.crypt.EncryptionInfo;
import org.apache.poi.poifs.filesystem.DirectoryEntry;
import org.apache.poi.poifs.filesystem.DirectoryNode;
import org.apache.poi.poifs.filesystem.Entry;
import org.apache.poi.poifs.filesystem.NPOIFSFileSystem;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.apache.tika.exception.EncryptedDocumentException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.CloseShieldInputStream;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.microsoft.ooxml.OOXMLParser;
import org.apache.tika.sax.BodyContentHandler;
import org.apache.tika.sax.EmbeddedContentHandler;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * Defines a Microsoft document content extractor.
 */
@Component @Service(Parser.class)
public class OfficeParser extends AbstractParser {

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
        	POIFSDocumentType.VISIO.type,
        	POIFSDocumentType.OUTLOOK.type,
                MediaType.application("vnd.ms-excel.sheet.binary.macroenabled.12")
         )));
    
    public enum POIFSDocumentType {
        WORKBOOK("xls", MediaType.application("vnd.ms-excel")),
        OLE10_NATIVE("ole", MediaType.application("x-tika-msoffice")),
        WORDDOCUMENT("doc", MediaType.application("msword")),
        UNKNOWN("unknown", MediaType.application("x-tika-msoffice")),
        ENCRYPTED("ole", MediaType.application("x-tika-msoffice")),
        POWERPOINT("ppt", MediaType.application("vnd.ms-powerpoint")),
        PUBLISHER("pub", MediaType.application("x-mspublisher")),
        VISIO("vsd", MediaType.application("vnd.visio")),
        WORKS("wps", MediaType.application("vnd.ms-works")),
        OUTLOOK("msg", MediaType.application("vnd.ms-outlook"));

        private final String extension;
        private final MediaType type;

        POIFSDocumentType(String extension, MediaType type) {
            this.extension = extension;
            this.type = type;
        }

        public String getExtension() {
            return extension;
        }

        public MediaType getType() {
            return type;
        }

        public static POIFSDocumentType detectType(POIFSFileSystem fs) {
            return detectType(fs.getRoot());
        }

        public static POIFSDocumentType detectType(NPOIFSFileSystem fs) {
           return detectType(fs.getRoot());
       }

        public static POIFSDocumentType detectType(DirectoryEntry node) {
            for (Entry entry : node) {
                POIFSDocumentType type = detectType(entry);
                if (type!=UNKNOWN) {
                    return type;
                }
            }
            return UNKNOWN;
        }

        private final static Map<String,POIFSDocumentType> typeMap = new HashMap<String,POIFSDocumentType>();
        static {
            typeMap.put("Workbook", WORKBOOK);
            typeMap.put("EncryptedPackage", ENCRYPTED);
            typeMap.put("WordDocument", WORDDOCUMENT);
            typeMap.put("Quill", PUBLISHER);
            typeMap.put("PowerPoint Document", POWERPOINT);
            typeMap.put("VisioDocument", VISIO);
            typeMap.put("CONTENTS", WORKS);
            typeMap.put("\u0001Ole10Native", POIFSDocumentType.OLE10_NATIVE);
        }

        public static POIFSDocumentType detectType(Entry entry) {
            String name = entry.getName();
            POIFSDocumentType type = typeMap.get(name);
            if (type != null) {
                return type;
            }
            if (entry.getName().startsWith("__substg1.0_")) {
                return OUTLOOK;
            }
            return UNKNOWN;
        }
    }

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
        XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
        xhtml.startDocument();

        final DirectoryNode root;
        TikaInputStream tstream = TikaInputStream.cast(stream);
        if (tstream == null) {
            root = new NPOIFSFileSystem(new CloseShieldInputStream(stream)).getRoot();
        } else {
            final Object container = tstream.getOpenContainer();
            if (container instanceof NPOIFSFileSystem) {
                root = ((NPOIFSFileSystem) container).getRoot();
            } else if (container instanceof DirectoryNode) {
                root = (DirectoryNode) container;
            } else if (tstream.hasFile()) {
                root = new NPOIFSFileSystem(tstream.getFileChannel()).getRoot();
            } else {
                root = new NPOIFSFileSystem(new CloseShieldInputStream(tstream)).getRoot();
            }
        }
        parse(root, context, metadata, xhtml);
        xhtml.endDocument();
    }

    protected void parse(
            DirectoryNode root, ParseContext context, Metadata metadata, XHTMLContentHandler xhtml)
            throws IOException, SAXException, TikaException {

        // Parse summary entries first, to make metadata available early
        new SummaryExtractor(metadata).parseSummaries(root);

        // Parse remaining document entries
        boolean outlookExtracted = false;
        for (Entry entry : root) {
            POIFSDocumentType type = POIFSDocumentType.detectType(entry);

            if (type!=POIFSDocumentType.UNKNOWN) {
                setType(metadata, type.getType());
            }

            switch (type) {
                case PUBLISHER:
                    PublisherTextExtractor publisherTextExtractor =
                        new PublisherTextExtractor(root);
                    xhtml.element("p", publisherTextExtractor.getText());
                    break;
                case WORDDOCUMENT:
                    new WordExtractor(context).parse(root, xhtml);
                    break;
                case POWERPOINT:
                    new HSLFExtractor(context).parse(root, xhtml);
                    break;
                case WORKBOOK:
                    Locale locale = context.get(Locale.class, Locale.getDefault());
                    new ExcelExtractor(context).parse(root, xhtml, locale);
                    break;
                case VISIO:
                    VisioTextExtractor visioTextExtractor =
                        new VisioTextExtractor(root);
                    for (String text : visioTextExtractor.getAllText()) {
                        xhtml.element("p", text);
                    }
                    break;
                case OUTLOOK:
                    if (!outlookExtracted) {
                        outlookExtracted = true;

                        OutlookExtractor extractor =
                            new OutlookExtractor(root, context);

                        extractor.parse(xhtml, metadata);
                    }
                    break;
                case ENCRYPTED:
                    EncryptionInfo info = new EncryptionInfo(root);
                    Decryptor d = Decryptor.getInstance(info);

                    try {
                        if (!d.verifyPassword(Decryptor.DEFAULT_PASSWORD)) {
                            throw new EncryptedDocumentException();
                        }

                        OOXMLParser parser = new OOXMLParser();

                        parser.parse(d.getDataStream(root), new EmbeddedContentHandler(
                                        new BodyContentHandler(xhtml)),
                                        metadata, context);
                    } catch (GeneralSecurityException ex) {
                        throw new EncryptedDocumentException(ex);
                    }
            }
        }
    }

    private void setType(Metadata metadata, MediaType type) {
        metadata.set(Metadata.CONTENT_TYPE, type.toString());
    }

}
