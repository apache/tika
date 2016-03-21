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

import org.apache.tika.metadata.Property;

/**
 * A collection of ExifTool metadata names.
 *
 * @see <a href="http://www.sno.phy.queensu.ca/~phil/exiftool/TagNames/index.html">ExifTool Tag Names</a>
 */
public interface ExifToolMetadata {

    String NAMESPACE_URI_IPTC = "http://ns.exiftool.ca/IPTC/IPTC/1.0/";
    String NAMESPACE_URI_XMP_DC = "http://ns.exiftool.ca/XMP/XMP-dc/1.0/";
    String NAMESPACE_URI_XMP_IPTC_CORE = "http://ns.exiftool.ca/XMP/XMP-iptcCore/1.0/";
    String NAMESPACE_URI_XMP_IPTC_EXT = "http://ns.exiftool.ca/XMP/XMP-iptcExt/1.0/";
    String NAMESPACE_URI_XMP_PHOTOSHOP = "http://ns.exiftool.ca/XMP/XMP-photoshop/1.0/";
    String NAMESPACE_URI_XMP_PLUS = "http://ns.exiftool.ca/XMP/XMP-plus/1.0/";
    String NAMESPACE_URI_XMP_X = "http://ns.exiftool.ca/XMP/XMP-x/1.0/";
    String NAMESPACE_URI_XMP_XMP = "http://ns.exiftool.ca/XMP/XMP-xmp/1.0/";
    String NAMESPACE_URI_XMP_XMP_RIGHTS = "http://ns.exiftool.ca/XMP/XMP-xmpRights/1.0/";

    String PREFIX_IPTC = "IPTC";
    String PREFIX_XMP = "XMP-";
    String PREFIX_XMP_DC = "XMP-dc";
    String PREFIX_XMP_IPTC_CORE = "XMP-iptcCore";
    String PREFIX_XMP_IPTC_EXT = "XMP-iptcExt";
    String PREFIX_XMP_PHOTOSHOP = "XMP-photoshop";
    String PREFIX_XMP_PLUS = "XMP-plus";
    String PREFIX_XMP_X = "XMP-x";
    String PREFIX_XMP_XMP = "XMP-xmp";
    String PREFIX_XMP_XMP_RIGHTS = "XMP-xmpRights";

    String PREFIX_DELIMITER = ":";

    Property IPTC_BY_LINE = Property.internalText(
            PREFIX_IPTC + PREFIX_DELIMITER + "By-line");
    Property IPTC_BY_LINE_TITLE = Property.internalText(
            PREFIX_IPTC + PREFIX_DELIMITER + "By-lineTitle");
    Property IPTC_CAPTION = Property.internalText(
            PREFIX_IPTC + PREFIX_DELIMITER + "Caption-Abstract");
    Property IPTC_CATEGORY = Property.internalText(
            PREFIX_IPTC + PREFIX_DELIMITER + "Category");
    Property IPTC_CITY = Property.internalText(
            PREFIX_IPTC + PREFIX_DELIMITER + "City");
    Property IPTC_COPYRIGHT_NOTICE = Property.internalText(
            PREFIX_IPTC + PREFIX_DELIMITER + "CopyrightNotice");
    Property IPTC_COUNTRY_PRIMARY_LOCATION_CODE = Property.internalText(
            PREFIX_IPTC + PREFIX_DELIMITER + "Country-PrimaryLocationCode");
    Property IPTC_COUNTRY_PRIMARY_LOCATION_NAME = Property.internalText(
            PREFIX_IPTC + PREFIX_DELIMITER + "Country-PrimaryLocationName");
    Property IPTC_CREDIT = Property.internalText(
            PREFIX_IPTC + PREFIX_DELIMITER + "Credit");
    Property IPTC_DATE_CREATED = Property.internalDate(
            PREFIX_IPTC + PREFIX_DELIMITER + "DateCreated");
    Property IPTC_HEADLINE = Property.internalText(
            PREFIX_IPTC + PREFIX_DELIMITER + "Headline");
    Property IPTC_KEYWORDS = Property.internalTextBag(
            PREFIX_IPTC + PREFIX_DELIMITER + "Keywords");
    Property IPTC_OBJECT_NAME = Property.internalText(
            PREFIX_IPTC + PREFIX_DELIMITER + "ObjectName");
    Property IPTC_OBJECT_ATTRIBUTE_REFERENCE = Property.internalText(
            PREFIX_IPTC + PREFIX_DELIMITER + "ObjectAttributeReference");
    Property IPTC_ORIGINAL_TRANSMISSION_REFERENCE = Property.internalText(
            PREFIX_IPTC + PREFIX_DELIMITER + "OriginalTransmissionReference");
    Property IPTC_PROVINCE_STATE = Property.internalText(
            PREFIX_IPTC + PREFIX_DELIMITER + "Province-State");
    Property IPTC_SOURCE = Property.internalText(
            PREFIX_IPTC + PREFIX_DELIMITER + "Source");
    Property IPTC_SPECIAL_INSTRUCTIONS = Property.internalText(
            PREFIX_IPTC + PREFIX_DELIMITER + "SpecialInstructions");
    Property IPTC_SUBLOCATION = Property.internalText(
            PREFIX_IPTC + PREFIX_DELIMITER + "Sub-location");
    Property IPTC_SUPPLEMENTAL_CATEGORIES = Property.internalTextBag(
            PREFIX_IPTC + PREFIX_DELIMITER + "SupplementalCategories");
    Property IPTC_URGENCY = Property.internalText(
            PREFIX_IPTC + PREFIX_DELIMITER + "Urgency");
    Property IPTC_WRITER_EDITOR = Property.internalText(
            PREFIX_IPTC + PREFIX_DELIMITER + "Writer-Editor");

