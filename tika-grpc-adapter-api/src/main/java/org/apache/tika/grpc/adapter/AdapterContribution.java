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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.tika.metadata.Metadata;

/**
 * What an {@link ExternalParserAdapter} made of one reply, staged for tika-grpc to check and
 * merge: keys for the parent, child documents in order, warnings, and an outcome. It is built
 * with {@link AdapterRequest#contribution()}, whose builder applies the request's
 * {@link ContributionLimits} as things are added, and cannot change afterwards. Nothing in it
 * is applied until tika-grpc merges it, under its own rules; a contribution built with other
 * limits than the request's is refused.
 * <p>
 * The children kept are the ones added, in order, up to the first that a limit
 * ({@code maxChildren} or {@code maxBytes}) stops; nothing added after that is kept. A child
 * with an unusable or repeated path is dropped where it is and counted, so the kept list is
 * the adapter's list up to the first limit, less such children.
 * <p>
 * Outcomes:
 * <ul>
 *     <li>{@link Outcome#OK}: everything is merged.</li>
 *     <li>{@link Outcome#PARTIAL} ({@link Builder#partialFailure}): the parser stopped part
 *     way, say at a broken record; the children and parent keys added are merged, and the
 *     slot is {@code STATUS_PARTIAL} with the reason.</li>
 *     <li>{@link Outcome#FAILED} ({@link Builder#fail}): nothing is merged, neither children
 *     nor parent keys; the slot is {@code STATUS_FAILED} with the reason.</li>
 * </ul>
 */
public final class AdapterContribution {

    /** How the parse went, as the adapter reads the reply. */
    public enum Outcome { OK, PARTIAL, FAILED }

    private final Outcome outcome;
    private final String reason;
    private final Map<String, List<String>> parentKeys;
    private final List<ChildDraft> children;
    private final long reportedChildren;
    private final boolean truncated;
    private final List<String> warnings;
    private final ContributionLimits limits;

    private AdapterContribution(Builder builder) {
        this.outcome = builder.outcome;
        this.reason = builder.reason;
        Map<String, List<String>> keys = new LinkedHashMap<>();
        builder.parentKeys.forEach((key, values) -> keys.put(key, List.copyOf(values)));
        this.parentKeys = Collections.unmodifiableMap(keys);
        this.children = List.copyOf(builder.children);
        this.reportedChildren = Math.max(builder.reportedChildren, builder.offeredChildren);
        this.truncated = reportedChildren > children.size();
        this.warnings = builder.warnings();
        this.limits = builder.limits;
    }

    /** A builder with the default limits, for tests; adapters use the request's. */
    static Builder builder() {
        return builder(ContributionLimits.DEFAULTS);
    }

    static Builder builder(ContributionLimits limits) {
        return new Builder(limits);
    }

    /** The limits it was built under; tika-grpc checks they are the request's. */
    public ContributionLimits limits() {
        return limits;
    }

    public Outcome outcome() {
        return outcome;
    }

    /** Why the outcome is not {@link Outcome#OK}, or {@code null}. */
    public String reason() {
        return reason;
    }

    /** Keys for the parent document, with their values in order. */
    public Map<String, List<String>> parentKeys() {
        return parentKeys;
    }

    /** The children kept, in the order the adapter added them. */
    public List<ChildDraft> children() {
        return children;
    }

    /**
     * How many children the reply described: every one the adapter offered, kept or not,
     * or more if it said so with {@link Builder#reportedChildren}.
     */
    public long reportedChildren() {
        return reportedChildren;
    }

    /** Whether fewer children were kept than the reply described. */
    public boolean truncated() {
        return truncated;
    }

    public List<String> warnings() {
        return warnings;
    }

    /** Adds to a contribution, applying its limits as it goes. Not thread-safe. */
    public static final class Builder {

