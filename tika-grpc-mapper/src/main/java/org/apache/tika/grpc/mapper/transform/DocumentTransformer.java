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
package org.apache.tika.grpc.mapper.transform;

import java.util.Set;

import org.apache.tika.grpc.v1.Document;
import org.apache.tika.metadata.Metadata;

/**
 * Maps one Tika {@link Metadata} (a document, or an embedded part) onto the typed
 * {@link Document}. Transformers are code, not schema: adding a parser adds a
 * transformer, and the wire contract never changes. More than one transformer may
 * apply to a single document (a format transformer plus cross-cutting ones, such as
 * Creative Commons rights detection alongside a PDF or Office transformer).
 */
public interface DocumentTransformer {

    /**
     * True if this transformer applies to the given document. Most transformers only
     * look at {@code tika.get(Metadata.CONTENT_TYPE)}; cross-cutting ones (e.g. Creative
     * Commons rights detection) may inspect the full metadata instead.
     */
    boolean appliesTo(Metadata tika);

    /**
     * Populate typed fields on the builder, and mark every Tika key consumed in
     * {@code consumed}. {@code consumed} is shared across every transformer that applies to
     * this document (not private to this transformer), so the tagged tail -- appended once,
     * after every applicable transformer has run -- never duplicates a key that a different
     * transformer already mapped to a typed field.
     */
    void transform(Metadata tika, Document.Builder document, Set<String> consumed);

    /**
     * True if this transformer is cross-cutting (may apply alongside, not instead of, a
     * format transformer -- e.g. Creative Commons rights detection). Cross-cutting
     * transformers do not count toward whether a document "matched" a format, so they never
     * suppress the generic fallback: a plain-text document with CC rights metadata still
     * gets the universal title/author/description mapping.
     */
    default boolean isCrossCutting() {
        return false;
    }
}
