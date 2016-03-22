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
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import java.util.Arrays;

import org.apache.tika.TikaTest;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.external.ExternalParser;
import org.junit.Test;

public class StringsParserTest extends TikaTest {
	public static boolean canRun() {
		StringsConfig config = new StringsConfig();
		String[] checkCmd = {config.getStringsPath() + getStringsProg(), "--version"};
		boolean hasStrings = ExternalParser.check(checkCmd);
		return hasStrings;
	}

	@Test
	public void testParse() throws Exception {
		assumeTrue(canRun());
		
		String resource = "testOCTET_header.dbase3";

		String[] content = { "CLASSNO", "TITLE", "ITEMNO", "LISTNO", "LISTDATE" };
		
		String[] met_attributes = {"min-len", "encoding", "strings:file_output"};

		StringsConfig stringsConfig = new StringsConfig();
		FileConfig fileConfig = new FileConfig();

		Parser parser = new StringsParser();
		ParseContext context = new ParseContext();
		context.set(StringsConfig.class, stringsConfig);
		context.set(FileConfig.class, fileConfig);
		Metadata metadata = new Metadata();
		XMLResult r = getXML(resource, parser, metadata, context);

		// Content
		for (String word : content) {
			assertTrue(r.xml.contains(word));
		}
		
		// Metadata
		Arrays.equals(met_attributes, metadata.names());
	}
}
