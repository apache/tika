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
package org.apache.tika.parser.geoinfo;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.sis.metadata.iso.DefaultMetadata;
import org.apache.sis.metadata.iso.DefaultMetadataScope;
import org.apache.sis.metadata.iso.constraint.DefaultLegalConstraints;
import org.apache.sis.metadata.iso.extent.DefaultGeographicBoundingBox;
import org.apache.sis.metadata.iso.extent.DefaultGeographicDescription;
import org.apache.sis.metadata.iso.identification.DefaultDataIdentification;
import org.apache.sis.storage.DataStore;
import org.apache.sis.storage.DataStoreException;
import org.apache.sis.storage.DataStores;
import org.apache.sis.storage.UnsupportedStorageException;
import org.apache.sis.util.collection.CodeListSet;
import org.opengis.metadata.Identifier;
import org.opengis.metadata.citation.Citation;
import org.opengis.metadata.citation.CitationDate;
import org.opengis.metadata.citation.OnlineResource;
import org.opengis.metadata.citation.ResponsibleParty;
import org.opengis.metadata.constraint.Restriction;
import org.opengis.metadata.distribution.DigitalTransferOptions;
import org.opengis.metadata.distribution.Distribution;
import org.opengis.metadata.distribution.Distributor;
import org.opengis.metadata.distribution.Format;
import org.opengis.metadata.extent.Extent;
import org.opengis.metadata.extent.GeographicExtent;
import org.opengis.metadata.identification.Identification;
import org.opengis.metadata.identification.Keywords;
import org.opengis.metadata.identification.Progress;
import org.opengis.metadata.identification.TopicCategory;
import org.opengis.util.InternationalString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import org.apache.tika.annotation.TikaComponent;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.ISO19115;
import org.apache.tika.metadata.KeyPrefix;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.XHTMLContentHandler;
import org.apache.tika.utils.DateUtils;

@TikaComponent
public class GeographicInformationParser implements Parser {

    public static final String geoInfoType = "text/iso19139+xml";
    private static final Logger LOG = LoggerFactory.getLogger(GeographicInformationParser.class);
    private final Set<MediaType> SUPPORTED_TYPES =
            Collections.singleton(MediaType.text("iso19139+xml"));

    // Indexed per Keywords/thesaurus entry; unbounded distinct names, so minted via KeyPrefix
    // rather than a Property constant. BAG: a keyword group can hold multiple strings, and the
    // per-Identification index restarts at 1, so the same key can recur and merge across
    // Identifications (known limitation, not fixed here).
    private static final KeyPrefix KEYWORDS =
            KeyPrefix.file("iso19115:keywords:", "ISO19139 indexed identification keyword group");

    private static final KeyPrefix KEYWORDS_TYPE = KeyPrefix.file("iso19115:keywords-type:",
            "ISO19139 indexed identification keyword group type");

    private static final KeyPrefix THESAURUS_NAME_TITLE =
            KeyPrefix.file("iso19115:thesaurus-name-title:", "ISO19139 indexed thesaurus name title");

    private static final KeyPrefix THESAURUS_NAME_ALTERNATIVE_TITLE = KeyPrefix.file(
            "iso19115:thesaurus-name-alternative-title:",
            "ISO19139 indexed thesaurus name alternative title");


    @Override
    public Set<MediaType> getSupportedTypes(ParseContext parseContext) {
        return SUPPORTED_TYPES;
    }

    @Override
    public void parse(TikaInputStream tis, ContentHandler contentHandler, Metadata metadata,
                      ParseContext parseContext) throws IOException, SAXException, TikaException {
        metadata.set(HttpHeaders.CONTENT_TYPE, geoInfoType);
        XHTMLContentHandler xhtmlContentHandler = new XHTMLContentHandler(contentHandler, metadata, parseContext);

        TemporaryResources tmp = null;
        try (tis) {
            File file = tis.getFile();
            try (DataStore dataStore = DataStores.open(file)) {
                DefaultMetadata defaultMetadata = new DefaultMetadata(dataStore.getMetadata());
                extract(xhtmlContentHandler, metadata, defaultMetadata);
            }

        } catch (UnsupportedStorageException e) {
            throw new TikaException("UnsupportedStorageException", e);
        } catch (DataStoreException e) {
            throw new TikaException("DataStoreException", e);
        } finally {
            if (tmp != null) {
                tmp.dispose();
            }
        }
    }

