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

/**
 * What one {@link AdapterContribution} may hold. The builder applies these as the adapter
 * adds to it, so an adapter cannot build more than this in memory: children past
 * {@code maxChildren} are counted, not kept, and keys and values past their limits are
 * dropped or cut. tika-grpc sets them from the adapter's config entry.
 *
 * @param maxChildren        children kept
 * @param maxKeysPerDocument metadata keys kept per child, and on the parent
 * @param maxValueChars      characters kept per value and per path
 * @param maxBytes           UTF-8 bytes of keys, values, paths and child errors kept across
 *                           the whole contribution; tika-grpc checks serialized bytes again
 *                           when it merges
 * @param maxWarnings        warnings kept, and errors kept per child
 * @param maxWarningChars    characters kept per warning
 */
public record ContributionLimits(int maxChildren, int maxKeysPerDocument, int maxValueChars,
                                 long maxBytes, int maxWarnings, int maxWarningChars) {

    public static final ContributionLimits DEFAULTS =
            new ContributionLimits(100_000, 256, 8_192, 64L * 1024 * 1024, 100, 1_000);

    public ContributionLimits {
        if (maxChildren < 0 || maxKeysPerDocument < 0 || maxValueChars < 0 || maxBytes < 0
                || maxWarnings < 0 || maxWarningChars < 0) {
            throw new IllegalArgumentException("contribution limits must not be negative");
        }
    }
}
