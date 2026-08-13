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

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A Tika-owned prefix under which keys are passed through from the source: the prefix is fixed, but
 * each key name comes verbatim from the document or tool, so keys are unbounded and can't be
 * {@link Property} constants. Declaring one self-registers it, so the open set is enumerable (as
 * {@link Property} makes the closed set) and lintable: a String write is legitimate iff its key is a
 * registered {@link Property} or its prefix is a registered {@code KeyPrefix}.
 *
 * <p>Writes under a prefix go through {@link Metadata#add(KeyPrefix, String, String)} (or its
 * {@code Instant} overload for source-typed dates) — append-only, with built-in skip-and-WARN
 * handling of blank, over-length, or flooding source-derived names.</p>
 *
 * <p><strong>{@code KeyPrefix} instances are declaration-time constants.</strong> Declare one as a
 * {@code static final} field, the same way a curated {@link Property} constant is declared — never
 * construct one from document-derived text, and never construct one per-parse: the registry is
 * static and unbounded, so per-parse construction grows it forever. An identical redeclaration
 * (class re-init in another classloader) returns the incumbent; a conflicting one throws
 * {@link IllegalStateException}.
 *
 * @since Apache Tika 4.0.0
 */
public final class KeyPrefix {

    public enum Provenance { FILE, TOOL }

    /** Delimiters accepted at the end of a prefix — the complete live population uses only these
     * two ({@code geotopic:alt-}, {@code ogg:streams-}); '.' and '_' were retired with the
     * envi./NER_ renames. Loosening later is free; tightening later is breaking. */
    private static final char[] TRAILING_DELIMITERS = {':', '-'};

    private static final Map<String, KeyPrefix> REGISTRY = new ConcurrentHashMap<>();

    private final String prefix;
    private final Provenance provenance;
    private final String description;

    private KeyPrefix(String prefix, Provenance provenance, String description) {
        if (prefix == null || prefix.isEmpty()) {
            throw new IllegalArgumentException("KeyPrefix prefix must not be null or empty");
        }
        if (ReservedNamespaces.isTikaNative(prefix)) {
            throw new IllegalArgumentException("'" + prefix + "' is in the reserved Tika-native "
                    + "namespace (tk:/X-TIKA:); it cannot be a KeyPrefix");
        }
        if (!hasTrailingDelimiter(prefix)) {
            throw new IllegalArgumentException("'" + prefix + "' must end with one of "
                    + Arrays.toString(TRAILING_DELIMITERS));
        }
        this.prefix = prefix;
        this.provenance = provenance;
        this.description = description;
    }

    /**
     * Registers a freshly-constructed instance, tolerating an IDENTICAL redeclaration: a
     * declaring class re-initialized against a shared tika-core (webapp redeploy, a second
     * plugin classloader) gets the incumbent back instead of an
     * {@code ExceptionInInitializerError} that kills the class until JVM restart. A
     * CONFLICTING redeclaration (different provenance or description) still throws — that is
     * either per-parse misconstruction or two libraries claiming one prefix.
     */
    private static KeyPrefix register(KeyPrefix fresh) {
        KeyPrefix incumbent = REGISTRY.putIfAbsent(fresh.prefix, fresh);
        if (incumbent == null) {
            return fresh;
        }
        if (incumbent.provenance == fresh.provenance
                && Objects.equals(incumbent.description, fresh.description)) {
            return incumbent;
        }
        throw new IllegalStateException(
                "KeyPrefix '" + fresh.prefix + "' is already registered with different "
                        + "provenance/description; another library may already own this prefix. "
                        + "KeyPrefix instances are declaration-time constants (never from "
                        + "document-derived text, never per-parse).");
    }

    private static boolean hasTrailingDelimiter(String prefix) {
        char last = prefix.charAt(prefix.length() - 1);
        for (char d : TRAILING_DELIMITERS) {
            if (d == last) {
                return true;
            }
        }
        return false;
    }

    /**
     * Declares a FILE-provenance prefix: names read from the document itself. An identical
     * redeclaration (same provenance and description, e.g. class re-initialization in another
     * classloader) returns the incumbent instance.
     *
     * @throws IllegalArgumentException if {@code prefix} is null, empty, reserved, or lacks a
     * trailing delimiter
     * @throws IllegalStateException if {@code prefix} is already registered with a different
     * provenance or description
     */
    public static KeyPrefix file(String prefix, String description) {
        return register(new KeyPrefix(prefix, Provenance.FILE, description));
    }

    /**
     * Declares a TOOL-provenance prefix: names coined by an external tool or service. An
     * identical redeclaration returns the incumbent instance; see {@link #file}.
     *
     * @throws IllegalArgumentException if {@code prefix} is null, empty, reserved, or lacks a
     * trailing delimiter
     * @throws IllegalStateException if {@code prefix} is already registered with a different
     * provenance or description
     */
    public static KeyPrefix tool(String prefix, String description) {
        return register(new KeyPrefix(prefix, Provenance.TOOL, description));
    }

    /** The registered prefix instance for {@code prefix}, or {@code null}. */
    public static KeyPrefix get(String prefix) {
        return REGISTRY.get(prefix);
    }

    /**
     * The full key for a source-derived {@code suffix}.
     *
     * @throws IllegalArgumentException if {@code suffix} is null or empty
     */
    public String key(String suffix) {
        requireNonEmptyName(suffix);
        return prefix + suffix;
    }

    public String prefix() {
        return prefix;
    }

    public Provenance provenance() {
        return provenance;
    }

    public String description() {
        return description;
    }

    /** The prefix, so a String→KeyPrefix flip doesn't turn existing
     * logging/concatenation into {@code KeyPrefix@<hex>} with no compile signal. */
    public String toString() {
        return prefix;
    }

    /** Declared prefixes, from loaded classes only. */
    public static Collection<KeyPrefix> registered() {
        return Collections.unmodifiableCollection(REGISTRY.values());
    }

    private static void requireNonEmptyName(String name) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("KeyPrefix key suffix must not be null or empty");
        }
    }
}
