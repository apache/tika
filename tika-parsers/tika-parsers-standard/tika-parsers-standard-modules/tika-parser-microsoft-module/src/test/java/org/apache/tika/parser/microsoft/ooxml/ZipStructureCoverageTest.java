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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;

import org.apache.tika.TikaTest;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Office;
import org.apache.tika.metadata.TikaCoreProperties;

/**
 * Characterizes the boundary of Tika's OOXML parsing and the
 * {@link Office#HAS_UNREFERENCED_PARTS} signal. Tika (like Office) loads content by
 * following the OPC relationship graph, so a declared part that nothing references is
 * carried in the file but sits outside the parsed structure -- a place to hide bytes a
 * raw-ZIP reader (AV/DLP/CDR) can still see.
 *
 * <p>Two related facts pinned here:
 * <ul>
 *   <li>A part with NO content type is not a silent case at all: POI rejects the whole
 *       package at open time (OPC rule M.1.14), so it never reaches a successful parse.</li>
 *   <li>A part WITH a content type but no referencing relationship opens fine and is
 *       flagged. Whether its bytes are also read depends on type: a {@code application/zip}
 *       part is ignored, while a {@code wordprocessingml} part is still read by POI's
 *       content-type-based enumeration -- so "unreferenced" is not the same as "unparsed".</li>
 * </ul>
 *
 * <p>Consistent with Tika's <a href="https://tika.apache.org/security-model.html">security
 * model</a>: Tika is not a security boundary and this signal is best-effort and evadable.
 */
public class ZipStructureCoverageTest extends TikaTest {

    private static final String VISIBLE = "VISIBLE_BODY_TEXT_MARKER";
    private static final String ORPHAN = "ORPHAN_DECLARED_PART_MARKER";
    private static final String SMUGGLED = "SMUGGLED_UNREFERENCED_ZIP_MARKER";

    private static final String CONTENT_TYPES =
            "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
            + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
            + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
            + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
            + "<Default Extension=\"zip\" ContentType=\"application/zip\"/>"
            + "<Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/>"
            + "<Override PartName=\"/word/orphan.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/>"
            + "</Types>";

    private static final String RELS =
            "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
            + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
            + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/document.xml\"/>"
            + "</Relationships>";

    private static final String DOCUMENT =
            "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
            + "<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\">"
            + "<w:body><w:p><w:r><w:t>" + VISIBLE + "</w:t></w:r></w:p></w:body>"
            + "</w:document>";

    private static final String ORPHAN_DOC =
            "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
            + "<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\">"
            + "<w:body><w:p><w:r><w:t>" + ORPHAN + "</w:t></w:r></w:p></w:body>"
            + "</w:document>";

    private static final String SMUGGLED_PAYLOAD = "not-really-a-zip " + SMUGGLED;

    private void put(ZipOutputStream zos, String name, String content) throws Exception {
        zos.putNextEntry(new ZipEntry(name));
        zos.write(content.getBytes(StandardCharsets.UTF_8));
        zos.closeEntry();
    }

    private String allContent(List<Metadata> list) {
        StringBuilder sb = new StringBuilder();
        for (Metadata m : list) {
            String c = m.get(TikaCoreProperties.TIKA_CONTENT);
            if (c != null) {
                sb.append(c).append('\n');
            }
        }
        return sb.toString();
    }

    @Test
    public void testUnreferencedPartsAreFlagged() throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(bos)) {
            put(zos, "[Content_Types].xml", CONTENT_TYPES);
            put(zos, "_rels/.rels", RELS);
            put(zos, "word/document.xml", DOCUMENT);
            // declared (wordprocessingml) but referenced by nothing
            put(zos, "word/orphan.xml", ORPHAN_DOC);
            // declared (application/zip) but referenced by nothing
            put(zos, "my-stuff/please-ignore.zip", SMUGGLED_PAYLOAD);
        }

        List<Metadata> metadataList = getRecursiveMetadata(TikaInputStream.get(bos.toByteArray()), true);
        Metadata container = metadataList.get(0);
        String content = allContent(metadataList);

        // Sanity: the referenced body is extracted.
        assertTrue(content.contains(VISIBLE), "referenced body text should be extracted");
        // "unreferenced" is not "unparsed": the wordprocessingml orphan is still read by
        // POI's content-type enumeration, but the application/zip part is not.
        assertTrue(content.contains(ORPHAN), "unreferenced wordprocessingml part is still read");
        assertFalse(content.contains(SMUGGLED), "unreferenced application/zip part is not parsed");

        // The signal fires and names both unreferenced parts.
        assertEquals("true", container.get(Office.HAS_UNREFERENCED_PARTS));
        List<String> names = Arrays.asList(container.getValues(Office.UNREFERENCED_PART_NAMES));
        assertTrue(names.contains("/my-stuff/please-ignore.zip"),
                "should list the unreferenced zip; got: " + names);
        assertTrue(names.contains("/word/orphan.xml"),
                "should list the unreferenced orphan; got: " + names);
    }

    @Test
    public void testCleanPackageHasNoUnreferencedFlag() throws Exception {
        // Every declared part is referenced from the root relationship graph.
        String cleanContentTypes =
                "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
                + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
                + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
                + "<Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/>"
                + "</Types>";
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(bos)) {
            put(zos, "[Content_Types].xml", cleanContentTypes);
            put(zos, "_rels/.rels", RELS);
            put(zos, "word/document.xml", DOCUMENT);
        }
        List<Metadata> metadataList =
                getRecursiveMetadata(TikaInputStream.get(bos.toByteArray()), true);
        Metadata container = metadataList.get(0);
        assertTrue(allContent(metadataList).contains(VISIBLE));
        assertNull(container.get(Office.HAS_UNREFERENCED_PARTS),
                "clean package must not set HAS_UNREFERENCED_PARTS; listed: "
                        + Arrays.toString(container.getValues(Office.UNREFERENCED_PART_NAMES)));
    }

    /**
     * XPS wires documents, pages and resources together through markup, not OPC
     * relationships, so a normal XPS would otherwise flag nearly every part.
     */
    @Test
    public void testXpsIsNotFlagged() throws Exception {
        Metadata m = getXML("testXPS_various.xps").metadata;
        assertNull(m.get(Office.HAS_UNREFERENCED_PARTS),
                "XPS must not be flagged; listed: "
                        + Arrays.toString(m.getValues(Office.UNREFERENCED_PART_NAMES)));
    }
}
