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

import static org.junit.Assert.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.tika.TikaTest;
import org.apache.tika.parser.Parser;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

public class ISArchiveParserTest extends TikaTest {

	static Path tmpDir;
    final static String ISA_SUBDIR = "testISATab_BII-I-1";
    final static String[] ISA_FILES = {
            "a_bii-s-2_metabolite profiling_NMR spectroscopy.txt",
            "a_metabolome.txt",
            "a_microarray.txt",
            "a_proteome.txt",
            "a_transcriptome.txt",
            "i_investigation.txt"
    };

    @BeforeClass
	public static void createTempDir() throws Exception {
        tmpDir = Files.createTempDirectory(ISA_SUBDIR);
        for (String isaFile : ISA_FILES) {
            String isaPath = "test-documents/"+ISA_SUBDIR+"/"+isaFile;
            Files.copy(ISArchiveParserTest.class.getClassLoader().getResourceAsStream(isaPath),
                    tmpDir.resolve(isaFile));
        }
    }
	@AfterClass
    public static void deleteTempDir() throws Exception {
        for (String isaFile : ISA_FILES) {
            Path p = tmpDir.resolve(isaFile);
            Files.delete(p);
        }
        Files.delete(tmpDir);
    }

	@Test
	public void testParseArchive() throws Exception {

		Parser parser = new ISArchiveParser(tmpDir.toString());
		XMLResult r = getXML(ISA_SUBDIR+"/s_BII-S-1.txt",
					parser);

		// INVESTIGATION
		assertEquals("Invalid Investigation Identifier", "BII-I-1",
				r.metadata.get("Investigation Identifier"));
		assertEquals("Invalid Investigation Title",
				"Growth control of the eukaryote cell: a systems biology study in yeast",
				r.metadata.get("Investigation Title"));
		
		// INVESTIGATION PUBLICATIONS
		assertEquals("Invalid Investigation PubMed ID", "17439666",
				r.metadata.get("Investigation PubMed ID"));
		assertEquals("Invalid Investigation Publication DOI", "doi:10.1186/jbiol54",
				r.metadata.get("Investigation Publication DOI"));
		
		// INVESTIGATION CONTACTS
		assertEquals("Invalid Investigation Person Last Name", "Oliver",
				r.metadata.get("Investigation Person Last Name"));
		assertEquals("Invalid Investigation Person First Name", "Stephen",
				r.metadata.get("Investigation Person First Name"));
	}
}
