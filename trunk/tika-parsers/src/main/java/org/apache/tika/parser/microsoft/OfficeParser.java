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
import java.util.*;

import org.apache.poi.hdgf.extractor.VisioTextExtractor;
import org.apache.poi.hpbf.extractor.PublisherTextExtractor;
import org.apache.poi.poifs.crypt.Decryptor;
import org.apache.poi.poifs.crypt.EncryptionInfo;
import org.apache.poi.poifs.filesystem.DirectoryEntry;
import org.apache.poi.poifs.filesystem.Entry;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
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
public class OfficeParser implements Parser {
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

        public static POIFSDocumentType detectType(DirectoryEntry node) {
            for (Entry entry : node) {
                POIFSDocumentType type = detectType(entry);
                if (type!=UNKNOWN) {
                    return type;
                }
            }
            return UNKNOWN;
        }

        public static POIFSDocumentType detectType(Entry entry) {
            String name = entry.getName();

            if ("Workbook".equals(name)) {
                return WORKBOOK;
            }
            if ("EncryptedPackage".equals(name)) {
                return ENCRYPTED;
            }
            if ("WordDocument".equals(name)) {
                return WORDDOCUMENT;
            }
            if ("Quill".equals(name)) {
                return PUBLISHER;
            }
            if ("PowerPoint Document".equals(entry.getName())) {
                return POWERPOINT;
            }
            if ("VisioDocument".equals(entry.getName())) {
                return VISIO;
            }
            if ("CONTENTS".equals(entry.getName())) {
               return WORKS;
           }
            if (entry.getName().startsWith("__substg1.0_")) {
                return OUTLOOK;
            }
            if ("\u0001Ole10Native".equals(name)) {
              return POIFSDocumentType.OLE10_NATIVE;
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

        POIFSFileSystem filesystem;
        if(stream instanceof TikaInputStream && 
        	((TikaInputStream)stream).getOpenContainer() != null) {
            filesystem = (POIFSFileSystem)((TikaInputStream)stream).getOpenContainer();
        } else {
            filesystem = new POIFSFileSystem(stream);
        }

        // Parse summary entries first, to make metadata available early
        new SummaryExtractor(metadata).parseSummaries(filesystem);

        // Parse remaining document entries
        boolean outlookExtracted = false;
        for (Entry entry : filesystem.getRoot()) {
            POIFSDocumentType type = POIFSDocumentType.detectType(entry);

            if (type!=POIFSDocumentType.UNKNOWN) {
                setType(metadata, type.getType());
            }

            switch (type) {
                case PUBLISHER:
                    PublisherTextExtractor publisherTextExtractor =
                        new PublisherTextExtractor(filesystem);
                    xhtml.element("p", publisherTextExtractor.getText());
                    break;
                case WORDDOCUMENT:
                    new WordExtractor(context).parse(filesystem, xhtml);
                    break;
                case POWERPOINT:
                    new HSLFExtractor(context).parse(filesystem, xhtml);
                    break;
                case WORKBOOK:
                    Locale locale = context.get(Locale.class, Locale.getDefault());
                    new ExcelExtractor(context).parse(filesystem, xhtml, locale);
                    break;
                case VISIO:
                    VisioTextExtractor visioTextExtractor =
                        new VisioTextExtractor(filesystem);
                    for (String text : visioTextExtractor.getAllText()) {
                        xhtml.element("p", text);
                    }
                    break;
                case OUTLOOK:
                    if (!outlookExtracted) {
                        outlookExtracted = true;

                        OutlookExtractor extractor =
                            new OutlookExtractor(filesystem, context);

                        extractor.parse(xhtml, metadata);
                    }
                    break;
                case ENCRYPTED:
                    EncryptionInfo info = new EncryptionInfo(filesystem);
                    Decryptor d = new Decryptor(info);

                    try {
                        if (!d.verifyPassword(Decryptor.DEFAULT_PASSWORD)) {
                            throw new TikaException("Unable to process: document is encrypted");
                        }

                        OOXMLParser parser = new OOXMLParser();

                        parser.parse(d.getDataStream(filesystem), new EmbeddedContentHandler(
                                        new BodyContentHandler(xhtml)),
                                        metadata, context);
                    } catch (GeneralSecurityException ex) {
                        throw new TikaException("Unable to process encrypted document", ex);
                    }
            }
        }

        xhtml.endDocument();
    }

    /**
     * @deprecated This method will be removed in Apache Tika 1.0.
     */
    public void parse(
            InputStream stream, ContentHandler handler, Metadata metadata)
            throws IOException, SAXException, TikaException {
        parse(stream, handler, metadata, new ParseContext());
    }

    private void setType(Metadata metadata, MediaType type) {
        metadata.set(Metadata.CONTENT_TYPE, type.toString());
    }

}
