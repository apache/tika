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
package org.apache.tika.parser.microsoft.ooxml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import org.apache.tika.TikaTest;
import org.apache.tika.config.loader.TikaLoader;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;

public class OOXMLParserTest extends TikaTest {

    @Test
    public void testEmbeddedPDFInXLSX() throws Exception {
        List<Metadata> metadataList = getRecursiveMetadata("testExcel_embeddedPDF.xlsx");
        Metadata pdfMetadata = metadataList.get(1);
        assertContains("Hello World", pdfMetadata.get(TikaCoreProperties.TIKA_CONTENT));
    }

    @Disabled("TODO figure out why this doesn't work")
    @Test
    public void testCorruptedZip() throws Exception {
        //TIKA_2446
        getRecursiveMetadata("testZIP_corrupted_oom.zip");
    }

    @Test
    public void testDigestTranslator() throws Exception {
        TikaLoader loader = TikaLoader.load(
                getConfigPath(OOXMLParserTest.class, "tika-config-digests.json"));
        Parser parser = loader.loadAutoDetectParser();
        ParseContext parseContext = loader.loadParseContext();
        List<Metadata> metadataList =
                getRecursiveMetadata("testMSChart-govdocs-428996.pptx", parser, parseContext);
        assertEquals(4, metadataList.size());
        for (Metadata m : metadataList) {
            assertNotNull(m.get("X-TIKA:digest:SHA256:BASE32"));
            assertNull(m.get(TikaCoreProperties.EMBEDDED_EXCEPTION));
        }
    }
}
