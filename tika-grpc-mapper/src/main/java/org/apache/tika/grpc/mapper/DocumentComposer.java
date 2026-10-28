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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;

import com.google.protobuf.ByteString;

import org.apache.tika.grpc.v2.Document;
import org.apache.tika.grpc.v2.ExtensionResult;

/**
 * Appends external parsers' results to Tika's {@link Document}, following the
 * TIKA-4771 composition rules:
 * <ol>
 *     <li>The core Document (everything but {@code id} and {@code extensions}) is never
 *     changed.</li>
 *     <li>The composer's work-item id becomes the Document id when the Document has none. A
 *     Document whose id differs from the work-item id is refused.</li>
 *     <li>When the composer says what it hashed, {@code origin.sha256} must be the same
 *     lowercase or uppercase hex SHA-256, or the merge is refused: a Document without a
 *     digest, or either digest not 64 hex digits, is refused too.</li>
 *     <li>Each envelope is checked first: {@code STATUS_UNSPECIFIED} becomes
 *     {@code STATUS_FAILED}; a payload is kept only on {@code STATUS_OK} and
 *     {@code STATUS_PARTIAL}, and only up to {@link #MAX_PAYLOAD_BYTES} (a larger one is
 *     dropped and the envelope marked truncated); negative counts become zero; warnings are
 *     capped. Each change is a warning on the envelope.</li>
 *     <li>Contributions to one slot that carry the same status and payload are the same
 *     result, whatever their {@code elapsed_ms} and {@code warnings} say (a retry, or a
 *     producer called again downstream): one is kept, the fastest, and of equally fast ones
 *     the one whose bytes sort first, so the choice does not depend on arrival order.
 *     Otherwise the best status wins, {@code STATUS_OK} over {@code STATUS_PARTIAL} over
 *     {@code STATUS_EMPTY} over {@code STATUS_SKIPPED} over {@code STATUS_FAILED}, so a
 *     successful retry replaces a failure. Two different payloads with the same best status
 *     are a conflict: neither is kept, and the slot gets a {@code STATUS_FAILED}
 *     envelope.</li>
 *     <li>One envelope per planned slot, and none for a slot that was not planned.</li>
 *     <li>A planned slot with no contribution gets a {@code STATUS_FAILED} envelope, so the
 *     result accounts for every slot the composer planned.</li>
 * </ol>
 * Producer ids are compared trimmed. Envelopes come out sorted by {@code producer_id}, so
 * the result does not depend on the order contributions arrived in.
 */
public final class DocumentComposer {

    /** The largest payload an envelope keeps, in serialized bytes. */
    public static final int MAX_PAYLOAD_BYTES = 64 * 1024 * 1024;

    /** Warnings an envelope keeps; the rest are counted in one more. */
    public static final int MAX_WARNINGS = 100;

    /** Characters kept per warning: v2's limit for failure text. */
    public static final int MAX_WARNING_CHARS = 1024;

    private static final Pattern SHA256_HEX = Pattern.compile("[0-9a-fA-F]{64}");

    private DocumentComposer() {
    }

    /**
     * @param core          Tika's Document for the bytes; extensions it already carries
     *                      count as contributions
     * @param workItemId    the composer's id for this work item; blank keeps the core id
     * @param inputSha256   hex SHA-256 of the bytes the composer sent, or {@code null} to
     *                      skip the check
     * @param contributions envelopes the producers returned, in any order
     * @param plannedSlots  every producer id the composer planned to call
     * @throws IllegalArgumentException if the digests differ or are not hex SHA-256, the
     *                                  Document already has another id, an envelope has no
     *                                  producer id, or a planned slot id is null or blank
     */
    public static Document compose(Document core, String workItemId, String inputSha256,
                                   Collection<ExtensionResult> contributions,
                                   Collection<String> plannedSlots) {
        if (inputSha256 != null) {
            String tikaSha256 = core.getOrigin().getSha256();
            if (!SHA256_HEX.matcher(inputSha256.trim()).matches()) {
                throw new IllegalArgumentException("the composer's digest is not a hex SHA-256: "
                        + inputSha256);
            }
            if (!SHA256_HEX.matcher(tikaSha256.trim()).matches()) {
                throw new IllegalArgumentException("Tika's Document has no hex SHA-256 to"
                        + " check the composer's digest against: '" + tikaSha256 + "'");
            }
            if (!tikaSha256.trim().equalsIgnoreCase(inputSha256.trim())) {
                throw new IllegalArgumentException("Tika parsed different bytes: origin.sha256"
                        + " is " + tikaSha256 + ", the composer sent " + inputSha256);
            }
        }
        boolean hasWorkItemId = workItemId != null && !workItemId.isBlank();
        if (hasWorkItemId && !core.getId().isEmpty() && !core.getId().equals(workItemId)) {
            throw new IllegalArgumentException("the Document's id is '" + core.getId()
                    + "', not the work item's '" + workItemId + "'");
        }

        Set<String> planned = new TreeSet<>();
        for (String slot : plannedSlots) {
            if (slot == null || slot.isBlank()) {
                throw new IllegalArgumentException("a planned slot has no producer id");
            }
            planned.add(slot.trim());
        }
        Map<String, List<ExtensionResult>> bySlot = new TreeMap<>();
        for (String slot : planned) {
            bySlot.put(slot, new ArrayList<>());
        }
        List<ExtensionResult> all = new ArrayList<>(core.getExtensionsList());
        all.addAll(contributions);
        for (ExtensionResult result : all) {
            if (result.getProducerId().isBlank()) {
                throw new IllegalArgumentException("an extension result has no producer_id");
            }
            List<ExtensionResult> slot = bySlot.get(result.getProducerId().trim());
            // A slot the composer did not plan is not reported.
            if (slot != null) {
                slot.add(checked(result));
            }
        }

        Document.Builder composed = core.toBuilder().clearExtensions();
        if (hasWorkItemId) {
            composed.setId(workItemId);
        }
        for (Map.Entry<String, List<ExtensionResult>> slot : bySlot.entrySet()) {
            composed.addExtensions(resolve(slot.getKey(), slot.getValue()));
        }
        return composed.build();
    }

