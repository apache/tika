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

import java.util.Date;

import org.apache.poi.POIXMLTextExtractor;
import org.apache.poi.POIXMLProperties.CoreProperties;
import org.apache.poi.POIXMLProperties.ExtendedProperties;
import org.apache.poi.openxml4j.opc.internal.PackagePropertiesPart;
import org.apache.poi.openxml4j.util.Nullable;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.PagedText;
import org.apache.tika.metadata.Property;
import org.openxmlformats.schemas.officeDocument.x2006.extendedProperties.CTProperties;

/**
 * OOXML metadata extractor.
 * 
 * Currently POI doesn't support metadata extraction for OOXML.
 * 
 * @see OOXMLExtractor#getMetadataExtractor()
 */
public class MetadataExtractor {

    private final POIXMLTextExtractor extractor;

    private final String type;

    public MetadataExtractor(POIXMLTextExtractor extractor, String type) {
        this.extractor = extractor;
        this.type = type;
    }

    public void extract(Metadata metadata) throws TikaException {
        addProperty(metadata, Metadata.CONTENT_TYPE, type);
        
        if (extractor.getDocument()!=null) {
            extractMetadata(extractor.getCoreProperties(), metadata);
            extractMetadata(extractor.getExtendedProperties(), metadata);
        }
    }

    private void extractMetadata(CoreProperties properties, Metadata metadata) {
        PackagePropertiesPart propsHolder = properties
                .getUnderlyingProperties();

        addProperty(metadata, Metadata.CATEGORY, propsHolder.getCategoryProperty());
        addProperty(metadata, Metadata.CONTENT_STATUS, propsHolder
                .getContentStatusProperty());
        addProperty(metadata, Metadata.DATE, propsHolder
                .getCreatedProperty());
        addProperty(metadata, Metadata.CREATION_DATE, propsHolder
                .getCreatedProperty());
        addProperty(metadata, Metadata.CREATOR, propsHolder
                .getCreatorProperty());
        addProperty(metadata, Metadata.AUTHOR, propsHolder
                .getCreatorProperty());
        addProperty(metadata, Metadata.DESCRIPTION, propsHolder
                .getDescriptionProperty());
        addProperty(metadata, Metadata.IDENTIFIER, propsHolder
                .getIdentifierProperty());
        addProperty(metadata, Metadata.KEYWORDS, propsHolder
                .getKeywordsProperty());
        addProperty(metadata, Metadata.LANGUAGE, propsHolder
                .getLanguageProperty());
        addProperty(metadata, Metadata.LAST_AUTHOR, propsHolder
                .getLastModifiedByProperty());
        addProperty(metadata, Metadata.LAST_PRINTED, propsHolder
                .getLastPrintedPropertyString());
        addProperty(metadata, Metadata.LAST_MODIFIED, propsHolder
                .getModifiedProperty());
        addProperty(metadata, Metadata.REVISION_NUMBER, propsHolder
                .getRevisionProperty());
        addProperty(metadata, Metadata.SUBJECT, propsHolder
                .getSubjectProperty());
        addProperty(metadata, Metadata.TITLE, propsHolder.getTitleProperty());
        addProperty(metadata, Metadata.VERSION, propsHolder.getVersionProperty());
    }

    private void extractMetadata(ExtendedProperties properties,
            Metadata metadata) {
        CTProperties propsHolder = properties.getUnderlyingProperties();

        addProperty(metadata, Metadata.APPLICATION_NAME, propsHolder
                .getApplication());
        addProperty(metadata, Metadata.APPLICATION_VERSION, propsHolder
                .getAppVersion());
        addProperty(metadata, Metadata.CHARACTER_COUNT, propsHolder
                .getCharacters());
        addProperty(metadata, Metadata.CHARACTER_COUNT_WITH_SPACES, propsHolder
                .getCharactersWithSpaces());
        addProperty(metadata, Metadata.PUBLISHER, propsHolder.getCompany());
        addProperty(metadata, Metadata.LINE_COUNT, propsHolder.getLines());
        addProperty(metadata, Metadata.MANAGER, propsHolder.getManager());
        addProperty(metadata, Metadata.NOTES, propsHolder.getNotes());
        addProperty(metadata, Metadata.PAGE_COUNT, propsHolder.getPages());
        if (propsHolder.getPages() > 0) {
            metadata.set(PagedText.N_PAGES, propsHolder.getPages());
        } else if (propsHolder.getSlides() > 0) {
            metadata.set(PagedText.N_PAGES, propsHolder.getSlides());
        }
        addProperty(metadata, Metadata.PARAGRAPH_COUNT, propsHolder.getParagraphs());
        addProperty(metadata, Metadata.PRESENTATION_FORMAT, propsHolder
                .getPresentationFormat());
        addProperty(metadata, Metadata.SLIDE_COUNT, propsHolder.getSlides());
        addProperty(metadata, Metadata.TEMPLATE, propsHolder.getTemplate());
        addProperty(metadata, Metadata.TOTAL_TIME, propsHolder.getTotalTime());
        addProperty(metadata, Metadata.WORD_COUNT, propsHolder.getWords());
    }

    private void addProperty(Metadata metadata, Property property, Nullable<Date> value) {
        if (value.getValue() != null) {
            metadata.set(property, value.getValue());
        }
    }

    private void addProperty(Metadata metadata, String name, Nullable<?> value) {
        if (value.getValue() != null) {
            addProperty(metadata, name, value.getValue().toString());
        }
    }

    private void addProperty(Metadata metadata, String name, String value) {
        if (value != null) {
            metadata.set(name, value);
        }
    }

    private void addProperty(Metadata metadata, String name, long value) {
        if (value > 0) {
            metadata.set(name, Long.toString(value));
        }
    }
}