    private void extract(XHTMLContentHandler xhtmlContentHandler, Metadata metadata,
                         DefaultMetadata defaultMetadata) throws SAXException {
        getMetaDataCharacterSet(metadata, defaultMetadata);
        getMetaDataContact(metadata, defaultMetadata);
        getMetaDataIdentificationInfo(metadata, defaultMetadata);
        getMetaDataDistributionInfo(metadata, defaultMetadata);
        getMetaDataDateInfo(metadata, defaultMetadata);
        getMetaDataResourceScope(metadata, defaultMetadata);
        getMetaDataParentMetaDataTitle(metadata, defaultMetadata);
        getMetaDataIdetifierCode(metadata, defaultMetadata);
        getMetaDataStandard(metadata, defaultMetadata);
        extractContent(xhtmlContentHandler, defaultMetadata);
    }

    private void extractContent(XHTMLContentHandler xhtmlContentHandler,
                                DefaultMetadata defaultMetadata) throws SAXException {
        xhtmlContentHandler.startDocument();
        xhtmlContentHandler.newline();

        xhtmlContentHandler.newline();
        ArrayList<Identification> identifications =
                (ArrayList<Identification>) defaultMetadata.getIdentificationInfo();
        for (Identification i : identifications) {
            xhtmlContentHandler.startElement("h1");
            xhtmlContentHandler.characters(i.getCitation().getTitle().toString());
            xhtmlContentHandler.endElement("h1");
            xhtmlContentHandler.newline();

            ArrayList<ResponsibleParty> responsiblePartyArrayList =
                    (ArrayList<ResponsibleParty>) i.getCitation().getCitedResponsibleParties();
            for (ResponsibleParty r : responsiblePartyArrayList) {
                xhtmlContentHandler.startElement("h3");
                xhtmlContentHandler.newline();
                xhtmlContentHandler
                        .characters("CitedResponsiblePartyRole " + r.getRole().toString());
                xhtmlContentHandler
                        .characters("CitedResponsiblePartyName " + r.getIndividualName());
                xhtmlContentHandler.endElement("h3");
                xhtmlContentHandler.newline();
            }

            xhtmlContentHandler.startElement("p");
            xhtmlContentHandler.newline();
            xhtmlContentHandler
                    .characters("IdentificationInfoAbstract " + i.getAbstract().toString());
            xhtmlContentHandler.endElement("p");
            xhtmlContentHandler.newline();
            Collection<Extent> extentList = ((DefaultDataIdentification) i).getExtents();
            for (Extent e : extentList) {
                ArrayList<GeographicExtent> geoElements =
                        (ArrayList<GeographicExtent>) e.getGeographicElements();
                for (GeographicExtent g : geoElements) {

                    if (g instanceof DefaultGeographicBoundingBox) {
                        xhtmlContentHandler.startElement("tr");
                        xhtmlContentHandler.startElement("td");
                        xhtmlContentHandler.characters("GeographicElementWestBoundLatitude");
                        xhtmlContentHandler.endElement("td");
                        xhtmlContentHandler.startElement("td");
                        xhtmlContentHandler.characters(String.valueOf(
                                ((DefaultGeographicBoundingBox) g).getWestBoundLongitude()));
                        xhtmlContentHandler.endElement("td");
                        xhtmlContentHandler.endElement("tr");
                        xhtmlContentHandler.startElement("tr");
                        xhtmlContentHandler.startElement("td");
                        xhtmlContentHandler.characters("GeographicElementEastBoundLatitude");
                        xhtmlContentHandler.endElement("td");
                        xhtmlContentHandler.startElement("td");
                        xhtmlContentHandler.characters(String.valueOf(
                                ((DefaultGeographicBoundingBox) g).getEastBoundLongitude()));
                        xhtmlContentHandler.endElement("td");
                        xhtmlContentHandler.endElement("tr");
                        xhtmlContentHandler.startElement("tr");
                        xhtmlContentHandler.startElement("td");
                        xhtmlContentHandler.characters("GeographicElementNorthBoundLatitude");
                        xhtmlContentHandler.endElement("td");
                        xhtmlContentHandler.startElement("td");
                        xhtmlContentHandler.characters(String.valueOf(
                                ((DefaultGeographicBoundingBox) g).getNorthBoundLatitude()));
                        xhtmlContentHandler.endElement("td");
                        xhtmlContentHandler.endElement("tr");
                        xhtmlContentHandler.startElement("tr");
                        xhtmlContentHandler.startElement("td");
                        xhtmlContentHandler.characters("GeographicElementSouthBoundLatitude");
                        xhtmlContentHandler.endElement("td");
                        xhtmlContentHandler.startElement("td");
                        xhtmlContentHandler.characters(String.valueOf(
                                ((DefaultGeographicBoundingBox) g).getSouthBoundLatitude()));
                        xhtmlContentHandler.endElement("td");
                        xhtmlContentHandler.endElement("tr");
                    }
                }
            }
        }
        xhtmlContentHandler.newline();
        xhtmlContentHandler.endDocument();
    }

