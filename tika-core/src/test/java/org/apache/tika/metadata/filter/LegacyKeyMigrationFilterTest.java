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
package org.apache.tika.metadata.filter;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.filter.LegacyKeyMigrationFilter.Direction;

public class LegacyKeyMigrationFilterTest {

    private static List<Metadata> apply(LegacyKeyMigrationFilter f, Metadata m) throws Exception {
        List<Metadata> list = new ArrayList<>();
        list.add(m);
        f.filter(list);
        return list;
    }

    @Test
    public void egressRenamesAndPassesThrough() throws Exception {
        // table is v3 -> v4; egress rewrites 4.x output back to 3.x keys.
        var f = new LegacyKeyMigrationFilter(
                Map.of("pdf:hasMarkedContent", "pdf:has-marked-content"), Direction.V4_TO_V3);
        Metadata m = new Metadata();
        m.set("pdf:has-marked-content", "true");   // 4.x key
        m.set("dc:title", "Hello");                // unmapped -> passthrough
        apply(f, m);
        assertEquals("true", m.get("pdf:hasMarkedContent"));
        assertNull(m.get("pdf:has-marked-content"));
        assertEquals("Hello", m.get("dc:title"));
    }

    @Test
    public void egressWritesReservedTarget() throws Exception {
        // Proves the trusted bracket: the 3.x target is a reserved (X-TIKA:) key a plain String
        // write would be dropped for.
        var f = new LegacyKeyMigrationFilter(
                Map.of("X-TIKA:Parsed-By", "tk:parsed-by"), Direction.V4_TO_V3);
        Metadata m = new Metadata();
        // Post-4.0 tk: is reserved too, so seed it with a trusted write (a plain String set to a
        // reserved key is dropped by the guard); the point of the test is the X-TIKA: target.
        m.setTrusted("tk:parsed-by", "org.apache.tika.parser.DefaultParser");
        apply(f, m);
        assertEquals("org.apache.tika.parser.DefaultParser", m.get("X-TIKA:Parsed-By"));
        assertNull(m.get("tk:parsed-by"));
    }

    @Test
    public void ingestDropsRemovedKeysAndRenames() throws Exception {
        var f = new LegacyKeyMigrationFilter(Map.of(
                "pst:folderPath", "DROPPED",
                "pdf:hasMarkedContent", "pdf:has-marked-content"), Direction.V3_TO_V4);
        Metadata m = new Metadata();
        m.set("pst:folderPath", "/Inbox/Sub");     // 3.x key with no 4.x successor
        m.set("pdf:hasMarkedContent", "true");     // 3.x key -> 4.x
        apply(f, m);
        assertNull(m.get("pst:folderPath"));       // dropped
        assertEquals("true", m.get("pdf:has-marked-content"));
    }

    @Test
    public void multiValuedKeysArePreserved() throws Exception {
        var f = new LegacyKeyMigrationFilter(
                Map.of("meta:mapi-importance", "mapi:importance"), Direction.V4_TO_V3);
        Metadata m = new Metadata();
        m.add("mapi:importance", "1");
        m.add("mapi:importance", "2");
        apply(f, m);
        assertArrayEquals(new String[]{"1", "2"}, m.getValues("meta:mapi-importance"));
    }

    @Test
    public void emptyTableIsNoOp() throws Exception {
        var f = new LegacyKeyMigrationFilter(Map.of(), Direction.V4_TO_V3);
        Metadata m = new Metadata();
        m.set("dc:title", "Hello");
        apply(f, m);
        assertEquals("Hello", m.get("dc:title"));
    }

    @Test
    public void loadsBundledTableAndRewritesRealKeys() throws Exception {
        // default ctor loads the committed metadata-migration-3x-4x.json; egress (V4_TO_V3).
        LegacyKeyMigrationFilter f = new LegacyKeyMigrationFilter();
        Metadata m = new Metadata();
        m.setTrusted("tk:content", "hello");       // reserved 4.x key -> X-TIKA:content
        m.set("message:from-email", "a@b.com");    // non-reserved 4.x key -> Message:From-Email
        apply(f, m);
        assertEquals("hello", m.get("X-TIKA:content"));
        assertNull(m.get("tk:content"));
        assertEquals("a@b.com", m.get("Message:From-Email"));
    }

