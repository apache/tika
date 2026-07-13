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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;

import org.apache.tika.TikaTest;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;

/**
 * An OOXML file that declares a relationship to a part whose target is missing makes POI's
 * getRelatedPart throw an unchecked IllegalArgumentException. Extractors must skip it, not
 * abort the whole parse. Companion to {@link SXWPFMissingRelatedPartTest} (docx path); this
 * covers the xlsx (threaded comments, persons) and vsdx paths.
 */
public class OOXMLMissingRelatedPartTest extends TikaTest {

    private static final String THREADED_COMMENT_REL =
            "http://schemas.microsoft.com/office/2017/10/relationships/threadedComment";
    private static final String PERSON_REL =
            "http://schemas.microsoft.com/office/2017/10/relationships/person";

    private static final String RELS_NS =
            "http://schemas.openxmlformats.org/package/2006/relationships";

    private static final String RELS_CLOSE = "</Relationships>";

    @Test
    public void testXLSXMissingPersonsPart() throws Exception {
        //person rel whose target is not in the package
        String rel = "<Relationship Id=\"rIdPersons\" Type=\"" + PERSON_REL +
                "\" Target=\"persons/person.xml\"/>";

        Metadata m = withDanglingRel("testEXCEL.xlsx", "xl/_rels/workbook.xml.rels", rel);
        assertContains("Sample Excel Worksheet", m.get(TikaCoreProperties.TIKA_CONTENT));
    }

    @Test
    public void testXLSXMissingThreadedCommentsPart() throws Exception {
        //sheet threadedComment rel whose target is not in the package
        String rel = "<Relationship Id=\"rIdTc\" Type=\"" + THREADED_COMMENT_REL +
                "\" Target=\"../threadedComments/threadedComment1.xml\"/>";

        Metadata m = withDanglingRel("testEXCEL.xlsx", "xl/worksheets/_rels/sheet1.xml.rels", rel);
        assertContains("Sample Excel Worksheet", m.get(TikaCoreProperties.TIKA_CONTENT));
    }

    /**
     * Not a getRelatedPart case: a workbook with no sharedStrings part or relationship whose
     * cells still carry t="s" indices. Every index is out of range for the empty table -- must
     * degrade to empty cells, not an IndexOutOfBoundsException.
     */
    @Test
    public void testXLSXMissingSharedStringsPart() throws Exception {
        byte[] xlsx = mutate("testEXCEL.xlsx", Map.of(), Set.of("xl/sharedStrings.xml"));
        //drop the rel too, so POI yields an empty table instead of throwing
        xlsx = stripSharedStringsRel(xlsx);

        Metadata m = parse(xlsx, "testEXCEL.xlsx");
        //numeric cells survive though every shared string is unresolvable
        assertContains("<td>144</td>", m.get(TikaCoreProperties.TIKA_CONTENT));
    }

    @Test
    public void testVSDXMissingPagePart() throws Exception {
        //drop page1.xml, leaving the dangling relationship in pages.xml.rels
        Metadata m = parseMutated("testVISIO_text.vsdx", Map.of(),
                Set.of("visio/pages/page1.xml"));
        assertNotNull(m.get(TikaCoreProperties.TIKA_CONTENT));
    }

    @Test
    public void testVSDXMissingPagesPart() throws Exception {
        //drop pages.xml, leaving the dangling relationship in document.xml.rels
        Metadata m = parseMutated("testVISIO_text.vsdx", Map.of(),
                Set.of("visio/pages/pages.xml"));
        assertNotNull(m.get(TikaCoreProperties.TIKA_CONTENT));
    }

    /** Appends relXml to relsEntry (creating it if absent) and parses; the target is missing. */
    private Metadata withDanglingRel(String resource, String relsEntry, String relXml)
            throws Exception {
        return parseMutated(resource, Map.of(relsEntry, relXml), Set.of());
    }

    private Metadata parseMutated(String resource, Map<String, String> relAppends,
                                  Set<String> toDrop) throws Exception {
        return parse(mutate(resource, relAppends, toDrop), resource);
    }

    private Metadata parse(byte[] bytes, String desc) throws Exception {
        List<Metadata> metadataList;
        try (TikaInputStream tis = TikaInputStream.get(bytes)) {
            metadataList = getRecursiveMetadata(tis, new Metadata(), new ParseContext(), false);
        }
        assertEquals(1, metadataList.size(), desc);
        return metadataList.get(0);
    }

    /** Removes the sharedStrings relationship from xl/_rels/workbook.xml.rels. */
    private byte[] stripSharedStringsRel(byte[] xlsx) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipArchiveInputStream zin = new ZipArchiveInputStream(new ByteArrayInputStream(xlsx));
             ZipArchiveOutputStream zout = new ZipArchiveOutputStream(bos)) {
            ZipArchiveEntry entry;
            while ((entry = zin.getNextEntry()) != null) {
                zout.putArchiveEntry(new ZipArchiveEntry(entry.getName()));
                if ("xl/_rels/workbook.xml.rels".equals(entry.getName())) {
                    String rels = new String(IOUtils.toByteArray(zin), StandardCharsets.UTF_8);
                    zout.write(rels.replaceAll("<Relationship[^>]*sharedStrings\\.xml\"\\s*/>", "")
                            .getBytes(StandardCharsets.UTF_8));
                } else {
                    IOUtils.copy(zin, zout);
                }
                zout.closeArchiveEntry();
            }
        }
        return bos.toByteArray();
    }

    /** Copies resource, dropping toDrop entries and appending relAppends to the named .rels. */
    private byte[] mutate(String resource, Map<String, String> relAppends, Set<String> toDrop)
            throws Exception {
        Map<String, String> remaining = new HashMap<>(relAppends);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipArchiveInputStream zin =
                     new ZipArchiveInputStream(getResourceAsStream("/test-documents/" + resource));
             ZipArchiveOutputStream zout = new ZipArchiveOutputStream(bos)) {
            ZipArchiveEntry entry;
            while ((entry = zin.getNextEntry()) != null) {
                String name = entry.getName();
                if (toDrop.contains(name)) {
                    continue;
                }
                zout.putArchiveEntry(new ZipArchiveEntry(name));
                String relXml = remaining.remove(name);
                if (relXml != null) {
                    //append to the existing .rels, keeping the rest
                    String rels = new String(IOUtils.toByteArray(zin), StandardCharsets.UTF_8);
                    zout.write(rels.replace(RELS_CLOSE, relXml + RELS_CLOSE)
                            .getBytes(StandardCharsets.UTF_8));
                } else {
                    IOUtils.copy(zin, zout);
                }
                zout.closeArchiveEntry();
            }
            //.rels parts the package didn't have at all
            for (Map.Entry<String, String> e : remaining.entrySet()) {
                zout.putArchiveEntry(new ZipArchiveEntry(e.getKey()));
                String rels = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                        "<Relationships xmlns=\"" + RELS_NS + "\">" + e.getValue() + RELS_CLOSE;
                zout.write(rels.getBytes(StandardCharsets.UTF_8));
                zout.closeArchiveEntry();
            }
        }
        return bos.toByteArray();
    }
}
