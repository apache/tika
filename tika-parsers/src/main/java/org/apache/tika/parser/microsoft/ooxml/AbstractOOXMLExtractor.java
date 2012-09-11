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
package org.apache.tika.parser.microsoft.ooxml;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;

import org.apache.poi.POIXMLDocument;
import org.apache.poi.POIXMLTextExtractor;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.openxml4j.opc.PackagePart;
import org.apache.poi.openxml4j.opc.PackageRelationship;
import org.apache.poi.openxml4j.opc.TargetMode;
import org.apache.poi.poifs.filesystem.DirectoryNode;
import org.apache.poi.poifs.filesystem.Ole10Native;
import org.apache.poi.poifs.filesystem.Ole10NativeException;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.extractor.ParsingEmbeddedDocumentExtractor;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.microsoft.OfficeParser.POIFSDocumentType;
import org.apache.tika.sax.EmbeddedContentHandler;
import org.apache.tika.sax.XHTMLContentHandler;
import org.apache.xmlbeans.XmlException;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * Base class for all Tika OOXML extractors.
 * 
 * Tika extractors decorate POI extractors so that the parsed content of
 * documents is returned as a sequence of XHTML SAX events. Subclasses must
 * implement the buildXHTML method {@link #buildXHTML(XHTMLContentHandler)} that
 * populates the {@link XHTMLContentHandler} object received as parameter.
 */
public abstract class AbstractOOXMLExtractor implements OOXMLExtractor {
    static final String RELATION_AUDIO = "http://schemas.openxmlformats.org/officeDocument/2006/relationships/audio";
    static final String RELATION_IMAGE = "http://schemas.openxmlformats.org/officeDocument/2006/relationships/image";
    static final String RELATION_OLE_OBJECT = "http://schemas.openxmlformats.org/officeDocument/2006/relationships/oleObject";
    static final String RELATION_PACKAGE = "http://schemas.openxmlformats.org/officeDocument/2006/relationships/package";

    private static final String TYPE_OLE_OBJECT =
            "application/vnd.openxmlformats-officedocument.oleObject";

    protected POIXMLTextExtractor extractor;

    private final EmbeddedDocumentExtractor embeddedExtractor;

    public AbstractOOXMLExtractor(ParseContext context, POIXMLTextExtractor extractor) {
        this.extractor = extractor;

        EmbeddedDocumentExtractor ex = context.get(EmbeddedDocumentExtractor.class);

        if (ex==null) {
            embeddedExtractor = new ParsingEmbeddedDocumentExtractor(context);
        } else {
            embeddedExtractor = ex;
        }

    }

    /**
     * @see org.apache.tika.parser.microsoft.ooxml.OOXMLExtractor#getDocument()
     */
    public POIXMLDocument getDocument() {
        return extractor.getDocument();
    }

    /**
     * @see org.apache.tika.parser.microsoft.ooxml.OOXMLExtractor#getMetadataExtractor()
     */
    public MetadataExtractor getMetadataExtractor() {
        return new MetadataExtractor(extractor);
    }

    /**
     * @see org.apache.tika.parser.microsoft.ooxml.OOXMLExtractor#getXHTML(org.xml.sax.ContentHandler,
     *      org.apache.tika.metadata.Metadata)
     */
    public void getXHTML(
            ContentHandler handler, Metadata metadata, ParseContext context)
            throws SAXException, XmlException, IOException, TikaException {
        XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
        xhtml.startDocument();

        buildXHTML(xhtml);

        // Now do any embedded parts
        handleEmbeddedParts(handler);

        xhtml.endDocument();
    }

    private void handleEmbeddedParts(ContentHandler handler)
            throws TikaException, IOException, SAXException {
        try {
            for (PackagePart source : getMainDocumentParts()) {
                for (PackageRelationship rel : source.getRelationships()) {
                    if (rel.getTargetMode() == TargetMode.INTERNAL) {
                        PackagePart target;

                        try {
                            target = source.getRelatedPart(rel);
                        } catch (IllegalArgumentException ex) {
                            continue;
                        }

                        String type = rel.getRelationshipType();
                        if (RELATION_OLE_OBJECT.equals(type)
                                && TYPE_OLE_OBJECT.equals(target.getContentType())) {
                            handleEmbeddedOLE(target, handler, rel.getId());
                        } else if (RELATION_AUDIO.equals(type)
                                || RELATION_IMAGE.equals(type)
                                || RELATION_PACKAGE.equals(type)
                                || RELATION_OLE_OBJECT.equals(type)) {
                            handleEmbeddedFile(target, handler, rel.getId());
                        }
                    }
                }
            }
        } catch (InvalidFormatException e) {
            throw new TikaException("Broken OOXML file", e);
        }
    }

    /**
     * Handles an embedded OLE object in the document
     */
    private void handleEmbeddedOLE(PackagePart part, ContentHandler handler, String rel)
            throws IOException, SAXException {
        POIFSFileSystem fs = new POIFSFileSystem(part.getInputStream());
        try {
            Metadata metadata = new Metadata();
            TikaInputStream stream = null;
            metadata.set(Metadata.EMBEDDED_RELATIONSHIP_ID, rel);

            DirectoryNode root = fs.getRoot();
            POIFSDocumentType type = POIFSDocumentType.detectType(root);
            
            if (root.hasEntry("CONTENTS")
                  && root.hasEntry("\u0001Ole")
                  && root.hasEntry("\u0001CompObj")
                  && root.hasEntry("\u0003ObjInfo")) {
               // TIKA-704: OLE 2.0 embedded non-Office document?
               stream = TikaInputStream.get(
                     fs.createDocumentInputStream("CONTENTS"));
               if (embeddedExtractor.shouldParseEmbedded(metadata)) {
                  embeddedExtractor.parseEmbedded(
                        stream, new EmbeddedContentHandler(handler),
                        metadata, false);
               }
            } else if (POIFSDocumentType.OLE10_NATIVE == type) {
                // TIKA-704: OLE 1.0 embedded document
                Ole10Native ole =
                        Ole10Native.createFromEmbeddedOleObject(fs);
                metadata.set(Metadata.RESOURCE_NAME_KEY, ole.getLabel());
                byte[] data = ole.getDataBuffer();
                if (data != null) {
                    stream = TikaInputStream.get(data);
                }

                if (stream != null
                        && embeddedExtractor.shouldParseEmbedded(metadata)) {
                    embeddedExtractor.parseEmbedded(
                            stream, new EmbeddedContentHandler(handler),
                            metadata, false);
                }
            } else {
                handleEmbeddedFile(part, handler, rel);
            }
        } catch (FileNotFoundException e) {
            // There was no CONTENTS entry, so skip this part
        } catch (Ole10NativeException e) {
            // Could not process an OLE 1.0 entry, so skip this part
        }
    }

    /**
     * Handles an embedded file in the document
     */
    protected void handleEmbeddedFile(PackagePart part, ContentHandler handler, String rel)
            throws SAXException, IOException {
        Metadata metadata = new Metadata();
        metadata.set(Metadata.EMBEDDED_RELATIONSHIP_ID, rel);

        // Get the name
        String name = part.getPartName().getName();
        metadata.set(
                Metadata.RESOURCE_NAME_KEY,
                name.substring(name.lastIndexOf('/') + 1));

        // Get the content type
        metadata.set(
                Metadata.CONTENT_TYPE, part.getContentType());

        // Call the recursing handler
        if (embeddedExtractor.shouldParseEmbedded(metadata)) {
            embeddedExtractor.parseEmbedded(
                    TikaInputStream.get(part.getInputStream()),
                    new EmbeddedContentHandler(handler),
                    metadata, false);
        }
    }

    /**
     * Populates the {@link XHTMLContentHandler} object received as parameter.
     */
    protected abstract void buildXHTML(XHTMLContentHandler xhtml)
            throws SAXException, XmlException, IOException;
    
    /**
     * Return a list of the main parts of the document, used
     *  when searching for embedded resources.
     * This should be all the parts of the document that end
     *  up with things embedded into them.
     */
    protected abstract List<PackagePart> getMainDocumentParts()
            throws TikaException;
}
