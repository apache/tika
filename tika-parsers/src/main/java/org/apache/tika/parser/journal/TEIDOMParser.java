/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.tika.parser.journal;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

public class TEIDOMParser {

    public TEIDOMParser() {
    }

    public Metadata parse(String source, ParseContext parseContext) throws TikaException, SAXException, IOException {

        Document root = parseContext.getDocumentBuilder().parse(
                new ByteArrayInputStream(source.getBytes(StandardCharsets.UTF_8))
        );
        Metadata metadata = new Metadata();
        createGrobidMetadata(source, root.getDocumentElement(), metadata);
        return metadata;
    }

    private void createGrobidMetadata(String source, Element root,
                                      Metadata metadata) {
        if (root != null) {

            Node text = getFirstChild(root.getChildNodes(), "text");
            if (text != null) {
                parseText(text, metadata);
            }
            Node teiHeader = getFirstChild(root.getChildNodes(), "teiHeader");
            Node fileDesc = getFirstChild(teiHeader.getChildNodes(), "fileDesc");
            if (fileDesc != null) {
                parseFileDesc(fileDesc, metadata);

            }
            Node profileDesc = getFirstChild(teiHeader.getChildNodes(), "profileDesc");
            if (profileDesc != null) {
                parseProfileDesc(profileDesc, metadata);
            }

        }

        addStaticMet(source, root, metadata);
    }

    private void addStaticMet(String source, Element obj, Metadata metadata) {
        metadata.add("Class", Metadata.class.getName());
        //no longer available after we got rid of json.org's and its .toJSONObject()
//        metadata.add("TEIJSONSource", obj.toString());
        metadata.add("TEIXMLSource", source);
    }

    private void parseText(Node text, Metadata metadata) {
        String lang = getFirstAttribute(text, "xml", "lang");
        if (lang != null) {
            metadata.add("Language", lang);
        }
    }

    private void parseFileDesc(Node fileDesc, Metadata metadata) {
        Node titleStmt = getFirstChild(fileDesc.getChildNodes(), "titleStmt");

        if (titleStmt != null) {
            parseTitleStmt(titleStmt, metadata);
        }

        Node sourceDesc = getFirstChild(fileDesc.getChildNodes(), "sourceDesc");
        if (sourceDesc != null) {
            parseSourceDesc(sourceDesc, metadata);
        }
    }

    private void parseTitleStmt(Node titleStmt, Metadata metadata) {
        Node title = getFirstChild(titleStmt.getChildNodes(), "title");
        if (title != null) {
            String titleText = title.getTextContent();
            if (titleText != null) {
                metadata.add("Title", titleText);
            }
        }
    }

    private void parseSourceDesc(Node sourceDesc, Metadata metadata) {
        Node biblStruct = getFirstChild(sourceDesc.getChildNodes(), "biblStruct");
        if (biblStruct != null) {
            parseBiblStruct(biblStruct, metadata);
        }
    }

    private void parseBiblStruct(Node biblStruct, Metadata metadata) {

        Node analytic = getFirstChild(biblStruct.getChildNodes(), "analytic");
        if (analytic != null) {
            List<Node> authorNodes = getChildNodes(analytic.getChildNodes(), "author");
            List<Author> authorList = new ArrayList<>();
            for (Node authorNode : authorNodes) {
                parseAuthor(authorNode, authorList);
            }

            metadata.add("Address", getMetadataAddresses(authorList));
            metadata.add("Affiliation", getMetadataAffiliations(authorList));
            metadata.add("Authors", getMetadataAuthors(authorList));
            metadata.add("FullAffiliations",
                    getMetadataFullAffiliations(authorList));


        } else {
            metadata.add("Error", "Unable to parse: no analytic section in JSON");
        }

    }

    private String getMetadataFullAffiliations(List<Author> authorList) {
        List<Affiliation> unique = new ArrayList<Affiliation>();
        StringBuilder metAffils = new StringBuilder();

        for (Author a : authorList) {
            for (Affiliation af : a.getAffiliations()) {
                if (!unique.contains(af)) {
                    unique.add(af);
                }
            }
        }
        metAffils.append("[");
        for (Affiliation af : unique) {
            metAffils.append(af.toString());
            metAffils.append(",");
        }
        metAffils.append(metAffils.deleteCharAt(metAffils.length() - 1));
        metAffils.append("]");
        return metAffils.toString();
    }