    Property XMP_DC_CONTRIBUTOR = Property.internalText(
            PREFIX_XMP_DC + PREFIX_DELIMITER + "Contributor");
    Property XMP_DC_COVERAGE = Property.internalText(
            PREFIX_XMP_DC + PREFIX_DELIMITER + "Coverage");
    Property XMP_DC_CREATOR = Property.internalText(
            PREFIX_XMP_DC + PREFIX_DELIMITER + "Creator");
    Property XMP_DC_DATE = Property.internalDate(
            PREFIX_XMP_DC + PREFIX_DELIMITER + "Date");
    Property XMP_DC_DESCRIPTION = Property.internalText(
            PREFIX_XMP_DC + PREFIX_DELIMITER + "Description");
    Property XMP_DC_FORMAT = Property.internalText(
            PREFIX_XMP_DC + PREFIX_DELIMITER + "Format");
    Property XMP_DC_IDENTIFIER = Property.internalText(
            PREFIX_XMP_DC + PREFIX_DELIMITER + "Identifier");
    Property XMP_DC_LANGUAGE = Property.internalText(
            PREFIX_XMP_DC + PREFIX_DELIMITER + "Language");
    Property XMP_DC_PUBLISHER = Property.internalText(
            PREFIX_XMP_DC + PREFIX_DELIMITER + "Publisher");
    Property XMP_DC_RELATION = Property.internalText(
            PREFIX_XMP_DC + PREFIX_DELIMITER + "Relation");
    Property XMP_DC_RIGHTS = Property.internalText(
            PREFIX_XMP_DC + PREFIX_DELIMITER + "Rights");
    Property XMP_DC_SOURCE = Property.internalText(
            PREFIX_XMP_DC + PREFIX_DELIMITER + "Source");
    Property XMP_DC_SUBJECT = Property.internalTextBag(
            PREFIX_XMP_DC + PREFIX_DELIMITER + "Subject");
    Property XMP_DC_TITLE = Property.internalText(
            PREFIX_XMP_DC + PREFIX_DELIMITER + "Title");
    Property XMP_DC_TYPE = Property.internalText(
            PREFIX_XMP_DC + PREFIX_DELIMITER + "Type");

    Property XMP_IPTCCORE_CREATOR_CONTACT_INFO_CITY = Property.internalText(
            PREFIX_XMP_IPTC_CORE + PREFIX_DELIMITER + "CreatorCity");
    Property XMP_IPTCCORE_CREATOR_CONTACT_INFO_COUNTRY = Property.internalText(
            PREFIX_XMP_IPTC_CORE + PREFIX_DELIMITER + "CreatorCountry");
    Property XMP_IPTCCORE_CREATOR_CONTACT_INFO_ADDRESS = Property.internalText(
            PREFIX_XMP_IPTC_CORE + PREFIX_DELIMITER + "CreatorAddress");
    Property XMP_IPTCCORE_CREATOR_CONTACT_INFO_POSTAL_CODE = Property.internalText(
            PREFIX_XMP_IPTC_CORE + PREFIX_DELIMITER + "CreatorPostalCode");
    Property XMP_IPTCCORE_CREATOR_CONTACT_INFO_REGION = Property.internalText(
            PREFIX_XMP_IPTC_CORE + PREFIX_DELIMITER + "CreatorRegion");
    Property XMP_IPTCCORE_CREATOR_CONTACT_INFO_EMAIL_WORK = Property.internalTextBag(
            PREFIX_XMP_IPTC_CORE + PREFIX_DELIMITER + "CreatorWorkEmail");
    Property XMP_IPTCCORE_CREATOR_CONTACT_INFO_TELEPHONE_WORK = Property.internalTextBag(
            PREFIX_XMP_IPTC_CORE + PREFIX_DELIMITER + "CreatorWorkTelephone");
    Property XMP_IPTCCORE_CREATOR_CONTACT_INFO_URL_WORK = Property.internalTextBag(
            PREFIX_XMP_IPTC_CORE + PREFIX_DELIMITER + "CreatorWorkURL");
    Property XMP_IPTCCORE_COUNTRY_CODE = Property.internalText(
            PREFIX_XMP_IPTC_CORE + PREFIX_DELIMITER + "CountryCode");
    Property XMP_IPTCCORE_INTELLECTUAL_GENRE = Property.internalText(
            PREFIX_XMP_IPTC_CORE + PREFIX_DELIMITER + "IntellectualGenre");
    Property XMP_IPTCCORE_LOCATION = Property.internalText(
            PREFIX_XMP_IPTC_CORE + PREFIX_DELIMITER + "Location");
    Property XMP_IPTCCORE_SCENE = Property.internalTextBag(
            PREFIX_XMP_IPTC_CORE + PREFIX_DELIMITER + "Scene");
    Property XMP_IPTCCORE_SUBJECT_CODE = Property.internalTextBag(
            PREFIX_XMP_IPTC_CORE + PREFIX_DELIMITER + "SubjectCode");

