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

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.Date;
import java.util.Optional;

import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.opc.PackagePart;
import org.apache.poi.openxml4j.opc.PackageProperties;
import org.apache.poi.openxml4j.opc.PackageRelationship;
import org.apache.poi.openxml4j.opc.PackageRelationshipCollection;
import org.xml.sax.Attributes;
import org.xml.sax.helpers.DefaultHandler;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.DublinCore;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Office;
import org.apache.tika.metadata.OfficeOpenXMLCore;
import org.apache.tika.metadata.OfficeOpenXMLExtended;
import org.apache.tika.metadata.PagedText;
import org.apache.tika.metadata.Property;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.microsoft.SummaryExtractor;
import org.apache.tika.utils.XMLReaderUtils;

/**
 * SAX-based metadata extractor for OOXML documents that reads document properties
 * directly from the OPC package without needing POIXMLProperties or ooxml-lite schemas.
 * <p>
 * Core properties are read from {@link PackagePropertiesPart} (OPC level).
 * Extended properties (app.xml) and custom properties (custom.xml) are parsed with SAX.
 */
class SAXBasedMetadataExtractor extends MetadataExtractor {

    private static final String EXTENDED_PROPERTIES_REL =
            "http://schemas.openxmlformats.org/officeDocument/2006/relationships/extended-properties";
    private static final String CUSTOM_PROPERTIES_REL =
            "http://schemas.openxmlformats.org/officeDocument/2006/relationships/custom-properties";

    private final OPCPackage opcPackage;
    private final ParseContext parseContext;

    SAXBasedMetadataExtractor(OPCPackage opcPackage, ParseContext parseContext) {
        this.opcPackage = opcPackage;
        this.parseContext = parseContext;
    }

    @Override
    public void extract(Metadata metadata) throws TikaException {
        extractCoreProperties(metadata);
        extractExtendedProperties(metadata);
        extractCustomProperties(metadata);
    }

    private void extractCoreProperties(Metadata metadata) {
        try {
            PackageProperties props = opcPackage.getPackageProperties();
            if (props == null) {
                return;
            }
            setProperty(metadata, OfficeOpenXMLCore.CATEGORY, props.getCategoryProperty());
            setProperty(metadata, OfficeOpenXMLCore.CONTENT_STATUS,
                    props.getContentStatusProperty());
            setProperty(metadata, TikaCoreProperties.CREATED, props.getCreatedProperty());
            addMultiProperty(metadata, TikaCoreProperties.CREATOR, props.getCreatorProperty());
            setProperty(metadata, TikaCoreProperties.DESCRIPTION,
                    props.getDescriptionProperty());
            setProperty(metadata, TikaCoreProperties.IDENTIFIER, props.getIdentifierProperty());
            addProperty(metadata, DublinCore.SUBJECT, props.getSubjectProperty());
            addProperty(metadata, Office.KEYWORDS, props.getKeywordsProperty());
            setProperty(metadata, TikaCoreProperties.LANGUAGE, props.getLanguageProperty());
            setProperty(metadata, TikaCoreProperties.MODIFIER,
                    props.getLastModifiedByProperty());
            setProperty(metadata, TikaCoreProperties.PRINT_DATE,
                    props.getLastPrintedProperty());
            setProperty(metadata, TikaCoreProperties.MODIFIED, props.getModifiedProperty());
            setProperty(metadata, OfficeOpenXMLCore.REVISION, props.getRevisionProperty());
            setProperty(metadata, TikaCoreProperties.TITLE, props.getTitleProperty());
            setProperty(metadata, OfficeOpenXMLCore.VERSION, props.getVersionProperty());
        } catch (Exception e) {
            //swallow
        }
    }

    private void extractExtendedProperties(Metadata metadata) {
        try {
            PackagePart extPart = getRelatedPart(EXTENDED_PROPERTIES_REL);
            if (extPart == null) {
                return;
            }
            ExtendedPropertiesHandler handler = new ExtendedPropertiesHandler();
            try (InputStream is = extPart.getInputStream()) {
                XMLReaderUtils.parseSAX(is, handler, parseContext);
            }
            handler.applyTo(metadata);
        } catch (Exception e) {
            //swallow
        }
    }

    private void extractCustomProperties(Metadata metadata) {
        try {
            PackagePart custPart = getRelatedPart(CUSTOM_PROPERTIES_REL);
            if (custPart == null) {
                return;
            }
            CustomPropertiesHandler handler = new CustomPropertiesHandler();
            try (InputStream is = custPart.getInputStream()) {
                XMLReaderUtils.parseSAX(is, handler, parseContext);
            }
            handler.applyTo(metadata);
        } catch (Exception e) {
            //swallow
        }
    }

