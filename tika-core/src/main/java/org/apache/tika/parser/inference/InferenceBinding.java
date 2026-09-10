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
package org.apache.tika.parser.inference;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.apache.tika.mime.MediaType;

/** One entry of the {@code "inference"} list: engine, input, tasks, filters, budget. */
public final class InferenceBinding {

    private final String id;
    private final String engine;
    private final InputKind input;
    private final List<String> tasks;
    private final Set<MediaType> include;
    private final Set<MediaType> exclude;
    private final int maxChunks;
    private final long maxBytes;
    private final boolean enabled;

    public InferenceBinding(String id, String engine, InputKind input, List<String> tasks,
                            Set<MediaType> include, Set<MediaType> exclude, int maxChunks,
                            long maxBytes, boolean enabled) {
        this.id = id;
        this.engine = engine;
        this.input = input;
        this.tasks = List.copyOf(tasks);
        this.include = include == null ? Collections.emptySet() : Set.copyOf(include);
        this.exclude = exclude == null ? Collections.emptySet() : Set.copyOf(exclude);
        this.maxChunks = maxChunks;
        this.maxBytes = maxBytes;
        this.enabled = enabled;
    }

    public String getId() {
        return id;
    }

    public String getEngine() {
        return engine;
    }

    public InputKind getInput() {
        return input;
    }

    public List<String> getTasks() {
        return tasks;
    }

    /** Units this binding may enrich per document; -1 for no limit. */
    public int getMaxChunks() {
        return maxChunks;
    }

    /** Largest unit this binding accepts, in bytes; -1 for no limit. */
    public long getMaxBytes() {
        return maxBytes;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean accepts(InputKind kind, MediaType type) {
        if (kind != input || type == null) {
            return false;
        }
        MediaType base = type.getBaseType();
        if (!include.isEmpty() && !matches(include, base)) {
            return false;
        }
        return !matches(exclude, base);
    }

    private static boolean matches(Set<MediaType> set, MediaType base) {
        for (MediaType t : set) {
            if (t.equals(base) || (t.getSubtype().equals("*") && t.getType().equals(base.getType()))) {
                return true;
            }
        }
        return false;
    }
}