    Property XMP_IPTCEXT_ADDITIONAL_MODEL_INFORMATION = Property.internalText(
            PREFIX_XMP_IPTC_EXT + PREFIX_DELIMITER + "AdditionalModelInformation");
    Property XMP_IPTCEXT_ARTWORK_OR_OBJECT_COPYRIGHT_NOTICE = Property.internalTextBag(
            PREFIX_XMP_IPTC_EXT + PREFIX_DELIMITER + "ArtworkCopyrightNotice");
    Property XMP_IPTCEXT_ARTWORK_OR_OBJECT_CREATOR = Property.internalTextBag(
            PREFIX_XMP_IPTC_EXT + PREFIX_DELIMITER + "ArtworkCreator");
    Property XMP_IPTCEXT_ARTWORK_OR_OBJECT_DATE_CREATED = Property.internalTextBag(
            PREFIX_XMP_IPTC_EXT + PREFIX_DELIMITER + "ArtworkDateCreated");
    Property XMP_IPTCEXT_ARTWORK_OR_OBJECT_SOURCE = Property.internalTextBag(
            PREFIX_XMP_IPTC_EXT + PREFIX_DELIMITER + "ArtworkSource");
    Property XMP_IPTCEXT_ARTWORK_OR_OBJECT_SOURCE_INVENTORY_NO = Property.internalTextBag(
            PREFIX_XMP_IPTC_EXT + PREFIX_DELIMITER + "ArtworkSourceInventoryNo");
    Property XMP_IPTCEXT_ARTWORK_OR_OBJECT_TITLE = Property.internalTextBag(
            PREFIX_XMP_IPTC_EXT + PREFIX_DELIMITER + "ArtworkTitle");
    Property XMP_IPTCEXT_CONTROLLED_VOCABULAR_TERM = Property.internalTextBag(
            PREFIX_XMP_IPTC_EXT + PREFIX_DELIMITER + "ControlledVocabularyTerm");
    Property XMP_IPTCEXT_DIGITAL_IMAGE_GUID = Property.internalText(
            PREFIX_XMP_IPTC_EXT + PREFIX_DELIMITER + "DigitalImageGUID");
    Property XMP_IPTCEXT_DIGITAL_SOURCE_TYPE = Property.internalText(
            PREFIX_XMP_IPTC_EXT + PREFIX_DELIMITER + "DigitalSourceType");
    Property XMP_IPTCEXT_EVENT = Property.internalText(
            PREFIX_XMP_IPTC_EXT + PREFIX_DELIMITER + "Event");
    Property XMP_IPTCEXT_IPTC_LAST_EDITED = Property.internalDate(
            PREFIX_XMP_IPTC_EXT + PREFIX_DELIMITER + "IptcLastEdited");
    Property XMP_IPTCEXT_LOCATION_CREATED_CITY = Property.internalText(
            PREFIX_XMP_IPTC_EXT + PREFIX_DELIMITER + "LocationCreatedCity");
    Property XMP_IPTCEXT_LOCATION_CREATED_COUNTRY_CODE = Property.internalText(
            PREFIX_XMP_IPTC_EXT + PREFIX_DELIMITER + "LocationCreatedCountryCode");
    Property XMP_IPTCEXT_LOCATION_CREATED_COUNTRY_NAME = Property.internalText(
            PREFIX_XMP_IPTC_EXT + PREFIX_DELIMITER + "LocationCreatedCountryName");
    Property XMP_IPTCEXT_LOCATION_CREATED_PROVINCE_STATE = Property.internalText(
            PREFIX_XMP_IPTC_EXT + PREFIX_DELIMITER + "LocationCreatedProvinceState");
    Property XMP_IPTCEXT_LOCATION_CREATED_SUBLOCATION = Property.internalText(
            PREFIX_XMP_IPTC_EXT + PREFIX_DELIMITER + "LocationCreatedSublocation");
    Property XMP_IPTCEXT_LOCATION_CREATED_WORLD_REGION = Property.internalText(
            PREFIX_XMP_IPTC_EXT + PREFIX_DELIMITER + "LocationCreatedWorldRegion");
    Property XMP_IPTCEXT_LOCATION_SHOWN_CITY = Property.internalTextBag(
            PREFIX_XMP_IPTC_EXT + PREFIX_DELIMITER + "LocationShownCity");
    Property XMP_IPTCEXT_LOCATION_SHOWN_COUNTRY_CODE = Property.internalTextBag(
            PREFIX_XMP_IPTC_EXT + PREFIX_DELIMITER + "LocationShownCountryCode");
    Property XMP_IPTCEXT_LOCATION_SHOWN_COUNTRY_NAME = Property.internalTextBag(
            PREFIX_XMP_IPTC_EXT + PREFIX_DELIMITER + "LocationShownCountryName");
    Property XMP_IPTCEXT_LOCATION_SHOWN_PROVINCE_STATE = Property.internalTextBag(
            PREFIX_XMP_IPTC_EXT + PREFIX_DELIMITER + "LocationShownProvinceState");
    Property XMP_IPTCEXT_LOCATION_SHOWN_SUBLOCATION = Property.internalTextBag(
            PREFIX_XMP_IPTC_EXT + PREFIX_DELIMITER + "LocationShownSublocation");
    Property XMP_IPTCEXT_LOCATION_SHOWN_WORLD_REGION = Property.internalTextBag(
            PREFIX_XMP_IPTC_EXT + PREFIX_DELIMITER + "LocationShownWorldRegion");
    Property XMP_IPTCEXT_MAX_AVAIL_HEIGHT = Property.internalInteger(
            PREFIX_XMP_IPTC_EXT + PREFIX_DELIMITER + "MaxAvailHeight");
    Property XMP_IPTCEXT_MAX_AVAIL_WIDTH = Property.internalInteger(
            PREFIX_XMP_IPTC_EXT + PREFIX_DELIMITER + "MaxAvailWidth");
    Property XMP_IPTCEXT_MODEL_AGE = Property.internalTextBag(
            PREFIX_XMP_IPTC_EXT + PREFIX_DELIMITER + "ModelAge");
    Property XMP_IPTCEXT_ORGANISATION_IN_IMAGE_CODE = Property.internalTextBag(
            PREFIX_XMP_IPTC_EXT + PREFIX_DELIMITER + "OrganisationInImageCode");
    Property XMP_IPTCEXT_ORGANISATION_IN_IMAGE_NAME = Property.internalTextBag(
            PREFIX_XMP_IPTC_EXT + PREFIX_DELIMITER + "OrganisationInImageName");
    Property XMP_IPTCEXT_PERSON_IN_IMAGE = Property.internalTextBag(
            PREFIX_XMP_IPTC_EXT + PREFIX_DELIMITER + "PersonInImage");
    Property XMP_IPTCEXT_REGISTRY_ITEM_ID = Property.internalTextBag(
            PREFIX_XMP_IPTC_EXT + PREFIX_DELIMITER + "RegistryItemID");
    Property XMP_IPTCEXT_REGISTRY_ORGANISATION_ID = Property.internalTextBag(
            PREFIX_XMP_IPTC_EXT + PREFIX_DELIMITER + "RegistryOrganisationID");

