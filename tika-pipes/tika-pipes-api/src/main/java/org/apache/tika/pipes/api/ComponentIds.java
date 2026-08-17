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
package org.apache.tika.pipes.api;

import java.util.regex.Pattern;

/**
 * Naming rules for pipes component instance ids (fetchers, emitters, iterators).
 * <p>
 * Ids beginning with {@value #SYSTEM_PREFIX} are reserved for components the host wires up for
 * its own use -- tika-server's upload fetcher, for instance. Reserving a namespace rather than
 * enumerating names means a new internal component cannot be reached from a request by someone
 * forgetting to add it to a list.
 * <p>
 * Callers must {@link #normalize(String)} an id before comparing or storing it, and must validate
 * the normalized form. Validating the raw id and resolving the trimmed one would let
 * {@code " __foo"} slip past the prefix check and then bind to {@code __foo}.
 */
public final class ComponentIds {

    /** Prefix reserved for host-wired components; not nameable from user config or a request. */
    public static final String SYSTEM_PREFIX = "__";

    /**
     * Ids are restricted to this set so a normalized id can be used verbatim everywhere.
     * Trimming alone leaves NBSP and zero-width characters, which produce ids that look
     * reserved but resolve to nothing; the allowlist refuses them outright. It also keeps
     * path separators and quoting characters out of anything derived from an id.
     * System ids satisfy it too -- {@code _} is a member.
     */
    public static final Pattern LEGAL_ID = Pattern.compile("[A-Za-z0-9._-]+");

    private ComponentIds() {
    }

    /**
     * Canonical form of an id: trimmed, or null if null.
     */
    public static String normalize(String id) {
        return id == null ? null : id.trim();
    }

    /**
     * True if the normalized id is in the reserved system namespace.
     */
    public static boolean isSystem(String id) {
        String normalized = normalize(id);
        return normalized != null && normalized.startsWith(SYSTEM_PREFIX);
    }

    /**
     * Normalizes {@code id} and rejects it if it is blank, illegal, or system-reserved.
     *
     * @param id    the raw, caller-supplied id
     * @param what  component kind, for the message (e.g. "fetcher")
     * @param where origin, for the message (e.g. "configuration" or "a request")
     * @return the normalized id
     * @throws IllegalArgumentException if the id is blank, illegal, or system-reserved
     */
    public static String requireUserId(String id, String what, String where) {
        String normalized = requireLegalId(id, what, where);
        if (normalized.startsWith(SYSTEM_PREFIX)) {
            throw new IllegalArgumentException("'" + normalized + "' is reserved: " + what
                    + " ids beginning with '" + SYSTEM_PREFIX + "' are for internal use and may not"
                    + " be set from " + where + ".");
        }
        return normalized;
    }

    /**
     * As {@link #requireUserId}, but permits the system namespace. For ids the host itself
     * supplies, where the charset rule still applies but {@code __} is legitimate.
     *
     * @throws IllegalArgumentException if the id is blank or outside {@link #LEGAL_ID}
     */
    public static String requireLegalId(String id, String what, String where) {
        String normalized = normalize(id);
        if (normalized == null || normalized.isEmpty()) {
            throw new IllegalArgumentException("Blank " + what + " id in " + where + ".");
        }
        if (!LEGAL_ID.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Illegal " + what + " id '" + normalized + "' in "
                    + where + ". Ids may contain only letters, digits, '.', '_' and '-'.");
        }
        return normalized;
    }
}
