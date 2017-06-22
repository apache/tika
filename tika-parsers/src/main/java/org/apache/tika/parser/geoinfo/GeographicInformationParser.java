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
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Locale;
import java.util.Set;

import org.apache.sis.internal.util.CheckedArrayList;
import org.apache.sis.internal.util.CheckedHashSet;
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
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.XHTMLContentHandler;
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


public class GeographicInformationParser extends AbstractParser{

    private static final Logger LOG = LoggerFactory.getLogger(GeographicInformationParser.class);


    public static final String geoInfoType="text/iso19139+xml";
    private final Set<MediaType> SUPPORTED_TYPES =
            Collections.singleton(MediaType.text("iso19139+xml"));


    @Override
    public Set<MediaType> getSupportedTypes(ParseContext parseContext) {
        return SUPPORTED_TYPES;
    }

    @Override
    public void parse(InputStream inputStream, ContentHandler contentHandler, Metadata metadata, ParseContext parseContext) throws IOException, SAXException, TikaException {
        metadata.set(Metadata.CONTENT_TYPE,geoInfoType);
        DataStore dataStore= null;
        DefaultMetadata defaultMetadata=null;
        XHTMLContentHandler xhtmlContentHandler=new XHTMLContentHandler(contentHandler,metadata);

        TemporaryResources tmp = TikaInputStream.isTikaInputStream(inputStream) ? null
                : new TemporaryResources();
        try {
            TikaInputStream tikaInputStream = TikaInputStream.get(inputStream,tmp);
            File file= tikaInputStream.getFile();
            dataStore = DataStores.open(file);
            defaultMetadata=new DefaultMetadata(dataStore.getMetadata());
            if(defaultMetadata!=null)
                extract(xhtmlContentHandler, metadata, defaultMetadata);

        } catch (UnsupportedStorageException e) {
            throw new TikaException("UnsupportedStorageException",e);
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

    private void extractContent(XHTMLContentHandler xhtmlContentHandler, DefaultMetadata defaultMetadata) throws SAXException{
        xhtmlContentHandler.startDocument();
        xhtmlContentHandler.newline();

        xhtmlContentHandler.newline();
        ArrayList<Identification> identifications= (ArrayList<Identification>) defaultMetadata.getIdentificationInfo();
        for(Identification i:identifications) {
            xhtmlContentHandler.startElement("h1");
            xhtmlContentHandler.characters(i.getCitation().getTitle().toString());
            xhtmlContentHandler.endElement("h1");
            xhtmlContentHandler.newline();

            ArrayList<ResponsibleParty> responsiblePartyArrayList = (ArrayList<ResponsibleParty>) i.getCitation().getCitedResponsibleParties();
            for (ResponsibleParty r : responsiblePartyArrayList) {
                xhtmlContentHandler.startElement("h3");
                xhtmlContentHandler.newline();
                xhtmlContentHandler.characters("CitedResponsiblePartyRole " + r.getRole().toString());
                xhtmlContentHandler.characters("CitedResponsiblePartyName " + r.getIndividualName().toString());
                xhtmlContentHandler.endElement("h3");
                xhtmlContentHandler.newline();
            }

            xhtmlContentHandler.startElement("p");
            xhtmlContentHandler.newline();
            xhtmlContentHandler.characters("IdentificationInfoAbstract " + i.getAbstract().toString());
            xhtmlContentHandler.endElement("p");
            xhtmlContentHandler.newline();
            Collection<Extent> extentList=((DefaultDataIdentification) i).getExtents();
            for(Extent e:extentList){
                ArrayList<GeographicExtent> geoElements= (ArrayList<GeographicExtent>) e.getGeographicElements();
                for(GeographicExtent g:geoElements) {

                    if (g instanceof DefaultGeographicBoundingBox) {
                        xhtmlContentHandler.startElement("tr");
                        xhtmlContentHandler.startElement("td");
                        xhtmlContentHandler.characters("GeographicElementWestBoundLatitude");
                        xhtmlContentHandler.endElement("td");
                        xhtmlContentHandler.startElement("td");
                        xhtmlContentHandler.characters(String.valueOf(((DefaultGeographicBoundingBox) g).getWestBoundLongitude()));
                        xhtmlContentHandler.endElement("td");
                        xhtmlContentHandler.endElement("tr");
                        xhtmlContentHandler.startElement("tr");
                        xhtmlContentHandler.startElement("td");
                        xhtmlContentHandler.characters("GeographicElementEastBoundLatitude");
                        xhtmlContentHandler.endElement("td");
                        xhtmlContentHandler.startElement("td");
                        xhtmlContentHandler.characters(String.valueOf(((DefaultGeographicBoundingBox) g).getEastBoundLongitude()));
                        xhtmlContentHandler.endElement("td");
                        xhtmlContentHandler.endElement("tr");
                        xhtmlContentHandler.startElement("tr");
                        xhtmlContentHandler.startElement("td");
                        xhtmlContentHandler.characters("GeographicElementNorthBoundLatitude");
                        xhtmlContentHandler.endElement("td");
                        xhtmlContentHandler.startElement("td");
                        xhtmlContentHandler.characters(String.valueOf(((DefaultGeographicBoundingBox) g).getNorthBoundLatitude()));
                        xhtmlContentHandler.endElement("td");
                        xhtmlContentHandler.endElement("tr");
                        xhtmlContentHandler.startElement("tr");
                        xhtmlContentHandler.startElement("td");
                        xhtmlContentHandler.characters("GeographicElementSouthBoundLatitude");
                        xhtmlContentHandler.endElement("td");
                        xhtmlContentHandler.startElement("td");
                        xhtmlContentHandler.characters(String.valueOf(((DefaultGeographicBoundingBox) g).getSouthBoundLatitude()));
                        xhtmlContentHandler.endElement("td");
                        xhtmlContentHandler.endElement("tr");
                    }
                }
            }
        }
        xhtmlContentHandler.newline();
        xhtmlContentHandler.endDocument();
    }

    private void getMetaDataCharacterSet(Metadata metadata, DefaultMetadata defaultMetaData){
        CheckedHashSet<Charset> charSetList= (CheckedHashSet<Charset>) defaultMetaData.getCharacterSets();
        for(Charset c:charSetList){
            metadata.add("CharacterSet",c.name());
        }
    }


    private void getMetaDataContact(Metadata metadata, DefaultMetadata defaultMetaData){
        CheckedArrayList<ResponsibleParty> contactSet= (CheckedArrayList<ResponsibleParty>) defaultMetaData.getContacts();
        for(ResponsibleParty rparty:contactSet){
           if(rparty.getRole()!=null)
                metadata.add("ContactRole",rparty.getRole().name());
           if(rparty.getOrganisationName()!=null)
                metadata.add("ContactPartyName-",rparty.getOrganisationName().toString());
        }
    }

    private void getMetaDataIdentificationInfo(Metadata metadata, DefaultMetadata defaultMetaData){
        ArrayList<Identification> identifications= (ArrayList<Identification>) defaultMetaData.getIdentificationInfo();
        for(Identification i:identifications){
            DefaultDataIdentification defaultDataIdentification= (DefaultDataIdentification) i;
            if(i.getCitation()!=null && i.getCitation().getTitle()!=null)
                metadata.add("IdentificationInfoCitationTitle ",i.getCitation().getTitle().toString());

            ArrayList<CitationDate> dateArrayList= (ArrayList<CitationDate>) i.getCitation().getDates();
            for (CitationDate d:dateArrayList){
                if(d.getDateType()!=null)
                    metadata.add("CitationDate ",d.getDateType().name()+"-->"+d.getDate());
            }
            ArrayList<ResponsibleParty> responsiblePartyArrayList= (ArrayList<ResponsibleParty>) i.getCitation().getCitedResponsibleParties();
            for(ResponsibleParty r:responsiblePartyArrayList){
                if(r.getRole()!=null)
                    metadata.add("CitedResponsiblePartyRole ",r.getRole().toString());
                if(r.getIndividualName()!=null)
                    metadata.add("CitedResponsiblePartyName ",r.getIndividualName().toString());
                if(r.getOrganisationName()!=null)
                    metadata.add("CitedResponsiblePartyOrganizationName ", r.getOrganisationName().toString());
                if(r.getPositionName()!=null)
                    metadata.add("CitedResponsiblePartyPositionName ",r.getPositionName().toString());

                if(r.getContactInfo()!=null){
                    for(String s:r.getContactInfo().getAddress().getElectronicMailAddresses()) {
                        metadata.add("CitedResponsiblePartyEMail ",s.toString());
                    }
                }
            }
            if(i.getAbstract()!=null)
                metadata.add("IdentificationInfoAbstract ",i.getAbstract().toString());
            for(Progress p:i.getStatus()) {
                metadata.add("IdentificationInfoStatus ",p.name());
            }
            ArrayList<Format> formatArrayList= (ArrayList<Format>) i.getResourceFormats();
            for(Format f:formatArrayList){
                if(f.getName()!=null)
                    metadata.add("ResourceFormatSpecificationAlternativeTitle ",f.getName().toString());
            }
            CheckedHashSet<Locale> localeCheckedHashSet= (CheckedHashSet<Locale>) defaultDataIdentification.getLanguages();
            for(Locale l:localeCheckedHashSet){
                metadata.add("IdentificationInfoLanguage-->",l.getDisplayLanguage(Locale.ENGLISH));
            }
            CodeListSet<TopicCategory> categoryList= (CodeListSet<TopicCategory>) defaultDataIdentification.getTopicCategories();
            for(TopicCategory t:categoryList){
                metadata.add("IdentificationInfoTopicCategory-->",t.name());
            }
            ArrayList<Keywords> keywordList= (ArrayList<Keywords>) i.getDescriptiveKeywords();
            int j=1;
            for(Keywords k:keywordList){
                j++;
                ArrayList<InternationalString> stringList= (ArrayList<InternationalString>) k.getKeywords();
                for(InternationalString s:stringList){
                    metadata.add("Keywords "+j ,s.toString());
                }
                if(k.getType()!=null)
                    metadata.add("KeywordsType "+j,k.getType().name());
                if(k.getThesaurusName()!=null && k.getThesaurusName().getTitle()!=null)
                    metadata.add("ThesaurusNameTitle "+j,k.getThesaurusName().getTitle().toString());
                if(k.getThesaurusName()!=null && k.getThesaurusName().getAlternateTitles()!=null)
                    metadata.add("ThesaurusNameAlternativeTitle "+j,k.getThesaurusName().getAlternateTitles().toString());

                ArrayList<CitationDate>citationDates= (ArrayList<CitationDate>) k.getThesaurusName().getDates();
                for(CitationDate cd:citationDates) {
                   if(cd.getDateType()!=null)
                        metadata.add("ThesaurusNameDate ",cd.getDateType().name() +"-->" + cd.getDate());
                }
            }
            ArrayList<DefaultLegalConstraints> constraintList= (ArrayList<DefaultLegalConstraints>) i.getResourceConstraints();

            for(DefaultLegalConstraints c:constraintList){
                for(Restriction r:c.getAccessConstraints()){
                    metadata.add("AccessContraints ",r.name());
                }
                for(InternationalString s:c.getOtherConstraints()){
                    metadata.add("OtherConstraints ",s.toString());
                }
                for(Restriction r:c.getUseConstraints()) {
                    metadata.add("UserConstraints ",r.name());
                }
              
            }
            Collection<Extent> extentList=((DefaultDataIdentification) i).getExtents();
            for(Extent e:extentList){
                ArrayList<GeographicExtent> geoElements= (ArrayList<GeographicExtent>) e.getGeographicElements();
                for(GeographicExtent g:geoElements){

                    if(g instanceof DefaultGeographicDescription){
                        if(((DefaultGeographicDescription) g).getGeographicIdentifier()!=null && ((DefaultGeographicDescription) g).getGeographicIdentifier().getCode()!=null )
                            metadata.add("GeographicIdentifierCode ",((DefaultGeographicDescription) g).getGeographicIdentifier().getCode().toString());
                        if(((DefaultGeographicDescription) g).getGeographicIdentifier()!=null && ((DefaultGeographicDescription) g).getGeographicIdentifier().getAuthority()!=null && ((DefaultGeographicDescription) g).getGeographicIdentifier().getAuthority().getTitle()!=null )
                        metadata.add("GeographicIdentifierAuthorityTitle ",((DefaultGeographicDescription) g).getGeographicIdentifier().getAuthority().getTitle().toString());

                        for(InternationalString s:((DefaultGeographicDescription) g).getGeographicIdentifier().getAuthority().getAlternateTitles()) {
                            metadata.add("GeographicIdentifierAuthorityAlternativeTitle ",s.toString());
                        }
                        for(CitationDate cd:((DefaultGeographicDescription) g).getGeographicIdentifier().getAuthority().getDates()){
                            if(cd.getDateType()!=null && cd.getDate()!=null)
                                metadata.add("GeographicIdentifierAuthorityDate ",cd.getDateType().name()+" "+cd.getDate().toString());
                        }
                    }
                }
            }
        }
    }

    private void getMetaDataDistributionInfo(Metadata metadata, DefaultMetadata defaultMetaData){
        Distribution distribution=defaultMetaData.getDistributionInfo();
        ArrayList<Format> distributionFormat= (ArrayList<Format>) distribution.getDistributionFormats();
        for(Format f:distributionFormat){
            if(f.getName()!=null)
                metadata.add("DistributionFormatSpecificationAlternativeTitle ",f.getName().toString());
        }
        ArrayList<Distributor> distributorList= (ArrayList<Distributor>) distribution.getDistributors();
        for(Distributor d:distributorList){
            if(d!=null && d.getDistributorContact()!=null && d.getDistributorContact().getRole()!=null)
                metadata.add("Distributor Contact ",d.getDistributorContact().getRole().name());
            if(d!=null && d.getDistributorContact()!=null && d.getDistributorContact().getOrganisationName()!=null)
                metadata.add("Distributor Organization Name ",d.getDistributorContact().getOrganisationName().toString());
        }
        ArrayList<DigitalTransferOptions> transferOptionsList= (ArrayList<DigitalTransferOptions>) distribution.getTransferOptions();
        for(DigitalTransferOptions d:transferOptionsList){
            ArrayList<OnlineResource> onlineResourceList= (ArrayList<OnlineResource>) d.getOnLines();
            for(OnlineResource or:onlineResourceList){
                if(or.getLinkage()!=null)
                    metadata.add("TransferOptionsOnlineLinkage ",or.getLinkage().toString());
                if(or.getProtocol()!=null)
                    metadata.add("TransferOptionsOnlineProtocol ",or.getProtocol());
                if(or.getApplicationProfile()!=null)
                    metadata.add("TransferOptionsOnlineProfile ",or.getApplicationProfile());
                if(or.getName()!=null)
                    metadata.add("TransferOptionsOnlineName ",or.getName());
                if(or.getDescription()!=null)
                    metadata.add("TransferOptionsOnlineDescription ",or.getDescription().toString());
                if(or.getFunction()!=null)
                    metadata.add("TransferOptionsOnlineFunction ",or.getFunction().name());

            }
        }
    }

    private void getMetaDataDateInfo(Metadata metadata, DefaultMetadata defaultMetaData){
        ArrayList<CitationDate> citationDateList= (ArrayList<CitationDate>) defaultMetaData.getDateInfo();
        for(CitationDate c:citationDateList){
            if(c.getDateType()!=null)
                metadata.add("DateInfo ",c.getDateType().name()+" "+c.getDate());
        }
    }

    private void getMetaDataResourceScope(Metadata metadata, DefaultMetadata defaultMetaData){
        ArrayList<DefaultMetadataScope> scopeList= (ArrayList<DefaultMetadataScope>) defaultMetaData.getMetadataScopes();
        for(DefaultMetadataScope d:scopeList){
            if(d.getResourceScope()!=null)
                metadata.add("MetaDataResourceScope ",d.getResourceScope().name());
        }
    }

    private void getMetaDataParentMetaDataTitle(Metadata metadata, DefaultMetadata defaultMetaData){
        Citation parentMetaData=defaultMetaData.getParentMetadata();
        if(parentMetaData!=null && parentMetaData.getTitle()!=null)
            metadata.add("ParentMetaDataTitle",parentMetaData.getTitle().toString());
    }

    private void getMetaDataIdetifierCode(Metadata metadata, DefaultMetadata defaultMetaData){
        Identifier identifier= defaultMetaData.getMetadataIdentifier();
        if(identifier!=null)
            metadata.add("MetaDataIdentifierCode",identifier.getCode());
    }

    private void getMetaDataStandard(Metadata metadata, DefaultMetadata defaultMetaData){
        ArrayList<Citation> citationList= (ArrayList<Citation>) defaultMetaData.getMetadataStandards();
        for(Citation c:citationList){
            if(c.getTitle()!=null)
                metadata.add("MetaDataStandardTitle ",c.getTitle().toString());
            if(c.getEdition()!=null)
                metadata.add("MetaDataStandardEdition ",c.getEdition().toString());
        }
    }
}