    Property XMP_PHOTOSHOP_AUTHORS_POSITION = Property.internalText(
            PREFIX_XMP_PHOTOSHOP + PREFIX_DELIMITER + "AuthorsPosition");
    Property XMP_PHOTOSHOP_CAPTION_WRITER = Property.internalText(
            PREFIX_XMP_PHOTOSHOP + PREFIX_DELIMITER + "CaptionWriter");
    Property XMP_PHOTOSHOP_CATEGORY = Property.internalText(
            PREFIX_XMP_PHOTOSHOP + PREFIX_DELIMITER + "Category");
    Property XMP_PHOTOSHOP_CITY = Property.internalText(
            PREFIX_XMP_PHOTOSHOP + PREFIX_DELIMITER + "City");
    Property XMP_PHOTOSHOP_COUNTRY = Property.internalText(
            PREFIX_XMP_PHOTOSHOP + PREFIX_DELIMITER + "Country");
    Property XMP_PHOTOSHOP_CREDIT = Property.internalText(
            PREFIX_XMP_PHOTOSHOP + PREFIX_DELIMITER + "Credit");
    Property XMP_PHOTOSHOP_DATE_CREATED = Property.internalDate(
            PREFIX_XMP_PHOTOSHOP + PREFIX_DELIMITER + "DateCreated");
    Property XMP_PHOTOSHOP_HEADLINE = Property.internalText(
            PREFIX_XMP_PHOTOSHOP + PREFIX_DELIMITER + "Headline");
    Property XMP_PHOTOSHOP_HISTORY = Property.internalText(
            PREFIX_XMP_PHOTOSHOP + PREFIX_DELIMITER + "History");
    Property XMP_PHOTOSHOP_INSTRUCTIONS = Property.internalText(
            PREFIX_XMP_PHOTOSHOP + PREFIX_DELIMITER + "Instructions");
    Property XMP_PHOTOSHOP_LEGACY_IPTC_DIGEST = Property.internalText(
            PREFIX_XMP_PHOTOSHOP + PREFIX_DELIMITER + "LegacyIPTCDigest");
    Property XMP_PHOTOSHOP_SIDECAR_FOR_EXTENSION = Property.internalText(
            PREFIX_XMP_PHOTOSHOP + PREFIX_DELIMITER + "SidecarForExtension");
    Property XMP_PHOTOSHOP_SOURCE = Property.internalText(
            PREFIX_XMP_PHOTOSHOP + PREFIX_DELIMITER + "Source");
    Property XMP_PHOTOSHOP_STATE = Property.internalText(
            PREFIX_XMP_PHOTOSHOP + PREFIX_DELIMITER + "State");
    Property XMP_PHOTOSHOP_SUPPLEMENTAL_CATEGORIES = Property.internalTextBag(
            PREFIX_XMP_PHOTOSHOP + PREFIX_DELIMITER + "SupplementalCategories");
    Property XMP_PHOTOSHOP_TRANSMISSION_REFERENCE = Property.internalText(
            PREFIX_XMP_PHOTOSHOP + PREFIX_DELIMITER + "TransmissionReference");
    Property XMP_PHOTOSHOP_URGENCY = Property.internalText(
            PREFIX_XMP_PHOTOSHOP + PREFIX_DELIMITER + "Urgency");