    private void getMetaDataCharacterSet(Metadata metadata, DefaultMetadata defaultMetaData) {
        Map<Locale, Charset> charsetMap = defaultMetaData.getLocalesAndCharsets();
        for (Charset c : charsetMap.values()) {
            metadata.add(ISO19115.CHARACTER_SET, c.name());
        }
    }


    private void getMetaDataContact(Metadata metadata, DefaultMetadata defaultMetaData) {
        Collection<ResponsibleParty> contactSet =
                (Collection<ResponsibleParty>) defaultMetaData.getContacts();
        for (ResponsibleParty rparty : contactSet) {
            if (rparty.getRole() != null) {
                metadata.add(ISO19115.CONTACT_ROLE, rparty.getRole().name());
            }
            if (rparty.getOrganisationName() != null) {
                metadata.add(ISO19115.CONTACT_PARTY_NAME, rparty.getOrganisationName().toString());
            }
        }
    }

    private void getMetaDataIdentificationInfo(Metadata metadata, DefaultMetadata defaultMetaData) {
        ArrayList<Identification> identifications =
                (ArrayList<Identification>) defaultMetaData.getIdentificationInfo();
        for (Identification i : identifications) {
            DefaultDataIdentification defaultDataIdentification = (DefaultDataIdentification) i;
            if (i.getCitation() != null && i.getCitation().getTitle() != null) {
                metadata.set(TikaCoreProperties.TITLE,
                        i.getCitation().getTitle().toString());
            }

            ArrayList<CitationDate> dateArrayList =
                    (ArrayList<CitationDate>) i.getCitation().getDates();
            for (CitationDate d : dateArrayList) {
                if (d.getDateType() != null) {
                    String date = DateUtils.formatDate(d.getDate());
                    metadata.add(ISO19115.CITATION_DATE, d.getDateType().name() + "-->" + date);
                }
            }
            ArrayList<ResponsibleParty> responsiblePartyArrayList =
                    (ArrayList<ResponsibleParty>) i.getCitation().getCitedResponsibleParties();
            for (ResponsibleParty r : responsiblePartyArrayList) {
                if (r.getRole() != null) {
                    metadata.add(ISO19115.CITED_RESPONSIBLE_PARTY_ROLE, r.getRole().toString());
                }
                if (r.getIndividualName() != null) {
                    metadata.add(ISO19115.CITED_RESPONSIBLE_PARTY_NAME, r.getIndividualName());
                }
                if (r.getOrganisationName() != null) {
                    metadata.add(ISO19115.CITED_RESPONSIBLE_PARTY_ORGANIZATION_NAME,
                            r.getOrganisationName().toString());
                }
                if (r.getPositionName() != null) {
                    metadata.add(ISO19115.CITED_RESPONSIBLE_PARTY_POSITION_NAME,
                            r.getPositionName().toString());
                }

                if (r.getContactInfo() != null) {
                    for (String s : r.getContactInfo().getAddress().getElectronicMailAddresses()) {
                        metadata.add(ISO19115.CITED_RESPONSIBLE_PARTY_EMAIL, s);
                    }
                }
            }
            if (i.getAbstract() != null) {
                metadata.set(TikaCoreProperties.DESCRIPTION, i.getAbstract().toString());
            }
            for (Progress p : i.getStatus()) {
                metadata.add(ISO19115.IDENTIFICATION_INFO_STATUS, p.name());
            }
            ArrayList<Format> formatArrayList = (ArrayList<Format>) i.getResourceFormats();
            for (Format f : formatArrayList) {
                if (f.getName() != null) {
                    metadata.add(ISO19115.RESOURCE_FORMAT_SPECIFICATION_ALTERNATIVE_TITLE,
                            f.getName().toString());
                }
            }
            Map<Locale, Charset> localeCharsetMap =
                    defaultDataIdentification.getLocalesAndCharsets();
            for (Locale l : localeCharsetMap.keySet()) {
                metadata.set(TikaCoreProperties.LANGUAGE, l.getDisplayLanguage(Locale.ENGLISH));
            }
            CodeListSet<TopicCategory> categoryList =
                    (CodeListSet<TopicCategory>) defaultDataIdentification.getTopicCategories();
            for (TopicCategory t : categoryList) {
                metadata.add(ISO19115.IDENTIFICATION_INFO_TOPIC_CATEGORY, t.name());
            }
            ArrayList<Keywords> keywordList = (ArrayList<Keywords>) i.getDescriptiveKeywords();
            int j = 1;
            for (Keywords k : keywordList) {
                j++;
                ArrayList<InternationalString> stringList =
                        (ArrayList<InternationalString>) k.getKeywords();
                for (InternationalString s : stringList) {
                    metadata.add(KEYWORDS, String.valueOf(j), s.toString());
                }
                if (k.getType() != null) {
                    metadata.add(KEYWORDS_TYPE, String.valueOf(j), k.getType().name());
                }
                if (k.getThesaurusName() != null && k.getThesaurusName().getTitle() != null) {
                    metadata.add(THESAURUS_NAME_TITLE, String.valueOf(j),
                            k.getThesaurusName().getTitle().toString());
                }
                if (k.getThesaurusName() != null &&
                        k.getThesaurusName().getAlternateTitles() != null) {
                    metadata.add(THESAURUS_NAME_ALTERNATIVE_TITLE, String.valueOf(j),
                            k.getThesaurusName().getAlternateTitles().toString());
                }

                ArrayList<CitationDate> citationDates =
                        (ArrayList<CitationDate>) k.getThesaurusName().getDates();
                for (CitationDate cd : citationDates) {
                    if (cd.getDateType() != null) {
                        String date = DateUtils.formatDate(cd.getDate());
                        metadata.add(ISO19115.THESAURUS_NAME_DATE,
                                cd.getDateType().name() + "-->" + date);
                    }
                }
            }
            ArrayList<DefaultLegalConstraints> constraintList =
                    (ArrayList<DefaultLegalConstraints>) i.getResourceConstraints();

            for (DefaultLegalConstraints c : constraintList) {
                for (Restriction r : c.getAccessConstraints()) {
                    metadata.add(ISO19115.ACCESS_CONSTRAINTS, r.name());
                }
                for (InternationalString s : c.getOtherConstraints()) {
                    metadata.add(ISO19115.OTHER_CONSTRAINTS, s.toString());
                }
                for (Restriction r : c.getUseConstraints()) {
                    metadata.add(ISO19115.USE_CONSTRAINTS, r.name());
                }

            }
            Collection<Extent> extentList = ((DefaultDataIdentification) i).getExtents();
            for (Extent e : extentList) {
                ArrayList<GeographicExtent> geoElements =
                        (ArrayList<GeographicExtent>) e.getGeographicElements();
                for (GeographicExtent g : geoElements) {

                    if (g instanceof DefaultGeographicDescription) {
                        if (((DefaultGeographicDescription) g).getGeographicIdentifier() != null &&
                                ((DefaultGeographicDescription) g).getGeographicIdentifier()
                                        .getCode() != null) {
                            metadata.add(ISO19115.GEOGRAPHIC_IDENTIFIER_CODE,
                                    ((DefaultGeographicDescription) g).getGeographicIdentifier()
                                            .getCode());
                        }
                        if (((DefaultGeographicDescription) g).getGeographicIdentifier() != null &&
                                ((DefaultGeographicDescription) g).getGeographicIdentifier()
                                        .getAuthority() != null &&
                                ((DefaultGeographicDescription) g).getGeographicIdentifier()
                                        .getAuthority().getTitle() != null) {
                            metadata.add(ISO19115.GEOGRAPHIC_IDENTIFIER_AUTHORITY_TITLE,
                                    ((DefaultGeographicDescription) g).getGeographicIdentifier()
                                            .getAuthority().getTitle().toString());
                        }

                        for (InternationalString s : ((DefaultGeographicDescription) g)
                                .getGeographicIdentifier().getAuthority().getAlternateTitles()) {
                            metadata.add(ISO19115.GEOGRAPHIC_IDENTIFIER_AUTHORITY_ALTERNATIVE_TITLE,
                                    s.toString());
                        }
                        for (CitationDate cd : ((DefaultGeographicDescription) g)
                                .getGeographicIdentifier().getAuthority().getDates()) {
                            if (cd.getDateType() != null && cd.getDate() != null) {
                                String date = DateUtils.formatDate(cd.getDate());
                                metadata.add(ISO19115.GEOGRAPHIC_IDENTIFIER_AUTHORITY_DATE,
                                        cd.getDateType().name() + " " + date);
                            }
                        }
                    }
                }
            }
        }
    }

