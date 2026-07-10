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
import java.io.InputStream;
import java.util.List;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;

import org.apache.tika.TikaTest;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.microsoft.OfficeParserConfig;

/**
 * A docx may declare a relationship to an optional part (numbering.xml,
 * settings.xml, ...) whose target is missing from the package -- a truncated or
 * otherwise malformed file. POI's {@code PackagePart.getRelatedPart} then throws
 * an unchecked {@code IllegalArgumentException}. The streaming (SAX) docx
 * extractor must skip the missing part and keep going rather than aborting the
 * whole parse (which also drops the file's embedded documents).
 *
 * <p>Regression guard for the 3.3.2 tika-eval finding: 740 docx crashed on a
 * missing numbering.xml (484) or settings.xml (254) because those
 * getRelatedPart calls bypassed {@code safeGetRelatedPart}.
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

    /**
     * Strips {@code partToDrop} from a known-good docx (leaving the dangling
     * relationship in document.xml.rels) and asserts the SAX docx extractor
     * still parses it -- without the fix, suppressException=false rethrows the
     * IllegalArgumentException and this fails.
     */
    private void assertParsesWithoutPart(String partToDrop) throws Exception {
        byte[] docx = docxWithoutPart("testWORD_numbered_list.docx", partToDrop);
        List<Metadata> metadataList;
        try (InputStream is = new ByteArrayInputStream(docx)) {
            metadataList = getRecursiveMetadata(is, new Metadata(), saxDocxContext(), false);
        }
        assertEquals(1, metadataList.size(), "dropped " + partToDrop);
        Metadata m = metadataList.get(0);
        assertEquals("application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                m.get(Metadata.CONTENT_TYPE), "dropped " + partToDrop);
        String content = m.get(TikaCoreProperties.TIKA_CONTENT);
        assertNotNull(content, "dropped " + partToDrop);
        //body text is still recovered, not lost to a catastrophic abort
        assertContains("This is another list", content);
        assertContains("Within cell 1", content);
    }

    private ParseContext saxDocxContext() {
        ParseContext pc = new ParseContext();
        OfficeParserConfig config = new OfficeParserConfig();
        config.setUseSAXDocxExtractor(true);
        pc.set(OfficeParserConfig.class, config);
        return pc;
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
