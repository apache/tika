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
 *
 * IPTC Metadata Descriptions taken from the IPTC Photo Metadata (July 2010) 
 * standard. These parts Copyright 2010 International Press Telecommunications 
 * Council.
 */
package org.apache.tika.metadata;

/**
 * IPTC photo metadata schema.
 * 
 * A collection of
 * {@link Property property definition} constants for the photo metadata
 * properties defined in the IPTC standard.
 * 
 * @since Apache Tika 1.1
 * @see <a href="http://www.iptc.org/std/photometadata/specification/IPTC-PhotoMetadata-201007_1.pdf">IPTC Photo Metadata</a>
 */
public interface IPTC {

   String NAMESPACE_URI_IPTC_CORE = "http://iptc.org/std/Iptc4xmpCore/1.0/xmlns/";
   String NAMESPACE_URI_IPTC_EXT = "http://iptc.org/std/Iptc4xmpExt/2008-02-29/";
   String NAMESPACE_URI_PLUS = "http://ns.useplus.org/ldf/xmp/1.0/";

   String PREFIX_IPTC_CORE = "Iptc4xmpCore";
   String PREFIX_IPTC_EXT = "Iptc4xmpExt";
   String PREFIX_PLUS = "plus";

   /**
    * Name of the city the content is focussing on -- either the place shown
    * in visual media or referenced by text or audio media. This element is at
    * the third level of a top-down geographical hierarchy.
    * <p>
    * This is a detail of a location with blurred semantics as it does not
    * clearly indicate whether it is the location in the image or the location
    * the photo was taken - which can be different. Two more concise properties
    * are available in IPTC Extension with Location Created and Location Shown
    * in the Image.
    * <p>
    * Maps to this IIM property: 2:90 City
    * 
    * @see Photoshop#CITY
    */
   Property CITY = Photoshop.CITY;

   /**
    * Full name of the country the content is focussing on -- either the
    * country shown in visual media or referenced in text or audio media. This
    * element is at the top/first level of a top- down geographical hierarchy.
    * The full name should be expressed as a verbal name and not as a code, a
    * code should go to the element "CountryCode"
    * <p>
    * This is a detail of a location with blurred semantics as it does not
    * clearly indicate whether it is the location in the image or the location
    * the photo was taken - which can be different. Two more concise properties
    * are available in IPTC Extension with Location Created and Location Shown
    * in the Image.
    * <p>
    * Maps to this IIM property: 2:101 Country/Primary Location Name
    * 
    * @see Photoshop#COUNTRY
    */
   Property COUNTRY = Photoshop.COUNTRY;

   /**
    * Code of the country the content is focussing on -- either the country
    * shown in visual media or referenced in text or audio media. This element
    * is at the top/first level of a top-down geographical hierarchy. The code
    * should be taken from ISO 3166 two or three letter code. The full name of
    * a country should go to the "Country" element.
    * <p>
    * This is a detail of a location with blurred semantics as it does not
    * clearly indicate whether it is the location in the image or the location
    * the photo was taken - which can be different. Two more concise properties
    * are available in IPTC Extension with Location Created and Location Shown
    * in the Image.
    * <p>
    * Maps to this IIM property: 2:100 Country/Primary Location Code
    */
   Property COUNTRY_CODE = Property.internalText(
         PREFIX_IPTC_CORE + Metadata.NAMESPACE_PREFIX_DELIMITER + "CountryCode");

   /**
    * A textual description, including captions, of the item's content,
    * particularly used where the object is not text.
    * <p>
    * Note: the XMP property (dc:description) which stores the value of this
    * IPTC Core property is of type Lang Alt. Hence any software agent dealing
    * with this property must abide to the processing rules for
    * Lang Alt value type as specified by the XMP specifications.
    * <p>
    * Maps to this IIM property: 2:120 Caption/Abstract
    * 
    * @see DublinCore#DESCRIPTION
    */
   Property DESCRIPTION = DublinCore.DESCRIPTION;

   /**
    * A brief synopsis of the caption. Headline is not the same as Title.
    * <p>
    * Maps to this IIM property: 2:105 Headline
    * 
    * @see Photoshop#HEADLINE
    */
   Property HEADLINE = Photoshop.HEADLINE;

   /**
    * Describes the nature, intellectual, artistic or journalistic
    * characteristic of a item, not specifically its content.
    * <p>
    * The IPTC recognizes that the corresponding IPTC Genre NewsCodes needs
    * photo specific extension to be better usable with this field (as of the
    * release of this standard in the year 2008).
    * <p>
    * Maps to this IIM property: 2:04 Object Attribute Reference
    */
   Property INTELLECTUAL_GENRE = Property.internalText(
         PREFIX_IPTC_CORE + Metadata.NAMESPACE_PREFIX_DELIMITER + "IntellectualGenre");

   /**
    * Keywords to express the subject of the content. Keywords may be free
    * text and don't have to be taken from a controlled vocabulary. Codes from
    * the controlled vocabulary IPTC Subject NewsCodes must go to the
    * "Subject Code" field.
    * <p>
    * Single values of this field should not be restricted to single words
    * but must allow for phrases as well.
    * <p>
    * Maps to this IIM property: 2:25 Keywords
    * 
    * @see DublinCore#SUBJECT
    */
   Property KEYWORDS = DublinCore.SUBJECT;

   /**
    * Name of the subregion of a country -- either called province or state or
    * anything else -- the content is focussing on -- either the subregion
    * shown in visual media or referenced by text or audio media. This element
    * is at the second level of a top-down geographical hierarchy.
    * <p>
    * This is a detail of a location with blurred semantics as it does not
    * clearly indicate whether it is the location in the image or the location
    * the photo was taken - which can be different. Two more concise properties
    * are available in IPTC Extension with Location Created and Location Shown
    * in the Image.
    * <p>
    * Maps to this IIM property: 2:95 Province/State
    * 
    * @see Photoshop#STATE
    */
   Property PROVINCE_OR_STATE = Photoshop.STATE;

   /**
    * Describes the scene of a news content. Specifies one or more terms
    * from the IPTC "Scene-NewsCodes". Each Scene is represented as a string of
    * 6 digits in an unordered list.
    * <p>
    * Note: Only Scene values from this IPTC taxonomy should be used here. More
    * about the IPTC Scene-NewsCodes at www.newscodes.org.
    */
   Property SCENE_CODE = Property.internalText(
         PREFIX_IPTC_CORE + Metadata.NAMESPACE_PREFIX_DELIMITER + "Scene");

