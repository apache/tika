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

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Set;

import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

public class ISArchiveParser implements Parser {

	/**
	 * Serial version UID
	 */
	private static final long serialVersionUID = 3640809327541300229L;
	
	private final Set<MediaType> SUPPORTED_TYPES = Collections.singleton(MediaType.application("x-isatab"));
	
	private static String studyAssayFileNameField = "Study Assay File Name";
	
	private String location = null;
	
	private String studyFileName = null; 
	
	/**
	 * Default constructor.
	 */
	public ISArchiveParser() {
		this(null);
	}
	
	/**
	 * Constructor that accepts the pathname of ISArchive folder.
	 * @param location pathname of ISArchive folder including ISA-Tab files
	 */
	public ISArchiveParser(String location) {
		if (location != null && !location.endsWith(File.separator)) {
			location += File.separator;
		}
		this.location = location;
	}
	
	@Override
	public Set<MediaType> getSupportedTypes(ParseContext context) {
		return SUPPORTED_TYPES;
	}

	@Override
	public void parse(InputStream stream, ContentHandler handler, Metadata metadata,
			ParseContext context) throws IOException, SAXException, TikaException {

		TikaInputStream tis = TikaInputStream.get(stream);
		if (this.location == null) {
			this.location = tis.getFile().getParent() + File.separator;
		}
		this.studyFileName = tis.getFile().getName();
		 
		File locationFile = new File(location);
		String[] investigationList = locationFile.list(new FilenameFilter() {
			
			@Override
			public boolean accept(File dir, String name) {
				return name.matches("i_.+\\.txt");
			}
		});	
		
		XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
		xhtml.startDocument();
		
		parseInvestigation(investigationList, xhtml, metadata, context);
		parseStudy(stream, xhtml, metadata, context);
		parseAssay(xhtml, metadata, context);
		
		xhtml.endDocument();
	}
	
	private void parseInvestigation(String[] investigationList, XHTMLContentHandler xhtml, Metadata metadata, ParseContext context) throws IOException, SAXException, TikaException {
		if ((investigationList == null) || (investigationList.length == 0)) {
			// TODO warning
			return;
		}
		if (investigationList.length > 1) {
			// TODO warning
			return;
		}
		
		String investigation = investigationList[0]; // TODO add to metadata?
		InputStream stream = TikaInputStream.get(new File(this.location + investigation));
		
		ISATabUtils.parseInvestigation(stream, xhtml, metadata, context, this.studyFileName);
		
		xhtml.element("h1", "INVESTIGATION " + metadata.get("Investigation Identifier"));
	}

	private void parseStudy(InputStream stream, XHTMLContentHandler xhtml, Metadata metadata, ParseContext context) throws IOException, SAXException, TikaException {
		xhtml.element("h2", "STUDY " + metadata.get("Study Identifier"));
		
		ISATabUtils.parseStudy(stream, xhtml, metadata, context);
	}
	
	private void parseAssay(XHTMLContentHandler xhtml, Metadata metadata, ParseContext context) throws IOException, SAXException, TikaException {
		for (String assayFileName : metadata.getValues(studyAssayFileNameField)) {
			xhtml.startElement("div");
			xhtml.element("h3", "ASSAY " + assayFileName);
			InputStream stream = TikaInputStream.get(new File(this.location + assayFileName));
			ISATabUtils.parseAssay(stream, xhtml, metadata, context);
			xhtml.endElement("div");
		}
	}
}
