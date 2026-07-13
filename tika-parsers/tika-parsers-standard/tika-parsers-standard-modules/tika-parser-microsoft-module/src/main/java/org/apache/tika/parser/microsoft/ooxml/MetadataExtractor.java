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

import org.apache.poi.ooxml.POIXMLProperties;
import org.apache.poi.ooxml.extractor.POIXMLTextExtractor;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.opc.PackagePart;
import org.apache.poi.openxml4j.opc.PackageRelationship;
import org.apache.poi.openxml4j.opc.PackageRelationshipCollection;
import org.apache.poi.openxml4j.opc.internal.PackagePropertiesPart;
import org.apache.poi.xssf.extractor.XSSFEventBasedExcelExtractor;
import org.apache.xmlbeans.impl.values.XmlValueOutOfRangeException;
import org.openxmlformats.schemas.officeDocument.x2006.extendedProperties.CTProperties;
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
import org.apache.tika.parser.microsoft.ooxml.xps.XPSTextExtractor;
import org.apache.tika.parser.microsoft.ooxml.xslf.XSLFEventBasedPowerPointExtractor;
import org.apache.tika.parser.microsoft.ooxml.xwpf.XWPFEventBasedWordExtractor;
import org.apache.tika.utils.XMLReaderUtils;

/**
 * OOXML metadata extractor.
 * <p/>
 * Currently POI doesn't support metadata extraction for OOXML.
 *
 * @see OOXMLExtractor#getMetadataExtractor()
 */
public class MetadataExtractor {

    private static final String CUSTOM_PROPERTIES_REL =
            "http://schemas.openxmlformats.org/officeDocument/2006/relationships/custom-properties";

    /**
     * Hard cap on the accumulated text-content of a single property element
     * inside docProps/custom.xml. Real OOXML property values are at most a few
     * hundred bytes; anything beyond this is malformed (e.g. a {@code <vt:decimal>}
     * with a 1M-digit literal). 64 KB leaves headroom for any legitimate value
     * while bounding the slow-path inputs.
     */
    static final int MAX_TEXT_BUFFER_LENGTH = 64 * 1024;

    /**
     * Hard cap on the {@code <vt:decimal>} text length passed to
     * {@link BigDecimal#BigDecimal(String)}. JDK 17's parser is O(n²) in the
     * digit count, so even a 64 KB string costs noticeable CPU. Real-world
     * decimal values fit in well under 50 digits; 256 is generous.
     */
    static final int MAX_DECIMAL_LENGTH = 256;

    private final POIXMLTextExtractor extractor;

    public MetadataExtractor(POIXMLTextExtractor extractor) {
        this.extractor = extractor;
    }

    /**
     * For subclasses (e.g. {@link SAXBasedMetadataExtractor}) that read metadata directly from
     * the OPC package rather than through a {@link POIXMLTextExtractor}. Such subclasses must
     * override {@link #extract(Metadata)}; the extractor-based path here is never invoked for them.
     */
    protected MetadataExtractor() {
        this.extractor = null;
    }

    public void extract(Metadata metadata) throws TikaException {
        if (extractor.getDocument() != null ||
                ((extractor instanceof XSSFEventBasedExcelExtractor ||
                        extractor instanceof XWPFEventBasedWordExtractor ||
                        extractor instanceof XSLFEventBasedPowerPointExtractor ||
                        extractor instanceof XPSTextExtractor) && extractor.getPackage() != null)) {
            extractMetadata(extractor.getCoreProperties(), metadata);
            extractMetadata(extractor.getExtendedProperties(), metadata);
            // Custom properties are read via SAX directly from the OPC part
            // rather than through POI/XMLBeans. The XMLBeans path materializes
            // a <vt:decimal> through BigDecimal(String), which is O(n²) on
            // JDK 17 -- a 3 KB carrier with a 1,000,000-digit literal burns
            // ~25 s of CPU before this method even returns.
            extractCustomPropertiesViaSAX(extractor.getPackage(), metadata);
        }
    }

    private void extractMetadata(POIXMLProperties.CoreProperties properties, Metadata metadata) {
        PackagePropertiesPart propsHolder = properties.getUnderlyingProperties();

        setProperty(metadata, OfficeOpenXMLCore.CATEGORY, propsHolder.getCategoryProperty());
        setProperty(metadata, OfficeOpenXMLCore.CONTENT_STATUS,
                propsHolder.getContentStatusProperty());
        setProperty(metadata, TikaCoreProperties.CREATED, propsHolder.getCreatedProperty());
        addMultiProperty(metadata, TikaCoreProperties.CREATOR, propsHolder.getCreatorProperty());
        setProperty(metadata, TikaCoreProperties.DESCRIPTION, propsHolder.getDescriptionProperty());
        setProperty(metadata, TikaCoreProperties.IDENTIFIER, propsHolder.getIdentifierProperty());
        addProperty(metadata, DublinCore.SUBJECT, propsHolder.getSubjectProperty());
        addProperty(metadata, Office.KEYWORDS, propsHolder.getKeywordsProperty());
        setProperty(metadata, TikaCoreProperties.LANGUAGE, propsHolder.getLanguageProperty());
        setProperty(metadata, TikaCoreProperties.MODIFIER, propsHolder.getLastModifiedByProperty());
        setProperty(metadata, TikaCoreProperties.PRINT_DATE, propsHolder.getLastPrintedProperty());
        setProperty(metadata, TikaCoreProperties.MODIFIED, propsHolder.getModifiedProperty());
        setProperty(metadata, OfficeOpenXMLCore.REVISION, propsHolder.getRevisionProperty());

        setProperty(metadata, TikaCoreProperties.TITLE, propsHolder.getTitleProperty());
        setProperty(metadata, OfficeOpenXMLCore.VERSION, propsHolder.getVersionProperty());

    }

