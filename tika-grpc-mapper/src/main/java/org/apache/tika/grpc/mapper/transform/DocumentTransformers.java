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

import java.util.List;
import java.util.Set;

import org.apache.tika.grpc.v1.Document;
import org.apache.tika.metadata.Metadata;

/**
 * Picks and runs the applicable transformer(s) for a parsed document. The generic
 * fallback runs whenever no format-specific transformer matched, so an unknown format
 * still yields a useful, lossless Document.
 *
 * Adding support for a new format means adding a transformer to {@link #defaults()}.
 * The proto does not change, so clients never rebuild for it.
 */
public final class DocumentTransformers {

    private final List<DocumentTransformer> transformers;
    private final DocumentTransformer fallback = new GenericDocumentTransformer();

    public DocumentTransformers(List<DocumentTransformer> transformers) {
        this.transformers = transformers;
    }

    /**
     * Ships with only the generic Dublin Core mapping for now; per-format transformers
     * (PDF, Office, HTML, ...) are planned as code-only additions here.
     */
    public static DocumentTransformers defaults() {
        return new DocumentTransformers(List.of());
    }

    /**
     * Populate the typed metadata and tagged-tail portion of {@code document} for
     * {@code tika}. {@code consumed} carries the keys the caller already mapped (the
     * envelope fields) and is shared across every applicable transformer, so the tagged
     * tail -- appended exactly once below -- never duplicates a key that was already
     * given a typed home.
     */
    public void transform(Metadata tika, Document.Builder document, Set<String> consumed) {
        boolean matched = false;
        for (DocumentTransformer transformer : transformers) {
            if (transformer.appliesTo(tika)) {
                transformer.transform(tika, document, consumed);
                matched = true;
            }
        }
        if (!matched) {
            fallback.transform(tika, document, consumed);
        }
        MetadataTagger.appendTail(tika, consumed, document);
    }
}
