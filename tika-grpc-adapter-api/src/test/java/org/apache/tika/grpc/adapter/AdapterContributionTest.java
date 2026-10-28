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
package org.apache.tika.grpc.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;

class AdapterContributionTest {

    private static Metadata metadata(String value) {
        Metadata metadata = new Metadata();
        metadata.set("k", value);
        return metadata;
    }

    /** The builder holds no more than its limits allow, however much the adapter adds. */
    @Test
    void limitsApplyAsChildrenAreAdded() {
        AdapterContribution.Builder builder = AdapterContribution.builder(
                new ContributionLimits(2, 256, 5, 1_000_000, 2, 10));
        for (int i = 1; i <= 1000; i++) {
            builder.addChild("/" + i, metadata("value-" + i));
        }
        for (int i = 0; i < 50; i++) {
            builder.warn("a warning that is longer than ten characters");
        }

        AdapterContribution contribution = builder.build();

        assertEquals(2, contribution.children().size());
        assertEquals(1000, contribution.reportedChildren());
        assertTrue(contribution.truncated());
        assertEquals("value", contribution.children().get(0).metadata().get("k"));
        assertEquals("a warning ...", contribution.warnings().get(0));
        assertTrue(contribution.warnings().contains("998 children past maxChildren (2) not kept"),
                contribution.warnings().toString());
        assertTrue(contribution.warnings().contains("48 more warnings not kept"),
                contribution.warnings().toString());
    }

    @Test
    void theSizeLimitStopsChildrenAndKeys() {
        AdapterContribution contribution = AdapterContribution.builder(
                        new ContributionLimits(100, 256, 100, 30, 10, 100))
                .addChild("/1", metadata("0123456789"))
                .addChild("/2", metadata("0123456789"))
                .addChild("/3", metadata("0123456789"))
                .build();

        assertEquals(2, contribution.children().size());
        assertEquals(3, contribution.reportedChildren());
    }

    /**
     * The first child a limit stops ends the list, so what is kept is always the adapter's
     * list up to that point: a smaller child after it is not kept either.
     */
    @Test
    void theKeptChildrenAreAPrefixOfTheList() {
        AdapterContribution contribution = AdapterContribution.builder(
                        new ContributionLimits(100, 256, 100, 30, 10, 100))
                .addChild("/1", metadata("0123456789"))
                .addChild("/2", metadata("0123456789".repeat(3)))
                .addChild("/3", metadata("x"))
                .parentKey("k", "v")
                .build();

        assertEquals(List.of("/1"),
                contribution.children().stream().map(ChildDraft::path).toList());
        assertTrue(contribution.parentKeys().isEmpty(), "nothing is kept after the limit");
        assertEquals(3, contribution.reportedChildren());
        assertTrue(contribution.truncated());
    }

    /** A child's errors are bounded like warnings: count and length. */
    @Test
    void childErrorsAreBounded() {
        List<String> errors = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            errors.add("error number " + i + " is long");
        }
        AdapterContribution contribution = AdapterContribution.builder(
                        new ContributionLimits(100, 256, 100, 1_000_000, 3, 8))
                .addChild("/1", metadata("x"), errors)
                .build();