   /**
    * Specifies one or more Subjects from the IPTC Subject-NewsCodes taxonomy
    * to categorise the content. Each Subject is represented as a string of 8
    * digits in an unordered list.
    * <p>
    * Note: Only Subjects from a controlled vocabulary should be used here,
    * free text has to be put into the Keyword element. More about
    * IPTC Subject-NewsCodes at www.newscodes.org.
    */
   Property SUBJECT_CODE = Property.internalTextBag(
         PREFIX_IPTC_CORE + Metadata.NAMESPACE_PREFIX_DELIMITER + "SubjectCode");

   /**
    * Name of a sublocation the content is focussing on -- either the
    * location shown in visual media or referenced by text or audio media. This
    * location name could either be the name of a sublocation to a city or the
    * name of a well known location or (natural) monument outside a city. In
    * the sense of a sublocation to a city this element is at the fourth level
    * of a top-down geographical hierarchy.
    * <p>
    * This is a detail of a location with blurred semantics as it does not
    * clearly indicate whether it is the location in the image or the location
    * the photo was taken - which can be different. Two more concise properties
    * are available in IPTC Extension with Location Created and Location Shown
    * in the Image.
    * <p>
    * Maps to this IIM property: 2:92 Sublocation
    */
   Property SUBLOCATION = Property.internalText(
         PREFIX_IPTC_CORE + Metadata.NAMESPACE_PREFIX_DELIMITER + "Location");

   /**
    * Designates the date and optionally the time the intellectual content was
    * created rather than the date of the creation of the physical
    * representation.
    * <p>
    * If a software system requires explicit time values and no time is given
    * by the Date Created property the software system should default the time
    * to 00:00:00. If the software system does not require an explicit time
    * value the time part should be left empty as it is.
    * <p>
    * Note 1: Any content of the IIM dataset 2:60, Time Created, should be
    * merged to this element.
    * Note 2: Implementers are encouraged to provide
    * the creation date and time from the EXIF data of a digital
    * camera to the user for entering this date for the first time.
    * <p>
    * Maps to this IIM property: 2:55 Date Created
    * 
    * @see Photoshop#DATE_CREATED
    */
   Property DATE_CREATED = Photoshop.DATE_CREATED;

   /**
    * Identifier or the name of the person involved in writing, editing or
    * correcting the description of the content.
    * <p>
    * Maps to this IIM property: 2:122 Writer/Editor
    * 
    * @see Photoshop#CAPTION_WRITER
    */
   Property DESCRIPTION_WRITER = Photoshop.CAPTION_WRITER;

   /**
    * Any of a number of instructions from the provider or creator to the
    * receiver of the item.
    * <p>
    * Maps to this IIM property: 2:40 Special Instruction
    * 
    * @see Photoshop#INSTRUCTIONS
    */
   Property INSTRUCTIONS = Photoshop.INSTRUCTIONS;

   /**
    * Number or identifier for the purpose of improved workflow handling. This
    * is a user created identifier related to the job for which the item is
    * supplied.
    * <p>
    * Note: As this identifier references a job of the receiver's workflow it
    * must first be issued by the receiver, then transmitted to the creator or
    * provider of the news object and finally added by the creator
    * to this field.
    * <p>
    * Maps to this IIM property: 2:103 Original Transmission Reference
    * 
    * @see Photoshop#TRANSMISSION_REFERENCE
    */
   Property JOB_ID = Photoshop.TRANSMISSION_REFERENCE;

   /**
    * A shorthand reference for the item. Title provides a short human readable
    * name which can be a text and/or numeric reference. It is not the same as
    * Headline.
    * <p>
    * Many use the Title field to store the filename of the image, though the
    * field may be used in many ways. Formal identifiers are provided by the
    * Digital Image Id, or the Registry Entry property of the IPTC Extension.
    * <p>
    * Note 1: This element aligns with the use of Dublin Core's "Title"
    * element.
    * Note 2: the XMP property (dc:title) which stores the value of
    * this IPTC Core property is of type Lang Alt. Hence any software agent
    * dealing with this property must abide to the processing rules for Lang
    * Alt value type as specified by the XMP specifications.
    * <p>
    * Maps to this IIM property: 2:05 Object Name
    * 
    * @see DublinCore#TITLE
    */
   Property TITLE = DublinCore.TITLE;

   /**
    * Contains any necessary copyright notice for claiming the intellectual
    * property for this item and should identify the current owner of the
    * copyright for the item. Other entities like the creator of the item may
    * be added in the corresponding field. Notes on usage rights should be
    * provided in "Rights usage terms".
    * <p>
    * Copyright ownership can be expressed in a more controlled way using the
    * PLUS fields "Copyright Owner", "Copyright Owner ID",
    * "Copyright Owner Name" of the IPTC Extension. It is the user's
    * responsibility to keep the values of the four fields in sync.
    * <p>
    * Note: the XMP property (dc:rights) which stores the value of this IPTC
    * Core property is of type Lang Alt. Hence any software agent dealing with
    * this property must abide to the processing rules for Lang Alt
    * value type as specified by the XMP specifications.
    * <p>
    * Maps to this IIM property: 2:116 Copyright Notice
    * 
    * @see DublinCore#RIGHTS
    */
   Property COPYRIGHT_NOTICE = DublinCore.RIGHTS;

   /**
    * Contains the name of the person who created the content of this item, a
    * photographer for photos, a graphic artist for graphics, or a writer for
    * textual news, but in cases where the photographer should not be
    * identified the name of a company or organisation may be appropriate.
    * <p>
    * The creator can be expressed in a more controlled way using the
    * "Image Creator" of PLUS in the IPTC Extension additionally. It is the
    * user's responsibility to keep the values of the IPTC Core and the PLUS
    * fields in sync.
    * <p>
    * Maps to this IIM property: 2:80 By-line
    * 
    * @see DublinCore#CREATOR
    */
   Property CREATOR = DublinCore.CREATOR;

   /**
    * The creator's contact information provides all necessary information to
    * get in contact with the creator of this item and comprises a set of
    * sub-properties for proper addressing.
    * <p>
    * The IPTC Extension Licensor fields should be used instead of these
    * Creator's Contact Info fields if you are using IPTC Extension fields. If
    * the creator is also the licensor his or her contact information should be
    * provided in the Licensor fields.
    * <p>
    * Note 1 to user interface implementers: All sub-properties of "Creator's
    * contact information" should be shown as group on the form.
    * Note 2: the
    * CreatorContactInfo sub-properties' naming aligns with the vCard
    * specification RFC 2426.
    */
   Property CREATORS_CONTACT_INFO = Property.internalText(
         PREFIX_IPTC_CORE + Metadata.NAMESPACE_PREFIX_DELIMITER + "CreatorContactInfo");

