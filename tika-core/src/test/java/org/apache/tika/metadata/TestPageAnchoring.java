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
package org.apache.tika.metadata;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Arrays;
import java.util.Collections;

import org.junit.jupiter.api.Test;

public class TestPageAnchoring {

    @Test
    void nullPagesIsNoOp() {
        Metadata m = new Metadata();
        PageAnchoring.applyPageMetadata(m, null);
        assertNull(m.get(TikaPagedText.PAGE_NUMBER));
        assertNull(m.get(TikaPagedText.PAGE_NUMBERS));
    }

    @Test
    void nullTargetIsNoOp() {
        PageAnchoring.applyPageMetadata(null, Arrays.asList(1, 2));
    }

    @Test
    void emptyPagesMeansUnlinked() {
        Metadata m = new Metadata();
        PageAnchoring.applyPageMetadata(m, Collections.emptyList());
        assertArrayEquals(new int[]{TikaPagedText.UNLINKED_PAGE},
                m.getIntValues(TikaPagedText.PAGE_NUMBERS));
        // PAGE_NUMBER is intentionally unset when unlinked.
        assertNull(m.get(TikaPagedText.PAGE_NUMBER));
    }

    @Test
    void singlePageSetsBothProperties() {
        Metadata m = new Metadata();
        PageAnchoring.applyPageMetadata(m, Collections.singletonList(7));
        assertArrayEquals(new int[]{7}, m.getIntValues(TikaPagedText.PAGE_NUMBERS));
        assertEquals("7", m.get(TikaPagedText.PAGE_NUMBER));
    }

    @Test
    void multiPageSetsSequenceOnly() {
        Metadata m = new Metadata();
        PageAnchoring.applyPageMetadata(m, Arrays.asList(3, 1, 7));
        assertArrayEquals(new int[]{1, 3, 7}, m.getIntValues(TikaPagedText.PAGE_NUMBERS));
        // No single-page-number when the resource spans multiple pages.
        assertNull(m.get(TikaPagedText.PAGE_NUMBER));
    }

    @Test
    void duplicatePagesArePreservedSorted() {
        // The helper doesn't dedupe — callers control set semantics by
        // using a Set.  This documents the current behaviour.
        Metadata m = new Metadata();
        PageAnchoring.applyPageMetadata(m, Arrays.asList(2, 2, 1));
        assertArrayEquals(new int[]{1, 2, 2}, m.getIntValues(TikaPagedText.PAGE_NUMBERS));
    }

    @Test
    void reapplicationClearsPriorValues() {
        Metadata m = new Metadata();
        PageAnchoring.applyPageMetadata(m, Arrays.asList(1, 2, 3));
        PageAnchoring.applyPageMetadata(m, Collections.singletonList(5));
        assertArrayEquals(new int[]{5}, m.getIntValues(TikaPagedText.PAGE_NUMBERS));
        assertEquals("5", m.get(TikaPagedText.PAGE_NUMBER));
    }

    @Test
    void reapplicationFromSingleToMultiClearsPageNumber() {
        Metadata m = new Metadata();
        PageAnchoring.applyPageMetadata(m, Collections.singletonList(4));
        assertEquals("4", m.get(TikaPagedText.PAGE_NUMBER));
        PageAnchoring.applyPageMetadata(m, Arrays.asList(4, 5));
        assertArrayEquals(new int[]{4, 5}, m.getIntValues(TikaPagedText.PAGE_NUMBERS));
        assertNull(m.get(TikaPagedText.PAGE_NUMBER));
    }

    @Test
    void sheetMetadataMirrorsPageMetadata() {
        // Sanity check that applySheetMetadata writes to Office's properties
        // with the same convention as applyPageMetadata writes to TikaPagedText's.
        Metadata m = new Metadata();
        PageAnchoring.applySheetMetadata(m, Arrays.asList(2, 4, 6));
        assertArrayEquals(new int[]{2, 4, 6}, m.getIntValues(Office.SHEET_NUMBERS));
        assertNull(m.get(Office.SHEET_NUMBER), "multi-sheet should leave SHEET_NUMBER unset");

        Metadata m2 = new Metadata();
        PageAnchoring.applySheetMetadata(m2, Collections.singletonList(3));
        assertArrayEquals(new int[]{3}, m2.getIntValues(Office.SHEET_NUMBERS));
        assertEquals("3", m2.get(Office.SHEET_NUMBER));

        Metadata m3 = new Metadata();
        PageAnchoring.applySheetMetadata(m3, Collections.emptyList());
        assertArrayEquals(new int[]{Office.UNLINKED_SHEET},
                m3.getIntValues(Office.SHEET_NUMBERS));
        assertNull(m3.get(Office.SHEET_NUMBER));
    }
}