        private final ContributionLimits limits;
        private final Map<String, List<String>> parentKeys = new LinkedHashMap<>();
        private final List<ChildDraft> children = new ArrayList<>();
        private final Set<String> paths = new HashSet<>();
        private final List<String> warnings = new ArrayList<>();
        private long offeredChildren;
        private long reportedChildren;
        private long bytes;
        private Outcome outcome = Outcome.OK;
        private String reason;
        private int droppedWarnings;
        private int badPaths;
        private int duplicatePaths;
        private int overChildLimit;
        private int overSizeLimit;
        private int droppedKeys;
        private int cutValues;
        private int droppedErrors;
        // Set by the first child or key a size or count limit stops; nothing is kept after.
        private boolean full;

        private Builder(ContributionLimits limits) {
            this.limits = limits;
        }

        /**
         * Adds a value for {@code key} on the parent. tika-grpc keeps it only under the
         * adapter's key prefix and only where Tika set no value.
         */
        public Builder parentKey(String key, String value) {
            if (key == null) {
                droppedKeys++;
                return this;
            }
            List<String> values = parentKeys.get(key);
            if (values == null && parentKeys.size() >= limits.maxKeysPerDocument()) {
                droppedKeys++;
                return this;
            }
            String kept = cut(value);
            if (full || !fits(utf8Length(key) + utf8Length(kept))) {
                full = true;
                overSizeLimit++;
                return this;
            }
            parentKeys.computeIfAbsent(key, k -> new ArrayList<>()).add(kept);
            return this;
        }

        /** {@link #addChild(String, Metadata, List)} with no errors. */
        public Builder addChild(String path, Metadata metadata) {
            return addChild(path, metadata, List.of());
        }

        /**
         * Adds a child document after the ones already added. Its {@code path} names it within
         * the parent: it must start with {@code /} and be unique in this contribution, or the
         * child is dropped with a warning. Keys and values past the limits are dropped or cut,
         * and errors past {@code maxWarnings} or {@code maxWarningChars}. The first child past
         * {@code maxChildren}, or past the size limit, ends the list: it and every child after
         * it are counted but not kept.
         *
         * @param errors what went wrong reading this child; the child is still kept
         */
        public Builder addChild(String path, Metadata metadata, List<String> errors) {
            offeredChildren++;
            if (full) {
                overSizeLimit++;
                return this;
            }
            if (children.size() >= limits.maxChildren()) {
                overChildLimit++;
                return this;
            }
            if (path == null || !path.startsWith("/") || path.length() > limits.maxValueChars()
                    || hasControl(path)) {
                badPaths++;
                return this;
            }
            if (paths.contains(path)) {
                duplicatePaths++;
                return this;
            }
            Metadata bounded = bound(metadata == null ? new Metadata() : metadata);
            List<String> keptErrors = new ArrayList<>();
            long size = utf8Length(path) + bytesOf(bounded);
            for (String error : errors == null ? List.<String>of() : errors) {
                if (keptErrors.size() >= limits.maxWarnings()) {
                    droppedErrors++;
                    continue;
                }
                String kept = cutWarning(error);
                keptErrors.add(kept);
                size += utf8Length(kept);
            }
            if (!fits(size)) {
                full = true;
                overSizeLimit++;
                return this;
            }
            paths.add(path);
            children.add(new ChildDraft(path, bounded, keptErrors));
            return this;
        }

        /** Says how many children the reply described, when more than were added. */
        public Builder reportedChildren(long count) {
            this.reportedChildren = count;
            return this;
        }

        /** Adds a warning to the adapter's slot, for something the reply reported or lacked. */
        public Builder warn(String warning) {
            if (warnings.size() >= limits.maxWarnings()) {
                droppedWarnings++;
            } else {
                warnings.add(cutWarning(warning));
            }
            return this;
        }

        /**
         * The parser stopped part way: what was added is merged, and the slot is
         * {@code STATUS_PARTIAL} with {@code reason}. The first reason given is kept; a later
         * one becomes a warning. Ignored after {@link #fail}, except as a warning.
         */
        public Builder partialFailure(String reason) {
            if (outcome == Outcome.OK) {
                this.outcome = Outcome.PARTIAL;
                this.reason = cutWarning(reason);
            } else {
                warn(reason);
            }
            return this;
        }