   /**
    * Contains the job title of the person who created the content of this
    * item. As this is sort of a qualifier the Creator element has to be filled
    * in as mandatory prerequisite for using Creator's Jobtitle.
    * <p>
    * Maps to this IIM property: 2:85 By-line Title
    * 
    * @see Photoshop#AUTHORS_POSITION
    */
   Property CREATORS_JOB_TITLE = Photoshop.AUTHORS_POSITION;

   /**
    * The credit to person(s) and/or organisation(s) required by the supplier
    * of the item to be used when published. This is a free-text field.
    * <p>
    * Note 1: For more formal identifications of the creator or the owner of
    * the copyrights of this image other rights properties may be used.
    * Note 2:
    * This property was named "Credit" by the IIM metadata, then it was renamed
    * to "Provider" in IPTC Core 1.0. In IPTC Core 1.1. it has been renamed to
    * "Credit Line" as the field is used for this purpose by many users.
    * <p>
    * Maps to this IIM property: 2:110 Credit
    * 
    * @see Photoshop#CREDIT_LINE
    */
   Property CREDIT_LINE = Photoshop.CREDIT;

   /**
    * The licensing parameters of the item expressed in free-text.
    * <p>
    * The PLUS fields of the IPTC Extension can be used in parallel to express
    * the licensed usage in more controlled terms.
    */
   Property RIGHTS_USAGE_TERMS = XMPRights.USAGE_TERMS;

   /**
    * Identifies the original owner of the copyright for the intellectual
    * content of the item. This could be an agency, a member of an agency or an
    * individual. Source could be different from Creator and from the entities
    * in the CopyrightNotice.
    * <p>
    * The original owner can never change. For that reason the content of this
    * property should never be changed or deleted after the information is
    * entered following the news object's initial creation.
    * <p>
    * Maps to this IIM property: 2:115 Source
    * 
    * @see Photoshop#SOURCE
    */
   Property SOURCE = Photoshop.SOURCE;

   /**
    * The contact information address part. Comprises an optional company name
    * and all required information to locate the building or postbox to which
    * mail should be sent. To that end, the address is a multiline field.
    * <p>
    * Note 1: to user interface implementers: This field should be part of a
    * "Contact information" group on the form.
    * Note 2: the ContactInfo naming aligns with the vCard specification RFC 2426.
    */
   Property CONTACT_INFO_ADDRESS = Property.internalText(
         PREFIX_IPTC_CORE + Metadata.NAMESPACE_PREFIX_DELIMITER + "CiAdrExtadr");

   /**
    * The contact information city part.
    * <p>
    * Note 1: to user interface implementers: This field should be part of a
    * "Contact information" group on the form.
    * Note 2: the ContactInfo naming aligns with the vCard specification RFC 2426.
    */
   Property CONTACT_INFO_CITY = Property.internalText(
         PREFIX_IPTC_CORE + Metadata.NAMESPACE_PREFIX_DELIMITER + "CiAdrCity");

   /**
    * The contact information country part.
    * <p>
    * Note 1: to user interface implementers: This field should be part of a
    * "Contact information" group on the form.
    * Note 2: the ContactInfo naming aligns with the vCard specification RFC 2426.
    */
   Property CONTACT_INFO_COUNTRY = Property.internalText(
         PREFIX_IPTC_CORE + Metadata.NAMESPACE_PREFIX_DELIMITER + "CiAdrCtry");

   /**
    * The contact information email address part.
    * <p>
    * Multiple email addresses can be given. May have to be separated by a
    * comma in the user interface.
    * <p>
    * Note 1: to user interface implementers: This field should be part of a
    * "Contact information" group on the form.
    * Note 2 to user interface
    * implementers: provide sufficient space to fill in multiple e-mail
    * addresses.
    * Note 3: the ContactInfo naming aligns with the vCard
    * specification RFC 2426.
    */
   Property CONTACT_INFO_EMAIL = Property.internalText(
         PREFIX_IPTC_CORE + Metadata.NAMESPACE_PREFIX_DELIMITER + "CiEmailWork");

   /**
    * The contact information phone number part.
    * <p>
    * Multiple numbers can be given. May have to be separated by a
    * comma in the user interface.
    * <p>
    * Note 1: to user interface implementers: This field should be part of a
    * "Contact information" group on the form.
    * Note 2 to user interface
    * implementers: provide sufficient space to fill in multiple international
    * numbers.
    * Note 3: the ContactInfo naming aligns with the vCard
    * specification RFC 2426.
    */
   Property CONTACT_INFO_PHONE = Property.internalText(
         PREFIX_IPTC_CORE + Metadata.NAMESPACE_PREFIX_DELIMITER + "CiTelWork");

   /**
    * The contact information part denoting the local postal code.
    * <p>
    * Note 1: to user interface implementers: This field should be part of a
    * "Contact information" group on the form.
    * Note 2: the ContactInfo naming aligns with the vCard specification RFC 2426.
    */
   Property CONTACT_INFO_POSTAL_CODE = Property.internalText(
         PREFIX_IPTC_CORE + Metadata.NAMESPACE_PREFIX_DELIMITER + "CiAdrPcode");

   /**
    * The contact information part denoting regional information such as state or province.
    * <p>
    * Note 1: to user interface implementers: This field should be part of a
    * "Contact information" group on the form.
    * Note 2: the ContactInfo naming aligns with the vCard specification RFC 2426.
    */
   Property CONTACT_INFO_STATE_PROVINCE = Property.internalText(
         PREFIX_IPTC_CORE + Metadata.NAMESPACE_PREFIX_DELIMITER + "CiAdrRegion");

   /**
    * The contact information web address part. Multiple addresses can be given, separated by a comma.
    * <p>
    * Note 1: to user interface implementers: This field should be part of a
    * "Contact information" group on the form.
    * Note 2 to user interface
    * implementers: provide sufficient space to fill in multiple URLs.
    * Note 3: the ContactInfo naming aligns with the vCard
    * specification RFC 2426.
    */
   Property CONTACT_INFO_WEB_URL = Property.internalText(
         PREFIX_IPTC_CORE + Metadata.NAMESPACE_PREFIX_DELIMITER + "CiUrlWork");

   /**
    * As this metadata element pertains to distribution management, it was not
    * adopted. However, this data is still synchronised with the XMP property
    * [photoshop:Urgency], and hence, available for future use, but outside the
    * IPTC Core.
    *
    * @deprecated
    */
   Property URGENCY = Photoshop.URGENCY;

   /**
    * As this metadata element was earmarked as deprecated already for IIM 4.1,
    * it was not adopted. However, this data is still synchronised with the XMP
    * property [photoshop:Category], and hence available for future use - but
    * outside the IPTC Core. For migrating from Category codes to Subject Codes
    * please read the Guideline for mapping Category Codes to Subject NewsCodes
    * section below.
    *
    * @deprecated
    */
   Property CATEGORY = Photoshop.CATEGORY;

