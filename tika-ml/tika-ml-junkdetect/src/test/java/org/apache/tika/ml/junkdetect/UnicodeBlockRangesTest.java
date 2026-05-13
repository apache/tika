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
package org.apache.tika.ml.junkdetect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Sanity checks for the static {@link UnicodeBlockRanges} lookup table.
 *
 * <p>The table is the single source of truth for F2 block bucketing across
 * trainer and inference, so any silent drift here would silently corrupt
 * the block-transition feature for the entire model.  These tests assert
 * a handful of known-codepoint → known-bucket facts plus the table's
 * internal invariants (sorted, non-overlapping, contiguous bucket ids).
 */
public class UnicodeBlockRangesTest {

    @Test
    void bucketCountIs339() {
        // 338 named ranges in the static table + 1 unassigned = 339 total.
        // If this ever fails, the static table has changed — check that
        // SCHEME_VERSION was bumped and downstream models retrained.
        assertEquals(339, UnicodeBlockRanges.bucketCount());
        assertEquals(338, UnicodeBlockRanges.UNASSIGNED);
    }

    @Test
    void wellKnownCodepointsMapToExpectedBuckets() {
        // 'A' (U+0041) → BASIC_LATIN bucket 0
        assertEquals(0, UnicodeBlockRanges.bucketOf('A'));
        // 'a' (U+0061) → BASIC_LATIN
        assertEquals(0, UnicodeBlockRanges.bucketOf('a'));
        // U+00FF (ÿ) → LATIN_1_SUPPLEMENT bucket 1 (last codepoint in range)
        assertEquals(1, UnicodeBlockRanges.bucketOf(0x00FF));
        // U+0100 (Ā) → LATIN_EXTENDED_A bucket 2 (first codepoint in next range)
        assertEquals(2, UnicodeBlockRanges.bucketOf(0x0100));
        // 中 (U+4E2D) → CJK_UNIFIED_IDEOGRAPHS bucket 120
        assertEquals(120, UnicodeBlockRanges.bucketOf(0x4E2D));
        // 国 (U+56FD) → CJK_UNIFIED_IDEOGRAPHS bucket 120
        assertEquals(120, UnicodeBlockRanges.bucketOf(0x56FD));
        // U+0D24 (ത, Malayalam letter ta) → MALAYALAM bucket 30
        assertEquals(30, UnicodeBlockRanges.bucketOf(0x0D24));
        // Hangul syllables - U+AC00 → bucket 147
        assertEquals(147, UnicodeBlockRanges.bucketOf(0xAC00));
        // Cyrillic А (U+0410) → CYRILLIC bucket 8
        assertEquals(8, UnicodeBlockRanges.bucketOf(0x0410));
    }

    @Test
    void codepointsInGapsBetweenBlocksReturnUnassigned() {
        // The Unicode standard leaves gaps where no block is assigned.
        // Examples (verified by enumeration on JDK 25):
        // U+10200 falls between PHAISTOS_DISC (U+101D0..U+101FF) and
        // LYCIAN (U+10280..U+1029F).
        assertEquals(UnicodeBlockRanges.UNASSIGNED, UnicodeBlockRanges.bucketOf(0x10200));
        // U+0860 changed in Unicode 10 — verify it's in some block (SYRIAC_SUPPLEMENT).
        assertNotEquals(UnicodeBlockRanges.UNASSIGNED, UnicodeBlockRanges.bucketOf(0x0860));
    }

    @Test
    void codepointsBeyondSupplementaryReturnUnassigned() {
        // Negative codepoints, supplementary range edges, and beyond U+10FFFF
        // are not valid input but the lookup must not crash; UNASSIGNED is fine.
        assertEquals(UnicodeBlockRanges.UNASSIGNED, UnicodeBlockRanges.bucketOf(-1));
        // U+10FFFF is the last codepoint and is in SUPPLEMENTARY_PRIVATE_USE_AREA_B.
        assertNotEquals(UnicodeBlockRanges.UNASSIGNED, UnicodeBlockRanges.bucketOf(0x10FFFF));
    }

    @Test
    void schemeVersionIsBumpedOnAnyTableChange() {
        // If the static table is ever modified, SCHEME_VERSION MUST be bumped
        // — otherwise loaded models silently re-map to the new bucketing.
        // This test enforces awareness: anyone changing the table will see
        // this assertion fail and be forced to think about the consequence.
        // Update the expected value here and bump SCHEME_VERSION together.
        assertEquals(1, UnicodeBlockRanges.SCHEME_VERSION);
    }

    @Test
    void bucketIdsCoverContiguousRange() {
        // Every named block id 0..337 must be reachable.  Hits a representative
        // codepoint in each range and asserts all 338 ids are produced (plus
        // UNASSIGNED for the gaps).
        boolean[] seen = new boolean[UnicodeBlockRanges.bucketCount()];
        for (int cp = 0; cp <= 0x10FFFF; cp++) {
            int bucket = UnicodeBlockRanges.bucketOf(cp);
            assertTrue(bucket >= 0 && bucket < UnicodeBlockRanges.bucketCount(),
                    "Bucket out of range at cp=U+" + Integer.toHexString(cp)
                            + ": " + bucket);
            seen[bucket] = true;
        }
        for (int b = 0; b < UnicodeBlockRanges.bucketCount(); b++) {
            assertTrue(seen[b], "Bucket id " + b + " is never produced by any codepoint");
        }
    }
}
