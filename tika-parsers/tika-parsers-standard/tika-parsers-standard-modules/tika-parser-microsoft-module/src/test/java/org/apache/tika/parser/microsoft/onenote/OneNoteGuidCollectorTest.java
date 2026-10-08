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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.OneNote;

public class OneNoteGuidCollectorTest {

    @Test
    public void testCapsTotalGuidValuesAndWarnsOnce() {
        List<String> warnings = new ArrayList<>();
        OneNoteGuidCollector collector = new OneNoteGuidCollector(warnings::add);
        assertFalse(collector.isFull());
        collector.add(OneNoteGuidCollector.Category.PAGE, null);
        for (int i = 0; i < OneNoteGuidCollector.MAX_GUID_COUNT; i++) {
            collector.add(OneNoteGuidCollector.Category.CONFLICT_PAGE, "guid-" + i);
        }
        collector.add(OneNoteGuidCollector.Category.CONFLICT_PAGE, "guid-0");
        collector.add(OneNoteGuidCollector.Category.PAGE, "overflow-1");
        collector.add(OneNoteGuidCollector.Category.PAGE, "overflow-2");

        assertEquals(OneNoteGuidCollector.MAX_GUID_COUNT,
                collector.values(OneNoteGuidCollector.Category.CONFLICT_PAGE).size());
        assertTrue(collector.values(OneNoteGuidCollector.Category.PAGE).isEmpty());
        assertEquals(1, warnings.size());
        assertTrue(collector.isFull());
    }

    @Test
    public void testDuplicateValuesDoNotConsumeBudgetBeforeSaturation() {
        List<String> warnings = new ArrayList<>();
        OneNoteGuidCollector collector = new OneNoteGuidCollector(warnings::add);
        collector.add(OneNoteGuidCollector.Category.PAGE, "dup");
        collector.add(OneNoteGuidCollector.Category.PAGE, "dup");
        for (int i = 0; i < OneNoteGuidCollector.MAX_GUID_COUNT - 1; i++) {
            collector.add(OneNoteGuidCollector.Category.CONFLICT_PAGE, "e" + i);
        }
        collector.add(OneNoteGuidCollector.Category.CONFLICT_PAGE, "final-extra");

        assertEquals(1, collector.values(OneNoteGuidCollector.Category.PAGE).size());
        assertEquals(OneNoteGuidCollector.MAX_GUID_COUNT - 1,
                collector.values(OneNoteGuidCollector.Category.CONFLICT_PAGE).size());
        assertFalse(collector.values(OneNoteGuidCollector.Category.CONFLICT_PAGE)
                .contains("final-extra"));
        assertEquals(1, warnings.size());
        assertTrue(collector.isFull());
    }

    @Test
    public void testObjectTypesMapToKeys() {
        assertEquals(OneNoteGuidCollector.Category.PAGE,
                OneNoteGuidCollector.categoryFor(OneNoteJcid.PAGE_METADATA));
        assertEquals(OneNoteGuidCollector.Category.PAGE_SERIES,
                OneNoteGuidCollector.categoryFor(OneNoteJcid.PAGE_SERIES_NODE));
        assertEquals(OneNoteGuidCollector.Category.PAGE_NODE,
                OneNoteGuidCollector.categoryFor(OneNoteJcid.PAGE_NODE));
        assertEquals(OneNoteGuidCollector.Category.CONFLICT_PAGE,
                OneNoteGuidCollector.categoryFor(OneNoteJcid.CONFLICT_PAGE_METADATA));
        assertNull(OneNoteGuidCollector.categoryFor(OneNoteJcid.SECTION_NODE));
        assertNull(OneNoteGuidCollector.categoryFor(0x7fff));
    }

    @Test
    public void testPublishSortsValuesAndSkipsEmptyKeys() {
        OneNoteGuidCollector collector = new OneNoteGuidCollector(w -> { });
        collector.addForObjectType(OneNoteJcid.PAGE_NODE, "{B}");
        collector.addForObjectType(OneNoteJcid.PAGE_NODE, "{A}");
        collector.addForObjectType(OneNoteJcid.SECTION_NODE, "{section-node}");
        collector.add(OneNoteGuidCollector.Category.SECTION, "{first}");
        collector.add(OneNoteGuidCollector.Category.SECTION, "{second}");
        Metadata metadata = new Metadata();

        collector.publish(metadata);

        assertArrayEquals(new String[] {"{A}", "{B}"},
                metadata.getValues(OneNote.PAGE_NODE_GUIDS));
        assertEquals("{first}", metadata.get(OneNote.SECTION_GUID));
        assertEquals(2, metadata.names().length);
    }
}
