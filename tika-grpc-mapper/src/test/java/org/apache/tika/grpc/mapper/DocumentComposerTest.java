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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Locale;

import com.google.protobuf.Any;
import com.google.protobuf.StringValue;
import org.junit.jupiter.api.Test;

import org.apache.tika.grpc.v2.Document;
import org.apache.tika.grpc.v2.DocumentMetadata;
import org.apache.tika.grpc.v2.ExtensionResult;
import org.apache.tika.grpc.v2.SourceOrigin;

class DocumentComposerTest {

    private static final String SHA256 = "ab".repeat(32);

    private static final Document CORE = Document.newBuilder()
            .setContentType("application/warc")
            .setOrigin(SourceOrigin.newBuilder().setSha256(SHA256.toUpperCase(Locale.ROOT)))
            .setMetadata(DocumentMetadata.newBuilder().setTitle("t"))
            .build();

    private static ExtensionResult ok(String producer, String payload) {
        return ExtensionResult.newBuilder()
                .setProducerId(producer)
                .setStatus(ExtensionResult.Status.STATUS_OK)
                .setPayload(Any.pack(StringValue.of(payload)))
                .build();
    }

    @Test
    void appendsContributionsSortedBySlotAndLeavesTheCoreAlone() {
        Document composed = DocumentComposer.compose(CORE, "work-1", SHA256,
                List.of(ok("fastwarc", "w"), ok("calamine", "c")),
                List.of("calamine", "fastwarc"));

        assertEquals("work-1", composed.getId());
        assertEquals(List.of("calamine", "fastwarc"), composed.getExtensionsList().stream()
                .map(ExtensionResult::getProducerId).toList());
        assertEquals(CORE.toBuilder().setId("work-1").build(),
                composed.toBuilder().clearExtensions().build(), "core fields unchanged");
    }

    @Test
    void refusesTheMergeWhenTheDigestsDiffer() {
        assertThrows(IllegalArgumentException.class, () -> DocumentComposer.compose(CORE,
                "w", "01".repeat(32), List.of(ok("fastwarc", "w")), List.of("fastwarc")));
    }

    /** A digest check fails closed: a missing or malformed digest on either side refuses. */
    @Test
    void aDigestCheckWithoutTwoHexDigestsIsRefused() {
        Document noDigest = CORE.toBuilder().setOrigin(SourceOrigin.getDefaultInstance()).build();
        for (String sent : List.of("", " ", "abc123", SHA256 + "00")) {
            assertThrows(IllegalArgumentException.class, () -> DocumentComposer.compose(CORE,
                    "w", sent, List.of(), List.of()), sent);
        }
        assertThrows(IllegalArgumentException.class, () -> DocumentComposer.compose(noDigest,
                "w", SHA256, List.of(), List.of()));
    }

    /** The work-item id fills a blank id; an id already set is never overwritten. */
    @Test
    void theDocumentsOwnIdIsNeverOverwritten() {
        Document withId = CORE.toBuilder().setId("tika-id").build();
        assertEquals("tika-id", DocumentComposer.compose(withId, null, null, List.of(),
                List.of()).getId());
        assertEquals("tika-id", DocumentComposer.compose(withId, "tika-id", null, List.of(),
                List.of()).getId());
        assertThrows(IllegalArgumentException.class, () -> DocumentComposer.compose(withId,
                "other", null, List.of(), List.of()));
    }

    @Test
    void plannedSlotIdsAreTrimmedAndMustNotBeBlank() {
        Document composed = DocumentComposer.compose(CORE, null, null,
                List.of(ok(" fastwarc ", "w")), List.of("fastwarc "));
        assertEquals(List.of(ok("fastwarc", "w")), composed.getExtensionsList());

        for (String blank : new String[]{"", "  ", null}) {
            List<String> planned = new java.util.ArrayList<>();
            planned.add(blank);
            assertThrows(IllegalArgumentException.class, () -> DocumentComposer.compose(CORE,
                    null, null, List.of(), planned));
        }
    }

    /** A contribution for a slot the composer did not plan is not reported. */
    @Test
    void unplannedSlotsAreDropped() {
        Document composed = DocumentComposer.compose(CORE, null, null,
                List.of(ok("a", "x"), ok("rogue", "y")), List.of("a"));
        assertEquals(List.of(ok("a", "x")), composed.getExtensionsList());
    }