    Property XMP_PLUS_IMAGE_SUPPLIER_IMAGE_ID = Property.internalText(
            PREFIX_XMP_PLUS + PREFIX_DELIMITER + "ImageSupplierImageID");
    Property XMP_PLUS_IMAGE_SUPPLIER_ID = Property.internalText(
            PREFIX_XMP_PLUS + PREFIX_DELIMITER + "ImageSupplierID");
    Property XMP_PLUS_IMAGE_SUPPLIER_NAME = Property.internalText(
            PREFIX_XMP_PLUS + PREFIX_DELIMITER + "ImageSupplierName");
    Property XMP_PLUS_VERSION = Property.internalText(
            PREFIX_XMP_PLUS + PREFIX_DELIMITER + "PLUSVersion");
    Property XMP_PLUS_COPYRIGHT_OWNER_ID = Property.internalTextBag(
            PREFIX_XMP_PLUS + PREFIX_DELIMITER + "CopyrightOwnerID");
    Property XMP_PLUS_COPYRIGHT_OWNER_NAME = Property.internalTextBag(
            PREFIX_XMP_PLUS + PREFIX_DELIMITER + "CopyrightOwnerName");
    Property XMP_PLUS_IMAGE_CREATOR_ID = Property.internalTextBag(
            PREFIX_XMP_PLUS + PREFIX_DELIMITER + "ImageCreatorID");
    Property XMP_PLUS_IMAGE_CREATOR_NAME = Property.internalTextBag(
            PREFIX_XMP_PLUS + PREFIX_DELIMITER + "ImageCreatorName");
    Property XMP_PLUS_LICENSOR_ID = Property.internalTextBag(
            PREFIX_XMP_PLUS + PREFIX_DELIMITER + "LicensorID");
    Property XMP_PLUS_LICENSOR_NAME = Property.internalTextBag(
            PREFIX_XMP_PLUS + PREFIX_DELIMITER + "LicensorName");
    Property XMP_PLUS_LICENSOR_CITY = Property.internalTextBag(
            PREFIX_XMP_PLUS + PREFIX_DELIMITER + "LicensorCity");
    Property XMP_PLUS_LICENSOR_COUNTRY = Property.internalTextBag(
            PREFIX_XMP_PLUS + PREFIX_DELIMITER + "LicensorCountry");
    Property XMP_PLUS_LICENSOR_EMAIL = Property.internalTextBag(
            PREFIX_XMP_PLUS + PREFIX_DELIMITER + "LicensorEmail");
    Property XMP_PLUS_LICENSOR_EXTENDED_ADDRESS = Property.internalTextBag(
            PREFIX_XMP_PLUS + PREFIX_DELIMITER + "LicensorExtendedAddress");
    Property XMP_PLUS_LICENSOR_POSTAL_CODE = Property.internalTextBag(
            PREFIX_XMP_PLUS + PREFIX_DELIMITER + "LicensorPostalCode");
    Property XMP_PLUS_LICENSOR_REGION = Property.internalTextBag(
            PREFIX_XMP_PLUS + PREFIX_DELIMITER + "LicensorRegion");
    Property XMP_PLUS_LICENSOR_STREET_ADDRESS = Property.internalTextBag(
            PREFIX_XMP_PLUS + PREFIX_DELIMITER + "LicensorStreetAddress");
    Property XMP_PLUS_LICENSOR_TELEPHONE_1 = Property.internalTextBag(
            PREFIX_XMP_PLUS + PREFIX_DELIMITER + "LicensorTelephone1");
    Property XMP_PLUS_LICENSOR_TELEPHONE_2 = Property.internalTextBag(
            PREFIX_XMP_PLUS + PREFIX_DELIMITER + "LicensorTelephone2");
    Property XMP_PLUS_LICENSOR_URL = Property.internalTextBag(
            PREFIX_XMP_PLUS + PREFIX_DELIMITER + "LicensorURL");
    Property XMP_PLUS_MINOR_MODEL_AGE_DISCLOSURE = Property.internalText(
            PREFIX_XMP_PLUS + PREFIX_DELIMITER + "MinorModelAgeDisclosure");
    Property XMP_PLUS_MODEL_RELEASE_ID = Property.internalTextBag(
            PREFIX_XMP_PLUS + PREFIX_DELIMITER + "ModelReleaseID");
    Property XMP_PLUS_MODEL_RELEASE_STATUS = Property.internalText(
            PREFIX_XMP_PLUS + PREFIX_DELIMITER + "ModelReleaseStatus");
    Property XMP_PLUS_PROPERTY_RELEASE_ID = Property.internalTextBag(
            PREFIX_XMP_PLUS + PREFIX_DELIMITER + "PropertyReleaseID");
    Property XMP_PLUS_PROPERTY_RELEASE_STATUS = Property.internalText(
            PREFIX_XMP_PLUS + PREFIX_DELIMITER + "PropertyReleaseStatus");