    /** The envelope as rule 4 allows it, under its trimmed producer id. */
    static ExtensionResult checked(ExtensionResult result) {
        ExtensionResult.Builder checked = result.toBuilder()
                .setProducerId(result.getProducerId().trim());
        List<String> notes = new ArrayList<>();
        ExtensionResult.Status status = result.getStatus();
        if (status != ExtensionResult.Status.STATUS_OK
                && status != ExtensionResult.Status.STATUS_PARTIAL
                && status != ExtensionResult.Status.STATUS_EMPTY
                && status != ExtensionResult.Status.STATUS_SKIPPED
                && status != ExtensionResult.Status.STATUS_FAILED) {
            checked.setStatus(ExtensionResult.Status.STATUS_FAILED);
            notes.add("status " + result.getStatusValue() + " is not a known status");
            status = ExtensionResult.Status.STATUS_FAILED;
        }
        if (result.hasPayload()) {
            if (status != ExtensionResult.Status.STATUS_OK
                    && status != ExtensionResult.Status.STATUS_PARTIAL) {
                checked.clearPayload();
                notes.add("payload dropped: a " + status.name() + " result carries none");
            } else if (result.getPayload().getSerializedSize() > MAX_PAYLOAD_BYTES) {
                checked.clearPayload().setTruncated(true);
                notes.add("payload dropped: over " + MAX_PAYLOAD_BYTES + " bytes");
            }
        }
        if (result.getElapsedMs() < 0) {
            checked.setElapsedMs(0);
        }
        if (result.getEmbeddedReported() < 0 || result.getEmbeddedKept() < 0) {
            checked.setEmbeddedReported(Math.max(0, result.getEmbeddedReported()))
                    .setEmbeddedKept(Math.max(0, result.getEmbeddedKept()));
            notes.add("negative embedded counts set to 0");
        }
        if (checked.getEmbeddedKept() < checked.getEmbeddedReported()) {
            checked.setTruncated(true);
        }
        List<String> warnings = new ArrayList<>();
        for (String warning : result.getWarningsList()) {
            if (warnings.size() == MAX_WARNINGS) {
                break;
            }
            warnings.add(warning.length() > MAX_WARNING_CHARS
                    ? warning.substring(0, MAX_WARNING_CHARS) + "..." : warning);
        }
        if (result.getWarningsCount() > MAX_WARNINGS) {
            warnings.add((result.getWarningsCount() - MAX_WARNINGS) + " more warnings dropped");
        }
        warnings.addAll(notes);
        return checked.clearWarnings().addAllWarnings(warnings).build();
    }

    /** The one envelope a slot keeps, by the precedence in the class comment. */
    private static ExtensionResult resolve(String producerId, List<ExtensionResult> results) {
        if (results.isEmpty()) {
            return failed(producerId, "no contribution");
        }
        int best = results.stream().mapToInt(DocumentComposer::rank).max().getAsInt();
        // Keyed by status and payload only, so a retry with another elapsed_ms or other
        // warnings is the same result. Of each, the fastest is kept, then the one whose
        // bytes sort first, so arrival order does not matter.
        Map<ExtensionResult, ExtensionResult> distinct = new LinkedHashMap<>();
        ExtensionResult.Status bestStatus = null;
        for (ExtensionResult result : results) {
            if (rank(result) != best) {
                continue;
            }
            bestStatus = result.getStatus();
            ExtensionResult key = ExtensionResult.newBuilder()
                    .setStatus(result.getStatus())
                    .setPayload(result.getPayload())
                    .build();
            distinct.merge(key, result, (kept, other) ->
                    PREFERRED.compare(other, kept) < 0 ? other : kept);
        }
        if (distinct.size() == 1) {
            return distinct.values().iterator().next();
        }
        return failed(producerId, distinct.size() + " conflicting " + bestStatus.name()
                + " contributions; none kept");
    }

    private static final Comparator<ExtensionResult> PREFERRED =
            Comparator.comparingLong(ExtensionResult::getElapsedMs)
                    .thenComparing(ExtensionResult::toByteString,
                            ByteString.unsignedLexicographicalComparator());

    private static int rank(ExtensionResult result) {
        return switch (result.getStatus()) {
            case STATUS_OK -> 5;
            case STATUS_PARTIAL -> 4;
            case STATUS_EMPTY -> 3;
            case STATUS_SKIPPED -> 2;
            case STATUS_FAILED -> 1;
            default -> 0;
        };
    }

    /** A {@code STATUS_FAILED} envelope the composer writes for {@code producerId}. */
    public static ExtensionResult failed(String producerId, String warning) {
        return ExtensionResult.newBuilder()
                .setProducerId(producerId)
                .setStatus(ExtensionResult.Status.STATUS_FAILED)
                .addWarnings(warning)
                .build();
    }
}