    @Test
    void envelopesAreCheckedBeforeTheyAreMerged() {
        ExtensionResult failedWithPayload = ok("a", "x").toBuilder()
                .setStatus(ExtensionResult.Status.STATUS_FAILED).build();
        ExtensionResult unspecified = ok("b", "y").toBuilder()
                .setStatus(ExtensionResult.Status.STATUS_UNSPECIFIED).build();
        ExtensionResult badCounts = ExtensionResult.newBuilder().setProducerId("c")
                .setStatus(ExtensionResult.Status.STATUS_OK)
                .setEmbeddedReported(5).setEmbeddedKept(-1).setElapsedMs(-3).build();
        ExtensionResult.Builder noisy = ExtensionResult.newBuilder().setProducerId("d")
                .setStatus(ExtensionResult.Status.STATUS_OK);
        for (int i = 0; i < DocumentComposer.MAX_WARNINGS + 5; i++) {
            noisy.addWarnings("w".repeat(DocumentComposer.MAX_WARNING_CHARS + 1));
        }

        Document composed = DocumentComposer.compose(CORE, null, null,
                List.of(failedWithPayload, unspecified, badCounts, noisy.build()),
                List.of("a", "b", "c", "d"));

        ExtensionResult a = composed.getExtensions(0);
        assertFalse(a.hasPayload(), "a FAILED result carries no payload");
        ExtensionResult b = composed.getExtensions(1);
        assertEquals(ExtensionResult.Status.STATUS_FAILED, b.getStatus());
        assertFalse(b.hasPayload());
        ExtensionResult c = composed.getExtensions(2);
        assertEquals(0, c.getEmbeddedKept());
        assertEquals(0, c.getElapsedMs());
        assertTrue(c.getTruncated(), "kept < reported is truncated");
        ExtensionResult d = composed.getExtensions(3);
        assertEquals(DocumentComposer.MAX_WARNINGS + 1, d.getWarningsCount());
        assertTrue(d.getWarnings(0).length() <= DocumentComposer.MAX_WARNING_CHARS + 3);
    }

    /** Equally fast copies of one result: the same one is kept whatever the arrival order. */
    @Test
    void theTieBreakDoesNotDependOnArrivalOrder() {
        ExtensionResult one = ok("a", "same").toBuilder().addWarnings("first").build();
        ExtensionResult two = ok("a", "same").toBuilder().addWarnings("second").build();
        assertEquals(
                DocumentComposer.compose(CORE, null, null, List.of(one, two), List.of("a")),
                DocumentComposer.compose(CORE, null, null, List.of(two, one), List.of("a")));
    }

    @Test
    void identicalContributionsCollapseAndDifferentOnesFailTheSlot() {
        Document composed = DocumentComposer.compose(CORE, null, null,
                List.of(ok("a", "same"), ok("a", "same"), ok("b", "one"), ok("b", "two")),
                List.of("a", "b"));

        assertEquals("", composed.getId());
        assertEquals(ok("a", "same"), composed.getExtensions(0));
        ExtensionResult conflict = composed.getExtensions(1);
        assertEquals("b", conflict.getProducerId());
        assertEquals(ExtensionResult.Status.STATUS_FAILED, conflict.getStatus());
        assertEquals(false, conflict.hasPayload(), "neither conflicting payload is kept");
    }

    /**
     * A producer called twice for the same bytes (a retry, or a downstream composer calling
     * it again) returns the same payload with another elapsed_ms: that is one result, not a
     * conflict. The fastest is kept.
     */
    @Test
    void aRetryWithAnotherElapsedTimeIsTheSameResult() {
        ExtensionResult first = ok("fastwarc", "records").toBuilder().setElapsedMs(812).build();
        ExtensionResult retry = ok("fastwarc", "records").toBuilder().setElapsedMs(790)
                .addWarnings("retried").build();
        Document alreadyComposed = CORE.toBuilder().addExtensions(first).build();

        Document composed = DocumentComposer.compose(alreadyComposed, "w", null,
                List.of(retry), List.of("fastwarc"));

        assertEquals(List.of(retry), composed.getExtensionsList());
    }

    /** A successful retry replaces the failure it retried. */
    @Test
    void anOkResultWinsOverAFailedOne() {
        ExtensionResult failure = DocumentComposer.failed("fastwarc", "UNAVAILABLE");
        Document composed = DocumentComposer.compose(CORE, "w", null,
                List.of(failure, ok("fastwarc", "records")), List.of("fastwarc"));

        assertEquals(List.of(ok("fastwarc", "records")), composed.getExtensionsList());
    }

    /** Two failures with different reasons are still one failed slot, not a conflict. */
    @Test
    void failuresWithDifferentWarningsCollapse() {
        Document composed = DocumentComposer.compose(CORE, "w", null,
                List.of(DocumentComposer.failed("a", "timeout"),
                        DocumentComposer.failed("a", "UNAVAILABLE")),
                List.of("a"));

        assertEquals(1, composed.getExtensionsCount());
        assertEquals(List.of("timeout"), composed.getExtensions(0).getWarningsList());
    }

    @Test
    void conflictResultDoesNotDependOnArrivalOrder() {
        assertEquals(
                DocumentComposer.compose(CORE, "w", null,
                        List.of(ok("b", "one"), ok("b", "two")), List.of("b")),
                DocumentComposer.compose(CORE, "w", null,
                        List.of(ok("b", "two"), ok("b", "one")), List.of("b")));
    }

    @Test
    void aPlannedSlotWithNoContributionIsFailed() {
        Document composed = DocumentComposer.compose(CORE, "w", null, List.of(),
                List.of("fastwarc"));

        assertEquals(ExtensionResult.Status.STATUS_FAILED,
                composed.getExtensions(0).getStatus());
        assertEquals(List.of("no contribution"), composed.getExtensions(0).getWarningsList());
    }

    @Test
    void anEnvelopeWithoutAProducerIdIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> DocumentComposer.compose(CORE, "w",
                null, List.of(ok("", "x")), List.of()));
    }
}