    private void getMetaDataDistributionInfo(Metadata metadata, DefaultMetadata defaultMetaData) {
        Distribution distribution = defaultMetaData.getDistributionInfo();
        ArrayList<Format> distributionFormat =
                (ArrayList<Format>) distribution.getDistributionFormats();
        for (Format f : distributionFormat) {
            if (f.getName() != null) {
                metadata.add(ISO19115.DISTRIBUTION_FORMAT_SPECIFICATION_ALTERNATIVE_TITLE,
                        f.getName().toString());
            }
        }
        ArrayList<Distributor> distributorList =
                (ArrayList<Distributor>) distribution.getDistributors();
        for (Distributor d : distributorList) {
            if (d != null && d.getDistributorContact() != null &&
                    d.getDistributorContact().getRole() != null) {
                metadata.add(ISO19115.DISTRIBUTOR_CONTACT, d.getDistributorContact().getRole().name());
            }
            if (d != null && d.getDistributorContact() != null &&
                    d.getDistributorContact().getOrganisationName() != null) {
                metadata.add(ISO19115.DISTRIBUTOR_ORGANIZATION_NAME,
                        d.getDistributorContact().getOrganisationName().toString());
            }
        }
        ArrayList<DigitalTransferOptions> transferOptionsList =
                (ArrayList<DigitalTransferOptions>) distribution.getTransferOptions();
        for (DigitalTransferOptions d : transferOptionsList) {
            ArrayList<OnlineResource> onlineResourceList =
                    (ArrayList<OnlineResource>) d.getOnLines();
            for (OnlineResource or : onlineResourceList) {
                if (or.getLinkage() != null) {
                    metadata.add(ISO19115.TRANSFER_OPTIONS_ONLINE_LINKAGE, or.getLinkage().toString());
                }
                if (or.getProtocol() != null) {
                    metadata.add(ISO19115.TRANSFER_OPTIONS_ONLINE_PROTOCOL, or.getProtocol());
                }
                if (or.getApplicationProfile() != null) {
                    metadata.add(ISO19115.TRANSFER_OPTIONS_ONLINE_PROFILE, or.getApplicationProfile());
                }
                if (or.getName() != null) {
                    metadata.add(ISO19115.TRANSFER_OPTIONS_ONLINE_NAME, or.getName());
                }
                if (or.getDescription() != null) {
                    metadata.add(ISO19115.TRANSFER_OPTIONS_ONLINE_DESCRIPTION,
                            or.getDescription().toString());
                }
                if (or.getFunction() != null) {
                    metadata.add(ISO19115.TRANSFER_OPTIONS_ONLINE_FUNCTION, or.getFunction().name());
                }

            }
        }
    }

