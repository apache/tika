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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;

import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;

/**
 * The document parsers report the external DTDs and entities a file would have fetched.
 */
public class XmlExternalReferenceMetadataTest extends XMLTestBase {

    private static final String DTD = "file:///couldnt_possibly_exist/xxe.dtd";
    private static final byte[] DOCTYPE =
            ("<!DOCTYPE roottag SYSTEM \"" + DTD + "\">").getBytes(StandardCharsets.UTF_8);

    @Test
    public void testXmlFile() throws Exception {
        byte[] injected = injectXML("<?xml version=\"1.0\"?><r>text</r>".getBytes(StandardCharsets.UTF_8),
                DOCTYPE);
        Metadata m = getXML(TikaInputStream.get(injected), AUTO_DETECT_PARSER, new Metadata()).metadata;
        assertEquals(DTD, m.get(TikaCoreProperties.XML_EXTERNAL_REFERENCE));
        assertEquals(1, m.getInt(TikaCoreProperties.XML_EXTERNAL_REFERENCE_COUNT));
        assertNull(m.get(TikaCoreProperties.XML_EXTERNAL_REFERENCE_EMBEDDED));
    }

    @Test
    public void testCleanXmlFile() throws Exception {
        Metadata m = getXML(TikaInputStream.get("<r>text</r>".getBytes(StandardCharsets.UTF_8)),
                AUTO_DETECT_PARSER, new Metadata()).metadata;
        assertNull(m.get(TikaCoreProperties.XML_EXTERNAL_REFERENCE));
        assertNull(m.get(TikaCoreProperties.XML_EXTERNAL_REFERENCE_COUNT));
    }

    // EPUB parts are parsed inline, so the container itself carries the ids
    @Test
    public void testEpubPartsLandOnContainer() throws Exception {
        Path injected;
        try (TikaInputStream tis = TikaInputStream.get(
                getClass().getResourceAsStream("/test-documents/testEPUB.epub"))) {
            injected = injectZippedXMLs(tis.getPath(), DOCTYPE, false);
        }
        try {
            Metadata container = getRecursiveMetadata(injected).get(0);
            assertEquals(DTD, container.get(TikaCoreProperties.XML_EXTERNAL_REFERENCE));
            assertTrue(container.getInt(TikaCoreProperties.XML_EXTERNAL_REFERENCE_COUNT) >= 1);
            assertNull(container.get(TikaCoreProperties.XML_EXTERNAL_REFERENCE_EMBEDDED));
        } finally {
            Files.delete(injected);
        }
    }

    // an XML file inside a zip is an embedded document: it carries the ids, the zip is flagged
    @Test
    public void testEmbeddedXmlFlagsContainer() throws Exception {
        Path zip = Files.createTempFile("tika-xxe-", ".zip");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
            out.putNextEntry(new ZipEntry("inner.xml"));
            out.write(injectXML("<?xml version=\"1.0\"?><r>text</r>".getBytes(StandardCharsets.UTF_8),
                    DOCTYPE));
            out.closeEntry();
        }
        try {
            List<Metadata> all = getRecursiveMetadata(zip);
            assertEquals(2, all.size());
            Metadata container = all.get(0);
            Metadata inner = all.get(1);
            assertEquals("true", container.get(TikaCoreProperties.XML_EXTERNAL_REFERENCE_EMBEDDED));
            assertNull(container.get(TikaCoreProperties.XML_EXTERNAL_REFERENCE));
            assertEquals(DTD, inner.get(TikaCoreProperties.XML_EXTERNAL_REFERENCE));
            assertEquals(1, inner.getInt(TikaCoreProperties.XML_EXTERNAL_REFERENCE_COUNT));
        } finally {
            Files.delete(zip);
        }
    }
}
