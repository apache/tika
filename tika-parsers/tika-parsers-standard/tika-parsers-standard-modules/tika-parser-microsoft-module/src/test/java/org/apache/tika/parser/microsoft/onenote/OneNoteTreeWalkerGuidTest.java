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
package org.apache.tika.parser.microsoft.onenote;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.xml.sax.SAXException;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.OneNote;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.ToTextContentHandler;
import org.apache.tika.sax.XHTMLContentHandler;

public class OneNoteTreeWalkerGuidTest {

    @Test
    public void testGuidBagsPublishAfterWalkFailure() throws Exception {
        Metadata metadata = new Metadata();
        OneNoteTreeWalker walker = newWalker(metadata, true, null);
        walker.addClassicEntityGuid(OneNoteJcid.PAGE_METADATA, "page-guid");
        walker.addClassicEntityGuid(OneNoteJcid.PAGE_SERIES_NODE, "series-guid");
        walker.addClassicEntityGuid(OneNoteJcid.CONFLICT_PAGE_METADATA, "conflict-guid");
        walker.addClassicEntityGuid(OneNoteJcid.SECTION_NODE, "section-node-guid");
        walker.addClassicEntityGuid(0x7fff, "entity-guid");
        for (int i = 0; i < OneNoteGuidCollector.MAX_GUID_COUNT - 4; i++) {
            walker.addClassicEntityGuid(0x7fff, "entity-" + i);
        }
        walker.addClassicEntityGuid(0x7fff, "overflow-guid");

        assertThrows(TikaException.class, walker::walkTree);
        assertEquals(List.of("page-guid"),
                Arrays.asList(metadata.getValues(OneNote.PAGE_GUIDS)));
        assertEquals(List.of("series-guid"),
                Arrays.asList(metadata.getValues(OneNote.PAGE_SERIES_GUIDS)));
        assertEquals(List.of("conflict-guid"),
                Arrays.asList(metadata.getValues(OneNote.CONFLICT_PAGE_GUIDS)));
        assertEquals(OneNoteGuidCollector.MAX_GUID_COUNT - 3,
                metadata.getValues(OneNote.ENTITY_GUIDS).length);
        assertFalse(Arrays.asList(metadata.getValues(OneNote.ENTITY_GUIDS))
                .contains("section-node-guid"));
        assertEquals(1, Arrays.stream(metadata.getValues(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING))
                .filter(w -> w.contains("Capping OneNote GUID metadata")).count());
    }

    @Test
    public void testGuidLimitWithNoMetadataParent() throws Exception {
        OneNoteTreeWalker walker = newWalker(null, false, null);
        for (int i = 0; i < OneNoteGuidCollector.MAX_GUID_COUNT; i++) {
            walker.addClassicEntityGuid(OneNoteJcid.PAGE_METADATA, "guid-" + i);
        }
        walker.addClassicEntityGuid(OneNoteJcid.PAGE_METADATA, "overflow-guid");
        walker.walkTree();
    }

    @Test
    public void testJcidIndexRequiresCompleteJcid() {
        assertEquals(-1, OneNoteTreeWalker.jcidIndex(null));
        FileNode fileNode = new FileNode();
        assertEquals(0, OneNoteTreeWalker.jcidIndex(fileNode));
        fileNode.subType = null;
        assertEquals(-1, OneNoteTreeWalker.jcidIndex(fileNode));
        fileNode.subType = new FileNodeUnion();
        fileNode.subType.objectDeclarationWithRefCount = null;
        assertEquals(-1, OneNoteTreeWalker.jcidIndex(fileNode));
        fileNode.subType.objectDeclarationWithRefCount = new ObjectDeclarationWithRefCount();
        fileNode.subType.objectDeclarationWithRefCount.body = null;
        assertEquals(-1, OneNoteTreeWalker.jcidIndex(fileNode));
        fileNode.subType.objectDeclarationWithRefCount.body =
                new ObjectDeclarationWithRefCountBody();
        fileNode.subType.objectDeclarationWithRefCount.body.jcid = null;
        assertEquals(-1, OneNoteTreeWalker.jcidIndex(fileNode));
        fileNode.subType.objectDeclarationWithRefCount.body.jcid = new JCID();
        fileNode.subType.objectDeclarationWithRefCount.body.jcid.index = OneNoteJcid.PAGE_METADATA;
        assertEquals(OneNoteJcid.PAGE_METADATA, OneNoteTreeWalker.jcidIndex(fileNode));
    }

    @Test
    public void testNotebookGuidRequiresSixteenBytes(@TempDir Path tempDir) throws Exception {
        byte[] guidBytes = new byte[] {
                0x33, 0x22, 0x11, 0x00, 0x55, 0x44, 0x77, 0x66,
                (byte) 0x88, (byte) 0x99, (byte) 0xaa, (byte) 0xbb,
                (byte) 0xcc, (byte) 0xdd, (byte) 0xee, (byte) 0xff
        };
        Path input = tempDir.resolve("guid-data.bin");
        Files.write(input, guidBytes);
        Metadata metadata = new Metadata();
        ParseContext context = new ParseContext();
        try (OneNoteDirectFileResource dif = new OneNoteDirectFileResource(input.toFile())) {
            OneNoteTreeWalker walker = newWalker(metadata, false, dif);
            PropertyValue valid = notebookGuidProperty(guidBytes.length);
            Map<String, Object> parsed = walker.processPropertyValue(valid, null,
                    OneNoteJcid.PAGE_METADATA);
            assertEquals("{00112233-4455-6677-8899-AABBCCDDEEFF}",
                    parsed.get("notebookManagementEntityGuid"));

            PropertyValue shortGuid = notebookGuidProperty(15);
            Map<String, Object> malformed = walker.processPropertyValue(shortGuid, null,
                    OneNoteJcid.PAGE_METADATA);
            assertFalse(malformed.containsKey("notebookManagementEntityGuid"));
            walker.walkTree();
        }
        assertEquals(List.of("{00112233-4455-6677-8899-AABBCCDDEEFF}"),
                Arrays.asList(metadata.getValues(OneNote.PAGE_GUIDS)));
    }

    private static PropertyValue notebookGuidProperty(int length) {
        PropertyValue value = new PropertyValue();
        value.propertyId.propertyEnum = OneNotePropertyEnum.NotebookManagementEntityGuid;
        value.propertyId.type = 7;
        value.rawData.setStp(0).setCb(length);
        return value;
    }

    private static OneNoteTreeWalker newWalker(Metadata metadata, boolean fail,
                                                OneNoteDirectFileResource dif) {
        ParseContext context = new ParseContext();
        Metadata handlerMetadata = metadata == null ? new Metadata() : metadata;
        XHTMLContentHandler xhtml = new XHTMLContentHandler(
                new ToTextContentHandler(), handlerMetadata, context);
        return new OneNoteTreeWalker(new OneNoteTreeWalkerOptions(), new OneNoteDocument(), dif,
                xhtml, metadata, context, Pair.of(1L, ExtendedGUID.nil())) {
            @Override
            public List<Map<String, Object>> walkRootFileNodes()
                    throws IOException, TikaException, SAXException {
                if (fail) {
                    throw new TikaException("test walk failure");
                }
                return Collections.emptyList();
            }
        };
    }
}
