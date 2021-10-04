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

import java.math.BigDecimal;
import java.util.Date;
import java.util.Optional;

import org.apache.poi.ooxml.POIXMLProperties;
import org.apache.poi.ooxml.extractor.POIXMLTextExtractor;
import org.apache.poi.openxml4j.opc.internal.PackagePropertiesPart;
import org.apache.poi.openxml4j.util.Nullable;
import org.apache.poi.xssf.extractor.XSSFEventBasedExcelExtractor;
import org.apache.xmlbeans.impl.values.XmlValueOutOfRangeException;
import org.openxmlformats.schemas.officeDocument.x2006.customProperties.CTProperty;
import org.openxmlformats.schemas.officeDocument.x2006.extendedProperties.CTProperties;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.DublinCore;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Office;
import org.apache.tika.metadata.OfficeOpenXMLCore;
import org.apache.tika.metadata.OfficeOpenXMLExtended;
import org.apache.tika.metadata.PagedText;
import org.apache.tika.metadata.Property;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.microsoft.SummaryExtractor;
import org.apache.tika.parser.microsoft.ooxml.xps.XPSTextExtractor;
import org.apache.tika.parser.microsoft.ooxml.xslf.XSLFEventBasedPowerPointExtractor;
import org.apache.tika.parser.microsoft.ooxml.xwpf.XWPFEventBasedWordExtractor;

/**
 * OOXML metadata extractor.
 * <p/>
 * Currently POI doesn't support metadata extraction for OOXML.
 *
 * @see OOXMLExtractor#getMetadataExtractor()
 */
public class MetadataExtractor {

    private final POIXMLTextExtractor extractor;

    public MetadataExtractor(POIXMLTextExtractor extractor) {
        this.extractor = extractor;
    }

    public void extract(Metadata metadata) throws TikaException {
        if (extractor.getDocument() != null ||
                ((extractor instanceof XSSFEventBasedExcelExtractor ||
                        extractor instanceof XWPFEventBasedWordExtractor ||
                        extractor instanceof XSLFEventBasedPowerPointExtractor ||
                        extractor instanceof XPSTextExtractor) && extractor.getPackage() != null)) {
            extractMetadata(extractor.getCoreProperties(), metadata);
            extractMetadata(extractor.getExtendedProperties(), metadata);
            extractMetadata(extractor.getCustomProperties(), metadata);
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

    private void extractMetadata(POIXMLProperties.CustomProperties properties, Metadata metadata) {
        org.openxmlformats.schemas.officeDocument.x2006.customProperties.CTProperties props =
                properties.getUnderlyingProperties();
        for (int i = 0; i < props.sizeOfPropertyArray(); i++) {
            CTProperty property = props.getPropertyArray(i);
            String val = null;
            Date date = null;

            if (property.isSetLpwstr()) {
                val = property.getLpwstr();
            } else if (property.isSetLpstr()) {
                val = property.getLpstr();
            } else if (property.isSetDate()) {
                date = property.getDate().getTime();
            } else if (property.isSetFiletime()) {
                date = property.getFiletime().getTime();
            } else if (property.isSetBool()) {
                val = Boolean.toString(property.getBool());
            }

            // Integers
            else if (property.isSetI1()) {
                val = Integer.toString(property.getI1());
            } else if (property.isSetI2()) {
                val = Integer.toString(property.getI2());
            } else if (property.isSetI4()) {
                val = Integer.toString(property.getI4());
            } else if (property.isSetI8()) {
                val = Long.toString(property.getI8());
            } else if (property.isSetInt()) {
                val = Integer.toString(property.getInt());
            }

            // Unsigned Integers
            else if (property.isSetUi1()) {
                val = Integer.toString(property.getUi1());
            } else if (property.isSetUi2()) {
                val = Integer.toString(property.getUi2());
            } else if (property.isSetUi4()) {
                val = Long.toString(property.getUi4());
            } else if (property.isSetUi8()) {
                val = property.getUi8().toString();
            } else if (property.isSetUint()) {
                val = Long.toString(property.getUint());
            }

            // Reals
            else if (property.isSetR4()) {
                val = Float.toString(property.getR4());
            } else if (property.isSetR8()) {
                val = Double.toString(property.getR8());
            } else if (property.isSetDecimal()) {
                BigDecimal d = property.getDecimal();
                if (d == null) {
                    val = null;
                } else {
                    val = d.toPlainString();
                }
            } else if (property.isSetArray()) {
                // TODO Fetch the array values and output
            } else if (property.isSetVector()) {
                // TODO Fetch the vector values and output
            } else if (property.isSetBlob() || property.isSetOblob()) {
                // TODO Decode, if possible
            } else if (property.isSetStream() || property.isSetOstream() ||
                    property.isSetVstream()) {
                // TODO Decode, if possible
            } else if (property.isSetStorage() || property.isSetOstorage()) {
                // TODO Decode, if possible
            } else {
                // This type isn't currently supported yet, skip the property
            }

            String propName = "custom:" + property.getName();
            if (date != null) {
                Property tikaProp = Property.externalDate(propName);
                metadata.set(tikaProp, date);
            } else if (val != null) {
                metadata.set(propName, val);
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

    private void setProperty(Metadata metadata, String name, Nullable<?> value) {
        if (value.getValue() != null) {
            setProperty(metadata, name, value.getValue().toString());
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
