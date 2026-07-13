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
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;

/**
 * XWPFNumberingShim sizes a dense per-level array to the largest w:ilvl it sees. An
 * out-of-range ilvl (OOXML defines only levels 0-8) would ask for a ~2-billion-element
 * array and OOM -- an Error that escapes every catch in the chain and drops the file.
 * The out-of-range level must be ignored.
 */
public class SXWPFNonStandardNumberingTest extends TikaTest {

    // A well-formed w:lvl with an out-of-range ilvl, injected into an abstractNum.
    private static final String OUT_OF_RANGE_LVL =
            "<w:lvl w:ilvl=\"2000000000\">" +
            "<w:start w:val=\"1\"/><w:numFmt w:val=\"decimal\"/><w:lvlText w:val=\"%1.\"/>" +
            "</w:lvl>";

    @Test
    public void testOutOfRangeIlvlDoesNotOOM() throws Exception {
        byte[] docx = docxWithInjectedLevel("testWORD_numbered_list.docx");
        List<Metadata> metadataList;
        try (TikaInputStream tis = TikaInputStream.get(docx)) {
            metadataList = getRecursiveMetadata(tis, new Metadata(), new ParseContext(), false);
        }
        assertEquals(1, metadataList.size());
        Metadata m = metadataList.get(0);
        assertEquals("application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                m.get(Metadata.CONTENT_TYPE));
        //the out-of-range level is ignored; the real list content still comes through
        assertContains("This is another list", m.get(TikaCoreProperties.TIKA_CONTENT));
    }

    // Injects OUT_OF_RANGE_LVL just before the first </w:abstractNum> in numbering.xml.
    private byte[] docxWithInjectedLevel(String resource) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipArchiveInputStream zin =
                     new ZipArchiveInputStream(getResourceAsStream("/test-documents/" + resource));
             ZipArchiveOutputStream zout = new ZipArchiveOutputStream(bos)) {
            ZipArchiveEntry entry;
            while ((entry = zin.getNextEntry()) != null) {
                zout.putArchiveEntry(new ZipArchiveEntry(entry.getName()));
                if ("word/numbering.xml".equals(entry.getName())) {
                    String xml = new String(IOUtils.toByteArray(zin), StandardCharsets.UTF_8);
                    xml = xml.replaceFirst("</w:abstractNum>", OUT_OF_RANGE_LVL + "</w:abstractNum>");
                    zout.write(xml.getBytes(StandardCharsets.UTF_8));
                } else {
                    IOUtils.copy(zin, zout);
                }
                zout.closeArchiveEntry();
            }
        }
        return bos.toByteArray();
    }
}