    private String getMetadataAuthors(List<Author> authorList) {
        // generates Chris A. Mattmann 1, 2 Daniel J. Crichton 1 Nenad Medvidovic 2
        // Steve Hughes 1
        List<Affiliation> unique = new ArrayList<Affiliation>();
        StringBuilder metAuthors = new StringBuilder();

        for (Author a : authorList) {
            for (Affiliation af : a.getAffiliations()) {
                if (!unique.contains(af)) {
                    unique.add(af);
                }
            }
        }

        for (Author a : authorList) {
            metAuthors.append(printOrBlank(a.getFirstName()));
            metAuthors.append(printOrBlank(a.getMiddleName()));
            metAuthors.append(printOrBlank(a.getSurName()));

            StringBuilder affilBuilder = new StringBuilder();
            for (int idx = 0; idx < unique.size(); idx++) {
                Affiliation af = unique.get(idx);
                if (a.getAffiliations().contains(af)) {
                    affilBuilder.append((idx + 1));
                    affilBuilder.append(",");
                }
            }

            if (affilBuilder.length() > 0)
                affilBuilder.deleteCharAt(affilBuilder.length() - 1);

            metAuthors.append(affilBuilder.toString());
            metAuthors.append(" ");
        }

        return metAuthors.toString();
    }

    private String getMetadataAffiliations(List<Author> authorList) {
        // generates 1 Jet Propulsion Laboratory California Institute of Technology
        // ; 2 Computer Science Department University of Southern California
        List<Affiliation> unique = new ArrayList<Affiliation>();
        StringBuilder metAffil = new StringBuilder();

        for (Author a : authorList) {
            for (Affiliation af : a.getAffiliations()) {
                if (!unique.contains(af)) {
                    unique.add(af);
                }
            }
        }

        int count = 1;
        for (Affiliation a : unique) {
            metAffil.append(count);
            metAffil.append(" ");
            metAffil.append(a.getOrgName().toString());
            metAffil.deleteCharAt(metAffil.length() - 1);
            metAffil.append("; ");
            count++;
        }

        if (count > 1) {
            metAffil.deleteCharAt(metAffil.length() - 1);
            metAffil.deleteCharAt(metAffil.length() - 1);
        }

        return metAffil.toString();
    }

    private String getMetadataAddresses(List<Author> authorList) {
        // generates: "Pasadena, CA 91109, USA Los Angeles, CA 90089, USA",
        List<Address> unique = new ArrayList<Address>();
        StringBuilder metAddress = new StringBuilder();

        for (Author a : authorList) {
            for (Affiliation af : a.getAffiliations()) {
                if (!unique.contains(af.getAddress())) {
                    unique.add(af.getAddress());
                }
            }
        }

        for (Address ad : unique) {
            metAddress.append(ad.toString());
            metAddress.append(" ");
        }

        return metAddress.toString();
    }

    private void parseAuthor(Node authorNode, List<Author> authorList) {
        Author author = new Author();
        Node persName = getFirstChild(authorNode.getChildNodes(), "persName");
        if (persName != null) {
            List<Node> forenames = getChildNodes(persName.getChildNodes(), "forename");
            for (Node forenameNode : forenames) {
                parseNamePart(forenameNode, author);
            }
            Node surnameNode = getFirstChild(persName.getChildNodes(), "surname");
            if (surnameNode != null) {
                String surnameContent = surnameNode.getTextContent();
                if (surnameContent != null) {
                    author.setSurName(surnameContent);
                }
            }
        }
        List<Node> affiliationNodes = getChildNodes(authorNode.getChildNodes(), "affiliation");
        for (Node affiliationNode : affiliationNodes) {
            parseOneAffiliation(affiliationNode, author);
        }


        authorList.add(author);
    }

    private void parseNamePart(Node namePart, Author author) {
        String type = getFirstAttribute(namePart, null, "type");
        String content = namePart.getTextContent();
        if (type != null && content != null) {

            if (type.equals("first")) {
                author.setFirstName(content);
            }

            if (type.equals("middle")) {
                author.setMiddleName(content);
            }
        }
    }

