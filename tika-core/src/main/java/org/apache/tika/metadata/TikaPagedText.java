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

/**
 * Metadata properties for paged text, metadata appropriate
 * for an individual page (useful for embedded document handlers
 * called on individual pages).
 *
 * Use {@link PagedText} where possible
 */
public interface TikaPagedText {
    String TIKA_PAGED_TEXT_PREFIX = "tika_pg:";
    /**
     * 1-based page number for a specific page.  Set when a resource is
     * anchored to exactly one page; for resources spanning multiple pages
     * see {@link #PAGE_NUMBERS}.
     */
    Property PAGE_NUMBER = Property.internalInteger(TIKA_PAGED_TEXT_PREFIX + "page_number");

    /**
     * 1-based page numbers an embedded resource is anchored to, as a
     * sequence.  Used for resources that appear on multiple pages
     * (e.g. a logo or a shared image referenced from several slides
     * of a presentation, or an image embedded in multiple sheets of a
     * workbook).
     *
     * <p>Conventions:
     * <ul>
     *   <li>Resource on a single page: {@code PAGE_NUMBERS = [N]} and
     *       {@code PAGE_NUMBER = N} are both set.</li>
     *   <li>Resource on multiple pages: {@code PAGE_NUMBERS = [N1, N2, ...]};
     *       {@code PAGE_NUMBER} is not set.</li>
     *   <li>Resource present in the container but not referenced from any
     *       page (an "unlinked" or orphan resource):
     *       {@code PAGE_NUMBERS = [-1]} (see {@link #UNLINKED_PAGE}).
     *       Distinguishes "we know this image is unanchored" from
     *       "this format has no page concept" (which leaves the property
     *       unset).</li>
     *   <li>Format has no page concept, or per-page anchoring is unknown:
     *       both {@code PAGE_NUMBER} and {@code PAGE_NUMBERS} unset.</li>
     * </ul>
     */
    Property PAGE_NUMBERS =
            Property.internalIntegerSequence(TIKA_PAGED_TEXT_PREFIX + "page_numbers");

    /**
     * Sentinel value used as the sole element of {@link #PAGE_NUMBERS}
     * when an embedded resource is present in a paginated container but
     * not referenced from any page.  Chosen because real page numbers
     * are 1-based, so any negative value is out-of-band.
     */
    int UNLINKED_PAGE = -1;

    Property PAGE_ROTATION = Property.internalRational(TIKA_PAGED_TEXT_PREFIX + "page_rotation");
}