    Property XMP_XMP_BASE_URL = Property.internalText(
            PREFIX_XMP_XMP + PREFIX_DELIMITER + "BaseURL");
    Property XMP_XMP_CREATE_DATE = Property.internalDate(
            PREFIX_XMP_XMP + PREFIX_DELIMITER + "CreateDate");
    Property XMP_XMP_CREATOR_TOOL = Property.internalText(
            PREFIX_XMP_XMP + PREFIX_DELIMITER + "CreatorTool");
    Property XMP_XMP_DESCRIPTION = Property.internalText(
            PREFIX_XMP_XMP + PREFIX_DELIMITER + "Description");
    Property XMP_XMP_FORMAT = Property.internalText(
            PREFIX_XMP_XMP + PREFIX_DELIMITER + "Format");
    Property XMP_XMP_IDENTIFIER = Property.internalText(
            PREFIX_XMP_XMP + PREFIX_DELIMITER + "Identifier");
    Property XMP_XMP_KEYWORDS = Property.internalText(
            PREFIX_XMP_XMP + PREFIX_DELIMITER + "Keywords");
    Property XMP_XMP_LABEL = Property.internalText(
            PREFIX_XMP_XMP + PREFIX_DELIMITER + "Label");
    Property XMP_XMP_METADATA_DATE = Property.internalDate(
            PREFIX_XMP_XMP + PREFIX_DELIMITER + "MetadataDate");
    Property XMP_XMP_MODIFY_DATE = Property.internalDate(
            PREFIX_XMP_XMP + PREFIX_DELIMITER + "ModifyDate");
    Property XMP_XMP_NICKNAME = Property.internalText(
            PREFIX_XMP_XMP + PREFIX_DELIMITER + "Nickname");
    Property XMP_XMP_RATING = Property.internalReal(
            PREFIX_XMP_XMP + PREFIX_DELIMITER + "Rating");
    Property XMP_XMP_TITLE = Property.internalText(
            PREFIX_XMP_XMP + PREFIX_DELIMITER + "Title");

    Property XMP_XMPRIGHTS_CERTIFICATE = Property.internalText(
            PREFIX_XMP_XMP_RIGHTS + PREFIX_DELIMITER + "Certificate");
    Property XMP_XMPRIGHTS_MARKED = Property.internalBoolean(
            PREFIX_XMP_XMP_RIGHTS + PREFIX_DELIMITER + "Marked");
    Property XMP_XMPRIGHTS_OWNER = Property.internalText(
            PREFIX_XMP_XMP_RIGHTS + PREFIX_DELIMITER + "Owner");
    Property XMP_XMPRIGHTS_USAGE_TERMS = Property.internalText(
            PREFIX_XMP_XMP_RIGHTS + PREFIX_DELIMITER + "UsageTerms");
    Property XMP_XMPRIGHTS_WEB_STATEMENT = Property.internalText(
            PREFIX_XMP_XMP_RIGHTS + PREFIX_DELIMITER + "WebStatement");

