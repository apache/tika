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

package org.apache.tika.parser.microsoft.ooxml.xps;

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.CloseShieldInputStream;
import org.apache.poi.POIXMLDocument;
import org.apache.poi.POIXMLTextExtractor;
import org.apache.poi.openxml4j.opc.PackagePart;
import org.apache.poi.openxml4j.opc.PackageRelationship;
import org.apache.poi.openxml4j.opc.PackageRelationshipCollection;
import org.apache.poi.openxml4j.opc.ZipPackage;
import org.apache.poi.openxml4j.util.ZipEntrySource;
import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentUtil;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.microsoft.ooxml.AbstractOOXMLExtractor;
import org.apache.tika.sax.EmbeddedContentHandler;
import org.apache.tika.sax.OfflineContentHandler;
import org.apache.tika.sax.XHTMLContentHandler;
import org.apache.tika.utils.ExceptionUtils;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;

public class XPSExtractorDecorator extends AbstractOOXMLExtractor {

    private static String XPS_DOCUMENT = "http://schemas.microsoft.com/xps/2005/06/fixedrepresentation";

    private final ParseContext context;
    private final ZipPackage pkg;
    Map<String, Metadata> embeddedImages = new HashMap<>();

    public XPSExtractorDecorator(ParseContext context, POIXMLTextExtractor extractor) throws TikaException {
        super(context, extractor);
        this.context = context;
        if (extractor.getPackage() instanceof ZipPackage) {
            this.pkg = (ZipPackage) extractor.getPackage();
        } else {
            throw new TikaException("OPCPackage must be a ZipPackage");
        }
    }

    @Override
    public POIXMLDocument getDocument() {
        return null;
    }


    @Override
    protected void buildXHTML(XHTMLContentHandler xhtml) throws SAXException, IOException {

        PackageRelationshipCollection prc = pkg.getRelationshipsByType(XPS_DOCUMENT);
        for (int i = 0; i < prc.size(); i++) {
            PackageRelationship pr = prc.getRelationship(i);

            //there should only be one.
            //in the test file, this points to FixedDocSeq.fdseq
            try {
                handleDocuments(pr, xhtml);
            } catch (TikaException e) {
                throw new SAXException(e);
            }
        }

        //now handle embedded images
        if (embeddedImages.size() > 0) {
            EmbeddedDocumentUtil embeddedDocumentUtil = new EmbeddedDocumentUtil(context);
            for (Map.Entry<String, Metadata> embeddedImage : embeddedImages.entrySet()) {
                String zipPath = embeddedImage.getKey();
                Metadata metadata = embeddedImage.getValue();
                    if (embeddedDocumentUtil.shouldParseEmbedded(metadata)) {
                        handleEmbeddedImage(
                                zipPath,
                                metadata,
                                embeddedDocumentUtil,
                                xhtml);
                    }
            }
        }

    }

    private void handleEmbeddedImage(String zipPath, Metadata metadata,
                                      EmbeddedDocumentUtil embeddedDocumentUtil,
                                     XHTMLContentHandler xhtml) throws SAXException, IOException {
        InputStream stream = null;
        try {
            stream = getZipStream(zipPath, pkg);
        } catch (IOException|TikaException e) {
            //store this exception in the parent's metadata
            EmbeddedDocumentUtil.recordEmbeddedStreamException(e, metadata);
            return;
        }

        try {
            embeddedDocumentUtil.parseEmbedded(stream, xhtml, metadata, true);
        } finally {
            IOUtils.closeQuietly(stream);
        }
    }

    private void handleDocuments(PackageRelationship packageRelationship,
                                 XHTMLContentHandler xhtml) throws IOException, SAXException, TikaException {

        try (InputStream stream = pkg.getPart(packageRelationship).getInputStream()) {
            context.getSAXParser().parse(
                    new CloseShieldInputStream(stream),
                    new OfflineContentHandler(new EmbeddedContentHandler(
                            new FixedDocSeqHandler(xhtml))));
        }
    }

    @Override
    protected List<PackagePart> getMainDocumentParts() throws TikaException {
        return Collections.EMPTY_LIST;
    }