    private void getMetaDataDateInfo(Metadata metadata, DefaultMetadata defaultMetaData) {
        ArrayList<CitationDate> citationDateList =
                (ArrayList<CitationDate>) defaultMetaData.getDateInfo();
        for (CitationDate c : citationDateList) {
            if (c.getDateType() != null) {
                String date = DateUtils.formatDate(c.getDate());
                metadata.add(ISO19115.DATE_INFO, c.getDateType().name() + " " + date);
            }
        }
    }

    private void getMetaDataResourceScope(Metadata metadata, DefaultMetadata defaultMetaData) {
        ArrayList<DefaultMetadataScope> scopeList =
                (ArrayList<DefaultMetadataScope>) defaultMetaData.getMetadataScopes();
        for (DefaultMetadataScope d : scopeList) {
            if (d.getResourceScope() != null) {
                metadata.add(ISO19115.METADATA_RESOURCE_SCOPE, d.getResourceScope().name());
            }
        }
    }

    private void getMetaDataParentMetaDataTitle(Metadata metadata,
                                                DefaultMetadata defaultMetaData) {
        Citation parentMetaData = defaultMetaData.getParentMetadata();
        if (parentMetaData != null && parentMetaData.getTitle() != null) {
            metadata.add(ISO19115.PARENT_METADATA_TITLE, parentMetaData.getTitle().toString());
        }
    }

    private void getMetaDataIdetifierCode(Metadata metadata, DefaultMetadata defaultMetaData) {
        Identifier identifier = defaultMetaData.getMetadataIdentifier();
        if (identifier != null) {
            metadata.add(ISO19115.METADATA_IDENTIFIER_CODE, identifier.getCode());
        }
    }

    private void getMetaDataStandard(Metadata metadata, DefaultMetadata defaultMetaData) {
        ArrayList<Citation> citationList =
                (ArrayList<Citation>) defaultMetaData.getMetadataStandards();
        for (Citation c : citationList) {
            if (c.getTitle() != null) {
                metadata.add(ISO19115.METADATA_STANDARD_TITLE, c.getTitle().toString());
            }
            if (c.getEdition() != null) {
                metadata.add(ISO19115.METADATA_STANDARD_EDITION, c.getEdition().toString());
            }
        }
    }
}