   /**
    * As this metadata element was earmarked as deprecated already for IIM 4.1,
    * it was not adopted. However, this data is still synchronised with the XMP
    * property [photoshop:SupplementalCategories], and hence available for
    * future use - but outside the IPTC Core.
    *
    * @deprecated
    */
   Property SUPPLEMENTAL_CATEGORIES = Photoshop.SUPPLEMENTAL_CATEGORIES;

   /**
    * Information about the ethnicity and other facets of the model(s) in a
    * model-released image.
    * <p>
    * Use the Model Age field for the age of model(s).
    */
   Property ADDITIONAL_MODEL_INFO = Property.internalText(
         PREFIX_IPTC_EXT + Metadata.NAMESPACE_PREFIX_DELIMITER + "AddlModelInfo");

   /**
    * A set of metadata about artwork or an object in the item
    */
   Property ARTWORK_OR_OBJECT = Property.internalTextBag(
         PREFIX_IPTC_EXT + Metadata.NAMESPACE_PREFIX_DELIMITER + "ArtworkOrObject");

   /**
    * A set of metadata about artwork or an object in the item
    */
   Property ORGANISATION_CODE = Property.internalTextBag(
         PREFIX_IPTC_EXT + Metadata.NAMESPACE_PREFIX_DELIMITER + "OrganisationInImageCode");

   /**
    * A term to describe the content of the image by a value from a Controlled
    * Vocabulary.
    * <p>
    * This property is part of the Photo Metadata 2008 specifications, but
    * should not released to the public on the standard Adobe Custom Panels for
    * IPTC metadata or other user interfaces unless agreed by the IPTC.
    */
   Property CONTROLLED_VOCABULARY_TERM = Property.internalTextBag(
         PREFIX_IPTC_EXT + Metadata.NAMESPACE_PREFIX_DELIMITER + "CVterm");

   /**
    * A location the content of the item is about. For photos that is a
    * location shown in the image.
    * <p>
    * If the location the image was taken in is different from this location
    * the property Location Created should be used too.
    */
   Property LOCATION_SHOWN = Property.internalTextBag(
         PREFIX_IPTC_EXT + Metadata.NAMESPACE_PREFIX_DELIMITER + "LocationShown");

   /**
    * Age of the human model(s) at the time this image was taken in a model
    * released image.
    * <p>
    * The user should be aware of any legal implications of providing ages for
    * young models. Ages below 18 years should not be included.
    */
   Property MODEL_AGE = Property.internalTextBag(
         PREFIX_IPTC_EXT + Metadata.NAMESPACE_PREFIX_DELIMITER + "ModelAge");

   /**
    * Name of the organisation or company which is featured in the content.
    * <p>
    * May be supplemented by values from a controlled vocabulary in the
    * Organisation Code field.
    */
   Property ORGANISATION_NAME = Property.internalTextBag(
         PREFIX_IPTC_EXT + Metadata.NAMESPACE_PREFIX_DELIMITER + "OrganisationInImageName");

   /**
    * Name of a person the content of the item is about. For photos that is a
    * person shown in the image.
    */
   Property PERSON = Property.internalTextBag(
         PREFIX_IPTC_EXT + Metadata.NAMESPACE_PREFIX_DELIMITER + "PersonInImage");

   /**
    * Globally unique identifier for the item. It is created and applied by the
    * creator of the item at the time of its creation . This value shall not be
    * changed after that time.
    * <p>
    * The identifier will probably be generated by the technical means of an
    * imaging device or software and should be applied to the digital image
    * file as early as possible in its life cycle. This identifier does not
    * identify any pictured content, particularly in case of a scan of non-
    * digital images, only this digital representation.
    * <p>
    * Any algorithm to create this identifier has to comply with the technical
    * requirements to create a globally unique id. Any device creating digital
    * images - e.g. still image cameras, video cameras, scanners - should
    * create such an identifer right at the time of the creation of the digital
    * data and add the id to the set of metadata without compromising
    * performance. It is recommended that this image identifier allows
    * identifying the device by which the image data and the GUID were created.
    * IPTC's basic requirements for unique ids are:
    * - It must be globally unique. Algorithms for this purpose exist.
    * - It should identify the camera body.
    * - It should identify each individual photo from this camera body.
    * - It should identify the date and time of the creation of the picture.
    * - It should be secured against tampering.
    * This field should be implemented in a way to prove it has not been changed since its value has
    * been applied. If the identifier has been created by the imaging device
    * its type and brand can be found in the Exif/technical metadata.
    */
   Property DIGITAL_IMAGE_GUID = Property.internalText(
         PREFIX_IPTC_EXT + Metadata.NAMESPACE_PREFIX_DELIMITER + "DigImageGUID");

   /**
    * The type of the source digital file.
    * <p>
    * The IPTC recommends not to implement this property any longer.
    *
    * @deprecated
    */
   Property DIGITAL_SOURCE_FILE_TYPE = Property.internalText(
         PREFIX_IPTC_EXT + Metadata.NAMESPACE_PREFIX_DELIMITER + "DigitalSourcefileType");

   /**
    * The type of the source of this digital image
    */
   Property DIGITAL_SOURCE_TYPE = Property.internalText(
         PREFIX_IPTC_EXT + Metadata.NAMESPACE_PREFIX_DELIMITER + "DigitalSourceType");

   /**
    * Names or describes the specific event the content relates to.
    * <p>
    * Examples are: a press conference, dedication ceremony, etc. If this is a
    * sub-event of a larger event both can be provided by the field: e.g. XXXIX
    * Olympic Summer Games (Beijing): opening ceremony. Unplanned events could
    * be named by this property too.
    */
   Property EVENT = Property.internalText(
         PREFIX_IPTC_EXT + Metadata.NAMESPACE_PREFIX_DELIMITER + "Event");

   /**
    * Both a Registry Item Id and a Registry Organisation Id to record any
    * registration of this item with a registry.
    * <p>
    * Typically an id from a registry is negotiated and applied after the
    * creation of the digital image.
    * <p>
    * Any user interface implementation must show both sub-properties - Item Id
    * and Organisation Id - as corresponding values. Further an input to both
    * fields should be made mandatory.
    */
   Property IMAGE_REGISTRY_ENTRY = Property.internalTextBag(
         PREFIX_IPTC_EXT + Metadata.NAMESPACE_PREFIX_DELIMITER + "RegistryId");