    private void extractMetadata(POIXMLProperties.ExtendedProperties properties,
                                 Metadata metadata) {
        CTProperties propsHolder = properties.getUnderlyingProperties();

        //TIKA-2055, some ooxml files can include unsigned int/long values
        //which cause this exception.
        //For now, catch it and record as '0' because
        //Word converts to '0' on save.
        int totalTime = 0;
        try {
            totalTime = propsHolder.getTotalTime();
        } catch (XmlValueOutOfRangeException e) {
            //swallow for now
        }
        setProperty(metadata, OfficeOpenXMLExtended.APPLICATION, propsHolder.getApplication());
        setProperty(metadata, OfficeOpenXMLExtended.APP_VERSION, propsHolder.getAppVersion());
        setProperty(metadata, TikaCoreProperties.PUBLISHER, propsHolder.getCompany());
        setProperty(metadata, OfficeOpenXMLExtended.COMPANY, propsHolder.getCompany());
        SummaryExtractor
                .addMulti(metadata, OfficeOpenXMLExtended.MANAGER, propsHolder.getManager());
        setProperty(metadata, OfficeOpenXMLExtended.NOTES, propsHolder.getNotes());
        setProperty(metadata, OfficeOpenXMLExtended.PRESENTATION_FORMAT,
                propsHolder.getPresentationFormat());
        setProperty(metadata, OfficeOpenXMLExtended.TEMPLATE, propsHolder.getTemplate());
        setProperty(metadata, OfficeOpenXMLExtended.TOTAL_TIME, totalTime);
        int docSecurityFlag = propsHolder.getDocSecurity();
        setProperty(metadata, OfficeOpenXMLExtended.DOC_SECURITY, docSecurityFlag);
        setProperty(metadata, OfficeOpenXMLExtended.DOC_SECURITY_STRING,
                getDocSecurityString(docSecurityFlag));
        if (propsHolder.getPages() > 0) {
            metadata.set(PagedText.N_PAGES, propsHolder.getPages());
        } else if (propsHolder.getSlides() > 0) {
            metadata.set(PagedText.N_PAGES, propsHolder.getSlides());
        }

        // Process the document statistics
        setProperty(metadata, Office.PAGE_COUNT, propsHolder.getPages());
        setProperty(metadata, Office.SLIDE_COUNT, propsHolder.getSlides());
        setProperty(metadata, Office.PARAGRAPH_COUNT, propsHolder.getParagraphs());
        setProperty(metadata, Office.LINE_COUNT, propsHolder.getLines());
        setProperty(metadata, Office.WORD_COUNT, propsHolder.getWords());
        setProperty(metadata, Office.CHARACTER_COUNT, propsHolder.getCharacters());
        setProperty(metadata, Office.CHARACTER_COUNT_WITH_SPACES,
                propsHolder.getCharactersWithSpaces());
    }