    private void parseOneAffiliation(Node affiliationNode, Author author) {

        Affiliation affiliation = new Affiliation();
        Node address = getFirstChild(affiliationNode.getChildNodes(), "address");
        if (address != null) {
            parseAddress(address, affiliation);
        }

        List<Node> orgNameNodes = getChildNodes(affiliationNode.getChildNodes(), "orgName");
        OrgName orgName = new OrgName();
        for (Node orgNameNode : orgNameNodes) {
            parseOrgName(orgNameNode, orgName);
        }
        affiliation.setOrgName(orgName);

        author.getAffiliations().add(affiliation);
    }

    private void parseAddress(Node addressNode, Affiliation affiliation) {
        Address address = new Address();
        Node region = getFirstChild(addressNode.getChildNodes(), "region");
        if (region != null && region.getTextContent() != null) {
            address.setRegion(region.getTextContent());
        }
        Node postCode = getFirstChild(addressNode.getChildNodes(), "postCode");
        if (postCode != null && postCode.getTextContent() != null) {
            address.setPostCode(postCode.getTextContent());
        }
        Node settlementNode = getFirstChild(addressNode.getChildNodes(), "settlement");
        if (settlementNode != null && settlementNode.getTextContent() != null) {
            address.setSettlment(settlementNode.getTextContent());
        }

        Node countryNode = getFirstChild(addressNode.getChildNodes(), "country");
        if (countryNode != null) {
            Country country = new Country();
            String key = getFirstAttribute(countryNode, null, "key");
            if (key != null) {
                country.setKey(key);
            }
            String content = countryNode.getTextContent();
            if (content != null) {
                country.setContent(content);
            }
            address.setCountry(country);
        }

        affiliation.setAddress(address);
    }

    private void parseOrgName(Node orgNode, OrgName orgName) {
        OrgTypeName typeName = new OrgTypeName();
        String orgContent = orgNode.getTextContent();
        if (orgContent != null) {
            typeName.setName(orgContent);
        }
        String orgType = getFirstAttribute(orgNode, null, "type");
        if (orgType != null) {
            typeName.setType(orgType);
        }

        orgName.getTypeNames().add(typeName);
    }

    private void parseProfileDesc(Node profileDesc, Metadata metadata) {
        Node abstractNode = getFirstChild(profileDesc.getChildNodes(), "abstract");
        if (abstractNode != null) {
            Node pNode = getFirstChild(abstractNode.getChildNodes(), "p");
            if (pNode != null) {
                metadata.add("Abstract", pNode.getTextContent());
            }
        }

        Node textClassNode = getFirstChild(profileDesc.getChildNodes(), "textClass");
        if (textClassNode != null) {
            Node keywordsNode = getFirstChild(textClassNode.getChildNodes(), "keywords");
            if (keywordsNode != null) {
                List<Node> terms = getChildNodes(keywordsNode.getChildNodes(), "term");
                if (terms.size() == 0) {
                    // test AJ15.pdf
                    metadata.add("Keyword", keywordsNode.getTextContent());
                } else {
                    for (Node term : terms) {
                        metadata.add("Keyword", term.getTextContent());
                    }
                }

            }
        }

    }

    private String printOrBlank(String val) {
        if (val != null && !val.equals("")) {
            return val + " ";
        } else
            return " ";
    }

    class Author {

        private String surName;

        private String middleName;

        private String firstName;

        private List<Affiliation> affiliations;

        public Author() {
            this.surName = null;
            this.middleName = null;
            this.firstName = null;
            this.affiliations = new ArrayList<Affiliation>();
        }

        /**
         * @return the surName
         */
        public String getSurName() {
            return surName;
        }

        /**
         * @param surName the surName to set
         */
        public void setSurName(String surName) {
            this.surName = surName;
        }

        /**
         * @return the middleName
         */
        public String getMiddleName() {
            return middleName;
        }

        /**
         * @param middleName the middleName to set
         */
        public void setMiddleName(String middleName) {
            this.middleName = middleName;
        }

        /**
         * @return the firstName
         */
        public String getFirstName() {
            return firstName;
        }

