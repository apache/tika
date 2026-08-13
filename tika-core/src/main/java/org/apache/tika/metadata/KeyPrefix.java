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
import java.util.concurrent.ConcurrentHashMap;

import org.apache.tika.metadata.Property.PropertyType;
import org.apache.tika.metadata.Property.ValueType;

/**
 * A Tika-owned prefix under which keys are passed through from the source: the prefix is fixed, but
 * each key name comes verbatim from the document or tool, so keys are unbounded and can't be
 * {@link Property} constants. Declaring one self-registers it, so the open set is enumerable (as
 * {@link Property} makes the closed set) and lintable: a String write is legitimate iff its key is a
 * registered {@link Property} or its prefix is a registered {@code KeyPrefix}.
 *
 * <p><strong>{@code KeyPrefix} instances are declaration-time constants.</strong> Declare one as a
 * {@code static final} field, the same way a curated {@link Property} constant is declared — never
 * construct one from document-derived text, and never construct one per-parse: the registry is
 * static and unbounded, so per-parse construction grows it forever, and re-registering the same
 * prefix throws {@link IllegalStateException}.
 *
 * @since Apache Tika 4.0.0
 */
public final class KeyPrefix {

    public enum Provenance { FILE, TOOL }

    /** Delimiters accepted at the end of a prefix; kept intentionally loose (some declarations
     * predate a single convention) — see {@code ReservedNamespaces} for the reserved-name check. */
    private static final char[] TRAILING_DELIMITERS = {':', '.', '-', '_'};

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
        KeyPrefix incumbent = REGISTRY.putIfAbsent(prefix, this);
        if (incumbent != null) {
            throw new IllegalStateException(
                    "KeyPrefix '" + prefix + "' is already registered; KeyPrefix instances are "
                            + "declaration-time constants and must be constructed exactly once "
                            + "(never from document-derived text, never per-parse)");
        }
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
     * Declares a FILE-provenance prefix: names read from the document itself.
     *
     * @throws IllegalArgumentException if {@code prefix} is null, empty, reserved, or lacks a
     * trailing delimiter
     * @throws IllegalStateException if {@code prefix} is already registered
     */
    public static KeyPrefix file(String prefix, String description) {
        return new KeyPrefix(prefix, Provenance.FILE, description);
    }

    /**
     * Declares a TOOL-provenance prefix: names coined by an external tool or service.
     *
     * @throws IllegalArgumentException if {@code prefix} is null, empty, reserved, or lacks a
     * trailing delimiter
     * @throws IllegalStateException if {@code prefix} is already registered
     */
    public static KeyPrefix tool(String prefix, String description) {
        return new KeyPrefix(prefix, Provenance.TOOL, description);
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

    // ---- Typed minting factories -----------------------------------------------------
    // Each mints an UNREGISTERED Property (Property.mintUnregistered) named prefix + name,
    // shaped like the corresponding external* Property factory. Mint per call for doc-derived
    // names: minted Properties are cheap, unregistered value objects, GC'd with the parse.
    // A bounded, known vocabulary belongs in a curated Property constant instead (that IS the
    // cache, with registry/CI coverage) — never build a name-keyed static cache here, that
    // recreates the registry-growth leak this design avoids.

    private static void requireNonEmptyName(String name) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("KeyPrefix-minted Property name must not be null "
                    + "or empty");
        }
    }

    /** Mints an unregistered SIMPLE/TEXT Property, like {@link Property#externalText}. */
    public Property text(String name) {
        requireNonEmptyName(name);
        return Property.mintUnregistered(key(name), false, PropertyType.SIMPLE, ValueType.TEXT, null);
    }

    /** Mints an unregistered BAG/TEXT Property, like {@link Property#externalTextBag}. */
    public Property textBag(String name) {
        requireNonEmptyName(name);
        return Property.mintUnregistered(key(name), false, PropertyType.BAG, ValueType.TEXT, null);
    }

    /** Mints an unregistered SIMPLE/DATE Property, like {@link Property#externalDate}. */
    public Property date(String name) {
        requireNonEmptyName(name);
        return Property.mintUnregistered(key(name), false, PropertyType.SIMPLE, ValueType.DATE, null);
    }

    /** Mints an unregistered SIMPLE/INTEGER Property, like {@link Property#externalInteger}. */
    public Property integer(String name) {
        requireNonEmptyName(name);
        return Property.mintUnregistered(key(name), false, PropertyType.SIMPLE, ValueType.INTEGER,
                null);
    }

    /** Mints an unregistered SIMPLE/REAL Property, like {@link Property#externalReal}. */
    public Property real(String name) {
        requireNonEmptyName(name);
        return Property.mintUnregistered(key(name), false, PropertyType.SIMPLE, ValueType.REAL, null);
    }

    /** Mints an unregistered SIMPLE/BOOLEAN Property, like {@link Property#externalBoolean}. */
    public Property bool(String name) {
        requireNonEmptyName(name);
        return Property.mintUnregistered(key(name), false, PropertyType.SIMPLE, ValueType.BOOLEAN,
                null);
    }
}