   /**
    * Identifies the most recent supplier of the item, who is not necessarily
    * its owner or creator.
    * <p>
    * For identifying the supplier either a well known and/or registered
    * company name or a URL of the company's web site may be used. This
    * property succeeds the Provider property of IPTC Core 1.0 by its semantics
    * as that Provider was renamed to Credit Line.
    * <p>
    * This is a PLUS version 1.2 property included in the IPTC Extension
    * schema.
    */
   Property IMAGE_SUPPLIER = Property.internalText(
         PREFIX_PLUS + Metadata.NAMESPACE_PREFIX_DELIMITER + "ImageSupplier");

   /**
    * Identifies the most recent supplier of the item, who is not necessarily
    * its owner or creator.
    * <p>
    * For identifying the supplier either a well known and/or registered
    * company name or a URL of the company's web site may be used. This
    * property succeeds the Provider property of IPTC Core 1.0 by its semantics
    * as that Provider was renamed to Credit Line.
    * <p>
    * This is a PLUS version 1.2 property included in the IPTC Extension
    * schema.
    */
   Property IMAGE_SUPPLIER_ID = Property.internalText(
         PREFIX_PLUS + Metadata.NAMESPACE_PREFIX_DELIMITER + "ImageSupplierId");

   /**
    * Identifies the most recent supplier of the item, who is not necessarily
    * its owner or creator.
    * <p>
    * For identifying the supplier either a well known and/or registered
    * company name or a URL of the company's web site may be used. This
    * property succeeds the Provider property of IPTC Core 1.0 by its semantics
    * as that Provider was renamed to Credit Line.
    * <p>
    * This is a PLUS version 1.2 property included in the IPTC Extension
    * schema.
    */
   Property IMAGE_SUPPLIER_NAME = Property.internalText(
         PREFIX_PLUS + Metadata.NAMESPACE_PREFIX_DELIMITER + "ImageSupplierName");

   /**
    * Optional identifier assigned by the Image Supplier to the image.
    * <p>
    * This is a PLUS version 1.2 property included in the IPTC Extension
    * schema.
    */
   Property IMAGE_SUPPLIER_IMAGE_ID = Property.internalText(
         PREFIX_PLUS + Metadata.NAMESPACE_PREFIX_DELIMITER + "ImageSupplierImageID");

   /**
    * The date and optionally time when any of the IPTC photo metadata fields
    * has been last edited
    * <p>
    * The public use of this property is deprecated by IPTC Extension version
    * 1.1. It may only still be used by a private user interface for a use
    * scoped to a company. If used this field should be a timestamp of the
    * latest change applied to any of the fields.
    * <p>
    * The value of this property should never be set by software. XMP-aware
    * software should reflect any changes to metadata by the xmp:MetadataDate
    * property of the XMP Basic scheme.
    */
   Property IPTC_LAST_EDITED = Property.internalDate(
         PREFIX_IPTC_EXT + Metadata.NAMESPACE_PREFIX_DELIMITER + "IptcLastEdited");

   /**
    * The location the content of the item was created.
    * <p>
    * If the location in the image is different from the location the photo was
    * taken the IPTC Extension property Location Shown in the Image should be
    * used.
    */
   Property LOCATION_CREATED = Property.internalTextBag(
         PREFIX_IPTC_EXT + Metadata.NAMESPACE_PREFIX_DELIMITER + "LocationCreated");

   /**
    * The maximum available height in pixels of the original photo from which
    * this photo has been derived by downsizing.
    */
   Property MAX_AVAIL_HEIGHT = Property.internalInteger(
         PREFIX_IPTC_EXT + Metadata.NAMESPACE_PREFIX_DELIMITER + "MaxAvailHeight");

   /**
    * The maximum available width in pixels of the original photo from which
    * this photo has been derived by downsizing.
    */
   Property MAX_AVAIL_WIDTH = Property.internalInteger(
         PREFIX_IPTC_EXT + Metadata.NAMESPACE_PREFIX_DELIMITER + "MaxAvailWidth");

   /**
    * The version number of the PLUS standards in place at the time of the
    * transaction.
    * <p>
    * This property was included into the IPTC Extension schema from PLUS
    * version 1.2 as all other PLUS properties. To reflect this the value of
    * "PLUS Version" should be set to the string "1.2.0"
    */
   Property PLUS_VERSION = Property.internalText(
         PREFIX_PLUS + Metadata.NAMESPACE_PREFIX_DELIMITER + "Version");

   /**
    * Owner or owners of the copyright in the licensed image.
    * <p>
    * Serves to identify the rights holder/s for the image. The Copyright
    * Owner, Image Creator and Licensor may be the same or different entities.
    * <p>
    * This is a PLUS version 1.2 property included in the IPTC Extension
    * schema.
    */
   Property COPYRIGHT_OWNER = Property.internalTextBag(
         PREFIX_PLUS + Metadata.NAMESPACE_PREFIX_DELIMITER + "CopyrightOwner");

   /**
    * The ID of the owner or owners of the copyright in the licensed image.
    * <p>
    * Serves to identify the rights holder/s for the image. The Copyright
    * Owner, Image Creator and Licensor may be the same or different entities.
    * <p>
    * This is a PLUS version 1.2 property included in the IPTC Extension
    * schema.
    */
   Property COPYRIGHT_OWNER_ID = Property.internalTextBag(
         PREFIX_PLUS + Metadata.NAMESPACE_PREFIX_DELIMITER + "CopyrightOwnerId");

   /**
    * The name of the owner or owners of the copyright in the licensed image.
    * <p>
    * Serves to identify the rights holder/s for the image. The Copyright
    * Owner, Image Creator and Licensor may be the same or different entities.
    * <p>
    * This is a PLUS version 1.2 property included in the IPTC Extension
    * schema.
    */
   Property COPYRIGHT_OWNER_NAME = Property.internalTextBag(
         PREFIX_PLUS + Metadata.NAMESPACE_PREFIX_DELIMITER + "CopyrightOwnerName");

   /**
    * Creator or creators of the image.
    * <p>
    * The creator can be additionally expressed in free-text using the IPTC
    * Core Creator field. In many countries, the Image Creator must be
    * attributed in association with any use of the image. The Image Creator,
    * Copyright Owner, Image Supplier and Licensor may be the same or different
    * entities.
    * <p>
    * This is a PLUS version 1.2 property included in the IPTC Extension
    * schema.
    */
   Property IMAGE_CREATOR = Property.internalTextBag(
         PREFIX_PLUS + Metadata.NAMESPACE_PREFIX_DELIMITER + "ImageCreator");

   /**
    * The ID of the creator or creators of the image.
    * <p>
    * The creator can be additionally expressed in free-text using the IPTC
    * Core Creator field. In many countries, the Image Creator must be
    * attributed in association with any use of the image. The Image Creator,
    * Copyright Owner, Image Supplier and Licensor may be the same or different
    * entities.
    * <p>
    * This is a PLUS version 1.2 property included in the IPTC Extension
    * schema.
    */
   Property IMAGE_CREATOR_ID = Property.internalTextBag(
         PREFIX_PLUS + Metadata.NAMESPACE_PREFIX_DELIMITER + "ImageCreatorId");