    private class FixedDocSeqHandler extends DefaultHandler {
        private final static String DOCUMENT_REFERENCE = "DocumentReference";
        private final static String SOURCE = "Source";

        private final XHTMLContentHandler xhtml;

        private FixedDocSeqHandler(XHTMLContentHandler xhtml) {
            this.xhtml = xhtml;
        }

        @Override
        public void startElement(String uri, String localName, String qName, Attributes atts) throws SAXException {
            if (!DOCUMENT_REFERENCE.equals(localName)) {
                return;
            }
            for (int i = 0; i < atts.getLength(); i++) {
                String lName = atts.getLocalName(i);
                if (SOURCE.equals(lName)) {
                    handleDocumentRef(atts.getValue(i));
                }
            }
        }

        private void handleDocumentRef(String docRef) throws SAXException {
            //docRef is a path to a FixedDocumentSequence document,
            // e.g. /Documents/1/FixedDoc.fdoc

            //relative root is /Documents/1 ..need this Pages...
            String relativeRoot = null;
            int i = docRef.lastIndexOf("/");
            if (i > 0) {
                relativeRoot = docRef.substring(0, i);
            } else {
                relativeRoot = "";
            }
            String zipPath = (docRef.startsWith("/") ? docRef.substring(1) : docRef);
            if (pkg instanceof ZipPackage) {
                try (InputStream stream = getZipStream(zipPath, pkg)) {
                    context.getSAXParser().parse(
                            new CloseShieldInputStream(stream),
                            new OfflineContentHandler(new EmbeddedContentHandler(
                                    new PageContentPartHandler(relativeRoot, xhtml))));

                } catch (IOException | TikaException e) {
                    throw new SAXException(new TikaException("IOException trying to read: " + docRef));
                }
            } else {
                throw new SAXException(new TikaException("Package must be ZipPackage"));
            }
        }

        private class PageContentPartHandler extends DefaultHandler {
            private static final String PAGE_CONTENT = "PageContent";
            private static final String SOURCE = "Source";

            private final String relativeRoot;
            private final XHTMLContentHandler xhtml;

            private PageContentPartHandler(String relativeRoot, XHTMLContentHandler xhtml) {
                this.relativeRoot = relativeRoot;
                this.xhtml = xhtml;
            }

            @Override
            public void startElement(String uri, String localName, String qName, Attributes atts) throws SAXException {
                if (!PAGE_CONTENT.equals(localName)) {
                    return;
                }
                String pagePath = null;
                for (int i = 0; i < atts.getLength(); i++) {
                    if (SOURCE.equals(atts.getLocalName(i))) {
                        pagePath = atts.getValue(i);
                        break;
                    }
                }

                if (pagePath != null) {
                    if (!pagePath.startsWith("/")) {
                        pagePath = relativeRoot + "/" + pagePath;
                    }
                    //trim initial /
                    if (pagePath.startsWith("/")) {
                        pagePath = pagePath.substring(1);
                    }
                    try (InputStream stream = getZipStream(pagePath, pkg)) {
                        context.getSAXParser().parse(
                                new CloseShieldInputStream(stream),
                                new OfflineContentHandler(
                                        new XPSPageContentHandler(xhtml, embeddedImages)
                                )
                        );
                    } catch (TikaException | IOException e) {
                        throw new SAXException(e);
                    }
                }

            }
        }
    }

    private static InputStream getZipStream(String zipPath, ZipPackage zipPackage) throws IOException, TikaException {
        String targPath = (zipPath.length() > 1 && zipPath.startsWith("/") ? zipPath.substring(1) : zipPath);
        ZipEntrySource zipEntrySource = zipPackage.getZipArchive();
        Enumeration<? extends ZipEntry> zipEntryEnumeration = zipEntrySource.getEntries();
        ZipEntry zipEntry = null;
        while (zipEntryEnumeration.hasMoreElements()) {
            ZipEntry ze = zipEntryEnumeration.nextElement();
            if (ze.getName().equals(targPath)) {
                zipEntry = ze;
                break;
            }
        }
        if (zipEntry == null) {
            throw new TikaException("Couldn't find required zip entry: " + zipPath);
        }
        return zipEntrySource.getInputStream(zipEntry);
    }
}
