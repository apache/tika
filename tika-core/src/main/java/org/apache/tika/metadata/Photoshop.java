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
 * XMP Photoshop metadata schema. 
 * 
 * A collection of property constants for the 
 * Photo Metadata properties defined in the XMP Photoshop
 * standard.
 * 
 * @since Apache Tika 1.2
 * @see <a href="http://partners.adobe.com/public/developer/en/xmp/sdk/XMPspecification.pdf">XMP Photoshop</a>
 */
public interface Photoshop {

    String NAMESPACE_URI_PHOTOSHOP = "http://ns.adobe.com/photoshop/1.0/";
    String PREFIX_PHOTOSHOP = "photoshop";

    Property AUTHORS_POSITION = Property.internalText(
            PREFIX_PHOTOSHOP + Metadata.NAMESPACE_PREFIX_DELIMITER + "AuthorsPosition");

    Property CAPTION_WRITER = Property.internalText(
            PREFIX_PHOTOSHOP + Metadata.NAMESPACE_PREFIX_DELIMITER + "CaptionWriter");

    Property CATEGORY = Property.internalText(
            PREFIX_PHOTOSHOP + Metadata.NAMESPACE_PREFIX_DELIMITER + "Category");

    Property CITY = Property.internalText(
            PREFIX_PHOTOSHOP + Metadata.NAMESPACE_PREFIX_DELIMITER + "City");

    Property COUNTRY = Property.internalText(
            PREFIX_PHOTOSHOP + Metadata.NAMESPACE_PREFIX_DELIMITER + "Country");

    Property CREDIT = Property.internalText(
            PREFIX_PHOTOSHOP + Metadata.NAMESPACE_PREFIX_DELIMITER + "Credit");

    Property DATE_CREATED = Property.internalDate(
            PREFIX_PHOTOSHOP + Metadata.NAMESPACE_PREFIX_DELIMITER + "DateCreated");

    Property HEADLINE = Property.internalText(
            PREFIX_PHOTOSHOP + Metadata.NAMESPACE_PREFIX_DELIMITER + "Headline");

    Property INSTRUCTIONS = Property.internalText(
            PREFIX_PHOTOSHOP + Metadata.NAMESPACE_PREFIX_DELIMITER + "Instructions");

    Property SOURCE = Property.internalText(
            PREFIX_PHOTOSHOP + Metadata.NAMESPACE_PREFIX_DELIMITER + "Source");

    Property STATE = Property.internalText(
            PREFIX_PHOTOSHOP + Metadata.NAMESPACE_PREFIX_DELIMITER + "State");

    Property SUPPLEMENTAL_CATEGORIES = Property.internalTextBag(
            PREFIX_PHOTOSHOP + Metadata.NAMESPACE_PREFIX_DELIMITER + "SupplementalCategories");

    Property TRANSMISSION_REFERENCE = Property.internalText(
            PREFIX_PHOTOSHOP + Metadata.NAMESPACE_PREFIX_DELIMITER + "TransmissionReference");

    Property URGENCY = Property.internalText(
            PREFIX_PHOTOSHOP + Metadata.NAMESPACE_PREFIX_DELIMITER + "Urgency");

}