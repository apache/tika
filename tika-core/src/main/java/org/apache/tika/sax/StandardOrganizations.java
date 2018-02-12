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

package org.apache.tika.sax;

import java.util.Map;
import java.util.TreeMap;

/**
 * This class provides a collection of the most important technical standard organizations.
 * The collection of standard organizations has been obtained from <a href="https://en.wikipedia.org/wiki/List_of_technical_standard_organisations">Wikipedia</a>.
 * Currently, the list is composed of the most important international standard organizations, the regional standard organizations (i.e., Africa, Americas, Asia Pacific, Europe, and Middle East), and British and American standard organizations among the national-based ones.
 *
 */
public class StandardOrganizations {

	private static Map<String, String> organizations;
	static {
		organizations = new TreeMap<String, String>();
		//International standard organizations
	    organizations.put("3GPP", "3rd Generation Partnership Project");
	    organizations.put("3GPP2", "3rd Generation Partnership Project 2");
	    organizations.put("Accellera", "Accellera Organization");
	    organizations.put("A4L", "Access for Learning Community (formerly known as the Schools Interoperability Framework)");
	    organizations.put("AES", "Audio Engineering Society");
	    organizations.put("AIIM", "Association for Information and Image Management");
	    organizations.put("ASAM", "Association for Automation and Measuring Systems - Automotive technology");
	    organizations.put("ASHRAE", "American Society of Heating, Refrigerating and Air-Conditioning Engineers (ASHRAE is an international organization, despite its name)");
	    organizations.put("ASME", "formerly The American Society of Mechanical Engineers");
	    organizations.put("ASTM", "ASTM (American Society for Testing and Materials) International");
	    organizations.put("ATIS", "Alliance for Telecommunications Industry Solutions");
	    organizations.put("AUTOSAR", "Automotive technology");
	    organizations.put("BIPM, CGPM, and CIPM", "Bureau International des Poids et Mesures and the related organizations established under the Metre Convention of 1875.");
	    organizations.put("CableLabs", "Cable Television Laboratories");
	    organizations.put("CCSDS", "Consultative Committee for Space Data Sciences");
	    organizations.put("CISPR", "International Special Committee on Radio Interference");
	    organizations.put("CFA", "Compact flash association");
	    organizations.put("DCMI", "Dublin Core Metadata Initiative");
	    organizations.put("DMTF", "Distributed Management Task Force");
	    organizations.put("Ecma International", "Ecma International (previously called ECMA)");
	    organizations.put("EKOenergy", "EKOenergy Network managed by environmental NGOs");
	    organizations.put("FAI", "Fédération Aéronautique Internationale");
	    organizations.put("GlobalPlatform", "Secure element and TEE standards");
	    organizations.put("GS1", "Global supply chain standards (identification numbers, barcodes, electronic commerce transactions, RFID)");
	    organizations.put("HGI", "Home Gateway Initiative");
	    organizations.put("HFSB", "Hedge Fund Standards Board");
	    organizations.put("IATA", "International Air Transport Association");
	    organizations.put("IAU*", "International Arabic Union");
	    organizations.put("ICAO", "International Civil Aviation Organization");
	    organizations.put("IEC", "International Electrotechnical Commission");
	    organizations.put("IEEE", "Institute of Electrical and Electronics Engineers");
	    organizations.put("IEEE-SA", "IEEE Standards Association");
	    organizations.put("IETF", "Internet Engineering Task Force");
	    organizations.put("IFOAM", "International Federation of Organic Agriculture Movements");
	    organizations.put("IFSWF", "International Forum of Sovereign Wealth Funds");
	    organizations.put("IMO", "International Maritime Organization");
	    organizations.put("IMS", "IMS Global Learning Consortium");
	    organizations.put("ISO", "International Organization for Standardization");
	    organizations.put("IPTC", "International Press Telecommunications Council");
	    organizations.put("ITU", "The International Telecommunication Union");
	    organizations.put("ITU-R", "ITU Radiocommunications Sector (formerly known as CCIR)");
	    organizations.put("CCIR", "Comité Consultatif International pour la Radio, a forerunner of the ITU-R");
	    organizations.put("ITU-T", "ITU Telecommunications Sector (formerly known as CCITT)");
	    organizations.put("CCITT", "Comité Consultatif International Téléphonique et Télégraphique, renamed ITU-T in 1993");
	    organizations.put("ITU-D", "ITU Telecom Development (formerly known as BDT)");
	    organizations.put("BDT", "Bureau de développement des télécommunications, renamed ITU-D");
	    organizations.put("IUPAC", "International Union of Pure and Applied Chemistry");
	    organizations.put("Liberty Alliance", "Liberty Alliance");
	    organizations.put("Media Grid", "Media Grid Standards Organization");
	    organizations.put("NACE International", "Formerly known as National Association of Corrosion Engineers");
	    organizations.put("OASIS", "Organization for the Advancement of Structured Information Standards");
	    organizations.put("OGC", "Open Geospatial Consortium");
	    organizations.put("OHICC", "Organization of Hotel Industry Classification & Certification");
	    organizations.put("OMA", "Open Mobile Alliance");
	    organizations.put("OMG", "Object Management Group");
	    organizations.put("OGF", "Open Grid Forum (merger of Global Grid Forum (GGF) and Enterprise Grid Alliance (EGA))");
	    organizations.put("GGF", "Global Grid Forum");
	    organizations.put("EGA", "Enterprise Grid Alliance");
	    organizations.put("OpenTravel Alliance", "OpenTravel Alliance (previously known as OTA)");
	    organizations.put("OTA", "OpenTravel Alliance");
	    organizations.put("OSGi", "OSGi Alliance");
	    organizations.put("PESC", "P20 Education Standards Council[1]");
	    organizations.put("SAI", "Social Accountability International");
	    organizations.put("SDA", "Secure Digital Association");
	    organizations.put("SNIA", "Storage Networking Industry Association");
	    organizations.put("SMPTE", "Society of Motion Picture and Television Engineers");
	    organizations.put("SSDA", "Solid State Drive Alliance");
	    organizations.put("The Open Group", "The Open Group");
	    organizations.put("TIA", "Telecommunications Industry Association");
	    organizations.put("TM Forum", "Telemanagement Forum");
	    organizations.put("UIC", "International Union of Railways");
	    organizations.put("UL", "Underwriters Laboratories");
	    organizations.put("UPU", "Universal Postal Union");
	    organizations.put("WMO", "World Meteorological Organization");
	    organizations.put("W3C", "World Wide Web Consortium");
	    organizations.put("WSA", "Website Standards Association");
	    organizations.put("WHO", "World Health Organization");
	    organizations.put("XSF", "The XMPP Standards Foundation");
	    organizations.put("FAO", "Food and Agriculture Organization");
	    //Regional standards organizations
	    //Africa
	    organizations.put("ARSO", "African Regional Organization for Standarization");
	    organizations.put("SADCSTAN", "Southern African Development Community (SADC) Cooperation in Standarization");
	    //Americas
	    organizations.put("COPANT", "Pan American Standards Commission");
	    organizations.put("AMN", "MERCOSUR Standardization Association");
	    organizations.put("CROSQ", "CARICOM Regional Organization for Standards and Quality");
	    organizations.put("AAQG", "America's Aerospace Quality Group");
	    //Asia Pacific
	    organizations.put("PASC", "Pacific Area Standards Congress");
	    organizations.put("ACCSQ", "ASEAN Consultative Committee for Standards and Quality");
	    //Europe
	    organizations.put("RoyalCert", "RoyalCert International Registrars");
	    organizations.put("CEN", "European Committee for Standardization");
	    organizations.put("CENELEC", "European Committee for Electrotechnical Standardization");
	    organizations.put("URS", "United Registrar of Systems, UK");
	    organizations.put("ETSI", "European Telecommunications Standards Institute");
	    organizations.put("EASC", "Euro-Asian Council for Standardization, Metrology and Certification");
	    organizations.put("IRMM", "Institute for Reference Materials and Measurements (European Union)");
	    //Middle East
	    organizations.put("AIDMO", "Arab Industrial Development and Mining Organization");
	    organizations.put("IAU", "International Arabic Union");
	    //Nationally-based standards organizations
	    //United Kingdom
	    organizations.put("BSI", "British Standards Institution aka BSI Group");
	    organizations.put("DStan", "UK Defence Standardization");
	    //United States of America
	    organizations.put("ANSI", "American National Standards Institute");
	    organizations.put("ACI", "American Concrete Institute");
	    organizations.put("NIST", "National Institute of Standards and Technology");
	}
	
	/**
	 * Returns the map containing the collection of the most important technical standard organizations.
	 * 
	 * @return the map containing the collection of the most important technical standard organizations.
	 */
	public static Map<String, String> getOrganizations() {
		return organizations;
	}
	
	/**
	 * Returns the regular expression containing the most important technical standard organizations.
	 * 
	 * @return the regular expression containing the most important technical standard organizations.
	 */
	public static String getOrganzationsRegex() {
		StringBuilder sb = new StringBuilder();
		sb.append("(");
		int i = 0;
		for (String org : organizations.keySet()) {
			if (i > 0) {
				sb.append("|");
			}
			sb.append(org);
			i++;
		}
		sb.append(")");
		return sb.toString();
	}
}