   /**
    * The name of the creator or creators of the image.
    * <p>
    * The creator can be additionally expressed in free-text using the IPTC
    * Core Creator field. In many countries, the Image Creator must be
    * attributed in association with any use of the image. The Image Creator,
    * Copyright Owner, Image Supplier and Licensor may be the same or different
    * entities.
    * <p>
    * This is a PLUS version 1.2 property included in the IPTC Extension
    * schema.
    */
   Property IMAGE_CREATOR_NAME = Property.internalTextBag(
         PREFIX_PLUS + Metadata.NAMESPACE_PREFIX_DELIMITER + "ImageCreatorName");

   /**
    * A person or company that should be contacted to obtain a licence for
    * using the item or who has licensed the item.
    * <p>
    * This is a PLUS version 1.2 property included in the IPTC Extension
    * schema.
    */
   Property LICENSOR = Property.internalTextBag(
         PREFIX_PLUS + Metadata.NAMESPACE_PREFIX_DELIMITER + "Licensor");

   /**
    * The ID of the person or company that should be contacted to obtain a licence for
    * using the item or who has licensed the item.
    * <p>
    * This is a PLUS version 1.2 property included in the IPTC Extension
    * schema.
    */
   Property LICENSOR_ID = Property.internalTextBag(
         PREFIX_PLUS + Metadata.NAMESPACE_PREFIX_DELIMITER + "LicensorId");

   /**
    * The name of the person or company that should be contacted to obtain a licence for
    * using the item or who has licensed the item.
    * <p>
    * This is a PLUS version 1.2 property included in the IPTC Extension
    * schema.
    */
   Property LICENSOR_NAME = Property.internalTextBag(
         PREFIX_PLUS + Metadata.NAMESPACE_PREFIX_DELIMITER + "LicensorName");

   /**
    * The city of a person or company that should be contacted to obtain a licence for
    * using the item or who has licensed the item.
    * <p>
    * This is a PLUS version 1.2 property included in the IPTC Extension
    * schema.
    */
   Property LICENSOR_CITY = Property.internalTextBag(
         PREFIX_PLUS + Metadata.NAMESPACE_PREFIX_DELIMITER + "LicensorCity");

   /**
    * The country of a person or company that should be contacted to obtain a licence for
    * using the item or who has licensed the item.
    * <p>
    * This is a PLUS version 1.2 property included in the IPTC Extension
    * schema.
    */
   Property LICENSOR_COUNTRY = Property.internalTextBag(
         PREFIX_PLUS + Metadata.NAMESPACE_PREFIX_DELIMITER + "LicensorCountry");

   /**
    * The email of a person or company that should be contacted to obtain a licence for
    * using the item or who has licensed the item.
    * <p>
    * This is a PLUS version 1.2 property included in the IPTC Extension
    * schema.
    */
   Property LICENSOR_EMAIL = Property.internalTextBag(
         PREFIX_PLUS + Metadata.NAMESPACE_PREFIX_DELIMITER + "LicensorEmail");

   /**
    * The extended address of a person or company that should be contacted to obtain a licence for
    * using the item or who has licensed the item.
    * <p>
    * This is a PLUS version 1.2 property included in the IPTC Extension
    * schema.
    */
   Property LICENSOR_EXTENDED_ADDRESS = Property.internalTextBag(
         PREFIX_PLUS + Metadata.NAMESPACE_PREFIX_DELIMITER + "LicensorExtendedAddress");

   /**
    * The postal code of a person or company that should be contacted to obtain a licence for
    * using the item or who has licensed the item.
    * <p>
    * This is a PLUS version 1.2 property included in the IPTC Extension
    * schema.
    */
   Property LICENSOR_POSTAL_CODE = Property.internalTextBag(
         PREFIX_PLUS + Metadata.NAMESPACE_PREFIX_DELIMITER + "LicensorPostalCode");

   /**
    * The region of a person or company that should be contacted to obtain a licence for
    * using the item or who has licensed the item.
    * <p>
    * This is a PLUS version 1.2 property included in the IPTC Extension
    * schema.
    */
   Property LICENSOR_REGION = Property.internalTextBag(
         PREFIX_PLUS + Metadata.NAMESPACE_PREFIX_DELIMITER + "LicensorRegion");

   /**
    * The street address of a person or company that should be contacted to obtain a licence for
    * using the item or who has licensed the item.
    * <p>
    * This is a PLUS version 1.2 property included in the IPTC Extension
    * schema.
    */
   Property LICENSOR_STREET_ADDRESS = Property.internalTextBag(
         PREFIX_PLUS + Metadata.NAMESPACE_PREFIX_DELIMITER + "LicensorStreetAddress");

   /**
    * The phone number of a person or company that should be contacted to obtain a licence for
    * using the item or who has licensed the item.
    * <p>
    * This is a PLUS version 1.2 property included in the IPTC Extension
    * schema.
    */
   Property LICENSOR_TELEPHONE_1 = Property.internalTextBag(
         PREFIX_PLUS + Metadata.NAMESPACE_PREFIX_DELIMITER + "LicensorTelephone1");

   /**
    * The phone number of a person or company that should be contacted to obtain a licence for
    * using the item or who has licensed the item.
    * <p>
    * This is a PLUS version 1.2 property included in the IPTC Extension
    * schema.
    */
   Property LICENSOR_TELEPHONE_2 = Property.internalTextBag(
         PREFIX_PLUS + Metadata.NAMESPACE_PREFIX_DELIMITER + "LicensorTelephone2");

   /**
    * The URL of a person or company that should be contacted to obtain a licence for
    * using the item or who has licensed the item.
    * <p>
    * This is a PLUS version 1.2 property included in the IPTC Extension
    * schema.
    */
   Property LICENSOR_URL = Property.internalTextBag(
         PREFIX_PLUS + Metadata.NAMESPACE_PREFIX_DELIMITER + "LicensorURL");

   /**
    * Age of the youngest model pictured in the image, at the time that the
    * image was made.
    * <p>
    * This age should not be displayed to the public on open web portals and
    * the like. But it may be used by image repositories in a
    * B2B enviroment.
    * <p>
    * This is a PLUS version 1.2 property included in the IPTC Extension
    * schema.
    */
   Property MINOR_MODEL_AGE_DISCLOSURE = Property.internalText(
         PREFIX_PLUS + Metadata.NAMESPACE_PREFIX_DELIMITER + "MinorModelAgeDisclosure");