    Property[] PROPERTY_GROUP_IPTC = new Property[] {
        IPTC_BY_LINE,
        IPTC_BY_LINE_TITLE,
        IPTC_CAPTION,
        IPTC_CITY,
        IPTC_COPYRIGHT_NOTICE,
        IPTC_COUNTRY_PRIMARY_LOCATION_NAME,
        IPTC_CREDIT,
        IPTC_DATE_CREATED,
        IPTC_HEADLINE,
        IPTC_KEYWORDS,
        IPTC_OBJECT_NAME,
        IPTC_ORIGINAL_TRANSMISSION_REFERENCE,
        IPTC_PROVINCE_STATE,
        IPTC_SOURCE,
        IPTC_SPECIAL_INSTRUCTIONS,
        IPTC_SUPPLEMENTAL_CATEGORIES,
        IPTC_WRITER_EDITOR
    };

    Property[] PROPERTY_GROUP_XMP_DC = new Property[] {
        XMP_DC_CONTRIBUTOR,
        XMP_DC_COVERAGE,
        XMP_DC_CREATOR,
        XMP_DC_DATE,
        XMP_DC_DESCRIPTION,
        XMP_DC_FORMAT,
        XMP_DC_IDENTIFIER,
        XMP_DC_LANGUAGE,
        XMP_DC_PUBLISHER,
        XMP_DC_RELATION,
        XMP_DC_RIGHTS,
        XMP_DC_SOURCE,
        XMP_DC_SUBJECT,
        XMP_DC_TITLE,
        XMP_DC_TYPE
    };

    Property[] PROPERTY_GROUP_XMP_IPTC_CORE = new Property[] {
        XMP_IPTCCORE_CREATOR_CONTACT_INFO_CITY,
        XMP_IPTCCORE_CREATOR_CONTACT_INFO_COUNTRY,
        XMP_IPTCCORE_CREATOR_CONTACT_INFO_ADDRESS,
        XMP_IPTCCORE_CREATOR_CONTACT_INFO_POSTAL_CODE,
        XMP_IPTCCORE_CREATOR_CONTACT_INFO_REGION,
        XMP_IPTCCORE_CREATOR_CONTACT_INFO_EMAIL_WORK,
        XMP_IPTCCORE_CREATOR_CONTACT_INFO_TELEPHONE_WORK,
        XMP_IPTCCORE_CREATOR_CONTACT_INFO_URL_WORK,
        XMP_IPTCCORE_COUNTRY_CODE,
        XMP_IPTCCORE_INTELLECTUAL_GENRE,
        XMP_IPTCCORE_LOCATION,
        XMP_IPTCCORE_SCENE,
        XMP_IPTCCORE_SUBJECT_CODE
    };

    Property[] PROPERTY_GROUP_XMP_IPTC_EXT = new Property[] {
        XMP_IPTCEXT_ADDITIONAL_MODEL_INFORMATION,
        XMP_IPTCEXT_ARTWORK_OR_OBJECT_COPYRIGHT_NOTICE,
        XMP_IPTCEXT_ARTWORK_OR_OBJECT_CREATOR,
        XMP_IPTCEXT_ARTWORK_OR_OBJECT_DATE_CREATED,
        XMP_IPTCEXT_ARTWORK_OR_OBJECT_SOURCE,
        XMP_IPTCEXT_ARTWORK_OR_OBJECT_SOURCE_INVENTORY_NO,
        XMP_IPTCEXT_ARTWORK_OR_OBJECT_TITLE,
        XMP_IPTCEXT_CONTROLLED_VOCABULAR_TERM,
        XMP_IPTCEXT_DIGITAL_IMAGE_GUID,
        XMP_IPTCEXT_DIGITAL_SOURCE_TYPE,
        XMP_IPTCEXT_EVENT,
        XMP_IPTCEXT_IPTC_LAST_EDITED,
        XMP_IPTCEXT_LOCATION_CREATED_CITY,
        XMP_IPTCEXT_LOCATION_CREATED_COUNTRY_CODE,
        XMP_IPTCEXT_LOCATION_CREATED_COUNTRY_NAME,
        XMP_IPTCEXT_LOCATION_CREATED_PROVINCE_STATE,
        XMP_IPTCEXT_LOCATION_CREATED_SUBLOCATION,
        XMP_IPTCEXT_LOCATION_CREATED_WORLD_REGION,
        XMP_IPTCEXT_LOCATION_SHOWN_CITY,
        XMP_IPTCEXT_LOCATION_SHOWN_COUNTRY_CODE,
        XMP_IPTCEXT_LOCATION_SHOWN_COUNTRY_NAME,
        XMP_IPTCEXT_LOCATION_SHOWN_PROVINCE_STATE,
        XMP_IPTCEXT_LOCATION_SHOWN_SUBLOCATION,
        XMP_IPTCEXT_LOCATION_SHOWN_WORLD_REGION,
        XMP_IPTCEXT_MAX_AVAIL_HEIGHT,
        XMP_IPTCEXT_MAX_AVAIL_WIDTH,
        XMP_IPTCEXT_MODEL_AGE,
        XMP_IPTCEXT_ORGANISATION_IN_IMAGE_CODE,
        XMP_IPTCEXT_ORGANISATION_IN_IMAGE_NAME,
        XMP_IPTCEXT_PERSON_IN_IMAGE,
        XMP_IPTCEXT_REGISTRY_ITEM_ID,
        XMP_IPTCEXT_REGISTRY_ORGANISATION_ID
    };

