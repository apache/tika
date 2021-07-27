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

import java.util.List;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import org.apache.tika.TikaTest;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.microsoft.OfficeParserConfig;

public class OOXMLParserTest extends TikaTest {

    @Test
    public void testEmbeddedPDFInPPTX() throws Exception {
        List<Metadata> metadataList = getRecursiveMetadata("testPPT_EmbeddedPDF.pptx");
        Metadata pdfMetadata1 = metadataList.get(4);
        assertContains("Apache Tika", pdfMetadata1.get(TikaCoreProperties.TIKA_CONTENT));
        Metadata pdfMetadata2 = metadataList.get(5);
        assertContains("Hello World", pdfMetadata2.get(TikaCoreProperties.TIKA_CONTENT));
    }

    @Test
    public void testEmbeddedPDFInXLSX() throws Exception {
        List<Metadata> metadataList = getRecursiveMetadata("testExcel_embeddedPDF.xlsx");
        Metadata pdfMetadata = metadataList.get(1);
        assertContains("Hello World", pdfMetadata.get(TikaCoreProperties.TIKA_CONTENT));
    }

    @Test
    public void testEmbeddedPDFInStreamingPPTX() throws Exception {
        ParseContext parseContext = new ParseContext();
        OfficeParserConfig officeParserConfig = new OfficeParserConfig();
        officeParserConfig.setUseSAXPptxExtractor(true);
        parseContext.set(OfficeParserConfig.class, officeParserConfig);

        List<Metadata> metadataList =
                getRecursiveMetadata("testPPT_EmbeddedPDF.pptx", parseContext);
        Metadata pdfMetadata1 = metadataList.get(4);
        assertContains("Apache Tika", pdfMetadata1.get(TikaCoreProperties.TIKA_CONTENT));
        Metadata pdfMetadata2 = metadataList.get(5);
        assertContains("Hello World", pdfMetadata2.get(TikaCoreProperties.TIKA_CONTENT));
    }

    @Disabled("TODO figure out why this doesn't work")
    @Test//(expected = org.apache.tika.exception.TikaException.class)
    public void testCorruptedZip() throws Exception {
        //TIKA_2446
        getRecursiveMetadata("testZIP_corrupted_oom.zip");
    }
}
