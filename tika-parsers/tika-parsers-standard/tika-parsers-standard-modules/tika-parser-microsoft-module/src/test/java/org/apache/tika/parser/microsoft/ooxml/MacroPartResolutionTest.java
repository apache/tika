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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;

import org.apache.tika.TikaTest;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.microsoft.OfficeParserConfig;

/**
 * Guards that Tika resolves the VBA project through the OPC relationship graph,
 * NOT by raw-zip filename-suffix / stream order.
 *
 * <p>POI's {@code VBAMacroReader.openOOXML} selects the VBA project as the first
 * zip entry (in stream order) whose name ends with "vbaProject.bin", bypassing
 * OPC. A tool built directly on that (an AV/DLP/CDR macro scanner) can be fooled
 * by a decoy vbaProject.bin ordered before the real one, while Office executes
 * the relationship-referenced part (CWE-436 parser differential; reported against
 * POI by n0mi1k, fixed there as a plain bug).
 *
 * <p>Tika does not use that path: it constructs {@code VBAMacroReader} only from a
 * {@code POIFSFileSystem} built from the OPC-resolved {@code vbaProject} part. This
 * test crafts an .xlsm with a decoy vbaProject.bin (a DIFFERENT, valid VBA project
 * lifted from testWORD_macros.docm) placed first in stream order, and asserts Tika
 * still extracts the real /xl/vbaProject.bin macro. If a future change routes macro
 * extraction through the raw-zip path, this fails.
 */
public class MacroPartResolutionTest extends TikaTest {

    private static final String REAL_MACRO = "Sub Dirty()";      // testEXCEL_macro.xlsm
    private static final String DECOY_MACRO = "Sub Embolden()";  // testWORD_macros.docm

    private byte[] resourceBytes(String name) throws Exception {
        try (TikaInputStream tis = getResourceAsStream("/test-documents/" + name)) {
            return tis.readAllBytes();
        }
    }

    private byte[] entryEndingWith(byte[] zip, String suffix) throws Exception {
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry e;
            byte[] buf = new byte[8192];
            while ((e = zis.getNextEntry()) != null) {
                if (e.getName().toLowerCase(java.util.Locale.ROOT).endsWith(suffix)) {
                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    int n;
                    while ((n = zis.read(buf)) > 0) {
                        bos.write(buf, 0, n);
                    }
                    return bos.toByteArray();
                }
            }
        }
        throw new IllegalStateException("no entry ending with " + suffix);
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

    private List<Metadata> parseWithMacros(byte[] bytes) throws Exception {
        ParseContext context = new ParseContext();
        OfficeParserConfig config = new OfficeParserConfig();
        config.setExtractMacros(true);
        context.set(OfficeParserConfig.class, config);
        return getRecursiveMetadata(TikaInputStream.get(bytes), new Metadata(), context, false);
    }

    @Test
    public void testVbaProjectResolvedViaOpcNotStreamOrder() throws Exception {
        byte[] xlsm = resourceBytes("testEXCEL_macro.xlsm");
        // A different, valid VBA project to act as the decoy.
        byte[] decoyVba = entryEndingWith(resourceBytes("testWORD_macros.docm"), "vbaproject.bin");

        // Sanity: the two projects have distinct, non-overlapping macro signatures.
        String realOnly = allContent(parseWithMacros(xlsm));
        assertTrue(realOnly.contains(REAL_MACRO), "baseline xlsm should expose real macro");
        assertFalse(realOnly.contains(DECOY_MACRO), "baseline xlsm must not contain decoy macro");

        // Craft an .xlsm whose FIRST *vbaProject.bin entry (stream order) is the
        // undeclared decoy, with the real, relationship-referenced /xl/vbaProject.bin
        // kept intact later in the stream.
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(bos)) {
            zos.putNextEntry(new ZipEntry("xl/decoy/vbaProject.bin")); // undeclared, first in stream
            zos.write(decoyVba);
            zos.closeEntry();
            try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(xlsm))) {
                ZipEntry e;
                byte[] buf = new byte[8192];
                while ((e = zis.getNextEntry()) != null) {
                    zos.putNextEntry(new ZipEntry(e.getName()));
                    int n;
                    while ((n = zis.read(buf)) > 0) {
                        zos.write(buf, 0, n);
                    }
                    zos.closeEntry();
                }
            }
        }

        String crafted = allContent(parseWithMacros(bos.toByteArray()));
        // Tika must extract the OPC-resolved real macro, and must NOT have been
        // steered to the stream-order decoy.
        assertTrue(crafted.contains(REAL_MACRO),
                "Tika must extract the OPC-resolved /xl/vbaProject.bin macro");
        assertFalse(crafted.contains(DECOY_MACRO),
                "Tika must not extract the stream-order decoy vbaProject.bin macro");
    }
}
