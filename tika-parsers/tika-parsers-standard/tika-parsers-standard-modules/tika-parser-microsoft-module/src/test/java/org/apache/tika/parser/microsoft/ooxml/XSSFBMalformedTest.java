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
import java.util.List;
import java.util.Set;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;

import org.apache.tika.TikaTest;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;

/**
 * The binary xlsb path must degrade the same way the xlsx path does when a part is missing.
 * These drop parts a malformed/truncated xlsb might lack and assert the workbook still parses.
 */
public class XSSFBMalformedTest extends TikaTest {

    private static final String XLSB_CT =
            "application/vnd.ms-excel.sheet.binary.macroenabled.12";

    // Cells still carry SST indices, but the string table is gone: getItemAt(idx) is out
    // of range for the empty table -- must not IndexOutOfBounds out of the whole workbook.
    @Test
    public void testMissingSharedStrings() throws Exception {
        Metadata m = parseWithout("testEXCEL.xlsb", Set.of("xl/sharedStrings.bin"));
        assertEquals(XLSB_CT, m.get(Metadata.CONTENT_TYPE));
    }

    // A workbook that references a sheet part missing from the package.
    @Test
    public void testMissingSheetPart() throws Exception {
        Metadata m = parseWithout("testEXCEL.xlsb", Set.of("xl/worksheets/sheet1.bin"));
        assertEquals(XLSB_CT, m.get(Metadata.CONTENT_TYPE));
    }

    private Metadata parseWithout(String resource, Set<String> toDrop) throws Exception {
        byte[] xlsb = drop(resource, toDrop);
        List<Metadata> metadataList;
        try (TikaInputStream tis = TikaInputStream.get(xlsb)) {
            metadataList = getRecursiveMetadata(tis, new Metadata(), new ParseContext(), false);
        }
        assertEquals(1, metadataList.size());
        return metadataList.get(0);
    }

    private byte[] drop(String resource, Set<String> toDrop) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipArchiveInputStream zin =
                     new ZipArchiveInputStream(getResourceAsStream("/test-documents/" + resource));
             ZipArchiveOutputStream zout = new ZipArchiveOutputStream(bos)) {
            ZipArchiveEntry entry;
            while ((entry = zin.getNextEntry()) != null) {
                if (toDrop.contains(entry.getName())) {
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