    private PackagePart getRelatedPart(String relationshipType) {
        try {
            PackageRelationshipCollection rels =
                    opcPackage.getRelationshipsByType(relationshipType);
            if (rels == null || rels.size() == 0) {
                return null;
            }
            PackageRelationship rel = rels.getRelationship(0);
            if (rel == null) {
                return null;
            }
            return opcPackage.getPart(rel);
        } catch (Exception e) {
            return null;
        }
    }

    private <T> void setProperty(Metadata metadata, Property property,
                                 Optional<T> optionalValue) {
        if (!optionalValue.isPresent()) {
            return;
        }
        T value = optionalValue.get();
        if (value instanceof Date) {
            metadata.set(property, (Date) value);
        } else if (value instanceof String) {
            metadata.set(property, (String) value);
        } else if (value instanceof Integer) {
            metadata.set(property, (Integer) value);
        } else if (value instanceof Double) {
            metadata.set(property, (Double) value);
        }
    }

    private <T> void addProperty(Metadata metadata, Property property,
                                 Optional<T> optionalValue) {
        if (!optionalValue.isPresent()) {
            return;
        }
        T value = optionalValue.get();
        if (value instanceof String) {
            metadata.add(property, (String) value);
        }
    }

    private void addMultiProperty(Metadata metadata, Property property,
                                  Optional<String> value) {
        if (!value.isPresent()) {
            return;
        }
        SummaryExtractor.addMulti(metadata, property, value.get());
    }

    /**
     * SAX handler for docProps/app.xml (extended properties).
     */
    private static class ExtendedPropertiesHandler extends DefaultHandler {

        private String application;
        private String appVersion;
        private String company;
        private String manager;
        private String notes;
        private String presentationFormat;
        private String template;
        private int totalTime;
        private int docSecurity;
        private int pages;
        private int slides;
        private int paragraphs;
        private int lines;
        private int words;
        private int characters;
        private int charactersWithSpaces;

        private String currentElement;
        private final StringBuilder textBuffer = new StringBuilder();

        @Override
        public void startElement(String uri, String localName, String qName, Attributes atts) {
            currentElement = localName;
            textBuffer.setLength(0);
        }

        @Override
        public void characters(char[] ch, int start, int length) {
            textBuffer.append(ch, start, length);
        }

        @Override
        public void endElement(String uri, String localName, String qName) {
            if (!localName.equals(currentElement)) {
                return;
            }
            String val = textBuffer.toString().trim();
            if (val.isEmpty()) {
                currentElement = null;
                return;
            }
            switch (localName) {
                case "Application":
                    application = val;
                    break;
                case "AppVersion":
                    appVersion = val;
                    break;
                case "Company":
                    company = val;
                    break;
                case "Manager":
                    manager = val;
                    break;
                case "Notes":
                    notes = val;
                    break;
                case "PresentationFormat":
                    presentationFormat = val;
                    break;
                case "Template":
                    template = val;
                    break;
                case "TotalTime":
                    totalTime = safeParseInt(val);
                    break;
                case "DocSecurity":
                    docSecurity = safeParseInt(val);
                    break;
                case "Pages":
                    pages = safeParseInt(val);
                    break;
                case "Slides":
                    slides = safeParseInt(val);
                    break;
                case "Paragraphs":
                    paragraphs = safeParseInt(val);
                    break;
                case "Lines":
                    lines = safeParseInt(val);
                    break;
                case "Words":
                    words = safeParseInt(val);
                    break;
                case "Characters":
                    characters = safeParseInt(val);
                    break;
                case "CharactersWithSpaces":
                    charactersWithSpaces = safeParseInt(val);
                    break;
                default:
                    break;
            }
            currentElement = null;
        }

        private int safeParseInt(String val) {
            try {
                // Handle unsigned int overflow (TIKA-2055)
                long l = Long.parseLong(val);
                if (l > Integer.MAX_VALUE || l < 0) {
                    return 0;
                }
                return (int) l;
            } catch (NumberFormatException e) {
                return 0;
            }
        }

