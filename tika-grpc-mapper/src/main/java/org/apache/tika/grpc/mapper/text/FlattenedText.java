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
package org.apache.tika.grpc.mapper.text;

import java.util.List;

/**
 * The result of flattening one Tika Document (or one embedded child): the analyzable
 * text, the exact block-path anchors constructed during the walk, and the provenance
 * needed to tie downstream annotations back to the parsed bytes.
 *
 * @param documentId     the Tika Document id ("" when the source carried none)
 * @param sourceSha256   SourceOrigin.sha256 ("" when the parse pipeline had no digester)
 * @param flattenVersion the flattening algorithm version the offsets are valid against
 * @param text           the flattened text OpenNLP analyzes
 * @param ranges         text-bearing anchors in ascending start order, non-overlapping
 * @param skippedPaths   block paths deliberately not analyzed (code blocks, raw html)
 */
public record FlattenedText(String documentId, String sourceSha256, String flattenVersion,
                                String text, List<BlockRange> ranges,
                                List<String> skippedPaths) {

    /**
     * One text-bearing anchor: the block (or table cell) at {@code path} produced
     * {@code text[start, end)}.
     */
    public record BlockRange(String path, int start, int end) {
    }

    /**
     * Projects a span in flattened coordinates onto the block anchors it overlaps, in
     * order. The common case is a single anchor; a span crossing a block boundary (rare,
     * since blocks are separated by blank lines) reports every anchor it touches with
     * the local range inside each.
     *
     * @param start inclusive start in flattened coordinates
     * @param end   exclusive end; must be greater than {@code start} and within the text
     * @return the overlapped anchors with span-local ranges, block order
     * @throws IllegalArgumentException if the span is empty, inverted, or out of bounds
     */
    public List<Projection> project(int start, int end) {
        if (start < 0 || end <= start || end > text.length()) {
            throw new IllegalArgumentException(
                    "Span [" + start + ", " + end + ") is not a valid range of a text of length "
                            + text.length());
        }
        return ranges.stream()
                .filter(r -> r.start() < end && start < r.end())
                .map(r -> new Projection(r.path(),
                        Math.max(start, r.start()) - r.start(),
                        Math.min(end, r.end()) - r.start()))
                .toList();
    }

    /**
     * A span projected into one block's local coordinates: {@code [localStart, localEnd)}
     * within the text that block contributed.
     */
    public record Projection(String path, int localStart, int localEnd) {
    }
}
