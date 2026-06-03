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
package org.apache.tika.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;

import org.apache.tika.TikaTest;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;

/**
 * Android Binary XML (AXML) is the compiled binary form of AndroidManifest.xml and the
 * res/*.xml resources packed inside an APK. Those entries keep a .xml extension and live
 * inside the (zip) APK, so before TIKA-4747 the *.xml glob caused them to be detected as
 * application/xml and handed to the XML parser, which failed on the binary header with
 * "Invalid byte 1 of 1-byte UTF-8 sequence". This was a large source of exceptions in
 * regression runs over APK-heavy corpora.
 *
 * <p>Real corpus APKs can't be committed, so this builds an equivalent zip in memory:
 * two compiled (AXML) entries plus one genuine text-XML entry under assets/ as a control,
 * and asserts the AXML entries are detected as application/vnd.android.axml and produce no
 * exception, while the text-XML entry is still application/xml.
 */
public class AndroidBinaryXMLTest extends TikaTest {

    private static final String AXML = "application/vnd.android.axml";

    /**
     * Minimal but structurally-plausible Android Binary XML header:
     * ResChunk_header {type=RES_XML_TYPE(0x0003), headerSize=0x0008, size=&lt;total&gt;}
     * followed by a zeroed ResStringPool_header. Only the leading 4 bytes (0x00080003 LE)
     * are the detection signature; the following 4 bytes are the per-file size.
     */
    private static byte[] axmlBytes() {
        ByteBuffer bb = ByteBuffer.allocate(64).order(ByteOrder.LITTLE_ENDIAN);
        bb.putShort((short) 0x0003);   // RES_XML_TYPE
        bb.putShort((short) 0x0008);   // headerSize
        bb.putInt(0x00000038);         // string pool chunk size
        // remaining bytes (string/style counts, flags, offsets) left zero
        return bb.array();
    }

    private static byte[] zipWith(String[] names, byte[][] contents) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(bos)) {
            for (int i = 0; i < names.length; i++) {
                zos.putNextEntry(new ZipEntry(names[i]));
                zos.write(contents[i]);
                zos.closeEntry();
            }
        }
        return bos.toByteArray();
    }

    @Test
    public void testAxmlInsideZipNotRoutedToXmlParser() throws Exception {
        byte[] textXml =
                "<?xml version=\"1.0\"?><root><city>example</city></root>".getBytes(StandardCharsets.UTF_8);
        byte[] zip = zipWith(
                new String[] {"AndroidManifest.xml", "res/anim/anim0to1.xml", "assets/province_data.xml"},
                new byte[][] {axmlBytes(), axmlBytes(), textXml});

        List<Metadata> metadataList;
        try (TikaInputStream tis = TikaInputStream.get(zip)) {
            metadataList = getRecursiveMetadata(tis, true);
        }

        Metadata manifest = byPathSuffix(metadataList, "AndroidManifest.xml");
        Metadata resAnim = byPathSuffix(metadataList, "anim0to1.xml");
        Metadata assetXml = byPathSuffix(metadataList, "province_data.xml");

        // The two compiled AXML entries: detected as AXML, NOT routed to the XML parser.
        assertEquals(AXML, manifest.get(Metadata.CONTENT_TYPE));
        assertEquals(AXML, resAnim.get(Metadata.CONTENT_TYPE));
        assertNull(manifest.get(TikaCoreProperties.EMBEDDED_EXCEPTION),
                "AXML manifest must not throw a parse exception");
        assertNull(resAnim.get(TikaCoreProperties.EMBEDDED_EXCEPTION),
                "AXML resource must not throw a parse exception");

        // Control: a genuine text XML under assets/ is still detected and parsed as XML.
        assertEquals("application/xml", assetXml.get(Metadata.CONTENT_TYPE));
        assertNull(assetXml.get(TikaCoreProperties.EMBEDDED_EXCEPTION));
    }

    private static Metadata byPathSuffix(List<Metadata> metadataList, String suffix) {
        for (Metadata m : metadataList) {
            String path = m.get(TikaCoreProperties.EMBEDDED_RESOURCE_PATH);
            if (path != null && path.endsWith(suffix)) {
                return m;
            }
        }
        throw new AssertionError("No embedded entry found ending with: " + suffix);
    }
}