        void applyTo(Metadata metadata) {
            setIfNotNull(metadata, OfficeOpenXMLExtended.APPLICATION, application);
            setIfNotNull(metadata, OfficeOpenXMLExtended.APP_VERSION, appVersion);
            setIfNotNull(metadata, TikaCoreProperties.PUBLISHER, company);
            setIfNotNull(metadata, OfficeOpenXMLExtended.COMPANY, company);
            if (manager != null) {
                SummaryExtractor.addMulti(metadata, OfficeOpenXMLExtended.MANAGER, manager);
            }
            setIfNotNull(metadata, OfficeOpenXMLExtended.NOTES, notes);
            setIfNotNull(metadata, OfficeOpenXMLExtended.PRESENTATION_FORMAT, presentationFormat);
            setIfNotNull(metadata, OfficeOpenXMLExtended.TEMPLATE, template);
            setIfPositive(metadata, OfficeOpenXMLExtended.TOTAL_TIME, totalTime);
            setIfPositive(metadata, OfficeOpenXMLExtended.DOC_SECURITY, docSecurity);
            metadata.set(OfficeOpenXMLExtended.DOC_SECURITY_STRING,
                    getDocSecurityString(docSecurity));

            if (pages > 0) {
                metadata.set(PagedText.N_PAGES, pages);
            } else if (slides > 0) {
                metadata.set(PagedText.N_PAGES, slides);
            }

            setIfPositive(metadata, Office.PAGE_COUNT, pages);
            setIfPositive(metadata, Office.SLIDE_COUNT, slides);
            setIfPositive(metadata, Office.PARAGRAPH_COUNT, paragraphs);
            setIfPositive(metadata, Office.LINE_COUNT, lines);
            setIfPositive(metadata, Office.WORD_COUNT, words);
            setIfPositive(metadata, Office.CHARACTER_COUNT, characters);
            setIfPositive(metadata, Office.CHARACTER_COUNT_WITH_SPACES, charactersWithSpaces);
        }

        private void setIfNotNull(Metadata metadata, Property property, String value) {
            if (value != null) {
                metadata.set(property, value);
            }
        }

        private void setIfPositive(Metadata metadata, Property property, int value) {
            if (value > 0) {
                metadata.set(property, value);
            }
        }

        private String getDocSecurityString(int flag) {
            switch (flag) {
                case 0:
                    return OfficeOpenXMLExtended.SECURITY_NONE;
                case 1:
                    return OfficeOpenXMLExtended.SECURITY_PASSWORD_PROTECTED;
                case 2:
                    return OfficeOpenXMLExtended.SECURITY_READ_ONLY_RECOMMENDED;
                case 4:
                    return OfficeOpenXMLExtended.SECURITY_READ_ONLY_ENFORCED;
                case 8:
                    return OfficeOpenXMLExtended.SECURITY_LOCKED_FOR_ANNOTATIONS;
                default:
                    return OfficeOpenXMLExtended.SECURITY_UNKNOWN;
            }
        }
    }

    /**
     * SAX handler for docProps/custom.xml (custom properties).
     */
    private static class CustomPropertiesHandler extends DefaultHandler {

        private static final String VT_NS =
                "http://schemas.openxmlformats.org/officeDocument/2006/docPropsVTypes";

        private final Metadata customMetadata = new Metadata();
        private String currentPropertyName;
        private String currentValueType;
        private final StringBuilder textBuffer = new StringBuilder();

        @Override
        public void startElement(String uri, String localName, String qName, Attributes atts) {
            if ("property".equals(localName)) {
                currentPropertyName = atts.getValue("name");
                currentValueType = null;
            } else if (VT_NS.equals(uri) && currentPropertyName != null) {
                currentValueType = localName;
                textBuffer.setLength(0);
            }
        }

        @Override
        public void characters(char[] ch, int start, int length) {
            textBuffer.append(ch, start, length);
        }

        @Override
        public void endElement(String uri, String localName, String qName) {
            if (VT_NS.equals(uri) && currentValueType != null &&
                    localName.equals(currentValueType) && currentPropertyName != null) {
                String val = textBuffer.toString().trim();
                String propName = "custom:" + currentPropertyName;
                switch (currentValueType) {
                    case "lpwstr":
                    case "lpstr":
                    case "bstr":
                        customMetadata.set(propName, val);
                        break;
                    case "filetime":
                    case "date":
                        Property tikaProp = Property.externalDate(propName);
                        customMetadata.set(tikaProp, val);
                        break;
                    case "bool":
                        customMetadata.set(propName, val);
                        break;
                    case "i1":
                    case "i2":
                    case "i4":
                    case "int":
                    case "ui1":
                    case "ui2":
                        customMetadata.set(propName, val);
                        break;
                    case "i8":
                    case "ui4":
                    case "ui8":
                    case "uint":
                        customMetadata.set(propName, val);
                        break;
                    case "r4":
                    case "r8":
                        customMetadata.set(propName, val);
                        break;
                    case "decimal":
                        try {
                            BigDecimal d = new BigDecimal(val);
                            customMetadata.set(propName, d.toPlainString());
                        } catch (NumberFormatException e) {
                            //swallow
                        }
                        break;
                    default:
                        break;
                }
                currentValueType = null;
            } else if ("property".equals(localName)) {
                currentPropertyName = null;
            }
        }

        void applyTo(Metadata metadata) {
            for (String name : customMetadata.names()) {
                for (String value : customMetadata.getValues(name)) {
                    metadata.add(name, value);
                }
            }
        }
    }
}
