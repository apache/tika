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

import java.io.IOException;
import java.util.List;

import org.apache.poi.POIXMLDocument;
import org.apache.poi.POIXMLTextExtractor;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.openxml4j.opc.PackagePart;
import org.apache.poi.openxml4j.opc.PackagePartName;
import org.apache.poi.openxml4j.opc.PackageRelationship;
import org.apache.poi.openxml4j.opc.PackageRelationshipCollection;
import org.apache.poi.openxml4j.opc.PackagingURIHelper;
import org.apache.poi.openxml4j.opc.TargetMode;
import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.extractor.ParsingEmbeddedDocumentExtractor;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
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
   
    protected POIXMLTextExtractor extractor;

    private final EmbeddedDocumentExtractor embeddedExtractor;

    private final String type;

    public AbstractOOXMLExtractor(ParseContext context, POIXMLTextExtractor extractor, String type) {
        this.extractor = extractor;
        this.type = type;

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
        return new MetadataExtractor(extractor, type);
    }

    /**
     * @see org.apache.tika.parser.microsoft.ooxml.OOXMLExtractor#getXHTML(org.xml.sax.ContentHandler,
     *      org.apache.tika.metadata.Metadata)
     */
    public void getXHTML(ContentHandler handler, Metadata metadata, ParseContext context)
            throws SAXException, XmlException, IOException, TikaException {
        XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
        xhtml.startDocument();
        buildXHTML(xhtml);
        xhtml.endDocument();
        
        // Now do any embedded parts
        List<PackagePart> mainParts = getMainDocumentParts();
        for(PackagePart part : mainParts) {
           PackageRelationshipCollection rels;
           try {
              rels = part.getRelationships();
           } catch(InvalidFormatException e) {
              throw new TikaException("Corrupt OOXML file", e);
           }
           
           for(PackageRelationship rel : rels) {
              // Is it an embedded type (not part of the document)
              if( rel.getRelationshipType().equals(RELATION_AUDIO) ||
                  rel.getRelationshipType().equals(RELATION_IMAGE) ||
                  rel.getRelationshipType().equals(RELATION_OLE_OBJECT) ||
                  rel.getRelationshipType().equals(RELATION_PACKAGE) ) {
                 if(rel.getTargetMode() == TargetMode.INTERNAL) {
                    PackagePartName relName;
                    try {
                       relName = PackagingURIHelper.createPartName(rel.getTargetURI());
                    } catch(InvalidFormatException e) {
                       throw new TikaException("Broken OOXML file", e);
                    }
                    PackagePart relPart = rel.getPackage().getPart(relName);
                    handleEmbedded(rel, relPart, handler, context);
                 }
              }
           }
        }
    }
    
    /**
     * Handles an embedded resource in the file
     */
    protected void handleEmbedded(PackageRelationship rel, PackagePart part, 
            ContentHandler handler, ParseContext context)
            throws SAXException, XmlException, IOException, TikaException {
       // Get the name
       String name = rel.getTargetURI().toString();
       if(name.indexOf('/') > -1) {
          name = name.substring(name.lastIndexOf('/')+1);
       }
       
       // Get the content type
       String type = part.getContentType();
       
       // Call the recursing handler
       Metadata metadata = new Metadata();
       metadata.set(Metadata.RESOURCE_NAME_KEY, name);
       metadata.set(Metadata.CONTENT_TYPE, type);

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