    @Test
    public void digestPrefixRuleEgress() throws Exception {
        // digest keys have no declaring field, so not in the flat table -> handled by the prefix rule.
        var f = new LegacyKeyMigrationFilter(Map.of(), Direction.V4_TO_V3);
        Metadata m = new Metadata();
        m.setTrusted("tk:digest:SHA-256", "abc");
        m.setTrusted("tk:digest:SHA-256:BASE32", "def");   // encoding suffix preserved
        m.setTrusted("tk:digest:MD5", "ghi");              // MD5 unchanged between 3.x/4.x
        apply(f, m);
        assertEquals("abc", m.get("X-TIKA:digest:SHA256"));
        assertEquals("def", m.get("X-TIKA:digest:SHA256:BASE32"));
        assertEquals("ghi", m.get("X-TIKA:digest:MD5"));
        assertNull(m.get("tk:digest:SHA-256"));
    }

    @Test
    public void digestPrefixRuleIngest() throws Exception {
        var f = new LegacyKeyMigrationFilter(Map.of(), Direction.V3_TO_V4);
        Metadata m = new Metadata();
        m.setTrusted("X-TIKA:digest:SHA3_512", "z");
        apply(f, m);
        assertEquals("z", m.get("tk:digest:SHA3-512"));
        assertNull(m.get("X-TIKA:digest:SHA3_512"));
    }

    // Open-vocabulary KeyPrefix families (unbounded suffix), rewritten by prefix rule rather than
    // the flat table; one representative row per family (plus a second NER row for the entity
    // type's own underscore, kept verbatim through the rule).
    private static final String[][] EGRESS_ROWS = {
            // {v4 key, v3 key, sample value}
            {"ner:PERSON", "NER_PERSON", "John McKay"},
            {"ner:WEEK_DAY", "NER_WEEK_DAY", "Sunday"},
            {"grobid:header:Title", "grobid:header_Title", "A Paper"},
            {"envi:lat/lon", "envi.lat/lon", "36.79, -108.48"},
            {"ogg:streams-total", "streams-total", "2"},
            {"iso19115:keywords:2", "Keywords 2", "climate"},
            {"iso19115:keywords-type:2", "KeywordsType 2", "theme"},
            {"iso19115:thesaurus-name-title:2", "ThesaurusNameTitle 2", "GCMD"},
            {"iso19115:thesaurus-name-alternative-title:2", "ThesaurusNameAlternativeTitle 2", "GCMD Keywords"},
            {"geotopic:alt-name1", "Optional_NAME1", "United States"},
            {"geotopic:alt-longitude12", "Optional_LONGITUDE12", "-98.5"},
    };
    private static final String[][] INGEST_ROWS = {
            // {v3 key, v4 key, sample value}
            {"NER_LOCATION", "ner:LOCATION", "Los Angeles"},
            {"grobid:header_Title", "grobid:header:Title", "A Paper"},
            {"envi.samples", "envi:samples", "2400"},
            {"streams-vorbis", "ogg:streams-vorbis", "1"},
            {"Keywords 3", "iso19115:keywords:3", "ocean"},
            {"Optional_LATITUDE1", "geotopic:alt-latitude1", "39.76"},
    };

    @Test
    public void prefixRuleFamiliesEgress() throws Exception {
        for (String[] row : EGRESS_ROWS) {
            assertMigrates(Direction.V4_TO_V3, row[0], row[1], row[2]);
        }
    }

    @Test
    public void prefixRuleFamiliesIngest() throws Exception {
        for (String[] row : INGEST_ROWS) {
            assertMigrates(Direction.V3_TO_V4, row[0], row[1], row[2]);
        }
    }

    private static void assertMigrates(Direction direction, String from, String to, String value)
            throws Exception {
        var f = new LegacyKeyMigrationFilter(Map.of(), direction);
        Metadata m = new Metadata();
        m.set(from, value);
        apply(f, m);
        assertEquals(value, m.get(to), from + " -> " + to);
        assertNull(m.get(from), from + " should be removed");
    }
}