        /**
         * The parse failed: nothing is merged, and the slot is {@code STATUS_FAILED} with
         * {@code reason}. The first failure's reason is kept; any earlier partial-failure
         * reason, or a later failure's, becomes a warning.
         */
        public Builder fail(String reason) {
            if (outcome == Outcome.FAILED) {
                warn(reason);
                return this;
            }
            if (outcome == Outcome.PARTIAL) {
                warn(this.reason);
            }
            this.outcome = Outcome.FAILED;
            this.reason = cutWarning(reason);
            return this;
        }

        public AdapterContribution build() {
            return new AdapterContribution(this);
        }

        private List<String> warnings() {
            List<String> all = new ArrayList<>(warnings);
            count(all, badPaths, "children dropped: path missing, not starting with /,"
                    + " too long or with control characters");
            count(all, duplicatePaths, "children dropped: path already used");
            count(all, overChildLimit, "children past maxChildren (" + limits.maxChildren()
                    + ") not kept");
            count(all, overSizeLimit, "children or keys past the size limit not kept");
            count(all, droppedKeys, "keys past maxKeysPerDocument ("
                    + limits.maxKeysPerDocument() + ") dropped");
            count(all, cutValues, "values cut to " + limits.maxValueChars() + " characters");
            count(all, droppedErrors, "child errors past maxWarnings (" + limits.maxWarnings()
                    + ") not kept");
            count(all, droppedWarnings, "more warnings not kept");
            return List.copyOf(all);
        }

        private static void count(List<String> warnings, int count, String what) {
            if (count > 0) {
                warnings.add(count + " " + what);
            }
        }

        private boolean fits(long size) {
            if (bytes + size > limits.maxBytes()) {
                return false;
            }
            bytes += size;
            return true;
        }

        /**
         * A bounded copy of {@code metadata}, built key by key: keys past the limit are never
         * copied, and values are cut before they are copied, so an oversized child costs no
         * more than the limits.
         */
        private Metadata bound(Metadata metadata) {
            Metadata copy = new Metadata();
            int keys = 0;
            for (String name : metadata.names()) {
                if (++keys > limits.maxKeysPerDocument()) {
                    droppedKeys++;
                    continue;
                }
                for (String value : metadata.getValues(name)) {
                    // addTrusted: the adapter may write any key on its own child, reserved
                    // ones included, as a copy with putAll would.
                    copy.addTrusted(name, cut(value));
                }
            }
            return copy;
        }

        private static long bytesOf(Metadata metadata) {
            long size = 0;
            for (String name : metadata.names()) {
                size += utf8Length(name);
                for (String value : metadata.getValues(name)) {
                    size += utf8Length(value);
                }
            }
            return size;
        }

        /** The UTF-8 length of {@code text}, without encoding it. */
        static long utf8Length(String text) {
            if (text == null) {
                return 0;
            }
            long length = 0;
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                if (c < 0x80) {
                    length++;
                } else if (c < 0x800) {
                    length += 2;
                } else if (Character.isHighSurrogate(c) && i + 1 < text.length()
                        && Character.isLowSurrogate(text.charAt(i + 1))) {
                    length += 4;
                    i++;
                } else {
                    length += 3;
                }
            }
            return length;
        }

        private String cut(String value) {
            if (value != null && value.length() > limits.maxValueChars()) {
                cutValues++;
                return value.substring(0, limits.maxValueChars());
            }
            return value == null ? "" : value;
        }

        private String cutWarning(String warning) {
            String text = String.valueOf(warning);
            return text.length() > limits.maxWarningChars()
                    ? text.substring(0, limits.maxWarningChars()) + "..."
                    : text;
        }

        private static boolean hasControl(String text) {
            for (int i = 0; i < text.length(); i++) {
                if (Character.isISOControl(text.charAt(i))) {
                    return true;
                }
            }
            return false;
        }
    }
}
