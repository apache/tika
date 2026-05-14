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

import java.util.Collection;
import java.util.Objects;

/**
 * Helpers for tagging an embedded resource's metadata with the pages or
 * sheets of its parent document on which it appears.  Centralised so every
 * paginated/sheeted parser (HSLF/XSLF for PowerPoint, OpenDocumentParser
 * for ODP, HSSF/XSSF for Excel, PDF, ...) encodes the same convention
 * identically.
 *
 * <p>Convention:
 * <ul>
 *   <li>{@code indices == null}: caller does not know the resource's
 *       anchoring &mdash; both single- and sequence-valued properties are
 *       left unset.</li>
 *   <li>{@code indices.isEmpty()}: the resource is known to be present in
 *       the container but referenced from no page/sheet &mdash; the
 *       sequence property is set to a single-element array containing the
 *       sentinel value (see {@link TikaPagedText#UNLINKED_PAGE} and
 *       {@link Office#UNLINKED_SHEET}).</li>
 *   <li>{@code indices.size() == 1}: both the single-valued and the
 *       sequence-valued property are set, the sequence carrying the one
 *       index as its only element.  Consumers that only inspect the
 *       single-valued property still see the right answer.</li>
 *   <li>{@code indices.size() > 1}: only the sequence-valued property is
 *       set; the single-valued property is cleared, because no single
 *       index would be meaningful.</li>
 * </ul>
 *
 * <p>This class only mutates the metadata it is handed &mdash; it is the
 * caller's job to compute the index set (e.g. by pre-scanning a slide
 * file for picture references before the embedded-resource emission).
 */
public final class PageAnchoring {

    private PageAnchoring() {
    }

    /**
     * Apply {@link TikaPagedText#PAGE_NUMBER} / {@link TikaPagedText#PAGE_NUMBERS}
     * to {@code target}, per the page-anchoring convention.  Used by
     * presentation parsers (PPT, PPTX, ODP), and by PDF parsers when
     * tagging per-page embedded resources.
     *
     * @param target metadata to write to
     * @param pages  1-based page numbers, an empty collection for
     *               "known unlinked", or {@code null} for unknown
     */
    public static void applyPageMetadata(Metadata target, Collection<Integer> pages) {
        applyAnchorMetadata(target, pages,
                TikaPagedText.PAGE_NUMBER, TikaPagedText.PAGE_NUMBERS,
                TikaPagedText.UNLINKED_PAGE);
    }

    /**
     * Apply {@link Office#SHEET_NUMBER} / {@link Office#SHEET_NUMBERS} to
     * {@code target}, per the same convention as
     * {@link #applyPageMetadata}.  Used by spreadsheet parsers (HSSF,
     * XSSF) when tagging an embedded resource with the sheets it appears
     * on.
     *
     * @param target metadata to write to
     * @param sheets 1-based sheet numbers, an empty collection for
     *               "known unlinked", or {@code null} for unknown
     */
    public static void applySheetMetadata(Metadata target, Collection<Integer> sheets) {
        applyAnchorMetadata(target, sheets,
                Office.SHEET_NUMBER, Office.SHEET_NUMBERS,
                Office.UNLINKED_SHEET);
    }

    /**
     * Shared core for {@link #applyPageMetadata} and {@link #applySheetMetadata}.
     * Exposed so future paginated-resource conventions (e.g. arbitrary
     * index spaces other than pages/sheets) can reuse the same logic
     * without copying it.
     *
     * @param target            metadata to write to (no-op if {@code null})
     * @param indices           the anchor indices, or {@code null} for "unknown"
     * @param singleProperty    {@code internalInteger} property set when there
     *                          is exactly one anchor index
     * @param sequenceProperty  {@code internalIntegerSequence} property
     *                          populated with all anchor indices
     * @param unlinkedSentinel  value used as the sole element of
     *                          {@code sequenceProperty} when {@code indices}
     *                          is empty (known unlinked)
     */
    public static void applyAnchorMetadata(Metadata target, Collection<Integer> indices,
                                           Property singleProperty,
                                           Property sequenceProperty,
                                           int unlinkedSentinel) {
        if (target == null || indices == null) {
            return;
        }
        int[] arr;
        if (indices.isEmpty()) {
            arr = new int[]{unlinkedSentinel};
        } else {
            arr = indices.stream()
                    .filter(Objects::nonNull)
                    .mapToInt(Integer::intValue)
                    .sorted()
                    .toArray();
            if (arr.length == 0) {
                arr = new int[]{unlinkedSentinel};
            }
        }
        // Clear any previous value, then append each element.  Necessary
        // because Metadata has no set-int[] overload; using add() builds
        // the sequence one element at a time.  Clearing first guards
        // against accidental double application.
        target.remove(sequenceProperty.getName());
        target.remove(singleProperty.getName());
        for (int v : arr) {
            target.add(sequenceProperty, v);
        }
        if (arr.length == 1 && arr[0] != unlinkedSentinel) {
            target.set(singleProperty, arr[0]);
        }
    }
}