        assertEquals(List.of("error nu...", "error nu...", "error nu..."),
                contribution.children().get(0).errors());
        assertTrue(contribution.warnings().contains(
                "17 child errors past maxWarnings (3) not kept"),
                contribution.warnings().toString());
    }

    @Test
    void theFirstReasonIsKeptAndLaterOnesAreWarnings() {
        AdapterContribution contribution = AdapterContribution.builder()
                .addChild("/1", metadata("x"))
                .partialFailure("stopped at byte 20")
                .partialFailure("stopped at byte 90")
                .build();

        assertEquals("stopped at byte 20", contribution.reason());
        assertEquals(List.of("stopped at byte 90"), contribution.warnings());
    }

    /** Paths are required, rooted and unique; children keep the order they were added in. */
    @Test
    void pathsAreCheckedAndOrderIsKept() {
        AdapterContribution contribution = AdapterContribution.builder()
                .addChild("/b", metadata("1"))
                .addChild("/a", metadata("2"), List.of("cut short"))
                .addChild("/b", metadata("3"))
                .addChild("relative", metadata("4"))
                .addChild(null, metadata("5"))
                .addChild("/c\n", metadata("6"))
                .build();

        assertEquals(List.of("/b", "/a"),
                contribution.children().stream().map(ChildDraft::path).toList());
        assertEquals(List.of("cut short"), contribution.children().get(1).errors());
        assertEquals(6, contribution.reportedChildren());
        assertTrue(contribution.warnings().contains("1 children dropped: path already used"),
                contribution.warnings().toString());
    }

    /** A child is copied in and out, so neither the adapter nor the server can change it. */
    @Test
    void childrenCannotChangeAfterTheyAreAdded() {
        Metadata original = metadata("before");
        original.set(TikaCoreProperties.RESOURCE_NAME_KEY, "<urn:uuid:1>");
        AdapterContribution contribution = AdapterContribution.builder()
                .addChild("/1", original)
                .build();
        original.set("k", "after");
        contribution.children().get(0).metadata().set("k", "changed");

        assertEquals("before", contribution.children().get(0).metadata().get("k"));
        assertEquals("<urn:uuid:1>", contribution.children().get(0).metadata()
                .get(TikaCoreProperties.RESOURCE_NAME_KEY));
    }

    @Test
    void failBeatsPartialFailure() {
        AdapterContribution failed = AdapterContribution.builder()
                .fail("unreadable")
                .partialFailure("ignored")
                .build();
        assertEquals(AdapterContribution.Outcome.FAILED, failed.outcome());
        assertEquals("unreadable", failed.reason());

        AdapterContribution partial = AdapterContribution.builder()
                .addChild("/1", metadata("x"))
                .partialFailure("stopped at byte 20")
                .build();
        assertEquals(AdapterContribution.Outcome.PARTIAL, partial.outcome());
        assertFalse(partial.truncated());
    }

    /** The size limit counts UTF-8 bytes, so text of multi-byte characters fills it sooner. */
    @Test
    void theSizeLimitCountsUtf8Bytes() {
        assertEquals(1, AdapterContribution.Builder.utf8Length("a"));
        assertEquals(2, AdapterContribution.Builder.utf8Length("\u00e9"));
        assertEquals(3, AdapterContribution.Builder.utf8Length("\u20ac"));
        assertEquals(4, AdapterContribution.Builder.utf8Length("\ud83d\ude00"));

        ContributionLimits limits = new ContributionLimits(10, 10, 100, 40, 10, 100);
        Metadata ascii = new Metadata();
        ascii.set("k", "a".repeat(12));
        Metadata euros = new Metadata();
        euros.set("k", "\u20ac".repeat(12));
        AdapterContribution contribution = AdapterContribution.builder(limits)
                .addChild("/1", ascii)
                .addChild("/2", euros)
                .build();

        assertEquals(1, contribution.children().size(), "12 euro signs are 36 bytes, not 12");
    }

    /** Keys past the limit are never copied, and the caller's metadata is not changed. */
    @Test
    void childMetadataIsBoundedWhileItIsCopied() {
        ContributionLimits limits = new ContributionLimits(10, 2, 4, 1 << 20, 10, 100);
        Metadata many = new Metadata();
        for (int i = 0; i < 5; i++) {
            many.set("k" + i, "value" + i);
        }
        Metadata before = new Metadata();
        before.putAll(many);

        AdapterContribution contribution = AdapterContribution.builder(limits)
                .addChild("/1", many)
                .build();

        Metadata kept = contribution.children().get(0).metadata();
        assertEquals(2, kept.names().length);
        for (String name : kept.names()) {
            assertEquals(4, kept.get(name).length(), name);
        }
        assertEquals(before, many);
    }
}
