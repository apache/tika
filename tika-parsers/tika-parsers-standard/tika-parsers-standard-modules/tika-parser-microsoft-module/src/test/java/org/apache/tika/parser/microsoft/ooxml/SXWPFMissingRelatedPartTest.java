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
import java.util.List;

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
 * A docx that declares a relationship to an optional part (numbering.xml, settings.xml, ...)
 * whose target is missing makes POI's getRelatedPart throw an unchecked
 * IllegalArgumentException. The SAX docx extractor must skip it, not abort the whole parse.
 * Regression guard for the 3.3.2 tika-eval finding: 740 docx crashed this way.
 */
public class SXWPFMissingRelatedPartTest extends TikaTest {

    @Test
    public void testMissingNumberingPart() throws Exception {
        assertParsesWithoutPart("word/numbering.xml");
    }

    @Test
    public void testMissingSettingsPart() throws Exception {
        assertParsesWithoutPart("word/settings.xml");
    }

    @Test
    public void testMissingStylesPart() throws Exception {
        assertParsesWithoutPart("word/styles.xml");
    }

    @Test
    public void testMissingWebSettingsPart() throws Exception {
        assertParsesWithoutPart("word/webSettings.xml");
    }

    /** Drops partToDrop (leaving its dangling relationship) and asserts the docx still parses. */
    private void assertParsesWithoutPart(String partToDrop) throws Exception {
        byte[] docx = docxWithoutPart("testWORD_numbered_list.docx", partToDrop);
        List<Metadata> metadataList;
        try (TikaInputStream tis = TikaInputStream.get(docx)) {
            metadataList = getRecursiveMetadata(tis, new Metadata(), new ParseContext(), false);
        }
        assertEquals(1, metadataList.size(), "dropped " + partToDrop);
        Metadata m = metadataList.get(0);
        assertEquals("application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                m.get(Metadata.CONTENT_TYPE), "dropped " + partToDrop);
        String content = m.get(TikaCoreProperties.TIKA_CONTENT);
        assertNotNull(content, "dropped " + partToDrop);
        //body text still recovered, not lost to the abort
        assertContains("This is another list", content);
        assertContains("Within cell 1", content);
    }

    /** Copies the resource docx, omitting a single zip entry. */
    private byte[] docxWithoutPart(String resource, String entryName) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipArchiveInputStream zin =
                     new ZipArchiveInputStream(getResourceAsStream("/test-documents/" + resource));
             ZipArchiveOutputStream zout = new ZipArchiveOutputStream(bos)) {
            ZipArchiveEntry entry;
            while ((entry = zin.getNextEntry()) != null) {
                if (entry.getName().equals(entryName)) {
                    continue;
                }
                zout.putArchiveEntry(new ZipArchiveEntry(entry.getName()));
                IOUtils.copy(zin, zout);
                zout.closeArchiveEntry();
            }
        }
        return bos.toByteArray();
    }
}
