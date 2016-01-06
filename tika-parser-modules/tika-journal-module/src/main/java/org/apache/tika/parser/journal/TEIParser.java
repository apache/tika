/**
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

package org.apache.tika.parser.journal;

import java.util.ArrayList;
import java.util.List;

import org.apache.tika.metadata.Metadata;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.XML;

public class TEIParser {

  public TEIParser() {
  }

  public Metadata parse(String source) {
    JSONObject obj = XML.toJSONObject(source);
    Metadata metadata = new Metadata();
    createGrobidMetadata(source, obj, metadata);
    return metadata;
  }

  private void createGrobidMetadata(String source, JSONObject obj,
      Metadata metadata) {
    if (obj != null) {
      JSONObject teiHeader = obj.getJSONObject("TEI")
          .getJSONObject("teiHeader");
      if (teiHeader.has("text")) {
        parseText(teiHeader.getJSONObject("text"), metadata);
      }

      if (teiHeader.has("fileDesc")) {
        parseFileDesc(teiHeader.getJSONObject("fileDesc"), metadata);

      }
      if (teiHeader.has("profileDesc")) {
        parseProfileDesc(teiHeader.getJSONObject("profileDesc"), metadata);
      }
    }

    addStaticMet(source, obj, metadata);
  }

  private void addStaticMet(String source, JSONObject obj, Metadata metadata) {
    metadata.add("Class", Metadata.class.getName());
    metadata.add("TEIJSONSource", obj.toString());
    metadata.add("TEIXMLSource", source);
  }

  private void parseText(JSONObject text, Metadata metadata) {
    if (text.has("xml:lang")) {
      metadata.add("Language", text.getString("xml:lang"));
    }
  }

  private void parseFileDesc(JSONObject fileDesc, Metadata metadata) {
    if (fileDesc.has("titleStmt")) {
      parseTitleStmt(fileDesc.getJSONObject("titleStmt"), metadata);
    }

    if (fileDesc.has("sourceDesc")) {
      parseSourceDesc(fileDesc.getJSONObject("sourceDesc"), metadata);
    }
  }

  private void parseTitleStmt(JSONObject titleStmt, Metadata metadata) {
    if (titleStmt.has("title")) {
      JSONObject title = titleStmt.getJSONObject("title");
      if (title.has("content")) {
        metadata.add("Title", title.getString("content"));
      }
    }
  }

  private void parseSourceDesc(JSONObject sourceDesc, Metadata metadata) {
    if (sourceDesc.has("biblStruct")) {
      parseBiblStruct(sourceDesc.getJSONObject("biblStruct"), metadata);
    }
  }

  private void parseBiblStruct(JSONObject biblStruct, Metadata metadata) {
    if (biblStruct.has("analytic")
        && biblStruct.get("analytic") instanceof JSONObject) {
      JSONObject analytic = biblStruct.getJSONObject("analytic");
      if (analytic.has("author")) {
        Object authorObj = analytic.get("author");

        List<Author> authorList = new ArrayList<Author>();
        if (authorObj instanceof JSONObject) {
          parseAuthor((JSONObject) authorObj, authorList);
        } else if (authorObj instanceof JSONArray) {
          JSONArray authors = (JSONArray) authorObj;
          if (authors.length() > 0) {
            for (int i = 0; i < authors.length(); i++) {
              JSONObject author = authors.getJSONObject(i);
              parseAuthor(author, authorList);
            }
          }

          metadata.add("Address", getMetadataAddresses(authorList));
          metadata.add("Affiliation", getMetadataAffiliations(authorList));
          metadata.add("Authors", getMetadataAuthors(authorList));
          metadata.add("FullAffiliations",
              getMetadataFullAffiliations(authorList));
        }

      }
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

  private void parseAuthor(JSONObject authorObj, List<Author> authorList) {
    Author author = new Author();

    if (authorObj.has("persName")) {
      JSONObject persName = authorObj.getJSONObject("persName");

      if (persName.has("forename")) {

        Object foreNameObj = persName.get("forename");

        if (foreNameObj instanceof JSONObject) {
          parseNamePart((JSONObject) foreNameObj, author);
        } else if (foreNameObj instanceof JSONArray) {
          JSONArray foreName = persName.getJSONArray("forename");

          if (foreName.length() > 0) {
            for (int i = 0; i < foreName.length(); i++) {
              JSONObject namePart = foreName.getJSONObject(i);
              parseNamePart(namePart, author);
            }
          }
        }
      }

      if (persName.has("surname")) {
        author.setSurName(persName.getString("surname"));
      }

      if (authorObj.has("affiliation")) {
        parseAffiliation(authorObj.get("affiliation"), author);
      }

    }

    authorList.add(author);
  }

  private void parseNamePart(JSONObject namePart, Author author) {
    if (namePart.has("type") && namePart.has("content")) {
      String type = namePart.getString("type");
      String content = namePart.getString("content");

      if (type.equals("first")) {
        author.setFirstName(content);
      }

      if (type.equals("middle")) {
        author.setMiddleName(content);
      }
    }
  }

  private void parseAffiliation(Object affiliationJSON, Author author) {
    if (affiliationJSON instanceof JSONObject) {
      parseOneAffiliation((JSONObject) affiliationJSON, author);
    } else if (affiliationJSON instanceof JSONArray) {
      JSONArray affiliationArray = (JSONArray) affiliationJSON;
      if (affiliationArray != null && affiliationArray.length() > 0) {
        for (int i = 0; i < affiliationArray.length(); i++) {
          JSONObject affiliationObj = affiliationArray.getJSONObject(i);
          parseOneAffiliation(affiliationObj, author);
        }
      }
    }
  }

  private void parseOneAffiliation(JSONObject affiliationObj, Author author) {

    Affiliation affiliation = new Affiliation();
    if (affiliationObj.has("address")) {
      parseAddress(affiliationObj.getJSONObject("address"), affiliation);
    }

    if (affiliationObj.has("orgName")) {
      OrgName orgName = new OrgName();
      Object orgObject = affiliationObj.get("orgName");
      if (orgObject instanceof JSONObject) {
        parseOrgName((JSONObject) orgObject, orgName);
      } else if (orgObject instanceof JSONArray) {
        JSONArray orgNames = (JSONArray) orgObject;
        if (orgNames != null && orgNames.length() > 0) {
          for (int i = 0; i < orgNames.length(); i++) {
            parseOrgName(orgNames.getJSONObject(i), orgName);
          }
        }

        affiliation.setOrgName(orgName);
      }

    }

    author.getAffiliations().add(affiliation);
  }

  private void parseAddress(JSONObject addressObj, Affiliation affiliation) {
    Address address = new Address();

    if (addressObj.has("region")) {
      address.setRegion(addressObj.getString("region"));
    }

    if (addressObj.has("postCode")) {
      address.setPostCode(JSONObject.valueToString(addressObj.get("postCode")));
    }

    if (addressObj.has("settlement")) {
      address.setSettlment(addressObj.getString("settlement"));
    }

    if (addressObj.has("country")) {
      Country country = new Country();
      Object countryObj = addressObj.get("country");

      if (countryObj instanceof JSONObject) {
        JSONObject countryJson = addressObj.getJSONObject("country");

        if (countryJson.has("content")) {
          country.setContent(countryJson.getString("content"));
        }

        if (countryJson.has("key")) {
          country.setKey(countryJson.getString("key"));
        }
      } else if (countryObj instanceof String) {
        country.setContent((String) countryObj);
      }
      address.setCountry(country);
    }

    affiliation.setAddress(address);
  }

  private void parseOrgName(JSONObject orgObj, OrgName orgName) {
    OrgTypeName typeName = new OrgTypeName();
    if (orgObj.has("content")) {
      typeName.setName(orgObj.getString("content"));
    }

    if (orgObj.has("type")) {
      typeName.setType(orgObj.getString("type"));
    }

    orgName.getTypeNames().add(typeName);
  }

  private void parseProfileDesc(JSONObject profileDesc, Metadata metadata) {
    if (profileDesc.has("abstract")) {
      if (profileDesc.has("p")) {
        metadata.add("Abstract", profileDesc.getString("p"));
      }
    }

    if (profileDesc.has("textClass")) {
      JSONObject textClass = profileDesc.getJSONObject("textClass");

      if (textClass.has("keywords")) {
        Object keywordsObj = textClass.get("keywords");
        // test AJ15.pdf
        if (keywordsObj instanceof String) {
          metadata.add("Keyword", (String) keywordsObj);
        } else if (keywordsObj instanceof JSONObject) {
          JSONObject keywords = textClass.getJSONObject("keywords");
          if (keywords.has("term")) {
            JSONArray termArr = keywords.getJSONArray("term");
            for (int i = 0; i < termArr.length(); i++) {
              metadata.add("Keyword", JSONObject.valueToString(termArr.get(i)));
            }
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
     * @param surName
     *          the surName to set
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
     * @param middleName
     *          the middleName to set
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
     * @param firstName
     *          the firstName to set
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
     * @param affiliations
     *          the affiliations to set
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
     * @param orgName
     *          the orgName to set
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
     * @param address
     *          the address to set
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
     * @param typeNames
     *          the typeNames to set
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
     * @param name
     *          the name to set
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
     * @param type
     *          the type to set
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
     * @param region
     *          the region to set
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
     * @param postCode
     *          the postCode to set
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
     * @param settlment
     *          the settlment to set
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
     * @param country
     *          the country to set
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
     * @param key
     *          the key to set
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
     * @param content
     *          the content to set
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
}
