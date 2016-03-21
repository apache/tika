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
package org.apache.tika.parser.exiftool;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import javax.xml.namespace.QName;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Property;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.TeeContentHandler;
import org.xml.sax.ContentHandler;

/**
 * An IPTC extractor implementation of <code>AbstractExiftoolMetadataExtractor</code>
 */
public class ExiftoolIptcMetadataExtractor extends AbstractExiftoolMetadataExtractor {


    public ExiftoolIptcMetadataExtractor(Metadata metadata, Set<MediaType> supportedTypes) {
        super(metadata, supportedTypes);
    }

    public ExiftoolIptcMetadataExtractor(Metadata metadata, Set<MediaType> supportedTypes, String runtimeExiftoolExecutable) {
        super(metadata, supportedTypes, runtimeExiftoolExecutable);
    }
    
    public ExiftoolIptcMetadataExtractor(Metadata metadata, Set<MediaType> supportedTypes, String runtimeExiftoolExecutable,
            Collection<Property> passthroughXmpProperties) {
        super(metadata, supportedTypes, runtimeExiftoolExecutable, passthroughXmpProperties);
    }

    /**
     * Gets a parser responsible for extracting metadata from the XML output of the ExifTool external parser.
     *
     * @return the XML parser
     */
    @Override
    protected Parser getXmlResponseParser() {
        return new ExiftoolIptcXmlParser(new ExiftoolTikaIptcMapper());
    }

    @Override
    public QName getQName(Property exiftoolProperty) {
        QName qName = super.getQName(exiftoolProperty);
        if (qName != null) {
            return qName;
        }

        String prefix = null;
        String namespaceUrl = null;
        if (exiftoolProperty.getName().startsWith(ExifToolMetadata.PREFIX_IPTC)) {
            prefix = ExifToolMetadata.PREFIX_IPTC;
            namespaceUrl = ExifToolMetadata.NAMESPACE_URI_IPTC;
        } else if (exiftoolProperty.getName().startsWith(ExifToolMetadata.PREFIX_XMP_IPTC_CORE)) {
            prefix = ExifToolMetadata.PREFIX_XMP_IPTC_CORE;
            namespaceUrl = ExifToolMetadata.NAMESPACE_URI_XMP_IPTC_CORE;
        } else if (exiftoolProperty.getName().startsWith(ExifToolMetadata.PREFIX_XMP_IPTC_EXT)) {
            prefix = ExifToolMetadata.PREFIX_XMP_IPTC_EXT;
            namespaceUrl = ExifToolMetadata.NAMESPACE_URI_XMP_IPTC_EXT;
        } else if (exiftoolProperty.getName().startsWith(ExifToolMetadata.PREFIX_XMP_PHOTOSHOP)) {
            prefix = ExifToolMetadata.PREFIX_XMP_PHOTOSHOP;
            namespaceUrl = ExifToolMetadata.NAMESPACE_URI_XMP_PHOTOSHOP;
        } else if (exiftoolProperty.getName().startsWith(ExifToolMetadata.PREFIX_XMP_PLUS)) {
            prefix = ExifToolMetadata.PREFIX_XMP_PLUS;
            namespaceUrl = ExifToolMetadata.NAMESPACE_URI_XMP_PLUS;
        }
        if (prefix != null && namespaceUrl != null) {
            return new QName(namespaceUrl, exiftoolProperty.getName().replace(prefix + ExifToolMetadata.PREFIX_DELIMITER, ""), prefix);
        }
        return null;
    }

    /**
     * Extension of <code>XMLParser</code> which provides a {@link ContentHandler} which
     * recognizes ExifTool's namespaces and elements.
     *
     * @author rgauss
     *
     */
    public class ExiftoolIptcXmlParser extends AbstractExiftoolXmlParser {

        private static final long serialVersionUID = -8633426227113109506L;

        public ExiftoolIptcXmlParser(ExiftoolTikaMapper exiftoolTikaMapper) {
            super(exiftoolTikaMapper);
        }

        @Override
        protected ContentHandler getContentHandler(
                ContentHandler handler, Metadata metadata, ParseContext context) {

            Set<ContentHandler> contentHandlers = new HashSet<ContentHandler>();
            contentHandlers.add(super.getContentHandler(handler, metadata, context));
            for (Property property : ExifToolMetadata.PROPERTY_GROUP_IPTC) {
                contentHandlers.addAll(getElementMetadataHandlers(property, metadata));
            }
            for (Property property : ExifToolMetadata.PROPERTY_GROUP_XMP_DC) {
                contentHandlers.addAll(getElementMetadataHandlers(property, metadata));
            }
            for (Property property : ExifToolMetadata.PROPERTY_GROUP_XMP_IPTC_CORE) {
                contentHandlers.addAll(getElementMetadataHandlers(property, metadata));
            }
            for (Property property : ExifToolMetadata.PROPERTY_GROUP_XMP_IPTC_EXT) {
                contentHandlers.addAll(getElementMetadataHandlers(property, metadata));
            }
            for (Property property : ExifToolMetadata.PROPERTY_GROUP_XMP_PHOTOSHOP) {
                contentHandlers.addAll(getElementMetadataHandlers(property, metadata));
            }
            for (Property property : ExifToolMetadata.PROPERTY_GROUP_XMP_PLUS) {
                contentHandlers.addAll(getElementMetadataHandlers(property, metadata));
            }
            for (Property property : ExifToolMetadata.PROPERTY_GROUP_XMP_XMP) {
                contentHandlers.addAll(getElementMetadataHandlers(property, metadata));
            }
            for (Property property : ExifToolMetadata.PROPERTY_GROUP_XMP_XMPRIGHTS) {
                contentHandlers.addAll(getElementMetadataHandlers(property, metadata));
            }

            return new TeeContentHandler(contentHandlers.toArray(new ContentHandler[] {}));
        }
    }

}
