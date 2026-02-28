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
package org.apache.tika.detect.encoding;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.Test;

public class CharsetConfusablesTest {

    // ---------------------------------------------------------------
    // isLenientMatch — exact match always true
    // ---------------------------------------------------------------

    @Test
    public void testSameCharsetIsAlwaysSoft() {
        assertTrue(CharsetConfusables.isLenientMatch("UTF-8", "UTF-8"));
        assertTrue(CharsetConfusables.isLenientMatch("GB18030", "GB18030"));
        assertTrue(CharsetConfusables.isLenientMatch("Big5-HKSCS", "Big5-HKSCS"));
    }

    // ---------------------------------------------------------------
    // Symmetric groups — both directions are lenient
    // ---------------------------------------------------------------

    @Test
    public void testWesternEuropeanSymmetric() {
        assertTrue(CharsetConfusables.isLenientMatch("ISO-8859-1", "windows-1252"));
        assertTrue(CharsetConfusables.isLenientMatch("windows-1252", "ISO-8859-1")); // symmetric
        assertTrue(CharsetConfusables.isLenientMatch("ISO-8859-1", "ISO-8859-15"));
        assertTrue(CharsetConfusables.isLenientMatch("ISO-8859-15", "windows-1252"));
    }

    @Test
    public void testCyrillicSymmetric() {
        assertTrue(CharsetConfusables.isLenientMatch("ISO-8859-5", "windows-1251"));
        assertTrue(CharsetConfusables.isLenientMatch("windows-1251", "ISO-8859-5")); // symmetric
        assertTrue(CharsetConfusables.isLenientMatch("KOI8-R", "KOI8-U"));
        assertTrue(CharsetConfusables.isLenientMatch("KOI8-U", "KOI8-R")); // symmetric
    }

    @Test
    public void testUtf16Symmetric() {
        assertTrue(CharsetConfusables.isLenientMatch("UTF-16-LE", "UTF-16-BE"));
        assertTrue(CharsetConfusables.isLenientMatch("UTF-16-BE", "UTF-16-LE")); // symmetric
    }

    @Test
    public void testEbcdicVariantsSymmetric() {
        assertTrue(CharsetConfusables.isLenientMatch("IBM424-ltr", "IBM424-rtl"));
        assertTrue(CharsetConfusables.isLenientMatch("IBM424-rtl", "IBM424-ltr")); // symmetric
        assertTrue(CharsetConfusables.isLenientMatch("IBM420-ltr", "IBM420-rtl"));
        assertTrue(CharsetConfusables.isLenientMatch("IBM420-rtl", "IBM420-ltr")); // symmetric
    }

    // ---------------------------------------------------------------
    // Superset chains — directional (subset→superset is lenient, not vice versa)
    // ---------------------------------------------------------------

    @Test
    public void testSimplifiedChineseChainDirectional() {
        // Predicting superset when actual is subset: SOFT
        assertTrue(CharsetConfusables.isLenientMatch("GB2312", "GBK"));      // one step up
        assertTrue(CharsetConfusables.isLenientMatch("GB2312", "GB18030"));  // two steps up
        assertTrue(CharsetConfusables.isLenientMatch("GBK", "GB18030"));     // one step up

        // Predicting subset when actual is superset: NOT SOFT
        assertFalse(CharsetConfusables.isLenientMatch("GB18030", "GBK"));    // one step down
        assertFalse(CharsetConfusables.isLenientMatch("GB18030", "GB2312")); // two steps down
        assertFalse(CharsetConfusables.isLenientMatch("GBK", "GB2312"));     // one step down
    }

    @Test
    public void testTraditionalChineseDirectional() {
        // Big5 ⊂ Big5-HKSCS: predicting superset is lenient
        assertTrue(CharsetConfusables.isLenientMatch("Big5", "Big5-HKSCS"));

        // Predicting subset when actual is superset: NOT SOFT
        assertFalse(CharsetConfusables.isLenientMatch("Big5-HKSCS", "Big5"));
    }

    // ---------------------------------------------------------------
    // Unrelated charsets are never lenient matches
    // ---------------------------------------------------------------

    @Test
    public void testUnrelatedCharsets() {
        assertFalse(CharsetConfusables.isLenientMatch("UTF-8", "windows-1252"));
        assertFalse(CharsetConfusables.isLenientMatch("EUC-JP", "Shift_JIS"));
        assertFalse(CharsetConfusables.isLenientMatch("ISO-8859-1", "ISO-8859-5"));
        assertFalse(CharsetConfusables.isLenientMatch("GB18030", "Big5"));
        assertFalse(CharsetConfusables.isLenientMatch("IBM424-ltr", "IBM420-ltr"));
        assertFalse(CharsetConfusables.isLenientMatch("UTF-16-LE", "UTF-32-LE"));
    }

    // ---------------------------------------------------------------
    // symmetricPeersOf
    // ---------------------------------------------------------------

    @Test
    public void testSymmetricPeersOf() {
        Set<String> peers = CharsetConfusables.symmetricPeersOf("ISO-8859-1");
        assertTrue(peers.contains("windows-1252"));
        assertTrue(peers.contains("ISO-8859-15"));
        assertFalse(peers.contains("ISO-8859-1")); // not a peer of itself

        // Superset-chain members are NOT in the symmetric peer map
        assertTrue(CharsetConfusables.symmetricPeersOf("GB2312").isEmpty());
        assertTrue(CharsetConfusables.symmetricPeersOf("Big5").isEmpty());

        // Charset not in any group
        assertTrue(CharsetConfusables.symmetricPeersOf("UTF-8").isEmpty());
    }

    // ---------------------------------------------------------------
    // Structural integrity checks
    // ---------------------------------------------------------------

    @Test
    public void testSymmetricGroupsAreSymmetric() {
        for (Set<String> group : CharsetConfusables.SYMMETRIC_GROUPS) {
            String[] members = group.toArray(new String[0]);
            for (int i = 0; i < members.length; i++) {
                for (int j = 0; j < members.length; j++) {
                    if (i != j) {
                        assertTrue(CharsetConfusables.isLenientMatch(members[i], members[j]),
                                members[i] + " → " + members[j] + " should be lenient");
                        assertTrue(CharsetConfusables.isLenientMatch(members[j], members[i]),
                                members[j] + " → " + members[i] + " should be lenient");
                    }
                }
            }
        }
    }

    @Test
    public void testNoCharsetInTwoSymmetricGroups() {
        java.util.Map<String, Integer> seen = new java.util.HashMap<>();
        for (int i = 0; i < CharsetConfusables.SYMMETRIC_GROUPS.size(); i++) {
            for (String cs : CharsetConfusables.SYMMETRIC_GROUPS.get(i)) {
                Integer prev = seen.put(cs, i);
                if (prev != null) {
                    throw new AssertionError(
                            cs + " appears in symmetric groups " + prev + " and " + i);
                }
            }
        }
    }

    @Test
    public void testSupersetChainAcyclic() {
        // Walk every chain from SUPERSET_OF and confirm no cycle
        for (String start : CharsetConfusables.SUPERSET_OF.keySet()) {
            java.util.Set<String> visited = new java.util.HashSet<>();
            String cur = start;
            while (cur != null) {
                assertTrue(visited.add(cur),
                        "Cycle detected in SUPERSET_OF chain starting at " + start);
                cur = CharsetConfusables.SUPERSET_OF.get(cur);
            }
        }
    }
}