    Property[] PROPERTY_GROUP_XMP_PHOTOSHOP = new Property[] {
        XMP_PHOTOSHOP_AUTHORS_POSITION,
        XMP_PHOTOSHOP_CAPTION_WRITER,
        XMP_PHOTOSHOP_CATEGORY,
        XMP_PHOTOSHOP_CITY,
        XMP_PHOTOSHOP_COUNTRY,
        XMP_PHOTOSHOP_CREDIT,
        XMP_PHOTOSHOP_DATE_CREATED,
        XMP_PHOTOSHOP_HEADLINE,
        XMP_PHOTOSHOP_HISTORY,
        XMP_PHOTOSHOP_INSTRUCTIONS,
        XMP_PHOTOSHOP_LEGACY_IPTC_DIGEST,
        XMP_PHOTOSHOP_SIDECAR_FOR_EXTENSION,
        XMP_PHOTOSHOP_SOURCE,
        XMP_PHOTOSHOP_STATE,
        XMP_PHOTOSHOP_SUPPLEMENTAL_CATEGORIES,
        XMP_PHOTOSHOP_TRANSMISSION_REFERENCE,
        XMP_PHOTOSHOP_URGENCY
    };

    Property[] PROPERTY_GROUP_XMP_PLUS = new Property[] {
        XMP_PLUS_COPYRIGHT_OWNER_ID,
        XMP_PLUS_COPYRIGHT_OWNER_NAME,
        XMP_PLUS_IMAGE_CREATOR_ID,
        XMP_PLUS_IMAGE_CREATOR_NAME,
        XMP_PLUS_IMAGE_SUPPLIER_ID,
        XMP_PLUS_IMAGE_SUPPLIER_IMAGE_ID,
        XMP_PLUS_IMAGE_SUPPLIER_NAME,
        XMP_PLUS_LICENSOR_CITY,
        XMP_PLUS_LICENSOR_COUNTRY,
        XMP_PLUS_LICENSOR_EMAIL,
        XMP_PLUS_LICENSOR_EXTENDED_ADDRESS,
        XMP_PLUS_LICENSOR_ID,
        XMP_PLUS_LICENSOR_NAME,
        XMP_PLUS_LICENSOR_POSTAL_CODE,
        XMP_PLUS_LICENSOR_REGION,
        XMP_PLUS_LICENSOR_STREET_ADDRESS,
        XMP_PLUS_LICENSOR_TELEPHONE_1,
        XMP_PLUS_LICENSOR_TELEPHONE_2,
        XMP_PLUS_LICENSOR_URL,
        XMP_PLUS_MINOR_MODEL_AGE_DISCLOSURE,
        XMP_PLUS_MODEL_RELEASE_ID,
        XMP_PLUS_MODEL_RELEASE_STATUS,
        XMP_PLUS_PROPERTY_RELEASE_ID,
        XMP_PLUS_PROPERTY_RELEASE_STATUS,
        XMP_PLUS_VERSION
    };

    Property[] PROPERTY_GROUP_XMP_XMP = new Property[] {
        XMP_XMP_BASE_URL,
        XMP_XMP_CREATE_DATE,
        XMP_XMP_CREATOR_TOOL,
        XMP_XMP_DESCRIPTION,
        XMP_XMP_FORMAT,
        XMP_XMP_IDENTIFIER,
        XMP_XMP_KEYWORDS,
        XMP_XMP_LABEL,
        XMP_XMP_METADATA_DATE,
        XMP_XMP_MODIFY_DATE,
        XMP_XMP_NICKNAME,
        XMP_XMP_RATING,
        XMP_XMP_TITLE
    };

    Property[] PROPERTY_GROUP_XMP_XMPRIGHTS = new Property[] {
        XMP_XMPRIGHTS_CERTIFICATE,
        XMP_XMPRIGHTS_MARKED,
        XMP_XMPRIGHTS_OWNER,
        XMP_XMPRIGHTS_USAGE_TERMS,
        XMP_XMPRIGHTS_WEB_STATEMENT
    };

    Property EXIF_COPYRIGHT = Property.internalText("EXIF:Copyright");

}