   /**
    * Optional identifier associated with each Model Release.
    * <p>
    * This is a PLUS version 1.2 property included in the IPTC Extension
    * schema.
    */
   Property MODEL_RELEASE_ID = Property.internalTextBag(
         PREFIX_PLUS + Metadata.NAMESPACE_PREFIX_DELIMITER + "ModelReleaseID");

   /**
    * Summarizes the availability and scope of model releases authorizing usage
    * of the likenesses of persons appearing in the photograph.
    * <p>
    * It is recommended to apply the PLUS controlled value Unlimited Model
    * Releases (MR- UMR) very carefully and to check the wording of the model
    * release thoroughly before applying it.
    * <p>
    * This is a PLUS version 1.2 property included in the IPTC Extension
    * schema.
    */
   Property MODEL_RELEASE_STATUS = Property.internalText(
         PREFIX_PLUS + Metadata.NAMESPACE_PREFIX_DELIMITER + "ModelReleaseStatus");

   /**
    * Optional identifier associated with each Property Release.
    * <p>
    * This is a PLUS version 1.2 property included in the IPTC Extension
    * schema.
    */
   Property PROPERTY_RELEASE_ID = Property.internalTextBag(
         PREFIX_PLUS + Metadata.NAMESPACE_PREFIX_DELIMITER + "PropertyReleaseID");

   /**
    * Summarises the availability and scope of property releases authorizing
    * usage of the properties appearing in the photograph.
    * <p>
    * It is recommended to apply the value PR-UPR very carefully and to check
    * the wording of the property release thoroughly before applying it.
    * <p>
    * This is a PLUS version 1.2 property included in the IPTC Extension
    * schema.
    */
   Property PROPERTY_RELEASE_STATUS = Property.internalText(
         PREFIX_PLUS + Metadata.NAMESPACE_PREFIX_DELIMITER + "PropertyReleaseStatus");

   /**
    * Contains any necessary copyright notice for claiming the intellectual
    * property for artwork or an object in the image and should identify the
    * current owner of the copyright of this work with associated intellectual
    * property rights.
    */
   Property ARTWORK_OR_OBJECT_DETAIL_COPYRIGHT_NOTICE = Property.internalText(
         PREFIX_IPTC_EXT + Metadata.NAMESPACE_PREFIX_DELIMITER + "AOCopyrightNotice");

   /**
    * Contains the name of the artist who has created artwork or an object in the image.
    */
   Property ARTWORK_OR_OBJECT_DETAIL_CREATOR = Property.internalText(
         PREFIX_IPTC_EXT + Metadata.NAMESPACE_PREFIX_DELIMITER + "AOCreator");

   /**
    * Designates the date and optionally the time the artwork or object in the
    * image was created. This relates to artwork or objects with associated
    * intellectual property rights.
    */
   Property ARTWORK_OR_OBJECT_DETAIL_DATE_CREATED = Property.internalDate(
         PREFIX_IPTC_EXT + Metadata.NAMESPACE_PREFIX_DELIMITER + "AODateCreated");

   /**
    * The organisation or body holding and registering the artwork or object in
    * the image for inventory purposes.
    */
   Property ARTWORK_OR_OBJECT_DETAIL_SOURCE = Property.internalText(
         PREFIX_IPTC_EXT + Metadata.NAMESPACE_PREFIX_DELIMITER + "AOSource");

   /**
    * The inventory number issued by the organisation or body holding and
    * registering the artwork or object in the image.
    */
   Property ARTWORK_OR_OBJECT_DETAIL_SOURCE_INVENTORY_NUMBER = Property.internalText(
         PREFIX_IPTC_EXT + Metadata.NAMESPACE_PREFIX_DELIMITER + "AOSourceInvNo");

   /**
    * A reference for the artwork or object in the image.
    */
   Property ARTWORK_OR_OBJECT_DETAIL_TITLE = Property.internalText(
         PREFIX_IPTC_EXT + Metadata.NAMESPACE_PREFIX_DELIMITER + "AOTitle");

   /**
    * Name of the city of a location. This element is at the fourth level of a
    * top-down geographical hierarchy.
    */
   Property LOCATION_SHOWN_CITY = Property.internalText(
         PREFIX_IPTC_EXT + Metadata.NAMESPACE_PREFIX_DELIMITER + "LocationShownCity");

   /**
    * The ISO code of a country of a location. This element is at the second
    * level of a top-down geographical hierarchy.
    * <p>
    * Note 1: an implementer would have to derive from the length of the value
    * string whether this is the country code from the two or three letter
    * scheme as no explicit indication can be provided.
    */
   Property LOCATION_SHOWN_COUNTRY_CODE = Property.internalText(
         PREFIX_IPTC_EXT + Metadata.NAMESPACE_PREFIX_DELIMITER + "LocationShownCountryCode");

   /**
    * The name of a country of a location. This element is at the second level
    * of a top-down geographical hierarchy.
    */
   Property LOCATION_SHOWN_COUNTRY_NAME = Property.internalText(
         PREFIX_IPTC_EXT + Metadata.NAMESPACE_PREFIX_DELIMITER + "LocationShownCountryName");

   /**
    * The name of a subregion of a country - a province or state - of a
    * location. This element is at the third level of a top-down geographical
    * hierarchy.
    */
   Property LOCATION_SHOWN_PROVINCE_OR_STATE = Property.internalText(
         PREFIX_IPTC_EXT + Metadata.NAMESPACE_PREFIX_DELIMITER + "LocationShownProvinceState");

   /**
    * Name of a sublocation. This sublocation name could either be the name of
    * a sublocation to a city or the name of a well known location or (natural)
    * monument outside a city. In the sense of a sublocation to a city this
    * element is at the fifth level of a top-down geographical hierarchy.
    */
   Property LOCATION_SHOWN_SUBLOCATION = Property.internalText(
         PREFIX_IPTC_EXT + Metadata.NAMESPACE_PREFIX_DELIMITER + "LocationShownSublocation");

   /**
    * The name of a world region of a location. This element is at the first
    * (topI) level of a top- down geographical hierarchy.
    */
   Property LOCATION_SHOWN_WORLD_REGION = Property.internalText(
         PREFIX_IPTC_EXT + Metadata.NAMESPACE_PREFIX_DELIMITER + "LocationShownWorldRegion");

   /**
    * Name of the city of a location. This element is at the fourth level of a
    * top-down geographical hierarchy.
    */
   Property LOCATION_CREATED_CITY = Property.internalText(
         PREFIX_IPTC_EXT + Metadata.NAMESPACE_PREFIX_DELIMITER + "LocationCreatedCity");

