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
package org.apache.tika.metadata;

/**
 * Keys extracted from ISO 19139/19115 geographic metadata documents by
 * {@code GeographicInformationParser} (via the Apache SIS/GeoAPI object model). Namespaced
 * {@code iso19115:} + kebab-case (TIKA-4816 rename batch): the original spellings were this
 * parser's own ad hoc coinages (bare PascalCase, a few with a stray trailing space, a trailing
 * hyphen, or a trailing {@code -->}) predating curated {@link Property} constants -- the old
 * literal spellings are bridged forward by {@code LegacyKeyMigrationFilter}'s migration table.
 *
 * <p>All but {@link #PARENT_METADATA_TITLE} and {@link #METADATA_IDENTIFIER_CODE} are BAG: each
 * is written from a loop over a repeatable ISO 19139 element, so these can legitimately repeat.
 */
public interface ISO19115 {

    Property CHARACTER_SET = Property.externalTextBag("iso19115:character-set");

    Property CONTACT_ROLE = Property.externalTextBag("iso19115:contact-role");

    Property CONTACT_PARTY_NAME = Property.externalTextBag("iso19115:contact-party-name");

    Property CITATION_DATE = Property.externalTextBag("iso19115:citation-date");

    Property CITED_RESPONSIBLE_PARTY_ROLE =
            Property.externalTextBag("iso19115:cited-responsible-party-role");

    Property CITED_RESPONSIBLE_PARTY_NAME =
            Property.externalTextBag("iso19115:cited-responsible-party-name");

    Property CITED_RESPONSIBLE_PARTY_ORGANIZATION_NAME =
            Property.externalTextBag("iso19115:cited-responsible-party-organization-name");

    Property CITED_RESPONSIBLE_PARTY_POSITION_NAME =
            Property.externalTextBag("iso19115:cited-responsible-party-position-name");

    Property CITED_RESPONSIBLE_PARTY_EMAIL =
            Property.externalTextBag("iso19115:cited-responsible-party-email");

    Property IDENTIFICATION_INFO_STATUS = Property.externalTextBag("iso19115:identification-info-status");

    Property RESOURCE_FORMAT_SPECIFICATION_ALTERNATIVE_TITLE =
            Property.externalTextBag("iso19115:resource-format-specification-alternative-title");

    Property IDENTIFICATION_INFO_TOPIC_CATEGORY =
            Property.externalTextBag("iso19115:identification-info-topic-category");

    Property THESAURUS_NAME_DATE = Property.externalTextBag("iso19115:thesaurus-name-date");

    Property ACCESS_CONSTRAINTS = Property.externalTextBag("iso19115:access-constraints");

    Property OTHER_CONSTRAINTS = Property.externalTextBag("iso19115:other-constraints");

    Property USE_CONSTRAINTS = Property.externalTextBag("iso19115:use-constraints");

    Property GEOGRAPHIC_IDENTIFIER_CODE = Property.externalTextBag("iso19115:geographic-identifier-code");

    Property GEOGRAPHIC_IDENTIFIER_AUTHORITY_TITLE =
            Property.externalTextBag("iso19115:geographic-identifier-authority-title");

    Property GEOGRAPHIC_IDENTIFIER_AUTHORITY_ALTERNATIVE_TITLE =
            Property.externalTextBag("iso19115:geographic-identifier-authority-alternative-title");

    Property GEOGRAPHIC_IDENTIFIER_AUTHORITY_DATE =
            Property.externalTextBag("iso19115:geographic-identifier-authority-date");

    Property DISTRIBUTION_FORMAT_SPECIFICATION_ALTERNATIVE_TITLE =
            Property.externalTextBag("iso19115:distribution-format-specification-alternative-title");

    Property DISTRIBUTOR_CONTACT = Property.externalTextBag("iso19115:distributor-contact");

    Property DISTRIBUTOR_ORGANIZATION_NAME =
            Property.externalTextBag("iso19115:distributor-organization-name");

    Property TRANSFER_OPTIONS_ONLINE_LINKAGE =
            Property.externalTextBag("iso19115:transfer-options-online-linkage");

    Property TRANSFER_OPTIONS_ONLINE_PROTOCOL =
            Property.externalTextBag("iso19115:transfer-options-online-protocol");

    Property TRANSFER_OPTIONS_ONLINE_PROFILE =
            Property.externalTextBag("iso19115:transfer-options-online-profile");

    Property TRANSFER_OPTIONS_ONLINE_NAME =
            Property.externalTextBag("iso19115:transfer-options-online-name");

    Property TRANSFER_OPTIONS_ONLINE_DESCRIPTION =
            Property.externalTextBag("iso19115:transfer-options-online-description");

    Property TRANSFER_OPTIONS_ONLINE_FUNCTION =
            Property.externalTextBag("iso19115:transfer-options-online-function");

    Property DATE_INFO = Property.externalTextBag("iso19115:date-info");

    Property METADATA_RESOURCE_SCOPE = Property.externalTextBag("iso19115:metadata-resource-scope");

    /** Single-valued: read from one {@code Citation}, not a loop. */
    Property PARENT_METADATA_TITLE = Property.externalText("iso19115:parent-metadata-title");

    /** Single-valued: read from one {@code Identifier}, not a loop. */
    Property METADATA_IDENTIFIER_CODE = Property.externalText("iso19115:metadata-identifier-code");

    Property METADATA_STANDARD_TITLE = Property.externalTextBag("iso19115:metadata-standard-title");

    Property METADATA_STANDARD_EDITION = Property.externalTextBag("iso19115:metadata-standard-edition");

}
