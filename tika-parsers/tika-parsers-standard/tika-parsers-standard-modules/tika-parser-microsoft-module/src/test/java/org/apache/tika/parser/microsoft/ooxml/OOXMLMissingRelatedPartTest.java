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

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;

import org.apache.tika.TikaTest;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;

/**
 * An OOXML package may declare a relationship whose target part is missing -- a truncated
 * or otherwise malformed file. POI's {@code PackagePart.getRelatedPart} then throws an
 * unchecked {@code IllegalArgumentException}, which
 * {@link OOXMLExtractorFactory} converts into a TikaException: the whole file aborts and
 * even the text already extracted is lost. Every such call must go through
 * {@link AbstractOOXMLExtractor#safeGetRelatedPart}.
 *
 * <p>Same failure class as the 3.3.2 docx regression (740 files crashed on a missing
 * numbering.xml/settings.xml). 3.3.2 guards these four xlsx/vsdx sites; 4.0.0 shipped
 * without them (TIKA-4879).
 */
public class OOXMLMissingRelatedPartTest extends TikaTest {

    private static final String XLSX_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final String VSDX_TYPE = "application/vnd.ms-visio.drawing";

    @Test
    public void testDanglingThreadedCommentRelationship() throws Exception {
        //XSSFExcelExtractorDecorator.getThreadedComments
        byte[] xlsx = withExtraRelationship("testComment.xlsx",
                "xl/worksheets/_rels/sheet1.xml.rels",
                "http://schemas.microsoft.com/office/2017/10/relationships/threadedComment",
                "../threadedComments/threadedComment1.xml");
        assertSheetTextSurvives(xlsx);
    }

    @Test
    public void testDanglingPersonRelationship() throws Exception {
        //XSSFExcelExtractorDecorator.getPersons
        byte[] xlsx = withExtraRelationship("testComment.xlsx", "xl/_rels/workbook.xml.rels",
                "http://schemas.microsoft.com/office/2017/10/relationships/person",
                "persons/person.xml");
        assertSheetTextSurvives(xlsx);
    }

    @Test
    public void testMissingVisioPage() throws Exception {
        //VSDXExtractorDecorator.getPageParts, the per-page loop
        assertVisioParseCompletes(withoutEntry("testVISIO.vsdx", "visio/pages/page1.xml"));
    }

    @Test
    public void testMissingVisioPagesPart() throws Exception {
        //VSDXExtractorDecorator.getRelatedPart(PackagePart, String), document.xml -> pages.xml
        assertVisioParseCompletes(withoutEntry("testVISIO.vsdx", "visio/pages/pages.xml"));
    }

    /**
     * The pages are unreachable, but the parse must still run to completion: the EMF
     * thumbnail is emitted after buildXHTML, so its presence proves we did not abort.
     */
    private void assertVisioParseCompletes(byte[] vsdx) throws Exception {
        List<Metadata> metadataList = assertParses(vsdx, VSDX_TYPE);
        assertEquals(2, metadataList.size());
        assertEquals("image/emf", metadataList.get(1).get(HttpHeaders.CONTENT_TYPE));
    }

    /** The dangling relationship must not cost us the sheet text that parsed fine. */
    private void assertSheetTextSurvives(byte[] xlsx) throws Exception {
        List<Metadata> metadataList = assertParses(xlsx, XLSX_TYPE);
        assertEquals(1, metadataList.size());
        assertContains("Here is some text",
                metadataList.get(0).get(TikaCoreProperties.TIKA_CONTENT));
    }

    private List<Metadata> assertParses(byte[] bytes, String expectedType) throws Exception {
        List<Metadata> metadataList;
        try (TikaInputStream tis = TikaInputStream.get(bytes)) {
            //suppressException=false: an escaping IllegalArgumentException fails the test here
            metadataList = getRecursiveMetadata(tis, false);
        }
        Metadata m = metadataList.get(0);
        assertEquals(expectedType, m.get(HttpHeaders.CONTENT_TYPE));
        assertNotNull(m.get(TikaCoreProperties.TIKA_CONTENT));
        return metadataList;
    }

    /** Copies the resource, omitting one zip entry and leaving its relationship dangling. */
    private byte[] withoutEntry(String resource, String entryName) throws Exception {
        return copy(resource, entryName, null, null, null);
    }

    /** Copies the resource, appending a relationship whose target is not in the package. */
    private byte[] withExtraRelationship(String resource, String relsEntry, String type,
                                         String target) throws Exception {
        return copy(resource, null, relsEntry, type, target);
    }

    private byte[] copy(String resource, String dropEntry, String relsEntry, String type,
                        String target) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipArchiveInputStream zin =
                     new ZipArchiveInputStream(getResourceAsStream("/test-documents/" + resource));
             ZipArchiveOutputStream zout = new ZipArchiveOutputStream(bos)) {
            ZipArchiveEntry entry;
            while ((entry = zin.getNextEntry()) != null) {
                if (entry.getName().equals(dropEntry)) {
                    continue;
                }
                byte[] data = IOUtils.toByteArray(zin);
                if (entry.getName().equals(relsEntry)) {
                    String rels = new String(data, StandardCharsets.UTF_8);
                    String injected = "<Relationship Id=\"rIdMissingTarget\" Type=\"" + type +
                            "\" Target=\"" + target + "\"/></Relationships>";
                    data = rels.replace("</Relationships>", injected)
                            .getBytes(StandardCharsets.UTF_8);
                }
                zout.putArchiveEntry(new ZipArchiveEntry(entry.getName()));
                zout.write(data);
                zout.closeArchiveEntry();
            }
        }
        return bos.toByteArray();
    }
}
