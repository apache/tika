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
package org.apache.tika.parser.pkg;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.junit.jupiter.api.Test;

import org.apache.tika.TikaTest;
import org.apache.tika.detect.CompositeEncodingDetector;
import org.apache.tika.detect.EncodingDetector;
import org.apache.tika.detect.MetadataCharsetDetector;
import org.apache.tika.detect.OverrideEncodingDetector;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;

/**
 * TIKA-4752: a zip can only declare an entry name as UTF-8 (never a legacy charset),
 * two ways -- the EFS flag (general purpose bit 11) and the Unicode path extra field.
 * ZipParser must honor both.
 */
public class ZipEntryNameEncodingTest extends TikaTest {

    private static final String LATIN = "café-Köln-Süß.txt";
    private static final String CJK = "日本語.txt";

    @Test
    public void testEfsFlagHint() throws Exception {
        // Deterministic + discriminating: MetadataCharsetDetector consumes the
        // EFS->UTF-8 hint; the override garbles anything it doesn't catch. So only the
        // hint yields UTF-8 -- an empty-returning detector wouldn't isolate it, because
        // ZipParser would fall back to getName(), already UTF-8 for a flagged entry.
        ParseContext context = new ParseContext();
        context.set(EncodingDetector.class, new CompositeEncodingDetector(List.of(
                new MetadataCharsetDetector(),
                new OverrideEncodingDetector(Charset.forName("windows-1252")))));
        assertEquals(LATIN, entryName(efsZip(LATIN), context));
    }

    @Test
    public void testUnicodeExtraField() throws Exception {
        // CRC-validated UTF-8 name in the extra field; the main-header name is a garbled
        // CP437 fallback. We must use the extra-field name, not detect the raw bytes.
        assertEquals(CJK, entryName(unicodeExtraFieldZip(CJK), new ParseContext()));
    }

    private String entryName(byte[] zipBytes, ParseContext context) throws Exception {
        try (TikaInputStream tis = TikaInputStream.get(zipBytes)) {
            List<Metadata> list = getRecursiveMetadata(tis, new Metadata(), context, false);
            assertEquals(2, list.size());
            return list.get(1).get(TikaCoreProperties.RESOURCE_NAME_KEY);
        }
    }

    private static byte[] efsZip(String name) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipArchiveOutputStream zos = new ZipArchiveOutputStream(bos)) {
            zos.setEncoding("UTF-8");
            zos.setUseLanguageEncodingFlag(true);
            zos.setCreateUnicodeExtraFields(ZipArchiveOutputStream.UnicodeExtraFieldPolicy.NEVER);
            writeEntry(zos, name);
        }
        return bos.toByteArray();
    }

    private static byte[] unicodeExtraFieldZip(String name) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipArchiveOutputStream zos = new ZipArchiveOutputStream(bos)) {
            zos.setEncoding("Cp437");
            zos.setUseLanguageEncodingFlag(false);
            zos.setCreateUnicodeExtraFields(ZipArchiveOutputStream.UnicodeExtraFieldPolicy.ALWAYS);
            writeEntry(zos, name);
        }
        return bos.toByteArray();
    }

    private static void writeEntry(ZipArchiveOutputStream zos, String name) throws IOException {
        ZipArchiveEntry entry = new ZipArchiveEntry(name);
        zos.putArchiveEntry(entry);
        zos.write("hello".getBytes(StandardCharsets.US_ASCII));
        zos.closeArchiveEntry();
    }
}
