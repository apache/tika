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
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import org.apache.tika.TikaTest;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Office;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.microsoft.EMFParser;
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

    @Test
    public void testEMFAssociatedWithAttachments() throws Exception {
        //TIKA-3968
        List<Metadata> metadataList = getRecursiveMetadata("testWORD_EMFAndAttachments.docx");

        assertEquals("true", metadataList.get(1).get(EMFParser.EMF_ICON_ONLY));
        assertEquals("true", metadataList.get(3).get(EMFParser.EMF_ICON_ONLY));
        assertEquals("true", metadataList.get(5).get(EMFParser.EMF_ICON_ONLY));
        assertEquals("TestText.txt", metadataList.get(1).get(EMFParser.EMF_ICON_STRING));
        assertEquals("TestPdf.pdf", metadataList.get(3).get(EMFParser.EMF_ICON_STRING));
        assertEquals("testWORD123.docx", metadataList.get(5).get(EMFParser.EMF_ICON_STRING));

        assertNull(metadataList.get(2).get(Office.PROG_ID));
        assertEquals("AcroExch.Document.DC", metadataList.get(4).get(Office.PROG_ID));
        assertEquals("Word.Document.12", metadataList.get(6).get(Office.PROG_ID));

        assertEquals("TestText.txt", metadataList.get(2).get(TikaCoreProperties.RESOURCE_NAME_KEY));
        assertEquals("TestPdf.pdf", metadataList.get(4).get(TikaCoreProperties.RESOURCE_NAME_KEY));
        assertEquals("testWORD123.docx", metadataList.get(6).get(TikaCoreProperties.RESOURCE_NAME_KEY));

        assertEquals("/TestText.txt",
                metadataList.get(2).get(TikaCoreProperties.EMBEDDED_RESOURCE_PATH));
        assertEquals("/TestPdf.pdf",
                metadataList.get(4).get(TikaCoreProperties.EMBEDDED_RESOURCE_PATH));
        assertEquals("/testWORD123.docx",
                metadataList.get(6).get(TikaCoreProperties.EMBEDDED_RESOURCE_PATH));

        assertContains("This is Text File",
                metadataList.get(2).get(TikaCoreProperties.TIKA_CONTENT));

        assertContains("This is test PDF document for parser.",
                metadataList.get(4).get(TikaCoreProperties.TIKA_CONTENT));

        assertContains("This is test word document for parser.",
                metadataList.get(6).get(TikaCoreProperties.TIKA_CONTENT));

        assertEquals(TikaCoreProperties.EmbeddedResourceType.ATTACHMENT.name(),
                metadataList.get(2).get(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE));
        assertEquals(TikaCoreProperties.EmbeddedResourceType.ATTACHMENT.name(),
                metadataList.get(4).get(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE));
        assertEquals(TikaCoreProperties.EmbeddedResourceType.ATTACHMENT.name(),
                metadataList.get(6).get(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE));

        assertEquals(TikaCoreProperties.EmbeddedResourceType.INLINE.name(),
                metadataList.get(1).get(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE));
        assertEquals(TikaCoreProperties.EmbeddedResourceType.INLINE.name(),
                metadataList.get(3).get(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE));
        assertEquals(TikaCoreProperties.EmbeddedResourceType.INLINE.name(),
                metadataList.get(5).get(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE));
    }

    @Disabled("TODO figure out why this doesn't work")
    @Test//(expected = org.apache.tika.exception.TikaException.class)
    public void testCorruptedZip() throws Exception {
        //TIKA_2446
        getRecursiveMetadata("testZIP_corrupted_oom.zip");
    }

    @Test
    public void testAltFileMHTChunk() throws Exception {
        //test file with permission from:
        // https://github.com/jgm/pandoc/files/1290782/Sample_DOCX_using_MHT_container.docx
        List<Metadata> metadataList = getRecursiveMetadata("testAltChunkMHT.docx");
        assertEquals(3, metadataList.size());
        assertContains("Example of a table",
                metadataList.get(2).get(TikaCoreProperties.TIKA_CONTENT));
    }

    @Test
    public void testAltFileHTMLChunk() throws Exception {
        //test file with permission from:
        // https://github.com/jgm/pandoc/files/1290782/Sample_DOCX_using_HTML_container.docx
        List<Metadata> metadataList = getRecursiveMetadata("testAltChunkHTML.docx");
        assertEquals(2, metadataList.size());
        assertContains("Example of a table",
                metadataList.get(1).get(TikaCoreProperties.TIKA_CONTENT));
    }
}