    private String getDocSecurityString(int docSecurityFlag) {
        //mappings from: https://exiftool.org/TagNames/OOXML.html and
        //https://docs.microsoft.com/en-us/dotnet/api/documentformat.openxml.extendedproperties.documentsecurity?view=openxml-2.8.1
        switch (docSecurityFlag) {
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

    /**
     * Parse {@code docProps/custom.xml} directly via SAX, bypassing
     * POI/XMLBeans. The XMLBeans path materializes a {@code <vt:decimal>}
     * through {@link BigDecimal#BigDecimal(String)} during XML
     * deserialization, which is O(n²) in the digit count on JDK 17. By
     * reading the part ourselves we can cap both the buffered text content
     * ({@link #MAX_TEXT_BUFFER_LENGTH}) and the decimal literal length
     * ({@link #MAX_DECIMAL_LENGTH}) before any slow parse
     * runs.
     */
    private void extractCustomPropertiesViaSAX(OPCPackage opcPackage, Metadata metadata) {
        if (opcPackage == null) {
            return;
        }
        try {
            PackagePart custPart = getRelatedPart(opcPackage, CUSTOM_PROPERTIES_REL);
            if (custPart == null) {
                return;
            }
            CustomPropertiesHandler handler = new CustomPropertiesHandler();
            try (InputStream is = custPart.getInputStream()) {
                XMLReaderUtils.parseSAX(is, handler, new ParseContext());
            }
            handler.applyTo(metadata);
        } catch (Exception e) {
            //swallow
        }
    }

    private static PackagePart getRelatedPart(OPCPackage opcPackage, String relationshipType) {
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

    /**
     * Append SAX {@code characters()} content to {@code buf}, but stop accepting
     * once {@link #MAX_TEXT_BUFFER_LENGTH} is reached. Excess characters are
     * silently dropped; truncated values still flow through downstream parsing.
     */
    static void appendCapped(StringBuilder buf, char[] ch, int start, int length) {
        if (buf.length() >= MAX_TEXT_BUFFER_LENGTH) {
            return;
        }
        int remaining = MAX_TEXT_BUFFER_LENGTH - buf.length();
        buf.append(ch, start, Math.min(length, remaining));
    }

    /**
     * SAX handler for {@code docProps/custom.xml} (custom properties).
     * Matches the schema defined by Microsoft's
     * {@code http://schemas.openxmlformats.org/officeDocument/2006/custom-properties}
     * namespace, with value types coming from the {@code vt:} namespace.
     */
    static class CustomPropertiesHandler extends DefaultHandler {

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
            } else if (VT_NS.equals(uri) && currentPropertyName != null
                    && currentValueType == null) {
                // Only the direct vt: child of <property> is captured.
                // Containers like <vt:vector>/<vt:array> latch currentValueType
                // here and their scalar children are then ignored, matching the
                // prior POI/XMLBeans behavior which skipped vectors/arrays.
                currentValueType = localName;
                textBuffer.setLength(0);
            }
        }

        @Override
        public void characters(char[] ch, int start, int length) {
            appendCapped(textBuffer, ch, start, length);
        }

        @Override
        public void endElement(String uri, String localName, String qName) {
            if (VT_NS.equals(uri) && currentValueType != null &&
                    localName.equals(currentValueType) && currentPropertyName != null) {
                String raw = textBuffer.toString();
                String trimmed = raw.trim();
                String propName = "custom:" + currentPropertyName;
                switch (currentValueType) {
                    case "lpwstr":
                    case "lpstr":
                    case "bstr":
                        // String values are user-controlled metadata content;
                        // preserve leading/trailing whitespace as the prior
                        // POI getLpwstr()/getLpstr() path did.
                        customMetadata.set(propName, raw);
                        break;
                    case "filetime":
                    case "date":
                        Property tikaProp = Property.externalDate(propName);
                        customMetadata.set(tikaProp, trimmed);
                        break;
                    case "bool":
                        // xs:boolean lexical space allows "1"/"0" alongside
                        // "true"/"false"; the prior POI path emitted
                        // Boolean.toString(...). Preserve that normalization.
                        if ("1".equals(trimmed) || "true".equalsIgnoreCase(trimmed)) {
                            customMetadata.set(propName, "true");
                        } else if ("0".equals(trimmed) || "false".equalsIgnoreCase(trimmed)) {
                            customMetadata.set(propName, "false");
                        }
                        break;
                    case "i1":
                    case "i2":
                    case "i4":
                    case "int":
                    case "ui1":
                    case "ui2":
                        customMetadata.set(propName, trimmed);
                        break;
                    case "i8":
                    case "ui4":
                    case "ui8":
                    case "uint":
                        customMetadata.set(propName, trimmed);
                        break;
                    case "r4":
                    case "r8":
                        customMetadata.set(propName, trimmed);
                        break;
                    case "decimal":
                        // BigDecimal(String) is O(n²) on JDK 17; cap the input
                        // length so a large <vt:decimal> can't burn CPU. Real
                        // values are < 50 chars; 256 is generous.
                        if (trimmed.length() > MAX_DECIMAL_LENGTH) {
                            break;
                        }
                        try {
                            BigDecimal d = new BigDecimal(trimmed);
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
                currentValueType = null;
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

    private <T> void setProperty(Metadata metadata, Property property, Optional<T> optionalValue) {
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

    private <T> void addProperty(Metadata metadata, Property property, Optional<T> optionalValue) {
        if (!optionalValue.isPresent()) {
            return;
        }
        T value = optionalValue.get();
        if (value instanceof String) {
            metadata.add(property, (String) value);
        } else {
            throw new IllegalArgumentException(
                    "Can't add property of class: " + optionalValue.getClass());
        }
    }

    private void setProperty(Metadata metadata, Property property, String value) {
        if (value != null) {
            metadata.set(property, value);
        }
    }

    private void setProperty(Metadata metadata, String name, String value) {
        if (value != null) {
            metadata.set(name, value);
        }
    }

    private void setProperty(Metadata metadata, Property property, int value) {
        if (value > 0) {
            metadata.set(property, value);
        }
    }

    private void setProperty(Metadata metadata, String name, int value) {
        if (value > 0) {
            metadata.set(name, Integer.toString(value));
        }
    }

    private void addMultiProperty(Metadata metadata, Property property, Optional<String> value) {
        if (!value.isPresent()) {
            return;
        }
        SummaryExtractor.addMulti(metadata, property, value.get());
    }

}
