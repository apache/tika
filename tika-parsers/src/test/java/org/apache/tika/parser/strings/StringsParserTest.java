/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.tika.parser.strings;

import static org.apache.tika.parser.strings.StringsParser.getStringsProg;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

import java.io.InputStream;
import java.util.Arrays;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.external.ExternalParser;
import org.apache.tika.sax.BodyContentHandler;
import org.junit.Test;
import org.xml.sax.ContentHandler;

public class StringsParserTest {
	public static boolean canRun() {
		StringsConfig config = new StringsConfig();
		String[] checkCmd = {config.getStringsPath() + getStringsProg(), "--version"};
		boolean hasStrings = ExternalParser.check(checkCmd);
		return hasStrings;
	}

	@Test
	public void testParse() throws Exception {
		assumeTrue(canRun());
		
		String resource = "/test-documents/testOCTET_header.dbase3";

		String[] content = { "CLASSNO", "TITLE", "ITEMNO", "LISTNO", "LISTDATE" };
		
		String[] met_attributes = {"min-len", "encoding", "strings:file_output"};

		StringsConfig stringsConfig = new StringsConfig();
		FileConfig fileConfig = new FileConfig();

		Parser parser = new StringsParser();
		ContentHandler handler = new BodyContentHandler();
		Metadata metadata = new Metadata();

		ParseContext context = new ParseContext();
		context.set(StringsConfig.class, stringsConfig);
		context.set(FileConfig.class, fileConfig);

		InputStream stream = StringsParserTest.class.getResourceAsStream(resource);

		try {
			parser.parse(stream, handler, metadata, context);
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			stream.close();
		}

		// Content
		for (String word : content) {
			assertTrue(handler.toString().contains(word));
		}
		
		// Metadata
		Arrays.equals(met_attributes, metadata.names());
	}
}