        /**
         * @param firstName the firstName to set
         */
        public void setFirstName(String firstName) {
            this.firstName = firstName;
        }

        /**
         * @return the affiliations
         */
        public List<Affiliation> getAffiliations() {
            return affiliations;
        }

        /**
         * @param affiliations the affiliations to set
         */
        public void setAffiliations(List<Affiliation> affiliations) {
            this.affiliations = affiliations;
        }

        /*
         * (non-Javadoc)
         *
         * @see java.lang.Object#toString()
         */
        @Override
        public String toString() {
            return "Author [surName=" + surName + ", middleName=" + middleName != null ? middleName
                    : "" + ", firstName=" + firstName + ", affiliations=" + affiliations
                    + "]";
        }

    }

    class Affiliation {

        private OrgName orgName;

        private Address address;

        public Affiliation() {
            this.orgName = new OrgName();
            this.address = new Address();
        }

        /**
         * @return the orgName
         */
        public OrgName getOrgName() {
            return orgName;
        }

        /**
         * @param orgName the orgName to set
         */
        public void setOrgName(OrgName orgName) {
            this.orgName = orgName;
        }

        /**
         * @return the address
         */
        public Address getAddress() {
            return address;
        }

        /**
         * @param address the address to set
         */
        public void setAddress(Address address) {
            this.address = address;
        }

        /*
         * (non-Javadoc)
         *
         * @see java.lang.Object#equals(java.lang.Object)
         */
        @Override
        public boolean equals(Object obj) {
            Affiliation otherA = (Affiliation) obj;
            return this.getAddress().equals(otherA.getAddress())
                    && this.getOrgName().equals(otherA.getOrgName());

        }

        /*
         * (non-Javadoc)
         *
         * @see java.lang.Object#toString()
         */
        @Override
        public String toString() {
            return "Affiliation {orgName=" + orgName + ", address=" + address + "}";
        }

    }

    class OrgName {
        private List<OrgTypeName> typeNames;

        public OrgName() {
            this.typeNames = new ArrayList<OrgTypeName>();
        }

        /**
         * @return the typeNames
         */
        public List<OrgTypeName> getTypeNames() {
            return typeNames;
        }

        /**
         * @param typeNames the typeNames to set
         */
        public void setTypeNames(List<OrgTypeName> typeNames) {
            this.typeNames = typeNames;
        }

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Object#toString()
     */

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();
            for (OrgTypeName on : this.typeNames) {
                builder.append(on.getName());
                builder.append(" ");
            }
            return builder.toString();
        }

