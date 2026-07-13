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
 * The SAX xlsx sheet handler must not abort the whole workbook on a malformed cell/row.
 * Each case injects one non-standard construct into sheet1.xml and asserts the workbook
 * still parses (other cells' numbers still come through).
 */
public class XSSFMalformedSheetTest extends TikaTest {

    // A stable numeric cell present in testEXCEL.xlsx, recovered when parsing survives.
    private static final String SURVIVES = "<td>144</td>";

    @Test
    public void testNonNumericRowNumber() throws Exception {
        Metadata m = parseWithSheetEdit("<row r=\"1\"", "<row r=\"x\"");
        assertContains(SURVIVES, m.get(TikaCoreProperties.TIKA_CONTENT));
    }

    @Test
    public void testNonNumericStyleIndex() throws Exception {
        //B6 is a numeric cell (no t) -- the style-index parse only runs on that branch
        Metadata m = parseWithSheetEdit("<c r=\"B6\">", "<c r=\"B6\" s=\"x\">");
        assertContains(SURVIVES, m.get(TikaCoreProperties.TIKA_CONTENT));
    }

    @Test
    public void testBooleanCellEmptyValue() throws Exception {
        Metadata m = parseWithSheetEdit("<c r=\"B6\"><v>1</v></c>", "<c r=\"B6\" t=\"b\"><v></v></c>");
        assertContains(SURVIVES, m.get(TikaCoreProperties.TIKA_CONTENT));
    }

    @Test
    public void testMalformedCellReference() throws Exception {
        Metadata m = parseWithSheetEdit("<c r=\"B6\">", "<c r=\"1A\">");
        assertContains(SURVIVES, m.get(TikaCoreProperties.TIKA_CONTENT));
    }

    private Metadata parseWithSheetEdit(String find, String replace) throws Exception {
        byte[] xlsx = editSheet1("testEXCEL.xlsx", find, replace);
        List<Metadata> metadataList;
        try (TikaInputStream tis = TikaInputStream.get(xlsx)) {
            metadataList = getRecursiveMetadata(tis, new Metadata(), new ParseContext(), false);
        }
        assertEquals(1, metadataList.size());
        return metadataList.get(0);
    }

    private byte[] editSheet1(String resource, String find, String replace) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipArchiveInputStream zin =
                     new ZipArchiveInputStream(getResourceAsStream("/test-documents/" + resource));
             ZipArchiveOutputStream zout = new ZipArchiveOutputStream(bos)) {
            ZipArchiveEntry entry;
            while ((entry = zin.getNextEntry()) != null) {
                zout.putArchiveEntry(new ZipArchiveEntry(entry.getName()));
                if ("xl/worksheets/sheet1.xml".equals(entry.getName())) {
                    String xml = new String(IOUtils.toByteArray(zin), StandardCharsets.UTF_8);
                    zout.write(xml.replace(find, replace).getBytes(StandardCharsets.UTF_8));
                } else {
                    IOUtils.copy(zin, zout);
                }
                zout.closeArchiveEntry();
            }
        }
        return bos.toByteArray();
    }
}
