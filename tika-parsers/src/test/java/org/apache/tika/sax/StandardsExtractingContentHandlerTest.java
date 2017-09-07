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

import static org.junit.Assert.*;

import java.io.File;
import java.io.InputStream;

import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.junit.Test;

/**
 * Test class for the {@link StandardsExtractingContentHandler} class.
 */
public class StandardsExtractingContentHandlerTest {

	@Test
	public void testExtractStandards() throws Exception {
		Parser parser = new AutoDetectParser();
		Metadata metadata = new Metadata();
		
		StandardsExtractingContentHandler handler = new StandardsExtractingContentHandler(new BodyContentHandler(-1), metadata);
		handler.setThreshold(0.75);
		InputStream inputStream = StandardsExtractingContentHandlerTest.class.getResourceAsStream("/test-documents/testStandardsExtractor.pdf");
		
		parser.parse(inputStream, handler, metadata, new ParseContext());
		
		String[] standardReferences = metadata.getValues(StandardsExtractingContentHandler.STANDARD_REFERENCES);
		
		assertTrue(standardReferences[0].equals("ANSI/TIA 222-G"));
		assertTrue(standardReferences[1].equals("TIA/ANSI 222-G-1"));
		assertTrue(standardReferences[2].equals("FIPS 140-2"));
		assertTrue(standardReferences[3].equals("FIPS 197"));
	}
}