        /*
         * (non-Javadoc)
         *
         * @see java.lang.Object#equals(java.lang.Object)
         */
        @Override
        public boolean equals(Object obj) {
            OrgName otherA = (OrgName) obj;

            if (otherA.getTypeNames() != null) {
                if (this.typeNames == null) {
                    return false;
                } else {
                    return this.typeNames.size() == otherA.getTypeNames().size();
                }
            } else {
                if (this.typeNames == null) {
                    return true;
                } else
                    return false;
            }

        }

    }

    class OrgTypeName {
        private String name;
        private String type;

        public OrgTypeName() {
            this.name = null;
            this.type = null;
        }

        /**
         * @return the name
         */
        public String getName() {
            return name;
        }

        /**
         * @param name the name to set
         */
        public void setName(String name) {
            this.name = name;
        }

        /**
         * @return the type
         */
        public String getType() {
            return type;
        }

        /**
         * @param type the type to set
         */
        public void setType(String type) {
            this.type = type;
        }

        /*
         * (non-Javadoc)
         *
         * @see java.lang.Object#equals(java.lang.Object)
         */
        @Override
        public boolean equals(Object obj) {
            OrgTypeName otherOrgName = (OrgTypeName) obj;
            return this.type.equals(otherOrgName.getType())
                    && this.name.equals(otherOrgName.getName());
        }

    }

    private class Address {

        private String region;
        private String postCode;
        private String settlment;
        private Country country;

        public Address() {
            this.region = null;
            this.postCode = null;
            this.settlment = null;
            this.country = new Country();
        }

        /**
         * @return the region
         */
        public String getRegion() {
            return region;
        }

        /**
         * @param region the region to set
         */
        public void setRegion(String region) {
            this.region = region;
        }

        /**
         * @return the postCode
         */
        public String getPostCode() {
            return postCode;
        }

        /**
         * @param postCode the postCode to set
         */
        public void setPostCode(String postCode) {
            this.postCode = postCode;
        }

        /**
         * @return the settlment
         */
        public String getSettlment() {
            return settlment;
        }

        /**
         * @param settlment the settlment to set
         */
        public void setSettlment(String settlment) {
            this.settlment = settlment;
        }

        /**
         * @return the country
         */
        public Country getCountry() {
            return country;
        }

        /**
         * @param country the country to set
         */
        public void setCountry(Country country) {
            this.country = country;
        }

        /*
         * (non-Javadoc)
         *
         * @see java.lang.Object#equals(java.lang.Object)
         */
        @Override
        public boolean equals(Object obj) {
            Address otherA = (Address) obj;
            if (this.settlment == null) {
                return otherA.getSettlment() == null;
            } else if (this.country == null) {
                return otherA.getCountry() == null;
            } else if (this.postCode == null) {
                return otherA.getPostCode() == null;
            } else if (this.region == null) {
                return otherA.getRegion() == null;
            }

            return this.settlment.equals(otherA.getSettlment())
                    && this.country.equals(otherA.getCountry())
                    && this.postCode.equals(otherA.getPostCode())
                    && this.region.equals(otherA.getRegion());
        }

        /*
         * (non-Javadoc)
         *
         * @see java.lang.Object#toString()
         */
        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();
            builder.append(settlment);
            builder.append(", ");
            builder.append(region);
            builder.append(" ");
            builder.append(postCode);
            builder.append(" ");
            builder.append(country.getContent());
            return builder.toString();
        }
    }

    private class Country {
        private String key;
        private String content;

        public Country() {
            this.key = null;
            this.content = null;
        }

        /**
         * @return the key
         */
        public String getKey() {
            return key;
        }

        /**
         * @param key the key to set
         */
        public void setKey(String key) {
            this.key = key;
        }

        /**
         * @return the content
         */
        public String getContent() {
            return content;
        }

        /**
         * @param content the content to set
         */
        public void setContent(String content) {
            this.content = content;
        }

        /*
         * (non-Javadoc)
         *
         * @see java.lang.Object#equals(java.lang.Object)
         */
        @Override
        public boolean equals(Object obj) {
            Country otherC = (Country) obj;

            if (this.key == null) {
                if (otherC.getKey() != null) {
                    return false;
                } else {
                    if (this.content == null) {
                        if (otherC.getContent() != null) {
                            return false;
                        } else {
                            return true;
                        }
                    } else {
                        return content.equals(otherC.getContent());
                    }
                }
            } else {
                if (this.content == null) {
                    if (otherC.getContent() != null) {
                        return false;
                    } else {
                        return this.key.equals(otherC.getKey());
                    }
                } else {
                    return this.key.equals(otherC.getKey())
                            && this.content.equals(otherC.getContent());
                }
            }
        }
    }

    //returns first child with this name, null otherwise
    private static Node getFirstChild(NodeList childNodes, String name) {
        for (int i = 0; i < childNodes.getLength(); i++) {
            Node n = childNodes.item(i);
            if (n.getNodeName().equals(name)) {
                return n;
            }
        }
        return null;
    }

    private static String getFirstAttribute(Node node, String ns, String name) {
        if (node.hasAttributes()) {
            NamedNodeMap attrs = node.getAttributes();
            for (int i = 0; i < attrs.getLength(); i++) {
                Node attr = attrs.item(i);
                if (attr.getLocalName().equals(name)) {
                    return attr.getNodeValue();
                }
            }
        }
        return null;
    }

    private static List<Node> getChildNodes(NodeList childNodes, String localName) {
        List<Node> ret = new ArrayList<>();
        for (int i = 0; i < childNodes.getLength(); i++) {
            Node child = childNodes.item(i);
            if (child.getLocalName() != null && child.getLocalName().equals(localName)) {
                ret.add(child);
            }
        }
        return ret;
    }

}
