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
package org.apache.tika.parser.pkg;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.InputStream;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.apache.tika.config.TikaConfig;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.external.ExternalParser;


/**
 * Test case for parsing rar files.
 */
public class UnrarParserTest extends AbstractPkgTest {

    /**
     * Tests that the ParseContext parser is correctly
     * fired for all the embedded entries.
     */
    @Test
    public void testEmbedded() throws Exception {
        assumeTrue(ExternalParser.check("unrar"));

        // Expected embedded resources in test-documents.rar file.
        String[] expectedResources = { "testHTML.html", "testEXCEL.xls", "testOpenOffice2.odt", "testPDF.pdf",
                "testPPT.ppt", "testRTF.rtf", "testTXT.txt", "testWORD.doc", "testXML.xml"};

        TikaConfig tikaConfig = null;
        try (InputStream is = getResourceAsStream("tika-unrar-config.xml")) {
            tikaConfig = new TikaConfig(is);
        }
        Parser p = new AutoDetectParser(tikaConfig);
        List<Metadata> metadataList = getRecursiveMetadata("test-documents.rar", p);
        assertEquals("org.apache.tika.parser.pkg.UnrarParser",
                metadataList.get(0).getValues(TikaCoreProperties.TIKA_PARSED_BY)[1]);
        assertEquals(12, metadataList.size());

        for (String resource : expectedResources) {
            assertEquals(1, metadataList.stream()
                    .filter(m -> m.get(TikaCoreProperties.ORIGINAL_RESOURCE_NAME) != null &&
                            m.get(TikaCoreProperties.ORIGINAL_RESOURCE_NAME).contains(resource)).count());
        }
    }

}