   /**
    * The ISO code of a country of a location. This element is at the second
    * level of a top-down geographical hierarchy.
    * <p>
    * Note 1: an implementer would have to derive from the length of the value
    * string whether this is the country code from the two or three letter
    * scheme as no explicit indication can be provided.
    */
   Property LOCATION_CREATED_COUNTRY_CODE = Property.internalText(
         PREFIX_IPTC_EXT + Metadata.NAMESPACE_PREFIX_DELIMITER + "LocationCreatedCountryCode");

   /**
    * The name of a country of a location. This element is at the second level
    * of a top-down geographical hierarchy.
    */
   Property LOCATION_CREATED_COUNTRY_NAME = Property.internalText(
         PREFIX_IPTC_EXT + Metadata.NAMESPACE_PREFIX_DELIMITER + "LocationCreatedCountryName");

   /**
    * The name of a subregion of a country - a province or state - of a
    * location. This element is at the third level of a top-down geographical
    * hierarchy.
    */
   Property LOCATION_CREATED_PROVINCE_OR_STATE = Property.internalText(
         PREFIX_IPTC_EXT + Metadata.NAMESPACE_PREFIX_DELIMITER + "LocationCreatedProvinceState");

   /**
    * Name of a sublocation. This sublocation name could either be the name of
    * a sublocation to a city or the name of a well known location or (natural)
    * monument outside a city. In the sense of a sublocation to a city this
    * element is at the fifth level of a top-down geographical hierarchy.
    */
   Property LOCATION_CREATED_SUBLOCATION = Property.internalText(
         PREFIX_IPTC_EXT + Metadata.NAMESPACE_PREFIX_DELIMITER + "LocationCreatedSublocation");

   /**
    * The name of a world region of a location. This element is at the first
    * (topI) level of a top- down geographical hierarchy.
    */
   Property LOCATION_CREATED_WORLD_REGION = Property.internalText(
         PREFIX_IPTC_EXT + Metadata.NAMESPACE_PREFIX_DELIMITER + "LocationCreatedWorldRegion");

   /**
    * A unique identifier created by a registry and applied by the creator of
    * the item. This value shall not be changed after being applied. This
    * identifier is linked to a corresponding Registry Organisation Identifier.
    */
   Property REGISTRY_ENTRY_CREATED_ITEM_ID = Property.internalText(
         PREFIX_IPTC_EXT + Metadata.NAMESPACE_PREFIX_DELIMITER + "RegItemId");

   /**
    * An identifier for the registry which issued the corresponding Registry Image Id.
    */
   Property REGISTRY_ENTRY_CREATED_ORGANISATION_ID = Property.internalText(
         PREFIX_IPTC_EXT + Metadata.NAMESPACE_PREFIX_DELIMITER + "RegOrgId");


   Property[] PROPERTY_GROUP_IPTC_CORE = new Property[] {
         CITY,
         COUNTRY,
         COUNTRY_CODE,
         DESCRIPTION,
         HEADLINE,
         INTELLECTUAL_GENRE,
         KEYWORDS,
         PROVINCE_OR_STATE,
         SCENE_CODE,
         SUBJECT_CODE,
         SUBLOCATION,
         DATE_CREATED,
         DESCRIPTION_WRITER,
         INSTRUCTIONS,
         JOB_ID,
         TITLE,
         COPYRIGHT_NOTICE,
         CREATOR,
         CREATORS_JOB_TITLE,
         CREDIT_LINE,
         RIGHTS_USAGE_TERMS,
         SOURCE,
         CONTACT_INFO_ADDRESS,
         CONTACT_INFO_CITY,
         CONTACT_INFO_COUNTRY,
         CONTACT_INFO_EMAIL,
         CONTACT_INFO_PHONE,
         CONTACT_INFO_POSTAL_CODE,
         CONTACT_INFO_STATE_PROVINCE,
         CONTACT_INFO_WEB_URL
   };

   Property[] PROPERTY_GROUP_IPTC_EXT = new Property[] {
         ADDITIONAL_MODEL_INFO,
         ORGANISATION_CODE,
         CONTROLLED_VOCABULARY_TERM,
         MODEL_AGE,
         ORGANISATION_NAME,
         PERSON,
         DIGITAL_IMAGE_GUID,
         DIGITAL_SOURCE_TYPE,
         EVENT,
         IMAGE_SUPPLIER_ID,
         IMAGE_SUPPLIER_NAME,
         IMAGE_SUPPLIER_IMAGE_ID,
         IPTC_LAST_EDITED,
         MAX_AVAIL_HEIGHT,
         MAX_AVAIL_WIDTH,
         PLUS_VERSION,
         COPYRIGHT_OWNER_ID,
         COPYRIGHT_OWNER_NAME,
         IMAGE_CREATOR_ID,
         IMAGE_CREATOR_NAME,
         LICENSOR_ID,
         LICENSOR_NAME,
         LICENSOR_CITY,
         LICENSOR_COUNTRY,
         LICENSOR_EMAIL,
         LICENSOR_EXTENDED_ADDRESS,
         LICENSOR_POSTAL_CODE,
         LICENSOR_REGION,
         LICENSOR_STREET_ADDRESS,
         LICENSOR_TELEPHONE_1,
         LICENSOR_TELEPHONE_2,
         LICENSOR_URL,
         MINOR_MODEL_AGE_DISCLOSURE,
         MODEL_RELEASE_ID,
         MODEL_RELEASE_STATUS,
         PROPERTY_RELEASE_ID,
         PROPERTY_RELEASE_STATUS,
         ARTWORK_OR_OBJECT_DETAIL_COPYRIGHT_NOTICE,
         ARTWORK_OR_OBJECT_DETAIL_CREATOR,
         ARTWORK_OR_OBJECT_DETAIL_DATE_CREATED,
         ARTWORK_OR_OBJECT_DETAIL_SOURCE,
         ARTWORK_OR_OBJECT_DETAIL_SOURCE_INVENTORY_NUMBER,
         ARTWORK_OR_OBJECT_DETAIL_TITLE,
         LOCATION_SHOWN_CITY,
         LOCATION_SHOWN_COUNTRY_CODE,
         LOCATION_SHOWN_COUNTRY_NAME,
         LOCATION_SHOWN_PROVINCE_OR_STATE,
         LOCATION_SHOWN_SUBLOCATION,
         LOCATION_SHOWN_WORLD_REGION,
         LOCATION_CREATED_CITY,
         LOCATION_CREATED_COUNTRY_CODE,
         LOCATION_CREATED_COUNTRY_NAME,
         LOCATION_CREATED_PROVINCE_OR_STATE,
         LOCATION_CREATED_SUBLOCATION,
         LOCATION_CREATED_WORLD_REGION,
         REGISTRY_ENTRY_CREATED_ITEM_ID,
         REGISTRY_ENTRY_CREATED_ORGANISATION_ID
   };
}