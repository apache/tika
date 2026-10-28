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
package org.apache.tika.grpc.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.apache.tika.grpc.v2.Document;
import org.apache.tika.grpc.v2.EmbeddedDocument;
import org.apache.tika.grpc.v2.MetadataField;
import org.apache.tika.grpc.v2.MetadataValue;
import org.apache.tika.grpc.v2.ParseStatus;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;

class AdapterMergeTest {

    private static Document tika() {
        Metadata metadata = new Metadata();
        metadata.set(HttpHeaders.CONTENT_TYPE, "application/warc");
        metadata.set(TikaCoreProperties.TITLE, "Tika's title");
        metadata.set("warc:from-tika", "tika");
        return DocumentBuilder.build(metadata, "doc-1", "PARSE_SUCCESS", 5);
    }

    private static MetadataValue extra(List<MetadataField> fields, String key) {
        for (MetadataField field : fields) {
            if (field.getKey().equals(key)) {
                return field.getValue();
            }
        }
        return null;
    }

    private static List<AdapterMerge.Child> children(int count) {
        List<AdapterMerge.Child> children = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            Metadata child = new Metadata();
            child.set(HttpHeaders.CONTENT_TYPE, "text/html");
            child.set(TikaCoreProperties.CREATED, "2026-10-28T08:00:0" + (i % 10) + "Z");
            child.set(TikaCoreProperties.RESOURCE_NAME_KEY, "<urn:uuid:" + i + ">");
            children.add(new AdapterMerge.Child("/record-" + i, child, List.of()));
        }
        return children;
    }

    private static AdapterMerge.Result merge(Document core, Map<String, List<String>> parent,
                                             List<AdapterMerge.Child> children, long reported,
                                             AdapterMerge.Limits limits) {
        List<AdapterMerge.Child> copies = new ArrayList<>();
        for (AdapterMerge.Child child : children) {
            Metadata copy = new Metadata();
            copy.putAll(child.metadata());
            copies.add(new AdapterMerge.Child(child.path(), copy, child.errors()));
        }
        return AdapterMerge.merge(core, "fastwarc", "warc:", List.of(), parent, copies,
                reported, limits);
    }

    /** The adapter adds keys under its prefix only, and never changes what Tika set. */
    @Test
    void parentKeysAreAddedOnlyUnderThePrefixAndOnlyWhereTikaHasNone() {
        Document core = tika();
        Map<String, List<String>> parent = new LinkedHashMap<>();
        parent.put("warc:record-count", List.of("2"));
        parent.put("warc:from-tika", List.of("adapter"));
        parent.put("dc:title", List.of("Adapter's title"));
        parent.put("Content-Type", List.of("text/plain"));

        AdapterMerge.Result result = merge(core, parent, List.of(), 0,
                AdapterMerge.Limits.DEFAULTS);

        Document merged = result.document();
        assertEquals("Tika's title", merged.getMetadata().getTitle());
        assertEquals("application/warc", merged.getContentType());
        assertEquals("tika", extra(merged.getExtraList(), "warc:from-tika")
                .getStrings().getValues(0));
        assertEquals("2", extra(merged.getExtraList(), "warc:record-count")
                .getStrings().getValues(0));
        assertEquals(core.getStatus(), merged.getStatus());
        assertEquals(core.getOrigin(), merged.getOrigin());
        assertEquals(core.getMetadata(), merged.getMetadata());
        assertEquals(List.of("kept Tika's warc:from-tika", "dropped 2 parent keys outside warc:"),
                result.warnings());
        assertFalse(result.truncated());
    }

    /**
     * A key Tika's parse recorded is Tika's even when DocumentBuilder moved it into a typed
     * field, so it is not in extra; and a prefix over Tika's own namespaces is refused.
     */
    @Test
    void anAdapterCannotShadowTikasTypedFields() {
        Metadata tikaMetadata = new Metadata();
        tikaMetadata.set(TikaCoreProperties.TITLE, "Tika's title");
        Document core = DocumentBuilder.build(tikaMetadata, "doc-1", "PARSE_SUCCESS", 5);
        assertNull(extra(core.getExtraList(), "dc:title"), "typed, so not in extra");

        AdapterMerge.Result result = AdapterMerge.merge(core, "rogue", "dc:",
                List.of(tikaMetadata.names()), Map.of("dc:title", List.of("Adapter's title"),
                        "dc:other", List.of("x")), List.of(), 0, AdapterMerge.Limits.DEFAULTS);

        assertNull(extra(result.document().getExtraList(), "dc:title"));
        assertTrue(result.warnings().contains("kept Tika's dc:title"),
                result.warnings().toString());

        for (String prefix : List.of("dc:", "DC:", "Content-", "pdf:", "X-TIKA:", "tk:", "x",
                "mp4:", "MP4:", "audio:", "digest:", "fs:", "ner:", "machine:", "video:")) {
            assertTrue(AdapterMerge.tikaNamespace(prefix) != null, prefix);
        }
        for (String prefix : List.of("warc:", "fastwarc:", "calamine:")) {
            assertNull(AdapterMerge.tikaNamespace(prefix), prefix);
        }
    }

    @Test
    void childrenBecomeTypedEmbeddedDocumentsInOrder() {
        Metadata broken = new Metadata();
        broken.set("k", "v");
        List<AdapterMerge.Child> children = new ArrayList<>(children(2));
        children.add(new AdapterMerge.Child("/record-3", broken, List.of("cut short")));

        AdapterMerge.Result result = merge(tika(), Map.of(), children, 3,
                AdapterMerge.Limits.DEFAULTS);

        Document merged = result.document();
        assertEquals(3, merged.getEmbeddedCount());
        EmbeddedDocument first = merged.getEmbedded(0);
        assertEquals("/record-1", first.getPath());
        assertEquals("fastwarc", first.getProducerId());
        assertEquals("text/html", first.getContentType());
        assertEquals("<urn:uuid:1>", first.getOrigin().getFilename());
        assertEquals(ParseStatus.Status.STATUS_SUCCESS, first.getStatus());
        assertTrue(first.getMetadata().hasCreated(), "typed through Tika's Property model");
        assertEquals("/record-2", merged.getEmbedded(1).getPath());
        EmbeddedDocument third = merged.getEmbedded(2);
        assertEquals(ParseStatus.Status.STATUS_PARTIAL, third.getStatus());
        assertEquals(List.of("cut short"), third.getErrorsList());
        assertEquals(3, result.kept());
        assertFalse(result.truncated());
        assertTrue(result.warnings().isEmpty(), result.warnings().toString());
    }

    /** Paths are the producer's: required, rooted, and unique per producer. */
    @Test
    void childrenWithoutAUsablePathAreDropped() {
        Metadata metadata = new Metadata();
        metadata.set("k", "v");
        metadata.set(TikaCoreProperties.EMBEDDED_RESOURCE_PATH, "/ignored");
        List<AdapterMerge.Child> children = List.of(
                new AdapterMerge.Child("/a", metadata, List.of()),
                new AdapterMerge.Child("/a", metadata, List.of()),
                new AdapterMerge.Child("relative", metadata, List.of()),
                new AdapterMerge.Child(null, metadata, List.of()));

        AdapterMerge.Result result = merge(tika(), Map.of(), children, 4,
                AdapterMerge.Limits.DEFAULTS);

        assertEquals(1, result.document().getEmbeddedCount());
        assertNull(extra(result.document().getEmbedded(0).getExtraList(),
                TikaCoreProperties.EMBEDDED_RESOURCE_PATH.getName()),
                "the path is the EmbeddedDocument's, not a metadata key");
        assertTrue(result.truncated());
        assertTrue(result.warnings().stream().anyMatch(w -> w.startsWith("dropped 2 embedded")),
                result.warnings().toString());
        assertTrue(result.warnings().contains(
                "dropped 1 embedded documents whose path this producer already used"),
                result.warnings().toString());
    }

    @Test
    void aCappedListIsMarkedTruncatedWithBothCounts() {
        AdapterMerge.Result capped = merge(tika(), Map.of(), children(4), 4,
                new AdapterMerge.Limits(2, 256, 8192, Long.MAX_VALUE));
        assertEquals(2, capped.document().getEmbeddedCount());
        assertTrue(capped.truncated());
        assertEquals(4, capped.reported());
        assertEquals(2, capped.kept());
        assertEquals(List.of("kept 2 of 4 embedded documents (maxChildren)"),
                capped.warnings());

        AdapterMerge.Result bytes = merge(tika(), Map.of(), children(4), 4,
                new AdapterMerge.Limits(100, 256, 8192, 1));
        assertEquals(0, bytes.document().getEmbeddedCount());
        assertTrue(bytes.truncated());

        AdapterMerge.Result fewer = merge(tika(), Map.of(), children(2), 5,
                AdapterMerge.Limits.DEFAULTS);
        assertTrue(fewer.truncated(), "the adapter said there were more");
        assertEquals(5, fewer.reported());
    }

    @Test
    void keysAndValuesAreBounded() {
        Metadata child = new Metadata();
        child.set("warc:long", "x".repeat(100));
        child.set("bad key", "v");
        for (int i = 0; i < 5; i++) {
            child.set("k" + i, "v");
        }

        AdapterMerge.Result result = merge(tika(), Map.of(),
                List.of(new AdapterMerge.Child("/1", child, List.of())), 1,
                new AdapterMerge.Limits(10, 3, 10, Long.MAX_VALUE));

        EmbeddedDocument embedded = result.document().getEmbedded(0);
        assertEquals(3, embedded.getExtraCount(), embedded.getExtraList().toString());
        assertEquals("xxxxxxxxxx", extra(embedded.getExtraList(), "warc:long")
                .getStrings().getValues(0));
        assertTrue(result.warnings().contains("dropped 1 keys that are not plain names"),
                result.warnings().toString());
        assertTrue(result.warnings().contains("dropped 3 keys over the limit of 3 per document"),
                result.warnings().toString());
        assertTrue(result.warnings().contains("cut 1 values to 10 characters"),
                result.warnings().toString());
    }

    /** The merge reads the adapter's child metadata and never changes it. */
    @Test
    void theMergeDoesNotChangeItsInput() {
        Metadata child = new Metadata();
        child.set(TikaCoreProperties.EMBEDDED_RESOURCE_PATH, "/x");
        child.set("warc:long", "y".repeat(50));
        child.set("bad key", "z");
        Metadata before = new Metadata();
        before.putAll(child);
        Document core = DocumentBuilder.build(new Metadata(), "doc-1", "PARSE_SUCCESS", 5);

        AdapterMerge.merge(core, "records", "warc:", List.of(), Map.of(),
                List.of(new AdapterMerge.Child("/record-1", child, List.of())), 1,
                new AdapterMerge.Limits(10, 10, 10, 1 << 20));

        assertEquals(before, child);
    }

    /** A parent key matches the adapter's prefix whatever its case. */
    @Test
    void parentKeysMatchThePrefixIgnoringCase() {
        Document core = DocumentBuilder.build(new Metadata(), "doc-1", "PARSE_SUCCESS", 5);

        AdapterMerge.Result result = AdapterMerge.merge(core, "records", "warc:", List.of(),
                Map.of("WARC:Record-Count", List.of("2")), List.of(), 0,
                AdapterMerge.Limits.DEFAULTS);

        assertTrue(extra(result.document().getExtraList(), "WARC:Record-Count") != null,
                result.warnings().toString());
    }
}
