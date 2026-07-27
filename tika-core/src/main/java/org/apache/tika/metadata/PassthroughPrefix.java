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
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A Tika-owned prefix under which keys are passed through from the source: the prefix is fixed, but
 * each key name comes verbatim from the document or tool, so keys are unbounded and can't be
 * {@link Property} constants. Declaring one self-registers it, so the open set is enumerable (as
 * {@link Property} makes the closed set) and lintable: a String write is legitimate iff its key is a
 * registered {@link Property} or its prefix is a registered {@code PassthroughPrefix}.
 *
 * @since Apache Tika 4.0.0
 */
public final class PassthroughPrefix {

    public enum Provenance { FILE, TOOL }

    private static final Map<String, PassthroughPrefix> REGISTRY = new ConcurrentHashMap<>();

    private final String prefix;
    private final Provenance provenance;
    private final String description;

    private PassthroughPrefix(String prefix, Provenance provenance, String description) {
        this.prefix = prefix;
        this.provenance = provenance;
        this.description = description;
        REGISTRY.put(prefix, this);
    }

    public static PassthroughPrefix file(String prefix, String description) {
        return new PassthroughPrefix(prefix, Provenance.FILE, description);
    }

    public static PassthroughPrefix tool(String prefix, String description) {
        return new PassthroughPrefix(prefix, Provenance.TOOL, description);
    }

    /** The full key for a source-derived {@code suffix}. */
    public String key(String suffix) {
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

    /** Declared prefixes, from loaded classes only. */
    public static Collection<PassthroughPrefix> registered() {
        return Collections.unmodifiableCollection(REGISTRY.values());
    }
}
