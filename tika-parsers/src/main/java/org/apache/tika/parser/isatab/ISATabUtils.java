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
package org.apache.tika.parser.isatab;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.tika.config.ServiceLoader;
import org.apache.tika.detect.AutoDetectReader;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.CloseShieldInputStream;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.SAXException;

public class ISATabUtils {
	
	private static final ServiceLoader LOADER = new ServiceLoader(ISATabUtils.class.getClassLoader());
	
	/**
	 * INVESTIGATION
	 */
	
	// Investigation section.
	private static final String[] sections = {
			"ONTOLOGY SOURCE REFERENCE",
			"INVESTIGATION", 
			"INVESTIGATION PUBLICATIONS", 
			"INVESTIGATION CONTACTS"
		};
	
	// STUDY section (inside the Study section)
	private static final String studySectionField = "STUDY";
	
	// Study File Name (inside the STUDY section)
	private static final String studyFileNameField = "Study File Name";
	
	public static void parseInvestigation(InputStream stream, XHTMLContentHandler handler, Metadata metadata, ParseContext context, String studyFileName) throws IOException, TikaException, SAXException {
		// Automatically detect the character encoding
		AutoDetectReader reader = new AutoDetectReader(new CloseShieldInputStream(stream), metadata, context.get(ServiceLoader.class, LOADER));

		try {
			extractMetadata(reader, metadata, studyFileName);
		} finally {
			reader.close();
		}
	}
	
	public static void parseInvestigation(InputStream stream, XHTMLContentHandler handler, Metadata metadata, ParseContext context) throws IOException, TikaException, SAXException {
		parseInvestigation(stream, handler, metadata, context, null);
	}
	
	public static void parseStudy(InputStream stream, XHTMLContentHandler xhtml, Metadata metadata, ParseContext context) throws IOException, TikaException, SAXException {
		TikaInputStream tis = TikaInputStream.get(stream);
		// Automatically detect the character encoding
		AutoDetectReader reader = new AutoDetectReader(new CloseShieldInputStream(tis), metadata, context.get(ServiceLoader.class, LOADER));
		CSVParser csvParser = null;
		
		try {
			csvParser = new CSVParser(reader, CSVFormat.TDF);
			Iterator<CSVRecord> iterator = csvParser.iterator();

			xhtml.startElement("table");

			xhtml.startElement("thead");
			if (iterator.hasNext()) {
				CSVRecord record = iterator.next();
				for (int i = 0; i < record.size(); i++) {
					xhtml.startElement("th");
					xhtml.characters(record.get(i));
					xhtml.endElement("th");
				}
			}
			xhtml.endElement("thead");

			xhtml.startElement("tbody");
			while (iterator.hasNext()) {
				CSVRecord record = iterator.next();
				xhtml.startElement("tr");
				for (int j = 0; j < record.size(); j++) {
					xhtml.startElement("td");
					xhtml.characters(record.get(j));
					xhtml.endElement("td");
				}
				xhtml.endElement("tr");
			}
			xhtml.endElement("tbody");

			xhtml.endElement("table");

		} finally {
			reader.close();
			csvParser.close();
		}
	}
	
	public static void parseAssay(InputStream stream, XHTMLContentHandler xhtml, Metadata metadata, ParseContext context) throws IOException, TikaException, SAXException {
		TikaInputStream tis = TikaInputStream.get(stream);
		
		// Automatically detect the character encoding
		AutoDetectReader reader = new AutoDetectReader(new CloseShieldInputStream(tis), metadata, context.get(ServiceLoader.class, LOADER));
		CSVParser csvParser = null;
		
		try {
			csvParser = new CSVParser(reader, CSVFormat.TDF);
			
			xhtml.startElement("table");
			
			Iterator<CSVRecord> iterator = csvParser.iterator();
			
			xhtml.startElement("thead");
			if (iterator.hasNext()) {
				CSVRecord record = iterator.next();
				for (int i = 0; i < record.size(); i++) {
					xhtml.startElement("th");
					xhtml.characters(record.get(i));
					xhtml.endElement("th");
				}
			}
			xhtml.endElement("thead");
			
			xhtml.startElement("tbody");
			while (iterator.hasNext()) {
				CSVRecord record = iterator.next();
				xhtml.startElement("tr");
				for (int j = 0; j < record.size(); j++) {
					xhtml.startElement("td");
					xhtml.characters(record.get(j));
					xhtml.endElement("td");
				}
				xhtml.endElement("tr");
			}
			xhtml.endElement("tbody");
			
			xhtml.endElement("table");
			
		} finally {
			reader.close();
			csvParser.close();
		}
	}
	
	private static void extractMetadata(Reader reader, Metadata metadata, String studyFileName) throws IOException {
		boolean investigationSection = false;
		boolean studySection = false;
		boolean studyTarget = false;
				
		Map<String, String> map = new HashMap<String, String>();
		
		CSVParser csvParser = null;
		try {
			csvParser = new CSVParser(reader, CSVFormat.TDF);
			
			Iterator<CSVRecord> iterator = csvParser.iterator();
			
			while (iterator.hasNext()) {
				CSVRecord record = iterator.next();
				String field = record.get(0);
				if ((field.toUpperCase(Locale.ENGLISH).equals(field)) && (record.size() == 1)) {
					investigationSection = Arrays.asList(sections).contains(field);
					studySection = (studyFileName != null) && (field.equals(studySectionField));
				}
				else {
					if (investigationSection) {
						addMetadata(field, record, metadata);
					}
					else if (studySection) {
						if (studyTarget) {
							break;
						}
						String value = record.get(1);
						map.put(field, value);
						studyTarget = (field.equals(studyFileNameField)) && (value.equals(studyFileName));
						if (studyTarget) {
							mapStudyToMetadata(map, metadata);
							studySection = false;
						}
					}
					else if (studyTarget) {
						addMetadata(field, record, metadata);
					}
				}
			}
		} catch (IOException ioe) {
			throw ioe;
		} finally {
			csvParser.close();
		}
	}
	
	private static void addMetadata(String field, CSVRecord record, Metadata metadata) {
		if ((record ==null) || (record.size() <= 1)) {
			return;
		}
		
		for (int i = 1; i < record.size(); i++) {
			metadata.add(field, record.get(i));
		}
	}
	
	private static void mapStudyToMetadata(Map<String, String> map, Metadata metadata) {
		for (Map.Entry<String, String> entry : map.entrySet()) {
			metadata.add(entry.getKey(), entry.getValue());
		}
	}
}
