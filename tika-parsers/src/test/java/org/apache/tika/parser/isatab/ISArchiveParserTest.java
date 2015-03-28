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

package org.apache.tika.parser.isatab;

import static org.junit.Assert.*;

import java.io.InputStream;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.junit.Test;
import org.xml.sax.ContentHandler;

public class ISArchiveParserTest {

	@Test
	public void testParseArchive() throws Exception {
		String path = "/test-documents/testISATab_BII-I-1/s_BII-S-1.txt";
		
		Parser parser = new ISArchiveParser(ISArchiveParserTest.class.getResource("/test-documents/testISATab_BII-I-1/").getPath());
		//Parser parser = new AutoDetectParser();
		
		ContentHandler handler = new BodyContentHandler();
		Metadata metadata = new Metadata();
		ParseContext context = new ParseContext();
		InputStream stream = null;
		try {
			stream = ISArchiveParserTest.class.getResourceAsStream(path);
			parser.parse(stream, handler, metadata, context);
		}
		finally {
			stream.close();
		}
		
		// INVESTIGATION
		assertEquals("Invalid Investigation Identifier", "BII-I-1", metadata.get("Investigation Identifier"));
		assertEquals("Invalid Investigation Title", "Growth control of the eukaryote cell: a systems biology study in yeast", metadata.get("Investigation Title"));
		
		// INVESTIGATION PUBLICATIONS
		assertEquals("Invalid Investigation PubMed ID", "17439666", metadata.get("Investigation PubMed ID")); 
		assertEquals("Invalid Investigation Publication DOI", "doi:10.1186/jbiol54", metadata.get("Investigation Publication DOI"));
		
		// INVESTIGATION CONTACTS
		assertEquals("Invalid Investigation Person Last Name", "Oliver", metadata.get("Investigation Person Last Name")); 
		assertEquals("Invalid Investigation Person First Name", "Stephen", metadata.get("Investigation Person First Name"));
	}
}
