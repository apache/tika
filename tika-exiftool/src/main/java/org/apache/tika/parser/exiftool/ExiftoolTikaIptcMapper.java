/*
 * Copyright (C) 2005-2013 Alfresco Software Limited.
 *
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.tika.metadata.IPTC;
import org.apache.tika.metadata.Property;
import org.apache.tika.metadata.TikaCoreProperties;

/**
 * Class for mapping Tika IPTC metadata names to and from ExifTool metadata names.
 *
 * @author rgauss
 *
 */
public class ExiftoolTikaIptcMapper implements ExiftoolTikaMapper {

    private static final Map<Property, List<Property>> _tikaToExiftoolMetadataMap = new HashMap<Property, List<Property>>();
    private static final Map<Property, List<Property>> _exiftoolToTikaMetadataMap = new HashMap<Property, List<Property>>();

    static {
        _tikaToExiftoolMetadataMap.put(
                IPTC.CITY,
                Arrays.asList(
                    ExifToolMetadata.XMP_PHOTOSHOP_CITY,
                    ExifToolMetadata.IPTC_CITY ));
        _tikaToExiftoolMetadataMap.put(
                IPTC.COUNTRY,
                Arrays.asList(
                    ExifToolMetadata.XMP_PHOTOSHOP_COUNTRY,
                    ExifToolMetadata.IPTC_COUNTRY_PRIMARY_LOCATION_NAME ));
        _tikaToExiftoolMetadataMap.put(
                IPTC.COUNTRY_CODE,
                Arrays.asList(
                    ExifToolMetadata.XMP_IPTCCORE_COUNTRY_CODE,
                    ExifToolMetadata.IPTC_COUNTRY_PRIMARY_LOCATION_CODE ));
        _tikaToExiftoolMetadataMap.put(
                IPTC.DESCRIPTION,
                Arrays.asList(
                    ExifToolMetadata.XMP_DC_DESCRIPTION,
                    ExifToolMetadata.IPTC_CAPTION ));
        _tikaToExiftoolMetadataMap.put(
                IPTC.HEADLINE,
                Arrays.asList(
                    ExifToolMetadata.XMP_PHOTOSHOP_HEADLINE,
                    ExifToolMetadata.IPTC_HEADLINE ));
        _tikaToExiftoolMetadataMap.put(
                IPTC.INTELLECTUAL_GENRE,
                Arrays.asList(
                    ExifToolMetadata.XMP_IPTCCORE_INTELLECTUAL_GENRE,
                    ExifToolMetadata.IPTC_OBJECT_ATTRIBUTE_REFERENCE ));
        _tikaToExiftoolMetadataMap.put(
                IPTC.KEYWORDS,
                Arrays.asList(
                    ExifToolMetadata.XMP_DC_SUBJECT,
                    ExifToolMetadata.IPTC_KEYWORDS ));
        _tikaToExiftoolMetadataMap.put(
                IPTC.PROVINCE_OR_STATE,
                Arrays.asList(
                    ExifToolMetadata.XMP_PHOTOSHOP_STATE,
                    ExifToolMetadata.IPTC_PROVINCE_STATE ));
        _tikaToExiftoolMetadataMap.put(
                IPTC.SCENE_CODE,
                Arrays.asList(
                    ExifToolMetadata.XMP_IPTCCORE_SCENE ));
        _tikaToExiftoolMetadataMap.put(
                IPTC.SUBJECT_CODE,
                Arrays.asList(
                    ExifToolMetadata.XMP_IPTCCORE_SUBJECT_CODE ));
        _tikaToExiftoolMetadataMap.put(
                IPTC.SUBLOCATION,
                Arrays.asList(
                    ExifToolMetadata.XMP_IPTCCORE_LOCATION,
                    ExifToolMetadata.IPTC_SUBLOCATION ));
        _tikaToExiftoolMetadataMap.put(
                IPTC.DATE_CREATED,
                Arrays.asList(
                    ExifToolMetadata.XMP_PHOTOSHOP_DATE_CREATED,
                    ExifToolMetadata.IPTC_DATE_CREATED ));
        _tikaToExiftoolMetadataMap.put(
                IPTC.DESCRIPTION_WRITER,
                Arrays.asList(
                    ExifToolMetadata.XMP_PHOTOSHOP_CAPTION_WRITER,
                    ExifToolMetadata.IPTC_WRITER_EDITOR ));
        _tikaToExiftoolMetadataMap.put(
                IPTC.INSTRUCTIONS,
                Arrays.asList(
                    ExifToolMetadata.XMP_PHOTOSHOP_INSTRUCTIONS,
                    ExifToolMetadata.IPTC_SPECIAL_INSTRUCTIONS ));
        _tikaToExiftoolMetadataMap.put(
                IPTC.JOB_ID,
                Arrays.asList(
                    ExifToolMetadata.XMP_PHOTOSHOP_TRANSMISSION_REFERENCE,
                    ExifToolMetadata.IPTC_ORIGINAL_TRANSMISSION_REFERENCE ));
        _tikaToExiftoolMetadataMap.put(
                IPTC.TITLE,
                Arrays.asList(
                    ExifToolMetadata.XMP_DC_TITLE,
                    ExifToolMetadata.IPTC_OBJECT_NAME ));
        _tikaToExiftoolMetadataMap.put(
                IPTC.COPYRIGHT_NOTICE,
                Arrays.asList(
                    ExifToolMetadata.XMP_DC_RIGHTS,
                    ExifToolMetadata.IPTC_COPYRIGHT_NOTICE ));
        _tikaToExiftoolMetadataMap.put(
                IPTC.CREATOR,
                Arrays.asList(
                    ExifToolMetadata.XMP_DC_CREATOR,
                    ExifToolMetadata.IPTC_BY_LINE ));
        _tikaToExiftoolMetadataMap.put(
                IPTC.CREATORS_JOB_TITLE,
                Arrays.asList(
                    ExifToolMetadata.XMP_PHOTOSHOP_AUTHORS_POSITION,
                    ExifToolMetadata.IPTC_BY_LINE_TITLE ));
        _tikaToExiftoolMetadataMap.put(
                IPTC.CREDIT_LINE,
                Arrays.asList(
                    ExifToolMetadata.XMP_PHOTOSHOP_CREDIT,
                    ExifToolMetadata.IPTC_CREDIT ));
        _tikaToExiftoolMetadataMap.put(
                IPTC.RIGHTS_USAGE_TERMS,
                Arrays.asList(
                    ExifToolMetadata.XMP_XMPRIGHTS_USAGE_TERMS ));
        _tikaToExiftoolMetadataMap.put(
                IPTC.SOURCE,
                Arrays.asList(
                    ExifToolMetadata.XMP_PHOTOSHOP_SOURCE,
                    ExifToolMetadata.IPTC_SOURCE ));
        _tikaToExiftoolMetadataMap.put(
                IPTC.CONTACT_INFO_ADDRESS,
                Arrays.asList(
                    ExifToolMetadata.XMP_IPTCCORE_CREATOR_CONTACT_INFO_ADDRESS ));
        _tikaToExiftoolMetadataMap.put(
                IPTC.CONTACT_INFO_CITY,
                Arrays.asList(
                    ExifToolMetadata.XMP_IPTCCORE_CREATOR_CONTACT_INFO_CITY ));
        _tikaToExiftoolMetadataMap.put(
                IPTC.CONTACT_INFO_COUNTRY,
                Arrays.asList(
                    ExifToolMetadata.XMP_IPTCCORE_CREATOR_CONTACT_INFO_COUNTRY ));
        _tikaToExiftoolMetadataMap.put(
                IPTC.CONTACT_INFO_EMAIL,
                Arrays.asList(
                    ExifToolMetadata.XMP_IPTCCORE_CREATOR_CONTACT_INFO_EMAIL_WORK ));
        _tikaToExiftoolMetadataMap.put(
                IPTC.CONTACT_INFO_PHONE,
                Arrays.asList(
                    ExifToolMetadata.XMP_IPTCCORE_CREATOR_CONTACT_INFO_TELEPHONE_WORK ));
        _tikaToExiftoolMetadataMap.put(
                IPTC.CONTACT_INFO_POSTAL_CODE,
                Arrays.asList(
                    ExifToolMetadata.XMP_IPTCCORE_CREATOR_CONTACT_INFO_POSTAL_CODE ));
        _tikaToExiftoolMetadataMap.put(
                IPTC.CONTACT_INFO_STATE_PROVINCE,
                Arrays.asList(
                    ExifToolMetadata.XMP_IPTCCORE_CREATOR_CONTACT_INFO_REGION ));
        _tikaToExiftoolMetadataMap.put(
                IPTC.CONTACT_INFO_WEB_URL,
                Arrays.asList(
                    ExifToolMetadata.XMP_IPTCCORE_CREATOR_CONTACT_INFO_URL_WORK ));

        _tikaToExiftoolMetadataMap.put(
                IPTC.URGENCY,
                Arrays.asList(
                    ExifToolMetadata.XMP_PHOTOSHOP_URGENCY,
                    ExifToolMetadata.IPTC_URGENCY ));
        _tikaToExiftoolMetadataMap.put(
                IPTC.CATEGORY,
                Arrays.asList(
                    ExifToolMetadata.XMP_PHOTOSHOP_CATEGORY,
                    ExifToolMetadata.IPTC_CATEGORY ));
        _tikaToExiftoolMetadataMap.put(
                IPTC.SUPPLEMENTAL_CATEGORIES,
                Arrays.asList(
                    ExifToolMetadata.XMP_PHOTOSHOP_SUPPLEMENTAL_CATEGORIES,
                    ExifToolMetadata.IPTC_SUPPLEMENTAL_CATEGORIES ));

        _tikaToExiftoolMetadataMap.put(
                IPTC.ADDITIONAL_MODEL_INFO,
                Arrays.asList(
                    ExifToolMetadata.XMP_IPTCEXT_ADDITIONAL_MODEL_INFORMATION ));
        _tikaToExiftoolMetadataMap.put(
                IPTC.ORGANISATION_CODE,
                Arrays.asList(
                    ExifToolMetadata.XMP_IPTCEXT_ORGANISATION_IN_IMAGE_CODE ));
        _tikaToExiftoolMetadataMap.put(
                IPTC.CONTROLLED_VOCABULARY_TERM,
                Arrays.asList(
                    ExifToolMetadata.XMP_IPTCEXT_CONTROLLED_VOCABULAR_TERM ));
        _tikaToExiftoolMetadataMap.put(
                IPTC.MODEL_AGE,
                Arrays.asList(
                    ExifToolMetadata.XMP_IPTCEXT_MODEL_AGE ));
        _tikaToExiftoolMetadataMap.put(
                IPTC.ORGANISATION_NAME,
                Arrays.asList(
                    ExifToolMetadata.XMP_IPTCEXT_ORGANISATION_IN_IMAGE_NAME ));
        _tikaToExiftoolMetadataMap.put(
                IPTC.PERSON,
                Arrays.asList(
                    ExifToolMetadata.XMP_IPTCEXT_PERSON_IN_IMAGE ));
        _tikaToExiftoolMetadataMap.put(
                IPTC.DIGITAL_IMAGE_GUID,
                Arrays.asList(
                    ExifToolMetadata.XMP_IPTCEXT_DIGITAL_IMAGE_GUID ));
        _tikaToExiftoolMetadataMap.put(
                IPTC.DIGITAL_SOURCE_TYPE,
                Arrays.asList(
                    ExifToolMetadata.XMP_IPTCEXT_DIGITAL_SOURCE_TYPE ));
        _tikaToExiftoolMetadataMap.put(
                IPTC.EVENT,
                Arrays.asList(
                    ExifToolMetadata.XMP_IPTCEXT_EVENT ));
        _tikaToExiftoolMetadataMap.put(
                IPTC.IMAGE_SUPPLIER_ID,
                Arrays.asList(
                    ExifToolMetadata.XMP_PLUS_IMAGE_SUPPLIER_ID ));
        _tikaToExiftoolMetadataMap.put(
                IPTC.IMAGE_SUPPLIER_NAME,
                Arrays.asList(
                    ExifToolMetadata.XMP_PLUS_IMAGE_SUPPLIER_NAME ));
        _tikaToExiftoolMetadataMap.put(
                IPTC.IMAGE_SUPPLIER_IMAGE_ID,
                Arrays.asList(
                    ExifToolMetadata.XMP_PLUS_IMAGE_SUPPLIER_IMAGE_ID ));
        _tikaToExiftoolMetadataMap.put(
                IPTC.IPTC_LAST_EDITED,
                Arrays.asList(
                    ExifToolMetadata.XMP_IPTCEXT_IPTC_LAST_EDITED ));
        _tikaToExiftoolMetadataMap.put(
                IPTC.MAX_AVAIL_HEIGHT,
                Arrays.asList(
                    ExifToolMetadata.XMP_IPTCEXT_MAX_AVAIL_HEIGHT ));
        _tikaToExiftoolMetadataMap.put(
                IPTC.MAX_AVAIL_WIDTH,
                Arrays.asList(
                    ExifToolMetadata.XMP_IPTCEXT_MAX_AVAIL_WIDTH ));
        _tikaToExiftoolMetadataMap.put(
                IPTC.PLUS_VERSION,
                Arrays.asList(
                    ExifToolMetadata.XMP_PLUS_VERSION ));
        _tikaToExiftoolMetadataMap.put(
                IPTC.COPYRIGHT_OWNER_ID,
                Arrays.asList(
                    ExifToolMetadata.XMP_PLUS_COPYRIGHT_OWNER_ID ));
        _tikaToExiftoolMetadataMap.put(
                IPTC.COPYRIGHT_OWNER_NAME,
                Arrays.asList(
                    ExifToolMetadata.XMP_PLUS_COPYRIGHT_OWNER_NAME ));
        _tikaToExiftoolMetadataMap.put(
                IPTC.IMAGE_CREATOR_ID,
                Arrays.asList(
                    ExifToolMetadata.XMP_PLUS_IMAGE_CREATOR_ID ));
        _tikaToExiftoolMetadataMap.put(
                IPTC.IMAGE_CREATOR_NAME,
                Arrays.asList(
                    ExifToolMetadata.XMP_PLUS_IMAGE_CREATOR_NAME ));
        _tikaToExiftoolMetadataMap.put(
                IPTC.LICENSOR_ID,
                Arrays.asList(
                    ExifToolMetadata.XMP_PLUS_LICENSOR_ID ));
        _tikaToExiftoolMetadataMap.put(
                IPTC.LICENSOR_NAME,
                Arrays.asList(
                    ExifToolMetadata.XMP_PLUS_LICENSOR_NAME ));
        _tikaToExiftoolMetadataMap.put(
                IPTC.LICENSOR_CITY,
                Arrays.asList(
                    ExifToolMetadata.XMP_PLUS_LICENSOR_CITY ));
        _tikaToExiftoolMetadataMap.put(
                IPTC.LICENSOR_COUNTRY,
                Arrays.asList(
                    ExifToolMetadata.XMP_PLUS_LICENSOR_COUNTRY ));
        _tikaToExiftoolMetadataMap.put(
                IPTC.LICENSOR_EMAIL,
                Arrays.asList(
                    ExifToolMetadata.XMP_PLUS_LICENSOR_EMAIL ));
        _tikaToExiftoolMetadataMap.put(
                IPTC.LICENSOR_EXTENDED_ADDRESS,
                Arrays.asList(
                    ExifToolMetadata.XMP_PLUS_LICENSOR_EXTENDED_ADDRESS ));
        _tikaToExiftoolMetadataMap.put(
                IPTC.LICENSOR_POSTAL_CODE,
                Arrays.asList(
                    ExifToolMetadata.XMP_PLUS_LICENSOR_POSTAL_CODE ));
        _tikaToExiftoolMetadataMap.put(
                IPTC.LICENSOR_REGION,
                Arrays.asList(
                    ExifToolMetadata.XMP_PLUS_LICENSOR_REGION ));
        _tikaToExiftoolMetadataMap.put(
                IPTC.LICENSOR_STREET_ADDRESS,
                Arrays.asList(
                    ExifToolMetadata.XMP_PLUS_LICENSOR_STREET_ADDRESS ));
        _tikaToExiftoolMetadataMap.put(
                IPTC.LICENSOR_TELEPHONE_1,
                Arrays.asList(
                    ExifToolMetadata.XMP_PLUS_LICENSOR_TELEPHONE_1 ));
        _tikaToExiftoolMetadataMap.put(
                IPTC.LICENSOR_TELEPHONE_2,
                Arrays.asList(
                    ExifToolMetadata.XMP_PLUS_LICENSOR_TELEPHONE_2 ));
        _tikaToExiftoolMetadataMap.put(
                IPTC.LICENSOR_URL,
                Arrays.asList(
                    ExifToolMetadata.XMP_PLUS_LICENSOR_URL ));
        _tikaToExiftoolMetadataMap.put(
                IPTC.MINOR_MODEL_AGE_DISCLOSURE,
                Arrays.asList(
                    ExifToolMetadata.XMP_PLUS_MINOR_MODEL_AGE_DISCLOSURE ));
        _tikaToExiftoolMetadataMap.put(
                IPTC.MODEL_RELEASE_ID,
                Arrays.asList(
                    ExifToolMetadata.XMP_PLUS_MODEL_RELEASE_ID ));
        _tikaToExiftoolMetadataMap.put(
                IPTC.MODEL_RELEASE_STATUS,
                Arrays.asList(
                    ExifToolMetadata.XMP_PLUS_MODEL_RELEASE_STATUS ));
        _tikaToExiftoolMetadataMap.put(
                IPTC.PROPERTY_RELEASE_ID,
                Arrays.asList(
                    ExifToolMetadata.XMP_PLUS_PROPERTY_RELEASE_ID ));
        _tikaToExiftoolMetadataMap.put(
                IPTC.PROPERTY_RELEASE_STATUS,
                Arrays.asList(
                    ExifToolMetadata.XMP_PLUS_PROPERTY_RELEASE_STATUS ));
        _tikaToExiftoolMetadataMap.put(
                IPTC.ARTWORK_OR_OBJECT_DETAIL_COPYRIGHT_NOTICE,
                Arrays.asList(
                    ExifToolMetadata.XMP_IPTCEXT_ARTWORK_OR_OBJECT_COPYRIGHT_NOTICE ));
        _tikaToExiftoolMetadataMap.put(
                IPTC.ARTWORK_OR_OBJECT_DETAIL_CREATOR,
                Arrays.asList(
                    ExifToolMetadata.XMP_IPTCEXT_ARTWORK_OR_OBJECT_CREATOR ));
        _tikaToExiftoolMetadataMap.put(
                IPTC.ARTWORK_OR_OBJECT_DETAIL_DATE_CREATED,
                Arrays.asList(
                    ExifToolMetadata.XMP_IPTCEXT_ARTWORK_OR_OBJECT_DATE_CREATED ));
        _tikaToExiftoolMetadataMap.put(
                IPTC.ARTWORK_OR_OBJECT_DETAIL_SOURCE,
                Arrays.asList(
                    ExifToolMetadata.XMP_IPTCEXT_ARTWORK_OR_OBJECT_SOURCE ));
        _tikaToExiftoolMetadataMap.put(
                IPTC.ARTWORK_OR_OBJECT_DETAIL_SOURCE_INVENTORY_NUMBER,
                Arrays.asList(
                    ExifToolMetadata.XMP_IPTCEXT_ARTWORK_OR_OBJECT_SOURCE_INVENTORY_NO ));
        _tikaToExiftoolMetadataMap.put(
                IPTC.ARTWORK_OR_OBJECT_DETAIL_TITLE,
                Arrays.asList(
                    ExifToolMetadata.XMP_IPTCEXT_ARTWORK_OR_OBJECT_TITLE ));
        _tikaToExiftoolMetadataMap.put(
                IPTC.LOCATION_CREATED_CITY,
                Arrays.asList(
                    ExifToolMetadata.XMP_IPTCEXT_LOCATION_CREATED_CITY ));
        _tikaToExiftoolMetadataMap.put(
                IPTC.LOCATION_CREATED_COUNTRY_CODE,
                Arrays.asList(
                    ExifToolMetadata.XMP_IPTCEXT_LOCATION_CREATED_COUNTRY_CODE ));
        _tikaToExiftoolMetadataMap.put(
                IPTC.LOCATION_CREATED_COUNTRY_NAME,
                Arrays.asList(
                    ExifToolMetadata.XMP_IPTCEXT_LOCATION_CREATED_COUNTRY_NAME ));
        _tikaToExiftoolMetadataMap.put(
                IPTC.LOCATION_CREATED_PROVINCE_OR_STATE,
                Arrays.asList(
                    ExifToolMetadata.XMP_IPTCEXT_LOCATION_CREATED_PROVINCE_STATE ));
        _tikaToExiftoolMetadataMap.put(
                IPTC.LOCATION_CREATED_SUBLOCATION,
                Arrays.asList(
                    ExifToolMetadata.XMP_IPTCEXT_LOCATION_CREATED_SUBLOCATION ));
        _tikaToExiftoolMetadataMap.put(
                IPTC.LOCATION_CREATED_WORLD_REGION,
                Arrays.asList(
                    ExifToolMetadata.XMP_IPTCEXT_LOCATION_CREATED_WORLD_REGION ));
        _tikaToExiftoolMetadataMap.put(
                IPTC.LOCATION_SHOWN_CITY,
                Arrays.asList(
                    ExifToolMetadata.XMP_IPTCEXT_LOCATION_SHOWN_CITY ));
        _tikaToExiftoolMetadataMap.put(
                IPTC.LOCATION_SHOWN_COUNTRY_CODE,
                Arrays.asList(
                    ExifToolMetadata.XMP_IPTCEXT_LOCATION_SHOWN_COUNTRY_CODE ));
        _tikaToExiftoolMetadataMap.put(
                IPTC.LOCATION_SHOWN_COUNTRY_NAME,
                Arrays.asList(
                    ExifToolMetadata.XMP_IPTCEXT_LOCATION_SHOWN_COUNTRY_NAME ));
        _tikaToExiftoolMetadataMap.put(
                IPTC.LOCATION_SHOWN_PROVINCE_OR_STATE,
                Arrays.asList(
                    ExifToolMetadata.XMP_IPTCEXT_LOCATION_SHOWN_PROVINCE_STATE ));
        _tikaToExiftoolMetadataMap.put(
                IPTC.LOCATION_SHOWN_SUBLOCATION,
                Arrays.asList(
                    ExifToolMetadata.XMP_IPTCEXT_LOCATION_SHOWN_SUBLOCATION ));
        _tikaToExiftoolMetadataMap.put(
                IPTC.LOCATION_SHOWN_WORLD_REGION,
                Arrays.asList(
                    ExifToolMetadata.XMP_IPTCEXT_LOCATION_SHOWN_WORLD_REGION ));
        _tikaToExiftoolMetadataMap.put(
                IPTC.REGISTRY_ENTRY_CREATED_ITEM_ID,
                Arrays.asList(
                    ExifToolMetadata.XMP_IPTCEXT_REGISTRY_ITEM_ID ));
        _tikaToExiftoolMetadataMap.put(
                IPTC.REGISTRY_ENTRY_CREATED_ORGANISATION_ID,
                Arrays.asList(
                    ExifToolMetadata.XMP_IPTCEXT_REGISTRY_ORGANISATION_ID ));



        _tikaToExiftoolMetadataMap.put(
                TikaCoreProperties.TITLE,
                Arrays.asList(
                    ExifToolMetadata.XMP_DC_TITLE,
                    ExifToolMetadata.IPTC_OBJECT_NAME ));
        _tikaToExiftoolMetadataMap.put(
            TikaCoreProperties.DESCRIPTION,
                Arrays.asList(
                    ExifToolMetadata.XMP_DC_DESCRIPTION,
                    ExifToolMetadata.IPTC_CAPTION ));
        _tikaToExiftoolMetadataMap.put(
                TikaCoreProperties.CREATOR,
                Arrays.asList(
                    ExifToolMetadata.XMP_DC_CREATOR,
                    ExifToolMetadata.IPTC_BY_LINE ));
        _tikaToExiftoolMetadataMap.put(
                TikaCoreProperties.KEYWORDS,
                Arrays.asList(
                    ExifToolMetadata.XMP_DC_SUBJECT,
                    ExifToolMetadata.IPTC_KEYWORDS ));

        // Create reverse mapping
        for (Property tikaMetadata : _tikaToExiftoolMetadataMap.keySet()) {
            for (Property exiftoolMetadata : _tikaToExiftoolMetadataMap.get(tikaMetadata)) {
                List<Property> mapping = _exiftoolToTikaMetadataMap.get(exiftoolMetadata);
                if (mapping == null) {
                    mapping = new ArrayList<Property>();
                }
                mapping.add(tikaMetadata);
                _exiftoolToTikaMetadataMap.put(exiftoolMetadata, mapping);
            }
        }

    }

    /**
     * Gets a map of Tika metadata names to an array of ExifTool metadata names. Most
     * useful for constructing command line arguments.
     *
     * Multiple ExifTool metadata names are provided since it is commonplace to write the
     * same general, Tika metadata value to several metadata fields.  For example,
     * a copyright notice Tika field might be written to EXIF, legacy IPTC, and
     * XMP.
     *
     * @return the map of Tika metadata names to ExifTool names
     */
    public Map<Property, List<Property>> getTikaToExiftoolMetadataMap() {
        return _tikaToExiftoolMetadataMap;
    }

    /**
     * Gets a map of ExifTool metadata names to a single Tika metadata name. Most
     * useful for parsers.
     *
     * @return the map of ExifTool metadata names to Tika names
     */
    public Map<Property, List<Property>> getExiftoolToTikaMetadataMap() {
        return _exiftoolToTikaMetadataMap;
    }

}
